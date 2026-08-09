#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SOMA_QUALIFY_SKIP_BENCHMARK=1 \
    exec "$repo_root/scripts/qualify.sh"
