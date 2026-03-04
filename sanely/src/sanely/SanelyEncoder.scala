package sanely

import io.circe.{Encoder, Json, JsonObject}
import scala.deriving.Mirror
import scala.compiletime.*
import scala.quoted.*

object SanelyEncoder:

  inline def derived[A](using inline m: Mirror.Of[A]): Encoder.AsObject[A] =
    ${ deriveMacro[A]('m) }

  private def deriveMacro[A: Type](mirror: Expr[Mirror.Of[A]])(using Quotes): Expr[Encoder.AsObject[A]] =
    val helper = new EncoderDerivation[A]
    helper.derive(mirror)

  private class EncoderDerivation[A: Type](using val quotes: Quotes):
    import quotes.reflect.*

    val selfType: TypeRepr = TypeRepr.of[A]

    def derive(mirror: Expr[Mirror.Of[A]]): Expr[Encoder.AsObject[A]] =
      // Wrap in lazy val for recursive self-reference support
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
            '{ $base.add($labelExpr, $typedEnc($product.productElement($idxExpr).asInstanceOf[t])) }

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

      def buildBranch(a: Expr[S], label: String, tpe: Type[?], enc: Expr[Encoder[?]], isSubTrait: Boolean): Expr[JsonObject] =
        tpe match
          case '[t] =>
            if isSubTrait then
              // Sub-trait: use AsObject encoder directly (it already wraps with variant name)
              val typedEnc = enc.asInstanceOf[Expr[Encoder.AsObject[t]]]
              '{ $typedEnc.encodeObject($a.asInstanceOf[t]) }
            else
              // Regular variant: use Encoder[t].apply() — may not be AsObject
              val typedEnc = enc.asInstanceOf[Expr[Encoder[t]]]
              val labelExpr = Expr(label)
              '{ JsonObject.singleton($labelExpr, $typedEnc($a.asInstanceOf[t])) }

      // Detect which variants are themselves sum types (sub-traits)
      val casesWithSubTrait = cases.map { case (label, tpe, enc) =>
        val isSub = tpe match
          case '[t] => Expr.summon[Mirror.SumOf[t]].isDefined
        (label, tpe, enc, isSub)
      }

      '{
        new Encoder.AsObject[S]:
          def encodeObject(a: S): JsonObject =
            val ord = $mirror.ordinal(a)
            ${
              casesWithSubTrait.zipWithIndex.foldRight('{ throw new MatchError(ord) }: Expr[JsonObject]) {
                case (((label, tpe, enc, isSub), idx), elseExpr) =>
                  val idxExpr = Expr(idx)
                  val branch = buildBranch('a, label, tpe, enc, isSub)
                  '{ if ord == $idxExpr then $branch else $elseExpr }
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

    private def resolveOneEncoder[T: Type](
      selfRef: Expr[Encoder.AsObject[A]]
    ): Expr[Encoder[T]] =
      val tpe = TypeRepr.of[T]

      // Direct recursion: T is the same type we're currently deriving
      if tpe =:= selfType then
        return selfRef.asInstanceOf[Expr[Encoder[T]]]

      // Check if T contains the recursive type in its type params (e.g., Option[A], List[A])
      // Must check BEFORE Expr.summonIgnoring to avoid exponential implicit search
      if containsType(tpe, selfType) then
        return constructRecursiveEncoder[T](tpe, selfRef)

      // Safe path: no recursion risk
      val ignoreSymbols = collectIgnoreSymbols("autoEncoder", "deriveEncoder", "importedEncoder", "importedAsObjectEncoder")
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

    private def collectIgnoreSymbols(sanelyAutoMethod: String, genericAutoMethod: String, importedMethods: String*): List[Symbol] =
      val buf = List.newBuilder[Symbol]
      buf += Symbol.requiredModule("sanely.auto").methodMember(sanelyAutoMethod).head
      // io.circe.generic.auto alias
      try
        val genericAuto = Symbol.requiredModule("io.circe.generic.auto")
        genericAuto.methodMember(genericAutoMethod).foreach(buf += _)
      catch case _: Exception => ()
      // circe-core imported* unwrappers
      for method <- importedMethods do
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
      tpe match
        case AppliedType(tycon, List(arg)) if arg =:= selfType =>
          arg.asType match
            case '[a] =>
              val innerEnc = selfRef.asInstanceOf[Expr[Encoder[a]]]
              tycon.typeSymbol.fullName match
                case "scala.Option" =>
                  '{ Encoder.encodeOption[a](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]]
                case s if s.endsWith(".List") =>
                  '{ Encoder.encodeList[a](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]]
                case s if s.endsWith(".Vector") =>
                  '{ Encoder.encodeVector[a](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]]
                case s if s.endsWith(".Set") =>
                  '{ Encoder.encodeSet[a](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]]
                case s if s.endsWith(".Seq") =>
                  '{ Encoder.encodeSeq[a](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]]
                case "cats.data.Chain" =>
                  '{ Encoder.encodeChain[a](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]]
                case "cats.data.NonEmptyList" =>
                  '{ Encoder.encodeNonEmptyList[a](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]]
                case "cats.data.NonEmptyVector" =>
                  '{ Encoder.encodeNonEmptyVector[a](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]]
                case "cats.data.NonEmptySeq" =>
                  '{ Encoder.encodeNonEmptySeq[a](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]]
                case "cats.data.NonEmptyChain" =>
                  '{ Encoder.encodeNonEmptyChain[a](using $innerEnc) }.asInstanceOf[Expr[Encoder[T]]]
                case other =>
                  report.errorAndAbort(s"Cannot derive Encoder for recursive type in container ${other}[${Type.show[a]}]")
        case AppliedType(tycon, List(keyArg, valArg)) if valArg =:= selfType =>
          (keyArg.asType, valArg.asType) match
            case ('[k], '[v]) =>
              val innerEnc = selfRef.asInstanceOf[Expr[Encoder[v]]]
              Expr.summon[io.circe.KeyEncoder[k]] match
                case Some(keyEnc) =>
                  '{ Encoder.encodeMap[k, v](using $keyEnc, $innerEnc) }.asInstanceOf[Expr[Encoder[T]]]
                case None =>
                  report.errorAndAbort(s"Cannot derive Encoder for Map: no KeyEncoder for ${Type.show[k]}")
        case _ =>
          report.errorAndAbort(s"Cannot derive Encoder for recursive type application: ${Type.show[T]}")
