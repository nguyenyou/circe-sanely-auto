package sanely

import utest.*
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import sanely.auto.given

case class Simple(i: Int, s: String)

object SanelyAutoSuite extends TestSuite:
  val tests = Tests {
    test("Simple product round-trip") {
      val v = Simple(42, "hello")
      val json = v.asJson
      val decoded = decode[Simple](json.noSpaces)
      assert(decoded == Right(v))
    }
  }
