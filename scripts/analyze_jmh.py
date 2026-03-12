#!/usr/bin/env python3
"""Parse JMH benchmark output and produce a compact runtime summary.

Usage:
  python3 analyze_jmh.py <jmh-output-file>
  python3 analyze_jmh.py -              # read from stdin
  ./mill benchmark-jmh.runJmh 2>&1 | python3 scripts/analyze_jmh.py -

Accepts raw JMH output or mill-prefixed log output (e.g. "178] ReadBenchmark...").
Extracts the JMH summary table and produces:
  1. A compact "At a Glance — Runtime" markdown table
  2. Per-benchmark detail with error margins
  3. Comparison ratios vs circe baselines
"""

import re
import sys
from pathlib import Path

# ── Configuration ──

JMH_NAME_MAP = {
    "ReadBenchmark.circeJawn": ("circe-jawn", "reading"),
    "ReadBenchmark.circeJsoniterBridge": ("circe+jsoniter", "reading"),
    "ReadBenchmark.sanelyJsoniter": ("sanely-jsoniter", "reading"),
    "ReadBenchmark.jsoniterScala": ("jsoniter-scala", "reading"),
    "WriteBenchmark.circePrinter": ("circe-printer", "writing"),
    "WriteBenchmark.circeJsoniterBridge": ("circe+jsoniter", "writing"),
    "WriteBenchmark.sanelyJsoniter": ("sanely-jsoniter", "writing"),
    "WriteBenchmark.jsoniterScala": ("jsoniter-scala", "writing"),
}

# Display order within each section
DISPLAY_ORDER = ["circe-jawn", "circe-printer", "circe+jsoniter", "sanely-jsoniter", "jsoniter-scala"]

BASELINES = {"reading": "circe-jawn", "writing": "circe-printer"}


# ── Parsing ──

def parse_jmh(text: str) -> dict:
    """Parse JMH summary table lines into structured data.

    Returns: {"reading": {name: {ops, error}}, "writing": {name: {ops, error}}}
    """
    result = {"reading": {}, "writing": {}}

    for line in text.splitlines():
        # Match both raw JMH and mill-prefixed: "178] ReadBenchmark.circeJawn thrpt 10 89856.884 ± 1240.638 ops/s"
        match = re.match(
            r"(?:\d+\]\s+)?(?:runtime\.)?(\w+\.\w+)\s+thrpt\s+(\d+)\s+([\d.]+)\s*±\s*([\d.]+)\s+ops/s",
            line,
        )
        if not match:
            continue

        benchmark_name = match.group(1)
        mapping = JMH_NAME_MAP.get(benchmark_name)
        if not mapping:
            continue

        display_name, section = mapping
        result[section][display_name] = {
            "ops": float(match.group(3)),
            "error": float(match.group(4)),
            "cnt": int(match.group(2)),
        }

    return result


def extract_jvm_info(text: str) -> str | None:
    """Extract JVM version from JMH output."""
    match = re.search(r"# VM version: (.+)", text)
    return match.group(1) if match else None


def extract_jmh_config(text: str) -> str | None:
    """Extract JMH config (forks, warmup, measurement) from output."""
    forks = re.search(r"# Fork: (\d+) of (\d+)", text)
    warmup = re.search(r"# Warmup: (\d+) iterations, ([\d.]+) s each", text)
    measurement = re.search(r"# Measurement: (\d+) iterations, ([\d.]+) s each", text)

    parts = []
    if forks:
        parts.append(f"{forks.group(2)} fork{'s' if int(forks.group(2)) > 1 else ''}")
    if warmup:
        parts.append(f"{warmup.group(1)}wi x {warmup.group(2)}s")
    if measurement:
        parts.append(f"{measurement.group(1)}i x {measurement.group(2)}s")
    return ", ".join(parts) if parts else None


# ── Formatting ──

def fmt_ops(ops: float) -> str:
    if ops >= 1_000_000:
        return f"{ops/1_000_000:.2f}M"
    if ops >= 1_000:
        return f"{ops/1_000:,.0f}k"
    return f"{ops:.0f}"


def fmt_ops_with_error(ops: float, error: float) -> str:
    return f"{fmt_ops(ops)} ± {fmt_ops(error)}"


def fmt_ratio(ratio: float) -> str:
    if ratio >= 1.0:
        return f"**{ratio:.1f}x**"
    return f"{ratio:.1f}x"


# ── Output ──

def build_glance_table(data: dict) -> list[str]:
    """Build the 'At a Glance — Runtime' markdown table."""
    lines = []
    lines.append("### At a Glance — Runtime (JMH)")
    lines.append("")
    lines.append("| Benchmark | ops/sec | vs circe |")
    lines.append("|-----------|---------|----------|")

    for section, label in [("reading", "Read"), ("writing", "Write")]:
        benchmarks = data[section]
        baseline_name = BASELINES[section]
        baseline = benchmarks.get(baseline_name)

        for name in DISPLAY_ORDER:
            if name not in benchmarks:
                continue
            d = benchmarks[name]
            ops_str = fmt_ops_with_error(d["ops"], d["error"])
            if name == baseline_name:
                ratio_str = "1.0x"
            elif baseline:
                ratio = d["ops"] / baseline["ops"]
                ratio_str = fmt_ratio(ratio)
            else:
                ratio_str = "—"
            lines.append(f"| {label}: {name} | {ops_str} | {ratio_str} |")

    return lines


def build_detail_table(data: dict) -> list[str]:
    """Build detailed per-benchmark table with all metrics."""
    lines = []
    lines.append("### Detailed Results")
    lines.append("")

    for section, label in [("reading", "Reading (bytes → case class)"), ("writing", "Writing (case class → bytes)")]:
        benchmarks = data[section]
        if not benchmarks:
            continue

        baseline_name = BASELINES[section]
        baseline = benchmarks.get(baseline_name)

        lines.append(f"**{label}**")
        lines.append("")
        lines.append("| Library | ops/sec | ± error | vs baseline | min (ops/sec) | max (ops/sec) |")
        lines.append("|---------|---------|---------|-------------|---------------|---------------|")

        for name in DISPLAY_ORDER:
            if name not in benchmarks:
                continue
            d = benchmarks[name]
            ops = d["ops"]
            error = d["error"]
            min_ops = ops - error
            max_ops = ops + error

            if name == baseline_name:
                ratio_str = "baseline"
            elif baseline:
                ratio = ops / baseline["ops"]
                ratio_str = fmt_ratio(ratio)
            else:
                ratio_str = "—"

            lines.append(
                f"| {name} | {fmt_ops(ops)} | {fmt_ops(error)} | {ratio_str} | {fmt_ops(min_ops)} | {fmt_ops(max_ops)} |"
            )

        lines.append("")

    return lines


def build_highlights(data: dict) -> list[str]:
    """Build key highlights section."""
    lines = []
    reading = data["reading"]
    writing = data["writing"]

    highlights = []

    # sanely-jsoniter vs circe-jawn
    if "sanely-jsoniter" in reading and "circe-jawn" in reading:
        ratio = reading["sanely-jsoniter"]["ops"] / reading["circe-jawn"]["ops"]
        highlights.append(f"sanely-jsoniter reads **{ratio:.1f}x** faster than circe-jawn")

    if "sanely-jsoniter" in writing and "circe-printer" in writing:
        ratio = writing["sanely-jsoniter"]["ops"] / writing["circe-printer"]["ops"]
        highlights.append(f"sanely-jsoniter writes **{ratio:.1f}x** faster than circe-printer")

    # sanely-jsoniter vs jsoniter-scala
    if "sanely-jsoniter" in reading and "jsoniter-scala" in reading:
        ratio = reading["sanely-jsoniter"]["ops"] / reading["jsoniter-scala"]["ops"]
        if ratio >= 1.0:
            highlights.append(f"sanely-jsoniter reads **+{(ratio-1)*100:.0f}%** faster than jsoniter-scala native")
        else:
            highlights.append(f"sanely-jsoniter reads **{(1-ratio)*100:.0f}%** slower than jsoniter-scala native")

    if "sanely-jsoniter" in writing and "jsoniter-scala" in writing:
        ratio = writing["sanely-jsoniter"]["ops"] / writing["jsoniter-scala"]["ops"]
        if ratio >= 1.0:
            highlights.append(f"sanely-jsoniter writes **+{(ratio-1)*100:.0f}%** faster than jsoniter-scala native")
        else:
            highlights.append(f"sanely-jsoniter writes **{(1-ratio)*100:.0f}%** slower than jsoniter-scala native")

    if highlights:
        lines.append("### Key Takeaways")
        lines.append("")
        for h in highlights:
            lines.append(f"- {h}")
        lines.append("")

    return lines


def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <jmh-output-file>", file=sys.stderr)
        print(f"       {sys.argv[0]} -  (read from stdin)", file=sys.stderr)
        sys.exit(1)

    path = sys.argv[1]
    if path == "-":
        text = sys.stdin.read()
    else:
        text = Path(path).read_text()

    data = parse_jmh(text)

    if not data["reading"] and not data["writing"]:
        print("No JMH benchmark results found in input.", file=sys.stderr)
        sys.exit(1)

    # Header
    jvm = extract_jvm_info(text)
    config = extract_jmh_config(text)
    meta_parts = []
    if jvm:
        meta_parts.append(f"**JVM:** {jvm}")
    if config:
        meta_parts.append(f"**Config:** {config}")
    if meta_parts:
        print(" | ".join(meta_parts))
        print()

    # Glance table
    for line in build_glance_table(data):
        print(line)
    print()

    # Highlights
    for line in build_highlights(data):
        print(line)

    # Detail table
    for line in build_detail_table(data):
        print(line)


if __name__ == "__main__":
    main()
