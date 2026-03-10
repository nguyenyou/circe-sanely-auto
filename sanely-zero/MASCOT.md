# sanely-zero mascot: Rei (零) — The Zero Swift

## The name

**Rei** (零) — Japanese for "zero." Also evokes "ray" — a beam of light cutting through noise. Short, memorable, and encodes the project's identity in a single syllable.

## Why a swift

The common swift (*Apus apus*) is the perfect embodiment of sanely-zero's philosophy. Every trait of the bird maps to a design principle of the project:

| Swift trait | sanely-zero principle |
|---|---|
| **Fastest bird in level flight** (111 km/h sustained) | Fastest JSON engine — competing with jsoniter-scala head-on |
| **Spends its entire life airborne** — eats, sleeps, mates in flight, never stops | Zero overhead — no intermediate AST, no unnecessary allocation, never pauses |
| **One of the lightest birds for its wingspan** | Zero dependencies — only scala-library, nothing else |
| **"Apus" means "footless" in Greek** — named for its tiny, nearly invisible feet | Minimal footprint — no framework lock-in, no transitive dep tree, no weight |
| **Navigates via Earth's magnetic field** — senses what others cannot see | Compile-time foresight — knows every field, every type, every variant before a single byte is parsed |
| **Cross-continental migrant** (Africa ↔ Europe, 200K km/year) | Cross-platform — JVM and Scala.js from the same source |
| **Screams while flying** — its distinctive call carries far | Expressive macros — `Expr.summonIgnoring`, single-pass derivation, the technique no one else has |
| **Wing shape forms a crescent/sickle** — a natural arc | The zero — the negative space between swept wings forms a circle |
| **Hunts insects mid-flight with precision** — no wasted movement | Speculative sequential access, golden mask validation — every CPU cycle accounted for |
| **Flock of swifts is called a "scream"** | We scream through JSON at 700K+ ops/sec |

The swift is NOT a falcon (brute force speed) or a hummingbird (tiny and delicate). The swift is **relentless endurance at extreme speed** — sustained throughput, not burst performance. This matches our design: not a benchmark trick, but genuine architectural advantage that holds across all payloads.

## Visual concept

### Primary form

A **geometric swift in mid-dive**, wings swept back, viewed from a three-quarter angle. The body is a single fluid stroke. The swept wings curve to form the negative space of a **circle (zero)** between them.

```
        ╱‾‾‾╲
   ━━━━╱     ╲━━━━        ← swept wings form the zero
      │   ○   │            ← the void between wings = zero
   ━━━━╲     ╱━━━━           (also: the eye of compile-time foresight)
        ╲___╱
          │
          ▼                ← tail: a single sharp vector pointing down (speed)
```

The circle between the wings serves triple duty:
1. **The zero** — zero dependencies, the project name
2. **The eye** — compile-time omniscience, seeing the schema before runtime
3. **The ensō** — the Zen circle, representing the "sanely" philosophy: clarity through simplicity

### The eye within the zero

Inside the circle, a single **dot or pupil** — representing the compile-time macro's view into the type. The macro sees everything: field names, types, default values, sealed trait hierarchies. This is the "foresight" that gives us the structural advantage. The eye is calm and focused — sane, not frantic.

### The dive trail

Behind the swift, a **trail of dissolving geometric fragments** — squares and curly braces (`{`, `}`) breaking apart into raw bytes, then into nothing. This represents:
- JSON being parsed — structured text decomposed into typed values
- Zero intermediate representation — bytes go directly to domain types
- The speed trail — motion, throughput, the wake of a fast-moving parser

The fragments are sparse and minimal. Not a dense particle effect — just 3-5 shapes fading from solid to transparent. The "sanely" aesthetic: restrained, not noisy.

## Color palette

| Role | Color | Hex | Reasoning |
|---|---|---|---|
| **Primary — body** | Midnight indigo | `#1B1F3B` | Depth, sophistication. Darker than Scala red, stands apart |
| **Secondary — wings** | Electric teal | `#00D4FF` | Speed, technology. Catches the eye. Nods to Scala's blue heritage |
| **Accent — eye/zero** | Warm amber | `#FF9F1C` | The spark of compile-time magic. Warmth against the cool palette. The "insight" glow |
| **Trail fragments** | Fading teal → transparent | `#00D4FF` → `#00D4FF00` | Motion, dissolution of JSON structure |
| **Background** | Transparent or white | — | Clean, works on any surface |

### Dark mode variant

Swap midnight indigo body for **silver-white** (`#E8E8EC`). Eye stays amber. Wings stay teal. Works on dark backgrounds.

## SVG design constraints

The mascot must work at multiple sizes:

| Context | Size | Detail level |
|---|---|---|
| **Hero image** (README, docs) | 400-800px | Full detail: body, wings, eye, trail fragments, subtle gradients |
| **Badge** (shields.io, npm) | 20-32px | Swift silhouette only — no trail, no eye detail, recognizable shape |
| **Favicon** | 16-32px | Circle (zero) with amber dot (eye) only — the iconic element |
| **Social preview** (GitHub, Twitter) | 1200x630 | Full mascot + "sanely-zero" text + tagline |

### Style rules

- **Geometric, not organic** — clean vector lines, no hand-drawn roughness. Flat or minimal gradient fills. The aesthetic is precision engineering, not illustration.
- **No outline strokes** — shapes defined by filled regions and negative space. Scales cleanly.
- **Bilateral symmetry** on the front view (for badge/favicon). Three-quarter angle for hero.
- **No more than 4 colors** in any single render (including transparent). Simplicity.
- **The zero (circle) must be recognizable at every size** — it is the primary brand element.

## Typography (for text pairings)

When the mascot appears alongside the project name:

```
sanely-zero
```

- Font: **monospace** (the codebase, the terminal, the developer's world)
- Weight: regular, not bold. "Sanely" means calm, not shouty.
- The hyphen matters — it separates the philosophy ("sanely") from the architecture ("zero")
- Color: match the midnight indigo body color

Tagline (optional, for social/README hero):

```
zero dependencies. compile-time foresight. fastest JSON.
```

All lowercase. Three short phrases. The period after each is deliberate — statements of fact, not excitement.

## Personality

Rei is **calm, precise, and relentless**. Not aggressive, not cute.

- Calm — the "sanely" part. Rational approach to an insane problem (JSON performance)
- Precise — every byte accounted for. Speculative sequential access. Golden mask validation. No wasted work.
- Relentless — sustained throughput. The swift never lands. 700K ops/sec, all day.
- Confident — we know we can compete. The codec layer already beats jsoniter-scala. We just need our own parser.
- Minimal — zero dependencies means zero excess. The mascot reflects this: no decorative flourishes, no unnecessary detail.

Rei does not:
- Look angry or threatening (we're not fighting anyone, we're building something better)
- Look cute or cartoonish (this is serious engineering)
- Have human features (no anthropomorphization)
- Have text integrated into the body (the mascot stands alone)

## Conceptual sketches

### Sketch 1: The dive (hero image)

```
                    ·  ·
                  ·      {
                ·    ·     }
     ╱━━━━━━━━━━━━╲
    ╱   ╱‾‾‾‾‾╲    ╲
   ╱   ╱       ╲    ╲
  ━   │    ◉    │    ━      ← Rei diving, eye glowing amber
   ╲   ╲       ╱    ╱         wings swept, forming the zero
    ╲   ╲_____╱    ╱          trail of dissolving JSON fragments
     ╲━━━━━━━━━━━━╱
          │╲
          │ ╲
          ▼  ·                ← tail fork + fading trail
```

### Sketch 2: The icon (badge/favicon)

```
    ╱‾‾‾╲
   ╱     ╲
  │   ◉   │      ← circle (zero) + amber eye
   ╲     ╱         nothing else needed at small sizes
    ╲___╱
```

### Sketch 3: Social preview (wide format)

```
┌──────────────────────────────────────────────┐
│                                              │
│     [Rei mascot]     sanely-zero             │
│     (hero size)                              │
│                      zero dependencies.      │
│                      compile-time foresight.  │
│                      fastest JSON.            │
│                                              │
└──────────────────────────────────────────────┘
```

## The story

In a world of JSON libraries carrying heavy dependency trees and runtime reflection, a swift named Rei took flight with nothing — zero dependencies, zero overhead, zero compromise. Rei sees the schema before runtime begins, knows every field name as pre-computed bytes, predicts the field order before the first key arrives. While other parsers build and traverse intermediate trees, Rei transforms bytes into types in a single, unbroken dive. No landing. No pausing. No wasted movement.

The circle between Rei's wings is the zero — the void where dependencies used to live, now filled with compile-time knowledge. The amber eye is the macro's foresight: `Expr.summonIgnoring` resolving the entire type hierarchy in one expansion. The dissolved JSON fragments in the wake are the bytes that were, for one brief moment, structured text — before Rei turned them directly into your domain types.

Rei doesn't fight jsoniter-scala. Rei simply owns both the wings and the wind.
