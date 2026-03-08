# circe-sanely-auto

[![CI](https://github.com/nguyenyou/circe-sanely-auto/actions/workflows/ci.yml/badge.svg)](https://github.com/nguyenyou/circe-sanely-auto/actions/workflows/ci.yml)
[![Maven Central Version](https://img.shields.io/maven-central/v/io.github.nguyenyou/circe-sanely-auto_3)](https://central.sonatype.com/artifact/io.github.nguyenyou/circe-sanely-auto_3)
![Scala3](https://img.shields.io/badge/Scala%203-%23de3423.svg?logo=scala&logoColor=white)

Drop-in replacement for circe's derivation on Scala 3. Compile faster, run faster, change nothing else.

**Scala 3.8.2+ | JVM + Scala.js | ✅ 327 tests passing**

## The circe problem

[circe](https://github.com/circe/circe) is the most widely used JSON library in the Scala ecosystem. It's well-designed, well-tested, and deeply embedded in production codebases everywhere — from startups to large enterprises. Frameworks like Tapir, http4s, and Pekko HTTP all integrate with circe. If you write Scala, you probably use circe.

But circe has two performance problems:

**Slow compilation.** circe-generic's `auto` derivation triggers a new round of implicit resolution for every nested type. For a codebase with hundreds of types, these rounds compound — each type waits for all its fields to be resolved, which wait for their fields, and so on. A clean compile of 350 types takes over 9 seconds just for derivation. In large monorepos with thousands of types, this adds minutes to every build.

**Slow runtime.** circe parses JSON into an intermediate `Json` AST, then traverses that AST to build your domain objects. Every request allocates a full tree of `Json` nodes only to throw them away immediately. This parse-allocate-traverse pipeline caps throughput at ~130K ops/sec and allocates ~28 KB per decode for a typical 1.4 KB payload.

The catch: you can't just switch libraries. circe is not merely a dependency — it's infrastructure. Production codebases have `io.circe.Json` as field types in domain models, cursor navigation in business logic, tree manipulation in API layers, and framework integrations wired to `Encoder[T]`/`Decoder[T]`. Migrating away from circe is a rewrite, not a refactor.

## What this library does

circe-sanely-auto fixes both problems without changing anything else:

> **The contract: 100% circe compatibility, zero compromise.** Same JSON output, same decoded values, same error messages, same behavior in every edge case. You swap the dependency and nothing changes except performance. Any difference is a bug. No exceptions, no "close enough", no subtle surprises. This is the promise that makes or breaks this library.

This contract is enforced by 327 tests — including 192 property-based tests auto-generated from circe's own test suite using the same types, same `Arbitrary` instances, and same property checks.

## The numbers

### Compile time — 2.9–3.6x faster

~350 types, measured with [hyperfine](https://github.com/sharkdp/hyperfine):

| | circe | circe-sanely-auto | |
|---|---|---|---|
| **Auto derivation** | 9.25s | **2.56s** | **3.6x faster** |
| **Configured derivation** | 2.74s | **1.27s** | **2.2x faster** |
| **Bytecode** | 3,384 KB | **2,635 KB** | **-22%** |
| **Peak RSS** | 1,133 MB | **893 MB** | **-21%** |

<details>
<summary>CI numbers (ubuntu-latest, GitHub Actions shared runners)</summary>

Shared runners are noisier than dedicated hardware. Absolute times are ~3x slower; **ratios** are the meaningful metric:

| | circe | circe-sanely-auto | |
|---|---|---|---|
| **Auto derivation** | 21.0s | **7.2s** | **2.9x faster** |
| **Configured derivation** | 6.6s | **4.6s** | **1.4x faster** |
| **Peak RSS (auto)** | 1,041 MB | **863 MB** | **-17%** |

The configured speedup (1.4x on CI vs 2.2x locally) is compressed by CI noise — sanely's σ is 1.26s on a 4.6s mean (27% CoV), while circe-generic's σ is 0.25s (4% CoV). The speedup is real but smaller workloads are harder to measure reliably on shared runners. Full CI results: [BENCHMARK.md](BENCHMARK.md).
</details>

### Runtime — 4–5x faster reads, 6–7x faster writes, 85–95% less allocation

~1.4 KB JSON payload, nested products, sealed traits, optional fields:

| Approach | Reading (ops/sec) | | Alloc/read | Writing (ops/sec) | | Alloc/write |
|---|---|---|---|---|---|---|
| **circe + jawn** (baseline) | ~135K | 1.0x | 28 KB | ~124K | 1.0x | 27 KB |
| **circe + jsoniter bridge** | ~196K | 1.5x | 25 KB | ~109K | 0.9x | 23 KB |
| **sanely-jsoniter** | **~762K** | **5.7x** | **3 KB** | **~770K** | **6.2x** | **1 KB** |
| jsoniter-scala native | ~742K | 5.5x | 3 KB | ~615K | 5.0x | 1 KB |

sanely-jsoniter **surpasses jsoniter-scala native** on both reads and writes on Apple Silicon, while remaining competitive on x86. Both dramatically outperform circe-jawn. sanely-jsoniter allocates **89% less memory per read** and **96% less per write** compared to circe-jawn, meaning less GC pressure in high-throughput services.

### Compile-time cost of adding sanely-jsoniter

Adding jsoniter codecs on top of existing circe codecs. 199 types, measured with [hyperfine](https://github.com/sharkdp/hyperfine):

| Module | Mean ± σ | |
|---|---|---|
| **circe codecs only** (baseline) | 811ms ± 21ms | 1.0x |
| **circe + jsoniter codecs** | 1,560ms ± 101ms | 1.9x |
| **Marginal cost** | **~749ms** | **~3.8ms/type** |

The jsoniter codec derivation roughly doubles compile time for the affected module — but in absolute terms it's under 1 second for 199 types. For a codebase with hundreds of types, expect sub-2-second additional compile time in exchange for 5–6x runtime throughput.

<details>
<summary>CI numbers (ubuntu-latest, GitHub Actions shared runners)</summary>

| Approach | Reading (ops/sec) | | Writing (ops/sec) | |
|---|---|---|---|---|
| **circe + jawn** (baseline) | ~90K | 1.0x | ~58K | 1.0x |
| **sanely-jsoniter** | **~370K** | **4.1x** | **~378K** | **6.5x** |
| jsoniter-scala native | ~362K | 4.0x | ~422K | 7.3x |

On CI (x86), jsoniter-scala is ~11% faster on writes while sanely-jsoniter is ~2% faster on reads. On Apple Silicon (M3 Max), sanely-jsoniter leads on both axes. The performance difference between the two is small and architecture-dependent — both are in the same league. Full CI results: [BENCHMARK.md](BENCHMARK.md).
</details>

<details>
<summary>Benchmark methodology & cross-session stability</summary>

**Local environment**: Apple M3 Max (10P + 4E cores), 36 GB RAM, macOS 26.3, OpenJDK 25.0.2 (Homebrew, aarch64), Mill 1.1.2 (zinc on JDK 21.0.9), Scala 3.8.2.

**CI environment**: `ubuntu-latest` (GitHub Actions shared runners), x86_64, OpenJDK 25. CPU governor set to `performance`, turbo boost disabled. Full results tracked in [BENCHMARK.md](BENCHMARK.md).

**Compile-time**: Measured with [hyperfine](https://github.com/sharkdp/hyperfine) (`bash bench.sh 10`). Each run cleans only the benchmark module's output then recompiles ~350 types (auto) or ~230 types (configured). One untimed warmup run ensures the Mill daemon JVM is JIT-warm. Ten timed runs follow, with hyperfine randomizing execution order. Dependencies are pre-compiled and cached. Cross-session stability: speedup ranged 3.55x–3.61x across 20 runs in 2 separate sessions (local). The jsoniter marginal cost benchmark (`bash bench.sh --jsoniter 10`) compiles 199 types with circe codecs only vs circe + jsoniter codecs, measuring the additional compile-time cost of adopting sanely-jsoniter.

**Runtime**: Each configuration runs 5 warmup + 5 measured iterations of 1 second each. Allocation per operation measured via `ThreadMXBean.getThreadAllocatedBytes` (precise, per-thread, no GC noise). Numbers reported are from a representative run.

**Fairness**: Both libraries compile the same source files, same Scala version, same JVM, same Mill daemon, in the same hyperfine invocation. The only difference is the derivation import.
</details>

## How to use it

### Step 1: Faster compilation

Swap one dependency, change one import:

```diff
- mvn"io.circe::circe-generic:0.14.x"
+ mvn"io.github.nguyenyou::circe-sanely-auto:0.18.0"
```

```diff
- import io.circe.generic.auto._
+ import io.circe.generic.auto.given
```

That's it. Same JSON format, same API, same behavior. Everything else stays the same — semiauto (`deriveEncoder`, `deriveDecoder`, `deriveCodec`), configured (`deriveConfiguredCodec`, `deriveEnumCodec`), and all `io.circe.derivation.Configuration` options work identically.

### Step 2: Faster runtime (optional)

For HTTP hot paths where runtime throughput matters, [sanely-jsoniter](sanely-jsoniter/) generates `JsonValueCodec[T]` instances that skip the `Json` tree entirely — bytes go directly to domain objects:

```scala
mvn"io.github.nguyenyou::sanely-jsoniter:0.18.0"
```

```scala
import sanely.jsoniter.auto.given  // auto-derives JsonValueCodec for all types
import com.github.plokhotnyuk.jsoniter_scala.core.*

case class User(name: String, age: Int)

val json = writeToString(User("Alice", 30))  // {"name":"Alice","age":30}
val user = readFromString[User](json)        // User(Alice, 30)
```

Both circe codecs and jsoniter codecs coexist — they're different types, no conflicts. circe stays for everything that needs the `Json` AST (cursor navigation, tree merging, programmatic construction). Only the serialization hot path changes.

There's also a lighter option — pair circe-sanely-auto with the [jsoniter-scala-circe bridge](https://github.com/plokhotnyuk/jsoniter-scala) for a drop-in 1.5x decode speedup without changing any codec code.

See the [sanely-jsoniter README](sanely-jsoniter/README.md) for full documentation, configured derivation, and migration guide.

## How it works

### Compile time: one macro, no search chains

Based on Mateusz Kubuszok's [sanely-automatic derivation](https://kubuszok.com/2025/sanely-automatic-derivation/) technique. The core problem with circe-generic is how `inline given` and `summonInline` interact: every nested type triggers a new round of implicit resolution through the compiler, and each round must wait for all its dependencies. For 300 types with 5 fields average, that's 1,500+ implicit searches the compiler performs sequentially.

circe-sanely-auto replaces this with a single macro expansion:

1. An `inline given autoEncoder[A]` delegates to a macro
2. The macro uses `Expr.summonIgnoring` (Scala 3.7+) to search for existing instances — excluding our own auto-given from the search to prevent infinite loops
3. If a user-provided instance exists, it's used. Otherwise, the macro derives it recursively within the same expansion

One macro call derives everything. The compiler never re-enters implicit search. Seven optimizations on top of this reduce both macro expansion time and generated AST size:

- **Single-pass codec derivation** — `deriveConfiguredCodec` resolves both encoder and decoder in one traversal, sharing one cache, one `Mirror` summon, and one builtin check per type (230 vs 460 expansions)
- **Builtin short-circuit** — primitives resolve directly to circe instances without `summonIgnoring`, saving ~66% of implicit searches
- **Container composition** — `Option[String]`, `List[Int]`, `Map[String, T]` etc. are composed directly from cached inner codecs (10 container types supported)
- **Factory method consolidation** — 11 runtime factory methods replace per-expansion anonymous class generation, reducing generated AST size by ~30-50%
- **Sub-trait detection cache** — O(1) set lookup replaces redundant `summonIgnoring` calls for sealed trait sub-type detection (-90% per-call time)
- **Codec-first summon** — tries `Codec.AsObject[T]` before separate `Encoder[T]` + `Decoder[T]`, reducing configured implicit searches by 30%
- **Cross-expansion cache** — 75% hit rate in auto derivation, avoiding redundant derivations for repeated types

### Runtime: no intermediate AST, direct streaming

circe's pipeline allocates a `Json` tree on every request:

```
bytes  →  Json tree (allocated)  →  Decoder[T]  →  T
T  →  Encoder[T]  →  Json tree (allocated)  →  bytes
```

sanely-jsoniter eliminates the tree entirely, using jsoniter-scala as the streaming engine:

```
bytes  →  JsonValueCodec[T]  →  T
T  →  JsonValueCodec[T]  →  bytes
```

The macro generates optimized codec bodies using TASTy API:

- **Typed local variables** — `var _0: Int = 0; var _1: String = null` instead of `Array[Any]`, keeping primitives unboxed on the stack during the decode loop
- **Hash-based field dispatch** — for types with > 8 fields, `(in.charBufToHashCode(l): @switch) match { ... }` with compile-time pre-computed hashes; small types keep a linear if-else chain
- **Direct primitive read/write calls** — `in.readInt()`, `out.writeVal(x.age)` instead of virtual dispatch through `codec.decodeValue()`/`codec.encodeValue()` for primitive fields
- **Direct constructor call** — `new P(_f0, _f1, ...)` instead of `mirror.fromProduct(ArrayProduct(Array(...)))`, eliminating primitive boxing and two intermediate allocations per product decode
- **Branchless product encoding** — every field is unconditionally written in a straight-line sequence: write-key, write-value, write-key, write-value. No per-field conditional checks

This brings sanely-jsoniter to **surpass jsoniter-scala native** on both reads and writes on Apple Silicon, while remaining competitive on x86 — all while maintaining full circe wire compatibility, with 89–96% less allocation than circe-jawn.

<details>
<summary>Why sanely-jsoniter is competitive with jsoniter-scala native</summary>

**Writes**: jsoniter-scala's default configuration (`transientNone`, `transientEmpty`, `transientDefault` all enabled) generates per-field conditional branches. sanely-jsoniter writes every field unconditionally — the circe format requires `null` for `None` and always includes all fields. The macro emits a branchless straight-line sequence. On some architectures (e.g. Apple Silicon), this gives sanely-jsoniter an edge; on others (e.g. x86 CI runners), jsoniter-scala is ~11% faster on writes. The difference is small and architecture-dependent.

**Reads**: Both use the same direct constructor pattern (`new P(f0, f1, ...)`), typed locals, and hash-based field dispatch. sanely-jsoniter's simpler field semantics (no transient/default field skipping, no `transientEmpty` checks during decode) means fewer branches in the hot loop. Performance is within a few percent of each other.
</details>

## Compatibility

circe-sanely-auto provides the same packages and APIs as circe-generic:

| Before | After |
|---|---|
| `mvn"io.circe::circe-generic:0.14.x"` | `mvn"io.github.nguyenyou::circe-sanely-auto:0.18.0"` |
| `import io.circe.generic.auto._` | `import io.circe.generic.auto.given` |
| `import io.circe.generic.semiauto._` | `import io.circe.generic.semiauto.*` (unchanged) |

**327 tests**: 135 unit tests (utest, cross-compiled JVM + Scala.js) covering auto, semiauto, and configured derivation. Plus 192 compatibility tests (munit + discipline) auto-generated from circe's own `DerivesSuite`, `SemiautoDerivationSuite`, `ConfiguredDerivesSuite`, and `ConfiguredEnumDerivesSuites` via `scripts/sync-circe-tests.py` — same types, same Arbitrary instances, same property-based checks.

**Requirement**: Scala 3.8.2+. No Scala 2 support.

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

## Detailed benchmarks

### Compile-time benchmarks

Two benchmark suites compare compile times against circe's native derivation. All numbers from M3 Max MacBook Pro, Mill 1.1.2, Scala 3.8.2.

```bash
bash bench.sh 5              # auto derivation (~350 types)
bash bench.sh --configured 5 # configured derivation (~230 types)
```

#### Results

| Suite | circe-sanely-auto | circe baseline | Speedup |
|---|---|---|---|
| **Auto derivation** (~350 types) | **2.56s** ± 0.07s | 9.25s ± 0.04s (circe-generic) | **3.61x** ± 0.09 |
| **Configured derivation** (~230 types) | **1.27s** ± 0.03s | 2.74s ± 0.04s (circe-core) | **2.16x** ± 0.05 |

#### Benchmark method

Measurements use [hyperfine](https://github.com/sharkdp/hyperfine) for statistical rigor. The harness (`bench.sh`) works as follows:

1. **Dependency warm-up** — `sanely.jvm` (and the configured compat shim for `--configured`) are compiled via the Mill daemon before any timed run, so dependency compilation is never included in the measurement.
2. **`--warmup 1`** — one untimed warmup round ensures the Mill daemon JVM is JIT-warm and OS file caches are hot.
3. **`--prepare 'rm -rf out/…'`** — before each timed run, the benchmark module's output is deleted so every measurement is a clean recompilation of only the benchmark sources.
4. **`--runs N`** — N timed runs per command. Hyperfine randomizes execution order across runs to prevent systematic ordering bias, and reports mean ± σ with min/max range.

This measures what users actually experience: warm-daemon, incremental-dependency compilation of the benchmark types only.

### JVM profiling (async-profiler)

JVM-level profiling with async-profiler shows where the Scala compiler spends time. Compiler-only samples (excluding JIT/GC cold-start overhead):

#### Auto derivation — compiler workload

| Phase | sanely (825 samples) | circe-generic (1558 samples) | Delta |
|---|---|---|---|
| core (types, symbols, contexts) | 298 (36%) | 519 (33%) | **-43%** |
| ast (tree maps, accumulators) | 89 (11%) | 242 (16%) | **-63%** |
| typer | 69 (8%) | 90 (6%) | -23% |
| other (infra, denotations) | 113 (14%) | 148 (10%) | -24% |
| transform (erasure, lambdalift) | 63 (8%) | 78 (5%) | -19% |
| backend (bytecode, classfiles) | 45 (5%) | 70 (4%) | -36% |
| macro inlines | 7 (1%) | 71 (5%) | **-90%** |
| macro quoted | 12 (1%) | 7 (0%) | sanely only |
| typer implicits | 14 (2%) | 14 (1%) | 0% |
| parsing | 11 (1%) | 7 (0%) | +57% |
| **total compiler** | **825** | **1558** | **-47%** |

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

**Key pattern**: Sanely trades inlining for quote reflection. circe-generic/circe-core's `inline` + `summonInline` approach forces the compiler to do heavy inlining work (71 samples for auto, 30 for configured). Sanely's `Expr.summonIgnoring` approach shifts that work to the quote reflection phase (12/6 samples) which is cheaper. Factory method consolidation eliminated the previous regression in transform+backend phases — sanely is now lighter than circe in all phases. For auto derivation, the total compiler workload is **47% lighter**. For configured, the compiler workload is **23% lighter**.

### Memory profiling

Peak RSS via `/usr/bin/time -l`, allocation samples via async-profiler `event=alloc`.

#### Peak RSS

| Suite | sanely | circe baseline | Delta |
|---|---|---|---|
| **Auto derivation** (~350 types) | **893 MB** | 1,133 MB (circe-generic) | **-21%** |
| **Configured derivation** (~230 types) | **752 MB** | 750 MB (circe-core) | ~0% |

Auto derivation uses **21% less peak memory** thanks to fewer generated classes and smaller ASTs. Configured derivation uses comparable peak memory. RSS includes compiler heap, JIT code, metaspace, and OS buffers.

#### Allocation pressure — auto derivation

| Category | sanely | circe-generic | Delta |
|---|---|---|---|
| **total** | **4,822** | **11,571** | **-58%** |
| compiler | 3,942 (82%) | 10,674 (92%) | **-63%** |
| compiler.core | 1,444 | 4,069 | **-65%** |
| compiler.core.types | 465 | 1,477 | **-69%** |
| compiler.ast | 335 | 1,582 | **-79%** |
| compiler.macro.inlines | 70 | 721 | **-90%** |
| compiler.typer | 246 | 549 | -55% |
| compiler.backend | 204 | 351 | -42% |
| mill / zinc / jvm | 880 | 897 | -2% |

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

### Bytecode impact

Bytecode size and method count measured with `javap` across all compiled class files. Fewer methods means faster classloading, less JIT work, and smaller vtables. Measured with `python3 .claude/skills/bytecode-impact/scripts/analyze_bytecode.py`.

#### Auto derivation (~350 types)

| Metric | circe-sanely-auto | circe-generic | Delta |
|---|---|---|---|
| Total bytecode | 2,634,732 bytes | 3,384,133 bytes | **-22.1%** |
| Total methods | 12,433 | 13,761 | **-9.7%** |
| LazyRef/lzyINIT/monitor | 0 | 0 | — |

#### Configured derivation (~230 types)

| Metric | circe-sanely-auto | circe-core | Delta |
|---|---|---|---|
| Total bytecode | 2,775,933 bytes | 3,097,489 bytes | **-10.4%** |
| Total methods | 8,991 | 9,449 | **-4.8%** |
| lzyINIT patterns | 1,200 | 1,658 | **-27.6%** |

Auto derivation generates **22% less bytecode** and **1,328 fewer methods** thanks to `SanelyRuntime` factory methods (consolidating encoder/decoder construction into shared templates) and lazy-val elimination for non-recursive types. Configured derivation generates **10% less bytecode** with **27% fewer lazy initialization patterns**.

### Macro profiling

Built-in compile-time profiling via `SANELY_PROFILE=true` tracks where time is spent inside our macros:

```bash
# Profile auto derivation (~350 types)
rm -rf out/benchmark/sanely
SANELY_PROFILE=true ./mill --no-server benchmark.sanely.compile 2>&1 | tee /tmp/profile.txt
python3 .claude/skills/macro-profile/scripts/analyze_profile.py /tmp/profile.txt

# Profile configured derivation (~230 types)
rm -rf out/benchmark-configured/sanely
SANELY_PROFILE=true ./mill --no-server benchmark-configured.sanely.compile 2>&1 | tee /tmp/profile.txt
python3 .claude/skills/macro-profile/scripts/analyze_profile.py /tmp/profile.txt
```

#### Auto derivation (398 expansions, 2.5s total macro time)

| Category | Time | % | Calls | Avg |
|---|---|---|---|---|
| `summonIgnoring` | 1340ms | 53.7% | 894 | 1.50ms |
| `derive` | 916ms | 36.7% | 920 | 1.00ms |
| `tryBuiltin` | 125ms | 5.0% | 1943 | 0.06ms |
| `summonMirror` | 102ms | 4.1% | 920 | 0.11ms |
| `subTraitDetect` | 45ms | 1.8% | 336 | 0.13ms |
| `cheapTypeKey` | 3ms | 0.1% | 4342 | 0.00ms |
| `builtinHit` | — | — | 907 | — |
| `constructorNegHit` | — | — | 142 | — |
| cache hits | — | — | 2399 (75%) | — |

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

## Automated benchmarks

Every release automatically triggers a [benchmark workflow](.github/workflows/benchmark.yml) that runs five benchmarks in parallel on `ubuntu-latest`:

| Job | What it measures |
|---|---|
| **compile-auto** | Compile time — auto derivation (~350 types), sanely vs circe-generic |
| **compile-configured** | Compile time — configured derivation (~230 types), sanely vs circe-core |
| **runtime** | Encoding/decoding throughput — circe-jawn vs circe+jsoniter vs sanely-jsoniter vs jsoniter-scala |
| **macro-profile-auto** | Macro expansion profiling — auto derivation (398 expansions) |
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
