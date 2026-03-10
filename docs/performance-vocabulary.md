# Performance Vocabulary

A reference for discussing performance — both for this project specifically and JVM/macro performance in general. Use these terms when writing issues, PRs, benchmarks, or having design conversations.

---

## Compile-Time Performance

### Core concepts

- **Macro expansion time** — how long the compiler spends inside our macro code generating ASTs
- **Implicit search / given search** — the compiler looking for typeclass instances; circe-generic's bottleneck. Our `Expr.summonIgnoring` approach avoids implicit search chains entirely
- **Zinc incremental compilation** — the Scala build tool's ability to recompile only what changed. Coarser codegen means more recompilation when types change
- **Compilation unit** — the chunk of code the compiler processes together. Monolithic codegen = one big unit; compositional = many small units

### Questions to ask

- "What's the **compile-time cost** of this change?"
- "Does this affect **incremental compilation granularity**?"
- "How much **AST** are we generating per type?"
- "Does this change the **macro expansion depth**? Do we need to bump `-Xmax-inlines`?"
- "What does the **macro profile** show? Where is time spent — `summonIgnoring`, `deriveProduct`, `resolveDefaults`?"

### Describing results

- "Compile time went from **X to Y seconds** across N types" (absolute)
- "This adds **Z ms per type** to macro expansion" (marginal cost)
- "Zinc now recompiles **M files** instead of **N files** when type T changes" (incremental impact)

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
- **Devirtualization** — JIT converts a virtual/interface call into a direct call because it observes only one implementing type at that call site
- **Inlining** — JIT copies the callee's code into the caller, eliminating call overhead entirely. Only happens after devirtualization
- **Deoptimization / uncommon trap** — JIT made an assumption (e.g., "this call site is monomorphic") that turned out wrong; falls back to interpreted/generic code
- **Tiered compilation** — JVM compiles hot methods through increasingly aggressive optimization tiers (C1 → C2 on HotSpot)
- **On-stack replacement (OSR)** — JIT replaces a running method's code mid-execution (e.g., optimizing a hot loop while it's running)

### Call site polymorphism

- **Monomorphic call site** — always dispatches to the same type. JIT can devirtualize and inline. Best case
- **Bimorphic call site** — dispatches to exactly 2 types. JIT can handle with a conditional check. Still fast
- **Megamorphic call site** — dispatches to 3+ types. JIT gives up inlining; uses vtable lookup every time. Worst case
- **Virtual dispatch** — calling a method through an interface/vtable lookup. One pointer dereference + indirect branch
- **Indirect branch** — CPU instruction that jumps to an address loaded from memory (the vtable entry). Expensive if mispredicted

### Memory and allocation

- **Allocation rate** — bytes of heap memory allocated per operation. High allocation rate = more GC pressure
- **Garbage per operation** — objects created and immediately discarded during encode/decode. Ideally zero for hot paths
- **GC pause / stop-the-world** — garbage collector halts all application threads. Distorts benchmark results if not accounted for
- **Peak RSS** (Resident Set Size) — maximum physical memory used by the process
- **Escape analysis** — JIT optimization that stack-allocates objects that don't "escape" the method. Eliminates heap allocation for short-lived objects

### CPU and hardware

- **Branch target buffer (BTB)** — CPU cache that predicts where indirect branches will jump. BTB miss = pipeline flush = ~15-20 cycle penalty
- **Instruction cache (I-cache)** — CPU cache for compiled code. Inlining too aggressively can cause I-cache thrashing
- **Branch prediction** — CPU guessing which way a branch will go before it's evaluated. Wrong guess = pipeline flush
- **Speculative execution** — CPU executing instructions ahead of branches before knowing the result. Wasted work on mispredict
- **SIMD / vectorization** — processing multiple data elements in a single instruction. Relevant for bulk byte operations like JSON key writing

---

## Architecture Trade-offs

### Compositional vs monolithic codegen

- **Compositional codecs** — each type gets its own codec; composed at runtime via virtual dispatch. Our approach
- **Monolithic / whole-program codegen** — one macro expansion generates code for the entire type tree. jsoniter-scala's approach
- **Abstraction tax** — the runtime cost of crossing codec boundaries through virtual dispatch
- **Zero-cost abstraction** — an abstraction that compiles away completely, leaving no runtime overhead. What the JIT achieves on ARM for our compositional codecs, but fails to achieve on x86
- **Ahead-of-time (AOT) specialization** — doing at compile time what the JIT would do at runtime. What our proposed fixes do

### How to frame the trade-off

- "We're trading **compile-time budget** for **runtime performance**" — when a macro does more work to generate faster code
- "We're **raising the floor without lowering the ceiling**" — improving worst-case (x86) without regressing best-case (ARM)
- "The JIT should handle this, but **doesn't on all platforms**" — when you need compile-time fixes for JIT inconsistencies
- "This is an **abstraction tax** we pay at each codec boundary" — describing the virtual dispatch overhead
- "This **contradicts our compile-time budget**" — when a runtime optimization makes compilation slower

---

## Benchmarking

### Methodology

- **JMH** (Java Microbenchmark Harness) — the gold standard for JVM microbenchmarks. Handles warmup, GC, JIT, fork isolation
- **Hyperfine** — command-line benchmarking tool. What we use for compile-time benchmarks
- **Warmup iterations** — runs discarded before measurement to let JIT optimize. Critical for JVM benchmarks
- **Measurement iterations** — runs actually recorded. More = lower variance
- **Fork isolation** — running each benchmark in a fresh JVM to avoid cross-contamination of JIT state

### Common pitfalls

- **Coordinated omission** — benchmark flaw where slow responses delay subsequent requests, hiding tail latency. Coined by Gil Tene
- **Dead code elimination (DCE)** — JIT removes computation whose result is never used. Makes benchmarks show artificially fast results. JMH's `Blackhole` prevents this
- **Constant folding** — JIT computes results at compile time when inputs are constants. Use `Blackhole` or varying inputs
- **Benchmark mode confusion** — throughput (ops/sec, higher=better) vs average time (ns/op, lower=better). Always state which one
- **Comparing absolute numbers across machines** — meaningless. Compare **ratios** (e.g., "library A is 1.2x faster than B") instead

### How to report results

- "**X ops/sec** on [platform] with [N warmup, M measurement] iterations"
- "**1.2x faster** than baseline" (ratio, not absolute)
- "Throughput improved from **X to Y ops/sec** (+Z%)"
- "Latency p99 dropped from **X ms to Y ms**"

---

## Questions for Design Reviews

When evaluating a performance-related change, ask:

1. "What's the **dispatch depth** per operation?"
2. "Is this call site **monomorphic or megamorphic** in practice?"
3. "Have you **profiled** this, or is it theoretical?"
4. "Are you measuring **throughput or latency**? **Steady-state or including warmup**?"
5. "What's the **allocation rate**? Are we creating garbage per operation?"
6. "Does this **scale with core count** or is it single-threaded bound?"
7. "Is this a **micro-benchmark artifact** or does it reproduce under realistic load?"
8. "What does this cost in **compile time**?"
9. "Does this affect **incremental compilation**?"
10. "Will this **regress on other platforms**?"

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
- Would eliminate the x86 dispatch problem entirely, but at the cost of native-image build time
