# circe-sanely-auto

[![CI](https://github.com/nguyenyou/circe-sanely-auto/actions/workflows/ci.yml/badge.svg)](https://github.com/nguyenyou/circe-sanely-auto/actions/workflows/ci.yml)

Drop-in replacement for circe's auto/semi-auto/configured derivation for Scala 3. Faster compile times. No Shapeless. No circe-generic.

**Scala 3.8.2+ | JVM + Scala.js | ✅ 276 tests**

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

**✅ 116 unit tests** (utest, cross-compiled JVM + Scala.js) covering auto-derivation (products, sum types, case objects, generics, recursive types, large types, edge cases, error cases, semiauto API) and configured derivation (all 5 configuration options, enum codecs, hierarchical sealed traits, multi-level hierarchies, recursive types with discriminators).

**✅ 160 compatibility tests** (munit + discipline) ported directly from circe's own test suite — `DerivesSuite`, `SemiautoDerivationSuite`, and `ConfiguredDerivesSuite`. These use circe's `CodecTests` which runs property-based checks: roundtrip consistency, accumulating decoder consistency, and `Codec.from` consistency. Same test types, same Arbitrary instances, same assertions. If circe's tests pass with circe-generic, they pass with circe-sanely-auto.

**✅ 276 tests total**, all green.

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
+ mvn"io.github.nguyenyou::circe-sanely-auto:0.8.0"
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

Two benchmark suites compare compile times against circe's native derivation:

```bash
bash bench.sh 5              # auto derivation (~300 types)
bash bench.sh --configured 5 # configured derivation (~230 types)
```

Results on M3 Max MacBook Pro (Mill 1.1.2, Scala 3.8.2):

### Auto derivation

| | Median compile time | |
|---|---|---|
| **circe-sanely-auto** | **3.17s** | |
| **circe-generic** | **6.75s** | 2.1x slower |

### Configured derivation

| | Median compile time | |
|---|---|---|
| **circe-sanely-auto** | **2.90s** | |
| **circe-core** | **2.94s** | ~same |

### Why the difference?

The speedup only applies to **auto derivation**. With `import io.circe.generic.auto.given`, the compiler must implicitly search for and synthesize codecs at every use site — each nested type triggers another round of implicit resolution. Sanely avoids this by deriving everything in a single macro expansion.

**Configured derivation** uses explicit semi-auto calls (`deriveConfiguredCodec` in each companion object). There's no implicit search chain to eliminate — you're telling the compiler exactly what to derive. JVM-level profiling (async-profiler) confirms that both implementations produce nearly identical compiler workloads:

| Compiler phase | circe-sanely-auto | circe-core | Delta |
|---|---|---|---|
| typer | 60 samples | 82 samples | -27% (sanely does less type checking) |
| macro inlines | 6 | 20 | -70% (sanely does less inlining) |
| macro quoted | 17 | 7 | +143% (sanely does more quote reflection) |
| typer implicits | 15 | 6 | +150% (sanely uses `Expr.summonIgnoring`) |
| transform | 76 | 61 | +25% (sanely generates slightly more code) |
| backend | 67 | 55 | +22% (sanely produces slightly more bytecode) |
| **total compiler** | **859** | **804** | **1.07x** (roughly equal) |

Sanely trades typer time for quoted/transform/backend time — our macros avoid some type checking overhead but generate more code via quote reflection, which the compiler's transform and backend phases then have to process. The net effect is roughly break-even.

Two optimizations reduce this overhead:

1. **Builtin short-circuit** — primitive types (String, Int, Long, Double, Float, Boolean, Short, Byte, BigDecimal, BigInt) are resolved directly to their `Encoder.encodeX`/`Decoder.decodeX` instances without calling `Expr.summonIgnoring`, saving ~66% of summonIgnoring calls.
2. **Runtime dispatch** — instead of generating N nested `.add()` calls (products) or N if-then-else branches (sum types) in the macro AST, the macro builds flat `Array[Encoder]`/`Array[Decoder]` and delegates to runtime while-loops in `SanelyRuntime`, reducing generated AST size by ~30-50%.

### Macro profiling

Built-in compile-time profiling is available via the `SANELY_PROFILE=true` environment variable. When enabled, each macro expansion prints timing data to stderr with zero cost when disabled.

```bash
# Profile auto derivation (~300 types)
SANELY_PROFILE=true ./mill --no-server clean benchmark.sanely && \
SANELY_PROFILE=true ./mill --no-server benchmark.sanely.compile 2>&1 | tee /tmp/profile.txt

# Profile configured derivation (~230 types)
SANELY_PROFILE=true ./mill --no-server clean benchmark-configured.sanely && \
SANELY_PROFILE=true ./mill --no-server benchmark-configured.sanely.compile 2>&1 | tee /tmp/profile.txt
```

**Auto derivation profile** (308 expansions, ~3.1s total macro time):

| Category | Time | % | Calls | Avg |
|---|---|---|---|---|
| `summonIgnoring` | 1487ms | 47.2% | 720 | 2.07ms |
| `derive` | 1115ms | 35.4% | 586 | 1.90ms |
| `summonMirror` | 108ms | 3.4% | 586 | 0.18ms |
| `subTraitDetect` | 61ms | 1.9% | 336 | 0.18ms |
| `builtinHit` | — | — | 646 | — |
| overhead | 377ms | 12.0% | — | — |
| cache hits | — | — | 1714 (75%) | — |

**Configured derivation profile** (460 expansions, ~1.4s total macro time):

| Category | Time | % | Calls | Avg |
|---|---|---|---|---|
| `topDerive` | 1156ms | 85.1%* | 460 | 2.51ms |
| `summonIgnoring` | 405ms | 29.8% | 336 | 1.21ms |
| `subTraitDetect` | 30ms | 2.2% | 138 | 0.22ms |
| `resolveDefaults` | 9ms | 0.7% | 214 | 0.04ms |
| `builtinHit` | — | — | 648 | — |
| cache hits | — | — | 654 | — |

*`topDerive` is a container category that includes `summonIgnoring`, `derive`, `summonMirror`, `subTraitDetect`, and `resolveDefaults`.

**Key insight**: `summonIgnoring` (the compiler's implicit search via `Expr.summonIgnoring`) dominates auto derivation at 47%. Builtin short-circuiting resolves ~646 primitive type lookups without calling `summonIgnoring` at all (47% fewer calls vs without the optimization). For configured derivation, builtin hits account for 648 resolutions, reducing `summonIgnoring` calls by 66% (from 984 to 336). The intra-expansion cache achieves a 75% hit rate, avoiding redundant derivations for repeated types within a single macro call.

## Building

Requires [Mill](https://mill-build.org/) 1.1.2+.

```bash
./mill sanely.jvm.compile    # compile (JVM)
./mill sanely.js.compile     # compile (Scala.js)
./mill sanely.jvm.test       # unit tests (JVM)
./mill sanely.js.test         # unit tests (Scala.js)
./mill compat.test            # circe compatibility tests
./mill demo.run               # run demo
```

## How it's made

This entire library — every macro, every test, every line of build config, and this README — was written by [Claude Code](https://claude.com/claude-code) with Claude Opus 4.6. 100% vibe coded.

## Roadmap

- [x] **Runtime dispatch for generated code** — instead of emitting N nested `.add()` calls or N if-then-else branches in the macro AST, build flat `Array[Encoder]`/`Array[Decoder]` at compile time and delegate to runtime while-loops in `SanelyRuntime`. Reduces generated AST size by ~30-50%.
- [x] **Builtin short-circuit for primitive types** — resolve String, Int, Long, Double, Float, Boolean, Short, Byte, BigDecimal, BigInt directly to their circe instances without calling `Expr.summonIgnoring`. Saves ~66% of summonIgnoring calls in configured derivation.
- [x] **Profile macro expansion** — compile-time profiling via `SANELY_PROFILE=true` tracks `summonIgnoring`, `summonMirror`, `derive`, `subTraitDetect`, `resolveDefaults`, `builtinHit`, and cache hits per expansion.
- [ ] **Container + builtin composition** — extend builtin short-circuit to handle containers of primitives: `Option[String]`, `List[Int]`, `Map[String, Double]`, etc. Emit `Decoder.decodeList(using Decoder.decodeInt)` directly without calling `Expr.summonIgnoring` for the inner type. Expected ~10-15% further reduction in summonIgnoring calls.
- [ ] **Accumulating decoder** (`decodeAccumulating`) — add a `failFast: Boolean` parameter to runtime decode methods; when false, collect all field errors into `ValidatedNel` instead of short-circuiting on the first. Single derivation pass, runtime branch. Matches circe-generic's behavior.
- [ ] **Literal/singleton type handling** — detect literal types (`"hello"`, `42`) and case object singletons at compile time; emit direct value references instead of going through encoder/decoder machinery.
- [ ] **Ignore `Encoder.derived`/`Decoder.derived` from circe-core** — add circe-core's own `derived` methods to `cachedIgnoreSymbols` so they don't interfere with `Expr.summonIgnoring` when circe-core is on the classpath.
- [ ] **Improved error messages** — when derivation fails, report what was tried and why each approach failed (builtin miss, summonIgnoring miss, no Mirror) instead of a single generic message.
- [ ] **Derive key encoders/decoders inline for built-in types** — generate `key.toString` directly for `Int`, `Long`, etc. instead of summoning `KeyEncoder[K]` via implicit search.

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
