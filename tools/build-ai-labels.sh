#!/usr/bin/env bash
# Builds the label-reading model file for Fitness App: PaddleOCR's PP-OCRv6 small text-finding
# and text-reading models (Apache-2.0), as converted to ONNX by RapidOCR (Apache-2.0), taken from
# RapidOCR's package on PyPI and checked against pinned SHA-256 sums. Writes the model files to
# OUT/labels and the file the app opens to OUT/fitness-ai-labels.zip.
# Usage: build-ai-labels.sh OUT
set -euo pipefail
out="$1"
work="$(mktemp -d)"
wheel="rapidocr-3.10.0-py3-none-any.whl"
python3 -m pip download --quiet --no-deps --dest "$work" "rapidocr==3.10.0"
echo "2fc34e26cd0f48514804a94f8832283039c7334076bff60768b1fed9ad068050  $work/$wheel" | sha256sum -c -
mkdir -p "$work/x" "$out/labels"
python3 -I -m zipfile -e "$work/$wheel" "$work/x"
cp "$work/x/rapidocr/models/PP-OCRv6_det_small.onnx" "$out/labels/det.onnx"
cp "$work/x/rapidocr/models/PP-OCRv6_rec_small.onnx" "$out/labels/rec.onnx"
(cd "$out/labels" && sha256sum -c - <<SUMS)
090f04abcd9d9a7498bc4ebf677e4cb9bdce1fe4197ddb7e529f1ef44e1ff94f  det.onnx
6f327246b50388f3c176ae304bd95767ea6dc0c9ae92153ef8cbe210b3c14884  rec.onnx
SUMS
# The reading model's letters, from its own metadata, as a plain file: reading them through
# ONNX Runtime's Java API garbles the letters outside the basic multilingual plane.
python3 -m pip install --quiet --target "$work/py" "onnx==1.23.2"
PYTHONPATH="$work/py" python3 - "$out/labels/rec.onnx" "$out/labels/keys.txt" <<'PY'
import sys, onnx
m = onnx.load(sys.argv[1], load_external_data=False)
keys = next(p.value for p in m.metadata_props if p.key == "character")
open(sys.argv[2], "w", encoding="utf-8", newline="\n").write(keys)
print(len(keys.split("\n")), "letters")
PY
cp "$work/x/rapidocr-3.10.0.dist-info/licenses/LICENSE" "$out/labels/LICENSE.txt"
cp "$work/x/rapidocr-3.10.0.dist-info/licenses/MODEL_LICENSES.md" "$out/labels/MODEL_LICENSES.md"
cat > "$out/labels/README.txt" <<'TXT'
Label reading for Fitness App: open this file in the app (Settings, Features, Better label reading).

det.onnx and rec.onnx (with its letters in keys.txt) are PaddleOCR's PP-OCRv6 small text detection and recognition models,
copyright Baidu and/or the applicable PaddleOCR rights holders, licensed under the Apache License,
Version 2.0 (LICENSE.txt). Converted from an official PaddleOCR model to the ONNX format and
packaged for use with RapidOCR (https://github.com/RapidAI/RapidOCR), whose model notice is in
MODEL_LICENSES.md. Not changed further; only renamed and put in this file.
TXT
printf '{"id":"labels","version":1,"title":"Better label reading"}\n' > "$out/labels/manifest.json"
rm -f "$out/fitness-ai-labels.zip"
(cd "$out/labels" && python3 -I -m zipfile -c "$out/fitness-ai-labels.zip" manifest.json det.onnx rec.onnx keys.txt LICENSE.txt MODEL_LICENSES.md README.txt)
ls -l "$out/fitness-ai-labels.zip"
