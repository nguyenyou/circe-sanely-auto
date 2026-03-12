package sanely.jsoniter

import com.github.plokhotnyuk.jsoniter_scala.core.*

// Types scoped to this test file only
case class SemiInner(x: Int, y: String)
case class SemiOuter(inner: SemiInner, z: Boolean)

sealed trait SemiAnimal
case class SemiDog(name: String) extends SemiAnimal
case class SemiCat(lives: Int) extends SemiAnimal

class SemiautoCompileTest extends munit.FunSuite:
  import sanely.jsoniter.semiauto.*

  test("semiauto: summon without explicit derivation must not compile") {
    assert(compileErrors("summon[JsonValueCodec[SemiInner]]").nonEmpty)
  }

  test("semiauto: summon sum type without explicit derivation must not compile") {
    assert(compileErrors("summon[JsonValueCodec[SemiAnimal]]").nonEmpty)
  }

  test("semiauto: explicit derivation compiles and works") {
    given JsonValueCodec[SemiInner] = deriveJsoniterCodec
    val inner = SemiInner(1, "hello")
    val json = writeToString(inner)
    val decoded = readFromString[SemiInner](json)
    assertEquals(decoded, inner)
  }

  test("semiauto: deriving Outer without Inner codec must not compile") {
    assert(compileErrors("deriveJsoniterCodec[SemiOuter]").nonEmpty)
  }

  test("semiauto: deriving Outer with explicit Inner codec works") {
    given JsonValueCodec[SemiInner] = deriveJsoniterCodec
    given JsonValueCodec[SemiOuter] = deriveJsoniterCodec
    val outer = SemiOuter(SemiInner(42, "test"), true)
    val json = writeToString(outer)
    val decoded = readFromString[SemiOuter](json)
    assertEquals(decoded, outer)
  }

  test("semiauto: sum type derives variants internally (matching circe semiauto)") {
    // circe's semiauto derives variant codecs internally for sum types —
    // only nested field types of products must have explicit codecs
    given JsonValueCodec[SemiAnimal] = deriveJsoniterCodec
    val dog: SemiAnimal = SemiDog("Rex")
    val json = writeToString(dog)
    val decoded = readFromString[SemiAnimal](json)
    assertEquals(decoded, dog)

    // But variants are NOT available as standalone givens
    assert(compileErrors("summon[JsonValueCodec[SemiDog]]").nonEmpty)
    assert(compileErrors("summon[JsonValueCodec[SemiCat]]").nonEmpty)
  }
