# Development performance ratchet baseline

类型：Fixed-host development baseline

候选源码：`b854e577dd60ff3de36e190f35ce4835bf14ffe0`

采集日期：2026-08-13

## 1. 适用边界

本目录保存 SOMA 开发阶段的轻量性能防退化基线，不是跨硬件 SLA、release claim 或生产容量承诺。
只有候选与本基线满足以下条件时，数值比较才有意义：

- Apple M5 Pro，18 cores，48 GB memory；
- macOS 26.6.1，arm64；
- Amazon Corretto `1.8.0_502-b07`；
- Maven 3.9.16；
- `core` workload，`soma-auto` implementation；
- Scheduling、Simulation、Real-time Dispatch 三个 deterministic scenario；
- parallelism 8，3 个 fresh JVM，每个 operation 2 次 inner warmup、5 个 sample；
- `-Xms2g -Xmx8g`，SOMA managed-memory budget 6 GiB；
- profiler 与 memory-attribution 关闭。

10K 观察固定成本、Key/Index 与小规模组合路径；1M 观察正常吞吐与复杂度。10M、JFR、allocation、
Operator × Type × Distribution 和 comprehensive frontier 仍属于人工触发的专项性能资格。

## 2. 基线文件

- [`core-10k-summary.json`](core-10k-summary.json)
- [`core-1m-summary.json`](core-1m-summary.json)

每个 summary 都经过 benchmark correctness 与 logical/shared fingerprint 校验。默认 ratchet 比较
ingest、scan、parallel scan、Key 10K probes、Index、Join、Top 和 GroupBy 中各 scenario 实际拥有的
指标；同一指标只有同时退化超过 15% 和 2 ms 才失败，从而避免把小指标噪声解释为回归。

## 3. 重放候选

从 repository root 运行：

```sh
JAVA_HOME=/path/to/java8 \
SOMA_BENCHMARK_WORKLOAD=core \
SOMA_BENCHMARK_SCENARIOS="scheduling simulation real-time-dispatch" \
SOMA_BENCHMARK_IMPLEMENTATIONS=soma-auto \
SOMA_BENCHMARK_ROWS=10000 \
SOMA_BENCHMARK_RUNS=3 \
SOMA_BENCHMARK_PARALLELISM=8 \
SOMA_BENCHMARK_PROFILER=none \
SOMA_BENCHMARK_OUTPUT_ROOT=target/development-ratchet/10k \
./scripts/benchmark.sh
```

复用已完成的构建采集 1M：

```sh
JAVA_HOME=/path/to/java8 \
SOMA_BENCHMARK_REUSE_BUILD=1 \
SOMA_BENCHMARK_WORKLOAD=core \
SOMA_BENCHMARK_SCENARIOS="scheduling simulation real-time-dispatch" \
SOMA_BENCHMARK_IMPLEMENTATIONS=soma-auto \
SOMA_BENCHMARK_ROWS=1000000 \
SOMA_BENCHMARK_RUNS=3 \
SOMA_BENCHMARK_PARALLELISM=8 \
SOMA_BENCHMARK_PROFILER=none \
SOMA_BENCHMARK_OUTPUT_ROOT=target/development-ratchet/1m \
./scripts/benchmark.sh
```

## 4. 比较

```sh
python3 benchmarks/tools/compare.py \
  --baseline benchmarks/baselines/development-m5-pro-java8/core-10k-summary.json \
  --candidate target/development-ratchet/10k/summary.json \
  --implementation soma-auto

python3 benchmarks/tools/compare.py \
  --baseline benchmarks/baselines/development-m5-pro-java8/core-1m-summary.json \
  --candidate target/development-ratchet/1m/summary.json \
  --implementation soma-auto
```

如果源码、JDK、硬件、workload identity、rows 或 parallelism 不一致，不能用调整阈值强行通过。
应先恢复同一实验条件；只有 Product Owner 接受新的正常候选后，才更新本 baseline。
