---
name: compile-profile
description: >
  Profile Scala compilation at the JVM level using async-profiler to identify
  bottlenecks in the compiler, JIT, GC, and macro expansion. Use this skill
  whenever investigating why compilation is slow at the JVM level, generating
  flame graphs, analyzing async-profiler output, or understanding where the
  Scala compiler spends time. Complements the sanely-profile skill (macro-level)
  with JVM-level visibility. Triggers on: "flame graph", "async-profiler",
  "JVM profile", "compilation bottleneck", "where does the compiler spend time",
  "JIT warmup", "GC during compilation", "profile the build".
---

# JVM Compilation Profiling

Profile Scala compilation at the JVM level using async-profiler. This gives
visibility into JIT compilation, GC pressure, and Scala compiler internals
that the macro-level `sanely-profile` skill can't see.

## Prerequisites

```bash
brew install async-profiler
```

## Workflow

### 1. Capture profile data

Use `JAVA_TOOL_OPTIONS` (not `JAVA_OPTS`) to profile ALL JVMs including
the zinc compilation worker. Use `collapsed` format for the analysis script,
and optionally also generate an HTML flame graph for visual inspection.

```bash
# Clean compilation cache first
rm -rf out/benchmark/sanely

# Capture collapsed stacks (for analysis script)
JAVA_TOOL_OPTIONS="-agentpath:$(brew --prefix async-profiler)/lib/libasyncProfiler.dylib=start,event=cpu,file=/tmp/profile-collapsed.txt,collapsed" \
  ./mill --no-server benchmark.sanely.compile

# Capture HTML flame graph (for visual inspection)
rm -rf out/benchmark/sanely
JAVA_TOOL_OPTIONS="-agentpath:$(brew --prefix async-profiler)/lib/libasyncProfiler.dylib=start,event=cpu,file=/tmp/flamegraph.html" \
  ./mill --no-server benchmark.sanely.compile
open /tmp/flamegraph.html
```

Important: `JAVA_TOOL_OPTIONS` is picked up by ALL JVMs the process spawns
(including zinc workers). `JAVA_OPTS` only affects the Mill launcher JVM
and will miss the actual Scala compiler.

### 2. Analyze with the script

```bash
# Full report
python .claude/skills/compile-profile/scripts/analyze_jvm_profile.py /tmp/profile-collapsed.txt

# Focus on compiler internals only
python .claude/skills/compile-profile/scripts/analyze_jvm_profile.py /tmp/profile-collapsed.txt --focus compiler

# JSON for programmatic use
python .claude/skills/compile-profile/scripts/analyze_jvm_profile.py /tmp/profile-collapsed.txt --json
```

### 3. Interpret the results

The script categorizes samples into:

| Group | What it covers |
|---|---|
| **compiler.macro.inlines** | Inline/macro expansion (our derivation macros) |
| **compiler.macro.quoted** | `scala.quoted` API (quote reflection used by macros) |
| **compiler.typer.implicits** | Implicit search (includes `Expr.summonIgnoring`) |
| **compiler.typer** | Type checking, inference, overload resolution |
| **compiler.transform** | Post-typer phases (erasure, lambdalift, etc.) |
| **compiler.backend** | JVM bytecode generation and classfile writing |
| **compiler.core** | Core compiler infrastructure (types, symbols, contexts) |
| **jvm.jit** | JIT compiler (C2/C1) compiling the Scala compiler itself |
| **jvm.gc** | Garbage collection pauses |
| **mill** | Mill build framework overhead |

## Key insight: cold vs warm JVM

With `--no-server`, the JVM is "cold" — JIT compilation typically dominates
(50-70% of samples) because the JIT is busy compiling the Scala compiler
itself. This is startup overhead, not representative of steady-state.

To see actual compiler performance:
- **Subtract JIT/GC** and look at compiler-only percentages
- **Or** use Mill daemon mode (no `--no-server`) for a warm JVM — but you
  need to invalidate caches between runs

## Combining with sanely-profile

For the full picture, run both profilers:

1. **compile-profile** (this skill) — shows WHERE in the JVM time is spent
2. **sanely-profile** — shows WHERE in our macro code time is spent

```bash
# Both at once:
rm -rf out/benchmark/sanely
SANELY_PROFILE=true \
JAVA_TOOL_OPTIONS="-agentpath:$(brew --prefix async-profiler)/lib/libasyncProfiler.dylib=start,event=cpu,file=/tmp/profile-collapsed.txt,collapsed" \
  ./mill --no-server benchmark.sanely.compile 2>&1 | tee /tmp/full-profile.txt

# Analyze JVM level:
python .claude/skills/compile-profile/scripts/analyze_jvm_profile.py /tmp/profile-collapsed.txt

# Analyze macro level:
python .claude/skills/sanely-profile/scripts/analyze_profile.py /tmp/full-profile.txt
```

## Optimization decision tree

Based on profile results:

1. **JIT > 50%**: Cold JVM problem. Not actionable for our code. Use warm JVM for representative numbers.
2. **GC > 10%**: Increase heap (`-Xmx4g`) or switch to ZGC (`-XX:+UseZGC`).
3. **compiler.macro > 30% of compiler time**: Our macros are the bottleneck. Use `sanely-profile` for details.
4. **compiler.typer.implicits > 20% of compiler time**: Implicit search is expensive. Reduce `summonIgnoring` calls.
5. **compiler.backend > 25% of compiler time**: Generated code is too large. Extract more to `SanelyRuntime`.
6. **compiler.transform > 20% of compiler time**: Post-typer phases are expensive. Usually not actionable from our side.
