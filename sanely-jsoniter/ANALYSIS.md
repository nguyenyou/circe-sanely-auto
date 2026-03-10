# Can we beat jsoniter-scala? A zero-dependency JSON engine for Scala

## The signal

On a ~1.4 KB nested JSON payload (products, sealed traits, optionals, collections):

| Approach | Read (ops/sec) | Write (ops/sec) | Alloc/read | Alloc/write |
|---|---|---|---|---|
| circe + jawn | ~135K | ~124K | 28 KB | 27 KB |
| **sanely-jsoniter** | **~762K** | **~770K** | **3 KB** | **1 KB** |
| jsoniter-scala native | ~742K | ~615K | 3 KB | 1 KB |

sanely-jsoniter already **surpasses** jsoniter-scala native — +3% on reads, +25% on writes — while *using jsoniter-scala's own `JsonReader`/`JsonWriter` underneath*.

This means the performance advantage comes entirely from the **codec layer**, not the parser. We generate better code than jsoniter-scala's own `JsonCodecMaker.make`. If we also own the parser layer, we can push further.

## The thesis

A zero-dependency Scala 3 JSON library that:

1. **Competes with or beats jsoniter-scala** on throughput and allocation
2. **Maintains 100% circe wire compatibility** (the contract is non-negotiable)
3. **Has zero transitive dependencies** — just Scala stdlib
4. **Exposes a circe-compatible API** (`Encoder[T]`, `Decoder[T]`, `Codec[T]`)

The core bet: **compile-time schema knowledge** gives a structural advantage over any runtime-generic parser. We know every field, every type, every variant at macro expansion time. A general-purpose parser handles arbitrary JSON. We don't have to.

## Why we're already winning (codec layer advantages)

These techniques are what make sanely-jsoniter faster than jsoniter-scala native, even when both use the same parser:

### 1. Direct constructor calls

```scala
// sanely-jsoniter: zero-overhead construction
new User(_f0, _f1, _f2, _f3, _f4)

// jsoniter-scala: intermediate boxing
mirror.fromProduct(new ArrayProduct(Array[Any](_f0, _f1, _f2, _f3, _f4)))
```

Eliminates two allocations (Array + ArrayProduct wrapper) and all primitive boxing per product decode. This was the single biggest win: **+19% reads, +2% writes** when introduced (P3.5).

### 2. Typed local variables

```scala
// sanely-jsoniter: unboxed primitives on JVM stack
var _name: String = null
var _age: Int = 0
var _active: Boolean = false

// jsoniter-scala: everything boxed in Any array
val fields = new Array[Any](fieldCount)
```

Primitives stay unboxed through the entire decode loop. No `Int → java.lang.Integer → Any → java.lang.Integer → Int` roundtrip.

### 3. Branchless product encoding

Every field is unconditionally written in a straight-line sequence:

```scala
out.writeNonEscapedAsciiKey("name")
out.writeVal(x.name)
out.writeNonEscapedAsciiKey("age")
out.writeVal(x.age)
// ...no conditionals, no per-field checks
```

No `transientEmpty` checks, no `transientDefault` checks, no per-field conditional branches. The branch predictor sees a perfectly predictable hot loop. This is why writes are +25% vs jsoniter-scala native.

### 4. Hash-based field dispatch with @switch

```scala
(in.charBufToHashCode(keyLen): @switch) match {
  case 0x1a2b3c4d => if (in.isCharBufEqualsTo(keyLen, "name")) _name = in.readString(null) else ...
  case 0x5e6f7a8b => if (in.isCharBufEqualsTo(keyLen, "age")) _age = in.readInt() else ...
}
```

Field name hashes pre-computed at compile time. `@switch` annotation generates JVM `tableswitch`/`lookupswitch` bytecode — O(1) dispatch, no String allocation, no hashCode computation on the read path (the charBuf hash is computed directly on the internal buffer).

### 5. Direct primitive I/O

```scala
// sanely-jsoniter: direct call, no codec dispatch
_age = in.readInt()
out.writeVal(x.age)

// generic approach: virtual dispatch through codec
_age = codecs(2).asInstanceOf[JsonValueCodec[Int]].decodeValue(in, 0)
codecs(2).asInstanceOf[JsonValueCodec[Int]].encodeValue(x.age, out)
```

~95% of fields in typical domain types are primitives, strings, or simple types. Direct calls avoid the virtual dispatch overhead.

### 6. Simpler field semantics

jsoniter-scala's `JsonCodecMaker.make` supports features we don't need:
- `transientDefault` / `transientEmpty` — skip fields matching defaults during write
- `transientNone` — skip None fields
- Configurable null handling per field
- Bitset-based required field tracking

We write every field unconditionally (circe's format). Fewer branches, simpler generated code, better JIT optimization.

## The architecture: port, keep, and rewrite

Three layers, three strategies:

1. **PORT from jsoniter-scala** (MIT licensed) — low-level parsing/writing primitives. Proven code, proven tests. We take correctness for free.
2. **KEEP from sanely-jsoniter** — the codec-layer macro techniques that already beat jsoniter-scala. These carry over unchanged.
3. **REWRITE for performance** — new optimizations only possible because we own BOTH the macro AND the parser. This is where we pull ahead.

### The unified component map

| Component | Strategy | Source | Lines | Read impact | Write impact | Why |
|---|---|---|---|---|---|---|
| **Integer read (Int/Long)** | PORT | jsoniter `readInt/readLong` | ~80 | — | — | Proven overflow detection, leading zero handling |
| **Float/Double read** | PORT | jsoniter Eisel-Lemire | ~200 | — | — | Hardest correctness problem; proven ~500-line implementation |
| **String read + UTF-8** | PORT | jsoniter `parseEncodedString` | ~150 | — | — | UTF-8 validation, escape sequences, surrogate pairs |
| **BigDecimal/BigInt read** | PORT | jsoniter | ~50 | — | — | Digit/scale limits for DoS prevention |
| **Boolean/null read** | FRESH | — | ~30 | — | — | Trivial: direct byte comparison |
| **Field dispatch (charBuf+hash)** | PORT | jsoniter | ~100 | — | — | `charBufToHashCode`, `isCharBufEqualsTo` |
| **Token navigation** | PORT+SIMPLIFY | jsoniter | ~100 | — | — | Strip streaming, keep `byte[]` path only |
| **skip()** | PORT | jsoniter | ~80 | — | — | Skip unknown fields: objects, arrays, strings, numbers |
| **Buffer management** | PORT+SIMPLIFY | jsoniter | ~100 | — | — | Strip `InputStream`, keep grow/pool logic |
| **Primitive write (Int/Long/...)** | PORT | jsoniter `writeVal` | ~200 | — | — | Shortest float/double representation is non-trivial |
| **String write + escaping** | PORT | jsoniter `writeEncodedString` | ~100 | — | — | UTF-8 encoding, surrogate pair handling |
| **Structure write (obj/arr)** | FRESH | — | ~50 | — | — | Trivial: `buf(pos) = '{'` |
| **Error reporting** | FRESH | — | ~80 | — | — | Our own format with byte offset |
| **Entry points** | FRESH | — | ~50 | — | — | `readFromArray`, `writeToString`, etc. |
| | | | | | | |
| **Direct constructor calls** | KEEP | sanely-jsoniter macro | 0 | **+19%** | +2% | Already have: `new P(_f0, _f1, ...)` vs `mirror.fromProduct` |
| **Typed local variables** | KEEP | sanely-jsoniter macro | 0 | **+5-10%** | — | Already have: unboxed primitives on stack |
| **Branchless product encoding** | KEEP | sanely-jsoniter macro | 0 | — | **+25%** | Already have: unconditional write-key, write-val sequence |
| **Hash-based @switch dispatch** | KEEP | sanely-jsoniter macro | 0 | **+5-10%** | — | Already have: compile-time hashes, JVM tableswitch |
| **Direct primitive I/O** | KEEP | sanely-jsoniter macro | 0 | **+3-5%** | +3-5% | Already have: `in.readInt()` not `codec.decodeValue()` |
| **Simpler field semantics** | KEEP | sanely-jsoniter macro | 0 | +2-3% | **+5-10%** | No transient/default checks (circe writes all fields) |
| | | | | | | |
| **Field-order prediction** | **REWRITE** | NEW | ~100 | **+5-15%** | — | Only with custom parser: skip hash for ordered fields |
| **Compile-time key bytes** | **REWRITE** | NEW | ~50 | — | **+5-10%** | Only with custom parser: pre-computed `"name":` byte arrays |
| **Fused key+value parsing** | **REWRITE** | NEW | incl. above | **+2-5%** | — | Only with custom parser: compare key bytes inline |
| **Eliminated virtual dispatch** | **REWRITE** | NEW (architectural) | 0 | **+2-5%** | +2-5% | No JsonReader/Writer class boundary for JIT to cross |
| **Pre-sized write buffers** | **REWRITE** | NEW | ~30 | — | **+2-5%** | Macro estimates output size, no grow-and-copy |
| **Inlined integer parsing** | **REWRITE** | NEW | ~80 | **+3-8%** | — | Macro generates readInt loop directly per field |

**Subtotals:**

| Strategy | Lines | Purpose |
|---|---|---|
| **PORT** from jsoniter | ~1,090 (~70%) | Correctness foundation — proven parsing/writing |
| **FRESH** code | ~210 (~14%) | Trivial components — structure, errors, entry points |
| **KEEP** from sanely-jsoniter | 0 (macro) | Existing codec-layer wins that already beat jsoniter |
| **REWRITE** for performance | ~260 (~16%) | New optimizations unlocked by owning both macro + parser |
| **Total** | ~1,560 | |

### Where each strategy comes from

**PORT** — jsoniter-scala is **MIT licensed** (Copyright 2017 Andriy Plokhotnyuk). We include the notice in source files. We port the ~1,100 lines of parsing/writing logic our macros call, strip the ~6,800 lines we don't need (streaming, pretty-printing, temporal types, UUID, base64, Scala 2 compat).

**KEEP** — The codec-layer techniques (direct constructors, typed locals, branchless encoding, hash dispatch, direct primitive I/O) are macro-generated and already proven in sanely-jsoniter benchmarks. They carry over unchanged because they're in the macro, not the parser.

**REWRITE** — The ~260 lines of new code are the performance moat. These optimizations are **only possible because we own both the macro AND the parser**:

### Why the REWRITE optimizations can't exist without a custom parser

**Field-order prediction** requires the parser to expose "check if the next key matches this known byte sequence" — a method that doesn't exist in jsoniter's `JsonReader` API. We can't do field-order prediction while calling `readKeyAsCharBuf()` + `charBufToHashCode()` because those always go through the hash path.

```scala
// With custom parser: O(1) ordered check, hash fallback for unordered
if (reader.isNextKey(KEY_NAME_BYTES)) { _name = reader.readString(null) }
else { fallbackToHashDispatch(reader) }
```

**Compile-time key bytes** requires the writer to accept pre-computed `Array[Byte]` for field names — `System.arraycopy(KEY_NAME, 0, buf, pos, 7)` instead of `writeNonEscapedAsciiKey("name")` which re-processes the string on every call. jsoniter's `writeNonEscapedAsciiKey` already skips escaping, but still copies char-by-char from a `String`.

```scala
// With custom writer: single arraycopy, compile-time pre-encoded
private val KEY_NAME: Array[Byte] = Array('"', 'n', 'a', 'm', 'e', '"', ':')
System.arraycopy(KEY_NAME, 0, buf, pos, 7); pos += 7
```

**Eliminated virtual dispatch** requires no `JsonReader`/`JsonWriter` class boundary at all. The parsing state (buffer, position, charBuf) can be local variables in the generated method, or a struct-like class that JIT can scalar-replace. With jsoniter, every `in.readInt()` is a virtual call on a class instance that JIT must prove monomorphic.

**Inlined integer parsing** requires the macro to generate the parsing loop inline per field. With jsoniter, `in.readInt()` calls a method on `JsonReader` — even if JIT eventually inlines it, that depends on warmup. Our macro can generate the 10-line digit loop directly in the codec.

### Performance win summary

Combining KEEP (existing) + REWRITE (new), the expected total improvement vs jsoniter-scala native:

| Operation | KEEP advantage (already proven) | REWRITE advantage (projected) | Combined |
|---|---|---|---|
| **Reading** | +3% (current benchmark) | +12-33% (field-order + fused key + inlined int + no dispatch) | **+15-36%** |
| **Writing** | +25% (current benchmark) | +7-15% (key bytes + pre-sized buf + no dispatch) | **+32-40%** |

These are estimates. The KEEP numbers are measured; the REWRITE numbers are projected from analysis of the hot path. Real benchmarks will validate.

## Scala.js: day-one cross-platform support

### How jsoniter-scala does it

jsoniter-scala has **separate implementations** for JVM and Scala.js (`CrossType.Full`):

| Platform | JsonReader | JsonWriter | Total |
|---|---|---|---|
| Scala.js | 4,493 lines | 3,696 lines | 8,189 lines |
| JVM-Native | 4,946 lines | 3,362 lines | 8,308 lines |

They do **NOT** use JavaScript's `JSON.parse()`. Both platforms do byte-level parsing with nearly identical logic. The differences are minimal:

- `Array[Int]` vs `Array[Byte]` for a magnitude field (platform-specific optimization)
- Float/Double formatting tolerances (JS allows slightly longer output for whole-number floats)
- `Long.MinValue` edge cases excluded on JS (scala-java-time library limitation)

### Our approach: shared implementation with minimal platform splits

Since the JVM and JS implementations are ~99% identical, we use a **shared source** strategy:

```
sanely-zero/
├── src/           # Shared: reader, writer, codecs, macros (95% of code)
├── src-jvm/       # JVM-only: Unsafe optimizations, if any
├── src-js/        # JS-only: platform stubs, if any
```

**What goes in shared (everything):**
- All parsing logic (readInt, readString, readDouble, etc.)
- All writing logic (writeVal, writeKey, etc.)
- Buffer management
- UTF-8 handling
- Field dispatch (charBuf, hash)
- All macro-generated code

**What might need platform splits (very little):**
- Float/Double shortest-representation formatting (minor tolerance differences)
- `Long.MinValue`/`Long.MaxValue` precision (JS number limitations)
- If we use `sun.misc.Unsafe` for SWAR tricks (JVM only, with safe fallback in shared)

### Why this works

1. **No `InputStream`/`OutputStream`** in phase 1 — `byte[]` and `String` work identically on both platforms
2. **No `Unsafe`** required for correctness — only for optional JVM-specific optimizations
3. **`java.lang.Double.parseDouble()`** works on Scala.js (transpiles to `parseFloat()` in JS)
4. **UTF-8 `Array[Byte]` handling** is identical on both platforms — Scala.js `Array[Byte]` is a real byte array
5. **`new String(bytes, UTF_8)`** works on both platforms

### The 3 known JS edge cases (from jsoniter-scala's test exclusions)

1. **`Long.MaxValue` / `Long.MinValue`** — JS numbers lose precision beyond 2^53. JSON numbers that exceed this range may not roundtrip perfectly. **Same issue our existing sanely library already handles** — we skip these tests with `Platform.isJS`.

2. **Float formatting** — JS allows 3 extra characters when formatting floats as whole numbers (e.g., `1.0E7` vs `1.0E7000`). Non-issue if we format consistently.

3. **Duration with negative nanos** — scala-java-time limitation. Not relevant for JSON number parsing.

## What we give up (revised honest assessment)

### 1. ~~Andriy's years of micro-optimization~~ → We port it

**No longer a concern.** MIT license means we take the proven code. The ~500 lines of Eisel-Lemire float/double parsing, the UTF-8 validation, the buffer management patterns — we port them directly and inherit their correctness.

### 2. Streaming support

jsoniter-scala supports `InputStream` reading. We start with `byte[]`/`String` only.

**Mitigation**: HTTP frameworks (http4s, Tapir, Netty) buffer the request body anyway. `byte[]` input covers >95% of real use cases. Add streaming later if needed.

### 3. JMH-level benchmark validation

Our current benchmarks use a custom measurement loop. For credible claims of "faster than jsoniter-scala," we'd want JMH benchmarks with proper warmup, blackholing, and allocation measurement.

**Mitigation**: Add a JMH benchmark module before any public claims.

## Phased implementation plan

All phases are **cross-platform from day one** (JVM + Scala.js). Shared source with minimal platform splits.

### Phase 1: Writer + reader core (port from jsoniter)

Port the proven primitives. This is translation, not invention.

Deliverables:
- `SanelyWriter`: buffer management, primitive writes (port `writeVal` overloads), string escaping (port `writeEncodedString`), structure tokens (`{`, `}`, `[`, `]`)
- `SanelyReader`: token navigation, primitive reads (port `readInt/Long/Float/Double/String/Boolean/BigDecimal/BigInt`), null handling, error reporting with byte offset
- Field dispatch infrastructure: `charBufToHashCode`, `isCharBufEqualsTo`, `readKeyAsCharBuf`
- `skip()` for unknown fields
- Entry points: `readFromArray`, `readFromString`, `writeToArray`, `writeToString`
- **Port jsoniter's test vectors**: all 370+ explicit edge cases + property-based generators
- Cross-platform: JVM + Scala.js, shared source
- Benchmark: measure against jsoniter-scala reader/writer

### Phase 2: Macro integration + products

Wire the macro to generate code targeting our reader/writer instead of jsoniter's.

Deliverables:
- Macro generates encode/decode bodies using `SanelyReader`/`SanelyWriter` API
- Direct primitive I/O, typed locals, direct constructor calls (all existing optimizations)
- Hash-based field dispatch using our `charBufToHashCode`
- Product roundtrip tests against circe (existing cross-codec suite)

### Phase 3: Containers + sum types + configured

Complete type coverage and configuration support.

Deliverables:
- Container codecs: List, Vector, Set, Option, Map, Array, Either
- Sum type dispatch with external tagging
- Sub-trait hierarchy support
- Discriminator handling (fast path + slow path)
- Configured derivation: `withDefaults`, `withDiscriminator`, `withSnakeCaseMemberNames`, `withDropNullValues`, `withStrictDecoding`
- Cross-codec tests against circe for all configurations

### Phase 4: Optimize (our unique advantages)

These are the optimizations only possible because we own the parser AND the macro:

Deliverables:
- Field-order prediction (skip hash dispatch for ordered fields)
- Compile-time key bytes (`"fieldName":` as pre-computed byte arrays)
- Pre-sized write buffers (macro estimates output size)
- JMH benchmarks for credible claims
- JSONTestSuite conformance (318 test files)
- Allocation profiling + optimization

## The competitive landscape

| Library | Parser ownership | Codec derivation | API compatibility | Zero-dep |
|---|---|---|---|---|
| **circe + jawn** | jawn (separate lib) | Macro (slow, implicit chains) | Native circe | No (jawn, cats) |
| **jsoniter-scala** | Custom (best-in-class) | Macro (good) | Own API | Yes |
| **jsoniter-scala-circe bridge** | jsoniter reader | circe codecs (slow) | circe API | No (jsoniter + circe) |
| **sanely-jsoniter** (today) | jsoniter reader/writer | Macro (best) | circe wire compat | No (jsoniter) |
| **sanely-zero** (proposed) | Custom (specialized) | Macro (best) | circe API + wire compat | **Yes** |

The proposed library ("sanely-zero" or whatever we name it) would be the only zero-dependency option with circe API compatibility AND competitive-with-jsoniter performance.

## The bottom line

**Can we beat jsoniter-scala?** On the codec layer, we already do. On the parser layer, we can match it for the common cases (ordered fields, ASCII keys, integer/string values) and potentially exceed it through compile-time specialization.

**The structural advantage is real**: compile-time schema knowledge allows optimizations that no runtime-generic parser can achieve. Field-order prediction, fused key parsing, compile-time key bytes, fully inlined primitive parsing — none of these are possible without knowing the schema at macro expansion time.

**The risk was correctness — but porting eliminates it.** By porting jsoniter-scala's proven parsing code (MIT licensed), we inherit years of battle-tested correctness for float/double precision, UTF-8 validation, and string escaping. We port the code and the tests together.

**The moat**: if we achieve this, no one else can easily replicate it. The combination of Scala 3 macros, `Expr.summonIgnoring`, single-pass expansion, typed locals, direct constructors, AND a custom parser is a high barrier. jsoniter-scala would need to adopt our macro techniques, and circe would need to abandon its `Json` tree.

## Correctness: learning from jsoniter-scala's test suite

jsoniter-scala's correctness is backed by **628+ explicit test cases** and **millions of property-based iterations** across ~46,000 lines of test code. If we build a custom parser, we must match this rigor. Here's exactly what they test and how we adopt it.

### jsoniter-scala's testing strategy

| Test file | Cases | Lines | What it covers |
|---|---|---|---|
| `JsonReaderSpec.scala` | 230 | 3,404 | Parser correctness: every read method, every type, every error |
| `JsonWriterSpec.scala` | 79 | 955 | Writer correctness: every write method, escaping, formatting |
| `PackageSpec.scala` | ~30 | 724 | Entry points: stream/array/buffer/string I/O, buffer resizing |
| `JsonCodecMakerSpec.scala` | 272 | 3,472 | Codec derivation: types, configs, edge cases |
| `JsonCodecMakerNewEnumSpec.scala` | 14 | 9,808 | Scala 3 enums |
| `GenUtils.scala` | — | ~100 | ScalaCheck generators for property-based testing |

Frameworks: **ScalaTest** (AnyWordSpec) + **ScalaCheck** (property-based, `minSuccessful(10000)` per property).

### Test categories we must replicate

#### 1. Integer parsing (readInt, readLong) — easy, high confidence

**What jsoniter tests:**
- Property-based: 10,000 random Int/Long values with random whitespace
- Boundary: `Int.MaxValue` (2147483647), `Int.MinValue` (-2147483648), `Long.MaxValue`, `Long.MinValue`
- Overflow: `2147483648` → error, `-2147483649` → error, 20+ digit numbers
- Leading zeros: `00`, `-00`, `0123456789` → "illegal number with leading zero"
- Decimal/exponent rejection: `123456789.0`, `123456789e10` → "illegal number" (for integer read)
- Illegal input: empty, `-` only, non-digit character
- Leading zeros with keys and stringified values: 1,000 cases with format `%011d`

**Our approach:** Port these tests directly. Integer parsing is well-understood — the main risk is overflow detection. Write a property-based test that parses with our reader and compares with `java.lang.Integer.parseInt` / `Long.parseLong`.

**Estimated test count: ~40 cases + 10,000 property iterations per type**

#### 2. Floating-point parsing (readFloat, readDouble) — hard, highest risk

This is the #1 correctness concern. jsoniter-scala has 45+ explicit precision edge cases for the Eisel-Lemire algorithm.

**What jsoniter tests:**

*Precision edge cases (explicitly enumerated):*
- **Float round-down halfway**: `16777217.0`, `33554434.0`, `17179870208.0`
- **Float round-up halfway**: `16777219.0`, `33554438.0`, `17179872256.0`
- **Float above-halfway**: `33554435.0`, `17179870209.0`
- **Float fast-path**: `37930954282500097`, `48696272630054913`
- **Float exactly-halfway**: `1.00000017881393432617187499`, `1.000000178813934326171875`, `1.00000017881393432617187501`
- **Float 2^n-1 regression**: `36028797018963967.0`
- **Float subnormals**: `1.17549435E-38` through `1.17549428E-38` (near `Float.MIN_NORMAL`)
- **Double round-down halfway**: `9007199254740993.0`, `18014398509481986.0`, `9223372036854776832.0`
- **Double round-up halfway**: `9007199254740995.0`, `18014398509481990.0`, `9223372036854778880.0`
- **Double subnormals**: `2.2250738585072014E-308` through `2.2250738585072011E-308`
- **Double large integer regression**: `11224326888185522059941158352151320185835795563643008`

*Property-based (10,000 per generator):*
- All Float values via `arbitrary[Float]`
- All Double→Float conversions via `arbitrary[Double]`
- All int-bits-to-float finite values via `arbitrary[Int]` + `Float.intBitsToFloat`
- All long-bits-to-double finite values via `arbitrary[Long]` + `Double.longBitsToDouble`
- Mantissa range [0, 2^32) with exponent [-22, 18] (Float)
- Mantissa range [0, 2^53) with exponent [-22, 37] (Double)
- Generated BigInt and BigDecimal values

*Overflow/underflow:*
- `12345e6789` → `Float.PositiveInfinity` / `Double.PositiveInfinity`
- `-12345e6789` → negative infinity
- `12345e-6789` → `0.0f` / `0.0`
- Extreme exponents: `123456789012345678901234567890e9223372036854775799`

*Denormalized number parsing:*
- `1` + 1,000,000 zeros + `e-1000000` → `1.0`
- `0.` + 1,000,000 zeros + `1e1000000` → `0.1`

*Sign handling:*
- Positive zero vs negative zero distinguished via `.equals()`

*Leading zeros in mantissa/exponent:*
- 1-7 leading zeros with property-based testing (1,000 cases)

**Our approach:** Port jsoniter-scala's Eisel-Lemire implementation directly (MIT licensed, ~500 lines). This is proven code with all edge cases handled — subnormals, rounding boundaries, denormalized numbers. We port the code AND the tests together: the 45+ explicit precision edge cases are regression tests for the exact Eisel-Lemire boundaries. No need for a phased correctness approach — we start with the proven fast implementation.

**Estimated test count: 60+ explicit cases + 70,000 property iterations (all ported from jsoniter)**

#### 3. String parsing + UTF-8 — medium difficulty, medium risk

**What jsoniter tests:**

*Property-based (10,000+ per generator):*
- All non-surrogate, non-escape Unicode chars
- Valid surrogate pairs (high + low)
- Escaped ASCII chars (control chars + `\` + `"`)

*Escape sequences (all 8 standard + unicode):*
- `\"`, `\\`, `\/`, `\b`, `\f`, `\n`, `\r`, `\t`
- `\uXXXX` with valid BMP chars
- `\uD834\uDC34` — surrogate pair in `\uXXXX` form

*Malformed escape sequences:*
- Incomplete: `\u`, `\u0`, `\u00`, `\u000`
- Invalid hex digits in `\uXXXX`
- `\uD834\uD834` — two high surrogates → "illegal surrogate character pair"
- `\uD834` alone (high without low) → error
- `\uD834\x` — high surrogate followed by non-`\u` → error

*Malformed UTF-8 byte sequences:*
- `0x80` — continuation byte without lead byte
- `0xC0, 0x80` — overlong encoding for ASCII
- `0xC8, 0x08` — invalid continuation byte
- `0xE0, 0x80, 0x80` — overlong 3-byte encoding
- `0xED, 0xA0, 0x80` — UTF-16 surrogate encoded as UTF-8 (illegal)
- `0xF0, 0x80, 0x80, 0x80` — overlong 4-byte encoding
- Various invalid continuation byte patterns across 2/3/4-byte sequences

*Control characters:*
- Unescaped 0x00-0x1F in strings → "unescaped control character"
- Property-based with `genControlChar` (100+ cases)

*String length limits:*
- Beyond `maxCharBufSize` → error

**Our approach:** String parsing is well-understood. The key insight: we handle **two separate problems**:

1. **UTF-8 → chars**: `new String(buf, off, len, UTF_8)` handles this correctly for the fast path (no escapes). Only when we see `\` do we need custom handling.
2. **Escape sequences**: ~30 lines of switch/case. The tricky part is surrogate pair validation in `\uXXXX` sequences.

Adopt jsoniter's malformed UTF-8 test vectors byte-for-byte. These are the exact byte patterns that catch overlong encodings, surrogate-in-UTF-8, and invalid continuations.

**Estimated test count: 30+ explicit cases + 30,000 property iterations**

#### 4. Writer string escaping — easy, low risk

**What jsoniter tests:**
- Non-escaped chars (property-based, 10,000)
- All escape sequences (property-based with `genEscapedAsciiChar`)
- Surrogate pair writing: valid pairs, invalid pairs (error detection)
- `escapeUnicode` config: non-ASCII → `\uXXXX`
- Mixed content: controls + Unicode + Latin-1

**Our approach:** The writer escaping is simpler than parsing. Standard JSON escaping rules. Adopt jsoniter's surrogate pair validation tests.

**Estimated test count: 15+ explicit cases + 20,000 property iterations**

#### 5. BigDecimal / BigInt — medium, delegate to stdlib

**What jsoniter tests:**
- Property-based: 10,000 arbitrary BigInt/BigDecimal
- Large numbers: 1,000-digit numbers
- Digit limit enforcement: default 308 digits (configurable)
- Scale limit enforcement: default 6178 (configurable)
- Exponent overflow: `1e2147483648` → error (exponent exceeds Int range)
- MathContext preservation: DECIMAL128, UNLIMITED, etc.
- Leading zeros: don't count against digit limit

**Our approach:** Parse the number as a string, delegate to `new BigDecimal(s)` / `new BigInt(s)`. Add configurable limits (digit count, scale) to prevent DoS via huge numbers. Jsoniter's limit tests are the security-critical ones — adopt them all.

**Estimated test count: 20+ explicit cases + 10,000 property iterations**

#### 6. Boolean / null — trivial

**What jsoniter tests:** `true`, `false`, `null`, error on truncated (`tru`, `fals`, `nul`), error on non-matching (`True`, `TRUE`).

**Our approach:** Direct byte comparison. ~10 lines of code, ~10 test cases.

#### 7. Structural tokens (objects, arrays, nesting) — easy

**What jsoniter tests via `PackageSpec.scala`:**
- Buffer resizing: reader initialized with 12-32 byte buffers, forcing growth during parse
- Stream reading with varying buffer sizes (1-99 bytes at a time)
- Nested objects/arrays to test stack depth
- `skip()` on all value types: strings, numbers, booleans, null, objects, arrays

**Our approach:** Phase 1 works on `byte[]` only (no streaming), so buffer resizing is not needed. Nesting depth and `skip()` tests are important.

**Estimated test count: 20+ cases**

#### 8. Error messages with offset — important for debugging

**What jsoniter tests:** Every error includes byte offset in hex format (`0x00000009`). Tests verify exact offset positions for every error type.

**Our approach:** Track byte position in reader. Include offset in all error messages. Don't need hex format — just need the offset to be accurate for debugging.

### The JSONTestSuite conformance suite

jsoniter-scala does **not** include the [JSONTestSuite](https://github.com/nst/JSONTestSuite) (Nicolas Seriot's comprehensive JSON parser test suite). This is a gap we can fill.

JSONTestSuite contains 318 test files:
- `y_*.json` — must be accepted (valid JSON)
- `n_*.json` — must be rejected (invalid JSON)
- `i_*.json` — implementation-defined (either accept or reject)

Categories: structure, numbers, strings, Unicode, nesting depth, trailing content, whitespace.

**We should run JSONTestSuite as part of our CI.** This gives us:
1. Conformance validation beyond what jsoniter tests
2. A marketing story: "passes JSONTestSuite conformance"
3. Edge cases neither we nor jsoniter might think of

### Our testing strategy (synthesis)

#### Layer 1: Exact port of jsoniter-scala's edge cases

Port every explicit test value from `JsonReaderSpec.scala` and `JsonWriterSpec.scala`. These are ~370 carefully chosen edge cases accumulated over years of real-world usage and bug reports. They represent the distilled knowledge of what actually goes wrong in JSON parsers.

Key test vectors to port:
- 45+ float/double precision edge cases (Eisel-Lemire boundaries)
- 15+ malformed UTF-8 byte sequences
- 10+ malformed escape sequences
- 10+ integer overflow cases
- 10+ BigDecimal scale/digit limit cases
- All subnormal/denormalized number cases

#### Layer 2: Property-based testing (ScalaCheck)

Same generators as jsoniter-scala (`GenUtils.scala`), same iteration counts:

| Generator | Iterations | What it validates |
|---|---|---|
| `arbitrary[Int]` | 10,000 | All integers roundtrip correctly |
| `arbitrary[Long]` | 10,000 | All longs roundtrip correctly |
| `arbitrary[Float]` | 10,000 | All floats roundtrip correctly |
| `arbitrary[Double]` | 10,000 | All doubles roundtrip correctly |
| `arbitrary[Int] → intBitsToFloat` | 10,000 | All float bit patterns roundtrip |
| `arbitrary[Long] → longBitsToDouble` | 10,000 | All double bit patterns roundtrip |
| `arbitrary[String]` (filtered) | 10,000 | All valid strings roundtrip |
| `genHighSurrogate + genLowSurrogate` | 10,000 | All surrogate pairs roundtrip |
| `arbitrary[BigInt]` | 10,000 | All big integers roundtrip |
| `arbitrary[BigDecimal]` | 10,000 | All big decimals roundtrip |

#### Layer 3: JSONTestSuite conformance

Run all 318 test files. Verify:
- All `y_*.json` files parse without error
- All `n_*.json` files produce parse errors
- Document our stance on `i_*.json` files

#### Layer 4: Cross-codec compatibility (already have this)

Our existing test strategy: encode with our library → decode with circe, and vice versa. This validates wire format compatibility, which is the contract that matters most.

#### Layer 5: Roundtrip fuzzing (new)

Use ScalaCheck or a fuzzer to generate random JSON strings and verify:
1. If it parses, re-serializing produces equivalent JSON
2. If it doesn't parse, the error message includes a valid byte offset
3. No uncaught exceptions, no infinite loops, no OOM

This catches edge cases that hand-written tests and property tests miss — malformed inputs that happen to trigger unexpected code paths.

### Test count estimate

| Category | Explicit | Property-based | Total effort |
|---|---|---|---|
| Integer parsing (Int, Long, Byte, Short) | ~40 | 40,000 | Low |
| Float/Double parsing | ~60 | 70,000 | High (most important) |
| String parsing + UTF-8 | ~30 | 30,000 | Medium |
| Writer escaping | ~15 | 20,000 | Low |
| BigDecimal / BigInt | ~20 | 20,000 | Low |
| Boolean / null | ~10 | 0 | Trivial |
| Structural (obj/arr/nesting) | ~20 | 0 | Low |
| Error messages | ~20 | 0 | Low |
| JSONTestSuite | 318 | 0 | Low (just run them) |
| Cross-codec roundtrip | ~50 | 10,000 | Already exists |
| **Total** | **~583** | **~190,000** | |

This matches jsoniter-scala's testing rigor while adding JSONTestSuite conformance and roundtrip fuzzing that jsoniter doesn't do.
