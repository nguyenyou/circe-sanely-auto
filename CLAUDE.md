# CLAUDE.md

## Important Rules

- **This is a purely open source project.** Never mention any company names, internal projects, proprietary codebases, or employer-related information anywhere — not in code, commit messages, PR descriptions, comments, changelogs, or documentation. All references must remain generic and public.
- **No dead code.** When replacing an implementation, delete the old one entirely. No deprecation, no keeping old code "for backwards compatibility." The only contract that matters is 100% circe compatibility — internal APIs can change freely. Keep the codebase clean and current.

## Project

Drop-in replacement for circe's auto/semi-auto/configured derivation. Scala 3.8.2+ only. Uses the "sanely-automatic" approach — Scala 3 macros with `Expr.summonIgnoring` to derive all instances in a single macro expansion, avoiding implicit search chains.

**The Contract (non-negotiable)**: 100% behavioral compatibility with circe's derivation, zero compromise. If an application works with circe-generic or circe's configured derivation, switching to circe-sanely-auto or sanely-jsoniter must produce identical JSON output, accept identical JSON input, and yield identical error messages in every edge case. Any deviation — no matter how minor — is a bug that must be fixed before release. This overrides all other priorities including performance. We never compromise compatibility for speed, convenience, or code elegance. Only the implementation changes; the observable behavior is identical. Experimental status does not weaken this contract.

## Build Commands

Mill 1.1.2. Run from repo root:

```bash
./mill sanely.jvm.compile       # compile (JVM)
./mill sanely.js.compile        # compile (Scala.js)
./mill sanely.jvm.test          # unit tests - JVM (135 tests, munit)
./mill sanely.js.test           # unit tests - Scala.js (135 tests, munit)
./mill compat.jvm.test          # circe compat tests - JVM (192 tests, munit + discipline)
./mill compat.js.test           # circe compat tests - Scala.js (192 tests, munit + discipline)
./mill demo.run                 # run demo
./mill tapir-test.test          # Tapir integration tests (8 tests, munit)
```

**Do NOT run** `./mill __.compile` or bare `./mill` — use targeted module commands to avoid cache invalidation.

### Syncing Circe Upstream Tests

Compat tests are auto-generated from circe's upstream test suite via a git submodule + Python script:

```bash
git submodule update --init      # init upstream/circe/ submodule (pinned to a release tag)
python3 scripts/sync-circe-tests.py  # transform & write to compat/test/src/io/circe/generic/
```

Generated files (DO NOT edit manually — regenerate with the script):
- `AutoDerivedSuite.scala` ← `DerivesSuite.scala`
- `SemiautoDerivedSuite.scala` ← `SemiautoDerivationSuite.scala`
- `ConfiguredDerivesSuite.scala` ← `ConfiguredDerivesSuite.scala`
- `ConfiguredEnumDerivesSuites.scala` ← `ConfiguredEnumDerivesSuites.scala`

### Zinc Incremental Compilation Tests

```bash
bash test-zinc.sh               # zinc incremental recompilation tests (5 scenarios, 21 checks)
```

### Benchmarks

Compile-time benchmarks use [hyperfine](https://github.com/sharkdp/hyperfine) (`brew install hyperfine`).

```bash
bash bench.sh 5                 # auto derivation via hyperfine (~300 types)
bash bench.sh --configured 5    # configured derivation via hyperfine (~230 types)
bash bench.sh --jsoniter 5      # marginal cost of sanely-jsoniter (~199 types, circe-only vs circe+jsoniter)
./mill benchmark.sanely.compile # compile benchmark: our library (auto)
./mill benchmark.generic.compile # compile benchmark: circe-generic (auto)
./mill benchmark-configured.sanely.compile   # compile benchmark: our library (configured)
./mill benchmark-configured.generic.compile  # compile benchmark: circe-core (configured)
bash bench-runtime.sh           # runtime benchmark: circe-jawn vs circe+jsoniter vs jsoniter-scala
./mill benchmark-runtime.run    # run runtime benchmark directly
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
| `sanely/test/` | Unit tests (munit). Platform-specific sources in `test/src-jvm/` and `test/src-js/` |
| `compat/` | Circe compatibility tests (munit + discipline). Cross-compiled JVM + Scala.js. Uses circe's own `CodecTests` |
| `demo/` | Runnable examples |
| `benchmark/` | Compile-time benchmark. Two sub-modules sharing `benchmark/shared/src/` |
| `benchmark-configured/` | Configured derivation benchmark. Three sub-modules: `sanely`, `generic`, `generic-compat` sharing `benchmark-configured/shared/src/` |
| `benchmark-jsoniter/` | sanely-jsoniter marginal compile-time cost. Three sub-modules: `types` (shared), `circe-only` (baseline), `circe-jsoniter` (circe + jsoniter codecs) |
| `benchmark-runtime/` | Runtime performance benchmark. Compares circe-jawn vs circe+jsoniter-parser vs pure jsoniter-scala (reading + writing throughput) |
| `tapir-test/` | Tapir integration tests. Proves sanely-jsoniter codecs work through Tapir's codec layer and produce wire-compatible output with circe bridge |
| `zinc-test/` | Zinc incremental compilation tests. Verifies macros re-expand correctly when types change. 5 scenarios, 21 checks |

## Source Files

### Core (`sanely/src/sanely/`)

| File | Purpose |
|---|---|
| `auto.scala` | Entry point. `inline given autoEncoder`/`autoDecoder` via `import sanely.auto.given` |
| `SanelyEncoder.scala` | Macro: `Encoder.AsObject[A]` for products and sum types |
| `SanelyDecoder.scala` | Macro: `Decoder[A]` for products and sum types |
| `SanelyCodec.scala` | Single-pass macro: `Codec.AsObject[A]` sharing one cache + traversal for both encoder and decoder |
| `SanelyConfiguredEncoder.scala` | Configured encoder with `Configuration` support |
| `SanelyConfiguredDecoder.scala` | Configured decoder (most complex: defaults, strict, discriminator) |
| `SanelyConfiguredCodec.scala` | Single-pass macro: configured `Codec.AsObject[A]` sharing one cache + traversal |
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
- `org.scalameta::munit:1.2.0` (all tests)
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

## Roadmap Item Workflow

When completing a roadmap item from `README.md`, **always** run the full validation suite before marking it done:

1. **All tests**: `./mill sanely.jvm.test`, `./mill sanely.js.test`, `./mill compat.jvm.test`, `./mill compat.js.test`
2. **Compile-time benchmarks**: `bash bench.sh 5` (auto) and `bash bench.sh --configured 5` (configured)
3. **Runtime benchmark**: `bash bench-runtime.sh 5 5` — measures encoding/decoding throughput (circe-jawn vs circe+jsoniter vs jsoniter-scala)
4. **Macro profile**: `SANELY_PROFILE=true` on both `benchmark.sanely.compile` and `benchmark-configured.sanely.compile`, then run `analyze_profile.py`
5. **JVM profile**: async-profiler on `benchmark.sanely.compile` and `benchmark-configured.sanely.compile`, then run `analyze_jvm_profile.py`
6. **Memory profile**: `/usr/bin/time -l` for peak RSS, async-profiler `event=alloc` for allocation pressure

**If improved**: mark `[x]` in README roadmap, update benchmark/profile numbers in README tables (both compile-time and runtime).

**If regression**: revert the change, mark with `~~strikethrough~~` in README roadmap, and explain the regression (what regressed, by how much, root cause).

## Pre-Release Checklist

Before tagging a release, run the full benchmark suite to establish baseline numbers and catch regressions:

1. All tests pass (step 1 above)
2. Compile-time benchmarks show no regression vs previous release
3. Runtime benchmark shows no regression vs previous release — update README runtime table if numbers change
4. If macro changes were made: macro profile + JVM profile to verify no new bottlenecks
5. If generated codec structure changed: runtime benchmark is critical (affects encoding/decoding speed)

See the `runtime-benchmark` skill for detailed instructions on running and interpreting runtime benchmarks.

## Known Issues

- Configured macro profiling: `topDerive` is a **container category** that includes `summonIgnoring`, `derive`, `summonMirror`, `subTraitDetect`, `resolveDefaults`. Category percentages sum > 100% due to nesting.
- `Long.MaxValue`/`Long.MinValue` lose precision on Scala.js due to JSON number representation. Skipped via `Platform.isJS` (platform-specific sources in `test/src-jvm/` and `test/src-js/`).
- Generated compat tests: `String with Tag` → `String & Tag` fix applied by sync script (Scala 3 intersection type syntax).
- Sealed trait `given` ordering: `given Codec.AsObject[SealedTrait] = deriveConfiguredCodec` must come AFTER all subtypes are defined (Mirror synthesis constraint). The sync script defers these.

## Circe Reference

Test sources to match:
- `circe/modules/tests/shared/src/test/scala-3/io/circe/DerivesSuite.scala`
- `circe/modules/tests/shared/src/test/scala-3/io/circe/SemiautoDerivationSuite.scala`
- `circe/modules/tests/shared/src/test/scala-3/io/circe/ConfiguredDerivesSuite.scala`
- `circe/modules/tests/shared/src/test/scala-3/io/circe/ConfiguredEnumDerivesSuites.scala`
- `circe/modules/tests/shared/src/main/scala/io/circe/tests/examples/package.scala`

These are now auto-synced via `scripts/sync-circe-tests.py` from the `upstream/circe/` submodule.

## Mill Notes

- `benchmark/package.mill`: two modules share `benchmark/shared/src/` via `override def moduleDir`
- `benchmark-configured/package.mill`: three modules (`sanely`, `generic`, `generic-compat`) share `benchmark-configured/shared/src/`
- Module names with hyphens: use `benchmark-configured.sanely`, NOT `benchmark.configured`
- Mill 1.x: override `moduleDir` (public API), not `millSourcePath` (internal)
- `sanely/package.mill`: cross-compile via `PlatformScalaModule` with `jvm` and `js` sub-modules
