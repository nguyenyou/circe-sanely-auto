package sanely.jsoniter.scalapb

import utest.*
import com.github.plokhotnyuk.jsoniter_scala.core.*

object ProtobufCodecsTest extends TestSuite:
  import ProtobufCodecs.*

  val tests = Tests {

    // === GeneratedEnum tests ===

    test("enum - encode Syntax as name string") {
      import com.google.protobuf.`type`.Syntax
      given JsonValueCodec[Syntax] = enumCodec
      val v: Syntax = Syntax.SYNTAX_PROTO3
      val json = writeToString(v)
      assert(json == "\"SYNTAX_PROTO3\"")
    }

    test("enum - decode Syntax from name string") {
      import com.google.protobuf.`type`.Syntax
      given JsonValueCodec[Syntax] = enumCodec
      val decoded = readFromString[Syntax]("\"SYNTAX_PROTO2\"")
      assert(decoded == Syntax.SYNTAX_PROTO2)
    }

    test("enum - round-trip all Syntax values") {
      import com.google.protobuf.`type`.Syntax
      given JsonValueCodec[Syntax] = enumCodec
      val values: Seq[Syntax] = Seq(Syntax.SYNTAX_PROTO2, Syntax.SYNTAX_PROTO3)
      for v <- values do
        val json = writeToString(v)
        val decoded = readFromString[Syntax](json)
        assert(decoded == v)
    }

    test("enum - unknown value decode error") {
      import com.google.protobuf.`type`.Syntax
      given JsonValueCodec[Syntax] = enumCodec
      val caught =
        try
          readFromString[Syntax]("\"INVALID\"")
          throw new RuntimeException("expected exception")
        catch
          case e: JsonReaderException => e
      assert(caught.getMessage.contains("unknown enum value"))
    }

    // === GeneratedMessage tests ===

    test("message - encode Duration") {
      import com.google.protobuf.duration.Duration
      given JsonValueCodec[Duration] = messageCodec
      val dur = Duration(seconds = 120, nanos = 500000000)
      val json = writeToString(dur)
      // seconds is int64, encoded as string per proto3 JSON spec
      assert(json.contains("\"seconds\":\"120\""))
      assert(json.contains("\"nanos\":500000000"))
    }

    test("message - decode Duration") {
      import com.google.protobuf.duration.Duration
      given JsonValueCodec[Duration] = messageCodec
      val json = """{"seconds":"120","nanos":500000000}"""
      val decoded = readFromString[Duration](json)
      assert(decoded.seconds == 120L)
      assert(decoded.nanos == 500000000)
    }

    test("message - round-trip Duration") {
      import com.google.protobuf.duration.Duration
      given JsonValueCodec[Duration] = messageCodec
      val dur = Duration(seconds = 3600, nanos = 123456789)
      val json = writeToString(dur)
      val decoded = readFromString[Duration](json)
      assert(decoded == dur)
    }

    test("message - encode Timestamp") {
      import com.google.protobuf.timestamp.Timestamp
      given JsonValueCodec[Timestamp] = messageCodec
      val ts = Timestamp(seconds = 1704067200L, nanos = 0)
      val json = writeToString(ts)
      assert(json.contains("\"seconds\":\"1704067200\""))
    }

    test("message - round-trip Timestamp") {
      import com.google.protobuf.timestamp.Timestamp
      given JsonValueCodec[Timestamp] = messageCodec
      val ts = Timestamp(seconds = 1704067200L, nanos = 500000000)
      val json = writeToString(ts)
      val decoded = readFromString[Timestamp](json)
      assert(decoded == ts)
    }

    test("message - encode StringValue wrapper") {
      import com.google.protobuf.wrappers.StringValue
      given JsonValueCodec[StringValue] = messageCodec
      val sv = StringValue("hello")
      val json = writeToString(sv)
      assert(json == """{"value":"hello"}""")
    }

    test("message - round-trip Int32Value wrapper") {
      import com.google.protobuf.wrappers.Int32Value
      given JsonValueCodec[Int32Value] = messageCodec
      val iv = Int32Value(42)
      val json = writeToString(iv)
      val decoded = readFromString[Int32Value](json)
      assert(decoded == iv)
    }

    test("message - omit default values by default") {
      import com.google.protobuf.duration.Duration
      given JsonValueCodec[Duration] = messageCodec
      val dur = Duration(seconds = 0, nanos = 0)
      val json = writeToString(dur)
      // Default values (0) should be omitted
      assert(json == "{}")
    }

    test("message - include default values when configured") {
      import com.google.protobuf.duration.Duration
      given JsonValueCodec[Duration] = messageCodec(includingDefaultValueFields = true)
      val dur = Duration(seconds = 0, nanos = 0)
      val json = writeToString(dur)
      assert(json.contains("\"seconds\""))
      assert(json.contains("\"nanos\""))
    }

    test("message - decode unknown fields are skipped") {
      import com.google.protobuf.duration.Duration
      given JsonValueCodec[Duration] = messageCodec
      val json = """{"seconds":"60","unknownField":true,"nanos":100}"""
      val decoded = readFromString[Duration](json)
      assert(decoded.seconds == 60L)
      assert(decoded.nanos == 100)
    }

    test("message - decode missing fields use defaults") {
      import com.google.protobuf.duration.Duration
      given JsonValueCodec[Duration] = messageCodec
      val json = """{"seconds":"30"}"""
      val decoded = readFromString[Duration](json)
      assert(decoded.seconds == 30L)
      assert(decoded.nanos == 0)
    }

    test("message - encode Empty") {
      import com.google.protobuf.empty.Empty
      given JsonValueCodec[Empty] = messageCodec
      val e = Empty()
      val json = writeToString(e)
      assert(json == "{}")
    }

    test("message - round-trip BoolValue") {
      import com.google.protobuf.wrappers.BoolValue
      given JsonValueCodec[BoolValue] = messageCodec
      val bv = BoolValue(true)
      val json = writeToString(bv)
      assert(json == """{"value":true}""")
      val decoded = readFromString[BoolValue](json)
      assert(decoded == bv)
    }
  }
