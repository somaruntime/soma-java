#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

./scripts/check-docs.sh
./mvnw -B -ntp verify
git diff --check

printf '%s\n' 'project-check: ok'
