# Why circe-sanely-auto

## The problem

circe is the most widely used JSON library in Scala. It's embedded in production codebases everywhere — as field types in domain models, cursor navigation in business logic, framework integrations with Tapir, http4s, Pekko HTTP. You can't just switch away from circe. It's infrastructure, not a dependency.

But circe has two performance problems that get worse as your codebase grows:

**Slow compilation.** circe-generic's derivation triggers a new round of implicit resolution for every nested type. Each type waits for all its fields to resolve, which wait for *their* fields, and so on. For a codebase with hundreds of types, this adds seconds to every clean compile. In large monorepos with thousands of types, it adds minutes.

**Slow runtime.** circe parses JSON into an intermediate `Json` AST, then traverses that AST to build your domain objects. Every request allocates a full tree of `Json` nodes only to throw them away immediately. This caps throughput and creates GC pressure in high-throughput services.

## What circe-sanely-auto does

It replaces circe's derivation macros with faster ones. You swap one dependency, change one import, and everything else stays the same — same JSON format, same API, same error messages, same behavior.

For runtime, an optional companion module (sanely-jsoniter) generates jsoniter-scala codecs that skip the `Json` AST entirely, streaming bytes directly to domain objects.

## Benefits

**Compilation**: 2–4x faster derivation. A benchmark with ~350 types compiles in 2.6s instead of 9.3s. Less compiler work means less memory usage too (21% lower peak RSS).

**Runtime** (optional, via sanely-jsoniter): 5–6x faster reads and writes, 85–96% less allocation per operation. Competitive with jsoniter-scala native.

**Zero migration cost**: Drop-in replacement. Same packages (`io.circe.generic.auto`, `io.circe.generic.semiauto`), same configured derivation API, same `Configuration` options. Existing code compiles without changes beyond the import.

**Bytecode**: 10–22% less bytecode output. Fewer generated classes, fewer methods, faster classloading.

## Risks

You should understand these before adopting this library:

### 1. Requires Scala 3.8.2+

This library uses `Expr.summonIgnoring`, a Scala 3.7+ compiler API. The minimum supported version is Scala 3.8.2. There is no Scala 2 support and never will be. If your codebase is on Scala 2 or an older Scala 3 version, this library is not an option.

### 2. Depends on compiler internals that may change

The core technique — `Expr.summonIgnoring` inside a TASTy macro — relies on Scala 3 macro APIs that are less stable than the language itself. A future Scala compiler release could change macro behavior, break `summonIgnoring` semantics, or alter `Mirror` synthesis in ways that require updates to this library. circe-generic uses `inline given` + `summonInline` which are more mainstream compiler features.

### 3. Young library, single maintainer

This library was first released in March 2026. It has 327 tests including 192 property-based tests generated from circe's own test suite, but it has not been battle-tested across many production codebases. Edge cases may exist that the test suite doesn't cover. circe-generic has years of production usage and a larger contributor base.

### 4. Silent data corruption if the macros have a bug

This is the most serious risk. The library reimplements circe's encoder and decoder generation via macros. If the macro produces wrong code — a field mapped to the wrong position, a default applied incorrectly, a sum type variant misidentified — your program compiles, runs, and silently produces wrong JSON or decodes wrong values. Unlike a crash, you wouldn't notice until the corrupted data causes a downstream failure.

The library mitigates this with 327 tests, including 192 property-based tests auto-generated from circe's own test suite using the same types, same `Arbitrary` instances, and same property checks. These verify round-trip encoding/decoding for products, sum types, enums, configured derivation, recursive types, and edge cases. But no test suite can cover every possible type shape in your codebase. A type combination that the tests don't exercise could trigger a macro bug that produces silently wrong output.

**Before adopting**: run your existing test suite with the dependency swapped. If your tests cover your JSON serialization paths (and they should), they will catch any incompatibility. If they don't, that's a gap worth closing regardless of which derivation library you use.

### 5. The compatibility promise is aspirational until proven by your codebase

The library claims 100% behavioral compatibility with circe's derivation. This is enforced by a comprehensive test suite, but "100% compatible" is a strong claim for any reimplementation. The safe approach: add the dependency, compile your project, run your tests. If something behaves differently, file an issue — it's treated as a bug.

### 6. Not affiliated with circe

This is a third-party library. It is not maintained by the circe team and has no official endorsement. If circe changes its derivation behavior in a future release, this library must catch up independently.

### 7. Macro-heavy approach has debugging tradeoffs

When derivation fails, error messages come from inside a macro expansion. These can be harder to interpret than circe-generic's implicit resolution errors, which are standard Scala compiler messages. If you hit a derivation issue, the failure mode is "macro expansion failed" rather than "no implicit found for Encoder[X]".

### 8. sanely-jsoniter is a separate, optional module

The runtime speedup (sanely-jsoniter) is a completely separate module with its own dependency (`io.github.nguyenyou::sanely-jsoniter`). It is not required. The core library (`circe-sanely-auto`) has no new runtime dependencies beyond circe-core — only the compile-time derivation changes. If you choose to add sanely-jsoniter, it brings [jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala) as a transitive dependency.

### 9. Upgrade coupling

When you upgrade Scala versions, you may need to wait for a compatible circe-sanely-auto release. With circe-generic, you only depend on circe's release cycle. With circe-sanely-auto, you depend on both circe's and this library's release cycles.

## When to use it

**Good fit:**
- Large Scala 3 codebase where circe derivation measurably slows compilation
- High-throughput services where JSON serialization is a bottleneck (with sanely-jsoniter)
- You're already on Scala 3.8.2+ and willing to accept a newer, less battle-tested dependency

**Not a good fit:**
- Scala 2 codebase
- Small project where compile time doesn't matter
- You need the stability guarantees of a mature, widely-adopted library
- You heavily customize circe internals beyond `Configuration`

## How to evaluate

1. Add the dependency alongside circe-generic (they can coexist during evaluation)
2. Switch one module's import from `io.circe.generic.auto._` to `io.circe.generic.auto.given`
3. Run your tests
4. Measure compile time with and without the change
5. If everything passes and compiles faster, expand to more modules
