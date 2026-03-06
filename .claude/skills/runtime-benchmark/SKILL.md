---
name: runtime-benchmark
description: >
  Benchmark runtime JSON encoding/decoding performance comparing circe-jawn,
  circe+jsoniter-parser, and pure jsoniter-scala. Use this skill whenever
  investigating runtime performance, throughput, ops/sec, or comparing
  JSON serialization speed. Also use when the user says "runtime benchmark",
  "runtime performance", "how fast is encoding", "how fast is decoding",
  "throughput", "ops/sec", "jsoniter comparison", "is circe slow at runtime",
  or wants to measure the impact of changes on runtime JSON performance.
  This skill covers the full workflow: running the benchmark, interpreting
  results, and updating README.md with new numbers. Use this skill alongside
  the compile-time profiling skills (macro-profile, jvm-profile, memory-profile)
  for a complete performance picture before any release.
---

# Runtime Benchmark

Measures JSON encoding/decoding throughput (ops/sec) for three approaches:
1. **circe-jawn** — circe's default parser + sanely-auto derived codecs
2. **circe+jsoniter** — jsoniter-scala's faster parser + same circe codecs
3. **jsoniter-scala** — native jsoniter-scala codecs (no circe at all)

The benchmark uses a realistic 1.2 KB JSON payload (nested user/orders model)
and measures both reading (bytes -> case class) and writing (case class -> bytes).

## Quick Start

```bash
# Run with defaults (5 warmup + 5 measurement iterations, each 1 second)
bash bench-runtime.sh

# Custom warmup and iterations
bash bench-runtime.sh 3 10

# Run directly via Mill
./mill benchmark-runtime.run 5 5
```

## Module Structure

```
benchmark-runtime/
  package.mill                           # Mill module definition
  src/runtime/RuntimeBenchmark.scala     # Models, codecs, benchmark harness
```

**Dependencies**: sanely (our library), circe-core, circe-parser, jsoniter-scala-core,
jsoniter-scala-circe, jsoniter-scala-macros.

## How the Benchmark Works

### Data model (defined in RuntimeBenchmark.scala)

```
ApiResponse
  ├── user: User
  │     ├── id, name, email, age, active
  │     ├── address: Address (5 fields)
  │     └── tags: List[String]
  ├── orders: List[Order]
  │     └── items: List[OrderItem] (4 fields each)
  ├── requestId: String
  └── timestamp: Long
```

Sample data: 1 user, 3 orders, 10 order items total. ~1.2 KB JSON.

### Codec derivation

- **circe codecs**: derived via `io.circe.generic.semiauto.deriveCodec` (uses sanely-auto)
- **jsoniter codecs**: derived via `JsonCodecMaker.make` (jsoniter-scala macros)
- **circe+jsoniter bridge**: `JsoniterScalaCodec.jsonC3c` provides `JsonValueCodec[Json]`

### Measurement harness

For each benchmark:
1. **Warmup**: N iterations x 1 second — lets JIT stabilize
2. **Measurement**: M iterations x 1 second — counts ops completed per second
3. **Report**: median, min, max ops/sec

Uses `@volatile var sink` to prevent dead code elimination (same principle as JMH Blackhole).

### What's measured

**Reading** (bytes -> case class):
- `circe-jawn`: `io.circe.jawn.decodeByteArray[ApiResponse](bytes)`
- `circe+jsoniter`: `readFromArray[Json](bytes).as[ApiResponse]`
- `jsoniter-scala`: `readFromArray[ApiResponse](bytes)`

**Writing** (case class -> bytes):
- `circe-printer`: `Printer.noSpaces.print(obj.asJson).getBytes`
- `circe+jsoniter`: `writeToArray(obj.asJson)` (jsoniter serializes circe Json AST)
- `jsoniter-scala`: `writeToArray(obj)` (direct, no AST)

## Interpreting Results

### Expected ratios (M3 Max, JDK 25)

| | Reading | Writing |
|---|---|---|
| **circe-jawn** (baseline) | 1.0x | 1.0x |
| **circe+jsoniter** | ~1.5x faster | ~0.9x (slightly slower) |
| **jsoniter-scala** | ~5x faster | ~5x faster |

### Why circe+jsoniter writing is slower than circe-printer

The writing bottleneck is `obj.asJson` — circe's `Encoder` building the `Json` AST (allocating
`JsonObject`, `JString`, `JNumber`, etc.). Swapping the printer doesn't help because the AST
construction dominates. jsoniter-scala writes directly to bytes without an intermediate AST.

### What to watch for after changes

- **circe-jawn reading drops**: sanely-auto generated a less efficient `Decoder` — check the
  generated HCursor traversal pattern
- **circe-jawn writing drops**: sanely-auto generated a less efficient `Encoder` — check the
  generated JsonObject construction
- **circe+jsoniter diverges from circe-jawn**: unexpected since they share the same `Decoder`/`Encoder`;
  only the parser/printer layer differs
- **All three drop equally**: JVM/system issue, not a code change issue

## Updating README.md

After running the benchmark, update the runtime table in README.md under
"Faster runtime with jsoniter-scala-circe":

```markdown
| | Reading (ops/sec) | | Writing (ops/sec) | |
|---|---|---|---|---|
| **circe + jawn** (baseline) | XXX,XXX | 1.0x | XXX,XXX | 1.0x |
| **circe + jsoniter parser** | XXX,XXX | **X.Xx** | XXX,XXX | X.Xx |
| **jsoniter-scala native** | XXX,XXX | **X.Xx** | XXX,XXX | **X.Xx** |
```

Update the prose with the reading speedup ratio (e.g., "1.5x reading speedup").

Include the payload size and platform info in the table caption:
`(1.2 KB JSON payload, M3 Max, JDK 25)`

## Full Pre-Release Benchmark Workflow

Before any release, run these in order to catch both compile-time and runtime regressions:

```bash
# 1. All tests (correctness)
./mill sanely.jvm.test
./mill sanely.js.test
./mill compat.jvm.test
./mill compat.js.test

# 2. Compile-time benchmarks (derivation speed, requires: brew install hyperfine)
bash bench.sh 5                    # auto derivation via hyperfine (~300 types)
bash bench.sh --configured 5      # configured derivation via hyperfine (~230 types)

# 3. Runtime benchmark (encoding/decoding throughput)
bash bench-runtime.sh 5 5

# 4. Macro profile (where macro time is spent)
rm -rf out/benchmark/sanely
SANELY_PROFILE=true ./mill --no-server benchmark.sanely.compile 2>&1 | tee /tmp/profile.txt
python3 .claude/skills/macro-profile/scripts/analyze_profile.py /tmp/profile.txt

rm -rf out/benchmark-configured/sanely
SANELY_PROFILE=true ./mill --no-server benchmark-configured.sanely.compile 2>&1 | tee /tmp/profile.txt
python3 .claude/skills/macro-profile/scripts/analyze_profile.py /tmp/profile.txt

# 5. JVM profile (compiler-level bottlenecks)
rm -rf out/benchmark/sanely
JAVA_TOOL_OPTIONS="-agentpath:$(brew --prefix async-profiler)/lib/libasyncProfiler.dylib=start,event=cpu,file=/tmp/collapsed.txt,collapsed" \
  ./mill --no-server benchmark.sanely.compile
python3 .claude/skills/jvm-profile/scripts/analyze_jvm_profile.py /tmp/collapsed.txt

# 6. Memory profile (peak RSS + allocation pressure)
rm -rf out/benchmark/sanely
/usr/bin/time -l ./mill --no-server benchmark.sanely.compile 2>&1 | grep "maximum resident"
```

**If runtime regresses**: investigate which approach regressed (circe-jawn, circe+jsoniter,
or jsoniter-scala). If only circe approaches regress, the issue is in sanely-auto's generated
codecs. If all regress, it's a JVM/system issue.

**If compile-time regresses but runtime is stable**: the macro generates the same quality code
but takes longer to do it — focus on macro profiling.

## Baseline Numbers

Reference from initial setup (M3 Max, JDK 25, 1.2 KB payload):

```
Reading (bytes -> case class):
  circe-jawn                  156,820 ops/sec
  circe+jsoniter              235,963 ops/sec  (1.50x)
  jsoniter-scala              796,505 ops/sec  (5.08x)

Writing (case class -> bytes):
  circe-printer               150,891 ops/sec
  circe+jsoniter              135,512 ops/sec  (0.90x)
  jsoniter-scala              739,119 ops/sec  (4.90x)
```
