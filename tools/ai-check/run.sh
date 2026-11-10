#!/usr/bin/env bash
# The AI check (see .github/workflows/ai-check.yml): builds llama.cpp and the app's own AI code
# for Linux, downloads the models from Hugging Face (pinned, with SHA-256 sums), and asks them
# the app's own prompts (app/src/main/assets/ai) about sample meals, a recipe, a week, a spoken
# sentence and a photo of a plate. Prints every answer and how long it took.
# Usage: run.sh all|probe|text|speech|plate
set -euo pipefail
step="${1:-all}"
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
assets="$repo/app/src/main/assets/ai"
work="${RUNNER_TEMP:-/tmp}/ai-check"
mkdir -p "$work"
# shellcheck source=/dev/null
source "$here/models.sh"

build() {
  if [ ! -x "$work/build/ai-check" ]; then
    git clone --quiet --depth 1 --branch "$LLAMA_TAG" https://github.com/ggml-org/llama.cpp "$work/llama"
    [ "$(git -C "$work/llama" rev-parse HEAD)" = "$LLAMA_COMMIT" ] || { echo "llama.cpp tag moved"; exit 1; }
    cmake -S "$here" -B "$work/build" -DLLAMA_DIR="$work/llama" -DCMAKE_BUILD_TYPE=Release -DGGML_NATIVE=ON > "$work/cmake.log"
    cmake --build "$work/build" --target ai-check -j "$(nproc)" > "$work/build.log" || { tail -50 "$work/build.log"; exit 1; }
  fi
}

# fetch REPO REVISION FILE SHA256: downloads one model file into $work/models.
fetch() {
  local out="$work/models/$3"
  mkdir -p "$work/models"
  if [ ! -f "$out" ]; then
    curl -sSfL --retry 3 -o "$out.part" "https://huggingface.co/$1/resolve/$2/$3"
    echo "$4  $out.part" | sha256sum -c --quiet -
    mv "$out.part" "$out"
  fi
}

ask() {  # ask MODEL MMPROJ SYSTEM USER GRAMMAR MEDIA MAX PREFIX (at most 5 minutes each)
  timeout 300 "$work/build/ai-check" "$@" 2>&1 | grep -v "^\s*$" || echo "(no answer within 5 minutes)"
}

if [ "$step" = "probe" ]; then
  echo "Nothing to probe."
  exit 0
fi

SECONDS=0
build
echo "[built in ${SECONDS} s]"
cases="$here/cases"

if [ "$step" = "all" ] || [ "$step" = "text" ]; then
  fetch "$TEXT_REPO" "$TEXT_REV" "$TEXT_FILE" "$TEXT_SHA"
  m="$work/models/$TEXT_FILE"
  echo "===== Meals"
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    printf '%s' "$line" > "$work/user.txt"
    echo "--- $line"
    ask "$m" - "$assets/meal-system.txt" "$work/user.txt" "$assets/meal.gbnf" - 300 "$assets/qwen3-prefix.txt"
  done < "$cases/meals.txt"
  for r in "$cases"/recipe-*.txt; do
    echo "===== Recipe $(basename "$r")"
    ask "$m" - "$assets/recipe-system.txt" "$r" "$assets/meal.gbnf" - 600 "$assets/qwen3-prefix.txt"
  done
  for w in "$cases"/week-*.txt; do
    echo "===== Week $(basename "$w")"
    ask "$m" - "$assets/summary-system.txt" "$w" - - 250 "$assets/qwen3-prefix.txt"
  done
fi

if [ "$step" = "all" ] || [ "$step" = "speech" ]; then
  fetch "$SPEECH_REPO" "$SPEECH_REV" "$SPEECH_FILE" "$SPEECH_SHA"
  fetch "$SPEECH_REPO" "$SPEECH_REV" "$SPEECH_MMPROJ" "$SPEECH_MMPROJ_SHA"
  command -v espeak-ng > /dev/null || sudo apt-get install -y -qq espeak-ng > /dev/null
  echo "===== Speech"
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    espeak-ng -v en-gb -s 150 -w "$work/said.wav" "$line"
    # 16 kHz mono, as the phone records it.
    ffmpeg -loglevel error -y -i "$work/said.wav" -ar 16000 -ac 1 "$work/said16.wav"
    echo "--- said: $line"
    ask "$work/models/$SPEECH_FILE" "$work/models/$SPEECH_MMPROJ" /dev/null "$assets/speech-user.txt" - "$work/said16.wav" 120 -
  done < "$cases/spoken.txt"
fi

if [ "$step" = "all" ] || [ "$step" = "plate" ]; then
  fetch "$PLATE_REPO" "$PLATE_REV" "$PLATE_FILE" "$PLATE_SHA"
  fetch "$PLATE_REPO" "$PLATE_REV" "$PLATE_MMPROJ" "$PLATE_MMPROJ_SHA"
  echo "===== Plates"
  python3 "$here/plates.py" "$work/plates"
  for p in "$work"/plates/*.jpg; do
    echo "--- $(basename "$p")"
    ask "$work/models/$PLATE_FILE" "$work/models/$PLATE_MMPROJ" "$assets/plate-system.txt" "$assets/plate-user.txt" "$assets/plate.gbnf" "$p" 80 -
  done
fi
