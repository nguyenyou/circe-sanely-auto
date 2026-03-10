# sanely-zero: Competing with jsoniter-scala from scratch

## What this document is

A deep technical analysis of every performance technique in jsoniter-scala, mapped against our constraints and advantages. The goal: build a zero-dependency JSON engine for Scala 3 that competes with — and in key areas beats — jsoniter-scala, while maintaining circe wire compatibility and cross-platform JVM + Scala.js support.

## Our constraints

| Constraint | Implication |
|---|---|
| **Scala 3.8.2 only** | Full access to Scala 3 macros, `Expr.summonIgnoring`, inline, match types. No Scala 2 compat baggage |
| **Cross-platform JVM + Scala.js** | Cannot rely on `sun.misc.Unsafe`, VarHandle, or SWAR for correctness. Must have pure-Scala fallbacks |
| **Zero dependencies** | No jsoniter-scala, no circe-core, no cats. Only `scala-library` and `scala3-library` |
| **Own the macro AND parser** | Can fuse compile-time schema knowledge into the parser hot path — the key structural advantage |

---

## Part 1: jsoniter-scala's performance techniques — complete inventory

### 1.1 Architecture: no intermediate AST

The single most important design decision. While circe parses JSON → `Json` AST → domain type (two passes, AST allocation), jsoniter-scala parses UTF-8 bytes → domain type directly (one pass, zero AST). This alone accounts for **2-5x** throughput advantage over AST-based libraries.

**Our stance**: We match this. sanely-zero parses bytes → domain type in a single macro-generated pass. No AST.

### 1.2 SWAR (SIMD Within A Register)

Introduced in PRs #866 (serialization) and #876 (parsing). Processes 4-8 bytes simultaneously using 64-bit integer arithmetic.

**String parsing** (`JsonReader.scala:3961-3987`):
```scala
val bs = ByteArrayAccess.getInt(buf, pos)
val m = ((bs - 0x20202020 ^ 0x3C3C3C3C) - 0x1010101 | (bs ^ 0x5D5D5D5D) + 0x1010101) & 0x80808080
```
Checks 4 bytes simultaneously for control chars, `"`, and `\`. If `m == 0`, all 4 bytes are plain ASCII — bulk copy. Based on borer's technique.

**Number digit validation** (`JsonReader.scala:2140`):
```scala
val bs = ByteArrayAccess.getLong(buf, pos)
dec = bs - 0x3030303030303030L
((bs + 0x4646464646464646L | dec) & 0x8080808080808080L) == 0
```
Validates 8 bytes as digits simultaneously using the high-bit overflow trick. Based on simdjson/FastDoubleParser.

**8-digit integer parsing** (`JsonReader.scala:2143`):
```scala
dec *= 2561
x *= 100000000
x -= ((dec >> 8 & 0xFF000000FFL) * 4294967296000100L + (dec >> 24 & 0xFF000000FFL) * 42949672960001L >> 32)
```
Converts 8 validated digit bytes to an integer in a single expression using magic multiplier arithmetic.

**Impact**: Up to 2x for string-heavy and number-heavy payloads on JVM.

**Our stance**: SWAR requires `ByteArrayAccess` (VarHandle on JVM, LLVM intrinsics on Native). **Not available on Scala.js.** We can use SWAR on JVM via a platform split (`src-jvm/`), with byte-by-byte fallback in shared code. This is our main disadvantage on the parser layer — but our codec-layer advantages compensate.

### 1.3 Float/double parsing: rust-lexical moderate path

Three-tier approach (`JsonReader.scala:2184-2333`):

1. **Fast path** (line 2280): Mantissa < 2^52, moderate exponent → direct `pow10Doubles` table lookup + multiply/divide. Handles ~80% of real-world doubles.

2. **Moderate path** (line 2295): Based on Alexander Huszagh's `rust-lexical`. 128-bit multiplication via `Math.multiplyHigh` (or `unsignedMultiplyHigh`) with precomputed `pow10Mantissas` table (686 Long entries for e10 range [-343, +343]). Computes result in integer arithmetic, checks rounding error.

3. **Slow path** (line 2330): Falls back to `java.lang.Double.parseDouble` for values extremely close to halfway points. Rare.

**Performance**: 3.5x faster than Jackson+FastDoubleParser for double arrays (380K vs 108K ops/s from issue #672).

**Our stance**: Port the moderate path directly (MIT licensed). The `pow10Mantissas` table and the `unsignedMultiplyHigh` logic are ~200 lines of pure arithmetic — cross-platform by nature. `Math.multiplyHigh` is available on JVM (Java 9+). On Scala.js, we implement it via two 64-bit multiplications (well-known technique).

### 1.4 Float/double writing: xjb algorithm

`JsonWriter.scala:2239-2435`. Based on the work of Xiang JunBo and Wang TieJun ("xjb: Fast Float to String Algorithm"). Replaced the earlier Schubfach algorithm in v2.38.6.

Produces the shortest decimal representation that round-trips back to the same binary value. Uses:
- Precomputed `floatPow10s` (77 entries) and `doublePow10s` (1170 entries)
- `Math.multiplyHigh` for 128-bit multiplication
- Unsigned comparison tricks for halfway-point detection

**Our stance**: Port directly. Pure arithmetic, cross-platform. The tables and algorithm are ~200 lines total.

### 1.5 Integer writing: James Anhalt's algorithm

`JsonWriter.scala:2116-2129`. Writes 8 digits in a single operation without any division:

```scala
val y1 = x * 140737489L           // magic multiplier
val y2 = (y1 & 0x7FFFFFFFFFFFL) * 100L
val y3 = (y2 & 0x7FFFFFFFFFFFL) * 100L
val y4 = (y3 & 0x7FFFFFFFFFFFL) * 100L
// Extract 2-digit pairs, look up in digits table, pack into one Long
ByteArrayAccess.setLong(buf, pos, d1 | d2 | d3 | d4)
```

Uses a precomputed `digits` table (100 entries of 2-byte pairs: `"00"`, `"01"`, ..., `"99"`). All division replaced by multiplication + shift.

**Our stance**: Port directly. The core algorithm is pure arithmetic. The `ByteArrayAccess.setLong` call can be replaced with byte-by-byte writes on Scala.js (4 `setShort` calls or 8 byte writes).

### 1.6 Digit count: Daniel Lemire's branchless trick

`JsonWriter.scala:2446-2448`:
```scala
private[this] def digitCount(x: Long) = (offsets(java.lang.Long.numberOfLeadingZeros(x)) + x >> 58).toInt
```
Branchless digit count using a 64-entry lookup table indexed by leading zeros. No loops, no conditionals.

**Our stance**: Port directly. `Long.numberOfLeadingZeros` is available on all platforms.

### 1.7 ByteArrayAccess: VarHandle multi-byte I/O

`ByteArrayAccess.java:8-45`. Four VarHandle instances for little-endian and big-endian unaligned access on `byte[]`:

```java
private static final VarHandle VH_LONG =
    MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
```

Provides `getLong`, `getInt`, `setLong`, `setInt`, `setShort` — the JIT compiles these to single MOV instructions on x86.

**Our stance**: This is the **JVM-only performance moat**. We create a platform split:
- `src-jvm/`: `ByteArrayAccess` using VarHandle (or `Unsafe` on older JVMs)
- `src/` (shared): byte-by-byte fallback

On Scala.js, we lose SWAR but keep every other optimization. The codec-layer wins (+19% direct constructors, +25% branchless encoding) don't depend on SWAR.

### 1.8 ThreadLocal reader/writer pooling

`package.scala:8-13`:
```scala
private[this] final val readerPool: ThreadLocal[JsonReader] = new ThreadLocal[JsonReader] {
  override def initialValue(): JsonReader = new JsonReader
}
```

Eliminates ~32KB allocation per parse call (buffer reuse). Reader carries a 32KB byte buffer + 4KB char buffer that survive between calls.

**Our stance**: Implement the same pattern. On Scala.js, ThreadLocal is effectively a global (single-threaded) — same benefit, simpler.

### 1.9 Hash-based field dispatch with @switch

`JsonCodecMaker.scala:2250-2258`. The macro generates:

1. `in.readKeyAsCharBuf()` → reads key into char buffer (no String allocation)
2. `in.charBufToHashCode(len)` → hash via `(h << 5) + (c - h)` formula
3. `(hash: @switch) match { case 0x1a2b → ... }` → JVM `tableswitch`/`lookupswitch`
4. `in.isCharBufEqualsTo(len, "fieldName")` → verify on hash collision

For types with ≤8 fields and total name length ≤64: simple linear comparison chain instead.

**Our stance**: We already do this in sanely-jsoniter. Port to our own reader. **Enhancement opportunity**: with our own parser, we can do field-order prediction (skip hash entirely when fields arrive in declaration order).

### 1.10 Compile-time key classification

`JsonCodecMaker.scala:3399-3403`:
```scala
private def isEncodingRequired(s: String): Boolean = {
  var i = 0
  while (i < len && JsonWriter.isNonEscapedAscii(s.charAt(i))) i += 1
  i != len
}
```

At macro expansion time, decides whether a field name needs Unicode escaping. Most field names are ASCII → fast path `writeNonEscapedAsciiKey`. This avoids a runtime branch on every field write.

**Our stance**: We already do this. With our own writer, we go further: pre-compute the entire `"fieldName":` byte sequence at compile time and emit it as a `System.arraycopy` from a static `Array[Byte]`.

### 1.11 @specialized codec traits

`JsonValueCodec[@sp A]` uses `@specialized` to avoid boxing for primitive codecs (`Int`, `Long`, `Double`, etc.).

**Our stance**: Irrelevant. Our macro generates direct `readInt()`/`writeVal(x.age)` calls — no codec trait dispatch for primitives at all. We're already better here.

### 1.12 Transient field optimization

When `transientDefault=true`, the generated encoder skips fields matching their default values. Requires per-field comparison branches.

**Our stance**: We don't need this. Circe writes all fields unconditionally. Fewer branches = simpler code = better JIT optimization. This is why our writes are +25% faster already.

### 1.13 Security: discriminator-first, size limits

- `requireDiscriminatorFirst=true`: prevents DoS via discriminator at end of large objects
- Configurable digit/scale limits for BigDecimal/BigInt
- Configurable max size for maps, sets, BitSets

**Our stance**: Implement the same security limits. Discriminator-first is already our default in sanely-jsoniter.

### 1.14 JIT-friendly coding patterns

- `final class` for `JsonReader`/`JsonWriter` → enables devirtualization
- Local `val buf = this.buf` aliases → helps JIT prove no aliasing through method calls
- Sign handling via `x ^= s; x -= s` instead of `if (neg) -x` → branchless
- Division by constant via magic multiplier: `Math.multiplyHigh(x, 6189700196426901375L) >>> 25` for ÷100M
- `@tailrec` for string processing loops → compiled to tight loops

**Our stance**: Apply all these patterns. They're pure coding discipline, not library-specific.

### 1.15 BigInt/BigDecimal: O(n^1.5) parsing

Based on the `big-math` library. Converts large digit strings to BigInt/BigDecimal in O(n^1.5) time instead of Java's standard O(n^2).

**Our stance**: Implement if needed. For most JSON payloads, BigDecimal/BigInt have few digits. Profile first.

### 1.16 Scala.js differences

jsoniter-scala has **separate implementations** for JVM and JS:
- JVM: 4,946 (reader) + 3,362 (writer) = 8,308 lines
- JS: 4,493 (reader) + 3,696 (writer) = 8,189 lines

Key differences:
- **No ByteArrayAccess/SWAR on JS**: All byte operations are individual
- **No `@specialized`**: No effect on JS
- **`Array[Int]` vs `Array[Byte]`** for BigInt magnitude (platform-specific optimization)
- **Float formatting tolerances**: JS allows slightly longer output for whole-number floats
- **Same ThreadLocal pattern**: single-threaded no-op on JS

**Our stance**: Shared source with minimal platform splits. Our macro-generated codec code is 100% shared. Only the reader/writer needs a small JVM-specific optimization layer (`src-jvm/ByteArrayAccess.scala`).

### 1.17 Recent optimizations (2024-2026)

| Date | Change | Impact |
|---|---|---|
| Feb 2026 | Issue #1370: xjb author proposes minor float-writing optimizations | Minor |
| Nov 2025 | PR #1335: Fix 5-year-old unnecessary buffer reallocation bug | Allocation reduction |
| 2024 | xjb algorithm replaces Schubfach for float/double writing (v2.38.6) | Throughput improvement |
| 2024 | SWAR digit validation for 8-byte number parsing | Up to 2x for number-heavy payloads |
| 2023-2024 | Ongoing rust-lexical moderate path refinements | Better float/double precision handling |
| 2023 | SWAR for serialization (PR #866) and parsing (PR #876) | Up to 2x for ASCII strings |

---

## Part 2: Where we can compete — technique-by-technique assessment

### Techniques we can match (PORT or reimplemented)

| Technique | jsoniter-scala approach | Our approach | Gap |
|---|---|---|---|
| No intermediate AST | Direct bytes → types | Same — macro-generated single-pass | **None** |
| rust-lexical moderate path | 128-bit multiply + pow10 table | Port directly (pure arithmetic) | **None** |
| xjb float/double writing | Shortest representation via mantissa arithmetic | Port directly (pure arithmetic) | **None** |
| Anhalt integer writing | Multiplication-based 8-digit extraction | Port directly (pure arithmetic) | **None** |
| Lemire digit count | Branchless 64-entry lookup table | Port directly | **None** |
| Hash-based field dispatch | charBufToHashCode + @switch | Already implemented in sanely-jsoniter | **None** |
| ThreadLocal pooling | Per-thread reader/writer reuse | Same pattern | **None** |
| JIT-friendly patterns | final class, local aliases, branchless arithmetic | Same patterns | **None** |
| Security limits | Digit/scale limits, discriminator-first | Same | **None** |
| Compile-time key classification | isEncodingRequired at macro time | Already do this | **None** |

### Techniques where we're already ahead (KEEP from sanely-jsoniter)

| Technique | jsoniter-scala | sanely-zero | Advantage |
|---|---|---|---|
| **Direct constructor calls** | `mirror.fromProduct(new ArrayProduct(...))` → 2 allocations + boxing | `new User(_f0, _f1, _f2)` → zero allocation | **+19% reads** |
| **Typed local variables** | `Array[Any]` → all fields boxed | `var _age: Int = 0` → unboxed on stack | **+5-10% reads** |
| **Branchless product encoding** | transientDefault/transientEmpty checks per field | Unconditional write-key, write-val sequence | **+25% writes** |
| **Direct primitive I/O** | `codecs(i).asInstanceOf[JsonValueCodec[Int]].decodeValue(in, 0)` | `reader.readInt()` — no virtual dispatch | **+3-5% both** |
| **Simpler field semantics** | Per-field conditional branches (transient, default, none) | Write every field unconditionally (circe format) | **+5-10% writes** |

### Techniques only possible because we own both macro AND parser (REWRITE)

| Technique | Why impossible in jsoniter-scala | How we do it | Projected gain |
|---|---|---|---|
| **Field-order prediction** | `readKeyAsCharBuf()` + hash is the only API. Can't "peek" at the next key | Parser exposes `isNextKey(precomputedBytes)` — O(1) memcmp when fields arrive in order, hash fallback for unordered | **+5-15% reads** |
| **Compile-time key bytes** | `writeNonEscapedAsciiKey(s)` copies char-by-char from String | Macro pre-computes `val KEY = Array[Byte]('"','n','a','m','e','"',':')`, emits `System.arraycopy` | **+5-10% writes** |
| **Fused key+value parsing** | Key reading and value reading are separate API calls | Macro generates inline: `if (buf(pos) == 'n' && buf(pos+1) == 'a' ...) { pos += 6; _name = readString() }` | **+2-5% reads** |
| **Eliminated virtual dispatch** | Every `in.readInt()` is a virtual call on JsonReader | Reader state (buf, pos, charBuf) can be local variables or a struct the JIT scalar-replaces | **+2-5% both** |
| **Pre-sized write buffers** | Writer doesn't know output size — must grow-and-copy | Macro estimates output size from field count + type info, pre-allocates | **+2-5% writes** |
| **Inlined integer parsing** | `in.readInt()` is a method call (depends on JIT warmup to inline) | Macro generates the 10-line digit loop directly in the codec per field | **+3-8% reads** |

### Techniques where we're at a disadvantage

| Technique | jsoniter-scala | sanely-zero | Gap | Mitigation |
|---|---|---|---|---|
| **SWAR string parsing (JVM)** | 4 bytes at once via ByteArrayAccess | JVM: same via platform split. JS: byte-by-byte | **JS only**: ~1.5-2x slower on long ASCII strings | Codec-layer wins compensate. Most JSON keys are short (<20 chars) |
| **SWAR number validation (JVM)** | 8 bytes at once | JVM: same. JS: digit-by-digit | **JS only**: ~1.5x slower on large number arrays | Most JSON numbers are short (<10 digits) |
| **VarHandle setLong for writes (JVM)** | Write 8 bytes atomically | JVM: same. JS: byte-by-byte | **JS only**: write-heavy workloads slower | Pre-computed key bytes partially compensate |
| **Years of edge-case hardening** | 628+ explicit tests, ~46K lines of test code | Start from ported test vectors + JSONTestSuite | Test coverage gap initially | Phase 1 ports all test vectors; property-based fills gaps |
| **Streaming (InputStream)** | Full support | `byte[]` and `String` only in phase 1 | No streaming | HTTP frameworks buffer anyway (>95% of use cases covered) |

---

## Part 3: Our strategic advantages (what jsoniter-scala cannot easily replicate)

### 3.1 Macro + parser fusion

jsoniter-scala's macro (`JsonCodecMaker.make`) generates code that calls `JsonReader`/`JsonWriter` methods. The macro and parser are **separate compilation units** with a fixed API boundary. The macro cannot:
- Change how `readInt()` works for a specific type
- Specialize string parsing for known-short keys
- Predict field order based on case class declaration
- Pre-compute the exact byte pattern of a field key

We have **no API boundary**. The macro can generate arbitrary parsing code that directly manipulates `buf` and `pos`. This is the fundamental structural advantage.

### 3.2 Scala 3.8.2 exclusive

jsoniter-scala supports Scala 2.13 and Scala 3.x. This means:
- Scala 2 macro compatibility constraints on code generation
- Cannot use Scala 3-only features (match types, inline match, opaque types for zero-cost wrappers)
- Cannot use `Expr.summonIgnoring` (our single-pass derivation trick)

We target Scala 3.8.2 exclusively. No backward compatibility overhead.

### 3.3 Zero dependency = zero conflict

jsoniter-scala-core is ~16K lines. Any project using it inherits version management overhead. Our zero-dependency approach means:
- No transitive dependency conflicts
- No version alignment issues
- Smaller total artifact size
- Simpler for library authors to adopt

### 3.4 Circe wire compatibility as a feature

jsoniter-scala uses its own JSON format conventions (e.g., different ADT tagging defaults, transient fields). Applications must choose one format or the other.

We maintain **100% circe wire compatibility** — same JSON output, same JSON input, same error messages for the same types. This means:
- Drop-in replacement for circe-based APIs
- Mixed codebases can use both circe and sanely-zero
- Zero migration risk for existing circe users

---

## Part 4: Implementation strategy

### Phase 1 — Parser/writer core (~1,500 lines)

Port proven primitives from jsoniter-scala (MIT licensed). Pure translation.

```
sanely-zero/
├── src/                    # Shared (95% of code)
│   └── sanely/zero/
│       ├── ZeroReader.scala        # ~800 lines: token nav, readInt/Long/Float/Double/String/Boolean/BigDecimal/BigInt, null, skip, error reporting
│       ├── ZeroWriter.scala        # ~500 lines: buffer mgmt, writeVal overloads, string escaping, structure tokens
│       ├── ByteArrayAccess.scala   # ~40 lines: shared trait + byte-by-byte default impl
│       └── ZeroPool.scala          # ~30 lines: ThreadLocal pooling, entry points (readFromArray, writeToString, etc.)
├── src-jvm/                # JVM-only
│   └── sanely/zero/
│       └── ByteArrayAccessJvm.scala  # ~30 lines: VarHandle-based multi-byte access
├── src-js/                 # JS-only (if needed)
│   └── sanely/zero/
│       └── Platform.scala          # ~10 lines: platform flags
└── test/
    └── src/sanely/zero/
        ├── ReaderIntTest.scala     # Port: ~40 explicit + property-based
        ├── ReaderFloatDoubleTest.scala  # Port: ~60 explicit + property-based (Eisel-Lemire edges)
        ├── ReaderStringTest.scala  # Port: ~30 explicit + property-based (UTF-8, escapes, surrogates)
        ├── WriterTest.scala        # Port: ~50 explicit + property-based
        ├── RoundtripTest.scala     # New: encode → decode roundtrip for all types
        └── JSONTestSuiteTest.scala # New: 318 conformance tests
```

**Key algorithms to port**:

| Algorithm | Source | Lines | Purpose |
|---|---|---|---|
| rust-lexical moderate path | `JsonReader.scala:2184-2333` | ~150 | Fast double/float parsing |
| `pow10Mantissas` table | `JsonReader.scala:4563-4630` | ~70 | Precomputed mantissa table for moderate path |
| xjb float/double writing | `JsonWriter.scala:2239-2435` | ~200 | Shortest-representation float/double output |
| Anhalt `write8Digits` | `JsonWriter.scala:2116-2129` | ~15 | Branchless 8-digit integer writing |
| Lemire `digitCount` | `JsonWriter.scala:2446-2448` | ~5 | Branchless digit count |
| `charBufToHashCode` | `JsonReader.scala:1274-1282` | ~10 | Field name hash for dispatch |
| `isCharBufEqualsTo` | `JsonReader.scala:1291-1300` | ~10 | Field name verification |
| `parseString` (SWAR) | `JsonReader.scala:3961-3987` | ~30 | 4-byte-at-a-time string scan |
| `parseEncodedString` | `JsonReader.scala:3988-4060` | ~70 | Escape sequence handling |
| `skip` methods | `JsonReader.scala:4431-4481` | ~50 | Skip unknown values |

### Phase 2 — Macro integration + products

Wire the macro to generate code targeting `ZeroReader`/`ZeroWriter`. All existing sanely-jsoniter codec techniques carry over:
- Direct constructor calls
- Typed local variables
- Branchless product encoding
- Hash-based @switch dispatch
- Direct primitive I/O

### Phase 3 — Containers + sum types + configured

Complete type coverage:
- Container codecs (List, Vector, Set, Option, Map, Array, Either)
- Sum type dispatch (external tagging + discriminator)
- Sub-trait hierarchy support
- Configured derivation (defaults, discriminator, snake_case, strict decoding)

### Phase 4 — Unique optimizations (our moat)

The REWRITE layer — only possible because we own both macro and parser:

1. **Field-order prediction**: `reader.isNextKey(precomputedKeyBytes)` — O(1) `memcmp` for ordered fields, hash fallback for unordered. Skip the entire hash computation for the common case.

2. **Compile-time key bytes**: `val KEY_NAME = Array[Byte]('"','n','a','m','e','"',':')` + `System.arraycopy`. Zero per-call string processing.

3. **Fused key+value parsing**: Inline key comparison directly in the generated code. For short keys (≤8 bytes), compare as a single Long.

4. **Inlined integer parsing**: Generate the digit loop directly per field. No method call overhead, no JIT warmup needed.

5. **Pre-sized write buffers**: Macro estimates minimum output size from field count and type info. Allocate once, no grow-and-copy.

---

## Part 5: Performance projections

### JVM

| Operation | jsoniter-scala | sanely-zero (projected) | Source of advantage |
|---|---|---|---|
| **Product read (ordered fields)** | Hash-based dispatch | Field-order prediction skips hash | +5-15% |
| **Product read (primitives)** | `readInt()` via virtual call | Inlined digit loop, no dispatch | +3-8% |
| **Product read (construction)** | `mirror.fromProduct(ArrayProduct(...))` | `new T(_f0, _f1, ...)` direct | +19% (proven) |
| **Product write (keys)** | `writeNonEscapedAsciiKey(s)` char-by-char | `System.arraycopy(KEY_BYTES)` | +5-10% |
| **Product write (structure)** | transientDefault/Empty checks | Unconditional branchless sequence | +25% (proven) |
| **String parsing (ASCII)** | SWAR 4-byte scan | Same (JVM platform split) | Even |
| **Float/double parsing** | rust-lexical moderate path | Same (ported) | Even |
| **Float/double writing** | xjb algorithm | Same (ported) | Even |
| **Integer writing** | Anhalt + SWAR setLong | Same (ported, JVM uses setLong) | Even |

**Projected combined JVM**: +15-36% reads, +32-40% writes vs jsoniter-scala native.

### Scala.js

| Operation | jsoniter-scala | sanely-zero (projected) | Note |
|---|---|---|---|
| **String parsing** | Byte-by-byte (no SWAR on JS) | Same | Even |
| **Number parsing/writing** | Same algorithms, no SWAR | Same | Even |
| **Product read/write** | (jsoniter-scala not commonly used on JS) | Our codec-layer wins apply | Advantage |
| **Integer writing** | Anhalt without setLong | Same — byte-by-byte digit pairs | Even |

On Scala.js, both libraries lose SWAR. The codec-layer advantages (direct constructors, branchless encoding, typed locals) are fully preserved. We should be competitive or ahead on JS.

---

## Part 6: Risk assessment

### High confidence (proven or trivially portable)

- [x] Direct constructor calls (+19% reads) — already implemented in sanely-jsoniter
- [x] Branchless product encoding (+25% writes) — already implemented
- [x] Hash-based field dispatch — already implemented
- [x] Typed local variables — already implemented
- [x] rust-lexical moderate path — pure arithmetic, direct port
- [x] xjb float/double writing — pure arithmetic, direct port
- [x] ThreadLocal pooling — trivial
- [x] JIT-friendly patterns — coding discipline

### Medium confidence (requires implementation + validation)

- [ ] Field-order prediction (+5-15% reads) — concept is sound, needs benchmarking to confirm real-world gain (depends on how often fields arrive in order)
- [ ] Compile-time key bytes (+5-10% writes) — straightforward but gain depends on key length distribution
- [ ] Pre-sized write buffers (+2-5% writes) — estimation accuracy affects gain
- [ ] VarHandle ByteArrayAccess on JVM — trivial to port, but we need to test Scala 3's interaction with VarHandle

### Lower confidence (speculative)

- [ ] Inlined integer parsing (+3-8% reads) — may bloat generated code; JIT might inline the method call anyway after warmup
- [ ] Fused key+value parsing (+2-5% reads) — for short keys only; benefit may be marginal
- [ ] Eliminated virtual dispatch (+2-5%) — Scala 3's `final class` + `inline` may already achieve this

### Risks

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| Float/double correctness bugs | Low (porting proven code) | High (wrong numbers) | Port test vectors alongside code. Property-based testing |
| UTF-8 handling bugs | Low (well-understood) | Medium (mojibake) | Port jsoniter's malformed UTF-8 test vectors |
| JIT regression from code size | Medium | Medium (slower than expected) | Profile method sizes, split hot paths |
| Scala.js perf gap vs JVM | Expected | Low (different target) | Accept JS is slower for SWAR-dependent paths |
| Macro expansion time regression | Low | Medium (slower builds) | Profile with SANELY_PROFILE, keep macros lean |

---

## Part 7: What we explicitly do NOT build

| Feature | Why not |
|---|---|
| InputStream/OutputStream streaming | HTTP frameworks buffer anyway. Add later if needed |
| Pretty printing | Not needed for wire format. Add later if needed |
| `Json` AST type | Against our philosophy. Direct bytes ↔ types only |
| Temporal types (java.time.*) | Out of scope. Users add their own codecs |
| UUID/Base64 built-in codecs | Out of scope. Users add their own codecs |
| Scala 2 support | Scala 3.8.2+ only |
| Custom number formatting | Shortest representation is the only correct choice |
| Configurable null handling per field | Circe writes all fields including nulls. Simpler = faster |

---

## Part 8: Success criteria

### Must achieve (non-negotiable)

1. **Correctness**: Pass all ported jsoniter-scala test vectors + JSONTestSuite conformance
2. **Circe wire compatibility**: Identical JSON output to circe for all types
3. **Zero dependencies**: Only `scala-library` and `scala3-library`
4. **Cross-platform**: JVM + Scala.js from the same source (minimal platform splits)

### Should achieve (target)

5. **JVM reads**: ≥ jsoniter-scala native throughput (currently +3% via sanely-jsoniter)
6. **JVM writes**: ≥ 20% faster than jsoniter-scala native (currently +25% via sanely-jsoniter)
7. **Allocations**: ≤ jsoniter-scala native per operation
8. **Compile time**: No regression vs sanely-jsoniter

### Nice to have

9. **JVM reads**: +15% over jsoniter-scala via field-order prediction + inlined parsing
10. **JSONTestSuite**: 100% conformance (all y_ pass, all n_ rejected)
11. **Scala.js**: Competitive with jsoniter-scala-js (no SWAR = level playing field)
