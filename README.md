# circe-sanely-auto

[![CI](https://github.com/nguyenyou/circe-sanely-auto/actions/workflows/ci.yml/badge.svg)](https://github.com/nguyenyou/circe-sanely-auto/actions/workflows/ci.yml)

Drop-in replacement for circe's auto/semi-auto/configured derivation for Scala 3. Swap one dependency, change one import, compile faster.

> **The contract: 100% circe compatibility, zero compromise.** If your application works with circe, it must work identically with circe-sanely-auto — same JSON output, same decoded values, same error messages, same behavior in every edge case. Any difference is a bug. No exceptions, no "close enough", no subtle surprises. You swap the dependency and nothing changes except compile time. This is the promise that makes or breaks this library.

**Scala 3.8.2+ | JVM + Scala.js | ✅ 327 tests passing**

## Why

circe-generic is slow. Every nested type triggers another round of implicit resolution, and those rounds compound. On a codebase with 300 types:

| | circe-generic | circe-sanely-auto | |
|---|---|---|---|
| **Auto derivation** | 6.15s | **2.11s** | **2.9x faster**\* |
| **Configured derivation** | 2.59s | **1.39s** | **1.9x faster**\* |
| **Compiler work** | 1,542 samples | **806 samples** | **48% less** |
| **Memory allocations** | 8,547 samples | **4,168 samples** | **51% less** |
| **Peak RSS** | 963 MB | **769 MB** | **20% less** |

<details>
<summary>*Benchmark methodology & environment</summary>

**Environment**: Apple M3 Max (10P + 4E cores), 36 GB RAM, macOS 26.3, OpenJDK 25.0.2 (Homebrew, aarch64), Mill 1.1.2 (runs zinc on JDK 21.0.9), Scala 3.8.2.

**Methodology**: Compile-time numbers are measured with [hyperfine](https://github.com/sharkdp/hyperfine) (`bash bench.sh 10`). Each run cleans only the benchmark module's output (`rm -rf out/benchmark/…`) then recompiles ~300 types (auto) or ~230 types (configured). One untimed warmup run ensures the Mill daemon JVM is JIT-warm. Ten timed runs follow, with hyperfine randomizing execution order to prevent ordering bias. Reported values are mean ± σ. Dependencies (`sanely.jvm`) are pre-compiled and cached — only the benchmark types are recompiled each run.

**Cross-session stability**: We ran benchmarks 3 times (5+10+10 = 25 total runs per suite) across separate sessions. Auto derivation speedup ranged from 2.88x to 3.01x across sessions (σ < 0.08 within each session). Configured derivation ranged from 1.86x to 1.93x (σ < 0.09). The hero table reports the most conservative 10-run session. The first 5-run session produced a 3.01x outlier (sanely hit its best at 2.02s) — we chose not to report it.

**Fairness**: Both libraries compile the same source files from the same `benchmark/shared/src/` directory, using the same Scala version, same JVM, same Mill daemon, in the same hyperfine invocation. The only difference is the derivation import (`sanely.auto.given` vs `io.circe.generic.auto.given` for auto; `sanely.SanelyConfiguredCodec.derived` vs `io.circe.derivation.ConfiguredCodec.derived` for configured). The benchmark measures derivation overhead only — shared dependencies are pre-compiled and not re-timed.

**Caveats**: These are synthetic benchmarks on ~300 isolated types. Real-world speedups depend on codebase size, type complexity, nesting depth, and how many types use derivation. The benchmark is single-module — projects with many parallel modules may see different bottleneck distributions. Numbers vary across machines and JDK versions. We encourage you to run `bash bench.sh 10` on your own hardware. See the [detailed benchmark section](#compile-time-benchmarks) for the full methodology and profiling data.
</details>

This library replaces circe-generic with a macro that derives everything in one expansion — no implicit search chains, no Shapeless. It passes circe's own test suite (318 property-based tests) plus 129 additional unit tests.

### How to try it

```diff
- mvn"io.circe::circe-generic:0.14.x"
+ mvn"io.github.nguyenyou::circe-sanely-auto:0.14.0"
```

```diff
- import io.circe.generic.auto._
+ import io.circe.generic.auto.given
```

That's it. Same JSON format, same API, same behavior. Everything else stays the same.

### Faster runtime serialization

circe-sanely-auto fixes compile time. For **runtime** performance, there are two options depending on how much speedup you need:

**Runtime benchmark** (~1.4 KB JSON payload with nested products, sealed traits, optional fields — M3 Max, JDK 25):

| Approach | Reading (ops/sec) | | Writing (ops/sec) | |
|---|---|---|---|---|
| **circe + jawn** (baseline) | ~131K | 1.0x | ~115K | 1.0x |
| **circe + jsoniter bridge** | ~190K | **1.5x** | ~110K | 1.0x |
| **sanely-jsoniter** (experimental) | **~431K** | **3.3x** | **~630K** | **5.5x** |
| jsoniter-scala native | ~646K | 4.9x | ~682K | 5.9x |

#### Option 1: jsoniter-scala-circe bridge (1.5x read, drop-in)

Pair with [jsoniter-scala-circe](https://github.com/plokhotnyuk/jsoniter-scala) — it replaces circe's jawn parser with jsoniter-scala's faster one while keeping all your circe codecs unchanged:

```scala
mvn"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core:2.38.9"
mvn"com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-circe:2.38.9"
```

```scala
import io.circe.*
import io.circe.syntax.*
import sanely.auto.given  // fast compile-time derivation
import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec.given
import com.github.plokhotnyuk.jsoniter_scala.core.*

case class User(name: String, age: Int)

// Reading: use jsoniter-scala's parser (1.5x faster than jawn)
val user = readFromArray[Json](jsonBytes).as[User].toOption.get

// Writing: use circe's printer (faster than jsoniter bridge for writing)
val bytes = Printer.noSpaces.print(user.asJson).getBytes("UTF-8")
```

Swap two imports, decoding gets 50% faster. The `Json` AST is still allocated on every request — the bridge just parses bytes faster.

#### Option 2: sanely-jsoniter (3-5x, experimental)

[sanely-jsoniter](sanely-jsoniter/) eliminates the `Json` tree entirely. It generates `JsonValueCodec[T]` instances (jsoniter-scala's codec type) that go directly from bytes to domain objects — no intermediate AST allocation. The generated codecs produce **identical JSON** to circe, so the wire format stays the same.

```scala
mvn"io.github.nguyenyou::sanely-jsoniter:0.13.0"
```

```scala
import sanely.jsoniter.auto.given  // auto-derives JsonValueCodec for all types
import com.github.plokhotnyuk.jsoniter_scala.core.*

case class User(name: String, age: Int)

val json = writeToString(User("Alice", 30))  // {"name":"Alice","age":30}
val user = readFromString[User](json)        // User(Alice, 30)
```

Both circe codecs and jsoniter codecs can coexist — they're different types, no conflicts. circe stays for everything that needs the `Json` AST (cursor navigation, tree merging, programmatic construction). Only the serialization hot path changes.

See the [sanely-jsoniter README](sanely-jsoniter/README.md) for full documentation, supported types, configured derivation, and migration guide.

## How it works

Based on Mateusz Kubuszok's [sanely-automatic derivation](https://kubuszok.com/2025/sanely-automatic-derivation/) technique. Scala 3.7+ provides `Expr.summonIgnoring`, which lets a macro summon implicit instances while excluding specific symbols:

1. Define an `inline given autoEncoder[A]` that delegates to a macro
2. Inside the macro, use `Expr.summonIgnoring` to search for an existing `Encoder[A]` — excluding our own auto-given from the search
3. If a user-provided instance exists, use it. Otherwise, derive it internally using `Mirror` — recursively, within the same macro expansion

One macro call derives everything. No implicit search chains. No Shapeless.

## Compatibility

The goal is full API compatibility with circe's derivation — same JSON format, same behavior, same error messages where possible.

**327 tests total**: 135 unit tests (utest, cross-compiled JVM + Scala.js) covering auto, semiauto, and configured derivation. Plus 192 compatibility tests (munit + discipline) auto-generated from circe's own `DerivesSuite`, `SemiautoDerivationSuite`, `ConfiguredDerivesSuite`, and `ConfiguredEnumDerivesSuites` via `scripts/sync-circe-tests.py` — same types, same Arbitrary instances, same property-based checks.

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
| `mvn"io.circe::circe-generic:0.14.x"` | `mvn"io.github.nguyenyou::circe-sanely-auto:0.14.0"` |
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
| **Auto derivation** (~300 types) | **2.11s** ± 0.04s | 6.15s ± 0.04s (circe-generic) | **2.91x** ± 0.06 |
| **Configured derivation** (~230 types) | **1.39s** ± 0.03s | 2.59s ± 0.04s (circe-core) | **1.86x** ± 0.05 |

### Benchmark method

Measurements use [hyperfine](https://github.com/sharkdp/hyperfine) for statistical rigor. The harness (`bench.sh`) works as follows:

1. **Dependency warm-up** — `sanely.jvm` (and the configured compat shim for `--configured`) are compiled via the Mill daemon before any timed run, so dependency compilation is never included in the measurement.
2. **`--warmup 1`** — one untimed warmup round ensures the Mill daemon JVM is JIT-warm and OS file caches are hot.
3. **`--prepare 'rm -rf out/…'`** — before each timed run, the benchmark module's output is deleted so every measurement is a clean recompilation of only the benchmark sources.
4. **`--runs N`** — N timed runs per command. Hyperfine randomizes execution order across runs to prevent systematic ordering bias, and reports mean ± σ with min/max range.

This measures what users actually experience: warm-daemon, incremental-dependency compilation of the benchmark types only.

### Why the difference?

**Auto derivation** (2.9x faster): With `import io.circe.generic.auto.given`, the compiler must implicitly search for and synthesize codecs at every use site — each nested type triggers another round of implicit resolution. Sanely avoids this by deriving everything in a single macro expansion.

**Configured derivation** (1.9x faster): Even though configured derivation uses explicit semi-auto calls (`deriveConfiguredCodec` in each companion object) with no implicit search chain to eliminate, our optimizations reduce both macro expansion time and generated AST size.

Seven optimizations drive this:

1. **Single-pass codec derivation** — `deriveConfiguredCodec` uses a dedicated macro that resolves both encoder and decoder for each field type in one traversal, sharing one cache, one `Mirror` summon, and one builtin check per type. Halves the number of macro expansions (230 vs 460).
2. **Builtin short-circuit** — primitive types (String, Int, Long, Double, Float, Boolean, Short, Byte, BigDecimal, BigInt) are resolved directly to their circe instances without calling `Expr.summonIgnoring`, saving ~66% of summonIgnoring calls.
3. **Container composition** — containers of primitives or already-cached types (`Option[String]`, `List[Int]`, `Map[String, Double]`, `Option[CustomType]`, etc.) are composed directly using `buildContainerEncoder`/`buildContainerDecoder`, covering all 10 container types (Option, List, Vector, Set, Seq, Chain, NonEmptyList, NonEmptyVector, NonEmptySeq, NonEmptyChain). Saves ~12% more summonIgnoring calls.
4. **Runtime dispatch** — instead of generating N nested `.add()` calls (products) or N if-then-else branches (sum types) in the macro AST, the macro builds flat `Array[Encoder]`/`Array[Decoder]` and delegates to runtime while-loops in `SanelyRuntime`, reducing generated AST size by ~30-50%.
5. **Sub-trait detection cache** — when `resolveOneEncoder`/`resolveOneDecoder` resolves a type via `Expr.summonIgnoring`, the cache key is recorded in a `summonedKeys` set. In `deriveSum`, sub-trait detection checks this set (O(1) lookup) instead of calling `summonIgnoring` again, eliminating redundant implicit searches. For configured codec derivation, this reduced per-call sub-trait detection time from 0.29ms to 0.03ms (-90%).
6. **Factory method consolidation** — instead of each macro expansion generating its own anonymous `Encoder`/`Decoder`/`Codec` class definition (300+ classes for 300 types), all 6 macro files delegate to 11 factory methods in `SanelyRuntime` that define each anonymous class template once. Type-specific data (field names, encoder/decoder arrays) is passed as parameters. Lazy initialization uses `() => Array[Encoder[Any]]` lambdas which compile to `invokedynamic` (not anonymous classes). This dramatically reduces the transform+backend compiler phases — from heavier than circe to lighter.
7. **Codec-first `summonIgnoring`** — in configured codec derivation, `resolveOneCodec` tries `Expr.summonIgnoring[Codec.AsObject[T]]` before falling back to separate `Encoder[T]` + `Decoder[T]` calls. When the nested type has a user-provided `Codec.AsObject[T]` in scope (the norm in configured derivation), one implicit search replaces two. Reduces configured summonIgnoring calls by 30% (294 → 205).

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

#### Configured derivation (230 expansions, 815ms total macro time)

| Category | Time | % | Calls | Avg |
|---|---|---|---|---|
| `topDerive`* | 708ms | 86.9% | 230 | 3.08ms |
| `summonIgnoring` | 303ms | 37.1% | 205 | 1.48ms |
| `tryBuiltin` | 30ms | 3.7% | 493 | 0.06ms |
| `resolveDefaults` | 9ms | 1.2% | 214 | 0.04ms |
| `subTraitDetect` | 2ms | 0.2% | 69 | 0.03ms |
| `cheapTypeKey` | 1ms | 0.2% | 820 | 0.00ms |
| `builtinHit` | — | — | 345 | — |
| `codecHit` | — | — | 118 | — |
| cache hits | — | — | 327 | — |

*`topDerive` is a container category that includes `summonIgnoring`, `derive`, `summonMirror`, `subTraitDetect`, and `resolveDefaults`. Percentages sum > 100% due to nesting.

#### Optimization effectiveness

| Metric | Auto | Configured |
|---|---|---|
| Total macro expansions | 308 | 230 |
| `summonIgnoring` calls | 660 | 205 |
| Builtin short-circuit hits | 706 | 345 |
| Container composition hits | included in builtin | included in builtin |
| Cache hit rate | 75% (1714 hits) | — (327 hits) |
| Codec-first hits | — | 118 |
| summonIgnoring % of total | 49% | 37% |
| `tryBuiltin` time | 47ms (1.7%) | 30ms (3.7%) |
| `cheapTypeKey` time | 4ms (0.1%) | 1ms (0.2%) |

`summonIgnoring` (the compiler's implicit search) dominates auto derivation at 49%. Builtin short-circuiting and container composition resolve ~706 type lookups without calling `summonIgnoring` at all. Sub-trait detection uses cached `summonedKeys` (O(1) set lookup) instead of re-calling `summonIgnoring`, reducing per-call time from 0.29ms to 0.04ms in configured derivation (-86%). For configured derivation, single-pass codec derivation halved the macro expansion count from 460 (separate CfgEncoder + CfgDecoder) to 230 (unified CfgCodec), while sharing one cache and one Mirror summon per type. Codec-first `summonIgnoring` further reduced configured calls from 294 to 205 (-30%) by trying `Codec.AsObject[T]` before separate `Encoder[T]` + `Decoder[T]` — 118 of 147 type pairs were resolved with a single call. The intra-expansion cache achieves a 75% hit rate in auto derivation, avoiding redundant derivations for repeated types within a single macro call. Factory method consolidation reduced macro framework overhead from 359ms to 231ms (-36%) by generating simpler factory calls instead of full anonymous class definitions. The remaining overhead is intrinsic to Scala 3's quote reflection (tuple type recursion at ~2ms/field, AST construction, quote splicing).

## Automated benchmarks

Every release automatically triggers a [benchmark workflow](.github/workflows/benchmark.yml) that runs five benchmarks in parallel on `ubuntu-latest`:

| Job | What it measures |
|---|---|
| **compile-auto** | Compile time — auto derivation (~300 types), sanely vs circe-generic |
| **compile-configured** | Compile time — configured derivation (~230 types), sanely vs circe-core |
| **runtime** | Encoding/decoding throughput — circe-jawn vs circe+jsoniter vs sanely-jsoniter vs jsoniter-scala |
| **macro-profile-auto** | Macro expansion profiling — auto derivation (308 expansions) |
| **macro-profile-configured** | Macro expansion profiling — configured derivation (230 expansions) |

Results accumulate in [`BENCHMARK.md`](BENCHMARK.md) — each release adds a new section so you can track performance across versions. The workflow opens a PR with the updated results after each run.

Compile-time benchmark entries include hyperfine's statistical output (mean ± σ, min, max) so you can see exactly how the numbers were produced.

**Benchmarking a PR:** Maintainers can comment `/benchmark` on any pull request to run the full benchmark suite against that PR's code. Results are posted as a collapsible comment on the PR. Only repository collaborators can trigger this.

You can also trigger it manually from the Actions tab with custom parameters (number of runs, warmup iterations, etc.).

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
bash bench-runtime.sh        # runtime benchmark (circe vs circe+jsoniter vs sanely-jsoniter vs jsoniter-scala)
```

## How it's made

This entire library — every macro, every test, every line of build config, and this README — was written by [Claude Code](https://claude.com/claude-code) with Claude Opus 4.6. 100% vibe coded.

## Roadmap

See [ROADMAP.md](ROADMAP.md).

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
