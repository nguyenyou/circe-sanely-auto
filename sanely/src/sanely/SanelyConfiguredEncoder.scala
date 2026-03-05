package sanely

import io.circe.{Encoder, Json, JsonObject}
import io.circe.derivation.Configuration
import scala.deriving.Mirror
import scala.collection.mutable
import scala.compiletime.*
import scala.quoted.*

object SanelyConfiguredEncoder:

  inline def derived[A](using inline conf: Configuration)(using inline m: Mirror.Of[A]): Encoder.AsObject[A] =
    ${ deriveMacro[A]('conf, 'm) }

  private def deriveMacro[A: Type](conf: Expr[Configuration], mirror: Expr[Mirror.Of[A]])(using Quotes): Expr[Encoder.AsObject[A]] =
    val helper = new ConfiguredEncoderDerivation[A](conf)
    val result = helper.derive(mirror)
    helper.timer.report()
    result

  private class ConfiguredEncoderDerivation[A: Type](conf: Expr[Configuration])(using val quotes: Quotes):
    import quotes.reflect.*

    val selfType: TypeRepr = TypeRepr.of[A]
    val timer: MacroTimer = MacroTimer.create(Type.show[A], "CfgEncoder")
    private val exprCache = mutable.Map.empty[String, Expr[?]]

    def derive(mirror: Expr[Mirror.Of[A]]): Expr[Encoder.AsObject[A]] =
      '{
        lazy val _selfEnc: Encoder.AsObject[A] = ${
          val selfRef: Expr[Encoder.AsObject[A]] = '{ _selfEnc }
          mirror match
            case '{ $m: Mirror.ProductOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
              timer.time("topDerive") { deriveProduct[A, types, labels](m, selfRef) }
            case '{ $m: Mirror.SumOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
              timer.time("topDerive") { deriveSum[A, types, labels](m, selfRef) }
        }
        _selfEnc
      }

    private def deriveProduct[P: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.ProductOf[P]],
      selfRef: Expr[Encoder.AsObject[A]]
    ): Expr[Encoder.AsObject[P]] =
      val fields = resolveFields[Types, Labels](selfRef)
      val labelsExpr = Expr(fields.map(_._1))
      val encoderExprs = fields.map { case (_, tpe, enc) =>
        tpe match
          case '[t] => '{ ${enc.asInstanceOf[Expr[Encoder[t]]]}.asInstanceOf[Encoder[Any]] }
      }
      val encodersArrayExpr = '{ Array(${Varargs(encoderExprs)}*) }

      '{
        val _names = $labelsExpr.map($conf.transformMemberNames).toArray
        new Encoder.AsObject[P]:
          private lazy val _encoders = $encodersArrayExpr
          def encodeObject(a: P): JsonObject =
            SanelyRuntime.encodeProductFields(a.asInstanceOf[Product], _names, _encoders)
      }

    private def deriveSum[S: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.SumOf[S]],
      selfRef: Expr[Encoder.AsObject[A]]
    ): Expr[Encoder.AsObject[S]] =
      val cases = resolveFields[Types, Labels](selfRef)

      // Only flatten sub-traits when no user-provided encoder exists
      val ignoreSymbols = cachedIgnoreSymbols
      val casesWithSubTrait = cases.map { case (label, tpe, enc) =>
        val isSub = timer.time("subTraitDetect") {
          tpe match
            case '[t] =>
              Expr.summon[Mirror.SumOf[t]].isDefined &&
              Expr.summonIgnoring[Encoder[t]](ignoreSymbols*).isEmpty
        }
        (label, tpe, enc, isSub)
      }

      val labelsExpr = Expr(casesWithSubTrait.map(_._1).toArray)
      val isSubTraitExpr = Expr(casesWithSubTrait.map(_._4).toArray)
      val encoderExprs = casesWithSubTrait.map { case (_, tpe, enc, _) =>
        tpe match
          case '[t] => '{ ${enc.asInstanceOf[Expr[Encoder[t]]]}.asInstanceOf[Encoder[Any]] }
      }
      val encodersArrayExpr = '{ Array(${Varargs(encoderExprs)}*) }

      '{
        new Encoder.AsObject[S]:
          private lazy val _encoders = $encodersArrayExpr
          private val _labels = $labelsExpr
          private val _isSubTrait = $isSubTraitExpr
          def encodeObject(a: S): JsonObject =
            val ord = $mirror.ordinal(a)
            SanelyRuntime.encodeSumConfigured(
              a, ord, _labels, _encoders, _isSubTrait,
              $conf.transformConstructorNames, $conf.discriminator)
      }

    private def resolveFields[Types: Type, Labels: Type](
      selfRef: Expr[Encoder.AsObject[A]]
    ): List[(String, Type[?], Expr[Encoder[?]])] =
      (Type.of[Types], Type.of[Labels]) match
        case ('[EmptyTuple], '[EmptyTuple]) => Nil
        case ('[t *: ts], '[label *: ls]) =>
          val labelStr = Type.of[label] match
            case '[l] =>
              Type.valueOfConstant[l].getOrElse(
                report.errorAndAbort(s"Expected literal string type")
              ).toString
          val enc = resolveOneEncoder[t](selfRef)
          (labelStr, Type.of[t], enc) :: resolveFields[ts, ls](selfRef)
        case _ => report.errorAndAbort("Mismatched Types and Labels tuple lengths")

    private def resolveOneEncoder[T: Type](
      selfRef: Expr[Encoder.AsObject[A]]
    ): Expr[Encoder[T]] =
      val tpe = TypeRepr.of[T]

      if tpe =:= selfType then
        return selfRef.asInstanceOf[Expr[Encoder[T]]]

      if containsType(tpe, selfType) then
        return constructRecursiveEncoder[T](tpe, selfRef)

      // Safe path: no recursion risk — check cache first
      val cacheKey = tpe.dealias.show
      exprCache.get(cacheKey) match
        case Some(cached) =>
          timer.count("cacheHit")
          return cached.asInstanceOf[Expr[Encoder[T]]]
        case None => ()

      tryResolveBuiltinEncoder[T] match
        case Some(enc) =>
          timer.count("builtinHit")
          exprCache(cacheKey) = enc
          return enc
        case None => ()

      val resolved: Expr[Encoder[T]] =
        timer.time("summonIgnoring")(Expr.summonIgnoring[Encoder[T]](cachedIgnoreSymbols*)) match
          case Some(enc) => enc
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
                report.errorAndAbort(s"Cannot derive Encoder for ${Type.show[T]}: no implicit Encoder and no Mirror available")
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

    private def tryResolveBuiltinEncoder[T: Type]: Option[Expr[Encoder[T]]] =
      val tpe = TypeRepr.of[T].dealias
      val result: Option[Expr[Encoder[?]]] =
        if tpe =:= TypeRepr.of[String] then Some('{ Encoder.encodeString })
        else if tpe =:= TypeRepr.of[Int] then Some('{ Encoder.encodeInt })
        else if tpe =:= TypeRepr.of[Long] then Some('{ Encoder.encodeLong })
        else if tpe =:= TypeRepr.of[Double] then Some('{ Encoder.encodeDouble })
        else if tpe =:= TypeRepr.of[Float] then Some('{ Encoder.encodeFloat })
        else if tpe =:= TypeRepr.of[Boolean] then Some('{ Encoder.encodeBoolean })
        else if tpe =:= TypeRepr.of[Short] then Some('{ Encoder.encodeShort })
        else if tpe =:= TypeRepr.of[Byte] then Some('{ Encoder.encodeByte })
        else if tpe =:= TypeRepr.of[BigDecimal] then Some('{ Encoder.encodeBigDecimal })
        else if tpe =:= TypeRepr.of[BigInt] then Some('{ Encoder.encodeBigInt })
        else None
      result.map(_.asInstanceOf[Expr[Encoder[T]]])

    private lazy val cachedIgnoreSymbols: List[Symbol] =
      val buf = List.newBuilder[Symbol]
      buf += Symbol.requiredModule("sanely.auto").methodMember("autoEncoder").head
      try
        val genericAuto = Symbol.requiredModule("io.circe.generic.auto")
        genericAuto.methodMember("deriveEncoder").foreach(buf += _)
      catch case _: Exception => ()
      for method <- List("importedEncoder", "importedAsObjectEncoder") do
        try
          val encoderCompanion = Symbol.requiredModule("io.circe.Encoder")
          encoderCompanion.methodMember(method).foreach(buf += _)
        catch case _: Exception => ()
        try
          val decoderCompanion = Symbol.requiredModule("io.circe.Decoder")
          decoderCompanion.methodMember(method).foreach(buf += _)
        catch case _: Exception => ()
      buf.result()

    private def constructRecursiveEncoder[T: Type](
      tpe: TypeRepr,
      selfRef: Expr[Encoder.AsObject[A]]
    ): Expr[Encoder[T]] =
      val ignoreSymbols = cachedIgnoreSymbols
      def trySummon: Option[Expr[Encoder[T]]] = Expr.summonIgnoring[Encoder[T]](ignoreSymbols*)

      tpe match
        case AppliedType(tycon, List(arg)) if arg =:= selfType =>
          arg.asType match
            case '[a] =>
              val innerEnc = selfRef.asInstanceOf[Expr[Encoder[a]]]
              buildContainerEncoder[T, a](tycon, innerEnc) match
                case Some(enc) => enc
                case None => trySummon.getOrElse(
                  report.errorAndAbort(s"Cannot derive Encoder for recursive type in container ${tycon.typeSymbol.fullName}[${Type.show[a]}]"))
        case AppliedType(tycon, List(keyArg, valArg)) if valArg =:= selfType =>
          (keyArg.asType, valArg.asType) match
            case ('[k], '[v]) =>
              val innerEnc = selfRef.asInstanceOf[Expr[Encoder[v]]]
              Expr.summon[io.circe.KeyEncoder[k]] match
                case Some(keyEnc) =>
                  '{ Encoder.encodeMap[k, v](using $keyEnc, $innerEnc) }.asInstanceOf[Expr[Encoder[T]]]
                case None =>
                  report.errorAndAbort(s"Cannot derive Encoder for Map: no KeyEncoder for ${Type.show[k]}")
            case _ => report.errorAndAbort(s"Unexpected type pattern in Map recursive encoder")
        case AppliedType(tycon, List(arg)) if containsType(arg, selfType) =>
          arg.asType match
            case '[a] =>
              val innerEnc = constructRecursiveEncoder[a](arg, selfRef)
              buildContainerEncoder[T, a](tycon, innerEnc) match
                case Some(enc) => enc
                case None => trySummon.getOrElse(
                  report.errorAndAbort(s"Cannot derive Encoder for recursive type in container ${tycon.typeSymbol.fullName}[${Type.show[a]}]"))
        case AppliedType(tycon, List(keyArg, valArg)) if containsType(valArg, selfType) =>
          (keyArg.asType, valArg.asType) match
            case ('[k], '[v]) =>
              val innerEnc = constructRecursiveEncoder[v](valArg, selfRef)
              Expr.summon[io.circe.KeyEncoder[k]] match
                case Some(keyEnc) =>
                  '{ Encoder.encodeMap[k, v](using $keyEnc, $innerEnc) }.asInstanceOf[Expr[Encoder[T]]]
                case None =>
                  report.errorAndAbort(s"Cannot derive Encoder for Map: no KeyEncoder for ${Type.show[k]}")
            case _ => report.errorAndAbort(s"Unexpected type pattern in Map recursive encoder")
        case _ =>
          trySummon.getOrElse(
            report.errorAndAbort(s"Cannot derive Encoder for recursive type application: ${Type.show[T]}"))

    private def buildContainerEncoder[T: Type, A: Type](
      tycon: TypeRepr,
      innerEnc: Expr[Encoder[A]]
    ): Option[Expr[Encoder[T]]] =
      tycon.typeSymbol.fullName match
        case "scala.Option" =>
          Some('{ Encoder.encodeOption[A](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]])
        case s if s.endsWith(".List") =>
          Some('{ Encoder.encodeList[A](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]])
        case s if s.endsWith(".Vector") =>
          Some('{ Encoder.encodeVector[A](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]])
        case s if s.endsWith(".Set") =>
          Some('{ Encoder.encodeSet[A](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]])
        case s if s.endsWith(".Seq") =>
          Some('{ Encoder.encodeSeq[A](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]])
        case "cats.data.Chain" =>
          Some('{ Encoder.encodeChain[A](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]])
        case "cats.data.NonEmptyList" =>
          Some('{ Encoder.encodeNonEmptyList[A](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]])
        case "cats.data.NonEmptyVector" =>
          Some('{ Encoder.encodeNonEmptyVector[A](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]])
        case "cats.data.NonEmptySeq" =>
          Some('{ Encoder.encodeNonEmptySeq[A](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]])
        case "cats.data.NonEmptyChain" =>
          Some('{ Encoder.encodeNonEmptyChain[A](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]])
        case _ => None
