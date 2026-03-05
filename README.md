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
+ mvn"io.github.nguyenyou::circe-sanely-auto:0.7.0"
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
| **circe-sanely-auto** | **3.69s** | |
| **circe-generic** | **6.99s** | 1.9x slower |

### Configured derivation

| | Median compile time | |
|---|---|---|
| **circe-sanely-auto** | **3.10s** | |
| **circe-core** | **3.20s** | ~same |

### Why the difference?

The speedup only applies to **auto derivation**. With `import io.circe.generic.auto.given`, the compiler must implicitly search for and synthesize codecs at every use site — each nested type triggers another round of implicit resolution. Sanely avoids this by deriving everything in a single macro expansion.

**Configured derivation** uses explicit semi-auto calls (`deriveConfiguredCodec` in each companion object). There's no implicit search — you're telling the compiler exactly what to derive. Both implementations end up doing similar inline macro expansion, so compile times converge.

In short: sanely's advantage is specifically in eliminating implicit search overhead, which only affects auto derivation.

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

**Auto derivation profile** (308 expansions, ~2.4s total macro time):

| Category | Time | % | Calls | Avg |
|---|---|---|---|---|
| `summonIgnoring` | 1217ms | 50.0% | 1366 | 0.89ms |
| `derive` | 683ms | 28.1% | 586 | 1.17ms |
| `summonMirror` | 81ms | 3.3% | 586 | 0.14ms |
| `subTraitDetect` | 42ms | 1.7% | 336 | 0.13ms |
| overhead | 410ms | 16.8% | — | — |
| cache hits | — | — | 1714 (75%) | — |

**Configured derivation profile** (460 expansions, ~1.5s total macro time):

| Category | Time | % | Calls | Avg |
|---|---|---|---|---|
| `topDerive` | 1283ms | 86.6%* | 460 | 2.79ms |
| `summonIgnoring` | 522ms | 35.2% | 984 | 0.53ms |
| `subTraitDetect` | 30ms | 2.0% | 138 | 0.21ms |
| `resolveDefaults` | 11ms | 0.7% | 214 | 0.05ms |
| cache hits | — | — | 654 | — |

*`topDerive` is a container category that includes `summonIgnoring`, `derive`, `summonMirror`, `subTraitDetect`, and `resolveDefaults`.

**Key insight**: `summonIgnoring` (the compiler's implicit search via `Expr.summonIgnoring`) dominates auto derivation at 50%. For configured derivation, AST construction (building encode/decode expression chains) accounts for ~59% of `topDerive` time, with `summonIgnoring` at ~41%. The intra-expansion cache achieves a 75% hit rate, avoiding redundant derivations for repeated types within a single macro call.

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

- [ ] **Emit `lazy val` for derived codecs in generated code** — when a derived `Encoder[Foo]` is used by multiple fields in the same product, the full AST tree is currently duplicated at each splice site. Emit a single `lazy val encoder_Foo = ...` and reference it by name to deduplicate.
- [ ] **Accumulating decoder** (`decodeAccumulating`) — generate both fail-fast and accumulating decode paths, matching circe-generic's behavior for collecting all errors instead of stopping at the first.
- [x] **Profile macro expansion** — compile-time profiling via `SANELY_PROFILE=true` tracks `summonIgnoring`, `summonMirror`, `derive`, `subTraitDetect`, `resolveDefaults`, and cache hits per expansion.
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
