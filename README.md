# circe-sanely-auto

Drop-in replacement for circe's automatic Encoder/Decoder derivation, built with Scala 3 macros. No Shapeless. No circe-generic.

Based on the [sanely-automatic derivation](https://kubuszok.com/2025/sanely-automatic-derivation/) approach: a single macro expansion recursively derives all nested instances at compile time, avoiding the implicit search chains that make circe-generic slow to compile.

**Scala 3.8.2+ only.**

## Usage

```scala
import io.circe.*
import io.circe.syntax.*
import sanely.auto.given

case class Address(street: String, city: String)
case class Person(name: String, age: Int, address: Address)

val json = Person("Alice", 30, Address("123 Main St", "Springfield")).asJson
// {"name":"Alice","age":30,"address":{"street":"123 Main St","city":"Springfield"}}

val decoded = io.circe.parser.decode[Person](json.noSpaces)
// Right(Person(Alice, 30, Address(123 Main St, Springfield)))
```

Sum types use external tagging:

```scala
enum Shape:
  case Circle(radius: Double)
  case Rectangle(width: Double, height: Double)

Shape.Circle(5.0).asJson
// {"Circle":{"radius":5.0}}
```

## Building

Requires [Mill](https://mill-build.org/) (bootstrapped via `./mill` wrapper).

```bash
./mill sanely.compile    # compile library
./mill sanely.test       # run tests
./mill demo.run          # run demo
```

## Goal

API-compatible with circe's auto-derivation. The success metric is passing all of circe's auto-derivation tests — only the implementation changes, not the user-facing API.

## Test Progress

Ported from circe's `DerivesSuite` / `SemiautoDerivationSuite`.

### Tier 1 — Products
- [ ] Single-field product (`Wub(x: Long)`)
- [ ] Multi-field product (`Bar(i: Int, s: String)`)
- [ ] Nested product (`Person(name, age, address: Address)`)
- [ ] Option field (`Outer(a: Option[Inner[String]])`)
- [ ] List field (`Baz(xs: List[String])`)
- [ ] Generic product (`Box[A](a: A)`)
- [ ] Multi-param generic (`Qux[A](i: Int, a: A, j: Int)`)

### Tier 1 — Sums
- [ ] Sealed trait with case classes (`Foo: Bar|Baz|Bam`)
- [ ] Enum with case classes + case object (`Vegetable`)
- [ ] ADT with case object only (`Adt2: Object1|Object2`)
- [ ] ADT with case class + case object (`Adt1`)

### Tier 1 — Recursive
- [ ] Recursive sealed trait (`RecursiveAdtExample`)
- [ ] Recursive Option (`RecursiveWithOptionExample`)
- [ ] Recursive enum (`RecursiveEnumAdt`)

### Tier 1 — Shape Assertions
- [ ] Product field names preserved
- [ ] Sum externally tagged `{"Variant": {...}}`
- [ ] Case object encodes as `{}`
- [ ] None encodes as null
- [ ] User-provided instances respected

### Tier 2 — Edge Cases
- [ ] Empty case class in ADT (`Adt3: Class1()|Object1`)
- [ ] Superfluous JSON keys ignored by sum decoder
- [ ] Large product (33 fields)
- [ ] Large sum (33 variants)

### Tier 3 — New Features
- [ ] Sub-trait flattening (`ADTWithSubTraitExample`)
- [ ] Tagged type members (`ProductWithTaggedMember`)

### Error Cases
- [ ] Wrong JSON shape → Left
- [ ] Unknown variant → Left
- [ ] Non-object for sum → Left
