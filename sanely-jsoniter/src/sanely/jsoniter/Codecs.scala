package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.*
import scala.reflect.ClassTag

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

  // === Public givens (import Codecs.given for standalone primitive codecs) ===

  given JsonValueCodec[Boolean] = boolean
  given JsonValueCodec[Byte] = byte
  given JsonValueCodec[Short] = short
  given JsonValueCodec[Int] = int
  given JsonValueCodec[Long] = long
  given JsonValueCodec[Float] = float
  given JsonValueCodec[Double] = double
  given JsonValueCodec[String] = string
  given JsonValueCodec[BigDecimal] = bigDecimal
  given JsonValueCodec[BigInt] = bigInt
  given JsonValueCodec[Char] = char

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
        val it = x.iterator
        while it.hasNext do inner.encodeValue(it.next(), out)
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
    private val listCodec = list(inner)
    def encodeValue(x: Seq[T], out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeArrayStart()
        val it = x.iterator
        while it.hasNext do inner.encodeValue(it.next(), out)
        out.writeArrayEnd()
    def decodeValue(in: JsonReader, default: Seq[T]): Seq[T] =
      listCodec.decodeValue(in, if default == null then null else default.toList)

  def indexedSeq[T](inner: JsonValueCodec[T]): JsonValueCodec[IndexedSeq[T]] = new JsonValueCodec[IndexedSeq[T]]:
    val nullValue: IndexedSeq[T] = null
    private val vectorCodec = vector(inner)
    def encodeValue(x: IndexedSeq[T], out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeArrayStart()
        val it = x.iterator
        while it.hasNext do inner.encodeValue(it.next(), out)
        out.writeArrayEnd()
    def decodeValue(in: JsonReader, default: IndexedSeq[T]): IndexedSeq[T] =
      vectorCodec.decodeValue(in, if default == null then null else default.toVector)

  def iterable[T](inner: JsonValueCodec[T]): JsonValueCodec[Iterable[T]] = new JsonValueCodec[Iterable[T]]:
    val nullValue: Iterable[T] = null
    private val listCodec = list(inner)
    def encodeValue(x: Iterable[T], out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeArrayStart()
        val it = x.iterator
        while it.hasNext do inner.encodeValue(it.next(), out)
        out.writeArrayEnd()
    def decodeValue(in: JsonReader, default: Iterable[T]): Iterable[T] =
      listCodec.decodeValue(in, if default == null then null else default.toList)

  def array[T](inner: JsonValueCodec[T])(using ct: ClassTag[T]): JsonValueCodec[Array[T]] = new JsonValueCodec[Array[T]]:
    val nullValue: Array[T] = null
    def encodeValue(x: Array[T], out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeArrayStart()
        var i = 0
        while i < x.length do
          inner.encodeValue(x(i), out)
          i += 1
        out.writeArrayEnd()
    def decodeValue(in: JsonReader, default: Array[T]): Array[T] =
      if !in.isNextToken('[') then
        in.readNullOrTokenError(default, '[')
      else
        val buf = Array.newBuilder[T]
        if !in.isNextToken(']') then
          in.rollbackToken()
          buf += inner.decodeValue(in, inner.nullValue)
          while in.isNextToken(',') do
            buf += inner.decodeValue(in, inner.nullValue)
          if !in.isCurrentToken(']') then in.arrayEndOrCommaError()
        buf.result()

  def set[T](inner: JsonValueCodec[T]): JsonValueCodec[Set[T]] = new JsonValueCodec[Set[T]]:
    val nullValue: Set[T] = null
    def encodeValue(x: Set[T], out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeArrayStart()
        val it = x.iterator
        while it.hasNext do inner.encodeValue(it.next(), out)
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
        val it = x.iterator
        while it.hasNext do
          val (k, v) = it.next()
          out.writeKey(keyCodec.encode(k))
          valueCodec.encodeValue(v, out)
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

  // === Tuples ===

  def tuple1[A](ca: JsonValueCodec[A]): JsonValueCodec[Tuple1[A]] = new JsonValueCodec[Tuple1[A]]:
    val nullValue: Tuple1[A] = null.asInstanceOf[Tuple1[A]]
    def encodeValue(x: Tuple1[A], out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeArrayStart()
        ca.encodeValue(x._1, out)
        out.writeArrayEnd()
    def decodeValue(in: JsonReader, default: Tuple1[A]): Tuple1[A] =
      if !in.isNextToken('[') then in.readNullOrTokenError(default, '[')
      else
        val a = ca.decodeValue(in, ca.nullValue)
        if !in.isNextToken(']') then in.arrayEndOrCommaError()
        Tuple1(a)

  def tuple2[A, B](ca: JsonValueCodec[A], cb: JsonValueCodec[B]): JsonValueCodec[(A, B)] = new JsonValueCodec[(A, B)]:
    val nullValue: (A, B) = null.asInstanceOf[(A, B)]
    def encodeValue(x: (A, B), out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeArrayStart()
        ca.encodeValue(x._1, out)
        cb.encodeValue(x._2, out)
        out.writeArrayEnd()
    def decodeValue(in: JsonReader, default: (A, B)): (A, B) =
      if !in.isNextToken('[') then in.readNullOrTokenError(default, '[')
      else
        val a = ca.decodeValue(in, ca.nullValue)
        if !in.isNextToken(',') then in.arrayEndOrCommaError()
        val b = cb.decodeValue(in, cb.nullValue)
        if !in.isNextToken(']') then in.arrayEndOrCommaError()
        (a, b)

  def tuple3[A, B, C](ca: JsonValueCodec[A], cb: JsonValueCodec[B], cc: JsonValueCodec[C]): JsonValueCodec[(A, B, C)] = new JsonValueCodec[(A, B, C)]:
    val nullValue: (A, B, C) = null.asInstanceOf[(A, B, C)]
    def encodeValue(x: (A, B, C), out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeArrayStart()
        ca.encodeValue(x._1, out)
        cb.encodeValue(x._2, out)
        cc.encodeValue(x._3, out)
        out.writeArrayEnd()
    def decodeValue(in: JsonReader, default: (A, B, C)): (A, B, C) =
      if !in.isNextToken('[') then in.readNullOrTokenError(default, '[')
      else
        val a = ca.decodeValue(in, ca.nullValue)
        if !in.isNextToken(',') then in.arrayEndOrCommaError()
        val b = cb.decodeValue(in, cb.nullValue)
        if !in.isNextToken(',') then in.arrayEndOrCommaError()
        val c = cc.decodeValue(in, cc.nullValue)
        if !in.isNextToken(']') then in.arrayEndOrCommaError()
        (a, b, c)

  def tuple4[A, B, C, D](ca: JsonValueCodec[A], cb: JsonValueCodec[B], cc: JsonValueCodec[C], cd: JsonValueCodec[D]): JsonValueCodec[(A, B, C, D)] = new JsonValueCodec[(A, B, C, D)]:
    val nullValue: (A, B, C, D) = null.asInstanceOf[(A, B, C, D)]
    def encodeValue(x: (A, B, C, D), out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeArrayStart()
        ca.encodeValue(x._1, out); cb.encodeValue(x._2, out)
        cc.encodeValue(x._3, out); cd.encodeValue(x._4, out)
        out.writeArrayEnd()
    def decodeValue(in: JsonReader, default: (A, B, C, D)): (A, B, C, D) =
      if !in.isNextToken('[') then in.readNullOrTokenError(default, '[')
      else
        val a = ca.decodeValue(in, ca.nullValue)
        if !in.isNextToken(',') then in.arrayEndOrCommaError()
        val b = cb.decodeValue(in, cb.nullValue)
        if !in.isNextToken(',') then in.arrayEndOrCommaError()
        val c = cc.decodeValue(in, cc.nullValue)
        if !in.isNextToken(',') then in.arrayEndOrCommaError()
        val d = cd.decodeValue(in, cd.nullValue)
        if !in.isNextToken(']') then in.arrayEndOrCommaError()
        (a, b, c, d)

  def tuple5[A, B, C, D, E](ca: JsonValueCodec[A], cb: JsonValueCodec[B], cc: JsonValueCodec[C], cd: JsonValueCodec[D], ce: JsonValueCodec[E]): JsonValueCodec[(A, B, C, D, E)] = new JsonValueCodec[(A, B, C, D, E)]:
    val nullValue: (A, B, C, D, E) = null.asInstanceOf[(A, B, C, D, E)]
    def encodeValue(x: (A, B, C, D, E), out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        out.writeArrayStart()
        ca.encodeValue(x._1, out); cb.encodeValue(x._2, out)
        cc.encodeValue(x._3, out); cd.encodeValue(x._4, out)
        ce.encodeValue(x._5, out)
        out.writeArrayEnd()
    def decodeValue(in: JsonReader, default: (A, B, C, D, E)): (A, B, C, D, E) =
      if !in.isNextToken('[') then in.readNullOrTokenError(default, '[')
      else
        val a = ca.decodeValue(in, ca.nullValue)
        if !in.isNextToken(',') then in.arrayEndOrCommaError()
        val b = cb.decodeValue(in, cb.nullValue)
        if !in.isNextToken(',') then in.arrayEndOrCommaError()
        val c = cc.decodeValue(in, cc.nullValue)
        if !in.isNextToken(',') then in.arrayEndOrCommaError()
        val d = cd.decodeValue(in, cd.nullValue)
        if !in.isNextToken(',') then in.arrayEndOrCommaError()
        val e = ce.decodeValue(in, ce.nullValue)
        if !in.isNextToken(']') then in.arrayEndOrCommaError()
        (a, b, c, d, e)

  /** Generic tuple codec for arities 6-22. Uses productElement/Tuple.fromArray at runtime. */
  def tupleGeneric(codecs: Array[JsonValueCodec[Any]]): JsonValueCodec[Any] = new JsonValueCodec[Any]:
    val nullValue: Any = null
    def encodeValue(x: Any, out: JsonWriter): Unit =
      if x == null then out.writeNull()
      else
        val p = x.asInstanceOf[Product]
        out.writeArrayStart()
        var i = 0
        while i < codecs.length do
          codecs(i).encodeValue(p.productElement(i), out)
          i += 1
        out.writeArrayEnd()
    def decodeValue(in: JsonReader, default: Any): Any =
      if !in.isNextToken('[') then in.readNullOrTokenError(default, '[')
      else
        val arr = new Array[Any](codecs.length)
        arr(0) = codecs(0).decodeValue(in, codecs(0).nullValue)
        var i = 1
        while i < codecs.length do
          if !in.isNextToken(',') then in.arrayEndOrCommaError()
          arr(i) = codecs(i).decodeValue(in, codecs(i).nullValue)
          i += 1
        if !in.isNextToken(']') then in.arrayEndOrCommaError()
        Tuple.fromArray(arr)

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
        val it = x.iterator
        while it.hasNext do
          val (k, v) = it.next()
          out.writeKey(k)
          inner.encodeValue(v, out)
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
