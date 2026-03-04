package sanely

import io.circe.{Codec, Decoder, Encoder}
import scala.deriving.Mirror

object SanelyCodec:

  inline def derived[A](using inline m: Mirror.Of[A]): Codec.AsObject[A] =
    Codec.AsObject.from(SanelyDecoder.derived[A], SanelyEncoder.derived[A])
