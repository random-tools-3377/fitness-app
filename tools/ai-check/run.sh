#!/usr/bin/env bash
# The AI check (see .github/workflows/ai-check.yml). Usage: run.sh STEP
set -euo pipefail
step="${1:-all}"
echo "Step: $step"
echo "Nothing to run yet on this branch."
