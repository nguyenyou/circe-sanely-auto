// AUTO-GENERATED from circe v0.14.15 by scripts/sync-circe-tests.py
// Source: upstream/circe/modules/tests/shared/src/test/scala-3/io/circe/SemiautoDerivationSuite.scala
//
// Transformations applied:
//   - Package: io.circe -> io.circe.generic
//   - Renamed: SemiautoDerivationSuite -> SemiautoDerivedSuite
//   - Replaced: Decoder.derived -> deriveDecoder
//   - Replaced: Encoder.AsObject.derived / Encoder.derived -> deriveEncoder
//   - Replaced: Codec.AsObject.derived / Codec.derived -> deriveCodec
//   - Added: import io.circe.generic.semiauto._
//
// DO NOT EDIT — regenerate with: python3 scripts/sync-circe-tests.py

package io.circe.generic

import cats.kernel.Eq
import cats.syntax.eq.*
import io.circe.testing.CodecTests
import io.circe.tests.CirceMunitSuite
import org.scalacheck.{ Arbitrary, Gen }
import io.circe.{ Codec, Decoder, Encoder }
import io.circe.generic.semiauto._

object SemiautoDerivedSuite {
  case class Box[A](a: A)

  object Box {
    given decoder[A: Decoder]: Decoder[Box[A]] = deriveDecoder
    given encoder[A: Encoder]: Encoder.AsObject[Box[A]] = deriveEncoder

    given eq[A: Eq]: Eq[Box[A]] = Eq.by(_.a)
    given arbitrary[A](using A: Arbitrary[A]): Arbitrary[Box[A]] = Arbitrary(A.arbitrary.map(Box(_)))
  }

  case class Foo(int: Int)

  object Foo {
    given decoder: Decoder[Foo] = deriveDecoder
    given encoder: Encoder.AsObject[Foo] = deriveEncoder

    given eq: Eq[Foo] = Eq.by(_.int)
    given arbitrary: Arbitrary[Foo] = Arbitrary(Arbitrary.arbitrary[Int].map(Foo(_)))
  }

  case class Bar(foo: Box[Foo])

  object Bar {
    // We can derive a `Decoder` and `Encoder` for `Bar` because instances of each exist for `Foo`
    given decoder: Decoder[Bar] = deriveDecoder
    given encoder: Encoder.AsObject[Bar] = deriveEncoder

    given eq: Eq[Bar] = Eq.by(_.foo)
    given arbitrary: Arbitrary[Bar] = Arbitrary(Arbitrary.arbitrary[Box[Foo]].map(Bar(_)))
  }

  case class Baz(str: String)

  // We cannot derive a `Decoder` or `Encoder` for `Quux` because no instances exist for `Baz`
  // see test below for proof that deriving instances fails to compile
  case class Quux(baz: Box[Baz])

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

    // We can derive a `Decoder` and `Encoder` for `Adt1` because instances of each exist for `Class1`
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

    // We can derive a `Decoder` and `Encoder` for `Adt2` because its members are all `case object`s
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

  sealed trait Adt5
  object Adt5 {
    case class Nested()
    case class Class1(nested: Nested) extends Adt5
    case object Object1 extends Adt5

    // We cannot derive a `Decoder` or `Encoder` for `Adt5` because no instances exist for `Nested`
    // see test below for proof that deriving instances fails to compile
  }
}

class SemiautoDerivedSuite extends CirceMunitSuite {
  import SemiautoDerivedSuite.*

  checkAll("Codec[Foo]", CodecTests[Foo].codec)
  checkAll("Codec[Box[Foo]]", CodecTests[Box[Foo]].codec)
  checkAll("Codec[Bar]", CodecTests[Bar].codec)
  checkAll("Codec[Box[Bar]]", CodecTests[Box[Bar]].codec)
  checkAll("Codec[Adt1]", CodecTests[Adt1].codec)
  checkAll("Codec[Adt2]", CodecTests[Adt2].codec)
  checkAll("Codec[Adt3]", CodecTests[Adt3].codec)
  checkAll("Codec[Adt4]", CodecTests[Adt4].codec)

  test("Nested case classes cannot be derived") {
    assert(compileErrors("deriveDecoder[Quux]").nonEmpty)
    assert(compileErrors("deriveEncoder[Quux]").nonEmpty)
  }

  test("Nested ADTs cannot be derived") {
    assert(compileErrors("deriveDecoder[Adt5]").nonEmpty)
    assert(compileErrors("deriveEncoder[Adt5]").nonEmpty)
  }

  // Local classes use `implicit val` instead of `given` to ensure derivation is strictly evaluated
  // `given`s are desugared to `lazy val`s and part of the point of these tests is to ensure that
  // deriving with strict `val`s doesn't cause `StackOverflowError`s
  // See https://github.com/circe/circe/pull/2278
  def testLocalCaseClasses(): Unit = {
    // Standard derivation of `Decoder` and `Encoder` separately
    case class LocalCaseClass1(int: Int)
    object LocalCaseClass1 {
      implicit val decoder: Decoder[LocalCaseClass1] = deriveDecoder
      implicit val encoder: Encoder.AsObject[LocalCaseClass1] = deriveEncoder

      given eq: Eq[LocalCaseClass1] = Eq.by(_.int)
      given arbitrary: Arbitrary[LocalCaseClass1] = Arbitrary(Arbitrary.arbitrary[Int].map(LocalCaseClass1(_)))
    }

    // Derivation of `Decoder` and `Encoder` separately using relaxed derivation for `Encoder`
    case class LocalCaseClass2(int: Int)
    object LocalCaseClass2 {
      implicit val decoder: Decoder[LocalCaseClass2] = deriveDecoder
      implicit val encoder: Encoder.AsObject[LocalCaseClass2] = deriveEncoder

      given eq: Eq[LocalCaseClass2] = Eq.by(_.int)
      given arbitrary: Arbitrary[LocalCaseClass2] = Arbitrary(Arbitrary.arbitrary[Int].map(LocalCaseClass2(_)))
    }

    // Standard derivation of `Codec` separately
    case class LocalCaseClass3(int: Int)
    object LocalCaseClass3 {
      implicit val codec: Codec.AsObject[LocalCaseClass3] = deriveCodec

      given eq: Eq[LocalCaseClass3] = Eq.by(_.int)
      given arbitrary: Arbitrary[LocalCaseClass3] = Arbitrary(Arbitrary.arbitrary[Int].map(LocalCaseClass3(_)))
    }

    // Relaxed derivation of `Codec`
    case class LocalCaseClass4(int: Int)
    object LocalCaseClass4 {
      implicit val codec: Codec.AsObject[LocalCaseClass4] = deriveCodec

      given eq: Eq[LocalCaseClass4] = Eq.by(_.int)
      given arbitrary: Arbitrary[LocalCaseClass4] = Arbitrary(Arbitrary.arbitrary[Int].map(LocalCaseClass4(_)))
    }

    checkAll("Codec[LocalCaseClass1]", CodecTests[LocalCaseClass1].unserializableCodec)
    checkAll("Codec[LocalCaseClass2]", CodecTests[LocalCaseClass2].unserializableCodec)
    checkAll("Codec[LocalCaseClass3]", CodecTests[LocalCaseClass3].unserializableCodec)
    checkAll("Codec[LocalCaseClass4]", CodecTests[LocalCaseClass4].unserializableCodec)
  }

  def testLocalAdts(): Unit = {
    // Standard derivation of `Decoder` and `Encoder` separately
    sealed trait LocalAdt1
    object LocalAdt1 {
      case class Class(int: Int) extends LocalAdt1
      object Class {
        given eq: Eq[Class] = Eq.by(_.int)
        given arbitrary: Arbitrary[Class] = Arbitrary(Arbitrary.arbitrary[Int].map(Class(_)))
      }
      case object Object extends LocalAdt1

      implicit val decoder: Decoder[LocalAdt1] = deriveDecoder
      implicit val encoder: Encoder.AsObject[LocalAdt1] = deriveEncoder

      given eq: Eq[LocalAdt1] = Eq.instance {
        case (x: Class, y: Class) => x === y
        case (Object, Object)     => true
        case _                    => false
      }
      given arbitrary: Arbitrary[LocalAdt1] = Arbitrary(Gen.oneOf(Arbitrary.arbitrary[Class], Gen.const(Object)))
    }

    // Derivation of `Decoder` and `Encoder` separately using relaxed derivation for `Encoder`
    sealed trait LocalAdt2
    object LocalAdt2 {
      case class Class(int: Int) extends LocalAdt2
      object Class {
        given eq: Eq[Class] = Eq.by(_.int)
        given arbitrary: Arbitrary[Class] = Arbitrary(Arbitrary.arbitrary[Int].map(Class(_)))
      }
      case object Object extends LocalAdt2

      implicit val decoder: Decoder[LocalAdt2] = deriveDecoder
      implicit val encoder: Encoder.AsObject[LocalAdt2] = deriveEncoder

      given eq: Eq[LocalAdt2] = Eq.instance {
        case (x: Class, y: Class) => x === y
        case (Object, Object)     => true
        case _                    => false
      }
      given arbitrary: Arbitrary[LocalAdt2] = Arbitrary(Gen.oneOf(Arbitrary.arbitrary[Class], Gen.const(Object)))
    }

    // Standard derivation of `Codec` separately
    sealed trait LocalAdt3
    object LocalAdt3 {
      case class Class(int: Int) extends LocalAdt3
      object Class {
        given eq: Eq[Class] = Eq.by(_.int)
        given arbitrary: Arbitrary[Class] = Arbitrary(Arbitrary.arbitrary[Int].map(Class(_)))
      }
      case object Object extends LocalAdt3

      implicit val codec: Codec.AsObject[LocalAdt3] = deriveCodec

      given eq: Eq[LocalAdt3] = Eq.instance {
        case (x: Class, y: Class) => x === y
        case (Object, Object)     => true
        case _                    => false
      }
      given arbitrary: Arbitrary[LocalAdt3] = Arbitrary(Gen.oneOf(Arbitrary.arbitrary[Class], Gen.const(Object)))
    }

    // Relaxed derivation of `Codec`
    sealed trait LocalAdt4
    object LocalAdt4 {
      case class Class(int: Int) extends LocalAdt4
      object Class {
        given eq: Eq[Class] = Eq.by(_.int)
        given arbitrary: Arbitrary[Class] = Arbitrary(Arbitrary.arbitrary[Int].map(Class(_)))
      }
      case object Object extends LocalAdt4

      implicit val codec: Codec.AsObject[LocalAdt4] = deriveCodec

      given eq: Eq[LocalAdt4] = Eq.instance {
        case (x: Class, y: Class) => x === y
        case (Object, Object)     => true
        case _                    => false
      }
      given arbitrary: Arbitrary[LocalAdt4] = Arbitrary(Gen.oneOf(Arbitrary.arbitrary[Class], Gen.const(Object)))
    }

    checkAll("Codec[LocalAdt1]", CodecTests[LocalAdt1].unserializableCodec)
    checkAll("Codec[LocalAdt2]", CodecTests[LocalAdt2].unserializableCodec)
    checkAll("Codec[LocalAdt3]", CodecTests[LocalAdt3].unserializableCodec)
    checkAll("Codec[LocalAdt4]", CodecTests[LocalAdt4].unserializableCodec)
  }

  testLocalCaseClasses()
  testLocalAdts()

  test("Decoder for ADT/Enum ignores superfluous keys") {
    import io.circe.parser.decode
    val expected = Right(Adt1.Class1(3))
    assertEquals(decode[Adt1]("""{"Class1":{"int":3}}"""), expected)
    assertEquals(decode[Adt1]("""{"extraField":true,"Class1":{"int":3}}"""), expected)
    assertEquals(decode[Adt1]("""{"extraField":true,"extraField2":15,"Class1":{"int":3}}"""), expected)
    assertEquals(decode[Adt1]("""{"Class1":{"int":3},"extraField":true}"""), expected)
  }
}
