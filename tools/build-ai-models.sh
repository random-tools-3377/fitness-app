#!/usr/bin/env bash
# Builds the larger AI model files for Fitness App (0.18.0 onwards) from Hugging Face, each pinned
# to a revision and checked against its SHA-256 (see tools/ai-check/models.sh):
#   fitness-ai-text.zip    Qwen3 1.7B (Apache-2.0): meals, recipes and the weekly summary
#   fitness-ai-speech.zip  Qwen3-ASR 0.6B (Apache-2.0): saying a meal
#   fitness-ai-plate.zip   SmolVLM 500M Instruct (Apache-2.0): photos of a plate
# Each holds the model as model.gguf (and mmproj.gguf for photos and speech), a manifest, the
# licence and a README with the credits. Stored without compression, as the models don't shrink.
# Usage: build-ai-models.sh OUT
set -euo pipefail
out="$1"
here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=/dev/null
source "$here/ai-check/models.sh"
work="$(mktemp -d)"
mkdir -p "$out"
curl -sSfL --retry 3 -o "$work/LICENSE.txt" https://www.apache.org/licenses/LICENSE-2.0.txt

fetch() {  # fetch REPO REV FILE SHA DEST
  curl -sSfL --retry 3 -o "$5" "https://huggingface.co/$1/resolve/$2/$3"
  echo "$4  $5" | sha256sum -c --quiet -
}

pack() {  # pack ID TITLE CREDIT DIR
  local id="$1" dir="$4"
  printf '{"id":"%s","version":1,"title":"%s"}\n' "$id" "$2" > "$dir/manifest.json"
  cp "$work/LICENSE.txt" "$dir/LICENSE.txt"
  printf '%s\n\nOpen this file in Fitness App: Settings, Features, AI on this phone.\n\n%s\n' "$2 for Fitness App" "$3" > "$dir/README.txt"
  python3 -I - "$dir" "$out/fitness-ai-$id.zip" <<'PY'
import os, sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(dst, "w", zipfile.ZIP_STORED, allowZip64=True) as z:
    for name in sorted(os.listdir(src)):
        z.write(os.path.join(src, name), name)
PY
  ls -l "$out/fitness-ai-$id.zip"
}

mkdir -p "$work/text" "$work/speech" "$work/plate"
fetch "$TEXT_REPO" "$TEXT_REV" "$TEXT_FILE" "$TEXT_SHA" "$work/text/model.gguf"
pack text "Meals, recipes and summaries" \
  "model.gguf is Qwen3 1.7B by the Qwen team, Alibaba Cloud, under the Apache License 2.0 (LICENSE.txt), as $TEXT_FILE from https://huggingface.co/$TEXT_REPO (revision $TEXT_REV), not changed." \
  "$work/text"
rm -f "$work/text/model.gguf"

fetch "$SPEECH_REPO" "$SPEECH_REV" "$SPEECH_FILE" "$SPEECH_SHA" "$work/speech/model.gguf"
fetch "$SPEECH_REPO" "$SPEECH_REV" "$SPEECH_MMPROJ" "$SPEECH_MMPROJ_SHA" "$work/speech/mmproj.gguf"
pack speech "Speech" \
  "model.gguf and mmproj.gguf are Qwen3-ASR 0.6B by the Qwen team, Alibaba Cloud, under the Apache License 2.0 (LICENSE.txt), as $SPEECH_FILE and $SPEECH_MMPROJ from https://huggingface.co/$SPEECH_REPO (revision $SPEECH_REV), not changed." \
  "$work/speech"
rm -f "$work/speech/"*.gguf

fetch "$PLATE_REPO" "$PLATE_REV" "$PLATE_FILE" "$PLATE_SHA" "$work/plate/model.gguf"
fetch "$PLATE_REPO" "$PLATE_REV" "$PLATE_MMPROJ" "$PLATE_MMPROJ_SHA" "$work/plate/mmproj.gguf"
pack plate "Plate photos" \
  "model.gguf and mmproj.gguf are SmolVLM 500M Instruct by Hugging Face, under the Apache License 2.0 (LICENSE.txt), as $PLATE_FILE and $PLATE_MMPROJ from https://huggingface.co/$PLATE_REPO (revision $PLATE_REV), not changed." \
  "$work/plate"
rm -rf "$work"
