#!/usr/bin/env python3
"""Analyze bytecode from compiled Scala class files.

Usage:
  # Single directory — report stats
  python3 analyze_bytecode.py <classes-dir>

  # Two directories — compare stats (A vs B)
  python3 analyze_bytecode.py <classes-dir-A> <classes-dir-B> [--labels "With Change" "Without Change"]

  # With pattern filtering — only count specific bytecode patterns
  python3 analyze_bytecode.py <classes-dir> --patterns "LazyRef" "lzyINIT" "monitorenter"

  # Save javap output for manual inspection
  python3 analyze_bytecode.py <classes-dir> --dump-javap /tmp/javap-output.txt

Requires: javap (ships with JDK)
"""

import argparse
import os
import subprocess
import sys
from pathlib import Path


# Default patterns that indicate lazy val / synchronization overhead
DEFAULT_PATTERNS = ["LazyRef", "lzyINIT", "monitorenter", "monitorexit"]


def find_class_files(directory: str) -> list[str]:
    """Find all .class files recursively."""
    result = []
    for root, _, files in os.walk(directory):
        for f in files:
            if f.endswith(".class"):
                result.append(os.path.join(root, f))
    return sorted(result)


def total_bytes(class_files: list[str]) -> int:
    """Sum file sizes of all class files."""
    return sum(os.path.getsize(f) for f in class_files)


def run_javap(class_files: list[str], disassemble: bool = True) -> str:
    """Run javap on all class files and return combined output."""
    if not class_files:
        return ""
    flags = ["-p", "-c"] if disassemble else ["-p"]
    # javap can handle many files at once, but very large lists may hit arg limits
    batch_size = 200
    output_parts = []
    for i in range(0, len(class_files), batch_size):
        batch = class_files[i : i + batch_size]
        try:
            result = subprocess.run(
                ["javap"] + flags + batch,
                capture_output=True,
                text=True,
                timeout=120,
            )
            output_parts.append(result.stdout)
        except (subprocess.TimeoutExpired, FileNotFoundError) as e:
            print(f"Warning: javap failed: {e}", file=sys.stderr)
    return "\n".join(output_parts)


def count_methods(javap_output: str) -> int:
    """Count method declarations in javap output."""
    count = 0
    for line in javap_output.splitlines():
        stripped = line.strip()
        if stripped and ("(" in stripped) and (")" in stripped):
            # Method declarations have access modifiers and parentheses
            for keyword in ("private", "public", "protected", "static"):
                if stripped.startswith(keyword) and "(" in stripped:
                    count += 1
                    break
    return count


def count_patterns(javap_output: str, patterns: list[str]) -> dict[str, int]:
    """Count occurrences of each pattern in javap disassembly."""
    counts = {p: 0 for p in patterns}
    for line in javap_output.splitlines():
        for p in patterns:
            if p in line:
                counts[p] += 1
    return counts


def collect_stats(
    directory: str, patterns: list[str]
) -> dict:
    """Collect all bytecode stats for a directory."""
    class_files = find_class_files(directory)
    if not class_files:
        print(f"Error: no .class files found in {directory}", file=sys.stderr)
        sys.exit(1)

    javap_methods = run_javap(class_files, disassemble=False)
    javap_disasm = run_javap(class_files, disassemble=True)

    return {
        "directory": directory,
        "class_count": len(class_files),
        "total_bytes": total_bytes(class_files),
        "method_count": count_methods(javap_methods),
        "pattern_counts": count_patterns(javap_disasm, patterns),
        "pattern_total": sum(count_patterns(javap_disasm, patterns).values()),
        "_javap_disasm": javap_disasm,
    }


def format_bytes(n: int) -> str:
    """Format byte count with KB."""
    if n >= 1024 * 1024:
        return f"{n:,} ({n / 1024 / 1024:.1f} MB)"
    elif n >= 1024:
        return f"{n:,} ({n / 1024:.1f} KB)"
    return f"{n:,}"


def print_single(stats: dict, patterns: list[str], label: str = ""):
    """Print stats for a single directory."""
    header = f"BYTECODE ANALYSIS: {label or stats['directory']}"
    print("=" * 70)
    print(header)
    print("=" * 70)
    print(f"  Class files:      {stats['class_count']}")
    print(f"  Total bytecode:   {format_bytes(stats['total_bytes'])}")
    print(f"  Total methods:    {stats['method_count']:,}")
    if patterns:
        print(f"  Pattern matches:  {stats['pattern_total']:,}")
        for p, c in stats["pattern_counts"].items():
            print(f"    {p:30s} {c:,}")
    print("=" * 70)


def print_comparison(
    stats_a: dict, stats_b: dict, patterns: list[str], labels: tuple[str, str]
):
    """Print side-by-side comparison of two directories."""
    label_a, label_b = labels

    def delta_str(a: int, b: int) -> str:
        diff = a - b
        if b == 0:
            return f"{diff:+,}"
        pct = abs(diff) * 100 / b
        direction = "fewer" if diff < 0 else "more"
        return f"{diff:+,} ({pct:.1f}% {direction})"

    w = 72
    print("=" * w)
    print("BYTECODE COMPARISON".center(w))
    print("=" * w)
    print(f"  {'Metric':<28s} {'[A] ' + label_a:>18s}  {'[B] ' + label_b:>18s}  {'Delta (A-B)':>20s}")
    print("-" * w)

    rows = [
        ("Class files", stats_a["class_count"], stats_b["class_count"]),
        ("Total bytecode (bytes)", stats_a["total_bytes"], stats_b["total_bytes"]),
        ("Total methods", stats_a["method_count"], stats_b["method_count"]),
    ]
    for name, va, vb in rows:
        print(f"  {name:<28s} {va:>18,}  {vb:>18,}  {delta_str(va, vb):>20s}")

    if patterns:
        print("-" * w)
        pa = stats_a["pattern_counts"]
        pb = stats_b["pattern_counts"]
        for p in patterns:
            va, vb = pa[p], pb[p]
            print(f"  {p:<28s} {va:>18,}  {vb:>18,}  {delta_str(va, vb):>20s}")
        va, vb = stats_a["pattern_total"], stats_b["pattern_total"]
        print(f"  {'(pattern total)':<28s} {va:>18,}  {vb:>18,}  {delta_str(va, vb):>20s}")

    print("=" * w)


def main():
    parser = argparse.ArgumentParser(
        description="Analyze and compare bytecode from compiled Scala class files."
    )
    parser.add_argument(
        "directories",
        nargs="+",
        help="One or two directories containing .class files",
    )
    parser.add_argument(
        "--labels",
        nargs=2,
        default=None,
        metavar=("LABEL_A", "LABEL_B"),
        help="Labels for comparison (default: directory names)",
    )
    parser.add_argument(
        "--patterns",
        nargs="*",
        default=DEFAULT_PATTERNS,
        help=f"Bytecode patterns to count (default: {' '.join(DEFAULT_PATTERNS)})",
    )
    parser.add_argument(
        "--dump-javap",
        metavar="FILE",
        help="Save full javap disassembly to a file (first directory only)",
    )

    args = parser.parse_args()

    if len(args.directories) == 1:
        stats = collect_stats(args.directories[0], args.patterns)
        print_single(stats, args.patterns)
        if args.dump_javap:
            Path(args.dump_javap).write_text(stats["_javap_disasm"])
            print(f"\nJavap output saved to {args.dump_javap}")

    elif len(args.directories) == 2:
        stats_a = collect_stats(args.directories[0], args.patterns)
        stats_b = collect_stats(args.directories[1], args.patterns)
        labels = args.labels or (
            os.path.basename(args.directories[0].rstrip("/")),
            os.path.basename(args.directories[1].rstrip("/")),
        )
        print_comparison(stats_a, stats_b, args.patterns, labels)
        if args.dump_javap:
            Path(args.dump_javap).write_text(stats_a["_javap_disasm"])
            print(f"\nJavap output saved to {args.dump_javap}")
    else:
        parser.error("Provide one or two directories")


if __name__ == "__main__":
    main()
