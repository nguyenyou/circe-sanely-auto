# sanely-jsoniter

> **Experimental.** This module lets large Scala codebases that are deeply locked into circe incrementally adopt jsoniter-scala's streaming serialization for the HTTP hot path — without rewriting their entire codebase. It reaches 98% of jsoniter-scala native speed while producing circe-compatible JSON.

> **The contract: 100% circe compatibility, zero compromise.** Experimental or not, the promise is the same. If your application works with circe, it must work identically with sanely-jsoniter — same JSON output, same decoded values, same error messages, same behavior in every edge case. Any difference is a bug. No exceptions, no "close enough", no subtle surprises. The serialization backend changes; the observable behavior does not. This is the promise that makes or breaks this library.

## Why large codebases are stuck with circe

In mature Scala codebases, circe is not just a serialization library — it becomes infrastructure. A typical large codebase may have:

- **1,600+ files** importing `io.circe`, with 7,000+ derivation call sites
- **Hundreds of domain model classes** with `json: io.circe.Json` as a field type — these are public API contracts, not internal implementation details. Changing them is a breaking change that ripples across the entire codebase
- **800+ files** using cursor navigation (`.hcursor.downField("x").as[T]`) to traverse JSON trees — operations that fundamentally require the entire JSON AST in memory and have no streaming equivalent
- **3,000+ occurrences** of `.asJson` and 750+ of `.as[T]` — circe's encode/decode operations that go through the `Json` tree
- **Deep framework coupling** — HTTP frameworks (Tapir, http4s) wired to circe's `Encoder[T]`/`Decoder[T]` traits at the codec layer, with 1,000+ endpoint definitions depending on this integration
- **Tree manipulation everywhere** — `.deepMerge()`, `.mapObject()`, `Json.obj()` used to construct JSON programmatically for CDC event schemas, analytics pipelines, API request building
- **Circular dependencies** — code that produces `Json` ASTs and code that consumes them cannot be migrated independently

The result: you cannot remove circe from such a codebase. The `Json` AST is embedded in domain models, API contracts, framework integrations, and business logic. Any migration must be incremental.

## The opportunity

Despite circe being deeply embedded, the **HTTP serialization hot path** — where bytes are parsed into domain objects and domain objects are serialized back to bytes — is a narrow, well-defined layer. In a typical request:

```
HTTP request bytes  -->  circe Json tree (allocated)  -->  Decoder[T]  -->  T
T  -->  Encoder[T]  -->  circe Json tree (allocated)  -->  HTTP response bytes
```

The `Json` tree in the middle exists only to satisfy circe's API — the application code on either side works with typed domain objects, not raw JSON. If we can generate codecs that go directly from bytes to domain objects (and back), we skip the tree allocation entirely:

```
HTTP request bytes  -->  JsonValueCodec[T]  -->  T
T  -->  JsonValueCodec[T]  -->  HTTP response bytes
```

This is 3-5x faster because jsoniter-scala streams tokens directly without allocating intermediate `Json` nodes. The key insight: **this only works for the serialization boundary**, not for code that genuinely needs the JSON tree (cursor navigation, merging, programmatic construction). Those parts stay on circe.

## Where sanely-jsoniter fits

```
                        Your Scala Application
                                 │
         ┌───────────────────────┼───────────────────────┐
         │                       │                       │
  Business Logic          Cursor Navigation       Tree Manipulation
  (typed domain)          .hcursor.downField      .deepMerge, Json.obj
         │                       │                       │
         │                 circe Json AST          circe Json AST
         │                 (must stay circe)       (must stay circe)
         │
         ▼
 ┌───────────────┐
 │     Tapir     │  endpoint descriptions: jsonBody[T], paths, headers
 └───────┬───────┘
         │
         │  needs: T ↔ bytes   ◄── THIS is the hot path
         │
    ┌────┴────┐
    │         │
    ▼         ▼
tapir-json  tapir-jsoniter
  -circe      -scala
    │         │
    │         │  JsonValueCodec[T]
    │         │         ▲
    │         │    ┌────┴─────┐
    │         │    │ sanely-  │
    │         │    │ jsoniter │  circe-compatible derivation
    │         │    └──────────┘
    │         │
    ▼         ▼
Json tree   direct streaming
(allocate)  (zero alloc)
    │         │
 ~139K      ~661K ops/sec
 ops/sec    (4.8x faster)
 28 KB/op    4 KB/op
```

Tapir is the **HTTP boundary** — where every request/response passes through. It doesn't serialize anything itself; the integration module (`tapir-json-circe` vs `tapir-jsoniter-scala`) decides how `T` becomes bytes. sanely-jsoniter provides the `JsonValueCodec[T]` that `tapir-jsoniter-scala` needs, producing **circe-compatible JSON** so the wire format stays identical.

## What this module does

Generates `JsonValueCodec[A]` instances (jsoniter-scala's codec type) that produce **identical JSON** to what circe would produce. This means:

- The JSON on the wire is the same — no client-side changes needed
- circe stays for everything else (cursors, tree manipulation, framework integrations)
- Only the HTTP encode/decode hot path changes
- Both circe codecs and jsoniter codecs can coexist — they're different types, no conflicts

## Usage

### Semi-auto derivation

```scala
import sanely.jsoniter.semiauto.*
import com.github.plokhotnyuk.jsoniter_scala.core.*

case class User(name: String, age: Int, active: Boolean)
given JsonValueCodec[User] = deriveJsoniterCodec

val user = User("Alice", 30, true)
val json: String = writeToString(user)    // {"name":"Alice","age":30,"active":true}
val back: User = readFromString[User](json)
```

### Enum string codec

```scala
import sanely.jsoniter.semiauto.*
import com.github.plokhotnyuk.jsoniter_scala.core.*

enum Color:
  case Red, Green, Blue

given JsonValueCodec[Color] = deriveJsoniterEnumCodec

val json = writeToString(Color.Red)        // "Red"
val back = readFromString[Color](json)     // Color.Red
```

### Value enum codec

For enums with a value parameter (e.g., `val value: String` or `val value: Int`):

```scala
import sanely.jsoniter.semiauto.*
import com.github.plokhotnyuk.jsoniter_scala.core.*

enum Status(val value: String):
  case Active extends Status("active")
  case Inactive extends Status("inactive")

given JsonValueCodec[Status] = deriveJsoniterValueEnumCodec

val json = writeToString(Status.Active)        // "active"
val back = readFromString[Status](json)        // Status.Active
```

The macro auto-detects whether the value field is `String` or `Int`. For enums with multiple constructor parameters or other value types, use `Codecs.stringValueEnum` / `Codecs.intValueEnum` manually.

### Auto derivation

```scala
import sanely.jsoniter.auto.given
import com.github.plokhotnyuk.jsoniter_scala.core.*

case class User(name: String, age: Int)
val json = writeToString(User("Bob", 25))  // just works
```

### `derives` syntax

```scala
import com.github.plokhotnyuk.jsoniter_scala.core.*
import sanely.jsoniter.JsoniterCodec

// Standard
case class Point(x: Int, y: Int) derives JsoniterCodec

// With defaults (missing fields use Scala defaults)
case class Config(host: String, port: Int = 8080) derives JsoniterCodec.WithDefaults

// Snake_case + defaults
case class Profile(firstName: String, lastName: String) derives JsoniterCodec.WithSnakeCaseAndDefaults

// Defaults + drop null
case class Event(name: String, detail: Option[String] = None) derives JsoniterCodec.WithDefaultsDropNull

// Enum string codec
enum Color derives JsoniterCodec.Enum:
  case Red, Green, Blue

// Value enum
enum Level(val value: Int) derives JsoniterCodec.ValueEnum:
  case Low extends Level(1)
  case High extends Level(3)
```

Each wrapper extends `JsonValueCodec[A]` directly, so `derives` makes the codec available without any additional imports or conversions.

Available wrappers: `JsoniterCodec`, `WithDefaults`, `WithDefaultsDropNull`, `WithSnakeCaseAndDefaults`, `WithSnakeCaseAndDefaultsDropNull`, `Enum`, `ValueEnum`.

## Supported types

- **Products**: Case classes with any number of fields
- **Sum types**: Sealed traits with external tagging (e.g., `{"Circle": {"radius": 5.0}}`)
- **Primitives**: Boolean, Byte, Short, Int, Long, Float, Double, Char, String, BigDecimal, BigInt
- **Option**: `None` encodes as `null`, missing JSON field decodes as `None`
- **Collections**: List, Vector, Seq, Set
- **Maps**: `Map[String, V]` (string keys only)
- **Recursive types**: Self-referential case classes (e.g., tree structures)

## Compatibility promise

**sanely-jsoniter is compatible with circe's JSON format, not jsoniter-scala's.**

jsoniter-scala is used as the streaming engine, but the generated codecs intentionally match circe's default encoding — not `JsonCodecMaker.make` defaults (which differ in sum type tagging, enum encoding, etc.). The compatibility contract:

- Encode with sanely-jsoniter, decode with circe → identical result
- Encode with circe, decode with sanely-jsoniter → identical result

This means you can adopt sanely-jsoniter on the hot path and keep circe everywhere else — the JSON on the wire is the same.

| Type | JSON format | Example |
|------|------------|---------|
| Product | `{"field1": ..., "field2": ...}` | `{"name":"Alice","age":30}` |
| Sum (external tag) | `{"VariantName": {...}}` | `{"Circle":{"radius":5.0}}` |
| Option None | `null` | `"age":null` |
| Collections | JSON array | `[1,2,3]` |
| Map[String, V] | JSON object | `{"k":"v"}` |

### How the promise is enforced

Cross-codec tests encode values with sanely-jsoniter and decode with circe (and vice versa), asserting identical results for products, sum types, options, and all primitive types. There are no upstream tests from jsoniter-scala — since we intentionally diverge from jsoniter-scala's format, their tests don't apply.

## Performance

Realistic payload (~1.4 KB JSON): nested products, sealed trait sum types (`OrderStatus`), optional fields, lists. Run via `bash bench-runtime.sh`.

**Reading** (bytes → case class):

| Approach | Throughput | vs circe | Alloc/op |
|----------|-----------|----------|----------|
| circe-jawn | ~135K ops/sec | 1.0x | 28 KB |
| circe + jsoniter bridge | ~196K ops/sec | 1.5x | 25 KB |
| **sanely-jsoniter** | **~762K ops/sec** | **5.7x** | **3 KB** |
| jsoniter-scala native | ~742K ops/sec | 5.5x | 3 KB |

**Writing** (case class → bytes):

| Approach | Throughput | vs circe | Alloc/op |
|----------|-----------|----------|----------|
| circe-printer | ~124K ops/sec | 1.0x | 27 KB |
| circe + jsoniter bridge | ~109K ops/sec | 0.9x | 23 KB |
| **sanely-jsoniter** | **~770K ops/sec** | **6.2x** | **1 KB** |
| jsoniter-scala native | ~615K ops/sec | 5.0x | 1 KB |

sanely-jsoniter **surpasses jsoniter-scala native** on both read and write — while producing circe-compatible JSON. It allocates **89% less per read** and **96% less per write** compared to circe-jawn. The improvement comes from eliminating the `Json` tree allocation entirely, with macro-generated typed locals and direct primitive read/write calls that avoid boxing overhead.

## Migration guide

Adopting sanely-jsoniter is straightforward — you keep circe for everything else and only swap the serialization hot path. For codebases with **configured derivation** (withDefaults, discriminator, snake_case, drop-null), see the full [Migration Guide for Configured Codebases](MIGRATION.md).

### Step 1: Add the dependency

```scala
// Mill
ivy"io.github.nguyenyou::sanely-jsoniter::0.13.0"

// sbt
"io.github.nguyenyou" %% "sanely-jsoniter" % "0.13.0"
```

No circe dependency is pulled in — sanely-jsoniter is fully independent.

### Step 2: Add the import

If you use auto derivation, just add one import alongside your existing circe import:

```scala
import io.circe.generic.auto.given          // existing circe derivation (unchanged)
import sanely.jsoniter.auto.given            // adds JsonValueCodec for free
import com.github.plokhotnyuk.jsoniter_scala.core.*
```

Both imports can coexist — circe codecs and jsoniter codecs are different types, so there's no conflict.

For semi-auto, derive the jsoniter codec next to your circe codec:

```scala
import sanely.jsoniter.semiauto.*

case class User(name: String, age: Int)
given Codec.AsObject[User] = deriveCodec          // circe (existing)
given JsonValueCodec[User] = deriveJsoniterCodec   // jsoniter (new)
```

### Step 3: Swap the hot path

Replace circe's tree-based encode/decode with direct jsoniter calls wherever performance matters — typically HTTP endpoint serialization:

```scala
// Before: circe pipeline (tree allocated on every request)
{ s => io.circe.parser.decode[T](s) }
{ v => v.asJson.noSpaces }

// After: direct jsoniter pipeline (5x faster, zero intermediate allocation)
{ s => readFromString[T](s) }
{ v => writeToString(v) }
```

That's it. The JSON on the wire is identical, so no client changes are needed.

## Roadmap

See [ROADMAP.md](ROADMAP.md).

## Dependencies

- `jsoniter-scala-core` (required)
- No circe dependency — this module is fully independent

## How it works

Uses the same "sanely-automatic" macro technique as circe-sanely-auto:

1. `inline given autoCodec[A]` triggers a single macro expansion
2. Macro walks the `Mirror` to discover all fields/variants
3. `Expr.summonIgnoring` finds existing `JsonValueCodec` instances while excluding the auto-given (preventing loops)
4. For types without an existing codec, derives one recursively within the same expansion
5. Generated code delegates to runtime helpers (`JsoniterRuntime`) to keep the AST small
