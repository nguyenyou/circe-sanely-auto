package sanely

import utest.*
import io.circe.*
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
