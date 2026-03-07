# The Real-World Problem This Project Solves

## The situation: a large Scala codebase locked into circe

In mature Scala codebases, circe stops being "just a JSON library" and becomes infrastructure. A typical production monorepo might have:

- **1,600+ files** importing `io.circe`, with 7,000+ derivation call sites
- **Hundreds of domain model classes** with `json: io.circe.Json` as a field type — these are public API contracts, not internal details. Changing them is a breaking change
- **800+ files** using cursor navigation (`.hcursor.downField("x").as[T]`) — operations that fundamentally require the full JSON AST in memory
- **3,000+ occurrences** of `.asJson` and 750+ of `.as[T]`
- **Deep framework coupling** — HTTP frameworks (Tapir, http4s) wired to circe's `Encoder[T]`/`Decoder[T]` at the codec layer, with 1,000+ endpoint definitions
- **Tree manipulation everywhere** — `.deepMerge()`, `.mapObject()`, `Json.obj()` for event schemas, analytics, API construction

circe shows up in nearly every layer of the system:

- **HTTP endpoints** — A central Tapir codec wires `Encoder[T]`/`Decoder[T]` for every API route. Every request and response goes through circe
- **Object storage (S3)** — Domain objects are serialized to JSON with `writeToString(formData.asJson)` before uploading to S3, and deserialized back when reading. circe is the storage format
- **Structured logging** — Log entries are serialized to JSON for ingestion by observability pipelines (some teams use jsoniter directly for the log struct, but the domain data inside is still circe-encoded)
- **CDC / event pipelines** — Change data capture events are built with `Json.obj()`, `.deepMerge()`, and cursor navigation, then published to message queues
- **Caching layers** — Redis and in-memory caches store circe `Json` or circe-encoded strings. The cache contract assumes circe's format
- **Configuration** — Application config parsed through circe decoders with defaults, discriminators, and custom transforms via `Configuration`
- **Cross-service protocols** — Shared endpoint definitions use circe codecs as the contract between frontend (Scala.js) and backend (JVM)

You cannot remove circe from such a codebase. The `Json` AST is embedded in domain models, API contracts, storage formats, event schemas, and business logic. Any improvement must be incremental.

### What this project addresses — layer by layer

Not every layer benefits equally. Some are hot paths where runtime speed matters. Others are cold paths where only compile time matters. And some genuinely need the `Json` AST and can't be optimized further. Here's the breakdown:

| Layer | Compile time (circe-sanely-auto) | Runtime (sanely-jsoniter) | Impact |
|---|---|---|---|
| **HTTP endpoints** | 2x faster derivation for every endpoint codec | **5x throughput** — eliminates `Json` tree on every request/response | **Highest.** This is the hot path. Every API call benefits |
| **Object storage (S3)** | 2x faster derivation | **5x faster** serialization/deserialization for uploads and downloads | **High.** Large documents (forms, reports) serialized on every read/write |
| **Structured logging** | 2x faster if log structs use circe derivation | Not applicable — log structs typically use jsoniter-scala directly | **Low runtime, medium compile.** Logging is already optimized |
| **CDC / event pipelines** | 2x faster derivation | Not applicable — these genuinely need the `Json` AST (`deepMerge`, `Json.obj()`, cursors) | **Compile only.** Tree manipulation has no streaming equivalent |
| **Caching layers** | 2x faster derivation | **5x faster** if serializing to/from cache as JSON strings | **Medium.** Depends on whether cache stores `Json` objects or serialized strings |
| **Configuration** | **2x faster** configured derivation (defaults, discriminators, transforms) | Not applicable — config is loaded once at startup, runtime is irrelevant | **Compile only.** But configured derivation is used heavily (~hundreds of call sites) |
| **Cross-service protocols** | **2x faster** on both JVM and Scala.js (cross-compiled) | Potential for shared codec definitions | **Compile only.** But affects both frontend and backend builds |

The pattern: **circe-sanely-auto helps everywhere** (every derivation call site compiles faster). **sanely-jsoniter helps on the I/O boundaries** — HTTP, storage, caching — where bytes are converted to/from domain objects. The layers that do tree manipulation (CDC, cursors, merging) stay on circe untouched.

In a typical production codebase, HTTP endpoints and storage account for the majority of serialization volume. These are exactly the layers where sanely-jsoniter's 5x improvement applies.

## Two independent performance problems

### Problem 1: Compile time

circe-generic's derivation triggers implicit search chains — every nested type spawns another round of resolution. In a codebase with hundreds of types, this compounds into minutes of derivation overhead spread across the build. The coding loop (write, compile, fix, repeat) slows to a crawl.

### Problem 2: Runtime serialization

The HTTP serialization hot path — where every API request parses bytes into domain objects and serializes them back — goes through an unnecessary intermediate step:

```
HTTP request bytes  -->  circe Json tree (allocated)  -->  Decoder[T]  -->  T
T  -->  Encoder[T]  -->  circe Json tree (allocated)  -->  HTTP response bytes
```

The `Json` tree in the middle exists only to satisfy circe's API. The application on either side works with typed domain objects, not raw JSON. Every single HTTP request pays the cost of allocating and traversing this intermediate tree.

## What teams typically try (and why it's not enough)

### The jsoniter-scala-circe bridge (the half-measure)

Teams that care about performance often adopt [`jsoniter-scala-circe`](https://github.com/plokhotnyuk/jsoniter-scala) — a bridge that replaces circe's jawn parser with jsoniter-scala's streaming parser. The pipeline becomes:

```
bytes --jsoniter--> circe.Json --Decoder--> T       (parsing: ~1.5x faster)
T --Encoder--> circe.Json --jsoniter--> bytes        (writing: ~1.5x faster)
```

This helps — jsoniter parses and writes bytes faster than jawn. But the **circe `Json` AST is still fully allocated on every request**. You're still building the entire tree in memory, then walking it again with circe's `Decoder`. It's a faster parser feeding into the same bottleneck.

In practice, this is what it looks like in a central Tapir endpoint codec:

```scala
// The central HTTP codec — every API endpoint goes through this
given circeCodec[T: Encoder: Decoder: ClassTag]: JsonCodec[T] =
  sttp.tapir.Codec.json[T] { s =>
    val json = readFromString[io.circe.Json](s)  // jsoniter parses bytes (fast)
    json.as[T]                                    // circe walks the Json tree (slow)
  } { t =>
    writeToString(t.asJson)                       // circe builds Json tree, jsoniter writes bytes
  }
```

Every request still allocates a full `io.circe.Json` tree. The jsoniter bridge only helps at the byte boundary.

### Why not just switch to jsoniter-scala entirely?

Because you can't. Those 800+ files using cursor navigation, the hundreds of domain models with `json: io.circe.Json` fields, the framework integrations — they all need circe. A full migration would touch thousands of files and take months, with high risk of regressions. And some operations (tree merging, programmatic JSON construction) have no streaming equivalent.

### Benchmarking everything (what motivated teams do)

Teams in this situation often run benchmarks comparing every option they can find — circe, jsoniter-circe bridge, borer (CBOR + JSON), protobuf, Fury. They know serialization is a bottleneck. They're actively searching for something that gives them jsoniter-scala speed without requiring a full rewrite.

## What this project provides

### circe-sanely-auto: fixing compile time

Drop-in replacement for circe-generic. Same API, same JSON format, different implementation. Uses Scala 3 macros with `Expr.summonIgnoring` to derive all instances in a single macro expansion instead of implicit search chains.

| | circe-generic | circe-sanely-auto | |
|---|---|---|---|
| **Auto derivation** | 6.15s | 2.11s | **2.9x faster** |
| **Configured derivation** | 2.59s | 1.39s | **1.9x faster** |
| **Compiler work** | 1,542 samples | 806 samples | **48% less** |
| **Memory allocations** | 8,547 samples | 4,168 samples | **51% less** |

Swap one dependency, change one import. Every derivation call site compiles faster.

### sanely-jsoniter: fixing runtime serialization

Generates `JsonValueCodec[T]` instances (jsoniter-scala's codec type) that produce **identical JSON** to circe. This eliminates the `Json` tree entirely on the hot path:

```
bytes --jsoniter--> T directly                      (5x faster)
T --jsoniter--> bytes directly                      (5x faster)
```

| Approach | Throughput | vs circe |
|----------|-----------|----------|
| circe-jawn (pure circe) | ~150K ops/sec | 1.0x |
| circe + jsoniter parser (the bridge) | ~235K ops/sec | 1.5x |
| **sanely-jsoniter** | ~800K ops/sec | **5x** |

The compatibility contract: encode with sanely-jsoniter, decode with circe (or vice versa) — identical results. The JSON on the wire doesn't change, so no client-side changes are needed.

## The migration path

### Step 1: Compile time (zero risk)

```diff
- mvn"io.circe::circe-generic:0.14.x"
+ mvn"io.github.nguyenyou::circe-sanely-auto:0.14.0"
```

Every module that derives circe codecs compiles faster. No code changes beyond the import.

### Step 2: Runtime hot path (one dependency + one file)

Add sanely-jsoniter alongside circe-sanely-auto:

```diff
+ mvn"io.github.nguyenyou::sanely-jsoniter:0.14.0"
```

Then in the central HTTP codec (the one file that all endpoints go through):

```diff
- val json = readFromString[io.circe.Json](s)  // still allocates Json tree
- json.as[T]
+ readFromString[T](s)  // direct, no intermediate tree
```

```diff
- writeToString(t.asJson)  // builds Json tree, then serializes
+ writeToString(t)         // direct serialization
```

With `sanely-jsoniter.auto.given` imported, `JsonValueCodec[T]` instances are auto-derived for all domain types. One import, one file change, 5x throughput on every API endpoint.

### What stays on circe (everything else)

- Cursor navigation (`.hcursor.downField`)
- Tree manipulation (`.deepMerge`, `.mapObject`)
- Domain models with `json: io.circe.Json` fields
- Framework integrations
- Anything that genuinely needs the JSON AST

Both codec systems coexist — `JsonValueCodec[T]` and `Encoder[T]`/`Decoder[T]` are different types, no conflicts.

## Summary

| | Before | After |
|---|---|---|
| **Compile time** | Slow implicit search chains | Single macro expansion (~2x faster) |
| **Runtime (hot path)** | bytes -> Json tree -> T (1.0-1.5x) | bytes -> T directly (5x) |
| **Migration cost** | — | 1 dep swap + 1 file for the hot path |
| **circe compatibility** | N/A | Same JSON format, coexists with circe |
| **Risk** | — | Zero for compile time; hot path is opt-in |

The goal is not to replace circe. It's to make circe fast where it matters — compile time across the board, and runtime on the serialization boundary — while leaving everything else untouched.
