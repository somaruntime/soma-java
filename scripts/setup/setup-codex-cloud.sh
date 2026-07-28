#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$root_dir"

for command_name in awk cp curl git mktemp mv tar; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf '%s\n' \
      "codex-cloud-setup: required command not found: $command_name" >&2
    exit 1
  fi
done

toolchain_root=${SOMA_TOOLCHAIN_ROOT:-"$HOME/.cache/soma-java/toolchains"}
export SOMA_TOOLCHAIN_ROOT=$toolchain_root
java_home=$(./scripts/setup/install-zulu8-linux-x64.sh)
osv_scanner=$(./scripts/setup/install-osv-scanner.sh)
ripgrep=$(./scripts/setup/install-ripgrep-linux-x64.sh)
evidence_repository=$toolchain_root/maven-evidence/repository
export JAVA_HOME=$java_home
export OSV_SCANNER=$osv_scanner
export RIPGREP=$ripgrep
export SOMA_MAVEN_EVIDENCE_REPOSITORY=$evidence_repository
ripgrep_dir=$(dirname -- "$RIPGREP")
export PATH=$JAVA_HOME/bin:$ripgrep_dir:$PATH

# Codex Cloud exposes its network proxy CA through SSL_CERT_FILE. The downloaded
# Zulu JDK has an independent truststore, so merge the platform-controlled CA
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
      'codex-cloud-setup: Zulu truststore or keytool is unavailable.' >&2
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
    'codex-cloud-java-trust: platform CA bundle not present; using Zulu defaults'
fi

environment_file=$toolchain_root/codex-cloud-environment.sh
mkdir -p "$toolchain_root"
{
  printf 'export SOMA_TOOLCHAIN_ROOT=%s\n' "$toolchain_root"
  printf 'export JAVA_HOME=%s\n' "$JAVA_HOME"
  printf 'export OSV_SCANNER=%s\n' "$OSV_SCANNER"
  printf 'export RIPGREP=%s\n' "$RIPGREP"
  printf 'export SOMA_MAVEN_EVIDENCE_REPOSITORY=%s\n' \
    "$SOMA_MAVEN_EVIDENCE_REPOSITORY"
  printf 'export PATH="$JAVA_HOME/bin:%s:$PATH"\n' "$ripgrep_dir"
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
./scripts/check-toolchain.sh

# Prewarm the normal Maven repository for reactor, clean/package and benchmark
# goals. The later agent phase can then run the full Gate without network access.
./mvnw -B -ntp verify

# Populate a repository outside the checkout through the same external-consumer
# path used by isolated Gate scripts. Reactor clean cannot remove this cache.
./scripts/check-external-consumer.sh

# The complete Gate exercises several independent Maven fixtures. They do not
# inherit the reactor's plugin management, so Maven may select different
# default-lifecycle plugin versions for them. Resolve every fixture's complete
# dependency and plugin graph while setup networking is available; the agent
# phase can then execute those fixtures from the persistent repository without
# falling back to Maven Central.
for fixture_pom in \
  tests/fixtures/external-maven-*/pom.xml \
  tests/fixtures/invalid-keyed-int/pom.xml; do
  ./mvnw -B -ntp \
    -Dmaven.repo.local="$SOMA_MAVEN_EVIDENCE_REPOSITORY" \
    -f "$fixture_pom" \
    org.apache.maven.plugins:maven-dependency-plugin:3.8.1:go-offline
done

# Build governance uses a pinned help-plugin goal in a fresh repository seeded
# from this cache, so resolve its complete plugin graph during networked setup.
setup_effective_pom=$toolchain_root/setup-effective-pom.xml
./mvnw -B -ntp \
  -Dmaven.repo.local="$SOMA_MAVEN_EVIDENCE_REPOSITORY" \
  org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom \
  -Doutput="$setup_effective_pom"

printf '%s\n' "codex-cloud-environment: $environment_file"
printf '%s\n' 'codex-cloud-setup: ok'
