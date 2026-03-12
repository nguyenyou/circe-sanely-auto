package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, JsonWriter}
import scala.deriving.Mirror
import scala.collection.mutable
import scala.compiletime.*
import scala.quoted.*

object SanelyJsoniterConfigured:

  inline def derived[A](using inline conf: JsoniterConfiguration)(using inline m: Mirror.Of[A]): JsonValueCodec[A] =
    ${ deriveMacro[A]('conf, 'm) }

  private def deriveMacro[A: Type](conf: Expr[JsoniterConfiguration], mirror: Expr[Mirror.Of[A]])(using Quotes): Expr[JsonValueCodec[A]] =
    val helper = new ConfiguredCodecDerivation[A](conf)
    helper.derive(mirror)

  private class ConfiguredCodecDerivation[A: Type](conf: Expr[JsoniterConfiguration])(using val quotes: Quotes):
    import quotes.reflect.*

    val selfType: TypeRepr = TypeRepr.of[A]
    private val exprCache = mutable.Map.empty[String, Expr[?]]
    private val negativeBuiltinCache = mutable.Set.empty[String]
    private val summonedKeys = mutable.Set.empty[String]

    def derive(mirror: Expr[Mirror.Of[A]]): Expr[JsonValueCodec[A]] =
      // Check if A is a known container type before Mirror derivation
      tryResolveBuiltin[A](TypeRepr.of[A].dealias) match
        case Some(codec) => codec
        case None =>
          '{
            lazy val _selfCodec: JsonValueCodec[A] = ${
              val selfRef: Expr[JsonValueCodec[A]] = '{ _selfCodec }
              mirror match
                case '{ $m: Mirror.ProductOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                  deriveProduct[A, types, labels](selfRef)
                case '{ $m: Mirror.SumOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                  deriveSum[A, types, labels](m, selfRef)
            }
            _selfCodec
          }

    // === Product derivation (configured) ===

    private def buildDirectConstruct[P: Type](varRefs: List[Term]): Term =
      val tpe = TypeRepr.of[P]
      val sym = tpe.typeSymbol
      if sym.flags.is(Flags.Module) then
        Ref(tpe.termSymbol)
      else
        val ctor = sym.primaryConstructor
        val ctorSelect = Select(New(Inferred(tpe)), ctor)
        val tpeTypeArgs = tpe match
          case AppliedType(_, args) => args
          case _ => Nil
        val ctorWithTypes =
          if tpeTypeArgs.isEmpty then ctorSelect
          else TypeApply(ctorSelect, tpeTypeArgs.map(Inferred(_)))
        Apply(ctorWithTypes, varRefs)

    private def deriveProduct[P: Type, Types: Type, Labels: Type](
      selfRef: Expr[JsonValueCodec[A]]
    ): Expr[JsonValueCodec[P]] =
      val fields = resolveFields[Types, Labels](selfRef)
      val defaults = resolveDefaults[P]

      val fieldsWithDefaults = fields.zipWithIndex.map { case ((label, tpe, codec), idx) =>
        (label, tpe, codec, defaults.lift(idx).flatten)
      }

      val namesExpr = Expr(fields.map(_._1).toArray)
      val codecExprs = fieldsWithDefaults.map { case (_, tpe, codec, _) =>
        tpe match
          case '[t] => '{ ${codec.asInstanceOf[Expr[JsonValueCodec[t]]]}.asInstanceOf[JsonValueCodec[Any]] }
      }
      val codecsArrayExpr = '{ Array(${Varargs(codecExprs)}*) }

      val nullValueExprs = fields.map { case (_, tpe, _) =>
        tpe match
          case '[t] => nullValueExpr[t]
      }
      val nullValuesExpr = '{ Array(${Varargs(nullValueExprs)}*) }

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

      val encodeFnExpr = '{ (x: P, names: Array[String], codecs: Array[JsonValueCodec[Any]], out: JsonWriter) =>
        ${ generateConfiguredFieldWrites[P]('x, 'names, 'codecs, 'out, fields) }
      }
      val encodeDropNullFnExpr = '{ (x: P, names: Array[String], codecs: Array[JsonValueCodec[Any]], out: JsonWriter) =>
        ${ generateConfiguredFieldWritesDropNull[P]('x, 'names, 'codecs, 'out, fields) }
      }

      val useDefaultsE = '{ $conf.useDefaults }
      val strictDecodingE = '{ $conf.strictDecoding }
      val decodeFnExpr = '{ (in: JsonReader, names: Array[String], codecs: Array[JsonValueCodec[Any]]) =>
        ${ generateConfiguredDecodeBody[P]('in, 'names, 'codecs, fields, fieldsWithDefaults, useDefaultsE, strictDecodingE) }
      }
      val decodeAfterDiscFnExpr = '{ (in: JsonReader, names: Array[String], codecs: Array[JsonValueCodec[Any]]) =>
        ${ generateConfiguredDecodeAfterDiscBody[P]('in, 'names, 'codecs, fields, fieldsWithDefaults, useDefaultsE, strictDecodingE) }
      }

      '{
        JsoniterRuntime.configuredProductCodec[P](
          $namesExpr, $conf.transformMemberNames,
          () => $codecsArrayExpr, $nullValuesExpr,
          $hasDefaultArrayExpr, $defaultsArrayExpr, $isOptionArrayExpr,
          $conf.useDefaults, $conf.dropNullValues, $conf.strictDecoding,
          $encodeFnExpr, $encodeDropNullFnExpr,
          $decodeFnExpr, $decodeAfterDiscFnExpr)
      }

    private def generateConfiguredFieldWrites[P: Type](
      x: Expr[P], names: Expr[Array[String]], codecs: Expr[Array[JsonValueCodec[Any]]], out: Expr[JsonWriter],
      fields: List[(String, Type[?], Expr[JsonValueCodec[?]])]
    ): Expr[Unit] =
      val stmts = fields.zipWithIndex.map { case ((name, tpe, _), idx) =>
        tpe match
          case '[t] =>
            val fieldAccess = Select.unique(x.asTerm, name).asExprOf[t]
            val writeValue = tryDirectWrite[t](fieldAccess, out).getOrElse(
              '{ $codecs(${Expr(idx)}).encodeValue($fieldAccess.asInstanceOf[Any], $out) }
            )
            '{
              $out.writeNonEscapedAsciiKey($names(${Expr(idx)}))
              $writeValue
            }
      }
      if stmts.isEmpty then '{ () }
      else
        val terms = stmts.map(_.asTerm)
        Block(terms.init.toList, terms.last).asExprOf[Unit]

    private def generateConfiguredFieldWritesDropNull[P: Type](
      x: Expr[P], names: Expr[Array[String]], codecs: Expr[Array[JsonValueCodec[Any]]], out: Expr[JsonWriter],
      fields: List[(String, Type[?], Expr[JsonValueCodec[?]])]
    ): Expr[Unit] =
      val stmts = fields.zipWithIndex.map { case ((name, tpe, _), idx) =>
        tpe match
          case '[t] =>
            val fieldAccess = Select.unique(x.asTerm, name).asExprOf[t]
            val idxExpr = Expr(idx)
            tryDirectWriteDropNull[t](fieldAccess, names, idxExpr, out).getOrElse {
              '{
                val v: Any = $fieldAccess.asInstanceOf[Any]
                val isNull = v == null || (v.isInstanceOf[Option[?]] && v.asInstanceOf[Option[?]].isEmpty)
                if !isNull then
                  $out.writeNonEscapedAsciiKey($names($idxExpr))
                  $codecs($idxExpr).encodeValue(v, $out)
              }
            }
      }
      if stmts.isEmpty then '{ () }
      else
        val terms = stmts.map(_.asTerm)
        Block(terms.init.toList, terms.last).asExprOf[Unit]

    private def tryDirectRead[T: Type](in: Expr[JsonReader]): Option[Expr[T]] =
      val tpe0 = TypeRepr.of[T].dealias
      val tpe = if isOpaqueAlias(tpe0) then opaqueDealias(tpe0) else tpe0
      // For opaque types, read as underlying then cast back
      def cast(e: Expr[?]): Expr[T] =
        if isOpaqueAlias(tpe0) then '{ ${e}.asInstanceOf[T] }
        else e.asExprOf[T]
      if tpe =:= TypeRepr.of[Int] then Some(cast('{ $in.readInt() }))
      else if tpe =:= TypeRepr.of[Long] then Some(cast('{ $in.readLong() }))
      else if tpe =:= TypeRepr.of[Double] then Some(cast('{ $in.readDouble() }))
      else if tpe =:= TypeRepr.of[Float] then Some(cast('{ $in.readFloat() }))
      else if tpe =:= TypeRepr.of[Boolean] then Some(cast('{ $in.readBoolean() }))
      else if tpe =:= TypeRepr.of[Short] then Some(cast('{ $in.readInt().toShort }))
      else if tpe =:= TypeRepr.of[Byte] then Some(cast('{ $in.readInt().toByte }))
      else if tpe =:= TypeRepr.of[Char] then
        Some(cast('{ val _s = $in.readString(null); if _s == null || _s.isEmpty then 0.toChar else _s.charAt(0) }))
      else if tpe =:= TypeRepr.of[String] then Some(cast('{ $in.readString(null) }))
      else if tpe =:= TypeRepr.of[BigDecimal] then Some(cast('{ $in.readBigDecimal(null) }))
      else if tpe =:= TypeRepr.of[BigInt] then Some(cast('{ $in.readBigInt(null) }))
      else None

    private def typedDefaultTerm(tpe: TypeRepr): Term =
      val d0 = tpe.dealias
      val needsCast = isOpaqueAlias(d0)
      val d = if needsCast then opaqueDealias(d0) else d0
      val raw =
        if d =:= TypeRepr.of[Int] then Literal(IntConstant(0))
        else if d =:= TypeRepr.of[Long] then Literal(LongConstant(0L))
        else if d =:= TypeRepr.of[Double] then Literal(DoubleConstant(0.0))
        else if d =:= TypeRepr.of[Float] then Literal(FloatConstant(0.0f))
        else if d =:= TypeRepr.of[Boolean] then Literal(BooleanConstant(false))
        else if d =:= TypeRepr.of[Short] then '{ (0: Short) }.asTerm
        else if d =:= TypeRepr.of[Byte] then '{ (0: Byte) }.asTerm
        else if d =:= TypeRepr.of[Char] then '{ (0: Char) }.asTerm
        else if d <:< TypeRepr.of[Option[?]] then '{ None }.asTerm
        // null.asInstanceOf[T] — always cast so it type-checks for abstract type members
        // (e.g. zio-prelude Subtype), AnyVal wrappers, and opaque types
        else TypeApply(Select.unique(Literal(NullConstant()), "asInstanceOf"), List(Inferred(tpe)))
      // Cast primitive zero to the opaque type so var initialization type-checks
      if needsCast then TypeApply(Select.unique(raw, "asInstanceOf"), List(Inferred(tpe)))
      else raw

    /** Generate decode body with typed locals for configured product.
      * Similar to SanelyJsoniter.generateDecodeBody but handles:
      * - useDefaults: initialize vars with defaults when available
      * - strictDecoding: error on unmatched fields
      * - Names are runtime-transformed (passed as array)
      */
    private def generateConfiguredDecodeBody[P: Type](
      inExpr: Expr[JsonReader],
      namesExpr: Expr[Array[String]],
      codecsExpr: Expr[Array[JsonValueCodec[Any]]],
      fields: List[(String, Type[?], Expr[JsonValueCodec[?]])],
      fieldsWithDefaults: List[(String, Type[?], Expr[JsonValueCodec[?]], Option[Expr[Any]])],
      useDefaultsExpr: Expr[Boolean],
      strictDecodingExpr: Expr[Boolean]
    ): Expr[P] =
      val n = fields.length
      if n == 0 then
        val constructExpr = buildDirectConstruct[P](Nil).asExprOf[P]
        return '{
          if !$inExpr.isNextToken('}') then
            $inExpr.rollbackToken()
            var _c = true
            while _c do
              $inExpr.readKeyAsCharBuf()
              if $strictDecodingExpr then
                $inExpr.decodeError(s"Strict decoding: unexpected field; valid fields: ${$namesExpr.mkString(", ")}")
              else $inExpr.skip()
              _c = $inExpr.isNextToken(',')
            if !$inExpr.isCurrentToken('}') then $inExpr.objectEndOrCommaError()
          $constructExpr
        }

      val owner = Symbol.spliceOwner

      case class VarInfo(sym: Symbol, label: String, tpe: Type[?], idx: Int,
                         defaultTerm: Term, hasDefault: Boolean, defaultExpr: Option[Expr[Any]], isOption: Boolean)
      val vars: List[VarInfo] = fieldsWithDefaults.zipWithIndex.map { case ((label, tpe, _, defaultOpt), idx) =>
        tpe match
          case '[t] =>
            val sym = Symbol.newVal(owner, s"_f$idx", TypeRepr.of[t], Flags.Mutable, Symbol.noSymbol)
            val nullDefault = typedDefaultTerm(TypeRepr.of[t])
            val isOpt = TypeRepr.of[t].dealias match
              case AppliedType(tycon, _) => tycon.typeSymbol.fullName == "scala.Option"
              case _ => false
            VarInfo(sym, label, Type.of[t], idx, nullDefault, defaultOpt.isDefined, defaultOpt, isOpt)
      }

      // var declarations with useDefaults-aware initialization
      val varDefs: List[Statement] = vars.map { v =>
        v.tpe match
          case '[t] =>
            if v.hasDefault then
              val defExpr = v.defaultExpr.get
              // if useDefaults then default else nullDefault
              val rhs = '{ if $useDefaultsExpr then $defExpr.asInstanceOf[t] else ${v.defaultTerm.asExprOf[t]} }.asTerm
              ValDef(v.sym, Some(rhs))
            else if v.isOption then
              // Options always start as None (same with or without useDefaults)
              ValDef(v.sym, Some('{ None }.asTerm))
            else
              ValDef(v.sym, Some(v.defaultTerm))
      }

      // Build if-else chain for field matching (names are runtime-transformed)
      // When a field has a default and is not Option, null JSON values are intercepted:
      // - with useDefaults: keep the Scala default (var already initialized with it)
      // - without useDefaults: keep the type's zero value (var already initialized with it)
      def buildMatchChain(keyLenRef: Term): Term =
        val unmatchedBranch = '{
          if $strictDecodingExpr then
            $inExpr.decodeError(s"Strict decoding: unexpected field; valid fields: ${$namesExpr.mkString(", ")}")
          else $inExpr.skip()
        }.asTerm
        vars.foldRight[Term](unmatchedBranch) { case (vi, elseBranch) =>
          vi.tpe match
            case '[t] =>
              val idxE = Expr(vi.idx)
              val cond = '{ $inExpr.isCharBufEqualsTo(${keyLenRef.asExprOf[Int]}, $namesExpr($idxE)) }.asTerm
              val readTerm: Term = tryDirectRead[t](inExpr) match
                case Some(expr) => expr.asTerm
                case None =>
                  '{ $codecsExpr($idxE).decodeValue($inExpr, $codecsExpr($idxE).nullValue).asInstanceOf[t] }.asTerm
              val body =
                if vi.hasDefault && !vi.isOption then
                  // Intercept null at JSON level: peek at next token
                  // If null → consume it, keep var's initialized value (default or zero)
                  // If not null → rollback and read normally
                  '{
                    if $inExpr.isNextToken('n') then
                      $inExpr.rollbackToken()
                      $inExpr.skip()
                      () // keep existing var value (Scala default if useDefaults, zero otherwise)
                    else
                      $inExpr.rollbackToken()
                      ${Assign(Ref(vi.sym), readTerm).asExprOf[Unit]}
                  }.asTerm
                else
                  Assign(Ref(vi.sym), readTerm)
              If(cond, body, elseBranch)
        }

      // Build result: direct constructor call
      val resultTerm = buildDirectConstruct[P](vars.map(v => Ref(v.sym)))

      // While loop
      val contSym = Symbol.newVal(owner, "_c", TypeRepr.of[Boolean], Flags.Mutable, Symbol.noSymbol)
      val contDef = ValDef(contSym, Some(Literal(BooleanConstant(true))))
      val klSym = Symbol.newVal(owner, "_kl", TypeRepr.of[Int], Flags.EmptyFlags, Symbol.noSymbol)
      val klDef = ValDef(klSym, Some('{ $inExpr.readKeyAsCharBuf() }.asTerm))
      val whileBody = Block(
        List(klDef, buildMatchChain(Ref(klSym))),
        Assign(Ref(contSym), '{ $inExpr.isNextToken(',') }.asTerm)
      )
      val whileLoop = While(Ref(contSym), whileBody)
      val checkEnd = If(
        '{ !$inExpr.isCurrentToken('}') }.asTerm,
        '{ $inExpr.objectEndOrCommaError() }.asTerm,
        Literal(UnitConstant())
      )
      val loopBlock = Block(
        List('{ $inExpr.rollbackToken() }.asTerm, contDef, whileLoop, checkEnd),
        Literal(UnitConstant())
      )
      val outerIf = If(
        '{ !$inExpr.isNextToken('}') }.asTerm,
        loopBlock,
        Literal(UnitConstant())
      )

      Block(varDefs :+ outerIf, resultTerm).asExprOf[P]

    /** Generate decode body for after-discriminator path (no opening `{`).
      * Reads remaining fields after discriminator was consumed.
      */
    private def generateConfiguredDecodeAfterDiscBody[P: Type](
      inExpr: Expr[JsonReader],
      namesExpr: Expr[Array[String]],
      codecsExpr: Expr[Array[JsonValueCodec[Any]]],
      fields: List[(String, Type[?], Expr[JsonValueCodec[?]])],
      fieldsWithDefaults: List[(String, Type[?], Expr[JsonValueCodec[?]], Option[Expr[Any]])],
      useDefaultsExpr: Expr[Boolean],
      strictDecodingExpr: Expr[Boolean]
    ): Expr[P] =
      val n = fields.length
      if n == 0 then
        val constructExpr = buildDirectConstruct[P](Nil).asExprOf[P]
        return '{
          if $inExpr.isNextToken(',') then
            var _c = true
            while _c do
              $inExpr.readKeyAsCharBuf()
              if $strictDecodingExpr then
                $inExpr.decodeError(s"Strict decoding: unexpected field; valid fields: ${$namesExpr.mkString(", ")}")
              else $inExpr.skip()
              _c = $inExpr.isNextToken(',')
            if !$inExpr.isCurrentToken('}') then $inExpr.objectEndOrCommaError()
          else if !$inExpr.isCurrentToken('}') then
            $inExpr.objectEndOrCommaError()
          $constructExpr
        }

      val owner = Symbol.spliceOwner

      case class VarInfo(sym: Symbol, label: String, tpe: Type[?], idx: Int,
                         defaultTerm: Term, hasDefault: Boolean, defaultExpr: Option[Expr[Any]], isOption: Boolean)
      val vars: List[VarInfo] = fieldsWithDefaults.zipWithIndex.map { case ((label, tpe, _, defaultOpt), idx) =>
        tpe match
          case '[t] =>
            val sym = Symbol.newVal(owner, s"_f$idx", TypeRepr.of[t], Flags.Mutable, Symbol.noSymbol)
            val nullDefault = typedDefaultTerm(TypeRepr.of[t])
            val isOpt = TypeRepr.of[t].dealias match
              case AppliedType(tycon, _) => tycon.typeSymbol.fullName == "scala.Option"
              case _ => false
            VarInfo(sym, label, Type.of[t], idx, nullDefault, defaultOpt.isDefined, defaultOpt, isOpt)
      }

      // var declarations (same as decode body)
      val varDefs: List[Statement] = vars.map { v =>
        v.tpe match
          case '[t] =>
            if v.hasDefault then
              val defExpr = v.defaultExpr.get
              val rhs = '{ if $useDefaultsExpr then $defExpr.asInstanceOf[t] else ${v.defaultTerm.asExprOf[t]} }.asTerm
              ValDef(v.sym, Some(rhs))
            else if v.isOption then
              ValDef(v.sym, Some('{ None }.asTerm))
            else
              ValDef(v.sym, Some(v.defaultTerm))
      }

      // Build if-else chain (same as decode body, with null interception)
      def buildMatchChain(keyLenRef: Term): Term =
        val unmatchedBranch = '{
          if $strictDecodingExpr then
            $inExpr.decodeError(s"Strict decoding: unexpected field; valid fields: ${$namesExpr.mkString(", ")}")
          else $inExpr.skip()
        }.asTerm
        vars.foldRight[Term](unmatchedBranch) { case (vi, elseBranch) =>
          vi.tpe match
            case '[t] =>
              val idxE = Expr(vi.idx)
              val cond = '{ $inExpr.isCharBufEqualsTo(${keyLenRef.asExprOf[Int]}, $namesExpr($idxE)) }.asTerm
              val readTerm: Term = tryDirectRead[t](inExpr) match
                case Some(expr) => expr.asTerm
                case None =>
                  '{ $codecsExpr($idxE).decodeValue($inExpr, $codecsExpr($idxE).nullValue).asInstanceOf[t] }.asTerm
              val body =
                if vi.hasDefault && !vi.isOption then
                  '{
                    if $inExpr.isNextToken('n') then
                      $inExpr.rollbackToken()
                      $inExpr.skip()
                      ()
                    else
                      $inExpr.rollbackToken()
                      ${Assign(Ref(vi.sym), readTerm).asExprOf[Unit]}
                  }.asTerm
                else
                  Assign(Ref(vi.sym), readTerm)
              If(cond, body, elseBranch)
        }

      // Build result: direct constructor call
      val resultTerm = buildDirectConstruct[P](vars.map(v => Ref(v.sym)))

      // After-discriminator: check for comma, then field loop, then check end
      val contSym = Symbol.newVal(owner, "_c", TypeRepr.of[Boolean], Flags.Mutable, Symbol.noSymbol)
      val contDef = ValDef(contSym, Some(Literal(BooleanConstant(true))))
      val klSym = Symbol.newVal(owner, "_kl", TypeRepr.of[Int], Flags.EmptyFlags, Symbol.noSymbol)
      val klDef = ValDef(klSym, Some('{ $inExpr.readKeyAsCharBuf() }.asTerm))
      val whileBody = Block(
        List(klDef, buildMatchChain(Ref(klSym))),
        Assign(Ref(contSym), '{ $inExpr.isNextToken(',') }.asTerm)
      )
      val whileLoop = While(Ref(contSym), whileBody)
      val checkEnd = If(
        '{ !$inExpr.isCurrentToken('}') }.asTerm,
        '{ $inExpr.objectEndOrCommaError() }.asTerm,
        Literal(UnitConstant())
      )

      // if comma then loop else check end
      val commaBlock = Block(
        List(contDef, whileLoop, checkEnd),
        Literal(UnitConstant())
      )
      val endCheck2 = If(
        '{ !$inExpr.isCurrentToken('}') }.asTerm,
        '{ $inExpr.objectEndOrCommaError() }.asTerm,
        Literal(UnitConstant())
      )
      val afterDiscIf = If(
        '{ $inExpr.isNextToken(',') }.asTerm,
        commaBlock,
        endCheck2
      )

      Block(varDefs :+ afterDiscIf, resultTerm).asExprOf[P]

    private def tryDirectWrite[T: Type](fa: Expr[T], out: Expr[JsonWriter]): Option[Expr[Unit]] =
      val tpe0 = TypeRepr.of[T].dealias
      val tpe = if isOpaqueAlias(tpe0) then opaqueDealias(tpe0) else tpe0
      // Cast to underlying type for opaque types so overload resolution works
      def castInt: Expr[Int] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[Int] } else fa.asExprOf[Int]
      def castLong: Expr[Long] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[Long] } else fa.asExprOf[Long]
      def castFloat: Expr[Float] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[Float] } else fa.asExprOf[Float]
      def castDouble: Expr[Double] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[Double] } else fa.asExprOf[Double]
      def castBoolean: Expr[Boolean] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[Boolean] } else fa.asExprOf[Boolean]
      def castShort: Expr[Short] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[Short] } else fa.asExprOf[Short]
      def castByte: Expr[Byte] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[Byte] } else fa.asExprOf[Byte]
      def castChar: Expr[Char] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[Char] } else fa.asExprOf[Char]
      def castString: Expr[String] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[String] } else fa.asExprOf[String]
      def castBigDecimal: Expr[BigDecimal] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[BigDecimal] } else fa.asExprOf[BigDecimal]
      def castBigInt: Expr[BigInt] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[BigInt] } else fa.asExprOf[BigInt]
      if tpe =:= TypeRepr.of[Int] then Some('{ $out.writeVal(${castInt}) })
      else if tpe =:= TypeRepr.of[Long] then Some('{ $out.writeVal(${castLong}) })
      else if tpe =:= TypeRepr.of[Float] then Some('{ $out.writeVal(${castFloat}) })
      else if tpe =:= TypeRepr.of[Double] then Some('{ $out.writeVal(${castDouble}) })
      else if tpe =:= TypeRepr.of[Boolean] then Some('{ $out.writeVal(${castBoolean}) })
      else if tpe =:= TypeRepr.of[Short] then Some('{ $out.writeVal(${castShort}.toInt) })
      else if tpe =:= TypeRepr.of[Byte] then Some('{ $out.writeVal(${castByte}.toInt) })
      else if tpe =:= TypeRepr.of[Char] then Some('{ $out.writeVal(${castChar}.toString) })
      else if tpe =:= TypeRepr.of[String] then
        Some('{ val _v = ${castString}; if _v == null then $out.writeNull() else $out.writeVal(_v) })
      else if tpe =:= TypeRepr.of[BigDecimal] then
        Some('{ val _v = ${castBigDecimal}; if _v == null then $out.writeNull() else $out.writeVal(_v) })
      else if tpe =:= TypeRepr.of[BigInt] then
        Some('{ val _v = ${castBigInt}; if _v == null then $out.writeNull() else $out.writeVal(_v) })
      else
        // Inline Option[prim] and List[prim] writes to avoid virtual dispatch
        tpe match
          case AppliedType(tycon, List(innerArg)) =>
            val outTerm = out.asTerm
            val castTerm = if isOpaqueAlias(tpe0) then
              TypeApply(Select.unique(fa.asTerm, "asInstanceOf"), List(Inferred(tpe)))
            else fa.asTerm
            def wv(arg: Term): Term = Select.overloaded(outTerm, "writeVal", Nil, List(arg))
            def wn(): Term = Select.overloaded(outTerm, "writeNull", Nil, Nil)
            def innerWriteVal(valTerm: Term): Option[Term] =
              val ei = innerArg.dealias
              if ei =:= TypeRepr.of[Int] then Some(wv(valTerm))
              else if ei =:= TypeRepr.of[Long] then Some(wv(valTerm))
              else if ei =:= TypeRepr.of[Float] then Some(wv(valTerm))
              else if ei =:= TypeRepr.of[Double] then Some(wv(valTerm))
              else if ei =:= TypeRepr.of[Boolean] then Some(wv(valTerm))
              else if ei =:= TypeRepr.of[Short] then Some(wv(Select.unique(valTerm, "toInt")))
              else if ei =:= TypeRepr.of[Byte] then Some(wv(Select.unique(valTerm, "toInt")))
              else if ei =:= TypeRepr.of[Char] then Some(wv(Select.overloaded(valTerm, "toString", Nil, Nil)))
              else if ei =:= TypeRepr.of[String] then Some(wv(valTerm))
              else if ei =:= TypeRepr.of[BigDecimal] then Some(wv(valTerm))
              else if ei =:= TypeRepr.of[BigInt] then Some(wv(valTerm))
              else None
            tycon.typeSymbol.fullName match
              case "scala.Option" =>
                val owner = Symbol.spliceOwner
                val optSym = Symbol.newVal(owner, "_o", tpe, Flags.EmptyFlags, Symbol.noSymbol)
                val optDef = ValDef(optSym, Some(castTerm))
                val optRef = Ref(optSym)
                val isDefined = Select.unique(optRef, "isDefined")
                val get = Select.unique(optRef, "get")
                innerWriteVal(get).map { writeTerm =>
                  Block(List(optDef), If(isDefined, writeTerm, wn())).asExprOf[Unit]
                }
              case s if s.endsWith(".List") =>
                val owner = Symbol.spliceOwner
                val lstSym = Symbol.newVal(owner, "_l", tpe, Flags.EmptyFlags, Symbol.noSymbol)
                val lstDef = ValDef(lstSym, Some(castTerm))
                val lstRef = Ref(lstSym)
                val isNull = Apply(Select.unique(lstRef, "=="), List(Literal(NullConstant())))
                val arrStart = Select.overloaded(outTerm, "writeArrayStart", Nil, Nil)
                val arrEnd = Select.overloaded(outTerm, "writeArrayEnd", Nil, Nil)
                val curSym = Symbol.newVal(owner, "_c", tpe, Flags.Mutable, Symbol.noSymbol)
                val curDef = ValDef(curSym, Some(lstRef))
                val curRef = Ref(curSym)
                val nonEmpty = Select.unique(curRef, "nonEmpty")
                val head = Select.unique(curRef, "head")
                val tail = Select.unique(curRef, "tail")
                innerWriteVal(head).map { writeHead =>
                  val whileBody = Block(List(writeHead), Assign(curRef, tail))
                  val whileLoop = While(nonEmpty, whileBody)
                  val elseBranch = Block(List(arrStart, curDef, whileLoop), arrEnd)
                  Block(List(lstDef), If(isNull, wn(), elseBranch)).asExprOf[Unit]
                }
              case _ => None
          case _ => None

    private def tryDirectWriteDropNull[T: Type](
      fa: Expr[T], names: Expr[Array[String]], idx: Expr[Int], out: Expr[JsonWriter]
    ): Option[Expr[Unit]] =
      val tpe0 = TypeRepr.of[T].dealias
      val tpe = if isOpaqueAlias(tpe0) then opaqueDealias(tpe0) else tpe0
      def castInt: Expr[Int] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[Int] } else fa.asExprOf[Int]
      def castLong: Expr[Long] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[Long] } else fa.asExprOf[Long]
      def castFloat: Expr[Float] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[Float] } else fa.asExprOf[Float]
      def castDouble: Expr[Double] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[Double] } else fa.asExprOf[Double]
      def castBoolean: Expr[Boolean] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[Boolean] } else fa.asExprOf[Boolean]
      def castShort: Expr[Short] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[Short] } else fa.asExprOf[Short]
      def castByte: Expr[Byte] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[Byte] } else fa.asExprOf[Byte]
      def castChar: Expr[Char] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[Char] } else fa.asExprOf[Char]
      def castString: Expr[String] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[String] } else fa.asExprOf[String]
      def castBigDecimal: Expr[BigDecimal] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[BigDecimal] } else fa.asExprOf[BigDecimal]
      def castBigInt: Expr[BigInt] = if isOpaqueAlias(tpe0) then '{ $fa.asInstanceOf[BigInt] } else fa.asExprOf[BigInt]
      if tpe =:= TypeRepr.of[Int] then Some('{ $out.writeNonEscapedAsciiKey($names($idx)); $out.writeVal(${castInt}) })
      else if tpe =:= TypeRepr.of[Long] then Some('{ $out.writeNonEscapedAsciiKey($names($idx)); $out.writeVal(${castLong}) })
      else if tpe =:= TypeRepr.of[Float] then Some('{ $out.writeNonEscapedAsciiKey($names($idx)); $out.writeVal(${castFloat}) })
      else if tpe =:= TypeRepr.of[Double] then Some('{ $out.writeNonEscapedAsciiKey($names($idx)); $out.writeVal(${castDouble}) })
      else if tpe =:= TypeRepr.of[Boolean] then Some('{ $out.writeNonEscapedAsciiKey($names($idx)); $out.writeVal(${castBoolean}) })
      else if tpe =:= TypeRepr.of[Short] then Some('{ $out.writeNonEscapedAsciiKey($names($idx)); $out.writeVal(${castShort}.toInt) })
      else if tpe =:= TypeRepr.of[Byte] then Some('{ $out.writeNonEscapedAsciiKey($names($idx)); $out.writeVal(${castByte}.toInt) })
      else if tpe =:= TypeRepr.of[Char] then Some('{ $out.writeNonEscapedAsciiKey($names($idx)); $out.writeVal(${castChar}.toString) })
      else if tpe =:= TypeRepr.of[String] then
        Some('{ val _v = ${castString}; if _v != null then { $out.writeNonEscapedAsciiKey($names($idx)); $out.writeVal(_v) } })
      else if tpe =:= TypeRepr.of[BigDecimal] then
        Some('{ val _v = ${castBigDecimal}; if _v != null then { $out.writeNonEscapedAsciiKey($names($idx)); $out.writeVal(_v) } })
      else if tpe =:= TypeRepr.of[BigInt] then
        Some('{ val _v = ${castBigInt}; if _v != null then { $out.writeNonEscapedAsciiKey($names($idx)); $out.writeVal(_v) } })
      else
        // Inline Option[prim] and List[prim] writes with drop-null to avoid virtual dispatch
        tpe match
          case AppliedType(tycon, List(innerArg)) =>
            val outTerm = out.asTerm
            val namesTerm = names.asTerm
            val idxTerm = idx.asTerm
            val castTerm = if isOpaqueAlias(tpe0) then
              TypeApply(Select.unique(fa.asTerm, "asInstanceOf"), List(Inferred(tpe)))
            else fa.asTerm
            def wv(arg: Term): Term = Select.overloaded(outTerm, "writeVal", Nil, List(arg))
            def writeKey(): Term = Select.overloaded(outTerm, "writeNonEscapedAsciiKey", Nil, List(
              Apply(Select.unique(namesTerm, "apply"), List(idxTerm))))
            def innerWriteVal(valTerm: Term): Option[Term] =
              val ei = innerArg.dealias
              if ei =:= TypeRepr.of[Int] then Some(wv(valTerm))
              else if ei =:= TypeRepr.of[Long] then Some(wv(valTerm))
              else if ei =:= TypeRepr.of[Float] then Some(wv(valTerm))
              else if ei =:= TypeRepr.of[Double] then Some(wv(valTerm))
              else if ei =:= TypeRepr.of[Boolean] then Some(wv(valTerm))
              else if ei =:= TypeRepr.of[Short] then Some(wv(Select.unique(valTerm, "toInt")))
              else if ei =:= TypeRepr.of[Byte] then Some(wv(Select.unique(valTerm, "toInt")))
              else if ei =:= TypeRepr.of[Char] then Some(wv(Select.overloaded(valTerm, "toString", Nil, Nil)))
              else if ei =:= TypeRepr.of[String] then Some(wv(valTerm))
              else if ei =:= TypeRepr.of[BigDecimal] then Some(wv(valTerm))
              else if ei =:= TypeRepr.of[BigInt] then Some(wv(valTerm))
              else None
            tycon.typeSymbol.fullName match
              case "scala.Option" =>
                val owner = Symbol.spliceOwner
                val optSym = Symbol.newVal(owner, "_o", tpe, Flags.EmptyFlags, Symbol.noSymbol)
                val optDef = ValDef(optSym, Some(castTerm))
                val optRef = Ref(optSym)
                val isDefined = Select.unique(optRef, "isDefined")
                val get = Select.unique(optRef, "get")
                innerWriteVal(get).map { writeTerm =>
                  val thenBranch = Block(List(writeKey()), writeTerm)
                  Block(List(optDef), If(isDefined, thenBranch, Literal(UnitConstant()))).asExprOf[Unit]
                }
              case s if s.endsWith(".List") =>
                val owner = Symbol.spliceOwner
                val lstSym = Symbol.newVal(owner, "_l", tpe, Flags.EmptyFlags, Symbol.noSymbol)
                val lstDef = ValDef(lstSym, Some(castTerm))
                val lstRef = Ref(lstSym)
                val isNotNull = Select.unique(Apply(Select.unique(lstRef, "=="), List(Literal(NullConstant()))), "unary_!")
                val arrStart = Select.overloaded(outTerm, "writeArrayStart", Nil, Nil)
                val arrEnd = Select.overloaded(outTerm, "writeArrayEnd", Nil, Nil)
                val curSym = Symbol.newVal(owner, "_c", tpe, Flags.Mutable, Symbol.noSymbol)
                val curDef = ValDef(curSym, Some(lstRef))
                val curRef = Ref(curSym)
                val nonEmpty = Select.unique(curRef, "nonEmpty")
                val head = Select.unique(curRef, "head")
                val tail = Select.unique(curRef, "tail")
                innerWriteVal(head).map { writeHead =>
                  val whileBody = Block(List(writeHead), Assign(curRef, tail))
                  val whileLoop = While(nonEmpty, whileBody)
                  val thenBranch = Block(List(writeKey(), arrStart, curDef, whileLoop), arrEnd)
                  Block(List(lstDef), If(isNotNull, thenBranch, Literal(UnitConstant()))).asExprOf[Unit]
                }
              case _ => None
          case _ => None

    // === Sum derivation (configured) ===

    /** Generate charBuf-based dispatch for configured sum type decode (external tagging).
      * Labels are runtime-transformed, so uses linear chain comparing against labels array elements.
      */
    private def generateConfiguredSumDecodeBody[S: Type](
      inExpr: Expr[JsonReader],
      labelsExpr: Expr[Array[String]],
      codecsExpr: Expr[Array[JsonValueCodec[Any]]],
      numVariants: Int
    ): Expr[S] =
      val errorExpr = '{ $inExpr.decodeError(s"expected one of: ${$labelsExpr.mkString(", ")}") }

      val owner = Symbol.spliceOwner
      val klSym = Symbol.newVal(owner, "_kl", TypeRepr.of[Int], Flags.EmptyFlags, Symbol.noSymbol)
      val klDef = ValDef(klSym, Some('{ $inExpr.readKeyAsCharBuf() }.asTerm))

      // Linear chain (labels are runtime, can't hash at compile time)
      val dispatchTerm = (0 until numVariants).foldRight[Term](errorExpr.asTerm) { case (idx, elseBranch) =>
        val idxE = Expr(idx)
        val cond = '{ $inExpr.isCharBufEqualsTo(${Ref(klSym).asExprOf[Int]}, $labelsExpr($idxE)) }.asTerm
        val decode = '{ $codecsExpr($idxE).decodeValue($inExpr, $codecsExpr($idxE).nullValue).asInstanceOf[S] }.asTerm
        If(cond, decode, elseBranch)
      }

      Block(List(klDef), dispatchTerm).asExprOf[S]

    private def deriveSum[S: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.SumOf[S]],
      selfRef: Expr[JsonValueCodec[A]]
    ): Expr[JsonValueCodec[S]] =
      val cases = resolveFields[Types, Labels](selfRef)

      // Detect sub-traits
      val casesWithSubTrait = cases.map { case (label, tpe, codec) =>
        val isSub = tpe match
          case '[t] =>
            val ck = cheapTypeKey(TypeRepr.of[t].dealias)
            !summonedKeys.contains(ck) && Expr.summon[Mirror.SumOf[t]].isDefined
        (label, tpe, codec, isSub)
      }

      val hasSubTraits = casesWithSubTrait.exists(_._4)

      if !hasSubTraits then
        val labelsExpr = Expr(cases.map(_._1).toArray)
        val codecExprs = cases.map { case (_, tpe, codec) =>
          tpe match
            case '[t] => '{ ${codec.asInstanceOf[Expr[JsonValueCodec[t]]]}.asInstanceOf[JsonValueCodec[Any]] }
        }
        val codecsArrayExpr = '{ Array(${Varargs(codecExprs)}*) }
        val n = cases.length
        val extTagDecodeFnExpr = '{ (in: JsonReader, labels: Array[String], codecs: Array[JsonValueCodec[Any]]) =>
          ${ generateConfiguredSumDecodeBody[S]('in, 'labels, 'codecs, n) }
        }
        '{ JsoniterRuntime.configuredSumCodec[S]($mirror, $labelsExpr, $conf.transformConstructorNames, $conf.discriminator, () => $codecsArrayExpr, $conf.strictDecoding, $extTagDecodeFnExpr) }
      else
        val directLabelsExpr = Expr(cases.map(_._1).toArray)
        val isSubTraitExpr = Expr(casesWithSubTrait.map(_._4).toArray)
        val directCodecExprs = casesWithSubTrait.map { case (_, tpe, codec, _) =>
          tpe match
            case '[t] => '{ ${codec.asInstanceOf[Expr[JsonValueCodec[t]]]}.asInstanceOf[JsonValueCodec[Any]] }
        }
        val directCodecsArrayExpr = '{ Array(${Varargs(directCodecExprs)}*) }

        val allLeaves = casesWithSubTrait.flatMap { case (label, tpe, codec, isSub) =>
          if isSub then
            tpe match
              case '[t] => collectLeafVariants[t](selfRef)
          else
            List((label, tpe, codec))
        }.distinctBy(_._1)

        val allLeafLabelsExpr = Expr(allLeaves.map(_._1).toArray)
        val allLeafCodecExprs = allLeaves.map { case (_, tpe, codec) =>
          tpe match
            case '[t] => '{ ${codec.asInstanceOf[Expr[JsonValueCodec[t]]]}.asInstanceOf[JsonValueCodec[Any]] }
        }
        val allLeafCodecsArrayExpr = '{ Array(${Varargs(allLeafCodecExprs)}*) }

        val nLeaves = allLeaves.length
        val extTagDecodeFnExpr = '{ (in: JsonReader, labels: Array[String], codecs: Array[JsonValueCodec[Any]]) =>
          ${ generateConfiguredSumDecodeBody[S]('in, 'labels, 'codecs, nLeaves) }
        }

        '{ JsoniterRuntime.configuredSumCodecWithSubTraits[S](
          $mirror, $directLabelsExpr, $isSubTraitExpr,
          $conf.transformConstructorNames, $conf.discriminator,
          () => $directCodecsArrayExpr,
          $allLeafLabelsExpr, () => $allLeafCodecsArrayExpr, $conf.strictDecoding, $extTagDecodeFnExpr) }

    // === Sub-trait leaf collection ===

    private def collectLeafVariants[T: Type](
      selfRef: Expr[JsonValueCodec[A]]
    ): List[(String, Type[?], Expr[JsonValueCodec[?]])] =
      Expr.summon[Mirror.SumOf[T]] match
        case Some(subMirror) =>
          subMirror match
            case '{ $sm: Mirror.SumOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
              collectLeaves[types, labels](selfRef)
        case None =>
          report.errorAndAbort(s"Expected Mirror.SumOf for sub-trait ${Type.show[T]}")

    private def collectLeaves[Types: Type, Labels: Type](
      selfRef: Expr[JsonValueCodec[A]]
    ): List[(String, Type[?], Expr[JsonValueCodec[?]])] =
      (Type.of[Types], Type.of[Labels]) match
        case ('[EmptyTuple], '[EmptyTuple]) => Nil
        case ('[t *: ts], '[label *: ls]) =>
          val labelStr = Type.of[label] match
            case '[l] =>
              Type.valueOfConstant[l].getOrElse(
                report.errorAndAbort(s"Expected literal string type")
              ).toString
          val ck = cheapTypeKey(TypeRepr.of[t].dealias)
          val isNestedSub = !summonedKeys.contains(ck) && Expr.summon[Mirror.SumOf[t]].isDefined
          val casesForThis =
            if isNestedSub then collectLeafVariants[t](selfRef)
            else
              val codec = resolveOneCodec[t](selfRef)
              List((labelStr, Type.of[t], codec))
          casesForThis ++ collectLeaves[ts, ls](selfRef)
        case _ => report.errorAndAbort("Mismatched Types and Labels tuple lengths")

    // === Defaults resolution ===

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
          val primaryCtor = sym.primaryConstructor
          val paramCount = primaryCtor.paramSymss
            .find(_.headOption.exists(s => !s.isTypeParam))
            .map(_.size)
            .getOrElse(0)
          (1 to paramCount).toList.map { idx =>
            val methodName = s"$$lessinit$$greater$$default$$$idx"
            val found = companion.declaredMethod(methodName).headOption
            found.map { method =>
              val select = Ref(companion).select(method)
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

    // === Field/variant resolution (reused from SanelyJsoniter pattern) ===

    private def resolveFields[Types: Type, Labels: Type](
      selfRef: Expr[JsonValueCodec[A]]
    ): List[(String, Type[?], Expr[JsonValueCodec[?]])] =
      (Type.of[Types], Type.of[Labels]) match
        case ('[EmptyTuple], '[EmptyTuple]) => Nil
        case ('[t *: ts], '[label *: ls]) =>
          val labelStr = Type.of[label] match
            case '[l] =>
              Type.valueOfConstant[l].getOrElse(
                report.errorAndAbort(s"Expected literal string type")
              ).toString
          val codec = resolveOneCodec[t](selfRef)
          (labelStr, Type.of[t], codec) :: resolveFields[ts, ls](selfRef)
        case _ => report.errorAndAbort("Mismatched Types and Labels tuple lengths")

    private def resolveOneCodec[T: Type](
      selfRef: Expr[JsonValueCodec[A]]
    ): Expr[JsonValueCodec[T]] =
      val tpe = TypeRepr.of[T]

      if tpe =:= selfType then
        return selfRef.asInstanceOf[Expr[JsonValueCodec[T]]]

      val dealiased = tpe.dealias
      val effectiveType = if isOpaqueAlias(dealiased) then opaqueDealias(dealiased) else dealiased
      val cacheKey = cheapTypeKey(effectiveType)

      exprCache.get(cacheKey) match
        case Some(cached) => return cached.asInstanceOf[Expr[JsonValueCodec[T]]]
        case None => ()

      if !negativeBuiltinCache.contains(cacheKey) then
        tryResolveBuiltin[T](dealiased, Some(selfRef)) match
          case Some(codec) =>
            exprCache(cacheKey) = codec
            return codec
          case None =>
            negativeBuiltinCache += cacheKey

      if containsType(dealiased, selfType) then
        val resolved = constructRecursiveCodec[T](dealiased, selfRef)
        exprCache(cacheKey) = resolved
        return resolved

      val resolved: Expr[JsonValueCodec[T]] =
        Expr.summonIgnoring[JsonValueCodec[T]](cachedIgnoreSymbols*) match
          case Some(codec) =>
            summonedKeys += cacheKey
            codec
          case None =>
            Expr.summon[Mirror.Of[T]] match
              case Some(mirrorExpr) =>
                mirrorExpr match
                  case '{ $m: Mirror.ProductOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                    deriveProduct[T, types, labels](selfRef)
                  case '{ $m: Mirror.SumOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                    deriveSum[T, types, labels](m, selfRef)
              case None =>
                report.errorAndAbort(s"Cannot derive JsonValueCodec for ${Type.show[T]}: no implicit JsonValueCodec and no Mirror available")
      exprCache(cacheKey) = resolved
      resolved

    // === Builtin type resolution ===

    private def isTuple(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Tuple]

    private def tryResolveTuple[T: Type](dealiased: TypeRepr, selfRefOpt: Option[Expr[JsonValueCodec[A]]]): Option[Expr[JsonValueCodec[T]]] =
      def resolveArg(arg: TypeRepr): Expr[JsonValueCodec[?]] =
        val argKey = cheapTypeKey(arg)
        exprCache.get(argKey).map(_.asInstanceOf[Expr[JsonValueCodec[?]]]).getOrElse {
          resolvePrimCodec(arg.dealias).getOrElse {
            arg.asType match
              case '[a] =>
                Expr.summonIgnoring[JsonValueCodec[a]](cachedIgnoreSymbols*).getOrElse {
                  tryResolveBuiltin[a](arg.dealias, selfRefOpt).getOrElse {
                    selfRefOpt match
                      case Some(selfRef) => resolveOneCodec[a](selfRef)
                      case None => report.errorAndAbort(s"Cannot resolve JsonValueCodec for tuple element ${Type.show[a]}")
                  }
                }
          }
        }
      dealiased match
        case AppliedType(_, args) if isTuple(dealiased) =>
          args.length match
            case 1 =>
              args.head.asType match
                case '[a] =>
                  val ca = resolveArg(args(0)).asInstanceOf[Expr[JsonValueCodec[a]]]
                  Some('{ Codecs.tuple1[a]($ca) }.asInstanceOf[Expr[JsonValueCodec[T]]])
            case 2 =>
              (args(0).asType, args(1).asType) match
                case ('[a], '[b]) =>
                  val ca = resolveArg(args(0)).asInstanceOf[Expr[JsonValueCodec[a]]]
                  val cb = resolveArg(args(1)).asInstanceOf[Expr[JsonValueCodec[b]]]
                  Some('{ Codecs.tuple2[a, b]($ca, $cb) }.asInstanceOf[Expr[JsonValueCodec[T]]])
                case _ => None
            case 3 =>
              (args(0).asType, args(1).asType, args(2).asType) match
                case ('[a], '[b], '[c]) =>
                  val ca = resolveArg(args(0)).asInstanceOf[Expr[JsonValueCodec[a]]]
                  val cb = resolveArg(args(1)).asInstanceOf[Expr[JsonValueCodec[b]]]
                  val cc = resolveArg(args(2)).asInstanceOf[Expr[JsonValueCodec[c]]]
                  Some('{ Codecs.tuple3[a, b, c]($ca, $cb, $cc) }.asInstanceOf[Expr[JsonValueCodec[T]]])
                case _ => None
            case 4 =>
              (args(0).asType, args(1).asType, args(2).asType, args(3).asType) match
                case ('[a], '[b], '[c], '[d]) =>
                  val ca = resolveArg(args(0)).asInstanceOf[Expr[JsonValueCodec[a]]]
                  val cb = resolveArg(args(1)).asInstanceOf[Expr[JsonValueCodec[b]]]
                  val cc = resolveArg(args(2)).asInstanceOf[Expr[JsonValueCodec[c]]]
                  val cd = resolveArg(args(3)).asInstanceOf[Expr[JsonValueCodec[d]]]
                  Some('{ Codecs.tuple4[a, b, c, d]($ca, $cb, $cc, $cd) }.asInstanceOf[Expr[JsonValueCodec[T]]])
                case _ => None
            case 5 =>
              (args(0).asType, args(1).asType, args(2).asType, args(3).asType, args(4).asType) match
                case ('[a], '[b], '[c], '[d], '[e]) =>
                  val ca = resolveArg(args(0)).asInstanceOf[Expr[JsonValueCodec[a]]]
                  val cb = resolveArg(args(1)).asInstanceOf[Expr[JsonValueCodec[b]]]
                  val cc = resolveArg(args(2)).asInstanceOf[Expr[JsonValueCodec[c]]]
                  val cd = resolveArg(args(3)).asInstanceOf[Expr[JsonValueCodec[d]]]
                  val ce = resolveArg(args(4)).asInstanceOf[Expr[JsonValueCodec[e]]]
                  Some('{ Codecs.tuple5[a, b, c, d, e]($ca, $cb, $cc, $cd, $ce) }.asInstanceOf[Expr[JsonValueCodec[T]]])
                case _ => None
            case n if n >= 6 && n <= 22 =>
              val codecExprs = args.map { arg =>
                val c = resolveArg(arg)
                '{ $c.asInstanceOf[JsonValueCodec[Any]] }
              }
              val codecArray = '{ Array[JsonValueCodec[Any]](${Varargs(codecExprs)}*) }
              Some('{ Codecs.tupleGeneric($codecArray) }.asInstanceOf[Expr[JsonValueCodec[T]]])
            case _ => None
        case _ => None

    private def tryResolveBuiltin[T: Type](dealiased: TypeRepr, selfRefOpt: Option[Expr[JsonValueCodec[A]]] = None): Option[Expr[JsonValueCodec[T]]] =
      resolvePrimCodec(dealiased).map(_.asInstanceOf[Expr[JsonValueCodec[T]]]).orElse {
        tryResolveTuple[T](dealiased, selfRefOpt)
      }.orElse {
        val resolveTarget = if isOpaqueAlias(dealiased) then opaqueDealias(dealiased) else dealiased
        resolveTarget match
          case AppliedType(tycon, List(arg)) if !isTuple(resolveTarget) =>
            val argKey = cheapTypeKey(arg)
            val innerOpt =
              if negativeBuiltinCache.contains(argKey) then exprCache.get(argKey)
              else resolvePrimCodec(arg.dealias).orElse(exprCache.get(argKey))
            val innerResolved = innerOpt.orElse {
              arg.asType match
                case '[a] =>
                  Expr.summonIgnoring[JsonValueCodec[a]](cachedIgnoreSymbols*).map { codec =>
                    exprCache(argKey) = codec
                    codec
                  }
            }.orElse {
              arg.asType match
                case '[a] =>
                  tryResolveBuiltin[a](arg.dealias, selfRefOpt).map { codec =>
                    exprCache(argKey) = codec
                    codec
                  }
            }.orElse {
              selfRefOpt.flatMap { selfRef =>
                arg.asType match
                  case '[a] =>
                    val codec = resolveOneCodec[a](selfRef)
                    exprCache(argKey) = codec
                    Some(codec)
              }
            }
            innerResolved.flatMap { innerCodec =>
              arg.asType match
                case '[a] =>
                  val inner = innerCodec.asInstanceOf[Expr[JsonValueCodec[a]]]
                  buildContainerCodec[T, a](tycon, inner)
            }
          case AppliedType(tycon, List(keyArg, valArg))
            if tycon.typeSymbol.fullName.endsWith(".Map") =>
            // Check for external given for the whole Map type first
            val wholeMapSummoned = Expr.summonIgnoring[JsonValueCodec[T]](cachedIgnoreSymbols*)
            wholeMapSummoned.orElse {
              val valKey = cheapTypeKey(valArg)
              val valOpt = (if negativeBuiltinCache.contains(valKey) then exprCache.get(valKey)
                           else resolvePrimCodec(valArg.dealias).orElse(exprCache.get(valKey)))
              val valResolved = valOpt.orElse {
                valArg.asType match
                  case '[v] =>
                    Expr.summonIgnoring[JsonValueCodec[v]](cachedIgnoreSymbols*).map { codec =>
                      exprCache(valKey) = codec
                      codec
                    }
              }.orElse {
                valArg.asType match
                  case '[v] =>
                    tryResolveBuiltin[v](valArg.dealias, selfRefOpt).map { codec =>
                      exprCache(valKey) = codec
                      codec
                    }
              }.orElse {
                selfRefOpt.flatMap { selfRef =>
                  valArg.asType match
                    case '[v] =>
                      val codec = resolveOneCodec[v](selfRef)
                      exprCache(valKey) = codec
                      Some(codec)
                }
              }
              valResolved.flatMap { valCodec =>
                valArg.asType match
                  case '[v] =>
                    val vc = valCodec.asInstanceOf[Expr[JsonValueCodec[v]]]
                    if keyArg =:= TypeRepr.of[String] then
                      Some('{ Codecs.stringMap[v]($vc) }.asInstanceOf[Expr[JsonValueCodec[T]]])
                    else
                      keyArg.asType match
                        case '[k] =>
                          Expr.summon[KeyCodec[k]].map { kc =>
                            '{ Codecs.map[k, v]($kc, $vc) }.asInstanceOf[Expr[JsonValueCodec[T]]]
                          }
              }
            }
          case AppliedType(tycon, List(leftArg, rightArg))
            if tycon.typeSymbol.fullName == "scala.util.Either" =>
            def resolveArg(arg: TypeRepr): Option[Expr[JsonValueCodec[?]]] =
              val argKey = cheapTypeKey(arg)
              val fromCache: Option[Expr[JsonValueCodec[?]]] =
                if negativeBuiltinCache.contains(argKey) then exprCache.get(argKey).map(_.asInstanceOf[Expr[JsonValueCodec[?]]])
                else resolvePrimCodec(arg.dealias).orElse(exprCache.get(argKey).map(_.asInstanceOf[Expr[JsonValueCodec[?]]]))
              fromCache.orElse {
                arg.asType match
                  case '[a] =>
                    Expr.summonIgnoring[JsonValueCodec[a]](cachedIgnoreSymbols*).map { codec =>
                      exprCache(argKey) = codec
                      codec: Expr[JsonValueCodec[?]]
                    }
              }.orElse {
                arg.asType match
                  case '[a] =>
                    tryResolveBuiltin[a](arg.dealias, selfRefOpt).map { codec =>
                      exprCache(argKey) = codec
                      codec: Expr[JsonValueCodec[?]]
                    }
              }.orElse {
                selfRefOpt.flatMap { selfRef =>
                  arg.asType match
                    case '[a] =>
                      val codec = resolveOneCodec[a](selfRef)
                      exprCache(argKey) = codec
                      Some(codec: Expr[JsonValueCodec[?]])
                }
              }
            (resolveArg(leftArg), resolveArg(rightArg)) match
              case (Some(lc), Some(rc)) =>
                (leftArg.asType, rightArg.asType) match
                  case ('[l], '[r]) =>
                    val leftCodec = lc.asInstanceOf[Expr[JsonValueCodec[l]]]
                    val rightCodec = rc.asInstanceOf[Expr[JsonValueCodec[r]]]
                    Some('{ Codecs.either[l, r]($leftCodec, $rightCodec) }.asInstanceOf[Expr[JsonValueCodec[T]]])
                  case _ => None
              case _ => None
          case _ => None
      }

    private def resolvePrimCodec(tpe: TypeRepr): Option[Expr[JsonValueCodec[?]]] =
      def tryPrim(t: TypeRepr): Option[Expr[JsonValueCodec[?]]] =
        if t =:= TypeRepr.of[String] then Some('{ Codecs.string })
        else if t =:= TypeRepr.of[Int] then Some('{ Codecs.int })
        else if t =:= TypeRepr.of[Long] then Some('{ Codecs.long })
        else if t =:= TypeRepr.of[Double] then Some('{ Codecs.double })
        else if t =:= TypeRepr.of[Float] then Some('{ Codecs.float })
        else if t =:= TypeRepr.of[Boolean] then Some('{ Codecs.boolean })
        else if t =:= TypeRepr.of[Short] then Some('{ Codecs.short })
        else if t =:= TypeRepr.of[Byte] then Some('{ Codecs.byte })
        else if t =:= TypeRepr.of[BigDecimal] then Some('{ Codecs.bigDecimal })
        else if t =:= TypeRepr.of[BigInt] then Some('{ Codecs.bigInt })
        else if t =:= TypeRepr.of[Char] then Some('{ Codecs.char })
        else None
      tryPrim(tpe).orElse {
        if isOpaqueAlias(tpe) then tryPrim(opaqueDealias(tpe))
        else None
      }

    private def buildContainerCodec[T: Type, A: Type](
      tycon: TypeRepr,
      innerCodec: Expr[JsonValueCodec[A]]
    ): Option[Expr[JsonValueCodec[T]]] =
      tycon.typeSymbol.fullName match
        case "scala.Option" =>
          Some('{ Codecs.option[A]($innerCodec) }.asInstanceOf[Expr[JsonValueCodec[T]]])
        case s if s.endsWith(".List") =>
          Some('{ Codecs.list[A]($innerCodec) }.asInstanceOf[Expr[JsonValueCodec[T]]])
        case s if s.endsWith(".Vector") =>
          Some('{ Codecs.vector[A]($innerCodec) }.asInstanceOf[Expr[JsonValueCodec[T]]])
        case s if s.endsWith(".Set") =>
          Some('{ Codecs.set[A]($innerCodec) }.asInstanceOf[Expr[JsonValueCodec[T]]])
        case s if s.endsWith(".Seq") =>
          Some('{ Codecs.seq[A]($innerCodec) }.asInstanceOf[Expr[JsonValueCodec[T]]])
        case s if s.endsWith(".IndexedSeq") =>
          Some('{ Codecs.indexedSeq[A]($innerCodec) }.asInstanceOf[Expr[JsonValueCodec[T]]])
        case s if s.endsWith(".Iterable") =>
          Some('{ Codecs.iterable[A]($innerCodec) }.asInstanceOf[Expr[JsonValueCodec[T]]])
        case "scala.Array" =>
          Expr.summon[scala.reflect.ClassTag[A]] match
            case Some(ct) =>
              Some('{ Codecs.array[A]($innerCodec)(using $ct) }.asInstanceOf[Expr[JsonValueCodec[T]]])
            case None => None
        case _ => None

    // === Recursive types ===

    private def containsType(tpe: TypeRepr, target: TypeRepr): Boolean =
      val d = tpe.dealias
      if d =:= target then true
      else d match
        case AppliedType(_, args) => args.exists(arg => containsType(arg, target))
        case AndType(l, r) => containsType(l, target) || containsType(r, target)
        case OrType(l, r) => containsType(l, target) || containsType(r, target)
        case _ => false

    private def constructRecursiveCodec[T: Type](
      tpe: TypeRepr,
      selfRef: Expr[JsonValueCodec[A]]
    ): Expr[JsonValueCodec[T]] =
      def trySummon: Option[Expr[JsonValueCodec[T]]] = Expr.summonIgnoring[JsonValueCodec[T]](cachedIgnoreSymbols*)

      tpe match
        case AppliedType(tycon, List(arg)) if arg =:= selfType =>
          arg.asType match
            case '[a] =>
              val innerCodec = selfRef.asInstanceOf[Expr[JsonValueCodec[a]]]
              buildContainerCodec[T, a](tycon, innerCodec) match
                case Some(codec) => codec
                case None => trySummon.getOrElse(
                  report.errorAndAbort(s"Cannot derive JsonValueCodec for recursive type in container ${tycon.typeSymbol.fullName}[${Type.show[a]}]"))
        case AppliedType(tycon, List(arg)) if containsType(arg, selfType) =>
          arg.asType match
            case '[a] =>
              val innerCodec = constructRecursiveCodec[a](arg, selfRef)
              buildContainerCodec[T, a](tycon, innerCodec) match
                case Some(codec) => codec
                case None => trySummon.getOrElse(
                  report.errorAndAbort(s"Cannot derive JsonValueCodec for recursive type in container ${tycon.typeSymbol.fullName}[${Type.show[a]}]"))
        case _ =>
          trySummon.getOrElse(
            report.errorAndAbort(s"Cannot derive JsonValueCodec for recursive type: ${Type.show[T]}"))

    // === Ignore symbols ===

    private lazy val cachedIgnoreSymbols: List[Symbol] =
      val buf = List.newBuilder[Symbol]
      try
        buf += Symbol.requiredModule("sanely.jsoniter.auto").methodMember("autoCodec").head
      catch case _: Exception => ()
      try
        buf += Symbol.requiredModule("sanely.jsoniter.configured.auto").methodMember("autoConfiguredCodec").head
      catch case _: Exception => ()
      buf.result()

    // === Opaque type helpers ===

    private def isOpaqueAlias(tpe: TypeRepr): Boolean =
      tpe match
        case tr: TypeRef => tr.isOpaqueAlias
        case _ => false

    @scala.annotation.tailrec
    private def opaqueDealias(tpe: TypeRepr): TypeRepr =
      tpe match
        case tr: TypeRef if tr.isOpaqueAlias => opaqueDealias(tr.translucentSuperType.dealias)
        case _ => tpe

    // === Utilities ===

    private def cheapTypeKey(tpe: TypeRepr): String =
      def go(t: TypeRepr): String =
        val d = t.dealias
        d match
          case AppliedType(tycon, args) =>
            val base = tycon.typeSymbol.fullName
            args.map(go).mkString(s"$base[", ",", "]")
          case _ if d.typeSymbol != Symbol.noSymbol =>
            d.typeSymbol.fullName
          case _ => d.show
      go(tpe)

    private def nullValueExpr[T: Type]: Expr[Any] =
      val tpe = TypeRepr.of[T].dealias
      if tpe =:= TypeRepr.of[Int] then '{ 0: Any }
      else if tpe =:= TypeRepr.of[Long] then '{ 0L: Any }
      else if tpe =:= TypeRepr.of[Double] then '{ 0.0: Any }
      else if tpe =:= TypeRepr.of[Float] then '{ 0.0f: Any }
      else if tpe =:= TypeRepr.of[Boolean] then '{ false: Any }
      else if tpe =:= TypeRepr.of[Short] then '{ (0: Short): Any }
      else if tpe =:= TypeRepr.of[Byte] then '{ (0: Byte): Any }
      else if tpe =:= TypeRepr.of[Char] then '{ (0: Char): Any }
      else if tpe <:< TypeRepr.of[Option[?]] then '{ None: Any }
      else if isOpaqueAlias(tpe) then
        val underlying = opaqueDealias(tpe)
        underlying.asType match
          case '[u] => '{ ${nullValueExpr[u]}.asInstanceOf[Any] }
      else '{ null: Any }
