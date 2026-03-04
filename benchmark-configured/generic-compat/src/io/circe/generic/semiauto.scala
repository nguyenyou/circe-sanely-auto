package io.circe.generic

import io.circe.{Codec, Decoder, Encoder}
import io.circe.derivation.Configuration
import scala.deriving.Mirror

// Compatibility shim: exposes deriveConfiguredCodec using circe-core's native derivation.
// This allows the configured benchmark to share source between the sanely and generic modules.
object semiauto:
  inline def deriveConfiguredDecoder[A](using inline conf: Configuration, inline m: Mirror.Of[A]): Decoder[A] =
    io.circe.derivation.ConfiguredDecoder.derived[A]
  inline def deriveConfiguredEncoder[A](using inline conf: Configuration, inline m: Mirror.Of[A]): Encoder.AsObject[A] =
    io.circe.derivation.ConfiguredEncoder.derived[A]
  inline def deriveConfiguredCodec[A](using inline conf: Configuration, inline m: Mirror.Of[A]): Codec.AsObject[A] =
    io.circe.derivation.ConfiguredCodec.derived[A]
