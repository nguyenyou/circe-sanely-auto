package sanely.jsoniter.configured

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sanely.jsoniter.{JsoniterConfiguration, SanelyJsoniterConfigured}
import scala.deriving.Mirror

object auto:
  inline given autoConfiguredCodec[A](using inline conf: JsoniterConfiguration)(using inline m: Mirror.Of[A]): JsonValueCodec[A] =
    SanelyJsoniterConfigured.derived[A]
