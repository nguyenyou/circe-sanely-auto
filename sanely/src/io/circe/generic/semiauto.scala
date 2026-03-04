package io.circe.generic

import io.circe.{Codec, Decoder, Encoder}
import io.circe.derivation.Configuration
import scala.deriving.Mirror

object semiauto:
  inline def deriveDecoder[A](using inline A: Mirror.Of[A]): Decoder[A] =
    sanely.SanelyDecoder.derived[A]
  inline def deriveEncoder[A](using inline A: Mirror.Of[A]): Encoder.AsObject[A] =
    sanely.SanelyEncoder.derived[A]
  inline def deriveCodec[A](using inline A: Mirror.Of[A]): Codec.AsObject[A] =
    sanely.SanelyCodec.derived[A]
  inline def deriveConfiguredDecoder[A](using inline conf: Configuration, inline m: Mirror.Of[A]): Decoder[A] =
    sanely.SanelyConfiguredDecoder.derived[A]
  inline def deriveConfiguredEncoder[A](using inline conf: Configuration, inline m: Mirror.Of[A]): Encoder.AsObject[A] =
    sanely.SanelyConfiguredEncoder.derived[A]
  inline def deriveConfiguredCodec[A](using inline conf: Configuration, inline m: Mirror.Of[A]): Codec.AsObject[A] =
    sanely.SanelyConfiguredCodec.derived[A]
  inline def deriveEnumCodec[A](using inline conf: Configuration, inline m: Mirror.SumOf[A]): Codec[A] =
    sanely.SanelyEnumCodec.derived[A]
