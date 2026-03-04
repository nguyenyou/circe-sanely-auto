# circe-sanely-auto

Drop-in replacement for circe's automatic Encoder/Decoder derivation, built with Scala 3 macros. No Shapeless. No circe-generic.

Based on the [sanely-automatic derivation](https://kubuszok.com/2025/sanely-automatic-derivation/) approach: uses `Expr.summonIgnoring` (Scala 3.7+) to exclude the auto-given from implicit search, then recursively derives all nested instances internally within a single macro expansion — avoiding the implicit search chains that make circe-generic slow to compile.

**Scala 3.8.2+ only.** Cross-published for JVM and Scala.js.

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

## Configured Derivation

For `io.circe.derivation.Configuration`-based derivation (snake_case field names, discriminator fields, default values, strict decoding, enum string codecs):

```scala
import io.circe.*
import io.circe.derivation.Configuration
import io.circe.generic.semiauto.*

given Configuration = Configuration.default
  .withSnakeCaseMemberNames
  .withDiscriminator("type")
  .withDefaults

case class User(firstName: String, lastName: String, age: Int = 25)
given Codec.AsObject[User] = deriveConfiguredCodec

User("Alice", "Smith", 30).asJson
// {"first_name":"Alice","last_name":"Smith","age":30,"type":"User"}
```

Configured API surface:

```scala
import io.circe.generic.semiauto.*

// With a given Configuration in scope:
deriveConfiguredEncoder[A]  // Encoder.AsObject[A]
deriveConfiguredDecoder[A]  // Decoder[A]
deriveConfiguredCodec[A]    // Codec.AsObject[A]
deriveEnumCodec[A]          // Codec[A] — singleton enum ↔ string
```

Or use the macro objects directly:

```scala
SanelyConfiguredEncoder.derived[A]  // Encoder.AsObject[A]
SanelyConfiguredDecoder.derived[A]  // Decoder[A]
SanelyConfiguredCodec.derived[A]    // Codec.AsObject[A]
SanelyEnumCodec.derived[A]          // Codec[A]
```

### Configuration options

| Option | Effect |
|---|---|
| `transformMemberNames` | Rename JSON keys (e.g., `withSnakeCaseMemberNames`) |
| `transformConstructorNames` | Rename ADT variant names (e.g., `withSnakeCaseConstructorNames`) |
| `useDefaults` | Use Scala default parameter values for missing/null JSON fields |
| `discriminator` | `Some("type")` → flat `{"type":"Variant",...}` instead of `{"Variant":{...}}` |
| `strictDecoding` | Reject unexpected JSON keys |

### Enum string codec

For enums with only singleton cases (no fields), encode as plain JSON strings:

```scala
enum Color:
  case Red, Green, Blue

given Configuration = Configuration.default.withSnakeCaseConstructorNames
given Codec[Color] = deriveEnumCodec

Color.Red.asJson  // "red"
```

## Migration from circe-generic

### Step 1: Swap the dependency

Remove `circe-generic` and add `circe-sanely-auto`:

```diff
-  mvn"io.circe::circe-generic:0.14.13"
+  mvn"io.github.nguyenyou::circe-sanely-auto:VERSION"
```

### Step 2: Update imports

No code changes needed if you use the drop-in aliases:

| circe-generic | circe-sanely-auto (drop-in) | Alternative |
|---|---|---|
| `import io.circe.generic.auto._` | `import io.circe.generic.auto.given` | `import sanely.auto.given` |
| `import io.circe.generic.semiauto._` | `import io.circe.generic.semiauto._` | — |

The `io.circe.generic.auto` and `io.circe.generic.semiauto` packages are provided by this library — they delegate to the sanely macro engine internally.

### Step 3: Update semiauto call sites (if any)

```diff
  object MyType:
-   given Decoder[MyType] = deriveDecoder
-   given Encoder.AsObject[MyType] = deriveEncoder
+   given Decoder[MyType] = deriveDecoder          // unchanged
+   given Encoder.AsObject[MyType] = deriveEncoder  // unchanged
```

Semiauto works identically. `deriveCodec` is also available:

```scala
import io.circe.generic.semiauto.*

object MyType:
  given Codec.AsObject[MyType] = deriveCodec
```

### What stays the same

- JSON format: products → `{"field": value}`, sums → `{"VariantName": {...}}`
- User-provided instances are respected (not overridden by auto-derivation)
- All standard containers work: `Option`, `List`, `Vector`, `Set`, `Seq`, `Map`, etc.
- Recursive types work
- `Codec.AsObject` works via `deriveCodec` or `SanelyCodec.derived`

### What changes

- **Faster compile times** — single macro expansion instead of implicit search chains
- **Scala 3 only** — no Scala 2 support, requires 3.8.2+
- **No Shapeless dependency** — uses Scala 3 `Mirror` + `Expr.summonIgnoring`
- **`derives` keyword** — works as-is via circe-core (no migration needed); `Encoder.AsObject.derived`/`Decoder.derived` are defined in circe-core itself, independent of circe-generic

## Compile-Time Benchmark

The whole point of this library is faster compile times. The benchmark compiles the same source code (~300 types across 9 files) against both `circe-sanely-auto` and `circe-generic`:

```bash
bash bench.sh 5   # 5 iterations per module, reports median
```

Results on an M3 Max MacBook Pro (Mill 1.1.2, Scala 3.8.2):

| | Median compile time | |
|---|---|---|
| **circe-sanely-auto** | **3.77s** | |
| **circe-generic** | **6.07s** | 1.6× slower |

The benchmark includes: nested products, sealed trait hierarchies, generic type instantiations, wide case classes (22 fields × 8), and cross-domain compositions — representative of a real-world codebase. Times include Mill/JVM overhead.

You can also compile and run each module individually:

```bash
./mill benchmark.sanely.compile   # compile with our library
./mill benchmark.generic.compile  # compile with circe-generic
./mill benchmark.sanely.run       # verify round-trips pass
./mill benchmark.generic.run      # verify round-trips pass
```

## Building

Requires [Mill](https://mill-build.org/) (bootstrapped via `./mill` wrapper).

```bash
./mill sanely.jvm.compile  # compile library (JVM)
./mill sanely.js.compile   # compile library (Scala.js)
./mill sanely.jvm.test     # run tests (JVM)
./mill sanely.js.test      # run tests (Scala.js)
./mill demo.run            # run demo
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
- [x] Nested product — `Person(name: String, age: Int, address: Address)`
- [x] Option field — `Outer(a: Option[Inner[String]])` *(needs Inner to derive first)*
- [x] List field — `Baz(xs: List[String])`

**What to test**: field names preserved in JSON keys, primitives roundtrip, nested objects roundtrip.

### Phase 2 — Simple Sum Types

Sealed traits / enums with case class variants. Our macro handles via `Mirror.SumOf` + ordinal dispatch. External tagging: `{"VariantName": {...}}`.

- [x] Sealed trait with case classes — `Foo` with `Bar(i: Int, s: String)`, `Baz(xs: List[String])`, `Bam(w: Wub, d: Double)`
- [x] Enum with case classes — `Shape.Circle(radius)`, `Shape.Rectangle(width, height)`

**What to test**: external tagging shape, each variant roundtrips, nested products inside variants.

### Phase 3 — Case Objects in Sums

Case objects have no fields → should encode as `{}` inside the wrapper. Requires handling `Mirror.ProductOf` with `EmptyTuple` element types.

- [x] ADT with case objects only — `Adt2(Object1 | Object2)` → `{"Object1":{}}`
- [x] ADT with case class + case object — `Adt1(Class1(int: Int) | Object1)`
- [x] Empty case class in ADT — `Adt3(Class1() | Object1)` *(Class1 has zero fields)*
- [x] Enum with case object — `Vegetable(Potato | Carrot | Onion | Turnip)` where `Turnip` is a case object

**What to test**: `Object1.asJson == {"Object1":{}}`, mixed case class/object ADTs roundtrip.

### Phase 4 — User-Provided Instances Respected

When a type already has an implicit `Encoder`/`Decoder`, our macro's `Expr.summonIgnoring` finds it (since it only excludes our own auto-given, not user-provided instances) instead of re-deriving. This is core to the "sanely-automatic" approach.

- [x] Custom `Encoder.AsObject` respected in nested type — `WrapsRenamed` uses `Renamed`'s custom field-renaming encoder
- [x] `Outer(a: Option[Inner[String]])` — macro internally derives `Inner[String]` and uses it
- [x] Nested sums not encoded redundantly — `ADTWithSubTraitExample` → `TheClass(0)` becomes `{"TheClass":{"a":0}}` *(tested in Phase 7 sub-trait flattening)*

**What to test**: JSON shape matches circe's expected output exactly, custom encoders are not overridden.

### Phase 5 — Generic Types

Type-parameterized case classes: `Box[A](a: A)`, `Qux[A](i: Int, a: A, j: Int)`. The macro must handle abstract type parameters — when expanding `Box[Wub]`, it resolves `Encoder[Wub]` for the field.

- [x] `Box[Long]` — generic wrapping primitive
- [x] `Box[Wub]` — generic wrapping product
- [x] `Qux[Long]` — generic with mixed fields
- [x] `Box[Foo]` — generic wrapping sum type
- [x] `Bar(foo: Box[Foo])` — nested generic

**What to test**: type parameter resolution at macro expansion time, nested generics.

### Phase 6 — Recursive Types

Self-referencing types. The macro breaks recursion using lazy val self-reference and container type detection.

- [x] Recursive sealed trait — `RecursiveAdtExample(Base(a: String) | Nested(r: RecursiveAdtExample))`
- [x] Recursive with Option — `RecursiveWithOptionExample(o: Option[RecursiveWithOptionExample])`
- [x] Recursive enum — `RecursiveEnumAdt(Base(a: String) | Nested(r: RecursiveEnumAdt))`

**What to test**: encode/decode trees of depth 0–3, `None` terminates recursion.

### Phase 7 — Large Types & Edge Cases

Stress tests and unusual patterns.

- [x] Large product — `LongClass` with 33 `String` fields
- [x] Large sum — `LongSum` with 33 case variants
- [x] Large enum — `LongEnum` with 33 nullary cases
- [x] Sub-trait flattening — `ADTWithSubTraitExample` *(sealed trait → sealed sub-trait → case class)*
- [x] Tagged type members — `ProductWithTaggedMember(x: TaggedString)` where `TaggedString = String & Tag`
- [x] Superfluous JSON keys ignored — decoder for `Adt1` handles `{"extraField":true,"Adt1Class1":{"int":3}}`

### Phase 8 — Error Cases

- [x] Wrong JSON shape → `Left(DecodingFailure(...))`
- [x] Unknown variant → `Left(DecodingFailure(...))`
- [x] Non-object for sum type → `Left(DecodingFailure(...))`
- ~~Compile error when nested type has no `Encoder`/`Decoder` and no `Mirror`~~ *(not testable in utest — `report.errorAndAbort` exists but compile errors can't be asserted at runtime)*

### Phase 9 — Semiauto API *(optional)*

Explicit `SanelyEncoder.derived[A]` / `SanelyDecoder.derived[A]` calls (already the internal API). Mirror circe's `Decoder.derived` / `Encoder.AsObject.derived` surface:

- [x] `SanelyDecoder.derived[Foo]` / `SanelyEncoder.derived[Foo]` in companion objects
- [x] Local case class derivation with strict `val` (no `StackOverflowError`)
- [x] Local ADT derivation with strict `val`

## Status

76 tests passing (52 auto-derivation + 24 configured derivation), on both JVM and Scala.js. The library provides:

- **`import sanely.auto.given`** — auto-derivation for `Encoder.AsObject` and `Decoder`
- **`import io.circe.generic.auto.given`** — drop-in alias using circe's `Exported` pattern
- **`io.circe.generic.semiauto.{deriveEncoder, deriveDecoder, deriveCodec}`** — explicit derivation
- **`io.circe.generic.semiauto.{deriveConfiguredEncoder, deriveConfiguredDecoder, deriveConfiguredCodec}`** — configured derivation with `io.circe.derivation.Configuration`
- **`io.circe.generic.semiauto.deriveEnumCodec`** — enum string codec
- **`SanelyCodec.derived[A]`** — `Codec.AsObject` derivation
- **`SanelyConfiguredCodec.derived[A]`** — configured `Codec.AsObject` derivation
- **`SanelyEnumCodec.derived[A]`** — enum string codec
- Recursive containers: `Option`, `List`, `Vector`, `Set`, `Seq`, `Map`, `Chain`, `NonEmptyList`, `NonEmptyVector`, `NonEmptySeq`, `NonEmptyChain`
- Cross-published for **JVM** and **Scala.js**

### Out of scope

- ~~**`derives` keyword support**~~ — works out of the box via circe-core; no action needed from this library
- ~~**`Either[E, Self]` recursive container**~~ — circe doesn't test this; `disjunctionCodecs` requires explicit import and string keys
- ~~**Compile error message quality**~~ — not testable in utest (compile errors can't be asserted at runtime)
