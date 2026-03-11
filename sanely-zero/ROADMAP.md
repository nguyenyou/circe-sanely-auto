# sanely-zero Roadmap

Zero-dependency JSON engine for Scala 3. Owns both the macro and the parser — enabling optimizations impossible when these layers are separate. Competes with jsoniter-scala on throughput while maintaining 100% circe wire compatibility and zero transitive dependencies.

## Phase 1 — Parser/Writer Core

Port proven primitives from jsoniter-scala (MIT licensed). Pure translation — no macro work yet. Target: ~1,500 lines of shared code + ~30 lines JVM-specific.

### Infrastructure

- [ ] **1.0: Project skeleton & platform split** — Set up `src/` (shared), `src-jvm/` (VarHandle), `src-js/` (platform flags). Verify cross-compilation builds with `./mill sanely-zero.jvm.compile` and `./mill sanely-zero.js.compile`. No logic yet, just the build wiring.
- [ ] **1.1: ByteArrayAccess** — Shared trait with byte-by-byte default impl (~40 lines). JVM impl using `VarHandle` for `getInt`/`getLong`/`setInt`/`setLong`/`setShort` (~30 lines). Verify JIT compiles VarHandle calls to single MOV instructions via JMH micro-benchmark.
- [ ] **1.2: ZeroPool (ThreadLocal reader/writer pooling)** — `ThreadLocal[ZeroReader]` and `ThreadLocal[ZeroWriter]` with 32KB byte buffer + 4KB char buffer that survive between calls. Entry points: `readFromArray[A]`, `readFromString[A]`, `writeToArray[A]`, `writeToString[A]`. On Scala.js, ThreadLocal is effectively a global.

### ZeroReader — Parsing

- [ ] **1.3: Reader skeleton** — `final class ZeroReader` with buffer management (`buf: Array[Byte]`, `pos: Int`, `charBuf: Array[Char]`). Token navigation: `skipWhitespace`, `nextToken`, `isNextToken`, `rollbackToken`. Error reporting with position context. ~100 lines.
- [ ] **1.4: Primitive number parsing** — `readInt`, `readLong`, `readFloat`, `readDouble`, `readBigDecimal`, `readBigInt`. Port jsoniter-scala's digit accumulation with overflow detection. Include branchless sign handling (`x ^= s; x -= s`). ~200 lines.
- [ ] **1.5: SWAR number parsing (JVM)** — Port 8-byte digit validation (`(bs + 0x46... | dec) & 0x80...`) and 8-digit integer parsing via magic multiplier (`dec *= 2561`). Platform split: SWAR on JVM, digit-by-digit on JS/shared. ~50 lines JVM-specific.
- [ ] **1.6: Float/double fast + moderate path** — Port rust-lexical moderate path: `pow10Mantissas` table (686 Long entries), `Math.multiplyHigh` for 128-bit multiplication, fast path for mantissa < 2^52. On Scala.js, implement `multiplyHigh` via two 64-bit multiplications. Slow-path fallback to `java.lang.Double.parseDouble`. ~200 lines.
- [ ] **1.7: String parsing** — `readString`, `readKeyAsCharBuf`, `charBufToHashCode`, `isCharBufEqualsTo`. Handle ASCII fast path (bulk copy), escape sequences (`\"`, `\\`, `\/`, `\b`, `\f`, `\n`, `\r`, `\t`, `\uXXXX`), surrogate pairs, and malformed UTF-8 rejection. ~150 lines.
- [ ] **1.8: SWAR string parsing (JVM)** — Port 4-byte-at-a-time scan: `((bs - 0x20202020 ^ 0x3C3C3C3C) - 0x1010101 | (bs ^ 0x5D5D5D5D) + 0x1010101) & 0x80808080`. If `m == 0`, all 4 bytes are plain ASCII → bulk copy. Consider borer's 8-byte variant for further throughput. ~30 lines JVM-specific.
- [ ] **1.9: Boolean, null, skip** — `readBoolean` (true/false keyword matching), `readNull` / `isCurrentNull`, `skip` (skip unknown JSON values including nested objects/arrays). Pre-computed literal byte arrays for `"ull"`, `"alse"`, `"rue"`. ~80 lines.
- [ ] **1.10: Structure navigation** — `readObjectFieldAsHash` (read key, compute hash, return length), `isNextKey(precomputedBytes)` (speculative O(1) memcmp for field-order prediction — the hook for Phase 4). `readArrayStart`, `readArrayEnd`, `readObjectStart`, `readObjectEnd`, `readComma`. ~80 lines.

### ZeroWriter — Serialization

- [ ] **1.11: Writer skeleton** — `final class ZeroWriter` with buffer management, auto-grow, `toByteArray`, `toString`. Structure tokens: `writeObjectStart`/`End`, `writeArrayStart`/`End`, `writeComma`, `writeColon`. ~100 lines.
- [ ] **1.12: Integer writing** — Port James Anhalt's algorithm: magic multiplier 8-digit extraction + `digits` lookup table (100 entries of 2-byte pairs). `writeVal(Int)`, `writeVal(Long)`. Branchless digit count via Daniel Lemire's `digitCount`. On JVM: `setLong` to write 8 bytes at once. On JS: byte-by-byte pairs. ~80 lines.
- [ ] **1.13: Float/double writing** — Port xjb algorithm: `floatPow10s` (77 entries), `doublePow10s` (1170 entries), `Math.multiplyHigh` for 128-bit multiplication. Produces shortest decimal that round-trips. `writeVal(Float)`, `writeVal(Double)`. ~200 lines.
- [ ] **1.14: String writing** — `writeVal(String)`, `writeKey(String)`, `writeNonEscapedAsciiKey(String)`. Handle escaping (control chars, `"`, `\`, non-ASCII as `\uXXXX`). Compile-time key classification (most keys are ASCII → fast path). ~80 lines.
- [ ] **1.15: Boolean, null, BigDecimal/BigInt writing** — `writeVal(Boolean)` (literal "true"/"false" bytes), `writeNull`, `writeVal(BigDecimal)`, `writeVal(BigInt)`. ~30 lines.
- [ ] **1.16: Raw byte writing** — `writeRawBytes(Array[Byte], offset, length)` for pre-computed key byte arrays (Phase 4 hook). `System.arraycopy` on JVM. ~10 lines.

### Phase 1 Testing

- [ ] **1.17: Integer parsing tests** — Port jsoniter-scala's test vectors (~40 cases): edge values (0, -1, Int.MaxValue, Int.MinValue, Long.MaxValue, Long.MinValue), overflow detection, leading zeros rejection. Add property-based tests (ScalaCheck or munit-scalacheck).
- [ ] **1.18: Float/double parsing tests** — Port ~60 test cases covering: fast path (simple decimals), moderate path (many significant digits), slow path (halfway points), special values (NaN rejection, Infinity rejection), subnormals. Property-based: `forAll((d: Double) => readDouble(writeDouble(d)) == d)`.
- [ ] **1.19: String parsing tests** — Port ~30 test cases: ASCII, Unicode, surrogate pairs, all escape sequences, malformed UTF-8 rejection, overlong encodings, null bytes. Property-based: `forAll((s: String) => readString(writeString(s)) == s)`.
- [ ] **1.20: Writer tests** — Port ~50 test cases across all write methods. Round-trip: `read(write(x)) == x` for all primitive types.
- [ ] **1.21: JSONTestSuite conformance** — Run all 318 tests from github.com/nst/JSONTestSuite: all `y_` inputs must parse successfully, all `n_` inputs must be rejected, `i_` inputs documented. This is the gold standard for parser correctness.

### Phase 1 Acceptance

- All tests pass on JVM and Scala.js
- `readFromArray(writeToArray(x)) == x` round-trips for: Int, Long, Float, Double, Boolean, String, BigDecimal, BigInt, null
- JSONTestSuite: 100% `y_` pass, 100% `n_` rejected
- No dependencies beyond `scala-library` and `scala3-library`

---

## Phase 2 — Macro Integration + Products

Wire the macro to generate codec code targeting `ZeroReader`/`ZeroWriter`. All proven sanely-jsoniter techniques carry over. The macro generates the encode/decode function bodies directly.

### Codec Trait & API Surface

- [ ] **2.0: ZeroCodec trait** — Define `trait ZeroCodec[A] { def encode(a: A, out: ZeroWriter): Unit; def decode(in: ZeroReader): A; def nullValue: A }`. This is our equivalent of jsoniter-scala's `JsonValueCodec[A]`. Final, no variance, no type class derivation overhead.
- [ ] **2.1: Built-in primitive codecs** — `ZeroCodec[Int]`, `ZeroCodec[Long]`, `ZeroCodec[Float]`, `ZeroCodec[Double]`, `ZeroCodec[String]`, `ZeroCodec[Boolean]`, `ZeroCodec[BigDecimal]`, `ZeroCodec[BigInt]`, `ZeroCodec[Byte]`, `ZeroCodec[Short]`, `ZeroCodec[Char]`. Direct `reader.readInt()` / `writer.writeVal(x)` calls, no intermediate dispatch.
- [ ] **2.2: Entry point API** — `SanelyZero.readFromArray[A](bytes)(using ZeroCodec[A]): A`, `SanelyZero.writeToArray[A](a)(using ZeroCodec[A]): Array[Byte]`, `readFromString`, `writeToString`. Uses `ZeroPool` internally.

### Product Derivation Macro

- [ ] **2.3: SanelyZeroCodec.derived macro** — `inline def derived[A](using inline m: Mirror.ProductOf[A]): ZeroCodec[A]`. Recursive resolution via `Expr.summonIgnoring` (same pattern as sanely-jsoniter). Cache `Map[String, Expr[ZeroCodec[_]]]` keyed by `cheapTypeKey`.
- [ ] **2.4: Product encoding** — Generate direct field access: `out.writeObjectStart(); out.writeKey("name"); out.writeVal(x.name); ...out.writeObjectEnd()`. Branchless unconditional sequence (no transientDefault/transientEmpty checks — circe writes all fields). Pre-compute `isEncodingRequired` at macro time.
- [ ] **2.5: Product decoding — typed locals** — Generate `var _name: String = null; var _age: Int = 0` for each field. No `Array[Any]`, no boxing for primitives. Default values resolved at compile time via companion `$lessinit$greater$default$N`.
- [ ] **2.6: Product decoding — hash-based field dispatch** — Generate `readKeyAsCharBuf` + `charBufToHashCode` + `(hash: @switch) match { ... }` with `isCharBufEqualsTo` verification on collision. For ≤8 fields with total name length ≤64: linear if-else chain instead.
- [ ] **2.7: Product decoding — direct constructor call** — Generate `new P(_f0, _f1, _f2)` instead of `mirror.fromProduct(ArrayProduct(...))`. Zero allocation, zero boxing. This is the +19% read advantage proven in sanely-jsoniter.
- [ ] **2.8: Product decoding — direct primitive reads** — Generate `_age = reader.readInt()` instead of `codec.decode(reader)` for primitive fields. No virtual dispatch, no codec trait indirection.
- [ ] **2.9: Opaque type support** — `opaqueDealias` using `translucentSuperType` to resolve codecs through opaque boundaries. Both encoding and decoding paths.

### Runtime Support

- [ ] **2.10: ZeroRuntime** — `productCodec[P]` factory wrapping macro-generated encode/decode lambdas with lazy initialization for recursive types. Mirrors `JsoniterRuntime.productCodec` pattern.

### Phase 2 Testing

- [ ] **2.11: Product encode/decode tests** — Simple case classes (0-field, 1-field, 20+ fields), nested products, products with all primitive types, products with String/Option/List fields.
- [ ] **2.12: Round-trip tests** — `decode(encode(x)) == x` for all product test types. Compare output bytes against circe's output for the same types (wire compatibility).
- [ ] **2.13: Recursive type tests** — Self-referential case classes (e.g., `case class Tree(value: Int, children: List[Tree])`).
- [ ] **2.14: Opaque type tests** — Opaque type wrapping primitives, Strings, and containers.

### Phase 2 Acceptance

- Products with all primitive fields encode/decode correctly
- Output matches circe byte-for-byte for the same types
- Recursive types work (lazy codec initialization)
- Opaque types work
- No boxing for primitive fields (verify via `-Xprint:all` or bytecode inspection)

---

## Phase 3 — Containers + Sum Types + Configured

Complete type coverage to match sanely-jsoniter's feature set.

### Container Codecs

- [ ] **3.0: Option codec** — `None` → `null`, `Some(x)` → encode `x`. Matches circe: `null` for None, field always present.
- [ ] **3.1: Collection codecs** — `List`, `Vector`, `Set`, `Seq`, `IndexedSeq`, `Iterable`, `Array`. JSON array format. Iterator-based encoding (while-loop, no `foreach` allocation). Builder-based decoding.
- [ ] **3.2: Map codecs** — `Map[String, V]` → JSON object. `Map[K, V]` where K is not String → JSON array of `[key, value]` pairs (matching circe). Requires `ZeroKeyCodec[K]` for non-String keys.
- [ ] **3.3: Either codec** — `Left(x)` → `{"Left": x}`, `Right(x)` → `{"Right": x}`. Matches circe's external tagging.
- [ ] **3.4: Tuple codecs** — JSON array format: `(a, b)` → `[a, b]`. Specialized codecs for arity 1-5, generic `Tuple.fromArray`-based for arity 6-22. Matches circe's tuple encoding.
- [ ] **3.5: ZeroKeyCodec trait** — `trait ZeroKeyCodec[K] { def encodeKey(k: K, out: ZeroWriter): Unit; def decodeKey(in: ZeroReader): K }`. Built-in for String, Int, Long, UUID, etc.

### Sum Type Derivation

- [ ] **3.6: Sum type encoding — external tagging** — `{"VariantName": {...}}`. Macro generates `mirror.ordinal(x)` dispatch + per-variant encoder. Matches circe default.
- [ ] **3.7: Sum type decoding — external tagging** — Read single-key object, dispatch on key string to variant decoder. Hash-based dispatch for many variants, linear for few.
- [ ] **3.8: Sub-trait hierarchy support** — Nested sealed traits with diamond dedup. Flatten leaf codecs for decoding, direct codecs for encoding. Mirrors sanely-jsoniter's `sumCodecWithSubTraits` pattern.
- [ ] **3.9: Enum string codec** — Singleton-only enums as `"VariantName"` strings. Port `SanelyEnumCodec` / `SanelyJsoniterEnum` pattern.

### Configured Derivation

- [ ] **3.10: ZeroConfiguration** — `case class ZeroConfiguration(transformMemberNames, transformConstructorNames, useDefaults, discriminator, strictDecoding, dropNullValues)`. Mirrors `io.circe.derivation.Configuration` and `JsoniterConfiguration`.
- [ ] **3.11: Configured encoder** — `SanelyZeroConfiguredEncoder.derived[A]`. Threads `Expr[ZeroConfiguration]` through the macro. Transform member names, transform constructor names, drop null values.
- [ ] **3.12: Configured decoder** — `SanelyZeroConfiguredDecoder.derived[A]`. Defaults, discriminator (None → external tagging, Some(d) → flat with field), strict decoding (reject unknown keys).
- [ ] **3.13: Configured codec (single-pass)** — `SanelyZeroConfiguredCodec.derived[A]`. Single macro expansion sharing cache between encoder and decoder. Mirrors `SanelyConfiguredCodec` pattern.
- [ ] **3.14: Discriminator-first encoding** — When discriminator is configured, always emit discriminator field first. This enables fast-path decoding (discriminator at position 0).
- [ ] **3.15: Null → default handling** — When `useDefaults=true` and JSON has `"field": null` for a non-Option field with a Scala default, use the default value.
- [ ] **3.16: Strict decoding for sum types** — Reject multi-key objects when `strictDecoding=true`. Post-decode key validation.

### Wrapper Classes & derives Support

- [ ] **3.17: ZeroCodecDerives wrapper classes** — `ZeroCodec.WithDefaults`, `WithDefaultsDropNull`, `WithSnakeCaseAndDefaults`, etc. Enable `derives ZeroCodec.WithDefaults` syntax. Thin wrapper + inline `.derived` that sets `given ZeroConfiguration` then calls core macro.
- [ ] **3.18: Auto-configured derivation** — `sanely.zero.configured.auto.given` with a `given ZeroConfiguration` in scope. Mirrors sanely-jsoniter's auto-configured pattern.
- [ ] **3.19: Semi-auto API** — `deriveZeroEncoder`, `deriveZeroDecoder`, `deriveZeroCodec`, `deriveZeroConfiguredCodec`, `deriveZeroEnumCodec`. Explicit derivation for users who prefer semi-auto.

### Phase 3 Testing

- [ ] **3.20: Container codec tests** — All container types with various element types. Empty containers, single-element, large collections.
- [ ] **3.21: Sum type tests** — External tagging, discriminator tagging, sub-trait hierarchies, enum codecs. Match circe output byte-for-byte.
- [ ] **3.22: Configured derivation tests** — All configuration combinations: defaults, discriminator, snake_case, strict, drop-null. Combined configs (defaults+discriminator, defaults+snake_case+drop-null).
- [ ] **3.23: Wire compatibility suite** — For every type in the circe compat test suite, verify `sanelyZeroEncode(x) == circeEncode(x)` and `sanelyZeroDecode(json) == circeDecode(json)`. This is the non-negotiable contract.

### Phase 3 Acceptance

- Feature parity with sanely-jsoniter (all codec types, all configuration options)
- 100% wire compatibility with circe for all supported types
- All sanely-jsoniter tests pass with equivalent sanely-zero codecs
- Zero dependencies (no circe-core, no jsoniter-scala, no cats)

---

## Phase 4 — Unique Optimizations (the moat)

Techniques only possible because we own both macro AND parser. These are the competitive advantages over jsoniter-scala.

### Tier 0 — High Impact, Proven Elsewhere

- [ ] **4.0: Field-order prediction (speculative sequential access)** — After reading field N, speculatively check if next key matches field N+1 (declaration order) via `reader.isNextKey(precomputedKeyBytes)` — O(1) memcmp. If hit → skip hash computation entirely. If miss → fall back to hash dispatch. Source: zio-blocks (by Plokhotnyuk). Expected: **+5-15% reads** (machine-generated JSON almost always emits fields in order).
- [ ] **4.1: Golden mask bitmask validation** — Each field gets a bit position. `var seen: Long = 0L`, per field: `seen |= (1L << idx)`. After all fields: `if (seen & REQUIRED_MASK != REQUIRED_MASK) missingFieldException(seen, REQUIRED_MASK)`. Replaces N boolean checks with 1 Long + 1 AND + 1 comparison. For >64 fields: dual-Long (supports up to 128). Source: kotlinx.serialization + zio-blocks. Expected: faster required-field checks, cleaner generated code.
- [ ] **4.2: Bitset missing field tracking** — Use the golden mask to track which fields still need defaults filled. `numberOfTrailingZeros` iteration over remaining set bits. Replaces N boolean checks with 1-2 integer operations. Source: zio-blocks.

### Tier 1 — Strong ROI

- [ ] **4.3: Compile-time key byte arrays** — Macro pre-computes `val KEY_NAME = Array[Byte]('"','n','a','m','e','"',':')` for each field. Encoding emits `System.arraycopy(KEY_NAME, 0, buf, pos, 7)` instead of per-char string processing. Expected: **+5-10% writes**.
- [ ] **4.4: Raw byte hashing (skip charBuf for ASCII keys)** — For ASCII-only keys (known at compile time via `isEncodingRequired`), hash raw UTF-8 bytes directly instead of reading into charBuf then hashing chars. Eliminates byte→char conversion. Source: DSL-JSON. Expected: **+2-5% reads**.
- [ ] **4.5: SWAR container skipping** — Skip unknown JSON values by counting brackets 8 bytes at a time: `popcnt(mask_open) - popcnt(mask_close)` applied to SWAR bitmask. 8x fewer iterations for nested containers. Source: sonic-rs. Expected: faster `skip()` for unrecognized fields.
- [ ] **4.6: Pre-sized write buffers** — Macro estimates minimum output size from field count + type info (e.g., a 5-field product with Int fields needs ≥ `2 + 5*(maxKeyLen+1+11+1)` bytes). Pre-allocate once, avoid grow-and-copy. Expected: **+2-5% writes**.

### Tier 2 — Worth Benchmarking

- [ ] **4.7: Fused key+value parsing** — For short keys (≤8 bytes), compare key as a single Long instead of charBufToHashCode + isCharBufEqualsTo. Inline: `if (getLong(buf, pos) == KEY_NAME_LONG) { pos += 8; _name = readString() }`. Expected: **+2-5% reads** for short-key types.
- [ ] **4.8: Inlined integer parsing** — Instead of `_age = reader.readInt()`, generate the 10-line digit loop directly in the codec. Eliminates method call overhead and JIT warmup dependency. Risk: may bloat generated code. Benchmark before committing.
- [ ] **4.9: 8-byte SWAR string parsing** — Port borer's 8-byte-at-a-time variant (vs jsoniter-scala's 4-byte). Uses `numberOfLeadingZeros` to find valid byte count. Source: borer. Benchmark vs 4-byte to confirm improvement.
- [ ] **4.10: Sequential decode fast path (opt-in)** — Specialized codec mode where fields MUST arrive in declaration order. Skips key reading entirely — just reads values in sequence. Source: kotlinx.serialization. Opt-in via annotation or configuration (breaks for out-of-order JSON).

### Tier 3 — Nice to Have

- [ ] **4.11: Native JSON.parse() on Scala.js** — Optional fast path: delegate to browser's `JSON.parse()` → traverse JS object → domain type. Bypasses our custom parser entirely on JS for 4-6x speedup. Source: upickle. Trade-off: JS-specific code path.
- [ ] **4.12: Speculative writes** — Write ALL 8 characters BEFORE checking how many are valid (borer pattern). Eliminates instruction dependencies on superscalar CPUs. Minor but measurable.
- [ ] **4.13: StringMatrix field matching** — zio-json's bitset trie for progressive field filtering. Interesting for types with 20+ fields or long field names. Benchmark against hash dispatch.

---

## Phase 5 — Security, Polish & Publishing

### Security

- [ ] **5.0: BigDecimal/BigInt limits** — Configurable `bigDecimalPrecision`, `bigDecimalScaleLimit`, `bigDecimalDigitsLimit`, `bigIntDigitsLimit`. Prevent DoS via pathologically large numbers.
- [ ] **5.1: Collection size limits** — Configurable `mapMaxInsertNumber`, `setMaxInsertNumber`. Prevent memory exhaustion from huge collections.
- [ ] **5.2: Nesting depth limit** — Maximum recursion depth for nested objects/arrays. Prevent stack overflow from deeply nested payloads.
- [ ] **5.3: Discriminator-first enforcement** — When decoding sum types with discriminator, optionally require discriminator as the first field. Prevents DoS via discriminator at end of large objects.

### Error Reporting

- [ ] **5.4: Position-aware errors** — Error messages include byte offset, line number, column number. Show surrounding context (e.g., `"unexpected 'x' at position 42, line 3, column 7"`).
- [ ] **5.5: Missing field errors** — List all missing required fields in a single error, not one-at-a-time. Use golden mask `numberOfTrailingZeros` iteration.
- [ ] **5.6: Type mismatch errors** — Clear messages: `"expected Int but got String at field 'age'"`.

### API & Publishing

- [ ] **5.7: Circe bridge** — Optional `circe-sanely-zero` bridge module: `ZeroCodec[A] => Encoder[A]` and `ZeroCodec[A] => Decoder[A]` adapters. Enables gradual migration from circe. This module DOES depend on circe-core.
- [ ] **5.8: Drop-in semiauto API** — `io.circe.generic.zero.semiauto.deriveEncoder`, `deriveDecoder`, `deriveCodec` that return `ZeroCodec`-based instances behind circe's `Encoder`/`Decoder` interface.
- [ ] **5.9: Publish to Maven Central** — JVM + Scala.js artifacts via `SonatypeCentralPublishModule`. Artifact: `io.github.nguyenyou::sanely-zero`.
- [ ] **5.10: Documentation** — README with usage examples, benchmark tables, migration guide from circe and from jsoniter-scala.

---

## Benchmarking Milestones

Run at each phase boundary to track progress and catch regressions.

### After Phase 1

- [ ] **B1: Micro-benchmarks** — JMH benchmarks for: `readInt`, `readDouble`, `readString`, `writeVal(Int)`, `writeVal(Double)`, `writeVal(String)`. Compare against jsoniter-scala's reader/writer. Target: parity or better on JVM.

### After Phase 2

- [ ] **B2: Product codec benchmark** — Encode/decode a representative product (5-10 fields, mixed types). Compare ops/sec against: (a) jsoniter-scala native, (b) sanely-jsoniter, (c) circe-jawn. Target: ≥ jsoniter-scala on reads, +20% on writes.

### After Phase 3

- [ ] **B3: Full codec suite benchmark** — Products, sum types, containers, configured types. Compare against same three baselines. Update README with benchmark tables.

### After Phase 4

- [ ] **B4: Field-order prediction benchmark** — Measure impact of speculative sequential access on ordered vs unordered payloads. Target: +5-15% on ordered, no regression on unordered.
- [ ] **B5: Compile-time benchmark** — Measure macro expansion time for ~200 types. Compare against sanely-jsoniter. Target: no regression (same macro architecture).

---

## Cross-Cutting Concerns

### Scala.js

- All shared code must compile on Scala.js without platform-specific imports
- SWAR optimizations in `src-jvm/` only; shared code uses byte-by-byte
- `Math.multiplyHigh` needs JS polyfill (two 64-bit multiplications)
- `Long.MaxValue`/`Long.MinValue` precision tests skipped on JS (JSON number limitation)
- ThreadLocal on JS is effectively a global (single-threaded runtime)

### Wire Compatibility Contract

- Every encoded value must be byte-identical to what circe produces for the same input
- Every decoded value must be identical to what circe produces for the same JSON
- Error messages for invalid JSON should be informative but need not match circe's exact text
- This contract is NON-NEGOTIABLE — any deviation is a bug blocking release

### Compile Time

- Macro expansion time must not regress vs sanely-jsoniter
- Profile with `SANELY_PROFILE=true` after macro changes
- Keep macros lean: pre-compute at compile time, minimize generated code size
- Monitor JIT regression risk from code bloat (Tier 2 inlining optimizations)

---

## Dependencies

**sanely-zero core**: Zero. Only `scala-library` and `scala3-library`.

**sanely-zero test**: `munit`, `munit-scalacheck` (property-based), `circe-core` + `circe-parser` (wire compatibility verification), `jsoniter-scala-core` (baseline comparison).

**circe-sanely-zero bridge** (optional separate module): `circe-core`.

---

## Success Criteria

### Must achieve (non-negotiable)

1. **Correctness**: Pass all ported jsoniter-scala test vectors + JSONTestSuite 318-test conformance
2. **Circe wire compatibility**: Identical JSON output to circe for all types
3. **Zero dependencies**: Only `scala-library` and `scala3-library`
4. **Cross-platform**: JVM + Scala.js from the same source

### Should achieve (target)

5. **JVM reads**: ≥ jsoniter-scala throughput
6. **JVM writes**: ≥ 20% faster than jsoniter-scala
7. **Allocations**: ≤ jsoniter-scala per operation
8. **Compile time**: No regression vs sanely-jsoniter

### Stretch goals

9. **JVM reads**: +15% over jsoniter-scala via field-order prediction
10. **JSONTestSuite**: 100% conformance (all `y_` pass, all `n_` rejected)
11. **Scala.js**: Competitive with jsoniter-scala-js
