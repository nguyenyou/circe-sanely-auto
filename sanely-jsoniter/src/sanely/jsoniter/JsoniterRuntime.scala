package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.*
import scala.deriving.Mirror

/** Runtime utility methods called by macro-generated code.
  * Reduces generated AST size by moving common patterns out of inline expansions.
  */
object JsoniterRuntime:

  /** Lightweight Product wrapper over an Array for use with Mirror.fromProduct. */
  private[jsoniter] final class ArrayProduct(val arr: Array[Any]) extends Product:
    def canEqual(that: Any): Boolean = true
    def productArity: Int = arr.length
    def productElement(n: Int): Any = arr(n)

  // === Product codec ===

  def productCodec[P](
    mirror: => Mirror.ProductOf[P],
    names: Array[String],
    initCodecs: () => Array[JsonValueCodec[Any]],
    nullValues: Array[Any]
  ): JsonValueCodec[P] =
    new JsonValueCodec[P]:
      private lazy val _codecs = initCodecs()
      val nullValue: P = null.asInstanceOf[P]

      def encodeValue(x: P, out: JsonWriter): Unit =
        if (x: Any) == null then out.writeNull()
        else encodeProduct(x.asInstanceOf[Product], names, _codecs, out)

      def decodeValue(in: JsonReader, default: P): P =
        if in.isNextToken('{') then
          decodeProduct(in, mirror, names, _codecs, nullValues)
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
    initAllLeafCodecs: () => Array[JsonValueCodec[Any]]
  ): JsonValueCodec[S] =
    val directLabels = rawDirectLabels.map(transformConstructorNames)
    val allLeafLabels = rawAllLeafLabels.map(transformConstructorNames)
    discriminator match
      case None =>
        sumCodecWithSubTraits[S](
          mirror, directLabels, isSubTrait, initDirectCodecs,
          allLeafLabels, initAllLeafCodecs)
      case Some(disc) =>
        // Discriminator with sub-traits: use flat leaf labels for lookup
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
                val product = x.asInstanceOf[Product]
                if product.productArity > 0 then
                  _directCodecs(ord).encodeValue(x, out)
                out.writeObjectEnd()

          def decodeValue(in: JsonReader, default: S): S =
            if !in.isNextToken('{') then
              in.readNullOrTokenError(default, '{')
            else
              if !in.isNextToken('}') then
                in.rollbackToken()
                val key = in.readKeyAsString()
                if key == disc then
                  val typeName = in.readString(null)
                  var idx = -1
                  var i = 0
                  while i < allLeafLabels.length && idx < 0 do
                    if typeName == allLeafLabels(i) then idx = i
                    i += 1
                  if idx < 0 then
                    in.decodeError(s"Unknown variant: $typeName")
                    return default
                  val result = _allLeafCodecs(idx).decodeValue(in, _allLeafCodecs(idx).nullValue)
                  if !in.isNextToken('}') then in.objectEndOrCommaError()
                  result.asInstanceOf[S]
                else
                  in.decodeError(s"Expected discriminator field '$disc' but got '$key'")
                  default
              else
                in.decodeError(s"Expected discriminator field '$disc' in empty object")
                default

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
    dropNullValues: Boolean
  ): JsonValueCodec[P] =
    val names = rawNames.map(transformMemberNames)
    new JsonValueCodec[P]:
      private lazy val _codecs = initCodecs()
      val nullValue: P = null.asInstanceOf[P]

      def encodeValue(x: P, out: JsonWriter): Unit =
        if (x: Any) == null then out.writeNull()
        else if dropNullValues then encodeProductDropNull(x.asInstanceOf[Product], names, _codecs, out)
        else encodeProduct(x.asInstanceOf[Product], names, _codecs, out)

      def decodeValue(in: JsonReader, default: P): P =
        if in.isNextToken('{') then
          decodeProductConfigured(in, mirror, names, _codecs, nullValues, hasDefaults, defaults, isOption, useDefaults)
        else
          in.readNullOrTokenError(default, '{')

  // === Configured sum codec ===

  def configuredSumCodec[S](
    mirror: => Mirror.SumOf[S],
    rawLabels: Array[String],
    transformConstructorNames: String => String,
    discriminator: Option[String],
    initCodecs: () => Array[JsonValueCodec[Any]]
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
                if !in.isNextToken('}') then in.objectEndOrCommaError()
                result.asInstanceOf[S]

      case Some(disc) => // discriminator tagging
        new JsonValueCodec[S]:
          private lazy val _codecs = initCodecs()
          val nullValue: S = null.asInstanceOf[S]

          def encodeValue(x: S, out: JsonWriter): Unit =
            if (x: Any) == null then out.writeNull()
            else
              val ord = mirror.ordinal(x)
              val inner = _codecs(ord)
              // Write discriminator field then the product fields (flattened)
              out.writeObjectStart()
              out.writeKey(disc)
              out.writeVal(labels(ord))
              // Encode the variant's fields inline (skip its own { })
              val product = x.asInstanceOf[Product]
              val arity = product.productArity
              if arity > 0 then
                // For case classes, we need the inner codec to write fields only
                // We encode via the inner codec into a temporary buffer, then replay without outer braces
                // Simpler approach: just write each field from the product
                // But we don't have the field names here... use inner codec
                inner.encodeValue(x, out)
              out.writeObjectEnd()

          def decodeValue(in: JsonReader, default: S): S =
            if !in.isNextToken('{') then
              in.readNullOrTokenError(default, '{')
            else
              // Read all fields, find discriminator
              // For now, simple approach: first key must be discriminator
              // More robust: scan for discriminator key
              var idx = -1
              var foundDisc = false
              // Buffer approach: read tokens until we find discriminator
              if !in.isNextToken('}') then
                in.rollbackToken()
                // We need to find the discriminator. For simplicity, require it first.
                val key = in.readKeyAsString()
                if key == disc then
                  val typeName = in.readString(null)
                  var i = 0
                  while i < labels.length && idx < 0 do
                    if typeName == labels(i) then idx = i
                    i += 1
                  if idx < 0 then
                    in.decodeError(s"Unknown variant: $typeName")
                    return default
                  foundDisc = true
                else
                  in.decodeError(s"Expected discriminator field '$disc' but got '$key'")
                  return default

                // Now decode the rest as the variant
                // The remaining fields are the variant's fields
                val result = _codecs(idx).decodeValue(in, _codecs(idx).nullValue)
                // Consume closing }
                if !in.isNextToken('}') then in.objectEndOrCommaError()
                result.asInstanceOf[S]
              else
                in.decodeError(s"Expected discriminator field '$disc' in empty object")
                default

  // === Internal helpers ===

  private def encodeProduct(
    x: Product, names: Array[String],
    codecs: Array[JsonValueCodec[Any]], out: JsonWriter
  ): Unit =
    out.writeObjectStart()
    var i = 0
    while i < names.length do
      out.writeNonEscapedAsciiKey(names(i))
      codecs(i).encodeValue(x.productElement(i), out)
      i += 1
    out.writeObjectEnd()

  private def encodeProductDropNull(
    x: Product, names: Array[String],
    codecs: Array[JsonValueCodec[Any]], out: JsonWriter
  ): Unit =
    out.writeObjectStart()
    var i = 0
    while i < names.length do
      val v = x.productElement(i)
      val isNull = v == null || (v.isInstanceOf[Option[?]] && v.asInstanceOf[Option[?]].isEmpty)
      if !isNull then
        out.writeNonEscapedAsciiKey(names(i))
        codecs(i).encodeValue(v, out)
      i += 1
    out.writeObjectEnd()

  private def decodeProduct[P](
    in: JsonReader, mirror: Mirror.ProductOf[P],
    names: Array[String], codecs: Array[JsonValueCodec[Any]],
    nullValues: Array[Any]
  ): P =
    val n = names.length
    val results = new Array[Any](n)
    System.arraycopy(nullValues, 0, results, 0, n)
    if !in.isNextToken('}') then
      in.rollbackToken()
      var continue = true
      while continue do
        val keyLen = in.readKeyAsCharBuf()
        var matched = false
        var i = 0
        while i < n && !matched do
          if in.isCharBufEqualsTo(keyLen, names(i)) then
            results(i) = codecs(i).decodeValue(in, nullValues(i))
            matched = true
          i += 1
        if !matched then in.skip()
        continue = in.isNextToken(',')
      if !in.isCurrentToken('}') then in.objectEndOrCommaError()
    mirror.fromProduct(new ArrayProduct(results))

  private def decodeProductConfigured[P](
    in: JsonReader, mirror: Mirror.ProductOf[P],
    names: Array[String], codecs: Array[JsonValueCodec[Any]],
    nullValues: Array[Any],
    hasDefaults: Array[Boolean], defaults: Array[Any],
    isOption: Array[Boolean], useDefaults: Boolean
  ): P =
    val n = names.length
    val results = new Array[Any](n)
    // Initialize with defaults when useDefaults is enabled
    if useDefaults then
      var i = 0
      while i < n do
        if hasDefaults(i) then results(i) = defaults(i)
        else if isOption(i) then results(i) = None
        else results(i) = nullValues(i)
        i += 1
    else
      System.arraycopy(nullValues, 0, results, 0, n)
    if !in.isNextToken('}') then
      in.rollbackToken()
      var continue = true
      while continue do
        val keyLen = in.readKeyAsCharBuf()
        var matched = false
        var i = 0
        while i < n && !matched do
          if in.isCharBufEqualsTo(keyLen, names(i)) then
            results(i) = codecs(i).decodeValue(in, nullValues(i))
            matched = true
          i += 1
        if !matched then in.skip()
        continue = in.isNextToken(',')
      if !in.isCurrentToken('}') then in.objectEndOrCommaError()
    mirror.fromProduct(new ArrayProduct(results))
