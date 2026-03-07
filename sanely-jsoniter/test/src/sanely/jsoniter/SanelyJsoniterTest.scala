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

// Enums for string codec
enum Color:
  case Red, Green, Blue

enum Direction:
  case North, South, East, West

// Hierarchical sealed trait enum (diamond)
sealed trait Animal
sealed trait Pet extends Animal
sealed trait Farm extends Animal
case object Dog extends Pet
case object Cat extends Pet
case object Cow extends Farm
case object Chicken extends Farm

// Recursive type
case class Tree(value: String, children: List[Tree])

// Value enum types
enum Status(val value: String):
  case Active extends Status("active")
  case Inactive extends Status("inactive")
  case Pending extends Status("pending")

enum Priority(val value: Int):
  case Low extends Priority(1)
  case Medium extends Priority(2)
  case High extends Priority(3)

// Non-string map key types
case class WithIntMap(scores: Map[Int, String])
case class WithLongMap(ids: Map[Long, Boolean])

// Either types
case class WithEither(result: Either[String, Int])
case class WithNestedEither(data: Either[String, List[Int]])

// Types with defaults
case class WithDefaults(name: String, age: Int = 25, active: Boolean = true)
case class WithDefaultOption(name: String, tag: Option[String] = Some("default"))
case class WithDefaultNone(name: String, tag: Option[String] = None)
case class NoDefaults(name: String, age: Int)

// Sum types for configured tests
sealed trait Vehicle
case class Car(make: String, year: Int) extends Vehicle
case class Bike(brand: String) extends Vehicle

// Types for snake_case tests
case class SnakeCaseExample(firstName: String, lastName: String, isActive: Boolean = true)

// Types for drop-null tests
case class WithNullable(name: String, nickname: Option[String], age: Int)

// Discriminator type — sealed trait with variant-specific fields, optional fields, defaults
sealed trait Activity
case class Comment(userId: String, text: String, pinned: Boolean = false) extends Activity
case class StatusChange(fromStatus: String, toStatus: String, reason: Option[String] = None) extends Activity
case class FileUpload(fileName: String, sizeBytes: Long, tags: List[String] = Nil) extends Activity

// Drop-null type — many optional fields with defaults
case class SchemaField(
  `type`: String,
  description: Option[String] = None,
  format: Option[String] = None,
  items: Option[String] = None,
  minLength: Option[Int] = None,
  maxLength: Option[Int] = None,
  required: Option[Boolean] = None,
  default: Option[String] = None
)

// Snake_case + drop-null type
case class ApiResponse(requestId: String, userName: Option[String] = None, errorMessage: Option[String] = None, retryCount: Int = 0)

// Sub-trait hierarchy (ADT with nested sealed traits)
sealed trait ADTWithSub
sealed trait SubTraitA extends ADTWithSub
case class LeafA1(x: Int) extends SubTraitA
case class LeafA2(y: String) extends SubTraitA
case class DirectCase(z: Boolean) extends ADTWithSub

// Deep hierarchy (sub-sub-trait)
sealed trait DeepADT
sealed trait Mid extends DeepADT
sealed trait Inner extends Mid
case class DeepLeaf(v: Int) extends Inner
case class MidLeaf(w: String) extends Mid
case class TopLeaf(u: Boolean) extends DeepADT

// Diamond hierarchy
sealed trait DiamondADT
sealed trait DiamondA extends DiamondADT
sealed trait DiamondB extends DiamondADT
case class DiamondLeaf(x: Int) extends DiamondA with DiamondB
case class OnlyA(a: Int) extends DiamondA
case class OnlyB(b: Int) extends DiamondB

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
  given JsonValueCodec[Color] = deriveJsoniterEnumCodec
  given JsonValueCodec[Direction] = deriveJsoniterEnumCodec
  given JsonValueCodec[Animal] = deriveJsoniterEnumCodec
  given JsonValueCodec[WithIntMap] = deriveJsoniterCodec
  given JsonValueCodec[WithLongMap] = deriveJsoniterCodec
  given JsonValueCodec[WithEither] = deriveJsoniterCodec
  given JsonValueCodec[WithNestedEither] = deriveJsoniterCodec

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

    test("map - int keys") {
      val w = WithIntMap(Map(1 -> "one", 2 -> "two"))
      val json = writeToString(w)
      assert(json.contains("\"1\":\"one\""))
      assert(json.contains("\"2\":\"two\""))
      val decoded = readFromString[WithIntMap](json)
      assert(decoded == w)
    }

    test("map - long keys") {
      val w = WithLongMap(Map(100L -> true, 200L -> false))
      val json = writeToString(w)
      assert(json.contains("\"100\":true"))
      assert(json.contains("\"200\":false"))
      val decoded = readFromString[WithLongMap](json)
      assert(decoded == w)
    }

    test("map - int keys circe format compatibility") {
      import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}
      import io.circe.{Encoder, Decoder, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode

      given Encoder[WithIntMap] = deriveEncoder
      given Decoder[WithIntMap] = deriveDecoder

      val v = WithIntMap(Map(1 -> "one", 2 -> "two"))

      // jsoniter -> circe
      val jJson = writeToString(v)
      assert(circeDecode[WithIntMap](jJson) == Right(v))

      // circe -> jsoniter
      val cJson = (v: WithIntMap).asJson.noSpaces
      assert(readFromString[WithIntMap](cJson) == v)
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

    test("enum - basic round-trip") {
      val json = writeToString(Color.Red)
      assert(json == "\"Red\"")
      val decoded = readFromString[Color](json)
      assert(decoded == Color.Red)
    }

    test("enum - all variants round-trip") {
      for dir <- Direction.values do
        val json = writeToString(dir)
        assert(json == s"\"${dir.toString}\"")
        val decoded = readFromString[Direction](json)
        assert(decoded == dir)
    }

    test("enum - unknown value decode error") {
      val caught =
        try
          readFromString[Color]("\"Purple\"")
          throw new RuntimeException("expected exception")
        catch
          case e: com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException => e
      assert(caught.getMessage.contains("does not contain case"))
    }

    test("enum - hierarchical sealed trait with diamond dedup") {
      val json = writeToString[Animal](Dog)
      assert(json == "\"Dog\"")
      val decoded = readFromString[Animal](json)
      assert(decoded eq Dog)

      // All variants
      val all = List[Animal](Dog, Cat, Cow, Chicken)
      for a <- all do
        val j = writeToString(a)
        val d = readFromString[Animal](j)
        assert(d eq a)
    }

    // === Configured derivation tests ===

    test("configured - withDefaults: missing fields use defaults") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[WithDefaults] = deriveJsoniterConfiguredCodec
      val json = """{"name":"Alice"}"""
      val decoded = readFromString[WithDefaults](json)
      assert(decoded == WithDefaults("Alice", 25, true))
    }

    test("configured - withDefaults: provided fields override defaults") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[WithDefaults] = deriveJsoniterConfiguredCodec
      val json = """{"name":"Alice","age":30,"active":false}"""
      val decoded = readFromString[WithDefaults](json)
      assert(decoded == WithDefaults("Alice", 30, false))
    }

    test("configured - withDefaults: Option with Some default") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[WithDefaultOption] = deriveJsoniterConfiguredCodec
      val json = """{"name":"Alice"}"""
      val decoded = readFromString[WithDefaultOption](json)
      assert(decoded == WithDefaultOption("Alice", Some("default")))
    }

    test("configured - withDefaults: Option with None default") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[WithDefaultNone] = deriveJsoniterConfiguredCodec
      val json = """{"name":"Alice"}"""
      val decoded = readFromString[WithDefaultNone](json)
      assert(decoded == WithDefaultNone("Alice", None))
    }

    test("configured - withDefaults: Option field without default gets None") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[WithNullable] = deriveJsoniterConfiguredCodec
      val json = """{"name":"Alice","age":30}"""
      val decoded = readFromString[WithNullable](json)
      assert(decoded == WithNullable("Alice", None, 30))
    }

    test("configured - withDefaults: non-Option non-default field gets null value") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[NoDefaults] = deriveJsoniterConfiguredCodec
      val json = """{"name":"Alice"}"""
      val decoded = readFromString[NoDefaults](json)
      assert(decoded == NoDefaults("Alice", 0))
    }

    test("configured - useDefaults=false ignores defaults") {
      given JsoniterConfiguration = JsoniterConfiguration.default // useDefaults=false
      given JsonValueCodec[WithDefaults] = deriveJsoniterConfiguredCodec
      val json = """{"name":"Alice"}"""
      val decoded = readFromString[WithDefaults](json)
      // Without useDefaults, missing Int gets 0, missing Boolean gets false
      assert(decoded == WithDefaults("Alice", 0, false))
    }

    test("configured - withDefaults: encode round-trip") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[WithDefaults] = deriveJsoniterConfiguredCodec
      val original = WithDefaults("Bob", 30, false)
      val json = writeToString(original)
      val decoded = readFromString[WithDefaults](json)
      assert(decoded == original)
    }

    test("configured - snake_case member names") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withSnakeCaseMemberNames
      given JsonValueCodec[SnakeCaseExample] = deriveJsoniterConfiguredCodec
      val original = SnakeCaseExample("Alice", "Smith", true)
      val json = writeToString(original)
      assert(json.contains("\"first_name\""))
      assert(json.contains("\"last_name\""))
      assert(json.contains("\"is_active\""))
      val decoded = readFromString[SnakeCaseExample](json)
      assert(decoded == original)
    }

    test("configured - snake_case with defaults") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withSnakeCaseMemberNames
      given JsonValueCodec[SnakeCaseExample] = deriveJsoniterConfiguredCodec
      val json = """{"first_name":"Alice","last_name":"Smith"}"""
      val decoded = readFromString[SnakeCaseExample](json)
      assert(decoded == SnakeCaseExample("Alice", "Smith", true))
    }

    test("configured - drop null values") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDropNullValues
      given JsonValueCodec[WithNullable] = deriveJsoniterConfiguredCodec
      val original = WithNullable("Alice", None, 30)
      val json = writeToString(original)
      assert(!json.contains("nickname"))
      assert(json.contains("\"name\":\"Alice\""))
      assert(json.contains("\"age\":30"))
    }

    test("configured - drop null values: Some values preserved") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDropNullValues
      given JsonValueCodec[WithNullable] = deriveJsoniterConfiguredCodec
      val original = WithNullable("Alice", Some("Ali"), 30)
      val json = writeToString(original)
      assert(json.contains("\"nickname\":\"Ali\""))
    }

    test("configured - sum type external tagging with constructor name transform") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withSnakeCaseMemberNames
      given JsonValueCodec[Car] = deriveJsoniterConfiguredCodec
      given JsonValueCodec[Bike] = deriveJsoniterConfiguredCodec
      given JsonValueCodec[Vehicle] = deriveJsoniterConfiguredCodec
      val car: Vehicle = Car("Toyota", 2024)
      val json = writeToString(car)
      // External tagging uses constructor names (not transformed by transformMemberNames)
      assert(json == """{"Car":{"make":"Toyota","year":2024}}""")
      val decoded = readFromString[Vehicle](json)
      assert(decoded == car)
    }

    test("configured - discriminator tagging") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDiscriminator("type")
      given JsonValueCodec[Car] = deriveJsoniterConfiguredCodec
      given JsonValueCodec[Bike] = deriveJsoniterConfiguredCodec
      given JsonValueCodec[Vehicle] = deriveJsoniterConfiguredCodec
      val car: Vehicle = Car("Toyota", 2024)
      val json = writeToString(car)
      assert(json.contains("\"type\":\"Car\""))
      assert(json.contains("\"make\":\"Toyota\""))
    }

    test("configured - cross-codec compatibility with circe (withDefaults)") {
      import io.circe.derivation.Configuration
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.{deriveConfiguredCodec}

      given Configuration = Configuration.default.withDefaults
      given CirceCodec[WithDefaults] = deriveConfiguredCodec

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[WithDefaults] = deriveJsoniterConfiguredCodec

      // Full fields: jsoniter -> circe
      val full = WithDefaults("Alice", 30, false)
      val jsoniterJson = writeToString(full)
      val circeResult = circeDecode[WithDefaults](jsoniterJson)
      assert(circeResult == Right(full))

      // Full fields: circe -> jsoniter
      val circeJson = full.asJson.noSpaces
      val jsoniterResult = readFromString[WithDefaults](circeJson)
      assert(jsoniterResult == full)

      // Missing fields: both should decode with defaults
      val partial = """{"name":"Bob"}"""
      val circePartial = circeDecode[WithDefaults](partial)
      val jsoniterPartial = readFromString[WithDefaults](partial)
      assert(circePartial == Right(jsoniterPartial))
      assert(jsoniterPartial == WithDefaults("Bob", 25, true))
    }

    test("configured - cross-codec compatibility with circe (snake_case)") {
      import io.circe.derivation.Configuration
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.{deriveConfiguredCodec}

      given Configuration = Configuration.default.withDefaults.withSnakeCaseMemberNames
      given CirceCodec[SnakeCaseExample] = deriveConfiguredCodec

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withSnakeCaseMemberNames
      given JsonValueCodec[SnakeCaseExample] = deriveJsoniterConfiguredCodec

      val original = SnakeCaseExample("Alice", "Smith", true)

      // jsoniter -> circe
      val jsoniterJson = writeToString(original)
      val circeResult = circeDecode[SnakeCaseExample](jsoniterJson)
      assert(circeResult == Right(original))

      // circe -> jsoniter
      val circeJson = original.asJson.noSpaces
      val jsoniterResult = readFromString[SnakeCaseExample](circeJson)
      assert(jsoniterResult == original)
    }

    // === Cross-codec: discriminator tests ===

    test("configured - cross-codec compatibility with circe (discriminator)") {
      import io.circe.derivation.Configuration
      import io.circe.{Encoder, Decoder, Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.{deriveConfiguredEncoder, deriveConfiguredDecoder, deriveConfiguredCodec}

      given Configuration = Configuration.default.withDefaults.withDiscriminator("__typename__")
      given CirceCodec[Activity] = deriveConfiguredCodec

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withDiscriminator("__typename__")
      given JsonValueCodec[Activity] = deriveJsoniterConfiguredCodec

      val variants: List[Activity] = List(
        Comment("u1", "hello", true),
        StatusChange("draft", "active", Some("approved")),
        FileUpload("doc.pdf", 1024, List("important", "draft"))
      )

      for v <- variants do
        // jsoniter -> circe
        val jJson = writeToString(v)
        assert(jJson.contains("\"__typename__\""))
        val cResult = circeDecode[Activity](jJson)
        assert(cResult == Right(v))

        // circe -> jsoniter
        val cJson = (v: Activity).asJson.noSpaces
        val jResult = readFromString[Activity](cJson)
        assert(jResult == v)
    }

    test("configured - cross-codec compatibility with circe (discriminator + defaults)") {
      import io.circe.derivation.Configuration
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.deriveConfiguredCodec

      given Configuration = Configuration.default.withDefaults.withDiscriminator("__typename__")
      given CirceCodec[Activity] = deriveConfiguredCodec

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withDiscriminator("__typename__")
      given JsonValueCodec[Activity] = deriveJsoniterConfiguredCodec

      // Missing "pinned" → default false
      val commentJson = """{"__typename__":"Comment","userId":"u1","text":"hi"}"""
      val circeComment = circeDecode[Activity](commentJson)
      val jsoniterComment = readFromString[Activity](commentJson)
      assert(circeComment == Right(jsoniterComment))
      assert(jsoniterComment == Comment("u1", "hi", false))

      // Missing "reason" → default None
      val statusJson = """{"__typename__":"StatusChange","fromStatus":"draft","toStatus":"active"}"""
      val circeStatus = circeDecode[Activity](statusJson)
      val jsoniterStatus = readFromString[Activity](statusJson)
      assert(circeStatus == Right(jsoniterStatus))
      assert(jsoniterStatus == StatusChange("draft", "active", None))

      // Missing "tags" → default Nil
      val uploadJson = """{"__typename__":"FileUpload","fileName":"x.txt","sizeBytes":100}"""
      val circeUpload = circeDecode[Activity](uploadJson)
      val jsoniterUpload = readFromString[Activity](uploadJson)
      assert(circeUpload == Right(jsoniterUpload))
      assert(jsoniterUpload == FileUpload("x.txt", 100, Nil))
    }

    // === Cross-codec: drop-null tests ===

    test("configured - cross-codec compatibility with circe (drop-null)") {
      import io.circe.derivation.Configuration
      import io.circe.{Encoder, Decoder, Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.{deriveConfiguredEncoder, deriveConfiguredDecoder}

      // circe: withDefaults + manual drop-null on encoder (circe has no withDropNullValues config)
      given Configuration = Configuration.default.withDefaults
      val circeEncoder: Encoder.AsObject[SchemaField] =
        deriveConfiguredEncoder[SchemaField].mapJsonObject(_.filter(!_._2.isNull))
      val circeDecoder: Decoder[SchemaField] = deriveConfiguredDecoder

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withDropNullValues
      given JsonValueCodec[SchemaField] = deriveJsoniterConfiguredCodec

      val sparse = SchemaField("string", description = Some("A name"))
      val full = SchemaField("integer", Some("count"), Some("int32"), None, Some(0), Some(100), Some(true), Some("0"))

      for v <- List(sparse, full) do
        // jsoniter -> circe
        val jJson = writeToString(v)
        assert(!jJson.contains(":null"))
        val cResult = circeDecoder.decodeJson(io.circe.parser.parse(jJson).toOption.get)
        assert(cResult == Right(v))

        // circe -> jsoniter
        val cJson = circeEncoder.encodeObject(v).asJson.noSpaces
        assert(!cJson.contains(":null"))
        val jResult = readFromString[SchemaField](cJson)
        assert(jResult == v)

        // Both produce identical decoded result
        val fromJ = circeDecoder.decodeJson(io.circe.parser.parse(jJson).toOption.get)
        val fromC = circeDecoder.decodeJson(io.circe.parser.parse(cJson).toOption.get)
        assert(fromJ == fromC)
    }

    test("configured - cross-codec compatibility with circe (drop-null + defaults decode)") {
      import io.circe.derivation.Configuration
      import io.circe.{Encoder, Decoder, Codec as CirceCodec, *}
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.{deriveConfiguredEncoder, deriveConfiguredDecoder}

      given Configuration = Configuration.default.withDefaults
      val circeDecoder: Decoder[SchemaField] = deriveConfiguredDecoder

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withDropNullValues
      given JsonValueCodec[SchemaField] = deriveJsoniterConfiguredCodec

      // Minimal JSON — all optional fields should default to None
      val minimal = """{"type":"integer"}"""
      val circeResult = circeDecoder.decodeJson(io.circe.parser.parse(minimal).toOption.get)
      val jsoniterResult = readFromString[SchemaField](minimal)
      assert(circeResult == Right(jsoniterResult))
      assert(jsoniterResult == SchemaField("integer"))
    }

    // === Cross-codec: snake_case + drop-null tests ===

    test("configured - cross-codec compatibility with circe (snake_case + drop-null)") {
      import io.circe.derivation.Configuration
      import io.circe.{Encoder, Decoder, Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.{deriveConfiguredEncoder, deriveConfiguredDecoder}

      given Configuration = Configuration.default.withDefaults.withSnakeCaseMemberNames
      val circeEncoder: Encoder.AsObject[ApiResponse] =
        deriveConfiguredEncoder[ApiResponse].mapJsonObject(_.filter(!_._2.isNull))
      val circeDecoder: Decoder[ApiResponse] = deriveConfiguredDecoder

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withSnakeCaseMemberNames.withDropNullValues
      given JsonValueCodec[ApiResponse] = deriveJsoniterConfiguredCodec

      val withSome = ApiResponse("req-1", userName = Some("Alice"))
      val allDefaults = ApiResponse("req-2")

      for v <- List(withSome, allDefaults) do
        // jsoniter -> circe
        val jJson = writeToString(v)
        assert(jJson.contains("\"request_id\""))
        assert(!jJson.contains(":null"))
        val cResult = circeDecoder.decodeJson(io.circe.parser.parse(jJson).toOption.get)
        assert(cResult == Right(v))

        // circe -> jsoniter
        val cJson = circeEncoder.encodeObject(v).asJson.noSpaces
        assert(cJson.contains("\"request_id\""))
        assert(!cJson.contains(":null"))
        val jResult = readFromString[ApiResponse](cJson)
        assert(jResult == v)

      // Verify allDefaults omits optional fields
      val defaultJson = writeToString(allDefaults)
      assert(!defaultJson.contains("user_name"))
      assert(!defaultJson.contains("error_message"))
    }

    // === Sub-trait tests ===

    test("sub-trait - encode flattens hierarchy") {
      given JsonValueCodec[ADTWithSub] = deriveJsoniterCodec
      val v: ADTWithSub = LeafA1(42)
      val json = writeToString(v)
      // Should be flat: {"LeafA1":{"x":42}}, NOT {"SubTraitA":{"LeafA1":{"x":42}}}
      assert(json == """{"LeafA1":{"x":42}}""")
    }

    test("sub-trait - decode flattened key") {
      given JsonValueCodec[ADTWithSub] = deriveJsoniterCodec
      val json = """{"LeafA1":{"x":42}}"""
      val decoded = readFromString[ADTWithSub](json)
      assert(decoded == LeafA1(42))
    }

    test("sub-trait - all variants round-trip") {
      given JsonValueCodec[ADTWithSub] = deriveJsoniterCodec
      val values: List[ADTWithSub] = List(LeafA1(1), LeafA2("hello"), DirectCase(true))
      for v <- values do
        val json = writeToString(v)
        val decoded = readFromString[ADTWithSub](json)
        assert(decoded == v)
    }

    test("sub-trait - direct case works") {
      given JsonValueCodec[ADTWithSub] = deriveJsoniterCodec
      val v: ADTWithSub = DirectCase(true)
      val json = writeToString(v)
      assert(json == """{"DirectCase":{"z":true}}""")
      val decoded = readFromString[ADTWithSub](json)
      assert(decoded == v)
    }

    test("sub-trait - deep hierarchy (sub-sub-trait)") {
      given JsonValueCodec[DeepADT] = deriveJsoniterCodec
      val values: List[DeepADT] = List(DeepLeaf(1), MidLeaf("hi"), TopLeaf(false))
      for v <- values do
        val json = writeToString(v)
        val decoded = readFromString[DeepADT](json)
        assert(decoded == v)
      // Verify flat encoding
      assert(writeToString[DeepADT](DeepLeaf(1)) == """{"DeepLeaf":{"v":1}}""")
    }

    test("sub-trait - diamond inheritance dedup") {
      given JsonValueCodec[DiamondADT] = deriveJsoniterCodec
      val values: List[DiamondADT] = List(DiamondLeaf(1), OnlyA(2), OnlyB(3))
      for v <- values do
        val json = writeToString(v)
        val decoded = readFromString[DiamondADT](json)
        assert(decoded == v)
    }

    test("sub-trait - circe format compatibility") {
      import io.circe.generic.auto.given
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode

      given JsonValueCodec[ADTWithSub] = deriveJsoniterCodec

      val v: ADTWithSub = LeafA1(42)

      // jsoniter -> circe
      val jsoniterJson = writeToString(v)
      val circeResult = circeDecode[ADTWithSub](jsoniterJson)
      assert(circeResult == Right(v))

      // circe -> jsoniter
      val circeJson = (v: ADTWithSub).asJson.noSpaces
      val jsoniterResult = readFromString[ADTWithSub](circeJson)
      assert(jsoniterResult == v)
    }

    test("sub-trait - configured with external tagging") {
      given JsoniterConfiguration = JsoniterConfiguration.default
      given JsonValueCodec[ADTWithSub] = deriveJsoniterConfiguredCodec
      val v: ADTWithSub = LeafA1(42)
      val json = writeToString(v)
      assert(json == """{"LeafA1":{"x":42}}""")
      val decoded = readFromString[ADTWithSub](json)
      assert(decoded == v)
    }

    // === Either codec tests ===

    test("either - Right value round-trip") {
      val v = WithEither(Right(42))
      val json = writeToString(v)
      assert(json.contains(""""result":{"Right":42}"""))
      val decoded = readFromString[WithEither](json)
      assert(decoded == v)
    }

    test("either - Left value round-trip") {
      val v = WithEither(Left("error"))
      val json = writeToString(v)
      assert(json.contains(""""result":{"Left":"error"}"""))
      val decoded = readFromString[WithEither](json)
      assert(decoded == v)
    }

    test("either - nested Either[String, List[Int]]") {
      val v = WithNestedEither(Right(List(1, 2, 3)))
      val decoded = roundtrip(v)
      assert(decoded == v)

      val v2 = WithNestedEither(Left("fail"))
      val decoded2 = roundtrip(v2)
      assert(decoded2 == v2)
    }

    test("either - circe format compatibility") {
      import io.circe.{Encoder, Decoder, *}
      import io.circe.disjunctionCodecs.given
      import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode

      // WithEither has an Either[String, Int] field — circe needs disjunctionCodecs for Either
      given Encoder[WithEither] = deriveEncoder
      given Decoder[WithEither] = deriveDecoder

      val right = WithEither(Right(42))
      val left = WithEither(Left("err"))

      // jsoniter -> circe (Right)
      val jRight = writeToString(right)
      assert(circeDecode[WithEither](jRight) == Right(right))

      // jsoniter -> circe (Left)
      val jLeft = writeToString(left)
      assert(circeDecode[WithEither](jLeft) == Right(left))

      // circe -> jsoniter (Right)
      val cRight = (right: WithEither).asJson.noSpaces
      assert(readFromString[WithEither](cRight) == right)

      // circe -> jsoniter (Left)
      val cLeft = (left: WithEither).asJson.noSpaces
      assert(readFromString[WithEither](cLeft) == left)
    }

    // === Value enum tests ===

    test("value enum - string value round-trip") {
      given JsonValueCodec[Status] = Codecs.stringValueEnum(Status.values, _.value)
      val json = writeToString(Status.Active)
      assert(json == "\"active\"")
      val decoded = readFromString[Status](json)
      assert(decoded == Status.Active)
    }

    test("value enum - all string variants") {
      given JsonValueCodec[Status] = Codecs.stringValueEnum(Status.values, _.value)
      for s <- Status.values do
        val json = writeToString(s)
        assert(json == s"\"${s.value}\"")
        val decoded = readFromString[Status](json)
        assert(decoded == s)
    }

    test("value enum - int value round-trip") {
      given JsonValueCodec[Priority] = Codecs.intValueEnum(Priority.values, _.value)
      val json = writeToString(Priority.High)
      assert(json == "3")
      val decoded = readFromString[Priority](json)
      assert(decoded == Priority.High)
    }

    test("value enum - all int variants") {
      given JsonValueCodec[Priority] = Codecs.intValueEnum(Priority.values, _.value)
      for p <- Priority.values do
        val json = writeToString(p)
        assert(json == p.value.toString)
        val decoded = readFromString[Priority](json)
        assert(decoded == p)
    }

    test("value enum - unknown string value decode error") {
      given JsonValueCodec[Status] = Codecs.stringValueEnum(Status.values, _.value)
      val caught =
        try
          readFromString[Status]("\"unknown\"")
          throw new RuntimeException("expected exception")
        catch
          case e: com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException => e
      assert(caught.getMessage.contains("does not contain value"))
    }

    test("value enum - unknown int value decode error") {
      given JsonValueCodec[Priority] = Codecs.intValueEnum(Priority.values, _.value)
      val caught =
        try
          readFromString[Priority]("99")
          throw new RuntimeException("expected exception")
        catch
          case e: com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException => e
      assert(caught.getMessage.contains("does not contain value"))
    }

    // === Auto-configured derivation tests ===

    test("auto-configured - withDefaults: missing fields use defaults") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      import sanely.jsoniter.configured.auto.given
      case class AutoCfgDefaults(name: String, age: Int = 25, active: Boolean = true)
      val json = """{"name":"Alice"}"""
      val decoded = readFromString[AutoCfgDefaults](json)
      assert(decoded == AutoCfgDefaults("Alice", 25, true))
    }

    test("auto-configured - nested types auto-derived") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      import sanely.jsoniter.configured.auto.given
      case class Inner(x: Int, y: String = "default")
      case class Outer(inner: Inner, label: String)
      val json = """{"inner":{"x":42},"label":"test"}"""
      val decoded = readFromString[Outer](json)
      assert(decoded == Outer(Inner(42, "default"), "test"))
    }

    test("auto-configured - snake_case member names") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withSnakeCaseMemberNames
      import sanely.jsoniter.configured.auto.given
      case class AutoSnake(firstName: String, lastName: String, isActive: Boolean = true)
      val original = AutoSnake("Alice", "Smith", true)
      val json = writeToString(original)
      assert(json.contains("\"first_name\""))
      assert(json.contains("\"last_name\""))
      assert(json.contains("\"is_active\""))
      val decoded = readFromString[AutoSnake](json)
      assert(decoded == original)
    }

    test("auto-configured - drop-null values") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDropNullValues
      import sanely.jsoniter.configured.auto.given
      case class AutoDropNull(name: String, nickname: Option[String], age: Int)
      val original = AutoDropNull("Alice", None, 30)
      val json = writeToString(original)
      assert(!json.contains("nickname"))
      assert(json.contains("\"name\":\"Alice\""))
      assert(json.contains("\"age\":30"))
    }

    test("auto-configured - discriminator tagging") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDiscriminator("type")
      import sanely.jsoniter.configured.auto.given
      sealed trait AutoVehicle
      case class AutoCar(make: String, year: Int) extends AutoVehicle
      case class AutoBike(brand: String) extends AutoVehicle
      val car: AutoVehicle = AutoCar("Toyota", 2024)
      val json = writeToString(car)
      assert(json.contains("\"type\":\"AutoCar\""))
      assert(json.contains("\"make\":\"Toyota\""))
      val decoded = readFromString[AutoVehicle](json)
      assert(decoded == car)
    }

    test("auto-configured - round-trip encode then decode") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withSnakeCaseMemberNames
      import sanely.jsoniter.configured.auto.given
      case class AutoRoundTrip(firstName: String, age: Int = 25, tags: List[String] = Nil)
      val original = AutoRoundTrip("Bob", 30, List("admin", "user"))
      val json = writeToString(original)
      val decoded = readFromString[AutoRoundTrip](json)
      assert(decoded == original)
    }

    test("auto-configured - cross-codec with circe") {
      import io.circe.derivation.Configuration
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.deriveConfiguredCodec

      case class AutoCrossCodec(firstName: String, lastName: String, age: Int = 25)

      given Configuration = Configuration.default.withDefaults.withSnakeCaseMemberNames
      given CirceCodec[AutoCrossCodec] = deriveConfiguredCodec

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withSnakeCaseMemberNames
      import sanely.jsoniter.configured.auto.given

      val original = AutoCrossCodec("Alice", "Smith", 30)

      // jsoniter -> circe
      val jJson = writeToString(original)
      val cResult = circeDecode[AutoCrossCodec](jJson)
      assert(cResult == Right(original))

      // circe -> jsoniter
      val cJson = original.asJson.noSpaces
      val jResult = readFromString[AutoCrossCodec](cJson)
      assert(jResult == original)

      // Partial (missing defaults): both decode identically
      val partial = """{"first_name":"Bob","last_name":"Jones"}"""
      val cPartial = circeDecode[AutoCrossCodec](partial)
      val jPartial = readFromString[AutoCrossCodec](partial)
      assert(cPartial == Right(jPartial))
      assert(jPartial == AutoCrossCodec("Bob", "Jones", 25))
    }

    test("enum - circe format compatibility") {
      import io.circe.derivation.Configuration
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.deriveEnumCodec

      given Configuration = Configuration.default
      given CirceCodec[Color] = deriveEnumCodec

      // jsoniter → circe
      val jsoniterJson = writeToString(Color.Green)
      val circeResult = circeDecode[Color](jsoniterJson)
      assert(circeResult == Right(Color.Green))

      // circe → jsoniter
      val circeJson = (Color.Blue: Color).asJson.noSpaces
      val jsoniterResult = readFromString[Color](circeJson)
      assert(jsoniterResult == Color.Blue)
    }
  }
