#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
. "$root_dir/scripts/lib/sha256.sh"
. "$root_dir/scripts/lib/download.sh"

if [ "$(uname -s)" != 'Linux' ] || [ "$(uname -m)" != 'x86_64' ]; then
  printf '%s\n' \
    "ripgrep-install: supported setup platform is Linux x86_64, got $(uname -s) $(uname -m)" >&2
  exit 1
fi

toolchain_root=${SOMA_TOOLCHAIN_ROOT:-"$HOME/.cache/soma-java/toolchains"}
package_name=ripgrep-15.2.0-x86_64-unknown-linux-musl.tar.gz
package_url=https://github.com/BurntSushi/ripgrep/releases/download/15.2.0/$package_name
package_sha256=33e15bcf1624b25cdd2a55813a47a2f95dbe126268203e76aa6a585d1e7b149c
archive_dir=$toolchain_root/downloads
archive=$archive_dir/$package_name
install_dir=$toolchain_root/ripgrep-15.2.0-x86_64-unknown-linux-musl
ripgrep=$install_dir/rg

mkdir -p "$toolchain_root" "$archive_dir"
soma_download_verified "$package_url" "$package_sha256" "$archive"

if [ ! -x "$ripgrep" ]; then
  extraction_dir=$(mktemp -d "$toolchain_root/ripgrep-extract.XXXXXX")
  tar -xzf "$archive" -C "$extraction_dir"
  extracted_dir=$extraction_dir/ripgrep-15.2.0-x86_64-unknown-linux-musl
  if [ ! -x "$extracted_dir/rg" ]; then
    printf '%s\n' 'ripgrep-install: verified archive has unexpected layout' >&2
    exit 1
  fi
  mv "$extracted_dir" "$install_dir"
fi

ripgrep_identity=$("$ripgrep" --version | sed -n '1p')
ripgrep_version=$(printf '%s\n' "$ripgrep_identity" |
  sed -n 's/^ripgrep \([0-9][0-9.]*\).*$/\1/p')
if [ "$ripgrep_version" != '15.2.0' ]; then
  printf '%s\n' \
    "ripgrep-install: installed identity mismatch: $ripgrep_identity" >&2
  exit 1
fi

printf '%s\n' "$ripgrep"
