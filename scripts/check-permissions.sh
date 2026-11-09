#!/usr/bin/env bash
# The app has no network access. Fails if the built APK asks for any network permission, or
# for any permission not listed in scripts/allowed-permissions.txt. Libraries can add
# permissions when manifests are merged, which is why this looks at the APK itself.
# Usage: check-permissions.sh app.apk
set -euo pipefail
apk="$1"
here="$(cd "$(dirname "$0")" && pwd)"
aapt2="$("$here/android-tool.sh" aapt2)"

dump="$("$aapt2" dump permissions "$apk")"
echo "$dump"

package="$(printf '%s\n' "$dump" | sed -n 's/^package: *//p' | head -n 1)"
requested="$(printf '%s\n' "$dump" | sed -n "s/^uses-permission[^:]*: name='\([^']*\)'.*/\1/p" | sort -u)"

network_pattern='(^|\.)(INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|CHANGE_WIFI_STATE|CHANGE_WIFI_MULTICAST_STATE|NEARBY_WIFI_DEVICES|BLUETOOTH|BLUETOOTH_ADMIN|BLUETOOTH_CONNECT|BLUETOOTH_SCAN|BLUETOOTH_ADVERTISE|NFC|UWB_RANGING|BIND_VPN_SERVICE|READ_SYNC_SETTINGS|WRITE_SYNC_SETTINGS|GET_ACCOUNTS|MANAGE_ACCOUNTS|USE_CREDENTIALS)$|c2dm|NETWORK|WIFI|BLUETOOTH'

failed=0
network="$(printf '%s\n' "$requested" | grep -E "$network_pattern" || true)"
if [ -n "$network" ]; then
  echo "::error::The APK asks for network permissions, which this app must not have:"
  printf '%s\n' "$network"
  failed=1
fi

allowed="$(sed -e 's/#.*//' -e '/^[[:space:]]*$/d' -e "s/\${applicationId}/$package/" "$here/allowed-permissions.txt" | tr -d ' ' | sort -u)"
unexpected="$(comm -23 <(printf '%s\n' "$requested" | sed '/^$/d') <(printf '%s\n' "$allowed"))"
if [ -n "$unexpected" ]; then
  echo "::error::The APK asks for permissions that are not in scripts/allowed-permissions.txt:"
  printf '%s\n' "$unexpected"
  failed=1
fi

if [ "$failed" != 0 ]; then
  exit 1
fi
echo "Permission check passed: no network permission."
