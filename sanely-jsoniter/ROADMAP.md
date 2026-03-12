# sanely-jsoniter Roadmap

Drop-in jsoniter-scala codec derivation that produces circe-compatible JSON on the wire. Skips circe's `Json` AST entirely — streams bytes directly to/from domain objects.

## Open

### Performance

- [ ] **P3.8: Discriminator slow-path optimization** — When the discriminator field is not the first key in the JSON object, the decoder buffers every non-discriminator field as a raw JSON string, reconstructs a full JSON string, then reparses it. This is a full double-parse. The fast path (discriminator first) hides this because our own encoders always emit the discriminator first. Fix: buffer raw byte offsets or accept pre-read key-value pairs to avoid the reparse. Low priority for self-produced JSON; high priority if decoding external sources.

### Gap analysis vs jsoniter-scala `CodecMakerConfig`

Features available in jsoniter-scala that sanely-jsoniter does not yet support. Listed for evaluation — not all are worth implementing.

- [ ] **(P2) DoS protection: collection/number size limits** — jsoniter-scala has configurable `bigDecimalPrecision(34)`, `bigDecimalScaleLimit(6178)`, `bigDecimalDigitsLimit(308)`, `bigIntDigitsLimit(308)`, `mapMaxInsertNumber(1024)`, `setMaxInsertNumber(1024)`, `bitSetValueLimit(1024)`. sanely-jsoniter passes no limits to `readBigDecimal`/`readBigInt` and has no cap on collection sizes during decode. Security-relevant for services accepting untrusted JSON. circe itself has no such limits (delegates to jawn), so this would be a sanely-jsoniter-only enhancement.
- [ ] **(P3) Strict product decoding (reject unknown fields)** — jsoniter-scala's `skipUnexpectedFields = false` errors on unknown JSON keys for products. sanely-jsoniter's `strictDecoding` only applies to sum types. circe doesn't offer this either.
- [ ] **(P4) Per-field `@named` annotation** — jsoniter-scala supports `@named("json_key")` on individual case class fields. sanely-jsoniter only has global `transformMemberNames`. circe doesn't have this (uses deprecated `@JsonKey` from circe-generic-extras). Global transforms + `Configuration` cover most use cases.
- [ ] **(P4) Duplicate key detection** — jsoniter-scala has `checkFieldDuplication = true` by default. sanely-jsoniter uses last-wins semantics (matching circe's behavior). Adding this would diverge from circe semantics.
- [ ] **(P4) `isStringified` mode (numbers as JSON strings)** — jsoniter-scala can read/write numbers and booleans as JSON strings (`"123"` instead of `123`). Not relevant to circe compatibility.
- [ ] **(P4) Built-in name transforms (camelCase, PascalCase, kebab-case)** — jsoniter-scala provides `enforce_snake_case`, `enforceCamelCase`, `EnforcePascalCase`, `enforce-kebab-case`. Users can already pass arbitrary `String => String`, so this is a convenience gap, not a capability gap.

### `derives` wrapper gap

- [ ] **`WithDefaultsAndTypeName` wrapper** — `derives JsoniterCodec.WithDefaultsAndTypeName` for sealed traits using `withDefaults + withDiscriminator("__typename__")`. This is a common pattern in large codebases where ADTs need flat discriminator encoding. The configured derivation already supports this via manual `JsoniterConfiguration`, but there is no convenience wrapper for `derives` syntax.

### Test coverage gaps (real-world patterns)

Patterns observed in large production codebases that are not explicitly tested:

- [ ] **Mixed case objects + case classes in sealed traits** — ADTs containing both `case object Idle` and `case class Failed(message: String)` in the same hierarchy. Common for status/state machine types (e.g., `NotStarted`, `InProgress(jobId)`, `Failed(message)`, `Completed`).
- [ ] **Sealed traits with many variants (7+)** — Real-world ADTs often have 7-10+ variants. Tests should verify hash-dispatch correctness at this scale.
- [ ] **Collection defaults** — Fields with `tags: Seq[String] = Seq.empty`, `items: List[Item] = Nil`, `metadata: Set[String] = Set.empty`, `lookup: Map[String, Int] = Map.empty`. Current default tests only cover primitives and `Option`.
- [ ] **`Map[String, CaseClass]` with configured derivation** — Map values that are themselves derived case classes. Common in schema/config types (e.g., `fields: Map[String, FieldConfig]`).
- [ ] **Deeply nested derivation (3+ levels)** — `Outer` → `Middle` → `Inner` nesting where all levels are macro-derived within a single expansion.
- [ ] **Sealed trait with discriminator + subtypes using different configs** — Parent sealed trait uses `withDiscriminator("type")` while subtypes have their own defaults. Mimics real protocol definitions.
- [ ] **Null on non-Option String field with default (SupportingFileType pattern)** — `case class SupportingFileType(blueprintMetadataMapping: String = "")` with JSON `{"blueprintMetadataMapping":null}` should decode to default `""`, not throw. Configured derivation with `withDefaults` handles this via null interception (`isNextToken('n')` → skip + keep default). Needs explicit test + cross-codec verification with circe.
- [ ] **Subtype field with default + null JSON (FormNamespace pattern)** — `case class FormRule(defaultNamespace: FormNamespace, ...)` where `FormNamespace = Subtype[String]`. Var initialization uses `null.asInstanceOf[T]` which compiles for abstract type members. Needs test with configured derivation + withDefaults where the Subtype field has a Scala default and JSON sends null.
- [ ] **AnyVal in configured derivation with defaults + null JSON (FileId pattern)** — `case class PdfCutInfo(fileId: FileId, reviewingFileId: Option[FileId])` where `FileId extends AnyVal`. Requires hand-rolled `JsonValueCodec[FileId]` (3 lines). Needs test with configured withDefaults proving null on AnyVal field keeps default.
- [ ] **Deeply nested null propagation (FormData/GaiaState pattern)** — Verify that a decode error deep in a nested structure (3+ levels) propagates as an exception, not silently returning nullValue (all-null object). Tests should prove: (a) `null` at field level → field gets nullValue (correct), (b) malformed JSON at nested level → exception propagates to caller, (c) no silent data corruption.

### Not optimizable (circe format constraints)

Inherent costs of producing circe-compatible JSON — cannot be optimized without breaking the compatibility contract:

- **External tagging overhead** — Sum types encode as `{"VariantName": {...}}` (one extra object wrapper per variant). jsoniter-scala native uses flat encoding that avoids this wrapper.
- **Option null-writing** — `None` encodes as `null` (field present with null value). jsoniter-scala native skips the field entirely. (`dropNullValues` config already skips nulls, but the default must match circe.)

## Completed

### Core features

- [x] Products, sum types, enums, recursive types
- [x] Sub-trait hierarchies (nested sealed traits, diamond dedup)
- [x] Either codec (`{"Left": v}` / `{"Right": v}`)
- [x] Non-string map keys via `KeyCodec[K]`
- [x] Enum string codec
- [x] Scala.js support
- [x] Value enum codecs (manual `Codecs.stringValueEnum` / `Codecs.intValueEnum`)

### Configured derivation

- [x] `withDefaults`
- [x] `withDiscriminator(field)`
- [x] `withSnakeCaseMemberNames`
- [x] `withDropNullValues`
- [x] `withTransformMemberNames` / `withTransformConstructorNames`
- [x] `withStrictDecoding`

### Migration & integration

- [x] **Auto-configured derivation** — `sanely.jsoniter.configured.auto.given` with a `given JsoniterConfiguration` in scope
- [x] **Tapir integration tests** — proves HTTP codec swap works end-to-end (direct jsoniter codec, configured types, error handling, circe coexistence)
- [x] **Migration guide** — [MIGRATION.md](MIGRATION.md): configuration mapping, semi-auto, configured auto, centralized wrapper pattern, HTTP codec swap with incremental fallback
- [x] **`derives` support** — `JsoniterCodec`, `JsoniterCodec.WithDefaults`, `WithDefaultsDropNull`, `WithSnakeCaseAndDefaults`, `WithSnakeCaseAndDefaultsDropNull`, `Enum`, `ValueEnum`
- [x] **Value enum macro derivation** — `deriveJsoniterValueEnumCodec` auto-detects String vs Int value field

### Bug fixes (from real-world migration)

- [x] **(P0) Opaque type support** — `opaqueDealias` using `translucentSuperType` to resolve codecs through opaque boundaries. Fixes nullValue, read, write, and default value handling for opaque-wrapped primitives and containers. Both non-configured and configured macro paths.
- [x] **(P0) Tuple encoding as JSON arrays** — tuples encode as `[a, b]` matching circe format, not `{"_1": a, "_2": b}`. Specialized codecs for arity 1-5, generic `Tuple.fromArray`-based codec for arity 6-22.
- [x] **(P1) Null → default** — when `useDefaults=true` and JSON contains `"field": null` for a non-Option field with a Scala default, the default value is used instead of assigning null.
- [x] **(P1) External Map given respected** — `Expr.summonIgnoring` checks for user-provided `JsonValueCodec[Map[K,V]]` before decomposing into key/value resolution.
- [x] **(P3) Public primitive codecs** — `import sanely.jsoniter.Codecs.given` exposes `JsonValueCodec` for all 11 primitive types.

### Performance (P3: closing the gap with native jsoniter-scala)

With P3.1–P3.7 complete, sanely-jsoniter **surpasses jsoniter-scala native** on both read and write on Apple Silicon.

- [x] **P3.1: Inline encode with direct field access** — `x.name` instead of `x.productElement(i)`, eliminating boxing
- [x] **P3.2: Direct primitive write calls** — `out.writeVal(x.age)` instead of `codec.encodeValue(x.age, out)` for primitives
- [x] **P3.3: Inline decode with typed locals** — typed local variables instead of `Array[Any]` boxing
- [x] **P3.4: Hash-based field key dispatch** — `charBufToHashCode` + `@switch` for products with > 8 fields
- [x] **P3.5: Direct constructor call** — `new P(_f0, _f1, ...)` with zero boxing and zero intermediate allocations (+19% read, +2% write)
- [x] **P3.6: Char-buf sum type dispatch** — `readKeyAsCharBuf()` + hash dispatch, eliminates String allocation per sum decode
- [x] **P3.7: Container codec cleanups** — cached delegate codecs, iterator while-loops in all container encoders (+15% read)

### Cross-codec tests

- [x] Products, sums, enums, either, sub-traits, maps, withDefaults, snake_case
- [x] Discriminator tagging
- [x] Drop-null encoding
- [x] Combined configs (defaults+discriminator, defaults+snake_case+drop-null)
- [x] Performance benchmarks vs bridge — **5.7x read / 6.2x write** vs circe-jawn
