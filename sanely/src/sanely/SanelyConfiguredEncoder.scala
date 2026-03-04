package sanely

import io.circe.{Encoder, Json, JsonObject}
import io.circe.derivation.Configuration
import scala.deriving.Mirror
import scala.compiletime.*
import scala.quoted.*

object SanelyConfiguredEncoder:

  inline def derived[A](using inline conf: Configuration)(using inline m: Mirror.Of[A]): Encoder.AsObject[A] =
    ${ deriveMacro[A]('conf, 'm) }

  private def deriveMacro[A: Type](conf: Expr[Configuration], mirror: Expr[Mirror.Of[A]])(using Quotes): Expr[Encoder.AsObject[A]] =
    val helper = new ConfiguredEncoderDerivation[A](conf)
    helper.derive(mirror)

  private class ConfiguredEncoderDerivation[A: Type](conf: Expr[Configuration])(using val quotes: Quotes):
    import quotes.reflect.*

    val selfType: TypeRepr = TypeRepr.of[A]

    def derive(mirror: Expr[Mirror.Of[A]]): Expr[Encoder.AsObject[A]] =
      '{
        lazy val _selfEnc: Encoder.AsObject[A] = ${
          val selfRef: Expr[Encoder.AsObject[A]] = '{ _selfEnc }
          mirror match
            case '{ $m: Mirror.ProductOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
              deriveProduct[A, types, labels](m, selfRef)
            case '{ $m: Mirror.SumOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
              deriveSum[A, types, labels](m, selfRef)
        }
        _selfEnc
      }

    private def deriveProduct[P: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.ProductOf[P]],
      selfRef: Expr[Encoder.AsObject[A]]
    ): Expr[Encoder.AsObject[P]] =
      val fields = resolveFields[Types, Labels](selfRef)

      def addField(base: Expr[JsonObject], product: Expr[Product], label: String, idx: Int, tpe: Type[?], enc: Expr[Encoder[?]]): Expr[JsonObject] =
        tpe match
          case '[t] =>
            val typedEnc = enc.asInstanceOf[Expr[Encoder[t]]]
            val labelExpr = Expr(label)
            val idxExpr = Expr(idx)
            '{ $base.add($conf.transformMemberNames($labelExpr), $typedEnc($product.productElement($idxExpr).asInstanceOf[t])) }

      '{
        new Encoder.AsObject[P]:
          def encodeObject(a: P): JsonObject =
            ${
              val product = '{ a.asInstanceOf[Product] }
              fields.zipWithIndex.foldLeft('{ JsonObject.empty }) { case (acc, ((label, tpe, enc), idx)) =>
                addField(acc, product, label, idx, tpe, enc)
              }
            }
      }

    private def deriveSum[S: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.SumOf[S]],
      selfRef: Expr[Encoder.AsObject[A]]
    ): Expr[Encoder.AsObject[S]] =
      val cases = resolveFields[Types, Labels](selfRef)

      // Only flatten sub-traits when no user-provided encoder exists
      val ignoreSymbols = collectIgnoreSymbols
      val casesWithSubTrait = cases.map { case (label, tpe, enc) =>
        val isSub = tpe match
          case '[t] =>
            Expr.summon[Mirror.SumOf[t]].isDefined &&
            Expr.summonIgnoring[Encoder[t]](ignoreSymbols*).isEmpty
        (label, tpe, enc, isSub)
      }

      def buildBranchExternal(a: Expr[S], label: String, tpe: Type[?], enc: Expr[Encoder[?]], isSubTrait: Boolean): Expr[JsonObject] =
        tpe match
          case '[t] =>
            if isSubTrait then
              val typedEnc = enc.asInstanceOf[Expr[Encoder.AsObject[t]]]
              '{ $typedEnc.encodeObject($a.asInstanceOf[t]) }
            else
              val typedEnc = enc.asInstanceOf[Expr[Encoder[t]]]
              val labelExpr = Expr(label)
              '{ JsonObject.singleton($conf.transformConstructorNames($labelExpr), $typedEnc($a.asInstanceOf[t])) }

      def buildBranchDiscriminator(a: Expr[S], discr: Expr[String], label: String, tpe: Type[?], enc: Expr[Encoder[?]], isSubTrait: Boolean): Expr[JsonObject] =
        tpe match
          case '[t] =>
            if isSubTrait then
              val typedEnc = enc.asInstanceOf[Expr[Encoder.AsObject[t]]]
              '{ $typedEnc.encodeObject($a.asInstanceOf[t]) }
            else
              val typedEnc = enc.asInstanceOf[Expr[Encoder[t]]]
              val labelExpr = Expr(label)
              '{
                val inner = $typedEnc($a.asInstanceOf[t])
                val base = inner.asObject.getOrElse(JsonObject.empty)
                base.add($discr, Json.fromString($conf.transformConstructorNames($labelExpr)))
              }

      '{
        new Encoder.AsObject[S]:
          def encodeObject(a: S): JsonObject =
            val ord = $mirror.ordinal(a)
            ${
              // We build two branches: one for discriminator mode, one for external tagging
              // The runtime check on conf.discriminator selects which path
              val externalMatch = casesWithSubTrait.zipWithIndex.foldRight('{ throw new MatchError(ord) }: Expr[JsonObject]) {
                case (((label, tpe, enc, isSub), idx), elseExpr) =>
                  val idxExpr = Expr(idx)
                  val branch = buildBranchExternal('a, label, tpe, enc, isSub)
                  '{ if ord == $idxExpr then $branch else $elseExpr }
              }

              val discrMatch = casesWithSubTrait.zipWithIndex.foldRight('{ throw new MatchError(ord) }: Expr[JsonObject]) {
                case (((label, tpe, enc, isSub), idx), elseExpr) =>
                  val idxExpr = Expr(idx)
                  val branch = buildBranchDiscriminator('a, '{ $conf.discriminator.get }, label, tpe, enc, isSub)
                  '{ if ord == $idxExpr then $branch else $elseExpr }
              }

              '{
                $conf.discriminator match
                  case None    => $externalMatch
                  case Some(_) => $discrMatch
              }
            }
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

      val ignoreSymbols = collectIgnoreSymbols
      Expr.summonIgnoring[Encoder[T]](ignoreSymbols*) match
        case Some(enc) => enc
        case None =>
          Expr.summon[Mirror.Of[T]] match
            case Some(mirrorExpr) =>
              mirrorExpr match
                case '{ $m: Mirror.ProductOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                  deriveProduct[T, types, labels](m, selfRef)
                case '{ $m: Mirror.SumOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                  deriveSum[T, types, labels](m, selfRef)
            case None =>
              report.errorAndAbort(s"Cannot derive Encoder for ${Type.show[T]}: no implicit Encoder and no Mirror available")

    private def containsType(tpe: TypeRepr, target: TypeRepr): Boolean =
      val dealiased = tpe.dealias
      if dealiased =:= target then true
      else dealiased match
        case AppliedType(_, args) => args.exists(arg => containsType(arg, target))
        case AndType(left, right) => containsType(left, target) || containsType(right, target)
        case OrType(left, right) => containsType(left, target) || containsType(right, target)
        case _ => false

    private def collectIgnoreSymbols: List[Symbol] =
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
      val ignoreSymbols = collectIgnoreSymbols
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
