#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$root_dir"
cloud_setup_started_at=$(date +%s)

for command_name in awk cp curl git mktemp mv tar; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf '%s\n' \
      "codex-cloud-setup: required command not found: $command_name" >&2
    exit 1
  fi
done

run_cloud_setup_stage() {
  cloud_setup_stage_name=$1
  shift
  cloud_setup_stage_started_at=$(date +%s)
  printf '%s\n' \
    "codex-cloud-stage: start name=$cloud_setup_stage_name"
  if "$@"; then
    cloud_setup_stage_finished_at=$(date +%s)
    printf '%s\n' \
      "codex-cloud-stage: passed name=$cloud_setup_stage_name durationSeconds=$((cloud_setup_stage_finished_at - cloud_setup_stage_started_at))"
  else
    cloud_setup_stage_status=$?
    cloud_setup_stage_finished_at=$(date +%s)
    printf '%s\n' \
      "codex-cloud-stage: failed name=$cloud_setup_stage_name durationSeconds=$((cloud_setup_stage_finished_at - cloud_setup_stage_started_at)) status=$cloud_setup_stage_status" >&2
    return "$cloud_setup_stage_status"
  fi
}

run_cloud_setup_path_stage() {
  cloud_path_stage_name=$1
  shift
  cloud_path_stage_started_at=$(date +%s)
  printf '%s\n' \
    "codex-cloud-stage: start name=$cloud_path_stage_name" >&2
  if cloud_path_stage_output=$("$@"); then
    cloud_path_stage_finished_at=$(date +%s)
    printf '%s\n' \
      "codex-cloud-stage: passed name=$cloud_path_stage_name durationSeconds=$((cloud_path_stage_finished_at - cloud_path_stage_started_at))" >&2
    printf '%s\n' "$cloud_path_stage_output"
  else
    cloud_path_stage_status=$?
    cloud_path_stage_finished_at=$(date +%s)
    printf '%s\n' \
      "codex-cloud-stage: failed name=$cloud_path_stage_name durationSeconds=$((cloud_path_stage_finished_at - cloud_path_stage_started_at)) status=$cloud_path_stage_status" >&2
    return "$cloud_path_stage_status"
  fi
}

toolchain_root=${SOMA_TOOLCHAIN_ROOT:-"$HOME/.cache/soma-java/toolchains"}
export SOMA_TOOLCHAIN_ROOT=$toolchain_root
java_home=$(run_cloud_setup_path_stage install-corretto \
  ./scripts/setup/install-corretto8-linux-x64.sh)
ripgrep=$(run_cloud_setup_path_stage install-ripgrep \
  ./scripts/setup/install-ripgrep-linux-x64.sh)
export JAVA_HOME=$java_home
export RIPGREP=$ripgrep
ripgrep_dir=$(dirname -- "$RIPGREP")
export PATH=$JAVA_HOME/bin:$ripgrep_dir:$PATH

# Codex Cloud exposes its network proxy CA through SSL_CERT_FILE. The downloaded
# Corretto JDK has an independent truststore, so merge the platform-controlled CA
# bundle into a private copy for setup-phase Maven traffic. TLS verification
# remains enabled and the vendor truststore remains unchanged.
cloud_ca_bundle=
if [ -n "${SSL_CERT_FILE:-}" ] && [ -r "$SSL_CERT_FILE" ]; then
  cloud_ca_bundle=$SSL_CERT_FILE
elif [ -n "${CODEX_CA_CERTIFICATE:-}" ] \
  && [ -r "$CODEX_CA_CERTIFICATE" ]; then
  cloud_ca_bundle=$CODEX_CA_CERTIFICATE
elif [ -r /opt/_internal/certs.pem ]; then
  cloud_ca_bundle=/opt/_internal/certs.pem
fi

if [ -n "$cloud_ca_bundle" ]; then
  vendor_trust_store=$JAVA_HOME/jre/lib/security/cacerts
  if [ ! -r "$vendor_trust_store" ] \
    || [ ! -x "$JAVA_HOME/bin/keytool" ]; then
    printf '%s\n' \
      'codex-cloud-setup: Corretto truststore or keytool is unavailable.' >&2
    exit 1
  fi

  certificates_dir=$(mktemp -d "$toolchain_root/codex-cloud-ca.XXXXXX")
  temporary_trust_store=$toolchain_root/codex-cloud-cacerts.$$
  trust_store=$toolchain_root/codex-cloud-cacerts
  cleanup_cloud_trust() {
    rm -rf -- "$certificates_dir"
    rm -f -- "$temporary_trust_store"
  }
  trap cleanup_cloud_trust EXIT HUP INT TERM

  awk -v output_dir="$certificates_dir" '
    /-----BEGIN CERTIFICATE-----/ {
      certificate_count++
      output_file = sprintf("%s/certificate-%04d.pem", output_dir, certificate_count)
    }
    output_file != "" {
      print > output_file
    }
    /-----END CERTIFICATE-----/ {
      close(output_file)
      output_file = ""
    }
    END {
      if (certificate_count == 0 || output_file != "") {
        exit 1
      }
    }
  ' "$cloud_ca_bundle" || {
    printf '%s\n' \
      'codex-cloud-setup: platform CA bundle is empty or malformed.' >&2
    exit 1
  }

  cp "$vendor_trust_store" "$temporary_trust_store"
  certificate_count=0
  for certificate_file in "$certificates_dir"/certificate-*.pem; do
    certificate_count=$((certificate_count + 1))
    certificate_alias=$(printf 'codex-cloud-ca-%04d' "$certificate_count")
    "$JAVA_HOME/bin/keytool" -importcert -noprompt \
      -alias "$certificate_alias" \
      -file "$certificate_file" \
      -keystore "$temporary_trust_store" \
      -storepass changeit >/dev/null
  done
  mv "$temporary_trust_store" "$trust_store"
  rm -rf -- "$certificates_dir"
  trap - EXIT HUP INT TERM

  maven_trust_options="-Djavax.net.ssl.trustStore=$trust_store -Djavax.net.ssl.trustStorePassword=changeit -Djavax.net.ssl.trustStoreType=JKS"
  export MAVEN_OPTS="${MAVEN_OPTS:+$MAVEN_OPTS }$maven_trust_options"
  printf '%s\n' \
    "codex-cloud-java-trust: imported $certificate_count platform certificate(s)"
else
  printf '%s\n' \
    'codex-cloud-java-trust: platform CA bundle not present; using Corretto defaults'
fi

environment_file=$toolchain_root/codex-cloud-environment.sh
mkdir -p "$toolchain_root"
{
  printf 'export SOMA_TOOLCHAIN_ROOT=%s\n' "$toolchain_root"
  printf 'export JAVA_HOME=%s\n' "$JAVA_HOME"
  printf 'export RIPGREP=%s\n' "$RIPGREP"
  printf 'export PATH="$JAVA_HOME/bin:%s:$PATH"\n' "$ripgrep_dir"
  if [ -n "${trust_store:-}" ]; then
    printf 'export MAVEN_OPTS="${MAVEN_OPTS:+$MAVEN_OPTS }-Djavax.net.ssl.trustStore=%s -Djavax.net.ssl.trustStorePassword=changeit -Djavax.net.ssl.trustStoreType=JKS"\n' \
      "$trust_store"
  fi
} >"$environment_file"
chmod 0644 "$environment_file"

source_line=". \"$environment_file\""
for shell_profile in "$HOME/.bashrc" "$HOME/.profile"; do
  touch "$shell_profile"
  if ! grep -Fqx "$source_line" "$shell_profile"; then
    printf '\n%s\n' "$source_line" >>"$shell_profile"
  fi
done

command -v rg >/dev/null 2>&1

run_cloud_setup_stage toolchain ./scripts/check-toolchain.sh

# Setup has network access; prepare the normal Maven repository once. Ordinary
# Cloud development then uses the same repository and lifecycle as local/CI
# development instead of duplicating every dependency in an evidence cache.
run_cloud_setup_stage reactor-cache \
  ./mvnw -B -ntp install -DskipTests

# The independent external fixture with the broadest plugin surface resolves the
# compiler, clean, jar, surefire and dependency plugin graph shared by the other
# fixtures. SOMA reactor artifacts were installed by the preceding stage.
run_cloud_setup_stage external-fixture-cache \
  ./mvnw -B -ntp \
    -f tests/fixtures/external-maven-value/pom.xml \
    org.apache.maven.plugins:maven-dependency-plugin:3.8.1:go-offline

# Build governance invokes one pinned help-plugin goal outside the normal
# lifecycle, so resolve it explicitly while setup networking is available.
setup_effective_pom=$toolchain_root/setup-effective-pom.xml
run_cloud_setup_stage build-governance-cache \
  ./mvnw -B -ntp \
    org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom \
    -Doutput="$setup_effective_pom"

cloud_setup_finished_at=$(date +%s)
printf '%s\n' "codex-cloud-environment: $environment_file"
printf '%s\n' \
  "codex-cloud-setup: ok durationSeconds=$((cloud_setup_finished_at - cloud_setup_started_at))"
