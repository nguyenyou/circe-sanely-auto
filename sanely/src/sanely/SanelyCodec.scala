package sanely

import io.circe.{ACursor, Codec, Decoder, Encoder, HCursor, JsonObject}
import scala.deriving.Mirror

object SanelyCodec:

  inline def derived[A](using inline m: Mirror.Of[A]): Codec.AsObject[A] =
    fromDecoderEncoder(SanelyDecoder.derived[A], SanelyEncoder.derived[A])

  private def fromDecoderEncoder[A](dec: Decoder[A], enc: Encoder.AsObject[A]): Codec.AsObject[A] =
    new Codec.AsObject[A]:
      def apply(c: HCursor): Decoder.Result[A] = dec(c)
      override def decodeAccumulating(c: HCursor): Decoder.AccumulatingResult[A] = dec.decodeAccumulating(c)
      override def tryDecode(c: ACursor): Decoder.Result[A] = dec.tryDecode(c)
      override def tryDecodeAccumulating(c: ACursor): Decoder.AccumulatingResult[A] = dec.tryDecodeAccumulating(c)
      def encodeObject(a: A): JsonObject = enc.encodeObject(a)
