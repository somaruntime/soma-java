# SOMA V1 Operator × Type × Distribution 性能资格与第二阶段架构优化

类型：Conformance / Performance Qualification and Architecture Optimization Record

状态：`PASS`

日期：2026-08-09

Owner：正常使用路径的物理 kernel breadth、数据分布敏感性、固定 1M/10M 资格、第二阶段内部优化与 claim boundary

## 1. 结论

本专题在三个 reference application 的长期场景证据之外，新增一个只属于
[`benchmarks/`](../../benchmarks/README.md) 的 synthetic type-kernel consumer，系统覆盖 V1 已承诺、
但真实场景没有完整触达的 logical type、operator 与数据分布组合。最终结论为 `PASS`：

- byte/short/char/int/long、float/double、String、Enum、nested `@SomaValue` 和 ordinary Object
  的代表 physical kernel 都有 deterministic fingerprint；
- sparse/medium/dense filter、低/高基数、null、skew 与重复 Join match 均完成正常路径资格；
- 1M breadth lane 使用 3 个 fresh JVM，所有 fingerprint 稳定，相同环境回归 ratchet 为 `PASS`；
- 10M hotspot lane 在 `-Xms8g -Xmx24g`、16 GiB SOMA budget、AUTO、P16 下完整通过，peak RSS
  约 10.39 GiB，无 OOME、OS kill、major page fault 或 fingerprint drift；
- AUTO RLE admission 从“只比较压缩字节”修正为同时要求平均 run length 至少为 4，nested Value
  Equality Join 的 1M 中位数从 553.693 ms 降至 385.329 ms，改善 30.4%；
- floating GroupBy canonical sum scratch 从全输入规模收窄为最大实际 group size，保持原 conservative
  lease 与 canonical numeric tree；
- Bound Plan 现在复用同一 StateRoot 的 Index bucket/distinct statistics，Field materialization 也按 Field
  shape 而非完整 detached Table row 估算；由此关闭两项会错误拒绝正常 10M 查询的 resource gap；
- sequential floating reduction 在本 workload 中仍快于 P16，证明“更多 worker”不是当前通用解法；
  本专题没有引入自动并行、共享 mutable cursor 或 public tuning hint。

本记录不修改 Blueprint、正式 Design、public/generated API、dependency、production artifact 或
release authorization。它不构成一亿行资格、跨硬件 SLA、GitHub Release/Package、Maven publication、
签名或正式 release 声明。

## 2. Evidence boundary

| 项目 | 1M breadth | 10M hotspot |
|---|---|---|
| Baseline | `develop@76fe661a4518ca3ac83fbd2aa97c38f6f302fe0c`；benchmark fixture 已存在、runtime 尚未优化 | 同 baseline；正常路径因 conservative false rejection 未产生 latency baseline |
| Qualified tree | 本记录所在 commit | 本记录所在 commit |
| Host | Apple M5 Pro，48 GiB physical memory | 同左 |
| OS | macOS / Darwin arm64 | 同左 |
| Java / Maven | Amazon Corretto `1.8.0_502` / Maven 3.9.16 | 同左 |
| JVM | `-Xms2g -Xmx8g -XX:+UseParallelGC` | `-Xms8g -Xmx24g -XX:+UseParallelGC` |
| SOMA budget | 6 GiB | 16 GiB |
| Rows / mode | 1,000,000 / AUTO / P16 | 10,000,000 / AUTO / P16 |
| Sampling | 3 fresh JVM；1 warmup + 3 samples | 1 fresh JVM；无 warmup + 1 qualification sample |
| Correctness | kernel assertion + per-operation fingerprint + shared fingerprint | 同左 |

1M before/after 使用同一机器、JDK、JVM、row count、parallelism、sampling 与 workload。10M 用于确认
architecture/resource closure 和 scale correctness，不把单次绝对 latency 写成稳定性能 claim。Raw JSONL、
environment、rusage、GC/profile 与 summary 只保留在本机临时 evidence；长期可重放入口是
[`scripts/benchmark.sh`](../../scripts/benchmark.sh)。

## 3. 覆盖矩阵

| Kernel family | Representative | 正常操作 | Distribution / proof |
|---|---|---|---|
| Narrow integral | byte/short/char/int/long | Field projection、typed filter、sum | 周期分布；exact expected sum |
| Floating reduction | float/double | sum、average、sequential/P16、GroupBy sum | finite deterministic values；canonical fingerprint |
| Intrinsic reference | String/Enum | Index、isNull、distinct、stable order、GroupBy | null、低基数、skew |
| Structural Value | nested `RouteKey` | Index、high-cardinality GroupBy、Equality Join | 复合 equality、duplicate right matches |
| Opaque reference | ordinary Object | storage、projection、`toArray()` | pooled identity + null；detached container |
| Selectivity | indexed/non-indexed reference key | filter 后 GroupBy | 约 1% / 50% / 99% |

该矩阵按 physical kernel family 取代表，不穷举 Type × Operator × Source 的笛卡尔积。三个正式
reference application 继续拥有业务编排与 mutation journey；synthetic consumer 不成为第四个 Example、
第三 production artifact 或公开 benchmark claim。

## 4. Profile 归因与 retained optimization

### 4.1 Cost-aware RLE admission

Baseline CPU profile 将 nested Value Equality Join 的主要额外开销归因到 `RleColumn.raw()`：测试数据的
部分 long leaf 每两个 row 重复一次，RLE 虽能节省约 25% column bytes，但每次随机访问都需要在大量 run
上做二分定位。旧 AUTO policy 只比较 representation bytes，因此接受了“空间略省、访问显著变慢”的编码。

新 admission 同时要求：

```text
encoded bytes < plain bytes
AND average run length >= 4
```

这仍是 AUTO 的 internal physical policy，不是公开 codec 合同。长重复 run 继续使用 RLE；平均长度 2/3
的边际压缩退回 plain integral column。1M retained bytes 从 295,482,896 增至 296,465,456，增加约
0.3%，换取 Value Join 中位数改善 30.4%。AUTO 相对 OFF 的 1M diagnostic 仍节省约 8% retained bytes，
但某些随机访问路径仍可能慢于 OFF；因此 `_metadata()`/profile 继续是高级归因入口，不能把 AUTO
描述成每个 workload 都最快。

### 4.2 Floating GroupBy scratch

旧 canonical floating sum 为每次 GroupBy 创建一个按全部 selected values 大小的 scratch，再逐组复用。
新实现先读取已完成的 group counts，只按最大实际 group size 创建 scratch。pairwise block/tree、NaN/
Infinity 与 encounter-order numeric semantics 均未改变；resource manager 仍持有原 conservative lease，
因此这是实际 allocation peak 的收窄，不是 resource failure 合同放宽。

当前 1M floating GroupBy latency 变化为 -0.5%，属于噪声范围；本记录只把它认定为 `PROVED_MEMORY_BOUND`，
不制造 latency improvement claim。

### 4.3 Bound cardinality statistics

10M 第一次执行在：

```java
indexedField.distinct().sorted(...).toArray();
```

正常路径 fail closed：真实 distinct 结果只有 65 个，但旧 `BoundRowPlan` 只传播 Table size 与 slice，按
10M materialized results 估算 23,250,000,064 bytes，而可用 budget 为 15,362,897,536 bytes。

修复后，Bound Plan 在 terminal-start binding 后读取同一 immutable StateRoot：

- Index source 使用 bucket posting count 作为 exact source upper bound；
- indexed Field distinct 使用 Index `distinctCount`；
- Key distinct 复用 unique membership 上界；
- statistics 缺失时继续使用原 conservative input upper bound；
- skip/limit/distinct 仍按 logical stage order传播 upper bound。

这实现了既有 Planning Design 的 exact/upper cardinality Owner，不增加 public hint，也不偷跑 scan 或
application callback。

### 4.4 Field-shape materialization cost

10M 第二次执行越过 distinct 后，在 ordinary Object Field `toArray()` 被拒绝：真实结果是 10M 个
application-owned reference，旧通用 path 却按 10M 个完整 detached Table object 估算
22,290,000,032 bytes，可用 budget 为 15,361,849,472 bytes。

现在 Row materialization 继续使用完整 detached-row estimate；schema-known Field materialization 使用
Field leaf width + recursive Value allowance + container estimate。String、Enum、ordinary Object 复用原
application reference，不重复计入对象 payload；nested Value 仍有 conservative recursive allowance。
结果仍在 callback/data work 前完成 checked arithmetic 和 temporary lease，fail-closed 语义没有放宽。

## 5. 1M before / after

以下为 3 个 fresh JVM run 的中位数：

| Metric | Baseline | Final | Delta |
|---|---:|---:|---:|
| Ingest | 617.565 ms | 597.232 ms | -3.3% |
| Integral kernels | 302.023 ms | 306.516 ms | +1.5% |
| Floating sequential | 174.482 ms | 174.010 ms | -0.3% |
| Floating P16 | 197.511 ms | 193.496 ms | -2.0% |
| Index/null probes | 2.461 ms | 2.592 ms | +5.3% |
| Reference distinct/order | 51.619 ms | 52.253 ms | +1.2% |
| Object materialization | 42.824 ms | 41.914 ms | -2.1% |
| Value high-cardinality GroupBy | 94.267 ms | 95.583 ms | +1.4% |
| Sparse GroupBy | 13.952 ms | 13.561 ms | -2.8% |
| Medium GroupBy | 57.618 ms | 55.855 ms | -3.1% |
| Dense GroupBy | 98.779 ms | 95.009 ms | -3.8% |
| Floating GroupBy | 79.529 ms | 79.134 ms | -0.5% |
| Value Equality Join | 553.693 ms | 385.329 ms | **-30.4%** |
| Process CPU | 12.49 s | 11.62 s | -7.0% |
| Process wall | 9.57 s | 8.69 s | -9.2% |
| Peak RSS | 2603.2 MiB | 2598.0 MiB | -0.2% |

除 Value Join 外的变化均未同时超过既有 15% / 2 ms regression threshold；正式 `compare.py` 为
`PASS`。Baseline/final shared fingerprint 完全一致。

## 6. 10M qualification

最终 10M normal-path result：

| Evidence | Result |
|---|---:|
| Ingest | 6.491 s |
| Integral kernels | 3.184 s |
| Floating sequential / P16 | 1.821 s / 1.959 s |
| Object materialization | 443.172 ms |
| Sparse / medium / dense GroupBy | 141.492 / 587.993 / 1022.115 ms |
| Floating GroupBy | 967.014 ms |
| Value Equality Join | 4.313 s |
| Retained bytes | 1,818,019,712 |
| Peak RSS | 10,388.4 MiB |
| Process CPU / wall | 44.96 s / 38.39 s |
| Average CPU cores | 1.171 |
| Major page faults | 0 |

所有 operator fingerprint 汇总为同一 deterministic shared fingerprint `8686166277882177054`。两次
中途 `RESOURCE_LIMIT_EXCEEDED` 都是预期的 fail-closed 行为，但其 conservative bound 对正常结果不够
精确；修复的是 planning/resource architecture，不是通过扩大 budget 隐藏问题。

## 7. Design / Execution / Memory / CPU 结论

| Dimension | 本轮事实 |
|---|---|
| Design | 用户只描述 typed operation；Index statistics 和 Field shape 应由 Bound Plan 自动利用，不要求 cardinality/memory hint |
| Execution | Value Join 的逻辑工作没有变化；RLE random-access physical cost 进入 encoding admission |
| Memory | scratch、materialization 与 result cardinality 分别由实际 group、Field shape 和 published statistics 约束 |
| CPU | P16 floating 在 1M/10M 均慢于 sequential；总进程只使用约 1.17–1.34 cores，主要剩余工作并不在已准入的 parallel prefix |

因此本轮不做“让所有 operator parallel”的局部补丁。Relation/Group 的真正扩展仍需要 participant-local
cursor/view、deterministic partition/merge、first-failure 和 bounded resource protocol；它只能由未来独立
架构专题准入，不能由本轮 benchmark 直接授权。

## 8. Correctness 与验证

Retained changes 保持：

- reference、optimized sequential、parallel 的 logical result与 canonical order；
- String/Enum/Value equality、null、duplicate Join match 与 first encounter Group order；
- signed-128 integer aggregate 与 canonical floating reduction；
- callback scope、one-shot lifecycle、terminal-start binding 与 structured failure；
- StateRoot、Key/Index/compression/accounting atomic publication；
- callback/data work 前的 checked resource admission；
- Java 8、exactly runtime/processor 两项 production artifact、无新 dependency。

Targeted evidence 包含 marginal short-run RLE、Index source/distinct bound、Field-shape materialization
pressure，以及既有 runtime differential/cumulative suite。最终资格重放 full-regeneration、runtime/
processor、三个 reference application、benchmark consumer、local package 与 source-delivery。

最终可重放结果：

- runtime：60 tests，0 failure/error；
- processor：34 tests，0 failure/error；
- type-kernel：1M × 3 fresh JVM 与 10M × 1 fresh JVM，全部 fingerprint `PASS`；
- default core benchmark：18 records / 6 groups，三个 reference application 全部 `PASS`；
- `scripts/check.sh`、`scripts/qualify.sh`、package/SBOM/checksum/provenance、`git diff --check`：`PASS`。

## 9. 当前使用准则与后续边界

1. AUTO 保持默认；它以总体内存与访问成本折中，不承诺每个查询都快于 OFF。
2. 为业务 lookup 建立的 Index 也能向 planner 提供 exact bucket/distinct cardinality；用户不需要额外 hint。
3. 只需要 scalar/result count 时优先 terminal aggregate，不要先物化完整 `List/array`。
4. 大型 Field `toArray()` 已按 Field shape admission，但结果数组本身仍是真实内存成本；application 应只在
   确实需要 detached random access 时使用。
5. `parallel()`继续显式启用，并以真实 workload sequential/Pn 对照决定；P16 不是默认更快。
6. 下一轮可观察候选包括 participant-local Relation/Group parallel、compression-aware random-access
   kernel 与更精细的 generated logical shape cost；它们当前不是 active debt，也没有预建 surface。
7. 一亿行仍是架构愿景；本记录证明的是当前机器上 1M breadth 与固定 10M normal-path qualification。

## 10. Replacement closure

| Temporary responsibility | Stable Owner | Disposition |
|---|---|---|
| Type/operator/distribution matrix | benchmark source + 本记录 | PROMOTED |
| 1M/10M evidence boundary | 本记录 | PROMOTED |
| RLE、floating scratch、Bound cardinality、Field materialization | runtime code + targeted tests | PROMOTED |
| Java 8 full-regeneration route | benchmark POM + stable script | PROMOTED |
| exhaustive Cartesian matrix | 不进入长期 suite | REJECTED BY SCOPE |
| automatic/blanket parallel | 不进入 implementation | REJECTED BY EVIDENCE |
| bounded Temporary | 删除 | RETIRED |

本记录关闭本专题；当前没有 active bounded topic。
