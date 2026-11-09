#!/usr/bin/env bash
# Signs the release APK with the app's one release key, read from the repository secrets:
#   SIGNING_KEYSTORE_B64    base64 of the PKCS12 keystore
#   SIGNING_STORE_PASSWORD  keystore password
#   SIGNING_KEY_ALIAS       key alias
#   SIGNING_KEY_PASSWORD    key password
# The keystore only exists in a temporary folder for the length of this script.
# Usage: sign-apk.sh unsigned.apk signed.apk
set -euo pipefail
in="$1"
out="$2"

for name in SIGNING_KEYSTORE_B64 SIGNING_STORE_PASSWORD SIGNING_KEY_ALIAS SIGNING_KEY_PASSWORD; do
  if [ -z "${!name:-}" ]; then
    echo "::error::The repository secret $name is not set, so the release APK can't be signed."
    exit 1
  fi
done

here="$(cd "$(dirname "$0")" && pwd)"
zipalign="$("$here/android-tool.sh" zipalign)"
apksigner="$("$here/android-tool.sh" apksigner)"

work="$(mktemp -d "${RUNNER_TEMP:-/tmp}/sign.XXXXXX")"
trap 'rm -rf "$work"' EXIT
chmod 700 "$work"

printf '%s' "$SIGNING_KEYSTORE_B64" | tr -d ' \r\n' | base64 --decode > "$work/release.p12"
# The build already aligns the APK, including the 16 KB page alignment newer phones need for
# native libraries. Only re-align if that check fails.
if "$zipalign" -c -P 16 4 "$in" > /dev/null 2>&1; then
  cp "$in" "$work/aligned.apk"
else
  echo "Aligning the APK."
  "$zipalign" -P 16 -f 4 "$in" "$work/aligned.apk"
fi
"$apksigner" sign \
  --ks "$work/release.p12" \
  --ks-type PKCS12 \
  --ks-pass env:SIGNING_STORE_PASSWORD \
  --ks-key-alias "$SIGNING_KEY_ALIAS" \
  --key-pass env:SIGNING_KEY_PASSWORD \
  --out "$out" \
  "$work/aligned.apk"
echo "Signed $out"
