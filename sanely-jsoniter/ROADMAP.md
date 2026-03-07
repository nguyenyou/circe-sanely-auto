# sanely-jsoniter Roadmap

Prioritized for enabling migration from circe-based codebases — replacing the circe intermediary in HTTP layers (e.g. `readFromString[io.circe.Json]` + `json.as[T]` → direct `readFromString[T]`).

## Current state

Core derivation engine is complete: products, sum types, enums, recursive types, sub-trait hierarchies, Either, non-string map keys, value enums (manual). Configured derivation supports withDefaults, discriminator, snake_case, drop-null, and arbitrary name transforms.

**What works today**: semi-auto derivation (`deriveJsoniterCodec`, `deriveJsoniterConfiguredCodec`, `deriveJsoniterEnumCodec`) with all configuration options. Auto derivation for standard (non-configured) types.

**What's missing**: auto-configured derivation, cross-codec tests for some config combos, Tapir integration guidance, strict decoding runtime, and REAL-WORLD.md accuracy for configured types.

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

- [ ] **Auto-configured derivation** — `sanely.jsoniter.configured.auto.given` that auto-derives `JsonValueCodec[T]` using a `given JsoniterConfiguration` in scope. Without this, every configured type needs explicit `deriveJsoniterConfiguredCodec`. Pattern: `given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults; import sanely.jsoniter.configured.auto.given`. This covers ~500+ call sites in a typical codebase that all use the same base config (withDefaults).

- [ ] **Cross-codec test: discriminator** — Prove that `JsoniterConfiguration.default.withDiscriminator("type")` produces identical JSON to circe's `Configuration.default.withDiscriminator("type")`. Encode with jsoniter → decode with circe and vice versa. ~45 call sites in a typical codebase use discriminator tagging.

- [ ] **Cross-codec test: drop-null** — Prove that `withDropNullValues` encoding matches circe's `.mapJsonObject(_.filter(!_._2.isNull))`. Encode with jsoniter → decode with circe. ~5+ call sites.

- [ ] **Cross-codec test: combined configs** — Test the real-world combos: defaults+discriminator, defaults+snake_case+drop-null. These are the actual configuration patterns used in production wrappers.

- [ ] **Fix REAL-WORLD.md migration path** — The current Step 2 says "one import, one file change" with `auto.given`, which only works for standard types. Update to show two paths: (a) auto for standard types, (b) configured semi-auto or auto-configured for types with custom encoding. Be honest about what "one file change" actually requires.

## P1 — Enables smoother adoption

- [ ] **Strict decoding** — `JsoniterConfiguration.withStrictDecoding` config option exists but is not wired into the runtime. Implement: reject unknown fields during decoding. ~18 call sites in typical codebases use strict decoding via direct `ConfiguredCodec.derived`.

- [ ] **Tapir integration example** — Working example of a Tapir `JsonCodec[T]` that uses `JsonValueCodec[T]` directly (no circe `Json` tree). Show the central endpoint codec pattern:
  ```scala
  // Before: jsoniter parses to circe.Json, then circe decodes (1.5x)
  val json = readFromString[io.circe.Json](s); json.as[T]
  // After: jsoniter decodes directly (5x)
  readFromString[T](s)
  ```
  Include a fallback pattern for incremental migration (use jsoniter when `JsonValueCodec[T]` is available, fall back to circe otherwise).

- [ ] **Migration guide for configured codebases** — Document the pattern for codebases with centralized configuration wrappers: extend the wrapper to derive both circe and jsoniter codecs side by side. Show how `deriveCodecWithDefaults` gets a parallel `deriveJsoniterCodecWithDefaults`. One-time wrapper change, not per-type.

## P2 — Polish

- [ ] **Value enum macro derivation** — `deriveJsoniterValueEnumCodec` that generates codecs from a `ValueEnumCompanion` or similar pattern, instead of requiring manual `Codecs.stringValueEnum(values, _.value)`.

- [ ] **`derives` support** — Companion objects like `JsoniterCodec.WithDefaults` that enable `case class Foo(...) derives JsoniterCodec.WithDefaults` syntax. Mirrors the `derives CirceCodec.WithDefaults` pattern used in some codebases (~336 call sites). This is a convenience layer — the underlying `deriveJsoniterConfiguredCodec` already works.

- [ ] **Performance benchmarks vs bridge** — Formal JMH benchmarks comparing sanely-jsoniter direct codecs vs the `jsoniter-scala-circe` bridge approach on realistic payloads (nested products, sum types, optional fields). Current 5x claim is from synthetic benchmarks — validate on real-world-shaped data.
