#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
. "$root_dir/scripts/lib/sha256.sh"
. "$root_dir/scripts/lib/download.sh"

case "$(uname -s):$(uname -m)" in
  Darwin:arm64)
    asset_name=osv-scanner_darwin_arm64
    asset_sha256=a8cd6507b06239f463a7642430cfd2d154882f150f6e30cdc0653e28dfc34216
    ;;
  Linux:x86_64)
    asset_name=osv-scanner_linux_amd64
    asset_sha256=bc98e15319ed0d515e3f9235287ba53cdc5535d576d24fd573978ecfe9ab92dc
    ;;
  *)
    printf '%s\n' \
      "osv-scanner-install: unsupported platform $(uname -s):$(uname -m)" >&2
    exit 1
    ;;
esac

toolchain_root=${SOMA_TOOLCHAIN_ROOT:-"$HOME/.cache/soma-java/toolchains"}
scanner_dir=$toolchain_root/osv-scanner-2.3.8
scanner=$scanner_dir/$asset_name
scanner_url=https://github.com/google/osv-scanner/releases/download/v2.3.8/$asset_name

mkdir -p "$scanner_dir"
soma_download_verified "$scanner_url" "$asset_sha256" "$scanner"
chmod 0755 "$scanner"
scanner_version=$("$scanner" --version 2>&1)
printf '%s\n' "$scanner_version" | grep -F 'osv-scanner version: 2.3.8' >/dev/null

printf '%s\n' "$scanner"
