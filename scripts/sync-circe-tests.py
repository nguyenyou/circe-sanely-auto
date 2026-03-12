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

  DerivesSuite.scala → SemiautoDerivesSuite.scala
    - Replace `derives` clauses with explicit givens using deriveDecoder/deriveEncoder/deriveCodec
    - Add `import io.circe.generic.semiauto.*`

  SemiautoDerivationSuite.scala → SemiautoDerivedSuite.scala
    - Replace Decoder.derived → deriveDecoder, etc.
    - Add `import io.circe.generic.semiauto._`

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
# DerivesSuite → SemiautoDerivesSuite
# ---------------------------------------------------------------------------


def extract_derives_info(text):
    """Extract (type_name, type_params, derives_list) for each type with a derives clause.

    Scans the raw upstream text BEFORE derives clauses are removed.
    Handles single-line and multi-line derives patterns.
    """
    results = []
    lines = text.split("\n")
    i = 0
    while i < len(lines):
        line = lines[i]

        # Try to find a derives clause on this line or spanning two lines
        derives_raw = None

        # Case 1: multi-line derives ending with comma → next line has the rest
        # Must check BEFORE single-line to avoid partial capture (e.g. "derives Encoder.AsObject,")
        if re.search(r"derives\s+[\w.]+\s*,\s*$", line) and i + 1 < len(lines):
            combined = line.rstrip() + " " + lines[i + 1].strip()
            m = re.search(r"derives\s+([\w.]+(?:\s*,\s*[\w.]+)*)", combined)
            if m:
                derives_raw = m.group(1)
                i += 1  # skip the continuation line
        # Case 2: derives on same line (single-line, no trailing comma)
        elif re.search(r"derives\s+([\w.]+(?:\s*,\s*[\w.]+)*)", line):
            m = re.search(r"derives\s+([\w.]+(?:\s*,\s*[\w.]+)*)", line)
            derives_raw = m.group(1)

        if derives_raw:
            derives_list = [d.strip() for d in derives_raw.split(",")]
            # Search backwards to find the associated type definition
            for j in range(i, max(i - 50, -1), -1):
                tm = re.search(
                    r"(?:case\s+class|sealed\s+trait|enum|class)\s+(\w+)(?:\[([^\]]+)\])?",
                    lines[j],
                )
                if tm:
                    results.append((tm.group(1), tm.group(2), derives_list))
                    break
        i += 1
    return results


def generate_given_lines(type_name, type_params, derives_list):
    """Generate explicit given definition strings for a type's derives clause."""
    givens = []
    if type_params:
        full_type = f"{type_name}[{type_params}]"
        # Parse type param names (strip any bounds)
        params = [p.strip().split(":")[0].strip() for p in type_params.split(",")]
    else:
        full_type = type_name
        params = []

    for trait in derives_list:
        if trait in ("Codec", "Codec.AsObject"):
            if params:
                bounds = ", ".join(f"{p}: Encoder.AsObject : Decoder" for p in params)
                givens.append(
                    f"given [{bounds}]: Codec.AsObject[{full_type}] = deriveCodec"
                )
            else:
                givens.append(f"given Codec.AsObject[{type_name}] = deriveCodec")
        elif trait in ("Encoder", "Encoder.AsObject"):
            if params:
                bounds = ", ".join(f"{p}: Encoder" for p in params)
                givens.append(
                    f"given [{bounds}]: Encoder.AsObject[{full_type}] = deriveEncoder"
                )
            else:
                givens.append(f"given Encoder.AsObject[{type_name}] = deriveEncoder")
        elif trait == "Decoder":
            if params:
                bounds = ", ".join(f"{p}: Decoder" for p in params)
                givens.append(
                    f"given [{bounds}]: Decoder[{full_type}] = deriveDecoder"
                )
            else:
                givens.append(f"given Decoder[{type_name}] = deriveDecoder")
    return givens


def insert_semiauto_givens(text, derives_info):
    """Insert explicit semiauto givens into companion objects (or create them).

    For types with existing companions: inserts givens at the start of the companion body.
    For types without companions: creates a minimal companion object after the type def.
    """
    # Build a map: type_name -> list of given strings
    givens_map = {}
    for type_name, type_params, derives_list in derives_info:
        given_lines = generate_given_lines(type_name, type_params, derives_list)
        if given_lines:
            givens_map[type_name] = given_lines

    if not givens_map:
        return text

    lines = text.split("\n")

    # First pass: find which types have companion objects
    has_companion = set()
    for line in lines:
        m = re.match(r"\s*object\s+(\w+)\s*[:{]", line)
        if m and m.group(1) in givens_map:
            has_companion.add(m.group(1))

    needs_companion = set(givens_map.keys()) - has_companion

    # Second pass: insert givens
    result = []
    inserted = set()

    for i, line in enumerate(lines):
        handled = False

        # Check if this line opens a companion for a type that needs givens
        m = re.match(r"(\s*)object\s+(\w+)\s*([:{])", line)
        if m and m.group(2) in givens_map and m.group(2) not in inserted:
            type_name = m.group(2)
            indent = m.group(1)
            result.append(line)
            for g in givens_map[type_name]:
                result.append(f"{indent}  {g}")
            inserted.add(type_name)
            handled = True

        if not handled:
            # Check if this is a type def that needs a NEW companion
            for type_name in list(needs_companion):
                if type_name in inserted:
                    continue
                # Match the type definition line
                if re.match(
                    rf"\s*(?:case\s+class|class)\s+{re.escape(type_name)}\b", line
                ):
                    indent = re.match(r"(\s*)", line).group(1)
                    result.append(line)
                    result.append(f"{indent}object {type_name}:")
                    for g in givens_map[type_name]:
                        result.append(f"{indent}  {g}")
                    inserted.add(type_name)
                    needs_companion.discard(type_name)
                    handled = True
                    break

        if not handled:
            result.append(line)

    return "\n".join(result)


def transform_derives_suite_semiauto(text):
    """Transform DerivesSuite into a semiauto version with explicit givens.

    Instead of using `import auto._`, replaces each `derives` clause with
    explicit `given` definitions using deriveDecoder/deriveEncoder/deriveCodec.
    This tests our semiauto path with the full range of DerivesSuite types:
    recursive ADTs, enums, custom variant instances, large types, etc.
    """
    # Extract derives info BEFORE removing clauses
    derives_info = extract_derives_info(text)

    text = strip_license_header(text)

    # Package
    text = text.replace("package io.circe\n", "package io.circe.generic\n")

    # Rename
    text = text.replace("DerivesSuite", "SemiautoDerivesSuite")

    # Remove all derives clauses
    text = remove_derives_clauses(text)

    # Fix Scala 3 deprecation: `String with Tag` → `String & Tag`
    text = text.replace("String with Tag", "String & Tag")

    # Add semiauto import
    text = add_import(text, "import io.circe.generic.semiauto.*")

    # Insert explicit givens based on extracted derives info
    text = insert_semiauto_givens(text, derives_info)

    # Add header
    header = make_header(
        "DerivesSuite.scala",
        [
            "Package: io.circe -> io.circe.generic",
            "Renamed: DerivesSuite -> SemiautoDerivesSuite",
            "Replaced `derives` clauses with explicit givens using deriveDecoder/deriveEncoder/deriveCodec",
            "Fixed: `String with Tag` -> `String & Tag` (Scala 3 intersection syntax)",
            "Added: import io.circe.generic.semiauto.*",
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

    # 1. DerivesSuite → AutoDerivedSuite (auto derivation via import)
    print("DerivesSuite.scala → AutoDerivedSuite.scala")
    text = read_upstream("DerivesSuite.scala")
    text = transform_derives_suite(text)
    write_output("AutoDerivedSuite.scala", text)

    # 2. DerivesSuite → SemiautoDerivesSuite (explicit givens via semiauto)
    print("DerivesSuite.scala → SemiautoDerivesSuite.scala")
    text = read_upstream("DerivesSuite.scala")
    text = transform_derives_suite_semiauto(text)
    write_output("SemiautoDerivesSuite.scala", text)

    # 3. SemiautoDerivationSuite → SemiautoDerivedSuite
    print("SemiautoDerivationSuite.scala → SemiautoDerivedSuite.scala")
    text = read_upstream("SemiautoDerivationSuite.scala")
    text = transform_semiauto_suite(text)
    write_output("SemiautoDerivedSuite.scala", text)

    # 4. ConfiguredDerivesSuite
    print("ConfiguredDerivesSuite.scala → ConfiguredDerivesSuite.scala")
    text = read_upstream("ConfiguredDerivesSuite.scala")
    text = transform_configured_suite(text)
    write_output("ConfiguredDerivesSuite.scala", text)

    # 5. ConfiguredEnumDerivesSuites
    print("ConfiguredEnumDerivesSuites.scala → ConfiguredEnumDerivesSuites.scala")
    text = read_upstream("ConfiguredEnumDerivesSuites.scala")
    text = transform_enum_suite(text)
    write_output("ConfiguredEnumDerivesSuites.scala", text)

    print()
    print("Done. Run `./mill compat.jvm.test` to verify.")


if __name__ == "__main__":
    main()
