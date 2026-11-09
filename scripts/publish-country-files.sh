#!/usr/bin/env bash
# Publishes the country files in a folder on the "countries" Release, replacing older copies and
# removing files for countries that are no longer built. The Release is never marked latest, so
# the newest app version stays at the top of the Releases page.
# Usage: publish-country-files.sh folder     (needs GH_TOKEN, GITHUB_REPOSITORY)
set -euo pipefail
dir="$1"
tag="countries"
notes="$(mktemp)"
python3 - "$dir/countries.json" > "$notes" <<'PY'
import json, sys
rows = json.load(open(sys.argv[1]))
print("Files of packaged products for barcode scanning in Fitness App, one per country. They are not the app itself: the app is under the newest version above.")
print()
print("**How to use one:** on the phone, tap your country's file below to download it. In the app, go to Foods, then \"Add a country file\", and pick the downloaded file. Add as many countries as you like. Download them again now and then for newer products.")
print()
print("Product data: [Open Food Facts](https://world.openfoodfacts.org), by its contributors, under the [Open Database License](https://opendatacommons.org/licenses/odbl/1-0/). The United States file also has USDA FoodData Central's Branded Foods (public domain). Carbs are given without fibre, as on EU labels.")
print()
print("| Country | Products | File size |")
print("|---|---:|---:|")
for r in sorted(rows, key=lambda r: r["name"]):
    print("| %s | %s | %.1f MB |" % (r["name"], format(r["products"], ","), r["bytes"] / 1e6))
PY
if ! gh release view "$tag" --repo "$GITHUB_REPOSITORY" > /dev/null 2>&1; then
  gh release create "$tag" --repo "$GITHUB_REPOSITORY" --title "Country files for barcode scanning" \
    --notes-file "$notes" --latest=false --target "${GITHUB_SHA:-main}"
else
  gh release edit "$tag" --repo "$GITHUB_REPOSITORY" --title "Country files for barcode scanning" --notes-file "$notes" --latest=false
fi
gh release upload "$tag" "$dir"/products-*.tsv.gz --repo "$GITHUB_REPOSITORY" --clobber
# Remove files for countries no longer built.
for old in $(gh release view "$tag" --repo "$GITHUB_REPOSITORY" --json assets --jq '.assets[].name'); do
  [ -f "$dir/$old" ] || gh release delete-asset "$tag" "$old" --repo "$GITHUB_REPOSITORY" --yes
done
echo "Published $(ls "$dir"/products-*.tsv.gz | wc -l) country files."
