# Write performance regression on x86: root cause analysis and fix plan

## Problem

sanely-jsoniter shows a **~36% performance swing between ARM and x86** on writes:

| Platform | sanely-jsoniter writes | jsoniter-scala writes | Ratio |
|---|---|---|---|
| Apple Silicon (M3 Max) | ~770K ops/sec | ~615K ops/sec | **+25%** (we win) |
| x86 CI (ubuntu-latest) | ~378K ops/sec | ~422K ops/sec | **-11%** (we lose) |

Reads are consistently better on both platforms (+2-5%). The instability is **writes only**.

## Root Cause: Compositional vs Monolithic Codec Architecture

### What jsoniter-scala does (monolithic)

```scala
given JsonValueCodec[ApiResponse] = JsonCodecMaker.make
```

`JsonCodecMaker.make` generates **one monolithic codec** for the entire type tree. All field writes for `ApiResponse`, `User`, `Address`, `OrderItem`, `Order`, `OrderStatus`, and all variants are generated into a single connected unit. Zero virtual dispatch between types.

### What sanely-jsoniter does (compositional)

```scala
given JsonValueCodec[Address] = deriveJsoniterCodec
given JsonValueCodec[User] = deriveJsoniterCodec
given JsonValueCodec[OrderItem] = deriveJsoniterCodec
// ... 6 more separate codecs
given JsonValueCodec[ApiResponse] = deriveJsoniterCodec
```

Each codec is a separate object. For every non-primitive field, the macro generates:

```scala
codecs(idx).encodeValue(x.field.asInstanceOf[Any], out)  // virtual dispatch
```

### Virtual dispatch count per `ApiResponse` write

Per `ApiResponse` encode:
- `user` → virtual call to `UserCodec.encodeValue`
  - `address` → virtual call to `AddressCodec.encodeValue`
  - `tags: List[String]` → virtual call to `ListCodec.encodeValue` → per-element virtual to `StringCodec`
  - `phone: Option[String]` → virtual call to `OptionCodec.encodeValue`
  - `bio: Option[String]` → virtual call to `OptionCodec.encodeValue`
- `orders: List[Order]` → virtual call to `ListCodec.encodeValue`
  - per Order (×3): virtual call to `OrderCodec.encodeValue`
    - `items: List[OrderItem]` → virtual call to `ListCodec` → per item virtual to `OrderItemCodec`
    - `status: OrderStatus` → virtual call to `SumCodec` → virtual to variant codec
    - `note: Option[String]` → virtual call to `OptionCodec`

**~30+ virtual dispatches** per write for us vs **zero** for jsoniter-scala.

### Why ARM tolerates this but x86 doesn't

**Apple Silicon (M3 Max)**: ARM's branch predictor and large BTB handle indirect calls well. The JIT quickly devirtualizes monomorphic call sites. With sufficient warmup, virtual dispatch overhead becomes nearly invisible on M3's wide execution pipeline.

**x86 CI runners**: Older/smaller x86 cores on shared GitHub Actions runners have less aggressive indirect-call prediction. The JIT may not fully inline across codec boundaries within the benchmark warmup window. Each virtual call through the `codecs` array adds overhead that accumulates across ~30 dispatch points.

### Secondary factor: we write more bytes

jsoniter-scala defaults to `transientNone = true` — it **skips** `None` fields entirely. In the benchmark payload:
- `User.bio = None` → jsoniter skips, we write `"bio":null`
- `Order.note = None` (×2) → jsoniter skips, we write `"note":null`

~3 fewer key+null writes for jsoniter, saving perhaps 3-5% of work. This is inherent to circe format compatibility and not fixable.

### Why reads don't show this difference

Reads are dominated by field-name parsing + hash dispatch — inherently expensive work that dwarfs virtual dispatch overhead. Both approaches use similar dispatch patterns for reads. The ~2-3% read advantage we hold on both platforms comes from direct constructors + typed locals, which don't depend on dispatch depth.

## Fix Plan

Three approaches, ordered by effort/impact ratio:

### Fix 1: Inline primitive container codecs (medium effort, ~5-8% write improvement)

For `Option[primitive]` and `List[primitive]`, instead of going through `codecs(idx).encodeValue(...)`, generate the encode logic inline in the macro:

```scala
// Current: codecs(3).encodeValue(x.phone.asInstanceOf[Any], out)  -- virtual dispatch

// Proposed: inline expansion for Option[String]
x.phone match
  case Some(v) => out.writeNonEscapedAsciiKey("phone"); out.writeVal(v)
  case None => out.writeNonEscapedAsciiKey("phone"); out.writeNull()
```

The macro already does this for leaf primitives via `tryDirectWriteTerm`. Extending it to `Option[T]` and `List[T]` where `T` is a directly-writable type eliminates virtual dispatch for the most common container patterns.

**Files to modify**: `SanelyJsoniter.scala` (`generateFieldWrites`, `tryDirectWriteTerm`)

### Fix 2: Pre-computed key byte arrays (medium effort, ~5-10% write improvement)

jsoniter's `writeNonEscapedAsciiKey` copies char-by-char from a `String`:

```scala
// Inside JsonWriter.writeNonEscapedAsciiKey:
while (i < len) {
  buf(pos) = x.charAt(i).toByte
  pos += 1
  i += 1
}
```

Pre-computing key bytes including quotes and colon (e.g. `,"name":` as `Array[Byte]`) and using `System.arraycopy` would let x86's `rep movsb` / ERMS instructions do this in 1-2 cycles instead of a per-char loop.

This requires finding a way to write pre-computed bytes through jsoniter's `JsonWriter` API (or adding a helper method).

**Files to modify**: `SanelyJsoniter.scala`, `JsoniterRuntime.scala`, possibly `Codecs.scala`

### Fix 3: Recursive inline expansion for known nested products (high effort, ~10-15% write improvement)

When the macro sees a field like `address: Address` and `Address` is being derived in the same expansion, inline its field writes directly:

```scala
// Current: codecs(5).encodeValue(x.address.asInstanceOf[Any], out)  -- virtual dispatch

// Proposed: inline the entire Address encoding
out.writeNonEscapedAsciiKey("address")
out.writeObjectStart()
out.writeNonEscapedAsciiKey("street"); out.writeVal(x.address.street)
out.writeNonEscapedAsciiKey("city"); out.writeVal(x.address.city)
out.writeNonEscapedAsciiKey("state"); out.writeVal(x.address.state)
out.writeNonEscapedAsciiKey("zip"); out.writeVal(x.address.zip)
out.writeNonEscapedAsciiKey("country"); out.writeVal(x.address.country)
out.writeObjectEnd()
```

This is what jsoniter-scala's monolithic derivation achieves automatically. It would close the gap entirely but requires significant macro architecture changes to support recursive inlining with cycle detection.

**Files to modify**: `SanelyJsoniter.scala` (major refactor of codec generation)

## Trade-off Analysis

Each fix trades something different. Understanding these trade-offs matters because the project's core value proposition is **fast compile times** — trading compile-time performance for runtime performance would undermine the reason this library exists.

### Fix 1: Inline primitive containers — near-free

| You trade | You get |
|---|---|
| Slightly more macro expansion work (marginally slower compile time) | Fewer virtual dispatches at runtime |

No zinc incremental compilation impact — we're inlining `Option`/`List` handling for stdlib types that never change. This is the closest to a free win.

### Fix 2: Pre-computed key byte arrays — near-free

| You trade | You get |
|---|---|
| Small memory increase per codec (one `Array[Byte]` per field name, allocated once at init) | Faster key writing on all platforms |

Essentially free. The byte arrays are tiny and allocated once.

### Fix 3: Recursive inline expansion — real costs

| You trade | You get |
|---|---|
| **Compile time** — macro must recursively expand nested types, generating much larger ASTs | Eliminates all inter-codec virtual dispatch |
| **Bytecode size** — Address fields get duplicated into every codec that references Address | Monolithic runtime performance |
| **Zinc granularity** — if Address changes, everything that inlines Address must recompile (today only AddressCodec recompiles) | Fewer runtime objects |
| **Macro complexity** — cycle detection, depth limits, deduplication logic needed | Harder to maintain |

Fix 3 is essentially trading **compile-time performance for runtime performance**. This directly contradicts the project's identity — the whole reason this library exists is that circe-generic has slow compilation. Making our macros do more work to generate faster runtime code undermines that.

### ARM impact

None of these fixes would regress ARM performance. On ARM, the JIT already devirtualizes the dispatch chain, so Fixes 1-2 produce code equivalent to what the JIT generates — same steady-state throughput, slightly faster warmup. Fix 3 is also neutral-to-positive on ARM but carries the compile-time costs listed above.

## Recommendation

Implement **Fix 1 + Fix 2** only. They're near-free and should close ~60-70% of the x86 gap. If x86 writes are still -3-4% after both fixes, that's an acceptable trade-off — the library's pitch is compile speed, not runtime speed.

**Do not implement Fix 3** unless there's a compelling user demand for runtime parity with jsoniter-scala on x86. The compile-time and zinc incremental compilation costs are too high relative to the project's priorities.

## Validation

After each fix:
1. `./mill sanely.jvm.test` + `./mill compat.jvm.test` — correctness
2. `bash bench-runtime.sh 10 10` locally — measure improvement on ARM
3. CI benchmark run — measure improvement on x86
4. Compare ratios, not absolute numbers
