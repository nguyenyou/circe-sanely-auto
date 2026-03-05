package io.circe.generic

import cats.kernel.Eq
import cats.syntax.eq._
import io.circe.{ Codec, Decoder, Encoder, Json }
import io.circe.generic.semiauto._
import io.circe.testing.CodecTests
import io.circe.tests.CirceMunitSuite
import io.circe.tests.examples._
import org.scalacheck.{ Arbitrary, Gen, Prop }

object SemiautoDerivedSuite {
  implicit def decodeBox[A: Decoder]: Decoder[Box[A]] = deriveDecoder
  implicit def encodeBox[A: Encoder]: Encoder[Box[A]] = deriveEncoder
  def codecForBox[A: Decoder: Encoder]: Codec[Box[A]] = deriveCodec

  implicit def decodeQux[A: Decoder]: Decoder[Qux[A]] = deriveDecoder
  implicit def encodeQux[A: Encoder]: Encoder[Qux[A]] = deriveEncoder
  def codecForQux[A: Decoder: Encoder]: Codec[Qux[A]] = deriveCodec

  implicit val decodeWub: Decoder[Wub] = deriveDecoder
  implicit val encodeWub: Encoder.AsObject[Wub] = deriveEncoder
  val codecForWub: Codec.AsObject[Wub] = deriveCodec

  implicit val decodeFoo: Decoder[Foo] = deriveDecoder
  implicit val encodeFoo: Encoder.AsObject[Foo] = deriveEncoder
  val codecForFoo: Codec.AsObject[Foo] = deriveCodec

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

    implicit val decodeRecursiveAdtExample: Decoder[RecursiveAdtExample] = deriveDecoder
    implicit val encodeRecursiveAdtExample: Encoder.AsObject[RecursiveAdtExample] = deriveEncoder
    val codecForRecursiveAdtExample: Codec.AsObject[RecursiveAdtExample] = deriveCodec
  }

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

    implicit val decodeRecursiveWithOptionExample: Decoder[RecursiveWithOptionExample] =
      deriveDecoder

    implicit val encodeRecursiveWithOptionExample: Encoder.AsObject[RecursiveWithOptionExample] =
      deriveEncoder

    val codecForRecursiveWithOptionExample: Codec.AsObject[RecursiveWithOptionExample] =
      deriveCodec
  }

  case class OvergenerationExampleInner(i: Int)
  case class OvergenerationExampleOuter0(i: OvergenerationExampleInner)
  case class OvergenerationExampleOuter1(oi: Option[OvergenerationExampleInner])

  // ADT variants matching circe's SemiautoDerivationSuite

  sealed trait Adt1
  object Adt1 {
    case class Class1(int: Int) extends Adt1
    object Class1 {
      given decoder: Decoder[Class1] = deriveDecoder
      given encoder: Encoder.AsObject[Class1] = deriveEncoder

      given eq: Eq[Class1] = Eq.by(_.int)
      given arbitrary: Arbitrary[Class1] = Arbitrary(Arbitrary.arbitrary[Int].map(Class1(_)))
    }

    case object Object1 extends Adt1

    given decoder: Decoder[Adt1] = deriveDecoder
    given encoder: Encoder.AsObject[Adt1] = deriveEncoder

    given eq: Eq[Adt1] = Eq.instance {
      case (x: Class1, y: Class1) => x === y
      case (Object1, Object1)     => true
      case _                      => false
    }
    given arbitrary: Arbitrary[Adt1] = Arbitrary(Gen.oneOf(Arbitrary.arbitrary[Class1], Gen.const(Object1)))
  }

  sealed trait Adt2
  object Adt2 {
    case object Object1 extends Adt2
    case object Object2 extends Adt2

    given decoder: Decoder[Adt2] = deriveDecoder
    given encoder: Encoder.AsObject[Adt2] = deriveEncoder

    given eq: Eq[Adt2] = Eq.fromUniversalEquals
    given arbitrary: Arbitrary[Adt2] = Arbitrary(Gen.oneOf(Gen.const(Object1), Gen.const(Object2)))
  }

  sealed trait Adt3
  object Adt3 {
    case class Class1() extends Adt3
    case object Object1 extends Adt3

    given decoder: Decoder[Adt3] = deriveDecoder
    given encoder: Encoder.AsObject[Adt3] = deriveEncoder

    given eq: Eq[Adt3] = Eq.fromUniversalEquals
    given arbitrary: Arbitrary[Adt3] = Arbitrary(Gen.oneOf(Gen.const(Class1()), Gen.const(Object1)))
  }

  sealed trait Adt4
  object Adt4 {
    sealed trait SubTrait1 extends Adt4
    case class Class1() extends SubTrait1
    sealed trait SubTrait2 extends Adt4
    case object Object1 extends SubTrait2

    given decoder: Decoder[Adt4] = deriveDecoder
    given encoder: Encoder.AsObject[Adt4] = deriveEncoder

    given eq: Eq[Adt4] = Eq.fromUniversalEquals
    given arbitrary: Arbitrary[Adt4] = Arbitrary(Gen.oneOf(Gen.const(Class1()), Gen.const(Object1)))
  }
}

class SemiautoDerivedSuite extends CirceMunitSuite {
  import SemiautoDerivedSuite._

  checkAll("Codec[Tuple1[Int]]", CodecTests[Tuple1[Int]].codec)
  checkAll("Codec[(Int, Int, Foo)]", CodecTests[(Int, Int, Foo)].codec)
  checkAll("Codec[Box[Int]]", CodecTests[Box[Int]].codec)
  checkAll("Codec[Box[Int]] via Codec", CodecTests[Box[Int]](using codecForBox[Int], codecForBox[Int]).codec)
  checkAll("Codec[Box[Int]] via Decoder and Codec", CodecTests[Box[Int]](using summon, codecForBox[Int]).codec)
  checkAll("Codec[Box[Int]] via Encoder and Codec", CodecTests[Box[Int]](using codecForBox[Int], summon).codec)
  checkAll("Codec[Qux[Int]]", CodecTests[Qux[Int]].codec)
  checkAll("Codec[Qux[Int]] via Codec", CodecTests[Qux[Int]](using codecForQux[Int], codecForQux[Int]).codec)
  checkAll("Codec[Qux[Int]] via Decoder and Codec", CodecTests[Qux[Int]](using summon, codecForQux[Int]).codec)
  checkAll("Codec[Qux[Int]] via Encoder and Codec", CodecTests[Qux[Int]](using codecForQux[Int], summon).codec)
  checkAll("Codec[Seq[Foo]]", CodecTests[Seq[Foo]].codec)
  checkAll("Codec[Baz]", CodecTests[Baz].codec)
  checkAll("Codec[Foo]", CodecTests[Foo].codec)
  checkAll("Codec[Foo] via Codec", CodecTests[Foo](using codecForFoo, codecForFoo).codec)
  checkAll("Codec[Foo] via Decoder and Codec", CodecTests[Foo](using summon, codecForFoo).codec)
  checkAll("Codec[Foo] via Encoder and Codec", CodecTests[Foo](using codecForFoo, summon).codec)
  checkAll("Codec[RecursiveAdtExample]", CodecTests[RecursiveAdtExample].codec)
  checkAll(
    "Codec[RecursiveAdtExample] via Codec",
    CodecTests[RecursiveAdtExample](using
      RecursiveAdtExample.codecForRecursiveAdtExample,
      RecursiveAdtExample.codecForRecursiveAdtExample
    ).codec
  )
  checkAll(
    "Codec[RecursiveAdtExample] via Decoder and Codec",
    CodecTests[RecursiveAdtExample](using summon, RecursiveAdtExample.codecForRecursiveAdtExample).codec
  )
  checkAll(
    "Codec[RecursiveAdtExample] via Encoder and Codec",
    CodecTests[RecursiveAdtExample](using RecursiveAdtExample.codecForRecursiveAdtExample, summon).codec
  )
  checkAll("Codec[RecursiveWithOptionExample]", CodecTests[RecursiveWithOptionExample].codec)
  checkAll(
    "Codec[RecursiveWithOptionExample] via Codec",
    CodecTests[RecursiveWithOptionExample](using
      RecursiveWithOptionExample.codecForRecursiveWithOptionExample,
      RecursiveWithOptionExample.codecForRecursiveWithOptionExample
    ).codec
  )
  checkAll(
    "Codec[RecursiveWithOptionExample] via Decoder and Codec",
    CodecTests[RecursiveWithOptionExample](using
      summon,
      RecursiveWithOptionExample.codecForRecursiveWithOptionExample
    ).codec
  )
  checkAll(
    "Codec[RecursiveWithOptionExample] via Encoder and Codec",
    CodecTests[RecursiveWithOptionExample](using
      RecursiveWithOptionExample.codecForRecursiveWithOptionExample,
      summon
    ).codec
  )

  checkAll("Codec[Adt1]", CodecTests[Adt1].codec)
  checkAll("Codec[Adt2]", CodecTests[Adt2].codec)
  checkAll("Codec[Adt3]", CodecTests[Adt3].codec)
  checkAll("Codec[Adt4]", CodecTests[Adt4].codec)

  property("A generically derived codec should not interfere with base instances") {
    Prop.forAll { (is: List[Int]) =>
      val json = Encoder[List[Int]].apply(is)

      assert(json === Json.fromValues(is.map(Json.fromInt)) && json.as[List[Int]] === Right(is))
    }
  }

  property("A generically derived codec for an empty case class should not accept non-objects") {
    Prop.forAll { (j: Json) =>
      case class EmptyCc()

      assert(deriveDecoder[EmptyCc].decodeJson(j).isRight == j.isObject)
      assert(deriveCodec[EmptyCc].decodeJson(j).isRight == j.isObject)
    }
  }

  test("Decoder for ADT/Enum ignores superfluous keys") {
    import io.circe.parser.decode
    val expected = Right(Adt1.Class1(3))
    assertEquals(decode[Adt1]("""{"Class1":{"int":3}}"""), expected)
    assertEquals(decode[Adt1]("""{"extraField":true,"Class1":{"int":3}}"""), expected)
    assertEquals(decode[Adt1]("""{"extraField":true,"extraField2":15,"Class1":{"int":3}}"""), expected)
    assertEquals(decode[Adt1]("""{"Class1":{"int":3},"extraField":true}"""), expected)
  }
}
