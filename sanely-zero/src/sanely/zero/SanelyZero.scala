package sanely.zero

/** Zero-dependency JSON engine for Scala 3.
  *
  * Owns both the macro (codec generation) and the parser/writer,
  * enabling optimizations impossible when these layers are separate.
  */
object SanelyZero
