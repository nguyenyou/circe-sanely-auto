# sanely-jsoniter: Design & Rationale

## The Problem

circe always serializes through an intermediate `Json` tree:

```
Encode:  A  →  Json tree (heap allocations)  →  bytes
Decode:  bytes  →  Json tree (heap allocations)  →  A
```

jsoniter-scala streams directly:

```
Encode:  A  →  bytes   (zero intermediate allocation)
Decode:  bytes  →  A   (zero intermediate allocation)
```

This is 3-5x faster. But jsoniter-scala's `JsonCodecMaker.make` produces **different JSON** than circe — different sum type tagging, different enum encoding, different defaults. You can't just swap one for the other without breaking wire compatibility.

## The Solution

Generate `JsonValueCodec[A]` instances that use jsoniter-scala's streaming engine but produce **circe-compatible JSON**. You get jsoniter's speed with circe's format.

This means you can swap the HTTP hot path:

```scala
// Before: circe pipeline (tree allocated on every request)
{ s => io.circe.parser.decode[T](s) }
{ v => v.asJson.noSpaces }

// After: direct jsoniter pipeline (5x faster, zero intermediate allocation)
{ s => readFromString[T](s) }
{ v => writeToString(v) }
```

Same JSON on the wire, no client changes needed.

## How It Works

Same macro technique as circe-sanely-auto:

1. `inline given` triggers a single macro expansion
2. Macro walks `Mirror` to discover fields/variants
3. `Expr.summonIgnoring` finds existing codecs while excluding the auto-given (prevents infinite loops)
4. For types without an existing codec, derives recursively within the same expansion
5. Generated code delegates to runtime helpers that call jsoniter-scala's `JsonWriter`/`JsonReader` API directly

The runtime helpers (`Codecs`, `JsoniterRuntime`) manually write/read JSON tokens matching circe's conventions — external tagging for sealed traits (`{"Circle": {"radius": 5.0}}`), discriminator fields when configured, `null` for `None`, snake_case transforms, etc.

## The Challenge: Matching circe's Format Exactly

circe and jsoniter-scala disagree on almost every encoding convention:

| Aspect | circe | jsoniter-scala (`JsonCodecMaker.make`) |
|--------|-------|---------------------------------------|
| Sum types | External tag: `{"Circle": {...}}` | Discriminator field |
| Enums | String: `"Red"` | Varies |
| Option None | `null` | Omit field |
| Map keys | Always stringified via `KeyEncoder` | Depends on key type |
| Either | `{"Left": v}` / `{"Right": v}` | Product-style |
| Configured transforms | `withSnakeCaseMemberNames`, `withDefaults`, `withDiscriminator` | Different API |

We had to reimplement all of circe's encoding decisions on top of jsoniter's streaming API, not inherit jsoniter's defaults. Every roadmap item (configured derivation, snake_case, drop-null, Either, non-string map keys, sub-traits) was about closing another gap between what circe produces and what we produce.

## Risks

### 1. Format drift

If we get any encoding detail wrong, `encode with sanely-jsoniter → decode with circe` silently produces wrong results or fails. This is the core risk because it's the whole value proposition — if the JSON isn't identical, you can't safely drop it in.

**Mitigation:** Cross-codec compat tests. We encode with sanely-jsoniter and decode with circe (and vice versa) for products, sum types, options, all primitives, configured derivation. For scalapb, we cross-validate against `scalapb_circe`'s Printer/Parser.

### 2. Coverage gaps

circe supports edge cases we might miss — custom `KeyEncoder`/`KeyDecoder` instances, unusual `Configuration` combinations, deeply nested recursive types. A user hits one of these in production and the swap silently changes wire format.

**Mitigation:** Running circe's own upstream test suite (192 compat tests via `scripts/sync-circe-tests.py`). But this only covers what circe tests, not every real-world usage pattern.

### 3. Maintenance burden

We're a shadow implementation of circe's encoding logic. If circe changes defaults or adds features, we need to track those changes. We depend on both circe-core (for types like `Encoder`, `Decoder`, `Configuration`) and jsoniter-scala-core (for streaming), so upstream changes in either can break us.

