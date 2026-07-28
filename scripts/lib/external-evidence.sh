#!/bin/sh

soma_external_mvn() {
  ./mvnw \
    -Daether.syncContext.named.factory=file-lock \
    -Daether.syncContext.named.nameMapper=file-gav \
    "$@"
}

soma_require_or_install_external_artifacts() (
  if [ "${SOMA_EXTERNAL_ARTIFACTS_PREPARED:-false}" = 'true' ]; then
    exit 0
  fi

  soma_external_mvn -B -ntp \
    -pl soma-runtime-core,soma-dataflow,soma-processor -am \
    install -DskipTests
)

soma_write_runtime_classpath() (
  consumer_pom=$1
  output_file=$2
  dependency_plugin_version=$(sed -n \
    's:.*<maven.dependency.plugin.version>\([^<]*\)</maven.dependency.plugin.version>.*:\1:p' \
    pom.xml | sed -n '1p')
  if [ -z "$dependency_plugin_version" ]; then
    printf '%s\n' \
      'external-evidence: dependency plugin version property missing' >&2
    exit 1
  fi
  soma_external_mvn -B -ntp -f "$consumer_pom" \
    "org.apache.maven.plugins:maven-dependency-plugin:$dependency_plugin_version:build-classpath" \
    -DincludeScope=runtime \
    -Dmdep.outputFile="$output_file"
)
