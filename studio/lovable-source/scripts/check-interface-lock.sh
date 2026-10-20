#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
LOCK="$ROOT/.github/interface-lock.sha"
cd "$ROOT"

if [[ ! -f "$LOCK" ]]; then
  echo "::error::Interface lock file is missing: $LOCK"
  exit 1
fi

failed=0
while read -r expected path; do
  [[ -z "${expected:-}" || "$expected" == \#* ]] && continue
  if [[ ! -f "$path" ]]; then
    echo "::error file=$path::Locked interface source is missing."
    failed=1
    continue
  fi

  actual="$(git rev-parse "HEAD:$path" 2>/dev/null || true)"
  if [[ "$actual" != "$expected" ]]; then
    echo "::error file=$path::Approved Lovable interface changed. Restore the locked file; do not update .github/interface-lock.sha without explicit user approval. Expected $expected, got ${actual:-<missing>}."
    failed=1
  else
    echo "LOCKED OK: $path"
  fi
done < "$LOCK"

if [[ "$failed" -ne 0 ]]; then
  echo "::error::Interface protection failed. Brain/backend work must not redesign the approved UI."
  exit 1
fi

echo "Approved Lovable interface is unchanged."
