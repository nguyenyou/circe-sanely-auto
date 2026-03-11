package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, JsonWriter}
import scala.deriving.Mirror
import scala.collection.mutable
import scala.compiletime.*
import scala.quoted.*

object SanelyJsoniter:

  inline def derived[A](using inline m: Mirror.Of[A]): JsonValueCodec[A] =
    ${ deriveMacro[A]('m) }

  private def deriveMacro[A: Type](mirror: Expr[Mirror.Of[A]])(using Quotes): Expr[JsonValueCodec[A]] =
    val helper = new CodecDerivation[A]
    helper.derive(mirror)

  private class CodecDerivation[A: Type](using val quotes: Quotes):
    import quotes.reflect.*

    val selfType: TypeRepr = TypeRepr.of[A]
    private val exprCache = mutable.Map.empty[String, Expr[?]]
    private val negativeBuiltinCache = mutable.Set.empty[String]
    private val summonedKeys = mutable.Set.empty[String]

    def derive(mirror: Expr[Mirror.Of[A]]): Expr[JsonValueCodec[A]] =
      // Check if A is a known container type (Option, List, etc.) before Mirror derivation
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

    // === Product derivation ===

    private def deriveProduct[P: Type, Types: Type, Labels: Type](
      selfRef: Expr[JsonValueCodec[A]]
    ): Expr[JsonValueCodec[P]] =
      val fields = resolveFields[Types, Labels](selfRef)
      val namesExpr = Expr(fields.map(_._1).toArray)
      val codecExprs = fields.map { case (_, tpe, codec) =>
        tpe match
          case '[t] => '{ ${codec.asInstanceOf[Expr[JsonValueCodec[t]]]}.asInstanceOf[JsonValueCodec[Any]] }
      }
      val codecsArrayExpr = '{ Array(${Varargs(codecExprs)}*) }
      val nullValueExprs = fields.map { case (_, tpe, _) =>
        tpe match
          case '[t] => nullValueExpr[t]
      }
      val nullValuesExpr = '{ Array(${Varargs(nullValueExprs)}*) }

      val encodeFnExpr = '{ (x: P, codecs: Array[JsonValueCodec[Any]], out: JsonWriter) =>
        ${ generateFieldWrites[P]('x, 'codecs, 'out, fields) }
      }

      val decodeFnExpr = '{ (in: JsonReader, codecs: Array[JsonValueCodec[Any]]) =>
        ${ generateDecodeBody[P]('in, 'codecs, fields) }
      }

      '{ JsoniterRuntime.productCodec[P]($namesExpr, () => $codecsArrayExpr, $nullValuesExpr, $encodeFnExpr, $decodeFnExpr) }

    private def generateFieldWrites[P: Type](
      x: Expr[P], codecs: Expr[Array[JsonValueCodec[Any]]], out: Expr[JsonWriter],
      fields: List[(String, Type[?], Expr[JsonValueCodec[?]])]
    ): Expr[Unit] =
      val xTerm = x.asTerm
      val codecsTerm = codecs.asTerm
      val outTerm = out.asTerm
      val stmts = fields.zipWithIndex.flatMap { case ((name, tpe, _), idx) =>
        tpe match
          case '[t] =>
            val fieldAccess = Select.unique(xTerm, name)
            val writeKey = callMethod(outTerm, "writeNonEscapedAsciiKey", List(Literal(StringConstant(name))))
            val writeValue = tryDirectWriteTerm[t](fieldAccess, outTerm).getOrElse {
              // codecs(idx).encodeValue(fieldAccess.asInstanceOf[Any], out)
              val codecAtIdx = Apply(Select.unique(codecsTerm, "apply"), List(Literal(IntConstant(idx))))
              callMethod(codecAtIdx, "encodeValue", List(
                TypeApply(Select.unique(fieldAccess, "asInstanceOf"), List(Inferred(TypeRepr.of[Any]))),
                outTerm
              ))
            }
            List(writeKey, writeValue)
      }
      if stmts.isEmpty then '{ () }
      else
        Block(stmts.init.toList, stmts.last).asExprOf[Unit]

    private def callMethod(receiver: Term, name: String, args: List[Term]): Term =
      Select.overloaded(receiver, name, Nil, args)

    private def tryDirectReadTerm[T: Type](inTerm: Term): Option[Term] =
      val tpe0 = TypeRepr.of[T].dealias
      val needsCast = isOpaqueAlias(tpe0)
      val tpe = if needsCast then opaqueDealias(tpe0) else tpe0
      def cast(t: Term): Term =
        if needsCast then TypeApply(Select.unique(t, "asInstanceOf"), List(Inferred(TypeRepr.of[T])))
        else t
      if tpe =:= TypeRepr.of[Int] then Some(cast(callMethod(inTerm, "readInt", Nil)))
      else if tpe =:= TypeRepr.of[Long] then Some(cast(callMethod(inTerm, "readLong", Nil)))
      else if tpe =:= TypeRepr.of[Double] then Some(cast(callMethod(inTerm, "readDouble", Nil)))
      else if tpe =:= TypeRepr.of[Float] then Some(cast(callMethod(inTerm, "readFloat", Nil)))
      else if tpe =:= TypeRepr.of[Boolean] then Some(cast(callMethod(inTerm, "readBoolean", Nil)))
      else if tpe =:= TypeRepr.of[Short] then
        Some(cast(Select.unique(callMethod(inTerm, "readInt", Nil), "toShort")))
      else if tpe =:= TypeRepr.of[Byte] then
        Some(cast(Select.unique(callMethod(inTerm, "readInt", Nil), "toByte")))
      else if tpe =:= TypeRepr.of[Char] then
        val owner = Symbol.spliceOwner
        val sSym = Symbol.newVal(owner, "_s", TypeRepr.of[String], Flags.EmptyFlags, Symbol.noSymbol)
        val sDef = ValDef(sSym, Some(callMethod(inTerm, "readString", List(Literal(NullConstant())))))
        val sRef = Ref(sSym)
        val isNull = Apply(Select.unique(sRef, "=="), List(Literal(NullConstant())))
        val isEmpty = callMethod(sRef, "isEmpty", Nil)
        val cond = Apply(Select.unique(isNull, "||"), List(isEmpty))
        val thenBr = Select.unique(Literal(IntConstant(0)), "toChar")
        val elseBr = callMethod(sRef, "charAt", List(Literal(IntConstant(0))))
        Some(cast(Block(List(sDef), If(cond, thenBr, elseBr))))
      else if tpe =:= TypeRepr.of[String] then
        Some(cast(callMethod(inTerm, "readString", List(Literal(NullConstant())))))
      else if tpe =:= TypeRepr.of[BigDecimal] then
        Some(cast(callMethod(inTerm, "readBigDecimal", List(Literal(NullConstant())))))
      else if tpe =:= TypeRepr.of[BigInt] then
        Some(cast(callMethod(inTerm, "readBigInt", List(Literal(NullConstant())))))
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
        else Literal(NullConstant())
      // Cast null/zero to the opaque type so var initialization type-checks
      if needsCast then TypeApply(Select.unique(raw, "asInstanceOf"), List(Inferred(tpe)))
      else raw

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

    private def generateDecodeBody[P: Type](
      inExpr: Expr[JsonReader],
      codecsExpr: Expr[Array[JsonValueCodec[Any]]],
      fields: List[(String, Type[?], Expr[JsonValueCodec[?]])]
    ): Expr[P] =
      // Convert to Terms to avoid ScopeException when macro is expanded from JAR
      val inTerm = inExpr.asTerm
      val codecsTerm = codecsExpr.asTerm
      val n = fields.length
      val byteCloseBrace = Literal(ByteConstant('}' .toByte))
      val byteComma = Literal(ByteConstant(',' .toByte))
      def inIsNextToken(b: Term): Term = callMethod(inTerm, "isNextToken", List(b))
      def inIsCurrentToken(b: Term): Term = callMethod(inTerm, "isCurrentToken", List(b))
      def notTerm(t: Term): Term = Select.unique(t, "unary_!")
      def inSkip(): Term = callMethod(inTerm, "skip", Nil)
      def inRollbackToken(): Term = callMethod(inTerm, "rollbackToken", Nil)
      def inReadKeyAsCharBuf(): Term = callMethod(inTerm, "readKeyAsCharBuf", Nil)
      def inObjectEndOrCommaError(): Term = callMethod(inTerm, "objectEndOrCommaError", Nil)

      if n == 0 then
        val constructTerm = buildDirectConstruct[P](Nil)
        val owner = Symbol.spliceOwner
        val cSym = Symbol.newVal(owner, "_c", TypeRepr.of[Boolean], Flags.Mutable, Symbol.noSymbol)
        val cDef = ValDef(cSym, Some(Literal(BooleanConstant(true))))
        val whileBody = Block(List(inReadKeyAsCharBuf(), inSkip()), Assign(Ref(cSym), inIsNextToken(byteComma)))
        val whileLoop = While(Ref(cSym), whileBody)
        val checkEnd = If(notTerm(inIsCurrentToken(byteCloseBrace)), inObjectEndOrCommaError(), Literal(UnitConstant()))
        val thenBranch = Block(List(inRollbackToken(), cDef, whileLoop, checkEnd), Literal(UnitConstant()))
        val outerIf = If(notTerm(inIsNextToken(byteCloseBrace)), thenBranch, Literal(UnitConstant()))
        return Block(List(outerIf), constructTerm).asExprOf[P]

      val owner = Symbol.spliceOwner

      // Create typed mutable var symbols for each field
      case class VarInfo(sym: Symbol, label: String, tpe: Type[?], idx: Int, defaultTerm: Term)
      val vars: List[VarInfo] = fields.zipWithIndex.map { case ((label, tpe, _), idx) =>
        tpe match
          case '[t] =>
            val sym = Symbol.newVal(owner, s"_f$idx", TypeRepr.of[t], Flags.Mutable, Symbol.noSymbol)
            val default = typedDefaultTerm(TypeRepr.of[t])
            VarInfo(sym, label, Type.of[t], idx, default)
      }

      // var declarations
      val varDefs: List[Statement] = vars.map(v => ValDef(v.sym, Some(v.defaultTerm)))

      // Build field matching dispatch, parameterized by keyLen ref.
      // <= 8 fields AND total chars <= 64: linear if-else chain (hashing overhead not worth it).
      // Otherwise: hash-based match on charBufToHashCode, collisions resolved by isCharBufEqualsTo.
      def buildMatchChain(keyLenRef: Term): Term =
        val skipTerm = inSkip()

        def buildReadAssign(vi: VarInfo, elseBranch: Term): Term =
          vi.tpe match
            case '[t] =>
              val cond = callMethod(inTerm, "isCharBufEqualsTo", List(keyLenRef, Literal(StringConstant(vi.label))))
              val readTerm: Term = tryDirectReadTerm[t](inTerm).getOrElse {
                val codecAtIdx = Apply(Select.unique(codecsTerm, "apply"), List(Literal(IntConstant(vi.idx))))
                val codecAtIdx2 = Apply(Select.unique(codecsTerm, "apply"), List(Literal(IntConstant(vi.idx))))
                val nullValue = Select.unique(codecAtIdx2, "nullValue") // parameterless def, no Apply
                val decoded = callMethod(codecAtIdx, "decodeValue", List(inTerm, nullValue))
                TypeApply(Select.unique(decoded, "asInstanceOf"), List(Inferred(TypeRepr.of[t])))
              }
              val assign = Assign(Ref(vi.sym), readTerm)
              If(cond, assign, elseBranch)

        def buildCollisionChain(fields: collection.Seq[VarInfo]): Term =
          fields.foldRight[Term](skipTerm)(buildReadAssign)

        val totalChars = vars.foldLeft(0)(_ + _.label.length)
        if vars.size <= 8 && totalChars <= 64 then
          buildCollisionChain(vars)
        else
          val hashOf = (vi: VarInfo) =>
            JsonReader.toHashCode(vi.label.toCharArray, vi.label.length)
          val grouped = groupByOrdered(vars)(hashOf)
          val cases = grouped.map { case (hash, fields) =>
            CaseDef(Literal(IntConstant(hash)), None, buildCollisionChain(fields))
          }
          val defaultCase = CaseDef(Wildcard(), None, skipTerm)
          val hashCall = callMethod(inTerm, "charBufToHashCode", List(keyLenRef))
          Match(hashCall, (cases :+ defaultCase).toList)

      // Build result: direct constructor call (no boxing, no intermediate allocations)
      val resultTerm = buildDirectConstruct[P](vars.map(v => Ref(v.sym)))

      // Build while loop: var _c = true; while(_c) { val _kl = ...; matchChain; _c = ... }
      val contSym = Symbol.newVal(owner, "_c", TypeRepr.of[Boolean], Flags.Mutable, Symbol.noSymbol)
      val contDef = ValDef(contSym, Some(Literal(BooleanConstant(true))))

      val klSym = Symbol.newVal(owner, "_kl", TypeRepr.of[Int], Flags.EmptyFlags, Symbol.noSymbol)
      val klDef = ValDef(klSym, Some(inReadKeyAsCharBuf()))

      val whileBody = Block(
        List(klDef, buildMatchChain(Ref(klSym))),
        Assign(Ref(contSym), inIsNextToken(byteComma))
      )
      val whileLoop = While(Ref(contSym), whileBody)

      val checkEnd = If(
        notTerm(inIsCurrentToken(byteCloseBrace)),
        inObjectEndOrCommaError(),
        Literal(UnitConstant())
      )

      val loopBlock = Block(
        List(inRollbackToken(), contDef, whileLoop, checkEnd),
        Literal(UnitConstant())
      )

      val outerIf = If(notTerm(inIsNextToken(byteCloseBrace)), loopBlock, Literal(UnitConstant()))

      Block(varDefs :+ outerIf, resultTerm).asExprOf[P]

    private def tryDirectWriteTerm[T: Type](fa: Term, outTerm: Term): Option[Term] =
      val tpe0 = TypeRepr.of[T].dealias
      val tpe = if isOpaqueAlias(tpe0) then opaqueDealias(tpe0) else tpe0
      // Cast field access to underlying type if opaque (so overload resolution works)
      val efa = if isOpaqueAlias(tpe0) then TypeApply(Select.unique(fa, "asInstanceOf"), List(Inferred(tpe))) else fa
      def writeVal(arg: Term): Term = Select.overloaded(outTerm, "writeVal", Nil, List(arg))
      def writeNull(): Term = callMethod(outTerm, "writeNull", Nil)
      def nullGuardWrite(valTerm: Term): Term =
        val owner = Symbol.spliceOwner
        val vSym = Symbol.newVal(owner, "_v", tpe, Flags.EmptyFlags, Symbol.noSymbol)
        val vDef = ValDef(vSym, Some(valTerm))
        val vRef = Ref(vSym)
        val cond = Apply(Select.unique(vRef, "=="), List(Literal(NullConstant())))
        Block(List(vDef), If(cond, writeNull(), writeVal(vRef)))

      if tpe =:= TypeRepr.of[Int] then Some(writeVal(efa))
      else if tpe =:= TypeRepr.of[Long] then Some(writeVal(efa))
      else if tpe =:= TypeRepr.of[Float] then Some(writeVal(efa))
      else if tpe =:= TypeRepr.of[Double] then Some(writeVal(efa))
      else if tpe =:= TypeRepr.of[Boolean] then Some(writeVal(efa))
      else if tpe =:= TypeRepr.of[Short] then Some(writeVal(Select.unique(efa, "toInt")))
      else if tpe =:= TypeRepr.of[Byte] then Some(writeVal(Select.unique(efa, "toInt")))
      else if tpe =:= TypeRepr.of[Char] then Some(writeVal(callMethod(efa, "toString", Nil)))
      else if tpe =:= TypeRepr.of[String] then Some(nullGuardWrite(efa))
      else if tpe =:= TypeRepr.of[BigDecimal] then Some(nullGuardWrite(efa))
      else if tpe =:= TypeRepr.of[BigInt] then Some(nullGuardWrite(efa))
      else None

    // === Sum derivation ===

    /** Generate charBuf-based dispatch for sum type decode (non-configured).
      * Labels are compile-time known strings. Uses linear chain for ≤8 variants / ≤64 total chars,
      * hash dispatch for larger ADTs.
      */
    private def generateSumDecodeBody[S: Type](
      inExpr: Expr[JsonReader],
      codecsExpr: Expr[Array[JsonValueCodec[Any]]],
      variants: List[(String, Int)] // (label, codec index)
    ): Expr[S] =
      val allLabels = variants.map(_._1)
      val errorMsg = Expr("expected one of: " + allLabels.mkString(", "))
      val errorTerm = '{ $inExpr.decodeError($errorMsg) }.asTerm

      val owner = Symbol.spliceOwner
      val klSym = Symbol.newVal(owner, "_kl", TypeRepr.of[Int], Flags.EmptyFlags, Symbol.noSymbol)
      val klDef = ValDef(klSym, Some('{ $inExpr.readKeyAsCharBuf() }.asTerm))

      def buildDecodeBranch(label: String, codecIdx: Int, elseBranch: Term): Term =
        val cond = '{ $inExpr.isCharBufEqualsTo(${Ref(klSym).asExprOf[Int]}, ${Expr(label)}) }.asTerm
        val idxE = Expr(codecIdx)
        val decode = '{ $codecsExpr($idxE).decodeValue($inExpr, $codecsExpr($idxE).nullValue).asInstanceOf[S] }.asTerm
        If(cond, decode, elseBranch)

      val totalChars = allLabels.foldLeft(0)(_ + _.length)
      val dispatchTerm: Term =
        if variants.size <= 8 && totalChars <= 64 then
          variants.foldRight[Term](errorTerm) { case ((label, idx), elseBranch) =>
            buildDecodeBranch(label, idx, elseBranch)
          }
        else
          val hashOf = (v: (String, Int)) =>
            JsonReader.toHashCode(v._1.toCharArray, v._1.length)
          val grouped = groupByOrdered(variants)(hashOf)
          val cases = grouped.map { case (hash, fields) =>
            val chain = fields.foldRight[Term](errorTerm) { case ((label, idx), elseBranch) =>
              buildDecodeBranch(label, idx, elseBranch)
            }
            CaseDef(Literal(IntConstant(hash)), None, chain)
          }
          val defaultCase = CaseDef(Wildcard(), None, errorTerm)
          val scrutinee = '{ $inExpr.charBufToHashCode(${Ref(klSym).asExprOf[Int]}): @scala.annotation.switch }.asTerm
          Match(scrutinee, (cases :+ defaultCase).toList)

      Block(List(klDef), dispatchTerm).asExprOf[S]

    private def deriveSum[S: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.SumOf[S]],
      selfRef: Expr[JsonValueCodec[A]]
    ): Expr[JsonValueCodec[S]] =
      val cases = resolveFields[Types, Labels](selfRef)

      // Detect sub-traits: variants that are themselves sealed traits (not user-provided)
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
        val variants = cases.zipWithIndex.map { case ((label, _, _), idx) => (label, idx) }
        val decodeFnExpr = '{ (in: JsonReader, codecs: Array[JsonValueCodec[Any]]) =>
          ${ generateSumDecodeBody[S]('in, 'codecs, variants) }
        }
        '{ JsoniterRuntime.sumCodec[S]($mirror, $labelsExpr, () => $codecsArrayExpr, $decodeFnExpr) }
      else
        // Build direct codec array (for encoding — includes sub-trait sum codecs)
        val directLabelsExpr = Expr(cases.map(_._1).toArray)
        val isSubTraitExpr = Expr(casesWithSubTrait.map(_._4).toArray)
        val directCodecExprs = casesWithSubTrait.map { case (_, tpe, codec, _) =>
          tpe match
            case '[t] => '{ ${codec.asInstanceOf[Expr[JsonValueCodec[t]]]}.asInstanceOf[JsonValueCodec[Any]] }
        }
        val directCodecsArrayExpr = '{ Array(${Varargs(directCodecExprs)}*) }

        // Flatten all leaf labels/codecs (for decoding)
        val allLeaves = casesWithSubTrait.flatMap { case (label, tpe, codec, isSub) =>
          if isSub then
            tpe match
              case '[t] => collectLeafVariants[t](selfRef)
          else
            List((label, tpe, codec))
        }.distinctBy(_._1) // dedup diamond inheritance

        val allLeafLabelsExpr = Expr(allLeaves.map(_._1).toArray)
        val allLeafCodecExprs = allLeaves.map { case (_, tpe, codec) =>
          tpe match
            case '[t] => '{ ${codec.asInstanceOf[Expr[JsonValueCodec[t]]]}.asInstanceOf[JsonValueCodec[Any]] }
        }
        val allLeafCodecsArrayExpr = '{ Array(${Varargs(allLeafCodecExprs)}*) }

        val allLeafVariants = allLeaves.zipWithIndex.map { case ((label, _, _), idx) => (label, idx) }
        val decodeFnExpr = '{ (in: JsonReader, codecs: Array[JsonValueCodec[Any]]) =>
          ${ generateSumDecodeBody[S]('in, 'codecs, allLeafVariants) }
        }

        '{ JsoniterRuntime.sumCodecWithSubTraits[S](
          $mirror, $directLabelsExpr, $isSubTraitExpr, () => $directCodecsArrayExpr,
          $allLeafLabelsExpr, () => $allLeafCodecsArrayExpr, $decodeFnExpr) }

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

    // === Field/variant resolution ===

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

      // Direct recursion
      if tpe =:= selfType then
        return selfRef.asInstanceOf[Expr[JsonValueCodec[T]]]

      val dealiased = tpe.dealias
      val effectiveType = if isOpaqueAlias(dealiased) then opaqueDealias(dealiased) else dealiased
      val cacheKey = cheapTypeKey(effectiveType)

      // Cache check
      exprCache.get(cacheKey) match
        case Some(cached) => return cached.asInstanceOf[Expr[JsonValueCodec[T]]]
        case None => ()

      // Try builtin (primitives + containers)
      if !negativeBuiltinCache.contains(cacheKey) then
        tryResolveBuiltin[T](dealiased, Some(selfRef)) match
          case Some(codec) =>
            exprCache(cacheKey) = codec
            return codec
          case None =>
            negativeBuiltinCache += cacheKey

      // Check for recursive type in type params
      if containsType(dealiased, selfType) then
        val resolved = constructRecursiveCodec[T](dealiased, selfRef)
        exprCache(cacheKey) = resolved
        return resolved

      // Try summoning an existing instance (ignoring auto-given to prevent loops)
      val resolved: Expr[JsonValueCodec[T]] =
        Expr.summonIgnoring[JsonValueCodec[T]](cachedIgnoreSymbols*) match
          case Some(codec) =>
            summonedKeys += cacheKey
            codec
          case None =>
            // Try deriving via Mirror
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
        // Try tuple before container (tuples are AppliedType too)
        tryResolveTuple[T](dealiased, selfRefOpt)
      }.orElse {
        val resolveTarget = if isOpaqueAlias(dealiased) then opaqueDealias(dealiased) else dealiased
        resolveTarget match
          case AppliedType(tycon, List(arg)) if !isTuple(resolveTarget) =>
            val argKey = cheapTypeKey(arg)
            val innerOpt =
              if negativeBuiltinCache.contains(argKey) then exprCache.get(argKey)
              else resolvePrimCodec(arg.dealias).orElse(exprCache.get(argKey))
            // Try summon for inner type if not in cache/prims
            val innerResolved = innerOpt.orElse {
              arg.asType match
                case '[a] =>
                  Expr.summonIgnoring[JsonValueCodec[a]](cachedIgnoreSymbols*).map { codec =>
                    exprCache(argKey) = codec
                    codec
                  }
            }.orElse {
              // Recursively try resolving inner type as builtin (nested containers like List[String])
              arg.asType match
                case '[a] =>
                  tryResolveBuiltin[a](arg.dealias, selfRefOpt).map { codec =>
                    exprCache(argKey) = codec
                    codec
                  }
            }.orElse {
              // Fallback: derive inner type via Mirror (case classes in containers like List[CaseClass])
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

    // === Ignore symbols (prevent auto-given infinite loops) ===

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

    private def groupByOrdered[X, K](xs: collection.Seq[X])(f: X => K): collection.Seq[(K, collection.Seq[X])] =
      xs.foldLeft(new mutable.LinkedHashMap[K, mutable.ArrayBuffer[X]]) { (m, x) =>
        m.getOrElseUpdate(f(x), new mutable.ArrayBuffer[X]).addOne(x)
        m
      }.toSeq

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
