#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$root_dir"

for command_name in curl git rg tar; do
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
export JAVA_HOME=$java_home
export OSV_SCANNER=$osv_scanner
export PATH=$JAVA_HOME/bin:$PATH

environment_file=$toolchain_root/codex-cloud-environment.sh
mkdir -p "$toolchain_root"
{
  printf 'export SOMA_TOOLCHAIN_ROOT=%s\n' "$toolchain_root"
  printf 'export JAVA_HOME=%s\n' "$JAVA_HOME"
  printf 'export OSV_SCANNER=%s\n' "$OSV_SCANNER"
  printf 'export PATH=\"$JAVA_HOME/bin:$PATH\"\n'
} >"$environment_file"
chmod 0644 "$environment_file"

source_line=". \"$environment_file\""
for shell_profile in "$HOME/.bashrc" "$HOME/.profile"; do
  touch "$shell_profile"
  if ! grep -Fqx "$source_line" "$shell_profile"; then
    printf '\n%s\n' "$source_line" >>"$shell_profile"
  fi
done

./scripts/check-toolchain.sh

# Populate the evidence repository through the same isolated Maven consumer path
# used by the full Gate. A later network-disabled agent phase can reuse it.
./scripts/check-external-consumer.sh

printf '%s\n' "codex-cloud-environment: $environment_file"
printf '%s\n' 'codex-cloud-setup: ok'
