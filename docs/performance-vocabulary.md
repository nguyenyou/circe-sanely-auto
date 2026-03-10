# Performance Vocabulary

Terms used across the circe, jsoniter-scala, and Scala/JVM communities when discussing performance. Use these in issues, PRs, benchmarks, and design conversations so we speak the same language.

---

## Compile-Time Performance

### Typeclass derivation cost

- **Implicit search chain / implicit search tree** — the compiler recursively resolving givens/implicits for each field and subtype. In circe-generic (Scala 2), Shapeless's `Generic`/`Lazy` layers make these chains deep and slow. The compiler "can't see through the shapeless.Lazy layers." This is the primary reason circe-generic auto is slow to compile
- **Implicit search time** — Odersky measured 50-65% of total type-checking time spent in implicit search. The dominant compile-time cost for typeclass-heavy code
- **Failed implicit search** — a search that doesn't find a match. Surprisingly expensive and a major bottleneck identified by `scalac-profiling`. The compiler may explore many dead-end candidates before giving up
- **Diverging implicit expansion** — the compiler giving up on implicit search because it detects (or suspects) infinite recursion. Common with recursive ADTs under circe-generic. The error message is often unhelpful; the `splain` compiler plugin was recommended for better diagnostics
- **Repeated materialization** — the core inefficiency where the compiler re-derives the same instance across different implicit searches because prior expansions can't be shared. What our single-pass expansion eliminates
- **Macro expansion** — the compiler executing macro code to generate ASTs. In our approach, `Expr.summonIgnoring` resolves all instances within a single expansion, avoiding implicit search chains entirely
- **Recursive macro expansion** — macro methods expanding macro methods as part of derivation. "Costly on compiletime" because each expansion is a fresh macro invocation. Semi-auto derivation acts as a "caching strategy" that breaks the chain
- **Suspended compilation** — in Scala 3, files containing unresolved macro calls are suspended until dependencies compile. The compiler repeats compilation stages until all sources compile or no progress is made
- **`transparent inline given` waste** — a known Scala 3 issue where the compiler "expands some transparent macro — perhaps triggering implicit searches — burns a lot of CPU to construct a whole correct expression, and then flushes it down the toilet" because a better match was found. No error message, no indication
- **Inlining budget / `-Xmax-inlines`** — the compiler's limit on recursive inline expansion depth. Complex derivation hierarchies can exhaust this, requiring the flag to be bumped
- **Tree growth** — Odersky measured ASTs growing from ~25,000 nodes (parser) to ~150,000 nodes (cleanup); 140% growth from parsing to cleanup. Macro-generated code contributes significantly to this

### Zinc and incremental compilation

- **Zinc incremental compilation** — the Scala build tool's ability to recompile only what changed. Coarser codegen means more recompilation when types change
- **Compilation unit** — the chunk of code the compiler processes together. Monolithic codegen = one big unit; compositional = many small units
- **Incremental compilation granularity** — how fine-grained Zinc's recompilation is. Finer = faster rebuilds when a single type changes

### Compiler phases (Scala 3)

Exact phase names from `scala3-compiler -Xshow-phases`. The ones most relevant to us:

- **`typer`** — where implicit/given resolution happens. Typically 50-70% of compilation. The bottleneck for shapeless-based derivation
- **`inlining`** — "inline and execute macros." Where our code runs. The bottleneck for macro-based derivation. Inspect output with `-Xprint:postInlining`
- **`postInlining`** — "add mirror support for inlined code." Runs after macro expansion
- **`patternMatcher`** — pattern matching compilation. Relevant when macros generate match expressions
- **`erasure`** — "rewrite types to JVM model, erasing all type parameters." Where generic type information is lost
- **`genBCode`** — translating typed ASTs to JVM bytecode. Rarely the bottleneck but affected by AST size

### Profiling tools

- **`scalac-profiling`** — Scala Center compiler plugin for profiling implicit search and macro expansion. The standard tool for diagnosing compilation bottlenecks
- **`-Xlog-implicits`** — compiler flag to trace implicit resolution (noisy but diagnostic)
- **`SANELY_PROFILE=true`** — our macro-level profiling via `MacroTimer`. Use with `analyze_profile.py`

### Questions to ask

- "What's the **compile-time cost** of this change?"
- "Does this affect **incremental compilation granularity**?"
- "How much **AST** are we generating per type?"
- "Does this change the **macro expansion depth**? Do we need to bump `-Xmax-inlines`?"
- "What does the **macro profile** show? Where is time spent — `summonIgnoring`, `deriveProduct`, `resolveDefaults`?"
- "Are we triggering **recursive macro expansion**, or is this resolved in a single pass?"

### Describing results

- "Compile time went from **X to Y seconds** across N types" (absolute)
- "This adds **Z ms per type** to macro expansion" (marginal cost)
- "Zinc now recompiles **M files** instead of **N files** when type T changes" (incremental impact)

### Compile-time hierarchy (community consensus)

From fastest to slowest: **manual codecs / jsoniter-scala > circe-derivation (macros, no shapeless) > circe-generic semi-auto ~ magnolia semi-auto > circe-generic auto > circe-generic-extras**. Our library targets the top of this hierarchy.

### How circe users describe the problem

These are the exact phrases from circe issues and community discussions. Use them to ensure we speak the same language:

- "**Exponentially increasing compile times** when using codec derivation" — circe issue #339 title
- "**Typer phase takes 6.9 seconds**" (auto) vs "**114 ms**" (semi-auto) — circe issue #2051, the canonical measurement showing auto's cost
- "An automatically derived decoder will be **generated for each implicit call site**" — why auto is slow: no caching across call sites
- "**Codecs are derived as needed by the usage**" (auto) vs "**you only have one place where a codec for a given type will be derived: at definition site**" (semi-auto) — kubukoz's explanation of the fundamental difference
- "The compiler **repeats the materialization** of implicit instances" — scalac-profiling blog, describing the core inefficiency
- "**Horribly slow compile times**" — circe-derivation README, motivating non-shapeless derivation
- "**May stack overflow during compilation** for large or deeply-nested case classes and sealed trait hierarchies" — circe known-issues docs
- "**knownDirectSubclasses** known to fail" — the Shapeless/Scala Reflect method that causes sealed trait issues
- "Runtime performance of derived instances **should be practically indistinguishable from manually-defined instances**" — circe docs, the runtime performance contract
- "**Drop-in replacement**" — how circe-derivation and our library position their APIs relative to circe-generic

### Quantitative reference points (from community benchmarks)

These help calibrate expectations when discussing compile times:

| Scenario | Time | Source |
|---|---|---|
| Auto derivation, typer phase | 6.9s | circe #2051 |
| Semi-auto derivation, typer phase | ~110ms | circe #2051 |
| 200 types, circe auto | ~68s | Scala Users forum |
| 200 types, jsoniter-scala | ~32s | Scala Users forum |
| Auto → semi-auto migration | "5 min down to under 1 min" | Scala Users forum |
| akka-http-json with circe auto | ~100s compile | akka-http-json #47 |
| akka-http-json after fix | ~3s compile | akka-http-json #47 |

---

## Runtime Performance

### Throughput and latency

- **Throughput** (ops/sec) — how many operations per second. Higher is better. What we measure in `bench-runtime.sh`
- **Latency** — how long a single operation takes. Lower is better
  - **p50** — median latency (50th percentile)
  - **p99** — tail latency (99th percentile) — often more important than p50 in production
  - **p99.9** — extreme tail, matters for SLA-bound services
- **Steady-state throughput** — performance after JIT warmup is complete. The number you report from benchmarks
- **Warmup period / warmup iterations** — the time before JIT has profiled and optimized hot paths. Not representative of final performance

### JIT and the JVM

- **JIT compilation** (Just-In-Time) — the JVM compiling bytecode to native machine code at runtime based on profiling data
- **Tiered compilation** — JVM compiles hot methods through increasingly aggressive optimization tiers (C1 → C2 on HotSpot)
- **On-stack replacement (OSR)** — JIT replaces a running method's code mid-execution (e.g., optimizing a hot loop while it's running)
- **Deoptimization / uncommon trap** — JIT made an assumption (e.g., "this call site is monomorphic") that turned out wrong; falls back to interpreted/generic code

### Inline caching and devirtualization

This is the standard JVM terminology for how virtual calls are optimized. These terms appear throughout JVM performance literature (Shipilev, Tene, Odersky).

- **Inline cache** — the JVM records which receiver type was seen at a call site and caches the resolved method. Avoids repeated vtable lookups on subsequent calls
- **Polymorphic inline cache (PIC)** — an inline cache that stores multiple (type → method) entries. Handles call sites that see a few different types
- **Monomorphic call site** — always dispatches to the same type. JIT can devirtualize and inline. Best case. Cost: ~1ns per call
- **Bimorphic call site** — dispatches to exactly 2 types. JIT handles with a conditional check. Still inlineable on HotSpot. Cost: ~1ns per call
- **Megamorphic call site** — dispatches to 3+ types. HotSpot gives up inlining; falls back to vtable lookup. Cost: ~3ns per call — but the real cost is **lost inlining**, which blocks all higher-level optimizations. "Most higher-level optimizations in HotSpot require an understanding of concrete control flow, and it's impossible to get that in the face of actual, realized polymorphism"
- **Bimorphic limit** — HotSpot's threshold: 2 receiver types max for inline caching and inlining. At 3+ types, the call site is declared megamorphic. Azul Zing's Falcon JIT extends this to 6 by default
- **Devirtualization** — JIT converts a virtual/interface call into a direct call because it observes only one (or two) implementing types at that call site
- **Inlining** — JIT copies the callee's code into the caller, eliminating call overhead entirely. Only happens after devirtualization. The most important single optimization in the JVM
- **Class Hierarchy Analysis (CHA)** — JIT optimization that checks whether a virtual method has any overrides in the loaded class hierarchy. If not, the call is devirtualized even without type profiling. Not available for default methods in traits — a known Scala performance issue
- **Virtual dispatch** — calling a method through a vtable lookup. One pointer dereference + indirect branch
- **Type profile pollution** — when a call site observes many different receiver types during warmup or cold paths, permanently poisoning the type profile so the JIT can never optimize it, even after the hot path stabilizes on a single type

### Scala-specific JVM issues

- **Closure megamorphism** — a well-known Scala issue. `foreach` with different anonymous functions causes the closure call site to become megamorphic. Odersky: "The reason seems to be that the foreach itself is not inlined, so the call to the closure becomes megamorphic and therefore slow." `RawArrayForeachMega` is 10x slower than the monomorphic case
- **Value boxing / unboxing** — wrapping primitives in objects (e.g., `Int` → `java.lang.Integer`) at generic boundaries. A JVM double is 8 bytes vs 24 bytes boxed. One of the two "well-known code patterns that the JVM fails to optimize properly" in Scala (the other being megamorphic dispatch). `@specialized` generates primitive-specific bytecode versions but causes code size explosion
- **Scalar replacement** — JIT optimization where escape analysis proves an object doesn't escape, so its fields are replaced with local variables. Can yield 10x speedups. More powerful than simple stack allocation
- **Trait method dispatch** — trait default methods can't be optimized via CHA. A megamorphic call to a trait method is never inlined, even if the method has no overrides. "Because CHA is not available for default methods, default methods can only be inlined by C2 based on type profiling"

### Memory and allocation

- **Allocation rate** — bytes of heap memory allocated per operation. Measured in MB/s. Key metric for GC tuning: Eden size = allocation rate x desired GC interval. Scala's immutable style tends to create more short-lived objects
- **Allocation pressure** — the GC load caused by high allocation rates. Scala's immutable, functional style creates more short-lived objects than typical Java code
- **Minimum allocations and copying** — jsoniter-scala's phrasing for their design goal. Not "allocation-free" or "zero-copy" — they say "minimum"
- **GC pause / stop-the-world** — garbage collector halts all application threads. Distorts benchmark results if not accounted for
  - **`-XX:+UseParallelGC`** — recommended for Scala compiler (throughput-oriented)
  - **`-XX:+UseG1GC`** — common for services (latency-oriented)
- **Peak RSS** (Resident Set Size) — maximum physical memory used by the process
- **Escape analysis** — JIT optimization that determines whether objects "escape" their creating method. Enables stack allocation and scalar replacement. Can yield 10x speedups for short-lived objects

### CPU and hardware

- **Branch target buffer (BTB)** — CPU cache that predicts where indirect branches will jump. BTB miss = pipeline flush = ~15-20 cycle penalty
- **Instruction cache (I-cache)** — CPU cache for compiled code. Inlining too aggressively can cause I-cache thrashing
- **Branch prediction** — CPU guessing which way a branch will go before it's evaluated. Wrong guess = pipeline flush
- **Speculative execution** — CPU executing instructions ahead of branches before knowing the result. Wasted work on mispredict

---

## Architecture: Intermediate AST vs Direct Encoding

This is the central design axis in the JSON library ecosystem.

### Intermediate AST approach (circe)

- Encode: `case class → Json ADT → String/bytes`
- Decode: `String/bytes → Json ADT → case class`
- The `Json` ADT is the intermediate representation. Enables composability (cursors, transformations) but costs allocation + traversal
- "Circe stores JSON objects as a stringy HashMap" — creates both performance overhead and attack surface

### Direct encoding approach (jsoniter-scala)

- Encode: `case class → bytes` (via `JsonWriter`)
- Decode: `bytes → case class` (via `JsonReader`)
- "Parsing and serialization of JSON directly from UTF-8 bytes to your data structures and back, crazily fast without using of runtime reflection or runtime code generation, intermediate ASTs, hash maps, but with minimum allocations and copying"
- The `JsonValueCodec[T]` interface with `decodeValue(in: JsonReader, ...)` and `encodeValue(x: T, out: JsonWriter)` methods is the core abstraction
- **Compile-time codecs** — jsoniter-scala's preferred term: "Scala macros for compile-time generation of safe and ultra-fast JSON codecs"
- **No runtime reflection, no runtime code generation** — why jsoniter-scala works with GraalVM Native Image without restrictions
- **Fail-fast validation** — "validates parsed values safely with the fail-fast approach and clear reporting"
- **Stack-less parsing exceptions** — "by default to greatly reduce the impact on performance"

### Our position (circe-sanely-auto)

- We use circe's intermediate AST (the `Json` ADT) — that's the contract
- We optimize the **macro expansion** (compile-time) and **codec dispatch** (runtime) layers
- jsoniter-scala-circe ("circe booster") lets users keep circe's API while using jsoniter's parser, replacing jawn as the JSON parser

### How to frame it

- "Intermediate AST vs direct encoding" — the fundamental architecture choice
- "We optimize within circe's AST-based architecture" — what we do
- "jsoniter eliminates the intermediate AST entirely" — what they do
- "The circe booster bridges both worlds" — jsoniter-scala-circe

---

## Architecture: Compositional vs Monolithic Codegen

### Approaches

- **Compositional codecs** — each type gets its own codec; composed at runtime via virtual dispatch. Our approach. Circe's approach
- **Monolithic / whole-program codegen** — one macro expansion generates code for the entire type tree. jsoniter-scala's approach. "The macro expands into direct, hand-optimized JsonReader/JsonWriter calls for each field, with no reflective field discovery, no generic 'walk the structure' logic, and no codegen warmup"
- **Semi-auto as caching** — declaring a codec with `derives` or `deriveEncoder`/`deriveDecoder` creates a stable val that implicit search finds, preventing re-derivation. "Since semi-auto yields a user-defined typeclass instance, it's always picked up as an overload and prohibits further traversal — effectively making derives an opt-in caching strategy"

### plokhotnyuk's observations on codec granularity

These quotes from jsoniter-scala's maintainer explain why monolithic codegen exists. They directly challenge our compositional approach and frame the trade-off we navigate:

- "Full semi-auto (codec per class) **brings too many mega-morphic virtual method calls which cannot be in-lined**"
- "Each implementation of the codec interface used in runtime **decreases possibility to optimize mega-morphic interface calls by JVM**"
- "The most optimal way is **somewhere in the middle** for the arbitrary case when looking from the JVM side"
- "Code that duplicates when messages have common parts will **provide pressure on the JIT compiler and CPU front-end** in runtime"
- "Increasing the number of `make` macro calls **increases the compilation time**"

### Trade-off vocabulary

- **Abstraction tax** — the runtime cost of crossing codec boundaries through virtual dispatch
- **Zero-cost abstraction** — an abstraction that compiles away completely, leaving no runtime overhead. What the JIT achieves on ARM for our compositional codecs, but fails to achieve on x86
- **Ahead-of-time (AOT) specialization** — doing at compile time what the JIT would do at runtime

### How to frame the trade-off

- "We're trading **compile-time budget** for **runtime performance**" — when a macro does more work to generate faster code
- "We're **raising the floor without lowering the ceiling**" — improving worst-case (x86) without regressing best-case (ARM)
- "The JIT should handle this, but **doesn't on all platforms**" — when you need compile-time fixes for JIT inconsistencies
- "This is an **abstraction tax** we pay at each codec boundary" — describing the virtual dispatch overhead
- "This **contradicts our compile-time budget**" — when a runtime optimization makes compilation slower

---

## Benchmarking

### Methodology

- **JMH** (Java Microbenchmark Harness) — the gold standard for JVM microbenchmarks. Handles warmup, GC, JIT, fork isolation. In Scala, typically used via the `sbt-jmh` plugin or Mill's JMH support
  - **Throughput mode (`thrpt`)** — operations per time unit, reported as `ops/s`. Higher = better
  - **Average time mode (`avgt`)** — time per operation, reported as `ns/op` or `ms/op`. Lower = better
  - **`@Benchmark`**, **`@BenchmarkMode`**, **`@OutputTimeUnit`** — standard JMH annotations
  - **`Blackhole`** — JMH construct that consumes values to prevent DCE and constant folding
- **GC profiler** (`-prof gc` in JMH) — measures allocation rate alongside throughput. How jsoniter-scala reports "throughput with the allocation rate of generated codecs"
  - **`gc.alloc.rate.norm`** — normalized allocation rate per operation (bytes/op). jsoniter-scala's key allocation metric. Lower = less GC pressure
- **Hyperfine** — command-line benchmarking tool. What we use for compile-time benchmarks
- **Warmup iterations** — runs discarded before measurement to let JIT optimize. JMH default: 20. Critical for JVM benchmarks
- **Measurement iterations** — runs actually recorded. JMH default: 20. More = lower variance
- **Forks** — separate JVM processes to avoid JIT bias and type profile pollution. JMH default: 10. Each fork starts with a clean JVM

### Common pitfalls

- **Coordinated omission** — benchmark flaw where slow responses delay subsequent requests, hiding tail latency. Coined by Gil Tene
- **Dead code elimination (DCE)** — JIT removes computation whose result is never used. Makes benchmarks show artificially fast results. JMH's `Blackhole` prevents this
- **Constant folding** — JIT computes results at compile time when inputs are constants. Use `Blackhole` or varying inputs
- **Benchmark mode confusion** — throughput (ops/sec, higher=better) vs average time (ns/op, lower=better). Always state which one
- **Comparing absolute numbers across machines** — meaningless. Compare **ratios** (e.g., "library A is 1.2x faster than B") instead
- **Closure megamorphism in benchmarks** — running multiple benchmark methods that share a call site can pollute type profiles, making individual benchmarks appear slower than they would be in isolation. Fork isolation prevents this

### How to report results

- "**X ops/sec** on [platform] with [N warmup, M measurement] iterations"
- "**1.2x faster** than baseline" (ratio, not absolute)
- "Throughput improved from **X to Y ops/sec** (+Z%)"
- "Latency p99 dropped from **X ms to Y ms**"
- "Allocation rate: **X bytes/op**" (from GC profiler)

---

## Questions for Design Reviews

When evaluating a performance-related change, ask:

1. "Is this call site **monomorphic, bimorphic, or megamorphic** in practice?"
2. "Have you **profiled** this, or is it theoretical?"
3. "Are you measuring **throughput or latency**? **Steady-state or including warmup**?"
4. "What's the **allocation rate**? Are we creating garbage per operation?"
5. "Does this **scale with core count** or is it single-threaded bound?"
6. "Is this a **micro-benchmark artifact** or does it reproduce under realistic load?"
7. "What does this cost in **compile time**? Does it trigger **recursive macro expansion**?"
8. "Does this affect **incremental compilation**?"
9. "Will this **regress on other platforms** (x86 vs ARM, HotSpot vs Zing)?"
10. "Does this introduce **type profile pollution** that could affect call sites in user code?"

---

## Platform-Specific Notes

### ARM (Apple Silicon)

- Large BTB, aggressive branch prediction — handles indirect calls well
- Wide execution pipeline — can absorb dispatch overhead
- JIT devirtualizes monomorphic sites effectively even with short warmup
- Generally more "forgiving" of virtual dispatch patterns

### x86 (CI runners, cloud VMs)

- Smaller cores on shared infrastructure — less aggressive speculation
- BTB capacity may be insufficient for deep dispatch chains
- JIT warmup may not complete within benchmark iteration count
- More sensitive to I-cache pressure from inlined code
- `rep movsb` / ERMS instructions optimize bulk memory copies (relevant for pre-computed key bytes)

### GraalVM / Native Image (future consideration)

- **Closed-world assumption** — all types known at build time; can devirtualize everything
- **Profile-guided optimization (PGO)** — feed runtime profiles back into AOT compilation
- Would eliminate the megamorphic dispatch problem entirely, but at the cost of native-image build time

### Azul Zing / Azul Prime

- Falcon JIT extends polymorphic inline caching to 6 receiver types (vs HotSpot's 2)
- May not exhibit the same megamorphic penalties as HotSpot for moderate polymorphism

---

## References

Key articles and discussions that established this vocabulary:

- [Shipilev — JVM Anatomy Quark #16: Megamorphic Virtual Calls](https://shipilev.net/jvm/anatomy-quarks/16-megamorphic-virtual-calls/) — definitive reference on megamorphic dispatch cost
- [Too Fast, Too Megamorphic (Richard Warburton)](https://dzone.com/articles/too-fast-too-megamorphic-what) — what influences method call performance
- [Slow-Auto, Inconvenient-Semi (Mateusz Kubuszok)](https://mateuszkubuszok.github.io/SlowAutoInconvenientSemi/) — compile-time benchmarks across derivation approaches
- [Sanely-automatic derivation (Kubuszok)](https://kubuszok.com/2025/sanely-automatic-derivation/) — the approach this library implements
- [Scala 3 Type Class Derivation (official docs)](https://docs.scala-lang.org/scala3/reference/contextual/derivation.html) — Mirror-based derivation and its compile-time cost
- [Expr.summonIgnoring proposal (scala/scala3#21909)](https://github.com/scala/scala3/discussions/21909) — the Scala 3 discussion on recursive summon prevention
- [Scala trait method performance (scala-lang.org)](https://www.scala-lang.org/blog/2016/07/08/trait-method-performance.html) — CHA limitations for trait default methods
- [circe-derivation](https://github.com/circe/circe-derivation) — macro-based derivation without shapeless
- [circe known issues](https://circe.github.io/circe/codecs/known-issues.html) — shapeless stack overflow, implicit divergence
- [jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala) — compile-time codecs, direct encoding, benchmark methodology
- [How much are you paying for abstraction?](https://kmaliszewski9.github.io/scala/2026/02/20/jsoniter.html) — real-world jsoniter-scala adoption and performance impact
- [scalac-profiling (Scala Center)](https://www.scala-lang.org/blog/2018/06/04/scalac-profiling.html) — profiling implicit search and macro expansion
- [Odersky on compiler speed](https://www.scala-lang.org/old/node/4762) — implicit search time measurements
- [Scala 3 inline docs](https://docs.scala-lang.org/scala3/guides/macros/inline.html) — `inline def`, `summonInline`, expansion semantics
- [Scala 3 derivation-macro docs](https://docs.scala-lang.org/scala3/reference/contextual/derivation-macro.html) — `Expr.summon` in macro context
- [Stephen's compile time blog](https://stephenn.com/2017/07/circe-argonaut-shapless-play-json-compile-time/) — circe vs argonaut-shapeless vs play-json compile times
- [GeoTrellis High Performance Scala](https://geotrellis.readthedocs.io/en/1.0/architecture/high-performance-scala/) — boxing, specialization, allocation patterns
- [circe #339: Exponentially increasing compile times](https://github.com/circe/circe/issues/339) — the original compile-time issue
- [circe #2051: Slow codec automatic derivation](https://github.com/circe/circe/issues/2051) — typer phase 6.9s vs 110ms measurements
- [circe #2126: Recursive derivation in Scala 3](https://github.com/circe/circe/issues/2126) — Mirror-based auto recursion cost
- [Semiauto vs auto derivation (kubukoz)](https://gist.github.com/kubukoz/50eb3f3bd41b20b4e464c0b6dbc0f7b0) — "derived at each call site" vs "one place at definition site"
- [Profiling typeclass derivation (jvican)](https://jvican.github.io/post/scalac-profiling/) — "compiler repeats materialization of implicit instances"
- [jsoniter-scala benchmark results](https://plokhotnyuk.github.io/jsoniter-scala/) — cross-JDK throughput and allocation benchmarks
- [LambdaSpot: fastest JSON parser for Scala](https://blog.lambdaspot.dev/the-fastest-and-safest-json-parser-and-serializer-for-scala) — jsoniter-scala design philosophy
- [jsoniter-scala #405: codec generation granularity](https://github.com/plokhotnyuk/jsoniter-scala/issues/405) — plokhotnyuk on megamorphic dispatch trade-offs
