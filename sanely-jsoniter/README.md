# sanely-jsoniter

Drop-in `JsonValueCodec[A]` derivation for Scala 3, using the sanely-automatic macro approach. Produces JSON format compatible with circe's default encoding, enabling 3-5x faster runtime serialization compared to circe's tree-based approach.

## Problem

circe's encode/decode pipeline always goes through an intermediate `Json` tree:

```
Encode:  A  -->  Json tree (allocations)  -->  bytes
Decode:  bytes  -->  Json tree (allocations)  -->  A
```

jsoniter-scala skips the tree entirely:

```
Encode:  A  -->  bytes   (streaming, zero intermediate allocation)
Decode:  bytes  -->  A   (streaming, zero intermediate allocation)
```

This module generates `JsonValueCodec[A]` instances that produce **identical JSON** to what circe would produce, so they can be used as a drop-in performance upgrade for the serialization hot path while keeping circe for everything else (cursors, tree manipulation, framework integrations).

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

### Auto derivation

```scala
import sanely.jsoniter.auto.given
import com.github.plokhotnyuk.jsoniter_scala.core.*

case class User(name: String, age: Int)
val json = writeToString(User("Bob", 25))  // just works
```

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

Compared to circe (encoding + decoding combined):

| Approach | Throughput | vs circe |
|----------|-----------|----------|
| circe-jawn (pure circe) | ~150K ops/sec | 1.0x |
| circe + jsoniter parser | ~235K ops/sec | 1.5x |
| **sanely-jsoniter** | ~800K ops/sec | **5x** |

The 5x improvement comes from eliminating the `Json` tree allocation entirely.

## Migration guide

Adopting sanely-jsoniter is straightforward — you keep circe for everything else and only swap the serialization hot path.

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

Prioritized for enabling migration from circe-based codebases — replacing the circe intermediary in HTTP layers (e.g. `readFromString[io.circe.Json]` + `json.as[T]` → direct `readFromString[T]`).

### P0 — Blocks migration

These cover the most common circe derivation patterns in real-world codebases.

- [x] **Configured derivation: `withDefaults`** — Decode missing fields using companion default values. The most common configured derivation pattern (`Configuration.default.withDefaults`).
- [x] **Configured derivation: discriminator** — Sum type tagging via `withDiscriminator(field)`. Required for sealed trait hierarchies that use a type discriminator field.
- [x] **Configured derivation: snake_case member names** — `withSnakeCaseMemberNames` for external API integration where JSON uses `snake_case` but Scala uses `camelCase`.
- [x] **Drop-null encoder** — Omit `null`-valued fields from JSON output. In circe this requires post-processing (`.mapJsonObject(_.filter(!_._2.isNull))`) — jsoniter can do this natively by skipping null field writes.

### P1 — Enables full hot-path optimization

- [x] **Sub-trait support**: Sealed trait variants that are nested sealed traits (currently must be case classes or case objects)
- [x] **Either codec**: Support `Either[L, R]` with `{"Left": value}` / `{"Right": value}` format (matching circe's `disjunctionCodecs`)
- [x] **Non-string map keys**: Support `Map[K, V]` where K is not String — keys stringified via `KeyCodec[K]` (matching circe's `KeyEncoder`/`KeyDecoder` pattern)

### P2 — Enables complete replacement

- [ ] **Protobuf codec bridge**: Support ScalaPB `GeneratedMessage`/`GeneratedEnum` types (matching `scalapb_circe` JSON format)
- [ ] **Value enum codecs**: Support custom value enum types (e.g. `StringEnum`/`IntEnum`) where the enum value is a raw string or int, not the case name. Currently requires manual `Codec.from(decoder.emap(...), encoder.contramap(...))` — could be macro-derived.

### Done

- [x] **Enum string codec**: Encode enum cases as strings (`"Red"`) instead of empty-object variants (`{"Red":{}}`)
- [x] **Scala.js support**: Cross-compile for Scala.js

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
