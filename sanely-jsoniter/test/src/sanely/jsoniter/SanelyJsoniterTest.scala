package sanely.jsoniter

import utest.*
import com.github.plokhotnyuk.jsoniter_scala.core.*

// === Test data models ===

case class Address(street: String, city: String, zip: String)
case class User(name: String, age: Int, active: Boolean, address: Address)
case class WithOption(name: String, age: Option[Int], tags: Option[List[String]])
case class WithCollections(names: List[String], ids: Vector[Int], scores: Set[Double])
case class WithMap(meta: Map[String, String], counts: Map[String, Int])
case class Wrapper(value: String)
case class Empty()

// Sum type
sealed trait Shape
case class Circle(radius: Double) extends Shape
case class Rect(width: Double, height: Double) extends Shape

// Recursive type
case class Tree(value: String, children: List[Tree])

object SanelyJsoniterTest extends TestSuite:
  import sanely.jsoniter.semiauto.*

  // Derive codecs
  given JsonValueCodec[Address] = deriveJsoniterCodec
  given JsonValueCodec[User] = deriveJsoniterCodec
  given JsonValueCodec[WithOption] = deriveJsoniterCodec
  given JsonValueCodec[WithCollections] = deriveJsoniterCodec
  given JsonValueCodec[WithMap] = deriveJsoniterCodec
  given JsonValueCodec[Wrapper] = deriveJsoniterCodec
  given JsonValueCodec[Empty] = deriveJsoniterCodec
  given JsonValueCodec[Shape] = deriveJsoniterCodec
  given JsonValueCodec[Tree] = deriveJsoniterCodec

  private def roundtrip[A: JsonValueCodec](value: A): A =
    val json = writeToString(value)
    readFromString[A](json)

  val tests = Tests {
    test("product - simple") {
      val addr = Address("123 Main", "Springfield", "62701")
      val json = writeToString(addr)
      assert(json.contains("\"street\":\"123 Main\""))
      assert(json.contains("\"city\":\"Springfield\""))
      assert(json.contains("\"zip\":\"62701\""))
      val decoded = readFromString[Address](json)
      assert(decoded == addr)
    }

    test("product - nested") {
      val user = User("Alice", 30, true, Address("1 St", "NY", "10001"))
      val decoded = roundtrip(user)
      assert(decoded == user)
    }

    test("product - empty") {
      val e = Empty()
      val json = writeToString(e)
      assert(json == "{}")
      val decoded = readFromString[Empty](json)
      assert(decoded == e)
    }

    test("option - some") {
      val w = WithOption("Alice", Some(30), Some(List("a", "b")))
      val decoded = roundtrip(w)
      assert(decoded == w)
    }

    test("option - none") {
      val w = WithOption("Alice", None, None)
      val json = writeToString(w)
      assert(json.contains("\"age\":null"))
      assert(json.contains("\"tags\":null"))
      val decoded = readFromString[WithOption](json)
      assert(decoded == w)
    }

    test("option - missing field decodes as default") {
      val json = """{"name":"Bob"}"""
      val decoded = readFromString[WithOption](json)
      assert(decoded.name == "Bob")
      // Missing Option fields get None (the null value for Option)
      assert(decoded.age == None)
    }

    test("collections - list, vector, set") {
      val w = WithCollections(List("a", "b"), Vector(1, 2, 3), Set(1.0, 2.0))
      val decoded = roundtrip(w)
      assert(decoded.names == w.names)
      assert(decoded.ids == w.ids)
      assert(decoded.scores == w.scores)
    }

    test("map - string keys") {
      val w = WithMap(Map("k1" -> "v1", "k2" -> "v2"), Map("a" -> 1, "b" -> 2))
      val decoded = roundtrip(w)
      assert(decoded == w)
    }

    test("sum - circle") {
      val shape: Shape = Circle(5.0)
      val json = writeToString(shape)
      assert(json == """{"Circle":{"radius":5.0}}""")
      val decoded = readFromString[Shape](json)
      assert(decoded == shape)
    }

    test("sum - rect") {
      val shape: Shape = Rect(3.0, 4.0)
      val json = writeToString(shape)
      assert(json == """{"Rect":{"width":3.0,"height":4.0}}""")
      val decoded = readFromString[Shape](json)
      assert(decoded == shape)
    }

    test("recursive") {
      val tree = Tree("root", List(
        Tree("a", List(Tree("a1", Nil))),
        Tree("b", Nil)
      ))
      val decoded = roundtrip(tree)
      assert(decoded == tree)
    }

    test("auto derivation") {
      import sanely.jsoniter.auto.given
      case class Foo(x: Int, y: String)
      val foo = Foo(42, "hello")
      val json = writeToString(foo)
      val decoded = readFromString[Foo](json)
      assert(decoded == foo)
    }

    test("circe format compatibility - product") {
      // Encode with sanely-jsoniter, decode with circe
      import io.circe.generic.semiauto.deriveCodec
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode

      given CirceCodec[Address] = deriveCodec
      given CirceCodec[User] = deriveCodec

      val user = User("Alice", 30, true, Address("1 St", "NY", "10001"))

      // jsoniter → string → circe decode
      val jsoniterJson = writeToString(user)(using summon[JsonValueCodec[User]])
      val circeResult = circeDecode[User](jsoniterJson)
      assert(circeResult == Right(user))

      // circe → string → jsoniter decode
      val circeJson = (user: User).asJson.noSpaces
      val jsoniterResult = readFromString[User](circeJson)(using summon[JsonValueCodec[User]])
      assert(jsoniterResult == user)
    }

    test("circe format compatibility - sum") {
      import io.circe.generic.semiauto.{deriveCodec, deriveEncoder, deriveDecoder}
      import io.circe.{Codec as CirceCodec, Encoder, Decoder, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode

      given Encoder[Circle] = deriveEncoder
      given Decoder[Circle] = deriveDecoder
      given Encoder[Rect] = deriveEncoder
      given Decoder[Rect] = deriveDecoder
      given CirceCodec[Shape] = deriveCodec

      val shape: Shape = Circle(5.0)

      // jsoniter → circe
      val jsoniterJson = writeToString(shape)(using summon[JsonValueCodec[Shape]])
      val circeResult = circeDecode[Shape](jsoniterJson)
      assert(circeResult == Right(shape))

      // circe → jsoniter
      val circeJson = (shape: Shape).asJson.noSpaces
      val jsoniterResult = readFromString[Shape](circeJson)(using summon[JsonValueCodec[Shape]])
      assert(jsoniterResult == shape)
    }

    test("all primitive types") {
      case class AllPrims(
        b: Boolean, by: Byte, s: Short, i: Int, l: Long,
        f: Float, d: Double, str: String, bd: BigDecimal, bi: BigInt
      )
      given JsonValueCodec[AllPrims] = deriveJsoniterCodec
      val v = AllPrims(true, 1, 2, 3, 4L, 1.5f, 2.5, "hello", BigDecimal("3.14"), BigInt("999"))
      val decoded = roundtrip(v)
      assert(decoded == v)
    }

    test("null handling - option wrapping") {
      given JsonValueCodec[Option[Address]] = deriveJsoniterCodec
      val json = "null"
      val result = readFromString[Option[Address]](json)
      assert(result == None)
    }
  }
