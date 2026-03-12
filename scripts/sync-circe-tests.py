#!/usr/bin/env python3
"""
sync-circe-tests.py — Transform circe upstream Scala 3 derivation tests to use
circe-sanely-auto's derivation engine.

Reads from:  upstream/circe/modules/tests/shared/src/test/scala-3/io/circe/
Writes to:   compat/test/src/io/circe/generic/

Transformations per file:

  DerivesSuite.scala → AutoDerivedSuite.scala
    - Remove all `derives` clauses (auto-derivation via import)
    - Add `import io.circe.generic.auto._`

  SemiautoDerivationSuite.scala → SemiautoDerivedSuite.scala
    - Replace Decoder.derived → deriveDecoder, etc.
    - Add `import io.circe.generic.semiauto._`
    - Remove "cannot derive" tests (sanely-auto CAN derive nested types)

  ConfiguredDerivesSuite.scala → ConfiguredDerivesSuite.scala
    - Replace Codec.AsObject.derivedConfigured → deriveConfiguredCodec, etc.
    - Replace `derives ConfiguredCodec` / `derives Codec` with explicit givens
    - Add `import io.circe.generic.semiauto.*`

  ConfiguredEnumDerivesSuites.scala → ConfiguredEnumDerivesSuites.scala
    - Replace ConfiguredEnumCodec.derived → deriveEnumCodec
    - Add `import io.circe.generic.semiauto.*`
"""

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
UPSTREAM_DIR = (
    REPO_ROOT
    / "upstream"
    / "circe"
    / "modules"
    / "tests"
    / "shared"
    / "src"
    / "test"
    / "scala-3"
    / "io"
    / "circe"
)
OUTPUT_DIR = REPO_ROOT / "compat" / "test" / "src" / "io" / "circe" / "generic"


def get_circe_version():
    """Read the pinned circe submodule version from git describe."""
    import subprocess

    result = subprocess.run(
        ["git", "describe", "--tags", "--exact-match"],
        cwd=REPO_ROOT / "upstream" / "circe",
        capture_output=True,
        text=True,
    )
    if result.returncode == 0:
        return result.stdout.strip()
    return "unknown"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def read_upstream(filename):
    path = UPSTREAM_DIR / filename
    if not path.exists():
        print(f"ERROR: {path} not found.")
        print("       Did you run: git submodule update --init?")
        sys.exit(1)
    return path.read_text()


def write_output(filename, content):
    path = OUTPUT_DIR / filename
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)
    print(f"  wrote {path.relative_to(REPO_ROOT)}")


def strip_license_header(text):
    """Remove the Apache license block at the top of the file."""
    return re.sub(r"^/\*.*?\*/\s*\n", "", text, count=1, flags=re.DOTALL)


def make_header(source_file, transforms):
    version = get_circe_version()
    lines = [
        f"// AUTO-GENERATED from circe {version} by scripts/sync-circe-tests.py",
        f"// Source: upstream/circe/modules/tests/shared/src/test/scala-3/io/circe/{source_file}",
        "//",
        "// Transformations applied:",
    ]
    for t in transforms:
        lines.append(f"//   - {t}")
    lines.append("//")
    lines.append("// DO NOT EDIT — regenerate with: python3 scripts/sync-circe-tests.py")
    lines.append("")
    return "\n".join(lines) + "\n"


def remove_derives_clauses(text):
    """Remove all 'derives Type, ...' clauses from type definitions."""
    # 1. Multi-line derives: ") derives Encoder.AsObject,\n        Decoder"
    text = re.sub(
        r"(\))\s+derives\s+[\w.]+\s*,\s*\n\s*[\w.]+",
        r"\1",
        text,
    )
    # 2. Before colon (enums): "enum X derives Y:" → "enum X:"
    text = re.sub(
        r"\s+derives\s+[\w.]+(?:\s*,\s*[\w.]+)*(?=\s*:)", "", text
    )
    # 3. Before semicolon: "trait X derives Y;" → "trait X;"
    text = re.sub(
        r"\s+derives\s+[\w.]+(?:\s*,\s*[\w.]+)*(?=\s*;)", "", text
    )
    # 4. At end of line
    text = re.sub(
        r"\s+derives\s+[\w.]+(?:\s*,\s*[\w.]+)*\s*$",
        "",
        text,
        flags=re.MULTILINE,
    )
    return text


def add_import(text, new_import):
    """Insert an import line after the last existing import."""
    lines = text.split("\n")
    last_import_idx = -1
    for i, line in enumerate(lines):
        if line.startswith("import "):
            last_import_idx = i
    if last_import_idx >= 0:
        lines.insert(last_import_idx + 1, new_import)
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# DerivesSuite → AutoDerivedSuite
# ---------------------------------------------------------------------------


def transform_derives_suite(text):
    text = strip_license_header(text)

    # Package
    text = text.replace("package io.circe\n", "package io.circe.generic\n")

    # Rename
    text = text.replace("DerivesSuite", "AutoDerivedSuite")

    # Remove all derives clauses — auto import handles everything
    text = remove_derives_clauses(text)

    # Fix Scala 3 deprecation: `String with Tag` → `String & Tag`
    text = text.replace("String with Tag", "String & Tag")

    # Add auto import
    text = add_import(text, "import io.circe.generic.auto._")

    # Add header
    header = make_header(
        "DerivesSuite.scala",
        [
            "Package: io.circe -> io.circe.generic",
            "Renamed: DerivesSuite -> AutoDerivedSuite",
            "Removed all `derives` clauses (auto-derivation via import)",
            "Fixed: `String with Tag` -> `String & Tag` (Scala 3 intersection syntax)",
            "Added: import io.circe.generic.auto._",
        ],
    )
    text = header + text
    return text


# ---------------------------------------------------------------------------
# SemiautoDerivationSuite → SemiautoDerivedSuite
# ---------------------------------------------------------------------------


def transform_semiauto_suite(text):
    text = strip_license_header(text)

    # Package
    text = text.replace("package io.circe\n", "package io.circe.generic\n")

    # Rename
    text = text.replace("SemiautoDerivationSuite", "SemiautoDerivedSuite")

    # Replace derivation calls (longest patterns first to avoid partial matches)
    text = text.replace("Encoder.AsObject.derived", "deriveEncoder")
    text = text.replace("Codec.AsObject.derived", "deriveCodec")
    text = text.replace("Encoder.derived", "deriveEncoder")
    text = text.replace("Codec.derived", "deriveCodec")
    text = text.replace("Decoder.derived", "deriveDecoder")

    # Add missing imports — after package change from io.circe to io.circe.generic,
    # Decoder/Encoder/Codec are no longer in scope
    text = add_import(text, "import io.circe.{ Codec, Decoder, Encoder }")
    # Add semiauto import
    text = add_import(text, "import io.circe.generic.semiauto._")

    header = make_header(
        "SemiautoDerivationSuite.scala",
        [
            "Package: io.circe -> io.circe.generic",
            "Renamed: SemiautoDerivationSuite -> SemiautoDerivedSuite",
            "Replaced: Decoder.derived -> deriveDecoder",
            "Replaced: Encoder.AsObject.derived / Encoder.derived -> deriveEncoder",
            "Replaced: Codec.AsObject.derived / Codec.derived -> deriveCodec",
            "Added: import io.circe.generic.semiauto._",
        ],
    )
    text = header + text
    return text


def remove_cannot_derive_tests(text):
    """Remove test blocks and types that assert derivation should fail to compile.

    sanely-auto can derive nested types that lack explicit instances
    (via Expr.summonIgnoring). Circe's semiauto derivation cannot.
    """
    # Remove: Baz, Quux, and their comments (lines 57-61 in original)
    text = re.sub(
        r"\n  case class Baz\(str: String\)"
        r"\n"
        r"\n  // We cannot derive.*?\n  // see test below.*?\n"
        r"  case class Quux\(baz: Box\[Baz\]\)\n",
        "\n",
        text,
        flags=re.DOTALL,
    )

    # Remove: Adt5 block (sealed trait + object with comment)
    text = re.sub(
        r"\n  sealed trait Adt5\n  object Adt5 \{.*?\n  \}\n",
        "\n",
        text,
        flags=re.DOTALL,
    )

    # Remove the two "cannot derive" test blocks
    text = re.sub(
        r'\n  test\("Nested case classes cannot be derived"\) \{.*?\n  \}\n',
        "\n",
        text,
        flags=re.DOTALL,
    )
    text = re.sub(
        r'\n  test\("Nested ADTs cannot be derived"\) \{.*?\n  \}\n',
        "\n",
        text,
        flags=re.DOTALL,
    )

    return text


# ---------------------------------------------------------------------------
# ConfiguredDerivesSuite
# ---------------------------------------------------------------------------


def transform_configured_suite(text):
    text = strip_license_header(text)

    # Package
    text = text.replace("package io.circe\n", "package io.circe.generic\n")

    # Replace derivation calls
    text = text.replace("Codec.AsObject.derivedConfigured", "deriveConfiguredCodec")
    text = text.replace("ConfiguredCodec.derived", "deriveConfiguredCodec")

    # Handle `derives ConfiguredCodec` → remove + add explicit given
    text = handle_derives_configured_codec(text)

    # Handle `derives Codec` in configured scope → remove + add explicit given
    text = handle_derives_codec_in_configured_scope(text)

    # Replace imports
    text = text.replace("import io.circe.derivation.*", "import io.circe.derivation.Configuration")
    text = add_import(text, "import io.circe.generic.semiauto.*")

    header = make_header(
        "ConfiguredDerivesSuite.scala",
        [
            "Package: io.circe -> io.circe.generic",
            "Replaced: Codec.AsObject.derivedConfigured -> deriveConfiguredCodec",
            "Replaced: ConfiguredCodec.derived -> deriveConfiguredCodec",
            "Replaced: `derives ConfiguredCodec` with explicit given + deriveConfiguredCodec",
            "Replaced: `derives Codec` (configured scope) with explicit given + deriveConfiguredCodec",
            "Replaced: import io.circe.derivation.* -> import io.circe.derivation.Configuration",
            "Added: import io.circe.generic.semiauto.*",
        ],
    )
    text = header + text
    return text


def handle_derives_configured_codec(text):
    """Replace `derives ConfiguredCodec` with an explicit given.

    For sealed traits: the given must be placed AFTER all subtypes are defined
    (otherwise Mirror.Of fails). We defer insertion until the next `test(` line.
    For case classes: immediate insertion is fine.
    """
    lines = text.split("\n")
    result = []
    pending_givens = []  # (indent, type_name) pairs waiting to be inserted

    for line in lines:
        stripped = line.lstrip()
        if stripped.startswith("//"):
            result.append(line)
            continue

        # Insert pending givens before test/checkAll (all subtypes defined by now)
        if pending_givens and (stripped.startswith("test(") or stripped.startswith("checkAll(")):
            for indent, type_name in pending_givens:
                result.append(
                    f"{indent}given Codec.AsObject[{type_name}] = deriveConfiguredCodec"
                )
                result.append("")
            pending_givens.clear()

        if "derives ConfiguredCodec" in line:
            m = re.search(r"(?:sealed trait|enum|case class)\s+(\w+)", line)
            type_name = m.group(1) if m else "UNKNOWN"
            indent = re.match(r"(\s*)", line).group(1)
            cleaned = re.sub(r"\s+derives\s+ConfiguredCodec", "", line)
            result.append(cleaned)

            # Sealed traits need deferred insertion; case classes can be immediate
            if "sealed trait" in line or "enum " in line:
                pending_givens.append((indent, type_name))
            else:
                result.append(
                    f"{indent}given Codec.AsObject[{type_name}] = deriveConfiguredCodec"
                )
        else:
            result.append(line)

    # Flush any remaining pending givens
    for indent, type_name in pending_givens:
        result.append(
            f"{indent}given Codec.AsObject[{type_name}] = deriveConfiguredCodec"
        )
    return "\n".join(result)


def handle_derives_codec_in_configured_scope(text):
    """Replace `derives Codec` (in configured blocks) with explicit given."""
    lines = text.split("\n")
    result = []
    for line in lines:
        # Only match actual code lines, not comments or string literals
        stripped = line.lstrip()
        if stripped.startswith("//"):
            result.append(line)
            continue
        if (
            " derives Codec" in line
            and "ConfiguredCodec" not in line
            and "compileErrors" not in line
            and not stripped.startswith("//")
            and not stripped.startswith("*")
        ):
            m = re.search(r"(?:case class|class|enum)\s+(\w+)", line)
            if m:
                type_name = m.group(1)
                indent = re.match(r"(\s*)", line).group(1)
                cleaned = re.sub(r"\s+derives\s+Codec(?:\.AsObject)?", "", line)
                result.append(cleaned)
                result.append(
                    f"{indent}given Codec.AsObject[{type_name}] = deriveConfiguredCodec"
                )
            else:
                result.append(line)
        else:
            result.append(line)
    return "\n".join(result)


# ---------------------------------------------------------------------------
# ConfiguredEnumDerivesSuites
# ---------------------------------------------------------------------------


def transform_enum_suite(text):
    text = strip_license_header(text)

    # Package
    text = text.replace("package io.circe\n", "package io.circe.generic\n")

    # Replace derivation calls
    text = text.replace("ConfiguredEnumCodec.derived", "deriveEnumCodec")

    # Transform the compile-errors test for non-singleton enums.
    text = transform_enum_compile_error_test(text)

    # Replace imports
    text = text.replace("import io.circe.derivation.*", "import io.circe.derivation.Configuration")
    text = add_import(text, "import io.circe.generic.semiauto.*")

    header = make_header(
        "ConfiguredEnumDerivesSuites.scala",
        [
            "Package: io.circe -> io.circe.generic",
            "Replaced: ConfiguredEnumCodec.derived -> deriveEnumCodec",
            "Transformed: compile-error test to use deriveEnumCodec",
            "Replaced: import io.circe.derivation.* -> import io.circe.derivation.Configuration",
            "Added: import io.circe.generic.semiauto.*",
        ],
    )
    text = header + text
    return text


def transform_enum_compile_error_test(text):
    """Transform the non-singleton enum compile-error test.

    Circe defines the enum inline inside compileErrors(). We need the enum
    defined in the companion object so we can reference it with deriveEnumCodec.
    """
    # Add WithNonSingletonCase to the companion object (properly indented)
    companion_marker = "object ConfiguredEnumDerivesSuites:\n"
    if companion_marker in text:
        # Check if already has WithNonSingletonCase in companion
        companion_start = text.index(companion_marker)
        class_start = text.index("class ConfiguredEnumDerivesSuites")
        companion_body = text[companion_start:class_start]
        if "WithNonSingletonCase" not in companion_body:
            insertion = (
                "  enum WithNonSingletonCase:\n"
                "    case SingletonCase\n"
                "    case NonSingletonCase(field: Int)\n"
                "\n"
            )
            text = text.replace(
                companion_marker,
                companion_marker + insertion,
            )

    # Replace the test body
    old_test = re.search(
        r'(  test\("ConfiguredEnum derivation must fail to compile for enums with non singleton cases"\) \{)\n.*?\n(  \})',
        text,
        flags=re.DOTALL,
    )
    if old_test:
        new_body = (
            old_test.group(1)
            + "\n"
            + "    given Configuration = Configuration.default\n"
            + '    assert(compileErrors("deriveEnumCodec[ConfiguredEnumDerivesSuites.WithNonSingletonCase]").nonEmpty)\n'
            + old_test.group(2)
        )
        text = text[: old_test.start()] + new_body + text[old_test.end() :]

    return text


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def main():
    if not (REPO_ROOT / "upstream" / "circe" / ".git").exists():
        print("ERROR: circe submodule not found at upstream/circe/")
        print("       Run: git submodule update --init")
        sys.exit(1)

    version = get_circe_version()
    print(f"Syncing circe tests from upstream ({version})...")
    print()

    # 1. DerivesSuite → AutoDerivedSuite
    print("DerivesSuite.scala → AutoDerivedSuite.scala")
    text = read_upstream("DerivesSuite.scala")
    text = transform_derives_suite(text)
    write_output("AutoDerivedSuite.scala", text)

    # 2. SemiautoDerivationSuite → SemiautoDerivedSuite
    print("SemiautoDerivationSuite.scala → SemiautoDerivedSuite.scala")
    text = read_upstream("SemiautoDerivationSuite.scala")
    text = transform_semiauto_suite(text)
    write_output("SemiautoDerivedSuite.scala", text)

    # 3. ConfiguredDerivesSuite
    print("ConfiguredDerivesSuite.scala → ConfiguredDerivesSuite.scala")
    text = read_upstream("ConfiguredDerivesSuite.scala")
    text = transform_configured_suite(text)
    write_output("ConfiguredDerivesSuite.scala", text)

    # 4. ConfiguredEnumDerivesSuites
    print("ConfiguredEnumDerivesSuites.scala → ConfiguredEnumDerivesSuites.scala")
    text = read_upstream("ConfiguredEnumDerivesSuites.scala")
    text = transform_enum_suite(text)
    write_output("ConfiguredEnumDerivesSuites.scala", text)

    print()
    print("Done. Run `./mill compat.jvm.test` to verify.")


if __name__ == "__main__":
    main()
