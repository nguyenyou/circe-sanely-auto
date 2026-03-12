package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.*

// Abstract type member (zio-prelude Subtype/Newtype style)
// NOT an opaque type — uses abstract type member with runtime asInstanceOf
abstract class NewtypeCustom[A]:
  type Type
  def apply(a: A): Type = a.asInstanceOf[Type]

abstract class SubtypeCustom[A] extends NewtypeCustom[A]:
  override type Type <: A

object MyNamespace extends SubtypeCustom[String]
type MyNamespace = MyNamespace.Type

case class HasAbstractTypeMember(ns: MyNamespace, label: String)

// AnyVal wrapper
case class FileId(id: String) extends AnyVal
case class HasAnyVal(fileId: FileId, name: String)

// Gap 2: Subtype field with default + null
case class HasSubtypeWithDefault(ns: MyNamespace = MyNamespace("default"), label: String)

// Gap 3: AnyVal with default + null
case class HasAnyValWithDefault(fileId: FileId = FileId("default"), name: String)
case class DocInfo(fileId: FileId, reviewingFileId: Option[FileId] = None, start: Int = 0)

// Opaque types need to be at file/object scope, not inside test blocks
object OpaqueTypes:
  opaque type UserId = String
  object UserId:
    def apply(s: String): UserId = s
    given JsonValueCodec[UserId] = Codecs.string.asInstanceOf[JsonValueCodec[UserId]]

  opaque type Score = Int
  object Score:
    def apply(i: Int): Score = i
    given JsonValueCodec[Score] = Codecs.int.asInstanceOf[JsonValueCodec[Score]]

  opaque type Tags = List[String]
  object Tags:
    def apply(l: List[String]): Tags = l
    given JsonValueCodec[Tags] = Codecs.list(Codecs.string).asInstanceOf[JsonValueCodec[Tags]]

  case class UserProfile(id: UserId, name: String, score: Score, tags: Tags)

object OpaqueAutoTypes:
  opaque type Name = String
  object Name:
    def apply(s: String): Name = s

  opaque type Age = Int
  object Age:
    def apply(i: Int): Age = i

  case class Person(name: Name, age: Age)

class OpaqueAndMapTest extends munit.FunSuite:
  import sanely.jsoniter.semiauto.*

  private def roundtrip[A: JsonValueCodec](value: A): A = readFromString[A](writeToString(value))


    // === Opaque type tests ===

    test("opaque - string opaque type round-trip") {
      import OpaqueTypes.*
      given JsonValueCodec[UserProfile] = deriveJsoniterCodec
      val profile = UserProfile(UserId("u123"), "Alice", Score(95), Tags(List("admin", "user")))
      val json = writeToString(profile)
      assert(json.contains("\"id\":\"u123\""))
      assert(json.contains("\"score\":95"))
      assert(json.contains("\"tags\":[\"admin\",\"user\"]"))
      val decoded = readFromString[UserProfile](json)
      assert(decoded == profile)
    }

    test("opaque - auto derivation with opaque fields") {
      import sanely.jsoniter.auto.given
      import OpaqueAutoTypes.*
      // Opaque types wrapping primitives should be resolved transparently
      val person = Person(Name("Bob"), Age(30))
      val json = writeToString(person)
      assert(json == """{"name":"Bob","age":30}""")
      val decoded = readFromString[Person](json)
      assert(decoded == person)
    }

    // === External Map given tests ===

    test("external map given - custom codec is used") {
      // Define a custom Map codec that encodes as an array of pairs
      given customMapCodec: JsonValueCodec[Map[String, Int]] = new JsonValueCodec[Map[String, Int]]:
        val nullValue: Map[String, Int] = null
        def encodeValue(x: Map[String, Int], out: JsonWriter): Unit =
          if x == null then out.writeNull()
          else
            out.writeArrayStart()
            val it = x.iterator
            while it.hasNext do
              val (k, v) = it.next()
              out.writeArrayStart()
              out.writeVal(k)
              out.writeVal(v)
              out.writeArrayEnd()
            out.writeArrayEnd()
        def decodeValue(in: JsonReader, default: Map[String, Int]): Map[String, Int] =
          if !in.isNextToken('[') then
            in.readNullOrTokenError(default, '[')
          else
            val buf = Map.newBuilder[String, Int]
            if !in.isNextToken(']') then
              in.rollbackToken()
              if !in.isNextToken('[') then in.readNullOrTokenError(default, '[')
              val k = in.readString(null)
              if !in.isNextToken(',') then in.arrayEndOrCommaError()
              val v = in.readInt()
              if !in.isNextToken(']') then in.arrayEndOrCommaError()
              buf += (k -> v)
              while in.isNextToken(',') do
                if !in.isNextToken('[') then in.readNullOrTokenError(default, '[')
                val k2 = in.readString(null)
                if !in.isNextToken(',') then in.arrayEndOrCommaError()
                val v2 = in.readInt()
                if !in.isNextToken(']') then in.arrayEndOrCommaError()
                buf += (k2 -> v2)
              if !in.isCurrentToken(']') then in.arrayEndOrCommaError()
            buf.result()

      case class WithCustomMap(data: Map[String, Int])
      given JsonValueCodec[WithCustomMap] = deriveJsoniterCodec
      val v = WithCustomMap(Map("a" -> 1, "b" -> 2))
      val json = writeToString(v)
      // Custom codec encodes as array of pairs, not object
      assert(json.contains("["))
      assert(!json.contains("{\"a\""))
      val decoded = readFromString[WithCustomMap](json)
      assert(decoded == v)
    }

    test("external map given - configured codec respects external given") {
      given customMapCodec: JsonValueCodec[Map[String, Int]] = new JsonValueCodec[Map[String, Int]]:
        val nullValue: Map[String, Int] = null
        def encodeValue(x: Map[String, Int], out: JsonWriter): Unit =
          out.writeArrayStart()
          val it = x.iterator
          while it.hasNext do
            val (k, v) = it.next()
            out.writeArrayStart()
            out.writeVal(k)
            out.writeVal(v)
            out.writeArrayEnd()
          out.writeArrayEnd()
        def decodeValue(in: JsonReader, default: Map[String, Int]): Map[String, Int] =
          if !in.isNextToken('[') then in.readNullOrTokenError(default, '[')
          else
            val buf = Map.newBuilder[String, Int]
            if !in.isNextToken(']') then
              in.rollbackToken()
              if !in.isNextToken('[') then in.arrayEndOrCommaError()
              val k = in.readString(null)
              if !in.isNextToken(',') then in.arrayEndOrCommaError()
              val v = in.readInt()
              if !in.isNextToken(']') then in.arrayEndOrCommaError()
              buf += (k -> v)
              while in.isNextToken(',') do
                if !in.isNextToken('[') then in.arrayEndOrCommaError()
                val k2 = in.readString(null)
                if !in.isNextToken(',') then in.arrayEndOrCommaError()
                val v2 = in.readInt()
                if !in.isNextToken(']') then in.arrayEndOrCommaError()
                buf += (k2 -> v2)
              if !in.isCurrentToken(']') then in.arrayEndOrCommaError()
            buf.result()

      given JsoniterConfiguration = JsoniterConfiguration.default
      case class ConfiguredWithMap(data: Map[String, Int])
      given JsonValueCodec[ConfiguredWithMap] = deriveJsoniterConfiguredCodec
      val v = ConfiguredWithMap(Map("x" -> 10))
      val json = writeToString(v)
      assert(json.contains("["))
      val decoded = readFromString[ConfiguredWithMap](json)
      assert(decoded == v)
    }

    // === Abstract type member tests (zio-prelude Subtype style) ===

    test("abstract type member - semiauto round-trip") {
      given JsonValueCodec[MyNamespace] = Codecs.string.asInstanceOf[JsonValueCodec[MyNamespace]]
      given JsonValueCodec[HasAbstractTypeMember] = deriveJsoniterCodec
      val v = HasAbstractTypeMember(MyNamespace("forms"), "test")
      val json = writeToString(v)
      assert(json == """{"ns":"forms","label":"test"}""")
      val decoded = readFromString[HasAbstractTypeMember](json)
      assert(decoded == v)
    }

    test("abstract type member - configured round-trip") {
      given JsonValueCodec[MyNamespace] = Codecs.string.asInstanceOf[JsonValueCodec[MyNamespace]]
      given JsoniterConfiguration = JsoniterConfiguration.default
      given JsonValueCodec[HasAbstractTypeMember] = deriveJsoniterConfiguredCodec
      val v = HasAbstractTypeMember(MyNamespace("forms"), "test")
      val json = writeToString(v)
      val decoded = readFromString[HasAbstractTypeMember](json)
      assert(decoded == v)
    }

    // === AnyVal tests ===

    test("AnyVal - semiauto round-trip") {
      // AnyVal types need a hand-rolled codec that unwraps/wraps the value
      given JsonValueCodec[FileId] = new JsonValueCodec[FileId]:
        val nullValue: FileId = null.asInstanceOf[FileId]
        def encodeValue(x: FileId, out: JsonWriter): Unit = out.writeVal(x.id)
        def decodeValue(in: JsonReader, default: FileId): FileId = FileId(in.readString(null))
      given JsonValueCodec[HasAnyVal] = deriveJsoniterCodec
      val v = HasAnyVal(FileId("f123"), "doc.pdf")
      val json = writeToString(v)
      assert(json == """{"fileId":"f123","name":"doc.pdf"}""")
      val decoded = readFromString[HasAnyVal](json)
      assert(decoded == v)
    }

    test("AnyVal - configured round-trip") {
      given JsonValueCodec[FileId] = new JsonValueCodec[FileId]:
        val nullValue: FileId = null.asInstanceOf[FileId]
        def encodeValue(x: FileId, out: JsonWriter): Unit = out.writeVal(x.id)
        def decodeValue(in: JsonReader, default: FileId): FileId = FileId(in.readString(null))
      given JsoniterConfiguration = JsoniterConfiguration.default
      given JsonValueCodec[HasAnyVal] = deriveJsoniterConfiguredCodec
      val v = HasAnyVal(FileId("f123"), "doc.pdf")
      val json = writeToString(v)
      val decoded = readFromString[HasAnyVal](json)
      assert(decoded == v)
    }

    // === Gap 2: Subtype field with default + null ===

    test("subtype-default - missing subtype field uses default") {
      given JsonValueCodec[MyNamespace] = Codecs.string.asInstanceOf[JsonValueCodec[MyNamespace]]
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[HasSubtypeWithDefault] = deriveJsoniterConfiguredCodec
      val json = """{"label":"test"}"""
      val decoded = readFromString[HasSubtypeWithDefault](json)
      assert(decoded.ns == MyNamespace("default"))
      assert(decoded.label == "test")
    }

    test("subtype-default - null subtype field uses default") {
      given JsonValueCodec[MyNamespace] = Codecs.string.asInstanceOf[JsonValueCodec[MyNamespace]]
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[HasSubtypeWithDefault] = deriveJsoniterConfiguredCodec
      val json = """{"ns":null,"label":"test"}"""
      val decoded = readFromString[HasSubtypeWithDefault](json)
      assert(decoded.ns == MyNamespace("default"))
      assert(decoded.label == "test")
    }

    test("subtype-default - provided value overrides default") {
      given JsonValueCodec[MyNamespace] = Codecs.string.asInstanceOf[JsonValueCodec[MyNamespace]]
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[HasSubtypeWithDefault] = deriveJsoniterConfiguredCodec
      val json = """{"ns":"custom","label":"test"}"""
      val decoded = readFromString[HasSubtypeWithDefault](json)
      assert(decoded.ns == MyNamespace("custom"))
    }

    test("subtype-default - round-trip") {
      given JsonValueCodec[MyNamespace] = Codecs.string.asInstanceOf[JsonValueCodec[MyNamespace]]
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[HasSubtypeWithDefault] = deriveJsoniterConfiguredCodec
      val v = HasSubtypeWithDefault(MyNamespace("forms"), "test")
      val decoded = roundtrip(v)
      assert(decoded == v)
    }

    test("subtype-default - cross-codec with circe") {
      import io.circe.derivation.Configuration
      import io.circe.{Encoder, Decoder}
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.deriveConfiguredCodec

      given JsonValueCodec[MyNamespace] = Codecs.string.asInstanceOf[JsonValueCodec[MyNamespace]]

      // circe needs Codec[MyNamespace] — it's a subtype of String
      given io.circe.Codec[MyNamespace] = io.circe.Codec.from(
        Decoder.decodeString.map(MyNamespace(_)),
        Encoder.encodeString.contramap[MyNamespace](_.asInstanceOf[String])
      )
      given Configuration = Configuration.default.withDefaults
      given io.circe.Codec[HasSubtypeWithDefault] = deriveConfiguredCodec

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[HasSubtypeWithDefault] = deriveJsoniterConfiguredCodec

      // null subtype field
      val withNull = """{"ns":null,"label":"test"}"""
      val circeNull = circeDecode[HasSubtypeWithDefault](withNull)
      val jsoniterNull = readFromString[HasSubtypeWithDefault](withNull)
      assert(circeNull == Right(jsoniterNull))
      assert(jsoniterNull.ns == MyNamespace("default"))

      // missing subtype field
      val withMissing = """{"label":"test"}"""
      val circeMissing = circeDecode[HasSubtypeWithDefault](withMissing)
      val jsoniterMissing = readFromString[HasSubtypeWithDefault](withMissing)
      assert(circeMissing == Right(jsoniterMissing))
    }

    // === Gap 3: AnyVal with default + null ===

    test("anyval-default - missing AnyVal field uses default") {
      given JsonValueCodec[FileId] = new JsonValueCodec[FileId]:
        val nullValue: FileId = null.asInstanceOf[FileId]
        def encodeValue(x: FileId, out: JsonWriter): Unit = out.writeVal(x.id)
        def decodeValue(in: JsonReader, default: FileId): FileId = FileId(in.readString(null))
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[HasAnyValWithDefault] = deriveJsoniterConfiguredCodec
      val json = """{"name":"doc.pdf"}"""
      val decoded = readFromString[HasAnyValWithDefault](json)
      assert(decoded.fileId == FileId("default"))
      assert(decoded.name == "doc.pdf")
    }

    test("anyval-default - null AnyVal field uses default") {
      given JsonValueCodec[FileId] = new JsonValueCodec[FileId]:
        val nullValue: FileId = null.asInstanceOf[FileId]
        def encodeValue(x: FileId, out: JsonWriter): Unit = out.writeVal(x.id)
        def decodeValue(in: JsonReader, default: FileId): FileId = FileId(in.readString(null))
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[HasAnyValWithDefault] = deriveJsoniterConfiguredCodec
      val json = """{"fileId":null,"name":"doc.pdf"}"""
      val decoded = readFromString[HasAnyValWithDefault](json)
      assert(decoded.fileId == FileId("default"))
      assert(decoded.name == "doc.pdf")
    }

    test("anyval-default - provided value overrides default") {
      given JsonValueCodec[FileId] = new JsonValueCodec[FileId]:
        val nullValue: FileId = null.asInstanceOf[FileId]
        def encodeValue(x: FileId, out: JsonWriter): Unit = out.writeVal(x.id)
        def decodeValue(in: JsonReader, default: FileId): FileId = FileId(in.readString(null))
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[HasAnyValWithDefault] = deriveJsoniterConfiguredCodec
      val json = """{"fileId":"custom","name":"doc.pdf"}"""
      val decoded = readFromString[HasAnyValWithDefault](json)
      assert(decoded.fileId == FileId("custom"))
    }

    test("anyval-default - DocInfo: Option[AnyVal] null → None") {
      given JsonValueCodec[FileId] = new JsonValueCodec[FileId]:
        val nullValue: FileId = null.asInstanceOf[FileId]
        def encodeValue(x: FileId, out: JsonWriter): Unit = out.writeVal(x.id)
        def decodeValue(in: JsonReader, default: FileId): FileId = FileId(in.readString(null))
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[DocInfo] = deriveJsoniterConfiguredCodec
      val json = """{"fileId":"f1","reviewingFileId":null}"""
      val decoded = readFromString[DocInfo](json)
      assert(decoded.reviewingFileId == None)
      assert(decoded.start == 0)
    }

    test("anyval-default - DocInfo: missing fields use defaults") {
      given JsonValueCodec[FileId] = new JsonValueCodec[FileId]:
        val nullValue: FileId = null.asInstanceOf[FileId]
        def encodeValue(x: FileId, out: JsonWriter): Unit = out.writeVal(x.id)
        def decodeValue(in: JsonReader, default: FileId): FileId = FileId(in.readString(null))
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[DocInfo] = deriveJsoniterConfiguredCodec
      val json = """{"fileId":"f1"}"""
      val decoded = readFromString[DocInfo](json)
      assert(decoded.fileId == FileId("f1"))
      assert(decoded.reviewingFileId == None)
      assert(decoded.start == 0)
    }

    test("anyval-default - cross-codec with circe") {
      import io.circe.derivation.Configuration
      import io.circe.{Encoder, Decoder}
      import io.circe.parser.decode as circeDecode
      import io.circe.generic.semiauto.deriveConfiguredCodec

      given JsonValueCodec[FileId] = new JsonValueCodec[FileId] {
        val nullValue: FileId = null.asInstanceOf[FileId]
        def encodeValue(x: FileId, out: JsonWriter): Unit = out.writeVal(x.id)
        def decodeValue(in: JsonReader, default: FileId): FileId = FileId(in.readString(null))
      }

      given io.circe.Codec[FileId] = io.circe.Codec.from(
        Decoder.decodeString.map(FileId(_)),
        Encoder.encodeString.contramap[FileId](_.id)
      )
      given Configuration = Configuration.default.withDefaults
      given io.circe.Codec[HasAnyValWithDefault] = deriveConfiguredCodec

      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      given JsonValueCodec[HasAnyValWithDefault] = deriveJsoniterConfiguredCodec

      // null AnyVal field
      val withNull = """{"fileId":null,"name":"doc.pdf"}"""
      val circeNull = circeDecode[HasAnyValWithDefault](withNull)
      val jsoniterNull = readFromString[HasAnyValWithDefault](withNull)
      assert(circeNull == Right(jsoniterNull))
      assert(jsoniterNull.fileId == FileId("default"))

      // missing AnyVal field
      val withMissing = """{"name":"doc.pdf"}"""
      val circeMissing = circeDecode[HasAnyValWithDefault](withMissing)
      val jsoniterMissing = readFromString[HasAnyValWithDefault](withMissing)
      assert(circeMissing == Right(jsoniterMissing))
    }
