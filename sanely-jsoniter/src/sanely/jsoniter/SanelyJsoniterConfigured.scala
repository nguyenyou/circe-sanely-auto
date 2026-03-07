package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
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
                  deriveProduct[A, types, labels](m, selfRef)
                case '{ $m: Mirror.SumOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                  deriveSum[A, types, labels](m, selfRef)
            }
            _selfCodec
          }

    // === Product derivation (configured) ===

    private def deriveProduct[P: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.ProductOf[P]],
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

      '{
        JsoniterRuntime.configuredProductCodec[P](
          $mirror, $namesExpr, $conf.transformMemberNames,
          () => $codecsArrayExpr, $nullValuesExpr,
          $hasDefaultArrayExpr, $defaultsArrayExpr, $isOptionArrayExpr,
          $conf.useDefaults, $conf.dropNullValues)
      }

    // === Sum derivation (configured) ===

    private def deriveSum[S: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.SumOf[S]],
      selfRef: Expr[JsonValueCodec[A]]
    ): Expr[JsonValueCodec[S]] =
      val cases = resolveFields[Types, Labels](selfRef)
      val labelsExpr = Expr(cases.map(_._1).toArray)
      val codecExprs = cases.map { case (_, tpe, codec) =>
        tpe match
          case '[t] => '{ ${codec.asInstanceOf[Expr[JsonValueCodec[t]]]}.asInstanceOf[JsonValueCodec[Any]] }
      }
      val codecsArrayExpr = '{ Array(${Varargs(codecExprs)}*) }

      '{ JsoniterRuntime.configuredSumCodec[S]($mirror, $labelsExpr, $conf.transformConstructorNames, $conf.discriminator, () => $codecsArrayExpr) }

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
      val cacheKey = cheapTypeKey(dealiased)

      exprCache.get(cacheKey) match
        case Some(cached) => return cached.asInstanceOf[Expr[JsonValueCodec[T]]]
        case None => ()

      if !negativeBuiltinCache.contains(cacheKey) then
        tryResolveBuiltin[T](dealiased) match
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
          case Some(codec) => codec
          case None =>
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
            if tycon.typeSymbol.fullName.endsWith(".Map") &&
               keyArg =:= TypeRepr.of[String] =>
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
            }
            valResolved.flatMap { valCodec =>
              valArg.asType match
                case '[v] =>
                  val vc = valCodec.asInstanceOf[Expr[JsonValueCodec[v]]]
                  Some('{ Codecs.stringMap[v]($vc) }.asInstanceOf[Expr[JsonValueCodec[T]]])
                case _ => None
            }
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

    // === Ignore symbols ===

    private lazy val cachedIgnoreSymbols: List[Symbol] =
      val buf = List.newBuilder[Symbol]
      try
        buf += Symbol.requiredModule("sanely.jsoniter.auto").methodMember("autoCodec").head
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
