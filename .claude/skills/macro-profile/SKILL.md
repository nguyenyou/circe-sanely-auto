---
name: macro-profile
description: >
  Profile and analyze circe-sanely-auto macro expansion performance.
  Use this skill whenever investigating compile time, macro performance,
  slow derivation, or optimizing the macro engine. Also use when the user
  says "profile", "why is compilation slow", "macro timing", "what's taking
  so long", "optimize macros", or wants to understand where time is spent
  during codec derivation. This skill covers the full workflow: running
  profiled compilation, analyzing results with the bundled Python script,
  and interpreting the output to plan optimizations.
---

# Macro Expansion Profiling

circe-sanely-auto has built-in compile-time profiling gated by the
`SANELY_PROFILE=true` environment variable. When enabled, each macro
expansion prints timing data to stderr. This has zero cost when disabled.

## Quick Start

### 1. Run profiled compilation

```bash
# Auto derivation benchmark (~300 types)
SANELY_PROFILE=true ./mill --no-server benchmark.sanely.compile 2>&1 | tee /tmp/sanely-profile-output.txt

# Configured derivation benchmark (~230 types)
SANELY_PROFILE=true ./mill --no-server benchmark.configured.compile 2>&1 | tee /tmp/sanely-profile-output.txt

# Just the library (unit test types)
SANELY_PROFILE=true ./mill --no-server sanely.jvm.test 2>&1 | tee /tmp/sanely-profile-output.txt
```

Use `--no-server` so Mill doesn't reuse a cached compilation.

### 2. Analyze with the Python script

```bash
# Full report
python .claude/skills/macro-profile/scripts/analyze_profile.py /tmp/sanely-profile-output.txt

# Top 20 slowest, sorted by summonIgnoring time
python .claude/skills/macro-profile/scripts/analyze_profile.py /tmp/sanely-profile-output.txt --top 20 --sort summonIgnoring

# Only Decoder expansions
python .claude/skills/macro-profile/scripts/analyze_profile.py /tmp/sanely-profile-output.txt --kind Decoder

# JSON output for programmatic use
python .claude/skills/macro-profile/scripts/analyze_profile.py /tmp/sanely-profile-output.txt --json
```

### 3. Interpret the results

The report shows:

| Section | What it tells you |
|---|---|
| **By Kind** | Encoder vs Decoder vs CfgEncoder vs CfgDecoder breakdown |
| **Category Breakdown** | Where time is spent across all expansions |
| **Top N Slowest** | Which types are the compilation bottlenecks |
| **Optimization Insights** | Actionable recommendations based on the data |

## What Gets Profiled

Each macro expansion (one per `Encoder[T]` or `Decoder[T]` derivation) tracks:

| Category | What it measures |
|---|---|
| `summonIgnoring` | `Expr.summonIgnoring[Encoder/Decoder[T]]` - compiler implicit search |
| `summonMirror` | `Expr.summon[Mirror.Of[T]]` - fetching the type's Mirror |
| `derive` | Recursive `deriveProduct`/`deriveSum` calls - AST construction |
| `subTraitDetect` | Checking if a sum type variant is itself a sub-trait |
| `cacheHit` | Times the intra-expansion cache avoided re-derivation |

## Instrumented Files

Profiling code lives in these files:

- `sanely/src/sanely/MacroTimer.scala` - Timer utility (zero-cost when disabled)
- `sanely/src/sanely/SanelyEncoder.scala` - `EncoderDerivation` class
- `sanely/src/sanely/SanelyDecoder.scala` - `DecoderDerivation` class
- `sanely/src/sanely/SanelyConfiguredEncoder.scala` - `ConfiguredEncoderDerivation` class
- `sanely/src/sanely/SanelyConfiguredDecoder.scala` - `ConfiguredDecoderDerivation` class

## Baseline Profile (v0.6.1, ~300 types auto benchmark)

Reference numbers from M3 Max MacBook Pro:

```
308 macro expansions, ~2450ms total macro time

Category Breakdown:
  summonIgnoring        1256ms (51.3%)  1366 calls  avg 0.92ms
  derive                 716ms (29.2%)   586 calls
  summonMirror            80ms  (3.3%)   586 calls  avg 0.14ms
  subTraitDetect          43ms  (1.8%)   336 calls
  cacheHit                       1714 hits
  overhead               355ms (14.5%)
```

Key insight: `summonIgnoring` (compiler implicit search) dominates at 51%.
The main optimization lever is reducing the number of summonIgnoring calls,
either through cross-expansion caching or lazy val emission patterns.

## Optimization Planning Guide

After analyzing profile data, use this to prioritize:

1. **If summonIgnoring > 40%**: Focus on reducing implicit search calls.
   Cross-expansion caching (lazy val emission) would help most. Currently
   each macro expansion starts with a fresh cache.

2. **If derive > 30%**: Focus on reducing generated AST size. Extract more
   logic from inline macro output to `SanelyRuntime` helper methods.

3. **If specific types are > 50ms**: Those types have deep nesting. Consider
   whether they could benefit from user-provided instances or structural
   changes.

4. **If Decoder >> Encoder**: Decoder's field-by-field decode chain is
   inherently more expensive. The `buildDecodeChain` recursive Expr
   construction is the cause.

5. **If cache hit ratio is low**: The intra-expansion cache isn't catching
   enough repeated types. Check if `cacheKey` computation is too specific.
