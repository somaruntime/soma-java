#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SOMA_QUALIFICATION_STAGE=i4 \
    exec "$repo_root/scripts/qualify-i3.sh"
