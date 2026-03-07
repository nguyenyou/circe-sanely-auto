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
