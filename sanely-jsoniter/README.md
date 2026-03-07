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

## Format compatibility with circe

The generated codecs produce JSON that is byte-for-byte compatible with circe's default encoding:

| Type | JSON format | Example |
|------|------------|---------|
| Product | `{"field1": ..., "field2": ...}` | `{"name":"Alice","age":30}` |
| Sum (external tag) | `{"VariantName": {...}}` | `{"Circle":{"radius":5.0}}` |
| Option None | `null` | `"age":null` |
| Collections | JSON array | `[1,2,3]` |
| Map[String, V] | JSON object | `{"k":"v"}` |

This means you can encode with jsoniter and decode with circe (or vice versa) — useful for gradual migration or mixing both in the same system.

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

## Limitations (v1)

- **No sub-trait support**: Sealed trait variants must be case classes or case objects (not nested sealed traits)
- **No configured derivation**: Field name transforms, discriminators, and strict decoding not yet supported
- **No enum string codec**: Enum cases encoded as empty-object variants (`{"Red":{}}` not `"Red"`)
- **String map keys only**: `Map[K, V]` only supported where K = String
- **JVM only**: Scala.js support planned

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
