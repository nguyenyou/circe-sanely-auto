# sanely-jsoniter Roadmap

Prioritized for enabling migration from circe-based codebases — replacing the circe intermediary in HTTP layers (e.g. `readFromString[io.circe.Json]` + `json.as[T]` → direct `readFromString[T]`).

## Current state

Core derivation engine is complete: products, sum types, enums, recursive types, sub-trait hierarchies, Either, non-string map keys, value enums (manual). Configured derivation supports withDefaults, discriminator, snake_case, drop-null, and arbitrary name transforms.

**What works today**: semi-auto derivation (`deriveJsoniterCodec`, `deriveJsoniterConfiguredCodec`, `deriveJsoniterEnumCodec`) with all configuration options. Auto derivation for standard (non-configured) types.

**What's missing**: Migration guide for configured codebases.

## Completed

- [x] Products, sum types, enums, recursive types
- [x] Sub-trait hierarchies (nested sealed traits, diamond dedup)
- [x] Either codec (`{"Left": v}` / `{"Right": v}`)
- [x] Non-string map keys via `KeyCodec[K]`
- [x] Configured derivation: `withDefaults`
- [x] Configured derivation: `withDiscriminator(field)`
- [x] Configured derivation: `withSnakeCaseMemberNames`
- [x] Configured derivation: `withDropNullValues`
- [x] Configured derivation: `withTransformMemberNames` / `withTransformConstructorNames`
- [x] Value enum codecs (manual `Codecs.stringValueEnum` / `Codecs.intValueEnum`)
- [x] Enum string codec
- [x] Scala.js support
- [x] Cross-codec tests: products, sums, enums, either, sub-traits, maps, withDefaults, snake_case

## P0 — Blocks real-world HTTP hot path migration

Real codebases use configured derivation for the vast majority of types (defaults, discriminator, snake_case, drop-null). Auto derivation produces **wrong JSON** for these types. These items close the gap between what works today and what the HTTP hot path swap actually requires.

- [x] **Auto-configured derivation** — `sanely.jsoniter.configured.auto.given` that auto-derives `JsonValueCodec[T]` using a `given JsoniterConfiguration` in scope. Without this, every configured type needs explicit `deriveJsoniterConfiguredCodec`. Pattern: `given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults; import sanely.jsoniter.configured.auto.given`. This covers ~500+ call sites in a typical codebase that all use the same base config (withDefaults).

- [x] **Cross-codec test: discriminator** — Prove that `JsoniterConfiguration.default.withDiscriminator("type")` produces identical JSON to circe's `Configuration.default.withDiscriminator("type")`. Encode with jsoniter → decode with circe and vice versa. ~45 call sites in a typical codebase use discriminator tagging.

- [x] **Cross-codec test: drop-null** — Prove that `withDropNullValues` encoding matches circe's `.mapJsonObject(_.filter(!_._2.isNull))`. Encode with jsoniter → decode with circe. ~5+ call sites.

- [x] **Cross-codec test: combined configs** — Test the real-world combos: defaults+discriminator, defaults+snake_case+drop-null. These are the actual configuration patterns used in production wrappers.

- [x] **Fix REAL-WORLD.md migration path** — Updated Step 2 to show three paths: (a) auto for standard types, (b) semi-auto with matching config for configured types, (c) extend centralized wrapper for codebases with one. Honest about what the hot path swap requires.

## P1 — Enables smoother adoption

- [x] **Tapir integration tests** — Add a test module with Tapir as a dependency that proves the HTTP codec swap works end-to-end. Test cases should cover:
  - **Direct jsoniter codec**: `sttp.tapir.Codec.json[T]` using `readFromString[T]` / `writeToString[T]` with `JsonValueCodec[T]` — no circe `Json` tree in the pipeline
  - **Standard types**: product types, sum types (external tagging), enums — verify Tapir roundtrip (encode → decode) produces identical results to the circe-based codec
  - **Configured types**: types with defaults (missing fields decoded correctly), discriminator tagging, snake_case field names, drop-null encoding — verify wire format matches circe's configured codec output
  - **Error handling**: malformed JSON, type mismatches, missing required fields — verify Tapir `DecodeResult.Error` is produced (not an unhandled exception)
  - **Fallback pattern**: a codec that uses `JsonValueCodec[T]` when available, falls back to circe `Json` tree otherwise — for incremental migration where not all types have jsoniter codecs yet
  - **Circe coexistence**: same type has both `Encoder[T]`/`Decoder[T]` and `JsonValueCodec[T]` in scope — verify no implicit conflicts and both can be used independently

  This is the strongest proof that the REAL-WORLD.md migration path actually works. Without it, the HTTP hot path swap is a theoretical claim.

- [x] **Strict decoding** — `JsoniterConfiguration.withStrictDecoding` config option exists but is not wired into the runtime. Implement: reject unknown fields during decoding. ~18 call sites in typical codebases use strict decoding via direct `ConfiguredCodec.derived`.

- [x] **Migration guide for configured codebases** — [MIGRATION.md](MIGRATION.md): configuration mapping, semi-auto per-type, configured auto, centralized wrapper pattern (extend once, add one line per call site), HTTP codec swap with incremental fallback.

## P2 — Polish

- [x] **Value enum macro derivation** — `deriveJsoniterValueEnumCodec` auto-detects String vs Int value field from the enum's constructor parameter. Replaces manual `Codecs.stringValueEnum(values, _.value)` / `Codecs.intValueEnum(values, _.value)`.

- [x] **`derives` support** — `JsoniterCodec`, `JsoniterCodec.WithDefaults`, `WithDefaultsDropNull`, `WithSnakeCaseAndDefaults`, `WithSnakeCaseAndDefaultsDropNull`, `Enum`, `ValueEnum`. Each extends `JsonValueCodec[A]` directly so no import conversions needed.

- [ ] **Performance benchmarks vs bridge** — Formal JMH benchmarks comparing sanely-jsoniter direct codecs vs the `jsoniter-scala-circe` bridge approach on realistic payloads (nested products, sum types, optional fields). Current 5x claim is from synthetic benchmarks — validate on real-world-shaped data.
