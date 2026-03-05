# CLAUDE.md

## Important Rules

- **This is a purely open source project.** Never mention any company names, internal projects, proprietary codebases, or employer-related information anywhere — not in code, commit messages, PR descriptions, comments, changelogs, or documentation. All references must remain generic and public.

## Project

Drop-in replacement for circe's auto/semi-auto/configured derivation. Scala 3.8.2+ only. Uses the "sanely-automatic" approach — Scala 3 macros with `Expr.summonIgnoring` to derive all instances in a single macro expansion, avoiding implicit search chains.

**Goal**: Full API compatibility with circe's derivation. Pass all circe tests. Only the implementation changes, not the user-facing API.

## Build Commands

Mill 1.1.2. Run from repo root:

```bash
./mill sanely.jvm.compile       # compile (JVM)
./mill sanely.js.compile        # compile (Scala.js)
./mill sanely.jvm.test          # unit tests - JVM (122 tests, utest)
./mill sanely.js.test           # unit tests - Scala.js (122 tests, utest)
./mill compat.test              # circe compat tests (318 tests, munit + discipline)
./mill demo.run                 # run demo
```

**Do NOT run** `./mill __.compile` or bare `./mill` — use targeted module commands to avoid cache invalidation.

### Benchmarks

```bash
bash bench.sh 5                 # auto derivation timed comparison (~300 types)
bash bench.sh --configured 5    # configured derivation timed comparison (~230 types)
./mill benchmark.sanely.compile # compile benchmark: our library (auto)
./mill benchmark.generic.compile # compile benchmark: circe-generic (auto)
./mill benchmark-configured.sanely.compile   # compile benchmark: our library (configured)
./mill benchmark-configured.generic.compile  # compile benchmark: circe-core (configured)
```

### Profiling

```bash
# Macro-level profiling (our MacroTimer)
rm -rf out/benchmark/sanely
SANELY_PROFILE=true ./mill --no-server benchmark.sanely.compile 2>&1 | tee /tmp/profile.txt
python3 .claude/skills/macro-profile/scripts/analyze_profile.py /tmp/profile.txt

# Configured derivation macro profiling
rm -rf out/benchmark-configured/sanely
SANELY_PROFILE=true ./mill --no-server benchmark-configured.sanely.compile 2>&1 | tee /tmp/profile.txt
python3 .claude/skills/macro-profile/scripts/analyze_profile.py /tmp/profile.txt

# JVM-level profiling (async-profiler, requires: brew install async-profiler)
# Use JAVA_TOOL_OPTIONS (not JAVA_OPTS) to profile ALL JVMs including zinc worker
rm -rf out/benchmark/sanely
JAVA_TOOL_OPTIONS="-agentpath:$(brew --prefix async-profiler)/lib/libasyncProfiler.dylib=start,event=cpu,file=/tmp/collapsed.txt,collapsed" \
  ./mill --no-server benchmark.sanely.compile
python3 .claude/skills/jvm-profile/scripts/analyze_jvm_profile.py /tmp/collapsed.txt

# HTML flame graph (for visual inspection)
rm -rf out/benchmark/sanely
JAVA_TOOL_OPTIONS="-agentpath:$(brew --prefix async-profiler)/lib/libasyncProfiler.dylib=start,event=cpu,file=/tmp/flamegraph.html" \
  ./mill --no-server benchmark.sanely.compile
open /tmp/flamegraph.html
```

## Modules

| Module | Purpose |
|---|---|
| `sanely/` | Core library. Cross-compiled JVM + Scala.js via `PlatformScalaModule` |
| `sanely/test/` | Unit tests (utest). Platform-specific sources in `test/src-jvm/` and `test/src-js/` |
| `compat/` | Circe compatibility tests (munit + discipline). Uses circe's own `CodecTests` |
| `demo/` | Runnable examples |
| `benchmark/` | Compile-time benchmark. Two sub-modules sharing `benchmark/shared/src/` |
| `benchmark-configured/` | Configured derivation benchmark. Three sub-modules: `sanely`, `generic`, `generic-compat` sharing `benchmark-configured/shared/src/` |

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

Automated via GitHub Actions (`.github/workflows/release.yml`). Triggered on `v*` tag push. Runs all tests, then publishes JVM + Scala.js to Sonatype Central.

**Release steps:**

1. Bump `publishVersion` in `sanely/package.mill`
2. Update version in `README.md`
3. Run all tests locally (`sanely.jvm.test`, `sanely.js.test`, `compat.test`)
4. Commit, push, merge PR
5. Tag and push — CI handles publishing:
   ```bash
   git tag v0.X.0
   git push origin v0.X.0
   ```
6. Create GitHub release:
   ```bash
   gh release create v0.X.0 --title "v0.X.0" --notes "changelog here"
   ```

**Manual publish** (if needed): `./mill sanely.jvm.publishSonatypeCentral` and `./mill sanely.js.publishSonatypeCentral` — requires env vars: `MILL_SONATYPE_USERNAME`, `MILL_SONATYPE_PASSWORD`, `MILL_PGP_PASSPHRASE`, `MILL_PGP_SECRET_BASE64`.

**GitHub secrets:** same 4 env vars configured as repo secrets for the release workflow.

Update `CHANGELOG.md` with new features, bug fixes, and breaking changes. Use `git log --oneline vPREV..HEAD` to gather commits.

## Known Issues

- Configured macro profiling: `topDerive` is a **container category** that includes `summonIgnoring`, `derive`, `summonMirror`, `subTraitDetect`, `resolveDefaults`. Category percentages sum > 100% due to nesting.
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
- `benchmark-configured/package.mill`: three modules (`sanely`, `generic`, `generic-compat`) share `benchmark-configured/shared/src/`
- Module names with hyphens: use `benchmark-configured.sanely`, NOT `benchmark.configured`
- Mill 1.x: override `moduleDir` (public API), not `millSourcePath` (internal)
- `sanely/package.mill`: cross-compile via `PlatformScalaModule` with `jvm` and `js` sub-modules
