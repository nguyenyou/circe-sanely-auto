package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonWriter, JsonValueCodec}
import scala.deriving.Mirror
import scala.compiletime.*
import scala.quoted.*

object SanelyJsoniterEnum:

  inline def derived[A](using inline m: Mirror.SumOf[A]): JsonValueCodec[A] =
    ${ deriveMacro[A]('m) }

  private def deriveMacro[A: Type](mirror: Expr[Mirror.SumOf[A]])(using Quotes): Expr[JsonValueCodec[A]] =
    val helper = new EnumCodecDerivation[A]
    helper.derive(mirror)

  private class EnumCodecDerivation[A: Type](using val quotes: Quotes):
    import quotes.reflect.*

    def derive(mirror: Expr[Mirror.SumOf[A]]): Expr[JsonValueCodec[A]] =
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
                  report.errorAndAbort(s"Enum case '${labelStr}' is not a singleton (has fields). SanelyJsoniterEnum only supports pure-value enums.")
          casesForThis ++ collectSingletonCases[S, ts, ls](mirror)
        case _ => report.errorAndAbort("Mismatched Types and Labels tuple lengths")

    private def buildCodec[S: Type](
      mirror: Expr[Mirror.SumOf[S]],
      cases: List[(String, Expr[S])]
    ): Expr[JsonValueCodec[S]] =
      val typeName = Expr(TypeRepr.of[S].typeSymbol.name)

      // Deduplicate cases (diamond inheritance can produce the same singleton via multiple paths)
      val deduped = cases.distinctBy(_._1)

      '{
        val _values: Array[AnyRef] = ${
          val exprs = deduped.map { case (_, valueExpr) => '{ $valueExpr.asInstanceOf[AnyRef] } }
          '{ Array(${Varargs(exprs)}*) }
        }
        val _names: Array[String] = ${
          val exprs = deduped.map { case (label, _) => Expr(label) }
          '{ Array(${Varargs(exprs)}*) }
        }
        val _nameToValue: Map[String, S] = ${
          val mapEntries = deduped.map { case (label, valueExpr) =>
            '{ (${Expr(label)}, $valueExpr) }
          }
          val listExpr = Expr.ofList(mapEntries)
          '{ $listExpr.toMap }
        }

        new JsonValueCodec[S]:
          def decodeValue(in: JsonReader, default: S): S =
            val name = in.readString(null)
            if name eq null then in.decodeError(s"expected string for enum ${$typeName}")
            _nameToValue.get(name) match
              case Some(v) => v
              case None => in.decodeError(s"enum ${$typeName} does not contain case: " + name)
          def encodeValue(a: S, out: JsonWriter): Unit =
            val ref = a.asInstanceOf[AnyRef]
            var i = 0
            while i < _values.length do
              if _values(i) eq ref then
                out.writeVal(_names(i))
                return
              i += 1
            out.writeVal(_names(0)) // unreachable for valid enums
          def nullValue: S = null.asInstanceOf[S]
      }
