#!/usr/bin/env python3
"""Merge one benchmark JVM result with process-level time/RSS evidence."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--stdout", required=True, type=Path)
    parser.add_argument("--time", required=True, type=Path)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    result_lines = [
        line[len("BENCHMARK ") :]
        for line in args.stdout.read_text(encoding="utf-8").splitlines()
        if line.startswith("BENCHMARK ")
    ]
    if len(result_lines) != 1:
        raise SystemExit(
            "expected exactly one BENCHMARK line in {}".format(args.stdout)
        )
    result = json.loads(result_lines[0])
    time_text = args.time.read_text(encoding="utf-8", errors="replace")

    darwin_rss = re.search(r"^\s*(\d+)\s+maximum resident set size\s*$", time_text, re.M)
    linux_rss = re.search(
        r"^\s*Maximum resident set size \(kbytes\):\s*(\d+)\s*$", time_text, re.M
    )
    if darwin_rss:
        result["maxRssBytes"] = int(darwin_rss.group(1))
    elif linux_rss:
        result["maxRssBytes"] = int(linux_rss.group(1)) * 1024

    darwin_wall = re.search(r"^\s*([0-9.]+)\s+real\s+", time_text, re.M)
    portable_wall = re.search(r"^\s*real\s+([0-9.]+)\s*$", time_text, re.M)
    wall = darwin_wall or portable_wall
    if wall:
        result["wallNanos"] = int(float(wall.group(1)) * 1_000_000_000)

    print(json.dumps(result, separators=(",", ":"), ensure_ascii=False))


if __name__ == "__main__":
    main()
