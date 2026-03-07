package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import scala.deriving.Mirror

/** Semi-auto derivation of JsonValueCodec instances.
  *
  * Usage:
  * {{{
  *   import sanely.jsoniter.semiauto.*
  *   case class User(name: String, age: Int)
  *   given JsonValueCodec[User] = deriveJsoniterCodec[User]
  * }}}
  */
object semiauto:
  inline def deriveJsoniterCodec[A](using inline m: Mirror.Of[A]): JsonValueCodec[A] =
    SanelyJsoniter.derived[A]

  inline def deriveJsoniterConfiguredCodec[A](using inline conf: JsoniterConfiguration)(using inline m: Mirror.Of[A]): JsonValueCodec[A] =
    SanelyJsoniterConfigured.derived[A]

  inline def deriveJsoniterEnumCodec[A](using inline m: Mirror.SumOf[A]): JsonValueCodec[A] =
    SanelyJsoniterEnum.derived[A]
