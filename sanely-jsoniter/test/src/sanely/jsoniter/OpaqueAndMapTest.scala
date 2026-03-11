package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.*

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
