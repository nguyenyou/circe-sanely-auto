package sanely

import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import scala.deriving.Mirror
import scala.collection.mutable
import scala.compiletime.*
import scala.quoted.*

object SanelyDecoder:

  inline def derived[A](using inline m: Mirror.Of[A]): Decoder[A] =
    ${ deriveMacro[A]('m) }

  private def deriveMacro[A: Type](mirror: Expr[Mirror.Of[A]])(using Quotes): Expr[Decoder[A]] =
    val helper = new DecoderDerivation[A]
    val result = helper.derive(mirror)
    helper.timer.report()
    result

  private class DecoderDerivation[A: Type](using val quotes: Quotes):
    import quotes.reflect.*

    val selfType: TypeRepr = TypeRepr.of[A]
    val timer: MacroTimer = MacroTimer.create(Type.show[A], "Decoder")
    private val exprCache = mutable.Map.empty[String, Expr[?]]

    def derive(mirror: Expr[Mirror.Of[A]]): Expr[Decoder[A]] =
      // Wrap in lazy val for recursive self-reference support
      '{
        lazy val _selfDec: Decoder[A] = ${
          val selfRef: Expr[Decoder[A]] = '{ _selfDec }
          mirror match
            case '{ $m: Mirror.ProductOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
              deriveProduct[A, types, labels](m, selfRef)
            case '{ $m: Mirror.SumOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
              deriveSum[A, types, labels](m, selfRef)
        }
        _selfDec
      }

    private def deriveProduct[P: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.ProductOf[P]],
      selfRef: Expr[Decoder[A]]
    ): Expr[Decoder[P]] =
      val fields = resolveFields[Types, Labels](selfRef)
      val namesExpr = Expr(fields.map(_._1).toArray)
      val decoderExprs = fields.map { case (_, tpe, dec) =>
        tpe match
          case '[t] => '{ ${dec.asInstanceOf[Expr[Decoder[t]]]}.asInstanceOf[Decoder[Any]] }
      }
      val decodersArrayExpr = '{ Array(${Varargs(decoderExprs)}*) }

      '{
        new Decoder[P]:
          private lazy val _decoders = $decodersArrayExpr
          private val _names = $namesExpr
          def apply(c: HCursor): Decoder.Result[P] =
            if !c.value.isObject then Left(DecodingFailure("Expected JSON object for product type", c.history))
            else SanelyRuntime.decodeProductFields(c, $mirror, _names, _decoders)
      }

    private def deriveSum[S: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.SumOf[S]],
      selfRef: Expr[Decoder[A]]
    ): Expr[Decoder[S]] =
      val cases = resolveFields[Types, Labels](selfRef)

      // Detect which variants are themselves sum types (sub-traits).
      // Only flatten when no user-provided decoder exists — a custom Decoder
      // may not handle the sub-dispatching that flattening requires.
      val casesWithSubTrait = cases.map { case (label, tpe, dec) =>
        val isSub = timer.time("subTraitDetect") {
          tpe match
            case '[t] =>
              Expr.summon[Mirror.SumOf[t]].isDefined &&
              Expr.summonIgnoring[Decoder[t]](cachedIgnoreSymbols*).isEmpty
        }
        (label, tpe, dec, isSub)
      }

      // Collect only non-sub-trait labels for key matching
      val directLabels = casesWithSubTrait.collect { case (label, _, _, false) => label }
      val directLabelsExpr = Expr(directLabels)

      val allLabelsExpr = Expr(casesWithSubTrait.map(_._1).toArray)
      val isSubTraitExpr = Expr(casesWithSubTrait.map(_._4).toArray)
      val decoderExprs = casesWithSubTrait.map { case (_, tpe, dec, _) =>
        tpe match
          case '[t] => '{ ${dec.asInstanceOf[Expr[Decoder[t]]]}.asInstanceOf[Decoder[Any]] }
      }
      val decodersArrayExpr = '{ Array(${Varargs(decoderExprs)}*) }

      '{
        new Decoder[S]:
          private val _knownLabels: Set[String] = $directLabelsExpr.toSet
          private val _labels = $allLabelsExpr
          private lazy val _decoders = $decodersArrayExpr
          private val _isSubTrait = $isSubTraitExpr
          def apply(c: HCursor): Decoder.Result[S] =
            c.keys match
              case Some(keys) =>
                val key = keys.find(_knownLabels.contains).getOrElse("")
                SanelyRuntime.decodeSum(c, key, _labels, _decoders, _isSubTrait)
              case None =>
                Left(DecodingFailure("Expected JSON object for sum type", c.history))
      }

    private def resolveFields[Types: Type, Labels: Type](
      selfRef: Expr[Decoder[A]]
    ): List[(String, Type[?], Expr[Decoder[?]])] =
      (Type.of[Types], Type.of[Labels]) match
        case ('[EmptyTuple], '[EmptyTuple]) => Nil
        case ('[t *: ts], '[label *: ls]) =>
          val labelStr = Type.of[label] match
            case '[l] =>
              Type.valueOfConstant[l].getOrElse(
                report.errorAndAbort(s"Expected literal string type")
              ).toString
          val dec = resolveOneDecoder[t](selfRef)
          (labelStr, Type.of[t], dec) :: resolveFields[ts, ls](selfRef)
        case _ => report.errorAndAbort("Mismatched Types and Labels tuple lengths")

    private def resolveOneDecoder[T: Type](
      selfRef: Expr[Decoder[A]]
    ): Expr[Decoder[T]] =
      val tpe = TypeRepr.of[T]

      // Direct recursion: T is the same type we're currently deriving
      if tpe =:= selfType then
        return selfRef.asInstanceOf[Expr[Decoder[T]]]

      // Check if T contains the recursive type in its type params
      if containsType(tpe, selfType) then
        return constructRecursiveDecoder[T](tpe, selfRef)

      // Safe path: no recursion risk — check cache first
      val cacheKey = tpe.dealias.show
      exprCache.get(cacheKey) match
        case Some(cached) =>
          timer.count("cacheHit")
          return cached.asInstanceOf[Expr[Decoder[T]]]
        case None => ()

      tryResolveBuiltinDecoder[T] match
        case Some(dec) =>
          timer.count("builtinHit")
          exprCache(cacheKey) = dec
          return dec
        case None => ()

      val resolved: Expr[Decoder[T]] =
        timer.time("summonIgnoring")(Expr.summonIgnoring[Decoder[T]](cachedIgnoreSymbols*)) match
          case Some(dec) => dec
          case None =>
            timer.time("summonMirror")(Expr.summon[Mirror.Of[T]]) match
              case Some(mirrorExpr) =>
                timer.time("derive") {
                  mirrorExpr match
                    case '{ $m: Mirror.ProductOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                      deriveProduct[T, types, labels](m, selfRef)
                    case '{ $m: Mirror.SumOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                      deriveSum[T, types, labels](m, selfRef)
                }
              case None =>
                report.errorAndAbort(s"Cannot derive Decoder for ${Type.show[T]}: no implicit Decoder and no Mirror available")
      exprCache(cacheKey) = resolved
      resolved

    private def containsType(tpe: TypeRepr, target: TypeRepr): Boolean =
      val dealiased = tpe.dealias
      if dealiased =:= target then true
      else dealiased match
        case AppliedType(_, args) => args.exists(arg => containsType(arg, target))
        case AndType(left, right) => containsType(left, target) || containsType(right, target)
        case OrType(left, right) => containsType(left, target) || containsType(right, target)
        case _ => false

    private def tryResolveBuiltinDecoder[T: Type]: Option[Expr[Decoder[T]]] =
      val tpe = TypeRepr.of[T].dealias
      val result: Option[Expr[Decoder[?]]] =
        if tpe =:= TypeRepr.of[String] then Some('{ Decoder.decodeString })
        else if tpe =:= TypeRepr.of[Int] then Some('{ Decoder.decodeInt })
        else if tpe =:= TypeRepr.of[Long] then Some('{ Decoder.decodeLong })
        else if tpe =:= TypeRepr.of[Double] then Some('{ Decoder.decodeDouble })
        else if tpe =:= TypeRepr.of[Float] then Some('{ Decoder.decodeFloat })
        else if tpe =:= TypeRepr.of[Boolean] then Some('{ Decoder.decodeBoolean })
        else if tpe =:= TypeRepr.of[Short] then Some('{ Decoder.decodeShort })
        else if tpe =:= TypeRepr.of[Byte] then Some('{ Decoder.decodeByte })
        else if tpe =:= TypeRepr.of[BigDecimal] then Some('{ Decoder.decodeBigDecimal })
        else if tpe =:= TypeRepr.of[BigInt] then Some('{ Decoder.decodeBigInt })
        else None
      result.map(_.asInstanceOf[Expr[Decoder[T]]])

    private lazy val cachedIgnoreSymbols: List[Symbol] =
      val buf = List.newBuilder[Symbol]
      buf += Symbol.requiredModule("sanely.auto").methodMember("autoDecoder").head
      // io.circe.generic.auto alias
      try
        val genericAuto = Symbol.requiredModule("io.circe.generic.auto")
        genericAuto.methodMember("deriveDecoder").foreach(buf += _)
      catch case _: Exception => ()
      // circe-core imported* unwrappers
      for method <- List("importedDecoder") do
        try
          val decoderCompanion = Symbol.requiredModule("io.circe.Decoder")
          decoderCompanion.methodMember(method).foreach(buf += _)
        catch case _: Exception => ()
        try
          val encoderCompanion = Symbol.requiredModule("io.circe.Encoder")
          encoderCompanion.methodMember(method).foreach(buf += _)
        catch case _: Exception => ()
      buf.result()

    private def constructRecursiveDecoder[T: Type](
      tpe: TypeRepr,
      selfRef: Expr[Decoder[A]]
    ): Expr[Decoder[T]] =
      // Try summoning a user-provided decoder first — handles custom generic containers
      // (e.g. CustomEnum[Self]) that have their own polymorphic givens.
      // We use a targeted ignore list to avoid finding circe-core's Decoder.derived.
      def trySummon: Option[Expr[Decoder[T]]] = Expr.summonIgnoring[Decoder[T]](cachedIgnoreSymbols*)

      tpe match
        case AppliedType(tycon, List(arg)) if arg =:= selfType =>
          arg.asType match
            case '[a] =>
              val innerDec = selfRef.asInstanceOf[Expr[Decoder[a]]]
              buildContainerDecoder[T, a](tycon, innerDec) match
                case Some(dec) => dec
                case None => trySummon.getOrElse(
                  report.errorAndAbort(s"Cannot derive Decoder for recursive type in container ${tycon.typeSymbol.fullName}[${Type.show[a]}]"))
        case AppliedType(tycon, List(keyArg, valArg)) if valArg =:= selfType =>
          (keyArg.asType, valArg.asType) match
            case ('[k], '[v]) =>
              val innerDec = selfRef.asInstanceOf[Expr[Decoder[v]]]
              Expr.summon[io.circe.KeyDecoder[k]] match
                case Some(keyDec) =>
                  '{ Decoder.decodeMap[k, v](using $keyDec, $innerDec) }.asInstanceOf[Expr[Decoder[T]]]
                case None =>
                  report.errorAndAbort(s"Cannot derive Decoder for Map: no KeyDecoder for ${Type.show[k]}")
            case _ => report.errorAndAbort(s"Unexpected type pattern in Map recursive decoder")
        case AppliedType(tycon, List(arg)) if containsType(arg, selfType) =>
          arg.asType match
            case '[a] =>
              val innerDec = constructRecursiveDecoder[a](arg, selfRef)
              buildContainerDecoder[T, a](tycon, innerDec) match
                case Some(dec) => dec
                case None => trySummon.getOrElse(
                  report.errorAndAbort(s"Cannot derive Decoder for recursive type in container ${tycon.typeSymbol.fullName}[${Type.show[a]}]"))
        case AppliedType(tycon, List(keyArg, valArg)) if containsType(valArg, selfType) =>
          (keyArg.asType, valArg.asType) match
            case ('[k], '[v]) =>
              val innerDec = constructRecursiveDecoder[v](valArg, selfRef)
              Expr.summon[io.circe.KeyDecoder[k]] match
                case Some(keyDec) =>
                  '{ Decoder.decodeMap[k, v](using $keyDec, $innerDec) }.asInstanceOf[Expr[Decoder[T]]]
                case None =>
                  report.errorAndAbort(s"Cannot derive Decoder for Map: no KeyDecoder for ${Type.show[k]}")
            case _ => report.errorAndAbort(s"Unexpected type pattern in Map recursive decoder")
        case _ =>
          trySummon.getOrElse(
            report.errorAndAbort(s"Cannot derive Decoder for recursive type application: ${Type.show[T]}"))

    private def buildContainerDecoder[T: Type, A: Type](
      tycon: TypeRepr,
      innerDec: Expr[Decoder[A]]
    ): Option[Expr[Decoder[T]]] =
      tycon.typeSymbol.fullName match
        case "scala.Option" =>
          Some('{ Decoder.decodeOption[A](using $innerDec) }.asInstanceOf[Expr[Decoder[T]]])
        case s if s.endsWith(".List") =>
          Some('{ Decoder.decodeList[A](using $innerDec) }.asInstanceOf[Expr[Decoder[T]]])
        case s if s.endsWith(".Vector") =>
          Some('{ Decoder.decodeVector[A](using $innerDec) }.asInstanceOf[Expr[Decoder[T]]])
        case s if s.endsWith(".Set") =>
          Some('{ Decoder.decodeSet[A](using $innerDec) }.asInstanceOf[Expr[Decoder[T]]])
        case s if s.endsWith(".Seq") =>
          Some('{ Decoder.decodeSeq[A](using $innerDec) }.asInstanceOf[Expr[Decoder[T]]])
        case "cats.data.Chain" =>
          Some('{ Decoder.decodeChain[A](using $innerDec) }.asInstanceOf[Expr[Decoder[T]]])
        case "cats.data.NonEmptyList" =>
          Some('{ Decoder.decodeNonEmptyList[A](using $innerDec) }.asInstanceOf[Expr[Decoder[T]]])
        case "cats.data.NonEmptyVector" =>
          Some('{ Decoder.decodeNonEmptyVector[A](using $innerDec) }.asInstanceOf[Expr[Decoder[T]]])
        case "cats.data.NonEmptySeq" =>
          Some('{ Decoder.decodeNonEmptySeq[A](using $innerDec) }.asInstanceOf[Expr[Decoder[T]]])
        case "cats.data.NonEmptyChain" =>
          Some('{ Decoder.decodeNonEmptyChain[A](using $innerDec) }.asInstanceOf[Expr[Decoder[T]]])
        case _ => None
