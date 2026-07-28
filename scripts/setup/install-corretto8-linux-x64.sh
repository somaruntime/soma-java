#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
. "$root_dir/scripts/lib/sha256.sh"
. "$root_dir/scripts/lib/download.sh"

if [ "$(uname -s)" != 'Linux' ] || [ "$(uname -m)" != 'x86_64' ]; then
  printf '%s\n' \
    "corretto8-install: supported setup platform is Linux x86_64, got $(uname -s) $(uname -m)" >&2
  exit 1
fi

toolchain_root=${SOMA_TOOLCHAIN_ROOT:-"$HOME/.cache/soma-java/toolchains"}
package_name=amazon-corretto-8.502.07.1-linux-x64.tar.gz
package_url=https://corretto.aws/downloads/resources/8.502.07.1/$package_name
package_sha256=ff9b634a2a70b81b75e855be1db50ff712e4ea5f92dc224cb1c069122710b111
archive_dir=$toolchain_root/downloads
archive=$archive_dir/$package_name
java_home=$toolchain_root/amazon-corretto-8.502.07.1-linux-x64

mkdir -p "$toolchain_root" "$archive_dir"
soma_download_verified "$package_url" "$package_sha256" "$archive"

if [ ! -x "$java_home/bin/javac" ]; then
  extraction_dir=$(mktemp -d "$toolchain_root/corretto8-extract.XXXXXX")
  tar -xzf "$archive" -C "$extraction_dir"
  extracted_home=$extraction_dir/amazon-corretto-8.502.07.1-linux-x64
  if [ ! -x "$extracted_home/bin/javac" ]; then
    printf '%s\n' 'corretto8-install: verified archive has unexpected layout' >&2
    exit 1
  fi
  mv "$extracted_home" "$java_home"
fi

java_runtime=$("$java_home/bin/java" -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.runtime.version = //p' | sed -n '1p')
java_vendor=$("$java_home/bin/java" -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.vendor = //p' | sed -n '1p')
javac_version=$("$java_home/bin/javac" -version 2>&1)
if [ "$java_vendor" != 'Amazon.com Inc.' ] \
    || [ "$java_runtime" != '1.8.0_502-b07' ] \
    || [ "$javac_version" != 'javac 1.8.0_502' ]; then
  printf '%s\n' \
    "corretto8-install: installed toolchain identity mismatch: $java_vendor / $java_runtime / $javac_version" >&2
  exit 1
fi

printf '%s\n' "$java_home"
