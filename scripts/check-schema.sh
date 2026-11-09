#!/usr/bin/env bash
# The database layout of every version is kept in app/schemas, so a later version can move the
# user's data forward instead of losing it. Fails if the build produced a layout that is not
# committed, or one that differs from the committed file, and prints it so it can be added.
# Formatting differences alone (spacing, a final newline) don't count.
set -euo pipefail
status="$(git status --porcelain --untracked-files=all -- app/schemas)"
failed=0
while read -r code file; do
  [ -z "${file:-}" ] && continue
  if [ "$code" = "M" ] && python3 - "$file" <<'PY'
import json, subprocess, sys
path = sys.argv[1]
committed = json.loads(subprocess.run(["git", "show", "HEAD:" + path], check=True, capture_output=True, text=True).stdout)
built = json.load(open(path))
sys.exit(0 if committed == built else 1)
PY
  then
    continue
  fi
  echo "::error::The database layout $file is not committed, or differs from the committed one. Commit it:"
  echo "----- $file"
  cat "$file"
  echo
  # The same file as one line (gzip, then base64), to copy out of the log exactly:
  #   echo '<line>' | base64 -d | gunzip > the/file.json
  echo "Compressed: $(gzip -9c "$file" | base64 -w0)"
  failed=1
done <<< "$status"
if [ "$failed" != 0 ]; then
  exit 1
fi
echo "Database layout check passed."
