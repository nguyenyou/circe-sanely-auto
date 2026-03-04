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

// Phase 6 types — recursive
sealed trait RecursiveAdtExample
case class BaseAdtExample(a: String) extends RecursiveAdtExample
case class NestedAdtExample(r: RecursiveAdtExample) extends RecursiveAdtExample

case class RecursiveWithOptionExample(o: Option[RecursiveWithOptionExample])

// Phase 7 types — large types & edge cases

case class LongClass(
  v1: String, v2: String, v3: String, v4: String, v5: String,
  v6: String, v7: String, v8: String, v9: String, v10: String,
  v11: String, v12: String, v13: String, v14: String, v15: String,
  v16: String, v17: String, v18: String, v19: String, v20: String,
  v21: String, v22: String, v23: String, v24: String, v25: String,
  v26: String, v27: String, v28: String, v29: String, v30: String,
  v31: String, v32: String, v33: String
)

enum LongSum:
  case V1(str: String)
  case V2(str: String)
  case V3(str: String)
  case V4(str: String)
  case V5(str: String)
  case V6(str: String)
  case V7(str: String)
  case V8(str: String)
  case V9(str: String)
  case V10(str: String)
  case V11(str: String)
  case V12(str: String)
  case V13(str: String)
  case V14(str: String)
  case V15(str: String)
  case V16(str: String)
  case V17(str: String)
  case V18(str: String)
  case V19(str: String)
  case V20(str: String)
  case V21(str: String)
  case V22(str: String)
  case V23(str: String)
  case V24(str: String)
  case V25(str: String)
  case V26(str: String)
  case V27(str: String)
  case V28(str: String)
  case V29(str: String)
  case V30(str: String)
  case V31(str: String)
  case V32(str: String)
  case V33(str: String)

enum LongEnum:
  case V1, V2, V3, V4, V5, V6, V7, V8, V9, V10,
    V11, V12, V13, V14, V15, V16, V17, V18, V19, V20,
    V21, V22, V23, V24, V25, V26, V27, V28, V29, V30,
    V31, V32, V33

sealed trait ADTWithSubTraitExample
sealed trait SubTrait extends ADTWithSubTraitExample
case class TheClass(a: Int) extends SubTrait

// Nested generic type
case class BarBoxFoo(foo: Box[Foo])

// Recursive enum
enum RecursiveEnumAdt:
  case Base(a: String)
  case Nested(r: RecursiveEnumAdt)

// Recursive with Seq
case class RecursiveWithSeq(children: Seq[RecursiveWithSeq], value: String)

// Recursive with Map
case class RecursiveWithMap(children: Map[String, RecursiveWithMap], value: String)

// Tagged type member
case class ProductWithTaggedMember(x: ProductWithTaggedMember.TaggedString)
object ProductWithTaggedMember:
  sealed trait Tag
  type TaggedString = String & Tag
  given Codec[TaggedString] = Codec.from(
    summon[Decoder[String]].map(_.asInstanceOf[TaggedString]),
    summon[Encoder[String]].contramap(x => x: String)
  )

// Codec derivation test type
case class CodecTestProduct(a: Int, b: String)
object CodecTestProduct:
  given Codec.AsObject[CodecTestProduct] = SanelyCodec.derived

sealed trait CodecTestAdt
case class CodecCase(i: Int) extends CodecTestAdt
case object CodecObj extends CodecTestAdt
object CodecTestAdt:
  given Codec.AsObject[CodecTestAdt] = SanelyCodec.derived

// Phase 9 types — semiauto (explicit derived) in companion objects
case class SemiAutoProduct(x: Int, y: String)
object SemiAutoProduct:
  given Encoder.AsObject[SemiAutoProduct] = SanelyEncoder.derived
  given Decoder[SemiAutoProduct] = SanelyDecoder.derived

sealed trait SemiAutoAdt
case class SemiAutoCase(i: Int) extends SemiAutoAdt
case object SemiAutoObj extends SemiAutoAdt
object SemiAutoAdt:
  given Encoder.AsObject[SemiAutoAdt] = SanelyEncoder.derived
  given Decoder[SemiAutoAdt] = SanelyDecoder.derived

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

    // --- Phase 6: Recursive Types ---

    test("Recursive sealed trait round-trip (base case)") {
      val v: RecursiveAdtExample = BaseAdtExample("hello")
      val json = v.asJson
      val expected = Json.obj("BaseAdtExample" -> Json.obj("a" -> Json.fromString("hello")))
      assert(json == expected)
      val decoded = decode[RecursiveAdtExample](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Recursive sealed trait round-trip (nested depth 1)") {
      val v: RecursiveAdtExample = NestedAdtExample(BaseAdtExample("inner"))
      val json = v.asJson
      val expected = Json.obj("NestedAdtExample" -> Json.obj(
        "r" -> Json.obj("BaseAdtExample" -> Json.obj("a" -> Json.fromString("inner")))
      ))
      assert(json == expected)
      val decoded = decode[RecursiveAdtExample](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Recursive sealed trait round-trip (nested depth 2)") {
      val v: RecursiveAdtExample = NestedAdtExample(NestedAdtExample(BaseAdtExample("deep")))
      val json = v.asJson
      val decoded = decode[RecursiveAdtExample](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Recursive with Option round-trip (None terminates)") {
      val v = RecursiveWithOptionExample(None)
      val json = v.asJson
      val expected = Json.obj("o" -> Json.Null)
      assert(json == expected)
      val decoded = decode[RecursiveWithOptionExample](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Recursive with Option round-trip (nested depth 2)") {
      val v = RecursiveWithOptionExample(Some(RecursiveWithOptionExample(Some(RecursiveWithOptionExample(None)))))
      val json = v.asJson
      val decoded = decode[RecursiveWithOptionExample](json.noSpaces)
      assert(decoded == Right(v))
    }

    // --- Phase 7: Large Types & Edge Cases ---

    test("Large product round-trip (LongClass with 33 fields)") {
      val v = LongClass(
        "a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a10",
        "a11", "a12", "a13", "a14", "a15", "a16", "a17", "a18", "a19", "a20",
        "a21", "a22", "a23", "a24", "a25", "a26", "a27", "a28", "a29", "a30",
        "a31", "a32", "a33"
      )
      val json = v.asJson
      // Spot-check a few fields
      assert(json.hcursor.downField("v1").as[String] == Right("a1"))
      assert(json.hcursor.downField("v33").as[String] == Right("a33"))
      val decoded = decode[LongClass](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Large sum round-trip (LongSum with 33 variants)") {
      val v1: LongSum = LongSum.V1("hello")
      val json1 = v1.asJson
      assert(json1 == Json.obj("V1" -> Json.obj("str" -> Json.fromString("hello"))))
      val decoded1 = decode[LongSum](json1.noSpaces)
      assert(decoded1 == Right(v1))

      val v33: LongSum = LongSum.V33("last")
      val json33 = v33.asJson
      assert(json33 == Json.obj("V33" -> Json.obj("str" -> Json.fromString("last"))))
      val decoded33 = decode[LongSum](json33.noSpaces)
      assert(decoded33 == Right(v33))
    }

    test("Large enum round-trip (LongEnum with 33 nullary cases)") {
      val v1: LongEnum = LongEnum.V1
      val json1 = v1.asJson
      assert(json1 == Json.obj("V1" -> Json.obj()))
      val decoded1 = decode[LongEnum](json1.noSpaces)
      assert(decoded1 == Right(v1))

      val v33: LongEnum = LongEnum.V33
      val json33 = v33.asJson
      assert(json33 == Json.obj("V33" -> Json.obj()))
      val decoded33 = decode[LongEnum](json33.noSpaces)
      assert(decoded33 == Right(v33))
    }

    test("Sub-trait flattening (ADTWithSubTraitExample)") {
      // TheClass extends SubTrait extends ADTWithSubTraitExample
      // Should encode as {"TheClass":{"a":0}}, NOT {"SubTrait":{"TheClass":{"a":0}}}
      val v: ADTWithSubTraitExample = TheClass(0)
      val json = v.asJson
      val expected = Json.obj("TheClass" -> Json.obj("a" -> Json.fromInt(0)))
      assert(json == expected)
      val decoded = decode[ADTWithSubTraitExample](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Decoder ignores superfluous JSON keys") {
      val expected = Right(Adt1Class1(3): Adt1)
      assert(decode[Adt1]("""{"Adt1Class1":{"int":3}}""") == expected)
      assert(decode[Adt1]("""{"extraField":true,"Adt1Class1":{"int":3}}""") == expected)
      assert(decode[Adt1]("""{"extraField":true,"extraField2":15,"Adt1Class1":{"int":3}}""") == expected)
      assert(decode[Adt1]("""{"Adt1Class1":{"int":3},"extraField":true}""") == expected)
    }

    // --- Phase 9: Semiauto API ---

    test("Semiauto product round-trip (SemiAutoProduct)") {
      val v = SemiAutoProduct(42, "hello")
      val json = Encoder.AsObject[SemiAutoProduct].encodeObject(v)
      val expected = JsonObject("x" -> Json.fromInt(42), "y" -> Json.fromString("hello"))
      assert(json == expected)
      val decoded = decode[SemiAutoProduct](Json.fromJsonObject(json).noSpaces)
      assert(decoded == Right(v))
    }

    test("Semiauto ADT round-trip (SemiAutoAdt)") {
      val v1: SemiAutoAdt = SemiAutoCase(7)
      val json1 = v1.asJson(using Encoder.AsObject[SemiAutoAdt])
      assert(json1 == Json.obj("SemiAutoCase" -> Json.obj("i" -> Json.fromInt(7))))
      val decoded1 = decode[SemiAutoAdt](json1.noSpaces)
      assert(decoded1 == Right(v1))

      val v2: SemiAutoAdt = SemiAutoObj
      val json2 = v2.asJson(using Encoder.AsObject[SemiAutoAdt])
      assert(json2 == Json.obj("SemiAutoObj" -> Json.obj()))
      val decoded2 = decode[SemiAutoAdt](json2.noSpaces)
      assert(decoded2 == Right(v2))
    }

    test("Local case class with strict val (no StackOverflowError)") {
      case class LocalCC(n: Int, s: String)
      object LocalCC:
        implicit val enc: Encoder.AsObject[LocalCC] = SanelyEncoder.derived
        implicit val dec: Decoder[LocalCC] = SanelyDecoder.derived

      val v = LocalCC(1, "local")
      val json = LocalCC.enc.encodeObject(v)
      assert(json == JsonObject("n" -> Json.fromInt(1), "s" -> Json.fromString("local")))
      val decoded = LocalCC.dec.decodeJson(Json.fromJsonObject(json))
      assert(decoded == Right(v))
    }

    test("Local ADT with strict val (no StackOverflowError)") {
      sealed trait LocalAdt
      case class LocalCase(x: Int) extends LocalAdt
      case object LocalObj extends LocalAdt
      object LocalAdt:
        implicit val enc: Encoder.AsObject[LocalAdt] = SanelyEncoder.derived
        implicit val dec: Decoder[LocalAdt] = SanelyDecoder.derived

      val v1: LocalAdt = LocalCase(42)
      val json1 = LocalAdt.enc.encodeObject(v1)
      assert(json1 == JsonObject("LocalCase" -> Json.obj("x" -> Json.fromInt(42))))
      val decoded1 = LocalAdt.dec.decodeJson(Json.fromJsonObject(json1))
      assert(decoded1 == Right(v1))

      val v2: LocalAdt = LocalObj
      val json2 = LocalAdt.enc.encodeObject(v2)
      assert(json2 == JsonObject("LocalObj" -> Json.obj()))
      val decoded2 = LocalAdt.dec.decodeJson(Json.fromJsonObject(json2))
      assert(decoded2 == Right(v2))
    }

    // --- Phase 8: Error Cases ---

    test("Wrong JSON shape for product returns DecodingFailure") {
      // Missing required field "s"
      val result = decode[Simple]("""{"i":42}""")
      assert(result.isLeft)

      // Wrong type for field
      val result2 = decode[Simple]("""{"i":"not_an_int","s":"hello"}""")
      assert(result2.isLeft)
    }

    test("Unknown variant for sum type returns DecodingFailure") {
      val result = decode[Foo]("""{"UnknownVariant":{"x":1}}""")
      assert(result.isLeft)
    }

    test("Non-object for sum type returns DecodingFailure") {
      // Array instead of object
      val result = decode[Foo]("""[1,2,3]""")
      assert(result.isLeft)

      // String instead of object
      val result2 = decode[Foo](""""hello"""")
      assert(result2.isLeft)

      // Number instead of object
      val result3 = decode[Foo]("""42""")
      assert(result3.isLeft)
    }

    test("Single-field product with extreme values") {
      // Long.MaxValue/MinValue lose precision on Scala.js due to JSON number representation
      if !Platform.isJS then
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

    // --- New coverage: nested generic ---

    test("Nested generic Box[Foo] in product round-trip") {
      val v = BarBoxFoo(Box[Foo](Bar(1, "x")))
      val json = v.asJson
      val expected = Json.obj("foo" -> Json.obj("a" -> Json.obj("Bar" -> Json.obj("i" -> Json.fromInt(1), "s" -> Json.fromString("x")))))
      assert(json == expected)
      val decoded = decode[BarBoxFoo](json.noSpaces)
      assert(decoded == Right(v))
    }

    // --- Recursive enum ---

    test("Recursive enum base case round-trip") {
      val v: RecursiveEnumAdt = RecursiveEnumAdt.Base("hello")
      val json = v.asJson
      val expected = Json.obj("Base" -> Json.obj("a" -> Json.fromString("hello")))
      assert(json == expected)
      val decoded = decode[RecursiveEnumAdt](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("Recursive enum nested depth 2 round-trip") {
      val v: RecursiveEnumAdt = RecursiveEnumAdt.Nested(RecursiveEnumAdt.Nested(RecursiveEnumAdt.Base("deep")))
      val json = v.asJson
      val decoded = decode[RecursiveEnumAdt](json.noSpaces)
      assert(decoded == Right(v))
    }

    // --- Recursive with Seq ---

    test("Recursive with Seq round-trip") {
      val v = RecursiveWithSeq(Seq(RecursiveWithSeq(Seq.empty, "child")), "parent")
      val json = v.asJson
      val decoded = decode[RecursiveWithSeq](json.noSpaces)
      assert(decoded == Right(v))
    }

    // --- Recursive with Map ---

    test("Recursive with Map round-trip") {
      val v = RecursiveWithMap(Map("child" -> RecursiveWithMap(Map.empty, "leaf")), "root")
      val json = v.asJson
      val decoded = decode[RecursiveWithMap](json.noSpaces)
      assert(decoded == Right(v))
    }

    // --- Tagged type member ---

    test("Tagged type member round-trip") {
      val v = ProductWithTaggedMember("hello".asInstanceOf[ProductWithTaggedMember.TaggedString])
      val json = v.asJson
      assert(json == Json.obj("x" -> Json.fromString("hello")))
      val decoded = decode[ProductWithTaggedMember](json.noSpaces)
      assert(decoded == Right(v))
    }

    // --- Codec derivation ---

    test("Codec.AsObject derivation product round-trip") {
      val v = CodecTestProduct(42, "hello")
      val json = v.asJson(using CodecTestProduct.given_AsObject_CodecTestProduct)
      val expected = Json.obj("a" -> Json.fromInt(42), "b" -> Json.fromString("hello"))
      assert(json == expected)
      val decoded = json.as[CodecTestProduct](using CodecTestProduct.given_AsObject_CodecTestProduct)
      assert(decoded == Right(v))
    }

    test("Codec.AsObject derivation ADT round-trip") {
      val v1: CodecTestAdt = CodecCase(7)
      val json1 = v1.asJson(using CodecTestAdt.given_AsObject_CodecTestAdt)
      assert(json1 == Json.obj("CodecCase" -> Json.obj("i" -> Json.fromInt(7))))
      val decoded1 = json1.as[CodecTestAdt](using CodecTestAdt.given_AsObject_CodecTestAdt)
      assert(decoded1 == Right(v1))
    }

    // --- io.circe.generic.auto alias ---

    test("io.circe.generic.auto alias works") {
      import io.circe.generic.auto.given
      case class AutoAlias(x: Int, y: String)
      val v = AutoAlias(1, "hi")
      val json = v.asJson
      assert(json == Json.obj("x" -> Json.fromInt(1), "y" -> Json.fromString("hi")))
      val decoded = decode[AutoAlias](json.noSpaces)
      assert(decoded == Right(v))
    }

    // --- io.circe.generic.semiauto alias ---

    test("io.circe.generic.semiauto alias works") {
      case class SemiAlias(a: Int, b: String)
      given Encoder.AsObject[SemiAlias] = io.circe.generic.semiauto.deriveEncoder
      given Decoder[SemiAlias] = io.circe.generic.semiauto.deriveDecoder

      val v = SemiAlias(1, "hi")
      val json = v.asJson
      assert(json == Json.obj("a" -> Json.fromInt(1), "b" -> Json.fromString("hi")))
      val decoded = decode[SemiAlias](json.noSpaces)
      assert(decoded == Right(v))
    }

    test("io.circe.generic.semiauto.deriveCodec works") {
      case class SemiCodecAlias(x: Int, y: String)
      given Codec.AsObject[SemiCodecAlias] = io.circe.generic.semiauto.deriveCodec

      val v = SemiCodecAlias(1, "hi")
      val json = v.asJson
      assert(json == Json.obj("x" -> Json.fromInt(1), "y" -> Json.fromString("hi")))
      val decoded = decode[SemiCodecAlias](json.noSpaces)
      assert(decoded == Right(v))
    }
  }
