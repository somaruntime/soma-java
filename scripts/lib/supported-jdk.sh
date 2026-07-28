#!/bin/sh

SOMA_SUPPORTED_JAVA_VENDOR='Amazon.com Inc.'
SOMA_SUPPORTED_JAVA_RUNTIME='1.8.0_502-b07'
SOMA_SUPPORTED_JAVAC_VERSION='javac 1.8.0_502'
SOMA_SUPPORTED_JDK_NAME='Amazon Corretto 8.502.07.1'

soma_require_supported_jdk() {
  soma_toolchain_context=$1
  if [ -z "${JAVA_HOME:-}" ] \
      || [ ! -x "$JAVA_HOME/bin/java" ] \
      || [ ! -x "$JAVA_HOME/bin/javac" ] \
      || [ ! -x "$JAVA_HOME/bin/javap" ]; then
    printf '%s\n' \
      "$soma_toolchain_context: JAVA_HOME must point to the supported full JDK 8" >&2
    return 1
  fi

  soma_toolchain_properties=$(
    "$JAVA_HOME/bin/java" -XshowSettings:properties -version 2>&1
  )
  SOMA_DETECTED_JAVA_VENDOR=$(
    printf '%s\n' "$soma_toolchain_properties" |
      sed -n 's/^[[:space:]]*java.vendor = //p' |
      sed -n '1p'
  )
  SOMA_DETECTED_JAVA_SPECIFICATION=$(
    printf '%s\n' "$soma_toolchain_properties" |
      sed -n 's/^[[:space:]]*java.specification.version = //p' |
      sed -n '1p'
  )
  SOMA_DETECTED_JAVA_RUNTIME=$(
    printf '%s\n' "$soma_toolchain_properties" |
      sed -n 's/^[[:space:]]*java.runtime.version = //p' |
      sed -n '1p'
  )
  SOMA_DETECTED_JAVAC_VERSION=$("$JAVA_HOME/bin/javac" -version 2>&1)

  if [ "$SOMA_DETECTED_JAVA_VENDOR" != "$SOMA_SUPPORTED_JAVA_VENDOR" ] \
      || [ "$SOMA_DETECTED_JAVA_SPECIFICATION" != '1.8' ] \
      || [ "$SOMA_DETECTED_JAVA_RUNTIME" != "$SOMA_SUPPORTED_JAVA_RUNTIME" ] \
      || [ "$SOMA_DETECTED_JAVAC_VERSION" != "$SOMA_SUPPORTED_JAVAC_VERSION" ]; then
    printf '%s\n' \
      "$soma_toolchain_context: expected $SOMA_SUPPORTED_JDK_NAME, got $SOMA_DETECTED_JAVA_VENDOR / $SOMA_DETECTED_JAVA_RUNTIME / $SOMA_DETECTED_JAVAC_VERSION" >&2
    return 1
  fi
}
