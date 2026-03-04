package sanely

import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import io.circe.derivation.Configuration
import scala.deriving.Mirror
import scala.compiletime.*
import scala.quoted.*

object SanelyConfiguredDecoder:

  inline def derived[A](using inline conf: Configuration)(using inline m: Mirror.Of[A]): Decoder[A] =
    ${ deriveMacro[A]('conf, 'm) }

  private def deriveMacro[A: Type](conf: Expr[Configuration], mirror: Expr[Mirror.Of[A]])(using Quotes): Expr[Decoder[A]] =
    val helper = new ConfiguredDecoderDerivation[A](conf)
    helper.derive(mirror)

  private class ConfiguredDecoderDerivation[A: Type](conf: Expr[Configuration])(using val quotes: Quotes):
    import quotes.reflect.*

    val selfType: TypeRepr = TypeRepr.of[A]

    def derive(mirror: Expr[Mirror.Of[A]]): Expr[Decoder[A]] =
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
      val defaults = resolveDefaults[P]

      def buildDecodeChain(c: Expr[HCursor], remaining: List[(String, Type[?], Expr[Decoder[?]], Option[Expr[Any]])], fieldIdx: Int, acc: List[Expr[Any]]): Expr[Decoder.Result[P]] =
        remaining match
          case Nil =>
            val tupleExpr = acc.reverse.foldRight('{ EmptyTuple }: Expr[Tuple]) { (elem, tuple) =>
              '{ $elem *: $tuple }
            }
            '{ Right($mirror.fromProduct($tupleExpr)) }
          case (label, tpe, dec, defaultOpt) :: rest =>
            tpe match
              case '[t] =>
                val typedDec = dec.asInstanceOf[Expr[Decoder[t]]]
                val labelExpr = Expr(label)
                defaultOpt match
                  case Some(defaultExpr) =>
                    val typedDefault = defaultExpr.asInstanceOf[Expr[t]]
                    // For Option types, null should decode normally as None, not trigger default
                    val isOptionType = TypeRepr.of[t].dealias match
                      case AppliedType(tycon, _) => tycon.typeSymbol.fullName == "scala.Option"
                      case _ => false
                    if isOptionType then
                      '{
                        val fieldName = $conf.transformMemberNames($labelExpr)
                        val cursor = $c.downField(fieldName)
                        val result: Decoder.Result[t] =
                          if $conf.useDefaults && cursor.failed then
                            Right($typedDefault)
                          else
                            $typedDec.tryDecode(cursor)
                        result match
                          case Right(v) => ${ buildDecodeChain(c, rest, fieldIdx + 1, 'v :: acc) }
                          case Left(e)  => Left(e)
                      }
                    else
                      '{
                        val fieldName = $conf.transformMemberNames($labelExpr)
                        val cursor = $c.downField(fieldName)
                        val result: Decoder.Result[t] =
                          if $conf.useDefaults && (cursor.failed || cursor.focus.exists(_.isNull)) then
                            Right($typedDefault)
                          else
                            $typedDec.tryDecode(cursor)
                        result match
                          case Right(v) => ${ buildDecodeChain(c, rest, fieldIdx + 1, 'v :: acc) }
                          case Left(e)  => Left(e)
                      }
                  case None =>
                    '{
                      $typedDec.tryDecode($c.downField($conf.transformMemberNames($labelExpr))) match
                        case Right(v) => ${ buildDecodeChain(c, rest, fieldIdx + 1, 'v :: acc) }
                        case Left(e)  => Left(e)
                    }

      val fieldsWithDefaults = fields.zipWithIndex.map { case ((label, tpe, dec), idx) =>
        (label, tpe, dec, defaults.lift(idx).flatten)
      }

      // Collect field labels for strict decoding
      val fieldLabels = fields.map(_._1)
      val fieldLabelsExpr = Expr(fieldLabels)

      '{
        new Decoder[P]:
          def apply(c: HCursor): Decoder.Result[P] =
            if !c.value.isObject then Left(DecodingFailure("Expected JSON object for product type", c.history))
            else
              // Strict decoding check
              if $conf.strictDecoding then
                val expectedKeys = $fieldLabelsExpr.map($conf.transformMemberNames).toSet
                c.keys match
                  case Some(keys) =>
                    val unexpected = keys.filterNot(expectedKeys.contains).toList
                    if unexpected.nonEmpty then
                      return Left(DecodingFailure(s"Unexpected field(s): ${unexpected.mkString(", ")}", c.history))
                  case None => ()
              ${ buildDecodeChain('c, fieldsWithDefaults, 0, Nil) }
      }

    private def deriveSum[S: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.SumOf[S]],
      selfRef: Expr[Decoder[A]]
    ): Expr[Decoder[S]] =
      val cases = resolveFields[Types, Labels](selfRef)

      // Only flatten sub-traits when no user-provided decoder exists
      val ignoreSymbols = cachedIgnoreSymbols
      val casesWithSubTrait = cases.map { case (label, tpe, dec) =>
        val isSub = tpe match
          case '[t] =>
            Expr.summon[Mirror.SumOf[t]].isDefined &&
            Expr.summonIgnoring[Decoder[t]](ignoreSymbols*).isEmpty
        (label, tpe, dec, isSub)
      }

      // --- External tagging path (discriminator = None) ---
      def buildMatchExternal(c: Expr[HCursor], key: Expr[String]): Expr[Decoder.Result[S]] =
        casesWithSubTrait.foldRight('{ Left(DecodingFailure("Unknown variant", $c.history)) }: Expr[Decoder.Result[S]]) {
          case ((label, tpe, dec, true), elseExpr) =>
            tpe match
              case '[t] =>
                val typedDec = dec.asInstanceOf[Expr[Decoder[t]]]
                '{ $typedDec.tryDecode($c) match
                    case Right(v) => Right(v.asInstanceOf[S])
                    case Left(_)  => $elseExpr
                }
          case ((label, tpe, dec, false), elseExpr) =>
            tpe match
              case '[t] =>
                val typedDec = dec.asInstanceOf[Expr[Decoder[t]]]
                val labelExpr = Expr(label)
                '{ if $key == $conf.transformConstructorNames($labelExpr) then $typedDec.tryDecode($c.downField($key)).asInstanceOf[Decoder.Result[S]] else $elseExpr }
        }

      // --- Discriminator path (discriminator = Some(d)) ---
      def buildMatchDiscriminator(c: Expr[HCursor], discriminatorValue: Expr[String]): Expr[Decoder.Result[S]] =
        casesWithSubTrait.foldRight('{ Left(DecodingFailure("Unknown variant: " + $discriminatorValue, $c.history)) }: Expr[Decoder.Result[S]]) {
          case ((label, tpe, dec, true), elseExpr) =>
            tpe match
              case '[t] =>
                val typedDec = dec.asInstanceOf[Expr[Decoder[t]]]
                '{ $typedDec.tryDecode($c) match
                    case Right(v) => Right(v.asInstanceOf[S])
                    case Left(_)  => $elseExpr
                }
          case ((label, tpe, dec, false), elseExpr) =>
            tpe match
              case '[t] =>
                val typedDec = dec.asInstanceOf[Expr[Decoder[t]]]
                val labelExpr = Expr(label)
                '{ if $discriminatorValue == $conf.transformConstructorNames($labelExpr) then $typedDec.tryDecode($c).asInstanceOf[Decoder.Result[S]] else $elseExpr }
        }

      val directLabels = casesWithSubTrait.collect { case (label, _, _, false) => label }
      val directLabelsExpr = Expr(directLabels)

      '{
        new Decoder[S]:
          def apply(c: HCursor): Decoder.Result[S] =
            $conf.discriminator match
              case Some(discField) =>
                // Discriminator mode: read discriminator field value
                c.downField(discField).as[String] match
                  case Right(typeName) =>
                    ${ buildMatchDiscriminator('c, 'typeName) }
                  case Left(e) =>
                    Left(DecodingFailure(s"Missing discriminator field '$$discField'", c.history))
              case None =>
                // External tagging mode
                c.keys match
                  case Some(keys) =>
                    val keysList = keys.toList
                    if $conf.strictDecoding && keysList.size > 1 then
                      Left(DecodingFailure("Expected single-key JSON object for sum type", c.history))
                    else
                      val transformedLabels = $directLabelsExpr.map(l => $conf.transformConstructorNames(l))
                      val key = keysList.find(transformedLabels.toSet.contains).getOrElse("")
                      ${ buildMatchExternal('c, 'key) }
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

    private def resolveDefaults[P: Type]: List[Option[Expr[Any]]] =
      val tpe = TypeRepr.of[P]
      val sym = tpe.typeSymbol
      val companionOpt =
        if sym.isClassDef then
          try Some(sym.companionModule) catch case _: Exception => None
        else None

      companionOpt match
        case None => Nil
        case Some(companion) =>
          // Get the number of fields from the primary constructor
          val primaryCtor = sym.primaryConstructor
          // Skip type parameter lists, only count value parameters
          val paramCount = primaryCtor.paramSymss
            .find(_.headOption.exists(s => !s.isTypeParam))
            .map(_.size)
            .getOrElse(0)
          (1 to paramCount).toList.map { idx =>
            val methodName = s"$$lessinit$$greater$$default$$$idx"
            val found = companion.declaredMethod(methodName).headOption
            found.map { method =>
              val select = Ref(companion).select(method)
              // Apply type args if the method has type parameters
              val applied =
                if method.paramSymss.exists(_.exists(_.isTypeParam)) then
                  tpe match
                    case AppliedType(_, args) if args.nonEmpty =>
                      select.appliedToTypes(args)
                    case _ => select
                else select
              applied.asExpr
            }
          }

    private def resolveOneDecoder[T: Type](
      selfRef: Expr[Decoder[A]]
    ): Expr[Decoder[T]] =
      val tpe = TypeRepr.of[T]

      if tpe =:= selfType then
        return selfRef.asInstanceOf[Expr[Decoder[T]]]

      if containsType(tpe, selfType) then
        return constructRecursiveDecoder[T](tpe, selfRef)
      val ignoreSymbols = cachedIgnoreSymbols
      Expr.summonIgnoring[Decoder[T]](ignoreSymbols*) match
        case Some(dec) => dec
        case None =>
          Expr.summon[Mirror.Of[T]] match
            case Some(mirrorExpr) =>
              mirrorExpr match
                case '{ $m: Mirror.ProductOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                  deriveProduct[T, types, labels](m, selfRef)
                case '{ $m: Mirror.SumOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                  deriveSum[T, types, labels](m, selfRef)
            case None =>
              report.errorAndAbort(s"Cannot derive Decoder for ${Type.show[T]}: no implicit Decoder and no Mirror available")

    private def containsType(tpe: TypeRepr, target: TypeRepr): Boolean =
      val dealiased = tpe.dealias
      if dealiased =:= target then true
      else dealiased match
        case AppliedType(_, args) => args.exists(arg => containsType(arg, target))
        case AndType(left, right) => containsType(left, target) || containsType(right, target)
        case OrType(left, right) => containsType(left, target) || containsType(right, target)
        case _ => false

    private lazy val cachedIgnoreSymbols: List[Symbol] =
      val buf = List.newBuilder[Symbol]
      buf += Symbol.requiredModule("sanely.auto").methodMember("autoDecoder").head
      try
        val genericAuto = Symbol.requiredModule("io.circe.generic.auto")
        genericAuto.methodMember("deriveDecoder").foreach(buf += _)
      catch case _: Exception => ()
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
      val ignoreSymbols = cachedIgnoreSymbols
      def trySummon: Option[Expr[Decoder[T]]] = Expr.summonIgnoring[Decoder[T]](ignoreSymbols*)

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
