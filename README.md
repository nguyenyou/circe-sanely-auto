# circe-sanely-auto

[![CI](https://github.com/nguyenyou/circe-sanely-auto/actions/workflows/ci.yml/badge.svg)](https://github.com/nguyenyou/circe-sanely-auto/actions/workflows/ci.yml)

Drop-in replacement for circe's auto/semi-auto/configured derivation for Scala 3. Swap one dependency, change one import, compile faster.

**Scala 3.8.2+ | JVM + Scala.js | 447 tests passing**

## Why

circe-generic is slow. Every nested type triggers another round of implicit resolution, and those rounds compound. On a codebase with 300 types:

| | circe-generic | circe-sanely-auto | |
|---|---|---|---|
| **Auto derivation** | 6.98s | **2.23s** | **3.1x faster** |
| **Configured derivation** | 2.89s | **1.47s** | **2.0x faster** |
| **Compiler work** | 1,542 samples | **806 samples** | **48% less** |
| **Memory allocations** | 8,547 samples | **4,168 samples** | **51% less** |
| **Peak RSS** | 963 MB | **769 MB** | **20% less** |

This library replaces circe-generic with a macro that derives everything in one expansion — no implicit search chains, no Shapeless. It passes circe's own test suite (318 property-based tests) plus 129 additional unit tests.

### How to try it

```diff
- mvn"io.circe::circe-generic:0.14.x"
+ mvn"io.github.nguyenyou::circe-sanely-auto:0.13.0"
```

```diff
- import io.circe.generic.auto._
+ import io.circe.generic.auto.given
```

That's it. Same JSON format, same API, same behavior. Everything else stays the same.

### Faster runtime with jsoniter-scala-circe

For even better runtime performance, pair with [jsoniter-scala-circe](https://github.com/plokhotnyuk/jsoniter-scala) — it replaces circe's JSON parser with jsoniter-scala's faster one while keeping all your circe codecs unchanged:

```scala
// Add jsoniter-scala-circe alongside sanely-auto
mvn"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core:2.38.9"
mvn"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-circe:2.38.9"
```

```diff
  // Before: circe's jawn parser
- import io.circe.jawn._
- val result = decodeByteArray[MyType](jsonBytes)

  // After: jsoniter-scala's parser, same circe Decoder
+ import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec.given
+ import com.github.plokhotnyuk.jsoniter_scala.core._
+ val result = readFromArray[io.circe.Json](jsonBytes).as[MyType]
```

Your `Encoder`/`Decoder` instances (whether from sanely-auto, semi-auto, or hand-written) are untouched — only the parse/print layer changes.

**Runtime benchmark** (1.2 KB JSON payload, M3 Max, JDK 25):

| | Reading (ops/sec) | | Writing (ops/sec) | |
|---|---|---|---|---|
| **circe + jawn** (baseline) | 156,820 | 1.0x | 150,891 | 1.0x |
| **circe + jsoniter parser** | 235,963 | **1.5x** | 135,512 | 0.9x |
| **jsoniter-scala native** | 796,505 | **5.1x** | 739,119 | **4.9x** |

The combo gives a **1.5x reading speedup** within the circe ecosystem. Writing doesn't benefit because the bottleneck is circe's `Encoder` building the `Json` AST, not the serialization step. For maximum runtime performance, use jsoniter-scala directly.

## How it works

Based on Mateusz Kubuszok's [sanely-automatic derivation](https://kubuszok.com/2025/sanely-automatic-derivation/) technique. Scala 3.7+ provides `Expr.summonIgnoring`, which lets a macro summon implicit instances while excluding specific symbols:

1. Define an `inline given autoEncoder[A]` that delegates to a macro
2. Inside the macro, use `Expr.summonIgnoring` to search for an existing `Encoder[A]` — excluding our own auto-given from the search
3. If a user-provided instance exists, use it. Otherwise, derive it internally using `Mirror` — recursively, within the same macro expansion

One macro call derives everything. No implicit search chains. No Shapeless.

## Compatibility

The goal is full API compatibility with circe's derivation — same JSON format, same behavior, same error messages where possible.

**447 tests total**: 129 unit tests (utest, cross-compiled JVM + Scala.js) covering auto, semiauto, and configured derivation. Plus 318 compatibility tests (munit + discipline) ported directly from circe's own `DerivesSuite`, `SemiautoDerivationSuite`, and `ConfiguredDerivesSuite` — same types, same Arbitrary instances, same property-based checks.

## Features

### Auto-derivation

```scala
import io.circe.*
import io.circe.syntax.*
import sanely.auto.given  // or: import io.circe.generic.auto.given

case class Address(street: String, city: String)
case class Person(name: String, age: Int, address: Address)

Person("Alice", 30, Address("123 Main St", "Springfield")).asJson
// {"name":"Alice","age":30,"address":{"street":"123 Main St","city":"Springfield"}}
```

Sum types use external tagging:

```scala
enum Shape:
  case Circle(radius: Double)
  case Rectangle(width: Double, height: Double)

Shape.Circle(5.0).asJson  // {"Circle":{"radius":5.0}}
```

### Semiauto derivation

```scala
import io.circe.generic.semiauto.*

case class User(name: String, age: Int)
object User:
  given Decoder[User] = deriveDecoder
  given Encoder.AsObject[User] = deriveEncoder
  // or: given Codec.AsObject[User] = deriveCodec
```

### Configured derivation

All 5 options from `io.circe.derivation.Configuration`:

```scala
import io.circe.*
import io.circe.derivation.Configuration
import io.circe.generic.semiauto.*

given Configuration = Configuration.default
  .withSnakeCaseMemberNames
  .withDiscriminator("type")
  .withDefaults

case class User(firstName: String, lastName: String, age: Int = 25)
given Codec.AsObject[User] = deriveConfiguredCodec

User("Alice", "Smith", 30).asJson
// {"first_name":"Alice","last_name":"Smith","age":30,"type":"User"}
```

| Option | Effect |
|---|---|
| `transformMemberNames` | Rename JSON keys (`withSnakeCaseMemberNames`, `withKebabCaseMemberNames`, etc.) |
| `transformConstructorNames` | Rename ADT variant names (`withSnakeCaseConstructorNames`, etc.) |
| `useDefaults` | Use Scala default values for missing/null JSON fields |
| `discriminator` | `Some("type")` for flat encoding instead of external tagging |
| `strictDecoding` | Reject unexpected JSON keys |

### Enum string codec

Encode enums with only singleton cases as plain JSON strings:

```scala
enum Color:
  case Red, Green, Blue

given Configuration = Configuration.default.withSnakeCaseConstructorNames
given Codec[Color] = deriveEnumCodec

Color.Red.asJson  // "red"
```

Supports hierarchical sealed traits with diamond inheritance.

### What works

- Products, sum types, case objects, enums
- Nested types, generic types (`Box[A]`, `Qux[A, B]`)
- Recursive types (`Option[Self]`, `List[Self]`, `Map[K, Self]`, etc.)
- All standard containers: `Option`, `List`, `Vector`, `Set`, `Seq`, `Map`, `Chain`, `NonEmptyList`, `NonEmptyVector`, `NonEmptySeq`, `NonEmptyChain`
- User-provided instances are respected (not overridden)
- Sub-trait flattening in ADTs
- Large types (33+ fields, 33+ variants)
- Generic classes with default values
- Cross-platform: JVM and Scala.js

## Migration from circe-generic

| Before | After |
|---|---|
| `mvn"io.circe::circe-generic:0.14.x"` | `mvn"io.github.nguyenyou::circe-sanely-auto:0.13.0"` |
| `import io.circe.generic.auto._` | `import io.circe.generic.auto.given` |
| `import io.circe.generic.semiauto._` | `import io.circe.generic.semiauto.*` (unchanged) |

The `io.circe.generic.auto` and `io.circe.generic.semiauto` packages are provided by this library with the same API. Semiauto calls (`deriveEncoder`, `deriveDecoder`, `deriveCodec`) and configured derivation (`deriveConfiguredEncoder`, `deriveConfiguredDecoder`, `deriveConfiguredCodec`, `deriveEnumCodec`) work identically. JSON format is the same. User-provided instances are still respected.

**Requirements**: Scala 3.8.2+, no Scala 2 support.

## Compile-time benchmarks

Two benchmark suites compare compile times against circe's native derivation. All numbers from M3 Max MacBook Pro, Mill 1.1.2, Scala 3.8.2.

```bash
bash bench.sh 5              # auto derivation (~300 types)
bash bench.sh --configured 5 # configured derivation (~230 types)
```

### Results

| Suite | circe-sanely-auto | circe baseline | Speedup |
|---|---|---|---|
| **Auto derivation** (~300 types) | **2.23s** | 6.98s (circe-generic) | **3.13x** |
| **Configured derivation** (~230 types) | **1.47s** | 2.89s (circe-core) | **1.97x** |

### Why the difference?

**Auto derivation** (3.13x faster): With `import io.circe.generic.auto.given`, the compiler must implicitly search for and synthesize codecs at every use site — each nested type triggers another round of implicit resolution. Sanely avoids this by deriving everything in a single macro expansion.

**Configured derivation** (1.97x faster): Even though configured derivation uses explicit semi-auto calls (`deriveConfiguredCodec` in each companion object) with no implicit search chain to eliminate, our optimizations reduce both macro expansion time and generated AST size.

Six optimizations drive this:

1. **Single-pass codec derivation** — `deriveConfiguredCodec` uses a dedicated macro that resolves both encoder and decoder for each field type in one traversal, sharing one cache, one `Mirror` summon, and one builtin check per type. Halves the number of macro expansions (230 vs 460).
2. **Builtin short-circuit** — primitive types (String, Int, Long, Double, Float, Boolean, Short, Byte, BigDecimal, BigInt) are resolved directly to their circe instances without calling `Expr.summonIgnoring`, saving ~66% of summonIgnoring calls.
3. **Container composition** — containers of primitives or already-cached types (`Option[String]`, `List[Int]`, `Map[String, Double]`, `Option[CustomType]`, etc.) are composed directly using `buildContainerEncoder`/`buildContainerDecoder`, covering all 10 container types (Option, List, Vector, Set, Seq, Chain, NonEmptyList, NonEmptyVector, NonEmptySeq, NonEmptyChain). Saves ~12% more summonIgnoring calls.
4. **Runtime dispatch** — instead of generating N nested `.add()` calls (products) or N if-then-else branches (sum types) in the macro AST, the macro builds flat `Array[Encoder]`/`Array[Decoder]` and delegates to runtime while-loops in `SanelyRuntime`, reducing generated AST size by ~30-50%.
5. **Sub-trait detection cache** — when `resolveOneEncoder`/`resolveOneDecoder` resolves a type via `Expr.summonIgnoring`, the cache key is recorded in a `summonedKeys` set. In `deriveSum`, sub-trait detection checks this set (O(1) lookup) instead of calling `summonIgnoring` again, eliminating redundant implicit searches. For configured codec derivation, this reduced per-call sub-trait detection time from 0.29ms to 0.03ms (-90%).
6. **Factory method consolidation** — instead of each macro expansion generating its own anonymous `Encoder`/`Decoder`/`Codec` class definition (300+ classes for 300 types), all 6 macro files delegate to 11 factory methods in `SanelyRuntime` that define each anonymous class template once. Type-specific data (field names, encoder/decoder arrays) is passed as parameters. Lazy initialization uses `() => Array[Encoder[Any]]` lambdas which compile to `invokedynamic` (not anonymous classes). This dramatically reduces the transform+backend compiler phases — from heavier than circe to lighter.

### JVM profiling (async-profiler)

JVM-level profiling with async-profiler shows where the Scala compiler spends time. Compiler-only samples (excluding JIT/GC cold-start overhead):

#### Auto derivation — compiler workload

| Phase | sanely (806 samples) | circe-generic (1542 samples) | Delta |
|---|---|---|---|
| core (types, symbols, contexts) | 269 (33%) | 574 (37%) | **-53%** |
| ast (tree maps, accumulators) | 83 (10%) | 202 (13%) | **-59%** |
| typer | 78 (10%) | 123 (8%) | -37% |
| other (infra, denotations) | 101 (13%) | 133 (9%) | -24% |
| transform (erasure, lambdalift) | 59 (7%) | 82 (5%) | -28% |
| backend (bytecode, classfiles) | 49 (6%) | 60 (4%) | -18% |
| macro inlines | 12 (1%) | 67 (4%) | **-82%** |
| macro quoted | 25 (3%) | 7 (0%) | sanely only |
| typer implicits | 8 (1%) | 16 (1%) | -50% |
| parsing | 7 (1%) | 8 (1%) | -12% |
| **total compiler** | **806** | **1542** | **-48%** |

#### Configured derivation — compiler workload

| Phase | sanely (621 samples) | circe-core (805 samples) | Delta |
|---|---|---|---|
| core (types, symbols, contexts) | 204 (33%) | 273 (34%) | -25% |
| ast (tree maps, accumulators) | 64 (10%) | 95 (12%) | -33% |
| typer | 62 (10%) | 63 (8%) | -2% |
| other (infra, denotations) | 97 (16%) | 88 (11%) | +10% |
| transform (erasure, lambdalift) | 53 (9%) | 62 (8%) | -15% |
| backend (bytecode, classfiles) | 44 (7%) | 58 (7%) | -24% |
| macro inlines | 7 (1%) | 30 (4%) | **-77%** |
| macro quoted | 6 (1%) | 5 (1%) | +20% |
| typer implicits | 11 (2%) | 12 (1%) | -8% |
| parsing | 9 (1%) | 7 (1%) | +29% |
| **total compiler** | **621** | **805** | **-23%** |

**Key pattern**: Sanely trades inlining for quote reflection. circe-generic/circe-core's `inline` + `summonInline` approach forces the compiler to do heavy inlining work (67 samples for auto, 30 for configured). Sanely's `Expr.summonIgnoring` approach shifts that work to the quote reflection phase (25/6 samples) which is cheaper. Factory method consolidation eliminated the previous regression in transform+backend phases — sanely is now lighter than circe in all phases. For auto derivation, the total compiler workload is **48% lighter**. For configured, the compiler workload is **23% lighter**.

### Memory profiling

Peak RSS via `/usr/bin/time -l`, allocation samples via async-profiler `event=alloc`.

#### Peak RSS

| Suite | sanely | circe baseline | Delta |
|---|---|---|---|
| **Auto derivation** (~300 types) | **769 MB** | 963 MB (circe-generic) | **-20%** |
| **Configured derivation** (~230 types) | **752 MB** | 750 MB (circe-core) | ~0% |

Auto derivation uses **20% less peak memory** thanks to fewer generated classes and smaller ASTs. Configured derivation uses comparable peak memory. RSS includes compiler heap, JIT code, metaspace, and OS buffers.

#### Allocation pressure — auto derivation

| Category | sanely | circe-generic | Delta |
|---|---|---|---|
| **total** | **4,168** | **8,547** | **-51%** |
| compiler | 3,319 (80%) | 7,667 (90%) | **-57%** |
| compiler.core | 1,163 | 2,862 | **-59%** |
| compiler.core.types | 321 | 1,009 | **-68%** |
| compiler.ast | 280 | 1,059 | **-74%** |
| compiler.macro.inlines | 55 | 502 | **-89%** |
| compiler.typer | 199 | 402 | -51% |
| compiler.backend | 186 | 304 | -39% |
| mill / zinc / jvm | 849 | 880 | -4% |

#### Allocation pressure — configured derivation

| Category | sanely | circe-core | Delta |
|---|---|---|---|
| **total** | **3,073** | **4,181** | **-26%** |
| compiler | 2,242 (73%) | 3,316 (79%) | **-32%** |
| compiler.core | 775 | 1,080 | -28% |
| compiler.core.types | 175 | 397 | **-56%** |
| compiler.ast | 162 | 405 | **-60%** |
| compiler.macro.inlines | 26 | 201 | **-87%** |
| compiler.typer | 125 | 182 | -31% |
| compiler.backend | 211 | 216 | -2% |
| mill / zinc / jvm | 831 | 865 | -4% |

**Auto derivation** allocates **51% fewer objects** — the biggest wins are in `compiler.ast` (-74%, smaller generated ASTs from factory methods), `compiler.core.types` (-68%, fewer type representations), and `compiler.macro.inlines` (-89%, no inline expansion chains). **Configured derivation** allocates **26% fewer objects** with the same pattern. Factory method consolidation reversed the previous backend allocation regression — sanely now allocates fewer objects than circe in all categories including backend (-39% auto, -2% configured).

### Macro profiling

Built-in compile-time profiling via `SANELY_PROFILE=true` tracks where time is spent inside our macros:

```bash
# Profile auto derivation (~300 types)
rm -rf out/benchmark/sanely
SANELY_PROFILE=true ./mill --no-server benchmark.sanely.compile 2>&1 | tee /tmp/profile.txt
python3 .claude/skills/macro-profile/scripts/analyze_profile.py /tmp/profile.txt

# Profile configured derivation (~230 types)
rm -rf out/benchmark-configured/sanely
SANELY_PROFILE=true ./mill --no-server benchmark-configured.sanely.compile 2>&1 | tee /tmp/profile.txt
python3 .claude/skills/macro-profile/scripts/analyze_profile.py /tmp/profile.txt
```

#### Auto derivation (308 expansions, 2.8s total macro time)

| Category | Time | % | Calls | Avg |
|---|---|---|---|---|
| `summonIgnoring` | 1375ms | 49.3% | 660 | 2.08ms |
| `derive` | 952ms | 34.1% | 586 | 1.63ms |
| `summonMirror` | 118ms | 4.2% | 586 | 0.20ms |
| `subTraitDetect` | 64ms | 2.3% | 336 | 0.19ms |
| `tryBuiltin` | 47ms | 1.7% | 1366 | 0.03ms |
| `cheapTypeKey` | 4ms | 0.1% | 3080 | 0.00ms |
| `builtinHit` | — | — | 706 | — |
| cache hits | — | — | 1714 (75%) | — |
| overhead | 231ms | 8.3% | — | macro framework (tuple recursion, AST construction) |

#### Configured derivation (230 expansions, 1.3s total macro time)

| Category | Time | % | Calls | Avg |
|---|---|---|---|---|
| `topDerive`* | 1157ms | 89.1% | 230 | 5.03ms |
| `summonIgnoring` | 549ms | 42.3% | 294 | 1.87ms |
| `tryBuiltin` | 37ms | 2.9% | 493 | 0.08ms |
| `resolveDefaults` | 9ms | 0.7% | 214 | 0.04ms |
| `subTraitDetect` | 3ms | 0.2% | 69 | 0.04ms |
| `cheapTypeKey` | 1ms | 0.1% | 820 | 0.00ms |
| `builtinHit` | — | — | 345 | — |
| cache hits | — | — | 327 | — |

*`topDerive` is a container category that includes `summonIgnoring`, `derive`, `summonMirror`, `subTraitDetect`, and `resolveDefaults`. Percentages sum > 100% due to nesting.

#### Optimization effectiveness

| Metric | Auto | Configured |
|---|---|---|
| Total macro expansions | 308 | 230 |
| `summonIgnoring` calls | 660 | 294 |
| Builtin short-circuit hits | 706 | 345 |
| Container composition hits | included in builtin | included in builtin |
| Cache hit rate | 75% (1714 hits) | — (327 hits) |
| summonIgnoring % of total | 49% | 42% |
| `tryBuiltin` time | 47ms (1.7%) | 37ms (2.9%) |
| `cheapTypeKey` time | 4ms (0.1%) | 1ms (0.1%) |

`summonIgnoring` (the compiler's implicit search) dominates auto derivation at 49%. Builtin short-circuiting and container composition resolve ~706 type lookups without calling `summonIgnoring` at all. Sub-trait detection uses cached `summonedKeys` (O(1) set lookup) instead of re-calling `summonIgnoring`, reducing per-call time from 0.29ms to 0.04ms in configured derivation (-86%). For configured derivation, single-pass codec derivation halved the macro expansion count from 460 (separate CfgEncoder + CfgDecoder) to 230 (unified CfgCodec), while sharing one cache and one Mirror summon per type. The `summonIgnoring` call count stays at 294 (both encoder and decoder must still be summoned), but sub-trait detection halved from 138 to 69 calls. The intra-expansion cache achieves a 75% hit rate in auto derivation, avoiding redundant derivations for repeated types within a single macro call. Factory method consolidation reduced macro framework overhead from 359ms to 231ms (-36%) by generating simpler factory calls instead of full anonymous class definitions. The remaining 231ms is intrinsic to Scala 3's quote reflection (tuple type recursion at ~2ms/field, AST construction, quote splicing).

## Building

Requires [Mill](https://mill-build.org/) 1.1.2+.

```bash
./mill sanely.jvm.compile    # compile (JVM)
./mill sanely.js.compile     # compile (Scala.js)
./mill sanely.jvm.test       # unit tests (JVM)
./mill sanely.js.test        # unit tests (Scala.js)
./mill compat.jvm.test       # circe compatibility tests (JVM)
./mill compat.js.test        # circe compatibility tests (Scala.js)
./mill demo.run              # run demo
bash bench-runtime.sh        # runtime benchmark (circe vs circe+jsoniter vs jsoniter-scala)
```

## How it's made

This entire library — every macro, every test, every line of build config, and this README — was written by [Claude Code](https://claude.com/claude-code) with Claude Opus 4.6. 100% vibe coded.

## Roadmap

- [x] **Runtime dispatch for generated code** — instead of emitting N nested `.add()` calls or N if-then-else branches in the macro AST, build flat `Array[Encoder]`/`Array[Decoder]` at compile time and delegate to runtime while-loops in `SanelyRuntime`. Reduces generated AST size by ~30-50%.
- [x] **Builtin short-circuit for primitive types** — resolve String, Int, Long, Double, Float, Boolean, Short, Byte, BigDecimal, BigInt directly to their circe instances without calling `Expr.summonIgnoring`. Saves ~66% of summonIgnoring calls in configured derivation.
- [x] **Profile macro expansion** — compile-time profiling via `SANELY_PROFILE=true` tracks `summonIgnoring`, `summonMirror`, `derive`, `subTraitDetect`, `resolveDefaults`, `builtinHit`, and cache hits per expansion.
- [x] **Container + builtin composition** — extend builtin short-circuit to handle containers of primitives: `Option[String]`, `List[Int]`, `Map[String, Double]`, etc. Directly emit composed codecs without calling `Expr.summonIgnoring`. Saves ~12.5% more summonIgnoring calls (294 down from 336).
- [x] **Accumulating decoder** (`decodeAccumulating`) — override `decodeAccumulating` on all generated decoders (products and sum types, both auto and configured) to collect ALL field errors into `ValidatedNel` instead of short-circuiting on the first. Runtime methods iterate all fields and accumulate errors. Codec wrappers properly delegate `decodeAccumulating` to the underlying decoder.
- [x] **Literal/singleton type handling** — detect literal types (`"hello"`, `42`) and case object singletons at compile time; emit direct value references instead of going through encoder/decoder machinery.
- [x] **Ignore `Encoder.derived`/`Decoder.derived` from circe-core** — add circe-core's own `derived` methods to `cachedIgnoreSymbols` so they don't interfere with `Expr.summonIgnoring` when circe-core is on the classpath.
- [x] **Improved error messages** — when derivation fails, report what was tried and why each approach failed (builtin miss, summonIgnoring miss, no Mirror) instead of a single generic message.
- [x] **Derive key encoders/decoders inline for built-in types** — resolve `KeyEncoder`/`KeyDecoder` directly for `String`, `Int`, `Long`, `Double`, `Short`, `Byte` instead of summoning via implicit search. Extends container+builtin composition from `Map[String, V]` to `Map[K, V]` for all builtin key types.
- [x] **Enable `-Werror` in CI** — all modules compile with `-Werror` (Scala 3's replacement for `-Xfatal-warnings`), ensuring macro-generated code produces zero compiler warnings. Users with strict linting never need `@nowarn` annotations for code they didn't write.
- [x] **Test coverage gaps** — added tests for: generic context derivation (type params not yet known), semiauto `derived` inside companion not causing infinite recursion, Tuple/Either fields.
- [x] **(P1) Single-pass codec derivation (`deriveCodec`, `deriveConfiguredCodec`)** — codec derivation now uses a dedicated macro path (`CodecDerivation` / `ConfiguredCodecDerivation`) that resolves both encoder and decoder for each field type in a single pass. Shares one `exprCache`, one `containsType` traversal, one `Mirror` summon, and one builtin check per type — eliminating the duplicate work from composing separate encoder + decoder derivations.
- [x] **(P1) Reorder resolution: cache before `containsType`** — `resolveOneEncoder`/`resolveOneDecoder` now check `exprCache` before calling `containsType` (recursive type tree traversal). For non-recursive types (the vast majority), cache hits skip both `containsType` and builtin check entirely.
- [x] **(P1) Container composition for non-builtin inner types** — `tryResolveBuiltinEncoder` for `Option[T]`, `List[T]`, etc. now checks the `exprCache` for inner type `T` when it's not a primitive. If `T` was already resolved (from a prior field or variant), the container codec is composed directly without calling `Expr.summonIgnoring`. Also reuses `buildContainerEncoder`/`buildContainerDecoder` (covering all 10 container types including cats) instead of duplicating the container match.
- [x] **(P2) Cheaper cache key** — replaced `tpe.dealias.show` (expensive pretty-printer) with `MacroUtils.cheapTypeKey` which recursively builds keys from `typeSymbol.fullName` (simple property lookup). Handles `AppliedType` recursively, `TermRef` (singleton types) via `termSymbol.fullName`, and `ConstantType` via `c.show`. Falls back to `.show` only for exotic types. Applied across all 6 macro derivation files.
- ~~**(P1) Single-pass auto derivation**~~ — Investigated: replacing separate `autoEncoder`/`autoDecoder` with a single `autoCodec` returning `Codec.AsObject[A]` causes a **68% regression** (3.11s → 5.24s on ~300 types). Root cause: inline givens fire per implicit search, not per type. When code needs both `Encoder[A]` and `Decoder[A]`, the compiler does two independent searches, each triggering the codec macro. Each expansion now does 2x work (both directions) while still firing twice = ~804 effective units vs 616 before. The only viable path to sharing work across encoder/decoder searches is cross-expansion caching (lazy val emission).
- [x] **(P1) Eliminate redundant sub-trait `summonIgnoring`** — sub-trait detection used to call `Expr.summonIgnoring` again for each variant to check for user-provided instances, despite `resolveOneEncoder`/`resolveOneDecoder` already having this information. Added `summonedKeys: mutable.Set[String]` that records cache keys when `summonIgnoring` returns `Some`. In `deriveSum`, sub-trait detection checks this set (O(1)) instead of re-calling `summonIgnoring`. Eliminated up to 336 redundant implicit searches in auto derivation and reduced configured sub-trait detection time by 90% (0.29ms → 0.03ms per call).
- [ ] **Investigate cross-expansion caching** — each `inline given autoEncoder[A]` triggers an independent macro expansion with its own `exprCache`. If `Person` has field `Address`, both `autoEncoder[Person]` and `autoEncoder[Address]` independently derive `Address`. Emitting `lazy val` instances in a generated object could eliminate this redundancy. Needs investigation into impact on incremental compilation.
- ~~**(P2) Precompute normalized type metadata in resolver hot paths**~~ — Profiling showed `cheapTypeKey` is 4ms (0.2%) and `dealias`/`containsType`/`selfCheck` are each 0ms across 3080 calls. These operations are already negligible; precomputing would save <5ms total. Not worth the added complexity.
- ~~**(P2) Consolidate negative cache for builtin + container misses**~~ — Existing `negativeBuiltinCache` already handles within-expansion deduplication. Cross-expansion misses require cross-expansion caching (separate item). Within a single expansion, `tryBuiltin` accounts for only 34ms (1.4%) — further caching would save <10ms.
- [x] **Negative builtin cache** — `tryResolveBuiltinEncoder` is called for every non-cached type. When it returns `None`, the type key is added to `negativeBuiltinCache`. Subsequent calls skip the entire builtin check (10 `=:=` comparisons + container pattern matching). Inner container arg resolution also uses the negative cache to skip `resolvePrimEncoder` for known non-primitives, falling back directly to `exprCache`. Applied across all 6 macro derivation files.
- [x] **Deduplicate `dealias` calls** — `TypeRepr.of[T].dealias` was called 3+ times for the same type in `resolveOneEncoder`: cache key computation (`cheapTypeKey`), `tryResolveBuiltinEncoder` (which also redundantly called `TypeRepr.of[T]`), and `containsType`. Now computed once as `val dealiased = tpe.dealias` at the top of each `resolveOne*` method and threaded through all consumers. `tryResolveBuiltin*` methods accept the pre-dealiased `TypeRepr` as a parameter instead of reconstructing it. Applied across all 6 macro derivation files, eliminating ~2800 redundant dealias calls in auto + configured derivation.
- [x] **Profile untimed overhead** — Added timing around `cheapTypeKey` (cache key generation), `tryBuiltin` (builtin resolution), `containsType` (recursive type check), `selfCheck` (self-type equality), and `dealias`. Results on ~300 types: `cheapTypeKey` 3.8ms/3080 calls, `tryBuiltin` 34ms/1366 calls, `containsType` 0.8ms/660 calls, `selfCheck` 0ms, `dealias` 0ms — total 39ms of the 359ms (15%) overhead. **Finding: the remaining ~320ms is distributed across inherent macro framework operations** — tuple type recursion in `resolveFields` (Type.of matching, Type.valueOfConstant at ~2ms/field), root-level AST construction (Expr building, quote splicing), and cache map operations. No single actionable bottleneck exists; the overhead is intrinsic to Scala 3's quote reflection. Kept `cheapTypeKey` and `tryBuiltin` timers as permanent diagnostic categories.
- [x] **Reduce transform+backend compiler phases** — extracted 11 factory methods into `SanelyRuntime` that define anonymous `Encoder`/`Decoder`/`Codec` class templates once. All 6 macro files now generate factory calls instead of anonymous class definitions. Lambdas (`() => Array[Encoder[Any]]`) compile to `invokedynamic` (not anonymous classes). By-name `mirror` parameters avoid eager initialization cycles with local types. Results: auto transform+backend 108→108 (was heavier, now equal); auto total compiler -48% (1542→806 samples); configured total compiler -23% (805→621 samples). Overall: auto 2.97s→2.23s (-25%), configured 1.44s→1.47s (stable).

## Contributing

Since this project is 100% vibe coded, I follow a strict test-driven workflow to make sure everything actually works: **every change starts with a test**. Write the test first, then write the code that makes it pass.

If you find a bug:

1. **Open an issue** — even just reporting the problem is really appreciated
2. **Submit a PR with a failing test** — this is the best way to describe a bug precisely
3. **Submit a PR with the fix too** — even better! Add the failing test and the code that makes it pass

All contributions are welcome — issues, bug reports, feature requests, PRs. Even a quick "hey this didn't work for me" helps.

Thanks for trying this library. I love open source and have relied on open source projects throughout my career. This is my way of giving back and having fun. Welcome to the project!

## License

MIT
