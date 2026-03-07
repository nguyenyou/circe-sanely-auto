# Migration Guide: Configured Codebases

This guide is for codebases that use circe's **configured derivation** — `withDefaults`, `withDiscriminator`, `withSnakeCaseMemberNames`, `withDropNullValues`, or custom name transforms. If your codebase only uses standard `deriveCodec` / `import auto.given`, see the [README](README.md) instead.

## Configuration mapping

Every circe `Configuration` option has a 1:1 equivalent in `JsoniterConfiguration`:

```scala
import io.circe.derivation.Configuration           // circe
import sanely.jsoniter.JsoniterConfiguration        // sanely-jsoniter
```

| circe | sanely-jsoniter |
|---|---|
| `Configuration.default` | `JsoniterConfiguration.default` |
| `.withDefaults` | `.withDefaults` |
| `.withDiscriminator("type")` | `.withDiscriminator("type")` |
| `.withSnakeCaseMemberNames` | `.withSnakeCaseMemberNames` |
| `.withTransformMemberNames(f)` | `.withTransformMemberNames(f)` |
| `.withTransformConstructorNames(f)` | `.withTransformConstructorNames(f)` |
| `.withStrictDecoding` | `.withStrictDecoding` |
| N/A (manual `.filter(!_._2.isNull)`) | `.withDropNullValues` |

The generated JSON is identical — encode with sanely-jsoniter, decode with circe (and vice versa).

## Pattern 1: Semi-auto (per-type derivation)

If each type has an explicit `given` with a specific configuration:

```scala
// Before: circe only
import io.circe.derivation.{Configuration, ConfiguredCodec}

given Configuration = Configuration.default.withDefaults
given Codec.AsObject[User] = ConfiguredCodec.derived

// After: add jsoniter alongside
import sanely.jsoniter.{JsoniterConfiguration, semiauto as jSemiauto}

given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
given JsonValueCodec[User] = jSemiauto.deriveJsoniterConfiguredCodec
```

Both givens coexist — `Codec.AsObject[User]` and `JsonValueCodec[User]` are different types.

## Pattern 2: Configured auto (one import for all types sharing a config)

If many types share the same configuration (e.g., everything uses `withDefaults`):

```scala
// One-time setup: put this where your types are defined
import sanely.jsoniter.JsoniterConfiguration
import sanely.jsoniter.configured.auto.given

given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
```

Every type with a `Mirror` in scope automatically gets a `JsonValueCodec` derived with that configuration. This covers the common case where hundreds of types all use the same base config.

## Pattern 3: Centralized wrapper (recommended for large codebases)

Most large codebases have a centralized derivation wrapper — a single file that defines convenience methods like `deriveCodecWithDefaults`. This is the highest-leverage migration path: change the wrapper once, and every call site gets jsoniter codecs for free.

### Before: circe-only wrapper

```scala
// your-project/shared/src/your/circe/semiauto.scala
package your.circe

import io.circe.{Codec, Encoder, Decoder}
import io.circe.derivation.Configuration
import scala.deriving.Mirror

object semiauto:
  private val withDefaultsConfig = Configuration.default.withDefaults
  private val withDefaultsAndTypeConfig = Configuration.default.withDefaults.withDiscriminator("__typename__")
  private val withSnakeAndDefaultsConfig = Configuration.default.withDefaults.withSnakeCaseMemberNames

  inline def deriveCodecWithDefaults[A](using inline m: Mirror.Of[A]): Codec.AsObject[A] =
    given Configuration = withDefaultsConfig
    sanely.SanelyConfiguredCodec.derived[A]

  inline def deriveCodecWithDefaultsAndTypename[A](using inline m: Mirror.Of[A]): Codec.AsObject[A] =
    given Configuration = withDefaultsAndTypeConfig
    sanely.SanelyConfiguredCodec.derived[A]

  inline def deriveCodecWithSnakeCaseAndDefaults[A](using inline m: Mirror.Of[A]): Codec.AsObject[A] =
    given Configuration = withSnakeAndDefaultsConfig
    sanely.SanelyConfiguredCodec.derived[A]
```

### After: add parallel jsoniter methods

```scala
// your-project/shared/src/your/circe/semiauto.scala
package your.circe

import io.circe.{Codec, Encoder, Decoder}
import io.circe.derivation.Configuration
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sanely.jsoniter.{JsoniterConfiguration, semiauto as jSemiauto}
import scala.deriving.Mirror

object semiauto:
  // --- Circe configs (unchanged) ---
  private val withDefaultsConfig = Configuration.default.withDefaults
  private val withDefaultsAndTypeConfig = Configuration.default.withDefaults.withDiscriminator("__typename__")
  private val withSnakeAndDefaultsConfig = Configuration.default.withDefaults.withSnakeCaseMemberNames

  // --- Jsoniter configs (mirror the circe ones) ---
  private val jWithDefaults = JsoniterConfiguration.default.withDefaults
  private val jWithDefaultsAndType = JsoniterConfiguration.default.withDefaults.withDiscriminator("__typename__")
  private val jWithSnakeAndDefaults = JsoniterConfiguration.default.withDefaults.withSnakeCaseMemberNames

  // --- Circe derivation (unchanged) ---

  inline def deriveCodecWithDefaults[A](using inline m: Mirror.Of[A]): Codec.AsObject[A] =
    given Configuration = withDefaultsConfig
    sanely.SanelyConfiguredCodec.derived[A]

  inline def deriveCodecWithDefaultsAndTypename[A](using inline m: Mirror.Of[A]): Codec.AsObject[A] =
    given Configuration = withDefaultsAndTypeConfig
    sanely.SanelyConfiguredCodec.derived[A]

  inline def deriveCodecWithSnakeCaseAndDefaults[A](using inline m: Mirror.Of[A]): Codec.AsObject[A] =
    given Configuration = withSnakeAndDefaultsConfig
    sanely.SanelyConfiguredCodec.derived[A]

  // --- Jsoniter derivation (new, parallel methods) ---

  inline def deriveJsoniterCodecWithDefaults[A](using inline m: Mirror.Of[A]): JsonValueCodec[A] =
    given JsoniterConfiguration = jWithDefaults
    jSemiauto.deriveJsoniterConfiguredCodec[A]

  inline def deriveJsoniterCodecWithDefaultsAndTypename[A](using inline m: Mirror.Of[A]): JsonValueCodec[A] =
    given JsoniterConfiguration = jWithDefaultsAndType
    jSemiauto.deriveJsoniterConfiguredCodec[A]

  inline def deriveJsoniterCodecWithSnakeCaseAndDefaults[A](using inline m: Mirror.Of[A]): JsonValueCodec[A] =
    given JsoniterConfiguration = jWithSnakeAndDefaults
    jSemiauto.deriveJsoniterConfiguredCodec[A]

  // --- Enum ---

  inline def deriveEnumCodec[A](using inline m: Mirror.SumOf[A]): io.circe.Codec[A] =
    sanely.SanelyEnumCodec.derived[A]

  inline def deriveJsoniterEnumCodec[A](using inline m: Mirror.SumOf[A]): JsonValueCodec[A] =
    jSemiauto.deriveJsoniterEnumCodec[A]
```

### At each call site: add one line

```scala
// Before
given Codec.AsObject[User] = deriveCodecWithDefaults

// After
given Codec.AsObject[User] = deriveCodecWithDefaults
given JsonValueCodec[User] = deriveJsoniterCodecWithDefaults  // new
```

This is mechanical — search for `deriveCodecWithDefaults` and add the parallel `deriveJsoniterCodecWithDefaults` next to it. The circe codec stays; you're adding, not replacing.

### Drop-null encoding

circe has no built-in `withDropNullValues` for configured derivation — codebases typically wrap the encoder with `.mapJsonObject(_.filter(!_._2.isNull))`. sanely-jsoniter has a first-class config option:

```scala
// Circe: manual null filtering
inline def deriveCodecWithDefaultsDropNull[A](using inline m: Mirror.Of[A]): Codec.AsObject[A] =
  given Configuration = withDefaultsConfig
  val enc = sanely.SanelyConfiguredEncoder.derived[A].mapJsonObject(_.filter(!_._2.isNull))
  val dec = sanely.SanelyConfiguredDecoder.derived[A]
  io.circe.Codec.AsObject.from(dec, enc)

// Jsoniter: built-in
inline def deriveJsoniterCodecWithDefaultsDropNull[A](using inline m: Mirror.Of[A]): JsonValueCodec[A] =
  given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withDropNullValues
  jSemiauto.deriveJsoniterConfiguredCodec[A]
```

## Swapping the HTTP hot path

Once types have `JsonValueCodec` instances, swap the central HTTP codec — the single place where all endpoints serialize/deserialize:

### Before (circe bridge)

```scala
// Every request: bytes -> circe Json tree -> Decoder[T] -> T (and back)
def circeCodec[T: Encoder: Decoder]: JsonCodec[T] =
  sttp.tapir.Codec.json[T] { s =>
    val json = readFromString[io.circe.Json](s)  // allocates full Json tree
    json.as[T] match
      case Right(v) => Value(v)
      case Left(f)  => Error(s, JsonDecodeException(List(JsonError(f.message, List.empty)), f))
  } { t => writeToString(t.asJson) }
```

### After (direct jsoniter)

```scala
// Every request: bytes -> T directly (5x faster, zero intermediate allocation)
def directCodec[T: JsonValueCodec]: JsonCodec[T] =
  sttp.tapir.Codec.json[T] { s =>
    try Value(readFromString[T](s))
    catch
      case e: JsonReaderException =>
        Error(s, JsonDecodeException(List(JsonError(e.getMessage, List.empty)), e))
  } { t => writeToString[T](t) }
```

### Incremental rollout with fallback

You don't have to swap everything at once. Use a fallback codec that tries the direct path first and falls back to the circe bridge for types that don't have a `JsonValueCodec` yet:

```scala
def codec[T: Encoder: Decoder](using jOpt: JsonValueCodec[T] = null): JsonCodec[T] =
  if jOpt != null then directCodec[T](using jOpt)
  else circeCodec[T]
```

This lets you migrate types one at a time. As you add `JsonValueCodec` instances, they automatically pick up the fast path.

## Verification

The Tapir integration tests ([`tapir-test/`](../tapir-test/)) prove this migration path works end-to-end:

- Products encode identically through both codecs
- Configured types (withDefaults, discriminator, snake_case + drop-null) produce wire-compatible JSON
- Cross-decoding works: JSON from the bridge codec decodes with the direct codec and vice versa
- Both codec types coexist in scope without conflicts
- Error handling (malformed JSON, type mismatches) produces `DecodeResult.Error` in both paths

Run the tests with `./mill tapir-test.test` to verify.
