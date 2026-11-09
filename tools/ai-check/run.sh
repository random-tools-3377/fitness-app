#!/usr/bin/env bash
# The AI check (see .github/workflows/ai-check.yml). Usage: run.sh STEP
set -euo pipefail
step="${1:-all}"
here="$(cd "$(dirname "$0")" && pwd)"
echo "Step: $step"

if [ "$step" = "probe" ]; then
  python3 - <<'PY'
import json, urllib.request
repos = ["ggml-org/Qwen3-ASR-0.6B-GGUF", "ggml-org/SmolVLM-500M-Instruct-GGUF", "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
         "ggml-org/Qwen3-1.7B-GGUF", "Qwen/Qwen3-1.7B-GGUF", "unsloth/Qwen3-1.7B-GGUF", "Qwen/Qwen3-ASR-0.6B",
         "HuggingFaceTB/SmolVLM-500M-Instruct", "Qwen/Qwen3-1.7B", "Qwen/Qwen2.5-1.5B-Instruct",
         "ggml-org/SmolVLM2-500M-Video-Instruct-GGUF", "HuggingFaceTB/SmolVLM2-500M-Video-Instruct"]
for r in repos:
    try:
        d = json.load(urllib.request.urlopen(f"https://huggingface.co/api/models/{r}?blobs=true", timeout=60))
    except Exception as e:
        print(f"== {r}: {e}"); continue
    lic = (d.get("cardData") or {}).get("license"), [t for t in d.get("tags", []) if t.startswith("license:")]
    print(f"== {r}  sha={d.get('sha')}  license={lic}  base={[t for t in d.get('tags',[]) if t.startswith('base_model:')][:3]}")
    for s in d.get("siblings", []):
        n = s["rfilename"]
        if n.endswith(".gguf") or n in ("LICENSE", "README.md"):
            print(f"   {n}  {s.get('size')}  {(s.get('lfs') or {}).get('sha256')}")
PY
  exit 0
fi
echo "Unknown step"
