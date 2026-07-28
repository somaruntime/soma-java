#!/bin/sh

# Portable SHA-256 output compatible with both sha256sum and shasum:
#   <hex>  <path-or-dash>
# With no arguments the function reads standard input.
soma_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    if [ "$#" -eq 0 ]; then
      sha256sum
    else
      sha256sum "$@"
    fi
  elif command -v shasum >/dev/null 2>&1; then
    if [ "$#" -eq 0 ]; then
      shasum -a 256
    else
      shasum -a 256 "$@"
    fi
  else
    printf '%s\n' 'SOMA requires sha256sum or shasum for SHA-256 verification.' >&2
    return 127
  fi
}

soma_sha256_hex() {
  soma_sha256 "$@" | awk 'NR == 1 { print $1 }'
}
