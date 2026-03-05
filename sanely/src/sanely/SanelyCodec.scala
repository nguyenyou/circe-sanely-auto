package sanely

import io.circe.{ACursor, Codec, Decoder, DecodingFailure, Encoder, HCursor, Json, JsonObject}
import scala.deriving.Mirror
import scala.collection.mutable
import scala.compiletime.*
import scala.quoted.*

object SanelyCodec:

  inline def derived[A](using inline m: Mirror.Of[A]): Codec.AsObject[A] =
    ${ deriveMacro[A]('m) }

  private def deriveMacro[A: Type](mirror: Expr[Mirror.Of[A]])(using Quotes): Expr[Codec.AsObject[A]] =
    val helper = new CodecDerivation[A]
    val result = helper.derive(mirror)
    helper.timer.report()
    result

  private class CodecDerivation[A: Type](using val quotes: Quotes):
    import quotes.reflect.*

    val selfType: TypeRepr = TypeRepr.of[A]
    val timer: MacroTimer = MacroTimer.create(Type.show[A], "Codec")
    // Cache stores (Encoder, Decoder) pairs — shared across both sides
    private val exprCache = mutable.Map.empty[String, (Expr[?], Expr[?])]

    def derive(mirror: Expr[Mirror.Of[A]]): Expr[Codec.AsObject[A]] =
      '{
        lazy val _selfCodec: Codec.AsObject[A] = ${
          val selfEncRef: Expr[Encoder.AsObject[A]] = '{ _selfCodec }
          val selfDecRef: Expr[Decoder[A]] = '{ _selfCodec }
          mirror match
            case '{ $m: Mirror.ProductOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
              deriveProductCodec[A, types, labels](m, selfEncRef, selfDecRef)
            case '{ $m: Mirror.SumOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
              deriveSumCodec[A, types, labels](m, selfEncRef, selfDecRef)
        }
        _selfCodec
      }

    private def deriveProductCodec[P: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.ProductOf[P]],
      selfEncRef: Expr[Encoder.AsObject[A]],
      selfDecRef: Expr[Decoder[A]]
    ): Expr[Codec.AsObject[P]] =
      val fields = resolveFields[Types, Labels](selfEncRef, selfDecRef)
      val namesExpr = Expr(fields.map(_._1).toArray)
      val encoderExprs = fields.map { case (_, tpe, enc, _) =>
        tpe match
          case '[t] => '{ ${enc.asInstanceOf[Expr[Encoder[t]]]}.asInstanceOf[Encoder[Any]] }
      }
      val decoderExprs = fields.map { case (_, tpe, _, dec) =>
        tpe match
          case '[t] => '{ ${dec.asInstanceOf[Expr[Decoder[t]]]}.asInstanceOf[Decoder[Any]] }
      }
      val encodersArrayExpr = '{ Array(${Varargs(encoderExprs)}*) }
      val decodersArrayExpr = '{ Array(${Varargs(decoderExprs)}*) }

      '{
        new Codec.AsObject[P]:
          private lazy val _encoders = $encodersArrayExpr
          private lazy val _decoders = $decodersArrayExpr
          private val _names = $namesExpr
          def encodeObject(a: P): JsonObject =
            SanelyRuntime.encodeProductFields(a.asInstanceOf[Product], _names, _encoders)
          def apply(c: HCursor): Decoder.Result[P] =
            if !c.value.isObject then Left(DecodingFailure("Expected JSON object for product type", c.history))
            else SanelyRuntime.decodeProductFields(c, $mirror, _names, _decoders)
          override def decodeAccumulating(c: HCursor): Decoder.AccumulatingResult[P] =
            SanelyRuntime.decodeProductFieldsAccumulating(c, $mirror, _names, _decoders)
      }

    private def deriveSumCodec[S: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.SumOf[S]],
      selfEncRef: Expr[Encoder.AsObject[A]],
      selfDecRef: Expr[Decoder[A]]
    ): Expr[Codec.AsObject[S]] =
      val cases = resolveFields[Types, Labels](selfEncRef, selfDecRef)

      val casesWithSubTrait = cases.map { case (label, tpe, enc, dec) =>
        val isSub = timer.time("subTraitDetect") {
          tpe match
            case '[t] =>
              Expr.summon[Mirror.SumOf[t]].isDefined &&
              Expr.summonIgnoring[Encoder[t]](cachedEncIgnoreSymbols*).isEmpty &&
              Expr.summonIgnoring[Decoder[t]](cachedDecIgnoreSymbols*).isEmpty
        }
        (label, tpe, enc, dec, isSub)
      }

      val directLabels = casesWithSubTrait.collect { case (label, _, _, _, false) => label }
      val directLabelsExpr = Expr(directLabels)

      val allLabelsExpr = Expr(casesWithSubTrait.map(_._1).toArray)
      val isSubTraitExpr = Expr(casesWithSubTrait.map(_._5).toArray)
      val encoderExprs = casesWithSubTrait.map { case (_, tpe, enc, _, _) =>
        tpe match
          case '[t] => '{ ${enc.asInstanceOf[Expr[Encoder[t]]]}.asInstanceOf[Encoder[Any]] }
      }
      val decoderExprs = casesWithSubTrait.map { case (_, tpe, _, dec, _) =>
        tpe match
          case '[t] => '{ ${dec.asInstanceOf[Expr[Decoder[t]]]}.asInstanceOf[Decoder[Any]] }
      }
      val encodersArrayExpr = '{ Array(${Varargs(encoderExprs)}*) }
      val decodersArrayExpr = '{ Array(${Varargs(decoderExprs)}*) }

      '{
        new Codec.AsObject[S]:
          private lazy val _encoders = $encodersArrayExpr
          private lazy val _decoders = $decodersArrayExpr
          private val _labels = $allLabelsExpr
          private val _isSubTrait = $isSubTraitExpr
          private val _knownLabels: Set[String] = $directLabelsExpr.toSet
          def encodeObject(a: S): JsonObject =
            val ord = $mirror.ordinal(a)
            SanelyRuntime.encodeSum(a, ord, _labels, _encoders, _isSubTrait)
          def apply(c: HCursor): Decoder.Result[S] =
            c.keys match
              case Some(keys) =>
                val key = keys.find(_knownLabels.contains).getOrElse("")
                SanelyRuntime.decodeSum(c, key, _labels, _decoders, _isSubTrait)
              case None =>
                Left(DecodingFailure("Expected JSON object for sum type", c.history))
          override def decodeAccumulating(c: HCursor): Decoder.AccumulatingResult[S] =
            c.keys match
              case Some(keys) =>
                val key = keys.find(_knownLabels.contains).getOrElse("")
                SanelyRuntime.decodeSumAccumulating(c, key, _labels, _decoders, _isSubTrait)
              case None =>
                cats.data.Validated.invalidNel(DecodingFailure("Expected JSON object for sum type", c.history))
      }

    // --- Nested type derivation (returns separate encoder + decoder pair) ---

    private def deriveProductPair[P: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.ProductOf[P]],
      selfEncRef: Expr[Encoder.AsObject[A]],
      selfDecRef: Expr[Decoder[A]]
    ): (Expr[Encoder.AsObject[P]], Expr[Decoder[P]]) =
      val fields = resolveFields[Types, Labels](selfEncRef, selfDecRef)
      val namesExpr = Expr(fields.map(_._1).toArray)
      val encoderExprs = fields.map { case (_, tpe, enc, _) =>
        tpe match
          case '[t] => '{ ${enc.asInstanceOf[Expr[Encoder[t]]]}.asInstanceOf[Encoder[Any]] }
      }
      val decoderExprs = fields.map { case (_, tpe, _, dec) =>
        tpe match
          case '[t] => '{ ${dec.asInstanceOf[Expr[Decoder[t]]]}.asInstanceOf[Decoder[Any]] }
      }
      val encodersArrayExpr = '{ Array(${Varargs(encoderExprs)}*) }
      val decodersArrayExpr = '{ Array(${Varargs(decoderExprs)}*) }

      val enc = '{
        new Encoder.AsObject[P]:
          private lazy val _encoders = $encodersArrayExpr
          private val _names = $namesExpr
          def encodeObject(a: P): JsonObject =
            SanelyRuntime.encodeProductFields(a.asInstanceOf[Product], _names, _encoders)
      }
      val dec = '{
        new Decoder[P]:
          private lazy val _decoders = $decodersArrayExpr
          private val _names = $namesExpr
          def apply(c: HCursor): Decoder.Result[P] =
            if !c.value.isObject then Left(DecodingFailure("Expected JSON object for product type", c.history))
            else SanelyRuntime.decodeProductFields(c, $mirror, _names, _decoders)
          override def decodeAccumulating(c: HCursor): Decoder.AccumulatingResult[P] =
            SanelyRuntime.decodeProductFieldsAccumulating(c, $mirror, _names, _decoders)
      }
      (enc, dec)

    private def deriveSumPair[S: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.SumOf[S]],
      selfEncRef: Expr[Encoder.AsObject[A]],
      selfDecRef: Expr[Decoder[A]]
    ): (Expr[Encoder.AsObject[S]], Expr[Decoder[S]]) =
      val cases = resolveFields[Types, Labels](selfEncRef, selfDecRef)

      val casesWithSubTrait = cases.map { case (label, tpe, enc, dec) =>
        val isSub = timer.time("subTraitDetect") {
          tpe match
            case '[t] =>
              Expr.summon[Mirror.SumOf[t]].isDefined &&
              Expr.summonIgnoring[Encoder[t]](cachedEncIgnoreSymbols*).isEmpty &&
              Expr.summonIgnoring[Decoder[t]](cachedDecIgnoreSymbols*).isEmpty
        }
        (label, tpe, enc, dec, isSub)
      }

      val directLabels = casesWithSubTrait.collect { case (label, _, _, _, false) => label }
      val directLabelsExpr = Expr(directLabels)

      val allLabelsExpr = Expr(casesWithSubTrait.map(_._1).toArray)
      val isSubTraitExpr = Expr(casesWithSubTrait.map(_._5).toArray)
      val encoderExprs = casesWithSubTrait.map { case (_, tpe, enc, _, _) =>
        tpe match
          case '[t] => '{ ${enc.asInstanceOf[Expr[Encoder[t]]]}.asInstanceOf[Encoder[Any]] }
      }
      val decoderExprs = casesWithSubTrait.map { case (_, tpe, _, dec, _) =>
        tpe match
          case '[t] => '{ ${dec.asInstanceOf[Expr[Decoder[t]]]}.asInstanceOf[Decoder[Any]] }
      }
      val encodersArrayExpr = '{ Array(${Varargs(encoderExprs)}*) }
      val decodersArrayExpr = '{ Array(${Varargs(decoderExprs)}*) }

      val enc = '{
        new Encoder.AsObject[S]:
          private lazy val _encoders = $encodersArrayExpr
          private val _labels = $allLabelsExpr
          private val _isSubTrait = $isSubTraitExpr
          def encodeObject(a: S): JsonObject =
            val ord = $mirror.ordinal(a)
            SanelyRuntime.encodeSum(a, ord, _labels, _encoders, _isSubTrait)
      }
      val dec = '{
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
          override def decodeAccumulating(c: HCursor): Decoder.AccumulatingResult[S] =
            c.keys match
              case Some(keys) =>
                val key = keys.find(_knownLabels.contains).getOrElse("")
                SanelyRuntime.decodeSumAccumulating(c, key, _labels, _decoders, _isSubTrait)
              case None =>
                cats.data.Validated.invalidNel(DecodingFailure("Expected JSON object for sum type", c.history))
      }
      (enc, dec)

    // --- Shared field resolution ---

    private def resolveFields[Types: Type, Labels: Type](
      selfEncRef: Expr[Encoder.AsObject[A]],
      selfDecRef: Expr[Decoder[A]]
    ): List[(String, Type[?], Expr[Encoder[?]], Expr[Decoder[?]])] =
      (Type.of[Types], Type.of[Labels]) match
        case ('[EmptyTuple], '[EmptyTuple]) => Nil
        case ('[t *: ts], '[label *: ls]) =>
          val labelStr = Type.of[label] match
            case '[l] =>
              Type.valueOfConstant[l].getOrElse(
                report.errorAndAbort(s"Expected literal string type")
              ).toString
          val (enc, dec) = resolveOneCodec[t](selfEncRef, selfDecRef)
          (labelStr, Type.of[t], enc, dec) :: resolveFields[ts, ls](selfEncRef, selfDecRef)
        case _ => report.errorAndAbort("Mismatched Types and Labels tuple lengths")

    private def resolveOneCodec[T: Type](
      selfEncRef: Expr[Encoder.AsObject[A]],
      selfDecRef: Expr[Decoder[A]]
    ): (Expr[Encoder[T]], Expr[Decoder[T]]) =
      val tpe = TypeRepr.of[T]

      // Direct recursion: T is the same type we're currently deriving
      if tpe =:= selfType then
        return (selfEncRef.asInstanceOf[Expr[Encoder[T]]], selfDecRef.asInstanceOf[Expr[Decoder[T]]])

      // Cache check — single key computation, pair lookup
      val cacheKey = MacroUtils.cheapTypeKey(tpe)
      exprCache.get(cacheKey) match
        case Some((cachedEnc, cachedDec)) =>
          timer.count("cacheHit")
          return (cachedEnc.asInstanceOf[Expr[Encoder[T]]], cachedDec.asInstanceOf[Expr[Decoder[T]]])
        case None => ()

      // Builtin check — resolves both encoder and decoder for primitives
      tryResolveBuiltinCodec[T] match
        case Some(pair) =>
          timer.count("builtinHit")
          exprCache(cacheKey) = pair
          return pair.asInstanceOf[(Expr[Encoder[T]], Expr[Decoder[T]])]
        case None => ()

      // Recursive type check
      if containsType(tpe, selfType) then
        val enc = constructRecursiveEncoder[T](tpe, selfEncRef)
        val dec = constructRecursiveDecoder[T](tpe, selfDecRef)
        return (enc, dec)

      // Try summon both — two summonIgnoring calls but shared everything else
      val summonedEnc = timer.time("summonIgnoring")(Expr.summonIgnoring[Encoder[T]](cachedEncIgnoreSymbols*))
      val summonedDec = timer.time("summonIgnoring")(Expr.summonIgnoring[Decoder[T]](cachedDecIgnoreSymbols*))

      val resolved: (Expr[Encoder[T]], Expr[Decoder[T]]) = (summonedEnc, summonedDec) match
        case (Some(enc), Some(dec)) => (enc, dec)
        case _ =>
          // Need to derive at least one — summon Mirror once
          timer.time("summonMirror")(Expr.summon[Mirror.Of[T]]) match
            case Some(mirrorExpr) =>
              timer.time("derive") {
                mirrorExpr match
                  case '{ $m: Mirror.ProductOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                    val (derivedEnc, derivedDec) = deriveProductPair[T, types, labels](m, selfEncRef, selfDecRef)
                    (summonedEnc.getOrElse(derivedEnc), summonedDec.getOrElse(derivedDec))
                  case '{ $m: Mirror.SumOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                    val (derivedEnc, derivedDec) = deriveSumPair[T, types, labels](m, selfEncRef, selfDecRef)
                    (summonedEnc.getOrElse(derivedEnc), summonedDec.getOrElse(derivedDec))
              }
            case None =>
              report.errorAndAbort(s"Cannot derive codec for ${Type.show[T]}: no implicit Encoder/Decoder and no Mirror available")
      exprCache(cacheKey) = resolved
      resolved

    // --- Builtin resolution (shared, returns pairs) ---

    private def tryResolveBuiltinCodec[T: Type]: Option[(Expr[Encoder[T]], Expr[Decoder[T]])] =
      val tpe = TypeRepr.of[T].dealias
      // Try primitive pair first
      (resolvePrimEncoder(tpe), resolvePrimDecoder(tpe)) match
        case (Some(enc), Some(dec)) =>
          Some((enc.asInstanceOf[Expr[Encoder[T]]], dec.asInstanceOf[Expr[Decoder[T]]]))
        case _ =>
          // Try container of known inner type
          tpe match
            case AppliedType(tycon, List(arg)) =>
              val innerPair = (resolvePrimEncoder(arg.dealias), resolvePrimDecoder(arg.dealias)) match
                case (Some(e), Some(d)) => Some((e, d))
                case _ => exprCache.get(MacroUtils.cheapTypeKey(arg))
              innerPair.flatMap { case (innerEnc, innerDec) =>
                arg.asType match
                  case '[a] =>
                    val encInner = innerEnc.asInstanceOf[Expr[Encoder[a]]]
                    val decInner = innerDec.asInstanceOf[Expr[Decoder[a]]]
                    for
                      ce <- buildContainerEncoder[T, a](tycon, encInner)
                      cd <- buildContainerDecoder[T, a](tycon, decInner)
                    yield (ce, cd)
              }
            case AppliedType(tycon, List(keyArg, valArg))
              if tycon.typeSymbol.fullName.endsWith(".Map") =>
              val valPair = (resolvePrimEncoder(valArg.dealias), resolvePrimDecoder(valArg.dealias)) match
                case (Some(e), Some(d)) => Some((e, d))
                case _ => exprCache.get(MacroUtils.cheapTypeKey(valArg))
              for
                keyEnc <- resolveBuiltinKeyEncoder(keyArg.dealias)
                keyDec <- resolveBuiltinKeyDecoder(keyArg.dealias)
                (valEnc, valDec) <- valPair
                result <- (keyArg.asType, valArg.asType) match
                  case ('[k], '[v]) =>
                    val ke = keyEnc.asInstanceOf[Expr[io.circe.KeyEncoder[k]]]
                    val kd = keyDec.asInstanceOf[Expr[io.circe.KeyDecoder[k]]]
                    val ve = valEnc.asInstanceOf[Expr[Encoder[v]]]
                    val vd = valDec.asInstanceOf[Expr[Decoder[v]]]
                    Some((
                      '{ Encoder.encodeMap[k, v](using $ke, $ve) }.asInstanceOf[Expr[Encoder[T]]],
                      '{ Decoder.decodeMap[k, v](using $kd, $vd) }.asInstanceOf[Expr[Decoder[T]]]
                    ))
                  case _ => None
              yield result
            case _ => None

    // --- Primitive resolution ---

    private def resolvePrimEncoder(tpe: TypeRepr): Option[Expr[Encoder[?]]] =
      tpe match
        case ConstantType(StringConstant(s)) =>
          return Some('{ Encoder.instance[Any]((_: Any) => Json.fromString(${Expr(s)})) })
        case ConstantType(IntConstant(i)) =>
          return Some('{ Encoder.instance[Any]((_: Any) => Json.fromInt(${Expr(i)})) })
        case ConstantType(LongConstant(l)) =>
          return Some('{ Encoder.instance[Any]((_: Any) => Json.fromLong(${Expr(l)})) })
        case ConstantType(DoubleConstant(d)) =>
          return Some('{ Encoder.instance[Any]((_: Any) => Json.fromDoubleOrNull(${Expr(d)})) })
        case ConstantType(FloatConstant(f)) =>
          return Some('{ Encoder.instance[Any]((_: Any) => Json.fromFloatOrNull(${Expr(f)})) })
        case ConstantType(BooleanConstant(b)) =>
          return Some('{ Encoder.instance[Any]((_: Any) => Json.fromBoolean(${Expr(b)})) })
        case ConstantType(CharConstant(ch)) =>
          return Some('{ Encoder.instance[Any]((_: Any) => Json.fromString(${Expr(ch.toString)})) })
        case _ => ()
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

    // --- Key encoder/decoder resolution ---

    private def resolveBuiltinKeyEncoder(tpe: TypeRepr): Option[Expr[io.circe.KeyEncoder[?]]] =
      if tpe =:= TypeRepr.of[String] then Some('{ io.circe.KeyEncoder.encodeKeyString })
      else if tpe =:= TypeRepr.of[Int] then Some('{ io.circe.KeyEncoder.encodeKeyInt })
      else if tpe =:= TypeRepr.of[Long] then Some('{ io.circe.KeyEncoder.encodeKeyLong })
      else if tpe =:= TypeRepr.of[Double] then Some('{ io.circe.KeyEncoder.encodeKeyDouble })
      else if tpe =:= TypeRepr.of[Short] then Some('{ io.circe.KeyEncoder.encodeKeyShort })
      else if tpe =:= TypeRepr.of[Byte] then Some('{ io.circe.KeyEncoder.encodeKeyByte })
      else None

    private def resolveBuiltinKeyDecoder(tpe: TypeRepr): Option[Expr[io.circe.KeyDecoder[?]]] =
      if tpe =:= TypeRepr.of[String] then Some('{ io.circe.KeyDecoder.decodeKeyString })
      else if tpe =:= TypeRepr.of[Int] then Some('{ io.circe.KeyDecoder.decodeKeyInt })
      else if tpe =:= TypeRepr.of[Long] then Some('{ io.circe.KeyDecoder.decodeKeyLong })
      else if tpe =:= TypeRepr.of[Double] then Some('{ io.circe.KeyDecoder.decodeKeyDouble })
      else if tpe =:= TypeRepr.of[Short] then Some('{ io.circe.KeyDecoder.decodeKeyShort })
      else if tpe =:= TypeRepr.of[Byte] then Some('{ io.circe.KeyDecoder.decodeKeyByte })
      else None

    // --- Container builders ---

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

    // --- Ignore symbols (separate for encoder and decoder summoning) ---

    private lazy val cachedEncIgnoreSymbols: List[Symbol] =
      val buf = List.newBuilder[Symbol]
      buf += Symbol.requiredModule("sanely.auto").methodMember("autoEncoder").head
      try
        val genericAuto = Symbol.requiredModule("io.circe.generic.auto")
        genericAuto.methodMember("deriveEncoder").foreach(buf += _)
      catch case _: Exception => ()
      for method <- List("importedEncoder", "importedAsObjectEncoder", "derived") do
        try
          val encoderCompanion = Symbol.requiredModule("io.circe.Encoder")
          encoderCompanion.methodMember(method).foreach(buf += _)
        catch case _: Exception => ()
        try
          val decoderCompanion = Symbol.requiredModule("io.circe.Decoder")
          decoderCompanion.methodMember(method).foreach(buf += _)
        catch case _: Exception => ()
      buf.result()

    private lazy val cachedDecIgnoreSymbols: List[Symbol] =
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

    // --- Type utilities ---

    private def containsType(tpe: TypeRepr, target: TypeRepr): Boolean =
      val dealiased = tpe.dealias
      if dealiased =:= target then true
      else dealiased match
        case AppliedType(_, args) => args.exists(arg => containsType(arg, target))
        case AndType(left, right) => containsType(left, target) || containsType(right, target)
        case OrType(left, right) => containsType(left, target) || containsType(right, target)
        case _ => false

    // --- Recursive type construction ---

    private def constructRecursiveEncoder[T: Type](
      tpe: TypeRepr,
      selfRef: Expr[Encoder.AsObject[A]]
    ): Expr[Encoder[T]] =
      def trySummon: Option[Expr[Encoder[T]]] = Expr.summonIgnoring[Encoder[T]](cachedEncIgnoreSymbols*)

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
              resolveBuiltinKeyEncoder(keyArg.dealias).map(_.asInstanceOf[Expr[io.circe.KeyEncoder[k]]])
                .orElse(Expr.summon[io.circe.KeyEncoder[k]]) match
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
              resolveBuiltinKeyEncoder(keyArg.dealias).map(_.asInstanceOf[Expr[io.circe.KeyEncoder[k]]])
                .orElse(Expr.summon[io.circe.KeyEncoder[k]]) match
                case Some(keyEnc) =>
                  '{ Encoder.encodeMap[k, v](using $keyEnc, $innerEnc) }.asInstanceOf[Expr[Encoder[T]]]
                case None =>
                  report.errorAndAbort(s"Cannot derive Encoder for Map: no KeyEncoder for ${Type.show[k]}")
            case _ => report.errorAndAbort(s"Unexpected type pattern in Map recursive encoder")
        case _ =>
          trySummon.getOrElse(
            report.errorAndAbort(s"Cannot derive Encoder for recursive type application: ${Type.show[T]}"))

    private def constructRecursiveDecoder[T: Type](
      tpe: TypeRepr,
      selfRef: Expr[Decoder[A]]
    ): Expr[Decoder[T]] =
      def trySummon: Option[Expr[Decoder[T]]] = Expr.summonIgnoring[Decoder[T]](cachedDecIgnoreSymbols*)

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
