#!/usr/bin/env python3
"""Compare two compatible SOMA benchmark summaries without encoding a machine SLA."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Tuple


GroupKey = Tuple[str, str, int, int]
DEFAULT_METRICS = (
    "ingestNanos",
    "scanMedianNanos",
    "parallelScanMedianNanos",
    "key10kMedianNanos",
    "indexMedianNanos",
    "joinMedianNanos",
    "topMedianNanos",
    "groupMedianNanos",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--candidate", required=True, type=Path)
    parser.add_argument("--implementation", action="append", default=[])
    parser.add_argument("--scenario", action="append", default=[])
    parser.add_argument("--metric", action="append", default=[])
    parser.add_argument("--max-regression-percent", type=float, default=15.0)
    parser.add_argument("--minimum-regression-nanos", type=int, default=2_000_000)
    return parser.parse_args()


def key(group: Dict[str, Any]) -> GroupKey:
    return (
        str(group["scenario"]),
        str(group["implementation"]),
        int(group["rows"]),
        int(group["parallelism"]),
    )


def load(path: Path, implementations: Iterable[str], scenarios: Iterable[str]) -> Dict[GroupKey, Dict[str, Any]]:
    document = json.loads(path.read_text(encoding="utf-8"))
    if document.get("schemaVersion") != 1:
        raise AssertionError("unsupported benchmark summary: {}".format(path))
    implementation_filter = set(implementations)
    scenario_filter = set(scenarios)
    groups: Dict[GroupKey, Dict[str, Any]] = {}
    for group in document.get("groups", []):
        if implementation_filter and group["implementation"] not in implementation_filter:
            continue
        if scenario_filter and group["scenario"] not in scenario_filter:
            continue
        group_key = key(group)
        if group_key in groups:
            raise AssertionError("duplicate benchmark group: {}".format(group_key))
        groups[group_key] = group
    if not groups:
        raise AssertionError("benchmark selection is empty: {}".format(path))
    return groups


def median(group: Dict[str, Any], metric: str) -> Optional[int]:
    value = group["metrics"].get(metric)
    return None if value is None else int(value["median"])


def main() -> None:
    args = parse_args()
    if args.max_regression_percent < 0.0:
        raise SystemExit("max regression percent must be non-negative")
    if args.minimum_regression_nanos < 0:
        raise SystemExit("minimum regression nanos must be non-negative")

    baseline = load(args.baseline, args.implementation, args.scenario)
    candidate = load(args.candidate, args.implementation, args.scenario)
    if set(baseline) != set(candidate):
        raise AssertionError("baseline and candidate benchmark groups differ")

    metrics: List[str] = args.metric or list(DEFAULT_METRICS)
    regressions: List[str] = []
    print("| Scenario | Implementation | Metric | Baseline ms | Candidate ms | Delta |")
    print("|---|---|---|---:|---:|---:|")
    for group_key in sorted(baseline):
        before = baseline[group_key]
        after = candidate[group_key]
        if before["fingerprint"] != after["fingerprint"] or before["sharedFingerprint"] != after["sharedFingerprint"]:
            raise AssertionError("logical fingerprint changed for {}".format(group_key))
        for metric in metrics:
            baseline_value = median(before, metric)
            candidate_value = median(after, metric)
            if baseline_value is None and candidate_value is None:
                continue
            if baseline_value is None or candidate_value is None:
                raise AssertionError("metric availability changed for {} {}".format(group_key, metric))
            delta = candidate_value - baseline_value
            percent = (delta * 100.0 / baseline_value) if baseline_value else 0.0
            print(
                "| {} | {} | {} | {:.3f} | {:.3f} | {:+.1f}% |".format(
                    group_key[0],
                    group_key[1],
                    metric,
                    baseline_value / 1_000_000.0,
                    candidate_value / 1_000_000.0,
                    percent,
                )
            )
            if (
                delta > args.minimum_regression_nanos
                and percent > args.max_regression_percent
            ):
                regressions.append("{} {} {:+.1f}%".format(group_key, metric, percent))

    if regressions:
        raise SystemExit("benchmark-compare: FAIL: " + "; ".join(regressions))
    print("benchmark-compare: PASS")


if __name__ == "__main__":
    main()
