# Benchmark History

Automated benchmarks run on `ubuntu-latest` (GitHub Actions shared runners) after each release.

**Note on CI vs local numbers:** Shared runners have noisy neighbors, no CPU pinning, and variable clock speeds. Absolute throughput is ~50% lower than dedicated hardware. **Ratios between libraries** (e.g. sanely-jsoniter vs circe-jawn) are the meaningful metric — they remain stable across environments. The README reports numbers from a dedicated Apple M3 Max; these CI results track regressions between releases.

<!-- BENCHMARK ENTRIES -->

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
