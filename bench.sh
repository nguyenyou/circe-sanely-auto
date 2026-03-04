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

echo "Compile-time benchmark: circe-sanely-auto vs circe-generic (N=$N)"
echo "Benchmark suite: $BENCH_TYPE"
echo "================================================================"

for module in sanely generic; do
  times=()
  for i in $(seq 1 "$N"); do
    rm -rf "out/$BENCH_TYPE/$module"
    start=$(python3 -c 'import time; print(time.time())')
    ./mill "$BENCH_TYPE.$module.compile" 2>/dev/null 1>/dev/null
    end=$(python3 -c 'import time; print(time.time())')
    elapsed=$(python3 -c "print(f'{$end - $start:.2f}')")
    times+=("$elapsed")
    echo "  $BENCH_TYPE.$module run $i: ${elapsed}s"
  done

  # compute median
  median=$(printf '%s\n' "${times[@]}" | sort -n | awk -v n="$N" 'NR==int((n+1)/2){print}')
  echo "$BENCH_TYPE.$module median: ${median}s (of ${times[*]})"
  echo ""
done
