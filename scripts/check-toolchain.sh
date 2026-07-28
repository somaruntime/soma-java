#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"
. "$root_dir/scripts/lib/supported-jdk.sh"

soma_require_supported_jdk toolchain-check
maven_version=$(./mvnw -version | sed -n '1p')

case "$maven_version" in
  'Apache Maven 3.9.16 '*) ;;
  *)
    printf '%s\n' "toolchain-check: expected Maven 3.9.16, got $maven_version" >&2
    exit 1
    ;;
esac

printf '%s\n' "toolchain-java-vendor: $SOMA_DETECTED_JAVA_VENDOR"
printf '%s\n' "toolchain-java-runtime: $SOMA_DETECTED_JAVA_RUNTIME"
printf '%s\n' "toolchain-javac: $SOMA_DETECTED_JAVAC_VERSION"
printf '%s\n' "toolchain-maven: $maven_version"
printf '%s\n' "toolchain-platform: $(uname -srm)"
printf '%s\n' 'toolchain-check: ok'
