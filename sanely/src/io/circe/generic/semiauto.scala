package io.circe.generic

import io.circe.{Codec, Decoder, Encoder}
import scala.deriving.Mirror

object semiauto:
  inline def deriveDecoder[A](using inline A: Mirror.Of[A]): Decoder[A] =
    sanely.SanelyDecoder.derived[A]
  inline def deriveEncoder[A](using inline A: Mirror.Of[A]): Encoder.AsObject[A] =
    sanely.SanelyEncoder.derived[A]
  inline def deriveCodec[A](using inline A: Mirror.Of[A]): Codec.AsObject[A] =
    sanely.SanelyCodec.derived[A]
