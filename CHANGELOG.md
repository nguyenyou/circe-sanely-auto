# Changelog

## [0.22.0] - Unreleased

### sanely-jsoniter

#### Added
- **`WithDefaultsAndTypeName` derives wrapper** — `derives JsoniterCodec.WithDefaultsAndTypeName` for sealed traits using `withDefaults + withDiscriminator("__typename__")`. Common pattern for flat ADT encoding in large codebases.

#### Fixed
- **Abstract type member fields** (e.g. zio-prelude `Subtype`/`Newtype`) — `deriveJsoniterCodec` and `deriveJsoniterConfiguredCodec` now compile for case classes containing fields whose types are abstract type members. Previously, the macro emitted bare `null` as the initial value for mutable decode vars, which Scala 3 rejected with `Found: Null, Required: T`. Now emits `null.asInstanceOf[T]`. This affects zio-prelude's `Subtype[String]`, `Newtype[Int]`, and any other library using abstract type members for zero-cost wrappers.
- **AnyVal case class fields** (e.g. `case class FileId(id: String) extends AnyVal`) — same root cause and fix. Fields of AnyVal wrapper types no longer produce compile errors during codec derivation.

## [0.21.0] - 2026-03-12

### sanely-jsoniter

#### Performance
- **Inline `Option[prim]` and `List[prim]` writes** — the macro now generates inline write code for `Option[T]` and `List[T]` where `T` is a directly-writable primitive (Int, Long, Float, Double, Boolean, Short, Byte, Char, String, BigDecimal, BigInt). Instead of dispatching through `codecs(idx).encodeValue(...)` (virtual call), the generated code writes values directly: `if _o.isDefined then out.writeVal(_o.get) else out.writeNull()` for Option, and a while-loop with `out.writeVal(_c.head)` for List. Eliminates ~8 virtual dispatches per `ApiResponse` encode in the benchmark payload.
- **Write throughput**: 1,194,849 ± 15,503 ops/s (+21% vs jsoniter-scala native, up from +19% in v0.20.0)
- **Read throughput**: 753,375 ± 11,453 ops/s (+10% vs jsoniter-scala native)
- **Allocation**: 1,432 B/op (no regression vs v0.20.0)

#### Changed
- Applied inline container writes to all three macro write paths: `tryDirectWriteTerm` (non-configured), `tryDirectWrite` (configured), and `tryDirectWriteDropNull` (configured drop-null)

## [0.20.0] - 2026-03-11

### Added
- **JMH benchmark module** (`benchmark-jmh/`) — rigorous runtime benchmarking via Mill's `contrib.jmh.JmhModule` (JMH 1.37). Fork isolation, compiler blackholes, 99.9% CI error reporting. 8 benchmarks: 4 read + 4 write across circe-jawn, circe+jsoniter, sanely-jsoniter, and jsoniter-scala.
- **CI runtime benchmarks** — GitHub Actions benchmark workflow now runs JMH for runtime performance (`./mill benchmark-jmh.runJmh`), replacing the hand-rolled harness.

### Fixed
- **12 non-exhaustive match warnings** in sanely-jsoniter macros — added wildcard cases to `asType` pattern matches (5 tuple + 1 Either per file in `SanelyJsoniter.scala` and `SanelyJsoniterConfigured.scala`) and Lambda bodies in `SanelyJsoniterValueEnum.scala`.

### Changed
- **Benchmark summary script** (`scripts/summarize_benchmark.py`) rewritten to parse JMH output format. Supports all 4 approaches with error bars and ratio computation.
- Updated README runtime table with JMH numbers (5.6x faster reads, 8.9x faster writes).
- Updated runtime-benchmark skill with JMH commands and expected ratios.

## [0.19.0] - 2026-03-11

### sanely-jsoniter

#### Fixed
- **(P0) Opaque type support** — resolve codecs through opaque type boundaries using `translucentSuperType`. Fixes `nullValue`, read, write, and default value handling for opaque-wrapped primitives (e.g., `opaque type UserId = String`) and opaque containers (e.g., `opaque type Tags = List[String]`). Both non-configured and configured macro paths.
- **(P0) Tuple encoding as JSON arrays** — tuples are now encoded as `[a, b, ...]` matching circe format, instead of `{"_1": a, "_2": b}`. Specialized codecs for arity 1-5, generic `Tuple.fromArray`-based codec for arity 6-22.
- **(P1) Null → default** — when `useDefaults=true` and JSON contains `"field": null` for a non-Option field with a Scala default, the default value is now used instead of assigning null. Uses `isNextToken('n')` + `rollbackToken()` + `skip()` to detect and consume null tokens before the field read.
- **(P1) External Map given respected** — user-provided `given JsonValueCodec[Map[K,V]]` is now checked via `Expr.summonIgnoring` before the macro decomposes Map into key/value resolution. Custom map codecs (e.g., array-of-pairs encoding) are correctly used in derived codecs.

#### Added
- **(P3) Public primitive codecs** — `import sanely.jsoniter.Codecs.given` puts `JsonValueCodec` instances for all 11 primitive types in scope (`Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `String`, `BigDecimal`, `BigInt`, `Char`).

### Docs
- Updated jsoniter marginal compile-time cost in README (1.9x → 1.05x, reflecting cumulative perf improvements since v0.15.0)

## [0.18.0] - 2026-03-08

### sanely-jsoniter

#### Performance
- **P3.7: Container codec cleanups** — cached delegate codecs in `seq`/`indexedSeq`/`iterable` (was allocating fresh codec on every `decodeValue` call); replaced `foreach` closures with iterator while-loops in all container encoders (`vector`, `seq`, `indexedSeq`, `iterable`, `set`, `map`, `stringMap`). Read throughput **+15%** (661K → 762K ops/sec, 5.7x vs circe-jawn).

## [0.17.0] - 2026-03-08

### sanely-jsoniter

#### Added
- **Iterable and Array container codecs** — `Iterable[T]` and `Array[T]` are now supported as container types in jsoniter codec derivation
- **IndexedSeq codec support** — `IndexedSeq[T]` is now correctly derived

#### Performance
- **P3.5: Direct constructor call for product decode** — `new P(_f0, _f1, ...)` instead of `mirror.fromProduct(ArrayProduct(Array(...)))`, eliminating primitive boxing and two intermediate allocations per product decode
- **P3.6: charBuf dispatch for sum type decode** — sum type discriminator matching now uses `charBufToHashCode`-based dispatch instead of string comparison

#### Fixed
- **Resolve case classes inside containers for auto derivation** — `List[MyCaseClass]`, `Option[MyCaseClass]` etc. now correctly auto-derive the inner case class codec instead of failing
- **ScopeException from JAR** — switched to Term-based reflect API to fix `ScopeException` when deriving codecs for types loaded from external JARs

## [0.16.0] - 2026-03-07

### Performance
- **Constructor-level negative cache (P2)** — added `constructorNegCache` across all 6 macro files. When a type constructor (e.g., `Paginated`) is first encountered and `summonIgnoring` returns `None`, the constructor is cached. Subsequent encounters with different type args (e.g., `Paginated[Money2]`, `Paginated[MetricVal]`) skip `summonIgnoring` entirely (~5.5ms saved per skip). 142 cache hits across 398 macro expansions in the benchmark suite.
- **Emit `val` instead of `lazy val` for non-recursive types (P3)** — added `MacroUtils.isRecursiveType` pre-check that traverses the type graph to detect if `selfRef` would ever be used. Non-recursive types (the vast majority) now return the encoder/decoder expression directly — no `lazy val`, no `LazyRef`, no `lzyINIT` method. Reduces bytecode size and method count.
- **Auto benchmark**: 2.56s, **3.6x faster** than circe-generic (9.25s) — was 2.11s/2.9x in v0.15.0
- **Configured benchmark**: 1.27s, **2.2x faster** than circe-core (2.74s) — was 1.39s/1.9x in v0.15.0
- **Compiler work**: -47% (825 vs 1,558 samples)
- **Memory**: -63% allocations, -21% peak RSS (893 MB vs 1,133 MB)
- **Bytecode**: -22% total bytes, -9.7% methods (auto derivation)

### Fixed
- **Discriminator slow path fails on null field values** — configured decoder no longer crashes when a JSON object has null-valued fields during discriminator-based sum type decoding

## [0.15.0] - 2026-03-07

### sanely-jsoniter — new module

**`sanely-jsoniter`** generates `JsonValueCodec[T]` instances that skip circe's `Json` AST entirely, streaming bytes directly to/from domain objects via jsoniter-scala. Produces circe-compatible JSON on the wire.

#### Features
- Product types, sum types (external tagging), enums, recursive types, sub-trait hierarchies (diamond dedup)
- Either codec (`{"Left": v}` / `{"Right": v}`)
- Non-string map keys via `KeyCodec[K]`
- Value enum codecs — macro-derived (`deriveJsoniterValueEnumCodec`) and manual (`Codecs.stringValueEnum` / `Codecs.intValueEnum`)
- Configured derivation: `withDefaults`, `withDiscriminator`, `withSnakeCaseMemberNames`, `withDropNullValues`, `withTransformMemberNames` / `withTransformConstructorNames`, `withStrictDecoding`
- Auto-configured derivation: `import sanely.jsoniter.configured.auto.given` with a `given JsoniterConfiguration` in scope
- `derives` syntax: `JsoniterCodec`, `JsoniterCodec.WithDefaults`, `WithDefaultsDropNull`, `WithSnakeCaseAndDefaults`, `WithSnakeCaseAndDefaultsDropNull`, `Enum`, `ValueEnum`
- Scala.js cross-platform support

#### Performance (P3.1–P3.4)
- **P3.1: Inline encode with direct field access** — `x.name` instead of `x.productElement(i)`, eliminating boxing
- **P3.2: Direct primitive write calls** — `out.writeVal(x.age)` instead of `codec.encodeValue(x.age, out)` for primitives
- **P3.3: Inline decode with typed locals** — `var _name: String = null; var _age: Int = 0` instead of `Array[Any]` boxing
- **P3.4: Hash-based field key dispatch** — `(in.charBufToHashCode(l): @switch) match { ... }` with compile-time pre-computed hashes for types with > 8 fields; small types keep linear if-else chain
- **Branchless product encoding** — unconditional write-key/write-value sequence with no per-field conditional checks

#### Benchmark results
- **4.8x** faster reads, **6.2x** faster writes vs circe-jawn (~1.4 KB payload)
- **98%** of jsoniter-scala native on decode, **surpasses it by 6%** on encode
- Write advantage: branchless field writing beats jsoniter-scala's per-field conditional branches (`transientNone`, `transientEmpty`, `transientDefault`)

#### Tests
- 80 unit tests (JVM), 91 unit tests (Scala.js)
- 8 Tapir integration tests proving HTTP codec swap works end-to-end

### Fixed
- **Respect custom instances inside containers** — `List[T]`, `Option[T]`, `Map[K, T]` now correctly use user-provided `Encoder[T]`/`Decoder[T]` instances instead of re-deriving

### Changed
- Compat tests auto-generated from circe upstream via git submodule + sync script
- Tapir integration test module added (`tapir-test/`)
- Runtime benchmark module added (`benchmark-runtime/`)
- README rewritten with compile + runtime performance story

## [0.14.0] - 2026-03-06

### Performance
- **Codec-first `summonIgnoring` fast path** — In `SanelyConfiguredCodec`, `resolveOneCodec` now tries a single `Expr.summonIgnoring[Codec.AsObject[T]]` before falling back to separate `Encoder[T]` + `Decoder[T]` calls. When the nested type has a user-provided `Codec.AsObject[T]` in scope (common in configured derivation where companions define `given Codec.AsObject[...] = deriveConfiguredCodec`), one implicit search replaces two. 118 of 147 type pairs resolved with a single call. Applied only to configured codec derivation — auto derivation has no explicit codec instances so the summon would always fail.
- **Configured benchmark**: `summonIgnoring` calls reduced from 294 to 205 (-30%), summonIgnoring time reduced by 13%, total macro time reduced by 8%

## [0.13.0] - 2026-03-06

### Performance
- **Factory method consolidation** — extracted 11 factory methods into `SanelyRuntime` that define `Encoder`/`Decoder`/`Codec` anonymous class templates once, replacing per-expansion anonymous class generation across all 6 macro files. Type-specific data (field names, encoder/decoder arrays) passed as parameters. Lazy initialization via `() => Array[Encoder[Any]]` lambdas compile to `invokedynamic` (not anonymous classes). By-name `mirror` parameters avoid eager initialization cycles with local types. Dramatically reduces transform+backend compiler phases — from heavier than circe to lighter.
- **Deduplicate `dealias` calls** — `TypeRepr.of[T].dealias` was called 3+ times for the same type in each resolver. Now computed once and threaded through all consumers. Eliminates ~2800 redundant dealias calls.
- **Profile untimed overhead** — instrumented `cheapTypeKey`, `tryBuiltin`, `containsType`, `selfCheck`, `dealias`. Found remaining ~230ms overhead is intrinsic to Scala 3's quote reflection (tuple recursion, AST construction). No actionable bottleneck.
- **Auto benchmark**: 2.23s median, **3.13x faster** than circe-generic (6.98s) — was 3.57s/1.99x in v0.12.0
- **Configured benchmark**: 1.47s median, **1.97x faster** than circe-core (2.89s) — was 2.18s/1.35x in v0.12.0
- **Compiler work**: auto -48% (806 vs 1542 samples), configured -23% (621 vs 805 samples)
- **Memory**: auto -51% allocations, -20% peak RSS (769 MB vs 963 MB)

### Changed
- All 6 macro files (`SanelyEncoder`, `SanelyDecoder`, `SanelyCodec`, `SanelyConfiguredEncoder`, `SanelyConfiguredDecoder`, `SanelyConfiguredCodec`) now generate factory calls instead of anonymous class definitions
- `SanelyRuntime` expanded with 11 factory methods: `productEncoder`, `productDecoder`, `productCodec`, `sumEncoder`, `sumDecoder`, `sumCodec`, `configuredProductDecoder`, `configuredProductCodec`, `configuredSumEncoder`, `configuredSumDecoder`, `configuredSumCodec`
- README restructured: key numbers and migration steps moved to top ("Why" section)

### CI
- Release tests now run in parallel via matrix strategy

## [0.12.0] - 2026-03-06

### Performance
- **Eliminate redundant sub-trait `summonIgnoring` calls** — sub-trait detection in `deriveSum` used to call `Expr.summonIgnoring` again for each variant to check for user-provided instances, despite `resolveOneEncoder`/`resolveOneDecoder` already having this information. Added `summonedKeys: mutable.Set[String]` that records cache keys when `summonIgnoring` returns `Some`. Sub-trait detection now checks this set (O(1) lookup) instead of re-calling the compiler's implicit search. Configured sub-trait detection time reduced by 90% (0.29ms → 0.03ms per call).
- **Negative builtin cache** — when `tryResolveBuiltinEncoder`/`Decoder` returns `None`, the type key is added to `negativeBuiltinCache`. Subsequent calls skip the entire builtin check (10 `=:=` comparisons + container pattern matching). Inner container arg resolution also uses the negative cache to skip `resolvePrimEncoder` for known non-primitives.
- **Cheaper cache key** — replaced `tpe.dealias.show` (expensive pretty-printer) with `MacroUtils.cheapTypeKey` using `typeSymbol.fullName` (simple property lookup). Handles `AppliedType` recursively, `TermRef` via `termSymbol.fullName`, and `ConstantType` via `c.show`. Falls back to `.show` only for exotic types.
- **Auto benchmark**: 3.57s median, 1.99x faster than circe-generic (7.09s)
- **Configured benchmark**: 2.18s median, 1.35x faster than circe-core (2.94s)

### Changed
- All 6 macro derivation files updated: `SanelyEncoder`, `SanelyDecoder`, `SanelyCodec`, `SanelyConfiguredEncoder`, `SanelyConfiguredDecoder`, `SanelyConfiguredCodec`

## [0.11.0] - 2026-03-05

### Performance
- **Single-pass codec derivation** — `deriveCodec` and `deriveConfiguredCodec` now derive both encoder and decoder in a single macro expansion instead of composing two separate expansions. Shares mirror summoning, `containsType` traversal, `summonIgnoring` calls, builtin resolution, and expression cache across encoder+decoder derivation. Reduces configured derivation macro expansions from 460 to 230 and sub-trait detections from 138 to 69.
- **Auto benchmark**: 3.91s median (was 3.17s after v0.8.0 optimizations), 1.77x faster than circe-generic (6.94s)
- **Configured benchmark**: 2.08s median (was 2.90s), 1.38x faster than circe-core (2.86s)

### Changed
- `SanelyCodec.scala` rewritten from 17-line composer to full `CodecDerivation` macro (~650 lines) with shared `exprCache` storing `(Expr[Encoder[?]], Expr[Decoder[?]])` pairs
- `SanelyConfiguredCodec.scala` rewritten from 19-line composer to full configured `CodecDerivation` macro (~760 lines) with configuration threading (transforms, defaults, strict decoding, discriminator)

## [0.10.0] - 2026-03-05

### Added
- **Literal/singleton type support** — detect `ConstantType` (literal `"hello"`, `42`, `true`, etc.) at compile time; encoders emit the literal value directly, decoders validate the exact value and produce typed error messages on mismatch.
- **Circe-compatible error messages** — decoder errors now match circe's format: `WrongTypeExpectation` for non-object inputs, typed "has no class/object/case named" messages, "could not find discriminator field" format, strict decoding with type/field names.
- **Builtin key encoder/decoder resolution** — `KeyEncoder`/`KeyDecoder` for `String`, `Int`, `Long`, `Double`, `Short`, `Byte` are now resolved directly without implicit search. Extends container+builtin composition from `Map[String, V]` to `Map[K, V]` for all builtin key types.
- **Full compat test coverage** — 318 compatibility tests (up from 253), covering auto-derivation (Box, Vegetable, RecursiveEnum, TaggedMember, LongSum, LongEnum), semiauto (SFoo/SBar composition, local classes, strict val evaluation), configured (exact error messages, DownField paths, field name collision hierarchies), and configured enum (compileErrors test).
- 6 new unit tests: literal type fields (string, int, boolean, long) with rejection tests, `Map[Int, String]` and `Map[Long, Boolean]` round-trips.
- Unit test count: 122 → 124; compat test count: 253 → 318; total: 442 tests.

## [0.9.0] - 2026-03-05

### Fixed
- **Hierarchical enum codec with diamond inheritance** — `SanelyEnumCodec` now correctly handles sealed trait hierarchies where a singleton extends multiple intermediate traits (diamond structure). Previously, `mirror.ordinal()` returned the ordinal of the intermediate trait rather than the leaf, causing ordinal collisions (e.g., both `B` and `D` mapping to ordinal 1). Now uses reference equality (`eq`) on singleton instances with deduplication, eliminating the collision.

### Added
- **Configured derivation compat tests** (`ConfiguredDerivesSuite`) — 25 tests ported from circe's `ConfiguredDerivesSuite` covering: member name transforms (snake_case, SCREAMING_SNAKE_CASE, kebab-case, PascalCase), default values (Option with/without defaults, null handling, generic classes), discriminator field, constructor name transforms, combined configuration options, strict decoding (sum types, product types, accumulating errors), multi-level hierarchies (2-level, 3-level with field name conflicts), and recursive discriminated types.
- **Configured enum compat tests** (`ConfiguredEnumDerivesSuites`) — 11 tests ported from circe's `ConfiguredEnumDerivesSuites` covering: codec property tests, decode failure for unknown cases, constructor name transforms, and hierarchical enums with diamond inheritance.
- **Expanded auto derivation compat tests** — added `RecursiveAdtExample`, `RecursiveWithOptionExample`, `ADTWithSubTraitExample`, `Outer` (nested with Option), `LongClass` (33 fields stress test), "nested sums not encoded redundantly" test, and "derived encoder respects existing instances" test.
- **Expanded semiauto derivation compat tests** — added `Adt1` (class+object), `Adt2` (all objects), `Adt3` (empty class+object), `Adt4` (nested sub-traits), and "decoder ignores superfluous keys" test.
- Compat test count: 160 → 253 (+93 tests)

## [0.8.0] - 2026-03-05

### Performance
- **Builtin short-circuit for primitive types** — String, Int, Long, Double, Float, Boolean, Short, Byte, BigDecimal, and BigInt are now resolved directly to their `Encoder.encodeX`/`Decoder.decodeX` instances without calling `Expr.summonIgnoring`. Reduces summonIgnoring calls by ~47% for auto derivation (1366→720) and ~66% for configured derivation (984→336).
- **Runtime product encoding** — macro-generated N nested `.add()` calls replaced with a flat `Array[Encoder]` and a single `SanelyRuntime.encodeProductFields` while-loop. Reduces generated AST size for product encoders.
- **Runtime product decoding** — macro-generated N-deep nested match chains replaced with `SanelyRuntime.decodeProductFields`/`decodeProductFieldsConfigured` using `Array[Decoder]` and `ArrayProduct` + `mirror.fromProduct`. Reduces generated AST size for product decoders.
- **Runtime sum type dispatch** — macro-generated foldRight if-then-else chains replaced with array-based `SanelyRuntime.encodeSum`/`encodeSumConfigured` and `decodeSum`/`decodeSumConfigured`. Reduces generated AST size for sum type encoders and decoders.
- **Configured benchmark**: 2.90s median (was ~3.10s), now slightly faster than circe-core (2.94s)
- **Auto benchmark**: 3.17s median (was ~3.69s), 2.1x faster than circe-generic (6.75s)

### Changed
- All optimizations applied to both configured (`SanelyConfiguredEncoder`/`SanelyConfiguredDecoder`) and non-configured (`SanelyEncoder`/`SanelyDecoder`) derivation files.
- `SanelyRuntime` expanded with 6 new runtime methods: `encodeProductFields`, `encodeSum`, `encodeSumConfigured`, `decodeProductFields`, `decodeProductFieldsConfigured`, `decodeSumConfigured`.

## [0.7.0] - 2026-03-05

### Added
- **Compile-time macro profiling** — enable with `SANELY_PROFILE=true` to get per-expansion timing breakdown. Shows time spent in implicit search (`summonIgnoring`), mirror summoning, AST derivation, sub-trait detection, and cache hits. Includes a global summary across all expansions. Zero cost when disabled.
- **Profiling analysis skill** — `.claude/skills/macro-profile/` with a Python script (`scripts/analyze_profile.py`) that aggregates profile data, ranks slowest types, and generates actionable optimization insights.

## [0.6.1] - 2026-03-05

### Performance
- **Extract runtime utilities to reduce generated AST size** — common patterns in macro-generated code (discriminator handling, strict decoding validation, default field decoding, sum type key extraction) are now delegated to `SanelyRuntime` helper methods instead of being inlined at every expansion site. Reduces generated bytecode size for configured derivation without affecting compile-time or runtime performance.
- **Cache transformed constructor names in configured sum decoder** — `transformConstructorNames` results are now pre-computed once per decoder instance instead of recomputed on every decode call.

## [0.6.0] - 2026-03-05

### Performance
- **Eliminate 2x code duplication in configured sum type derivation** — configured encoder and decoder for sum types (sealed traits/enums) previously generated two complete dispatch chains (one for external tagging, one for discriminator mode). Now generates a single chain where each variant branch handles both modes inline. Cuts generated AST roughly in half for sum types.
- **Cache `transformMemberNames` in configured product derivation** — transformed field names are now pre-computed once per encoder/decoder instance in an array, instead of calling `conf.transformMemberNames` per-field on every encode/decode call. Also reused for strict decoding validation.

## [0.5.0] - 2026-03-04

### Performance
- **Cache `collectIgnoreSymbols` as lazy val** — the `cachedIgnoreSymbols` list (used by `Expr.summonIgnoring`) is now computed once per macro expansion instead of on every field/variant resolution
- **Cache resolved Encoder/Decoder Exprs by type** — a `mutable.Map[String, Expr[?]]` keyed by `TypeRepr.dealias.show` avoids redundant implicit searches and re-derivation when multiple fields share the same type (e.g., 11 `String` fields, repeated `Money` types)

### Added
- **Configured derivation benchmark** — `bash bench.sh --configured 5` compares configured derivation compile times (~230 types)
- Contributing section in README

## [0.4.1] - 2026-03-04

### Fixed
- **Sub-trait flattening with user-provided custom codecs** — sealed trait variants with user-provided non-`AsObject` codecs (e.g. string-based singletons) are no longer incorrectly flattened, which caused `encodeObject` crashes on encoder side and silent decode failures on decoder side
- **Custom generic containers with Self in type param** — types like `MyEnum[Self]` with user-provided polymorphic givens are now correctly resolved instead of failing with "unknown container" errors. The macro now tries summoning a user-provided instance before falling back to recursive container construction.

### Added
- 2 new tests: sum variant with custom non-AsObject codec, custom generic container with Self in type param

## [0.4.0] - 2026-03-04

### Added
- **Nested recursive container types** — support for `Option[List[Self]]`, `Option[Map[String, Self]]`, `List[Option[Self]]`, and other multi-level container nesting in recursive types (both configured and unconfigured derivation)
- GitHub Actions release workflow (`.github/workflows/release.yml`) — automated publish on `v*` tag push
- 5 new recursive type tests covering nested containers

## [0.3.1] - 2026-03-04

### Added
- GitHub Actions CI workflow (JVM unit tests, Scala.js unit tests, compat tests)
- CI status badge in README

### Fixed
- Resolved all 10 compiler warnings:
  - 9 non-exhaustive match warnings in macro derivation files (added wildcard cases for tuple type pattern matching)
  - 1 dead code warning on Scala.js for `Platform.isJS` check (added `else` branch)

## [0.3.0] - 2026-03-04

### Added
- **Configured derivation** — full support for `io.circe.derivation.Configuration`:
  - `transformMemberNames` (snake_case, SCREAMING_SNAKE_CASE, kebab-case, PascalCase)
  - `transformConstructorNames` (same transforms for ADT variant names)
  - `useDefaults` — use Scala default parameter values for missing/null JSON fields
  - `discriminator` — flat encoding with discriminator field (`{"type": "Variant", ...}`)
  - `strictDecoding` — reject unexpected JSON keys (products and sum types)
- **Enum string codec** (`SanelyEnumCodec`) — encode singleton enums as plain JSON strings with `transformConstructorNames` support and hierarchical sealed trait support
- **Configured API surface**:
  - `SanelyConfiguredEncoder.derived`, `SanelyConfiguredDecoder.derived`, `SanelyConfiguredCodec.derived`
  - `io.circe.generic.semiauto.{deriveConfiguredEncoder, deriveConfiguredDecoder, deriveConfiguredCodec, deriveEnumCodec}`
- **Scala.js cross-platform support** — published for both JVM and Scala.js
- **Compile-time benchmark** — comparing sanely vs circe-generic (~300 types, 1.6x faster)
- 57 configured derivation unit tests ported from circe's `ConfiguredDerivesSuite` and `ConfiguredEnumDerivesSuites`

### Fixed
- Default value lookup for generic classes (skip type parameter lists when counting constructor params)
- `Option` fields with `null` decode as `None` instead of using the default value
- Hierarchical sealed traits in enum codecs (recurse into `Mirror.SumOf` sub-types)
- Strict decoding on sum types now rejects multi-key JSON objects in external tagging mode

### Changed
- Bumped circe dependency to 0.14.15

## [0.2.0] - 2025-12-15

### Added
- **Circe compatibility test suite** — 160 property-based tests using circe's own `CodecTests` (munit + discipline), ported from `DerivesSuite` and `SemiautoDerivationSuite`
- Published to Maven Central as `io.github.nguyenyou::circe-sanely-auto`

## [0.1.0] - 2025-12-14

### Added
- Initial release
- **Auto-derivation** — `import sanely.auto.given` for automatic `Encoder.AsObject` and `Decoder` derivation
- **Semiauto derivation** — `SanelyEncoder.derived`, `SanelyDecoder.derived`, `SanelyCodec.derived`
- **Drop-in API** — `io.circe.generic.auto.given` and `io.circe.generic.semiauto.*` aliases
- Product types (nested, generic, large — up to 33 fields)
- Sum types with external tagging (sealed traits, enums, case objects, sub-trait flattening)
- Recursive types (`Option[Self]`, `List[Self]`, `Vector[Self]`, `Set[Self]`, `Seq[Self]`, `Map[K, Self]`, cats containers)
- Generic types (`Box[A]`, `Qux[A, B]`)
- User-provided instances respected via `Expr.summonIgnoring`
- 52 unit tests covering all derivation features
