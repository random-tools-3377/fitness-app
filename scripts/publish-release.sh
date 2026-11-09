#!/usr/bin/env bash
# Publishes the signed APK as a GitHub Release for the version in gradle.properties, with the
# notes in release-notes/<version>.md. Does nothing if that version already has a Release, so
# a push that doesn't bump the version publishes nothing.
# Usage: publish-release.sh signed.apk     (needs GH_TOKEN, GITHUB_REPOSITORY, GITHUB_SHA)
set -euo pipefail
apk="$1"
here="$(cd "$(dirname "$0")" && pwd)"
version="$(sed -n 's/^appVersionName=//p' gradle.properties | tr -d '[:space:]')"
tag="v$version"
notes="release-notes/$version.md"

if gh release view "$tag" --repo "$GITHUB_REPOSITORY" > /dev/null 2>&1; then
  echo "Release $tag already exists. Raise appVersionName in gradle.properties to publish a new one."
  exit 0
fi
# Never publish an older version after a newer one, so the newest stays on top of the list.
newest="$(gh release list --repo "$GITHUB_REPOSITORY" --limit 100 --json tagName --jq '.[].tagName' | sed -n 's/^v//p' | sort -V | tail -n 1)"
if [ -n "$newest" ] && [ "$(printf '%s\n%s\n' "$newest" "$version" | sort -V | tail -n 1)" != "$version" ]; then
  echo "Not publishing $version: $newest is already out."
  exit 0
fi
if [ ! -s "$notes" ]; then
  echo "::error::$notes is missing. Every Release needs notes."
  exit 1
fi

aapt2="$("$here/android-tool.sh" aapt2)"
apk_version="$("$aapt2" dump badging "$apk" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -n 1)"
if [ "$apk_version" != "$version" ]; then
  echo "::error::The APK says version '$apk_version' but gradle.properties says '$version'."
  exit 1
fi

asset="$(dirname "$apk")/fitness-$version.apk"
cp "$apk" "$asset"
gh release create "$tag" "$asset" \
  --repo "$GITHUB_REPOSITORY" \
  --target "$GITHUB_SHA" \
  --title "Fitness App $version" \
  --notes-file "$notes"
echo "Published $tag"
