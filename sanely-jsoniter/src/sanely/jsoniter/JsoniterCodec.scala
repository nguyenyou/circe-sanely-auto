package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter, JsonValueCodec}
import scala.deriving.Mirror

/** Wrapper types enabling `derives` syntax for JsonValueCodec derivation.
  *
  * Each wrapper extends JsonValueCodec directly, so `derives JsoniterCodec.WithDefaults`
  * makes `JsonValueCodec[A]` available in implicit scope with zero additional imports.
  *
  * {{{
  * case class User(name: String, age: Int = 25) derives JsoniterCodec.WithDefaults
  *
  * // JsonValueCodec[User] is now available:
  * writeToString(User("Alice"))  // just works
  * }}}
  */
final class JsoniterCodec[A](inner: JsonValueCodec[A]) extends JsonValueCodec[A]:
  def decodeValue(in: JsonReader, default: A): A = inner.decodeValue(in, default)
  def encodeValue(x: A, out: JsonWriter): Unit = inner.encodeValue(x, out)
  def nullValue: A = inner.nullValue

object JsoniterCodec:
  inline def derived[A](using inline m: Mirror.Of[A]): JsoniterCodec[A] =
    new JsoniterCodec[A](SanelyJsoniter.derived[A])

  final class WithDefaults[A](inner: JsonValueCodec[A]) extends JsonValueCodec[A]:
    def decodeValue(in: JsonReader, default: A): A = inner.decodeValue(in, default)
    def encodeValue(x: A, out: JsonWriter): Unit = inner.encodeValue(x, out)
    def nullValue: A = inner.nullValue
  object WithDefaults:
    inline def derived[A](using inline m: Mirror.Of[A]): WithDefaults[A] =
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
      new WithDefaults[A](SanelyJsoniterConfigured.derived[A])

  final class WithDefaultsDropNull[A](inner: JsonValueCodec[A]) extends JsonValueCodec[A]:
    def decodeValue(in: JsonReader, default: A): A = inner.decodeValue(in, default)
    def encodeValue(x: A, out: JsonWriter): Unit = inner.encodeValue(x, out)
    def nullValue: A = inner.nullValue
  object WithDefaultsDropNull:
    inline def derived[A](using inline m: Mirror.Of[A]): WithDefaultsDropNull[A] =
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withDropNullValues
      new WithDefaultsDropNull[A](SanelyJsoniterConfigured.derived[A])

  final class WithSnakeCaseAndDefaults[A](inner: JsonValueCodec[A]) extends JsonValueCodec[A]:
    def decodeValue(in: JsonReader, default: A): A = inner.decodeValue(in, default)
    def encodeValue(x: A, out: JsonWriter): Unit = inner.encodeValue(x, out)
    def nullValue: A = inner.nullValue
  object WithSnakeCaseAndDefaults:
    inline def derived[A](using inline m: Mirror.Of[A]): WithSnakeCaseAndDefaults[A] =
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withSnakeCaseMemberNames
      new WithSnakeCaseAndDefaults[A](SanelyJsoniterConfigured.derived[A])

  final class WithSnakeCaseAndDefaultsDropNull[A](inner: JsonValueCodec[A]) extends JsonValueCodec[A]:
    def decodeValue(in: JsonReader, default: A): A = inner.decodeValue(in, default)
    def encodeValue(x: A, out: JsonWriter): Unit = inner.encodeValue(x, out)
    def nullValue: A = inner.nullValue
  object WithSnakeCaseAndDefaultsDropNull:
    inline def derived[A](using inline m: Mirror.Of[A]): WithSnakeCaseAndDefaultsDropNull[A] =
      given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults.withSnakeCaseMemberNames.withDropNullValues
      new WithSnakeCaseAndDefaultsDropNull[A](SanelyJsoniterConfigured.derived[A])

  final class Enum[A](inner: JsonValueCodec[A]) extends JsonValueCodec[A]:
    def decodeValue(in: JsonReader, default: A): A = inner.decodeValue(in, default)
    def encodeValue(x: A, out: JsonWriter): Unit = inner.encodeValue(x, out)
    def nullValue: A = inner.nullValue
  object Enum:
    inline def derived[A](using inline m: Mirror.SumOf[A]): Enum[A] =
      new Enum[A](SanelyJsoniterEnum.derived[A])

  final class ValueEnum[A](inner: JsonValueCodec[A]) extends JsonValueCodec[A]:
    def decodeValue(in: JsonReader, default: A): A = inner.decodeValue(in, default)
    def encodeValue(x: A, out: JsonWriter): Unit = inner.encodeValue(x, out)
    def nullValue: A = inner.nullValue
  object ValueEnum:
    inline def derived[A](using inline m: Mirror.SumOf[A]): ValueEnum[A] =
      new ValueEnum[A](SanelyJsoniterValueEnum.derived[A])
