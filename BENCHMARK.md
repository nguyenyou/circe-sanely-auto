# Benchmark History

Automated benchmarks run on `ubuntu-latest` (GitHub Actions shared runners) after each release.

**Note on CI vs local numbers:** Shared runners have noisy neighbors, no CPU pinning, and variable clock speeds. Absolute throughput is ~50% lower than dedicated hardware. **Ratios between libraries** (e.g. sanely-jsoniter vs circe-jawn) are the meaningful metric — they remain stable across environments. The README reports numbers from a dedicated Apple M3 Max; these CI results track regressions between releases.

<!-- BENCHMARK ENTRIES -->

## v0.21.0

**Date:** 2026-03-12 02:12:49 UTC | **SHA:** `62f1420`

### At a Glance — Compile Time

| Metric | sanely-auto | circe-generic | Delta |
|--------|-------------|---------------|-------|
| Compile (auto, ~300 types) | 6.3s ± 0.18s | 23.3s ± 1.34s | **3.7x faster** |
| Compile (configured, ~230 types) | 3.2s ± 0.14s | 7.4s ± 1.31s | **2.3x faster** |
| Peak RSS (auto) | 896 MB | 1135 MB | **-21%** |
| Peak RSS (configured) | 710 MB | 777 MB | **-9%** |
| Bytecode (auto) | 2.5 MB | 3.2 MB | **-22%** |
| Bytecode (configured) | 2.6 MB | 3.0 MB | **-10%** |

### At a Glance — Runtime (JMH)

| Benchmark | ops/sec | vs circe |
|-----------|---------|----------|
| Read: circe-jawn | 92k ± 752 | 1.0x |
| Read: circe+jsoniter | 115k ± 580 | **1.3x** |
| Read: jsoniter-scala | 393k ± 2k | **4.3x** |
| Read: sanely-jsoniter | 397k ± 2k | **4.3x** |
| Write: circe+jsoniter | 78k ± 504 | **1.2x** |
| Write: circe-printer | 65k ± 909 | 1.0x |
| Write: jsoniter-scala | 611k ± 6k | **9.4x** |
| Write: sanely-jsoniter | 559k ± 2k | **8.6x** |

<details>
<summary>Compile Time — Auto Derivation</summary>

```
Compile-time benchmark: circe-sanely-auto vs circe-generic (N=10)
Benchmark suite: benchmark
Method: Mill daemon, hyperfine with --warmup 1, --runs 10
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark.generic
  Time (mean ± σ):     23.342 s ±  1.344 s    [User: 0.138 s, System: 0.276 s]
  Range (min … max):   22.391 s … 26.747 s    10 runs
 
  Warning: The first benchmarking run for this command was significantly slower than the rest (26.747 s). This could be caused by (filesystem) caches that were not filled until after the first run. You are already using both the '--warmup' option as well as the '--prepare' option. Consider re-running the benchmark on a quiet system. Maybe it was a random outlier. Alternatively, consider increasing the warmup count.
 
Benchmark 2: benchmark.sanely
  Time (mean ± σ):      6.273 s ±  0.176 s    [User: 0.047 s, System: 0.093 s]
  Range (min … max):    6.125 s …  6.680 s    10 runs
 
Summary
  benchmark.sanely ran
    3.72 ± 0.24 times faster than benchmark.generic
```
</details>

<details>
<summary>Compile Time — Configured Derivation</summary>

```
Compile-time benchmark: circe-sanely-auto vs circe-core configured derivation (N=10)
Benchmark suite: benchmark-configured
Method: Mill daemon, hyperfine with --warmup 1, --runs 10
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark-configured.generic
  Time (mean ± σ):      7.382 s ±  1.310 s    [User: 0.057 s, System: 0.105 s]
  Range (min … max):    6.508 s … 10.850 s    10 runs
 
Benchmark 2: benchmark-configured.sanely
  Time (mean ± σ):      3.173 s ±  0.135 s    [User: 0.036 s, System: 0.062 s]
  Range (min … max):    2.976 s …  3.459 s    10 runs
 
Summary
  benchmark-configured.sanely ran
    2.33 ± 0.42 times faster than benchmark-configured.generic
```
</details>

<details>
<summary>Runtime Performance</summary>

```
# VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS
# VM invoker: /home/runner/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/zulu21.46.19-ca-jdk21.0.9-linux_x64.tar.gz/zulu21.46.19-ca-jdk21.0.9-linux_x64/bin/java
# VM options: -Xms512m -Xmx512m
# Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: runtime.ReadBenchmark.circeJawn

# Run progress: 0.00% complete, ETA 00:02:40
# Fork: 1 of 1
# Warmup Iteration   1: 57872.649 ops/s
# Warmup Iteration   2: 89640.199 ops/s
# Warmup Iteration   3: 89616.813 ops/s
# Warmup Iteration   4: 91911.556 ops/s
# Warmup Iteration   5: 91688.107 ops/s
# Warmup Iteration   6: 91094.006 ops/s
# Warmup Iteration   7: 91339.823 ops/s
# Warmup Iteration   8: 91357.277 ops/s
# Warmup Iteration   9: 91832.344 ops/s
# Warmup Iteration  10: 91506.232 ops/s
Iteration   1: 91788.161 ops/s
Iteration   2: 91682.547 ops/s
Iteration   3: 92089.655 ops/s
Iteration   4: 91741.176 ops/s
Iteration   5: 91954.788 ops/s
Iteration   6: 90484.799 ops/s
Iteration   7: 91898.943 ops/s
Iteration   8: 91546.985 ops/s
Iteration   9: 91638.362 ops/s
Iteration  10: 90917.097 ops/s


Result "runtime.ReadBenchmark.circeJawn":
91574.251 ±(99.9%) 751.690 ops/s [Average]


# JMH version: 1.37
# VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS
# VM invoker: /home/runner/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/zulu21.46.19-ca-jdk21.0.9-linux_x64.tar.gz/zulu21.46.19-ca-jdk21.0.9-linux_x64/bin/java
# VM options: -Xms512m -Xmx512m
# Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: runtime.ReadBenchmark.circeJsoniterBridge

# Run progress: 12.50% complete, ETA 00:02:24
# Fork: 1 of 1
# Warmup Iteration   1: 78545.477 ops/s
# Warmup Iteration   2: 116722.544 ops/s
# Warmup Iteration   3: 114684.356 ops/s
# Warmup Iteration   4: 114632.115 ops/s
# Warmup Iteration   5: 115164.797 ops/s
# Warmup Iteration   6: 114712.530 ops/s
# Warmup Iteration   7: 115006.162 ops/s
# Warmup Iteration   8: 114488.501 ops/s
# Warmup Iteration   9: 113497.878 ops/s
# Warmup Iteration  10: 114748.242 ops/s
Iteration   1: 113862.814 ops/s
Iteration   2: 114974.253 ops/s
Iteration   3: 115052.672 ops/s
Iteration   4: 114787.881 ops/s
Iteration   5: 115138.030 ops/s
Iteration   6: 114755.413 ops/s
Iteration   7: 114926.402 ops/s
Iteration   8: 114699.257 ops/s
Iteration   9: 114289.601 ops/s
Iteration  10: 114633.653 ops/s


Result "runtime.ReadBenchmark.circeJsoniterBridge":
114711.998 ±(99.9%) 580.265 ops/s [Average]


# JMH version: 1.37
# VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS
# VM invoker: /home/runner/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/zulu21.46.19-ca-jdk21.0.9-linux_x64.tar.gz/zulu21.46.19-ca-jdk21.0.9-linux_x64/bin/java
# VM options: -Xms512m -Xmx512m
# Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: runtime.ReadBenchmark.jsoniterScala

# Run progress: 25.00% complete, ETA 00:02:03
# Fork: 1 of 1
# Warmup Iteration   1: 271079.567 ops/s
# Warmup Iteration   2: 384276.350 ops/s
# Warmup Iteration   3: 393099.604 ops/s
# Warmup Iteration   4: 391055.561 ops/s
# Warmup Iteration   5: 391053.856 ops/s
# Warmup Iteration   6: 391983.740 ops/s
# Warmup Iteration   7: 391605.788 ops/s
# Warmup Iteration   8: 390648.428 ops/s
# Warmup Iteration   9: 392268.088 ops/s
# Warmup Iteration  10: 393822.348 ops/s
Iteration   1: 391652.139 ops/s
Iteration   2: 394344.276 ops/s
Iteration   3: 391402.980 ops/s
Iteration   4: 393611.121 ops/s
Iteration   5: 392028.885 ops/s
Iteration   6: 394796.474 ops/s
Iteration   7: 389590.072 ops/s
Iteration   8: 392685.584 ops/s
Iteration   9: 393174.009 ops/s
Iteration  10: 392122.089 ops/s


Result "runtime.ReadBenchmark.jsoniterScala":
392540.763 ±(99.9%) 2316.900 ops/s [Average]


# JMH version: 1.37
# VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS
# VM invoker: /home/runner/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/zulu21.46.19-ca-jdk21.0.9-linux_x64.tar.gz/zulu21.46.19-ca-jdk21.0.9-linux_x64/bin/java
# VM options: -Xms512m -Xmx512m
# Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: runtime.ReadBenchmark.sanelyJsoniter

# Run progress: 37.50% complete, ETA 00:01:42
# Fork: 1 of 1
# Warmup Iteration   1: 301103.265 ops/s
# Warmup Iteration   2: 394185.603 ops/s
# Warmup Iteration   3: 394472.392 ops/s
# Warmup Iteration   4: 397317.358 ops/s
# Warmup Iteration   5: 396005.289 ops/s
# Warmup Iteration   6: 399372.003 ops/s
# Warmup Iteration   7: 396587.641 ops/s
# Warmup Iteration   8: 395983.395 ops/s
# Warmup Iteration   9: 397354.485 ops/s
# Warmup Iteration  10: 397878.646 ops/s
Iteration   1: 396990.698 ops/s
Iteration   2: 397089.646 ops/s
Iteration   3: 398615.010 ops/s
Iteration   4: 394968.280 ops/s
Iteration   5: 395459.044 ops/s
Iteration   6: 399664.624 ops/s
Iteration   7: 397386.856 ops/s
Iteration   8: 398582.708 ops/s
Iteration   9: 399208.738 ops/s
Iteration  10: 395999.883 ops/s


Result "runtime.ReadBenchmark.sanelyJsoniter":
397396.549 ±(99.9%) 2426.305 ops/s [Average]


# JMH version: 1.37
# VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS
# VM invoker: /home/runner/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/zulu21.46.19-ca-jdk21.0.9-linux_x64.tar.gz/zulu21.46.19-ca-jdk21.0.9-linux_x64/bin/java
# VM options: -Xms512m -Xmx512m
# Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: runtime.WriteBenchmark.circeJsoniterBridge

# Run progress: 50.00% complete, ETA 00:01:22
# Fork: 1 of 1
# Warmup Iteration   1: 50532.646 ops/s
# Warmup Iteration   2: 75898.176 ops/s
# Warmup Iteration   3: 76459.831 ops/s
# Warmup Iteration   4: 77357.971 ops/s
# Warmup Iteration   5: 77460.674 ops/s
# Warmup Iteration   6: 77548.931 ops/s
# Warmup Iteration   7: 78088.193 ops/s
# Warmup Iteration   8: 77868.539 ops/s
# Warmup Iteration   9: 77886.148 ops/s
# Warmup Iteration  10: 78282.480 ops/s
Iteration   1: 77127.706 ops/s
Iteration   2: 78000.552 ops/s
Iteration   3: 77875.714 ops/s
Iteration   4: 77486.866 ops/s
Iteration   5: 77562.870 ops/s
Iteration   6: 77228.891 ops/s
Iteration   7: 77881.008 ops/s
Iteration   8: 78018.714 ops/s
Iteration   9: 77480.811 ops/s
Iteration  10: 78008.470 ops/s


Result "runtime.WriteBenchmark.circeJsoniterBridge":
77667.160 ±(99.9%) 504.370 ops/s [Average]


# JMH version: 1.37
# VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS
# VM invoker: /home/runner/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/zulu21.46.19-ca-jdk21.0.9-linux_x64.tar.gz/zulu21.46.19-ca-jdk21.0.9-linux_x64/bin/java
# VM options: -Xms512m -Xmx512m
# Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: runtime.WriteBenchmark.circePrinter

# Run progress: 62.50% complete, ETA 00:01:01
# Fork: 1 of 1
# Warmup Iteration   1: 46175.243 ops/s
# Warmup Iteration   2: 64001.633 ops/s
# Warmup Iteration   3: 64193.247 ops/s
# Warmup Iteration   4: 64624.404 ops/s
# Warmup Iteration   5: 65354.684 ops/s
# Warmup Iteration   6: 65058.316 ops/s
# Warmup Iteration   7: 65025.037 ops/s
# Warmup Iteration   8: 65490.995 ops/s
# Warmup Iteration   9: 64696.453 ops/s
# Warmup Iteration  10: 63816.824 ops/s
Iteration   1: 64871.803 ops/s
Iteration   2: 65209.787 ops/s
Iteration   3: 64934.040 ops/s
Iteration   4: 65153.158 ops/s
Iteration   5: 65361.713 ops/s
Iteration   6: 65143.757 ops/s
Iteration   7: 65011.660 ops/s
Iteration   8: 65003.165 ops/s
Iteration   9: 64602.460 ops/s
Iteration  10: 63248.198 ops/s


Result "runtime.WriteBenchmark.circePrinter":
64853.974 ±(99.9%) 909.004 ops/s [Average]


# JMH version: 1.37
# VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS
# VM invoker: /home/runner/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/zulu21.46.19-ca-jdk21.0.9-linux_x64.tar.gz/zulu21.46.19-ca-jdk21.0.9-linux_x64/bin/java
# VM options: -Xms512m -Xmx512m
# Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: runtime.WriteBenchmark.jsoniterScala

# Run progress: 75.00% complete, ETA 00:00:41
# Fork: 1 of 1
# Warmup Iteration   1: 365317.651 ops/s
# Warmup Iteration   2: 613368.322 ops/s
# Warmup Iteration   3: 610487.834 ops/s
# Warmup Iteration   4: 613817.271 ops/s
# Warmup Iteration   5: 611195.038 ops/s
# Warmup Iteration   6: 615306.633 ops/s
# Warmup Iteration   7: 603519.707 ops/s
# Warmup Iteration   8: 613112.051 ops/s
# Warmup Iteration   9: 613385.907 ops/s
# Warmup Iteration  10: 597898.504 ops/s
Iteration   1: 610736.008 ops/s
Iteration   2: 614790.944 ops/s
Iteration   3: 599977.436 ops/s
Iteration   4: 612532.028 ops/s
Iteration   5: 611854.629 ops/s
Iteration   6: 612953.699 ops/s
Iteration   7: 613663.795 ops/s
Iteration   8: 612750.669 ops/s
Iteration   9: 608741.792 ops/s
Iteration  10: 609246.925 ops/s


Result "runtime.WriteBenchmark.jsoniterScala":
610724.792 ±(99.9%) 6382.679 ops/s [Average]


# JMH version: 1.37
# VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS
# VM invoker: /home/runner/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/zulu21.46.19-ca-jdk21.0.9-linux_x64.tar.gz/zulu21.46.19-ca-jdk21.0.9-linux_x64/bin/java
# VM options: -Xms512m -Xmx512m
# Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: runtime.WriteBenchmark.sanelyJsoniter

# Run progress: 87.50% complete, ETA 00:00:20
# Fork: 1 of 1
# Warmup Iteration   1: 399713.687 ops/s
# Warmup Iteration   2: 549356.018 ops/s
# Warmup Iteration   3: 552205.490 ops/s
# Warmup Iteration   4: 555746.829 ops/s
# Warmup Iteration   5: 551398.955 ops/s
# Warmup Iteration   6: 556019.152 ops/s
# Warmup Iteration   7: 554085.488 ops/s
# Warmup Iteration   8: 556752.362 ops/s
# Warmup Iteration   9: 554150.964 ops/s
# Warmup Iteration  10: 555656.593 ops/s
Iteration   1: 557781.472 ops/s
Iteration   2: 559502.752 ops/s
Iteration   3: 558149.911 ops/s
Iteration   4: 558503.690 ops/s
Iteration   5: 557669.955 ops/s
Iteration   6: 557661.462 ops/s
Iteration   7: 559264.687 ops/s
Iteration   8: 561229.182 ops/s
Iteration   9: 558645.432 ops/s
Iteration  10: 561515.316 ops/s


Result "runtime.WriteBenchmark.sanelyJsoniter":
558992.386 ±(99.9%) 2123.698 ops/s [Average]


# Run complete. Total time: 00:02:44



Benchmark                            Mode  Cnt       Score      Error  Units
ReadBenchmark.circeJawn             thrpt   10   91574.251 ±  751.690  ops/s
ReadBenchmark.circeJsoniterBridge   thrpt   10  114711.998 ±  580.265  ops/s
ReadBenchmark.jsoniterScala         thrpt   10  392540.763 ± 2316.900  ops/s
ReadBenchmark.sanelyJsoniter        thrpt   10  397396.549 ± 2426.305  ops/s
WriteBenchmark.circeJsoniterBridge  thrpt   10   77667.160 ±  504.370  ops/s
WriteBenchmark.circePrinter         thrpt   10   64853.974 ±  909.004  ops/s
WriteBenchmark.jsoniterScala        thrpt   10  610724.792 ± 6382.679  ops/s
WriteBenchmark.sanelyJsoniter       thrpt   10  558992.386 ± 2123.698  ops/s
```
</details>

<details>
<summary>Peak RSS</summary>

```
sanely-auto (auto): 917592 KB
circe-generic (auto): 1162408 KB
sanely-auto (configured): 727016 KB
circe-generic (configured): 795540 KB
```
</details>

<details>
<summary>Bytecode Impact</summary>

```
sanely-auto (auto): 2634732 bytes (2573.0 KB)
circe-generic (auto): 3384133 bytes (3304.8 KB)
sanely-auto (configured): 2775933 bytes (2710.9 KB)
circe-generic (configured): 3097489 bytes (3024.9 KB)
```
</details>

<details>
<summary>Macro Profile — Auto</summary>

```
======================================================================
SANELY MACRO PROFILE (398 expansions, 6980ms total)
======================================================================

--- By Kind ---
  Decoder          187 expansions    3279.7ms  avg 17.54ms
  Encoder          211 expansions    3700.0ms  avg 17.54ms

--- Category Breakdown ---
  summonIgnoring         2989.6ms ( 42.8%)     894 calls  avg 3.34ms
  derive                 2909.2ms ( 41.7%)     920 calls  avg 3.16ms
  tryBuiltin              418.8ms (  6.0%)    1943 calls  avg 0.22ms
  summonMirror            331.6ms (  4.8%)     920 calls  avg 0.36ms
  subTraitDetect          160.9ms (  2.3%)     336 calls  avg 0.48ms
  cheapTypeKey             14.1ms (  0.2%)    4342 calls  avg 0.00ms
  builtinHit                0.0ms (  0.0%)     907 calls  avg 0.00ms
  cacheHit                           2399 hits
  constructorNegHit         0.0ms (  0.0%)     142 calls  avg 0.00ms
  overhead                155.5ms (  2.2%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. Encoder[MixedReport]: total=160.8ms  summonIgnoring=88.8ms(16x) derive=60.3ms(21x) tryBuiltin=37.5ms(29x) summonMirror=4.8ms(21x) cheapTypeKey=0.6ms(75x) builtinHit=0.0ms(5x) cacheHit=0.0ms(46x) constructorNegHit=0.0ms(8x)
   2. Encoder[Ticket]: total=160.0ms  summonIgnoring=95.2ms(27x) derive=85.7ms(22x) summonMirror=11.2ms(22x) tryBuiltin=10.5ms(31x) subTraitDetect=8.0ms(18x) cheapTypeKey=0.3ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   3. Decoder[Article]: total=151.4ms  summonIgnoring=107.8ms(14x) derive=43.6ms(12x) tryBuiltin=6.2ms(18x) summonMirror=5.0ms(12x) subTraitDetect=2.1ms(6x) cheapTypeKey=0.2ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   4. Decoder[Ticket]: total=150.2ms  summonIgnoring=90.5ms(27x) derive=80.7ms(22x) tryBuiltin=10.4ms(31x) summonMirror=8.7ms(22x) subTraitDetect=5.4ms(18x) cheapTypeKey=0.3ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   5. Encoder[Article]: total=126.3ms  summonIgnoring=81.8ms(14x) derive=49.5ms(12x) summonMirror=7.1ms(12x) tryBuiltin=4.1ms(18x) subTraitDetect=4.1ms(6x) cheapTypeKey=0.2ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   6. Encoder[AnalyticsView]: total=117.5ms  derive=86.8ms(12x) summonIgnoring=85.3ms(11x) tryBuiltin=7.9ms(21x) summonMirror=4.3ms(12x) cheapTypeKey=0.2ms(51x) builtinHit=0.0ms(3x) cacheHit=0.0ms(30x) constructorNegHit=0.0ms(7x)
   7. Encoder[HttpMethod]: total=114.2ms  summonIgnoring=73.2ms(5x) derive=11.0ms(5x) subTraitDetect=5.4ms(5x) summonMirror=4.2ms(5x) tryBuiltin=0.6ms(6x) cheapTypeKey=0.1ms(10x) builtinHit=0.0ms(1x) cacheHit=0.0ms(4x)
   8. Encoder[Sprint]: total=110.9ms  summonIgnoring=105.2ms(1x) tryBuiltin=2.0ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   9. Encoder[UserReport]: total=103.1ms  derive=74.3ms(6x) summonIgnoring=73.9ms(7x) tryBuiltin=13.8ms(13x) summonMirror=2.8ms(6x) cheapTypeKey=0.2ms(36x) builtinHit=0.0ms(1x) cacheHit=0.0ms(23x) constructorNegHit=0.0ms(5x)
  10. Encoder[AlertInstance]: total=101.0ms  derive=144.1ms(19x) summonIgnoring=39.3ms(20x) summonMirror=12.1ms(19x) subTraitDetect=6.8ms(13x) tryBuiltin=2.3ms(26x) cheapTypeKey=0.1ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)
  11. Decoder[Sprint]: total=100.6ms  summonIgnoring=96.2ms(1x) tryBuiltin=0.9ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
  12. Decoder[AlertInstance]: total=99.4ms  derive=142.3ms(19x) summonIgnoring=40.6ms(20x) summonMirror=9.0ms(19x) subTraitDetect=4.9ms(13x) tryBuiltin=2.2ms(26x) cheapTypeKey=0.2ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)
  13. Encoder[Wide22A]: total=97.9ms  tryBuiltin=2.1ms(4x) cheapTypeKey=0.2ms(22x) builtinHit=0.0ms(4x) cacheHit=0.0ms(18x)
  14. Encoder[NotifChannel]: total=96.8ms  summonIgnoring=67.7ms(5x) derive=13.2ms(5x) summonMirror=4.3ms(5x) subTraitDetect=3.8ms(5x) tryBuiltin=0.6ms(7x) cheapTypeKey=0.0ms(14x) builtinHit=0.0ms(2x) cacheHit=0.0ms(7x)
  15. Decoder[AnalyticsView]: total=94.3ms  derive=81.0ms(12x) summonIgnoring=64.6ms(11x) tryBuiltin=7.3ms(21x) summonMirror=2.7ms(12x) cheapTypeKey=0.2ms(51x) builtinHit=0.0ms(3x) cacheHit=0.0ms(30x) constructorNegHit=0.0ms(7x)

--- Hot Types (>50ms) ---
  Encoder[MixedReport]: 161ms
  Encoder[Ticket]: 160ms
  Decoder[Article]: 151ms
  Decoder[Ticket]: 150ms
  Encoder[Article]: 126ms
======================================================================
```
</details>

<details>
<summary>Macro Profile — Configured</summary>

```
======================================================================
SANELY MACRO PROFILE (230 expansions, 2544ms total)
======================================================================

--- By Kind ---
  CfgCodec         230 expansions    2544.5ms  avg 11.06ms

--- Category Breakdown ---
  topDerive              2511.9ms ( 98.7%)     230 calls  avg 10.92ms
  tryBuiltin              469.8ms ( 18.5%)     493 calls  avg 0.95ms
  summonIgnoring          257.9ms ( 10.1%)     118 calls  avg 2.19ms
  resolveDefaults          35.2ms (  1.4%)     214 calls  avg 0.16ms
  subTraitDetect           10.4ms (  0.4%)      69 calls  avg 0.15ms
  cheapTypeKey              4.8ms (  0.2%)     820 calls  avg 0.01ms
  builtinHit                0.0ms (  0.0%)     375 calls  avg 0.00ms
  cacheHit                            327 hits
  codecHit                  0.0ms (  0.0%)     118 calls  avg 0.00ms
  overhead               -745.5ms (-29.3%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. CfgCodec[Role]: total=90.2ms  topDerive=88.6ms(1x) tryBuiltin=75.7ms(2x) cheapTypeKey=0.9ms(4x) resolveDefaults=0.2ms(1x) builtinHit=0.0ms(2x) cacheHit=0.0ms(2x)
   2. CfgCodec[UserId]: total=84.0ms  topDerive=81.5ms(1x) resolveDefaults=2.1ms(1x) tryBuiltin=0.7ms(1x) cheapTypeKey=0.1ms(1x) builtinHit=0.0ms(1x)
   3. CfgCodec[Article]: total=63.8ms  topDerive=63.6ms(1x) tryBuiltin=21.3ms(8x) summonIgnoring=20.1ms(5x) resolveDefaults=0.7ms(1x) cheapTypeKey=0.1ms(11x) codecHit=0.0ms(5x) builtinHit=0.0ms(3x) cacheHit=0.0ms(3x)
   4. CfgCodec[AuthEvent]: total=51.9ms  topDerive=50.9ms(1x) summonIgnoring=12.0ms(5x) subTraitDetect=1.2ms(5x) tryBuiltin=1.0ms(5x) cheapTypeKey=0.1ms(5x) codecHit=0.0ms(5x)
   5. CfgCodec[ShipStock]: total=49.0ms  topDerive=48.9ms(1x) tryBuiltin=0.2ms(2x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(3x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x)
   6. CfgCodec[ChatMessage]: total=38.9ms  topDerive=38.8ms(1x) tryBuiltin=22.2ms(7x) summonIgnoring=7.4ms(3x) cheapTypeKey=0.1ms(8x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(3x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
   7. CfgCodec[Invoice]: total=38.4ms  topDerive=38.1ms(1x) tryBuiltin=21.8ms(6x) summonIgnoring=2.4ms(2x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(11x) builtinHit=0.0ms(4x) cacheHit=0.0ms(5x) codecHit=0.0ms(2x)
   8. CfgCodec[Product]: total=35.6ms  topDerive=35.4ms(1x) tryBuiltin=15.9ms(10x) summonIgnoring=10.0ms(6x) cheapTypeKey=0.1ms(10x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(4x) codecHit=0.0ms(6x)
   9. CfgCodec[CommentThread]: total=32.9ms  topDerive=32.8ms(1x) tryBuiltin=22.5ms(3x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.0ms(3x) builtinHit=0.0ms(3x)
  10. CfgCodec[CustomerRecord]: total=32.7ms  topDerive=32.5ms(1x) tryBuiltin=12.7ms(7x) summonIgnoring=10.0ms(5x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(8x) codecHit=0.0ms(5x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x)
  11. CfgCodec[ArticleBody]: total=32.4ms  topDerive=32.3ms(1x) tryBuiltin=0.2ms(2x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.0ms(3x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x)
  12. CfgCodec[MetricSeries]: total=29.8ms  topDerive=29.6ms(1x) tryBuiltin=15.8ms(4x) summonIgnoring=3.7ms(1x) resolveDefaults=0.3ms(1x) cheapTypeKey=0.1ms(4x) codecHit=0.0ms(1x) builtinHit=0.0ms(3x)
  13. CfgCodec[WorkflowStep]: total=27.9ms  topDerive=27.8ms(1x) tryBuiltin=15.5ms(6x) summonIgnoring=5.0ms(2x) cheapTypeKey=0.1ms(7x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(2x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
  14. CfgCodec[Report]: total=27.5ms  topDerive=27.3ms(1x) tryBuiltin=13.8ms(4x) summonIgnoring=3.0ms(1x) cheapTypeKey=0.1ms(6x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(3x) cacheHit=0.0ms(2x) codecHit=0.0ms(1x)
  15. CfgCodec[SecurityConfig]: total=27.1ms  topDerive=26.9ms(1x) tryBuiltin=14.8ms(3x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(4x) builtinHit=0.0ms(3x) cacheHit=0.0ms(1x)

--- Hot Types (>50ms) ---
  CfgCodec[Role]: 90ms
  CfgCodec[UserId]: 84ms
  CfgCodec[Article]: 64ms
  CfgCodec[AuthEvent]: 52ms
======================================================================
```
</details>


## v0.20.0

**Date:** 2026-03-11 16:41:21 UTC | **SHA:** `7f1053e`

### At a Glance — Compile Time

| Metric | sanely-auto | circe-generic | Delta |
|--------|-------------|---------------|-------|
| Compile (auto, ~300 types) | 6.4s ± 0.17s | 23.4s ± 1.84s | **3.6x faster** |
| Compile (configured, ~230 types) | 3.4s ± 0.14s | 7.7s ± 1.32s | **2.3x faster** |
| Peak RSS (auto) | 1008 MB | 1090 MB | **-7%** |
| Peak RSS (configured) | 673 MB | 738 MB | **-9%** |
| Bytecode (auto) | 2.5 MB | 3.2 MB | **-22%** |
| Bytecode (configured) | 2.6 MB | 3.0 MB | **-10%** |

### At a Glance — Runtime (JMH)

| Benchmark | ops/sec | vs circe |
|-----------|---------|----------|
| Read: circe-jawn | 90k ± 1k | 1.0x |
| Read: circe+jsoniter | 117k ± 832 | **1.3x** |
| Read: sanely-jsoniter | 384k ± 3k | **4.3x** |
| Read: jsoniter-scala | 387k ± 2k | **4.3x** |
| Write: circe-printer | 65k ± 437 | 1.0x |
| Write: circe+jsoniter | 76k ± 363 | **1.2x** |
| Write: sanely-jsoniter | 533k ± 3k | **8.2x** |
| Write: jsoniter-scala | 606k ± 3k | **9.4x** |

<details>
<summary>Compile Time — Auto Derivation</summary>

```
Compile-time benchmark: circe-sanely-auto vs circe-generic (N=10)
Benchmark suite: benchmark
Method: Mill daemon, hyperfine with --warmup 1, --runs 10
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark.generic
  Time (mean ± σ):     23.414 s ±  1.844 s    [User: 0.129 s, System: 0.285 s]
  Range (min … max):   22.273 s … 28.339 s    10 runs
 
  Warning: The first benchmarking run for this command was significantly slower than the rest (28.339 s). This could be caused by (filesystem) caches that were not filled until after the first run. You are already using both the '--warmup' option as well as the '--prepare' option. Consider re-running the benchmark on a quiet system. Maybe it was a random outlier. Alternatively, consider increasing the warmup count.
 
Benchmark 2: benchmark.sanely
  Time (mean ± σ):      6.418 s ±  0.165 s    [User: 0.044 s, System: 0.098 s]
  Range (min … max):    6.273 s …  6.699 s    10 runs
 
Summary
  benchmark.sanely ran
    3.65 ± 0.30 times faster than benchmark.generic
```
</details>

<details>
<summary>Compile Time — Configured Derivation</summary>

```
Compile-time benchmark: circe-sanely-auto vs circe-core configured derivation (N=10)
Benchmark suite: benchmark-configured
Method: Mill daemon, hyperfine with --warmup 1, --runs 10
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark-configured.generic
  Time (mean ± σ):      7.684 s ±  1.316 s    [User: 0.060 s, System: 0.106 s]
  Range (min … max):    6.771 s … 11.123 s    10 runs
 
Benchmark 2: benchmark-configured.sanely
  Time (mean ± σ):      3.381 s ±  0.135 s    [User: 0.034 s, System: 0.060 s]
  Range (min … max):    3.168 s …  3.588 s    10 runs
 
Summary
  benchmark-configured.sanely ran
    2.27 ± 0.40 times faster than benchmark-configured.generic
```
</details>

<details>
<summary>Runtime Performance</summary>

```
# VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS
# VM invoker: /home/runner/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/zulu21.46.19-ca-jdk21.0.9-linux_x64.tar.gz/zulu21.46.19-ca-jdk21.0.9-linux_x64/bin/java
# VM options: -Xms512m -Xmx512m
# Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: runtime.ReadBenchmark.circeJawn

# Run progress: 0.00% complete, ETA 00:02:40
# Fork: 1 of 1
# Warmup Iteration   1: 56723.437 ops/s
# Warmup Iteration   2: 86551.936 ops/s
# Warmup Iteration   3: 87195.959 ops/s
# Warmup Iteration   4: 89131.244 ops/s
# Warmup Iteration   5: 89961.488 ops/s
# Warmup Iteration   6: 87969.937 ops/s
# Warmup Iteration   7: 89984.486 ops/s
# Warmup Iteration   8: 87481.636 ops/s
# Warmup Iteration   9: 90101.937 ops/s
# Warmup Iteration  10: 89805.328 ops/s
Iteration   1: 89850.899 ops/s
Iteration   2: 90254.950 ops/s
Iteration   3: 89696.069 ops/s
Iteration   4: 89767.046 ops/s
Iteration   5: 90066.430 ops/s
Iteration   6: 90547.872 ops/s
Iteration   7: 90234.766 ops/s
Iteration   8: 87655.421 ops/s
Iteration   9: 90086.386 ops/s
Iteration  10: 90409.003 ops/s


Result "runtime.ReadBenchmark.circeJawn":
89856.884 ±(99.9%) 1240.638 ops/s [Average]


# JMH version: 1.37
# VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS
# VM invoker: /home/runner/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/zulu21.46.19-ca-jdk21.0.9-linux_x64.tar.gz/zulu21.46.19-ca-jdk21.0.9-linux_x64/bin/java
# VM options: -Xms512m -Xmx512m
# Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: runtime.ReadBenchmark.circeJsoniterBridge

# Run progress: 12.50% complete, ETA 00:02:24
# Fork: 1 of 1
# Warmup Iteration   1: 77693.294 ops/s
# Warmup Iteration   2: 116135.490 ops/s
# Warmup Iteration   3: 117671.221 ops/s
# Warmup Iteration   4: 115636.942 ops/s
# Warmup Iteration   5: 115532.933 ops/s
# Warmup Iteration   6: 115400.732 ops/s
# Warmup Iteration   7: 116158.171 ops/s
# Warmup Iteration   8: 115893.571 ops/s
# Warmup Iteration   9: 115308.549 ops/s
# Warmup Iteration  10: 115378.974 ops/s
Iteration   1: 115942.704 ops/s
Iteration   2: 117323.188 ops/s
Iteration   3: 117440.462 ops/s
Iteration   4: 116682.816 ops/s
Iteration   5: 117357.651 ops/s
Iteration   6: 117812.370 ops/s
Iteration   7: 117286.770 ops/s
Iteration   8: 117625.837 ops/s
Iteration   9: 117657.404 ops/s
Iteration  10: 117389.400 ops/s


Result "runtime.ReadBenchmark.circeJsoniterBridge":
117251.860 ±(99.9%) 832.389 ops/s [Average]


# JMH version: 1.37
# VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS
# VM invoker: /home/runner/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/zulu21.46.19-ca-jdk21.0.9-linux_x64.tar.gz/zulu21.46.19-ca-jdk21.0.9-linux_x64/bin/java
# VM options: -Xms512m -Xmx512m
# Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: runtime.ReadBenchmark.jsoniterScala

# Run progress: 25.00% complete, ETA 00:02:03
# Fork: 1 of 1
# Warmup Iteration   1: 284913.955 ops/s
# Warmup Iteration   2: 387819.890 ops/s
# Warmup Iteration   3: 388204.691 ops/s
# Warmup Iteration   4: 390268.717 ops/s
# Warmup Iteration   5: 390012.858 ops/s
# Warmup Iteration   6: 387364.402 ops/s
# Warmup Iteration   7: 389213.389 ops/s
# Warmup Iteration   8: 386780.975 ops/s
# Warmup Iteration   9: 389810.394 ops/s
# Warmup Iteration  10: 387049.151 ops/s
Iteration   1: 385591.969 ops/s
Iteration   2: 388232.119 ops/s
Iteration   3: 387326.184 ops/s
Iteration   4: 385410.267 ops/s
Iteration   5: 385038.499 ops/s
Iteration   6: 388542.997 ops/s
Iteration   7: 389223.113 ops/s
Iteration   8: 386779.709 ops/s
Iteration   9: 387829.896 ops/s
Iteration  10: 387725.249 ops/s


Result "runtime.ReadBenchmark.jsoniterScala":
387170.000 ±(99.9%) 2154.602 ops/s [Average]


# JMH version: 1.37
# VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS
# VM invoker: /home/runner/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/zulu21.46.19-ca-jdk21.0.9-linux_x64.tar.gz/zulu21.46.19-ca-jdk21.0.9-linux_x64/bin/java
# VM options: -Xms512m -Xmx512m
# Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: runtime.ReadBenchmark.sanelyJsoniter

# Run progress: 37.50% complete, ETA 00:01:42
# Fork: 1 of 1
# Warmup Iteration   1: 292502.979 ops/s
# Warmup Iteration   2: 388538.465 ops/s
# Warmup Iteration   3: 382234.629 ops/s
# Warmup Iteration   4: 393364.506 ops/s
# Warmup Iteration   5: 387835.524 ops/s
# Warmup Iteration   6: 382117.192 ops/s
# Warmup Iteration   7: 384951.818 ops/s
# Warmup Iteration   8: 384344.787 ops/s
# Warmup Iteration   9: 382940.110 ops/s
# Warmup Iteration  10: 384088.452 ops/s
Iteration   1: 385031.804 ops/s
Iteration   2: 384547.903 ops/s
Iteration   3: 383180.017 ops/s
Iteration   4: 382741.624 ops/s
Iteration   5: 384857.672 ops/s
Iteration   6: 383126.048 ops/s
Iteration   7: 384918.622 ops/s
Iteration   8: 379573.124 ops/s
Iteration   9: 384499.381 ops/s
Iteration  10: 382946.865 ops/s


Result "runtime.ReadBenchmark.sanelyJsoniter":
383542.306 ±(99.9%) 2510.919 ops/s [Average]


# JMH version: 1.37
# VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS
# VM invoker: /home/runner/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/zulu21.46.19-ca-jdk21.0.9-linux_x64.tar.gz/zulu21.46.19-ca-jdk21.0.9-linux_x64/bin/java
# VM options: -Xms512m -Xmx512m
# Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: runtime.WriteBenchmark.circeJsoniterBridge

# Run progress: 50.00% complete, ETA 00:01:22
# Fork: 1 of 1
# Warmup Iteration   1: 51571.951 ops/s
# Warmup Iteration   2: 74715.153 ops/s
# Warmup Iteration   3: 74728.148 ops/s
# Warmup Iteration   4: 75999.412 ops/s
# Warmup Iteration   5: 76522.345 ops/s
# Warmup Iteration   6: 76179.964 ops/s
# Warmup Iteration   7: 76340.906 ops/s
# Warmup Iteration   8: 76240.199 ops/s
# Warmup Iteration   9: 76700.974 ops/s
# Warmup Iteration  10: 76495.969 ops/s
Iteration   1: 76007.425 ops/s
Iteration   2: 76264.118 ops/s
Iteration   3: 75862.231 ops/s
Iteration   4: 76352.978 ops/s
Iteration   5: 75965.487 ops/s
Iteration   6: 76516.215 ops/s
Iteration   7: 76472.255 ops/s
Iteration   8: 76484.151 ops/s
Iteration   9: 76282.698 ops/s
Iteration  10: 76463.439 ops/s


Result "runtime.WriteBenchmark.circeJsoniterBridge":
76267.100 ±(99.9%) 363.320 ops/s [Average]


# JMH version: 1.37
# VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS
# VM invoker: /home/runner/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/zulu21.46.19-ca-jdk21.0.9-linux_x64.tar.gz/zulu21.46.19-ca-jdk21.0.9-linux_x64/bin/java
# VM options: -Xms512m -Xmx512m
# Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: runtime.WriteBenchmark.circePrinter

# Run progress: 62.50% complete, ETA 00:01:01
# Fork: 1 of 1
# Warmup Iteration   1: 46260.442 ops/s
# Warmup Iteration   2: 63935.140 ops/s
# Warmup Iteration   3: 62568.549 ops/s
# Warmup Iteration   4: 64955.631 ops/s
# Warmup Iteration   5: 64598.321 ops/s
# Warmup Iteration   6: 65001.485 ops/s
# Warmup Iteration   7: 64372.201 ops/s
# Warmup Iteration   8: 64468.215 ops/s
# Warmup Iteration   9: 63997.410 ops/s
# Warmup Iteration  10: 64784.272 ops/s
Iteration   1: 65194.955 ops/s
Iteration   2: 64825.215 ops/s
Iteration   3: 64881.285 ops/s
Iteration   4: 64375.234 ops/s
Iteration   5: 64572.446 ops/s
Iteration   6: 64747.399 ops/s
Iteration   7: 64760.941 ops/s
Iteration   8: 65166.634 ops/s
Iteration   9: 64372.553 ops/s
Iteration  10: 64543.198 ops/s


Result "runtime.WriteBenchmark.circePrinter":
64743.986 ±(99.9%) 436.853 ops/s [Average]


# JMH version: 1.37
# VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS
# VM invoker: /home/runner/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/zulu21.46.19-ca-jdk21.0.9-linux_x64.tar.gz/zulu21.46.19-ca-jdk21.0.9-linux_x64/bin/java
# VM options: -Xms512m -Xmx512m
# Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: runtime.WriteBenchmark.jsoniterScala

# Run progress: 75.00% complete, ETA 00:00:41
# Fork: 1 of 1
# Warmup Iteration   1: 344777.493 ops/s
# Warmup Iteration   2: 603346.093 ops/s
# Warmup Iteration   3: 605125.720 ops/s
# Warmup Iteration   4: 606723.536 ops/s
# Warmup Iteration   5: 609108.750 ops/s
# Warmup Iteration   6: 610354.121 ops/s
# Warmup Iteration   7: 605606.830 ops/s
# Warmup Iteration   8: 608640.810 ops/s
# Warmup Iteration   9: 602104.242 ops/s
# Warmup Iteration  10: 608872.799 ops/s
Iteration   1: 604556.671 ops/s
Iteration   2: 604201.468 ops/s
Iteration   3: 606543.131 ops/s
Iteration   4: 607264.664 ops/s
Iteration   5: 607297.697 ops/s
Iteration   6: 605561.539 ops/s
Iteration   7: 607984.408 ops/s
Iteration   8: 606388.514 ops/s
Iteration   9: 602714.523 ops/s
Iteration  10: 606963.504 ops/s


Result "runtime.WriteBenchmark.jsoniterScala":
605947.612 ±(99.9%) 2513.335 ops/s [Average]


# JMH version: 1.37
# VM version: JDK 21.0.9, OpenJDK 64-Bit Server VM, 21.0.9+10-LTS
# VM invoker: /home/runner/.cache/coursier/arc/https/cdn.azul.com/zulu/bin/zulu21.46.19-ca-jdk21.0.9-linux_x64.tar.gz/zulu21.46.19-ca-jdk21.0.9-linux_x64/bin/java
# VM options: -Xms512m -Xmx512m
# Blackhole mode: compiler (auto-detected, use -Djmh.blackhole.autoDetect=false to disable)
# Warmup: 10 iterations, 1 s each
# Measurement: 10 iterations, 1 s each
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
# Benchmark: runtime.WriteBenchmark.sanelyJsoniter

# Run progress: 87.50% complete, ETA 00:00:20
# Fork: 1 of 1
# Warmup Iteration   1: 360541.724 ops/s
# Warmup Iteration   2: 528445.322 ops/s
# Warmup Iteration   3: 536263.804 ops/s
# Warmup Iteration   4: 530357.655 ops/s
# Warmup Iteration   5: 531516.101 ops/s
# Warmup Iteration   6: 534449.712 ops/s
# Warmup Iteration   7: 533701.347 ops/s
# Warmup Iteration   8: 532412.401 ops/s
# Warmup Iteration   9: 533361.149 ops/s
# Warmup Iteration  10: 533156.099 ops/s
Iteration   1: 528773.177 ops/s
Iteration   2: 532605.029 ops/s
Iteration   3: 531975.974 ops/s
Iteration   4: 533763.628 ops/s
Iteration   5: 534076.395 ops/s
Iteration   6: 533689.219 ops/s
Iteration   7: 535302.259 ops/s
Iteration   8: 534417.686 ops/s
Iteration   9: 531686.540 ops/s
Iteration  10: 534871.763 ops/s


Result "runtime.WriteBenchmark.sanelyJsoniter":
533116.167 ±(99.9%) 2925.156 ops/s [Average]


# Run complete. Total time: 00:02:44



Benchmark                            Mode  Cnt       Score      Error  Units
ReadBenchmark.circeJawn             thrpt   10   89856.884 ± 1240.638  ops/s
ReadBenchmark.circeJsoniterBridge   thrpt   10  117251.860 ±  832.389  ops/s
ReadBenchmark.jsoniterScala         thrpt   10  387170.000 ± 2154.602  ops/s
ReadBenchmark.sanelyJsoniter        thrpt   10  383542.306 ± 2510.919  ops/s
WriteBenchmark.circeJsoniterBridge  thrpt   10   76267.100 ±  363.320  ops/s
WriteBenchmark.circePrinter         thrpt   10   64743.986 ±  436.853  ops/s
WriteBenchmark.jsoniterScala        thrpt   10  605947.612 ± 2513.335  ops/s
WriteBenchmark.sanelyJsoniter       thrpt   10  533116.167 ± 2925.156  ops/s
```
</details>

<details>
<summary>Peak RSS</summary>

```
sanely-auto (auto): 1032500 KB
circe-generic (auto): 1116052 KB
sanely-auto (configured): 688904 KB
circe-generic (configured): 755596 KB
```
</details>

<details>
<summary>Bytecode Impact</summary>

```
sanely-auto (auto): 2634732 bytes (2573.0 KB)
circe-generic (auto): 3384133 bytes (3304.8 KB)
sanely-auto (configured): 2775933 bytes (2710.9 KB)
circe-generic (configured): 3097489 bytes (3024.9 KB)
```
</details>

<details>
<summary>Macro Profile — Auto</summary>

```
======================================================================
SANELY MACRO PROFILE (398 expansions, 6580ms total)
======================================================================

--- By Kind ---
  Decoder          187 expansions    3162.5ms  avg 16.91ms
  Encoder          211 expansions    3417.1ms  avg 16.19ms

--- Category Breakdown ---
  derive                 2880.1ms ( 43.8%)     920 calls  avg 3.13ms
  summonIgnoring         2851.4ms ( 43.3%)     894 calls  avg 3.19ms
  tryBuiltin              320.5ms (  4.9%)    1943 calls  avg 0.16ms
  summonMirror            268.3ms (  4.1%)     920 calls  avg 0.29ms
  subTraitDetect          166.6ms (  2.5%)     336 calls  avg 0.50ms
  cheapTypeKey             11.6ms (  0.2%)    4342 calls  avg 0.00ms
  builtinHit                0.0ms (  0.0%)     907 calls  avg 0.00ms
  cacheHit                           2399 hits
  constructorNegHit         0.0ms (  0.0%)     142 calls  avg 0.00ms
  overhead                 81.1ms (  1.2%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. Decoder[Article]: total=179.1ms  summonIgnoring=130.4ms(14x) derive=51.6ms(12x) summonMirror=6.2ms(12x) tryBuiltin=4.6ms(18x) subTraitDetect=2.4ms(6x) cheapTypeKey=0.1ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   2. Encoder[Article]: total=168.4ms  summonIgnoring=112.9ms(14x) derive=57.7ms(12x) summonMirror=8.1ms(12x) tryBuiltin=4.9ms(18x) subTraitDetect=3.5ms(6x) cheapTypeKey=0.2ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   3. Decoder[Sprint]: total=154.6ms  summonIgnoring=149.8ms(1x) tryBuiltin=1.0ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   4. Decoder[Ticket]: total=123.6ms  derive=90.5ms(22x) summonIgnoring=78.7ms(27x) summonMirror=6.7ms(22x) subTraitDetect=5.3ms(18x) tryBuiltin=2.6ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   5. Encoder[Sprint]: total=121.5ms  summonIgnoring=115.1ms(1x) tryBuiltin=2.2ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   6. Encoder[Ticket]: total=121.3ms  summonIgnoring=70.9ms(27x) derive=66.1ms(22x) summonMirror=7.8ms(22x) tryBuiltin=6.6ms(31x) subTraitDetect=5.2ms(18x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   7. Encoder[HttpMethod]: total=120.0ms  summonIgnoring=78.4ms(5x) derive=12.9ms(5x) subTraitDetect=5.4ms(5x) summonMirror=3.9ms(5x) tryBuiltin=0.6ms(6x) cheapTypeKey=0.1ms(10x) builtinHit=0.0ms(1x) cacheHit=0.0ms(4x)
   8. Decoder[Ticket]: total=114.1ms  summonIgnoring=67.4ms(27x) derive=62.9ms(22x) tryBuiltin=6.9ms(31x) summonMirror=5.8ms(22x) subTraitDetect=3.4ms(18x) cheapTypeKey=0.7ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   9. Encoder[AlertInstance]: total=110.8ms  derive=159.6ms(19x) summonIgnoring=41.0ms(20x) summonMirror=14.0ms(19x) subTraitDetect=6.9ms(13x) tryBuiltin=1.9ms(26x) cheapTypeKey=0.1ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)
  10. Encoder[OrderSummary]: total=101.5ms  derive=111.6ms(18x) summonIgnoring=26.1ms(18x) summonMirror=10.4ms(18x) subTraitDetect=7.5ms(14x) tryBuiltin=1.1ms(21x) cheapTypeKey=0.1ms(66x) builtinHit=0.0ms(3x) cacheHit=0.0ms(45x)
  11. Decoder[OrderSummary]: total=99.3ms  derive=110.1ms(18x) summonIgnoring=27.7ms(18x) summonMirror=7.4ms(18x) subTraitDetect=5.9ms(14x) tryBuiltin=1.0ms(21x) cheapTypeKey=0.1ms(66x) builtinHit=0.0ms(3x) cacheHit=0.0ms(45x)
  12. Decoder[AlertInstance]: total=96.7ms  derive=143.9ms(19x) summonIgnoring=38.3ms(20x) summonMirror=7.8ms(19x) subTraitDetect=4.4ms(13x) tryBuiltin=1.8ms(26x) cheapTypeKey=0.1ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)
  13. Encoder[ProductInfo]: total=94.9ms  summonIgnoring=69.2ms(6x) derive=11.0ms(4x) tryBuiltin=4.9ms(9x) summonMirror=1.8ms(4x) cheapTypeKey=0.1ms(20x) builtinHit=0.0ms(3x) cacheHit=0.0ms(11x)
  14. Encoder[Ticket]: total=90.5ms  derive=52.1ms(22x) summonIgnoring=47.7ms(27x) summonMirror=5.8ms(22x) subTraitDetect=3.5ms(18x) tryBuiltin=2.3ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
  15. Decoder[MetricType]: total=90.2ms  derive=130.9ms(7x) summonIgnoring=13.9ms(7x) summonMirror=2.5ms(7x) subTraitDetect=1.9ms(4x) tryBuiltin=1.8ms(12x) cheapTypeKey=0.2ms(18x) builtinHit=0.0ms(5x) cacheHit=0.0ms(6x)

--- Hot Types (>50ms) ---
  Decoder[Article]: 179ms
  Encoder[Article]: 168ms
  Decoder[Sprint]: 155ms
  Decoder[Ticket]: 124ms
  Encoder[Sprint]: 122ms
======================================================================
```
</details>

<details>
<summary>Macro Profile — Configured</summary>

```
======================================================================
SANELY MACRO PROFILE (230 expansions, 2329ms total)
======================================================================

--- By Kind ---
  CfgCodec         230 expansions    2329.4ms  avg 10.13ms

--- Category Breakdown ---
  topDerive              2300.3ms ( 98.8%)     230 calls  avg 10.00ms
  tryBuiltin              421.3ms ( 18.1%)     493 calls  avg 0.85ms
  summonIgnoring          221.7ms (  9.5%)     118 calls  avg 1.88ms
  resolveDefaults          31.4ms (  1.3%)     214 calls  avg 0.15ms
  subTraitDetect            8.0ms (  0.3%)      69 calls  avg 0.12ms
  cheapTypeKey              3.8ms (  0.2%)     820 calls  avg 0.00ms
  builtinHit                0.0ms (  0.0%)     375 calls  avg 0.00ms
  cacheHit                            327 hits
  codecHit                  0.0ms (  0.0%)     118 calls  avg 0.00ms
  overhead               -657.1ms (-28.2%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. CfgCodec[Role]: total=71.3ms  topDerive=70.1ms(1x) tryBuiltin=57.7ms(2x) cheapTypeKey=0.4ms(4x) resolveDefaults=0.2ms(1x) builtinHit=0.0ms(2x) cacheHit=0.0ms(2x)
   2. CfgCodec[UserId]: total=67.0ms  topDerive=65.1ms(1x) resolveDefaults=1.7ms(1x) tryBuiltin=0.4ms(1x) cheapTypeKey=0.1ms(1x) builtinHit=0.0ms(1x)
   3. CfgCodec[FunnelReport]: total=57.7ms  topDerive=57.5ms(1x) tryBuiltin=39.8ms(3x) summonIgnoring=3.0ms(1x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(4x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x) codecHit=0.0ms(1x)
   4. CfgCodec[Article]: total=41.9ms  topDerive=41.8ms(1x) tryBuiltin=17.1ms(8x) summonIgnoring=11.1ms(5x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(11x) codecHit=0.0ms(5x) builtinHit=0.0ms(3x) cacheHit=0.0ms(3x)
   5. CfgCodec[NavigationMenu]: total=38.7ms  topDerive=38.6ms(1x) tryBuiltin=8.2ms(2x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(4x) builtinHit=0.0ms(2x) cacheHit=0.0ms(2x)
   6. CfgCodec[Invoice]: total=38.4ms  topDerive=38.1ms(1x) tryBuiltin=20.6ms(6x) summonIgnoring=2.3ms(2x) cheapTypeKey=0.1ms(11x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(4x) cacheHit=0.0ms(5x) codecHit=0.0ms(2x)
   7. CfgCodec[Product]: total=33.9ms  topDerive=33.7ms(1x) tryBuiltin=14.7ms(10x) summonIgnoring=9.3ms(6x) cheapTypeKey=0.1ms(10x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(4x) codecHit=0.0ms(6x)
   8. CfgCodec[CustomerRecord]: total=32.1ms  topDerive=31.8ms(1x) tryBuiltin=11.6ms(7x) summonIgnoring=9.5ms(5x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(8x) codecHit=0.0ms(5x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x)
   9. CfgCodec[ChatMessage]: total=31.1ms  topDerive=30.9ms(1x) tryBuiltin=16.1ms(7x) summonIgnoring=6.4ms(3x) cheapTypeKey=0.1ms(8x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(3x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
  10. CfgCodec[AuthEvent]: total=29.5ms  topDerive=28.9ms(1x) summonIgnoring=7.6ms(5x) subTraitDetect=0.8ms(5x) tryBuiltin=0.5ms(5x) cheapTypeKey=0.0ms(5x) codecHit=0.0ms(5x)
  11. CfgCodec[WorkflowStep]: total=27.2ms  topDerive=27.0ms(1x) tryBuiltin=14.1ms(6x) summonIgnoring=5.1ms(2x) cheapTypeKey=0.1ms(7x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(2x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
  12. CfgCodec[ResourceId]: total=24.9ms  topDerive=24.8ms(1x) resolveDefaults=5.5ms(1x) tryBuiltin=0.3ms(1x) cheapTypeKey=0.0ms(2x) builtinHit=0.0ms(1x) cacheHit=0.0ms(1x)
  13. CfgCodec[WidgetType]: total=24.4ms  topDerive=22.7ms(1x) summonIgnoring=10.7ms(5x) subTraitDetect=0.7ms(5x) tryBuiltin=0.4ms(5x) cheapTypeKey=0.0ms(5x) codecHit=0.0ms(5x)
  14. CfgCodec[CohortAnalysis]: total=23.4ms  topDerive=23.2ms(1x) tryBuiltin=14.0ms(2x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.0ms(4x) builtinHit=0.0ms(2x) cacheHit=0.0ms(2x)
  15. CfgCodec[AccessPolicy]: total=23.2ms  topDerive=23.1ms(1x) tryBuiltin=11.2ms(3x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(5x) builtinHit=0.0ms(3x) cacheHit=0.0ms(2x)

--- Hot Types (>50ms) ---
  CfgCodec[Role]: 71ms
  CfgCodec[UserId]: 67ms
  CfgCodec[FunnelReport]: 58ms
======================================================================
```
</details>


## v0.19.0

**Date:** 2026-03-11 10:24:15 UTC | **SHA:** `4f7c997`

### At a Glance — Compile Time

| Metric | sanely-auto | circe-generic | Delta |
|--------|-------------|---------------|-------|
| Compile (auto, ~300 types) | 6.2s ± 0.14s | 23.0s ± 1.43s | **3.7x faster** |
| Compile (configured, ~230 types) | 3.4s ± 0.13s | 7.9s ± 1.29s | **2.3x faster** |
| Peak RSS (auto) | 773 MB | 1036 MB | **-25%** |
| Peak RSS (configured) | 647 MB | 754 MB | **-14%** |
| Bytecode (auto) | 2.5 MB | 3.2 MB | **-22%** |
| Bytecode (configured) | 2.6 MB | 3.0 MB | **-10%** |

### At a Glance — Runtime

| Benchmark | ops/sec | vs circe | alloc |
|-----------|---------|----------|-------|
| Read: circe+jsoniter | 108k | **1.2x** | 25 KB/op |
| Read: sanely-jsoniter | 380k | **4.3x** | 3 KB/op |
| Read: jsoniter-scala | 365k | **4.1x** | 3 KB/op |
| Write: circe+jsoniter | 70k | **1.2x** | 23 KB/op |
| Write: sanely-jsoniter | 384k | **6.4x** | 1 KB/op |
| Write: jsoniter-scala | 424k | **7.1x** | 1 KB/op |

<details>
<summary>Compile Time — Auto Derivation</summary>

```
Compile-time benchmark: circe-sanely-auto vs circe-generic (N=10)
Benchmark suite: benchmark
Method: Mill daemon, hyperfine with --warmup 1, --runs 10
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark.generic
  Time (mean ± σ):     22.994 s ±  1.431 s    [User: 0.125 s, System: 0.225 s]
  Range (min … max):   21.950 s … 26.548 s    10 runs
 
  Warning: The first benchmarking run for this command was significantly slower than the rest (26.548 s). This could be caused by (filesystem) caches that were not filled until after the first run. You are already using both the '--warmup' option as well as the '--prepare' option. Consider re-running the benchmark on a quiet system. Maybe it was a random outlier. Alternatively, consider increasing the warmup count.
 
Benchmark 2: benchmark.sanely
  Time (mean ± σ):      6.184 s ±  0.138 s    [User: 0.045 s, System: 0.076 s]
  Range (min … max):    5.973 s …  6.362 s    10 runs
 
Summary
  benchmark.sanely ran
    3.72 ± 0.25 times faster than benchmark.generic
```
</details>

<details>
<summary>Compile Time — Configured Derivation</summary>

```
Compile-time benchmark: circe-sanely-auto vs circe-core configured derivation (N=10)
Benchmark suite: benchmark-configured
Method: Mill daemon, hyperfine with --warmup 1, --runs 10
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark-configured.generic
  Time (mean ± σ):      7.908 s ±  1.288 s    [User: 0.061 s, System: 0.115 s]
  Range (min … max):    7.004 s … 11.113 s    10 runs
 
Benchmark 2: benchmark-configured.sanely
  Time (mean ± σ):      3.415 s ±  0.127 s    [User: 0.034 s, System: 0.063 s]
  Range (min … max):    3.265 s …  3.569 s    10 runs
 
Summary
  benchmark-configured.sanely ran
    2.32 ± 0.39 times faster than benchmark-configured.generic
```
</details>

<details>
<summary>Runtime Performance</summary>

```
Building runtime benchmark...

./mill benchmark-runtime.run 10 10
188] benchmark-runtime.run
Runtime benchmark: circe-jawn vs circe+jsoniter-bridge vs sanely-jsoniter vs jsoniter-scala
  warmup=10 iterations=10 (each 1 second)
  payload: 1379 bytes (circe), 1414 bytes (sanely-jsoniter), 1394 bytes (jsoniter-scala)

Reading (bytes -> case class):
----------------------------------------------------------------------
  circe-jawn                      89199 ops/sec  (min=87222, max=89669)  28 KB/op
  circe+jsoniter                 107704 ops/sec  (min=107435, max=107942)  25 KB/op
  sanely-jsoniter                379907 ops/sec  (min=377339, max=382360)  3 KB/op
  jsoniter-scala                 364853 ops/sec  (min=357768, max=366355)  3 KB/op

  circe+jsoniter             1.21x vs circe-jawn  alloc 0.88x
  sanely-jsoniter            4.26x vs circe-jawn  alloc 0.10x
  jsoniter-scala             4.09x vs circe-jawn  alloc 0.09x

Writing (case class -> bytes):
----------------------------------------------------------------------
  circe-printer                   59736 ops/sec  (min=58982, max=60035)  27 KB/op
  circe+jsoniter                  69793 ops/sec  (min=68765, max=70061)  23 KB/op
  sanely-jsoniter                384403 ops/sec  (min=382632, max=385331)  1 KB/op
  jsoniter-scala                 424383 ops/sec  (min=421790, max=425970)  1 KB/op

  circe+jsoniter             1.17x vs circe-printer  alloc 0.85x
  sanely-jsoniter            6.44x vs circe-printer  alloc 0.05x
  jsoniter-scala             7.10x vs circe-printer  alloc 0.05x
188/188, SUCCESS] ./mill benchmark-runtime.run 10 10 161s
```
</details>

<details>
<summary>Peak RSS</summary>

```
sanely-auto (auto): 791704 KB
circe-generic (auto): 1060720 KB
sanely-auto (configured): 662972 KB
circe-generic (configured): 771868 KB
```
</details>

<details>
<summary>Bytecode Impact</summary>

```
sanely-auto (auto): 2634732 bytes (2573.0 KB)
circe-generic (auto): 3384133 bytes (3304.8 KB)
sanely-auto (configured): 2775933 bytes (2710.9 KB)
circe-generic (configured): 3097489 bytes (3024.9 KB)
```
</details>

<details>
<summary>Macro Profile — Auto</summary>

```
======================================================================
SANELY MACRO PROFILE (398 expansions, 7522ms total)
======================================================================

--- By Kind ---
  Decoder          187 expansions    3483.3ms  avg 18.63ms
  Encoder          211 expansions    4038.2ms  avg 19.14ms

--- Category Breakdown ---
  derive                 3370.9ms ( 44.8%)     920 calls  avg 3.66ms
  summonIgnoring         3199.2ms ( 42.5%)     894 calls  avg 3.58ms
  tryBuiltin              399.0ms (  5.3%)    1943 calls  avg 0.21ms
  summonMirror            308.8ms (  4.1%)     920 calls  avg 0.34ms
  subTraitDetect          150.7ms (  2.0%)     336 calls  avg 0.45ms
  cheapTypeKey             12.7ms (  0.2%)    4342 calls  avg 0.00ms
  builtinHit                0.0ms (  0.0%)     907 calls  avg 0.00ms
  cacheHit                           2399 hits
  constructorNegHit         0.0ms (  0.0%)     142 calls  avg 0.00ms
  overhead                 80.2ms (  1.1%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. Encoder[Article]: total=194.3ms  summonIgnoring=130.9ms(14x) derive=61.5ms(12x) summonMirror=6.7ms(12x) subTraitDetect=3.9ms(6x) tryBuiltin=3.7ms(18x) cheapTypeKey=0.1ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   2. Decoder[Article]: total=170.3ms  summonIgnoring=115.6ms(14x) derive=57.5ms(12x) tryBuiltin=4.9ms(18x) summonMirror=4.9ms(12x) subTraitDetect=2.3ms(6x) cheapTypeKey=0.2ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   3. Encoder[Sprint]: total=165.3ms  summonIgnoring=156.4ms(1x) tryBuiltin=3.1ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   4. Encoder[Ticket]: total=162.2ms  summonIgnoring=93.5ms(27x) derive=93.0ms(22x) summonMirror=10.0ms(22x) tryBuiltin=9.8ms(31x) subTraitDetect=7.5ms(18x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   5. Decoder[Ticket]: total=161.6ms  summonIgnoring=93.8ms(27x) derive=93.4ms(22x) tryBuiltin=10.2ms(31x) summonMirror=7.5ms(22x) subTraitDetect=5.5ms(18x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   6. Decoder[Sprint]: total=160.7ms  summonIgnoring=153.6ms(1x) tryBuiltin=1.4ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   7. Encoder[Ticket]: total=116.6ms  derive=67.0ms(22x) summonIgnoring=61.7ms(27x) summonMirror=6.9ms(22x) subTraitDetect=5.1ms(18x) tryBuiltin=3.1ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   8. Decoder[Ticket]: total=115.3ms  derive=72.2ms(22x) summonIgnoring=57.1ms(27x) summonMirror=7.7ms(22x) subTraitDetect=5.2ms(18x) tryBuiltin=3.3ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   9. Encoder[AnalyticsView]: total=112.4ms  derive=83.6ms(12x) summonIgnoring=78.5ms(11x) tryBuiltin=7.8ms(21x) summonMirror=3.9ms(12x) cheapTypeKey=0.2ms(51x) builtinHit=0.0ms(3x) cacheHit=0.0ms(30x) constructorNegHit=0.0ms(7x)
  10. Encoder[SystemSnapshot]: total=112.4ms  derive=108.7ms(17x) summonIgnoring=28.2ms(10x) summonMirror=8.7ms(17x) tryBuiltin=7.0ms(22x) cheapTypeKey=0.3ms(50x) builtinHit=0.0ms(5x) cacheHit=0.0ms(28x) constructorNegHit=0.0ms(7x)
  11. Encoder[FullDashboard]: total=112.1ms  summonIgnoring=65.2ms(18x) derive=56.3ms(22x) tryBuiltin=7.6ms(30x) summonMirror=4.4ms(22x) cheapTypeKey=0.3ms(71x) builtinHit=0.0ms(6x) cacheHit=0.0ms(41x) constructorNegHit=0.0ms(6x)
  12. Decoder[AlertInstance]: total=105.3ms  derive=159.0ms(19x) summonIgnoring=39.6ms(20x) summonMirror=8.2ms(19x) subTraitDetect=4.5ms(13x) tryBuiltin=1.8ms(26x) cheapTypeKey=0.1ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)
  13. Encoder[AlertInstance]: total=102.3ms  derive=149.6ms(19x) summonIgnoring=38.2ms(20x) summonMirror=10.6ms(19x) subTraitDetect=6.6ms(13x) tryBuiltin=1.9ms(26x) cheapTypeKey=0.1ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)
  14. Encoder[UserReport]: total=101.2ms  derive=73.5ms(6x) summonIgnoring=69.4ms(7x) tryBuiltin=13.2ms(13x) summonMirror=2.3ms(6x) cheapTypeKey=0.2ms(36x) builtinHit=0.0ms(1x) cacheHit=0.0ms(23x) constructorNegHit=0.0ms(5x)
  15. Decoder[UserReport]: total=98.1ms  derive=78.9ms(6x) summonIgnoring=65.4ms(7x) tryBuiltin=14.4ms(13x) summonMirror=1.5ms(6x) cheapTypeKey=0.2ms(36x) builtinHit=0.0ms(1x) cacheHit=0.0ms(23x) constructorNegHit=0.0ms(5x)

--- Hot Types (>50ms) ---
  Encoder[Article]: 194ms
  Decoder[Article]: 170ms
  Encoder[Sprint]: 165ms
  Encoder[Ticket]: 162ms
  Decoder[Ticket]: 162ms
======================================================================
```
</details>

<details>
<summary>Macro Profile — Configured</summary>

```
======================================================================
SANELY MACRO PROFILE (230 expansions, 2294ms total)
======================================================================

--- By Kind ---
  CfgCodec         230 expansions    2294.1ms  avg 9.97ms

--- Category Breakdown ---
  topDerive              2263.8ms ( 98.7%)     230 calls  avg 9.84ms
  tryBuiltin              403.6ms ( 17.6%)     493 calls  avg 0.82ms
  summonIgnoring          218.1ms (  9.5%)     118 calls  avg 1.85ms
  resolveDefaults          27.6ms (  1.2%)     214 calls  avg 0.13ms
  subTraitDetect            7.0ms (  0.3%)      69 calls  avg 0.10ms
  cheapTypeKey              3.6ms (  0.2%)     820 calls  avg 0.00ms
  builtinHit                0.0ms (  0.0%)     375 calls  avg 0.00ms
  cacheHit                            327 hits
  codecHit                  0.0ms (  0.0%)     118 calls  avg 0.00ms
  overhead               -629.6ms (-27.4%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. CfgCodec[Role]: total=105.9ms  topDerive=103.4ms(1x) tryBuiltin=87.4ms(2x) cheapTypeKey=0.4ms(4x) resolveDefaults=0.2ms(1x) builtinHit=0.0ms(2x) cacheHit=0.0ms(2x)
   2. CfgCodec[UserId]: total=64.8ms  topDerive=62.8ms(1x) resolveDefaults=1.7ms(1x) tryBuiltin=0.5ms(1x) cheapTypeKey=0.1ms(1x) builtinHit=0.0ms(1x)
   3. CfgCodec[GroupId]: total=45.4ms  topDerive=45.3ms(1x) tryBuiltin=0.3ms(1x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(1x) builtinHit=0.0ms(1x)
   4. CfgCodec[Invoice]: total=43.5ms  topDerive=43.2ms(1x) tryBuiltin=24.5ms(6x) summonIgnoring=2.4ms(2x) cheapTypeKey=0.1ms(11x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(4x) cacheHit=0.0ms(5x) codecHit=0.0ms(2x)
   5. CfgCodec[Article]: total=42.3ms  topDerive=42.0ms(1x) tryBuiltin=17.6ms(8x) summonIgnoring=10.5ms(5x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(11x) codecHit=0.0ms(5x) builtinHit=0.0ms(3x) cacheHit=0.0ms(3x)
   6. CfgCodec[Refund]: total=37.5ms  topDerive=37.3ms(1x) summonIgnoring=0.5ms(1x) tryBuiltin=0.3ms(2x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.0ms(5x) builtinHit=0.0ms(1x) cacheHit=0.0ms(3x) codecHit=0.0ms(1x)
   7. CfgCodec[ChatMessage]: total=33.6ms  topDerive=33.4ms(1x) tryBuiltin=16.9ms(7x) summonIgnoring=6.7ms(3x) cheapTypeKey=0.1ms(8x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(3x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
   8. CfgCodec[AuthEvent]: total=33.2ms  topDerive=32.6ms(1x) summonIgnoring=8.0ms(5x) subTraitDetect=0.7ms(5x) tryBuiltin=0.6ms(5x) cheapTypeKey=0.0ms(5x) codecHit=0.0ms(5x)
   9. CfgCodec[Product]: total=33.0ms  topDerive=32.8ms(1x) tryBuiltin=14.1ms(10x) summonIgnoring=8.6ms(6x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(10x) builtinHit=0.0ms(4x) codecHit=0.0ms(6x)
  10. CfgCodec[ActivityType]: total=30.7ms  topDerive=30.6ms(1x) summonIgnoring=25.1ms(4x) subTraitDetect=0.4ms(4x) tryBuiltin=0.2ms(4x) cheapTypeKey=0.0ms(4x) codecHit=0.0ms(4x)
  11. CfgCodec[DunningRecord]: total=30.5ms  topDerive=30.4ms(1x) tryBuiltin=18.3ms(3x) resolveDefaults=1.7ms(1x) cheapTypeKey=0.0ms(4x) builtinHit=0.0ms(3x) cacheHit=0.0ms(1x)
  12. CfgCodec[AccessPolicy]: total=26.8ms  topDerive=26.6ms(1x) tryBuiltin=13.2ms(3x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(5x) builtinHit=0.0ms(3x) cacheHit=0.0ms(2x)
  13. CfgCodec[WorkflowStep]: total=25.5ms  topDerive=25.4ms(1x) tryBuiltin=14.0ms(6x) summonIgnoring=4.2ms(2x) cheapTypeKey=0.1ms(7x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(2x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
  14. CfgCodec[SecurityConfig]: total=22.5ms  topDerive=22.3ms(1x) tryBuiltin=12.1ms(3x) resolveDefaults=0.3ms(1x) cheapTypeKey=0.1ms(4x) builtinHit=0.0ms(3x) cacheHit=0.0ms(1x)
  15. CfgCodec[WorkflowDefinition]: total=22.3ms  topDerive=22.1ms(1x) tryBuiltin=10.3ms(5x) summonIgnoring=4.8ms(2x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(6x) codecHit=0.0ms(2x) builtinHit=0.0ms(3x) cacheHit=0.0ms(1x)

--- Hot Types (>50ms) ---
  CfgCodec[Role]: 106ms
  CfgCodec[UserId]: 65ms
======================================================================
```
</details>


## v0.18.0

**Date:** 2026-03-08 08:53:46 UTC | **SHA:** `52d14f3`

### At a Glance — Compile Time

| Metric | sanely-auto | circe-generic | Delta |
|--------|-------------|---------------|-------|
| Compile (auto, ~300 types) | 6.6s ± 0.15s | 24.4s ± 2.48s | **3.7x faster** |
| Compile (configured, ~230 types) | 3.5s ± 0.16s | 7.9s ± 1.36s | **2.2x faster** |
| Peak RSS (auto) | 820 MB | 1067 MB | **-23%** |
| Peak RSS (configured) | 654 MB | 750 MB | **-13%** |
| Bytecode (auto) | 2.5 MB | 3.2 MB | **-22%** |
| Bytecode (configured) | 2.6 MB | 3.0 MB | **-10%** |

### At a Glance — Runtime

| Benchmark | ops/sec | vs circe | alloc |
|-----------|---------|----------|-------|
| Read: circe+jsoniter | 113k | **1.3x** | 25 KB/op |
| Read: sanely-jsoniter | 385k | **4.3x** | 3 KB/op |
| Read: jsoniter-scala | 367k | **4.1x** | 3 KB/op |
| Write: circe+jsoniter | 71k | **1.2x** | 22 KB/op |
| Write: sanely-jsoniter | 384k | **6.5x** | 1 KB/op |
| Write: jsoniter-scala | 434k | **7.3x** | 1 KB/op |

<details>
<summary>Compile Time — Auto Derivation</summary>

```
Compile-time benchmark: circe-sanely-auto vs circe-generic (N=10)
Benchmark suite: benchmark
Method: Mill daemon, hyperfine with --warmup 1, --runs 10
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark.generic
  Time (mean ± σ):     24.447 s ±  2.480 s    [User: 0.155 s, System: 0.306 s]
  Range (min … max):   22.997 s … 31.103 s    10 runs
 
  Warning: The first benchmarking run for this command was significantly slower than the rest (31.103 s). This could be caused by (filesystem) caches that were not filled until after the first run. You are already using both the '--warmup' option as well as the '--prepare' option. Consider re-running the benchmark on a quiet system. Maybe it was a random outlier. Alternatively, consider increasing the warmup count.
 
Benchmark 2: benchmark.sanely
  Time (mean ± σ):      6.611 s ±  0.147 s    [User: 0.049 s, System: 0.104 s]
  Range (min … max):    6.470 s …  6.905 s    10 runs
 
Summary
  benchmark.sanely ran
    3.70 ± 0.38 times faster than benchmark.generic
```
</details>

<details>
<summary>Compile Time — Configured Derivation</summary>

```
Compile-time benchmark: circe-sanely-auto vs circe-core configured derivation (N=10)
Benchmark suite: benchmark-configured
Method: Mill daemon, hyperfine with --warmup 1, --runs 10
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark-configured.generic
  Time (mean ± σ):      7.875 s ±  1.360 s    [User: 0.062 s, System: 0.114 s]
  Range (min … max):    6.979 s … 11.429 s    10 runs
 
Benchmark 2: benchmark-configured.sanely
  Time (mean ± σ):      3.530 s ±  0.161 s    [User: 0.037 s, System: 0.066 s]
  Range (min … max):    3.214 s …  3.747 s    10 runs
 
Summary
  benchmark-configured.sanely ran
    2.23 ± 0.40 times faster than benchmark-configured.generic
```
</details>

<details>
<summary>Runtime Performance</summary>

```
Building runtime benchmark...

./mill benchmark-runtime.run 10 10
188] benchmark-runtime.run
Runtime benchmark: circe-jawn vs circe+jsoniter-bridge vs sanely-jsoniter vs jsoniter-scala
  warmup=10 iterations=10 (each 1 second)
  payload: 1379 bytes (circe), 1414 bytes (sanely-jsoniter), 1394 bytes (jsoniter-scala)

Reading (bytes -> case class):
----------------------------------------------------------------------
  circe-jawn                      89053 ops/sec  (min=87838, max=89129)  28 KB/op
  circe+jsoniter                 113181 ops/sec  (min=111031, max=113558)  25 KB/op
  sanely-jsoniter                384737 ops/sec  (min=383431, max=385937)  3 KB/op
  jsoniter-scala                 367379 ops/sec  (min=363775, max=369014)  3 KB/op

  circe+jsoniter             1.27x vs circe-jawn  alloc 0.88x
  sanely-jsoniter            4.32x vs circe-jawn  alloc 0.10x
  jsoniter-scala             4.13x vs circe-jawn  alloc 0.10x

Writing (case class -> bytes):
----------------------------------------------------------------------
  circe-printer                   59477 ops/sec  (min=55634, max=59733)  26 KB/op
  circe+jsoniter                  70585 ops/sec  (min=65785, max=71173)  22 KB/op
  sanely-jsoniter                383598 ops/sec  (min=381955, max=385345)  1 KB/op
  jsoniter-scala                 434258 ops/sec  (min=432865, max=437413)  1 KB/op

  circe+jsoniter             1.19x vs circe-printer  alloc 0.85x
  sanely-jsoniter            6.45x vs circe-printer  alloc 0.05x
  jsoniter-scala             7.30x vs circe-printer  alloc 0.05x
188/188, SUCCESS] ./mill benchmark-runtime.run 10 10 161s
```
</details>

<details>
<summary>Peak RSS</summary>

```
sanely-auto (auto): 839332 KB
circe-generic (auto): 1092440 KB
sanely-auto (configured): 669948 KB
circe-generic (configured): 767860 KB
```
</details>

<details>
<summary>Bytecode Impact</summary>

```
sanely-auto (auto): 2634732 bytes (2573.0 KB)
circe-generic (auto): 3384133 bytes (3304.8 KB)
sanely-auto (configured): 2775933 bytes (2710.9 KB)
circe-generic (configured): 3097489 bytes (3024.9 KB)
```
</details>

<details>
<summary>Macro Profile — Auto</summary>

```
======================================================================
SANELY MACRO PROFILE (398 expansions, 7290ms total)
======================================================================

--- By Kind ---
  Decoder          187 expansions    3387.1ms  avg 18.11ms
  Encoder          211 expansions    3903.0ms  avg 18.50ms

--- Category Breakdown ---
  summonIgnoring         3205.0ms ( 44.0%)     894 calls  avg 3.59ms
  derive                 3106.1ms ( 42.6%)     920 calls  avg 3.38ms
  tryBuiltin              395.3ms (  5.4%)    1943 calls  avg 0.20ms
  summonMirror            349.8ms (  4.8%)     920 calls  avg 0.38ms
  subTraitDetect          154.3ms (  2.1%)     336 calls  avg 0.46ms
  cheapTypeKey             13.4ms (  0.2%)    4342 calls  avg 0.00ms
  builtinHit                0.0ms (  0.0%)     907 calls  avg 0.00ms
  cacheHit                           2399 hits
  constructorNegHit         0.0ms (  0.0%)     142 calls  avg 0.00ms
  overhead                 66.2ms (  0.9%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. Encoder[Article]: total=212.9ms  summonIgnoring=160.8ms(14x) derive=50.5ms(12x) summonMirror=9.4ms(12x) tryBuiltin=5.5ms(18x) subTraitDetect=3.2ms(6x) cheapTypeKey=0.2ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   2. Encoder[Ticket]: total=159.1ms  summonIgnoring=92.6ms(27x) derive=86.6ms(22x) summonMirror=10.3ms(22x) tryBuiltin=10.1ms(31x) subTraitDetect=7.5ms(18x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   3. Decoder[Article]: total=153.8ms  summonIgnoring=107.9ms(14x) derive=48.7ms(12x) summonMirror=4.9ms(12x) tryBuiltin=4.6ms(18x) subTraitDetect=2.2ms(6x) cheapTypeKey=0.2ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   4. Encoder[Sprint]: total=151.5ms  summonIgnoring=143.1ms(1x) tryBuiltin=2.9ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   5. Decoder[Ticket]: total=151.3ms  derive=89.3ms(22x) summonIgnoring=87.3ms(27x) tryBuiltin=9.5ms(31x) summonMirror=7.6ms(22x) subTraitDetect=5.3ms(18x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   6. Decoder[Sprint]: total=146.4ms  summonIgnoring=139.7ms(1x) tryBuiltin=1.3ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   7. Encoder[AnalyticsView]: total=119.9ms  derive=88.3ms(12x) summonIgnoring=86.2ms(11x) tryBuiltin=9.0ms(21x) summonMirror=3.7ms(12x) cheapTypeKey=0.2ms(51x) builtinHit=0.0ms(3x) cacheHit=0.0ms(30x) constructorNegHit=0.0ms(7x)
   8. Encoder[HttpMethod]: total=115.5ms  summonIgnoring=72.5ms(5x) derive=13.2ms(5x) subTraitDetect=5.8ms(5x) summonMirror=3.9ms(5x) tryBuiltin=0.6ms(6x) cheapTypeKey=0.1ms(10x) builtinHit=0.0ms(1x) cacheHit=0.0ms(4x)
   9. Encoder[Ticket]: total=109.2ms  derive=66.3ms(22x) summonIgnoring=54.8ms(27x) summonMirror=7.2ms(22x) subTraitDetect=4.9ms(18x) tryBuiltin=3.1ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
  10. Encoder[MixedReport]: total=107.6ms  derive=70.2ms(21x) summonIgnoring=57.5ms(16x) tryBuiltin=6.6ms(29x) summonMirror=4.4ms(21x) cheapTypeKey=0.3ms(75x) builtinHit=0.0ms(5x) cacheHit=0.0ms(46x) constructorNegHit=0.0ms(8x)
  11. Decoder[Ticket]: total=104.5ms  derive=63.1ms(22x) summonIgnoring=52.2ms(27x) summonMirror=9.7ms(22x) subTraitDetect=4.4ms(18x) tryBuiltin=2.9ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
  12. Encoder[OrderSummary]: total=102.5ms  derive=108.3ms(18x) summonIgnoring=28.0ms(18x) summonMirror=13.2ms(18x) subTraitDetect=7.6ms(14x) tryBuiltin=1.3ms(21x) cheapTypeKey=0.1ms(66x) builtinHit=0.0ms(3x) cacheHit=0.0ms(45x)
  13. Encoder[AlertInstance]: total=100.8ms  derive=146.0ms(19x) summonIgnoring=38.6ms(20x) summonMirror=10.8ms(19x) subTraitDetect=6.5ms(13x) tryBuiltin=1.9ms(26x) cheapTypeKey=0.1ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)
  14. Encoder[UserReport]: total=96.8ms  derive=70.2ms(6x) summonIgnoring=67.3ms(7x) tryBuiltin=13.3ms(13x) summonMirror=2.3ms(6x) cheapTypeKey=0.1ms(36x) builtinHit=0.0ms(1x) cacheHit=0.0ms(23x) constructorNegHit=0.0ms(5x)
  15. Encoder[ProductInfo]: total=96.5ms  summonIgnoring=70.5ms(6x) derive=10.2ms(4x) tryBuiltin=5.8ms(9x) summonMirror=2.0ms(4x) cheapTypeKey=0.1ms(20x) builtinHit=0.0ms(3x) cacheHit=0.0ms(11x)

--- Hot Types (>50ms) ---
  Encoder[Article]: 213ms
  Encoder[Ticket]: 159ms
  Decoder[Article]: 154ms
  Encoder[Sprint]: 152ms
  Decoder[Ticket]: 151ms
======================================================================
```
</details>

<details>
<summary>Macro Profile — Configured</summary>

```
======================================================================
SANELY MACRO PROFILE (230 expansions, 2276ms total)
======================================================================

--- By Kind ---
  CfgCodec         230 expansions    2276.3ms  avg 9.90ms

--- Category Breakdown ---
  topDerive              2249.4ms ( 98.8%)     230 calls  avg 9.78ms
  tryBuiltin              426.1ms ( 18.7%)     493 calls  avg 0.86ms
  summonIgnoring          219.3ms (  9.6%)     118 calls  avg 1.86ms
  resolveDefaults          24.7ms (  1.1%)     214 calls  avg 0.12ms
  subTraitDetect            7.2ms (  0.3%)      69 calls  avg 0.10ms
  cheapTypeKey              2.6ms (  0.1%)     820 calls  avg 0.00ms
  builtinHit                0.0ms (  0.0%)     375 calls  avg 0.00ms
  cacheHit                            327 hits
  codecHit                  0.0ms (  0.0%)     118 calls  avg 0.00ms
  overhead               -653.0ms (-28.7%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. CfgCodec[Role]: total=110.5ms  topDerive=109.2ms(1x) tryBuiltin=97.7ms(2x) cheapTypeKey=0.5ms(4x) resolveDefaults=0.2ms(1x) builtinHit=0.0ms(2x) cacheHit=0.0ms(2x)
   2. CfgCodec[UserId]: total=62.3ms  topDerive=60.4ms(1x) resolveDefaults=1.8ms(1x) tryBuiltin=0.4ms(1x) cheapTypeKey=0.1ms(1x) builtinHit=0.0ms(1x)
   3. CfgCodec[Invoice]: total=39.6ms  topDerive=39.4ms(1x) tryBuiltin=20.8ms(6x) summonIgnoring=2.1ms(2x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.0ms(11x) builtinHit=0.0ms(4x) cacheHit=0.0ms(5x) codecHit=0.0ms(2x)
   4. CfgCodec[Article]: total=38.8ms  topDerive=38.7ms(1x) tryBuiltin=15.0ms(8x) summonIgnoring=10.5ms(5x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(11x) codecHit=0.0ms(5x) builtinHit=0.0ms(3x) cacheHit=0.0ms(3x)
   5. CfgCodec[AuthEvent]: total=35.3ms  topDerive=34.5ms(1x) summonIgnoring=9.9ms(5x) tryBuiltin=0.7ms(5x) subTraitDetect=0.7ms(5x) cheapTypeKey=0.0ms(5x) codecHit=0.0ms(5x)
   6. CfgCodec[Product]: total=32.9ms  topDerive=32.7ms(1x) tryBuiltin=13.4ms(10x) summonIgnoring=8.4ms(6x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(10x) builtinHit=0.0ms(4x) codecHit=0.0ms(6x)
   7. CfgCodec[ChatMessage]: total=31.5ms  topDerive=31.3ms(1x) tryBuiltin=16.8ms(7x) summonIgnoring=6.0ms(3x) cheapTypeKey=0.1ms(8x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(3x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
   8. CfgCodec[ChurnedCustomer]: total=30.7ms  topDerive=30.7ms(1x) tryBuiltin=0.6ms(1x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.0ms(2x) builtinHit=0.0ms(1x) cacheHit=0.0ms(1x)
   9. CfgCodec[Report]: total=27.8ms  topDerive=27.5ms(1x) tryBuiltin=12.4ms(4x) summonIgnoring=3.4ms(1x) cheapTypeKey=0.1ms(6x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(3x) cacheHit=0.0ms(2x) codecHit=0.0ms(1x)
  10. CfgCodec[Subscription]: total=27.3ms  topDerive=27.1ms(1x) summonIgnoring=3.3ms(1x) tryBuiltin=0.3ms(2x) cheapTypeKey=0.1ms(5x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(1x) cacheHit=0.0ms(3x) codecHit=0.0ms(1x)
  11. CfgCodec[CustomerRecord]: total=27.3ms  topDerive=27.1ms(1x) tryBuiltin=9.6ms(7x) summonIgnoring=8.6ms(5x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(8x) codecHit=0.0ms(5x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x)
  12. CfgCodec[AccessPolicy]: total=24.5ms  topDerive=24.4ms(1x) tryBuiltin=12.5ms(3x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(5x) builtinHit=0.0ms(3x) cacheHit=0.0ms(2x)
  13. CfgCodec[WorkflowStep]: total=24.2ms  topDerive=24.1ms(1x) tryBuiltin=12.9ms(6x) summonIgnoring=4.6ms(2x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(7x) codecHit=0.0ms(2x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
  14. CfgCodec[MetricSeries]: total=23.8ms  topDerive=23.7ms(1x) tryBuiltin=12.4ms(4x) summonIgnoring=3.3ms(1x) cheapTypeKey=0.1ms(4x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(1x) builtinHit=0.0ms(3x)
  15. CfgCodec[DunningRecord]: total=22.3ms  topDerive=22.2ms(1x) tryBuiltin=14.5ms(3x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(4x) builtinHit=0.0ms(3x) cacheHit=0.0ms(1x)

--- Hot Types (>50ms) ---
  CfgCodec[Role]: 110ms
  CfgCodec[UserId]: 62ms
======================================================================
```
</details>


## v0.17.0

**Date:** 2026-03-08 06:37:52 UTC | **SHA:** `3d4b020`

### At a Glance — Compile Time

| Metric | sanely-auto | circe-generic | Delta |
|--------|-------------|---------------|-------|
| Compile (auto, ~300 types) | 6.8s ± 0.17s | 24.7s ± 2.13s | **3.6x faster** |
| Compile (configured, ~230 types) | 3.5s ± 0.16s | 7.7s ± 1.08s | **2.2x faster** |
| Peak RSS (auto) | 786 MB | 975 MB | **-19%** |
| Peak RSS (configured) | 683 MB | 762 MB | **-10%** |
| Bytecode (auto) | 2.5 MB | 3.2 MB | **-22%** |
| Bytecode (configured) | 2.6 MB | 3.0 MB | **-10%** |

### At a Glance — Runtime

| Benchmark | ops/sec | vs circe | alloc |
|-----------|---------|----------|-------|
| Read: circe+jsoniter | 112k | **1.2x** | 25 KB/op |
| Read: sanely-jsoniter | 380k | **4.2x** | 3 KB/op |
| Read: jsoniter-scala | 355k | **4.0x** | 3 KB/op |
| Write: circe+jsoniter | 68k | **1.2x** | 23 KB/op |
| Write: sanely-jsoniter | 378k | **6.4x** | 1 KB/op |
| Write: jsoniter-scala | 423k | **7.2x** | 1 KB/op |

<details>
<summary>Compile Time — Auto Derivation</summary>

```
Compile-time benchmark: circe-sanely-auto vs circe-generic (N=10)
Benchmark suite: benchmark
Method: Mill daemon, hyperfine with --warmup 1, --runs 10
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark.generic
  Time (mean ± σ):     24.680 s ±  2.131 s    [User: 0.150 s, System: 0.316 s]
  Range (min … max):   23.414 s … 30.334 s    10 runs
 
  Warning: The first benchmarking run for this command was significantly slower than the rest (30.334 s). This could be caused by (filesystem) caches that were not filled until after the first run. You are already using both the '--warmup' option as well as the '--prepare' option. Consider re-running the benchmark on a quiet system. Maybe it was a random outlier. Alternatively, consider increasing the warmup count.
 
Benchmark 2: benchmark.sanely
  Time (mean ± σ):      6.787 s ±  0.170 s    [User: 0.052 s, System: 0.103 s]
  Range (min … max):    6.590 s …  7.195 s    10 runs
 
Summary
  benchmark.sanely ran
    3.64 ± 0.33 times faster than benchmark.generic
```
</details>

<details>
<summary>Compile Time — Configured Derivation</summary>

```
Compile-time benchmark: circe-sanely-auto vs circe-core configured derivation (N=10)
Benchmark suite: benchmark-configured
Method: Mill daemon, hyperfine with --warmup 1, --runs 10
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark-configured.generic
  Time (mean ± σ):      7.685 s ±  1.085 s    [User: 0.057 s, System: 0.115 s]
  Range (min … max):    6.749 s … 10.283 s    10 runs
 
Benchmark 2: benchmark-configured.sanely
  Time (mean ± σ):      3.506 s ±  0.157 s    [User: 0.038 s, System: 0.065 s]
  Range (min … max):    3.250 s …  3.756 s    10 runs
 
Summary
  benchmark-configured.sanely ran
    2.19 ± 0.32 times faster than benchmark-configured.generic
```
</details>

<details>
<summary>Runtime Performance</summary>

```
Building runtime benchmark...

./mill benchmark-runtime.run 10 10
188] benchmark-runtime.run
Runtime benchmark: circe-jawn vs circe+jsoniter-bridge vs sanely-jsoniter vs jsoniter-scala
  warmup=10 iterations=10 (each 1 second)
  payload: 1379 bytes (circe), 1414 bytes (sanely-jsoniter), 1394 bytes (jsoniter-scala)

Reading (bytes -> case class):
----------------------------------------------------------------------
  circe-jawn                      89754 ops/sec  (min=87798, max=90300)  28 KB/op
  circe+jsoniter                 112163 ops/sec  (min=111471, max=112707)  25 KB/op
  sanely-jsoniter                379602 ops/sec  (min=377158, max=381332)  3 KB/op
  jsoniter-scala                 354647 ops/sec  (min=349368, max=355612)  3 KB/op

  circe+jsoniter             1.25x vs circe-jawn  alloc 0.88x
  sanely-jsoniter            4.23x vs circe-jawn  alloc 0.10x
  jsoniter-scala             3.95x vs circe-jawn  alloc 0.10x

Writing (case class -> bytes):
----------------------------------------------------------------------
  circe-printer                   58840 ops/sec  (min=56491, max=59308)  27 KB/op
  circe+jsoniter                  68112 ops/sec  (min=67913, max=68918)  23 KB/op
  sanely-jsoniter                377962 ops/sec  (min=372946, max=378729)  1 KB/op
  jsoniter-scala                 423474 ops/sec  (min=415698, max=425588)  1 KB/op

  circe+jsoniter             1.16x vs circe-printer  alloc 0.85x
  sanely-jsoniter            6.42x vs circe-printer  alloc 0.05x
  jsoniter-scala             7.20x vs circe-printer  alloc 0.05x
188/188, SUCCESS] ./mill benchmark-runtime.run 10 10 161s
```
</details>

<details>
<summary>Peak RSS</summary>

```
sanely-auto (auto): 804844 KB
circe-generic (auto): 998008 KB
sanely-auto (configured): 699256 KB
circe-generic (configured): 780464 KB
```
</details>

<details>
<summary>Bytecode Impact</summary>

```
sanely-auto (auto): 2634732 bytes (2573.0 KB)
circe-generic (auto): 3384133 bytes (3304.8 KB)
sanely-auto (configured): 2775933 bytes (2710.9 KB)
circe-generic (configured): 3097489 bytes (3024.9 KB)
```
</details>

<details>
<summary>Macro Profile — Auto</summary>

```
======================================================================
SANELY MACRO PROFILE (398 expansions, 6738ms total)
======================================================================

--- By Kind ---
  Decoder          187 expansions    3202.2ms  avg 17.12ms
  Encoder          211 expansions    3535.8ms  avg 16.76ms

--- Category Breakdown ---
  summonIgnoring         2891.5ms ( 42.9%)     894 calls  avg 3.23ms
  derive                 2842.9ms ( 42.2%)     920 calls  avg 3.09ms
  tryBuiltin              356.6ms (  5.3%)    1943 calls  avg 0.18ms
  summonMirror            267.2ms (  4.0%)     920 calls  avg 0.29ms
  subTraitDetect          145.3ms (  2.2%)     336 calls  avg 0.43ms
  cheapTypeKey             11.6ms (  0.2%)    4342 calls  avg 0.00ms
  builtinHit                0.0ms (  0.0%)     907 calls  avg 0.00ms
  cacheHit                           2399 hits
  constructorNegHit         0.0ms (  0.0%)     142 calls  avg 0.00ms
  overhead                222.9ms (  3.3%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. Decoder[Article]: total=209.1ms  summonIgnoring=165.8ms(14x) derive=46.6ms(12x) tryBuiltin=4.7ms(18x) summonMirror=4.4ms(12x) subTraitDetect=2.0ms(6x) cheapTypeKey=0.2ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   2. Encoder[Article]: total=167.5ms  summonIgnoring=119.7ms(14x) derive=48.1ms(12x) summonMirror=6.4ms(12x) tryBuiltin=5.0ms(18x) subTraitDetect=2.8ms(6x) cheapTypeKey=0.2ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   3. Encoder[Ticket]: total=152.9ms  summonIgnoring=89.6ms(27x) derive=85.4ms(22x) tryBuiltin=9.7ms(31x) summonMirror=9.6ms(22x) subTraitDetect=6.8ms(18x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   4. Decoder[Ticket]: total=146.2ms  summonIgnoring=86.7ms(27x) derive=80.7ms(22x) tryBuiltin=9.3ms(31x) summonMirror=7.1ms(22x) subTraitDetect=5.1ms(18x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   5. Encoder[Sprint]: total=143.1ms  summonIgnoring=134.5ms(1x) tryBuiltin=2.8ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   6. Decoder[Sprint]: total=140.2ms  summonIgnoring=133.8ms(1x) tryBuiltin=1.2ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   7. Encoder[Ticket]: total=103.8ms  derive=59.1ms(22x) summonIgnoring=55.8ms(27x) summonMirror=6.7ms(22x) subTraitDetect=4.4ms(18x) tryBuiltin=3.0ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   8. Decoder[Ticket]: total=103.0ms  derive=64.1ms(22x) summonIgnoring=51.2ms(27x) summonMirror=6.7ms(22x) subTraitDetect=4.9ms(18x) tryBuiltin=2.8ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   9. Encoder[HttpMethod]: total=101.3ms  summonIgnoring=59.5ms(5x) derive=15.3ms(5x) subTraitDetect=5.0ms(5x) summonMirror=4.5ms(5x) tryBuiltin=0.6ms(6x) cheapTypeKey=0.1ms(10x) builtinHit=0.0ms(1x) cacheHit=0.0ms(4x)
  10. Encoder[ProductInfo]: total=97.6ms  summonIgnoring=71.5ms(6x) derive=11.5ms(4x) tryBuiltin=5.2ms(9x) summonMirror=2.1ms(4x) cheapTypeKey=0.1ms(20x) builtinHit=0.0ms(3x) cacheHit=0.0ms(11x)
  11. Encoder[OrderSummary]: total=97.6ms  derive=109.2ms(18x) summonIgnoring=25.3ms(18x) summonMirror=11.5ms(18x) subTraitDetect=7.3ms(14x) tryBuiltin=1.1ms(21x) cheapTypeKey=0.1ms(66x) builtinHit=0.0ms(3x) cacheHit=0.0ms(45x)
  12. Encoder[AlertInstance]: total=94.9ms  derive=139.2ms(19x) summonIgnoring=37.2ms(20x) summonMirror=9.6ms(19x) subTraitDetect=5.8ms(13x) tryBuiltin=1.7ms(26x) cheapTypeKey=0.1ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)
  13. Encoder[Wide22A]: total=92.4ms  tryBuiltin=4.6ms(4x) cheapTypeKey=0.2ms(22x) builtinHit=0.0ms(4x) cacheHit=0.0ms(18x)
  14. Encoder[AnalyticsView]: total=91.9ms  summonIgnoring=68.2ms(11x) derive=66.0ms(12x) tryBuiltin=6.7ms(21x) summonMirror=2.8ms(12x) cheapTypeKey=0.2ms(51x) builtinHit=0.0ms(3x) cacheHit=0.0ms(30x) constructorNegHit=0.0ms(7x)
  15. Decoder[AlertInstance]: total=91.2ms  derive=135.6ms(19x) summonIgnoring=35.5ms(20x) summonMirror=6.8ms(19x) subTraitDetect=4.0ms(13x) tryBuiltin=1.7ms(26x) cheapTypeKey=0.1ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)

--- Hot Types (>50ms) ---
  Decoder[Article]: 209ms
  Encoder[Article]: 168ms
  Encoder[Ticket]: 153ms
  Decoder[Ticket]: 146ms
  Encoder[Sprint]: 143ms
======================================================================
```
</details>

<details>
<summary>Macro Profile — Configured</summary>

```
======================================================================
SANELY MACRO PROFILE (230 expansions, 2447ms total)
======================================================================

--- By Kind ---
  CfgCodec         230 expansions    2447.3ms  avg 10.64ms

--- Category Breakdown ---
  topDerive              2417.9ms ( 98.8%)     230 calls  avg 10.51ms
  tryBuiltin              447.0ms ( 18.3%)     493 calls  avg 0.91ms
  summonIgnoring          267.3ms ( 10.9%)     118 calls  avg 2.27ms
  resolveDefaults          29.5ms (  1.2%)     214 calls  avg 0.14ms
  subTraitDetect            9.5ms (  0.4%)      69 calls  avg 0.14ms
  cheapTypeKey              7.2ms (  0.3%)     820 calls  avg 0.01ms
  builtinHit                0.0ms (  0.0%)     375 calls  avg 0.00ms
  cacheHit                            327 hits
  codecHit                  0.0ms (  0.0%)     118 calls  avg 0.00ms
  overhead               -731.1ms (-29.9%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. CfgCodec[UserId]: total=73.9ms  topDerive=72.0ms(1x) resolveDefaults=1.7ms(1x) tryBuiltin=0.7ms(1x) cheapTypeKey=0.2ms(1x) builtinHit=0.0ms(1x)
   2. CfgCodec[Role]: total=70.9ms  topDerive=68.6ms(1x) tryBuiltin=56.0ms(2x) cheapTypeKey=0.4ms(4x) resolveDefaults=0.2ms(1x) builtinHit=0.0ms(2x) cacheHit=0.0ms(2x)
   3. CfgCodec[DunningRecord]: total=66.3ms  topDerive=66.2ms(1x) tryBuiltin=52.5ms(3x) resolveDefaults=1.6ms(1x) cheapTypeKey=0.0ms(4x) builtinHit=0.0ms(3x) cacheHit=0.0ms(1x)
   4. CfgCodec[InvoiceEvent]: total=63.2ms  topDerive=63.0ms(1x) summonIgnoring=45.9ms(5x) subTraitDetect=1.5ms(5x) tryBuiltin=0.5ms(5x) cheapTypeKey=0.0ms(5x) codecHit=0.0ms(5x)
   5. CfgCodec[ChatMessage]: total=61.8ms  topDerive=61.7ms(1x) tryBuiltin=46.4ms(7x) summonIgnoring=6.4ms(3x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(8x) codecHit=0.0ms(3x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
   6. CfgCodec[Product]: total=47.6ms  topDerive=47.4ms(1x) summonIgnoring=22.2ms(6x) tryBuiltin=14.3ms(10x) cheapTypeKey=0.1ms(10x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(4x) codecHit=0.0ms(6x)
   7. CfgCodec[Invoice]: total=42.6ms  topDerive=42.3ms(1x) tryBuiltin=20.7ms(6x) summonIgnoring=2.1ms(2x) cheapTypeKey=0.1ms(11x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(4x) cacheHit=0.0ms(5x) codecHit=0.0ms(2x)
   8. CfgCodec[Article]: total=41.6ms  topDerive=41.4ms(1x) tryBuiltin=16.8ms(8x) summonIgnoring=10.5ms(5x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(11x) codecHit=0.0ms(5x) builtinHit=0.0ms(3x) cacheHit=0.0ms(3x)
   9. CfgCodec[AuthEvent]: total=35.8ms  topDerive=35.1ms(1x) summonIgnoring=9.7ms(5x) subTraitDetect=0.8ms(5x) tryBuiltin=0.7ms(5x) cheapTypeKey=0.0ms(5x) codecHit=0.0ms(5x)
  10. CfgCodec[CustomerRecord]: total=32.8ms  topDerive=32.6ms(1x) tryBuiltin=11.4ms(7x) summonIgnoring=10.0ms(5x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(8x) codecHit=0.0ms(5x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x)
  11. CfgCodec[WorkflowStep]: total=24.7ms  topDerive=24.6ms(1x) tryBuiltin=13.0ms(6x) summonIgnoring=4.4ms(2x) cheapTypeKey=0.1ms(7x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(2x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
  12. CfgCodec[ProductVariant]: total=24.3ms  topDerive=24.2ms(1x) tryBuiltin=11.7ms(4x) summonIgnoring=5.3ms(2x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(4x) codecHit=0.0ms(2x) builtinHit=0.0ms(2x)
  13. CfgCodec[PaymentMethod]: total=23.8ms  topDerive=23.6ms(1x) summonIgnoring=11.6ms(4x) subTraitDetect=0.9ms(4x) tryBuiltin=0.4ms(4x) cheapTypeKey=0.0ms(4x) codecHit=0.0ms(4x)
  14. CfgCodec[AccessPolicy]: total=20.8ms  topDerive=20.7ms(1x) tryBuiltin=9.3ms(3x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(5x) builtinHit=0.0ms(3x) cacheHit=0.0ms(2x)
  15. CfgCodec[Pipeline]: total=20.6ms  topDerive=20.5ms(1x) tryBuiltin=13.3ms(3x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(4x) builtinHit=0.0ms(3x) cacheHit=0.0ms(1x)

--- Hot Types (>50ms) ---
  CfgCodec[UserId]: 74ms
  CfgCodec[Role]: 71ms
  CfgCodec[DunningRecord]: 66ms
  CfgCodec[InvoiceEvent]: 63ms
  CfgCodec[ChatMessage]: 62ms
======================================================================
```
</details>


## dev

**Date:** 2026-03-08 03:13:18 UTC | **SHA:** `d41ac0c`

### At a Glance — Compile Time

| Metric | sanely-auto | circe-generic | Delta |
|--------|-------------|---------------|-------|
| Compile (auto, ~300 types) | 7.2s ± 1.48s | 21.0s ± 0.21s | **2.9x faster** |
| Compile (configured, ~230 types) | 4.6s ± 1.26s | 6.6s ± 0.25s | **1.4x faster** |
| Peak RSS (auto) | 863 MB | 1041 MB | **-17%** |
| Peak RSS (configured) | 812 MB | 684 MB | +19% |
| Bytecode (auto) | 2.5 MB | 3.2 MB | **-22%** |
| Bytecode (configured) | 2.6 MB | 3.0 MB | **-10%** |

### At a Glance — Runtime

| Benchmark | ops/sec | vs circe | alloc |
|-----------|---------|----------|-------|
| Read: circe+jsoniter | 110k | **1.2x** | 25 KB/op |
| Read: sanely-jsoniter | 370k | **4.1x** | 3 KB/op |
| Read: jsoniter-scala | 362k | **4.0x** | 3 KB/op |
| Write: circe+jsoniter | 69k | **1.2x** | 23 KB/op |
| Write: sanely-jsoniter | 378k | **6.5x** | 1 KB/op |
| Write: jsoniter-scala | 422k | **7.3x** | 1 KB/op |

<details>
<summary>Compile Time — Auto Derivation</summary>

```
Compile-time benchmark: circe-sanely-auto vs circe-generic (N=10)
Benchmark suite: benchmark
Method: Mill daemon, hyperfine with --warmup 1, --runs 10
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark.sanely
  Time (mean ± σ):      7.214 s ±  1.483 s    [User: 0.052 s, System: 0.084 s]
  Range (min … max):    6.187 s … 11.133 s    10 runs
 
Benchmark 2: benchmark.generic
  Time (mean ± σ):     21.044 s ±  0.214 s    [User: 0.101 s, System: 0.207 s]
  Range (min … max):   20.865 s … 21.598 s    10 runs
 
Summary
  benchmark.sanely ran
    2.92 ± 0.60 times faster than benchmark.generic
```
</details>

<details>
<summary>Compile Time — Configured Derivation</summary>

```
Compile-time benchmark: circe-sanely-auto vs circe-core configured derivation (N=10)
Benchmark suite: benchmark-configured
Method: Mill daemon, hyperfine with --warmup 1, --runs 10
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark-configured.sanely
  Time (mean ± σ):      4.613 s ±  1.262 s    [User: 0.041 s, System: 0.076 s]
  Range (min … max):    3.581 s …  7.283 s    10 runs
 
Benchmark 2: benchmark-configured.generic
  Time (mean ± σ):      6.558 s ±  0.254 s    [User: 0.049 s, System: 0.097 s]
  Range (min … max):    6.325 s …  7.148 s    10 runs
 
Summary
  benchmark-configured.sanely ran
    1.42 ± 0.39 times faster than benchmark-configured.generic
```
</details>

<details>
<summary>Runtime Performance</summary>

```
Building runtime benchmark...

./mill benchmark-runtime.run 10 10
188] benchmark-runtime.run
Runtime benchmark: circe-jawn vs circe+jsoniter-bridge vs sanely-jsoniter vs jsoniter-scala
  warmup=10 iterations=10 (each 1 second)
  payload: 1379 bytes (circe), 1414 bytes (sanely-jsoniter), 1394 bytes (jsoniter-scala)

Reading (bytes -> case class):
----------------------------------------------------------------------
  circe-jawn                      89612 ops/sec  (min=87606, max=90044)  28 KB/op
  circe+jsoniter                 109735 ops/sec  (min=107064, max=110551)  25 KB/op
  sanely-jsoniter                370135 ops/sec  (min=367752, max=374379)  3 KB/op
  jsoniter-scala                 362361 ops/sec  (min=355206, max=366032)  3 KB/op

  circe+jsoniter             1.22x vs circe-jawn  alloc 0.88x
  sanely-jsoniter            4.13x vs circe-jawn  alloc 0.10x
  jsoniter-scala             4.04x vs circe-jawn  alloc 0.09x

Writing (case class -> bytes):
----------------------------------------------------------------------
  circe-printer                   58003 ops/sec  (min=56353, max=58356)  27 KB/op
  circe+jsoniter                  69488 ops/sec  (min=69096, max=69781)  23 KB/op
  sanely-jsoniter                378303 ops/sec  (min=374024, max=379705)  1 KB/op
  jsoniter-scala                 421798 ops/sec  (min=420097, max=423613)  1 KB/op

  circe+jsoniter             1.20x vs circe-printer  alloc 0.85x
  sanely-jsoniter            6.52x vs circe-printer  alloc 0.05x
  jsoniter-scala             7.27x vs circe-printer  alloc 0.05x
188/188, SUCCESS] ./mill benchmark-runtime.run 10 10 161s
```
</details>

<details>
<summary>Peak RSS</summary>

```
sanely-auto (auto): 883796 KB
circe-generic (auto): 1065908 KB
sanely-auto (configured): 831832 KB
circe-generic (configured): 700360 KB
```
</details>

<details>
<summary>Bytecode Impact</summary>

```
sanely-auto (auto): 2634732 bytes (2573.0 KB)
circe-generic (auto): 3384133 bytes (3304.8 KB)
sanely-auto (configured): 2775933 bytes (2710.9 KB)
circe-generic (configured): 3097489 bytes (3024.9 KB)
```
</details>

<details>
<summary>Macro Profile — Auto</summary>

```
======================================================================
SANELY MACRO PROFILE (398 expansions, 6632ms total)
======================================================================

--- By Kind ---
  Decoder          187 expansions    3119.3ms  avg 16.68ms
  Encoder          211 expansions    3513.0ms  avg 16.65ms

--- Category Breakdown ---
  derive                 2963.2ms ( 44.7%)     920 calls  avg 3.22ms
  summonIgnoring         2869.0ms ( 43.3%)     894 calls  avg 3.21ms
  tryBuiltin              367.1ms (  5.5%)    1943 calls  avg 0.19ms
  summonMirror            264.9ms (  4.0%)     920 calls  avg 0.29ms
  subTraitDetect          129.2ms (  1.9%)     336 calls  avg 0.38ms
  cheapTypeKey             12.2ms (  0.2%)    4342 calls  avg 0.00ms
  builtinHit                0.0ms (  0.0%)     907 calls  avg 0.00ms
  cacheHit                           2399 hits
  constructorNegHit         0.0ms (  0.0%)     142 calls  avg 0.00ms
  overhead                 26.7ms (  0.4%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. Encoder[Sprint]: total=220.2ms  summonIgnoring=211.8ms(1x) tryBuiltin=3.0ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   2. Encoder[Ticket]: total=178.6ms  derive=125.9ms(22x) summonIgnoring=94.3ms(27x) summonMirror=8.0ms(22x) subTraitDetect=4.9ms(18x) tryBuiltin=3.0ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   3. Encoder[Ticket]: total=164.1ms  summonIgnoring=95.1ms(27x) derive=92.0ms(22x) tryBuiltin=10.5ms(31x) summonMirror=10.5ms(22x) subTraitDetect=7.4ms(18x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   4. Decoder[Ticket]: total=151.6ms  summonIgnoring=88.5ms(27x) derive=87.4ms(22x) tryBuiltin=10.0ms(31x) summonMirror=7.7ms(22x) subTraitDetect=4.8ms(18x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   5. Decoder[Sprint]: total=146.2ms  summonIgnoring=140.0ms(1x) tryBuiltin=1.3ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   6. Encoder[Wide22A]: total=130.9ms  tryBuiltin=7.1ms(4x) cheapTypeKey=0.3ms(22x) builtinHit=0.0ms(4x) cacheHit=0.0ms(18x)
   7. Decoder[Article]: total=118.7ms  summonIgnoring=83.9ms(14x) derive=34.4ms(12x) summonMirror=4.5ms(12x) tryBuiltin=3.2ms(18x) subTraitDetect=2.5ms(6x) cheapTypeKey=0.2ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   8. Decoder[AlertInstance]: total=116.4ms  derive=195.6ms(19x) summonIgnoring=24.8ms(20x) summonMirror=5.5ms(19x) subTraitDetect=2.8ms(13x) tryBuiltin=1.4ms(26x) cheapTypeKey=0.1ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)
   9. Encoder[Article]: total=115.9ms  summonIgnoring=79.3ms(14x) derive=37.7ms(12x) summonMirror=4.9ms(12x) tryBuiltin=4.5ms(18x) subTraitDetect=2.3ms(6x) cheapTypeKey=0.1ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
  10. Decoder[FullDashboard]: total=110.4ms  derive=101.9ms(22x) summonIgnoring=70.4ms(18x) tryBuiltin=6.1ms(30x) summonMirror=4.2ms(22x) cheapTypeKey=0.6ms(71x) builtinHit=0.0ms(6x) cacheHit=0.0ms(41x) constructorNegHit=0.0ms(6x)
  11. Encoder[AnalyticsView]: total=104.6ms  derive=75.9ms(12x) summonIgnoring=73.0ms(11x) tryBuiltin=7.6ms(21x) summonMirror=3.3ms(12x) cheapTypeKey=0.2ms(51x) builtinHit=0.0ms(3x) cacheHit=0.0ms(30x) constructorNegHit=0.0ms(7x)
  12. Decoder[Ticket]: total=104.5ms  derive=65.4ms(22x) summonIgnoring=52.2ms(27x) summonMirror=6.8ms(22x) subTraitDetect=4.7ms(18x) tryBuiltin=2.7ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
  13. Decoder[UserReport]: total=101.9ms  derive=83.1ms(6x) summonIgnoring=70.2ms(7x) tryBuiltin=14.3ms(13x) summonMirror=1.7ms(6x) cheapTypeKey=0.2ms(36x) builtinHit=0.0ms(1x) cacheHit=0.0ms(23x) constructorNegHit=0.0ms(5x)
  14. Encoder[UserReport]: total=101.5ms  derive=73.9ms(6x) summonIgnoring=71.8ms(7x) tryBuiltin=13.4ms(13x) summonMirror=2.4ms(6x) cheapTypeKey=0.2ms(36x) builtinHit=0.0ms(1x) cacheHit=0.0ms(23x) constructorNegHit=0.0ms(5x)
  15. Encoder[MixedReport]: total=95.6ms  derive=59.5ms(21x) summonIgnoring=52.9ms(16x) tryBuiltin=7.2ms(29x) summonMirror=4.0ms(21x) cheapTypeKey=0.4ms(75x) builtinHit=0.0ms(5x) cacheHit=0.0ms(46x) constructorNegHit=0.0ms(8x)

--- Hot Types (>50ms) ---
  Encoder[Sprint]: 220ms
  Encoder[Ticket]: 179ms
  Encoder[Ticket]: 164ms
  Decoder[Ticket]: 152ms
  Decoder[Sprint]: 146ms
======================================================================
```
</details>

<details>
<summary>Macro Profile — Configured</summary>

```
======================================================================
SANELY MACRO PROFILE (230 expansions, 2357ms total)
======================================================================

--- By Kind ---
  CfgCodec         230 expansions    2356.7ms  avg 10.25ms

--- Category Breakdown ---
  topDerive              2297.1ms ( 97.5%)     230 calls  avg 9.99ms
  tryBuiltin              414.6ms ( 17.6%)     493 calls  avg 0.84ms
  summonIgnoring          265.9ms ( 11.3%)     118 calls  avg 2.25ms
  resolveDefaults          30.5ms (  1.3%)     214 calls  avg 0.14ms
  subTraitDetect            7.3ms (  0.3%)      69 calls  avg 0.11ms
  cheapTypeKey              3.2ms (  0.1%)     820 calls  avg 0.00ms
  builtinHit                0.0ms (  0.0%)     375 calls  avg 0.00ms
  cacheHit                            327 hits
  codecHit                  0.0ms (  0.0%)     118 calls  avg 0.00ms
  overhead               -661.9ms (-28.1%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. CfgCodec[UserId]: total=134.5ms  topDerive=101.3ms(1x) resolveDefaults=6.3ms(1x) tryBuiltin=0.5ms(1x) cheapTypeKey=0.1ms(1x) builtinHit=0.0ms(1x)
   2. CfgCodec[Role]: total=95.2ms  topDerive=93.4ms(1x) tryBuiltin=80.8ms(2x) cheapTypeKey=0.3ms(4x) resolveDefaults=0.2ms(1x) builtinHit=0.0ms(2x) cacheHit=0.0ms(2x)
   3. CfgCodec[WorkflowEvent]: total=49.7ms  topDerive=49.6ms(1x) summonIgnoring=43.5ms(4x) subTraitDetect=0.4ms(4x) tryBuiltin=0.2ms(4x) cheapTypeKey=0.0ms(4x) codecHit=0.0ms(4x)
   4. CfgCodec[Article]: total=37.8ms  topDerive=37.6ms(1x) tryBuiltin=14.8ms(8x) summonIgnoring=10.1ms(5x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(11x) codecHit=0.0ms(5x) builtinHit=0.0ms(3x) cacheHit=0.0ms(3x)
   5. CfgCodec[Invoice]: total=36.1ms  topDerive=35.8ms(1x) tryBuiltin=19.6ms(6x) summonIgnoring=1.9ms(2x) cheapTypeKey=0.1ms(11x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(4x) cacheHit=0.0ms(5x) codecHit=0.0ms(2x)
   6. CfgCodec[AuthEvent]: total=34.9ms  topDerive=34.1ms(1x) summonIgnoring=10.4ms(5x) subTraitDetect=0.8ms(5x) tryBuiltin=0.6ms(5x) cheapTypeKey=0.0ms(5x) codecHit=0.0ms(5x)
   7. CfgCodec[ChatMessage]: total=33.0ms  topDerive=32.8ms(1x) tryBuiltin=17.5ms(7x) summonIgnoring=7.0ms(3x) cheapTypeKey=0.1ms(8x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(3x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
   8. CfgCodec[Product]: total=32.8ms  topDerive=32.6ms(1x) tryBuiltin=14.0ms(10x) summonIgnoring=8.7ms(6x) cheapTypeKey=0.1ms(10x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(4x) codecHit=0.0ms(6x)
   9. CfgCodec[CustomerRecord]: total=29.0ms  topDerive=28.8ms(1x) tryBuiltin=10.5ms(7x) summonIgnoring=9.1ms(5x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(8x) codecHit=0.0ms(5x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x)
  10. CfgCodec[AccessPolicy]: total=27.3ms  topDerive=27.1ms(1x) tryBuiltin=14.0ms(3x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(5x) builtinHit=0.0ms(3x) cacheHit=0.0ms(2x)
  11. CfgCodec[MetricSeries]: total=27.3ms  topDerive=27.1ms(1x) tryBuiltin=14.5ms(4x) summonIgnoring=3.8ms(1x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(4x) codecHit=0.0ms(1x) builtinHit=0.0ms(3x)
  12. CfgCodec[FunnelReport]: total=25.5ms  topDerive=25.3ms(1x) tryBuiltin=13.7ms(3x) summonIgnoring=2.9ms(1x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(4x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x) codecHit=0.0ms(1x)
  13. CfgCodec[WorkflowStep]: total=24.2ms  topDerive=24.1ms(1x) tryBuiltin=12.6ms(6x) summonIgnoring=4.6ms(2x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(7x) codecHit=0.0ms(2x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
  14. CfgCodec[Report]: total=24.0ms  topDerive=23.8ms(1x) tryBuiltin=10.7ms(4x) summonIgnoring=3.2ms(1x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(3x) cacheHit=0.0ms(2x) codecHit=0.0ms(1x)
  15. CfgCodec[SecurityConfig]: total=22.2ms  topDerive=22.0ms(1x) tryBuiltin=12.3ms(3x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(4x) builtinHit=0.0ms(3x) cacheHit=0.0ms(1x)

--- Hot Types (>50ms) ---
  CfgCodec[UserId]: 134ms
  CfgCodec[Role]: 95ms
======================================================================
```
</details>


## dev

**Date:** 2026-03-08 02:48:16 UTC | **SHA:** `2f40010`

### At a Glance — Compile Time

| Metric | sanely-auto | circe-generic | Delta |
|--------|-------------|---------------|-------|
| Compile (auto, ~300 types) | 7.6s ± 1.51s | 22.2s ± 0.20s | **2.9x faster** |
| Compile (configured, ~230 types) | 4.7s ± 1.20s | 6.7s ± 0.24s | **1.4x faster** |
| Peak RSS (auto) | 50 MB | 51 MB | **-3%** |
| Peak RSS (configured) | 47 MB | 47 MB | **-1%** |

### At a Glance — Runtime

| Benchmark | ops/sec | vs circe | alloc |
|-----------|---------|----------|-------|
| Read: circe+jsoniter | 113k | **1.3x** | 25 KB/op |
| Read: sanely-jsoniter | 374k | **4.2x** | 3 KB/op |
| Read: jsoniter-scala | 365k | **4.1x** | 3 KB/op |
| Write: circe+jsoniter | 70k | **1.2x** | 23 KB/op |
| Write: sanely-jsoniter | 383k | **6.4x** | 1 KB/op |
| Write: jsoniter-scala | 438k | **7.4x** | 1 KB/op |

<details>
<summary>Compile Time — Auto Derivation</summary>

```
Compile-time benchmark: circe-sanely-auto vs circe-generic (N=10)
Benchmark suite: benchmark
Method: Mill daemon, hyperfine with --warmup 1, --runs 10
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark.sanely
  Time (mean ± σ):      7.643 s ±  1.510 s    [User: 0.060 s, System: 0.106 s]
  Range (min … max):    6.518 s … 11.066 s    10 runs
 
Benchmark 2: benchmark.generic
  Time (mean ± σ):     22.204 s ±  0.198 s    [User: 0.123 s, System: 0.260 s]
  Range (min … max):   22.012 s … 22.672 s    10 runs
 
Summary
  benchmark.sanely ran
    2.90 ± 0.57 times faster than benchmark.generic
```
</details>

<details>
<summary>Compile Time — Configured Derivation</summary>

```
Compile-time benchmark: circe-sanely-auto vs circe-core configured derivation (N=10)
Benchmark suite: benchmark-configured
Method: Mill daemon, hyperfine with --warmup 1, --runs 10
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark-configured.sanely
  Time (mean ± σ):      4.662 s ±  1.198 s    [User: 0.046 s, System: 0.072 s]
  Range (min … max):    3.513 s …  7.125 s    10 runs
 
Benchmark 2: benchmark-configured.generic
  Time (mean ± σ):      6.694 s ±  0.236 s    [User: 0.051 s, System: 0.099 s]
  Range (min … max):    6.499 s …  7.259 s    10 runs
 
Summary
  benchmark-configured.sanely ran
    1.44 ± 0.37 times faster than benchmark-configured.generic
```
</details>

<details>
<summary>Runtime Performance</summary>

```
Building runtime benchmark...

./mill benchmark-runtime.run 10 10
188] benchmark-runtime.run
Runtime benchmark: circe-jawn vs circe+jsoniter-bridge vs sanely-jsoniter vs jsoniter-scala
  warmup=10 iterations=10 (each 1 second)
  payload: 1379 bytes (circe), 1414 bytes (sanely-jsoniter), 1394 bytes (jsoniter-scala)

Reading (bytes -> case class):
----------------------------------------------------------------------
  circe-jawn                      89173 ops/sec  (min=88388, max=89971)  28 KB/op
  circe+jsoniter                 113466 ops/sec  (min=112718, max=114124)  25 KB/op
  sanely-jsoniter                373836 ops/sec  (min=365153, max=376336)  3 KB/op
  jsoniter-scala                 364763 ops/sec  (min=362604, max=365855)  3 KB/op

  circe+jsoniter             1.27x vs circe-jawn  alloc 0.88x
  sanely-jsoniter            4.19x vs circe-jawn  alloc 0.10x
  jsoniter-scala             4.09x vs circe-jawn  alloc 0.09x

Writing (case class -> bytes):
----------------------------------------------------------------------
  circe-printer                   59592 ops/sec  (min=58938, max=59771)  27 KB/op
  circe+jsoniter                  69973 ops/sec  (min=67585, max=70194)  23 KB/op
  sanely-jsoniter                382667 ops/sec  (min=380237, max=383500)  1 KB/op
  jsoniter-scala                 438307 ops/sec  (min=437564, max=440003)  1 KB/op

  circe+jsoniter             1.17x vs circe-printer  alloc 0.85x
  sanely-jsoniter            6.42x vs circe-printer  alloc 0.05x
  jsoniter-scala             7.36x vs circe-printer  alloc 0.05x
188/188, SUCCESS] ./mill benchmark-runtime.run 10 10 161s
```
</details>

<details>
<summary>Peak RSS</summary>

```
sanely-auto (auto): 50696 KB
circe-generic (auto): 52380 KB
sanely-auto (configured): 47784 KB
circe-generic (configured): 48144 KB
```
</details>

<details>
<summary>Bytecode Impact</summary>

```
sanely-auto (auto): 2634732 bytes (2572.9 KB)
(standard_in) 1: syntax error
circe-generic (auto):  bytes ( KB)
(standard_in) 1: syntax error
sanely-auto (configured):  bytes ( KB)
(standard_in) 1: syntax error
circe-generic (configured):  bytes ( KB)
```
</details>

<details>
<summary>Macro Profile — Auto</summary>

```
======================================================================
SANELY MACRO PROFILE (398 expansions, 6988ms total)
======================================================================

--- By Kind ---
  Decoder          187 expansions    3310.5ms  avg 17.70ms
  Encoder          211 expansions    3677.3ms  avg 17.43ms

--- Category Breakdown ---
  derive                 3077.6ms ( 44.0%)     920 calls  avg 3.35ms
  summonIgnoring         2996.7ms ( 42.9%)     894 calls  avg 3.35ms
  tryBuiltin              410.1ms (  5.9%)    1943 calls  avg 0.21ms
  summonMirror            290.0ms (  4.2%)     920 calls  avg 0.32ms
  subTraitDetect          142.4ms (  2.0%)     336 calls  avg 0.42ms
  cheapTypeKey             12.0ms (  0.2%)    4342 calls  avg 0.00ms
  builtinHit                0.0ms (  0.0%)     907 calls  avg 0.00ms
  cacheHit                           2399 hits
  constructorNegHit         0.0ms (  0.0%)     142 calls  avg 0.00ms
  overhead                 59.0ms (  0.8%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. Decoder[AnalyticsView]: total=157.8ms  derive=145.7ms(12x) summonIgnoring=125.6ms(11x) tryBuiltin=7.2ms(21x) summonMirror=2.8ms(12x) cheapTypeKey=0.2ms(51x) builtinHit=0.0ms(3x) cacheHit=0.0ms(30x) constructorNegHit=0.0ms(7x)
   2. Encoder[ProductInfo]: total=137.9ms  summonIgnoring=110.1ms(6x) derive=10.9ms(4x) tryBuiltin=6.2ms(9x) summonMirror=2.1ms(4x) cheapTypeKey=0.1ms(20x) builtinHit=0.0ms(3x) cacheHit=0.0ms(11x)
   3. Decoder[Article]: total=123.1ms  summonIgnoring=86.7ms(14x) derive=36.8ms(12x) summonMirror=3.9ms(12x) tryBuiltin=3.4ms(18x) subTraitDetect=2.0ms(6x) cheapTypeKey=0.1ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   4. Encoder[Ticket]: total=122.3ms  summonIgnoring=72.2ms(27x) derive=64.3ms(22x) summonMirror=7.4ms(22x) tryBuiltin=6.8ms(31x) subTraitDetect=5.3ms(18x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   5. Encoder[Article]: total=121.7ms  summonIgnoring=84.2ms(14x) derive=39.5ms(12x) summonMirror=5.3ms(12x) tryBuiltin=3.3ms(18x) subTraitDetect=2.2ms(6x) cheapTypeKey=0.1ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   6. Encoder[Sprint]: total=114.2ms  summonIgnoring=108.5ms(1x) tryBuiltin=1.9ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   7. Decoder[Sprint]: total=113.1ms  summonIgnoring=108.5ms(1x) tryBuiltin=0.9ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   8. Decoder[Ticket]: total=111.4ms  summonIgnoring=66.1ms(27x) derive=61.2ms(22x) tryBuiltin=6.9ms(31x) summonMirror=5.6ms(22x) subTraitDetect=4.0ms(18x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   9. Encoder[FullDashboard]: total=105.5ms  summonIgnoring=63.0ms(18x) derive=55.7ms(22x) tryBuiltin=6.3ms(30x) summonMirror=4.3ms(22x) cheapTypeKey=0.3ms(71x) builtinHit=0.0ms(6x) cacheHit=0.0ms(41x) constructorNegHit=0.0ms(6x)
  10. Encoder[OrderSummary]: total=105.0ms  derive=116.8ms(18x) summonIgnoring=27.9ms(18x) summonMirror=11.6ms(18x) subTraitDetect=8.0ms(14x) tryBuiltin=1.1ms(21x) cheapTypeKey=0.1ms(66x) builtinHit=0.0ms(3x) cacheHit=0.0ms(45x)
  11. Encoder[UserReport]: total=99.9ms  derive=73.8ms(6x) summonIgnoring=69.6ms(7x) tryBuiltin=13.2ms(13x) summonMirror=2.3ms(6x) cheapTypeKey=0.2ms(36x) builtinHit=0.0ms(1x) cacheHit=0.0ms(23x) constructorNegHit=0.0ms(5x)
  12. Decoder[UserReport]: total=97.3ms  derive=78.1ms(6x) summonIgnoring=65.8ms(7x) tryBuiltin=14.6ms(13x) summonMirror=1.4ms(6x) cheapTypeKey=0.2ms(36x) builtinHit=0.0ms(1x) cacheHit=0.0ms(23x) constructorNegHit=0.0ms(5x)
  13. Encoder[ComplianceReport]: total=95.5ms  tryBuiltin=33.9ms(23x) derive=33.2ms(17x) summonIgnoring=31.9ms(10x) summonMirror=4.3ms(17x) cheapTypeKey=0.2ms(57x) builtinHit=0.0ms(6x) cacheHit=0.0ms(34x) constructorNegHit=0.0ms(7x)
  14. Encoder[HttpMethod]: total=94.7ms  summonIgnoring=57.1ms(5x) derive=12.2ms(5x) subTraitDetect=5.5ms(5x) summonMirror=3.8ms(5x) tryBuiltin=0.4ms(6x) cheapTypeKey=0.1ms(10x) builtinHit=0.0ms(1x) cacheHit=0.0ms(4x)
  15. Decoder[AlertInstance]: total=93.4ms  derive=138.6ms(19x) summonIgnoring=35.7ms(20x) summonMirror=7.7ms(19x) subTraitDetect=4.5ms(13x) tryBuiltin=1.9ms(26x) cheapTypeKey=0.1ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)

--- Hot Types (>50ms) ---
  Decoder[AnalyticsView]: 158ms
  Encoder[ProductInfo]: 138ms
  Decoder[Article]: 123ms
  Encoder[Ticket]: 122ms
  Encoder[Article]: 122ms
======================================================================
```
</details>

<details>
<summary>Macro Profile — Configured</summary>

```
======================================================================
SANELY MACRO PROFILE (230 expansions, 2232ms total)
======================================================================

--- By Kind ---
  CfgCodec         230 expansions    2232.5ms  avg 9.71ms

--- Category Breakdown ---
  topDerive              2204.3ms ( 98.7%)     230 calls  avg 9.58ms
  tryBuiltin              417.3ms ( 18.7%)     493 calls  avg 0.85ms
  summonIgnoring          247.0ms ( 11.1%)     118 calls  avg 2.09ms
  resolveDefaults          28.4ms (  1.3%)     214 calls  avg 0.13ms
  subTraitDetect            7.5ms (  0.3%)      69 calls  avg 0.11ms
  cheapTypeKey              4.0ms (  0.2%)     820 calls  avg 0.00ms
  builtinHit                0.0ms (  0.0%)     375 calls  avg 0.00ms
  cacheHit                            327 hits
  codecHit                  0.0ms (  0.0%)     118 calls  avg 0.00ms
  overhead               -676.0ms (-30.3%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. CfgCodec[SecurityConfig]: total=80.7ms  topDerive=80.5ms(1x) tryBuiltin=71.3ms(3x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(4x) builtinHit=0.0ms(3x) cacheHit=0.0ms(1x)
   2. CfgCodec[Role]: total=69.6ms  topDerive=67.5ms(1x) tryBuiltin=54.1ms(2x) cheapTypeKey=0.5ms(4x) resolveDefaults=0.2ms(1x) builtinHit=0.0ms(2x) cacheHit=0.0ms(2x)
   3. CfgCodec[UserId]: total=61.3ms  topDerive=59.7ms(1x) resolveDefaults=1.3ms(1x) tryBuiltin=0.5ms(1x) cheapTypeKey=0.1ms(1x) builtinHit=0.0ms(1x)
   4. CfgCodec[ContentStatus]: total=59.1ms  topDerive=59.1ms(1x) summonIgnoring=45.8ms(5x) subTraitDetect=1.1ms(5x) tryBuiltin=0.6ms(5x) cheapTypeKey=0.1ms(5x) codecHit=0.0ms(5x)
   5. CfgCodec[StepInput]: total=50.6ms  topDerive=50.5ms(1x) tryBuiltin=0.2ms(2x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(4x) builtinHit=0.0ms(2x) cacheHit=0.0ms(2x)
   6. CfgCodec[Article]: total=41.4ms  topDerive=41.2ms(1x) tryBuiltin=17.1ms(8x) summonIgnoring=11.2ms(5x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(11x) codecHit=0.0ms(5x) builtinHit=0.0ms(3x) cacheHit=0.0ms(3x)
   7. CfgCodec[Product]: total=33.2ms  topDerive=33.0ms(1x) tryBuiltin=14.3ms(10x) summonIgnoring=8.9ms(6x) cheapTypeKey=0.1ms(10x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(4x) codecHit=0.0ms(6x)
   8. CfgCodec[ImageMedia]: total=33.0ms  topDerive=32.9ms(1x) tryBuiltin=0.3ms(2x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(3x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x)
   9. CfgCodec[ChatMessage]: total=31.1ms  topDerive=31.0ms(1x) tryBuiltin=16.5ms(7x) summonIgnoring=6.5ms(3x) cheapTypeKey=0.1ms(8x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(3x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
  10. CfgCodec[CustomerRecord]: total=30.7ms  topDerive=30.5ms(1x) tryBuiltin=11.3ms(7x) summonIgnoring=10.1ms(5x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(8x) codecHit=0.0ms(5x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x)
  11. CfgCodec[Invoice]: total=25.7ms  topDerive=25.5ms(1x) tryBuiltin=12.7ms(6x) summonIgnoring=1.4ms(2x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(11x) builtinHit=0.0ms(4x) cacheHit=0.0ms(5x) codecHit=0.0ms(2x)
  12. CfgCodec[AuthEvent]: total=25.0ms  topDerive=24.5ms(1x) summonIgnoring=7.1ms(5x) tryBuiltin=0.5ms(5x) subTraitDetect=0.5ms(5x) cheapTypeKey=0.0ms(5x) codecHit=0.0ms(5x)
  13. CfgCodec[DunningRecord]: total=24.9ms  topDerive=23.2ms(1x) tryBuiltin=12.7ms(3x) resolveDefaults=2.1ms(1x) cheapTypeKey=0.0ms(4x) builtinHit=0.0ms(3x) cacheHit=0.0ms(1x)
  14. CfgCodec[WorkflowStep]: total=24.4ms  topDerive=24.2ms(1x) tryBuiltin=13.2ms(6x) summonIgnoring=4.3ms(2x) cheapTypeKey=0.1ms(7x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(2x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
  15. CfgCodec[ProductVariant]: total=22.3ms  topDerive=22.1ms(1x) tryBuiltin=10.5ms(4x) summonIgnoring=5.3ms(2x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(4x) codecHit=0.0ms(2x) builtinHit=0.0ms(2x)

--- Hot Types (>50ms) ---
  CfgCodec[SecurityConfig]: 81ms
  CfgCodec[Role]: 70ms
  CfgCodec[UserId]: 61ms
  CfgCodec[ContentStatus]: 59ms
  CfgCodec[StepInput]: 51ms
======================================================================
```
</details>


## v0.16.0

**Date:** 2026-03-07 16:29:52 UTC | **SHA:** `1f58dcd`

### Compile Time — Auto Derivation

```
Compile-time benchmark: circe-sanely-auto vs circe-generic (N=5)
Benchmark suite: benchmark
Method: Mill daemon, hyperfine with --warmup 1, --runs 5
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark.sanely
  Time (mean ± σ):      8.633 s ±  1.868 s    [User: 0.069 s, System: 0.116 s]
  Range (min … max):    7.304 s … 11.802 s    5 runs
 
Benchmark 2: benchmark.generic
  Time (mean ± σ):     22.375 s ±  0.368 s    [User: 0.138 s, System: 0.259 s]
  Range (min … max):   22.063 s … 22.989 s    5 runs
 
Summary
  benchmark.sanely ran
    2.59 ± 0.56 times faster than benchmark.generic
```

### Compile Time — Configured Derivation

```
Compile-time benchmark: circe-sanely-auto vs circe-core configured derivation (N=5)
Benchmark suite: benchmark-configured
Method: Mill daemon, hyperfine with --warmup 1, --runs 5
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark-configured.sanely
  Time (mean ± σ):      5.354 s ±  1.305 s    [User: 0.046 s, System: 0.084 s]
  Range (min … max):    4.117 s …  7.314 s    5 runs
 
Benchmark 2: benchmark-configured.generic
  Time (mean ± σ):      6.899 s ±  0.304 s    [User: 0.052 s, System: 0.102 s]
  Range (min … max):    6.503 s …  7.265 s    5 runs
 
Summary
  benchmark-configured.sanely ran
    1.29 ± 0.32 times faster than benchmark-configured.generic
```

### Runtime Performance

```
Building runtime benchmark...

./mill benchmark-runtime.run 5 5
188] benchmark-runtime.run
Runtime benchmark: circe-jawn vs circe+jsoniter-bridge vs sanely-jsoniter vs jsoniter-scala
  warmup=5 iterations=5 (each 1 second)
  payload: 1379 bytes (circe), 1414 bytes (sanely-jsoniter), 1394 bytes (jsoniter-scala)

Reading (bytes -> case class):
----------------------------------------------------------------------
  circe-jawn                      87919 ops/sec  (min=86006, max=88777)  28 KB/op
  circe+jsoniter                 109610 ops/sec  (min=108219, max=111103)  25 KB/op
  sanely-jsoniter                323334 ops/sec  (min=320859, max=325317)  4 KB/op
  jsoniter-scala                 355753 ops/sec  (min=346359, max=358439)  3 KB/op

  circe+jsoniter             1.25x vs circe-jawn  alloc 0.88x
  sanely-jsoniter            3.68x vs circe-jawn  alloc 0.15x
  jsoniter-scala             4.05x vs circe-jawn  alloc 0.09x

Writing (case class -> bytes):
----------------------------------------------------------------------
  circe-printer                   59375 ops/sec  (min=59234, max=59808)  27 KB/op
  circe+jsoniter                  69459 ops/sec  (min=66784, max=70298)  23 KB/op
  sanely-jsoniter                378599 ops/sec  (min=376794, max=380187)  1 KB/op
  jsoniter-scala                 434210 ops/sec  (min=421762, max=435534)  1 KB/op

  circe+jsoniter             1.17x vs circe-printer  alloc 0.85x
  sanely-jsoniter            6.38x vs circe-printer  alloc 0.05x
  jsoniter-scala             7.31x vs circe-printer  alloc 0.05x
188/188, SUCCESS] ./mill benchmark-runtime.run 5 5 82s
```

### Macro Profile — Auto

```
======================================================================
SANELY MACRO PROFILE (398 expansions, 6479ms total)
======================================================================

--- By Kind ---
  Decoder          187 expansions    2996.8ms  avg 16.03ms
  Encoder          211 expansions    3481.8ms  avg 16.50ms

--- Category Breakdown ---
  summonIgnoring         2800.4ms ( 43.2%)     894 calls  avg 3.13ms
  derive                 2653.1ms ( 41.0%)     920 calls  avg 2.88ms
  tryBuiltin              340.8ms (  5.3%)    1943 calls  avg 0.18ms
  summonMirror            272.9ms (  4.2%)     920 calls  avg 0.30ms
  subTraitDetect          140.4ms (  2.2%)     336 calls  avg 0.42ms
  cheapTypeKey             12.6ms (  0.2%)    4342 calls  avg 0.00ms
  builtinHit                0.0ms (  0.0%)     907 calls  avg 0.00ms
  cacheHit                           2399 hits
  constructorNegHit         0.0ms (  0.0%)     142 calls  avg 0.00ms
  overhead                258.4ms (  4.0%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. Encoder[Ticket]: total=163.9ms  summonIgnoring=94.8ms(27x) derive=92.6ms(22x) summonMirror=11.2ms(22x) tryBuiltin=10.4ms(31x) subTraitDetect=7.6ms(18x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   2. Encoder[Sprint]: total=159.3ms  summonIgnoring=150.0ms(1x) tryBuiltin=3.1ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   3. Decoder[Ticket]: total=152.0ms  summonIgnoring=92.4ms(27x) derive=78.8ms(22x) tryBuiltin=10.1ms(31x) summonMirror=8.0ms(22x) subTraitDetect=4.5ms(18x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   4. Decoder[Sprint]: total=149.6ms  summonIgnoring=142.9ms(1x) tryBuiltin=1.4ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   5. Decoder[Article]: total=133.9ms  summonIgnoring=99.5ms(14x) derive=49.2ms(12x) summonMirror=4.4ms(12x) tryBuiltin=3.9ms(18x) subTraitDetect=1.6ms(6x) cheapTypeKey=0.2ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   6. Encoder[Article]: total=124.1ms  summonIgnoring=85.4ms(14x) derive=40.4ms(12x) summonMirror=5.9ms(12x) tryBuiltin=4.2ms(18x) subTraitDetect=2.2ms(6x) cheapTypeKey=0.1ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   7. Encoder[Ticket]: total=116.4ms  derive=66.4ms(22x) summonIgnoring=61.6ms(27x) summonMirror=7.8ms(22x) subTraitDetect=4.7ms(18x) tryBuiltin=3.3ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   8. Encoder[HttpMethod]: total=111.6ms  summonIgnoring=72.4ms(5x) derive=10.9ms(5x) subTraitDetect=5.3ms(5x) summonMirror=3.8ms(5x) tryBuiltin=0.6ms(6x) cheapTypeKey=0.1ms(10x) builtinHit=0.0ms(1x) cacheHit=0.0ms(4x)
   9. Decoder[Ticket]: total=109.6ms  derive=63.7ms(22x) summonIgnoring=54.8ms(27x) summonMirror=7.5ms(22x) subTraitDetect=6.0ms(18x) tryBuiltin=3.5ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
  10. Encoder[AlertInstance]: total=95.4ms  derive=137.9ms(19x) summonIgnoring=35.9ms(20x) summonMirror=10.4ms(19x) subTraitDetect=6.4ms(13x) tryBuiltin=1.9ms(26x) cheapTypeKey=0.1ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)
  11. Decoder[AlertInstance]: total=87.2ms  derive=125.2ms(19x) summonIgnoring=34.2ms(20x) summonMirror=7.3ms(19x) subTraitDetect=4.2ms(13x) tryBuiltin=1.8ms(26x) cheapTypeKey=0.1ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)
  12. Encoder[AnalyticsView]: total=86.7ms  summonIgnoring=64.2ms(11x) derive=61.6ms(12x) tryBuiltin=6.5ms(21x) summonMirror=2.9ms(12x) cheapTypeKey=0.2ms(51x) builtinHit=0.0ms(3x) cacheHit=0.0ms(30x) constructorNegHit=0.0ms(7x)
  13. Decoder[UserAccount]: total=86.3ms  derive=87.0ms(10x) summonIgnoring=53.6ms(10x) summonMirror=3.9ms(10x) tryBuiltin=0.7ms(13x) cheapTypeKey=0.1ms(33x) builtinHit=0.0ms(3x) cacheHit=0.0ms(20x)
  14. Decoder[DeploymentSpec]: total=83.8ms  derive=77.9ms(8x) summonIgnoring=52.6ms(11x) tryBuiltin=5.4ms(14x) summonMirror=3.0ms(8x) subTraitDetect=1.3ms(4x) cheapTypeKey=0.5ms(33x) builtinHit=0.0ms(3x) cacheHit=0.0ms(19x)
  15. Encoder[DeploymentSpec]: total=83.1ms  derive=75.3ms(8x) summonIgnoring=51.3ms(11x) tryBuiltin=5.6ms(14x) summonMirror=4.0ms(8x) subTraitDetect=1.8ms(4x) cheapTypeKey=0.1ms(33x) builtinHit=0.0ms(3x) cacheHit=0.0ms(19x)

--- Hot Types (>50ms) ---
  Encoder[Ticket]: 164ms
  Encoder[Sprint]: 159ms
  Decoder[Ticket]: 152ms
  Decoder[Sprint]: 150ms
  Decoder[Article]: 134ms
======================================================================
```

### Macro Profile — Configured

```
======================================================================
SANELY MACRO PROFILE (230 expansions, 2289ms total)
======================================================================

--- By Kind ---
  CfgCodec         230 expansions    2289.4ms  avg 9.95ms

--- Category Breakdown ---
  topDerive              2259.2ms ( 98.7%)     230 calls  avg 9.82ms
  tryBuiltin              389.4ms ( 17.0%)     493 calls  avg 0.79ms
  summonIgnoring          318.9ms ( 13.9%)     118 calls  avg 2.70ms
  resolveDefaults          27.8ms (  1.2%)     214 calls  avg 0.13ms
  subTraitDetect            8.2ms (  0.4%)      69 calls  avg 0.12ms
  cheapTypeKey              4.6ms (  0.2%)     820 calls  avg 0.01ms
  builtinHit                0.0ms (  0.0%)     375 calls  avg 0.00ms
  cacheHit                            327 hits
  codecHit                  0.0ms (  0.0%)     118 calls  avg 0.00ms
  overhead               -718.7ms (-31.4%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. CfgCodec[DeliveryStatus]: total=73.4ms  topDerive=73.3ms(1x) summonIgnoring=51.5ms(4x) subTraitDetect=0.7ms(4x) tryBuiltin=0.4ms(4x) cheapTypeKey=0.1ms(4x) codecHit=0.0ms(4x)
   2. CfgCodec[Role]: total=71.5ms  topDerive=69.4ms(1x) tryBuiltin=55.8ms(2x) cheapTypeKey=1.2ms(4x) resolveDefaults=0.2ms(1x) builtinHit=0.0ms(2x) cacheHit=0.0ms(2x)
   3. CfgCodec[WorkflowStep]: total=61.5ms  topDerive=61.3ms(1x) summonIgnoring=35.7ms(2x) tryBuiltin=16.4ms(6x) resolveDefaults=0.3ms(1x) cheapTypeKey=0.1ms(7x) codecHit=0.0ms(2x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
   4. CfgCodec[UserId]: total=58.7ms  topDerive=57.1ms(1x) resolveDefaults=1.2ms(1x) tryBuiltin=0.4ms(1x) cheapTypeKey=0.0ms(1x) builtinHit=0.0ms(1x)
   5. CfgCodec[Article]: total=42.5ms  topDerive=42.3ms(1x) tryBuiltin=17.1ms(8x) summonIgnoring=11.9ms(5x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(11x) codecHit=0.0ms(5x) builtinHit=0.0ms(3x) cacheHit=0.0ms(3x)
   6. CfgCodec[AuthEvent]: total=38.7ms  topDerive=37.9ms(1x) summonIgnoring=10.7ms(5x) tryBuiltin=1.4ms(5x) subTraitDetect=1.1ms(5x) cheapTypeKey=0.0ms(5x) codecHit=0.0ms(5x)
   7. CfgCodec[Invoice]: total=38.4ms  topDerive=38.2ms(1x) tryBuiltin=21.0ms(6x) summonIgnoring=2.5ms(2x) cheapTypeKey=0.1ms(11x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(4x) cacheHit=0.0ms(5x) codecHit=0.0ms(2x)
   8. CfgCodec[ChatMessage]: total=34.1ms  topDerive=34.0ms(1x) tryBuiltin=18.5ms(7x) summonIgnoring=6.8ms(3x) cheapTypeKey=0.1ms(8x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(3x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
   9. CfgCodec[Product]: total=33.5ms  topDerive=33.4ms(1x) tryBuiltin=14.2ms(10x) summonIgnoring=9.5ms(6x) cheapTypeKey=0.1ms(10x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(4x) codecHit=0.0ms(6x)
  10. CfgCodec[MediaType]: total=31.2ms  topDerive=31.1ms(1x) summonIgnoring=22.5ms(4x) tryBuiltin=1.3ms(4x) subTraitDetect=0.5ms(4x) cheapTypeKey=0.0ms(4x) codecHit=0.0ms(4x)
  11. CfgCodec[CustomerRecord]: total=29.4ms  topDerive=29.2ms(1x) tryBuiltin=10.8ms(7x) summonIgnoring=9.4ms(5x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(8x) codecHit=0.0ms(5x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x)
  12. CfgCodec[Report]: total=24.0ms  topDerive=23.7ms(1x) tryBuiltin=11.9ms(4x) summonIgnoring=3.0ms(1x) cheapTypeKey=0.1ms(6x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(3x) cacheHit=0.0ms(2x) codecHit=0.0ms(1x)
  13. CfgCodec[MetricSeries]: total=23.4ms  topDerive=23.2ms(1x) tryBuiltin=12.1ms(4x) summonIgnoring=3.2ms(1x) cheapTypeKey=0.1ms(4x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(1x) builtinHit=0.0ms(3x)
  14. CfgCodec[ProductVariant]: total=22.9ms  topDerive=22.8ms(1x) tryBuiltin=11.3ms(4x) summonIgnoring=5.2ms(2x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(4x) codecHit=0.0ms(2x) builtinHit=0.0ms(2x)
  15. CfgCodec[FunnelReport]: total=21.8ms  topDerive=21.6ms(1x) tryBuiltin=11.3ms(3x) summonIgnoring=2.7ms(1x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(4x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x) codecHit=0.0ms(1x)

--- Hot Types (>50ms) ---
  CfgCodec[DeliveryStatus]: 73ms
  CfgCodec[Role]: 72ms
  CfgCodec[WorkflowStep]: 62ms
  CfgCodec[UserId]: 59ms
======================================================================
```

## v0.15.0

**Date:** 2026-03-07 14:15:49 UTC | **SHA:** `60efae5`

### Compile Time — Auto Derivation

```
Compile-time benchmark: circe-sanely-auto vs circe-generic (N=5)
Benchmark suite: benchmark
Method: Mill daemon, hyperfine with --warmup 1, --runs 5
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark.sanely
  Time (mean ± σ):      7.805 s ±  1.610 s    [User: 0.065 s, System: 0.109 s]
  Range (min … max):    6.154 s … 10.196 s    5 runs
 
Benchmark 2: benchmark.generic
  Time (mean ± σ):     16.574 s ±  0.243 s    [User: 0.095 s, System: 0.220 s]
  Range (min … max):   16.419 s … 17.005 s    5 runs
 
  Warning: The first benchmarking run for this command was significantly slower than the rest (17.005 s). This could be caused by (filesystem) caches that were not filled until after the first run. You are already using both the '--warmup' option as well as the '--prepare' option. Consider re-running the benchmark on a quiet system. Maybe it was a random outlier. Alternatively, consider increasing the warmup count.
 
Summary
  benchmark.sanely ran
    2.12 ± 0.44 times faster than benchmark.generic
```

### Compile Time — Configured Derivation

```
Compile-time benchmark: circe-sanely-auto vs circe-core configured derivation (N=5)
Benchmark suite: benchmark-configured
Method: Mill daemon, hyperfine with --warmup 1, --runs 5
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark-configured.sanely
  Time (mean ± σ):      5.434 s ±  1.519 s    [User: 0.050 s, System: 0.084 s]
  Range (min … max):    4.015 s …  7.626 s    5 runs
 
Benchmark 2: benchmark-configured.generic
  Time (mean ± σ):      6.802 s ±  0.358 s    [User: 0.050 s, System: 0.105 s]
  Range (min … max):    6.414 s …  7.338 s    5 runs
 
Summary
  benchmark-configured.sanely ran
    1.25 ± 0.36 times faster than benchmark-configured.generic
```

### Runtime Performance

```
Building runtime benchmark...

./mill benchmark-runtime.run 5 5
188] benchmark-runtime.run
Runtime benchmark: circe-jawn vs circe+jsoniter-bridge vs sanely-jsoniter vs jsoniter-scala
  warmup=5 iterations=5 (each 1 second)
  payload: 1379 bytes (circe), 1414 bytes (sanely-jsoniter), 1394 bytes (jsoniter-scala)

Reading (bytes -> case class):
----------------------------------------------------------------------
  circe-jawn                      93219 ops/sec  (min=91821, max=93625)
  circe+jsoniter                 108849 ops/sec  (min=107400, max=109168)
  sanely-jsoniter                302863 ops/sec  (min=299445, max=303436)
  jsoniter-scala                 325008 ops/sec  (min=324425, max=326439)

  circe+jsoniter             1.17x vs circe-jawn
  sanely-jsoniter            3.25x vs circe-jawn
  jsoniter-scala             3.49x vs circe-jawn

Writing (case class -> bytes):
----------------------------------------------------------------------
  circe-printer                   66007 ops/sec  (min=65565, max=66395)
  circe+jsoniter                  72547 ops/sec  (min=72320, max=72815)
  sanely-jsoniter                360544 ops/sec  (min=359441, max=360912)
  jsoniter-scala                 406304 ops/sec  (min=404320, max=408072)

  circe+jsoniter             1.10x vs circe-printer
  sanely-jsoniter            5.46x vs circe-printer
  jsoniter-scala             6.16x vs circe-printer
188/188, SUCCESS] ./mill benchmark-runtime.run 5 5 81s
```

### Macro Profile — Auto

```
======================================================================
SANELY MACRO PROFILE (308 expansions, 5697ms total)
======================================================================

--- By Kind ---
  Decoder          154 expansions    2804.6ms  avg 18.21ms
  Encoder          154 expansions    2892.3ms  avg 18.78ms

--- Category Breakdown ---
  summonIgnoring         2361.6ms ( 41.5%)     660 calls  avg 3.58ms
  derive                 2023.7ms ( 35.5%)     586 calls  avg 3.45ms
  summonMirror            266.4ms (  4.7%)     586 calls  avg 0.45ms
  tryBuiltin              214.8ms (  3.8%)    1366 calls  avg 0.16ms
  subTraitDetect          154.7ms (  2.7%)     336 calls  avg 0.46ms
  cheapTypeKey             10.7ms (  0.2%)    3080 calls  avg 0.00ms
  builtinHit                0.0ms (  0.0%)     706 calls  avg 0.00ms
  cacheHit                           1714 hits
  overhead                665.0ms ( 11.7%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. Encoder[Article]: total=168.1ms  summonIgnoring=115.4ms(14x) derive=52.3ms(12x) summonMirror=6.4ms(12x) tryBuiltin=5.1ms(18x) subTraitDetect=3.0ms(6x) cheapTypeKey=0.2ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   2. Decoder[Article]: total=161.3ms  summonIgnoring=114.4ms(14x) derive=47.8ms(12x) tryBuiltin=4.6ms(18x) summonMirror=4.6ms(12x) subTraitDetect=2.4ms(6x) cheapTypeKey=0.2ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   3. Encoder[Sprint]: total=129.7ms  summonIgnoring=122.3ms(1x) tryBuiltin=2.1ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   4. Decoder[Sprint]: total=128.4ms  summonIgnoring=121.8ms(1x) tryBuiltin=1.3ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   5. Encoder[Ticket]: total=127.0ms  summonIgnoring=71.9ms(27x) derive=69.4ms(22x) summonMirror=8.1ms(22x) tryBuiltin=7.1ms(31x) subTraitDetect=5.5ms(18x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   6. Decoder[Ticket]: total=123.8ms  summonIgnoring=73.2ms(27x) derive=67.5ms(22x) tryBuiltin=7.6ms(31x) summonMirror=6.1ms(22x) subTraitDetect=3.6ms(18x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   7. Encoder[HttpMethod]: total=118.0ms  summonIgnoring=72.2ms(5x) derive=12.5ms(5x) subTraitDetect=5.8ms(5x) summonMirror=3.8ms(5x) tryBuiltin=0.6ms(6x) cheapTypeKey=0.1ms(10x) builtinHit=0.0ms(1x) cacheHit=0.0ms(4x)
   8. Encoder[ProductInfo]: total=100.7ms  summonIgnoring=74.3ms(6x) derive=10.6ms(4x) tryBuiltin=5.4ms(9x) summonMirror=2.1ms(4x) cheapTypeKey=0.1ms(20x) builtinHit=0.0ms(3x) cacheHit=0.0ms(11x)
   9. Encoder[OrderSummary]: total=99.4ms  derive=109.2ms(18x) summonIgnoring=26.2ms(18x) summonMirror=11.4ms(18x) subTraitDetect=7.1ms(14x) tryBuiltin=1.0ms(21x) cheapTypeKey=0.1ms(66x) builtinHit=0.0ms(3x) cacheHit=0.0ms(45x)
  10. Decoder[EventEnvelope]: total=95.5ms  derive=96.7ms(15x) summonIgnoring=44.8ms(15x) summonMirror=18.5ms(15x) subTraitDetect=2.8ms(8x) tryBuiltin=0.9ms(21x) cheapTypeKey=0.1ms(75x) builtinHit=0.0ms(6x) cacheHit=0.0ms(54x)
  11. Encoder[Ticket]: total=95.1ms  derive=53.3ms(22x) summonIgnoring=49.8ms(27x) summonMirror=6.6ms(22x) subTraitDetect=3.9ms(18x) tryBuiltin=2.5ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
  12. Decoder[Ticket]: total=94.7ms  derive=58.8ms(22x) summonIgnoring=46.5ms(27x) summonMirror=6.1ms(22x) subTraitDetect=5.4ms(18x) tryBuiltin=2.3ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
  13. Encoder[ValidationError]: total=90.7ms  summonMirror=37.6ms(5x) derive=21.3ms(5x) summonIgnoring=16.3ms(5x) subTraitDetect=4.1ms(5x) tryBuiltin=3.2ms(8x) cheapTypeKey=0.1ms(15x) builtinHit=0.0ms(3x) cacheHit=0.0ms(7x)
  14. Decoder[OrderSummary]: total=90.6ms  derive=97.0ms(18x) summonIgnoring=25.5ms(18x) summonMirror=7.3ms(18x) subTraitDetect=5.9ms(14x) tryBuiltin=0.9ms(21x) cheapTypeKey=0.1ms(66x) builtinHit=0.0ms(3x) cacheHit=0.0ms(45x)
  15. Decoder[ProductInfo]: total=88.9ms  summonIgnoring=64.7ms(6x) derive=9.8ms(4x) tryBuiltin=5.0ms(9x) summonMirror=1.4ms(4x) cheapTypeKey=0.1ms(20x) builtinHit=0.0ms(3x) cacheHit=0.0ms(11x)

--- Hot Types (>50ms) ---
  Encoder[Article]: 168ms
  Decoder[Article]: 161ms
  Encoder[Sprint]: 130ms
  Decoder[Sprint]: 128ms
  Encoder[Ticket]: 127ms
======================================================================
```

### Macro Profile — Configured

```
======================================================================
SANELY MACRO PROFILE (230 expansions, 2334ms total)
======================================================================

--- By Kind ---
  CfgCodec         230 expansions    2334.0ms  avg 10.15ms

--- Category Breakdown ---
  topDerive              2058.8ms ( 88.2%)     230 calls  avg 8.95ms
  tryBuiltin              412.2ms ( 17.7%)     493 calls  avg 0.84ms
  summonIgnoring          248.5ms ( 10.6%)     118 calls  avg 2.11ms
  resolveDefaults          25.7ms (  1.1%)     214 calls  avg 0.12ms
  subTraitDetect            7.9ms (  0.3%)      69 calls  avg 0.11ms
  cheapTypeKey              7.0ms (  0.3%)     820 calls  avg 0.01ms
  builtinHit                0.0ms (  0.0%)     375 calls  avg 0.00ms
  cacheHit                            327 hits
  codecHit                  0.0ms (  0.0%)     118 calls  avg 0.00ms
  overhead               -426.1ms (-18.3%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. CfgCodec[Role]: total=86.1ms  topDerive=84.0ms(1x) tryBuiltin=71.4ms(2x) cheapTypeKey=1.4ms(4x) resolveDefaults=0.2ms(1x) builtinHit=0.0ms(2x) cacheHit=0.0ms(2x)
   2. CfgCodec[UserId]: total=68.9ms  topDerive=40.8ms(1x) resolveDefaults=1.6ms(1x) tryBuiltin=0.6ms(1x) cheapTypeKey=0.4ms(1x) builtinHit=0.0ms(1x)
   3. CfgCodec[WorkflowStep]: total=64.6ms  topDerive=63.7ms(1x) tryBuiltin=27.0ms(6x) summonIgnoring=5.5ms(2x) cheapTypeKey=2.3ms(7x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(2x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
   4. CfgCodec[Article]: total=43.0ms  topDerive=42.0ms(1x) tryBuiltin=16.7ms(8x) summonIgnoring=10.7ms(5x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(11x) codecHit=0.0ms(5x) builtinHit=0.0ms(3x) cacheHit=0.0ms(3x)
   5. CfgCodec[WidgetType]: total=41.8ms  topDerive=40.1ms(1x) summonIgnoring=30.5ms(5x) subTraitDetect=0.7ms(5x) tryBuiltin=0.5ms(5x) cheapTypeKey=0.0ms(5x) codecHit=0.0ms(5x)
   6. CfgCodec[Product]: total=33.9ms  topDerive=33.1ms(1x) tryBuiltin=14.1ms(10x) summonIgnoring=9.4ms(6x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(10x) builtinHit=0.0ms(4x) codecHit=0.0ms(6x)
   7. CfgCodec[Invoice]: total=33.8ms  topDerive=32.5ms(1x) tryBuiltin=18.2ms(6x) summonIgnoring=1.8ms(2x) cheapTypeKey=0.1ms(11x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(4x) cacheHit=0.0ms(5x) codecHit=0.0ms(2x)
   8. CfgCodec[AuthEvent]: total=33.4ms  topDerive=30.8ms(1x) summonIgnoring=10.1ms(5x) tryBuiltin=0.7ms(5x) subTraitDetect=0.7ms(5x) cheapTypeKey=0.0ms(5x) codecHit=0.0ms(5x)
   9. CfgCodec[CustomerRecord]: total=31.2ms  topDerive=30.1ms(1x) tryBuiltin=11.3ms(7x) summonIgnoring=8.7ms(5x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(8x) codecHit=0.0ms(5x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x)
  10. CfgCodec[ChatMessage]: total=29.9ms  topDerive=29.0ms(1x) tryBuiltin=15.5ms(7x) summonIgnoring=6.3ms(3x) cheapTypeKey=0.1ms(8x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(3x) builtinHit=0.0ms(4x) cacheHit=0.0ms(1x)
  11. CfgCodec[MetricSeries]: total=26.2ms  topDerive=24.9ms(1x) tryBuiltin=11.5ms(4x) summonIgnoring=4.4ms(1x) cheapTypeKey=0.1ms(4x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(1x) builtinHit=0.0ms(3x)
  12. CfgCodec[Report]: total=25.8ms  topDerive=24.5ms(1x) tryBuiltin=11.8ms(4x) summonIgnoring=2.9ms(1x) cheapTypeKey=0.1ms(6x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(3x) cacheHit=0.0ms(2x) codecHit=0.0ms(1x)
  13. CfgCodec[AccessPolicy]: total=25.4ms  topDerive=23.9ms(1x) tryBuiltin=12.7ms(3x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(5x) builtinHit=0.0ms(3x) cacheHit=0.0ms(2x)
  14. CfgCodec[ReportQuery]: total=22.5ms  topDerive=21.1ms(1x) tryBuiltin=12.4ms(3x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(4x) builtinHit=0.0ms(3x) cacheHit=0.0ms(1x)
  15. CfgCodec[FunnelReport]: total=22.3ms  topDerive=21.1ms(1x) tryBuiltin=11.8ms(3x) summonIgnoring=2.3ms(1x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(4x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x) codecHit=0.0ms(1x)

--- Hot Types (>50ms) ---
  CfgCodec[Role]: 86ms
  CfgCodec[UserId]: 69ms
  CfgCodec[WorkflowStep]: 65ms
======================================================================
```

## v0.14.0

**Date:** 2026-03-06 13:05:44 UTC | **SHA:** `3f1b13e`

### Compile Time — Auto Derivation

```
Compile-time benchmark: circe-sanely-auto vs circe-generic (N=5)
Benchmark suite: benchmark
Method: Mill daemon, hyperfine with --warmup 1, --runs 5
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark.sanely
  Time (mean ± σ):      7.388 s ±  1.636 s    [User: 0.060 s, System: 0.101 s]
  Range (min … max):    6.111 s … 10.083 s    5 runs
 
Benchmark 2: benchmark.generic
  Time (mean ± σ):     15.900 s ±  0.397 s    [User: 0.092 s, System: 0.200 s]
  Range (min … max):   15.563 s … 16.588 s    5 runs
 
  Warning: The first benchmarking run for this command was significantly slower than the rest (16.588 s). This could be caused by (filesystem) caches that were not filled until after the first run. You are already using both the '--warmup' option as well as the '--prepare' option. Consider re-running the benchmark on a quiet system. Maybe it was a random outlier. Alternatively, consider increasing the warmup count.
 
Summary
  benchmark.sanely ran
    2.15 ± 0.48 times faster than benchmark.generic
```

### Compile Time — Configured Derivation

```
Compile-time benchmark: circe-sanely-auto vs circe-core configured derivation (N=5)
Benchmark suite: benchmark-configured
Method: Mill daemon, hyperfine with --warmup 1, --runs 5
================================================================
Warming up Mill daemon and source dependencies...
Running hyperfine benchmark...

Benchmark 1: benchmark-configured.sanely
  Time (mean ± σ):      5.796 s ±  1.289 s    [User: 0.051 s, System: 0.089 s]
  Range (min … max):    4.387 s …  7.861 s    5 runs
 
Benchmark 2: benchmark-configured.generic
  Time (mean ± σ):      7.210 s ±  0.414 s    [User: 0.056 s, System: 0.102 s]
  Range (min … max):    6.863 s …  7.903 s    5 runs
 
Summary
  benchmark-configured.sanely ran
    1.24 ± 0.29 times faster than benchmark-configured.generic
```

### Runtime Performance

```
Building runtime benchmark...

./mill benchmark-runtime.run 5 5
135] benchmark-runtime.run
Runtime benchmark: circe-jawn vs circe+jsoniter vs jsoniter-scala
  warmup=5 iterations=5 (each 1 second)
  payload: 1224 bytes (circe), 1224 bytes (jsoniter)

Reading (bytes -> case class):
----------------------------------------------------------------------
  circe-jawn                     100680 ops/sec  (min=99588, max=100830)
  circe+jsoniter                 129367 ops/sec  (min=129311, max=129604)
  jsoniter-scala                 445346 ops/sec  (min=442983, max=447021)

  circe+jsoniter             1.28x vs circe-jawn
  jsoniter-scala             4.42x vs circe-jawn

Writing (case class -> bytes):
----------------------------------------------------------------------
  circe-printer                   77574 ops/sec  (min=77283, max=78228)
  circe+jsoniter                  86204 ops/sec  (min=84931, max=86450)
  jsoniter-scala                 448508 ops/sec  (min=446815, max=448971)

  circe+jsoniter             1.11x vs circe-printer
  jsoniter-scala             5.78x vs circe-printer
135/135, SUCCESS] ./mill benchmark-runtime.run 5 5 61s
```

### Macro Profile — Auto

```
======================================================================
SANELY MACRO PROFILE (308 expansions, 5102ms total)
======================================================================

--- By Kind ---
  Decoder          154 expansions    2457.7ms  avg 15.96ms
  Encoder          154 expansions    2643.9ms  avg 17.17ms

--- Category Breakdown ---
  summonIgnoring         2193.6ms ( 43.0%)     660 calls  avg 3.32ms
  derive                 1919.4ms ( 37.6%)     586 calls  avg 3.28ms
  summonMirror            236.5ms (  4.6%)     586 calls  avg 0.40ms
  subTraitDetect          135.9ms (  2.7%)     336 calls  avg 0.40ms
  tryBuiltin               89.3ms (  1.8%)    1366 calls  avg 0.07ms
  cheapTypeKey             10.5ms (  0.2%)    3080 calls  avg 0.00ms
  builtinHit                0.0ms (  0.0%)     706 calls  avg 0.00ms
  cacheHit                           1714 hits
  overhead                516.4ms ( 10.1%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. Encoder[Ticket]: total=171.1ms  summonIgnoring=91.2ms(27x) derive=64.7ms(22x) summonMirror=8.1ms(22x) subTraitDetect=5.2ms(18x) tryBuiltin=1.5ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   2. Decoder[Article]: total=155.4ms  summonIgnoring=111.9ms(14x) derive=47.9ms(12x) summonMirror=4.6ms(12x) subTraitDetect=2.1ms(6x) tryBuiltin=1.2ms(18x) cheapTypeKey=0.2ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   3. Encoder[OrderSummary]: total=136.6ms  derive=106.6ms(18x) summonIgnoring=50.0ms(18x) summonMirror=24.2ms(18x) subTraitDetect=7.1ms(14x) tryBuiltin=1.0ms(21x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(3x) cacheHit=0.0ms(45x)
   4. Decoder[AlertInstance]: total=132.2ms  derive=211.6ms(19x) summonIgnoring=36.8ms(20x) summonMirror=27.1ms(19x) subTraitDetect=4.0ms(13x) tryBuiltin=1.3ms(26x) cheapTypeKey=0.1ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)
   5. Encoder[Article]: total=122.2ms  summonIgnoring=84.1ms(14x) derive=39.6ms(12x) summonMirror=4.9ms(12x) subTraitDetect=3.0ms(6x) tryBuiltin=1.0ms(18x) cheapTypeKey=0.2ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   6. Decoder[Sprint]: total=113.6ms  summonIgnoring=107.6ms(1x) tryBuiltin=0.2ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   7. Encoder[Sprint]: total=113.4ms  summonIgnoring=108.3ms(1x) tryBuiltin=0.2ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   8. Decoder[Ticket]: total=109.5ms  summonIgnoring=68.9ms(27x) derive=60.8ms(22x) summonMirror=5.4ms(22x) subTraitDetect=4.3ms(18x) tryBuiltin=1.1ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   9. Encoder[AlertInstance]: total=100.6ms  derive=143.8ms(19x) summonIgnoring=38.4ms(20x) summonMirror=9.9ms(19x) subTraitDetect=6.4ms(13x) tryBuiltin=1.5ms(26x) cheapTypeKey=0.1ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)
  10. Decoder[OrderSummary]: total=94.0ms  derive=104.5ms(18x) summonIgnoring=26.1ms(18x) summonMirror=7.0ms(18x) subTraitDetect=5.0ms(14x) tryBuiltin=0.9ms(21x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(3x) cacheHit=0.0ms(45x)
  11. Decoder[ProductInfo]: total=88.3ms  summonIgnoring=68.5ms(6x) derive=9.8ms(4x) summonMirror=1.2ms(4x) tryBuiltin=0.9ms(9x) cheapTypeKey=0.1ms(20x) builtinHit=0.0ms(3x) cacheHit=0.0ms(11x)
  12. Encoder[HttpMethod]: total=87.7ms  summonIgnoring=54.6ms(5x) derive=11.2ms(5x) subTraitDetect=4.0ms(5x) summonMirror=3.2ms(5x) tryBuiltin=0.5ms(6x) cheapTypeKey=0.1ms(10x) builtinHit=0.0ms(1x) cacheHit=0.0ms(4x)
  13. Encoder[Ticket]: total=83.1ms  derive=47.9ms(22x) summonIgnoring=44.2ms(27x) summonMirror=5.7ms(22x) subTraitDetect=3.4ms(18x) tryBuiltin=0.8ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
  14. Decoder[Ticket]: total=82.5ms  derive=51.0ms(22x) summonIgnoring=42.3ms(27x) summonMirror=5.3ms(22x) subTraitDetect=4.0ms(18x) tryBuiltin=0.7ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
  15. Encoder[DeploymentSpec]: total=82.3ms  derive=76.0ms(8x) summonIgnoring=53.6ms(11x) summonMirror=4.2ms(8x) subTraitDetect=2.1ms(4x) tryBuiltin=1.0ms(14x) cheapTypeKey=0.1ms(33x) builtinHit=0.0ms(3x) cacheHit=0.0ms(19x)

--- Hot Types (>50ms) ---
  Encoder[Ticket]: 171ms
  Decoder[Article]: 155ms
  Encoder[OrderSummary]: 137ms
  Decoder[AlertInstance]: 132ms
  Encoder[Article]: 122ms
======================================================================
```

### Macro Profile — Configured

```
======================================================================
SANELY MACRO PROFILE (230 expansions, 2438ms total)
======================================================================

--- By Kind ---
  CfgCodec         230 expansions    2438.2ms  avg 10.60ms

--- Category Breakdown ---
  topDerive              2168.0ms ( 88.9%)     230 calls  avg 9.43ms
  summonIgnoring          748.8ms ( 30.7%)     205 calls  avg 3.65ms
  tryBuiltin               81.5ms (  3.3%)     493 calls  avg 0.17ms
  resolveDefaults          25.4ms (  1.0%)     214 calls  avg 0.12ms
  subTraitDetect            7.0ms (  0.3%)      69 calls  avg 0.10ms
  cheapTypeKey              4.3ms (  0.2%)     820 calls  avg 0.01ms
  builtinHit                0.0ms (  0.0%)     345 calls  avg 0.00ms
  cacheHit                            327 hits
  codecHit                  0.0ms (  0.0%)     118 calls  avg 0.00ms
  overhead               -596.8ms (-24.5%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. CfgCodec[Role]: total=110.0ms  topDerive=108.1ms(1x) summonIgnoring=91.6ms(3x) cheapTypeKey=1.4ms(4x) tryBuiltin=1.3ms(2x) resolveDefaults=0.2ms(1x) builtinHit=0.0ms(1x) cacheHit=0.0ms(2x)
   2. CfgCodec[UserId]: total=65.8ms  topDerive=42.4ms(1x) resolveDefaults=1.8ms(1x) tryBuiltin=0.6ms(1x) cheapTypeKey=0.5ms(1x) builtinHit=0.0ms(1x)
   3. CfgCodec[Article]: total=54.1ms  topDerive=53.1ms(1x) summonIgnoring=37.8ms(11x) tryBuiltin=0.8ms(8x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(11x) codecHit=0.0ms(5x) builtinHit=0.0ms(1x) cacheHit=0.0ms(3x)
   4. CfgCodec[Product]: total=45.1ms  topDerive=44.1ms(1x) summonIgnoring=33.5ms(12x) tryBuiltin=0.8ms(10x) cheapTypeKey=0.1ms(10x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(2x) codecHit=0.0ms(6x)
   5. CfgCodec[ChatMessage]: total=43.9ms  topDerive=43.0ms(1x) summonIgnoring=34.3ms(12x) tryBuiltin=1.0ms(7x) cheapTypeKey=0.1ms(8x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(3x) builtinHit=0.0ms(1x) cacheHit=0.0ms(1x)
   6. CfgCodec[MediaType]: total=40.3ms  topDerive=39.0ms(1x) summonIgnoring=30.9ms(4x) tryBuiltin=1.2ms(4x) subTraitDetect=0.4ms(4x) cheapTypeKey=0.0ms(4x) codecHit=0.0ms(4x)
   7. CfgCodec[Invoice]: total=38.0ms  topDerive=37.1ms(1x) summonIgnoring=27.0ms(11x) tryBuiltin=0.5ms(6x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(11x) builtinHit=0.0ms(1x) cacheHit=0.0ms(5x) codecHit=0.0ms(2x)
   8. CfgCodec[WorkflowStep]: total=37.5ms  topDerive=36.7ms(1x) summonIgnoring=28.7ms(8x) tryBuiltin=1.1ms(6x) cheapTypeKey=0.1ms(7x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(2x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x)
   9. CfgCodec[SecurityConfig]: total=35.0ms  topDerive=33.6ms(1x) summonIgnoring=24.5ms(3x) tryBuiltin=0.8ms(3x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(4x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x)
  10. CfgCodec[MetricSeries]: total=33.9ms  topDerive=32.3ms(1x) summonIgnoring=23.6ms(4x) tryBuiltin=1.3ms(4x) cheapTypeKey=0.1ms(4x) resolveDefaults=0.1ms(1x) codecHit=0.0ms(1x) builtinHit=0.0ms(2x)
  11. CfgCodec[CustomerRecord]: total=33.9ms  topDerive=32.9ms(1x) summonIgnoring=23.6ms(8x) tryBuiltin=0.5ms(7x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(8x) codecHit=0.0ms(5x) builtinHit=0.0ms(1x) cacheHit=0.0ms(1x)
  12. CfgCodec[AuthEvent]: total=33.7ms  topDerive=31.2ms(1x) summonIgnoring=10.7ms(5x) tryBuiltin=0.7ms(5x) subTraitDetect=0.7ms(5x) cheapTypeKey=0.0ms(5x) codecHit=0.0ms(5x)
  13. CfgCodec[Permission]: total=32.3ms  topDerive=11.9ms(1x) tryBuiltin=0.3ms(1x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.0ms(3x) builtinHit=0.0ms(1x) cacheHit=0.0ms(2x)
  14. CfgCodec[AccessPolicy]: total=31.9ms  topDerive=30.7ms(1x) summonIgnoring=19.0ms(3x) tryBuiltin=0.8ms(3x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(5x) builtinHit=0.0ms(2x) cacheHit=0.0ms(2x)
  15. CfgCodec[ReportQuery]: total=31.7ms  topDerive=30.3ms(1x) summonIgnoring=21.5ms(3x) tryBuiltin=1.2ms(3x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(4x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x)

--- Hot Types (>50ms) ---
  CfgCodec[Role]: 110ms
  CfgCodec[UserId]: 66ms
  CfgCodec[Article]: 54ms
======================================================================
```

## v0.13.0

**Date:** 2026-03-06 06:01:14 UTC | **SHA:** `4e281df`

### Compile Time — Auto Derivation

```
Compile-time benchmark: circe-sanely-auto vs circe-generic (N=5)
Benchmark suite: benchmark
================================================================
  benchmark.sanely run 1: 65.47s
  benchmark.sanely run 2: 10.47s
  benchmark.sanely run 3: 9.05s
  benchmark.sanely run 4: 7.70s
  benchmark.sanely run 5: 6.69s
benchmark.sanely median: 9.05s (of 65.47 10.47 9.05 7.70 6.69)

  benchmark.generic run 1: 18.42s
  benchmark.generic run 2: 17.06s
  benchmark.generic run 3: 16.39s
  benchmark.generic run 4: 16.22s
  benchmark.generic run 5: 16.06s
benchmark.generic median: 16.39s (of 18.42 17.06 16.39 16.22 16.06)

```

### Compile Time — Configured Derivation

```
Compile-time benchmark: circe-sanely-auto vs circe-generic (N=5)
Benchmark suite: benchmark-configured
================================================================
  benchmark-configured.sanely run 1: 62.46s
  benchmark-configured.sanely run 2: 8.33s
  benchmark-configured.sanely run 3: 6.40s
  benchmark-configured.sanely run 4: 5.50s
  benchmark-configured.sanely run 5: 4.66s
benchmark-configured.sanely median: 6.40s (of 62.46 8.33 6.40 5.50 4.66)

  benchmark-configured.generic run 1: 9.91s
  benchmark-configured.generic run 2: 7.68s
  benchmark-configured.generic run 3: 7.26s
  benchmark-configured.generic run 4: 7.15s
  benchmark-configured.generic run 5: 7.00s
benchmark-configured.generic median: 7.26s (of 9.91 7.68 7.26 7.15 7.00)

```

### Runtime Performance

```
Building runtime benchmark...

./mill benchmark-runtime.run 5 5
135] benchmark-runtime.run
Runtime benchmark: circe-jawn vs circe+jsoniter vs jsoniter-scala
  warmup=5 iterations=5 (each 1 second)
  payload: 1224 bytes (circe), 1224 bytes (jsoniter)

Reading (bytes -> case class):
----------------------------------------------------------------------
  circe-jawn                     103092 ops/sec  (min=98122, max=103212)
  circe+jsoniter                 129858 ops/sec  (min=129107, max=130233)
  jsoniter-scala                 453054 ops/sec  (min=450319, max=454494)

  circe+jsoniter             1.26x vs circe-jawn
  jsoniter-scala             4.39x vs circe-jawn

Writing (case class -> bytes):
----------------------------------------------------------------------
  circe-printer                   79058 ops/sec  (min=78884, max=79182)
  circe+jsoniter                  88973 ops/sec  (min=88563, max=89441)
  jsoniter-scala                 444432 ops/sec  (min=443989, max=446034)

  circe+jsoniter             1.13x vs circe-printer
  jsoniter-scala             5.62x vs circe-printer
135/135, SUCCESS] ./mill benchmark-runtime.run 5 5 61s
```

### Macro Profile — Auto

```
======================================================================
SANELY MACRO PROFILE (308 expansions, 5896ms total)
======================================================================

--- By Kind ---
  Decoder          154 expansions    2893.0ms  avg 18.79ms
  Encoder          154 expansions    3002.7ms  avg 19.50ms

--- Category Breakdown ---
  summonIgnoring         2620.1ms ( 44.4%)     660 calls  avg 3.97ms
  derive                 2102.2ms ( 35.7%)     586 calls  avg 3.59ms
  summonMirror            219.0ms (  3.7%)     586 calls  avg 0.37ms
  subTraitDetect          149.5ms (  2.5%)     336 calls  avg 0.44ms
  tryBuiltin               94.0ms (  1.6%)    1366 calls  avg 0.07ms
  cheapTypeKey             10.0ms (  0.2%)    3080 calls  avg 0.00ms
  builtinHit                0.0ms (  0.0%)     706 calls  avg 0.00ms
  cacheHit                           1714 hits
  overhead                700.9ms ( 11.9%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. Decoder[Sprint]: total=197.5ms  summonIgnoring=189.7ms(1x) tryBuiltin=0.2ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   2. Encoder[Ticket]: total=162.8ms  derive=100.0ms(22x) summonIgnoring=96.0ms(27x) summonMirror=10.8ms(22x) subTraitDetect=8.0ms(18x) tryBuiltin=1.7ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   3. Encoder[Article]: total=161.5ms  summonIgnoring=113.0ms(14x) derive=51.1ms(12x) summonMirror=6.6ms(12x) subTraitDetect=3.2ms(6x) tryBuiltin=1.2ms(18x) cheapTypeKey=0.2ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   4. Decoder[AlertInstance]: total=155.5ms  derive=139.8ms(19x) summonIgnoring=95.8ms(20x) summonMirror=7.0ms(19x) subTraitDetect=4.6ms(13x) tryBuiltin=1.4ms(26x) cheapTypeKey=0.2ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)
   5. Encoder[Sprint]: total=155.2ms  summonIgnoring=146.0ms(1x) tryBuiltin=0.2ms(3x) cheapTypeKey=0.0ms(6x) builtinHit=0.0ms(2x) cacheHit=0.0ms(3x)
   6. Decoder[Ticket]: total=152.9ms  summonIgnoring=94.9ms(27x) derive=88.0ms(22x) summonMirror=7.7ms(22x) subTraitDetect=4.8ms(18x) tryBuiltin=1.5ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   7. Decoder[Ticket]: total=150.2ms  derive=155.0ms(22x) summonIgnoring=52.6ms(27x) summonMirror=7.8ms(22x) subTraitDetect=4.9ms(18x) tryBuiltin=1.1ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
   8. Decoder[Article]: total=147.1ms  summonIgnoring=106.1ms(14x) derive=44.4ms(12x) summonMirror=4.6ms(12x) subTraitDetect=2.0ms(6x) tryBuiltin=1.1ms(18x) cheapTypeKey=0.2ms(48x) builtinHit=0.0ms(4x) cacheHit=0.0ms(30x)
   9. Encoder[HttpMethod]: total=129.2ms  summonIgnoring=86.0ms(5x) derive=14.4ms(5x) subTraitDetect=5.8ms(5x) summonMirror=3.9ms(5x) tryBuiltin=0.5ms(6x) cheapTypeKey=0.1ms(10x) builtinHit=0.0ms(1x) cacheHit=0.0ms(4x)
  10. Encoder[ProductInfo]: total=122.4ms  summonIgnoring=102.6ms(6x) derive=9.5ms(4x) summonMirror=1.8ms(4x) tryBuiltin=0.9ms(9x) cheapTypeKey=0.1ms(20x) builtinHit=0.0ms(3x) cacheHit=0.0ms(11x)
  11. Encoder[Ticket]: total=107.3ms  derive=65.2ms(22x) summonIgnoring=54.7ms(27x) summonMirror=6.7ms(22x) subTraitDetect=4.4ms(18x) tryBuiltin=1.0ms(31x) cheapTypeKey=0.2ms(66x) builtinHit=0.0ms(4x) cacheHit=0.0ms(35x)
  12. Encoder[OrderSummary]: total=104.0ms  derive=113.9ms(18x) summonIgnoring=26.3ms(18x) summonMirror=12.2ms(18x) subTraitDetect=7.4ms(14x) tryBuiltin=1.1ms(21x) cheapTypeKey=0.1ms(66x) builtinHit=0.0ms(3x) cacheHit=0.0ms(45x)
  13. Encoder[AlertInstance]: total=99.7ms  derive=144.4ms(19x) summonIgnoring=37.4ms(20x) summonMirror=9.8ms(19x) subTraitDetect=6.5ms(13x) tryBuiltin=1.2ms(26x) cheapTypeKey=0.1ms(53x) builtinHit=0.0ms(6x) cacheHit=0.0ms(27x)
  14. Decoder[OrderSummary]: total=92.5ms  derive=102.4ms(18x) summonIgnoring=25.8ms(18x) summonMirror=6.9ms(18x) subTraitDetect=5.1ms(14x) tryBuiltin=0.8ms(21x) cheapTypeKey=0.1ms(66x) builtinHit=0.0ms(3x) cacheHit=0.0ms(45x)
  15. Encoder[Wide22A]: total=88.4ms  tryBuiltin=2.2ms(4x) cheapTypeKey=0.6ms(22x) builtinHit=0.0ms(4x) cacheHit=0.0ms(18x)

--- Optimization Insights ---
  * summonIgnoring is 44% of total time (2620ms, 660 calls). This is the compiler's implicit search. Reducing calls via cross-expansion caching (lazy val emission) would have the biggest impact.
  * Derivation (AST construction) is 36% of total (2102ms). Extracting more logic to SanelyRuntime could reduce generated AST size.
  * Cache hit ratio: 75% (1714 hits vs 586 derivations). Intra-expansion caching is working well.
  * Hot types (>50ms): Encoder[Wide22A] (88ms), Decoder[Wide22F] (60ms), Encoder[Wide22H] (72ms), Encoder[HttpMethod] (129ms), Encoder[UserAccount] (54ms)
======================================================================
```

### Macro Profile — Configured

```
======================================================================
SANELY MACRO PROFILE (230 expansions, 2724ms total)
======================================================================

--- By Kind ---
  CfgCodec         230 expansions    2724.4ms  avg 11.85ms

--- Category Breakdown ---
  topDerive              2476.3ms ( 90.9%)     230 calls  avg 10.77ms
  summonIgnoring          956.0ms ( 35.1%)     294 calls  avg 3.25ms
  tryBuiltin              142.3ms (  5.2%)     493 calls  avg 0.29ms
  resolveDefaults          24.7ms (  0.9%)     214 calls  avg 0.12ms
  subTraitDetect            7.7ms (  0.3%)      69 calls  avg 0.11ms
  cheapTypeKey              4.9ms (  0.2%)     820 calls  avg 0.01ms
  builtinHit                0.0ms (  0.0%)     345 calls  avg 0.00ms
  cacheHit                            327 hits
  overhead               -887.5ms (-32.6%)  (type checks, AST, etc)

--- Top 15 Slowest (total time) ---
   1. CfgCodec[Role]: total=97.6ms  topDerive=95.8ms(1x) summonIgnoring=80.7ms(2x) cheapTypeKey=1.2ms(4x) tryBuiltin=1.1ms(2x) resolveDefaults=0.3ms(1x) builtinHit=0.0ms(1x) cacheHit=0.0ms(2x)
   2. CfgCodec[UserId]: total=59.2ms  topDerive=41.4ms(1x) resolveDefaults=1.3ms(1x) tryBuiltin=0.4ms(1x) cheapTypeKey=0.3ms(1x) builtinHit=0.0ms(1x)
   3. CfgCodec[MeetingRecord]: total=58.4ms  topDerive=57.4ms(1x) tryBuiltin=37.8ms(3x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(5x) builtinHit=0.0ms(3x) cacheHit=0.0ms(2x)
   4. CfgCodec[Article]: total=55.5ms  topDerive=54.5ms(1x) summonIgnoring=39.7ms(14x) tryBuiltin=0.8ms(8x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(11x) builtinHit=0.0ms(1x) cacheHit=0.0ms(3x)
   5. CfgCodec[AuthEvent]: total=53.4ms  topDerive=50.7ms(1x) summonIgnoring=26.6ms(10x) subTraitDetect=0.8ms(5x) tryBuiltin=0.7ms(5x) cheapTypeKey=0.1ms(5x)
   6. CfgCodec[Invoice]: total=52.1ms  topDerive=50.7ms(1x) summonIgnoring=36.6ms(10x) tryBuiltin=0.8ms(6x) resolveDefaults=0.2ms(1x) cheapTypeKey=0.1ms(11x) builtinHit=0.0ms(1x) cacheHit=0.0ms(5x)
   7. CfgCodec[PipelineStage]: total=46.2ms  topDerive=45.5ms(1x) tryBuiltin=25.8ms(4x) cheapTypeKey=0.1ms(4x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(4x)
   8. CfgCodec[ChatMessage]: total=42.7ms  topDerive=41.9ms(1x) summonIgnoring=33.6ms(12x) tryBuiltin=0.8ms(7x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(8x) builtinHit=0.0ms(1x) cacheHit=0.0ms(1x)
   9. CfgCodec[ContentStatus]: total=41.0ms  topDerive=39.6ms(1x) summonIgnoring=26.9ms(10x) subTraitDetect=0.5ms(5x) tryBuiltin=0.4ms(5x) cheapTypeKey=0.0ms(5x)
  10. CfgCodec[Product]: total=40.9ms  topDerive=40.1ms(1x) summonIgnoring=30.5ms(16x) tryBuiltin=0.8ms(10x) cheapTypeKey=0.1ms(10x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(2x)
  11. CfgCodec[WidgetType]: total=36.5ms  topDerive=34.9ms(1x) summonIgnoring=25.1ms(10x) subTraitDetect=0.7ms(5x) tryBuiltin=0.4ms(5x) cheapTypeKey=0.0ms(5x)
  12. CfgCodec[AccessDecision]: total=35.2ms  topDerive=33.4ms(1x) summonIgnoring=24.9ms(8x) subTraitDetect=0.6ms(4x) tryBuiltin=0.4ms(4x) cheapTypeKey=0.0ms(4x)
  13. CfgCodec[MetricSeries]: total=34.4ms  topDerive=33.1ms(1x) summonIgnoring=23.4ms(4x) tryBuiltin=1.4ms(4x) cheapTypeKey=0.1ms(4x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(2x)
  14. CfgCodec[CustomerRecord]: total=33.4ms  topDerive=32.5ms(1x) summonIgnoring=24.0ms(12x) tryBuiltin=0.6ms(7x) resolveDefaults=0.1ms(1x) cheapTypeKey=0.0ms(8x) builtinHit=0.0ms(1x) cacheHit=0.0ms(1x)
  15. CfgCodec[WorkflowStep]: total=32.4ms  topDerive=31.7ms(1x) summonIgnoring=24.7ms(8x) tryBuiltin=1.0ms(6x) cheapTypeKey=0.1ms(7x) resolveDefaults=0.1ms(1x) builtinHit=0.0ms(2x) cacheHit=0.0ms(1x)

--- Optimization Insights ---
  * Hot types (>50ms): CfgCodec[UserId] (59ms), CfgCodec[Role] (98ms), CfgCodec[AuthEvent] (53ms), CfgCodec[Invoice] (52ms), CfgCodec[Article] (56ms)
======================================================================
```
