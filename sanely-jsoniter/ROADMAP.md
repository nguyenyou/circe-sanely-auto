# sanely-jsoniter Roadmap

Prioritized for enabling migration from circe-based codebases — replacing the circe intermediary in HTTP layers (e.g. `readFromString[io.circe.Json]` + `json.as[T]` → direct `readFromString[T]`).

## P0 — Blocks migration

These cover the most common circe derivation patterns in real-world codebases.

- [x] **Configured derivation: `withDefaults`** — Decode missing fields using companion default values. The most common configured derivation pattern (`Configuration.default.withDefaults`).
- [x] **Configured derivation: discriminator** — Sum type tagging via `withDiscriminator(field)`. Required for sealed trait hierarchies that use a type discriminator field.
- [x] **Configured derivation: snake_case member names** — `withSnakeCaseMemberNames` for external API integration where JSON uses `snake_case` but Scala uses `camelCase`.
- [x] **Drop-null encoder** — Omit `null`-valued fields from JSON output. In circe this requires post-processing (`.mapJsonObject(_.filter(!_._2.isNull))`) — jsoniter can do this natively by skipping null field writes.

## P1 — Enables full hot-path optimization

- [x] **Sub-trait support**: Sealed trait variants that are nested sealed traits (currently must be case classes or case objects)
- [x] **Either codec**: Support `Either[L, R]` with `{"Left": value}` / `{"Right": value}` format (matching circe's `disjunctionCodecs`)
- [x] **Non-string map keys**: Support `Map[K, V]` where K is not String — keys stringified via `KeyCodec[K]` (matching circe's `KeyEncoder`/`KeyDecoder` pattern)

## P2 — Enables complete replacement

- [x] **Value enum codecs**: `Codecs.stringValueEnum` and `Codecs.intValueEnum` — encode by associated value (not case name). E.g. `Status.Active` → `"active"`, `Priority.High` → `3`.

## Done

- [x] **Enum string codec**: Encode enum cases as strings (`"Red"`) instead of empty-object variants (`{"Red":{}}`)
- [x] **Scala.js support**: Cross-compile for Scala.js
