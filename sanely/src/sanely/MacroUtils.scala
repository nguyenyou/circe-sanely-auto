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

  /** Check if a type may have user-provided or library-provided implicit instances
    * that we must discover via summonIgnoring. Returns true if summonIgnoring
    * should NOT be skipped — i.e., the type might have external instances.
    *
    * Safe to skip summonIgnoring only when this returns false: the type has a
    * Mirror, no companion givens, and is not from an external library.
    */
  /** Check if a type may have user-provided or library-provided implicit instances
    * that must be discovered via summonIgnoring.
    *
    * Returns true (must call summonIgnoring) when:
    * - The type's companion has any given declarations
    * - The type is from a known external package (scala.*, java.*, io.circe.*)
    * - The type is from a different source file (may have instances from imports/package objects)
    *
    * Returns false (safe to skip summonIgnoring) only for types defined in the
    * same source file with no companion givens — these are types being co-derived
    * in the same macro expansion.
    *
    * Note: local-scope givens (defined in a method body, not in a companion) for
    * same-file types will NOT be detected. This is an accepted limitation — such
    * instances should be placed in the companion object instead.
    */
  def mayHaveExternalInstances(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean =
    import q.reflect.*
    val sym = tpe.dealias.typeSymbol
    if sym == Symbol.noSymbol then return true
    // Companion has given declarations — may provide codec instances
    val companion = sym.companionModule
    if companion != Symbol.noSymbol && companion.declarations.exists(_.flags.is(Flags.Given)) then
      return true
    // Known external packages — these types have implicit instances from
    // circe's Encoder/Decoder companions or import scope
    val name = sym.fullName
    if name.startsWith("scala.") || name.startsWith("java.") || name.startsWith("io.circe.") then
      return true
    // Different source file — may have instances from imports or package objects
    val symPos = sym.pos
    val selfPos = Symbol.spliceOwner.pos
    (symPos, selfPos) match
      case (Some(sp), Some(mp)) => sp.sourceFile.path != mp.sourceFile.path
      case _ => true
