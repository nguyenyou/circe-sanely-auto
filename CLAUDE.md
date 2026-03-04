# CLAUDE.md

## Project

Drop-in replacement for circe's auto/semi-auto/configured derivation. Scala 3.8.2+ only. Uses the "sanely-automatic" approach — Scala 3 macros with `Expr.summonIgnoring` to derive all instances in a single macro expansion, avoiding implicit search chains.

**Goal**: Full API compatibility with circe's derivation. Pass all circe tests. Only the implementation changes, not the user-facing API.

## Build Commands

Mill 1.1.2. Run from repo root:

```bash
./mill sanely.jvm.compile       # compile (JVM)
./mill sanely.js.compile        # compile (Scala.js)
./mill sanely.jvm.test          # unit tests - JVM (109 tests, utest)
./mill sanely.js.test           # unit tests - Scala.js (109 tests, utest)
./mill compat.test              # circe compat tests (160 tests, munit + discipline)
./mill demo.run                 # run demo
./mill benchmark.sanely.compile # benchmark: our library
./mill benchmark.generic.compile # benchmark: circe-generic
bash bench.sh 5                 # timed compile comparison
```

**Do NOT run** `./mill __.compile` or bare `./mill` — use targeted module commands to avoid cache invalidation.

## Modules

| Module | Purpose |
|---|---|
| `sanely/` | Core library. Cross-compiled JVM + Scala.js via `PlatformScalaModule` |
| `sanely/test/` | Unit tests (utest). Platform-specific sources in `test/src-jvm/` and `test/src-js/` |
| `compat/` | Circe compatibility tests (munit + discipline). Uses circe's own `CodecTests` |
| `demo/` | Runnable examples |
| `benchmark/` | Compile-time benchmark. Two sub-modules sharing `benchmark/shared/src/` |

## Source Files

### Core (`sanely/src/sanely/`)

| File | Purpose |
|---|---|
| `auto.scala` | Entry point. `inline given autoEncoder`/`autoDecoder` via `import sanely.auto.given` |
| `SanelyEncoder.scala` | Macro: `Encoder.AsObject[A]` for products and sum types |
| `SanelyDecoder.scala` | Macro: `Decoder[A]` for products and sum types |
| `SanelyCodec.scala` | Composes encoder + decoder into `Codec.AsObject[A]` |
| `SanelyConfiguredEncoder.scala` | Configured encoder with `Configuration` support |
| `SanelyConfiguredDecoder.scala` | Configured decoder (most complex: defaults, strict, discriminator) |
| `SanelyConfiguredCodec.scala` | Composes configured encoder + decoder |
| `SanelyEnumCodec.scala` | Singleton enum string codec with hierarchical sealed trait support |

### Drop-in API (`sanely/src/io/circe/generic/`)

| File | Purpose |
|---|---|
| `auto.scala` | `io.circe.generic.auto.given` — drop-in alias for `sanely.auto.given` via `Exported` |
| `semiauto.scala` | `deriveEncoder`, `deriveDecoder`, `deriveCodec`, `deriveConfigured*`, `deriveEnumCodec` |

## How It Works

1. `auto.scala` provides named `inline given autoEncoder`/`autoDecoder` → delegates to `SanelyEncoder.derived`/`SanelyDecoder.derived`
2. `derived` is `inline def` that splices a macro (`deriveMacro`)
3. Macro pattern-matches `Mirror` → dispatches to `deriveProduct` or `deriveSum`
4. **Recursive resolution** (`resolveOneEncoder`/`resolveOneDecoder`): `Expr.summonIgnoring` searches for existing instances while excluding our auto-given symbol. If none found, derives internally via `Mirror` — all within the same macro expansion. This is the core trick.

The givens are named (`autoEncoder`/`autoDecoder`) so macros can reference their symbols for `Expr.summonIgnoring`.

### Configured derivation

Threads `Expr[Configuration]` through the macro. At runtime:
- `transformMemberNames`/`transformConstructorNames` — wraps labels with transform functions
- `useDefaults` — companion `$lessinit$greater$default$N` methods looked up at compile time; fallback when field missing (or null for non-Option types)
- `discriminator` — `None` → external tagging, `Some(d)` → flat with discriminator field
- `strictDecoding` — post-decode key validation; for sum types, rejects multi-key objects

### Encoding format

- **Products**: `{"field1": ..., "field2": ...}`
- **Sum types**: `{"VariantName": {...}}` (external tagging) or `{"type": "VariantName", ...}` (discriminator)
- **Enums**: `"VariantName"` (string codec)

## Dependencies

- `io.circe::circe-core:0.14.15` (main)
- `io.circe::circe-parser:0.14.15` (test/demo)
- `com.lihaoyi::utest:0.10.0-RC1` (unit tests)
- `io.circe::circe-testing:0.14.15` + `org.typelevel::discipline-munit:2.0.0` (compat tests)

## Compiler Options

`-Xmax-inlines 64` — required for macro expansion depth. Set on all modules.

## Publishing

When publishing a new version:

1. Bump `publishVersion` in `sanely/package.mill`
2. Update version in `README.md`
3. Run all tests (`sanely.jvm.test`, `sanely.js.test`, `compat.test`)
4. Publish both platforms: `./mill sanely.jvm.publishSonatypeCentral` and `./mill sanely.js.publishSonatypeCentral`
5. Commit the version bump, push, and create a PR
6. After merge, create a git tag and GitHub release with a changelog:
   ```bash
   git tag v0.X.0
   git push origin v0.X.0
   gh release create v0.X.0 --title "v0.X.0" --notes "changelog here"
   ```

The changelog should summarize new features, bug fixes, and breaking changes since the last release. Use `git log --oneline vPREV..HEAD` to gather commits.

## Known Issues

- `Long.MaxValue`/`Long.MinValue` lose precision on Scala.js due to JSON number representation. Skipped via `Platform.isJS` (platform-specific sources in `test/src-jvm/` and `test/src-js/`).
- `inline def derived` inside utest `test {}` blocks may not expand for generic types. Workaround: derive in a helper object outside the test block.

## Circe Reference

Test sources to match:
- `circe/modules/tests/shared/src/test/scala-3/io/circe/DerivesSuite.scala`
- `circe/modules/tests/shared/src/test/scala-3/io/circe/SemiautoDerivationSuite.scala`
- `circe/modules/tests/shared/src/test/scala-3/io/circe/ConfiguredDerivesSuite.scala`
- `circe/modules/tests/shared/src/test/scala-3/io/circe/ConfiguredEnumDerivesSuites.scala`
- `circe/modules/tests/shared/src/main/scala/io/circe/tests/examples/package.scala`

## Mill Notes

- `benchmark/package.mill`: two modules share `benchmark/shared/src/` via `override def moduleDir`
- Mill 1.x: override `moduleDir` (public API), not `millSourcePath` (internal)
- `sanely/package.mill`: cross-compile via `PlatformScalaModule` with `jvm` and `js` sub-modules
