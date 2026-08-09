# SOMA benchmarks

本目录是 SOMA 的长期、非 production 性能与场景正确性工程。它不是示例代码、第三项 production
artifact、公开性能 SLA 或 release claim。

```text
three reference applications
    -> deterministic benchmark consumers
        -> fresh-JVM runner
            -> machine-readable raw results
                -> correctness validation and comparative summary
```

当前 benchmark 使用三个正式 reference application：

- `scheduling`：Medium shape，覆盖 ingest、scan、Key/Index、Join、top、GroupBy 和 point update；
- `simulation`：Narrow shape，覆盖 ingest、scan、Key/Index、stable top 和 remove；
- `real-time-dispatch`：Reference-mixed shape，覆盖 ordinary Object、scan、Key/Index、Join 和 remove。

此外，`type-kernel` 是 benchmark-only synthetic consumer，用于覆盖 byte/short/char/int/long、
float/double、String、Enum、nested Value、ordinary Object，以及 cardinality/selectivity/null/skew
等物理内核。它不是第四个 Example 或 production artifact：

```sh
SOMA_BENCHMARK_WORKLOAD=kernel \
SOMA_BENCHMARK_SCENARIOS=type-kernel \
SOMA_BENCHMARK_ROWS=1000000 \
SOMA_BENCHMARK_PARALLELISM=16 \
./scripts/benchmark.sh
```

默认 `core` workload 保持 release qualification 使用的百万行长期基线。显式 `composed` workload
面向固定千万行治理，在同一真实场景中组合 filter、Index、projection、stateful operation、GroupBy、
多种 Join、parallel 与 mutation；它不改变默认资格成本：

```sh
SOMA_BENCHMARK_WORKLOAD=composed \
SOMA_BENCHMARK_ROWS=10000000 \
SOMA_BENCHMARK_PARALLELISM=16 \
SOMA_BENCHMARK_XMS=8g \
SOMA_BENCHMARK_XMX=24g \
SOMA_BENCHMARK_MEMORY_BUDGET_BYTES=17179869184 \
./scripts/benchmark.sh
```

诊断时可用 `SOMA_BENCHMARK_SCENARIOS="scheduling"`（或 `simulation`、
`real-time-dispatch`）只重放一个 fresh JVM。`composed` 默认只运行 `soma-auto`；压缩归因时显式设置
`SOMA_BENCHMARK_IMPLEMENTATIONS="soma-auto soma-off"`。治理合同、固定环境、优化证据和当前边界见
[千万行组合负载 Conformance](../project/conformance/v1-ten-million-composed-workload-governance.md)。

从 repository root 运行：

```sh
./scripts/benchmark.sh
```

常用诊断配置：

```sh
SOMA_BENCHMARK_ROWS=100000 \
SOMA_BENCHMARK_RUNS=1 \
SOMA_BENCHMARK_PARALLELISM=8 \
./scripts/benchmark.sh
```

默认使用可移植的 process wall-time 采集。需要同时采集 peak RSS、process user/system CPU、平均
占用 core、minor/major page fault 与 voluntary/involuntary context switch 时，可显式设置
`SOMA_BENCHMARK_TIME_MODE=extended`；该模式不是 correctness 的前置条件。
CPU 归因使用 `SOMA_BENCHMARK_PROFILER=async`；allocation 归因再增加
`SOMA_BENCHMARK_ASYNC_EVENT=alloc`。两者都保留 JFR、collapsed stack 与 flame graph。

正式比较必须使用相同 commit/source、JDK/JVM、workload、row count、parallelism 和 fresh-JVM
run count；Smoke 只证明测量链可运行，不能证明性能提升。当前证据、scale/memory边界与使用准则见
[性能与正确性联合治理记录](../project/conformance/v1-performance-correctness-governance.md)。
固定10M的Design、Execution、Memory、CPU四维归因、GroupBy/Relation优化和当前parallel架构边界见
[四维性能架构治理记录](../project/conformance/v1-four-dimensional-performance-architecture-governance.md)。
Operator × Type × Distribution 矩阵、cost-aware RLE、Bound cardinality、Field materialization 与固定
1M/10M证据见[类型与分布性能资格记录](../project/conformance/v1-operator-type-distribution-performance-qualification.md)。

同一环境的 before/after 摘要可用比较器建立回归 ratchet：

```sh
python3 benchmarks/tools/compare.py \
  --baseline /path/to/baseline/summary.json \
  --candidate /path/to/candidate/summary.json \
  --implementation soma-auto
```

默认只有同时超过 15% 和 2 ms 的退化才失败，以隔离微小指标和日常噪声；阈值可以显式调整。
比较器同时要求 workload identity 与逻辑 fingerprint 一致。它用于相同环境的相对比较，不把某台
机器的绝对数值提升为 SOMA 的兼容合同或性能 SLA。
