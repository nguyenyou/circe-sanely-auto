# Benchmark History

Automated benchmarks run on `ubuntu-latest` after each release.

<!-- BENCHMARK ENTRIES -->

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
