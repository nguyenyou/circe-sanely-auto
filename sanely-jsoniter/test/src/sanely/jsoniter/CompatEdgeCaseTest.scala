package sanely.jsoniter

import utest.*
import com.github.plokhotnyuk.jsoniter_scala.core.*

// === Edge case test types ===

// Null vs missing semantics
case class NullVsMissing(a: Option[Int] = Some(42), b: String = "default", c: Option[String] = None)

// Numeric edge cases
case class NumericEdges(
  i: Int, l: Long, f: Float, d: Double,
  bd: BigDecimal, bi: BigInt
)

// Unicode & special chars
case class UnicodeFields(name: String, value: String)
case class SpecialFieldNames(`type`: String, `class`: Int, `null`: Boolean)

// Deeply nested recursive
case class DeepNode(value: Int, child: Option[DeepNode])

// Singleton case objects in ADT
sealed trait MixedADT
case class MixedProduct(x: Int) extends MixedADT
case object MixedSingleton extends MixedADT

// Multiple Option fields
case class ManyOptions(
  a: Option[String], b: Option[Int], c: Option[Boolean],
  d: Option[List[String]], e: Option[Double]
)

// Empty collections
case class WithEmptyCollections(xs: List[Int], ys: Map[String, String], zs: Set[Double])

// Single-field product
case class SingleField(only: String)

// All fields have defaults
case class AllDefaults(a: Int = 1, b: String = "hello", c: Boolean = true, d: Option[String] = Some("opt"))

// Configured sum with many variants
sealed trait ManyVariants
case class VarA(x: Int) extends ManyVariants
case class VarB(y: String) extends ManyVariants
case class VarC(z: Boolean) extends ManyVariants
case object VarD extends ManyVariants

// Nested containers (List of Options, Map of Lists, etc.)
case class NestedContainers(
  listOpt: List[Option[Int]],
  mapList: Map[String, List[String]],
  optList: Option[List[Int]]
)

// IndexedSeq fields (#146)
case class WithIndexedSeq(ids: IndexedSeq[Int], names: IndexedSeq[String])

// Iterable and Array fields (#151)
case class WithIterable(items: Iterable[Int], labels: Iterable[String])
case class WithArray(nums: Array[Int], words: Array[String]):
  override def equals(that: Any): Boolean = that match
    case o: WithArray => java.util.Arrays.equals(nums, o.nums) && java.util.Arrays.equals(words.asInstanceOf[Array[AnyRef]], o.words.asInstanceOf[Array[AnyRef]])
    case _ => false

// Field name that needs JSON escaping
case class QuotedFieldName(`a"b`: String, `c\\d`: String)

// Type with backtick field names (Scala keywords)
case class KeywordFields(`val`: Int, `type`: String, `class`: Boolean)

object CompatEdgeCaseTest extends TestSuite:
  import sanely.jsoniter.semiauto.*

  private def roundtrip[A: JsonValueCodec](value: A): A =
    val json = writeToString(value)
    readFromString[A](json)

  val tests = Tests {

    // =========================================================================
    // 1. Null vs Missing vs Default Semantics
    //    (Inspired by circe ConfiguredDerivesSuite lines 206-248)
    // =========================================================================

    test("null-vs-missing - configured: missing field uses default") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[NullVsMissing] = deriveJsoniterConfiguredCodec
      // Missing 'a' → uses default Some(42)
      val json = """{"b":"hello","c":"world"}"""
      val decoded = readFromString[NullVsMissing](json)
      assert(decoded.a == Some(42))
      assert(decoded.b == "hello")
      assert(decoded.c == Some("world"))
    }

    test("null-vs-missing - configured: explicit null on Option field → None") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[NullVsMissing] = deriveJsoniterConfiguredCodec
      // Explicit null overrides the default Some(42)
      val json = """{"a":null,"b":"hello"}"""
      val decoded = readFromString[NullVsMissing](json)
      assert(decoded.a == None)
      assert(decoded.b == "hello")
      assert(decoded.c == None) // default None preserved
    }

    test("null-vs-missing - configured: all fields missing → all defaults") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[NullVsMissing] = deriveJsoniterConfiguredCodec
      val json = """{}"""
      val decoded = readFromString[NullVsMissing](json)
      assert(decoded == NullVsMissing(Some(42), "default", None))
    }

    test("null-vs-missing - cross-codec with circe: null vs missing") {
      import io.circe.derivation.Configuration
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.deriveConfiguredCodec

      given Configuration = Configuration.default.withDefaults
      given CirceCodec[NullVsMissing] = deriveConfiguredCodec

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[NullVsMissing] = deriveJsoniterConfiguredCodec

      // Missing field — both should use default
      val missing = """{}"""
      val circeMissing = circeDecode[NullVsMissing](missing)
      val jsoniterMissing = readFromString[NullVsMissing](missing)
      assert(circeMissing == Right(jsoniterMissing))

      // Explicit null on Option with default — both should get None
      val withNull = """{"a":null}"""
      val circeNull = circeDecode[NullVsMissing](withNull)
      val jsoniterNull = readFromString[NullVsMissing](withNull)
      assert(circeNull == Right(jsoniterNull))
      assert(jsoniterNull.a == None)
    }

    test("null-vs-missing - all-defaults type: empty object decodes") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[AllDefaults] = deriveJsoniterConfiguredCodec
      val decoded = readFromString[AllDefaults]("{}")
      assert(decoded == AllDefaults())
    }

    test("null-vs-missing - all-defaults cross-codec") {
      import io.circe.derivation.Configuration
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.deriveConfiguredCodec

      given Configuration = Configuration.default.withDefaults
      given CirceCodec[AllDefaults] = deriveConfiguredCodec

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[AllDefaults] = deriveJsoniterConfiguredCodec

      val empty = "{}"
      val circeResult = circeDecode[AllDefaults](empty)
      val jsoniterResult = readFromString[AllDefaults](empty)
      assert(circeResult == Right(jsoniterResult))
      assert(jsoniterResult == AllDefaults())

      // Full round-trip
      val full = AllDefaults(99, "world", false, None)
      val jJson = writeToString(full)
      assert(circeDecode[AllDefaults](jJson) == Right(full))
    }

    // =========================================================================
    // 2. Numeric Edge Cases
    //    (Inspired by jsoniter-scala JsonCodecMakerSpec lines 336-419)
    // =========================================================================

    test("numeric - Int boundary values") {
      case class IntBounds(min: Int, max: Int, zero: Int)
      given JsonValueCodec[IntBounds] = deriveJsoniterCodec
      val v = IntBounds(Int.MinValue, Int.MaxValue, 0)
      val decoded = roundtrip(v)
      assert(decoded == v)
      // Verify exact JSON
      val json = writeToString(v)
      assert(json.contains(Int.MinValue.toString))
      assert(json.contains(Int.MaxValue.toString))
    }

    test("numeric - Long boundary values") {
      case class LongBounds(min: Long, max: Long)
      given JsonValueCodec[LongBounds] = deriveJsoniterCodec
      val v = LongBounds(Long.MinValue, Long.MaxValue)
      val decoded = roundtrip(v)
      assert(decoded == v)
    }

    test("numeric - Long boundary cross-codec with circe") {
      import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}
      import io.circe.{Encoder, Decoder, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode

      case class LongBounds(min: Long, max: Long)
      given JsonValueCodec[LongBounds] = deriveJsoniterCodec
      given Encoder[LongBounds] = deriveEncoder
      given Decoder[LongBounds] = deriveDecoder

      // Use JS-safe integer range (2^53-1) to avoid Scala.js double precision loss
      val v = LongBounds(-9007199254740991L, 9007199254740991L)

      // jsoniter -> circe
      val jJson = writeToString(v)
      val cResult = circeDecode[LongBounds](jJson)
      assert(cResult == Right(v))

      // circe -> jsoniter
      val cJson = v.asJson.noSpaces
      val jResult = readFromString[LongBounds](cJson)
      assert(jResult == v)
    }

    test("numeric - Float edge values") {
      case class FloatEdges(min: Float, max: Float, minPos: Float, negZero: Float)
      given JsonValueCodec[FloatEdges] = deriveJsoniterCodec
      val v = FloatEdges(Float.MinValue, Float.MaxValue, Float.MinPositiveValue, -0.0f)
      val decoded = roundtrip(v)
      assert(decoded.min == v.min)
      assert(decoded.max == v.max)
      assert(decoded.minPos == v.minPos)
    }

    test("numeric - Double edge values") {
      case class DoubleEdges(min: Double, max: Double, minPos: Double, negZero: Double)
      given JsonValueCodec[DoubleEdges] = deriveJsoniterCodec
      val v = DoubleEdges(Double.MinValue, Double.MaxValue, Double.MinPositiveValue, -0.0)
      val decoded = roundtrip(v)
      assert(decoded.min == v.min)
      assert(decoded.max == v.max)
      assert(decoded.minPos == v.minPos)
    }

    test("numeric - BigDecimal precision") {
      case class BigDecEdge(v: BigDecimal)
      given JsonValueCodec[BigDecEdge] = deriveJsoniterCodec
      // jsoniter-scala uses default MathContext for readBigDecimal,
      // so very high precision values get rounded. Test with values
      // that fit within default precision.
      val values = List(
        BigDecimal("0"),
        BigDecimal("0.1"),
        BigDecimal("12345678.123456789"),
        BigDecimal("-0.00000000001"),
        BigDecimal("99999999999999999999"),
      )
      for bd <- values do
        val v = BigDecEdge(bd)
        val decoded = roundtrip(v)
        assert(decoded.v == bd)
    }

    test("numeric - BigInt large values") {
      case class BigIntEdge(v: BigInt)
      given JsonValueCodec[BigIntEdge] = deriveJsoniterCodec
      val values = List(
        BigInt("0"),
        BigInt("999999999999999999999999999999"),
        BigInt("-999999999999999999999999999999"),
        BigInt("1" * 50),
      )
      for bi <- values do
        val v = BigIntEdge(bi)
        val decoded = roundtrip(v)
        assert(decoded.v == bi)
    }

    test("numeric - Byte and Short boundaries") {
      case class SmallInts(b: Byte, s: Short)
      given JsonValueCodec[SmallInts] = deriveJsoniterCodec
      val v = SmallInts(Byte.MinValue, Short.MinValue)
      assert(roundtrip(v) == v)
      val v2 = SmallInts(Byte.MaxValue, Short.MaxValue)
      assert(roundtrip(v2) == v2)
    }

    test("numeric - zero and negative zero") {
      case class Zeros(i: Int, l: Long, f: Float, d: Double)
      given JsonValueCodec[Zeros] = deriveJsoniterCodec
      val v = Zeros(0, 0L, 0.0f, 0.0)
      val decoded = roundtrip(v)
      assert(decoded == v)
    }

    // =========================================================================
    // 3. Unicode, Special Characters, and Escaping
    //    (Inspired by jsoniter-scala JsonCodecMakerSpec lines 2189-2203)
    // =========================================================================

    test("string - empty string round-trip") {
      given JsonValueCodec[SingleField] = deriveJsoniterCodec
      val v = SingleField("")
      val json = writeToString(v)
      assert(json == """{"only":""}""")
      assert(readFromString[SingleField](json) == v)
    }

    test("string - unicode characters") {
      given JsonValueCodec[UnicodeFields] = deriveJsoniterCodec
      val values = List(
        UnicodeFields("hello", "world"),                    // ASCII
        UnicodeFields("héllo", "wörld"),                    // Latin extended
        UnicodeFields("こんにちは", "世界"),                   // CJK
        UnicodeFields("🎉", "🚀"),                          // Emoji (surrogate pairs)
        UnicodeFields("mixed αβγ 123", "Σ sigma"),          // Greek
      )
      for v <- values do
        val decoded = roundtrip(v)
        assert(decoded == v)
    }

    test("string - control characters") {
      given JsonValueCodec[UnicodeFields] = deriveJsoniterCodec
      val values = List(
        UnicodeFields("tab\there", "newline\nhere"),
        UnicodeFields("cr\rhere", "backslash\\here"),
        UnicodeFields("quote\"here", "slash/here"),
      )
      for v <- values do
        val decoded = roundtrip(v)
        assert(decoded == v)
    }

    test("string - control chars cross-codec with circe") {
      import io.circe.generic.semiauto.deriveCodec
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode

      given CirceCodec[UnicodeFields] = deriveCodec
      given JsonValueCodec[UnicodeFields] = deriveJsoniterCodec

      val v = UnicodeFields("tab\there", "quote\"here")

      // jsoniter -> circe
      val jJson = writeToString(v)
      assert(circeDecode[UnicodeFields](jJson) == Right(v))

      // circe -> jsoniter
      val cJson = v.asJson.noSpaces
      assert(readFromString[UnicodeFields](cJson) == v)
    }

    test("string - unicode cross-codec with circe") {
      import io.circe.generic.semiauto.deriveCodec
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode

      given CirceCodec[UnicodeFields] = deriveCodec
      given JsonValueCodec[UnicodeFields] = deriveJsoniterCodec

      val v = UnicodeFields("こんにちは", "🎉🚀")

      // jsoniter -> circe
      val jJson = writeToString(v)
      assert(circeDecode[UnicodeFields](jJson) == Right(v))

      // circe -> jsoniter
      val cJson = v.asJson.noSpaces
      assert(readFromString[UnicodeFields](cJson) == v)
    }

    test("string - special Scala field names") {
      given JsonValueCodec[SpecialFieldNames] = deriveJsoniterCodec
      val v = SpecialFieldNames("hello", 42, true)
      val json = writeToString(v)
      assert(json.contains("\"type\""))
      assert(json.contains("\"class\""))
      assert(json.contains("\"null\""))
      val decoded = readFromString[SpecialFieldNames](json)
      assert(decoded == v)
    }

    test("string - backtick field names cross-codec") {
      import io.circe.generic.semiauto.deriveCodec
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode

      given CirceCodec[KeywordFields] = deriveCodec
      given JsonValueCodec[KeywordFields] = deriveJsoniterCodec

      val v = KeywordFields(42, "hello", true)
      val jJson = writeToString(v)
      assert(circeDecode[KeywordFields](jJson) == Right(v))
      val cJson = v.asJson.noSpaces
      assert(readFromString[KeywordFields](cJson) == v)
    }

    // =========================================================================
    // 4. Whitespace Tolerance in JSON Input
    //    (Inspired by jsoniter-scala JsonCodecMakerSpec lines 2171-2175)
    // =========================================================================

    test("whitespace - pretty-printed JSON decodes correctly") {
      given JsonValueCodec[SingleField] = deriveJsoniterCodec
      val json = """  {  "only"  :  "hello"  }  """
      val decoded = readFromString[SingleField](json)
      assert(decoded == SingleField("hello"))
    }

    test("whitespace - tabs and newlines in JSON") {
      given JsonValueCodec[SingleField] = deriveJsoniterCodec
      val json = "{\n\t\"only\"\t:\t\"hello\"\n}"
      val decoded = readFromString[SingleField](json)
      assert(decoded == SingleField("hello"))
    }

    test("whitespace - mixed whitespace with nested objects") {
      case class Inner(x: Int)
      case class Outer(inner: Inner, name: String)
      given JsonValueCodec[Inner] = deriveJsoniterCodec
      given JsonValueCodec[Outer] = deriveJsoniterCodec
      val json =
        """{
          |  "inner" : {
          |    "x" : 42
          |  },
          |  "name" : "test"
          |}""".stripMargin
      val decoded = readFromString[Outer](json)
      assert(decoded == Outer(Inner(42), "test"))
    }

    test("whitespace - carriage return handling") {
      given JsonValueCodec[SingleField] = deriveJsoniterCodec
      val json = "{\r\n  \"only\": \"hello\"\r\n}"
      val decoded = readFromString[SingleField](json)
      assert(decoded == SingleField("hello"))
    }

    // =========================================================================
    // 5. Discriminator Position Independence
    //    (Inspired by circe ConfiguredDerivesSuite lines 266-375)
    // =========================================================================

    test("discriminator - at beginning (fast path)") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDiscriminator("type")
      given JsonValueCodec[MixedADT] = deriveJsoniterConfiguredCodec
      val json = """{"type":"MixedProduct","x":42}"""
      val decoded = readFromString[MixedADT](json)
      assert(decoded == MixedProduct(42))
    }

    test("discriminator - at end (slow path)") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDiscriminator("type")
      given JsonValueCodec[MixedADT] = deriveJsoniterConfiguredCodec
      val json = """{"x":42,"type":"MixedProduct"}"""
      val decoded = readFromString[MixedADT](json)
      assert(decoded == MixedProduct(42))
    }

    test("discriminator - at middle position") {
      sealed trait ThreeFields
      case class TF(a: Int, b: String, c: Boolean) extends ThreeFields
      given JsoniterConfiguration = JsoniterConfiguration.default.withDiscriminator("kind")
      given JsonValueCodec[ThreeFields] = deriveJsoniterConfiguredCodec
      val json = """{"a":1,"kind":"TF","b":"hello","c":true}"""
      val decoded = readFromString[ThreeFields](json)
      assert(decoded == TF(1, "hello", true))
    }

    test("discriminator - cross-codec: discriminator at end") {
      import io.circe.derivation.Configuration
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.deriveConfiguredCodec

      given Configuration = Configuration.default.withDiscriminator("type")
      given CirceCodec[MixedADT] = deriveConfiguredCodec

      given JsoniterConfiguration = JsoniterConfiguration.default.withDiscriminator("type")
      given JsonValueCodec[MixedADT] = deriveJsoniterConfiguredCodec

      // Discriminator at end — both should handle
      val json = """{"x":42,"type":"MixedProduct"}"""
      val circeResult = circeDecode[MixedADT](json)
      val jsoniterResult = readFromString[MixedADT](json)
      assert(circeResult == Right(jsoniterResult))
      assert(jsoniterResult == MixedProduct(42))
    }

    test("discriminator - case object singleton") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDiscriminator("type")
      given JsonValueCodec[MixedADT] = deriveJsoniterConfiguredCodec
      val json = """{"type":"MixedSingleton"}"""
      val decoded = readFromString[MixedADT](json)
      assert(decoded == MixedSingleton)
    }

    test("discriminator - case object cross-codec") {
      import io.circe.derivation.Configuration
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.deriveConfiguredCodec

      given Configuration = Configuration.default.withDiscriminator("type")
      given CirceCodec[MixedADT] = deriveConfiguredCodec

      given JsoniterConfiguration = JsoniterConfiguration.default.withDiscriminator("type")
      given JsonValueCodec[MixedADT] = deriveJsoniterConfiguredCodec

      val v: MixedADT = MixedSingleton

      // jsoniter -> circe
      val jJson = writeToString(v)
      assert(circeDecode[MixedADT](jJson) == Right(v))

      // circe -> jsoniter
      val cJson = (v: MixedADT).asJson.noSpaces
      assert(readFromString[MixedADT](cJson) == v)
    }

    // =========================================================================
    // 6. Constructor Name Transformation with Discriminator
    //    (Inspired by circe ConfiguredDerivesSuite lines 311-375)
    // =========================================================================

    test("constructor-transform - snake_case constructor names with discriminator") {
      given JsoniterConfiguration = JsoniterConfiguration.default
        .withDiscriminator("type")
        .withTransformConstructorNames(JsoniterConfiguration.snakeCase)
      given JsonValueCodec[MixedADT] = deriveJsoniterConfiguredCodec
      val v: MixedADT = MixedProduct(42)
      val json = writeToString(v)
      assert(json.contains("\"type\":\"mixed_product\""))
      val decoded = readFromString[MixedADT](json)
      assert(decoded == v)
    }

    test("constructor-transform - cross-codec with circe") {
      import io.circe.derivation.Configuration
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.deriveConfiguredCodec

      given Configuration = Configuration.default
        .withDiscriminator("type")
        .withSnakeCaseConstructorNames
      given CirceCodec[MixedADT] = deriveConfiguredCodec

      given JsoniterConfiguration = JsoniterConfiguration.default
        .withDiscriminator("type")
        .withTransformConstructorNames(JsoniterConfiguration.snakeCase)
      given JsonValueCodec[MixedADT] = deriveJsoniterConfiguredCodec

      val v: MixedADT = MixedProduct(42)

      // jsoniter -> circe
      val jJson = writeToString(v)
      val cResult = circeDecode[MixedADT](jJson)
      assert(cResult == Right(v))

      // circe -> jsoniter
      val cJson = (v: MixedADT).asJson.noSpaces
      val jResult = readFromString[MixedADT](cJson)
      assert(jResult == v)
    }

    test("constructor-transform - external tagging with constructor name transform") {
      given JsoniterConfiguration = JsoniterConfiguration.default
        .withTransformConstructorNames(JsoniterConfiguration.snakeCase)
      given JsonValueCodec[MixedADT] = deriveJsoniterConfiguredCodec
      val v: MixedADT = MixedProduct(42)
      val json = writeToString(v)
      // External tagging: key is the constructor name (transformed)
      assert(json == """{"mixed_product":{"x":42}}""")
      val decoded = readFromString[MixedADT](json)
      assert(decoded == v)
    }

    // =========================================================================
    // 7. Systematic Cross-Codec Configuration Matrix
    //    (Inspired by tapir's cross-backend testing pattern)
    // =========================================================================

    test("config-matrix - defaults + snake_case + drop-null + discriminator") {
      import io.circe.derivation.Configuration
      import io.circe.{Encoder, Decoder, Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.{deriveConfiguredEncoder, deriveConfiguredDecoder}

      case class MatrixType(firstName: String, lastName: String, middleName: Option[String] = None, age: Int = 25)

      // circe: defaults + snake_case + manual drop-null
      given Configuration = Configuration.default.withDefaults.withSnakeCaseMemberNames
      val cEnc: Encoder.AsObject[MatrixType] =
        deriveConfiguredEncoder[MatrixType].mapJsonObject(_.filter(!_._2.isNull))
      val cDec: Decoder[MatrixType] = deriveConfiguredDecoder
      given Encoder[MatrixType] = cEnc
      given Decoder[MatrixType] = cDec

      // jsoniter: defaults + snake_case + drop-null
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withSnakeCaseMemberNames.withDropNullValues
      given JsonValueCodec[MatrixType] = deriveJsoniterConfiguredCodec

      val values = List(
        MatrixType("Alice", "Smith"),                           // all defaults
        MatrixType("Bob", "Jones", Some("Lee"), 30),            // all provided
        MatrixType("Carol", "White", None, 40),                 // explicit None
      )

      for v <- values do
        val jJson = writeToString(v)
        val cJson = cEnc.encodeObject(v).asJson.noSpaces

        // Verify no nulls in either output
        assert(!jJson.contains(":null"))
        assert(!cJson.contains(":null"))

        // Verify snake_case in both
        assert(jJson.contains("first_name"))
        assert(cJson.contains("first_name"))

        // Cross-decode
        assert(cDec.decodeJson(io.circe.parser.parse(jJson).toOption.get) == Right(v))
        assert(readFromString[MatrixType](cJson) == v)
    }

    test("config-matrix - defaults + discriminator + many variants") {
      import io.circe.derivation.Configuration
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.deriveConfiguredCodec

      given Configuration = Configuration.default.withDefaults.withDiscriminator("kind")
      given CirceCodec[ManyVariants] = deriveConfiguredCodec

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withDiscriminator("kind")
      given JsonValueCodec[ManyVariants] = deriveJsoniterConfiguredCodec

      val variants: List[ManyVariants] = List(VarA(1), VarB("hello"), VarC(true), VarD)

      for v <- variants do
        // jsoniter -> circe
        val jJson = writeToString(v)
        assert(jJson.contains("\"kind\""))
        val cResult = circeDecode[ManyVariants](jJson)
        assert(cResult == Right(v))

        // circe -> jsoniter
        val cJson = (v: ManyVariants).asJson.noSpaces
        val jResult = readFromString[ManyVariants](cJson)
        assert(jResult == v)
    }

    test("config-matrix - strict + defaults") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withStrictDecoding
      given JsonValueCodec[AllDefaults] = deriveJsoniterConfiguredCodec

      // Known fields only — should pass
      val ok = """{"a":99}"""
      val decoded = readFromString[AllDefaults](ok)
      assert(decoded == AllDefaults(99, "hello", true, Some("opt")))

      // Unknown field — should fail
      val bad = """{"a":99,"extra":true}"""
      val caught =
        try
          readFromString[AllDefaults](bad)
          throw new RuntimeException("expected exception")
        catch
          case e: JsonReaderException => e
      assert(caught.getMessage.contains("Strict decoding"))
    }

    // =========================================================================
    // 8. Duplicate Keys in Input
    //    (Inspired by jsoniter-scala JsonCodecMakerSpec lines 478-485)
    // =========================================================================

    test("duplicate-keys - last value wins (non-configured)") {
      given JsonValueCodec[SingleField] = deriveJsoniterCodec
      // jsoniter-scala's default behavior: last value wins
      val json = """{"only":"first","only":"second"}"""
      val decoded = readFromString[SingleField](json)
      assert(decoded == SingleField("second"))
    }

    test("duplicate-keys - configured, last value wins") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[SingleField] = deriveJsoniterConfiguredCodec
      val json = """{"only":"first","only":"second"}"""
      val decoded = readFromString[SingleField](json)
      assert(decoded == SingleField("second"))
    }

    // =========================================================================
    // 9. Error Message Content Verification
    //    (Inspired by circe ShowErrorSuite and jsoniter-scala error tests)
    // =========================================================================

    test("error - unknown variant in sum type") {
      given JsonValueCodec[MixedADT] = deriveJsoniterCodec
      val json = """{"NonExistent":{"x":1}}"""
      val caught =
        try
          readFromString[MixedADT](json)
          throw new RuntimeException("expected exception")
        catch
          case e: JsonReaderException => e
      assert(caught.getMessage.contains("expected one of:"))
    }

    test("error - unknown variant in configured sum type") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDiscriminator("type")
      given JsonValueCodec[MixedADT] = deriveJsoniterConfiguredCodec
      val json = """{"type":"NonExistent","x":1}"""
      val caught =
        try
          readFromString[MixedADT](json)
          throw new RuntimeException("expected exception")
        catch
          case e: JsonReaderException => e
      assert(caught.getMessage.contains("Unknown variant"))
    }

    test("error - missing discriminator field") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDiscriminator("type")
      given JsonValueCodec[MixedADT] = deriveJsoniterConfiguredCodec
      val json = """{"x":42}"""
      val caught =
        try
          readFromString[MixedADT](json)
          throw new RuntimeException("expected exception")
        catch
          case e: JsonReaderException => e
      assert(caught.getMessage.contains("Discriminator") || caught.getMessage.contains("type"))
    }

    test("error - empty object for discriminator sum") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDiscriminator("type")
      given JsonValueCodec[MixedADT] = deriveJsoniterConfiguredCodec
      val json = """{}"""
      val caught =
        try
          readFromString[MixedADT](json)
          throw new RuntimeException("expected exception")
        catch
          case e: JsonReaderException => e
      assert(caught.getMessage.contains("discriminator") || caught.getMessage.contains("empty"))
    }

    test("error - wrong JSON type (array instead of object)") {
      given JsonValueCodec[SingleField] = deriveJsoniterCodec
      val caught =
        try
          readFromString[SingleField]("""[1,2,3]""")
          throw new RuntimeException("expected exception")
        catch
          case e: JsonReaderException => e
      assert(caught.getMessage.nonEmpty)
    }

    test("error - wrong JSON type (string instead of object)") {
      given JsonValueCodec[SingleField] = deriveJsoniterCodec
      val caught =
        try
          readFromString[SingleField](""""hello"""")
          throw new RuntimeException("expected exception")
        catch
          case e: JsonReaderException => e
      assert(caught.getMessage.nonEmpty)
    }

    test("error - strict: unknown field mentions valid fields") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withStrictDecoding
      case class StrictType(name: String, age: Int)
      given JsonValueCodec[StrictType] = deriveJsoniterConfiguredCodec
      val json = """{"name":"Alice","age":30,"extra":"bad"}"""
      val caught =
        try
          readFromString[StrictType](json)
          throw new RuntimeException("expected exception")
        catch
          case e: JsonReaderException => e
      assert(caught.getMessage.contains("Strict decoding"))
      assert(caught.getMessage.contains("unexpected field"))
    }

    // =========================================================================
    // 10. Deep Nesting
    //     (Inspired by jsoniter-scala deep nesting tests)
    // =========================================================================

    test("deep-nesting - 5 levels") {
      given JsonValueCodec[DeepNode] = deriveJsoniterCodec
      val deep = DeepNode(1, Some(DeepNode(2, Some(DeepNode(3, Some(DeepNode(4, Some(DeepNode(5, None)))))))))
      val decoded = roundtrip(deep)
      assert(decoded == deep)
    }

    test("deep-nesting - 10 levels") {
      given JsonValueCodec[DeepNode] = deriveJsoniterCodec
      def build(n: Int): DeepNode =
        if n <= 0 then DeepNode(0, None)
        else DeepNode(n, Some(build(n - 1)))
      val deep = build(10)
      val decoded = roundtrip(deep)
      assert(decoded == deep)
    }

    test("deep-nesting - cross-codec with circe") {
      import io.circe.generic.semiauto.deriveCodec
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode

      given CirceCodec[DeepNode] = deriveCodec
      given JsonValueCodec[DeepNode] = deriveJsoniterCodec

      def build(n: Int): DeepNode =
        if n <= 0 then DeepNode(0, None)
        else DeepNode(n, Some(build(n - 1)))
      val deep = build(5)

      // jsoniter -> circe
      val jJson = writeToString(deep)
      assert(circeDecode[DeepNode](jJson) == Right(deep))

      // circe -> jsoniter
      val cJson = deep.asJson.noSpaces
      assert(readFromString[DeepNode](cJson) == deep)
    }

    // =========================================================================
    // 11. Additional Edge Cases
    // =========================================================================

    test("empty-collections - List, Map, Set round-trip") {
      given JsonValueCodec[WithEmptyCollections] = deriveJsoniterCodec
      val v = WithEmptyCollections(Nil, Map.empty, Set.empty)
      val json = writeToString(v)
      assert(json.contains("[]"))
      assert(json.contains("{}"))
      val decoded = readFromString[WithEmptyCollections](json)
      assert(decoded == v)
    }

    test("empty-collections - cross-codec with circe") {
      import io.circe.generic.semiauto.deriveCodec
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode

      given CirceCodec[WithEmptyCollections] = deriveCodec
      given JsonValueCodec[WithEmptyCollections] = deriveJsoniterCodec

      val v = WithEmptyCollections(Nil, Map.empty, Set.empty)
      val jJson = writeToString(v)
      assert(circeDecode[WithEmptyCollections](jJson) == Right(v))
      val cJson = v.asJson.noSpaces
      assert(readFromString[WithEmptyCollections](cJson) == v)
    }

    test("many-options - all None") {
      given JsonValueCodec[ManyOptions] = deriveJsoniterCodec
      val v = ManyOptions(None, None, None, None, None)
      val decoded = roundtrip(v)
      assert(decoded == v)
    }

    test("many-options - all Some") {
      given JsonValueCodec[ManyOptions] = deriveJsoniterCodec
      val v = ManyOptions(Some("a"), Some(1), Some(true), Some(List("x")), Some(3.14))
      val decoded = roundtrip(v)
      assert(decoded == v)
    }

    test("many-options - mixed") {
      given JsonValueCodec[ManyOptions] = deriveJsoniterCodec
      val v = ManyOptions(Some("a"), None, Some(false), None, Some(0.0))
      val decoded = roundtrip(v)
      assert(decoded == v)
    }

    test("many-options - drop-null configured") {
      given JsoniterConfiguration = JsoniterConfiguration.default.withDropNullValues
      given JsonValueCodec[ManyOptions] = deriveJsoniterConfiguredCodec
      val v = ManyOptions(Some("a"), None, None, None, Some(1.0))
      val json = writeToString(v)
      assert(!json.contains(":null"))
      assert(json.contains("\"a\":\"a\""))
      assert(json.contains("\"e\":1.0"))
    }

    test("nested-containers - List[Option[Int]]") {
      given JsonValueCodec[NestedContainers] = deriveJsoniterCodec
      val v = NestedContainers(
        listOpt = List(Some(1), None, Some(3)),
        mapList = Map("a" -> List("x", "y"), "b" -> Nil),
        optList = Some(List(1, 2, 3))
      )
      val decoded = roundtrip(v)
      assert(decoded == v)
    }

    test("nested-containers - cross-codec with circe") {
      import io.circe.generic.semiauto.deriveCodec
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode

      given CirceCodec[NestedContainers] = deriveCodec
      given JsonValueCodec[NestedContainers] = deriveJsoniterCodec

      val v = NestedContainers(
        listOpt = List(Some(1), None, Some(3)),
        mapList = Map("a" -> List("x", "y")),
        optList = None
      )

      // jsoniter -> circe
      val jJson = writeToString(v)
      assert(circeDecode[NestedContainers](jJson) == Right(v))

      // circe -> jsoniter
      val cJson = v.asJson.noSpaces
      assert(readFromString[NestedContainers](cJson) == v)
    }

    test("IndexedSeq - roundtrip") {
      given JsonValueCodec[WithIndexedSeq] = deriveJsoniterCodec
      val v = WithIndexedSeq(IndexedSeq(1, 2, 3), IndexedSeq("a", "b"))
      val decoded = roundtrip(v)
      assert(decoded == v)
    }

    test("IndexedSeq - empty") {
      given JsonValueCodec[WithIndexedSeq] = deriveJsoniterCodec
      val v = WithIndexedSeq(IndexedSeq.empty, IndexedSeq.empty)
      val decoded = roundtrip(v)
      assert(decoded == v)
    }

    test("IndexedSeq - cross-codec with circe") {
      import io.circe.generic.semiauto.deriveCodec
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode

      given CirceCodec[WithIndexedSeq] = deriveCodec
      given JsonValueCodec[WithIndexedSeq] = deriveJsoniterCodec

      val v = WithIndexedSeq(IndexedSeq(10, 20), IndexedSeq("x"))

      // jsoniter -> circe
      val jJson = writeToString(v)
      assert(circeDecode[WithIndexedSeq](jJson) == Right(v))

      // circe -> jsoniter
      val cJson = v.asJson.noSpaces
      assert(readFromString[WithIndexedSeq](cJson) == v)
    }

    test("IndexedSeq - configured codec") {
      case class ConfIS(items: IndexedSeq[Int], label: String)
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[ConfIS] = deriveJsoniterConfiguredCodec
      val v = ConfIS(IndexedSeq(1, 2, 3), "test")
      val decoded = roundtrip(v)
      assert(decoded == v)
    }

    test("IndexedSeq - nested List[IndexedSeq[Int]]") {
      case class NestedIS(data: List[IndexedSeq[Int]])
      given JsonValueCodec[NestedIS] = deriveJsoniterCodec
      val v = NestedIS(List(IndexedSeq(1, 2), IndexedSeq(3)))
      val decoded = roundtrip(v)
      assert(decoded == v)
    }

    test("Iterable - roundtrip") {
      given JsonValueCodec[WithIterable] = deriveJsoniterCodec
      val v = WithIterable(List(1, 2, 3), List("a", "b"))
      val decoded = roundtrip(v)
      assert(decoded.items.toList == v.items.toList)
      assert(decoded.labels.toList == v.labels.toList)
    }

    test("Iterable - empty") {
      given JsonValueCodec[WithIterable] = deriveJsoniterCodec
      val v = WithIterable(Nil, Nil)
      val decoded = roundtrip(v)
      assert(decoded.items.toList == Nil)
      assert(decoded.labels.toList == Nil)
    }

    test("Iterable - cross-codec with circe") {
      import io.circe.generic.semiauto.deriveCodec
      import io.circe.{Codec as CirceCodec, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode

      given CirceCodec[WithIterable] = deriveCodec
      given JsonValueCodec[WithIterable] = deriveJsoniterCodec

      val v = WithIterable(List(10, 20), List("x"))

      // jsoniter -> circe
      val jJson = writeToString(v)
      val circeResult = circeDecode[WithIterable](jJson)
      assert(circeResult.isRight)
      assert(circeResult.toOption.get.items.toList == v.items.toList)

      // circe -> jsoniter
      val cJson = v.asJson.noSpaces
      val jResult = readFromString[WithIterable](cJson)
      assert(jResult.items.toList == v.items.toList)
    }

    test("Iterable - configured codec") {
      case class ConfIt(items: Iterable[Int], label: String)
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[ConfIt] = deriveJsoniterConfiguredCodec
      val v = ConfIt(List(1, 2, 3), "test")
      val json = writeToString(v)
      val decoded = readFromString[ConfIt](json)
      assert(decoded.items.toList == v.items.toList)
      assert(decoded.label == v.label)
    }

    test("Array - roundtrip") {
      given JsonValueCodec[WithArray] = deriveJsoniterCodec
      val v = WithArray(Array(1, 2, 3), Array("a", "b"))
      val json = writeToString(v)
      val decoded = readFromString[WithArray](json)
      assert(decoded == v)
    }

    test("Array - empty") {
      given JsonValueCodec[WithArray] = deriveJsoniterCodec
      val v = WithArray(Array.empty[Int], Array.empty[String])
      val json = writeToString(v)
      val decoded = readFromString[WithArray](json)
      assert(decoded == v)
    }

    test("Array - cross-codec with circe") {
      import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}
      import io.circe.{Encoder, Decoder, *}
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode

      given Encoder[WithArray] = deriveEncoder
      given Decoder[WithArray] = deriveDecoder
      given JsonValueCodec[WithArray] = deriveJsoniterCodec

      val v = WithArray(Array(10, 20), Array("x"))

      // jsoniter -> circe
      val jJson = writeToString(v)
      val circeResult = circeDecode[WithArray](jJson)
      assert(circeResult.isRight)
      assert(circeResult.toOption.get == v)

      // circe -> jsoniter
      val cJson = v.asJson.noSpaces
      val jResult = readFromString[WithArray](cJson)
      assert(jResult == v)
    }

    test("Array - configured codec") {
      case class ConfArr(items: Array[Int], label: String)
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[ConfArr] = deriveJsoniterConfiguredCodec
      val v = ConfArr(Array(1, 2, 3), "test")
      val json = writeToString(v)
      val decoded = readFromString[ConfArr](json)
      assert(java.util.Arrays.equals(decoded.items, v.items))
      assert(decoded.label == v.label)
    }

    test("Array - nested Option[Array[Int]]") {
      case class NestedArr(data: Option[Array[Int]])
      given JsonValueCodec[NestedArr] = deriveJsoniterCodec
      val v = NestedArr(Some(Array(1, 2, 3)))
      val json = writeToString(v)
      val decoded = readFromString[NestedArr](json)
      assert(java.util.Arrays.equals(decoded.data.get, v.data.get))

      val empty = NestedArr(None)
      val decoded2 = roundtrip(empty)
      assert(decoded2.data == None)
    }

    test("field-order - decode with reversed field order") {
      case class OrderTest(a: Int, b: String, c: Boolean, d: Double)
      given JsonValueCodec[OrderTest] = deriveJsoniterCodec
      // Fields in reverse order
      val json = """{"d":3.14,"c":true,"b":"hello","a":42}"""
      val decoded = readFromString[OrderTest](json)
      assert(decoded == OrderTest(42, "hello", true, 3.14))
    }

    test("field-order - configured with reversed field order") {
      case class OrderTest(a: Int, b: String, c: Boolean)
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[OrderTest] = deriveJsoniterConfiguredCodec
      val json = """{"c":true,"a":42,"b":"hello"}"""
      val decoded = readFromString[OrderTest](json)
      assert(decoded == OrderTest(42, "hello", true))
    }

    test("singleton - case object in external tagging") {
      given JsonValueCodec[MixedADT] = deriveJsoniterCodec
      val v: MixedADT = MixedSingleton
      val json = writeToString(v)
      assert(json == """{"MixedSingleton":{}}""")
      val decoded = readFromString[MixedADT](json)
      assert(decoded == MixedSingleton)
    }

    test("singleton - cross-codec with circe") {
      import io.circe.generic.auto.given
      import io.circe.syntax.*
      import io.circe.parser.decode as circeDecode

      given JsonValueCodec[MixedADT] = deriveJsoniterCodec

      val v: MixedADT = MixedSingleton

      // jsoniter -> circe
      val jJson = writeToString(v)
      assert(circeDecode[MixedADT](jJson) == Right(v))

      // circe -> jsoniter
      val cJson = (v: MixedADT).asJson.noSpaces
      assert(readFromString[MixedADT](cJson) == v)
    }

    test("null-string - null string field throws error") {
      given JsonValueCodec[SingleField] = deriveJsoniterCodec
      // jsoniter's readString expects a quoted string, JSON null is rejected
      // (this is correct — use Option[String] for nullable fields)
      val json = """{"only":null}"""
      val caught =
        try
          readFromString[SingleField](json)
          throw new RuntimeException("expected exception")
        catch
          case e: JsonReaderException => e
      assert(caught.getMessage.nonEmpty)
    }

    test("discriminator - with nested objects in variant fields (slow path)") {
      case class Inner(x: Int, y: String)
      sealed trait WithNested
      case class NestedVar(inner: Inner, extra: Boolean) extends WithNested

      given JsoniterConfiguration = JsoniterConfiguration.default.withDiscriminator("type")
      given JsonValueCodec[WithNested] = deriveJsoniterConfiguredCodec

      // Discriminator at end, with nested object before it
      val json = """{"inner":{"x":42,"y":"hello"},"extra":true,"type":"NestedVar"}"""
      val decoded = readFromString[WithNested](json)
      assert(decoded == NestedVar(Inner(42, "hello"), true))
    }

    test("discriminator - with array fields in variant (slow path)") {
      sealed trait WithArray
      case class ArrayVar(items: List[Int], name: String) extends WithArray

      given JsoniterConfiguration = JsoniterConfiguration.default.withDiscriminator("type")
      given JsonValueCodec[WithArray] = deriveJsoniterConfiguredCodec

      // Discriminator at end, with array field before it
      val json = """{"items":[1,2,3],"name":"test","type":"ArrayVar"}"""
      val decoded = readFromString[WithArray](json)
      assert(decoded == ArrayVar(List(1, 2, 3), "test"))
    }

    test("discriminator - slow path with special chars in string values") {
      sealed trait SpecialChars
      case class SpecialVar(text: String) extends SpecialChars

      given JsoniterConfiguration = JsoniterConfiguration.default.withDiscriminator("type")
      given JsonValueCodec[SpecialChars] = deriveJsoniterConfiguredCodec

      // String value contains chars that need escaping, discriminator at end
      val json = """{"text":"hello \"world\"\nline2\\back","type":"SpecialVar"}"""
      val decoded = readFromString[SpecialChars](json)
      assert(decoded == SpecialVar("hello \"world\"\nline2\\back"))
    }

    test("discriminator - slow path with null values") {
      sealed trait WithNulls
      case class NullVar(a: Option[String], b: Int) extends WithNulls

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withDiscriminator("type")
      given JsonValueCodec[WithNulls] = deriveJsoniterConfiguredCodec

      // null value then discriminator
      val json = """{"a":null,"b":42,"type":"NullVar"}"""
      val decoded = readFromString[WithNulls](json)
      assert(decoded == NullVar(None, 42))
    }

    test("discriminator - slow path with boolean and number values") {
      sealed trait MixedValues
      case class MV(flag: Boolean, count: Int, ratio: Double) extends MixedValues

      given JsoniterConfiguration = JsoniterConfiguration.default.withDiscriminator("type")
      given JsonValueCodec[MixedValues] = deriveJsoniterConfiguredCodec

      val json = """{"flag":true,"count":99,"ratio":3.14,"type":"MV"}"""
      val decoded = readFromString[MixedValues](json)
      assert(decoded == MV(true, 99, 3.14))
    }
  }
