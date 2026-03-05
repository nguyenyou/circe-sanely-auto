package sanely

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.derivation.Configuration
import scala.deriving.Mirror
import scala.compiletime.*
import scala.quoted.*

object SanelyEnumCodec:

  inline def derived[A](using inline conf: Configuration)(using inline m: Mirror.SumOf[A]): Codec[A] =
    ${ deriveMacro[A]('conf, 'm) }

  private def deriveMacro[A: Type](conf: Expr[Configuration], mirror: Expr[Mirror.SumOf[A]])(using Quotes): Expr[Codec[A]] =
    val helper = new EnumCodecDerivation[A](conf)
    helper.derive(mirror)

  private class EnumCodecDerivation[A: Type](conf: Expr[Configuration])(using val quotes: Quotes):
    import quotes.reflect.*

    def derive(mirror: Expr[Mirror.SumOf[A]]): Expr[Codec[A]] =
      mirror match
        case '{ $m: Mirror.SumOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
          val cases = collectSingletonCases[A, types, labels](m)
          buildCodec(m, cases)

    private def collectSingletonCases[S: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.SumOf[S]]
    ): List[(String, Expr[S])] =
      (Type.of[Types], Type.of[Labels]) match
        case ('[EmptyTuple], '[EmptyTuple]) => Nil
        case ('[t *: ts], '[label *: ls]) =>
          val labelStr = Type.of[label] match
            case '[l] =>
              Type.valueOfConstant[l].getOrElse(
                report.errorAndAbort(s"Expected literal string type for enum label")
              ).toString

          val casesForThis: List[(String, Expr[S])] = Expr.summon[Mirror.ProductOf[t]] match
            case Some('{ $pm: Mirror.ProductOf[t] { type MirroredElemTypes = EmptyTuple } }) =>
              // Zero-field product = singleton
              List((labelStr, '{ $pm.fromProduct(EmptyTuple).asInstanceOf[S] }))
            case _ =>
              // Not a singleton — check if it's a sub-sum-type (nested sealed trait)
              Expr.summon[Mirror.SumOf[t]] match
                case Some(subMirror) =>
                  subMirror match
                    case '{ $sm: Mirror.SumOf[t] { type MirroredElemTypes = subTypes; type MirroredElemLabels = subLabels } } =>
                      collectSingletonCases[S, subTypes, subLabels](mirror)
                case None =>
                  report.errorAndAbort(s"Enum case '${labelStr}' is not a singleton (has fields). SanelyEnumCodec only supports pure-value enums.")
          casesForThis ++ collectSingletonCases[S, ts, ls](mirror)
        case _ => report.errorAndAbort("Mismatched Types and Labels tuple lengths")

    private def buildCodec[S: Type](
      mirror: Expr[Mirror.SumOf[S]],
      cases: List[(String, Expr[S])]
    ): Expr[Codec[S]] =
      val typeName = Expr(Type.show[S])

      // Deduplicate cases (diamond inheritance can produce the same singleton via multiple paths)
      val deduped = cases.distinctBy(_._1)

      '{
        // Use parallel arrays for encoding: match by reference equality (safe for singletons)
        val _values: Array[AnyRef] = ${
          val exprs = deduped.map { case (_, valueExpr) => '{ $valueExpr.asInstanceOf[AnyRef] } }
          '{ Array(${Varargs(exprs)}*) }
        }
        val _names: Array[String] = ${
          val exprs = deduped.map { case (label, _) =>
            val labelExpr = Expr(label)
            '{ $conf.transformConstructorNames($labelExpr) }
          }
          '{ Array(${Varargs(exprs)}*) }
        }
        val _nameToValue: Map[String, S] = ${
          val mapEntries = deduped.map { case (label, valueExpr) =>
            val labelExpr = Expr(label)
            '{ ($conf.transformConstructorNames($labelExpr), $valueExpr) }
          }
          val listExpr = Expr.ofList(mapEntries)
          '{ $listExpr.toMap }
        }

        new Codec[S]:
          def apply(c: HCursor): Decoder.Result[S] =
            c.as[String] match
              case Right(name) =>
                _nameToValue.get(name) match
                  case Some(v) => Right(v)
                  case None => Left(DecodingFailure(s"enum ${$typeName} does not contain case: " + name, c.history))
              case Left(e) => Left(e)
          def apply(a: S): Json =
            val ref = a.asInstanceOf[AnyRef]
            var i = 0
            while i < _values.length do
              if _values(i) eq ref then return Json.fromString(_names(i))
              i += 1
            Json.fromString(_names(0)) // unreachable for valid enums
      }
