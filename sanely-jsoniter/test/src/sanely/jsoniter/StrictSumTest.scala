package sanely.jsoniter

import utest.*
import com.github.plokhotnyuk.jsoniter_scala.core.*

// Strict sum type tests are in a separate file to avoid a Scala.js linker
// dispatch issue when configured sum codecs coexist with circe enum codecs
// in the same compilation unit.

sealed trait StrictVehicle
case class StrictCar(make: String, year: Int) extends StrictVehicle
case class StrictBike(brand: String) extends StrictVehicle

object StrictSumTest extends TestSuite:
  import sanely.jsoniter.semiauto.*

  val tests = Tests {
    test("strict - sum external: multi-key object rejected") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withStrictDecoding
      given JsonValueCodec[StrictVehicle] = deriveJsoniterConfiguredCodec
      val json = """{"StrictCar":{"make":"Toyota","year":2024},"extra":true}"""
      val caught =
        try
          readFromString[StrictVehicle](json)
          throw new RuntimeException("expected exception")
        catch
          case e: com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException => e
      assert(caught.getMessage.contains("Strict decoding"))
      assert(caught.getMessage.contains("single key"))
    }

    test("strict - sum external: single key passes") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withStrictDecoding
      given JsonValueCodec[StrictVehicle] = deriveJsoniterConfiguredCodec
      val json = """{"StrictCar":{"make":"Toyota","year":2024}}"""
      val decoded = readFromString[StrictVehicle](json)
      assert(decoded == StrictCar("Toyota", 2024))
    }

    test("strict - discriminator: unknown field in variant rejected") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withStrictDecoding.withDiscriminator("type")
      given JsonValueCodec[StrictVehicle] = deriveJsoniterConfiguredCodec
      val json = """{"type":"StrictCar","make":"Toyota","year":2024,"extra":"bad"}"""
      val caught =
        try
          readFromString[StrictVehicle](json)
          throw new RuntimeException("expected exception")
        catch
          case e: com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException => e
      assert(caught.getMessage.contains("Strict decoding"))
    }
  }
