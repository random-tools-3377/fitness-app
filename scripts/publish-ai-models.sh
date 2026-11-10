#!/usr/bin/env bash
# Publishes the AI model files in a folder on the "ai-models" Release, replacing older copies. The
# Release is never marked latest, so the newest app version stays at the top of the Releases page.
# Usage: publish-ai-models.sh folder     (needs GH_TOKEN, GITHUB_REPOSITORY)
set -euo pipefail
dir="$1"
tag="ai-models"
notes="$(mktemp)"
cat > "$notes" <<'MD'
Optional AI files for Fitness App. They are not the app itself: the app is under the newest version above, and it works fully without these.

Each one runs only on the phone, with no internet. Download a file on the phone, then open it in the app under **Settings → Features → AI on this phone**, after switching that part on.

| File | What it does | Size |
|---|---|---:|
MD
for f in "$dir"/fitness-ai-*.zip; do
  name="$(basename "$f")"
  case "$name" in
    fitness-ai-labels.zip) what="Better label reading: reads more of a nutrition label's numbers right." ;;
    fitness-ai-text.zip) what="Describe a meal, paste a recipe, and the weekly summary in words." ;;
    fitness-ai-speech.zip) what="Say a meal (needs the meals file too)." ;;
    fitness-ai-plate.zip) what="Foods from a photo of a plate." ;;
    *) what="" ;;
  esac
  printf '| %s | %s | %.0f MB |\n' "$name" "$what" "$(echo "$(stat -c %s "$f") / 1000000" | bc -l)" >> "$notes"
done
cat >> "$notes" <<'MD'

**Credits.** Meals, recipes and summaries: Qwen3 1.7B, by the Qwen team, Alibaba Cloud. Speech: Qwen3-ASR 0.6B, by the Qwen team, Alibaba Cloud. Plate photos: SmolVLM 500M Instruct, by Hugging Face. All under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0), run on the phone with [llama.cpp](https://github.com/ggml-org/llama.cpp) (MIT). Label reading: PaddleOCR's PP-OCRv6 small text detection and recognition models, copyright Baidu and/or the PaddleOCR rights holders, under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0), converted to ONNX by [RapidOCR](https://github.com/RapidAI/RapidOCR) (Apache License 2.0). The licence and notices are inside the file.
MD
if ! gh release view "$tag" --repo "$GITHUB_REPOSITORY" > /dev/null 2>&1; then
  gh release create "$tag" --repo "$GITHUB_REPOSITORY" --title "AI models (optional)" \
    --notes-file "$notes" --latest=false --target "${GITHUB_SHA:-main}"
else
  gh release edit "$tag" --repo "$GITHUB_REPOSITORY" --title "AI models (optional)" --notes-file "$notes" --latest=false
fi
gh release upload "$tag" "$dir"/fitness-ai-*.zip --repo "$GITHUB_REPOSITORY" --clobber
echo "Published $(ls "$dir"/fitness-ai-*.zip | wc -l) AI files."
