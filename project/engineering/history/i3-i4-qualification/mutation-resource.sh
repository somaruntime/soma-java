#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
SOMA_QUERY_QUALIFICATION_SCOPE=mutation \
    exec "$repo_root/build-support/qualification/query-planning.sh"
