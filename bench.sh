#!/usr/bin/env bash
set -euo pipefail

N=${1:-5}
echo "Compile-time benchmark: circe-sanely-auto vs circe-generic (N=$N)"
echo "================================================================"

for module in sanely generic; do
  times=()
  for i in $(seq 1 "$N"); do
    rm -rf out/benchmark/$module
    start=$(python3 -c 'import time; print(time.time())')
    ./mill benchmark.$module.compile 2>/dev/null 1>/dev/null
    end=$(python3 -c 'import time; print(time.time())')
    elapsed=$(python3 -c "print(f'{$end - $start:.2f}')")
    times+=("$elapsed")
    echo "  benchmark.$module run $i: ${elapsed}s"
  done

  # compute median
  median=$(printf '%s\n' "${times[@]}" | sort -n | awk -v n="$N" 'NR==int((n+1)/2){print}')
  echo "benchmark.$module median: ${median}s (of ${times[*]})"
  echo ""
done
