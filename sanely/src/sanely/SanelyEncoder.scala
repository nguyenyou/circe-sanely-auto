package sanely

import io.circe.{Encoder, Json, JsonObject}
import scala.deriving.Mirror
import scala.compiletime.*
import scala.quoted.*

object SanelyEncoder:

  inline def derived[A](using inline m: Mirror.Of[A]): Encoder.AsObject[A] =
    ${ deriveMacro[A]('m) }

  private def deriveMacro[A: Type](mirror: Expr[Mirror.Of[A]])(using Quotes): Expr[Encoder.AsObject[A]] =
    import quotes.reflect.*

    mirror match
      case '{ $m: Mirror.ProductOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
        deriveProduct[A, types, labels](m)

      case '{ $m: Mirror.SumOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
        deriveSum[A, types, labels](m)

  private def deriveProduct[A: Type, Types: Type, Labels: Type](
    mirror: Expr[Mirror.ProductOf[A]]
  )(using Quotes): Expr[Encoder.AsObject[A]] =
    import quotes.reflect.*

    val fields = resolveFields[Types, Labels]

    def addField(base: Expr[JsonObject], product: Expr[Product], label: String, idx: Int, tpe: Type[?], enc: Expr[Encoder[?]])(using Quotes): Expr[JsonObject] =
      tpe match
        case '[t] =>
          val typedEnc = enc.asInstanceOf[Expr[Encoder[t]]]
          val labelExpr = Expr(label)
          val idxExpr = Expr(idx)
          '{ $base.add($labelExpr, $typedEnc($product.productElement($idxExpr).asInstanceOf[t])) }

    '{
      new Encoder.AsObject[A]:
        def encodeObject(a: A): JsonObject =
          ${
            val product = '{ a.asInstanceOf[Product] }
            fields.zipWithIndex.foldLeft('{ JsonObject.empty }) { case (acc, ((label, tpe, enc), idx)) =>
              addField(acc, product, label, idx, tpe, enc)
            }
          }
    }

  private def deriveSum[A: Type, Types: Type, Labels: Type](
    mirror: Expr[Mirror.SumOf[A]]
  )(using Quotes): Expr[Encoder.AsObject[A]] =
    import quotes.reflect.*

    val cases = resolveFields[Types, Labels]

    def buildBranch(a: Expr[A], label: String, tpe: Type[?], enc: Expr[Encoder[?]])(using Quotes): Expr[JsonObject] =
      tpe match
        case '[t] =>
          val typedEnc = enc.asInstanceOf[Expr[Encoder.AsObject[t]]]
          val labelExpr = Expr(label)
          '{ JsonObject.singleton($labelExpr, Json.fromJsonObject($typedEnc.encodeObject($a.asInstanceOf[t]))) }

    '{
      new Encoder.AsObject[A]:
        def encodeObject(a: A): JsonObject =
          val ord = $mirror.ordinal(a)
          ${
            cases.zipWithIndex.foldRight('{ throw new MatchError(ord) }: Expr[JsonObject]) {
              case (((label, tpe, enc), idx), elseExpr) =>
                val idxExpr = Expr(idx)
                val branch = buildBranch('a, label, tpe, enc)
                '{ if ord == $idxExpr then $branch else $elseExpr }
            }
          }
    }

  private def resolveFields[Types: Type, Labels: Type](using Quotes): List[(String, Type[?], Expr[Encoder[?]])] =
    import quotes.reflect.*
    (Type.of[Types], Type.of[Labels]) match
      case ('[EmptyTuple], '[EmptyTuple]) => Nil
      case ('[t *: ts], '[label *: ls]) =>
        val labelStr = Type.of[label] match
          case '[l] =>
            Type.valueOfConstant[l].getOrElse(
              report.errorAndAbort(s"Expected literal string type")
            ).toString
        val enc = resolveOneEncoder[t]
        (labelStr, Type.of[t], enc) :: resolveFields[ts, ls]

  private def resolveOneEncoder[T: Type](using Quotes): Expr[Encoder[T]] =
    import quotes.reflect.*

    Expr.summon[Encoder[T]] match
      case Some(enc) => enc
      case None =>
        Expr.summon[Mirror.Of[T]] match
          case Some(mirrorExpr) =>
            mirrorExpr match
              case '{ $m: Mirror.ProductOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                deriveProduct[T, types, labels](m)
              case '{ $m: Mirror.SumOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                deriveSum[T, types, labels](m)
          case None =>
            report.errorAndAbort(s"Cannot derive Encoder for ${Type.show[T]}: no implicit Encoder and no Mirror available")
