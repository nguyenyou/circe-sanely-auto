package io.circe.generic

import io.circe.{Decoder, Encoder}
import io.circe.`export`.Exported
import scala.deriving.Mirror

trait AutoDerivation:
  implicit inline final def deriveDecoder[A](using inline A: Mirror.Of[A]): Exported[Decoder[A]] =
    Exported(sanely.SanelyDecoder.derived[A])
  implicit inline final def deriveEncoder[A](using inline A: Mirror.Of[A]): Exported[Encoder.AsObject[A]] =
    Exported(sanely.SanelyEncoder.derived[A])

object auto extends AutoDerivation
