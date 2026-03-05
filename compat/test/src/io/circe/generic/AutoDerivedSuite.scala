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

  // Enum with mixed singleton and data cases (from circe DerivesSuite)
  enum Vegetable:
    case Potato(species: String)
    case Carrot(length: Double)
    case Onion(layers: Int)
    case Turnip
  object Vegetable:
    given Eq[Vegetable] = Eq.fromUniversalEquals
    given Arbitrary[Vegetable.Potato] = Arbitrary(
      Arbitrary.arbitrary[String].map(Vegetable.Potato.apply)
    )
    given Arbitrary[Vegetable.Carrot] = Arbitrary(
      Arbitrary.arbitrary[Double].map(Vegetable.Carrot.apply)
    )
    given Arbitrary[Vegetable.Onion] = Arbitrary(
      Arbitrary.arbitrary[Int].map(Vegetable.Onion.apply)
    )
    given Arbitrary[Vegetable.Turnip.type] = Arbitrary(Gen.const(Vegetable.Turnip))
    given Arbitrary[Vegetable] = Arbitrary(
      Gen.oneOf(
        Arbitrary.arbitrary[Vegetable.Potato],
        Arbitrary.arbitrary[Vegetable.Carrot],
        Arbitrary.arbitrary[Vegetable.Onion],
        Arbitrary.arbitrary[Vegetable.Turnip.type]
      )
    )

  // Recursive enum ADT (from circe DerivesSuite)
  enum RecursiveEnumAdt:
    case BaseAdtExample(a: String)
    case NestedAdtExample(r: RecursiveEnumAdt)
  object RecursiveEnumAdt:
    given Eq[RecursiveEnumAdt] = Eq.fromUniversalEquals

    private def atDepth(depth: Int): Gen[RecursiveEnumAdt] = if (depth < 3)
      Gen.oneOf(
        Arbitrary.arbitrary[String].map(RecursiveEnumAdt.BaseAdtExample(_)),
        atDepth(depth + 1).map(RecursiveEnumAdt.NestedAdtExample(_))
      )
    else Arbitrary.arbitrary[String].map(RecursiveEnumAdt.BaseAdtExample(_))

    given Arbitrary[RecursiveEnumAdt] = Arbitrary(atDepth(0))

  // Phantom type tagging (from circe DerivesSuite #2135)
  case class ProductWithTaggedMember(x: ProductWithTaggedMember.TaggedString)

  object ProductWithTaggedMember:
    sealed trait Tag

    type TaggedString = String & Tag

    object TaggedString:
      val decoder: Decoder[TaggedString] =
        summon[Decoder[String]].map(_.asInstanceOf[TaggedString])
      val encoder: Encoder[TaggedString] =
        summon[Encoder[String]].contramap(x => x)

    given Codec[TaggedString] =
      Codec.from(TaggedString.decoder, TaggedString.encoder)

    def fromUntagged(x: String): ProductWithTaggedMember =
      ProductWithTaggedMember(x.asInstanceOf[TaggedString])

    given Arbitrary[ProductWithTaggedMember] =
      Arbitrary {
        Arbitrary.arbitrary[String].map(fromUntagged)
      }
    given Eq[ProductWithTaggedMember] = Eq.fromUniversalEquals

  // 33-variant singleton enum (from circe DerivesSuite)
  enum LongEnum:
    case v1, v2, v3, v4, v5, v6, v7, v8, v9, v10,
      v11, v12, v13, v14, v15, v16, v17, v18, v19, v20,
      v21, v22, v23, v24, v25, v26, v27, v28, v29, v30,
      v31, v32, v33

  object LongEnum:
    given Eq[LongEnum] = Eq.fromUniversalEquals
    given Arbitrary[LongEnum] = Arbitrary(Gen.oneOf(LongEnum.values.toSeq))

  // 33-variant sum enum with data fields (from circe DerivesSuite)
  enum LongSum:
    case v1(str: String)
    case v2(str: String)
    case v3(str: String)
    case v4(str: String)
    case v5(str: String)
    case v6(str: String)
    case v7(str: String)
    case v8(str: String)
    case v9(str: String)
    case v10(str: String)
    case v11(str: String)
    case v12(str: String)
    case v13(str: String)
    case v14(str: String)
    case v15(str: String)
    case v16(str: String)
    case v17(str: String)
    case v18(str: String)
    case v19(str: String)
    case v20(str: String)
    case v21(str: String)
    case v22(str: String)
    case v23(str: String)
    case v24(str: String)
    case v25(str: String)
    case v26(str: String)
    case v27(str: String)
    case v28(str: String)
    case v29(str: String)
    case v30(str: String)
    case v31(str: String)
    case v32(str: String)
    case v33(str: String)

  object LongSum:
    given Eq[LongSum] = Eq.fromUniversalEquals
    given Arbitrary[LongSum] = Arbitrary(
      for
        v <- Arbitrary.arbitrary[String]
        res <- Gen.oneOf(Seq(
          LongSum.v1(v), LongSum.v2(v), LongSum.v3(v), LongSum.v4(v), LongSum.v5(v),
          LongSum.v6(v), LongSum.v7(v), LongSum.v8(v), LongSum.v9(v), LongSum.v10(v),
          LongSum.v11(v), LongSum.v12(v), LongSum.v13(v), LongSum.v14(v), LongSum.v15(v),
          LongSum.v16(v), LongSum.v17(v), LongSum.v18(v), LongSum.v19(v), LongSum.v20(v),
          LongSum.v21(v), LongSum.v22(v), LongSum.v23(v), LongSum.v24(v), LongSum.v25(v),
          LongSum.v26(v), LongSum.v27(v), LongSum.v28(v), LongSum.v29(v), LongSum.v30(v),
          LongSum.v31(v), LongSum.v32(v), LongSum.v33(v)
        ))
      yield res
    )
}

class AutoDerivedSuite extends CirceMunitSuite {
  import AutoDerivedSuite._

  checkAll("Codec[Tuple1[Int]]", CodecTests[Tuple1[Int]].codec)
  checkAll("Codec[(Int, Int, Foo)]", CodecTests[(Int, Int, Foo)].codec)
  checkAll("Codec[Box[Wub]]", CodecTests[Box[Wub]].codec)
  checkAll("Codec[Box[Long]]", CodecTests[Box[Long]].codec)
  checkAll("Codec[Qux[Int]]", CodecTests[Qux[Int]].codec)
  checkAll("Codec[Seq[Foo]]", CodecTests[Seq[Foo]].codec)
  checkAll("Codec[Baz]", CodecTests[Baz].codec)
  checkAll("Codec[Foo]", CodecTests[Foo].codec)
  checkAll("Codec[OuterCaseClassExample]", CodecTests[OuterCaseClassExample].codec)
  checkAll("Codec[RecursiveAdtExample]", CodecTests[RecursiveAdtExample].unserializableCodec)
  checkAll("Codec[RecursiveWithOptionExample]", CodecTests[RecursiveWithOptionExample].unserializableCodec)
  checkAll("Codec[Vegetable]", CodecTests[Vegetable].unserializableCodec)
  checkAll("Codec[RecursiveEnumAdt]", CodecTests[RecursiveEnumAdt].unserializableCodec)
  checkAll("Codec[ADTWithSubTraitExample]", CodecTests[ADTWithSubTraitExample].codec)
  checkAll("Codec[ProductWithTaggedMember]", CodecTests[ProductWithTaggedMember].codec)
  checkAll("Codec[Outer]", CodecTests[Outer].codec)
  checkAll("Codec[LongClass]", CodecTests[LongClass].codec)
  checkAll("Codec[LongSum]", CodecTests[LongSum].unserializableCodec)
  checkAll("Codec[LongEnum]", CodecTests[LongEnum].codec)

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
