package sanely

import utest.*
import io.circe.{*, given}
import io.circe.parser.*
import io.circe.syntax.*
import sanely.auto.given

case class Simple(i: Int, s: String)
case class Wub(x: Long)
case class Address(street: String, city: String)
case class Person(name: String, age: Int, address: Address)
case class Bam(w: Wub, d: Double)
case class Inner[A](field: A)
case class Outer(a: Option[Inner[String]])
case class Baz(xs: List[String])

sealed trait Foo
case class Bar(i: Int, s: String) extends Foo
case class FooBaz(xs: List[String]) extends Foo
case class FooBam(w: Wub, d: Double) extends Foo

enum Shape:
  case Circle(radius: Double)
  case Rectangle(width: Double, height: Double)

// Phase 3 types
enum Vegetable:
  case Potato
  case Carrot
  case Onion
  case Turnip
sealed trait Adt2
case object Object1 extends Adt2
case object Object2 extends Adt2

sealed trait Adt1
case class Adt1Class1(int: Int) extends Adt1
case object Adt1Object1 extends Adt1

sealed trait Adt3
case class Adt3Class1() extends Adt3
case object Adt3Object1 extends Adt3

// Phase 4 types — custom instances that produce different JSON than auto-derivation

// Custom encoder renames fields: "x" -> "first", "y" -> "second"
case class Renamed(x: Int, y: String)
object Renamed:
  given Encoder.AsObject[Renamed] = Encoder.AsObject.instance { case Renamed(x, y) =>
    JsonObject("first" -> Json.fromInt(x), "second" -> Json.fromString(y))
  }
  given Decoder[Renamed] = Decoder.instance { c =>
    for
      x <- c.downField("first").as[Int]
      y <- c.downField("second").as[String]
    yield Renamed(x, y)
  }

case class WrapsRenamed(r: Renamed, extra: Boolean)

// Phase 5 types — generic type parameters
case class Box[A](a: A)
case class Qux[A](i: Int, a: A, j: Int)

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

    test("Nested product round-trip (Person with Address)") {
      val v = Person("Alice", 30, Address("123 Main St", "Springfield"))
      val json = v.asJson
      val expected = Json.obj(
        "name" -> Json.fromString("Alice"),
        "age" -> Json.fromInt(30),
        "address" -> Json.obj(
          "street" -> Json.fromString("123 Main St"),
          "city" -> Json.fromString("Springfield")
        )
      )
      assert(json == expected)
      val decoded = decode[Person](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Nested product round-trip (Bam with Wub)") {
      val v = Bam(Wub(42L), 3.14)
      val json = v.asJson
      val expected = Json.obj(
        "w" -> Json.obj("x" -> Json.fromLong(42L)),
        "d" -> Json.fromDoubleOrNull(3.14)
      )
      assert(json == expected)
      val decoded = decode[Bam](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Option field round-trip (Outer with Some)") {
      val v = Outer(Some(Inner("hello")))
      val json = v.asJson
      val expected = Json.obj("a" -> Json.obj("field" -> Json.fromString("hello")))
      assert(json == expected)
      val decoded = decode[Outer](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Option field round-trip (Outer with None)") {
      val v = Outer(None)
      val json = v.asJson
      val expected = Json.obj("a" -> Json.Null)
      assert(json == expected)
      val decoded = decode[Outer](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("List field round-trip (Baz)") {
      val v = Baz(List("a", "b", "c"))
      val json = v.asJson
      val expected = Json.obj("xs" -> Json.arr(
        Json.fromString("a"), Json.fromString("b"), Json.fromString("c")
      ))
      assert(json == expected)
      val decoded = decode[Baz](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("List field round-trip (Baz with empty list)") {
      val v = Baz(List.empty)
      val json = v.asJson
      val expected = Json.obj("xs" -> Json.arr())
      assert(json == expected)
      val decoded = decode[Baz](json.noSpaces)
      assert(decoded == Right(v))
    }

    // --- Phase 2: Simple Sum Types ---

    test("Sealed trait round-trip (Foo.Bar)") {
      val v: Foo = Bar(42, "hello")
      val json = v.asJson
      val expected = Json.obj("Bar" -> Json.obj("i" -> Json.fromInt(42), "s" -> Json.fromString("hello")))
      assert(json == expected)
      val decoded = decode[Foo](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Sealed trait round-trip (Foo.FooBaz)") {
      val v: Foo = FooBaz(List("a", "b"))
      val json = v.asJson
      val expected = Json.obj("FooBaz" -> Json.obj("xs" -> Json.arr(Json.fromString("a"), Json.fromString("b"))))
      assert(json == expected)
      val decoded = decode[Foo](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Sealed trait round-trip (Foo.FooBam with nested Wub)") {
      val v: Foo = FooBam(Wub(99L), 2.72)
      val json = v.asJson
      val expected = Json.obj("FooBam" -> Json.obj(
        "w" -> Json.obj("x" -> Json.fromLong(99L)),
        "d" -> Json.fromDoubleOrNull(2.72)
      ))
      assert(json == expected)
      val decoded = decode[Foo](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Enum round-trip (Shape.Circle)") {
      val v: Shape = Shape.Circle(5.0)
      val json = v.asJson
      val expected = Json.obj("Circle" -> Json.obj("radius" -> Json.fromDoubleOrNull(5.0)))
      assert(json == expected)
      val decoded = decode[Shape](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Enum round-trip (Shape.Rectangle)") {
      val v: Shape = Shape.Rectangle(3.0, 4.0)
      val json = v.asJson
      val expected = Json.obj("Rectangle" -> Json.obj(
        "width" -> Json.fromDoubleOrNull(3.0),
        "height" -> Json.fromDoubleOrNull(4.0)
      ))
      assert(json == expected)
      val decoded = decode[Shape](json.noSpaces)
      assert(decoded == Right(v))
    }

    // --- Phase 3: Case Objects in Sums ---

    test("ADT with case objects only (Adt2)") {
      val v1: Adt2 = Object1
      val json1 = v1.asJson
      assert(json1 == Json.obj("Object1" -> Json.obj()))
      val decoded1 = decode[Adt2](json1.noSpaces)
      assert(decoded1 == Right(v1))

      val v2: Adt2 = Object2
      val json2 = v2.asJson
      assert(json2 == Json.obj("Object2" -> Json.obj()))
      val decoded2 = decode[Adt2](json2.noSpaces)
      assert(decoded2 == Right(v2))
    }

    test("ADT with case class + case object (Adt1)") {
      val v1: Adt1 = Adt1Class1(42)
      val json1 = v1.asJson
      assert(json1 == Json.obj("Adt1Class1" -> Json.obj("int" -> Json.fromInt(42))))
      val decoded1 = decode[Adt1](json1.noSpaces)
      assert(decoded1 == Right(v1))

      val v2: Adt1 = Adt1Object1
      val json2 = v2.asJson
      assert(json2 == Json.obj("Adt1Object1" -> Json.obj()))
      val decoded2 = decode[Adt1](json2.noSpaces)
      assert(decoded2 == Right(v2))
    }

    test("Empty case class in ADT (Adt3)") {
      val v1: Adt3 = Adt3Class1()
      val json1 = v1.asJson
      assert(json1 == Json.obj("Adt3Class1" -> Json.obj()))
      val decoded1 = decode[Adt3](json1.noSpaces)
      assert(decoded1 == Right(v1))

      val v2: Adt3 = Adt3Object1
      val json2 = v2.asJson
      assert(json2 == Json.obj("Adt3Object1" -> Json.obj()))
      val decoded2 = decode[Adt3](json2.noSpaces)
      assert(decoded2 == Right(v2))
    }

    test("Enum with case objects (Vegetable)") {
      val v1: Vegetable = Vegetable.Potato
      val json1 = v1.asJson
      assert(json1 == Json.obj("Potato" -> Json.obj()))
      val decoded1 = decode[Vegetable](json1.noSpaces)
      assert(decoded1 == Right(v1))

      val v2: Vegetable = Vegetable.Turnip
      val json2 = v2.asJson
      assert(json2 == Json.obj("Turnip" -> Json.obj()))
      val decoded2 = decode[Vegetable](json2.noSpaces)
      assert(decoded2 == Right(v2))
    }

    // --- Phase 4: User-Provided Instances Respected ---

    test("Custom instance respected in nested type (WrapsRenamed)") {
      // If macro respects Renamed's custom given, "r" field uses renamed keys
      // If macro re-derives, "r" field would have {"x":1,"y":"hi"}
      val v = WrapsRenamed(Renamed(1, "hi"), true)
      val json = v.asJson
      val expected = Json.obj(
        "r" -> Json.obj("first" -> Json.fromInt(1), "second" -> Json.fromString("hi")),
        "extra" -> Json.fromBoolean(true)
      )
      assert(json == expected)
      val decoded = decode[WrapsRenamed](json.noSpaces)
      assert(decoded == Right(v))
    }

    // --- Phase 5: Generic Types ---

    test("Generic product Box[Long] round-trip") {
      val v = Box(42L)
      val json = v.asJson
      assert(json == Json.obj("a" -> Json.fromLong(42L)))
      val decoded = decode[Box[Long]](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Generic product Box[Wub] round-trip") {
      val v = Box(Wub(99L))
      val json = v.asJson
      assert(json == Json.obj("a" -> Json.obj("x" -> Json.fromLong(99L))))
      val decoded = decode[Box[Wub]](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Generic product Qux[Long] round-trip") {
      val v = Qux(1, 2L, 3)
      val json = v.asJson
      assert(json == Json.obj("i" -> Json.fromInt(1), "a" -> Json.fromLong(2L), "j" -> Json.fromInt(3)))
      val decoded = decode[Qux[Long]](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Generic product Box[Foo] (generic wrapping sum type)") {
      val v = Box[Foo](Bar(1, "x"))
      val json = v.asJson
      assert(json == Json.obj("a" -> Json.obj("Bar" -> Json.obj("i" -> Json.fromInt(1), "s" -> Json.fromString("x")))))
      val decoded = decode[Box[Foo]](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Derived Inner encoder used by Outer (Option[Inner[String]])") {
      // Outer's macro should derive Inner[String] internally
      // and use it when encoding the Option field
      val some = Outer(Some(Inner("c")))
      val none = Outer(None)
      assert(some.asJson == Json.obj("a" -> Json.obj("field" -> Json.fromString("c"))))
      assert(none.asJson == Json.obj("a" -> Json.Null))
    }

    // --- Phase 1 extras ---

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
