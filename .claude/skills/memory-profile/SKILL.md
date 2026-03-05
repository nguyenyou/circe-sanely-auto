---
name: memory-profile
description: >
  Profile memory usage during Scala compilation — peak RSS and allocation
  hotspots. Compare memory footprint of circe-sanely-auto vs circe-generic
  (or any two Mill modules). Use this skill whenever investigating memory
  usage, heap pressure, allocation hotspots, or comparing memory between
  builds. Triggers on: "memory usage", "memory profile", "peak RSS",
  "allocation profile", "heap usage", "does it use more memory",
  "compare memory", "how much memory", "alloc profile".
---

# Memory Profiling for Compilation

Measure and compare memory usage during Scala compilation. Two complementary
approaches: peak RSS (total process memory) and allocation profiling
(where objects are allocated).

## Prerequisites

```bash
brew install async-profiler  # for allocation profiling
```

## Workflow

### 1. Peak RSS comparison

Uses macOS `/usr/bin/time -l` to measure maximum resident set size.
This is the simplest "how much memory did compilation use?" metric.

```bash
# Compare two modules
rm -rf out/benchmark/sanely out/benchmark/generic

/usr/bin/time -l ./mill --no-server benchmark.sanely.compile 2>&1 \
  | grep -E "maximum resident|SUCCESS"

/usr/bin/time -l ./mill --no-server benchmark.generic.compile 2>&1 \
  | grep -E "maximum resident|SUCCESS"
```

On Linux, use `/usr/bin/time -v` and look for "Maximum resident set size".

The value is in bytes on macOS. Divide by 1048576 for MB.

### 2. Allocation profiling

Uses async-profiler with `event=alloc` to capture where the compiler
allocates objects. This shows allocation pressure, not retained heap.

```bash
# Profile sanely
rm -rf out/benchmark/sanely
JAVA_TOOL_OPTIONS="-agentpath:$(brew --prefix async-profiler)/lib/libasyncProfiler.dylib=start,event=alloc,file=/tmp/alloc-sanely.txt,collapsed" \
  ./mill --no-server benchmark.sanely.compile

# Profile circe-generic
rm -rf out/benchmark/generic
JAVA_TOOL_OPTIONS="-agentpath:$(brew --prefix async-profiler)/lib/libasyncProfiler.dylib=start,event=alloc,file=/tmp/alloc-generic.txt,collapsed" \
  ./mill --no-server benchmark.generic.compile
```

### 3. Analyze with the script

```bash
# Single profile
python3 .claude/skills/memory-profile/scripts/analyze_memory.py /tmp/alloc-sanely.txt

# Compare two profiles (our library vs baseline)
python3 .claude/skills/memory-profile/scripts/analyze_memory.py \
  /tmp/alloc-sanely.txt --compare /tmp/alloc-generic.txt \
  --labels sanely circe-generic

# JSON output
python3 .claude/skills/memory-profile/scripts/analyze_memory.py \
  /tmp/alloc-sanely.txt --compare /tmp/alloc-generic.txt --json

# Focus on compiler allocations only
python3 .claude/skills/memory-profile/scripts/analyze_memory.py \
  /tmp/alloc-sanely.txt --focus compiler
```

### 4. Allocation flame graph (visual)

```bash
rm -rf out/benchmark/sanely
JAVA_TOOL_OPTIONS="-agentpath:$(brew --prefix async-profiler)/lib/libasyncProfiler.dylib=start,event=alloc,file=/tmp/alloc-flamegraph.html" \
  ./mill --no-server benchmark.sanely.compile
open /tmp/alloc-flamegraph.html
```

## Interpreting results

### Peak RSS

Peak RSS includes everything: compiler heap, JIT compiled code, metaspace,
thread stacks, OS buffers. It's the most user-visible metric ("how much RAM
does compilation use?"). Expect 800MB-1.2GB for the benchmark suite.

### Allocation samples

Each sample represents an object allocation. More samples = more allocation
pressure = more GC work. The script categorizes allocations by source:

| Category | What allocates |
|---|---|
| **compiler.core** | Type representations, symbols, denotations |
| **compiler.ast** | Tree nodes, tree maps/accumulators |
| **compiler.macro** | Quote reflection, inline expansion |
| **compiler.typer** | Type checking, implicit search results |
| **compiler.backend** | ASM bytecode buffers, classfile structures |
| **jvm** | JIT compiler, GC metadata, classloading |
| **mill** | Build framework overhead |

### What to look for

- **Total alloc samples**: Lower is better. Fewer allocations = less GC pressure.
- **compiler.ast ratio**: If high, macros may be generating large ASTs.
- **compiler.core.types ratio**: Heavy type manipulation (dealias, show, etc.).
- **compiler.backend ratio**: If high relative to baseline, we generate more bytecode.

## Combining with other profiling

For the full picture, combine all three profiling skills:

1. **memory-profile** (this skill) — HOW MUCH memory
2. **jvm-profile** — WHERE CPU time is spent
3. **macro-profile** — WHERE macro expansion time is spent
