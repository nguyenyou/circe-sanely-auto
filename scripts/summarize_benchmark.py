#!/usr/bin/env python3
"""Parse benchmark result files and produce a compact markdown summary.

Usage:
  python3 summarize_benchmark.py <results-dir>

Expects the following files in <results-dir>/:
  compile-auto/compile-auto.txt         — hyperfine output
  compile-configured/compile-configured.txt — hyperfine output
  runtime/runtime.txt                   — RuntimeBenchmark output
  peak-rss/peak-rss.txt                 — Peak RSS measurements
  bytecode-impact/bytecode-impact.txt   — Bytecode size comparison
  macro-profile-auto/macro-profile-auto.txt         — (optional, for raw section)
  macro-profile-configured/macro-profile-configured.txt — (optional, for raw section)
"""

import os
import re
import sys
from pathlib import Path


def read_file(results_dir: str, *parts: str) -> str | None:
    """Read a file from results directory, return None if missing."""
    for root, _, files in os.walk(os.path.join(results_dir, parts[0])):
        for f in files:
            if f.endswith(".txt"):
                return Path(os.path.join(root, f)).read_text()
    return None


# ── Hyperfine parser ──


def parse_hyperfine(text: str) -> dict | None:
    """Parse hyperfine output into {name: {mean, stddev, speedup}}."""
    if not text:
        return None
    results = {}
    # Match: "Benchmark N: name"
    # Then: "Time (mean ± σ):  X.XXX s ±  X.XXX s"
    blocks = re.split(r"Benchmark \d+:", text)
    for block in blocks[1:]:  # skip pre-header
        name_match = re.match(r"\s*(.+)", block)
        if not name_match:
            continue
        name = name_match.group(1).strip()
        time_match = re.search(
            r"Time \(mean ± σ\):\s+([\d.]+)\s*s\s*±\s*([\d.]+)\s*s", block
        )
        if time_match:
            results[name] = {
                "mean": float(time_match.group(1)),
                "stddev": float(time_match.group(2)),
            }

    # Parse summary line: "X.XX ± Y.YY times faster than Z"
    summary = re.search(
        r"(\S+)\s+ran\s+([\d.]+)\s*±\s*([\d.]+)\s*times faster than\s+(\S+)", text
    )
    if summary and len(results) == 2:
        faster_name = summary.group(1)
        speedup = float(summary.group(2))
        slower_name = summary.group(4)
        if faster_name in results:
            results[faster_name]["speedup"] = speedup
            results[faster_name]["vs"] = slower_name
        if slower_name in results:
            results[slower_name]["speedup"] = 1.0 / speedup
            results[slower_name]["vs"] = faster_name

    return results if results else None


# ── Runtime parser ──


def parse_runtime(text: str) -> dict | None:
    """Parse RuntimeBenchmark output into structured data."""
    if not text:
        return None

    result = {"reading": {}, "writing": {}, "reading_cmp": {}, "writing_cmp": {}}

    # Find reading and writing sections
    sections = re.split(r"\n(Reading|Writing)\s+\(", text)

    current_section = None
    in_comparison = False

    for line in text.splitlines():
        if line.startswith("Reading"):
            current_section = "reading"
            in_comparison = False
            continue
        if line.startswith("Writing"):
            current_section = "writing"
            in_comparison = False
            continue
        if line.startswith("---"):
            continue
        if not current_section:
            continue

        # Empty line after results → comparison section follows
        if line.strip() == "":
            if current_section and not in_comparison:
                in_comparison = True
            continue

        # Parse result line: "  name        123456 ops/sec  (min=..., max=...)  XX KB/op"
        result_match = re.match(
            r"\s+(\S+(?:\+\S+)?)\s+([\d.]+)\s+ops/sec\s+\(min=([\d.]+),\s*max=([\d.]+)\)\s+(.+)",
            line,
        )
        if result_match and not in_comparison:
            name = result_match.group(1)
            result[current_section][name] = {
                "ops_sec": float(result_match.group(2)),
                "min": float(result_match.group(3)),
                "max": float(result_match.group(4)),
                "alloc": result_match.group(5).strip(),
            }
            continue

        # Parse comparison line: "  name        1.23x vs baseline  alloc 0.89x"
        cmp_match = re.match(
            r"\s+(\S+(?:\+\S+)?)\s+([\d.]+)x\s+vs\s+(\S+(?:\+\S+)?)\s*(.*)", line
        )
        if cmp_match:
            name = cmp_match.group(1)
            cmp_section = current_section + "_cmp"
            result[cmp_section][name] = {
                "ratio": float(cmp_match.group(2)),
                "vs": cmp_match.group(3),
            }
            alloc_match = re.search(r"alloc\s+([\d.]+)x", cmp_match.group(4))
            if alloc_match:
                result[cmp_section][name]["alloc_ratio"] = float(alloc_match.group(1))

    return result if any(result[k] for k in result) else None


# ── Peak RSS parser ──


def parse_peak_rss(text: str) -> dict | None:
    """Parse peak RSS output. Expected format:
    sanely-auto (auto): 450123 KB
    circe-generic (auto): 620456 KB
    sanely-auto (configured): 380123 KB
    circe-generic (configured): 540456 KB
    """
    if not text:
        return None
    result = {}
    for line in text.splitlines():
        match = re.match(r"(\S+)\s+\((\w+)\):\s+([\d.]+)\s*(KB|MB)", line)
        if match:
            name = match.group(1)
            mode = match.group(2)
            value = float(match.group(3))
            unit = match.group(4)
            mb = value / 1024 if unit == "KB" else value
            result[f"{name}_{mode}"] = mb
    return result if result else None


# ── Bytecode parser ──


def parse_bytecode(text: str) -> dict | None:
    """Parse bytecode impact output. Expected format:
    sanely-auto (auto): 123456 bytes (120.6 KB)
    circe-generic (auto): 234567 bytes (229.1 KB)
    ...
    """
    if not text:
        return None
    result = {}
    for line in text.splitlines():
        match = re.match(r"(\S+)\s+\((\w+)\):\s+(\d+)\s+bytes", line)
        if match:
            name = match.group(1)
            mode = match.group(2)
            value = int(match.group(3))
            result[f"{name}_{mode}"] = value
    return result if result else None


# ── Formatting helpers ──


def fmt_time(seconds: float, stddev: float | None = None) -> str:
    if stddev:
        return f"{seconds:.1f}s ± {stddev:.2f}s"
    return f"{seconds:.1f}s"


def fmt_speedup(ratio: float) -> str:
    if ratio >= 1:
        return f"**{ratio:.1f}x faster**"
    return f"{1/ratio:.1f}x slower"


def fmt_ops(ops: float) -> str:
    if ops >= 1_000_000:
        return f"{ops/1_000_000:.1f}M"
    if ops >= 1_000:
        return f"{ops/1_000:.0f}k"
    return f"{ops:.0f}"


def fmt_bytes(b: int) -> str:
    if b >= 1024 * 1024:
        return f"{b / (1024*1024):.1f} MB"
    if b >= 1024:
        return f"{b / 1024:.1f} KB"
    return f"{b} B"


def fmt_mb(mb: float) -> str:
    return f"{mb:.0f} MB"


def fmt_pct_change(a: float, b: float) -> str:
    """Format percentage change from b to a (negative = a is smaller)."""
    if b == 0:
        return "N/A"
    pct = (a - b) / b * 100
    if pct < 0:
        return f"**{pct:.0f}%**"
    return f"+{pct:.0f}%"


# ── Main summary builder ──


def build_summary(results_dir: str) -> str:
    compile_auto = parse_hyperfine(read_file(results_dir, "compile-auto"))
    compile_configured = parse_hyperfine(
        read_file(results_dir, "compile-configured")
    )
    runtime = parse_runtime(read_file(results_dir, "runtime"))
    peak_rss = parse_peak_rss(read_file(results_dir, "peak-rss"))
    bytecode = parse_bytecode(read_file(results_dir, "bytecode-impact"))

    lines = []

    # ── Compile-time table ──
    has_compile = compile_auto or compile_configured or peak_rss or bytecode
    if has_compile:
        lines.append("### At a Glance — Compile Time")
        lines.append("")
        lines.append("| Metric | sanely-auto | circe-generic | Delta |")
        lines.append("|--------|-------------|---------------|-------|")

        if compile_auto:
            sanely = None
            generic = None
            for name, data in compile_auto.items():
                if "sanely" in name:
                    sanely = data
                elif "generic" in name:
                    generic = data
            if sanely and generic:
                speedup = generic["mean"] / sanely["mean"]
                lines.append(
                    f"| Compile (auto, ~300 types) | {fmt_time(sanely['mean'], sanely['stddev'])} "
                    f"| {fmt_time(generic['mean'], generic['stddev'])} | {fmt_speedup(speedup)} |"
                )

        if compile_configured:
            sanely = None
            generic = None
            for name, data in compile_configured.items():
                if "sanely" in name:
                    sanely = data
                elif "generic" in name:
                    generic = data
            if sanely and generic:
                speedup = generic["mean"] / sanely["mean"]
                lines.append(
                    f"| Compile (configured, ~230 types) | {fmt_time(sanely['mean'], sanely['stddev'])} "
                    f"| {fmt_time(generic['mean'], generic['stddev'])} | {fmt_speedup(speedup)} |"
                )

        if peak_rss:
            sa = peak_rss.get("sanely-auto_auto")
            cg = peak_rss.get("circe-generic_auto")
            if sa and cg:
                lines.append(
                    f"| Peak RSS (auto) | {fmt_mb(sa)} | {fmt_mb(cg)} | {fmt_pct_change(sa, cg)} |"
                )
            sa = peak_rss.get("sanely-auto_configured")
            cg = peak_rss.get("circe-generic_configured")
            if sa and cg:
                lines.append(
                    f"| Peak RSS (configured) | {fmt_mb(sa)} | {fmt_mb(cg)} | {fmt_pct_change(sa, cg)} |"
                )

        if bytecode:
            sa = bytecode.get("sanely-auto_auto")
            cg = bytecode.get("circe-generic_auto")
            if sa is not None and cg is not None:
                lines.append(
                    f"| Bytecode (auto) | {fmt_bytes(sa)} | {fmt_bytes(cg)} | {fmt_pct_change(sa, cg)} |"
                )
            sa = bytecode.get("sanely-auto_configured")
            cg = bytecode.get("circe-generic_configured")
            if sa is not None and cg is not None:
                lines.append(
                    f"| Bytecode (configured) | {fmt_bytes(sa)} | {fmt_bytes(cg)} | {fmt_pct_change(sa, cg)} |"
                )

        lines.append("")

    # ── Runtime table ──
    if runtime and (runtime["reading_cmp"] or runtime["writing_cmp"]):
        lines.append("### At a Glance — Runtime")
        lines.append("")
        lines.append("| Benchmark | ops/sec | vs circe | alloc |")
        lines.append("|-----------|---------|----------|-------|")

        for section, label in [("reading", "Read"), ("writing", "Write")]:
            data = runtime[section]
            cmp = runtime[section + "_cmp"]
            for name in data:
                if name in ("circe-jawn", "circe-printer"):
                    continue  # skip baseline from detail rows
                ops = data[name]["ops_sec"]
                alloc = data[name]["alloc"]
                ratio_str = ""
                if name in cmp:
                    r = cmp[name]["ratio"]
                    ratio_str = f"**{r:.1f}x**"
                lines.append(
                    f"| {label}: {name} | {fmt_ops(ops)} | {ratio_str} | {alloc} |"
                )

        lines.append("")

    return "\n".join(lines)


def build_raw_details(results_dir: str) -> str:
    """Build the raw data details sections."""
    lines = []

    sections = [
        ("compile-auto", "Compile Time — Auto Derivation"),
        ("compile-configured", "Compile Time — Configured Derivation"),
        ("runtime", "Runtime Performance"),
        ("peak-rss", "Peak RSS"),
        ("bytecode-impact", "Bytecode Impact"),
        ("macro-profile-auto", "Macro Profile — Auto"),
        ("macro-profile-configured", "Macro Profile — Configured"),
    ]

    for folder, title in sections:
        text = read_file(results_dir, folder)
        if text:
            lines.append(f"<details>")
            lines.append(f"<summary>{title}</summary>")
            lines.append("")
            lines.append("```")
            lines.append(text.rstrip())
            lines.append("```")
            lines.append("</details>")
            lines.append("")

    return "\n".join(lines)


def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <results-dir> [--mode summary|raw|full]", file=sys.stderr)
        sys.exit(1)

    results_dir = sys.argv[1]
    mode = "full"
    if "--mode" in sys.argv:
        idx = sys.argv.index("--mode")
        if idx + 1 < len(sys.argv):
            mode = sys.argv[idx + 1]

    if mode == "summary":
        print(build_summary(results_dir))
    elif mode == "raw":
        print(build_raw_details(results_dir))
    else:
        summary = build_summary(results_dir)
        raw = build_raw_details(results_dir)
        if summary:
            print(summary)
        if raw:
            print(raw)


if __name__ == "__main__":
    main()
