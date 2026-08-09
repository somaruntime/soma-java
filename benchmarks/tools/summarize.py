#!/usr/bin/env python3
"""Validate benchmark correctness and summarize fresh-JVM comparative evidence."""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Any, Dict, Iterable, List, Tuple


GroupKey = Tuple[str, str, str, int, int]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output-json", required=True, type=Path)
    parser.add_argument("--output-markdown", required=True, type=Path)
    parser.add_argument("--expected-runs", required=True, type=int)
    return parser.parse_args()


def integer_median(values: Iterable[int]) -> int:
    ordered = sorted(values)
    if not ordered:
        raise AssertionError("cannot summarize an empty metric")
    return ordered[len(ordered) // 2]


def load(path: Path) -> List[Dict[str, Any]]:
    records: List[Dict[str, Any]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError as error:
            raise AssertionError("invalid JSON at line {}: {}".format(line_number, error))
        if record.get("schemaVersion") != 1:
            raise AssertionError("unsupported benchmark schema at line {}".format(line_number))
        if record.get("correctness") is not True:
            raise AssertionError("correctness did not pass at line {}".format(line_number))
        records.append(record)
    if not records:
        raise AssertionError("benchmark result set is empty")
    return records


def validate(records: List[Dict[str, Any]], expected_runs: int) -> Dict[GroupKey, List[Dict[str, Any]]]:
    groups: Dict[GroupKey, List[Dict[str, Any]]] = defaultdict(list)
    shared: Dict[Tuple[str, str, int], set] = defaultdict(set)
    soma: Dict[Tuple[str, str, int], set] = defaultdict(set)
    for record in records:
        key = (
            str(record["scenario"]),
            str(record["implementation"]),
            str(record.get("workload", "core")),
            int(record["rows"]),
            int(record["parallelism"]),
        )
        groups[key].append(record)
        scenario_key = (key[0], key[2], key[3])
        shared[scenario_key].add(int(record["sharedFingerprint"]))
        if key[1].startswith("soma-"):
            soma[scenario_key].add(int(record["fingerprint"]))

    for key, items in groups.items():
        if len(items) != expected_runs:
            raise AssertionError(
                "{} has {} runs, expected {}".format(key, len(items), expected_runs)
            )
        run_numbers = {int(item["run"]) for item in items}
        if run_numbers != set(range(1, expected_runs + 1)):
            raise AssertionError("{} has invalid run numbers {}".format(key, run_numbers))
    for key, fingerprints in shared.items():
        if len(fingerprints) != 1:
            raise AssertionError("shared fingerprint drift for {}: {}".format(key, fingerprints))
    for key, fingerprints in soma.items():
        if len(fingerprints) != 1:
            raise AssertionError("SOMA logical fingerprint drift for {}: {}".format(key, fingerprints))
    return groups


def summarize(groups: Dict[GroupKey, List[Dict[str, Any]]]) -> List[Dict[str, Any]]:
    summaries: List[Dict[str, Any]] = []
    for key in sorted(groups):
        records = groups[key]
        numeric_names = sorted(
            {
                name
                for record in records
                for name, value in record.items()
                if name.endswith("Nanos") and isinstance(value, int)
            }
        )
        metrics = {
            name: {
                "minimum": min(int(record[name]) for record in records if name in record),
                "median": integer_median(
                    int(record[name]) for record in records if name in record
                ),
                "maximum": max(int(record[name]) for record in records if name in record),
            }
            for name in numeric_names
        }
        rss = [int(record["maxRssBytes"]) for record in records if "maxRssBytes" in record]
        summaries.append(
            {
                "scenario": key[0],
                "implementation": key[1],
                "workload": key[2],
                "rows": key[3],
                "parallelism": key[4],
                "runs": len(records),
                "fingerprint": int(records[0]["fingerprint"]),
                "sharedFingerprint": int(records[0]["sharedFingerprint"]),
                "maxRssBytes": integer_median(rss) if rss else None,
                "metrics": metrics,
            }
        )
    return summaries


def milliseconds(summary: Dict[str, Any], metric: str) -> str:
    value = summary["metrics"].get(metric)
    if value is None:
        return "-"
    return "{:.3f}".format(value["median"] / 1_000_000.0)


def render_markdown(summaries: List[Dict[str, Any]]) -> str:
    lines = [
        "# SOMA benchmark summary",
        "",
        "All rows passed scenario correctness and fingerprint validation.",
        "",
        "| Scenario | Workload | Implementation | Rows | P | Runs | Ingest ms | Scan ms | Parallel scan ms | Key 10k ms | Index ms | Join ms | Top ms | Group ms | RSS MiB |",
        "|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for summary in summaries:
        rss = summary["maxRssBytes"]
        rss_text = "-" if rss is None else "{:.1f}".format(rss / 1024.0 / 1024.0)
        lines.append(
            "| {scenario} | {workload} | {implementation} | {rows} | {parallelism} | {runs} | {ingest} | {scan} | {parallel_scan} | {key} | {index} | {join} | {top} | {group} | {rss} |".format(
                **summary,
                ingest=milliseconds(summary, "ingestNanos"),
                scan=milliseconds(summary, "scanMedianNanos"),
                parallel_scan=milliseconds(summary, "parallelScanMedianNanos"),
                key=milliseconds(summary, "key10kMedianNanos"),
                index=milliseconds(summary, "indexMedianNanos"),
                join=milliseconds(summary, "joinMedianNanos"),
                top=milliseconds(summary, "topMedianNanos"),
                group=milliseconds(summary, "groupMedianNanos"),
                rss=rss_text,
            )
        )
    canonical = {
        "ingestNanos",
        "scanMedianNanos",
        "parallelScanMedianNanos",
        "key10kMedianNanos",
        "indexMedianNanos",
        "joinMedianNanos",
        "topMedianNanos",
        "groupMedianNanos",
    }
    additional = []
    for summary in summaries:
        metric_names = set(summary["metrics"])
        for name in sorted(metric_names):
            if name in canonical or name.endswith("MinNanos") or name.endswith("MaxNanos"):
                continue
            if name.endswith("Nanos") and not name.endswith("MedianNanos"):
                measured_prefix = name[: -len("Nanos")]
                if measured_prefix + "MedianNanos" in metric_names:
                    continue
            additional.append((summary, name))
    if additional:
        lines.extend(
            [
                "",
                "## Additional workload metrics",
                "",
                "| Scenario | Workload | Implementation | Metric | Median ms |",
                "|---|---|---|---|---:|",
            ]
        )
        for summary, name in additional:
            lines.append(
                "| {scenario} | {workload} | {implementation} | {metric} | {value} |".format(
                    **summary,
                    metric=name,
                    value=milliseconds(summary, name),
                )
            )
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    args = parse_args()
    if args.expected_runs < 1:
        raise SystemExit("expected runs must be positive")
    records = load(args.input)
    groups = validate(records, args.expected_runs)
    summaries = summarize(groups)
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(
            {"schemaVersion": 1, "groups": summaries},
            indent=2,
            ensure_ascii=False,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )
    args.output_markdown.write_text(render_markdown(summaries), encoding="utf-8")
    print("benchmark-summary: PASS ({} records, {} groups)".format(len(records), len(groups)))


if __name__ == "__main__":
    main()
