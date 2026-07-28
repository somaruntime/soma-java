#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ] \
    || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'toolchain-check: JAVA_HOME must point to a full JDK.' >&2
  exit 1
fi

java_properties=$("$JAVA_HOME/bin/java" -XshowSettings:properties -version 2>&1)
java_vendor=$(printf '%s\n' "$java_properties" |
  sed -n 's/^[[:space:]]*java.vendor = //p' | sed -n '1p')
java_specification=$(printf '%s\n' "$java_properties" |
  sed -n 's/^[[:space:]]*java.specification.version = //p' | sed -n '1p')
java_runtime=$(printf '%s\n' "$java_properties" |
  sed -n 's/^[[:space:]]*java.runtime.version = //p' | sed -n '1p')
javac_version=$("$JAVA_HOME/bin/javac" -version 2>&1)
maven_version=$(./mvnw -version | sed -n '1p')

if [ "$java_vendor" != 'Azul Systems, Inc.' ]; then
  printf '%s\n' "toolchain-check: expected Azul Systems, Inc., got $java_vendor" >&2
  exit 1
fi
if [ "$java_specification" != '1.8' ] \
    || [ "$java_runtime" != '1.8.0_492-b09' ] \
    || [ "$javac_version" != 'javac 1.8.0_492' ]; then
  printf '%s\n' \
    "toolchain-check: expected Zulu 8.94.0.17 / 1.8.0_492-b09, got $java_runtime / $javac_version" >&2
  exit 1
fi
case "$maven_version" in
  'Apache Maven 3.9.16 '*) ;;
  *)
    printf '%s\n' "toolchain-check: expected Maven 3.9.16, got $maven_version" >&2
    exit 1
    ;;
esac

printf '%s\n' "toolchain-java-vendor: $java_vendor"
printf '%s\n' "toolchain-java-runtime: $java_runtime"
printf '%s\n' "toolchain-javac: $javac_version"
printf '%s\n' "toolchain-maven: $maven_version"
printf '%s\n' "toolchain-platform: $(uname -srm)"
printf '%s\n' 'toolchain-check: ok'
