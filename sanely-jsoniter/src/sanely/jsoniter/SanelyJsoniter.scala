package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, JsonWriter}
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
                  deriveProduct[A, types, labels](m, selfRef)
                case '{ $m: Mirror.SumOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                  deriveSum[A, types, labels](m, selfRef)
            }
            _selfCodec
          }

    // === Product derivation ===

    private def deriveProduct[P: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.ProductOf[P]],
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

      '{ JsoniterRuntime.productCodec[P]($mirror, $namesExpr, () => $codecsArrayExpr, $nullValuesExpr, $encodeFnExpr) }

    private def generateFieldWrites[P: Type](
      x: Expr[P], codecs: Expr[Array[JsonValueCodec[Any]]], out: Expr[JsonWriter],
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
              $out.writeNonEscapedAsciiKey(${Expr(name)})
              $writeValue
            }
      }
      if stmts.isEmpty then '{ () }
      else
        val terms = stmts.map(_.asTerm)
        Block(terms.init.toList, terms.last).asExprOf[Unit]

    private def tryDirectWrite[T: Type](fa: Expr[T], out: Expr[JsonWriter]): Option[Expr[Unit]] =
      val tpe = TypeRepr.of[T].dealias
      if tpe =:= TypeRepr.of[Int] then Some('{ $out.writeVal(${fa.asExprOf[Int]}) })
      else if tpe =:= TypeRepr.of[Long] then Some('{ $out.writeVal(${fa.asExprOf[Long]}) })
      else if tpe =:= TypeRepr.of[Float] then Some('{ $out.writeVal(${fa.asExprOf[Float]}) })
      else if tpe =:= TypeRepr.of[Double] then Some('{ $out.writeVal(${fa.asExprOf[Double]}) })
      else if tpe =:= TypeRepr.of[Boolean] then Some('{ $out.writeVal(${fa.asExprOf[Boolean]}) })
      else if tpe =:= TypeRepr.of[Short] then Some('{ $out.writeVal(${fa.asExprOf[Short]}.toInt) })
      else if tpe =:= TypeRepr.of[Byte] then Some('{ $out.writeVal(${fa.asExprOf[Byte]}.toInt) })
      else if tpe =:= TypeRepr.of[Char] then Some('{ $out.writeVal(${fa.asExprOf[Char]}.toString) })
      else if tpe =:= TypeRepr.of[String] then
        val s = fa.asExprOf[String]
        Some('{ val _v = $s; if _v == null then $out.writeNull() else $out.writeVal(_v) })
      else if tpe =:= TypeRepr.of[BigDecimal] then
        val bd = fa.asExprOf[BigDecimal]
        Some('{ val _v = $bd; if _v == null then $out.writeNull() else $out.writeVal(_v) })
      else if tpe =:= TypeRepr.of[BigInt] then
        val bi = fa.asExprOf[BigInt]
        Some('{ val _v = $bi; if _v == null then $out.writeNull() else $out.writeVal(_v) })
      else None

    // === Sum derivation ===

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
        '{ JsoniterRuntime.sumCodec[S]($mirror, $labelsExpr, () => $codecsArrayExpr) }
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

        '{ JsoniterRuntime.sumCodecWithSubTraits[S](
          $mirror, $directLabelsExpr, $isSubTraitExpr, () => $directCodecsArrayExpr,
          $allLeafLabelsExpr, () => $allLeafCodecsArrayExpr) }

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
      val cacheKey = cheapTypeKey(dealiased)

      // Cache check
      exprCache.get(cacheKey) match
        case Some(cached) => return cached.asInstanceOf[Expr[JsonValueCodec[T]]]
        case None => ()

      // Try builtin (primitives + containers)
      if !negativeBuiltinCache.contains(cacheKey) then
        tryResolveBuiltin[T](dealiased) match
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
                    deriveProduct[T, types, labels](m, selfRef)
                  case '{ $m: Mirror.SumOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                    deriveSum[T, types, labels](m, selfRef)
              case None =>
                report.errorAndAbort(s"Cannot derive JsonValueCodec for ${Type.show[T]}: no implicit JsonValueCodec and no Mirror available")
      exprCache(cacheKey) = resolved
      resolved

    // === Builtin type resolution ===

    private def tryResolveBuiltin[T: Type](dealiased: TypeRepr): Option[Expr[JsonValueCodec[T]]] =
      resolvePrimCodec(dealiased).map(_.asInstanceOf[Expr[JsonValueCodec[T]]]).orElse {
        dealiased match
          case AppliedType(tycon, List(arg)) =>
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
                  tryResolveBuiltin[a](arg.dealias).map { codec =>
                    exprCache(argKey) = codec
                    codec
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
                  tryResolveBuiltin[v](valArg.dealias).map { codec =>
                    exprCache(valKey) = codec
                    codec
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
                    tryResolveBuiltin[a](arg.dealias).map { codec =>
                      exprCache(argKey) = codec
                      codec: Expr[JsonValueCodec[?]]
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
      }

    private def resolvePrimCodec(tpe: TypeRepr): Option[Expr[JsonValueCodec[?]]] =
      if tpe =:= TypeRepr.of[String] then Some('{ Codecs.string })
      else if tpe =:= TypeRepr.of[Int] then Some('{ Codecs.int })
      else if tpe =:= TypeRepr.of[Long] then Some('{ Codecs.long })
      else if tpe =:= TypeRepr.of[Double] then Some('{ Codecs.double })
      else if tpe =:= TypeRepr.of[Float] then Some('{ Codecs.float })
      else if tpe =:= TypeRepr.of[Boolean] then Some('{ Codecs.boolean })
      else if tpe =:= TypeRepr.of[Short] then Some('{ Codecs.short })
      else if tpe =:= TypeRepr.of[Byte] then Some('{ Codecs.byte })
      else if tpe =:= TypeRepr.of[BigDecimal] then Some('{ Codecs.bigDecimal })
      else if tpe =:= TypeRepr.of[BigInt] then Some('{ Codecs.bigInt })
      else if tpe =:= TypeRepr.of[Char] then Some('{ Codecs.char })
      else None

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
      else '{ null: Any }
