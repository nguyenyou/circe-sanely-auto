package sanely

import utest.*
import io.circe.{*, given}
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.derivation.Configuration

// --- Test types for configured derivation ---

// Mirrors circe's ConfigExampleBase
enum ConfigExampleBase:
  case ConfigExampleFoo(thisIsAField: String, a: Int = 0, b: Double)
  case ConfigExampleBar

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

// Mirrors circe's IntercardinalDirections
enum IntercardinalDirections:
  case NorthEast, SouthEast, SouthWest, NorthWest

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

// Option defaults test types (mirrors circe's FooWithDefault/FooNoDefault)
case class FooWithDefault(a: Option[Int] = Some(0), b: String = "b")
case class FooNoDefault(a: Option[Int], b: String = "b")

// Multi-level hierarchy with discriminator
sealed trait GrandParent
sealed trait Parent extends GrandParent
case class Child(a: Int, b: String) extends Parent

// 3-level hierarchy with name collision (mirrors circe test)
sealed trait GreatGrandParent
sealed trait GrandParent2 extends GreatGrandParent
case class Uncle(Child: Int) extends GrandParent2 // field name matches case class name
sealed trait Parent2 extends GrandParent2
case class Child2(a: Int, b: String) extends Parent2

// Recursive with discriminator
sealed trait Tree
case class Branch(l: Tree, r: Tree) extends Tree
case object Leaf extends Tree

// Hierarchical enum (mirrors circe's HierarchicalEnum with diamond)
sealed trait HierarchicalEnum
object HierarchicalEnum:
  sealed trait NestedA extends HierarchicalEnum
  sealed trait NestedB extends HierarchicalEnum
  case object A extends HierarchicalEnum
  case object B extends NestedA
  case object C extends NestedB
  case object D extends NestedA with NestedB // diamond

// Generic class with defaults (mirrors circe's GenericFoo)
case class GenericFoo[T](a: List[T] = List.empty, b: String = "b")

// Recursive types with nested containers (real-world patterns)
case class ConfigRecursiveOptionList(children: Option[List[ConfigRecursiveOptionList]] = None, value: String = "")
case class ConfigRecursiveOptionMap(properties: Option[Map[String, ConfigRecursiveOptionMap]] = None, name: String = "")

object GenericFooHelper:
  given Configuration = Configuration.default.withDefaults
  val genericFooIntDecoder: Decoder[GenericFoo[Int]] = SanelyConfiguredDecoder.derived

object SanelyConfiguredSuite extends TestSuite:
  val tests = Tests {

    // === transformMemberNames ===

    test("transformMemberNames - snake_case") {
      given Configuration = Configuration.default.withSnakeCaseMemberNames
      given Codec.AsObject[ConfigExampleBase.ConfigExampleFoo] = SanelyConfiguredCodec.derived

      val foo: ConfigExampleBase.ConfigExampleFoo = ConfigExampleBase.ConfigExampleFoo("value", 42, 3.14)
      val json = Json.obj(
        "this_is_a_field" -> Json.fromString("value"),
        "a" -> Json.fromInt(42),
        "b" -> Json.fromDoubleOrNull(3.14)
      )
      assert(Encoder.AsObject[ConfigExampleBase.ConfigExampleFoo].encodeObject(foo) == json.asObject.get)
      assert(json.as[ConfigExampleBase.ConfigExampleFoo] == Right(foo))
    }

    test("transformMemberNames - SCREAMING_SNAKE_CASE") {
      given Configuration = Configuration.default.withScreamingSnakeCaseMemberNames
      given Codec.AsObject[ConfigExampleBase.ConfigExampleFoo] = SanelyConfiguredCodec.derived

      val foo: ConfigExampleBase.ConfigExampleFoo = ConfigExampleBase.ConfigExampleFoo("value", 42, 3.14)
      val json = Json.obj(
        "THIS_IS_A_FIELD" -> Json.fromString("value"),
        "A" -> Json.fromInt(42),
        "B" -> Json.fromDoubleOrNull(3.14)
      )
      assert(Encoder.AsObject[ConfigExampleBase.ConfigExampleFoo].encodeObject(foo) == json.asObject.get)
      assert(json.as[ConfigExampleBase.ConfigExampleFoo] == Right(foo))
    }

    test("transformMemberNames - kebab-case") {
      given Configuration = Configuration.default.withKebabCaseMemberNames
      given Codec.AsObject[ConfigExampleBase.ConfigExampleFoo] = SanelyConfiguredCodec.derived

      val foo: ConfigExampleBase.ConfigExampleFoo = ConfigExampleBase.ConfigExampleFoo("value", 42, 3.14)
      val json = Json.obj(
        "this-is-a-field" -> Json.fromString("value"),
        "a" -> Json.fromInt(42),
        "b" -> Json.fromDoubleOrNull(3.14)
      )
      assert(Encoder.AsObject[ConfigExampleBase.ConfigExampleFoo].encodeObject(foo) == json.asObject.get)
      assert(json.as[ConfigExampleBase.ConfigExampleFoo] == Right(foo))
    }

    test("transformMemberNames - PascalCase") {
      given Configuration = Configuration.default.withPascalCaseMemberNames
      given Codec.AsObject[ConfigExampleBase.ConfigExampleFoo] = SanelyConfiguredCodec.derived

      val foo: ConfigExampleBase.ConfigExampleFoo = ConfigExampleBase.ConfigExampleFoo("value", 42, 3.14)
      val json = Json.obj(
        "ThisIsAField" -> Json.fromString("value"),
        "A" -> Json.fromInt(42),
        "B" -> Json.fromDoubleOrNull(3.14)
      )
      assert(Encoder.AsObject[ConfigExampleBase.ConfigExampleFoo].encodeObject(foo) == json.asObject.get)
      assert(json.as[ConfigExampleBase.ConfigExampleFoo] == Right(foo))
    }

    test("transformMemberNames - identity (default config)") {
      given Configuration = Configuration.default
      given Encoder.AsObject[SnakeCaseProduct] = SanelyConfiguredEncoder.derived
      given Decoder[SnakeCaseProduct] = SanelyConfiguredDecoder.derived

      val v = SnakeCaseProduct("Bob", "Jones", 99999)
      val json = v.asJson
      assert(json.hcursor.downField("firstName").as[String] == Right("Bob"))
      assert(json.as[SnakeCaseProduct] == Right(v))
    }

    test("transformMemberNames - snake_case product roundtrip") {
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
      assert(json.as[SnakeCaseProduct] == Right(v))
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
      assert(json.as[Animal] == Right(v))
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
      assert(json.as[Animal] == Right(v))
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
      assert(json.as[Vehicle] == Right(v))
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
      assert(json.as[Vehicle] == Right(v))
    }

    test("discriminator - singleton variant (ConfigExampleBar)") {
      given Configuration = Configuration.default.withDiscriminator("type")
      given Codec.AsObject[ConfigExampleBase] = SanelyConfiguredCodec.derived

      val v: ConfigExampleBase = ConfigExampleBase.ConfigExampleBar
      val json = v.asJson
      assert(json.hcursor.downField("type").as[String] == Right("ConfigExampleBar"))
      assert(json.as[ConfigExampleBase] == Right(v))
    }

    test("discriminator + snake_case constructor names") {
      given Configuration = Configuration.default.withDiscriminator("type").withSnakeCaseConstructorNames
      given Codec.AsObject[ConfigExampleBase] = SanelyConfiguredCodec.derived

      val foo = ConfigExampleBase.ConfigExampleFoo("x", 1, 2.0)
      val json = foo.asJson
      assert(json.hcursor.downField("type").as[String] == Right("config_example_foo"))
      assert(json.as[ConfigExampleBase] == Right(foo))
    }

    test("discriminator + SCREAMING_SNAKE_CASE constructor names") {
      given Configuration = Configuration.default.withDiscriminator("type").withScreamingSnakeCaseConstructorNames
      given Codec.AsObject[ConfigExampleBase] = SanelyConfiguredCodec.derived

      val foo = ConfigExampleBase.ConfigExampleFoo("x", 1, 2.0)
      val json = foo.asJson
      assert(json.hcursor.downField("type").as[String] == Right("CONFIG_EXAMPLE_FOO"))
      assert(json.as[ConfigExampleBase] == Right(foo))
    }

    test("discriminator + kebab-case constructor names") {
      given Configuration = Configuration.default.withDiscriminator("type").withKebabCaseConstructorNames
      given Codec.AsObject[ConfigExampleBase] = SanelyConfiguredCodec.derived

      val foo = ConfigExampleBase.ConfigExampleFoo("x", 1, 2.0)
      val json = foo.asJson
      assert(json.hcursor.downField("type").as[String] == Right("config-example-foo"))
      assert(json.as[ConfigExampleBase] == Right(foo))
    }

    test("discriminator + PascalCase constructor names") {
      given Configuration = Configuration.default.withDiscriminator("type").withPascalCaseConstructorNames
      given Codec.AsObject[ConfigExampleBase] = SanelyConfiguredCodec.derived

      val foo = ConfigExampleBase.ConfigExampleFoo("x", 1, 2.0)
      val json = foo.asJson
      assert(json.hcursor.downField("type").as[String] == Right("ConfigExampleFoo"))
      assert(json.as[ConfigExampleBase] == Right(foo))
    }

    test("discriminator + transformConstructorNames (custom field name)") {
      given Configuration = Configuration.default
        .withDiscriminator("__typename__")
        .withSnakeCaseConstructorNames
      given Encoder.AsObject[Vehicle] = SanelyConfiguredEncoder.derived
      given Decoder[Vehicle] = SanelyConfiguredDecoder.derived

      val v: Vehicle = Car("Honda", 2)
      val json = v.asJson
      assert(json.hcursor.downField("__typename__").as[String] == Right("car"))
      assert(json.hcursor.downField("brand").as[String] == Right("Honda"))
      assert(json.as[Vehicle] == Right(v))
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
      assert(json.as[SnakeDiscAdt] == Right(v))
    }

    test("discriminator missing field fails") {
      given Configuration = Configuration.default.withDiscriminator("type")
      given Codec.AsObject[ConfigExampleBase] = SanelyConfiguredCodec.derived

      val json = Json.obj(
        "_notType" -> Json.fromString("ConfigExampleFoo"),
        "thisIsAField" -> Json.fromString("x"),
        "a" -> Json.fromInt(0),
        "b" -> Json.fromDoubleOrNull(2.5)
      )
      assert(json.as[ConfigExampleBase].isLeft)
    }

    test("discriminator null value fails") {
      given Configuration = Configuration.default.withDiscriminator("type")
      given Codec.AsObject[ConfigExampleBase] = SanelyConfiguredCodec.derived

      val json = Json.obj(
        "type" -> Json.Null,
        "thisIsAField" -> Json.fromString("x"),
        "a" -> Json.fromInt(0),
        "b" -> Json.fromDoubleOrNull(2.5)
      )
      assert(json.as[ConfigExampleBase].isLeft)
    }

    // === Multi-level hierarchy with discriminator ===

    test("multi-level hierarchy with discriminator encodes and decodes") {
      given Configuration = Configuration.default.withDiscriminator("type")
      given Encoder.AsObject[GrandParent] = SanelyConfiguredEncoder.derived
      given Decoder[GrandParent] = SanelyConfiguredDecoder.derived

      val child: GrandParent = Child(1, "a")
      val json = child.asJson
      assert(json.hcursor.downField("type").as[String] == Right("Child"))
      assert(json.as[GrandParent] == Right(child))
    }

    test("3-level hierarchy with discriminator and name collision") {
      given Configuration = Configuration.default.withDiscriminator("type")
      given Encoder.AsObject[GreatGrandParent] = SanelyConfiguredEncoder.derived
      given Decoder[GreatGrandParent] = SanelyConfiguredDecoder.derived

      val child: GreatGrandParent = Child2(1, "a")
      val json = child.asJson
      val result = json.as[GreatGrandParent]
      assert(result == Right(child))
    }

    // === Recursive with discriminator ===

    test("recursive type with discriminator encodes and decodes") {
      given Configuration = Configuration.default.withDiscriminator("type")
      given Encoder.AsObject[Tree] = SanelyConfiguredEncoder.derived
      given Decoder[Tree] = SanelyConfiguredDecoder.derived

      val tree: Tree = Branch(Branch(Leaf, Leaf), Leaf)
      val json = tree.asJson
      val result = json.as[Tree]
      assert(result == Right(tree))
    }

    // === useDefaults ===

    test("useDefaults - missing field uses default value") {
      given Configuration = Configuration.default.withDefaults
      given Decoder[WithDefaults] = SanelyConfiguredDecoder.derived

      val json = Json.obj("x" -> Json.fromInt(42))
      assert(json.as[WithDefaults] == Right(WithDefaults(42, "default_y", true)))
    }

    test("useDefaults - provided fields override defaults") {
      given Configuration = Configuration.default.withDefaults
      given Decoder[WithDefaults] = SanelyConfiguredDecoder.derived

      val json = Json.obj(
        "x" -> Json.fromInt(1),
        "y" -> Json.fromString("custom"),
        "z" -> Json.fromBoolean(false)
      )
      assert(json.as[WithDefaults] == Right(WithDefaults(1, "custom", false)))
    }

    test("useDefaults - null field uses default value") {
      given Configuration = Configuration.default.withDefaults
      given Decoder[WithDefaults] = SanelyConfiguredDecoder.derived

      val json = Json.obj("x" -> Json.fromInt(10), "y" -> Json.Null, "z" -> Json.Null)
      assert(json.as[WithDefaults] == Right(WithDefaults(10, "default_y", true)))
    }

    test("useDefaults - all defaults") {
      given Configuration = Configuration.default.withDefaults
      given Decoder[AllDefaults] = SanelyConfiguredDecoder.derived

      assert(Json.obj().as[AllDefaults] == Right(AllDefaults(1, "hello")))
    }

    test("useDefaults=false - missing field fails") {
      given Configuration = Configuration.default
      given Decoder[WithDefaults] = SanelyConfiguredDecoder.derived

      assert(Json.obj("x" -> Json.fromInt(42)).as[WithDefaults].isLeft)
    }

    test("useDefaults + snake_case") {
      given Configuration = Configuration.default.withDefaults.withSnakeCaseMemberNames
      given Encoder.AsObject[WithDefaults] = SanelyConfiguredEncoder.derived
      given Decoder[WithDefaults] = SanelyConfiguredDecoder.derived

      assert(Json.obj("x" -> Json.fromInt(5)).as[WithDefaults] == Right(WithDefaults(5, "default_y", true)))
    }

    test("useDefaults - ConfigExampleFoo missing 'a' uses default 0") {
      given Configuration = Configuration.default.withDefaults
      given Codec.AsObject[ConfigExampleBase.ConfigExampleFoo] = SanelyConfiguredCodec.derived

      val foo: ConfigExampleBase.ConfigExampleFoo = ConfigExampleBase.ConfigExampleFoo("hello", 0, 2.5)
      val json = Json.obj(
        "thisIsAField" -> Json.fromString("hello"),
        "b" -> Json.fromDoubleOrNull(2.5)
      )
      val expected = Json.obj(
        "thisIsAField" -> Json.fromString("hello"),
        "a" -> Json.fromInt(0),
        "b" -> Json.fromDoubleOrNull(2.5)
      )
      assert(Encoder.AsObject[ConfigExampleBase.ConfigExampleFoo].encodeObject(foo) == expected.asObject.get)
      assert(json.as[ConfigExampleBase.ConfigExampleFoo] == Right(foo))
    }

    // Option[T] without default

    test("Option[T] without default should be None if null decoded") {
      given Configuration = Configuration.default.withDefaults
      given Decoder[FooNoDefault] = SanelyConfiguredDecoder.derived

      val json = Json.obj("a" -> Json.Null)
      assert(json.as[FooNoDefault] == Right(FooNoDefault(None, "b")))
    }

    test("Option[T] without default should be None if missing key decoded") {
      given Configuration = Configuration.default.withDefaults
      given Decoder[FooNoDefault] = SanelyConfiguredDecoder.derived

      val json = Json.obj()
      assert(json.as[FooNoDefault] == Right(FooNoDefault(None, "b")))
    }

    // Option[T] with default

    test("Option[T] with default should be None if null decoded") {
      given Configuration = Configuration.default.withDefaults
      given Decoder[FooWithDefault] = SanelyConfiguredDecoder.derived

      val json = Json.obj("a" -> Json.Null)
      assert(json.as[FooWithDefault] == Right(FooWithDefault(None, "b")))
    }

    test("Option[T] with default should be default value if missing key decoded") {
      given Configuration = Configuration.default.withDefaults
      given Decoder[FooWithDefault] = SanelyConfiguredDecoder.derived

      val json = Json.obj()
      assert(json.as[FooWithDefault] == Right(FooWithDefault(Some(0), "b")))
    }

    test("Value with default should be default value if value is null") {
      given Configuration = Configuration.default.withDefaults
      given Decoder[FooWithDefault] = SanelyConfiguredDecoder.derived

      val json = Json.obj("b" -> Json.Null)
      assert(json.as[FooWithDefault] == Right(FooWithDefault(Some(0), "b")))
    }

    // Wrong type should still fail even with defaults

    test("Option[T] with default should fail to decode if type is wrong") {
      given Configuration = Configuration.default.withDefaults
      given Decoder[FooWithDefault] = SanelyConfiguredDecoder.derived

      val json = Json.obj("a" -> Json.fromString("NotAnInt"))
      assert(json.as[FooWithDefault].isLeft)
    }

    test("Field with default should fail to decode if type is wrong") {
      given Configuration = Configuration.default.withDefaults
      given Decoder[FooWithDefault] = SanelyConfiguredDecoder.derived

      val json = Json.obj("b" -> Json.fromInt(25))
      assert(json.as[FooWithDefault].isLeft)
    }

    // === All options combined ===

    test("all options combined - snake members + defaults + discriminator + kebab constructors") {
      given Configuration = Configuration.default
        .withSnakeCaseMemberNames
        .withDefaults
        .withDiscriminator("type")
        .withKebabCaseConstructorNames
      given Codec.AsObject[ConfigExampleBase] = SanelyConfiguredCodec.derived

      val foo: ConfigExampleBase = ConfigExampleBase.ConfigExampleFoo("hello", 0, 2.5)
      val json = Json.obj(
        "type" -> Json.fromString("config-example-foo"),
        "this_is_a_field" -> Json.fromString("hello"),
        "b" -> Json.fromDoubleOrNull(2.5)
      )
      val expected = Json.obj(
        "this_is_a_field" -> Json.fromString("hello"),
        "a" -> Json.fromInt(0),
        "b" -> Json.fromDoubleOrNull(2.5),
        "type" -> Json.fromString("config-example-foo")
      )
      assert(Encoder.AsObject[ConfigExampleBase].encodeObject(foo) == expected.asObject.get)
      assert(json.as[ConfigExampleBase] == Right(foo))
    }

    // === strictDecoding ===

    test("strictDecoding - rejects unexpected fields on product") {
      given Configuration = Configuration.default.withStrictDecoding
      given Decoder[StrictProduct] = SanelyConfiguredDecoder.derived

      val json = Json.obj(
        "a" -> Json.fromInt(1),
        "b" -> Json.fromString("hi"),
        "extra" -> Json.fromBoolean(true)
      )
      assert(json.as[StrictProduct].isLeft)
    }

    test("strictDecoding - accepts exact fields") {
      given Configuration = Configuration.default.withStrictDecoding
      given Decoder[StrictProduct] = SanelyConfiguredDecoder.derived

      val json = Json.obj(
        "a" -> Json.fromInt(1),
        "b" -> Json.fromString("hi")
      )
      assert(json.as[StrictProduct] == Right(StrictProduct(1, "hi")))
    }

    test("strictDecoding + transformMemberNames") {
      given Configuration = Configuration.default.withStrictDecoding.withSnakeCaseMemberNames
      given Decoder[SnakeCaseProduct] = SanelyConfiguredDecoder.derived

      // camelCase keys should fail
      val json = Json.obj(
        "firstName" -> Json.fromString("Alice"),
        "lastName" -> Json.fromString("Smith"),
        "zipCode" -> Json.fromInt(12345)
      )
      assert(json.as[SnakeCaseProduct].isLeft)

      // snake_case keys should succeed
      val json2 = Json.obj(
        "first_name" -> Json.fromString("Alice"),
        "last_name" -> Json.fromString("Smith"),
        "zip_code" -> Json.fromInt(12345)
      )
      assert(json2.as[SnakeCaseProduct] == Right(SnakeCaseProduct("Alice", "Smith", 12345)))
    }

    // === Fail to decode unknown variant ===

    test("fail to decode if case name does not exist") {
      given Configuration = Configuration.default
      given Codec.AsObject[ConfigExampleBase] = SanelyConfiguredCodec.derived

      val json = Json.obj(
        "invalid-name" -> Json.obj(
          "thisIsAField" -> Json.fromString("x"),
          "a" -> Json.fromInt(0),
          "b" -> Json.fromDoubleOrNull(2.5)
        )
      )
      assert(json.as[ConfigExampleBase].isLeft)
    }

    test("fail to decode if case name does not exist when constructor names are transformed") {
      given Configuration = Configuration.default.withSnakeCaseConstructorNames
      given Codec.AsObject[ConfigExampleBase] = SanelyConfiguredCodec.derived

      // Using untransformed name should fail
      val json = Json.obj(
        "ConfigExampleFoo" -> Json.obj(
          "thisIsAField" -> Json.fromString("x"),
          "a" -> Json.fromInt(0),
          "b" -> Json.fromDoubleOrNull(2.5)
        )
      )
      assert(json.as[ConfigExampleBase].isLeft)
    }

    test("fail when json to decode is not a Json object") {
      given Configuration = Configuration.default
      given Codec.AsObject[ConfigExampleBase] = SanelyConfiguredCodec.derived
      given Codec.AsObject[ConfigExampleBase.ConfigExampleFoo] = SanelyConfiguredCodec.derived

      val json = Json.fromString("a string")
      assert(json.as[ConfigExampleBase].isLeft) // sum type
      assert(json.as[ConfigExampleBase.ConfigExampleFoo].isLeft) // product type
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

      assert(Json.fromString("Purple").as[Color].isLeft)
    }

    test("enum codec - snake_case") {
      given Configuration = Configuration.default.withSnakeCaseConstructorNames
      given Codec[IntercardinalDirections] = SanelyEnumCodec.derived

      val ne = IntercardinalDirections.NorthEast
      assert(ne.asJson == Json.fromString("north_east"))
      assert(Json.fromString("north_east").as[IntercardinalDirections] == Right(ne))
    }

    test("enum codec - SCREAMING_SNAKE_CASE") {
      given Configuration = Configuration.default.withScreamingSnakeCaseConstructorNames
      given Codec[IntercardinalDirections] = SanelyEnumCodec.derived

      val se = IntercardinalDirections.SouthEast
      assert(se.asJson == Json.fromString("SOUTH_EAST"))
      assert(Json.fromString("SOUTH_EAST").as[IntercardinalDirections] == Right(se))
    }

    test("enum codec - kebab-case") {
      given Configuration = Configuration.default.withKebabCaseConstructorNames
      given Codec[IntercardinalDirections] = SanelyEnumCodec.derived

      val sw = IntercardinalDirections.SouthWest
      assert(sw.asJson == Json.fromString("south-west"))
      assert(Json.fromString("south-west").as[IntercardinalDirections] == Right(sw))
    }

    test("enum codec - transformConstructorNames (Direction)") {
      given Configuration = Configuration.default.withSnakeCaseConstructorNames
      given Codec[Direction] = SanelyEnumCodec.derived

      assert(Direction.North.asJson == Json.fromString("north"))
      assert(Direction.South.asJson == Json.fromString("south"))
      assert(Json.fromString("north").as[Direction] == Right(Direction.North))
      assert(Json.fromString("east").as[Direction] == Right(Direction.East))
    }

    test("enum codec - hierarchical sealed trait") {
      given Configuration = Configuration.default
      given Codec[HierarchicalEnum] = SanelyEnumCodec.derived

      val enc = summon[Encoder[HierarchicalEnum]]
      val dec = summon[Decoder[HierarchicalEnum]]

      assert(enc(HierarchicalEnum.A) == Json.fromString("A"))
      assert(dec.decodeJson(Json.fromString("A")) == Right(HierarchicalEnum.A))

      assert(enc(HierarchicalEnum.C) == Json.fromString("C"))
      assert(dec.decodeJson(Json.fromString("C")) == Right(HierarchicalEnum.C))

      // Diamond case
      assert(enc(HierarchicalEnum.D) == Json.fromString("D"))
      assert(dec.decodeJson(Json.fromString("D")) == Right(HierarchicalEnum.D))
    }

    // === strictDecoding on sum types ===

    test("strictDecoding - rejects multiple keys on sum type") {
      given Configuration = Configuration.default.withStrictDecoding
      given Decoder[ConfigExampleBase] = SanelyConfiguredDecoder.derived

      val json = Json.obj(
        "ConfigExampleFoo" -> Json.obj(
          "thisIsAField" -> Json.fromString("x"),
          "a" -> Json.fromInt(0),
          "b" -> Json.fromDoubleOrNull(2.5)
        ),
        "anotherField" -> Json.fromString("some value")
      )
      assert(json.as[ConfigExampleBase].isLeft)
    }

    test("strictDecoding - rejects unexpected fields inside product variant of sum type") {
      given Configuration = Configuration.default.withStrictDecoding
      given Decoder[ConfigExampleBase] = SanelyConfiguredDecoder.derived

      val json = Json.obj(
        "ConfigExampleFoo" -> Json.obj(
          "thisIsAField" -> Json.fromString("x"),
          "a" -> Json.fromInt(0),
          "b" -> Json.fromDoubleOrNull(2.5),
          "anotherField" -> Json.fromString("some value")
        )
      )
      assert(json.as[ConfigExampleBase].isLeft)
    }

    // === useDefaults with generic classes ===

    test("useDefaults - generic class with defaults") {
      val dec = GenericFooHelper.genericFooIntDecoder

      val json = Json.obj()
      assert(dec.decodeJson(json) == Right(GenericFoo(List.empty[Int], "b")))
    }

    // === SanelyConfiguredCodec ===

    test("configured codec - round-trip with snake_case") {
      given Configuration = Configuration.default.withSnakeCaseMemberNames
      given Codec.AsObject[SnakeCaseProduct] = SanelyConfiguredCodec.derived

      val v = SnakeCaseProduct("Test", "User", 55555)
      val json = v.asJson
      assert(json.hcursor.downField("first_name").as[String] == Right("Test"))
      assert(json.as[SnakeCaseProduct] == Right(v))
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

    // === Recursive types with nested containers ===

    test("configured - recursive with Option[List[Self]]") {
      given Configuration = Configuration.default.withDefaults
      given Codec.AsObject[ConfigRecursiveOptionList] = SanelyConfiguredCodec.derived

      val leaf = ConfigRecursiveOptionList(None, "leaf")
      val branch = ConfigRecursiveOptionList(Some(List(leaf, leaf)), "branch")
      val root = ConfigRecursiveOptionList(Some(List(branch)), "root")
      val json = root.asJson
      val decoded = json.as[ConfigRecursiveOptionList]
      assert(decoded == Right(root))

      // None terminates recursion
      val leafJson = leaf.asJson
      val decodedLeaf = leafJson.as[ConfigRecursiveOptionList]
      assert(decodedLeaf == Right(leaf))
    }

    test("configured - recursive with Option[Map[String, Self]]") {
      given Configuration = Configuration.default.withDefaults
      given Codec.AsObject[ConfigRecursiveOptionMap] = SanelyConfiguredCodec.derived

      val leaf = ConfigRecursiveOptionMap(None, "leaf")
      val branch = ConfigRecursiveOptionMap(Some(Map("a" -> leaf, "b" -> leaf)), "branch")
      val root = ConfigRecursiveOptionMap(Some(Map("child" -> branch)), "root")
      val json = root.asJson
      val decoded = json.as[ConfigRecursiveOptionMap]
      assert(decoded == Right(root))
    }
  }
