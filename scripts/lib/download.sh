#!/bin/sh

# Download one immutable HTTPS artifact and publish it only after SHA-256
# verification. Callers must source scripts/lib/sha256.sh first.
soma_download_verified() {
  source_url=$1
  expected_sha256=$2
  destination=$3

  case "$source_url" in
    https://*) ;;
    *)
      printf '%s\n' "verified-download: HTTPS URL required: $source_url" >&2
      return 1
      ;;
  esac

  destination_dir=$(dirname -- "$destination")
  mkdir -p "$destination_dir"

  if [ -f "$destination" ]; then
    existing_sha256=$(soma_sha256_hex "$destination")
    if [ "$existing_sha256" = "$expected_sha256" ]; then
      return 0
    fi
    printf '%s\n' \
      "verified-download: existing file checksum mismatch: $destination" >&2
    return 1
  fi

  temporary_file=$(mktemp "$destination.download.XXXXXX")
  if ! curl --proto '=https' --tlsv1.2 -fsSL \
      --retry 3 --retry-delay 2 --retry-all-errors \
      "$source_url" -o "$temporary_file"; then
    rm -f "$temporary_file"
    return 1
  fi

  actual_sha256=$(soma_sha256_hex "$temporary_file")
  if [ "$actual_sha256" != "$expected_sha256" ]; then
    printf '%s\n' \
      "verified-download: checksum mismatch for $source_url: $actual_sha256" >&2
    rm -f "$temporary_file"
    return 1
  fi
  mv "$temporary_file" "$destination"
}
