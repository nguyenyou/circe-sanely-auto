package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import scala.deriving.Mirror
import scala.quoted.*

object SanelyJsoniterValueEnum:

  inline def derived[A](using inline m: Mirror.SumOf[A]): JsonValueCodec[A] =
    ${ deriveMacro[A] }

  private def deriveMacro[A: Type](using Quotes): Expr[JsonValueCodec[A]] =
    import quotes.reflect.*

    val tpe = TypeRepr.of[A]
    val sym = tpe.typeSymbol

    // Get constructor parameters (filter out type params)
    val ctorParams = sym.primaryConstructor.paramSymss.flatten.filterNot(_.isType)
    if ctorParams.size != 1 then
      report.errorAndAbort(
        s"deriveJsoniterValueEnumCodec requires an enum with exactly one constructor parameter, " +
        s"but ${sym.name} has ${ctorParams.size}. " +
        s"Use deriveJsoniterEnumCodec for pure enums, or Codecs.stringValueEnum/intValueEnum for custom patterns."
      )

    val param = ctorParams.head
    val paramName = param.name
    val paramType = tpe.memberType(param)

    // companion.values
    val companion = Ref(sym.companionModule)
    val valuesMethod = sym.companionModule.methodMember("values")
      .headOption
      .getOrElse(report.errorAndAbort(s"${sym.name} companion has no 'values' method — is it a Scala 3 enum?"))
    val valuesExpr = companion.select(valuesMethod).asExprOf[Array[A]]

    if paramType =:= TypeRepr.of[String] then
      val toValue = Lambda(
        Symbol.spliceOwner,
        MethodType(List("a"))(_ => List(tpe), _ => TypeRepr.of[String]),
        { case (_, List(a: Term)) => Select.unique(a, paramName); case _ => report.errorAndAbort("unexpected lambda shape") }
      ).asExprOf[A => String]
      '{ Codecs.stringValueEnum[A]($valuesExpr, $toValue) }
    else if paramType =:= TypeRepr.of[Int] then
      val toValue = Lambda(
        Symbol.spliceOwner,
        MethodType(List("a"))(_ => List(tpe), _ => TypeRepr.of[Int]),
        { case (_, List(a: Term)) => Select.unique(a, paramName); case _ => report.errorAndAbort("unexpected lambda shape") }
      ).asExprOf[A => Int]
      '{ Codecs.intValueEnum[A]($valuesExpr, $toValue) }
    else
      report.errorAndAbort(
        s"deriveJsoniterValueEnumCodec supports String and Int value types, " +
        s"but ${sym.name}.${paramName} has type ${paramType.show}. " +
        s"Use Codecs.stringValueEnum or Codecs.intValueEnum manually."
      )
