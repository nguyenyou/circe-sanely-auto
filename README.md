# circe-sanely-auto

[![CI](https://github.com/nguyenyou/circe-sanely-auto/actions/workflows/ci.yml/badge.svg)](https://github.com/nguyenyou/circe-sanely-auto/actions/workflows/ci.yml)

Drop-in replacement for circe's auto/semi-auto/configured derivation for Scala 3. Faster compile times. No Shapeless. No circe-generic.

**Scala 3.8.2+ | JVM + Scala.js | ✅ 447 tests**

## Motivation

[circe-generic](https://github.com/circe/circe) is the standard way to auto-derive JSON codecs in Scala. It works, but it's slow. On large codebases with hundreds of case classes and sealed traits, `circe-generic` adds significant compile time because it relies on implicit search chains — each nested type triggers a new round of implicit resolution, and those rounds compound.

This library exists to fix that. It replaces `circe-generic` entirely with a macro-based implementation that derives all `Encoder`/`Decoder` instances in a single macro expansion, avoiding implicit search chains altogether.

## Inspiration

The approach is based on Mateusz Kubuszok's [sanely-automatic derivation](https://kubuszok.com/2025/sanely-automatic-derivation/) technique. The key insight: Scala 3.7+ provides `Expr.summonIgnoring`, which lets a macro summon implicit instances while excluding specific symbols. This means we can:

1. Define an `inline given autoEncoder[A]` that delegates to a macro
2. Inside the macro, use `Expr.summonIgnoring` to search for an existing `Encoder[A]` — excluding our own auto-given from the search so we don't infinitely recurse
3. If a user-provided instance exists, use it. Otherwise, derive it internally using `Mirror` — and recursively apply the same logic to all nested types within the same macro expansion

The result: one macro call derives everything. No implicit search chains. No Shapeless. Just Scala 3 `Mirror` and `scala.quoted`.

## Compatibility

The goal is full API compatibility with circe's derivation. You should be able to swap the dependency, update one import, and have everything work the same — same JSON format, same behavior, same error messages where possible.

### How we verify compatibility

We maintain two layers of tests:

**✅ 124 unit tests** (utest, cross-compiled JVM + Scala.js) covering auto-derivation (products, sum types, case objects, generics, recursive types, large types, edge cases, error cases, semiauto API) and configured derivation (all 5 configuration options, enum codecs, hierarchical sealed traits, multi-level hierarchies, recursive types with discriminators).

**✅ 318 compatibility tests** (munit + discipline) ported directly from circe's own test suite — `DerivesSuite`, `SemiautoDerivationSuite`, and `ConfiguredDerivesSuite`. These use circe's `CodecTests` which runs property-based checks: roundtrip consistency, accumulating decoder consistency, and `Codec.from` consistency. Same test types, same Arbitrary instances, same assertions. If circe's tests pass with circe-generic, they pass with circe-sanely-auto.

**✅ 447 tests total**, all green.

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

### Step 1: Swap the dependency

```diff
- mvn"io.circe::circe-generic:0.14.x"
+ mvn"io.github.nguyenyou::circe-sanely-auto:0.10.0"
```

### Step 2: Update imports

| Before | After |
|---|---|
| `import io.circe.generic.auto._` | `import io.circe.generic.auto.given` |
| `import io.circe.generic.semiauto._` | `import io.circe.generic.semiauto.*` (unchanged) |

The `io.circe.generic.auto` and `io.circe.generic.semiauto` packages are provided by this library with the same API — they delegate to the sanely macro engine internally.

### Step 3: No other changes needed

Semiauto calls (`deriveEncoder`, `deriveDecoder`, `deriveCodec`) work identically. Configured derivation (`deriveConfiguredEncoder`, `deriveConfiguredDecoder`, `deriveConfiguredCodec`, `deriveEnumCodec`) works identically. JSON format is the same. User-provided instances are still respected.

### What changes

- **Faster compile times** — single macro expansion vs implicit search chains
- **Scala 3 only** — no Scala 2 support, requires 3.8.2+
- **No Shapeless** — uses Scala 3 `Mirror` + `Expr.summonIgnoring`

## Compile-time benchmarks

Two benchmark suites compare compile times against circe's native derivation. All numbers from M3 Max MacBook Pro, Mill 1.1.2, Scala 3.8.2.

```bash
bash bench.sh 5              # auto derivation (~300 types)
bash bench.sh --configured 5 # configured derivation (~230 types)
```

### Results

| Suite | circe-sanely-auto | circe baseline | Speedup |
|---|---|---|---|
| **Auto derivation** (~300 types) | **3.91s** | 6.94s (circe-generic) | **1.77x** |
| **Configured derivation** (~230 types) | **2.08s** | 2.86s (circe-core) | **1.38x** |

### Why the difference?

**Auto derivation** (1.77x faster): With `import io.circe.generic.auto.given`, the compiler must implicitly search for and synthesize codecs at every use site — each nested type triggers another round of implicit resolution. Sanely avoids this by deriving everything in a single macro expansion.

**Configured derivation** (38% faster): Even though configured derivation uses explicit semi-auto calls (`deriveConfiguredCodec` in each companion object) with no implicit search chain to eliminate, our optimizations reduce both macro expansion time and generated AST size.

Four optimizations drive this:

1. **Single-pass codec derivation** — `deriveConfiguredCodec` uses a dedicated macro that resolves both encoder and decoder for each field type in one traversal, sharing one cache, one `Mirror` summon, and one builtin check per type. Halves the number of macro expansions (230 vs 460).
2. **Builtin short-circuit** — primitive types (String, Int, Long, Double, Float, Boolean, Short, Byte, BigDecimal, BigInt) are resolved directly to their circe instances without calling `Expr.summonIgnoring`, saving ~66% of summonIgnoring calls.
3. **Container composition** — containers of primitives or already-cached types (`Option[String]`, `List[Int]`, `Map[String, Double]`, `Option[CustomType]`, etc.) are composed directly using `buildContainerEncoder`/`buildContainerDecoder`, covering all 10 container types (Option, List, Vector, Set, Seq, Chain, NonEmptyList, NonEmptyVector, NonEmptySeq, NonEmptyChain). Saves ~12% more summonIgnoring calls.
4. **Runtime dispatch** — instead of generating N nested `.add()` calls (products) or N if-then-else branches (sum types) in the macro AST, the macro builds flat `Array[Encoder]`/`Array[Decoder]` and delegates to runtime while-loops in `SanelyRuntime`, reducing generated AST size by ~30-50%.

### JVM profiling (async-profiler)

JVM-level profiling with async-profiler shows where the Scala compiler spends time. Compiler-only samples (excluding JIT/GC cold-start overhead):

#### Auto derivation — compiler workload

| Phase | sanely (911 samples) | circe-generic (1252 samples) | Delta |
|---|---|---|---|
| core (types, symbols, contexts) | 389 (43%) | 635 (51%) | -39% |
| ast (tree maps, accumulators) | 112 (12%) | 193 (15%) | -42% |
| typer | 92 (10%) | 102 (8%) | -10% |
| other (infra, denotations) | 120 (13%) | 117 (9%) | +3% |
| transform (erasure, lambdalift) | 76 (8%) | 73 (6%) | +4% |
| backend (bytecode, classfiles) | 70 (8%) | 62 (5%) | +13% |
| macro inlines | 17 (2%) | 43 (3%) | **-60%** |
| macro quoted | 17 (2%) | 0 (0%) | sanely only |
| typer implicits | 8 (1%) | 8 (1%) | same |
| parsing | 10 (1%) | 10 (1%) | same |
| **total compiler** | **911** | **1252** | **-27%** |

#### Configured derivation — compiler workload

| Phase | sanely (734 samples) | circe-core (800 samples) | Delta |
|---|---|---|---|
| core (types, symbols, contexts) | 309 (42%) | 379 (47%) | -18% |
| ast (tree maps, accumulators) | 98 (13%) | 115 (14%) | -15% |
| typer | 73 (10%) | 65 (8%) | +12% |
| other (infra, denotations) | 89 (12%) | 86 (11%) | +3% |
| transform (erasure, lambdalift) | 66 (9%) | 57 (7%) | +16% |
| backend (bytecode, classfiles) | 61 (8%) | 48 (6%) | +27% |
| macro inlines | 11 (1%) | 19 (2%) | **-42%** |
| macro quoted | 8 (1%) | 6 (1%) | +33% |
| typer implicits | 9 (1%) | 13 (2%) | -31% |
| parsing | 7 (1%) | 9 (1%) | -22% |
| **total compiler** | **734** | **800** | **-8%** |

**Key pattern**: Sanely trades inlining for quote reflection. circe-generic/circe-core's `inline` + `summonInline` approach forces the compiler to do heavy inlining work (43 samples for auto, 19 for configured). Sanely's `Expr.summonIgnoring` approach shifts that work to the quote reflection phase (17/8 samples) which is cheaper. For auto derivation, the total compiler workload is **27% lighter**. For configured, single-pass codec derivation reduces compiler work by **8%** — the 38% wall-clock speedup comes from both reduced compiler work and reduced JIT/classload overhead due to fewer macro expansions (230 vs 460).

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

#### Auto derivation (308 expansions, 2.4s total macro time)

| Category | Time | % | Calls | Avg |
|---|---|---|---|---|
| `summonIgnoring` | 1112ms | 46.2% | 660 | 1.69ms |
| `derive` | 779ms | 32.3% | 586 | 1.33ms |
| `summonMirror` | 88ms | 3.6% | 586 | 0.15ms |
| `subTraitDetect` | 47ms | 2.0% | 336 | 0.14ms |
| `builtinHit` | — | — | 706 | — |
| cache hits | — | — | 1714 (75%) | — |
| overhead | 382ms | 15.9% | — | — |

#### Configured derivation (230 expansions, 1.1s total macro time)

| Category | Time | % | Calls | Avg |
|---|---|---|---|---|
| `topDerive`* | 977ms | 86.7% | 230 | 4.25ms |
| `summonIgnoring` | 337ms | 29.9% | 294 | 1.15ms |
| `subTraitDetect` | 20ms | 1.8% | 69 | 0.29ms |
| `resolveDefaults` | 10ms | 0.9% | 214 | 0.05ms |
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
| summonIgnoring % of total | 46% | 30% |

`summonIgnoring` (the compiler's implicit search) dominates auto derivation at 46%. Builtin short-circuiting and container composition resolve ~706 type lookups without calling `summonIgnoring` at all. For configured derivation, single-pass codec derivation halved the macro expansion count from 460 (separate CfgEncoder + CfgDecoder) to 230 (unified CfgCodec), while sharing one cache and one Mirror summon per type. The `summonIgnoring` call count stays at 294 (both encoder and decoder must still be summoned), but sub-trait detection halved from 138 to 69 calls. The intra-expansion cache achieves a 75% hit rate in auto derivation, avoiding redundant derivations for repeated types within a single macro call.

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
- [ ] **(P2) Cheaper cache key** — `tpe.dealias.show` converts the entire type tree to a human-readable string on every field resolution (~2000+ calls). Use a cheaper representation (e.g., `TypeRepr` hash-based lookup or `.dealias.toString`).
- [ ] **Negative builtin cache** — `tryResolveBuiltinEncoder` is called for every non-cached type. When it returns `None`, the same type will be re-checked in subsequent expansions. Cache failures to skip repeated pattern matching on known non-builtin types.
- [ ] **(P2) Eliminate redundant sub-trait `summonIgnoring`** — sub-trait detection (line 74-75) calls `Expr.summonIgnoring[Encoder[t]]` to check if a user-provided encoder exists, but `resolveOneEncoder` already resolved an encoder for this variant. Pass the resolution source (summoned vs derived) to avoid the redundant implicit search. Saves up to 336 calls.
- [ ] **Deduplicate `dealias` calls** — `TypeRepr.of[T].dealias` is called 3+ times for the same type in `resolveOneEncoder`: cache key, `tryResolveBuiltinEncoder`, and inner arg resolution. Compute once at the top and reuse.
- [ ] **Profile untimed overhead** — 430ms (17%) is overhead not attributed to any timing category. Add timing around: cache key generation, `containsType` traversals, `resolveFields` tuple recursion, `Type.valueOfConstant`, Mirror pattern matching. Find whether it's one hot spot or distributed.
- [ ] **Investigate cross-expansion caching** — each `inline given autoEncoder[A]` triggers an independent macro expansion with its own `exprCache`. If `Person` has field `Address`, both `autoEncoder[Person]` and `autoEncoder[Address]` independently derive `Address`. Emitting `lazy val` instances in a generated object could eliminate this redundancy. Needs investigation into impact on incremental compilation.
- [ ] **Reduce transform+backend compiler phases** — JVM profile shows sanely's transform (66 samples) + backend (61) > circe-core's (57 + 48), meaning sanely generates more bytecode despite runtime dispatch. Investigate generated class count/size and look for further runtime method consolidation.

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
