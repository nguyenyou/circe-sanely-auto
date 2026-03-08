# Why sanely-jsoniter exists

## The problem

Large Scala codebases are deeply locked into circe. A real-world production codebase might have:

- **1,600+ files** importing `io.circe`
- **7,000+ derivation call sites**
- **Hundreds of domain models** with `json: io.circe.Json` as public API field types
- **800+ files** using cursor navigation (`.hcursor.downField(...)`)
- **Deep framework coupling** — Tapir, http4s, and every HTTP layer wired to `Encoder[T]`/`Decoder[T]`

Rewriting this to jsoniter-scala is not realistic. It would touch every model, every endpoint, every test. It's a multi-month project with high risk and zero feature value.

But circe is slow. On the HTTP hot path — where bytes become domain objects and domain objects become bytes — circe allocates an intermediate `Json` tree for every request and every response:

```
bytes → Json tree (allocated) → Decoder[T] → T → Encoder[T] → Json tree (allocated) → bytes
```

Two full tree allocations per round trip, every time, for every request.

## The insight

The HTTP hot path is narrow. It's `bytes → T` on the way in and `T → bytes` on the way out. You don't need a `Json` tree for that. jsoniter-scala can stream directly:

```
bytes → JsonValueCodec[T] → T → JsonValueCodec[T] → bytes
```

No tree allocation. No intermediate representation. Just bytes and domain objects.

And critically: **circe codecs and jsoniter codecs are different types**. `Encoder[T]`/`Decoder[T]` and `JsonValueCodec[T]` can coexist in the same codebase with zero conflicts. You don't have to choose one or the other. You add jsoniter codecs where they matter (HTTP endpoints) and leave everything else untouched.

## The constraint

The JSON on the wire must be **identical**. If you encode with sanely-jsoniter, circe must decode it. If circe encodes, sanely-jsoniter must decode it. Same field names, same casing, same default handling, same discriminator placement, same null behavior — every edge case, every configuration option.

This is non-negotiable. If sanely-jsoniter produces different JSON than circe, two things can happen:

1. **Loud failures** — the JSON is incompatible enough that decoding fails outright. You get an error, an HTTP 500, and you know immediately. Annoying but safe.

2. **Silent data corruption** — the JSON is *valid but subtly wrong*. A default value kicks in differently when a field is missing. `None` is encoded as `null` instead of field-absent, and circe reads it back as `Some(null)`. A discriminator is placed slightly wrong in a nested sum type. Circe decodes it successfully, returns a different domain object, and nobody notices. A user's age becomes 0 instead of their actual age. An enum resolves to the wrong variant. A nullable field silently loses its value.

The second failure mode is why the compatibility contract is absolute — not just the happy paths, but the error paths, the edge cases, the null handling, all of it.

## What sanely-jsoniter does

It derives `JsonValueCodec[T]` instances at compile time using the same macro technique as circe-sanely-auto — single-pass expansion with `Expr.summonIgnoring`, no implicit search chains. The generated codecs:

1. **Read and write JSON in circe's exact format** — external tagging for sum types, discriminator fields, snake_case transforms, default values, null handling, strict decoding — all configurable, all matching circe's behavior.

2. **Stream directly** — no intermediate `Json` tree. Fields are read from and written to the byte stream one at a time.

3. **Use direct constructor calls** — `new User(name, age)` instead of `mirror.fromProduct(ArrayProduct(Array(...)))`. No boxing, no reflection, no array allocation.

4. **Use typed primitive writes** — `out.writeVal(42)` instead of going through a generic codec. No boxing an `Int` into `Json.fromInt` into `JsonNumber` into bytes.

## The results

On a ~1.4 KB JSON payload with nested products, sealed traits, and optional fields:

| Approach | Read throughput | Write throughput | Alloc per read | Alloc per write |
|---|---|---|---|---|
| circe-jawn (baseline) | 139K ops/sec | 125K ops/sec | 28 KB | 27 KB |
| **sanely-jsoniter** | **661K ops/sec** | **782K ops/sec** | **4 KB** | **1 KB** |
| jsoniter-scala native | 680K ops/sec | 728K ops/sec | 3 KB | 1 KB |

**4.8x faster reads, 6.2x faster writes, 85-95% less allocation** — while producing byte-for-byte identical JSON to circe.

sanely-jsoniter reaches 98% of jsoniter-scala native on reads and exceeds it on writes, because it uses the same streaming engine but generates optimized code paths specific to each type.

## The migration path

You don't rewrite anything. You add jsoniter codecs alongside existing circe codecs, then wire them into your HTTP layer:

```scala
// Existing circe codec — untouched
case class User(name: String, age: Int) derives SanelyCodec

// Add jsoniter codec — one line
given JsonValueCodec[User] = deriveJsoniterCodec

// Or with configured derivation
given JsoniterConfiguration = JsoniterConfiguration.default.withDefaults
given JsonValueCodec[User] = deriveJsoniterConfiguredCodec
```

The rest of your codebase — cursor navigation, `Json` field types, test assertions, everything — stays on circe. Only the HTTP serialization boundary changes.

## Should you use this in production?

**Be cautious.** sanely-jsoniter is experimental. The entire value proposition depends on producing byte-for-byte identical JSON to circe — and if it doesn't, the failure mode is silent data corruption that is very hard to detect. A field decoded to the wrong default, a null handled differently, a discriminator placed wrong — these don't crash your app. They corrupt your data quietly.

We test extensively against circe's own test suite and run cross-codec compatibility tests, but JSON serialization has a large surface area. Edge cases in your specific domain models — deeply nested sum types, unusual `Configuration` combinations, custom key types — may not be covered.

**Our recommendation:**

- **Don't use this in critical production systems yet.** If incorrect JSON decoding could cause financial loss, data corruption, or security issues, wait until the library matures.
- **Do use this if you can tolerate the risk** — internal tools, non-critical services, staging environments, or systems where you have strong integration tests that would catch format mismatches.
- **If you adopt it**, write integration tests that encode with sanely-jsoniter and decode with circe (and vice versa) for your actual domain models. Don't rely on our tests alone. Your types are the ones that matter.

The performance gain is real and substantial. But performance is worthless if the data is wrong.

## In summary

sanely-jsoniter exists because:

1. **Rewriting to jsoniter-scala is impractical** in large circe-locked codebases
2. **The HTTP hot path is narrow** and doesn't need circe's `Json` tree
3. **Circe and jsoniter codecs coexist** — you can adopt incrementally
4. **Wire compatibility is guaranteed** — same JSON, different engine
5. **The performance gain is substantial** — 5-6x throughput, 85-95% less allocation

But it is experimental. The compatibility contract is absolute in intent — we just can't guarantee we've caught every edge case yet.
