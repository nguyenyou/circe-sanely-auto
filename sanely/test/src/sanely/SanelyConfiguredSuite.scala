package sanely

import utest.*
import io.circe.{*, given}
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.derivation.Configuration

// --- Test types for configured derivation ---

case class SnakeCaseProduct(firstName: String, lastName: String, zipCode: Int)

sealed trait Animal
case class DomesticCat(whiskerLength: Double) extends Animal
case class WildDog(packSize: Int) extends Animal

case class WithDefaults(x: Int, y: String = "default_y", z: Boolean = true)
case class AllDefaults(a: Int = 1, b: String = "hello")

sealed trait Status
case class Active(since: String) extends Status
case class Inactive(reason: String) extends Status

enum Color:
  case Red, Green, Blue

enum Direction:
  case North, South, East, West

case class StrictProduct(a: Int, b: String)

// Discriminator test type
sealed trait Vehicle
case class Car(brand: String, doors: Int) extends Vehicle
case class Truck(payload: Double) extends Vehicle

// Combined: snake_case + discriminator
case class SnakeDiscProduct(fieldOne: String, fieldTwo: Int)

sealed trait SnakeDiscAdt
case class VariantOne(someValue: Int) extends SnakeDiscAdt
case class VariantTwo(anotherValue: String) extends SnakeDiscAdt

object SanelyConfiguredSuite extends TestSuite:
  val tests = Tests {

    // === transformMemberNames ===

    test("transformMemberNames - snake_case product") {
      given Configuration = Configuration.default.withSnakeCaseMemberNames
      given Encoder.AsObject[SnakeCaseProduct] = SanelyConfiguredEncoder.derived
      given Decoder[SnakeCaseProduct] = SanelyConfiguredDecoder.derived

      val v = SnakeCaseProduct("Alice", "Smith", 12345)
      val json = v.asJson
      val expected = Json.obj(
        "first_name" -> Json.fromString("Alice"),
        "last_name" -> Json.fromString("Smith"),
        "zip_code" -> Json.fromInt(12345)
      )
      assert(json == expected)
      val decoded = json.as[SnakeCaseProduct]
      assert(decoded == Right(v))
    }

    test("transformMemberNames - identity (default config)") {
      given Configuration = Configuration.default
      given Encoder.AsObject[SnakeCaseProduct] = SanelyConfiguredEncoder.derived
      given Decoder[SnakeCaseProduct] = SanelyConfiguredDecoder.derived

      val v = SnakeCaseProduct("Bob", "Jones", 99999)
      val json = v.asJson
      assert(json.hcursor.downField("firstName").as[String] == Right("Bob"))
      val decoded = json.as[SnakeCaseProduct]
      assert(decoded == Right(v))
    }

    // === transformConstructorNames ===

    test("transformConstructorNames - snake_case sum type") {
      given Configuration = Configuration.default.withSnakeCaseConstructorNames
      given Encoder.AsObject[Animal] = SanelyConfiguredEncoder.derived
      given Decoder[Animal] = SanelyConfiguredDecoder.derived

      val v: Animal = DomesticCat(3.5)
      val json = v.asJson
      val expected = Json.obj(
        "domestic_cat" -> Json.obj("whiskerLength" -> Json.fromDoubleOrNull(3.5))
      )
      assert(json == expected)
      val decoded = json.as[Animal]
      assert(decoded == Right(v))
    }

    test("transformConstructorNames + transformMemberNames combined") {
      given Configuration = Configuration.default
        .withSnakeCaseConstructorNames
        .withSnakeCaseMemberNames
      given Encoder.AsObject[Animal] = SanelyConfiguredEncoder.derived
      given Decoder[Animal] = SanelyConfiguredDecoder.derived

      val v: Animal = WildDog(5)
      val json = v.asJson
      val expected = Json.obj(
        "wild_dog" -> Json.obj("pack_size" -> Json.fromInt(5))
      )
      assert(json == expected)
      val decoded = json.as[Animal]
      assert(decoded == Right(v))
    }

    // === discriminator ===

    test("discriminator - flat encoding") {
      given Configuration = Configuration.default.withDiscriminator("type")
      given Encoder.AsObject[Vehicle] = SanelyConfiguredEncoder.derived
      given Decoder[Vehicle] = SanelyConfiguredDecoder.derived

      val v: Vehicle = Car("Toyota", 4)
      val json = v.asJson
      val expected = Json.obj(
        "brand" -> Json.fromString("Toyota"),
        "doors" -> Json.fromInt(4),
        "type" -> Json.fromString("Car")
      )
      assert(json == expected)
      val decoded = json.as[Vehicle]
      assert(decoded == Right(v))
    }

    test("discriminator - flat encoding Truck variant") {
      given Configuration = Configuration.default.withDiscriminator("type")
      given Encoder.AsObject[Vehicle] = SanelyConfiguredEncoder.derived
      given Decoder[Vehicle] = SanelyConfiguredDecoder.derived

      val v: Vehicle = Truck(15.5)
      val json = v.asJson
      val expected = Json.obj(
        "payload" -> Json.fromDoubleOrNull(15.5),
        "type" -> Json.fromString("Truck")
      )
      assert(json == expected)
      val decoded = json.as[Vehicle]
      assert(decoded == Right(v))
    }

    test("discriminator + transformConstructorNames") {
      given Configuration = Configuration.default
        .withDiscriminator("__typename__")
        .withSnakeCaseConstructorNames
      given Encoder.AsObject[Vehicle] = SanelyConfiguredEncoder.derived
      given Decoder[Vehicle] = SanelyConfiguredDecoder.derived

      val v: Vehicle = Car("Honda", 2)
      val json = v.asJson
      assert(json.hcursor.downField("__typename__").as[String] == Right("car"))
      assert(json.hcursor.downField("brand").as[String] == Right("Honda"))
      val decoded = json.as[Vehicle]
      assert(decoded == Right(v))
    }

    test("discriminator + transformMemberNames + transformConstructorNames") {
      given Configuration = Configuration.default
        .withDiscriminator("type")
        .withSnakeCaseMemberNames
        .withSnakeCaseConstructorNames
      given Encoder.AsObject[SnakeDiscAdt] = SanelyConfiguredEncoder.derived
      given Decoder[SnakeDiscAdt] = SanelyConfiguredDecoder.derived

      val v: SnakeDiscAdt = VariantOne(42)
      val json = v.asJson
      val expected = Json.obj(
        "some_value" -> Json.fromInt(42),
        "type" -> Json.fromString("variant_one")
      )
      assert(json == expected)
      val decoded = json.as[SnakeDiscAdt]
      assert(decoded == Right(v))
    }

    // === useDefaults ===

    test("useDefaults - missing field uses default value") {
      given Configuration = Configuration.default.withDefaults
      given Decoder[WithDefaults] = SanelyConfiguredDecoder.derived

      val json = Json.obj("x" -> Json.fromInt(42))
      val decoded = json.as[WithDefaults]
      assert(decoded == Right(WithDefaults(42, "default_y", true)))
    }

    test("useDefaults - provided fields override defaults") {
      given Configuration = Configuration.default.withDefaults
      given Decoder[WithDefaults] = SanelyConfiguredDecoder.derived

      val json = Json.obj(
        "x" -> Json.fromInt(1),
        "y" -> Json.fromString("custom"),
        "z" -> Json.fromBoolean(false)
      )
      val decoded = json.as[WithDefaults]
      assert(decoded == Right(WithDefaults(1, "custom", false)))
    }

    test("useDefaults - null field uses default value") {
      given Configuration = Configuration.default.withDefaults
      given Decoder[WithDefaults] = SanelyConfiguredDecoder.derived

      val json = Json.obj("x" -> Json.fromInt(10), "y" -> Json.Null, "z" -> Json.Null)
      val decoded = json.as[WithDefaults]
      assert(decoded == Right(WithDefaults(10, "default_y", true)))
    }

    test("useDefaults - all defaults") {
      given Configuration = Configuration.default.withDefaults
      given Decoder[AllDefaults] = SanelyConfiguredDecoder.derived

      val json = Json.obj()
      val decoded = json.as[AllDefaults]
      assert(decoded == Right(AllDefaults(1, "hello")))
    }

    test("useDefaults=false - missing field fails") {
      given Configuration = Configuration.default
      given Decoder[WithDefaults] = SanelyConfiguredDecoder.derived

      val json = Json.obj("x" -> Json.fromInt(42))
      val decoded = json.as[WithDefaults]
      assert(decoded.isLeft)
    }

    test("useDefaults + snake_case") {
      given Configuration = Configuration.default.withDefaults.withSnakeCaseMemberNames
      given Encoder.AsObject[WithDefaults] = SanelyConfiguredEncoder.derived
      given Decoder[WithDefaults] = SanelyConfiguredDecoder.derived

      val json = Json.obj("x" -> Json.fromInt(5))
      val decoded = json.as[WithDefaults]
      assert(decoded == Right(WithDefaults(5, "default_y", true)))
    }

    // === strictDecoding ===

    test("strictDecoding - rejects unexpected fields") {
      given Configuration = Configuration.default.withStrictDecoding
      given Decoder[StrictProduct] = SanelyConfiguredDecoder.derived

      val json = Json.obj(
        "a" -> Json.fromInt(1),
        "b" -> Json.fromString("hi"),
        "extra" -> Json.fromBoolean(true)
      )
      val decoded = json.as[StrictProduct]
      assert(decoded.isLeft)
    }

    test("strictDecoding - accepts exact fields") {
      given Configuration = Configuration.default.withStrictDecoding
      given Decoder[StrictProduct] = SanelyConfiguredDecoder.derived

      val json = Json.obj(
        "a" -> Json.fromInt(1),
        "b" -> Json.fromString("hi")
      )
      val decoded = json.as[StrictProduct]
      assert(decoded == Right(StrictProduct(1, "hi")))
    }

    test("strictDecoding + transformMemberNames") {
      given Configuration = Configuration.default.withStrictDecoding.withSnakeCaseMemberNames
      given Decoder[SnakeCaseProduct] = SanelyConfiguredDecoder.derived

      // Using camelCase keys should fail (strict checks transformed names)
      val json = Json.obj(
        "firstName" -> Json.fromString("Alice"),
        "lastName" -> Json.fromString("Smith"),
        "zipCode" -> Json.fromInt(12345)
      )
      val decoded = json.as[SnakeCaseProduct]
      assert(decoded.isLeft)

      // Using snake_case keys should succeed
      val json2 = Json.obj(
        "first_name" -> Json.fromString("Alice"),
        "last_name" -> Json.fromString("Smith"),
        "zip_code" -> Json.fromInt(12345)
      )
      val decoded2 = json2.as[SnakeCaseProduct]
      assert(decoded2 == Right(SnakeCaseProduct("Alice", "Smith", 12345)))
    }

    // === SanelyEnumCodec ===

    test("enum codec - basic round-trip") {
      given Configuration = Configuration.default
      given Codec[Color] = SanelyEnumCodec.derived

      assert(Color.Red.asJson == Json.fromString("Red"))
      assert(Color.Green.asJson == Json.fromString("Green"))
      assert(Color.Blue.asJson == Json.fromString("Blue"))

      assert(Json.fromString("Red").as[Color] == Right(Color.Red))
      assert(Json.fromString("Green").as[Color] == Right(Color.Green))
      assert(Json.fromString("Blue").as[Color] == Right(Color.Blue))
    }

    test("enum codec - unknown value fails") {
      given Configuration = Configuration.default
      given Codec[Color] = SanelyEnumCodec.derived

      val result = Json.fromString("Purple").as[Color]
      assert(result.isLeft)
    }

    test("enum codec - transformConstructorNames") {
      given Configuration = Configuration.default.withSnakeCaseConstructorNames
      given Codec[Direction] = SanelyEnumCodec.derived

      assert(Direction.North.asJson == Json.fromString("north"))
      assert(Direction.South.asJson == Json.fromString("south"))
      assert(Json.fromString("north").as[Direction] == Right(Direction.North))
      assert(Json.fromString("east").as[Direction] == Right(Direction.East))
    }

    // === SanelyConfiguredCodec ===

    test("configured codec - round-trip with snake_case") {
      given Configuration = Configuration.default.withSnakeCaseMemberNames
      given Codec.AsObject[SnakeCaseProduct] = SanelyConfiguredCodec.derived

      val v = SnakeCaseProduct("Test", "User", 55555)
      val json = v.asJson
      assert(json.hcursor.downField("first_name").as[String] == Right("Test"))
      val decoded = json.as[SnakeCaseProduct]
      assert(decoded == Right(v))
    }

    // === semiauto API ===

    test("semiauto deriveConfiguredEncoder/Decoder") {
      import io.circe.generic.semiauto.*
      given Configuration = Configuration.default.withSnakeCaseMemberNames
      given Encoder.AsObject[SnakeCaseProduct] = deriveConfiguredEncoder
      given Decoder[SnakeCaseProduct] = deriveConfiguredDecoder

      val v = SnakeCaseProduct("Via", "Semiauto", 11111)
      val json = v.asJson
      assert(json.hcursor.downField("first_name").as[String] == Right("Via"))
      assert(json.as[SnakeCaseProduct] == Right(v))
    }

    test("semiauto deriveConfiguredCodec") {
      import io.circe.generic.semiauto.*
      given Configuration = Configuration.default.withSnakeCaseMemberNames
      given Codec.AsObject[SnakeCaseProduct] = deriveConfiguredCodec

      val v = SnakeCaseProduct("Codec", "Test", 22222)
      val json = v.asJson
      assert(json.hcursor.downField("last_name").as[String] == Right("Test"))
      assert(json.as[SnakeCaseProduct] == Right(v))
    }

    test("semiauto deriveEnumCodec") {
      import io.circe.generic.semiauto.*
      given Configuration = Configuration.default
      given Codec[Color] = deriveEnumCodec

      assert(Color.Red.asJson == Json.fromString("Red"))
      assert(Json.fromString("Blue").as[Color] == Right(Color.Blue))
    }
  }
