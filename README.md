# circe-sanely-auto

Drop-in replacement for circe's automatic Encoder/Decoder derivation, built with Scala 3 macros. No Shapeless. No circe-generic.

Based on the [sanely-automatic derivation](https://kubuszok.com/2025/sanely-automatic-derivation/) approach: uses `Expr.summonIgnoring` (Scala 3.7+) to exclude the auto-given from implicit search, then recursively derives all nested instances internally within a single macro expansion — avoiding the implicit search chains that make circe-generic slow to compile.

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

## Test Porting Plan

Tests ported from circe's `DerivesSuite` and `SemiautoDerivationSuite`. Approach: **port test first, then implement to pass**, before moving to the next phase.

Each test is a roundtrip: `encode(a) |> decode == Right(a)`. Circe uses property-based `CodecTests[T].codec` which checks roundtrip, accumulating consistency, and `Codec.from` consistency.

### Phase 1 — Simple Products *(already working)*

Basic case classes with primitive/standard-library fields. Our macro handles these via `Mirror.ProductOf` + `resolveOneEncoder`/`resolveOneDecoder` with `Expr.summonIgnoring`.

- [x] Multi-field product — `Simple(i: Int, s: String)`
- [x] Single-field product — `Wub(x: Long)`
- [ ] Nested product — `Person(name: String, age: Int, address: Address)`
- [ ] Option field — `Outer(a: Option[Inner[String]])` *(needs Inner to derive first)*
- [ ] List field — `Baz(xs: List[String])`

**What to test**: field names preserved in JSON keys, primitives roundtrip, nested objects roundtrip.

### Phase 2 — Simple Sum Types

Sealed traits / enums with case class variants. Our macro handles via `Mirror.SumOf` + ordinal dispatch. External tagging: `{"VariantName": {...}}`.

- [ ] Sealed trait with case classes — `Foo` with `Bar(i: Int, s: String)`, `Baz(xs: List[String])`, `Bam(w: Wub, d: Double)`
- [ ] Enum with case classes — `Shape.Circle(radius)`, `Shape.Rectangle(width, height)`

**What to test**: external tagging shape, each variant roundtrips, nested products inside variants.

### Phase 3 — Case Objects in Sums

Case objects have no fields → should encode as `{}` inside the wrapper. Requires handling `Mirror.ProductOf` with `EmptyTuple` element types.

- [ ] ADT with case objects only — `Adt2(Object1 | Object2)` → `{"Object1":{}}`
- [ ] ADT with case class + case object — `Adt1(Class1(int: Int) | Object1)`
- [ ] Empty case class in ADT — `Adt3(Class1() | Object1)` *(Class1 has zero fields)*
- [ ] Enum with case object — `Vegetable(Potato | Carrot | Onion | Turnip)` where `Turnip` is a case object

**What to test**: `Object1.asJson == {"Object1":{}}`, mixed case class/object ADTs roundtrip.

### Phase 4 — User-Provided Instances Respected

When a type already has an implicit `Encoder`/`Decoder`, our macro's `Expr.summonIgnoring` finds it (since it only excludes our own auto-given, not user-provided instances) instead of re-deriving. This is core to the "sanely-automatic" approach.

- [ ] `Foo` with custom-encoded children — `Bar` has `Encoder.forProduct2`, `Baz` encodes as JSON array (not object)
- [ ] `Outer(a: Option[Inner[String]])` — should use `Inner`'s derived encoder, not re-derive
- [ ] Nested sums not encoded redundantly — `ADTWithSubTraitExample` → `TheClass(0)` becomes `{"TheClass":{"a":0}}` not `{"SubTrait":{"TheClass":{"a":0}}}`

**What to test**: JSON shape matches circe's expected output exactly, custom encoders are not overridden.

### Phase 5 — Generic Types

Type-parameterized case classes: `Box[A](a: A)`, `Qux[A](i: Int, a: A, j: Int)`. The macro must handle abstract type parameters — when expanding `Box[Wub]`, it resolves `Encoder[Wub]` for the field.

- [ ] `Box[Long]` — generic wrapping primitive
- [ ] `Box[Wub]` — generic wrapping product
- [ ] `Qux[Long]` — generic with mixed fields
- [ ] `Box[Foo]` — generic wrapping sum type
- [ ] `Bar(foo: Box[Foo])` — nested generic

**What to test**: type parameter resolution at macro expansion time, nested generics.

### Phase 6 — Recursive Types

Self-referencing types. The macro must break the recursion — can't inline-expand forever. Likely needs lazy val or by-name encoder/decoder.

- [ ] Recursive sealed trait — `RecursiveAdtExample(Base(a: String) | Nested(r: RecursiveAdtExample))`
- [ ] Recursive with Option — `RecursiveWithOptionExample(o: Option[RecursiveWithOptionExample])`
- [ ] Recursive enum — `RecursiveEnumAdt(Base(a: String) | Nested(r: RecursiveEnumAdt))`

**What to test**: encode/decode trees of depth 0–3, `None` terminates recursion.

### Phase 7 — Large Types & Edge Cases

Stress tests and unusual patterns.

- [ ] Large product — `LongClass` with 33 `String` fields *(may need `-Xmax-inlines` bump)*
- [ ] Large sum — `LongSum` with 33 case variants
- [ ] Large enum — `LongEnum` with 33 nullary cases
- [ ] Sub-trait flattening — `ADTWithSubTraitExample` *(sealed trait → sealed sub-trait → case class)*
- [ ] Tagged type members — `ProductWithTaggedMember(x: TaggedString)` where `TaggedString = String with Tag`
- [ ] Superfluous JSON keys ignored — decoder for `Adt1` handles `{"extraField":true,"Class1":{"int":3}}`

### Phase 8 — Error Cases

- [ ] Wrong JSON shape → `Left(DecodingFailure(...))`
- [ ] Unknown variant → `Left(DecodingFailure(...))`
- [ ] Non-object for sum type → `Left(DecodingFailure(...))`
- [ ] Compile error when nested type has no `Encoder`/`Decoder` and no `Mirror`

### Phase 9 — Semiauto API *(optional)*

Explicit `SanelyEncoder.derived[A]` / `SanelyDecoder.derived[A]` calls (already the internal API). Mirror circe's `Decoder.derived` / `Encoder.AsObject.derived` surface:

- [ ] `Decoder.derived[Foo]` / `Encoder.AsObject.derived[Foo]`
- [ ] Local case class derivation with strict `val` (no `StackOverflowError`)
- [ ] Local ADT derivation with strict `val`

## Implementation Challenges by Phase

| Phase | Key Challenge |
|-------|--------------|
| 1 | None — already works |
| 2 | None — already works for case-class-only sums |
| 3 | Case objects: `Mirror.ProductOf` with `EmptyTuple`, singleton encoding |
| 4 | `Expr.summonIgnoring` already skips auto-given; verify user instances are found |
| 5 | Type params: macro must resolve `Encoder[A]` when `A` is abstract |
| 6 | Recursion: break infinite macro expansion, likely needs lazy wrapper |
| 7 | Inline budget for 33 fields/variants; sub-trait Mirror flattening |
| 8 | Error messages matching circe's format |
| 9 | API surface compatibility |
