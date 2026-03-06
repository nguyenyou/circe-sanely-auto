#!/usr/bin/env bash
set -euo pipefail

WARMUP=${1:-5}
ITERATIONS=${2:-5}

echo "Building runtime benchmark..."
./mill benchmark-runtime.compile 2>/dev/null 1>/dev/null

echo ""
./mill benchmark-runtime.run "$WARMUP" "$ITERATIONS"
