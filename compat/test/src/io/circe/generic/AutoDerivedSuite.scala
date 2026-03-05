package io.circe.generic

import cats.kernel.Eq
import cats.syntax.eq._
import io.circe.{ Codec, Decoder, Encoder, Json }
import io.circe.generic.auto._
import io.circe.testing.CodecTests
import io.circe.tests.CirceMunitSuite
import io.circe.tests.examples._
import io.circe.syntax._
import org.scalacheck.{ Arbitrary, Gen, Prop }

object AutoDerivedSuite {
  case class InnerCaseClassExample(a: String, b: String, c: String, d: String)
  case class OuterCaseClassExample(a: String, inner: InnerCaseClassExample)

  object InnerCaseClassExample {
    implicit val arbitraryInnerCaseClassExample: Arbitrary[InnerCaseClassExample] =
      Arbitrary(
        for {
          a <- Arbitrary.arbitrary[String]
          b <- Arbitrary.arbitrary[String]
          c <- Arbitrary.arbitrary[String]
          d <- Arbitrary.arbitrary[String]
        } yield InnerCaseClassExample(a, b, c, d)
      )
  }

  object OuterCaseClassExample {
    implicit val eqOuterCaseClassExample: Eq[OuterCaseClassExample] = Eq.fromUniversalEquals

    implicit val arbitraryOuterCaseClassExample: Arbitrary[OuterCaseClassExample] =
      Arbitrary(
        for {
          a <- Arbitrary.arbitrary[String]
          i <- Arbitrary.arbitrary[InnerCaseClassExample]
        } yield OuterCaseClassExample(a, i)
      )
  }

  // Recursive ADT — sealed trait hierarchy
  sealed trait RecursiveAdtExample
  case class BaseAdtExample(a: String) extends RecursiveAdtExample
  case class NestedAdtExample(r: RecursiveAdtExample) extends RecursiveAdtExample

  object RecursiveAdtExample {
    implicit val eqRecursiveAdtExample: Eq[RecursiveAdtExample] = Eq.fromUniversalEquals

    private def atDepth(depth: Int): Gen[RecursiveAdtExample] = if (depth < 3)
      Gen.oneOf(
        Arbitrary.arbitrary[String].map(BaseAdtExample(_)),
        atDepth(depth + 1).map(NestedAdtExample(_))
      )
    else Arbitrary.arbitrary[String].map(BaseAdtExample(_))

    implicit val arbitraryRecursiveAdtExample: Arbitrary[RecursiveAdtExample] =
      Arbitrary(atDepth(0))
  }

  // Recursive with Option
  case class RecursiveWithOptionExample(o: Option[RecursiveWithOptionExample])

  object RecursiveWithOptionExample {
    implicit val eqRecursiveWithOptionExample: Eq[RecursiveWithOptionExample] =
      Eq.fromUniversalEquals

    private def atDepth(depth: Int): Gen[RecursiveWithOptionExample] = if (depth < 3)
      Gen.oneOf(
        Gen.const(RecursiveWithOptionExample(None)),
        atDepth(depth + 1).map(Some(_)).map(RecursiveWithOptionExample(_))
      )
    else Gen.const(RecursiveWithOptionExample(None))

    implicit val arbitraryRecursiveWithOptionExample: Arbitrary[RecursiveWithOptionExample] =
      Arbitrary(atDepth(0))
  }

  // ADT with nested sub-trait
  sealed trait ADTWithSubTraitExample
  sealed trait SubTrait extends ADTWithSubTraitExample
  case class TheClass(a: Int) extends SubTrait

  object ADTWithSubTraitExample {
    implicit val arbitrary: Arbitrary[ADTWithSubTraitExample] = Arbitrary(Arbitrary.arbitrary[Int].map(TheClass.apply))
    implicit val eq: Eq[ADTWithSubTraitExample] = Eq.fromUniversalEquals
  }

  // Nested case class with Option (tests existing instances)
  case class Inner[A](field: A)
  case class Outer(a: Option[Inner[String]])
  object Outer {
    given Eq[Outer] = Eq.fromUniversalEquals
    given Arbitrary[Outer] =
      Arbitrary(Gen.option(Arbitrary.arbitrary[String].map(Inner.apply)).map(Outer.apply))
  }

  // Large case class (33 fields) — stress test
  case class LongClass(
    v1: String, v2: String, v3: String, v4: String, v5: String,
    v6: String, v7: String, v8: String, v9: String, v10: String,
    v11: String, v12: String, v13: String, v14: String, v15: String,
    v16: String, v17: String, v18: String, v19: String, v20: String,
    v21: String, v22: String, v23: String, v24: String, v25: String,
    v26: String, v27: String, v28: String, v29: String, v30: String,
    v31: String, v32: String, v33: String
  )
  object LongClass {
    given Eq[LongClass] = Eq.fromUniversalEquals
    given Arbitrary[LongClass] = Arbitrary {
      for
        s1 <- Arbitrary.arbitrary[String]; s2 <- Arbitrary.arbitrary[String]
        s3 <- Arbitrary.arbitrary[String]; s4 <- Arbitrary.arbitrary[String]
        s5 <- Arbitrary.arbitrary[String]; s6 <- Arbitrary.arbitrary[String]
        s7 <- Arbitrary.arbitrary[String]; s8 <- Arbitrary.arbitrary[String]
        s9 <- Arbitrary.arbitrary[String]; s10 <- Arbitrary.arbitrary[String]
        s11 <- Arbitrary.arbitrary[String]; s12 <- Arbitrary.arbitrary[String]
        s13 <- Arbitrary.arbitrary[String]; s14 <- Arbitrary.arbitrary[String]
        s15 <- Arbitrary.arbitrary[String]; s16 <- Arbitrary.arbitrary[String]
        s17 <- Arbitrary.arbitrary[String]; s18 <- Arbitrary.arbitrary[String]
        s19 <- Arbitrary.arbitrary[String]; s20 <- Arbitrary.arbitrary[String]
        s21 <- Arbitrary.arbitrary[String]; s22 <- Arbitrary.arbitrary[String]
        s23 <- Arbitrary.arbitrary[String]; s24 <- Arbitrary.arbitrary[String]
        s25 <- Arbitrary.arbitrary[String]; s26 <- Arbitrary.arbitrary[String]
        s27 <- Arbitrary.arbitrary[String]; s28 <- Arbitrary.arbitrary[String]
        s29 <- Arbitrary.arbitrary[String]; s30 <- Arbitrary.arbitrary[String]
        s31 <- Arbitrary.arbitrary[String]; s32 <- Arbitrary.arbitrary[String]
        s33 <- Arbitrary.arbitrary[String]
      yield LongClass(s1, s2, s3, s4, s5, s6, s7, s8, s9, s10,
        s11, s12, s13, s14, s15, s16, s17, s18, s19, s20,
        s21, s22, s23, s24, s25, s26, s27, s28, s29, s30,
        s31, s32, s33)
    }
  }
}

class AutoDerivedSuite extends CirceMunitSuite {
  import AutoDerivedSuite._

  checkAll("Codec[Tuple1[Int]]", CodecTests[Tuple1[Int]].codec)
  checkAll("Codec[(Int, Int, Foo)]", CodecTests[(Int, Int, Foo)].codec)
  checkAll("Codec[Qux[Int]]", CodecTests[Qux[Int]].codec)
  checkAll("Codec[Seq[Foo]]", CodecTests[Seq[Foo]].codec)
  checkAll("Codec[Baz]", CodecTests[Baz].codec)
  checkAll("Codec[Foo]", CodecTests[Foo].codec)
  checkAll("Codec[OuterCaseClassExample]", CodecTests[OuterCaseClassExample].codec)
  checkAll("Codec[RecursiveAdtExample]", CodecTests[RecursiveAdtExample].unserializableCodec)
  checkAll("Codec[RecursiveWithOptionExample]", CodecTests[RecursiveWithOptionExample].unserializableCodec)
  checkAll("Codec[ADTWithSubTraitExample]", CodecTests[ADTWithSubTraitExample].codec)
  checkAll("Codec[Outer]", CodecTests[Outer].codec)
  checkAll("Codec[LongClass]", CodecTests[LongClass].codec)

  property("A generically derived codec should not interfere with base instances") {
    Prop.forAll { (is: List[Int]) =>
      val json = Encoder[List[Int]].apply(is)

      assert(json === Json.fromValues(is.map(Json.fromInt)) && json.as[List[Int]] === Right(is))
    }
  }

  property("Generic decoders should not interfere with defined decoders") {
    Prop.forAll { (xs: List[String]) =>
      val json = Json.obj("Baz" -> Json.fromValues(xs.map(Json.fromString)))

      assert(Decoder[Foo].apply(json.hcursor) === Right(Baz(xs): Foo))
    }
  }

  property("Generic encoders should not interfere with defined encoders") {
    Prop.forAll { (xs: List[String]) =>
      val json = Json.obj("Baz" -> Json.fromValues(xs.map(Json.fromString)))

      assert(Encoder[Foo].apply(Baz(xs): Foo) === json)
    }
  }

  test("Nested sums should not be encoded redundantly") {
    val foo: ADTWithSubTraitExample = TheClass(0)
    val expected = Json.obj("TheClass" -> Json.obj("a" -> 0.asJson))
    assertEquals(foo.asJson, expected)
  }

  test("Derived Encoder respects existing instances") {
    val some = Outer(Some(Inner("c")))
    val none = Outer(None)
    val expectedSome = Json.obj("a" -> Json.obj("field" -> "c".asJson))
    val expectedNone = Json.obj("a" -> Json.Null)
    assertEquals(some.asJson, expectedSome)
    assertEquals(none.asJson, expectedNone)
  }
}
