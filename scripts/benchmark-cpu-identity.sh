#!/bin/sh

set -eu

if command -v system_profiler >/dev/null 2>&1; then
  cpu=$(system_profiler SPHardwareDataType 2>/dev/null |
    sed -n 's/^[[:space:]]*Chip: //p' | head -n 1)
  if [ -n "$cpu" ]; then
    printf '%s\n' "$cpu"
    exit 0
  fi
fi

if [ -r /proc/cpuinfo ]; then
  cpu=$(sed -n \
    -e 's/^model name[[:space:]]*:[[:space:]]*//p' \
    -e 's/^Hardware[[:space:]]*:[[:space:]]*//p' \
    /proc/cpuinfo | head -n 1)
  if [ -n "$cpu" ]; then
    printf '%s\n' "$cpu"
    exit 0
  fi
fi

printf 'architecture=%s;availableProcessors=%s\n' \
  "$(uname -m)" \
  "${SOMA_BENCHMARK_AVAILABLE_PROCESSORS:-unknown}"
