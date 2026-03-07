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

## Performance

Compared to circe (encoding + decoding combined):

| Approach | Throughput | vs circe |
|----------|-----------|----------|
| circe-jawn (pure circe) | ~150K ops/sec | 1.0x |
| circe + jsoniter parser | ~235K ops/sec | 1.5x |
| **sanely-jsoniter** | ~800K ops/sec | **5x** |

The 5x improvement comes from eliminating the `Json` tree allocation entirely.

## Integration with circe-sanely-auto

If you're already using `circe-sanely-auto` for faster compile times, `sanely-jsoniter` adds faster runtime performance. The two modules are independent — use either or both:

```scala
// Compile-time improvement (faster derivation)
import io.circe.generic.auto.given  // circe-sanely-auto
val json: io.circe.Json = myValue.asJson

// Runtime improvement (faster serialization)
import sanely.jsoniter.auto.given   // sanely-jsoniter
val bytes: String = writeToString(myValue)
```

For HTTP frameworks (tapir, http4s, etc.), the jsoniter codec can replace the circe-based JSON codec on the hot path:

```scala
// Before: circe pipeline (1.5x with jsoniter parser, tree still allocated)
{ s => readFromString[Json](s).as[T] }

// After: direct jsoniter pipeline (5x, no tree)
{ s => readFromString[T](s) }
```

## Roadmap

- [ ] **Sub-trait support**: Sealed trait variants that are nested sealed traits (currently must be case classes or case objects)
- [ ] **Configured derivation**: Field name transforms, discriminators, and strict decoding
- [ ] **Enum string codec**: Encode enum cases as strings (`"Red"`) instead of empty-object variants (`{"Red":{}}`)
- [ ] **Non-string map keys**: Support `Map[K, V]` where K is not String
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
