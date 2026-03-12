#!/usr/bin/env bash
set -e

# Build the Scala.js output
echo "Building playground..."
cd "$(dirname "$0")/.."
./mill playground.fastLinkJS

# Copy JS output to playground directory
cp out/playground/fastLinkJS.dest/main.js playground/main.js
cp out/playground/fastLinkJS.dest/main.js.map playground/main.js.map

echo ""
echo "Playground built! Open playground/index.html in your browser."
echo ""
echo "Or start a local server:"
echo "  cd playground && python3 -m http.server 8080"
echo "  Then open http://localhost:8080"
