#!/usr/bin/env python3
"""Analyze SANELY_PROFILE output from circe-sanely-auto macro expansion.

Usage:
  # From a file:
  python analyze_profile.py profile_output.txt

  # From stdin (pipe compilation stderr):
  SANELY_PROFILE=true ./mill --no-server benchmark.sanely.compile 2>&1 | python analyze_profile.py

  # With options:
  python analyze_profile.py profile_output.txt --top 20 --sort summonIgnoring --kind Encoder
"""

import sys
import re
import argparse
import json
from collections import defaultdict


def parse_profile_lines(lines):
    """Parse [PROFILE] lines into structured data."""
    expansions = []

    for line in lines:
        # Extract profile line content after [PROFILE] prefix
        m = re.search(r'\[PROFILE\]\s+(\w+)\[(.+?)\]:\s+(.+)', line)
        if not m:
            continue

        kind = m.group(1)       # Encoder, Decoder, CfgEncoder, CfgDecoder
        type_name = m.group(2)  # e.g., benchmark.domain7.Ticket
        rest = m.group(3)

        entry = {
            'kind': kind,
            'type': type_name,
            'short_type': type_name.rsplit('.', 1)[-1],
            'categories': {}
        }

        # Parse total=Xms
        total_m = re.search(r'total=([\d.]+)ms', rest)
        if total_m:
            entry['total_ms'] = float(total_m.group(1))

        # Parse category=Xms(Nx) pairs
        for cat_m in re.finditer(r'(\w+)=([\d.]+)ms\((\d+)x\)', rest):
            cat_name = cat_m.group(1)
            entry['categories'][cat_name] = {
                'ms': float(cat_m.group(2)),
                'calls': int(cat_m.group(3))
            }

        expansions.append(entry)

    return expansions


def aggregate(expansions):
    """Compute aggregate statistics across all expansions."""
    total_ms = sum(e.get('total_ms', 0) for e in expansions)
    n = len(expansions)

    cats = defaultdict(lambda: {'ms': 0.0, 'calls': 0})
    for e in expansions:
        for cat, data in e['categories'].items():
            cats[cat]['ms'] += data['ms']
            cats[cat]['calls'] += data['calls']

    # Count by kind
    kinds = defaultdict(int)
    kind_ms = defaultdict(float)
    for e in expansions:
        kinds[e['kind']] += 1
        kind_ms[e['kind']] += e.get('total_ms', 0)

    return {
        'total_expansions': n,
        'total_ms': total_ms,
        'avg_ms': total_ms / n if n > 0 else 0,
        'categories': dict(cats),
        'kinds': dict(kinds),
        'kind_ms': dict(kind_ms),
    }


def print_report(expansions, agg, top_n=15, sort_by='total', kind_filter=None):
    """Print a human-readable profiling report."""

    filtered = expansions
    if kind_filter:
        filtered = [e for e in filtered if e['kind'] == kind_filter]
        if not filtered:
            print(f"No expansions found for kind '{kind_filter}'")
            return

    n = agg['total_expansions']
    total = agg['total_ms']

    print(f"{'=' * 70}")
    print(f"SANELY MACRO PROFILE ({n} expansions, {total:.0f}ms total)")
    print(f"{'=' * 70}")

    # Kind breakdown
    print(f"\n--- By Kind ---")
    for kind, count in sorted(agg['kinds'].items()):
        ms = agg['kind_ms'][kind]
        print(f"  {kind:<15} {count:>4} expansions  {ms:>8.1f}ms  avg {ms/count:.2f}ms")

    # Category breakdown
    print(f"\n--- Category Breakdown ---")
    cats = agg['categories']
    for cat in sorted(cats.keys(), key=lambda c: -cats[c]['ms']):
        data = cats[cat]
        pct = data['ms'] / total * 100 if total > 0 else 0
        avg = data['ms'] / data['calls'] if data['calls'] > 0 else 0
        if cat == 'cacheHit':
            print(f"  {cat:<20} {'':>10}  {data['calls']:>6} hits")
        else:
            print(f"  {cat:<20} {data['ms']:>8.1f}ms ({pct:>5.1f}%)  {data['calls']:>6} calls  avg {avg:.2f}ms")

    unaccounted = total - sum(d['ms'] for c, d in cats.items() if c != 'cacheHit')
    if total > 0:
        print(f"  {'overhead':<20} {unaccounted:>8.1f}ms ({unaccounted/total*100:>5.1f}%)  (type checks, AST, etc)")

    # Top N slowest
    if sort_by in ('total', 'total_ms'):
        key = lambda e: e.get('total_ms', 0)
        sort_label = 'total time'
    else:
        key = lambda e: e['categories'].get(sort_by, {}).get('ms', 0)
        sort_label = f'{sort_by} time'

    ranked = sorted(filtered, key=key, reverse=True)[:top_n]

    print(f"\n--- Top {top_n} Slowest ({sort_label}) ---")
    for i, e in enumerate(ranked, 1):
        cats_str = ' '.join(
            f"{c}={d['ms']:.1f}ms({d['calls']}x)"
            for c, d in sorted(e['categories'].items(), key=lambda x: -x[1]['ms'])
        )
        print(f"  {i:>2}. {e['kind']}[{e['short_type']}]: total={e.get('total_ms', 0):.1f}ms  {cats_str}")

    # Hot types summary
    hot = [e for e in filtered if e.get('total_ms', 0) > 50]
    if hot:
        print(f"\n--- Hot Types (>50ms) ---")
        for e in sorted(hot, key=lambda e: e.get('total_ms', 0), reverse=True)[:5]:
            print(f"  {e['kind']}[{e['short_type']}]: {e['total_ms']:.0f}ms")

    print(f"{'=' * 70}")


def output_json(expansions, agg):
    """Output results as JSON for programmatic consumption."""
    result = {
        'summary': agg,
        'top_slowest': [
            {
                'kind': e['kind'],
                'type': e['type'],
                'total_ms': e.get('total_ms', 0),
                'categories': e['categories']
            }
            for e in sorted(expansions, key=lambda e: e.get('total_ms', 0), reverse=True)[:20]
        ]
    }
    print(json.dumps(result, indent=2))


def main():
    parser = argparse.ArgumentParser(
        description='Analyze SANELY_PROFILE macro expansion data'
    )
    parser.add_argument('file', nargs='?', default='-',
                        help='Profile output file (default: stdin)')
    parser.add_argument('--top', type=int, default=15,
                        help='Number of slowest expansions to show (default: 15)')
    parser.add_argument('--sort', default='total',
                        choices=['total', 'summonIgnoring', 'summonMirror', 'derive', 'subTraitDetect'],
                        help='Sort top expansions by category (default: total)')
    parser.add_argument('--kind', default=None,
                        choices=['Encoder', 'Decoder', 'CfgEncoder', 'CfgDecoder'],
                        help='Filter to specific derivation kind')
    parser.add_argument('--json', action='store_true',
                        help='Output as JSON instead of human-readable report')

    args = parser.parse_args()

    if args.file == '-':
        lines = sys.stdin.readlines()
    else:
        with open(args.file) as f:
            lines = f.readlines()

    expansions = parse_profile_lines(lines)

    if not expansions:
        print("No [PROFILE] data found in input.", file=sys.stderr)
        print("Make sure to compile with SANELY_PROFILE=true", file=sys.stderr)
        sys.exit(1)

    agg = aggregate(expansions)

    if args.json:
        output_json(expansions, agg)
    else:
        print_report(expansions, agg, top_n=args.top, sort_by=args.sort, kind_filter=args.kind)


if __name__ == '__main__':
    main()
