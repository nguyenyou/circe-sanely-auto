#!/usr/bin/env bash
set -euo pipefail

# Parse arguments
BENCH_TYPE="benchmark"
N=""
for arg in "$@"; do
  case "$arg" in
    --configured) BENCH_TYPE="benchmark-configured" ;;
    *) N="$arg" ;;
  esac
done
N=${N:-5}

if ! [[ "$N" =~ ^[0-9]+$ ]] || [ "$N" -lt 1 ]; then
  echo "Expected a positive integer run count, got: $N" >&2
  exit 1
fi

if ! command -v hyperfine &>/dev/null; then
  echo "hyperfine is required: brew install hyperfine" >&2
  exit 1
fi

case "$BENCH_TYPE" in
  benchmark)
    BASELINE_LABEL="circe-generic"
    PREP_TARGETS=(
      "sanely.jvm.compile"
    )
    ;;
  benchmark-configured)
    BASELINE_LABEL="circe-core configured derivation"
    PREP_TARGETS=(
      "sanely.jvm.compile"
      "benchmark-configured.generic-compat.compile"
    )
    ;;
  *)
    echo "Unknown benchmark suite: $BENCH_TYPE" >&2
    exit 1
    ;;
esac

echo "Compile-time benchmark: circe-sanely-auto vs $BASELINE_LABEL (N=$N)"
echo "Benchmark suite: $BENCH_TYPE"
echo "Method: Mill daemon, hyperfine with --warmup 1, --runs $N"
echo "================================================================"

# Warm up Mill daemon + compile source dependencies (untimed)
echo "Warming up Mill daemon and source dependencies..."
for target in "${PREP_TARGETS[@]}"; do
  ./mill "$target" >/dev/null 2>/dev/null
done

echo "Running hyperfine benchmark..."
echo ""

hyperfine \
  --warmup 1 \
  --runs "$N" \
  --prepare "rm -rf out/$BENCH_TYPE/sanely" \
  --command-name "$BENCH_TYPE.sanely" \
  "./mill $BENCH_TYPE.sanely.compile" \
  --prepare "rm -rf out/$BENCH_TYPE/generic" \
  --command-name "$BENCH_TYPE.generic" \
  "./mill $BENCH_TYPE.generic.compile"
