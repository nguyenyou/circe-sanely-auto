package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import scala.deriving.Mirror

/** Auto-derivation of JsonValueCodec instances.
  *
  * Usage: `import sanely.jsoniter.auto.given`
  *
  * All types with a Mirror.Of will automatically get a JsonValueCodec derived
  * in a single macro expansion, avoiding implicit search chains.
  */
object auto:
  inline given autoCodec[A](using inline m: Mirror.Of[A]): JsonValueCodec[A] =
    SanelyJsoniter.derived[A]
