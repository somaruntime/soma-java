#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
. "$root_dir/scripts/lib/sha256.sh"
. "$root_dir/scripts/lib/download.sh"

if [ "$(uname -s)" != 'Linux' ] || [ "$(uname -m)" != 'x86_64' ]; then
  printf '%s\n' \
    "zulu8-install: supported setup platform is Linux x86_64, got $(uname -s) $(uname -m)" >&2
  exit 1
fi

toolchain_root=${SOMA_TOOLCHAIN_ROOT:-"$HOME/.cache/soma-java/toolchains"}
package_name=zulu8.94.0.17-ca-jdk8.0.492-linux_x64.tar.gz
package_url=https://cdn.azul.com/zulu/bin/$package_name
package_sha256=a6d14104f2e7186cba8943c4dc182938db91509dc2c0ef9ecee046c864624d36
archive_dir=$toolchain_root/downloads
archive=$archive_dir/$package_name
java_home=$toolchain_root/zulu8.94.0.17-ca-jdk8.0.492-linux_x64

mkdir -p "$toolchain_root" "$archive_dir"
soma_download_verified "$package_url" "$package_sha256" "$archive"

if [ ! -x "$java_home/bin/javac" ]; then
  extraction_dir=$(mktemp -d "$toolchain_root/zulu8-extract.XXXXXX")
  tar -xzf "$archive" -C "$extraction_dir"
  extracted_home=$extraction_dir/zulu8.94.0.17-ca-jdk8.0.492-linux_x64
  if [ ! -x "$extracted_home/bin/javac" ]; then
    printf '%s\n' 'zulu8-install: verified archive has unexpected layout' >&2
    exit 1
  fi
  mv "$extracted_home" "$java_home"
fi

java_runtime=$("$java_home/bin/java" -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.runtime.version = //p' | sed -n '1p')
java_vendor=$("$java_home/bin/java" -XshowSettings:properties -version 2>&1 |
  sed -n 's/^[[:space:]]*java.vendor = //p' | sed -n '1p')
javac_version=$("$java_home/bin/javac" -version 2>&1)
if [ "$java_vendor" != 'Azul Systems, Inc.' ] \
    || [ "$java_runtime" != '1.8.0_492-b09' ] \
    || [ "$javac_version" != 'javac 1.8.0_492' ]; then
  printf '%s\n' \
    "zulu8-install: installed toolchain identity mismatch: $java_vendor / $java_runtime / $javac_version" >&2
  exit 1
fi

printf '%s\n' "$java_home"
