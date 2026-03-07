package sanely.jsoniter

/** Converts map keys to/from strings for JSON object encoding.
  * Mirrors circe's KeyEncoder/KeyDecoder — maps are always JSON objects with stringified keys.
  */
trait KeyCodec[K]:
  def encode(key: K): String
  def decode(key: String): K

object KeyCodec:

  def apply[K](using k: KeyCodec[K]): KeyCodec[K] = k

  given KeyCodec[String] with
    def encode(key: String): String = key
    def decode(key: String): String = key

  given KeyCodec[Int] with
    def encode(key: Int): String = java.lang.Integer.toString(key)
    def decode(key: String): Int = java.lang.Integer.parseInt(key)

  given KeyCodec[Long] with
    def encode(key: Long): String = java.lang.Long.toString(key)
    def decode(key: String): Long = java.lang.Long.parseLong(key)

  given KeyCodec[Short] with
    def encode(key: Short): String = java.lang.Short.toString(key)
    def decode(key: String): Short = java.lang.Short.parseShort(key)

  given KeyCodec[Byte] with
    def encode(key: Byte): String = java.lang.Byte.toString(key)
    def decode(key: String): Byte = java.lang.Byte.parseByte(key)

  given KeyCodec[Double] with
    def encode(key: Double): String = java.lang.Double.toString(key)
    def decode(key: String): Double = java.lang.Double.parseDouble(key)

  given KeyCodec[Float] with
    def encode(key: Float): String = java.lang.Float.toString(key)
    def decode(key: String): Float = java.lang.Float.parseFloat(key)

  given KeyCodec[Boolean] with
    def encode(key: Boolean): String = java.lang.Boolean.toString(key)
    def decode(key: String): Boolean = java.lang.Boolean.parseBoolean(key)

  given KeyCodec[BigDecimal] with
    def encode(key: BigDecimal): String = key.toString
    def decode(key: String): BigDecimal = BigDecimal(key)

  given KeyCodec[BigInt] with
    def encode(key: BigInt): String = key.toString
    def decode(key: String): BigInt = BigInt(key)

  given KeyCodec[java.util.UUID] with
    def encode(key: java.util.UUID): String = key.toString
    def decode(key: String): java.util.UUID = java.util.UUID.fromString(key)
