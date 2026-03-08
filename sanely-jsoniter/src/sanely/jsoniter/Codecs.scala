package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.*

/** Built-in JsonValueCodec instances for primitive and common types.
  * Used internally by the macro — not intended for direct user consumption.
  */
object Codecs:

  // === Primitives ===

  val boolean: JsonValueCodec[Boolean] = new JsonValueCodec[Boolean]:
    val nullValue: Boolean = false
    def encodeValue(x: Boolean, out: JsonWriter): Unit = out.writeVal(x)
    def decodeValue(in: JsonReader, default: Boolean): Boolean = in.readBoolean()

  val byte: JsonValueCodec[Byte] = new JsonValueCodec[Byte]:
    val nullValue: Byte = 0
    def encodeValue(x: Byte, out: JsonWriter): Unit = out.writeVal(x.toInt)
    def decodeValue(in: JsonReader, default: Byte): Byte = in.readInt().toByte

  val short: JsonValueCodec[Short] = new JsonValueCodec[Short]:
    val nullValue: Short = 0
    def encodeValue(x: Short, out: JsonWriter): Unit = out.writeVal(x.toInt)
    def decodeValue(in: JsonReader, default: Short): Short = in.readInt().toShort

  val int: JsonValueCodec[Int] = new JsonValueCodec[Int]:
    val nullValue: Int = 0
    def encodeValue(x: Int, out: JsonWriter): Unit = out.writeVal(x)
    def decodeValue(in: JsonReader, default: Int): Int = in.readInt()

  val long: JsonValueCodec[Long] = new JsonValueCodec[Long]:
    val nullValue: Long = 0L
    def encodeValue(x: Long, out: JsonWriter): Unit = out.writeVal(x)
    def decodeValue(in: JsonReader, default: Long): Long = in.readLong()

  val float: JsonValueCodec[Float] = new JsonValueCodec[Float]:
    val nullValue: Float = 0.0f
    def encodeValue(x: Float, out: JsonWriter): Unit = out.writeVal(x)
    def decodeValue(in: JsonReader, default: Float): Float = in.readFloat()

  val double: JsonValueCodec[Double] = new JsonValueCodec[Double]:
    val nullValue: Double = 0.0
    def encodeValue(x: Double, out: JsonWriter): Unit = out.writeVal(x)
    def decodeValue(in: JsonReader, default: Double): Double = in.readDouble()

  val string: JsonValueCodec[String] = new JsonValueCodec[String]:
    val nullValue: String = null
    def encodeValue(x: String, out: JsonWriter): Unit =
      if x == null then out.writeNull() else out.writeVal(x)
    def decodeValue(in: JsonReader, default: String): String = in.readString(default)

  val bigDecimal: JsonValueCodec[BigDecimal] = new JsonValueCodec[BigDecimal]:
    val nullValue: BigDecimal = null
    def encodeValue(x: BigDecimal, out: JsonWriter): Unit =
      if x == null then out.writeNull() else out.writeVal(x)
    def decodeValue(in: JsonReader, default: BigDecimal): BigDecimal = in.readBigDecimal(default)

  val bigInt: JsonValueCodec[BigInt] = new JsonValueCodec[BigInt]:
    val nullValue: BigInt = null
    def encodeValue(x: BigInt, out: JsonWriter): Unit =
      if x == null then out.writeNull() else out.writeVal(x)
    def decodeValue(in: JsonReader, default: BigInt): BigInt = in.readBigInt(default)

  val char: JsonValueCodec[Char] = new JsonValueCodec[Char]:
    val nullValue: Char = 0
    def encodeValue(x: Char, out: JsonWriter): Unit = out.writeVal(x.toString)
    def decodeValue(in: JsonReader, default: Char): Char =
      val s = in.readString(null)
      if s == null || s.isEmpty then default else s.charAt(0)

  // === Containers ===

  def option[T](inner: JsonValueCodec[T]): JsonValueCodec[Option[T]] = new JsonValueCodec[Option[T]]:
    val nullValue: Option[T] = None
    def encodeValue(x: Option[T], out: JsonWriter): Unit = x match
      case Some(v) => inner.encodeValue(v, out)
      case None => out.writeNull()
    def decodeValue(in: JsonReader, default: Option[T]): Option[T] =
      if in.isNextToken('n') then
        in.readNullOrError(default, "expected value or null")
      else
        in.rollbackToken()
        Some(inner.decodeValue(in, inner.nullValue))

  def list[T](inner: JsonValueCodec[T]): JsonValueCodec[List[T]] = new JsonValueCodec[List[T]]:
    val nullValue: List[T] = Nil
    def encodeValue(x: List[T], out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeArrayStart()
        var cur = x
        while cur.nonEmpty do
          inner.encodeValue(cur.head, out)
          cur = cur.tail
        out.writeArrayEnd()
    def decodeValue(in: JsonReader, default: List[T]): List[T] =
      if !in.isNextToken('[') then
        in.readNullOrTokenError(default, '[')
      else
        val buf = List.newBuilder[T]
        if !in.isNextToken(']') then
          in.rollbackToken()
          buf += inner.decodeValue(in, inner.nullValue)
          while in.isNextToken(',') do
            buf += inner.decodeValue(in, inner.nullValue)
          if !in.isCurrentToken(']') then in.arrayEndOrCommaError()
        buf.result()

  def vector[T](inner: JsonValueCodec[T]): JsonValueCodec[Vector[T]] = new JsonValueCodec[Vector[T]]:
    val nullValue: Vector[T] = null
    def encodeValue(x: Vector[T], out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeArrayStart()
        x.foreach(e => inner.encodeValue(e, out))
        out.writeArrayEnd()
    def decodeValue(in: JsonReader, default: Vector[T]): Vector[T] =
      if !in.isNextToken('[') then
        in.readNullOrTokenError(default, '[')
      else
        val buf = Vector.newBuilder[T]
        if !in.isNextToken(']') then
          in.rollbackToken()
          buf += inner.decodeValue(in, inner.nullValue)
          while in.isNextToken(',') do
            buf += inner.decodeValue(in, inner.nullValue)
          if !in.isCurrentToken(']') then in.arrayEndOrCommaError()
        buf.result()

  def seq[T](inner: JsonValueCodec[T]): JsonValueCodec[Seq[T]] = new JsonValueCodec[Seq[T]]:
    val nullValue: Seq[T] = null
    def encodeValue(x: Seq[T], out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeArrayStart()
        x.foreach(e => inner.encodeValue(e, out))
        out.writeArrayEnd()
    def decodeValue(in: JsonReader, default: Seq[T]): Seq[T] =
      list(inner).decodeValue(in, if default == null then null else default.toList)

  def indexedSeq[T](inner: JsonValueCodec[T]): JsonValueCodec[IndexedSeq[T]] = new JsonValueCodec[IndexedSeq[T]]:
    val nullValue: IndexedSeq[T] = null
    def encodeValue(x: IndexedSeq[T], out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeArrayStart()
        x.foreach(e => inner.encodeValue(e, out))
        out.writeArrayEnd()
    def decodeValue(in: JsonReader, default: IndexedSeq[T]): IndexedSeq[T] =
      vector(inner).decodeValue(in, if default == null then null else default.toVector)

  def set[T](inner: JsonValueCodec[T]): JsonValueCodec[Set[T]] = new JsonValueCodec[Set[T]]:
    val nullValue: Set[T] = null
    def encodeValue(x: Set[T], out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeArrayStart()
        x.foreach(e => inner.encodeValue(e, out))
        out.writeArrayEnd()
    def decodeValue(in: JsonReader, default: Set[T]): Set[T] =
      if !in.isNextToken('[') then
        in.readNullOrTokenError(default, '[')
      else
        val buf = Set.newBuilder[T]
        if !in.isNextToken(']') then
          in.rollbackToken()
          buf += inner.decodeValue(in, inner.nullValue)
          while in.isNextToken(',') do
            buf += inner.decodeValue(in, inner.nullValue)
          if !in.isCurrentToken(']') then in.arrayEndOrCommaError()
        buf.result()

  def either[L, R](leftCodec: JsonValueCodec[L], rightCodec: JsonValueCodec[R]): JsonValueCodec[Either[L, R]] =
    new JsonValueCodec[Either[L, R]]:
      val nullValue: Either[L, R] = null
      def encodeValue(x: Either[L, R], out: JsonWriter): Unit =
        if x == null then out.writeNull()
        else
          out.writeObjectStart()
          x match
            case Left(v) =>
              out.writeNonEscapedAsciiKey("Left")
              leftCodec.encodeValue(v, out)
            case Right(v) =>
              out.writeNonEscapedAsciiKey("Right")
              rightCodec.encodeValue(v, out)
          out.writeObjectEnd()
      def decodeValue(in: JsonReader, default: Either[L, R]): Either[L, R] =
        if !in.isNextToken('{') then
          in.readNullOrTokenError(default, '{')
        else
          val key = in.readKeyAsString()
          val result =
            if key == "Left" then Left(leftCodec.decodeValue(in, leftCodec.nullValue))
            else if key == "Right" then Right(rightCodec.decodeValue(in, rightCodec.nullValue))
            else
              in.decodeError(s"Expected 'Left' or 'Right' key for Either, got: $key")
              default
          if !in.isNextToken('}') then in.objectEndOrCommaError()
          result

  def map[K, V](keyCodec: KeyCodec[K], valueCodec: JsonValueCodec[V]): JsonValueCodec[Map[K, V]] = new JsonValueCodec[Map[K, V]]:
    val nullValue: Map[K, V] = null
    def encodeValue(x: Map[K, V], out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeObjectStart()
        x.foreach { (k, v) =>
          out.writeKey(keyCodec.encode(k))
          valueCodec.encodeValue(v, out)
        }
        out.writeObjectEnd()
    def decodeValue(in: JsonReader, default: Map[K, V]): Map[K, V] =
      if !in.isNextToken('{') then
        in.readNullOrTokenError(default, '{')
      else
        val buf = Map.newBuilder[K, V]
        if !in.isNextToken('}') then
          in.rollbackToken()
          buf += (keyCodec.decode(in.readKeyAsString()) -> valueCodec.decodeValue(in, valueCodec.nullValue))
          while in.isNextToken(',') do
            buf += (keyCodec.decode(in.readKeyAsString()) -> valueCodec.decodeValue(in, valueCodec.nullValue))
          if !in.isCurrentToken('}') then in.objectEndOrCommaError()
        buf.result()

  // === Value enums ===

  def stringValueEnum[E](values: Array[E], toValue: E => String): JsonValueCodec[E] =
    val valueToEnum = values.map(e => toValue(e) -> e).toMap
    val typeName = values.headOption.map(_.getClass.getSimpleName.stripSuffix("$")).getOrElse("?")
    new JsonValueCodec[E]:
      val nullValue: E = null.asInstanceOf[E]
      def encodeValue(x: E, out: JsonWriter): Unit =
        if (x: Any) == null then out.writeNull() else out.writeVal(toValue(x))
      def decodeValue(in: JsonReader, default: E): E =
        val s = in.readString(null)
        if s == null then in.decodeError(s"expected string for $typeName")
        valueToEnum.getOrElse(s, in.decodeError(s"$typeName does not contain value: $s"))

  def intValueEnum[E](values: Array[E], toValue: E => Int): JsonValueCodec[E] =
    val valueToEnum = values.map(e => toValue(e) -> e).toMap
    val typeName = values.headOption.map(_.getClass.getSimpleName.stripSuffix("$")).getOrElse("?")
    new JsonValueCodec[E]:
      val nullValue: E = null.asInstanceOf[E]
      def encodeValue(x: E, out: JsonWriter): Unit =
        if (x: Any) == null then out.writeNull() else out.writeVal(toValue(x))
      def decodeValue(in: JsonReader, default: E): E =
        val v = in.readInt()
        valueToEnum.getOrElse(v, in.decodeError(s"$typeName does not contain value: $v"))

  // === Maps ===

  def stringMap[V](inner: JsonValueCodec[V]): JsonValueCodec[Map[String, V]] = new JsonValueCodec[Map[String, V]]:
    val nullValue: Map[String, V] = null
    def encodeValue(x: Map[String, V], out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeObjectStart()
        x.foreach { (k, v) =>
          out.writeKey(k)
          inner.encodeValue(v, out)
        }
        out.writeObjectEnd()
    def decodeValue(in: JsonReader, default: Map[String, V]): Map[String, V] =
      if !in.isNextToken('{') then
        in.readNullOrTokenError(default, '{')
      else
        val buf = Map.newBuilder[String, V]
        if !in.isNextToken('}') then
          in.rollbackToken()
          buf += (in.readKeyAsString() -> inner.decodeValue(in, inner.nullValue))
          while in.isNextToken(',') do
            buf += (in.readKeyAsString() -> inner.decodeValue(in, inner.nullValue))
          if !in.isCurrentToken('}') then in.objectEndOrCommaError()
        buf.result()
