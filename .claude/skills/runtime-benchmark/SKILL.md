---
name: runtime-benchmark
description: >
  Benchmark runtime JSON encoding/decoding performance comparing circe-jawn,
  circe+jsoniter-parser, sanely-jsoniter, and pure jsoniter-scala. Use this
  skill whenever investigating runtime performance, throughput, ops/sec, or
  comparing JSON serialization speed. Also use when the user says "runtime
  benchmark", "runtime performance", "how fast is encoding", "how fast is
  decoding", "throughput", "ops/sec", "jsoniter comparison", "is circe slow
  at runtime", "JMH", "jmh benchmark", or wants to measure the impact of
  changes on runtime JSON performance. This skill covers the full workflow:
  running the benchmark, interpreting results, and updating README.md with
  new numbers. Use this skill alongside the compile-time profiling skills
  (macro-profile, jvm-profile, memory-profile) for a complete performance
  picture before any release.
---

# Runtime Benchmark

Measures JSON encoding/decoding throughput (ops/sec) for four approaches:
1. **circe-jawn** — circe's default parser + sanely-auto derived codecs
2. **circe+jsoniter** — jsoniter-scala's faster parser + same circe codecs
3. **sanely-jsoniter** — sanely-auto's direct streaming codecs (circe-compatible wire format, no AST)
4. **jsoniter-scala** — native jsoniter-scala codecs (jsoniter-scala's own wire format)

The benchmark uses a realistic ~1.4 KB JSON payload (nested user/orders model with sealed traits)
and measures both reading (bytes -> case class) and writing (case class -> bytes).

## Quick Start

Two benchmark harnesses are available:

### JMH (recommended — rigorous, statistically sound)

```bash
# Run all benchmarks (8 total: 4 read + 4 write)
./mill benchmark-jmh.runJmh

# Read benchmarks only
./mill benchmark-jmh.runJmh 'Read'

# Write benchmarks only
./mill benchmark-jmh.runJmh 'Write'

# Single library
./mill benchmark-jmh.runJmh 'sanelyJsoniter'

# Custom warmup/measurement/forks
./mill benchmark-jmh.runJmh -wi 5 -i 5 -f 1 -r 1 -w 1

# With GC allocation profiler
./mill benchmark-jmh.runJmh -prof gc

# With async-profiler flame graph
./mill benchmark-jmh.runJmh -prof async:output=flamegraph

# List all detected benchmarks
./mill benchmark-jmh.listJmhBenchmarks
```

JMH provides: fork isolation (separate JVM per benchmark), compiler blackhole dead-code prevention,
statistical error reporting (99.9% CI), and built-in profiler integration.

### Analyzing JMH results

After running JMH, use the analysis script to produce a compact summary:

```bash
# From a saved file
python3 scripts/analyze_jmh.py runtime.txt

# Pipe directly from JMH run
./mill benchmark-jmh.runJmh 2>&1 | tee runtime.txt
python3 scripts/analyze_jmh.py runtime.txt

# Or pipe from stdin
./mill benchmark-jmh.runJmh 2>&1 | python3 scripts/analyze_jmh.py -
```

The script handles both raw JMH output and mill-prefixed log output (e.g. CI logs with `178]` prefixes).
It produces:
- **At a Glance — Runtime** table with ops/sec and vs-circe ratios
- **Key Takeaways** with sanely-jsoniter speedups vs circe and vs jsoniter-scala
- **Detailed Results** with min/max ranges per library

### Hand-rolled harness (quick, for sanity checks)

```bash
# Run with defaults (5 warmup + 5 measurement iterations, each 1 second)
bash bench-runtime.sh

# Custom warmup and iterations
bash bench-runtime.sh 3 10

# Run directly via Mill
./mill benchmark-runtime.run 5 5
```

The hand-rolled harness also reports allocation per operation via `ThreadMXBean.getThreadAllocatedBytes`.

## Module Structure

```
benchmark-jmh/
  package.mill                           # Mill module with JmhModule mixin
  src/runtime/Benchmarks.scala           # JMH benchmark classes (@Benchmark, @State)

benchmark-runtime/
  package.mill                           # Mill module definition
  src/runtime/RuntimeBenchmark.scala     # Models, codecs, hand-rolled harness
```

**Dependencies**: sanely (our library), sanely-jsoniter, circe-core, circe-parser,
jsoniter-scala-core, jsoniter-scala-circe, jsoniter-scala-macros.

**Build-time**: `mill-contrib-jmh` declared in `build.mill` YAML header (`//| mvnDeps`).

## How the Benchmark Works

### Data model

```
ApiResponse
  ├── user: User
  │     ├── id, name, email, age, active
  │     ├── address: Address (5 fields)
  │     ├── tags: List[String]
  │     └── phone, bio: Option[String]
  ├── orders: List[Order]
  │     ├── items: List[OrderItem] (4 fields each)
  │     └── status: OrderStatus (sealed trait: Delivered | Shipped | Processing)
  ├── requestId: String
  └── timestamp: Long
```

Sample data: 1 user, 3 orders, 10 order items total. ~1.4 KB JSON.

### Codec derivation

- **circe codecs**: derived via `io.circe.generic.semiauto.deriveCodec` (uses sanely-auto)
- **sanely-jsoniter codecs**: derived via `sanely.jsoniter.semiauto.deriveJsoniterCodec` (per-type, circe-compatible wire format)
- **jsoniter codecs**: derived via `JsonCodecMaker.make` (jsoniter-scala macros, jsoniter-scala's own wire format)
- **circe+jsoniter bridge**: `JsoniterScalaCodec.jsonC3c` provides `JsonValueCodec[Json]`

### JMH benchmark structure

- `BenchmarkState` (`@State(Scope.Thread)`) — sets up data and pre-serialized byte arrays in `@Setup`
- `ReadBenchmark` — 4 `@Benchmark` methods: `circeJawn`, `circeJsoniterBridge`, `sanelyJsoniter`, `jsoniterScala`
- `WriteBenchmark` — 4 `@Benchmark` methods: `circePrinter`, `circeJsoniterBridge`, `sanelyJsoniter`, `jsoniterScala`
- Default config: `@Fork(1)`, `@Warmup(5 x 1s)`, `@Measurement(5 x 1s)`, `-Xms512m -Xmx512m`

### What's measured

**Reading** (bytes -> case class):
- `circe-jawn`: `io.circe.jawn.decodeByteArray[ApiResponse](bytes)`
- `circe+jsoniter`: `readFromArray[Json](bytes).as[ApiResponse]`
- `sanely-jsoniter`: `readFromArray[ApiResponse](bytes)` (using sanely-jsoniter codec, circe wire format)
- `jsoniter-scala`: `readFromArray[ApiResponse](bytes)` (using native jsoniter codec)

**Writing** (case class -> bytes):
- `circe-printer`: `Printer.noSpaces.print(obj.asJson).getBytes`
- `circe+jsoniter`: `writeToArray(obj.asJson)` (jsoniter serializes circe Json AST)
- `sanely-jsoniter`: `writeToArray(obj)` (direct streaming, circe-compatible output)
- `jsoniter-scala`: `writeToArray(obj)` (direct streaming, jsoniter-scala format)

## Interpreting Results

### Expected ratios (M3 Max, JDK 21, JMH)

| | Reading | Writing |
|---|---|---|
| **circe-jawn** (baseline) | 1.0x | 1.0x |
| **circe+jsoniter** | ~1.6x faster | ~0.9x (slightly slower) |
| **sanely-jsoniter** | **~5.6x faster** | **~8.9x faster** |
| **jsoniter-scala** | ~5.2x faster | ~7.4x faster |

sanely-jsoniter surpasses jsoniter-scala native: **+9% reads, +19% writes**.

### Why sanely-jsoniter beats jsoniter-scala

- **Direct constructor calls**: `new User(_f0, _f1, ...)` vs `mirror.fromProduct(ArrayProduct(...))` — zero boxing
- **Branchless product encoding**: unconditional write-key/write-val sequence (circe writes all fields)
- **Typed local variables**: `var _age: Int = 0` vs `Array[Any]` — no boxing for primitives
- **Direct primitive I/O**: `reader.readInt()` inline, no codec virtual dispatch

### Why circe+jsoniter writing is slower than circe-printer

The writing bottleneck is `obj.asJson` — circe's `Encoder` building the `Json` AST (allocating
`JsonObject`, `JString`, `JNumber`, etc.). Swapping the printer doesn't help because the AST
construction dominates. sanely-jsoniter and jsoniter-scala write directly to bytes without an
intermediate AST, which is why they're 7-9x faster.

### What to watch for after changes

- **sanely-jsoniter drops but jsoniter-scala stable**: issue in sanely-jsoniter's generated codecs
- **circe-jawn reading drops**: sanely-auto generated a less efficient `Decoder` — check the
  generated HCursor traversal pattern
- **circe-jawn writing drops**: sanely-auto generated a less efficient `Encoder` — check the
  generated JsonObject construction
- **circe+jsoniter diverges from circe-jawn**: unexpected since they share the same `Decoder`/`Encoder`;
  only the parser/printer layer differs
- **All four drop equally**: JVM/system issue, not a code change issue

## Updating README.md

After running the benchmark, update the runtime table in README.md under
"Runtime — 5.6x faster reads, 8.9x faster writes":

```markdown
| Approach | Reading (ops/sec) | | Writing (ops/sec) | |
|---|---|---|---|---|
| **circe + jawn** (baseline) | XXX,XXX ± X,XXX | 1.0x | XXX,XXX ± X,XXX | 1.0x |
| **circe + jsoniter bridge** | XXX,XXX ± X,XXX | X.Xx | XXX,XXX ± X,XXX | X.Xx |
| **sanely-jsoniter** | **XXX,XXX ± X,XXX** | **X.Xx** | **X,XXX,XXX ± XX,XXX** | **X.Xx** |
| jsoniter-scala native | XXX,XXX ± XX,XXX | X.Xx | XXX,XXX ± X,XXX | X.Xx |
```

JMH results include ± error (99.9% CI). Include these in the table.

Update the section heading with the reading/writing speedup ratios.

Update the methodology section to reference JMH:
```
**Runtime**: [JMH](https://github.com/openjdk/jmh) 1.37 via Mill's `contrib.jmh` module.
1 fork, 5 warmup + 5 measurement iterations of 1 second each, `-Xms512m -Xmx512m`.
```

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

# 3. Runtime benchmark (JMH — rigorous)
./mill benchmark-jmh.runJmh

# 3b. Runtime benchmark (quick sanity check)
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

**If runtime regresses**: investigate which approach regressed. If only circe approaches regress,
the issue is in sanely-auto's generated codecs. If sanely-jsoniter regresses but jsoniter-scala
doesn't, the issue is in sanely-jsoniter's macro-generated codecs. If all regress, it's a JVM/system issue.

**If compile-time regresses but runtime is stable**: the macro generates the same quality code
but takes longer to do it — focus on macro profiling.

## Baseline Numbers (JMH)

Reference from JMH run (M3 Max, JDK 25.0.2 Homebrew, JMH 1.37, 1 fork, 5wi + 5i x 1s, -Xms512m -Xmx512m):

```
Benchmark                            Mode  Cnt        Score       Error  Units
ReadBenchmark.circeJawn             thrpt    5   133,568 ±  1,334  ops/s
ReadBenchmark.circeJsoniterBridge   thrpt    5   208,474 ±  3,217  ops/s
ReadBenchmark.jsoniterScala         thrpt    5   689,629 ± 18,244  ops/s
ReadBenchmark.sanelyJsoniter        thrpt    5   753,142 ±  9,490  ops/s
WriteBenchmark.circeJsoniterBridge  thrpt    5   125,410 ±  1,816  ops/s
WriteBenchmark.circePrinter         thrpt    5   132,620 ±  2,162  ops/s
WriteBenchmark.jsoniterScala        thrpt    5   987,963 ±  1,954  ops/s
WriteBenchmark.sanelyJsoniter       thrpt    5 1,179,652 ± 17,232  ops/s
```
