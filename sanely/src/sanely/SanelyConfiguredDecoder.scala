package sanely

import io.circe.{ACursor, Decoder, DecodingFailure, HCursor, Json}
import io.circe.derivation.Configuration
import scala.deriving.Mirror
import scala.collection.mutable
import scala.compiletime.*
import scala.quoted.*

object SanelyConfiguredDecoder:

  inline def derived[A](using inline conf: Configuration)(using inline m: Mirror.Of[A]): Decoder[A] =
    ${ deriveMacro[A]('conf, 'm) }

  private def deriveMacro[A: Type](conf: Expr[Configuration], mirror: Expr[Mirror.Of[A]])(using Quotes): Expr[Decoder[A]] =
    val helper = new ConfiguredDecoderDerivation[A](conf)
    val result = helper.derive(mirror)
    helper.timer.report()
    result

  private class ConfiguredDecoderDerivation[A: Type](conf: Expr[Configuration])(using val quotes: Quotes):
    import quotes.reflect.*

    val selfType: TypeRepr = TypeRepr.of[A]
    val timer: MacroTimer = MacroTimer.create(Type.show[A], "CfgDecoder")
    private val exprCache = mutable.Map.empty[String, Expr[?]]
    private val negativeBuiltinCache = mutable.Set.empty[String]
    private val summonedKeys = mutable.Set.empty[String]
    private val constructorNegCache = mutable.Set.empty[String]

    def derive(mirror: Expr[Mirror.Of[A]]): Expr[Decoder[A]] =
      if MacroUtils.isRecursiveType(selfType) then
        '{
          lazy val _selfDec: Decoder[A] = ${
            val selfRef: Expr[Decoder[A]] = '{ _selfDec }
            timer.time("topDerive") { deriveInner(mirror, selfRef) }
          }
          _selfDec
        }
      else
        timer.time("topDerive") { deriveInner(mirror, '{ null.asInstanceOf[Decoder[A]] }) }

    private def deriveInner(mirror: Expr[Mirror.Of[A]], selfRef: Expr[Decoder[A]]): Expr[Decoder[A]] =
      mirror match
        case '{ $m: Mirror.ProductOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
          deriveProduct[A, types, labels](m, selfRef)
        case '{ $m: Mirror.SumOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
          deriveSum[A, types, labels](m, selfRef)

    private def deriveProduct[P: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.ProductOf[P]],
      selfRef: Expr[Decoder[A]]
    ): Expr[Decoder[P]] =
      val fields = resolveFields[Types, Labels](selfRef)
      val defaults = timer.time("resolveDefaults") { resolveDefaults[P] }
      val productTypeName = Expr(TypeRepr.of[P].typeSymbol.name)

      val fieldsWithDefaults = fields.zipWithIndex.map { case ((label, tpe, dec), idx) =>
        (label, tpe, dec, defaults.lift(idx).flatten)
      }

      val fieldLabels = fields.map(_._1)
      val fieldLabelsExpr = Expr(fieldLabels)

      val decoderExprs = fieldsWithDefaults.map { case (_, tpe, dec, _) =>
        tpe match
          case '[t] => '{ ${dec.asInstanceOf[Expr[Decoder[t]]]}.asInstanceOf[Decoder[Any]] }
      }
      val decodersArrayExpr = '{ Array(${Varargs(decoderExprs)}*) }

      val hasDefaultExprs = fieldsWithDefaults.map(f => Expr(f._4.isDefined))
      val hasDefaultArrayExpr = '{ Array(${Varargs(hasDefaultExprs)}*) }

      val defaultExprs = fieldsWithDefaults.map { case (_, _, _, defaultOpt) =>
        defaultOpt.getOrElse('{ null })
      }
      val defaultsArrayExpr = '{ Array[Any](${Varargs(defaultExprs)}*) }

      val isOptionExprs = fieldsWithDefaults.map { case (_, tpe, _, _) =>
        tpe match
          case '[t] =>
            val isOpt = TypeRepr.of[t].dealias match
              case AppliedType(tycon, _) => tycon.typeSymbol.fullName == "scala.Option"
              case _ => false
            Expr(isOpt)
      }
      val isOptionArrayExpr = '{ Array(${Varargs(isOptionExprs)}*) }

      '{
        SanelyRuntime.configuredProductDecoder[P](
          $mirror, $fieldLabelsExpr.map($conf.transformMemberNames).toArray,
          () => $decodersArrayExpr,
          $hasDefaultArrayExpr, $defaultsArrayExpr, $isOptionArrayExpr,
          $conf.useDefaults, $conf.strictDecoding, $productTypeName)
      }

    private def deriveSum[S: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.SumOf[S]],
      selfRef: Expr[Decoder[A]]
    ): Expr[Decoder[S]] =
      val cases = resolveFields[Types, Labels](selfRef)
      val sumTypeName = Expr(TypeRepr.of[S].typeSymbol.name)

      // Only flatten sub-traits when no user-provided decoder exists
      val casesWithSubTrait = cases.map { case (label, tpe, dec) =>
        val isSub = timer.time("subTraitDetect") {
          tpe match
            case '[t] =>
              val cacheKey = MacroUtils.cheapTypeKey(TypeRepr.of[t])
              !summonedKeys.contains(cacheKey) &&
              Expr.summon[Mirror.SumOf[t]].isDefined
        }
        (label, tpe, dec, isSub)
      }

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
        SanelyRuntime.configuredSumDecoder[S](
          $allLabelsExpr, () => $decodersArrayExpr, $isSubTraitExpr,
          $directLabelsExpr, $conf.transformConstructorNames, $conf.discriminator,
          $conf.strictDecoding, $sumTypeName)
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

      // Dealias once — reused by cheapTypeKey, tryResolveBuiltin, containsType
      val dealiased = tpe.dealias

      // Cache check first — hits 75% of the time, skips containsType traversal
      val cacheKey = timer.time("cheapTypeKey")(MacroUtils.cheapTypeKey(dealiased))
      exprCache.get(cacheKey) match
        case Some(cached) =>
          timer.count("cacheHit")
          return cached.asInstanceOf[Expr[Decoder[T]]]
        case None => ()

      if !negativeBuiltinCache.contains(cacheKey) then
        timer.time("tryBuiltin")(tryResolveBuiltinDecoder[T](dealiased)) match
          case Some(dec) =>
            timer.count("builtinHit")
            exprCache(cacheKey) = dec
            return dec
          case None =>
            negativeBuiltinCache += cacheKey

      // Check if T contains the recursive type in its type params
      // Must check BEFORE Expr.summonIgnoring to avoid exponential implicit search
      if containsType(dealiased, selfType) then
        return constructRecursiveDecoder[T](dealiased, selfRef)

      val constructorKey = dealiased match
        case AppliedType(tycon, _) => Some(tycon.typeSymbol.fullName)
        case _ => None

      val summonResult =
        if constructorKey.exists(constructorNegCache.contains) then
          timer.count("constructorNegHit")
          None
        else
          val result = timer.time("summonIgnoring")(Expr.summonIgnoring[Decoder[T]](cachedIgnoreSymbols*))
          if result.isDefined then summonedKeys += cacheKey
          else constructorKey.foreach(constructorNegCache += _)
          result

      val resolved: Expr[Decoder[T]] = summonResult match
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

    private def tryResolveBuiltinDecoder[T: Type](dealiased: TypeRepr): Option[Expr[Decoder[T]]] =
      resolvePrimDecoder(dealiased).map(_.asInstanceOf[Expr[Decoder[T]]]).orElse {
        dealiased match
          case AppliedType(tycon, List(arg)) =>
            val argKey = MacroUtils.cheapTypeKey(arg)
            val innerOpt =
              if negativeBuiltinCache.contains(argKey) then exprCache.get(argKey)
              else resolvePrimDecoder(arg.dealias).orElse(exprCache.get(argKey))
            val innerResolved = innerOpt.orElse {
              arg.asType match
                case '[a] =>
                  Expr.summonIgnoring[Decoder[a]](cachedIgnoreSymbols*).map { dec =>
                    exprCache(argKey) = dec
                    summonedKeys += argKey
                    dec
                  }
            }
            innerResolved.flatMap { innerDec =>
              arg.asType match
                case '[a] =>
                  val inner = innerDec.asInstanceOf[Expr[Decoder[a]]]
                  buildContainerDecoder[T, a](tycon, inner)
            }
          case AppliedType(tycon, List(keyArg, valArg))
            if tycon.typeSymbol.fullName.endsWith(".Map") =>
            val valKey = MacroUtils.cheapTypeKey(valArg)
            val valOpt = (if negativeBuiltinCache.contains(valKey) then exprCache.get(valKey)
                         else resolvePrimDecoder(valArg.dealias).orElse(exprCache.get(valKey)))
            val valResolved = valOpt.orElse {
              valArg.asType match
                case '[v] =>
                  Expr.summonIgnoring[Decoder[v]](cachedIgnoreSymbols*).map { dec =>
                    exprCache(valKey) = dec
                    summonedKeys += valKey
                    dec
                  }
            }
            for
              keyDec <- resolveBuiltinKeyDecoder(keyArg.dealias)
              valDec <- valResolved
              result <- (keyArg.asType, valArg.asType) match
                case ('[k], '[v]) =>
                  val kd = keyDec.asInstanceOf[Expr[io.circe.KeyDecoder[k]]]
                  val vd = valDec.asInstanceOf[Expr[Decoder[v]]]
                  Some('{ Decoder.decodeMap[k, v](using $kd, $vd) }.asInstanceOf[Expr[Decoder[T]]])
                case _ => None
            yield result
          case _ => None
      }

    private def resolveBuiltinKeyDecoder(tpe: TypeRepr): Option[Expr[io.circe.KeyDecoder[?]]] =
      if tpe =:= TypeRepr.of[String] then Some('{ io.circe.KeyDecoder.decodeKeyString })
      else if tpe =:= TypeRepr.of[Int] then Some('{ io.circe.KeyDecoder.decodeKeyInt })
      else if tpe =:= TypeRepr.of[Long] then Some('{ io.circe.KeyDecoder.decodeKeyLong })
      else if tpe =:= TypeRepr.of[Double] then Some('{ io.circe.KeyDecoder.decodeKeyDouble })
      else if tpe =:= TypeRepr.of[Short] then Some('{ io.circe.KeyDecoder.decodeKeyShort })
      else if tpe =:= TypeRepr.of[Byte] then Some('{ io.circe.KeyDecoder.decodeKeyByte })
      else None

    private def resolvePrimDecoder(tpe: TypeRepr): Option[Expr[Decoder[?]]] =
      tpe match
        case ConstantType(StringConstant(s)) =>
          return Some('{ Decoder.decodeString.emap(v => if v == ${Expr(s)} then Right(v) else Left(${Expr(s"""String("$s")""")})) })
        case ConstantType(IntConstant(i)) =>
          return Some('{ Decoder.decodeInt.emap(v => if v == ${Expr(i)} then Right(v) else Left(${Expr(s"Int($i)")})) })
        case ConstantType(LongConstant(l)) =>
          return Some('{ Decoder.decodeLong.emap(v => if v == ${Expr(l)} then Right(v) else Left(${Expr(s"Long($l)")})) })
        case ConstantType(DoubleConstant(d)) =>
          return Some('{ Decoder.decodeDouble.emap(v => if java.lang.Double.compare(v, ${Expr(d)}) == 0 then Right(v) else Left(${Expr(s"Double($d)")})) })
        case ConstantType(FloatConstant(f)) =>
          return Some('{ Decoder.decodeFloat.emap(v => if java.lang.Float.compare(v, ${Expr(f)}) == 0 then Right(v) else Left(${Expr(s"Float($f)")})) })
        case ConstantType(BooleanConstant(b)) =>
          return Some('{ Decoder.decodeBoolean.emap(v => if v == ${Expr(b)} then Right(v) else Left(${Expr(s"Boolean($b)")})) })
        case ConstantType(CharConstant(ch)) =>
          return Some('{ Decoder.decodeChar.emap(v => if v == ${Expr(ch)} then Right(v) else Left(${Expr(s"Char($ch)")})) })
        case _ => ()
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

    private lazy val cachedIgnoreSymbols: List[Symbol] =
      val buf = List.newBuilder[Symbol]
      buf += Symbol.requiredModule("sanely.auto").methodMember("autoDecoder").head
      try
        val genericAuto = Symbol.requiredModule("io.circe.generic.auto")
        genericAuto.methodMember("deriveDecoder").foreach(buf += _)
      catch case _: Exception => ()
      for method <- List("importedDecoder", "derived") do
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
              resolveBuiltinKeyDecoder(keyArg.dealias).map(_.asInstanceOf[Expr[io.circe.KeyDecoder[k]]])
                .orElse(Expr.summon[io.circe.KeyDecoder[k]]) match
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
              resolveBuiltinKeyDecoder(keyArg.dealias).map(_.asInstanceOf[Expr[io.circe.KeyDecoder[k]]])
                .orElse(Expr.summon[io.circe.KeyDecoder[k]]) match
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
