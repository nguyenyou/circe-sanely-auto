// AUTO-GENERATED from circe v0.14.15 by scripts/sync-circe-tests.py
// Source: upstream/circe/modules/tests/shared/src/test/scala-3/io/circe/ConfiguredEnumDerivesSuites.scala
//
// Transformations applied:
//   - Package: io.circe -> io.circe.generic
//   - Replaced: ConfiguredEnumCodec.derived -> deriveEnumCodec
//   - Transformed: compile-error test to use deriveEnumCodec
//   - Replaced: import io.circe.derivation.* -> import io.circe.derivation.Configuration
//   - Added: import io.circe.generic.semiauto.*
//
// DO NOT EDIT — regenerate with: python3 scripts/sync-circe-tests.py

package io.circe.generic

import cats.kernel.Eq
import cats.kernel.instances.all.*
import cats.syntax.eq.*
import cats.data.Validated
import io.circe.{ Codec, Decoder, DecodingFailure, Encoder, Json }
import io.circe.CursorOp.DownField
import io.circe.testing.CodecTests
import io.circe.tests.CirceMunitSuite
import io.circe.derivation.Configuration
import io.circe.syntax.*
import org.scalacheck.{ Arbitrary, Gen }
import org.scalacheck.Prop.forAll
import io.circe.generic.semiauto.*

object ConfiguredEnumDerivesSuites:
  enum WithNonSingletonCase:
    case SingletonCase
    case NonSingletonCase(field: Int)

  // "derives ConfiguredEnumCodec" is not here so we can change the configuration for the derivation in each test
  enum IntercardinalDirections:
    case NorthEast, SouthEast, SouthWest, NorthWest
  object IntercardinalDirections:
    given Eq[IntercardinalDirections] = Eq.fromUniversalEquals
    given Arbitrary[IntercardinalDirections] = Arbitrary(
      Gen.oneOf(
        Gen.const(IntercardinalDirections.NorthEast),
        Gen.const(IntercardinalDirections.SouthEast),
        Gen.const(IntercardinalDirections.SouthWest),
        Gen.const(IntercardinalDirections.NorthWest)
      )
    )

  sealed trait HierarchicalEnum
  object HierarchicalEnum:
    sealed trait NestedA extends HierarchicalEnum
    sealed trait NestedB extends HierarchicalEnum

    case object A extends HierarchicalEnum
    case object B extends NestedA
    case object C extends NestedB
    case object D extends NestedA, NestedB // diamond structure

    given Eq[HierarchicalEnum] = Eq.fromUniversalEquals
    given Arbitrary[HierarchicalEnum] = Arbitrary(
      Gen.oneOf(
        Gen.const(HierarchicalEnum.A),
        Gen.const(HierarchicalEnum.B),
        Gen.const(HierarchicalEnum.C),
        Gen.const(HierarchicalEnum.D)
      )
    )

class ConfiguredEnumDerivesSuites extends CirceMunitSuite:
  import ConfiguredEnumDerivesSuites.*

  test("ConfiguredEnum derivation must fail to compile for enums with non singleton cases") {
    given Configuration = Configuration.default
    assert(compileErrors("deriveEnumCodec[ConfiguredEnumDerivesSuites.WithNonSingletonCase]").nonEmpty)
  }

  {
    given Configuration = Configuration.default
    given Codec[IntercardinalDirections] = deriveEnumCodec
    checkAll("Codec[IntercardinalDirections] (default configuration)", CodecTests[IntercardinalDirections].codec)
  }

  test("Fail to decode if case name does not exist") {
    given Configuration = Configuration.default
    given Codec[IntercardinalDirections] = deriveEnumCodec
    val json = Json.fromString("NorthNorth")
    val failure = DecodingFailure("enum IntercardinalDirections does not contain case: NorthNorth", List())
    assert(Decoder[IntercardinalDirections].decodeJson(json) === Left(failure))
    assert(Decoder[IntercardinalDirections].decodeAccumulating(json.hcursor) === Validated.invalidNel(failure))
  }

  test("Configuration#transformConstructorNames should support constructor name transformation with snake_case") {
    given Configuration = Configuration.default.withSnakeCaseConstructorNames
    given Codec[IntercardinalDirections] = deriveEnumCodec

    val direction = IntercardinalDirections.NorthEast
    val json = Json.fromString("north_east")
    assert(summon[Encoder[IntercardinalDirections]].apply(direction) === json)
    assert(summon[Decoder[IntercardinalDirections]].decodeJson(json) === Right(direction))
  }
  test(
    "Configuration#transformConstructorNames should support constructor name transformation with SCREAMING_SNAKE_CASE"
  ) {
    given Configuration = Configuration.default.withScreamingSnakeCaseConstructorNames
    given Codec[IntercardinalDirections] = deriveEnumCodec

    val direction = IntercardinalDirections.SouthEast
    val json = Json.fromString("SOUTH_EAST")
    assert(summon[Encoder[IntercardinalDirections]].apply(direction) === json)
    assert(summon[Decoder[IntercardinalDirections]].decodeJson(json) === Right(direction))
  }
  test("Configuration#transformConstructorNames should support constructor name transformation with kebab-case") {
    given Configuration = Configuration.default.withKebabCaseConstructorNames
    given Codec[IntercardinalDirections] = deriveEnumCodec

    val direction = IntercardinalDirections.SouthWest
    val json = Json.fromString("south-west")
    assert(summon[Encoder[IntercardinalDirections]].apply(direction) === json)
    assert(summon[Decoder[IntercardinalDirections]].decodeJson(json) === Right(direction))
  }

  test("Should work with nested hierarchies") {
    given Configuration = Configuration.default
    given Codec[HierarchicalEnum] = deriveEnumCodec
    {
      val value = HierarchicalEnum.A
      val json = Json.fromString("A")
      assertEquals(summon[Encoder[HierarchicalEnum]].apply(value), json)
      assertEquals(summon[Decoder[HierarchicalEnum]].decodeJson(json), Right(value))
    }

    {
      val value = HierarchicalEnum.C
      val json = Json.fromString("C")
      assertEquals(summon[Encoder[HierarchicalEnum]].apply(value), json)
      assertEquals(summon[Decoder[HierarchicalEnum]].decodeJson(json), Right(value))
    }
  }

  {
    given Configuration = Configuration.default
    given Codec[HierarchicalEnum] = deriveEnumCodec
    checkAll("Codec[HierarchicalEnum] (default configuration)", CodecTests[HierarchicalEnum].codec)
  }
