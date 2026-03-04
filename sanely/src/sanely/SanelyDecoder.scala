package sanely

import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import scala.deriving.Mirror
import scala.compiletime.*
import scala.quoted.*

object SanelyDecoder:

  inline def derived[A](using inline m: Mirror.Of[A]): Decoder[A] =
    ${ deriveMacro[A]('m) }

  private def deriveMacro[A: Type](mirror: Expr[Mirror.Of[A]])(using Quotes): Expr[Decoder[A]] =
    import quotes.reflect.*

    mirror match
      case '{ $m: Mirror.ProductOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
        deriveProduct[A, types, labels](m)

      case '{ $m: Mirror.SumOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
        deriveSum[A, types, labels](m)

  private def deriveProduct[A: Type, Types: Type, Labels: Type](
    mirror: Expr[Mirror.ProductOf[A]]
  )(using Quotes): Expr[Decoder[A]] =
    import quotes.reflect.*

    val fields = resolveFields[Types, Labels]

    // Build nested flatMap chain with proper types
    def buildDecodeChain(c: Expr[HCursor], remaining: List[(String, Type[?], Expr[Decoder[?]])], acc: List[Expr[Any]])(using Quotes): Expr[Decoder.Result[A]] =
      remaining match
        case Nil =>
          val tupleExpr = acc.reverse.foldRight('{ EmptyTuple }: Expr[Tuple]) { (elem, tuple) =>
            '{ $elem *: $tuple }
          }
          '{ Right($mirror.fromProduct($tupleExpr)) }
        case (label, tpe, dec) :: rest =>
          tpe match
            case '[t] =>
              val typedDec = dec.asInstanceOf[Expr[Decoder[t]]]
              val labelExpr = Expr(label)
              '{
                $typedDec.tryDecode($c.downField($labelExpr)) match
                  case Right(v) => ${ buildDecodeChain(c, rest, 'v :: acc) }
                  case Left(e)  => Left(e)
              }

    '{
      new Decoder[A]:
        def apply(c: HCursor): Decoder.Result[A] =
          ${ buildDecodeChain('c, fields, Nil) }
    }

  private def deriveSum[A: Type, Types: Type, Labels: Type](
    mirror: Expr[Mirror.SumOf[A]]
  )(using Quotes): Expr[Decoder[A]] =
    import quotes.reflect.*

    val cases = resolveFields[Types, Labels]

    def buildMatch(c: Expr[HCursor], key: Expr[String]): Expr[Decoder.Result[A]] =
      cases.foldRight('{ Left(DecodingFailure("Unknown variant", $c.history)) }: Expr[Decoder.Result[A]]) {
        case ((label, tpe, dec), elseExpr) =>
          tpe match
            case '[t] =>
              val typedDec = dec.asInstanceOf[Expr[Decoder[t]]]
              val labelExpr = Expr(label)
              '{ if $key == $labelExpr then $typedDec.tryDecode($c.downField($labelExpr)).asInstanceOf[Decoder.Result[A]] else $elseExpr }
      }

    '{
      new Decoder[A]:
        def apply(c: HCursor): Decoder.Result[A] =
          c.keys match
            case Some(keys) =>
              val key = keys.head
              ${ buildMatch('c, 'key) }
            case None =>
              Left(DecodingFailure("Expected JSON object for sum type", c.history))
    }

  private def resolveFields[Types: Type, Labels: Type](using Quotes): List[(String, Type[?], Expr[Decoder[?]])] =
    import quotes.reflect.*
    (Type.of[Types], Type.of[Labels]) match
      case ('[EmptyTuple], '[EmptyTuple]) => Nil
      case ('[t *: ts], '[label *: ls]) =>
        val labelStr = Type.of[label] match
          case '[l] =>
            Type.valueOfConstant[l].getOrElse(
              report.errorAndAbort(s"Expected literal string type")
            ).toString
        val dec = resolveOneDecoder[t]
        (labelStr, Type.of[t], dec) :: resolveFields[ts, ls]

  /** Resolve a Decoder for type T using the "sanely-automatic" approach:
    * Use Expr.summonIgnoring to skip our own auto-given, so that only user-provided
    * or standard library decoders are found. If none exists, derive internally within
    * this same macro expansion — avoiding separate implicit search chains.
    */
  private def resolveOneDecoder[T: Type](using Quotes): Expr[Decoder[T]] =
    import quotes.reflect.*

    // Exclude our own auto-given so Expr.summon doesn't trigger a separate macro expansion
    val autoDecoderSymbol = Symbol.requiredModule("sanely.auto").methodMember("autoDecoder").head
    Expr.summonIgnoring[Decoder[T]](autoDecoderSymbol) match
      case Some(dec) => dec
      case None =>
        // No user-provided decoder found — derive internally in this macro expansion
        Expr.summon[Mirror.Of[T]] match
          case Some(mirrorExpr) =>
            mirrorExpr match
              case '{ $m: Mirror.ProductOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                deriveProduct[T, types, labels](m)
              case '{ $m: Mirror.SumOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                deriveSum[T, types, labels](m)
          case None =>
            report.errorAndAbort(s"Cannot derive Decoder for ${Type.show[T]}: no implicit Decoder and no Mirror available")
