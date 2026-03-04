package sanely

import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import scala.deriving.Mirror
import scala.compiletime.*
import scala.quoted.*

object SanelyDecoder:

  inline def derived[A](using inline m: Mirror.Of[A]): Decoder[A] =
    ${ deriveMacro[A]('m) }

  private def deriveMacro[A: Type](mirror: Expr[Mirror.Of[A]])(using Quotes): Expr[Decoder[A]] =
    val helper = new DecoderDerivation[A]
    helper.derive(mirror)

  private class DecoderDerivation[A: Type](using val quotes: Quotes):
    import quotes.reflect.*

    val selfType: TypeRepr = TypeRepr.of[A]

    def derive(mirror: Expr[Mirror.Of[A]]): Expr[Decoder[A]] =
      // Wrap in lazy val for recursive self-reference support
      '{
        lazy val _selfDec: Decoder[A] = ${
          val selfRef: Expr[Decoder[A]] = '{ _selfDec }
          mirror match
            case '{ $m: Mirror.ProductOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
              deriveProduct[A, types, labels](m, selfRef)
            case '{ $m: Mirror.SumOf[A] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
              deriveSum[A, types, labels](m, selfRef)
        }
        _selfDec
      }

    private def deriveProduct[P: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.ProductOf[P]],
      selfRef: Expr[Decoder[A]]
    ): Expr[Decoder[P]] =
      val fields = resolveFields[Types, Labels](selfRef)

      def buildDecodeChain(c: Expr[HCursor], remaining: List[(String, Type[?], Expr[Decoder[?]])], acc: List[Expr[Any]]): Expr[Decoder.Result[P]] =
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
        new Decoder[P]:
          def apply(c: HCursor): Decoder.Result[P] =
            ${ buildDecodeChain('c, fields, Nil) }
      }

    private def deriveSum[S: Type, Types: Type, Labels: Type](
      mirror: Expr[Mirror.SumOf[S]],
      selfRef: Expr[Decoder[A]]
    ): Expr[Decoder[S]] =
      val cases = resolveFields[Types, Labels](selfRef)

      def buildMatch(c: Expr[HCursor], key: Expr[String]): Expr[Decoder.Result[S]] =
        cases.foldRight('{ Left(DecodingFailure("Unknown variant", $c.history)) }: Expr[Decoder.Result[S]]) {
          case ((label, tpe, dec), elseExpr) =>
            tpe match
              case '[t] =>
                val typedDec = dec.asInstanceOf[Expr[Decoder[t]]]
                val labelExpr = Expr(label)
                '{ if $key == $labelExpr then $typedDec.tryDecode($c.downField($labelExpr)).asInstanceOf[Decoder.Result[S]] else $elseExpr }
        }

      '{
        new Decoder[S]:
          def apply(c: HCursor): Decoder.Result[S] =
            c.keys match
              case Some(keys) =>
                val key = keys.head
                ${ buildMatch('c, 'key) }
              case None =>
                Left(DecodingFailure("Expected JSON object for sum type", c.history))
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

    private def resolveOneDecoder[T: Type](
      selfRef: Expr[Decoder[A]]
    ): Expr[Decoder[T]] =
      val tpe = TypeRepr.of[T]

      // Direct recursion: T is the same type we're currently deriving
      if tpe =:= selfType then
        return selfRef.asInstanceOf[Expr[Decoder[T]]]

      // Check if T contains the recursive type in its type params
      if containsType(tpe, selfType) then
        return constructRecursiveDecoder[T](tpe, selfRef)

      // Safe path: no recursion risk
      val autoDecoderSymbol = Symbol.requiredModule("sanely.auto").methodMember("autoDecoder").head
      Expr.summonIgnoring[Decoder[T]](autoDecoderSymbol) match
        case Some(dec) => dec
        case None =>
          Expr.summon[Mirror.Of[T]] match
            case Some(mirrorExpr) =>
              mirrorExpr match
                case '{ $m: Mirror.ProductOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                  deriveProduct[T, types, labels](m, selfRef)
                case '{ $m: Mirror.SumOf[T] { type MirroredElemTypes = types; type MirroredElemLabels = labels } } =>
                  deriveSum[T, types, labels](m, selfRef)
            case None =>
              report.errorAndAbort(s"Cannot derive Decoder for ${Type.show[T]}: no implicit Decoder and no Mirror available")

    private def containsType(tpe: TypeRepr, target: TypeRepr): Boolean =
      tpe match
        case AppliedType(_, args) => args.exists(arg => (arg =:= target) || containsType(arg, target))
        case _ => false

    private def constructRecursiveDecoder[T: Type](
      tpe: TypeRepr,
      selfRef: Expr[Decoder[A]]
    ): Expr[Decoder[T]] =
      tpe match
        case AppliedType(tycon, List(arg)) if arg =:= selfType =>
          arg.asType match
            case '[a] =>
              val innerDec = selfRef.asInstanceOf[Expr[Decoder[a]]]
              tycon.typeSymbol.fullName match
                case "scala.Option" =>
                  '{ Decoder.decodeOption[a](using $innerDec) }.asInstanceOf[Expr[Decoder[T]]]
                case s if s.endsWith(".List") =>
                  '{ Decoder.decodeList[a](using $innerDec) }.asInstanceOf[Expr[Decoder[T]]]
                case s if s.endsWith(".Vector") =>
                  '{ Decoder.decodeVector[a](using $innerDec) }.asInstanceOf[Expr[Decoder[T]]]
                case s if s.endsWith(".Set") =>
                  '{ Decoder.decodeSet[a](using $innerDec) }.asInstanceOf[Expr[Decoder[T]]]
                case other =>
                  report.errorAndAbort(s"Cannot derive Decoder for recursive type in container ${other}[${Type.show[a]}]")
        case _ =>
          report.errorAndAbort(s"Cannot derive Decoder for recursive type application: ${Type.show[T]}")
