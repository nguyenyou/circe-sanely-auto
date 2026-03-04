# Changelog

## [0.3.0] - 2026-03-04

### Added
- **Configured derivation** — full support for `io.circe.derivation.Configuration`:
  - `transformMemberNames` (snake_case, SCREAMING_SNAKE_CASE, kebab-case, PascalCase)
  - `transformConstructorNames` (same transforms for ADT variant names)
  - `useDefaults` — use Scala default parameter values for missing/null JSON fields
  - `discriminator` — flat encoding with discriminator field (`{"type": "Variant", ...}`)
  - `strictDecoding` — reject unexpected JSON keys (products and sum types)
- **Enum string codec** (`SanelyEnumCodec`) — encode singleton enums as plain JSON strings with `transformConstructorNames` support and hierarchical sealed trait support
- **Configured API surface**:
  - `SanelyConfiguredEncoder.derived`, `SanelyConfiguredDecoder.derived`, `SanelyConfiguredCodec.derived`
  - `io.circe.generic.semiauto.{deriveConfiguredEncoder, deriveConfiguredDecoder, deriveConfiguredCodec, deriveEnumCodec}`
- **Scala.js cross-platform support** — published for both JVM and Scala.js
- **Compile-time benchmark** — comparing sanely vs circe-generic (~300 types, 1.6x faster)
- 57 configured derivation unit tests ported from circe's `ConfiguredDerivesSuite` and `ConfiguredEnumDerivesSuites`

### Fixed
- Default value lookup for generic classes (skip type parameter lists when counting constructor params)
- `Option` fields with `null` decode as `None` instead of using the default value
- Hierarchical sealed traits in enum codecs (recurse into `Mirror.SumOf` sub-types)
- Strict decoding on sum types now rejects multi-key JSON objects in external tagging mode

### Changed
- Bumped circe dependency to 0.14.15

## [0.2.0] - 2025-12-15

### Added
- **Circe compatibility test suite** — 160 property-based tests using circe's own `CodecTests` (munit + discipline), ported from `DerivesSuite` and `SemiautoDerivationSuite`
- Published to Maven Central as `io.github.nguyenyou::circe-sanely-auto`

## [0.1.0] - 2025-12-14

### Added
- Initial release
- **Auto-derivation** — `import sanely.auto.given` for automatic `Encoder.AsObject` and `Decoder` derivation
- **Semiauto derivation** — `SanelyEncoder.derived`, `SanelyDecoder.derived`, `SanelyCodec.derived`
- **Drop-in API** — `io.circe.generic.auto.given` and `io.circe.generic.semiauto.*` aliases
- Product types (nested, generic, large — up to 33 fields)
- Sum types with external tagging (sealed traits, enums, case objects, sub-trait flattening)
- Recursive types (`Option[Self]`, `List[Self]`, `Vector[Self]`, `Set[Self]`, `Seq[Self]`, `Map[K, Self]`, cats containers)
- Generic types (`Box[A]`, `Qux[A, B]`)
- User-provided instances respected via `Expr.summonIgnoring`
- 52 unit tests covering all derivation features
