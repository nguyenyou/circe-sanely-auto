# sanely-zero: JSON ecosystem performance research

Deep-dive research into every JSON library — Scala and beyond — that has a technique worth stealing. Each technique is assessed for applicability to sanely-zero given our constraints: Scala 3.8.2 only, cross-platform JVM + Scala.js, zero dependencies, own macro + parser.

---

## Table of contents

- [Part 1: jsoniter-scala — the baseline to beat](#part-1-jsoniter-scala--the-baseline-to-beat)
- [Part 2: zio-blocks — Plokhotnyuk's latest thinking](#part-2-zio-blocks--plokhotnyuks-latest-thinking)
- [Part 3: zio-json — StringMatrix and streaming design](#part-3-zio-json--stringmatrix-and-streaming-design)
- [Part 4: borer — SWAR origins and speculative writes](#part-4-borer--swar-origins-and-speculative-writes)
- [Part 5: upickle / weePickle — visitor pattern and Scala.js native](#part-5-upickle--weepickle--visitor-pattern-and-scalajs-native)
- [Part 6: kotlinx.serialization — golden mask and sequential fast path](#part-6-kotlinxserialization--golden-mask-and-sequential-fast-path)
- [Part 7: simdjson / simdjson-java — structural indexing](#part-7-simdjson--simdjson-java--structural-indexing)
- [Part 8: sonic-rs — SWAR skipping and direct-to-struct](#part-8-sonic-rs--swar-skipping-and-direct-to-struct)
- [Part 9: DSL-JSON — byte-level field matching](#part-9-dsl-json--byte-level-field-matching)
- [Part 10: FastDoubleParser — number parsing state of the art](#part-10-fastdoubleparser--number-parsing-state-of-the-art)
- [Part 11: Jackson 2.18-2.19 — mature library lessons](#part-11-jackson-218-219--mature-library-lessons)
- [Part 12: Other libraries (tethys, smithy4s, Moshi)](#part-12-other-libraries-tethys-smithy4s-moshi)
- [Synthesis: technique ranking for sanely-zero](#synthesis-technique-ranking-for-sanely-zero)

---

## Part 1: jsoniter-scala — the baseline to beat

**Repository**: https://github.com/plokhotnyuk/jsoniter-scala
**License**: MIT
**Codebase**: ~16K lines (reader + writer, JVM + JS)

jsoniter-scala is the fastest JSON library for Scala/JVM. Its performance comes from compounding many techniques, not a single silver bullet.

### 1.1 Architecture: no intermediate AST

The foundational decision. circe parses JSON → `Json` AST → domain type (two passes, AST allocation). jsoniter-scala parses UTF-8 bytes → domain type directly (one pass, zero AST). This alone accounts for **2-5x** throughput advantage over AST-based libraries.

### 1.2 SWAR (SIMD Within A Register)

Introduced in PRs #866 (serialization) and #876 (parsing). Processes 4-8 bytes simultaneously using 64-bit integer arithmetic.

**String parsing** (`JsonReader.scala:3961-3987`):
```scala
val bs = ByteArrayAccess.getInt(buf, pos)
val m = ((bs - 0x20202020 ^ 0x3C3C3C3C) - 0x1010101 | (bs ^ 0x5D5D5D5D) + 0x1010101) & 0x80808080
```
Checks 4 bytes simultaneously for control chars, `"`, and `\`. If `m == 0`, all 4 are plain ASCII — bulk copy. Based on borer's technique (see Part 4).

**Number digit validation** (`JsonReader.scala:2140`):
```scala
val bs = ByteArrayAccess.getLong(buf, pos)
dec = bs - 0x3030303030303030L
((bs + 0x4646464646464646L | dec) & 0x8080808080808080L) == 0
```
Validates 8 bytes as digits simultaneously. Based on simdjson/FastDoubleParser.

**8-digit integer parsing** (`JsonReader.scala:2143`):
```scala
dec *= 2561
x *= 100000000
x -= ((dec >> 8 & 0xFF000000FFL) * 4294967296000100L + (dec >> 24 & 0xFF000000FFL) * 42949672960001L >> 32)
```
Converts 8 validated digit bytes to an integer in a single expression via magic multiplier arithmetic.

**Impact**: Up to 2x for string-heavy and number-heavy payloads on JVM.

**Applicability**: JVM-only (requires ByteArrayAccess via VarHandle). We use platform split: SWAR on JVM, byte-by-byte on Scala.js. This is our main parser-layer disadvantage on JS — but our codec-layer advantages compensate.

### 1.3 Float/double parsing: rust-lexical moderate path

Three-tier approach (`JsonReader.scala:2184-2333`):

1. **Fast path** (line 2280): mantissa < 2^52, moderate exponent → direct `pow10Doubles` table lookup + multiply. Handles ~80% of real-world doubles.
2. **Moderate path** (line 2295): based on Alexander Huszagh's `rust-lexical`. 128-bit multiplication via `Math.multiplyHigh` with precomputed `pow10Mantissas` table (686 Long entries for e10 range [-343, +343]).
3. **Slow path** (line 2330): falls back to `java.lang.Double.parseDouble` for values extremely close to halfway points. Rare.

**Performance**: 3.5x faster than Jackson+FastDoubleParser for double arrays (380K vs 108K ops/s, issue #672).

**Applicability**: Port directly. Pure arithmetic, cross-platform. `Math.multiplyHigh` available on JVM (Java 9+); on Scala.js, implement via two 64-bit multiplications.

### 1.4 Float/double writing: xjb algorithm

`JsonWriter.scala:2239-2435`. Based on Xiang JunBo and Wang TieJun's paper ("xjb: Fast Float to String Algorithm"). Replaced Schubfach in v2.38.6.

Produces shortest decimal representation that round-trips to the same binary value. Uses precomputed `floatPow10s` (77 entries) and `doublePow10s` (1170 entries) with `Math.multiplyHigh` for 128-bit multiplication.

**Applicability**: Port directly. Pure arithmetic, ~200 lines.

### 1.5 Integer writing: James Anhalt's algorithm

`JsonWriter.scala:2116-2129`. Writes 8 digits without division:

```scala
val y1 = x * 140737489L           // magic multiplier
val y2 = (y1 & 0x7FFFFFFFFFFFL) * 100L
val y3 = (y2 & 0x7FFFFFFFFFFFL) * 100L
val y4 = (y3 & 0x7FFFFFFFFFFFL) * 100L
// extract 2-digit pairs, look up in digits table, pack into one Long
ByteArrayAccess.setLong(buf, pos, d1 | d2 | d3 | d4)
```

Precomputed `digits` table (100 entries of 2-byte pairs: "00"..."99"). All division replaced by multiplication + shift.

**Applicability**: Port directly. Replace `setLong` with byte-by-byte on Scala.js.

### 1.6 Digit count: Daniel Lemire's branchless trick

`JsonWriter.scala:2446-2448`:
```scala
private[this] def digitCount(x: Long) = (offsets(java.lang.Long.numberOfLeadingZeros(x)) + x >> 58).toInt
```
Branchless O(1) digit count via 64-entry lookup table. No loops, no conditionals.

**Applicability**: Port directly. `Long.numberOfLeadingZeros` is cross-platform.

### 1.7 ByteArrayAccess: VarHandle multi-byte I/O

`ByteArrayAccess.java:8-45`. Four VarHandle instances for unaligned little-endian and big-endian access:

```java
private static final VarHandle VH_LONG =
    MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
```

JIT compiles to single MOV instructions on x86. Foundation for all SWAR techniques.

**Applicability**: JVM-only. Platform split: `src-jvm/ByteArrayAccess.scala` with VarHandle, shared `src/` with byte-by-byte fallback.

### 1.8 ThreadLocal reader/writer pooling

```scala
private[this] final val readerPool: ThreadLocal[JsonReader] = new ThreadLocal[JsonReader] {
  override def initialValue(): JsonReader = new JsonReader
}
```

Each reader carries 32KB byte buffer + 4KB char buffer that survive between calls. Eliminates ~32KB allocation per parse call.

**Applicability**: Same pattern. On Scala.js, ThreadLocal is effectively a global (single-threaded).

### 1.9 Hash-based field dispatch with @switch

`JsonCodecMaker.scala:2250-2258`. The macro generates:

1. `in.readKeyAsCharBuf()` → reads key into char buffer (zero String allocation)
2. `in.charBufToHashCode(len)` → hash via `(h << 5) + (c - h)` formula
3. `(hash: @switch) match { ... }` → JVM `tableswitch`/`lookupswitch`
4. `in.isCharBufEqualsTo(len, "fieldName")` → verify on hash collision

For ≤8 fields with total name length ≤64: linear comparison chain instead.

**Applicability**: Already implemented in sanely-jsoniter. Port to our reader.

### 1.10 Compile-time key classification

```scala
private def isEncodingRequired(s: String): Boolean = {
  var i = 0
  while (i < len && JsonWriter.isNonEscapedAscii(s.charAt(i))) i += 1
  i != len
}
```

At macro time, decides whether a field name needs Unicode escaping. Most names are ASCII → fast path `writeNonEscapedAsciiKey`.

**Applicability**: Already implemented. We go further with compile-time key byte arrays.

### 1.11 JIT-friendly coding patterns

- `final class` for reader/writer → devirtualization
- Local `val buf = this.buf` aliases → helps JIT prove no aliasing
- Sign via `x ^= s; x -= s` instead of `if (neg) -x` → branchless
- Division by constant via magic multiplier: `Math.multiplyHigh(x, magic) >>> shift`
- `@tailrec` for string processing → tight loops

**Applicability**: Pure coding discipline. Apply everywhere.

### 1.12 Security features

- `requireDiscriminatorFirst=true` prevents DoS via discriminator at end of large objects
- Configurable digit/scale limits for BigDecimal/BigInt
- Configurable max size for maps, sets, BitSets

**Applicability**: Implement same limits.

### 1.13 BigInt/BigDecimal: O(n^1.5) parsing

Based on `big-math` library. Converts large digit strings in O(n^1.5) instead of Java's O(n^2).

**Applicability**: Implement if profiling shows need. Most JSON numbers have few digits.

### 1.14 Scala.js differences

Separate implementations: JVM 8,308 lines vs JS 8,189 lines. Key differences:
- No ByteArrayAccess/SWAR on JS (byte-by-byte)
- `Array[Int]` vs `Array[Byte]` for BigInt magnitude
- Same ThreadLocal pattern (no-op on single-threaded JS)

---

## Part 2: zio-blocks — Plokhotnyuk's latest thinking

**Repository**: https://github.com/zio/zio-blocks
**Key fact**: The JSON reader/writer was written by Andriy Plokhotnyuk (jsoniter-scala author, commit `f878897b`). These techniques represent his latest thinking beyond jsoniter-scala.

### 2.1 Speculative sequential field access

The most important technique to steal. In `JsonBinaryCodecDeriver` (line 606-616):

```scala
idx += 1
if (idx == len) idx = 0
fieldInfo = fieldInfos(idx)
(fieldInfo.nameMatch(in, keyLen) || {
  fieldInfo = fieldIndexMap.get(in, keyLen)
  ...
})
```

**How it works**: Maintains a field index counter. After reading field N, speculatively checks if the next key matches field N+1 (declaration order). If yes → O(1) memcmp, no hash computation. If no → fall back to `StringMap` hash lookup.

**Why it's fast**: Machine-generated JSON (APIs, databases, serialization frameworks) almost always emits fields in declaration order. The speculative check succeeds ~90%+ of the time in practice, making the hash dispatch path cold.

**How it differs from jsoniter-scala**: jsoniter-scala always hashes every key via `charBufToHashCode`. It cannot speculate because the macro and parser are separate — the macro doesn't know what order the parser will encounter fields. zio-blocks can speculate because the codec derivation controls both the field ordering and the parsing strategy.

**Applicability**: **P0**. This is exactly our "field-order prediction" from ANALYSIS.md, already proven in production. We implement `reader.isNextKey(precomputedKeyBytes)` that does the memcmp inline.

### 2.2 Register-based unboxed storage

`Registers` class uses two arrays:
- `bytes: Array[Byte]` for all primitives (Int, Long, Float, Double, Boolean, Byte, Char, Short) stored natively via ByteArrayAccess
- `objects: Array[AnyRef]` for reference types only

A `RegisterOffset` encodes both byte-offset and object-offset in a single `Long`.

**Why it matters**: During decode, primitives go from JSON bytes → `bytes` array → constructor call. They are never boxed as `java.lang.Integer` etc. Even our "typed local variables" approach (`var _age: Int = 0`) goes through the JVM stack, which the JIT may or may not optimize. The register approach guarantees no boxing at the language level.

**Applicability**: **P2**. Our macro already generates typed locals, which achieves most of the benefit. The register approach is more complex to generate in macros. Worth investigating if profiling shows boxing overhead.

### 2.3 Bitset missing field tracking

Uses two `Long` bitmasks (`missing1`, `missing2`) supporting up to 128 fields:

```scala
var missing1: Long = -1L  // all bits set = all fields missing
var missing2: Long = -1L
// per field: missing1 &= ~(1L << fieldIndex)  // clear bit when field is seen
// final: fill defaults for remaining set bits using numberOfTrailingZeros iteration
```

**Applicability**: **P0**. Adopt for strictDecoding and default-field handling. Replaces N boolean checks with 1-2 integer operations. Combined with the golden mask pattern (see Part 6).

### 2.4 StringMap — open-addressing hash table on charBuf

Custom open-addressing hash map that operates directly on the `charBuf` of the JsonReader. Field names are never materialized as `String` objects during lookup. Uses `charBufToHashCode` and `isCharBufEqualsTo` to match against pre-stored keys.

**vs jsoniter-scala's @switch dispatch**: StringMap uses open-addressing probing (cache-friendly linear scan on collision) vs @switch jump table. For types with hash collisions, StringMap may be faster because it avoids the nested `if/isCharBufEqualsTo` chains.

**Applicability**: **P2**. Consider as an alternative to @switch dispatch for types with many fields or hash-collision-prone names.

### 2.5 Predefined codec fast-path

For primitives, checks `isPredefinedCodec` and calls `in.readInt()` / `in.readLong()` directly instead of going through codec virtual dispatch:

```scala
(valueType: @switch) match {
  case 1 => regs.setInt(offset, in.readInt())
  case 2 => regs.setLong(offset, in.readLong())
  case 3 => regs.setFloat(offset, in.readFloat())
  // ...
}
```

**Applicability**: Already do this — our macro generates direct `readInt()` calls, no codec dispatch.

---

## Part 3: zio-json — StringMatrix and streaming design

**Repository**: https://github.com/zio/zio-json
**Performance**: ~2x faster than circe, slower than jsoniter-scala

### 3.1 StringMatrix — bitset trie for field matching

The crown jewel. A pre-computed matrix of expected field names that uses a 64-bit `Long` as a bitset of candidate matches:

```
Input:  {"n a m e": ...}
         ^
Matrix tracks: which of the N expected field names still match at position i?

candidates = 0b1111  (all 4 fields still possible)
read 'n' → candidates &= matrix['n'][0] → 0b0101  (fields 0,2 start with 'n')
read 'a' → candidates &= matrix['a'][1] → 0b0001  (only field 0 matches "na...")
read 'm' → candidates &= matrix['m'][2] → 0b0001  (still field 0)
read 'e' → candidates &= matrix['e'][3] → 0b0001  (confirmed: field 0 = "name")
```

**How it works**:
- Pre-build a matrix: for each character position and each possible byte value, store a bitmask of fields that have that character at that position
- As characters arrive, AND the current candidates with the matrix entry
- When only one candidate remains, that's the match
- Supports up to 64 fields (limited by Long width)

**Dense path**: When all candidates are alive, checks all candidates (early characters, many possibilities)
**Sparse path**: When some eliminated, uses `numberOfTrailingZeros` to iterate only survivors

**vs hash-based dispatch**: StringMatrix is a progressive filter — it can reject non-matching fields character-by-character during key reading, potentially short-circuiting before the full key is read. Hash-based dispatch must read the full key before computing the hash.

**Applicability**: **P2**. Interesting alternative to hash dispatch for types with many fields or long field names. For short keys (< 8 chars), hash is simpler and likely faster. For types with 20+ fields, StringMatrix may win by narrowing candidates early. Worth benchmarking against our hash dispatch.

### 3.2 Direct number parsing

`UnsafeNumbers` utilities parse numbers directly from the input stream without intermediate string conversion:

```scala
def int_(trace: List[JsonError], in: RetractReader): Int = {
  // reads digit-by-digit from Reader, accumulates directly
}
```

**Applicability**: Standard technique. We do the same via ported jsoniter-scala number parsing.

### 3.3 Streaming string reader

`streamingString` returns a `java.io.Reader` enabling consumers to process string content without materializing the full string. Critical for embedded JSON documents or very long strings.

**Applicability**: **P3**. Niche but useful for large string values. Not needed in phase 1.

### 3.4 Pre-computed literal arrays

Constants like `"ull"`, `"alse"`, `"rue"` cached as `Array[Char]` to avoid string allocation when validating keywords.

**Applicability**: Trivial to implement. Minor but free optimization.

### 3.5 @switch on character types

Pattern matches on character values generate JVM jump tables instead of sequential comparisons.

**Applicability**: Already used in jsoniter-scala and our macros.

---

## Part 4: borer — SWAR origins and speculative writes

**Repository**: https://github.com/sirthias/borer
**Author**: Mathias Doenitz (creator of spray-json)
**Performance**: ~43% slower than jsoniter-scala in benchmarks

### 4.1 SWAR string parsing (origin of jsoniter-scala's technique)

jsoniter-scala's README credits borer: "A bunch of SWAR technique tricks for JVM platform are based on borer — the fast parsing of JSON strings by 8-byte words."

The original technique (`parseUtf8String`):

```scala
val octa = input.readOctaByteBigEndianPadded()
val qMask = (octa ^ 0x5D5D5D5D5D5D5D5DL) + 0x0101010101010101L
val bMask = (octa ^ 0x2323232323232323L) + 0x0101010101010101L
val cMask = (octa | 0x1F1F1F1F1F1F1F1FL) - 0x2020202020202020L
val mask = (qMask | bMask | octa | cMask) & 0x8080808080808080L
val nlz = JLong.numberOfLeadingZeros(mask)
val charCount = nlz >> 3  // 0..8 valid chars
```

Processes 8 bytes at once. Uses `numberOfLeadingZeros` to find how many consecutive good bytes exist.

**Applicability**: Port the 8-byte version (jsoniter-scala uses 4-byte `getInt`). We may get better throughput with 8-byte words.

### 4.2 Speculative write pattern

The key borer innovation beyond what jsoniter-scala does:

```scala
// Write ALL 8 characters BEFORE knowing how many are valid
ensureCharsLen(charCursor + 8)
chars(charCursor + 0) = (octa >>> 56).toChar
chars(charCursor + 1) = (y >>> 48).toChar
chars(charCursor + 2) = (y >>> 40).toChar
// ... all 8 written unconditionally
// THEN check how many were valid
charCursor += charCount  // advance by actual valid count
```

**Why it's fast**: Eliminates instruction dependencies on superscalar CPUs. The branch predictor doesn't need to resolve before writes begin. Extra bytes are overwritten on the next iteration. The CPU pipeline stays fully utilized.

This yielded ~20% throughput improvement for number parsing after issue #114 identified IMUL latency bottlenecks.

**Applicability**: **P3**. Micro-optimization for our string parsing. Easy to implement, minor but measurable gain.

### 4.3 longFrom8Digits — SWAR number conversion

Converts 8 ASCII digits to a Long in a single expression:

```scala
private def longFrom8Digits(oct: Long) =
  var x = oct * 266
  x = (x >> 8) & 0x00FF00FF00FF00FFL
  x = x * 65636
  x = (x >> 16) & 0x0000FFFF0000FFFFL
  x = x * 4294977296L
  x >> 32
```

**Applicability**: Port alongside SWAR digit validation. Complements jsoniter-scala's 8-digit technique.

### 4.4 CBOR/JSON dual encoding

Single codec works for both CBOR and JSON via format-agnostic `Receiver` abstraction. Interesting design but adds abstraction overhead. Not applicable — we're JSON-only.

### 4.5 ArrayBasedCodecs

Positional arrays `[val1, val2, val3]` instead of named maps `{"f1":val1,"f2":val2}`. Fastest wire format but breaks circe compatibility.

**Applicability**: Not applicable (circe contract requires named fields).

---

## Part 5: upickle / weePickle — visitor pattern and Scala.js native

**Repository**: https://github.com/com-lihaoyi/upickle (upickle), https://github.com/rallyhealth/weePickle
**Performance**: ~50% slower than jsoniter-scala

### 5.1 Visitor pattern — zero-overhead tree processing

upickle's core innovation (documented in Li Haoyi's blog post "Zero-Overhead Tree Processing with the Visitor Pattern"):

```scala
trait Visitor[T, J] {
  def visitObject(length: Int): ObjVisitor[T, J]
  def visitArray(length: Int): ArrVisitor[T, J]
  def visitString(s: CharSequence): J
  def visitFloat64(d: Double): J
  // ...
}
```

A `Reader[MyCaseClass]` IS a visitor that directly consumes JSON tokens and produces a `MyCaseClass`. No intermediate `Json` AST is ever constructed. Composable and type-safe (unlike SAX).

**Applicability**: Not directly applicable — circe contract requires `Json` AST. But validates the principle: avoiding AST construction is the single biggest performance win. Our sanely-jsoniter/sanely-zero approach achieves the same for the jsoniter path.

### 5.2 Scala 3 macro limitations discovered by upickle

Issue #389 revealed fundamental Scala 3 macro limitations:

- **Cannot generate anonymous classes with typed fields** → forced to use `Array[Any]` (boxing overhead)
- **Cannot generate `match` statements** → forced to use runtime `Map[K, V]` lookups
- Scala 3 macros were dramatically slower than Scala 2: ~637 ops/s vs ~1403 ops/s

Fix (PR #440): Moved logic from runtime to compile-time. Pre-compute field name lookup structures. Result: 637 → 1065 ops/s.

**Applicability**: Important lesson. Our `Expr.summonIgnoring` approach and direct constructor calls (`new T(_f0, _f1, ...)`) already avoid the `Array[Any]` problem. We generate `(hash: @switch) match { ... }` which does work in our macros — upickle's limitation may be specific to their macro structure.

### 5.3 Native JSON.parse() on Scala.js

upickle's `WebJson` module delegates to the browser's native `JSON.parse()`:

```scala
// JS: use native parser, traverse result
val jsValue = js.JSON.parse(input)
traverse(jsValue, visitor)  // walk JS object tree through visitor
```

**4-6x faster** than pure Scala-based JSON parsers on Scala.js. The browser's C++-implemented `JSON.parse()` is unbeatable by any Scala/JS code.

**Applicability**: **P3**. We could offer an optional Scala.js fast path: `JSON.parse()` → traverse JS object → domain type. This bypasses our custom parser entirely on JS but gives massive speedup. Trade-off: depends on browser runtime, adds a JS-specific code path.

### 5.4 weePickle: removing runtime Maps

weePickle's key improvement over upickle: removed runtime `Map[String, Int]` lookups in Scala 3 macros, pre-computing field name structures at compile time instead. This was partially adopted back by upickle in PR #440.

**Applicability**: Already applied. Our macros pre-compute everything at compile time.

### 5.5 ujson mutable AST

ujson uses `mutable.LinkedHashMap` (vs circe's immutable `JsonObject`/`Vector`). Measurably faster for construction. Not applicable — we produce immutable `io.circe.Json`.

---

## Part 6: kotlinx.serialization — golden mask and sequential fast path

**Repository**: https://github.com/Kotlin/kotlinx.serialization
**Architecture**: Kotlin compiler plugin (IR-level, not annotation processor)

### 6.1 Golden mask — bitmask field validation

The most elegant technique in the research. Each field gets a bit position:

```kotlin
var seenMask: Int = 0

// During decode, per field:
seenMask = seenMask or (1 shl fieldIndex)

// After all fields read:
val REQUIRED_MASK: Int = 0b1011  // compile-time constant: bits for non-optional fields
if (seenMask and REQUIRED_MASK != REQUIRED_MASK) {
    // report which required fields are missing
    missingFieldException(seenMask, REQUIRED_MASK)
}
```

**Why it's better than individual checks**:
- Replaces N boolean variables + N conditional checks with 1 Int + 1 AND + 1 comparison
- For >32 fields, uses multiple Int masks
- `missingFieldException` can use `numberOfTrailingZeros` to find which fields are missing
- The JIT sees a single hot comparison instead of a chain of branches

**Applicability**: **P0**. Adopt immediately for:
- `strictDecoding` in configured decoder (check no unexpected fields)
- Default field handling (which fields need defaults filled)
- Required field validation

Combine with zio-blocks' dual-Long approach for up to 128 fields.

### 6.2 Sequential fast path (decodeSequentially)

When the codec knows fields will arrive in declaration order:

```kotlin
// Fast path: read fields in order, no name lookup at all
val field0 = decoder.decodeStringElement(descriptor, 0)
val field1 = decoder.decodeIntElement(descriptor, 1)
val field2 = decoder.decodeBooleanElement(descriptor, 2)
return MyClass(field0, field1, field2)
```

**No field name reading at all** — just reads values in order. Falls back to name-based dispatch (`decodeStructure` path) on format mismatch.

**Difference from zio-blocks' approach**: kotlinx skips reading the key entirely in the fast path. zio-blocks reads the key but speculatively checks against the expected next field. kotlinx is faster when order is guaranteed; zio-blocks is safer for mixed-order payloads.

**Applicability**: **P1** for a specialized "ordered" codec mode. Our general decoder must handle arbitrary order (circe contract), but we could offer an opt-in `@orderedFields` annotation or configuration that generates the sequential path.

### 6.3 Format-agnostic descriptors

Generated code talks to a `Decoder` interface abstraction, not JSON directly. Same codec works for JSON, Protobuf, CBOR, etc.

**Applicability**: Not applicable. We're JSON-only, and the abstraction adds overhead.

---

## Part 7: simdjson / simdjson-java — structural indexing

**Repository**: https://github.com/simdjson/simdjson-java
**Paper**: Langdale & Lemire, "Parsing Gigabytes of JSON per Second" (2019)
**Performance**: 4x Jackson on 512-bit SIMD hardware

### 7.1 Two-stage architecture

**Stage 1 (Structural Indexing)**: Scans bytes in 32/64-byte chunks to locate ALL structural characters (`{`, `}`, `[`, `]`, `:`, `,`, `"`). Uses vectorized classification via `vpshufb`-style lookup tables. Output: a bitmask of structural positions.

**Stage 2 (Tape Construction)**: Walks structural indices to build a "tape" — a flat `long[]` where each 64-bit element encodes `(type_tag << 56) | payload_56bit`.

### 7.2 Structural indexing without SIMD

The key insight applicable to us: **pre-scanning for structural characters improves branch prediction even without SIMD**. Even scalar SWAR (8 bytes at a time) gives 8x fewer iterations than byte-by-byte:

```scala
val word = getLong(buf, pos)
// Check 8 bytes at once for structural characters
val structural = detectStructural(word)  // SWAR bitmask
// Use numberOfTrailingZeros to find positions
```

**Applicability**: **P2**. Consider for our `skip()` implementation and container-level parsing. Not needed for field-level parsing (we know the schema).

### 7.3 Tape format — flat array instead of tree

Objects/arrays stored as `Array[Long]` with forward/backward pointer pairs. Strings on a separate tape. Numbers stored inline as raw bits.

**Applicability**: Not applicable (we don't build an AST). But if we ever need a lightweight JSON value type, this is far more efficient than tree-of-objects.

### 7.4 On-Demand API

Newer alternative to tape: runs only Stage 1, provides an iterator over structural indices. Values parsed only when accessed. Skipping means advancing the index past the structure's extent.

**Applicability**: **P3**. Interesting for a future "extract specific fields from large JSON" API. Not relevant for full-type codec derivation.

### 7.5 JVM Vector API limitations

simdjson-java requires Java 24+. Critical gap: `selectFrom`/`rearrange` (cross-lane shuffle) generates long instruction sequences instead of mapping to single `vpshufb`. Daniel Lemire reported this as a fundamental design gap in OpenJDK.

**Applicability**: Not viable yet. Monitor JDK Vector API progress.

---

## Part 8: sonic-rs — SWAR skipping and direct-to-struct

**Repository**: https://github.com/cloudwego/sonic-rs (Rust, by CloudWeGo/ByteDance)
**Performance**: ~30% faster than simd-json, ~2x faster than serde_json

### 8.1 SWAR container skipping

Skip unwanted JSON values by counting brackets 8 bytes at a time:

```
Load 8 bytes into a Long
Count open brackets: popcnt(mask_open)
Count close brackets: popcnt(mask_close)
depth_delta = opens - closes
depth += depth_delta
if (depth == 0) → container fully skipped
```

**Branchless**: no per-byte `if (byte == '{') depth++` chain. Processes 8 bytes per iteration.

**Applicability**: **P1**. Direct improvement to our `skip()` method. When encountering unknown fields in a JSON object, we need to skip the field's value efficiently. SWAR skipping is 8x fewer iterations for nested containers.

### 8.2 SIMD string bitmap

Computes a bitmap of special characters in strings (quotes, backslashes, control chars) using SIMD. Determines string boundaries and escape positions in bulk.

**Applicability**: We use SWAR (scalar) version of this via jsoniter-scala's technique.

### 8.3 Arena allocation for DOM

When parsing to a generic document, uses arena allocation — one big allocation for the whole document. Objects stored as sorted arrays (not HashMaps) for cache locality.

**Applicability**: **P3**. If we ever need a lightweight JSON DOM, sorted arrays are better than hash maps for small objects (which are the vast majority in real JSON).

### 8.4 LazyValue API

Returns raw JSON slices without parsing. Values parsed only when accessed during iteration.

**Applicability**: Not applicable for typed codec derivation. Could be useful for a future "extract and forward" use case.

---

## Part 9: DSL-JSON — byte-level field matching

**Repository**: https://github.com/ngs-doo/dsl-json
**Claims**: Fastest Java JSON library

### 9.1 Compile-time annotation processor generating type-specific code

`@CompiledJson` triggers Java annotation processing that generates dedicated serializer/deserializer classes per type. No reflection, no runtime dispatch.

**Applicability**: Our Scala 3 macros achieve the same. Validates the approach.

### 9.2 Hash-based field matching on raw UTF-8 bytes

Property names matched by precomputed hash of their UTF-8 byte representation. If hash matches, compares raw bytes at the pinned buffer position — field names are **never converted to String**.

```java
// Conceptual: hash the incoming key bytes directly
int hash = hashBytes(buf, keyStart, keyLen);
switch (hash) {
  case HASH_NAME: if (bytesEqual(buf, keyStart, KEY_NAME_BYTES)) { ... }
}
```

**Key difference from jsoniter-scala**: jsoniter-scala reads into a `charBuf` (char array), then hashes chars. DSL-JSON hashes the raw UTF-8 bytes without any char conversion. For ASCII keys (>99% of real JSON), this saves the byte→char conversion step.

**Applicability**: **P1**. Our custom parser can hash raw bytes directly instead of reading into a charBuf first. For ASCII-only keys (which we know at compile time via `isEncodingRequired`), skip the charBuf entirely.

### 9.3 Zero-copy converters

Custom converters work directly on byte buffers. Numbers parsed byte-by-byte without string creation.

**Applicability**: Standard technique. Already planned.

---

## Part 10: FastDoubleParser — number parsing state of the art

**Repository**: https://github.com/wrandelshofer/FastDoubleParser
**Performance**: 4-7x faster than `java.lang.Double.valueOf(String)`

### 10.1 The Lemire fast path

For inputs with ≤17 significant digits (covers 99%+ of real-world doubles): process digits into an integer mantissa, apply precomputed power-of-ten lookup to convert to IEEE 754 in a single multiply+shift. Avoids `Double.parseDouble` entirely.

**Key insight**: Accept `byte[]` and `char[]` directly, avoiding String construction. Jackson 2.18 integrated this and saw +20% for float-heavy workloads just from avoiding the String allocation.

**Applicability**: Already integrated in jsoniter-scala (which we port). The lesson: **never create a String to parse a number**. Parse directly from the byte buffer position.

### 10.2 BigDecimal/BigInteger: divide-and-conquer with FFT

O(N log N log log N) instead of Java's O(N^2) for large numbers.

**Applicability**: **P3**. Only matters for pathologically large numbers. Profile first.

---

## Part 11: Jackson 2.18-2.19 — mature library lessons

**Repository**: https://github.com/FasterXML/jackson

### 11.1 Avoid String allocation for number parsing

Jackson 2.18's biggest optimization: `getDoubleValue()`/`getFloatValue()` pass `TextBuffer` segments directly to FastDoubleParser instead of creating a `String` first. +20% for float-heavy payloads.

**Lesson**: The cost of `new String(chars, off, len)` is significant in a hot loop. Any parser that creates strings for number parsing leaves performance on the table.

### 11.2 Lock-free buffer management

Replaced `synchronized` with `ReentrantLock` in `InternCache` and `ThreadLocalBufferManager`. Reduces contention in multi-threaded parsing.

**Lesson**: For server workloads with many threads parsing simultaneously, lock contention on shared state matters. Our ThreadLocal approach avoids this entirely (no shared state).

---

## Part 12: Other libraries (tethys, smithy4s, Moshi)

### 12.1 tethys-json

**Repository**: https://github.com/tethys-json/tethys

Token-based streaming architecture (like upickle's visitor but with pull-based `TokenIterator`). Uses Jackson for tokenization. Supports recursive semi-auto derivation where `JsonConfiguration` propagates to nested types automatically.

**Technique of interest**: `WriterBuilder` / `ReaderBuilder` for field customization (rename, remove, add, transform) resolved at compile time. More flexible than annotation-based configuration.

**Applicability**: **P3**. The builder pattern for configuration is cleaner than annotations but doesn't affect runtime performance.

### 12.2 smithy4s-json

**Repository**: https://github.com/disneystreaming/smithy4s

Schema-as-value approach: `Schema[A]` captures structure, `SchemaVisitor` interprets it to produce codecs. Uses jsoniter-scala internally for actual JSON I/O.

**Technique of interest**: `CachedSchemaCompiler` memoizes compiled codecs. Schema generated once at compile time, codecs derived once at runtime, reused thereafter.

**Applicability**: Not applicable. Our macro-based approach avoids runtime derivation entirely.

### 12.3 Moshi (Square, Kotlin)

**Repository**: https://github.com/square/moshi

KSP code generation generates `JsonAdapter` classes. Uses `selectName()` with pre-built `JsonReader.Options` for binary search over field names.

**Technique of interest**: Binary search over sorted field names as an alternative to hashing. For 5-10 fields, binary search is 2-3 comparisons, competitive with hash dispatch.

**Applicability**: **P3**. Binary search is simpler than hash dispatch and avoids hash collision handling. But for Scala's `@switch` which compiles to JVM jump tables, hash dispatch is likely faster.

---

## Synthesis: technique ranking for sanely-zero

### Tier 0 — Adopt immediately (proven, high impact, low effort)

| # | Technique | Source | Impact | Effort | Phase |
|---|---|---|---|---|---|
| 1 | **Speculative sequential field access** | zio-blocks (by Plokhotnyuk) | +5-15% reads | Medium | 2 |
| 2 | **Golden mask bitmask validation** | kotlinx.serialization + zio-blocks | Faster required-field checks, cleaner code | Low | 2 |
| 3 | **Bitset missing field tracking** | zio-blocks | Replaces N booleans with 1-2 Long ops | Low | 2 |

### Tier 1 — Strong ROI (proven elsewhere, medium effort)

| # | Technique | Source | Impact | Effort | Phase |
|---|---|---|---|---|---|
| 4 | **Compile-time key byte arrays** | DSL-JSON + our ANALYSIS.md | +5-10% writes | Low | 4 |
| 5 | **Raw byte hashing (skip charBuf for ASCII keys)** | DSL-JSON | +2-5% reads | Medium | 4 |
| 6 | **SWAR container skipping** | sonic-rs | Faster skip() | Medium | 1 |
| 7 | **Sequential decode fast path (opt-in)** | kotlinx.serialization | Skip key reading entirely | Medium | 4 |

### Tier 2 — Worth investigating (need benchmarking to confirm)

| # | Technique | Source | Impact | Effort | Phase |
|---|---|---|---|---|---|
| 8 | **StringMatrix field matching** | zio-json | Better for many-field types? | High | 4 |
| 9 | **Register-based unboxed storage** | zio-blocks | Reduce boxing? | High | 4 |
| 10 | **8-byte SWAR string parsing** | borer (original) | Better than 4-byte? | Medium | 1 |
| 11 | **Structural pre-indexing (scalar SWAR)** | simdjson concept | Better skip(), container parsing | High | 4 |
| 12 | **Open-addressing StringMap** | zio-blocks | Alternative to @switch for many fields | Medium | 4 |

### Tier 3 — Nice to have (low priority or niche)

| # | Technique | Source | Impact | Effort | Phase |
|---|---|---|---|---|---|
| 13 | **Native JSON.parse() on Scala.js** | upickle | 4-6x JS speedup | Medium | 4 |
| 14 | **Speculative writes** | borer | Minor CPU pipeline gain | Low | 1 |
| 15 | **Arena allocation for JSON DOM** | sonic-rs | If building AST | High | future |
| 16 | **Streaming string reader** | zio-json | For very long strings | Low | future |
| 17 | **BigInt O(n^1.5) / FFT** | jsoniter-scala + FastDoubleParser | For huge numbers only | Medium | future |

### Not applicable (breaks constraints or doesn't help)

| Technique | Source | Why not |
|---|---|---|
| Visitor pattern (skip AST) | upickle | Breaks circe contract |
| Schema-as-value derivation | zio-blocks, smithy4s | Adds runtime derivation overhead |
| JVM Vector API SIMD | simdjson-java | Requires Java 24+ |
| SIMD number parsing | sonic-rs | Requires native SIMD instructions |
| ArrayBasedCodecs (positional) | borer | Breaks circe wire format |
| Mutable AST | ujson | circe uses immutable Json |
| Format-agnostic descriptors | kotlinx.serialization | Abstraction overhead, JSON-only focus |

---

## Key insight: the convergence

The most performant libraries (jsoniter-scala, zio-blocks, DSL-JSON, sonic-rs) have converged on the same fundamental techniques:

1. **No intermediate AST** — bytes → types directly
2. **Compile-time field knowledge** — pre-computed hashes, byte arrays, masks
3. **Speculative sequential access** — assume order, fall back on mismatch
4. **Bitmask tracking** — field presence as integer operations, not boolean arrays
5. **Direct primitive I/O** — no boxing, no codec dispatch for primitives
6. **Buffer reuse** — ThreadLocal or arena, not per-call allocation

sanely-zero is uniquely positioned to combine ALL of these because we own both the macro and the parser. jsoniter-scala can't do speculative access (API boundary). zio-blocks can't do compile-time key bytes (runtime derivation). DSL-JSON can't do Scala 3 macros (Java). We can do all of them.
