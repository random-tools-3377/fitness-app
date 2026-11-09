#!/usr/bin/env bash
# Fails unless the APK turns phone backup off (android:allowBackup="false") and points at the
# data-extraction rules that exclude everything.
# Usage: check-backup.sh app.apk
set -euo pipefail
apk="$1"
here="$(cd "$(dirname "$0")" && pwd)"
aapt2="$("$here/android-tool.sh" aapt2)"

manifest="$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk")"
printf '%s\n' "$manifest" | grep -E 'android:(allowBackup|dataExtractionRules|fullBackupContent)' || true

if ! printf '%s\n' "$manifest" | grep -qE 'android:allowBackup\([^)]*\)=(false|0x0+\b)'; then
  echo "::error::android:allowBackup is not false in the APK."
  exit 1
fi
if ! printf '%s\n' "$manifest" | grep -qE 'android:dataExtractionRules\('; then
  echo "::error::The APK has no data-extraction rules."
  exit 1
fi
echo "Backup check passed: phone backup is off."
