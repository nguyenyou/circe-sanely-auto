#!/usr/bin/env python3
"""Analyze async-profiler allocation profiles from Scala compilation.

Parses collapsed stack output from async-profiler (event=alloc), categorizes
allocations across JVM subsystems and Scala compiler phases, and optionally
compares two profiles side by side.

Usage:
  # Capture allocation profile:
  rm -rf out/benchmark/sanely
  JAVA_TOOL_OPTIONS="-agentpath:$(brew --prefix async-profiler)/lib/libasyncProfiler.dylib=start,event=alloc,file=/tmp/alloc.txt,collapsed" \\
    ./mill --no-server benchmark.sanely.compile

  # Analyze single profile:
  python3 analyze_memory.py /tmp/alloc.txt

  # Compare two profiles:
  python3 analyze_memory.py /tmp/alloc-sanely.txt --compare /tmp/alloc-generic.txt --labels sanely circe-generic

  # JSON output:
  python3 analyze_memory.py /tmp/alloc.txt --json
"""

import sys
import argparse
import json
from collections import defaultdict


# === Categorization rules ===
# Order matters: first match wins. More specific rules go first.

COMPILER_RULES = [
    # Macro / quoting
    ('compiler.macro.quoted',     lambda f: 'scala/quoted' in f or 'scala.quoted' in f),
    ('compiler.macro.inlines',    lambda f: 'dotc/inlines' in f or 'Inliner' in f or 'dotc.inlines' in f),
    # Typer phases
    ('compiler.typer.implicits',  lambda f: ('Implicits' in f or 'ImplicitSearch' in f) and 'dotc' in f),
    ('compiler.typer',            lambda f: 'dotc/typer' in f or 'dotc.typer' in f),
    # Backend
    ('compiler.backend',          lambda f: 'backend/jvm' in f or 'backend.jvm' in f or 'scala/tools/asm' in f),
    # Core infrastructure
    ('compiler.transform',        lambda f: 'dotc/transform' in f or 'dotc.transform' in f),
    ('compiler.core.types',       lambda f: 'dotc/core/Types' in f or 'dotc.core.Types' in f),
    ('compiler.core.symbols',     lambda f: 'dotc/core/Symbols' in f or 'dotc.core.Symbols' in f),
    ('compiler.core',             lambda f: 'dotc/core' in f or 'dotc.core' in f),
    ('compiler.parsing',          lambda f: 'dotc/parsing' in f or 'dotc.parsing' in f),
    ('compiler.tasty',            lambda f: 'dotc/core/tasty' in f or 'dotc.core.tasty' in f),
    ('compiler.ast',              lambda f: 'dotc/ast' in f or 'dotc.ast' in f),
    ('compiler.other',            lambda f: 'dotty' in f or 'dotc' in f),
    # Zinc / incremental
    ('zinc',                      lambda f: 'xsbt' in f or 'zinc' in f or 'sbt/' in f),
]

JVM_RULES = [
    ('jvm.jit',                   lambda f: 'CompileBroker' in f or 'C2Compiler' in f or 'C1Compiler' in f),
    ('jvm.gc',                    lambda f: 'GCTask' in f or 'G1' in f),
    ('jvm.classload',             lambda f: 'ClassLoader' in f or 'defineClass' in f),
]

FRAMEWORK_RULES = [
    ('mill',                      lambda f: 'mill/' in f or 'mill.' in f),
    ('coursier',                  lambda f: 'coursier' in f),
]

# Allocation type categories (from the allocated object type)
ALLOC_TYPE_RULES = [
    ('array.byte',     lambda t: t == 'byte[]' or t == 'byte[]_[i]'),
    ('array.int',      lambda t: t == 'int[]' or t == 'int[]_[i]'),
    ('array.object',   lambda t: 'Object[]' in t),
    ('array.char',     lambda t: t == 'char[]' or t == 'char[]_[i]'),
    ('array.boolean',  lambda t: t == 'boolean[]' or t == 'boolean[]_[i]'),
    ('array.long',     lambda t: t == 'long[]' or t == 'long[]_[i]'),
    ('array.other',    lambda t: '[]' in t),
]


def categorize_stack(stack):
    """Categorize a stack trace into a bucket."""
    frames = stack.split(';')

    # Check from the deepest (most specific) frame outward
    for frame in reversed(frames):
        for name, rule in COMPILER_RULES:
            if rule(frame):
                return name

    # If no compiler frame found, check JVM-level categories on full stack
    for name, rule in JVM_RULES:
        if rule(stack):
            return name

    for name, rule in FRAMEWORK_RULES:
        if rule(stack):
            return name

    return 'other'


def get_alloc_type(stack):
    """Extract the allocated type from the deepest frame."""
    frames = stack.split(';')
    if frames:
        leaf = frames[-1]
        for name, rule in ALLOC_TYPE_RULES:
            if rule(leaf):
                return name
        return leaf.split('.')[-1] if '.' in leaf else leaf
    return 'unknown'


def parse_collapsed(filepath):
    """Parse async-profiler collapsed stacks file."""
    entries = []
    with open(filepath) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.rsplit(' ', 1)
            if len(parts) != 2:
                continue
            try:
                stack, count = parts[0], int(parts[1])
                entries.append((stack, count))
            except ValueError:
                continue
    return entries


def aggregate(entries):
    """Aggregate entries into categories."""
    total_samples = sum(count for _, count in entries)
    categories = defaultdict(int)
    alloc_types = defaultdict(int)
    top_allocators = defaultdict(int)

    for stack, count in entries:
        cat = categorize_stack(stack)
        categories[cat] += count

        atype = get_alloc_type(stack)
        alloc_types[atype] += count

        # Find the deepest non-leaf frame (the method that triggered allocation)
        frames = stack.split(';')
        if len(frames) >= 2:
            allocator = frames[-2] if len(frames) >= 2 else frames[-1]
            top_allocators[allocator] += count

    return {
        'total_samples': total_samples,
        'categories': dict(categories),
        'alloc_types': dict(alloc_types),
        'top_allocators': dict(top_allocators),
    }


def compute_groups(categories):
    """Group categories into high-level buckets."""
    groups = defaultdict(int)
    for cat, count in categories.items():
        prefix = cat.split('.')[0]
        groups[prefix] += count
    return dict(groups)


def print_single_report(agg, label="Profile", focus=None):
    """Print report for a single profile."""
    total = agg['total_samples']
    cats = agg['categories']
    groups = compute_groups(cats)

    print(f"\n{'=' * 72}")
    print(f"MEMORY ALLOCATION PROFILE: {label} ({total} samples)")
    print(f"{'=' * 72}")

    # High-level groups
    print(f"\n--- High-Level Breakdown ---")
    for group in sorted(groups.keys(), key=lambda g: -groups[g]):
        pct = groups[group] / total * 100
        bar = '#' * int(pct / 2)
        print(f"  {group:<15} {groups[group]:>6} ({pct:>5.1f}%)  {bar}")

    # Detailed categories
    if focus in (None, 'compiler'):
        print(f"\n--- Detailed Categories ---")
        for cat in sorted(cats.keys(), key=lambda c: -cats[c]):
            pct = cats[cat] / total * 100
            if pct < 0.1:
                continue
            print(f"  {cat:<30} {cats[cat]:>6} ({pct:>5.1f}%)")

    # Allocation types
    print(f"\n--- Top Allocated Types ---")
    types_sorted = sorted(agg['alloc_types'].items(), key=lambda x: -x[1])[:15]
    for atype, count in types_sorted:
        pct = count / total * 100
        print(f"  {count:>6} ({pct:>5.1f}%)  {atype}")

    # Top allocating methods
    if focus in (None, 'compiler'):
        print(f"\n--- Top Allocating Methods ---")
        methods = sorted(agg['top_allocators'].items(), key=lambda x: -x[1])[:20]
        for method, count in methods:
            pct = count / total * 100
            print(f"  {count:>6} ({pct:>5.1f}%)  {method}")

    print(f"\n{'=' * 72}")


def print_comparison_report(agg_a, agg_b, label_a, label_b):
    """Print side-by-side comparison of two profiles."""
    total_a = agg_a['total_samples']
    total_b = agg_b['total_samples']
    groups_a = compute_groups(agg_a['categories'])
    groups_b = compute_groups(agg_b['categories'])

    print(f"\n{'=' * 72}")
    print(f"MEMORY ALLOCATION COMPARISON")
    print(f"  {label_a}: {total_a} samples")
    print(f"  {label_b}: {total_b} samples")
    delta_pct = (total_a - total_b) / total_b * 100 if total_b > 0 else 0
    delta_sign = '+' if delta_pct > 0 else ''
    print(f"  Delta: {delta_sign}{delta_pct:.1f}% ({'more' if delta_pct > 0 else 'fewer'} allocations)")
    print(f"{'=' * 72}")

    # High-level group comparison
    print(f"\n--- High-Level Breakdown ---")
    print(f"  {'Category':<15} {label_a:>10} {label_b:>10} {'Delta':>10}")
    print(f"  {'-'*15} {'-'*10} {'-'*10} {'-'*10}")

    all_groups = sorted(set(list(groups_a.keys()) + list(groups_b.keys())),
                        key=lambda g: -(groups_a.get(g, 0) + groups_b.get(g, 0)))
    for group in all_groups:
        va = groups_a.get(group, 0)
        vb = groups_b.get(group, 0)
        if va + vb == 0:
            continue
        pa = f"{va} ({va/total_a*100:.0f}%)" if total_a else "0"
        pb = f"{vb} ({vb/total_b*100:.0f}%)" if total_b else "0"
        if vb > 0:
            d = (va - vb) / vb * 100
            ds = f"{'+' if d > 0 else ''}{d:.0f}%"
        else:
            ds = "n/a"
        print(f"  {group:<15} {pa:>10} {pb:>10} {ds:>10}")

    # Detailed category comparison
    print(f"\n--- Detailed Categories ---")
    print(f"  {'Category':<30} {label_a:>8} {label_b:>8} {'Delta':>8}")
    print(f"  {'-'*30} {'-'*8} {'-'*8} {'-'*8}")

    all_cats = sorted(
        set(list(agg_a['categories'].keys()) + list(agg_b['categories'].keys())),
        key=lambda c: -(agg_a['categories'].get(c, 0) + agg_b['categories'].get(c, 0))
    )
    for cat in all_cats:
        va = agg_a['categories'].get(cat, 0)
        vb = agg_b['categories'].get(cat, 0)
        if va + vb == 0:
            continue
        pa = va / total_a * 100 if total_a else 0
        pb = vb / total_b * 100 if total_b else 0
        if pa < 0.5 and pb < 0.5:
            continue
        if vb > 0:
            d = (va - vb) / vb * 100
            ds = f"{'+' if d > 0 else ''}{d:.0f}%"
        elif va > 0:
            ds = "new"
        else:
            ds = ""
        print(f"  {cat:<30} {va:>8} {vb:>8} {ds:>8}")

    # Allocation type comparison
    print(f"\n--- Top Allocated Types ---")
    print(f"  {'Type':<20} {label_a:>8} {label_b:>8} {'Delta':>8}")
    print(f"  {'-'*20} {'-'*8} {'-'*8} {'-'*8}")

    all_types = sorted(
        set(list(agg_a['alloc_types'].keys()) + list(agg_b['alloc_types'].keys())),
        key=lambda t: -(agg_a['alloc_types'].get(t, 0) + agg_b['alloc_types'].get(t, 0))
    )
    for atype in all_types[:15]:
        va = agg_a['alloc_types'].get(atype, 0)
        vb = agg_b['alloc_types'].get(atype, 0)
        if vb > 0:
            d = (va - vb) / vb * 100
            ds = f"{'+' if d > 0 else ''}{d:.0f}%"
        elif va > 0:
            ds = "new"
        else:
            ds = ""
        print(f"  {atype:<20} {va:>8} {vb:>8} {ds:>8}")

    # Insights
    print(f"\n--- Insights ---")
    if delta_pct < -10:
        print(f"\n  [GOOD] {label_a} allocates {abs(delta_pct):.0f}% fewer objects than {label_b}.")
        print(f"    Less allocation pressure means less GC work and potentially lower pause times.")
    elif delta_pct > 10:
        print(f"\n  [WARN] {label_a} allocates {delta_pct:.0f}% more objects than {label_b}.")
        print(f"    More allocations increase GC pressure. Check which categories grew.")
    else:
        print(f"\n  [OK] Allocation counts are within 10% — roughly equivalent memory behavior.")

    # Check specific areas
    compiler_a = sum(v for k, v in agg_a['categories'].items() if k.startswith('compiler.'))
    compiler_b = sum(v for k, v in agg_b['categories'].items() if k.startswith('compiler.'))
    if compiler_b > 0:
        compiler_delta = (compiler_a - compiler_b) / compiler_b * 100
        if abs(compiler_delta) > 15:
            sign = "more" if compiler_delta > 0 else "fewer"
            print(f"\n  [INFO] Compiler allocations: {label_a} has {abs(compiler_delta):.0f}% {sign} than {label_b}.")
            # Find biggest category difference
            biggest_diff = None
            biggest_diff_val = 0
            for cat in all_cats:
                if not cat.startswith('compiler.'):
                    continue
                va = agg_a['categories'].get(cat, 0)
                vb = agg_b['categories'].get(cat, 0)
                diff = abs(va - vb)
                if diff > biggest_diff_val:
                    biggest_diff_val = diff
                    biggest_diff = cat
            if biggest_diff:
                va = agg_a['categories'].get(biggest_diff, 0)
                vb = agg_b['categories'].get(biggest_diff, 0)
                print(f"    Biggest difference: {biggest_diff} ({va} vs {vb})")

    backend_a = agg_a['categories'].get('compiler.backend', 0)
    backend_b = agg_b['categories'].get('compiler.backend', 0)
    if backend_b > 0 and backend_a / max(backend_b, 1) > 1.3:
        print(f"\n  [INFO] Backend allocations are {(backend_a/backend_b - 1)*100:.0f}% higher in {label_a}.")
        print(f"    This suggests more bytecode generation — macros may produce larger ASTs.")

    print(f"\n{'=' * 72}")


def output_json(agg_a, agg_b=None, label_a="a", label_b="b"):
    """Output results as JSON."""
    groups_a = compute_groups(agg_a['categories'])

    result = {
        'profiles': [{
            'label': label_a,
            'total_samples': agg_a['total_samples'],
            'groups': groups_a,
            'categories': agg_a['categories'],
            'top_alloc_types': [
                {'type': t, 'samples': c}
                for t, c in sorted(agg_a['alloc_types'].items(), key=lambda x: -x[1])[:15]
            ],
        }]
    }

    if agg_b:
        groups_b = compute_groups(agg_b['categories'])
        result['profiles'].append({
            'label': label_b,
            'total_samples': agg_b['total_samples'],
            'groups': groups_b,
            'categories': agg_b['categories'],
            'top_alloc_types': [
                {'type': t, 'samples': c}
                for t, c in sorted(agg_b['alloc_types'].items(), key=lambda x: -x[1])[:15]
            ],
        })
        total_a = agg_a['total_samples']
        total_b = agg_b['total_samples']
        result['comparison'] = {
            'delta_samples': total_a - total_b,
            'delta_percent': (total_a - total_b) / total_b * 100 if total_b else 0,
        }

    print(json.dumps(result, indent=2))


def main():
    parser = argparse.ArgumentParser(
        description='Analyze async-profiler allocation profiles from Scala compilation'
    )
    parser.add_argument('file', help='Collapsed stacks file from async-profiler (event=alloc)')
    parser.add_argument('--compare', metavar='FILE',
                        help='Second profile to compare against')
    parser.add_argument('--labels', nargs=2, metavar=('A', 'B'),
                        default=['profile-A', 'profile-B'],
                        help='Labels for the two profiles (default: profile-A profile-B)')
    parser.add_argument('--json', action='store_true',
                        help='Output as JSON instead of human-readable report')
    parser.add_argument('--focus', choices=['compiler', 'types'],
                        help='Focus report on a specific area')

    args = parser.parse_args()

    entries_a = parse_collapsed(args.file)
    if not entries_a:
        print("No allocation data found in input.", file=sys.stderr)
        print("Make sure to use async-profiler with event=alloc and collapsed format.", file=sys.stderr)
        sys.exit(1)

    agg_a = aggregate(entries_a)

    if args.compare:
        entries_b = parse_collapsed(args.compare)
        if not entries_b:
            print(f"No allocation data found in comparison file: {args.compare}", file=sys.stderr)
            sys.exit(1)
        agg_b = aggregate(entries_b)

        if args.json:
            output_json(agg_a, agg_b, args.labels[0], args.labels[1])
        else:
            print_comparison_report(agg_a, agg_b, args.labels[0], args.labels[1])
    else:
        if args.json:
            output_json(agg_a, label_a=args.labels[0])
        else:
            print_single_report(agg_a, label=args.labels[0], focus=args.focus)


if __name__ == '__main__':
    main()
