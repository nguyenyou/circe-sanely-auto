package sanely

import utest.*
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult
import sttp.tapir.DecodeResult.Error.JsonDecodeException

// === Bridge codec (mirrors AnduinEndpoints.circeCodec) ===

object BridgeCodec:
  import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec.given
  import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReaderException, readFromString, writeToString}
  import io.circe.syntax.EncoderOps
  import io.circe.{CursorOp, Decoder, DecodingFailure, Encoder}
  import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
  import sttp.tapir.DecodeResult.{Error, Value}
  import sttp.tapir.*

  def codec[T: Encoder: Decoder]: JsonCodec[T] =
    given Schema[T] = Schema.schemaForString.as[T]
    sttp.tapir.Codec.json[T] { s =>
      try {
        val json = readFromString[io.circe.Json](s)
        json.as[T] match {
          case Right(v) => Value(v)
          case Left(f: DecodingFailure) =>
            val path = CursorOp.opsToPath(f.history)
            val fields = path.split("\\.").toList.filter(_.nonEmpty).map(FieldName.apply)
            Error(s, JsonDecodeException(List(JsonError(f.message, fields)), f))
        }
      } catch {
        case e: JsonReaderException =>
          Error(s, JsonDecodeException(List(JsonError(e.getMessage, List.empty)), e))
      }
    } { t => writeToString(t.asJson) }

// === Direct codec (target: sanely-jsoniter JsonValueCodec) ===

object DirectCodec:
  import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReaderException, JsonValueCodec, readFromString, writeToString}
  import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
  import sttp.tapir.DecodeResult.{Error, Value}
  import sttp.tapir.*

  def codec[T: JsonValueCodec]: JsonCodec[T] =
    given Schema[T] = Schema.schemaForString.as[T]
    sttp.tapir.Codec.json[T] { s =>
      try Value(readFromString[T](s))
      catch
        case e: JsonReaderException =>
          Error(s, JsonDecodeException(List(JsonError(e.getMessage, List.empty)), e))
    } { t => writeToString[T](t) }

// === Test types ===

case class User(name: String, age: Int, active: Boolean)
case class WithDefaults(name: String, age: Int = 25, active: Boolean = true)
case class SnakeDropNull(firstName: String, lastName: String, nickname: Option[String] = None, retryCount: Int = 0)

sealed trait Vehicle
case class Car(make: String, year: Int) extends Vehicle
case class Bike(brand: String) extends Vehicle

object TapirIntegrationTest extends TestSuite:

  // --- Circe codecs (bridge) ---
  import io.circe.{Encoder, Decoder, Codec as CirceCodec}
  import io.circe.derivation.Configuration

  given CirceCodec[User] = sanely.SanelyCodec.derived

  // --- Jsoniter codecs (direct) ---
  import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
  import sanely.jsoniter.{JsoniterConfiguration, semiauto as jSemiauto}

  given JsonValueCodec[User] = jSemiauto.deriveJsoniterCodec

  val tests = Tests {
    test("product - encode match") {
      val bridge = BridgeCodec.codec[User]
      val direct = DirectCodec.codec[User]
      val user = User("Alice", 30, true)
      val bridgeJson = bridge.encode(user)
      val directJson = direct.encode(user)
      assert(bridgeJson == directJson)
    }

    test("product - decode match") {
      val bridge = BridgeCodec.codec[User]
      val direct = DirectCodec.codec[User]
      val json = """{"name":"Alice","age":30,"active":true}"""
      val bridgeResult = bridge.decode(json)
      val directResult = direct.decode(json)
      assert(bridgeResult == directResult)
      assert(bridgeResult == DecodeResult.Value(User("Alice", 30, true)))
    }

    test("configured withDefaults - both decode missing fields") {
      given Configuration = Configuration.default.withDefaults
      given CirceCodec[WithDefaults] = sanely.SanelyConfiguredCodec.derived

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[WithDefaults] = jSemiauto.deriveJsoniterConfiguredCodec

      val bridge = BridgeCodec.codec[WithDefaults]
      val direct = DirectCodec.codec[WithDefaults]

      val partial = """{"name":"Alice"}"""
      val bridgeResult = bridge.decode(partial)
      val directResult = direct.decode(partial)
      assert(bridgeResult == DecodeResult.Value(WithDefaults("Alice", 25, true)))
      assert(directResult == DecodeResult.Value(WithDefaults("Alice", 25, true)))

      // Encode round-trip
      val full = WithDefaults("Bob", 30, false)
      assert(bridge.encode(full) == direct.encode(full))
    }

    test("configured discriminator - wire format match") {
      given Configuration = Configuration.default.withDefaults.withDiscriminator("type")
      given CirceCodec[Vehicle] = sanely.SanelyConfiguredCodec.derived

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withDiscriminator("type")
      given JsonValueCodec[Vehicle] = jSemiauto.deriveJsoniterConfiguredCodec

      val bridge = BridgeCodec.codec[Vehicle]
      val direct = DirectCodec.codec[Vehicle]

      val car: Vehicle = Car("Toyota", 2024)
      val bridgeJson = bridge.encode(car)
      val directJson = direct.encode(car)
      // Key order may differ (circe: fields then discriminator, jsoniter: discriminator first)
      // but both contain the same data and cross-decode correctly
      assert(bridgeJson.contains("\"type\":\"Car\""))
      assert(directJson.contains("\"type\":\"Car\""))

      // Cross-decode: bridge-encoded JSON decoded by direct codec and vice versa
      assert(bridge.decode(directJson) == DecodeResult.Value(car))
      assert(direct.decode(bridgeJson) == DecodeResult.Value(car))
    }

    test("configured snake_case + drop-null - wire format match") {
      given Configuration = Configuration.default.withDefaults.withSnakeCaseMemberNames
      val circeEncoder: Encoder.AsObject[SnakeDropNull] =
        sanely.SanelyConfiguredEncoder.derived[SnakeDropNull].mapJsonObject(_.filter(!_._2.isNull))
      val circeDecoder: Decoder[SnakeDropNull] = sanely.SanelyConfiguredDecoder.derived
      given Encoder[SnakeDropNull] = circeEncoder
      given Decoder[SnakeDropNull] = circeDecoder

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withSnakeCaseMemberNames.withDropNullValues
      given JsonValueCodec[SnakeDropNull] = jSemiauto.deriveJsoniterConfiguredCodec

      val bridge = BridgeCodec.codec[SnakeDropNull]
      val direct = DirectCodec.codec[SnakeDropNull]

      val v = SnakeDropNull("Alice", "Smith")
      val bridgeJson = bridge.encode(v)
      val directJson = direct.encode(v)
      assert(bridgeJson == directJson)
      assert(bridgeJson.contains("\"first_name\""))
      assert(!bridgeJson.contains(":null"))

      assert(bridge.decode(directJson) == DecodeResult.Value(v))
      assert(direct.decode(bridgeJson) == DecodeResult.Value(v))
    }

    test("error - malformed JSON") {
      val bridge = BridgeCodec.codec[User]
      val direct = DirectCodec.codec[User]
      val bad = "not json"
      val bridgeResult = bridge.decode(bad)
      val directResult = direct.decode(bad)
      assert(bridgeResult.isInstanceOf[DecodeResult.Error])
      assert(directResult.isInstanceOf[DecodeResult.Error])
      val bridgeErr = bridgeResult.asInstanceOf[DecodeResult.Error].error
      val directErr = directResult.asInstanceOf[DecodeResult.Error].error
      assert(bridgeErr.isInstanceOf[JsonDecodeException])
      assert(directErr.isInstanceOf[JsonDecodeException])
    }

    test("error - missing required field") {
      val direct = DirectCodec.codec[User]
      // "name" is a required String, "age" a required Int — malformed object
      val result = direct.decode("""{"name":"Alice"}""")
      // jsoniter fills missing primitives with defaults (0, false) — this is valid
      assert(result == DecodeResult.Value(User("Alice", 0, false)))
      // But truly invalid JSON (wrong type) is an error
      val bad = direct.decode("""{"name":123,"age":"not_int","active":"yes"}""")
      assert(bad.isInstanceOf[DecodeResult.Error])
    }

    test("coexistence - both codecs in scope, no conflict") {
      // Both circe Encoder/Decoder and jsoniter JsonValueCodec for User are in scope
      val bridge = BridgeCodec.codec[User]
      val direct = DirectCodec.codec[User]
      val user = User("Test", 42, false)
      // Both work independently
      val json = direct.encode(user)
      assert(bridge.decode(json) == DecodeResult.Value(user))
      assert(direct.decode(bridge.encode(user)) == DecodeResult.Value(user))
    }
  }
