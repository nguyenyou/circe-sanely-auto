package sanely.jsoniter.scalapb

import com.github.plokhotnyuk.jsoniter_scala.core.*
import scalapb.{GeneratedEnum, GeneratedEnumCompanion, GeneratedMessage, GeneratedMessageCompanion}
import scalapb.descriptors.*

/** JsonValueCodec instances for ScalaPB GeneratedMessage and GeneratedEnum types.
  * Produces JSON matching scalapb_circe's default format (camelCase field names,
  * omitting default values, enums as name strings).
  */
object ProtobufCodecs:

  /** Codec for ScalaPB GeneratedEnum — encodes as the enum name string. */
  def enumCodec[E <: GeneratedEnum](using companion: GeneratedEnumCompanion[E]): JsonValueCodec[E] =
    new JsonValueCodec[E]:
      val nullValue: E = null.asInstanceOf[E]

      def encodeValue(x: E, out: JsonWriter): Unit =
        if (x: Any) == null then out.writeNull()
        else out.writeVal(x.name)

      def decodeValue(in: JsonReader, default: E): E =
        if in.isNextToken('n') then
          in.readNullOrError(default, "expected string or null")
        else
          in.rollbackToken()
          val name = in.readString(null)
          if name == null then in.decodeError("expected enum name string")
          companion.fromName(name) match
            case Some(e) => e
            case None =>
              in.decodeError(s"unknown enum value: $name")
              default

  /** Codec for ScalaPB GeneratedMessage — streams JSON using descriptor reflection. */
  def messageCodec[M <: GeneratedMessage](
    using companion: GeneratedMessageCompanion[M]
  ): JsonValueCodec[M] =
    messageCodec[M](includingDefaultValueFields = false)

  /** Codec for ScalaPB GeneratedMessage with configurable default field inclusion. */
  def messageCodec[M <: GeneratedMessage](
    includingDefaultValueFields: Boolean
  )(using companion: GeneratedMessageCompanion[M]): JsonValueCodec[M] =
    val descriptor = companion.scalaDescriptor
    val fields = descriptor.fields
    val jsonNames = fields.map(fd => fd.asProto.getJsonName).toArray
    new JsonValueCodec[M]:
      val nullValue: M = null.asInstanceOf[M]

      def encodeValue(x: M, out: JsonWriter): Unit =
        if (x: Any) == null then out.writeNull()
        else
          out.writeObjectStart()
          var i = 0
          while i < fields.length do
            val fd = fields(i)
            val value = x.getField(fd)
            val isDefault = isDefaultValue(value)
            if includingDefaultValueFields || !isDefault then
              out.writeKey(jsonNames(i))
              writePValue(value, fd, out)
            i += 1
          out.writeObjectEnd()

      def decodeValue(in: JsonReader, default: M): M =
        if in.isNextToken('{') then
          val fieldMap = scala.collection.mutable.Map.empty[FieldDescriptor, PValue]
          if !in.isNextToken('}') then
            in.rollbackToken()
            var continue = true
            while continue do
              val key = in.readKeyAsString()
              findField(key, fields, jsonNames) match
                case Some(fd) =>
                  fieldMap(fd) = readPValue(in, fd)
                case None =>
                  in.skip()
              continue = in.isNextToken(',')
            if !in.isCurrentToken('}') then in.objectEndOrCommaError()
          companion.messageReads.read(PMessage(fieldMap.toMap))
        else
          in.readNullOrTokenError(default, '{')

  // === Internal helpers ===

  private def isDefaultValue(value: PValue): Boolean =
    value match
      case PEmpty => true
      case PRepeated(xs) => xs.isEmpty
      case PInt(0) => true
      case PLong(0L) => true
      case PDouble(0.0) => true
      case PFloat(0.0f) => true
      case PBoolean(false) => true
      case PString("") => true
      case PByteString(bs) => bs.isEmpty
      case PEnum(ev) => ev.number == 0
      case _ => false

  private def findField(
    key: String, fields: Vector[FieldDescriptor], jsonNames: Array[String]
  ): Option[FieldDescriptor] =
    var i = 0
    while i < fields.length do
      if jsonNames(i) == key || fields(i).name == key then
        return Some(fields(i))
      i += 1
    None

  private def writePValue(value: PValue, fd: FieldDescriptor, out: JsonWriter): Unit =
    value match
      case PEmpty =>
        fd.scalaType match
          case ScalaType.Message(_) => out.writeNull()
          case ScalaType.Enum(_) => out.writeVal("")
          case ScalaType.String => out.writeVal("")
          case ScalaType.ByteString => out.writeVal("")
          case ScalaType.Boolean => out.writeVal(false)
          case ScalaType.Double | ScalaType.Float => out.writeVal(0.0)
          case ScalaType.Long => out.writeVal("0")
          case ScalaType.Int => out.writeVal(0)
      case PInt(v) =>
        out.writeVal(v)
      case PLong(v) =>
        // Proto3 JSON: int64/uint64 as string for JavaScript safety
        out.writeVal(v.toString)
      case PDouble(v) =>
        out.writeVal(v)
      case PFloat(v) =>
        out.writeVal(v)
      case PBoolean(v) =>
        out.writeVal(v)
      case PString(v) =>
        out.writeVal(v)
      case PByteString(v) =>
        out.writeVal(java.util.Base64.getEncoder.encodeToString(v.toByteArray))
      case PEnum(enumValue) =>
        out.writeVal(enumValue.name)
      case PMessage(fieldMap) =>
        out.writeObjectStart()
        fd.scalaType match
          case ScalaType.Message(md) =>
            val subFields = md.fields
            var i = 0
            while i < subFields.length do
              val subFd = subFields(i)
              fieldMap.get(subFd) match
                case Some(subValue) =>
                  if !isDefaultValue(subValue) then
                    out.writeKey(subFd.asProto.getJsonName)
                    writePValue(subValue, subFd, out)
                case None => ()
              i += 1
          case _ => ()
        out.writeObjectEnd()
      case PRepeated(values) =>
        if fd.isMapField then
          out.writeObjectStart()
          fd.scalaType match
            case ScalaType.Message(md) =>
              val keyFd = md.findFieldByNumber(1).get
              val valFd = md.findFieldByNumber(2).get
              for entry <- values do
                entry match
                  case PMessage(entryMap) =>
                    val keyValue = entryMap.getOrElse(keyFd, PEmpty)
                    val valValue = entryMap.getOrElse(valFd, PEmpty)
                    val keyStr = keyValue match
                      case PString(s) => s
                      case PInt(v) => v.toString
                      case PLong(v) => v.toString
                      case PBoolean(v) => v.toString
                      case _ => keyValue.toString
                    out.writeKey(keyStr)
                    writePValue(valValue, valFd, out)
                  case _ => ()
            case _ => ()
          out.writeObjectEnd()
        else
          out.writeArrayStart()
          for v <- values do
            writePValue(v, fd, out)
          out.writeArrayEnd()

  private def readPValue(in: JsonReader, fd: FieldDescriptor): PValue =
    if fd.isRepeated && !fd.isMapField then
      if !in.isNextToken('[') then
        in.readNullOrTokenError(PRepeated(Vector.empty), '[')
      else
        val buf = Vector.newBuilder[PValue]
        if !in.isNextToken(']') then
          in.rollbackToken()
          buf += readSingleValue(in, fd)
          while in.isNextToken(',') do
            buf += readSingleValue(in, fd)
          if !in.isCurrentToken(']') then in.arrayEndOrCommaError()
        PRepeated(buf.result())
    else if fd.isMapField then
      if !in.isNextToken('{') then
        in.readNullOrTokenError(PRepeated(Vector.empty), '{')
      else
        fd.scalaType match
          case ScalaType.Message(md) =>
            val keyFd = md.findFieldByNumber(1).get
            val valFd = md.findFieldByNumber(2).get
            val buf = Vector.newBuilder[PValue]
            if !in.isNextToken('}') then
              in.rollbackToken()
              var continue = true
              while continue do
                val key = in.readKeyAsString()
                val keyPValue: PValue = keyFd.scalaType match
                  case ScalaType.Int => PInt(key.toInt)
                  case ScalaType.Long => PLong(key.toLong)
                  case ScalaType.Boolean => PBoolean(key.toBoolean)
                  case _ => PString(key)
                val valPValue = readSingleValue(in, valFd)
                buf += PMessage(Map(keyFd -> keyPValue, valFd -> valPValue))
                continue = in.isNextToken(',')
              if !in.isCurrentToken('}') then in.objectEndOrCommaError()
            PRepeated(buf.result())
          case _ =>
            in.skip()
            PRepeated(Vector.empty)
    else
      readSingleValue(in, fd)

  private def readSingleValue(in: JsonReader, fd: FieldDescriptor): PValue =
    fd.scalaType match
      case ScalaType.Int =>
        PInt(in.readInt())
      case ScalaType.Long =>
        // Proto3 JSON: int64 may be string or number
        if in.isNextToken('"') then
          in.rollbackToken()
          PLong(in.readString(null).toLong)
        else
          in.rollbackToken()
          PLong(in.readLong())
      case ScalaType.Double =>
        PDouble(in.readDouble())
      case ScalaType.Float =>
        PFloat(in.readFloat())
      case ScalaType.Boolean =>
        PBoolean(in.readBoolean())
      case ScalaType.String =>
        PString(in.readString(null))
      case ScalaType.ByteString =>
        val b64 = in.readString(null)
        if b64 == null then PEmpty
        else PByteString(com.google.protobuf.ByteString.copyFrom(java.util.Base64.getDecoder.decode(b64)))
      case ScalaType.Enum(ed) =>
        val name = in.readString(null)
        if name == null then PEmpty
        else
          ed.values.find(_.name == name) match
            case Some(enumValue) => PEnum(enumValue)
            case None =>
              in.decodeError(s"unknown enum value: $name")
              PEmpty
      case ScalaType.Message(md) =>
        if in.isNextToken('n') then
          in.readNullOrError(PEmpty, "expected object or null")
        else
          in.rollbackToken()
          if !in.isNextToken('{') then
            in.readNullOrTokenError(PEmpty, '{')
          else
            val subFields = md.fields
            val subJsonNames = subFields.map(_.asProto.getJsonName).toArray
            val fieldMap = scala.collection.mutable.Map.empty[FieldDescriptor, PValue]
            if !in.isNextToken('}') then
              in.rollbackToken()
              var continue = true
              while continue do
                val key = in.readKeyAsString()
                findField(key, subFields, subJsonNames) match
                  case Some(subFd) =>
                    fieldMap(subFd) = readPValue(in, subFd)
                  case None =>
                    in.skip()
                continue = in.isNextToken(',')
              if !in.isCurrentToken('}') then in.objectEndOrCommaError()
            PMessage(fieldMap.toMap)
