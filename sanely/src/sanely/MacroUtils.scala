package sanely

import scala.collection.mutable
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

  /** Check if a type is recursive — any field type transitively references selfType.
    * Conservative: returns true if in doubt, so we never miss a recursive type.
    * Used to decide whether to emit `lazy val` (recursive) or direct expression (non-recursive).
    */
  def isRecursiveType(using q: Quotes)(selfType: q.reflect.TypeRepr): Boolean =
    import q.reflect.*
    val selfSym = selfType.typeSymbol
    val visited = mutable.Set[Symbol]()

    def fieldTypesOf(sym: Symbol): List[TypeRepr] =
      if !sym.isClassDef then Nil
      else
        val tpe = sym.typeRef
        sym.caseFields.map(f => tpe.memberType(f))

    def check(tpe: TypeRepr): Boolean =
      val d = tpe.dealias
      if d =:= selfType then true
      else d match
        case AppliedType(_, args) => args.exists(check)
        case AndType(l, r) => check(l) || check(r)
        case OrType(l, r) => check(l) || check(r)
        case _ =>
          val sym = d.typeSymbol
          if sym == selfSym || visited.contains(sym) then false
          else
            visited += sym
            if sym.flags.is(Flags.Sealed) then
              sym.children.exists(child => fieldTypesOf(child).exists(check))
            else if sym.isClassDef then
              fieldTypesOf(sym).exists(check)
            else false

    if selfSym.flags.is(Flags.Sealed) then
      selfSym.children.exists(child => fieldTypesOf(child).exists(check))
    else
      fieldTypesOf(selfSym).exists(check)
