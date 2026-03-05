package sanely

import scala.quoted.*

private[sanely] object MacroUtils:
  /** Cheap cache key for TypeRepr. Uses typeSymbol.fullName instead of the
    * expensive .show pretty-printer. Handles applied types recursively.
    */
  def cheapTypeKey(using q: Quotes)(tpe: q.reflect.TypeRepr): String =
    import q.reflect.*
    def go(t: TypeRepr): String =
      val d = t.dealias
      d match
        case ConstantType(c) => s"C:${c.show}"
        case AppliedType(tycon, args) =>
          val base = tycon.typeSymbol.fullName
          args.map(go).mkString(s"$base[", ",", "]")
        case _ if d.termSymbol != Symbol.noSymbol =>
          d.termSymbol.fullName
        case _ if d.typeSymbol != Symbol.noSymbol =>
          d.typeSymbol.fullName
        case _ => d.show
    go(tpe)
