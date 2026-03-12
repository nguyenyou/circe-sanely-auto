package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.*

// Test types using `derives` syntax — in a separate file to avoid
// Scala.js linker interference with circe enum codecs in the main test file.
case class DPoint(x: Int, y: Int) derives JsoniterCodec
case class DConfig(host: String, port: Int = 8080, debug: Boolean = false) derives JsoniterCodec.WithDefaults
case class DProfile(firstName: String, lastName: String, isActive: Boolean = true) derives JsoniterCodec.WithSnakeCaseAndDefaults
case class DEvent(name: String, detail: Option[String] = None, count: Int = 0) derives JsoniterCodec.WithDefaultsDropNull
case class DApiResp(requestId: String, errorMsg: Option[String] = None, retryCount: Int = 0) derives JsoniterCodec.WithSnakeCaseAndDefaultsDropNull
sealed trait DProtocol derives JsoniterCodec.WithDefaultsAndTypeName
case class DRequest(method: String, id: Int = 0) extends DProtocol
case class DResponse(result: String, ok: Boolean = true) extends DProtocol
case object DHeartbeat extends DProtocol
enum DColor derives JsoniterCodec.Enum:
  case Red, Green, Blue
enum DLevel(val value: Int) derives JsoniterCodec.ValueEnum:
  case Low extends DLevel(1)
  case High extends DLevel(3)

class DerivesTest extends munit.FunSuite:

    test("derives JsoniterCodec - product round-trip") {
      val p = DPoint(3, 4)
      val json = writeToString(p)
      assert(json == """{"x":3,"y":4}""")
      assert(readFromString[DPoint](json) == p)
    }

    test("derives WithDefaults - missing fields use defaults") {
      val json = """{"host":"localhost"}"""
      val config = readFromString[DConfig](json)
      assert(config == DConfig("localhost", 8080, false))
    }

    test("derives WithSnakeCaseAndDefaults - snake_case field names") {
      val p = DProfile("Alice", "Smith")
      val json = writeToString(p)
      assert(json.contains("\"first_name\""))
      assert(json.contains("\"last_name\""))
      assert(json.contains("\"is_active\""))
      assert(readFromString[DProfile](json) == p)
    }

    test("derives WithDefaultsDropNull - null fields omitted") {
      val e = DEvent("click")
      val json = writeToString(e)
      assert(!json.contains("null"))
      assert(json.contains("\"name\":\"click\""))
      assert(readFromString[DEvent](json) == e)
    }

    test("derives WithSnakeCaseAndDefaultsDropNull - snake + drop null") {
      val r = DApiResp("req-1")
      val json = writeToString(r)
      assert(json.contains("\"request_id\""))
      assert(!json.contains("null"))
      assert(readFromString[DApiResp](json) == r)
    }

    test("derives Enum - string round-trip") {
      val json = writeToString(DColor.Red: DColor)
      assert(json == "\"Red\"")
      assert(readFromString[DColor](json) == DColor.Red)
    }

    test("derives ValueEnum - int value round-trip") {
      val json = writeToString(DLevel.High: DLevel)
      assert(json == "3")
      assert(readFromString[DLevel](json) == DLevel.High)
    }

    test("derives WithDefaultsAndTypeName - case class round-trip") {
      val req = DRequest("invoke", 42): DProtocol
      val json = writeToString(req)
      val decoded = readFromString[DProtocol](json)
      assert(decoded == DRequest("invoke", 42))
    }

    test("derives WithDefaultsAndTypeName - case object round-trip") {
      val hb = DHeartbeat: DProtocol
      val json = writeToString(hb)
      val decoded = readFromString[DProtocol](json)
      assert(decoded == DHeartbeat)
    }

    test("derives WithDefaultsAndTypeName - __typename__ discriminator in JSON") {
      val resp = DResponse("ok"): DProtocol
      val json = writeToString(resp)
      assert(json.contains("\"__typename__\""))
      assert(json.contains("\"__typename__\":\"DResponse\""))
    }

    test("derives WithDefaultsAndTypeName - defaults used when fields missing") {
      val json = """{"__typename__":"DRequest","method":"ping"}"""
      val decoded = readFromString[DProtocol](json)
      assert(decoded == DRequest("ping", 0))
    }

    test("derives - codec is JsonValueCodec subtype") {
      val codec: JsonValueCodec[DPoint] = summon[JsoniterCodec[DPoint]]
      assert(writeToString(DPoint(1, 2))(using codec) == """{"x":1,"y":2}""")
    }
