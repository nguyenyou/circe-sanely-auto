#!/usr/bin/env python3
"""Analyze async-profiler collapsed stacks from Scala compilation.

Parses collapsed stack output from async-profiler, categorizes time spent
across JVM subsystems and Scala compiler phases, and generates actionable
optimization insights.

Usage:
  # Capture profile (use JAVA_TOOL_OPTIONS to profile ALL JVMs including zinc):
  rm -rf out/benchmark/sanely
  JAVA_TOOL_OPTIONS="-agentpath:$(brew --prefix async-profiler)/lib/libasyncProfiler.dylib=start,event=cpu,file=/tmp/profile.txt,collapsed" \\
    ./mill --no-server benchmark.sanely.compile

  # Analyze:
  python analyze_jvm_profile.py /tmp/profile.txt
  python analyze_jvm_profile.py /tmp/profile.txt --json
  python analyze_jvm_profile.py /tmp/profile.txt --focus compiler
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
    ('compiler.backend',          lambda f: 'backend/jvm' in f or 'backend.jvm' in f),
    # Core infrastructure
    ('compiler.transform',        lambda f: 'dotc/transform' in f or 'dotc.transform' in f),
    ('compiler.core.types',       lambda f: ('dotc/core/Types' in f or 'dotc.core.Types' in f)),
    ('compiler.core.symbols',     lambda f: ('dotc/core/Symbols' in f or 'dotc.core.Symbols' in f)),
    ('compiler.core',             lambda f: 'dotc/core' in f or 'dotc.core' in f),
    ('compiler.parsing',          lambda f: 'dotc/parsing' in f or 'dotc.parsing' in f),
    ('compiler.tasty',            lambda f: 'dotc/core/tasty' in f or 'dotc.core.tasty' in f),
    ('compiler.reporting',        lambda f: 'dotc/reporting' in f or 'dotc.reporting' in f),
    ('compiler.ast',              lambda f: 'dotc/ast' in f or 'dotc.ast' in f),
    ('compiler.other',            lambda f: 'dotty' in f or 'dotc' in f),
    # Zinc / incremental
    ('zinc',                      lambda f: 'xsbt' in f or 'zinc' in f or 'sbt/' in f),
]

JVM_RULES = [
    ('jvm.jit',                   lambda f: 'CompileBroker' in f or 'C2Compiler' in f or 'C1Compiler' in f),
    ('jvm.gc',                    lambda f: 'GCTask' in f or 'gc' in f.lower().split(';')[-1] or 'G1' in f),
    ('jvm.classload',             lambda f: 'ClassLoader' in f or 'defineClass' in f),
]

FRAMEWORK_RULES = [
    ('mill',                      lambda f: 'mill/' in f or 'mill.' in f),
    ('coursier',                  lambda f: 'coursier' in f),
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


def find_deepest_method(stack, prefix):
    """Find the deepest frame matching a prefix, return simplified name."""
    frames = stack.split(';')
    for frame in reversed(frames):
        if prefix in frame:
            return frame
    return None


def parse_collapsed(filepath):
    """Parse async-profiler collapsed stacks file."""
    entries = []
    with open(filepath) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            # Format: frame;frame;frame count
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
    """Aggregate entries into categories and extract method-level details."""
    total_samples = sum(count for _, count in entries)
    categories = defaultdict(int)
    compiler_methods = defaultdict(int)
    macro_methods = defaultdict(int)

    for stack, count in entries:
        cat = categorize_stack(stack)
        categories[cat] += count

        # Extract detailed compiler methods
        if cat.startswith('compiler.'):
            method = find_deepest_method(stack, 'dotty') or find_deepest_method(stack, 'scala/quoted')
            if method:
                compiler_methods[method] += count

        # Extract macro-specific methods
        if cat.startswith('compiler.macro'):
            method = find_deepest_method(stack, 'dotty') or find_deepest_method(stack, 'scala/quoted')
            if method:
                macro_methods[method] += count

    return {
        'total_samples': total_samples,
        'categories': dict(categories),
        'compiler_methods': dict(compiler_methods),
        'macro_methods': dict(macro_methods),
    }


def compute_groups(categories):
    """Group categories into high-level buckets."""
    groups = defaultdict(int)
    for cat, count in categories.items():
        prefix = cat.split('.')[0]
        groups[prefix] += count
    return dict(groups)


def generate_insights(agg):
    """Generate actionable insights from the profile data."""
    insights = []
    total = agg['total_samples']
    cats = agg['categories']
    groups = compute_groups(cats)

    # JIT dominance check
    jit = groups.get('jvm', 0)
    jit_pct = jit / total * 100 if total > 0 else 0
    if jit_pct > 50:
        insights.append({
            'severity': 'info',
            'area': 'jvm',
            'finding': f'JIT compilation is {jit_pct:.0f}% of total samples.',
            'explanation': (
                'This JVM is "cold" — the JIT compiler is busy compiling the Scala '
                'compiler itself. This is expected with --no-server mode (fresh JVM each time). '
                'In a long-running build server (Mill daemon), JIT warms up and this overhead disappears. '
                'The remaining compiler samples better represent steady-state performance.'
            ),
            'action': 'For more representative profiling, use Mill daemon mode or run multiple compilations in the same JVM.',
        })

    # GC pressure
    gc = cats.get('jvm.gc', 0)
    gc_pct = gc / total * 100 if total > 0 else 0
    if gc_pct > 10:
        insights.append({
            'severity': 'warning',
            'area': 'jvm',
            'finding': f'GC is {gc_pct:.0f}% of total time.',
            'explanation': 'High GC pressure suggests the compiler is allocating heavily. Macro expansion creates many temporary AST nodes.',
            'action': 'Consider increasing heap size (-Xmx) or using ZGC (-XX:+UseZGC) for lower pause times.',
        })

    # Compiler breakdown (normalized to compiler-only time)
    compiler_total = groups.get('compiler', 0) + groups.get('zinc', 0)
    if compiler_total > 0:
        macro_total = cats.get('compiler.macro.quoted', 0) + cats.get('compiler.macro.inlines', 0)
        implicit_total = cats.get('compiler.typer.implicits', 0)
        typer_total = cats.get('compiler.typer', 0) + implicit_total
        backend_total = cats.get('compiler.backend', 0)
        transform_total = cats.get('compiler.transform', 0)

        macro_pct = macro_total / compiler_total * 100
        implicit_pct = implicit_total / compiler_total * 100
        typer_pct = typer_total / compiler_total * 100
        backend_pct = backend_total / compiler_total * 100

        if macro_pct > 30:
            insights.append({
                'severity': 'high',
                'area': 'macro',
                'finding': f'Macro expansion (inlines + quoted) is {macro_pct:.0f}% of compiler time.',
                'explanation': 'Our macro derivation is a significant portion of compilation. Reducing AST size or caching more aggressively would help.',
                'action': 'Run sanely-profile (SANELY_PROFILE=true) for macro-level breakdown to identify which operations to optimize.',
            })

        if implicit_pct > 20:
            insights.append({
                'severity': 'high',
                'area': 'implicits',
                'finding': f'Implicit search is {implicit_pct:.0f}% of compiler time.',
                'explanation': 'The compiler spends significant time in implicit resolution. This includes our Expr.summonIgnoring calls and any user-facing implicit search.',
                'action': 'Reduce summonIgnoring calls via cross-expansion caching (lazy val emission pattern).',
            })

        if typer_pct > 40:
            insights.append({
                'severity': 'medium',
                'area': 'typer',
                'finding': f'Type checking is {typer_pct:.0f}% of compiler time ({implicit_pct:.0f}% implicits + {typer_pct - implicit_pct:.0f}% other).',
                'explanation': 'The typer does type inference, implicit search, and overload resolution. Some of this is our macro code, some is user code.',
                'action': 'Use compile-trace skill for file-level typer breakdown. Check if specific types are expensive.',
            })

        if backend_pct > 25:
            insights.append({
                'severity': 'medium',
                'area': 'backend',
                'finding': f'Backend (codegen + classfile writing) is {backend_pct:.0f}% of compiler time.',
                'explanation': 'The JVM backend generates bytecode. Large generated ASTs from macros produce more bytecode.',
                'action': 'Reduce generated code size by extracting more logic to SanelyRuntime helper methods.',
            })

    if not insights:
        insights.append({
            'severity': 'info',
            'area': 'general',
            'finding': 'No significant bottlenecks detected in this profile.',
            'explanation': 'The compilation is well-balanced across compiler phases.',
            'action': 'Profile with SANELY_PROFILE=true for macro-level insights.',
        })

    return insights


def print_report(agg, focus=None):
    """Print human-readable report."""
    total = agg['total_samples']
    cats = agg['categories']
    groups = compute_groups(cats)

    print(f"{'=' * 72}")
    print(f"JVM COMPILATION PROFILE ({total} samples)")
    print(f"{'=' * 72}")

    # High-level groups
    print(f"\n--- High-Level Breakdown ---")
    for group in sorted(groups.keys(), key=lambda g: -groups[g]):
        pct = groups[group] / total * 100
        bar = '#' * int(pct / 2)
        print(f"  {group:<15} {groups[group]:>6} ({pct:>5.1f}%)  {bar}")

    # Detailed categories
    print(f"\n--- Detailed Categories ---")
    for cat in sorted(cats.keys(), key=lambda c: -cats[c]):
        pct = cats[cat] / total * 100
        if pct < 0.1:
            continue
        print(f"  {cat:<30} {cats[cat]:>6} ({pct:>5.1f}%)")

    # Compiler methods (top 20)
    if focus in (None, 'compiler') and agg['compiler_methods']:
        print(f"\n--- Top Compiler Methods ---")
        methods = sorted(agg['compiler_methods'].items(), key=lambda x: -x[1])[:20]
        for method, count in methods:
            pct = count / total * 100
            print(f"  {count:>4} ({pct:>4.1f}%)  {method}")

    # Macro methods
    if focus in (None, 'macro') and agg['macro_methods']:
        print(f"\n--- Macro-Related Methods ---")
        methods = sorted(agg['macro_methods'].items(), key=lambda x: -x[1])[:15]
        for method, count in methods:
            pct = count / total * 100
            print(f"  {count:>4} ({pct:>4.1f}%)  {method}")

    # Insights
    insights = generate_insights(agg)
    print(f"\n--- Optimization Insights ---")
    for insight in insights:
        severity = insight['severity'].upper()
        print(f"\n  [{severity}] {insight['finding']}")
        print(f"    Why: {insight['explanation']}")
        print(f"    Action: {insight['action']}")

    print(f"\n{'=' * 72}")


def output_json(agg):
    """Output results as JSON for programmatic consumption."""
    insights = generate_insights(agg)
    groups = compute_groups(agg['categories'])

    result = {
        'summary': {
            'total_samples': agg['total_samples'],
            'groups': groups,
            'categories': agg['categories'],
        },
        'insights': insights,
        'top_compiler_methods': [
            {'method': m, 'samples': c}
            for m, c in sorted(agg['compiler_methods'].items(), key=lambda x: -x[1])[:20]
        ],
        'top_macro_methods': [
            {'method': m, 'samples': c}
            for m, c in sorted(agg['macro_methods'].items(), key=lambda x: -x[1])[:15]
        ],
    }
    print(json.dumps(result, indent=2))


def main():
    parser = argparse.ArgumentParser(
        description='Analyze async-profiler collapsed stacks from Scala compilation'
    )
    parser.add_argument('file', help='Collapsed stacks file from async-profiler')
    parser.add_argument('--json', action='store_true',
                        help='Output as JSON instead of human-readable report')
    parser.add_argument('--focus', choices=['compiler', 'macro', 'jvm'],
                        help='Focus report on a specific area')

    args = parser.parse_args()

    entries = parse_collapsed(args.file)
    if not entries:
        print("No stack data found in input.", file=sys.stderr)
        print("Make sure to use async-profiler with 'collapsed' output format.", file=sys.stderr)
        sys.exit(1)

    agg = aggregate(entries)

    if args.json:
        output_json(agg)
    else:
        print_report(agg, focus=args.focus)


if __name__ == '__main__':
    main()
