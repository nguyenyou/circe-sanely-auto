package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.*
import scala.deriving.Mirror

/** Runtime utility methods called by macro-generated code.
  * Reduces generated AST size by moving common patterns out of inline expansions.
  */
object JsoniterRuntime:

  /** Codec that supports inline field encoding/decoding (without object braces).
    * Used by discriminator-tagged sum codecs to write/read variant fields
    * at the same level as the discriminator field.
    */
  private[jsoniter] trait InlineFieldsCodec[P] extends JsonValueCodec[P]:
    /** Encode fields only (no writeObjectStart/writeObjectEnd). */
    def encodeFields(x: P, out: JsonWriter): Unit
    /** Decode fields from current position after a prior field (e.g. discriminator).
      * Handles both cases: more fields follow (comma) or object ends immediately.
      */
    def decodeFieldsAfterDiscriminator(in: JsonReader): P

  /** Lightweight Product wrapper over an Array for use with Mirror.fromProduct. */
  final class ArrayProduct(val arr: Array[Any]) extends Product:
    def canEqual(that: Any): Boolean = true
    def productArity: Int = arr.length
    def productElement(n: Int): Any = arr(n)

  // === Product codec ===

  def productCodec[P](
    mirror: => Mirror.ProductOf[P],
    names: Array[String],
    initCodecs: () => Array[JsonValueCodec[Any]],
    nullValues: Array[Any],
    encodeFn: (P, Array[JsonValueCodec[Any]], JsonWriter) => Unit,
    decodeFn: (JsonReader, Array[JsonValueCodec[Any]], Mirror.ProductOf[P]) => P
  ): JsonValueCodec[P] =
    new JsonValueCodec[P]:
      private lazy val _codecs = initCodecs()
      val nullValue: P = null.asInstanceOf[P]

      def encodeValue(x: P, out: JsonWriter): Unit =
        if (x: Any) == null then out.writeNull()
        else
          out.writeObjectStart()
          encodeFn(x, _codecs, out)
          out.writeObjectEnd()

      def decodeValue(in: JsonReader, default: P): P =
        if in.isNextToken('{') then
          decodeFn(in, _codecs, mirror)
        else
          in.readNullOrTokenError(default, '{')

  // === Sum codec (external tagging) ===

  def sumCodec[S](
    mirror: => Mirror.SumOf[S],
    labels: Array[String],
    initCodecs: () => Array[JsonValueCodec[Any]]
  ): JsonValueCodec[S] =
    new JsonValueCodec[S]:
      private lazy val _codecs = initCodecs()
      val nullValue: S = null.asInstanceOf[S]

      def encodeValue(x: S, out: JsonWriter): Unit =
        if (x: Any) == null then out.writeNull()
        else
          val ord = mirror.ordinal(x)
          out.writeObjectStart()
          out.writeKey(labels(ord))
          _codecs(ord).encodeValue(x, out)
          out.writeObjectEnd()

      def decodeValue(in: JsonReader, default: S): S =
        if !in.isNextToken('{') then
          in.readNullOrTokenError(default, '{')
        else
          val key = in.readKeyAsString()
          var idx = -1
          var i = 0
          while i < labels.length && idx < 0 do
            if key == labels(i) then idx = i
            i += 1
          if idx < 0 then
            in.decodeError(s"Unknown variant: $key")
            default
          else
            val result = _codecs(idx).decodeValue(in, _codecs(idx).nullValue)
            if !in.isNextToken('}') then in.objectEndOrCommaError()
            result.asInstanceOf[S]

  // === Sum codec with sub-trait support (external tagging) ===

  def sumCodecWithSubTraits[S](
    mirror: => Mirror.SumOf[S],
    directLabels: Array[String],
    isSubTrait: Array[Boolean],
    initDirectCodecs: () => Array[JsonValueCodec[Any]],
    allLeafLabels: Array[String],
    initAllLeafCodecs: () => Array[JsonValueCodec[Any]]
  ): JsonValueCodec[S] =
    new JsonValueCodec[S]:
      private lazy val _directCodecs = initDirectCodecs()
      private lazy val _allLeafCodecs = initAllLeafCodecs()
      val nullValue: S = null.asInstanceOf[S]

      def encodeValue(x: S, out: JsonWriter): Unit =
        if (x: Any) == null then out.writeNull()
        else
          val ord = mirror.ordinal(x)
          if isSubTrait(ord) then
            // Sub-trait codec handles its own external tagging
            _directCodecs(ord).encodeValue(x, out)
          else
            out.writeObjectStart()
            out.writeKey(directLabels(ord))
            _directCodecs(ord).encodeValue(x, out)
            out.writeObjectEnd()

      def decodeValue(in: JsonReader, default: S): S =
        if !in.isNextToken('{') then
          in.readNullOrTokenError(default, '{')
        else
          val key = in.readKeyAsString()
          var idx = -1
          var i = 0
          while i < allLeafLabels.length && idx < 0 do
            if key == allLeafLabels(i) then idx = i
            i += 1
          if idx < 0 then
            in.decodeError(s"Unknown variant: $key")
            default
          else
            val result = _allLeafCodecs(idx).decodeValue(in, _allLeafCodecs(idx).nullValue)
            if !in.isNextToken('}') then in.objectEndOrCommaError()
            result.asInstanceOf[S]

  // === Configured sum codec with sub-trait support ===

  def configuredSumCodecWithSubTraits[S](
    mirror: => Mirror.SumOf[S],
    rawDirectLabels: Array[String],
    isSubTrait: Array[Boolean],
    transformConstructorNames: String => String,
    discriminator: Option[String],
    initDirectCodecs: () => Array[JsonValueCodec[Any]],
    rawAllLeafLabels: Array[String],
    initAllLeafCodecs: () => Array[JsonValueCodec[Any]],
    strictDecoding: Boolean = false
  ): JsonValueCodec[S] =
    val directLabels = rawDirectLabels.map(transformConstructorNames)
    val allLeafLabels = rawAllLeafLabels.map(transformConstructorNames)
    discriminator match
      case None =>
        sumCodecWithSubTraits[S](
          mirror, directLabels, isSubTrait, initDirectCodecs,
          allLeafLabels, initAllLeafCodecs)
      case Some(disc) =>
        // Discriminator with sub-traits: flat encoding with inline fields
        new JsonValueCodec[S]:
          private lazy val _directCodecs = initDirectCodecs()
          private lazy val _allLeafCodecs = initAllLeafCodecs()
          val nullValue: S = null.asInstanceOf[S]

          def encodeValue(x: S, out: JsonWriter): Unit =
            if (x: Any) == null then out.writeNull()
            else
              val ord = mirror.ordinal(x)
              if isSubTrait(ord) then
                _directCodecs(ord).encodeValue(x, out)
              else
                out.writeObjectStart()
                out.writeKey(disc)
                out.writeVal(directLabels(ord))
                _directCodecs(ord).asInstanceOf[InlineFieldsCodec[Any]].encodeFields(x, out)
                out.writeObjectEnd()

          def decodeValue(in: JsonReader, default: S): S =
            decodeWithDiscriminator[S](in, disc, allLeafLabels, _allLeafCodecs, default)

  // === Configured product codec ===

  def configuredProductCodec[P](
    mirror: => Mirror.ProductOf[P],
    rawNames: Array[String],
    transformMemberNames: String => String,
    initCodecs: () => Array[JsonValueCodec[Any]],
    nullValues: Array[Any],
    hasDefaults: Array[Boolean],
    defaults: Array[Any],
    isOption: Array[Boolean],
    useDefaults: Boolean,
    dropNullValues: Boolean,
    strictDecoding: Boolean,
    encodeFn: (P, Array[String], Array[JsonValueCodec[Any]], JsonWriter) => Unit,
    encodeDropNullFn: (P, Array[String], Array[JsonValueCodec[Any]], JsonWriter) => Unit,
    decodeFn: (JsonReader, Array[String], Array[JsonValueCodec[Any]], Mirror.ProductOf[P]) => P,
    decodeAfterDiscFn: (JsonReader, Array[String], Array[JsonValueCodec[Any]], Mirror.ProductOf[P]) => P
  ): JsonValueCodec[P] =
    val names = rawNames.map(transformMemberNames)
    new InlineFieldsCodec[P]:
      private lazy val _codecs = initCodecs()
      val nullValue: P = null.asInstanceOf[P]

      def encodeValue(x: P, out: JsonWriter): Unit =
        if (x: Any) == null then out.writeNull()
        else
          out.writeObjectStart()
          if dropNullValues then encodeDropNullFn(x, names, _codecs, out)
          else encodeFn(x, names, _codecs, out)
          out.writeObjectEnd()

      def decodeValue(in: JsonReader, default: P): P =
        if in.isNextToken('{') then
          decodeFn(in, names, _codecs, mirror)
        else
          in.readNullOrTokenError(default, '{')

      def encodeFields(x: P, out: JsonWriter): Unit =
        if (x: Any) != null then
          if dropNullValues then encodeDropNullFn(x, names, _codecs, out)
          else encodeFn(x, names, _codecs, out)

      def decodeFieldsAfterDiscriminator(in: JsonReader): P =
        decodeAfterDiscFn(in, names, _codecs, mirror)

  // === Configured sum codec ===

  def configuredSumCodec[S](
    mirror: => Mirror.SumOf[S],
    rawLabels: Array[String],
    transformConstructorNames: String => String,
    discriminator: Option[String],
    initCodecs: () => Array[JsonValueCodec[Any]],
    strictDecoding: Boolean = false
  ): JsonValueCodec[S] =
    val labels = rawLabels.map(transformConstructorNames)
    discriminator match
      case None => // external tagging
        new JsonValueCodec[S]:
          private lazy val _codecs = initCodecs()
          val nullValue: S = null.asInstanceOf[S]

          def encodeValue(x: S, out: JsonWriter): Unit =
            if (x: Any) == null then out.writeNull()
            else
              val ord = mirror.ordinal(x)
              out.writeObjectStart()
              out.writeKey(labels(ord))
              _codecs(ord).encodeValue(x, out)
              out.writeObjectEnd()

          def decodeValue(in: JsonReader, default: S): S =
            if !in.isNextToken('{') then
              in.readNullOrTokenError(default, '{')
            else
              val key = in.readKeyAsString()
              var idx = -1
              var i = 0
              while i < labels.length && idx < 0 do
                if key == labels(i) then idx = i
                i += 1
              if idx < 0 then
                in.decodeError(s"Unknown variant: $key")
                default
              else
                val result = _codecs(idx).decodeValue(in, _codecs(idx).nullValue)
                if strictDecoding then
                  if !in.isNextToken('}') then
                    in.decodeError(s"Strict decoding: expected a single key object with one of: ${labels.mkString(", ")}")
                else
                  if !in.isNextToken('}') then in.objectEndOrCommaError()
                result.asInstanceOf[S]

      case Some(disc) => // discriminator tagging (flat: fields inline with discriminator)
        new JsonValueCodec[S]:
          private lazy val _codecs = initCodecs()
          val nullValue: S = null.asInstanceOf[S]

          def encodeValue(x: S, out: JsonWriter): Unit =
            if (x: Any) == null then out.writeNull()
            else
              val ord = mirror.ordinal(x)
              out.writeObjectStart()
              out.writeKey(disc)
              out.writeVal(labels(ord))
              _codecs(ord).asInstanceOf[InlineFieldsCodec[Any]].encodeFields(x, out)
              out.writeObjectEnd()

          def decodeValue(in: JsonReader, default: S): S =
            decodeWithDiscriminator[S](in, disc, labels, _codecs, default)

  // === Discriminator decode helper ===

  /** Decode an object with a discriminator field that may appear at any position.
    * Buffers non-discriminator fields as raw JSON strings, finds the discriminator,
    * then reconstructs and decodes the variant.
    */
  private def decodeWithDiscriminator[S](
    in: JsonReader, disc: String, labels: Array[String],
    codecs: Array[JsonValueCodec[Any]], default: S
  ): S =
    if !in.isNextToken('{') then
      in.readNullOrTokenError(default, '{')
    else if in.isNextToken('}') then
      in.decodeError(s"Expected discriminator field '$disc' in empty object")
      default
    else
      in.rollbackToken()
      // Fast path: discriminator is first key
      val firstKey = in.readKeyAsString()
      if firstKey == disc then
        val typeName = in.readString(null)
        val idx = findLabel(typeName, labels)
        if idx < 0 then
          in.decodeError(s"Unknown variant: $typeName")
          return default
        codecs(idx).asInstanceOf[InlineFieldsCodec[Any]].decodeFieldsAfterDiscriminator(in).asInstanceOf[S]
      else
        // Slow path: buffer fields, find discriminator at any position
        val buf = new java.util.ArrayList[(String, String)]()
        val firstValSb = new java.lang.StringBuilder(32)
        readJsonValueToString(in, firstValSb)
        buf.add((firstKey, firstValSb.toString))
        var discValue: String = null
        var cont = in.isNextToken(',')
        while cont do
          val k = in.readKeyAsString()
          if k == disc then
            discValue = in.readString(null)
          else
            val sb = new java.lang.StringBuilder(32)
            readJsonValueToString(in, sb)
            buf.add((k, sb.toString))
          cont = in.isNextToken(',')
        if !in.isCurrentToken('}') then in.objectEndOrCommaError()
        if discValue == null then
          in.decodeError(s"Discriminator field '$disc' not found")
          return default
        val idx = findLabel(discValue, labels)
        if idx < 0 then
          in.decodeError(s"Unknown variant: $discValue")
          return default
        // Reconstruct JSON object from buffered fields and decode
        val sb = new java.lang.StringBuilder(64)
        sb.append('{')
        var fi = 0
        while fi < buf.size do
          if fi > 0 then sb.append(',')
          val entry = buf.get(fi)
          sb.append('"')
          appendEscaped(sb, entry._1)
          sb.append("\":")
          sb.append(entry._2)
          fi += 1
        sb.append('}')
        readFromString[Any](sb.toString)(using codecs(idx)).asInstanceOf[S]

  private def findLabel(name: String, labels: Array[String]): Int =
    var i = 0
    while i < labels.length do
      if name == labels(i) then return i
      i += 1
    -1

  /** Read a JSON value from the reader and append its string representation to sb. */
  private def readJsonValueToString(in: JsonReader, sb: java.lang.StringBuilder): Unit =
    val b = in.nextToken()
    in.rollbackToken()
    (b: @annotation.switch) match
      case '"' =>
        val s = in.readString(null)
        sb.append('"')
        appendEscaped(sb, s)
        sb.append('"')
      case 't' | 'f' =>
        sb.append(in.readBoolean())
      case 'n' =>
        in.readNullOrError[String](null, "expected null")
        sb.append("null")
      case '{' =>
        in.isNextToken('{')
        sb.append('{')
        if !in.isNextToken('}') then
          in.rollbackToken()
          var first = true
          var cont = true
          while cont do
            if !first then sb.append(',')
            val key = in.readKeyAsString()
            sb.append('"')
            appendEscaped(sb, key)
            sb.append("\":")
            readJsonValueToString(in, sb)
            first = false
            cont = in.isNextToken(',')
          if !in.isCurrentToken('}') then in.objectEndOrCommaError()
        sb.append('}')
      case '[' =>
        in.isNextToken('[')
        sb.append('[')
        if !in.isNextToken(']') then
          in.rollbackToken()
          var first = true
          var cont = true
          while cont do
            if !first then sb.append(',')
            readJsonValueToString(in, sb)
            first = false
            cont = in.isNextToken(',')
        sb.append(']')
      case _ => // number
        sb.append(in.readBigDecimal(null).toString)

  private def appendEscaped(sb: java.lang.StringBuilder, s: String): Unit =
    var i = 0
    while i < s.length do
      val c = s.charAt(i)
      c match
        case '"' => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case _ if c < 0x20 => sb.append(f"\\u${c.toInt}%04x")
        case _ => sb.append(c)
      i += 1

