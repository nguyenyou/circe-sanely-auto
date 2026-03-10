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

## Recommendation

Start with **Fix 1** (inline primitive containers) — highest ROI. Most fields in real-world types are `String`, `Int`, `Long`, `Option[String]`, `List[String]`, and these are exactly the cases where virtual dispatch can be eliminated at compile time without architectural changes.

After Fix 1, measure on both platforms. If still losing on x86, proceed to Fix 2. Fix 3 is the nuclear option if the first two aren't sufficient.

## Validation

After each fix:
1. `./mill sanely.jvm.test` + `./mill compat.jvm.test` — correctness
2. `bash bench-runtime.sh 10 10` locally — measure improvement on ARM
3. CI benchmark run — measure improvement on x86
4. Compare ratios, not absolute numbers
