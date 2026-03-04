package sanely

import io.circe.{Codec, Decoder, Encoder}
import io.circe.derivation.Configuration
import scala.deriving.Mirror

object SanelyConfiguredCodec:

  inline def derived[A](using inline conf: Configuration)(using inline m: Mirror.Of[A]): Codec.AsObject[A] =
    Codec.AsObject.from(SanelyConfiguredDecoder.derived[A], SanelyConfiguredEncoder.derived[A])
