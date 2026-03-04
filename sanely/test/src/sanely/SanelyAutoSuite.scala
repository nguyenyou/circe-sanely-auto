package sanely

import utest.*
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import sanely.auto.given

case class Simple(i: Int, s: String)
case class Wub(x: Long)

object SanelyAutoSuite extends TestSuite:
  val tests = Tests {
    test("Simple product round-trip") {
      val v = Simple(42, "hello")
      val json = v.asJson
      val decoded = decode[Simple](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Single-field product round-trip (Wub)") {
      val v = Wub(123L)
      val json = v.asJson
      assert(json == Json.obj("x" -> Json.fromLong(123L)))
      val decoded = decode[Wub](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Single-field product with extreme values") {
      val v = Wub(Long.MaxValue)
      val decoded = decode[Wub](v.asJson.noSpaces)
      assert(decoded == Right(v))

      val v2 = Wub(Long.MinValue)
      val decoded2 = decode[Wub](v2.asJson.noSpaces)
      assert(decoded2 == Right(v2))

      val v3 = Wub(0L)
      val decoded3 = decode[Wub](v3.asJson.noSpaces)
      assert(decoded3 == Right(v3))
    }
  }
