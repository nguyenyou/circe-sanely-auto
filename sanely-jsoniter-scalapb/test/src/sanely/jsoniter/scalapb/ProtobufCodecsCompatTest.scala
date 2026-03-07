package sanely.jsoniter.scalapb

import utest.*
import com.github.plokhotnyuk.jsoniter_scala.core.*
import scalapb_circe.{Parser, Printer}

/** Cross-codec compatibility tests: verifies ProtobufCodecs produces JSON
  * identical to scalapb_circe's Printer for non-well-known types,
  * and that each side can decode the other's output.
  *
  * Note: well-known types (Duration, Timestamp, wrappers) have special
  * formatting in scalapb_circe (e.g. Duration → "120.5s") that differs
  * from raw descriptor-based encoding. These tests use regular message
  * types (SourceContext, Mixin, Method, EnumValue, Field) which don't
  * have special formatters.
  */
object ProtobufCodecsCompatTest extends TestSuite:
  import ProtobufCodecs.*

  private val printer = new Printer(includingDefaultValueFields = false)
  private val printerWithDefaults = new Printer(includingDefaultValueFields = true)
  private val parser = new Parser()

  val tests = Tests {

    // === SourceContext (single string field) ===

    test("compat - SourceContext encode matches scalapb_circe") {
      import com.google.protobuf.source_context.SourceContext
      given JsonValueCodec[SourceContext] = messageCodec
      val sc = SourceContext(fileName = "example.proto")
      val ours = writeToString(sc)
      val theirs = printer.toJson(sc).noSpaces
      assert(ours == theirs)
    }

    test("compat - SourceContext: scalapb_circe decodes our output") {
      import com.google.protobuf.source_context.SourceContext
      given JsonValueCodec[SourceContext] = messageCodec
      val sc = SourceContext(fileName = "test/foo.proto")
      val json = writeToString(sc)
      val decoded = parser.fromJsonString[SourceContext](json)
      assert(decoded == sc)
    }

    test("compat - SourceContext: we decode scalapb_circe output") {
      import com.google.protobuf.source_context.SourceContext
      given JsonValueCodec[SourceContext] = messageCodec
      val sc = SourceContext(fileName = "test/foo.proto")
      val json = printer.toJson(sc).noSpaces
      val decoded = readFromString[SourceContext](json)
      assert(decoded == sc)
    }

    test("compat - SourceContext default values match") {
      import com.google.protobuf.source_context.SourceContext
      given JsonValueCodec[SourceContext] = messageCodec
      val sc = SourceContext(fileName = "")
      val ours = writeToString(sc)
      val theirs = printer.toJson(sc).noSpaces
      assert(ours == theirs)
    }

    // === Mixin (two string fields) ===

    test("compat - Mixin encode matches scalapb_circe") {
      import com.google.protobuf.api.Mixin
      given JsonValueCodec[Mixin] = messageCodec
      val m = Mixin(name = "my.mixin.Name", root = "root_value")
      val ours = writeToString(m)
      val theirs = printer.toJson(m).noSpaces
      assert(ours == theirs)
    }

    test("compat - Mixin: scalapb_circe decodes our output") {
      import com.google.protobuf.api.Mixin
      given JsonValueCodec[Mixin] = messageCodec
      val m = Mixin(name = "my.mixin.Name", root = "root_value")
      val json = writeToString(m)
      val decoded = parser.fromJsonString[Mixin](json)
      assert(decoded == m)
    }

    test("compat - Mixin: we decode scalapb_circe output") {
      import com.google.protobuf.api.Mixin
      given JsonValueCodec[Mixin] = messageCodec
      val m = Mixin(name = "my.mixin.Name", root = "root_value")
      val json = printer.toJson(m).noSpaces
      val decoded = readFromString[Mixin](json)
      assert(decoded == m)
    }

    // === EnumValue (string + int32 + repeated Option) ===

    test("compat - EnumValue encode matches scalapb_circe") {
      import com.google.protobuf.`type`.EnumValue
      given JsonValueCodec[EnumValue] = messageCodec
      val ev = EnumValue(name = "MY_VALUE", number = 42)
      val ours = writeToString(ev)
      val theirs = printer.toJson(ev).noSpaces
      assert(ours == theirs)
    }

    test("compat - EnumValue: scalapb_circe decodes our output") {
      import com.google.protobuf.`type`.EnumValue
      given JsonValueCodec[EnumValue] = messageCodec
      val ev = EnumValue(name = "STATUS_OK", number = 200)
      val json = writeToString(ev)
      val decoded = parser.fromJsonString[EnumValue](json)
      assert(decoded == ev)
    }

    test("compat - EnumValue: we decode scalapb_circe output") {
      import com.google.protobuf.`type`.EnumValue
      given JsonValueCodec[EnumValue] = messageCodec
      val ev = EnumValue(name = "STATUS_OK", number = 200)
      val json = printer.toJson(ev).noSpaces
      val decoded = readFromString[EnumValue](json)
      assert(decoded == ev)
    }

    test("compat - EnumValue default values match") {
      import com.google.protobuf.`type`.EnumValue
      given JsonValueCodec[EnumValue] = messageCodec
      val ev = EnumValue()
      val ours = writeToString(ev)
      val theirs = printer.toJson(ev).noSpaces
      assert(ours == theirs)
    }

    // === EnumValue with includingDefaultValueFields ===

    test("compat - EnumValue with defaults matches scalapb_circe") {
      import com.google.protobuf.`type`.EnumValue
      given JsonValueCodec[EnumValue] = messageCodec(includingDefaultValueFields = true)
      val ev = EnumValue(name = "", number = 0)
      val ours = writeToString(ev)
      val theirs = printerWithDefaults.toJson(ev).noSpaces
      assert(ours == theirs)
    }

    // === Method (multiple field types: string, bool, enum) ===

    test("compat - Method encode matches scalapb_circe") {
      import com.google.protobuf.api.Method
      import com.google.protobuf.`type`.Syntax
      given JsonValueCodec[Method] = messageCodec
      val m = Method(
        name = "GetUser",
        requestTypeUrl = "type.googleapis.com/example.GetUserRequest",
        requestStreaming = false,
        responseTypeUrl = "type.googleapis.com/example.GetUserResponse",
        responseStreaming = true,
        syntax = Syntax.SYNTAX_PROTO3
      )
      val ours = writeToString(m)
      val theirs = printer.toJson(m).noSpaces
      assert(ours == theirs)
    }

    test("compat - Method: scalapb_circe decodes our output") {
      import com.google.protobuf.api.Method
      import com.google.protobuf.`type`.Syntax
      given JsonValueCodec[Method] = messageCodec
      val m = Method(
        name = "ListItems",
        requestTypeUrl = "type.googleapis.com/example.ListItemsRequest",
        responseTypeUrl = "type.googleapis.com/example.ListItemsResponse",
        responseStreaming = true,
        syntax = Syntax.SYNTAX_PROTO3
      )
      val json = writeToString(m)
      val decoded = parser.fromJsonString[Method](json)
      assert(decoded == m)
    }

    test("compat - Method: we decode scalapb_circe output") {
      import com.google.protobuf.api.Method
      import com.google.protobuf.`type`.Syntax
      given JsonValueCodec[Method] = messageCodec
      val m = Method(
        name = "ListItems",
        requestTypeUrl = "type.googleapis.com/example.ListItemsRequest",
        responseTypeUrl = "type.googleapis.com/example.ListItemsResponse",
        responseStreaming = true,
        syntax = Syntax.SYNTAX_PROTO3
      )
      val json = printer.toJson(m).noSpaces
      val decoded = readFromString[Method](json)
      assert(decoded == m)
    }

    // === Enum (complex: nested repeated fields + enum) ===

    test("compat - Enum encode matches scalapb_circe") {
      import com.google.protobuf.`type`.{Enum => ProtoEnum, EnumValue, Syntax}
      import com.google.protobuf.source_context.SourceContext
      given JsonValueCodec[ProtoEnum] = messageCodec
      val e = ProtoEnum(
        name = "Color",
        enumvalue = Seq(
          EnumValue(name = "RED", number = 0),
          EnumValue(name = "GREEN", number = 1),
          EnumValue(name = "BLUE", number = 2)
        ),
        sourceContext = Some(SourceContext(fileName = "color.proto")),
        syntax = Syntax.SYNTAX_PROTO3
      )
      val ours = writeToString(e)
      val theirs = printer.toJson(e).noSpaces
      assert(ours == theirs)
    }

    test("compat - Enum: scalapb_circe decodes our output") {
      import com.google.protobuf.`type`.{Enum => ProtoEnum, EnumValue, Syntax}
      import com.google.protobuf.source_context.SourceContext
      given JsonValueCodec[ProtoEnum] = messageCodec
      val e = ProtoEnum(
        name = "Status",
        enumvalue = Seq(
          EnumValue(name = "UNKNOWN", number = 0),
          EnumValue(name = "ACTIVE", number = 1)
        ),
        sourceContext = Some(SourceContext(fileName = "status.proto")),
        syntax = Syntax.SYNTAX_PROTO3
      )
      val json = writeToString(e)
      val decoded = parser.fromJsonString[ProtoEnum](json)
      assert(decoded == e)
    }

    test("compat - Enum: we decode scalapb_circe output") {
      import com.google.protobuf.`type`.{Enum => ProtoEnum, EnumValue, Syntax}
      import com.google.protobuf.source_context.SourceContext
      given JsonValueCodec[ProtoEnum] = messageCodec
      val e = ProtoEnum(
        name = "Status",
        enumvalue = Seq(
          EnumValue(name = "UNKNOWN", number = 0),
          EnumValue(name = "ACTIVE", number = 1)
        ),
        sourceContext = Some(SourceContext(fileName = "status.proto")),
        syntax = Syntax.SYNTAX_PROTO3
      )
      val json = printer.toJson(e).noSpaces
      val decoded = readFromString[ProtoEnum](json)
      assert(decoded == e)
    }

    // === Field type (complex with many field types) ===

    test("compat - Field encode matches scalapb_circe") {
      import com.google.protobuf.`type`.Field
      import com.google.protobuf.`type`.Field.{Kind, Cardinality}
      given JsonValueCodec[Field] = messageCodec
      val f = Field(
        kind = Kind.TYPE_STRING,
        cardinality = Cardinality.CARDINALITY_OPTIONAL,
        number = 1,
        name = "user_name",
        typeUrl = "",
        oneofIndex = 0,
        packed = false,
        jsonName = "userName"
      )
      val ours = writeToString(f)
      val theirs = printer.toJson(f).noSpaces
      assert(ours == theirs)
    }

    test("compat - Field: round-trip through scalapb_circe") {
      import com.google.protobuf.`type`.Field
      import com.google.protobuf.`type`.Field.{Kind, Cardinality}
      given JsonValueCodec[Field] = messageCodec
      val f = Field(
        kind = Kind.TYPE_INT64,
        cardinality = Cardinality.CARDINALITY_REPEATED,
        number = 5,
        name = "ids",
        jsonName = "ids"
      )
      // ours → scalapb_circe
      val json1 = writeToString(f)
      val decoded1 = parser.fromJsonString[Field](json1)
      assert(decoded1 == f)
      // scalapb_circe → ours
      val json2 = printer.toJson(f).noSpaces
      val decoded2 = readFromString[Field](json2)
      assert(decoded2 == f)
    }
  }
