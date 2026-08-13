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

当前 benchmark 覆盖三个正式 reference application 对应的 workload family：

- `scheduling`：Medium shape，覆盖 ingest、scan、Key/Index、Join、top、GroupBy 和 point update；
- `simulation`：Narrow shape，覆盖 ingest、scan、Key/Index、stable top 和 remove；
- `simulation-application`：完整Grassing headless journey，覆盖两张Table、五阶段tick、
  point/Selection mutation、Index迁移、GroupBy、Join、统计、内存与确定性fingerprint；
- `real-time-dispatch`：Reference-mixed shape，覆盖 ordinary Object、scan、Key/Index、Join 和 remove。

`scheduling` benchmark 为保持既有纵向性能时间序列，拥有独立的 non-production fixture schema；
它不再借用 FJSP application 的 runtime schema。完整 100K FJSP 的模型、求解和结果正确性由
[`soma-examples/scheduling`](../soma-examples/scheduling/README.md)负责，benchmark 继续稳定拥有
ingest/scan/Join/GroupBy 等 kernel 对照，二者不形成互相牵制的 schema 合同。

同样，历史`simulation` Narrow benchmark现在拥有自己的non-production Event/State fixture，保持既有
scenario identity、fingerprint与比较序列；完整Grassing Example由独立
`simulation-application` journey测量，不把新业务循环冒充旧kernel。

标准FJSP另有一个只服务治理的warm harness：

```sh
java -Xms1g -Xmx1g \
  -Dsoma.scheduling.warmups=3 \
  -Dsoma.scheduling.samples=7 \
  -cp 'benchmarks/target/classes:soma-examples/scheduling/target/classes:soma-runtime/target/soma-runtime-1.0.0-SNAPSHOT.jar' \
  io.github.somaruntime.benchmarks.scheduling.StandardFjspBenchmarkMain
```

它复用同一个immutable model，每次solve创建fresh `SomaGroup`，分别报告runtime initialization与纯
dispatch median，并在最后执行完整结果校验。它不是JMH替代品或公开SLA。

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

`frontier` workload 是更广泛的性能前沿矩阵。它以同一确定性 schema/distribution 分离四个
fresh-JVM family，并让每个 timed operation 在发布结果前验证独立期望值：

| Family | Direct source / derived shape | 主要操作 |
|---|---|---|
| `frontier-source` | Table、Field、IndexSelection、mapped primitive/reference | count、typed/callback filter、sum、Key lookup、Index residual、map、materialize、sequential/parallel |
| `frontier-stateful` | Table、primitive/reference Field | distinct、stable sort/limit、top、skip/limit、sequential/parallel |
| `frontier-relation` | Table-derived Group/Relation | low/high-cardinality GroupBy、Equality/Semi/Anti Join、typed predicate、sequential/parallel |
| `frontier-mutation` | point Table、mutable Selection | point update、selection update/remove、post-publication Table/Index verification |

默认只运行 `soma-auto`。`frontier-source` 与 `frontier-stateful` 可显式加入 `manual` 和
`java-stream`，用于同语义 raw-array/Java Stream 相对基线；只有 Java Stream baseline 输出 parallel
字段，manual 只拥有 sequential hardware-near 下界。Relation 与 mutation 没有伪造的通用 baseline。
10K 用于 breadth，1M 用于 profile/throughput，10M 用于 bandwidth/memory/capacity，三者
不是可以相互替代的规模曲线：

```sh
SOMA_BENCHMARK_WORKLOAD=frontier \
SOMA_BENCHMARK_SCENARIOS="frontier-source frontier-stateful" \
SOMA_BENCHMARK_IMPLEMENTATIONS="manual java-stream soma-auto" \
SOMA_BENCHMARK_ROWS=1000000 \
SOMA_BENCHMARK_PARALLELISM=16 \
./scripts/benchmark.sh
```

Relation/mutation 或 10M 重路径应显式只选择需要的 family，避免把不相关 setup、Profile 和 JVM
生命周期混进同一次结果。当前正式结论与 claim boundary 在治理关闭后由 Conformance 拥有；本节只
定义 executable harness。

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

需要按 type-kernel operation 记录 JVM Java allocation、SOMA observed temporary high-water、heap
used/committed 与 GC delta 时，显式设置 `SOMA_BENCHMARK_MEMORY_ATTRIBUTION=1`。该模式会在计时完成后
额外重放一次相同 query，并以 1 ms 采样 managed-memory snapshot；只用于诊断，不进入默认 latency
基线，也不把 allocation bytes 解释成 live memory：

```sh
SOMA_BENCHMARK_WORKLOAD=kernel \
SOMA_BENCHMARK_SCENARIOS=type-kernel \
SOMA_BENCHMARK_MEMORY_ATTRIBUTION=1 \
./scripts/benchmark.sh
```

内存字段按以下边界解释：

- `AllocatedBytes` 是 caller 与 SOMA `ForkJoinPool-*` participant thread 的累计 Java allocation
  traffic，不是同时存活对象，也不包含 JIT/compiler 与 sampler thread；
- `PeakTemporaryBytes` 是 SOMA resource admission 的保守 reservation high-water，不等于 JVM 实际
  创建了同量对象；
- `HeapUsed*` 是未强制 full GC 的瞬时 heap usage，`HeapCommitted*` 是 JVM 已提交容量，两者都不是
  Table retained size；
- process RSS 还包含 JVM/native/code cache/thread stack/JFR 等，不得用 `RSS - retainedBytes` 推导
  temporary object；
- ingest 是 one-shot mutation journey；query 的 attribution 是计时完成后的等价 replay。

正式归因合同、10M evidence、低分配优化与剩余边界见
[内存归因与低分配执行治理记录](../project/conformance/v1-memory-attribution-low-allocation-governance.md)。

正式比较必须使用相同 commit/source、JDK/JVM、workload、row count、parallelism 和 fresh-JVM
run count；Smoke 只证明测量链可运行，不能证明性能提升。当前证据、scale/memory边界与使用准则见
[性能与正确性联合治理记录](../project/conformance/v1-performance-correctness-governance.md)。
固定10M的Design、Execution、Memory、CPU四维归因、GroupBy/Relation优化和当前parallel架构边界见
[四维性能架构治理记录](../project/conformance/v1-four-dimensional-performance-architecture-governance.md)。
Operator × Type × Distribution 矩阵、cost-aware RLE、Bound cardinality、Field materialization 与固定
1M/10M证据见[类型与分布性能资格记录](../project/conformance/v1-operator-type-distribution-performance-qualification.md)。
Table、Field、IndexSelection与主要派生operation的10K/1M/10M矩阵、最终Profile优化、fixed-host
memory attribution、使用准则和剩余边界见
[全面性能前沿资格记录](../project/conformance/v1-performance-frontier-qualification.md)。

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

日常开发使用的固定 M5 Pro、Java 8、10K/1M `core` profile 与重放命令见
[`baselines/development-m5-pro-java8`](baselines/development-m5-pro-java8/README.md)。该 baseline
只保护三项 reference workload 的代表性正常路径；10M、完整 frontier、Profile 与内存归因仍由专门
性能治理按需触发，不进入 hosted CI 的严格毫秒 Gate。
