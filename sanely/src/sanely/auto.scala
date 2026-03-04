package sanely

import io.circe.{Encoder, Decoder}
import scala.deriving.Mirror

object auto:
  inline given [A](using inline m: Mirror.Of[A]): Encoder.AsObject[A] = SanelyEncoder.derived[A]
  inline given [A](using inline m: Mirror.Of[A]): Decoder[A] = SanelyDecoder.derived[A]
