#!/usr/bin/env bash
#
# Zinc incremental compilation test for circe-sanely-auto macros.
#
# Verifies that when types change, zinc properly recompiles dependent files
# so macro expansions produce correct codecs. If zinc failed to invalidate,
# the old macro-generated codec would produce wrong JSON.
#
# Usage: bash test-zinc.sh
#
set -euo pipefail

SRCDIR="zinc-test/src/zinctest"
CHANGES="zinc-test/changes"
PASSED=0
FAILED=0

green() { printf "\033[32m%s\033[0m\n" "$1"; }
red()   { printf "\033[31m%s\033[0m\n" "$1"; }
bold()  { printf "\033[1m%s\033[0m\n" "$1"; }

run_and_capture() {
  ./mill zinc-test.run 2>/dev/null
}

check_output() {
  local label="$1" pattern="$2" output="$3"
  if echo "$output" | grep -qF "$pattern"; then
    PASSED=$((PASSED + 1))
    return 0
  else
    FAILED=$((FAILED + 1))
    red "  FAIL: $label"
    red "    expected to contain: $pattern"
    red "    actual output:"
    echo "$output" | sed 's/^/      /'
    return 1
  fi
}

check_not_output() {
  local label="$1" pattern="$2" output="$3"
  if echo "$output" | grep -qF "$pattern"; then
    FAILED=$((FAILED + 1))
    red "  FAIL: $label"
    red "    should NOT contain: $pattern"
    return 1
  else
    PASSED=$((PASSED + 1))
    return 0
  fi
}

# Save originals for restoration
save_originals() {
  cp "$SRCDIR/Address.scala" "$SRCDIR/Address.scala.orig"
  cp "$SRCDIR/User.scala" "$SRCDIR/User.scala.orig"
  cp "$SRCDIR/Color.scala" "$SRCDIR/Color.scala.orig"
  cp "$SRCDIR/Codecs.scala" "$SRCDIR/Codecs.scala.orig"
  cp "$SRCDIR/Main.scala" "$SRCDIR/Main.scala.orig"
}

restore_originals() {
  cp "$SRCDIR/Address.scala.orig" "$SRCDIR/Address.scala"
  cp "$SRCDIR/User.scala.orig" "$SRCDIR/User.scala"
  cp "$SRCDIR/Color.scala.orig" "$SRCDIR/Color.scala"
  cp "$SRCDIR/Codecs.scala.orig" "$SRCDIR/Codecs.scala"
  cp "$SRCDIR/Main.scala.orig" "$SRCDIR/Main.scala"
}

cleanup() {
  restore_originals 2>/dev/null || true
  rm -f "$SRCDIR"/*.orig
}
trap cleanup EXIT

bold "=== Zinc Incremental Compilation Tests ==="
echo ""

# Clean build first
rm -rf out/zinc-test
save_originals

# --- Baseline ---
bold "[baseline] Initial compile + run"
OUTPUT=$(run_and_capture)
check_output "Address encoding" '"street":"123 Main St","city":"Springfield"' "$OUTPUT" || true
check_output "Address roundtrip" 'ADDR_DEC:Address(123 Main St,Springfield)' "$OUTPUT" || true
check_output "User encoding" '"name":"Alice","age":30,"address":{' "$OUTPUT" || true
check_output "User roundtrip" 'USER_DEC:User(Alice,30,Address(123 Main St,Springfield))' "$OUTPUT" || true
check_output "Color encoding" 'COLOR_ENC:{"Red":{}}' "$OUTPUT" || true
check_output "Color roundtrip" 'COLOR_DEC:Red' "$OUTPUT" || true
echo ""

# --- Step 1: Add field to Address ---
bold "[step1] Add zip field to Address"
cp "$CHANGES/step1-add-field/Address.scala" "$SRCDIR/Address.scala"
cp "$CHANGES/step1-add-field/Main.scala" "$SRCDIR/Main.scala"
OUTPUT=$(run_and_capture)
check_output "Address has zip" '"zip":"62704"' "$OUTPUT" || true
check_output "Address roundtrip with zip" 'ADDR_DEC:Address(123 Main St,Springfield,62704)' "$OUTPUT" || true
check_output "User nested address has zip" '"zip":"62704"' "$OUTPUT" || true
check_output "User roundtrip with zip" 'USER_DEC:User(Alice,30,Address(123 Main St,Springfield,62704))' "$OUTPUT" || true
restore_originals
echo ""

# --- Step 2: Add enum variant ---
bold "[step2] Add Yellow variant to Color"
cp "$CHANGES/step2-add-variant/Color.scala" "$SRCDIR/Color.scala"
cp "$CHANGES/step2-add-variant/Main.scala" "$SRCDIR/Main.scala"
OUTPUT=$(run_and_capture)
check_output "Yellow encodes" 'COLOR_ENC:{"Yellow":{}}' "$OUTPUT" || true
check_output "Yellow roundtrip" 'COLOR_DEC:Yellow' "$OUTPUT" || true
restore_originals
echo ""

# --- Step 3: Custom encoder takes priority ---
bold "[step3] Custom Encoder[User] in Codecs"
cp "$CHANGES/step3-custom-encoder/Codecs.scala" "$SRCDIR/Codecs.scala"
OUTPUT=$(run_and_capture)
check_output "Custom encoder used" '"custom":true' "$OUTPUT" || true
check_not_output "No age field" '"age":30' "$OUTPUT" || true
restore_originals
echo ""

# --- Step 4: Rename field in nested type ---
bold "[step4] Rename street -> streetName in Address"
cp "$CHANGES/step4-change-nested/Address.scala" "$SRCDIR/Address.scala"
cp "$CHANGES/step4-change-nested/Main.scala" "$SRCDIR/Main.scala"
OUTPUT=$(run_and_capture)
check_output "streetName in Address" '"streetName":"123 Main St"' "$OUTPUT" || true
check_not_output "No old street key" '"street":"123 Main St"' "$OUTPUT" || true
check_output "streetName in User nested" '"streetName":"123 Main St"' "$OUTPUT" || true
check_output "User roundtrip" 'USER_DEC:User(Alice,30,Address(123 Main St,Springfield))' "$OUTPUT" || true
restore_originals
echo ""

# --- Step 5: Remove field ---
bold "[step5] Remove city from Address"
cp "$CHANGES/step5-remove-field/Address.scala" "$SRCDIR/Address.scala"
cp "$CHANGES/step5-remove-field/Main.scala" "$SRCDIR/Main.scala"
OUTPUT=$(run_and_capture)
check_output "Address without city" 'ADDR_ENC:{"street":"123 Main St"}' "$OUTPUT" || true
check_not_output "No city field" '"city"' "$OUTPUT" || true
check_output "User nested without city" '"address":{"street":"123 Main St"}' "$OUTPUT" || true
restore_originals
echo ""

# --- Summary ---
bold "=== Results ==="
TOTAL=$((PASSED + FAILED))
if [ "$FAILED" -eq 0 ]; then
  green "All $TOTAL checks passed."
else
  red "$FAILED/$TOTAL checks failed."
  exit 1
fi
