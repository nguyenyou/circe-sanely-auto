# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Goal

Drop-in replacement for circe's auto-derivation (Scala 3 only, targeting 3.8.2+) using the "sanely-automatic" derivation approach from [kubuszok.com/2025/sanely-automatic-derivation](https://kubuszok.com/2025/sanely-automatic-derivation/). The technique uses Scala 3 macros to recursively derive Encoder/Decoder instances at compile time in a single macro expansion — avoiding the implicit search chains that make circe-generic slow to compile.

**Success metric**: Pass all circe auto-derivation tests. API compatibility with `io.circe` is the ultimate goal — only the implementation changes, not the user-facing API.

**Scope**: Scala 3 only. No Scala 2 support.

## Build Commands

Mill 1.1.2 build. Run from repo root:

```bash
./mill sanely.compile          # compile core library
./mill sanely.test              # run tests (utest)
./mill demo.run                 # run demo (product + sum round-trips)
./mill sanely.test.compile      # compile tests only
```

**Do NOT run** `./mill __.compile` or bare `./mill` — use targeted module commands to avoid cache invalidation.

## Architecture

Three Mill modules:

- **`sanely/`** — Core library. Macro-based Encoder.AsObject and Decoder derivation.
- **`sanely/test/`** — utest suite. Import `sanely.auto.given` to get instances.
- **`demo/`** — Runnable examples. Depends on `sanely`.

### Core source files (`sanely/src/sanely/`)

| File | Purpose |
|---|---|
| `auto.scala` | Entry point. Provides `inline given` instances via `import sanely.auto.given` |
| `SanelyEncoder.scala` | Macro that derives `Encoder.AsObject[A]` for products and sum types |
| `SanelyDecoder.scala` | Macro that derives `Decoder[A]` for products and sum types |

### How derivation works

Both encoder/decoder follow the same pattern:

1. `auto.scala` provides `inline given` that delegates to `SanelyEncoder.derived` / `SanelyDecoder.derived`
2. `derived` is an `inline def` that splices a macro (`deriveMacro`)
3. The macro pattern-matches the `Mirror` to dispatch to `deriveProduct` or `deriveSum`
4. **Recursive resolution** (`resolveOneEncoder`/`resolveOneDecoder`): first tries `Expr.summon[Encoder[T]]` for an existing given, then falls back to recursive macro derivation via the `Mirror`. This is the key "sanely-automatic" trick — nested types are derived inside the same macro expansion, not via implicit search chains.

### Encoding format

- **Products**: `{"field1": ..., "field2": ...}` — flat JSON object keyed by field names
- **Sum types**: `{"VariantName": {...}}` — single-key wrapper with variant's fields inside

## Dependencies

- `io.circe::circe-core:0.14.13` (main)
- `io.circe::circe-parser:0.14.13` (test/demo only)
- `com.lihaoyi::utest:0.10.0-RC1` (test only)

## Compiler Options

`-Xmax-inlines 64` is set on both main and test modules — required for macro expansion depth.

## Workflow: Test-First Porting

We follow a strict **port test → implement → pass → next** cycle:

1. Pick the next phase from `README.md` test plan (Phase 1–9, easiest to hardest)
2. Port the corresponding test from circe's `DerivesSuite` / `SemiautoDerivationSuite` into `sanely/test/`
3. Run `./mill sanely.test` — expect failures
4. Implement in `SanelyEncoder.scala` / `SanelyDecoder.scala` / `auto.scala` until tests pass
5. Do NOT move to the next phase until all current tests are green

Circe test sources live at:
- `circe/modules/tests/shared/src/test/scala-3/io/circe/DerivesSuite.scala` — `derives` keyword tests
- `circe/modules/tests/shared/src/test/scala-3/io/circe/SemiautoDerivationSuite.scala` — explicit `.derived` tests
- `circe/modules/tests/shared/src/main/scala/io/circe/tests/examples/package.scala` — shared test data types

See `README.md` "Test Porting Plan" for the full 9-phase breakdown with expected challenges.

## Reference Repositories

Configured as additional working directories:
- `/Users/tunguyen/Documents/GitHub/circe` — upstream circe (test suite to match)
- `/Users/tunguyen/Documents/GitHub/scala3` — Scala 3 compiler source
- `/Users/tunguyen/Documents/GitHub/mill` — Mill build tool source
