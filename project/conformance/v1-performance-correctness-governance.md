# SOMA V1 性能与正确性联合治理

类型：Conformance / Performance and Correctness Governance Record

状态：`PASS`

日期：2026-08-09

Owner：三个 reference application 的长期 benchmark、正常路径正确性复核、profile 驱动优化、
相对回归判定与当前性能结论边界

## 1. 结论

本专题在不改变 V1 Blueprint、public/generated API、两项 production artifact topology、dependency
与 release authorization boundary 的前提下完成收口：

- 建立了长期、非 production 的 [`benchmarks/`](../../benchmarks/README.md) 工程；
- scheduling、simulation、real-time-dispatch 在 100K、1M、AUTO/OFF、P1-P16 与最终资格中保持
  scenario assertion、shared fingerprint 和 SOMA logical fingerprint 一致；
- runtime 56 tests、processor 34 tests、三个 reference application、百万行 benchmark、local package、
  SBOM、checksum 与 provenance 全部通过；
- 未发现正常使用路径或核心不变量上的 correctness blocker；
- 修复一个稳定 typed `top(k)` 的主要算法热点，simulation 百万行 `top` 从 180.863 ms 降至
  最终资格的 12.822 ms，改善 92.9%；
- 删除 parallel typed-prefix 扫描中的每行 iterator allocation；allocation profile 证明原
  `ParallelRowScheduler -> ArrayList$Itr` 分配链消失；
- 建立相同环境 before/after 的相对回归 ratchet，不提交本机绝对 SLA；
- 明确了 sequential/parallel、Index、compression、materialization 与 memory 的当前使用准则。

当前产品 implementation 和 G1-G10 状态仍为 `PASS`。本记录不授权 GitHub Release/Package、
Maven publication、签名、正式 release 声明、一亿行承诺或跨硬件性能 SLA。

## 2. Evidence boundary

正式 baseline 与最终资格使用：

| 项目 | 值 |
|---|---|
| Baseline | `205f79c0ba86cf6367a369f2ff43613f311c6bea`，clean tree |
| Qualified implementation | `3e19bca137d8a59258853da507443cddf4e24d11`，clean tree |
| Host | Apple M5 Pro，18 core，48 GiB physical memory |
| OS | macOS 26.6.1 / Darwin arm64 25.6.0 |
| Java | Amazon Corretto 8.502.07.1，`1.8.0_502` |
| Maven | 3.9.16 |
| JVM | `-Xms2g -Xmx8g -XX:+UseParallelGC` |
| SOMA budget | 6 GiB |
| Qualification workload | 1,000,000 rows，P8，3 fresh JVM runs，2 warmups + 5 measured samples |
| Governance ceiling | P16 / 32 GiB；本轮没有接近 32 GiB allocation |

数字仅适用于上述环境和 fixture。Raw JSONL、environment、summary、JFR、collapsed stack 与 flame
graph 是本机生成证据，不提交为 distribution artifact；可由 `scripts/benchmark.sh` 重放。

## 3. 长期 benchmark 与正确性 spine

[`benchmarks/`](../../benchmarks/README.md) 独立消费三个正式 reference application，不把 profile
main 放回 Example test source，也不成为第三项 production artifact。它提供：

- 每个 scenario / implementation / run 一个 fresh JVM；
- deterministic workload、显式 seed/row count/parallelism、奇数样本与 min/median/max；
- manual、SOMA AUTO、SOMA OFF 三种 implementation；
- scenario assertion、跨 implementation shared fingerprint 与 SOMA representation-independent
  logical fingerprint；
- machine-readable `results.jsonl`、`summary.json`、Markdown summary 与 environment provenance；
- JFR、async-profiler CPU/allocation、collapsed stack 和 flame graph；
- 同机同 workload 的相对比较器 `benchmarks/tools/compare.py`；
- 失败前删除旧 summary，避免陈旧 PASS 被误认为本轮结果。

正式运行顺序按 run 交替 implementation 与 scenario 方向，减少固定顺序和热状态偏差。Smoke、
diagnostic、comparative 与 qualification 被明确区分，单次 profile 不再承担性能 claim。

## 4. 正确性复核

### 4.1 Scenario 与 execution equivalence

以下 evidence 全部为 `PASS`：

| 证据 | 覆盖 |
|---|---|
| 100K / 1M manual、AUTO、OFF | 三场景业务断言与 shared fingerprint；AUTO/OFF logical fingerprint |
| P1 / P2 / P4 / P8 / P16 | 三场景 sequential/parallel logical result 一致 |
| runtime 56 tests | reference/optimized、query、numeric、mutation、resource、relation、parallel、compression |
| processor 34 tests | schema validation、generation、public surface、scale、parallel/compression surface |
| `scripts/check.sh` | reactor、generated consumer、Examples、package 与 source-delivery |
| `scripts/qualify.sh` | 上述全部加百万行 profile、package/SBOM/checksum/provenance |

本轮优化路径新增或重放了 typed stable top 的 reference/optimized differential、ascending/descending、
stable tie、`k=0/1/2/17/64/65/oversize`、filter/top/skip、parallel 与 callback exactly-once；bounded
heap 使用 `(typed order, encounter ordinal)` 比较，不把 heap 完成顺序泄漏为 encounter order。

### 4.2 核心不变量结论

- typed top 只替换已经由正式 Design 允许的 physical algorithm，不改变 logical plan 或 public API；
- callback Comparator 的调用合同不被 bounded typed top 路径接管；
- parallel iterator 修改只删除 Java collection iterator allocation，不改变分片、guard、worker、merge
  或 publication 语义；
- AUTO/OFF representation 在三场景中产生相同 logical fingerprint；
- mutation、StateRoot/Key/Index/accounting atomic publication、resource lease、Group guard、detached result
  等未改路径由现有 cumulative tests 与最终 qualification 重放；
- 未发现需要修改 Blueprint/Design、failure contract 或资源可见性的正确性问题。

本专题没有把罕见非法 schema、reflection/Unsafe 绕过封装、application data race 或 callback side
effect 扩张为重复审查面。

## 5. Profile 与优化结论

### 5.1 Stable typed top

Baseline CPU profile 中，simulation 的 `top` 通过完整 stable merge sort，约占当次采样的 31%，
百万行 AUTO 中位数为 180.863 ms。实现加入 package-private `StableTopLocatorHeap`：当 typed order
紧邻 limit 且 `k` 相对 source 足够小时，先做 O(n log k) bounded selection，再只稳定排序 k 个结果；
其他 plan 与 callback Comparator 仍走原路径。

最终 clean qualification：

| Metric | Baseline | Final | Delta |
|---|---:|---:|---:|
| Simulation AUTO top | 180.863 ms | 12.822 ms | -92.9% |
| Scheduling AUTO top | 0.065 ms | 0.031 ms | -52.2%，绝对值很小 |

最终 CPU profile 可见 `collectBoundedTypedTop -> StableTopLocatorHeap`，不再出现 baseline 的完整
TimSort/merge-sort 热点。该优化保留，因为端到端收益、正确性与复杂度边界同时成立。

### 5.2 Parallel per-row allocation

Allocation profile 显示 parallel typed-prefix 每处理一个 locator 都由 enhanced-for 创建
`ArrayList$Itr`。实现改为 indexed list access。修改后：

- 原 `ParallelRowScheduler -> ArrayList$Itr` profile 链在三场景中消失；
- 剩余 scheduler allocation 位于每 terminal 的 range buffer/merge，而不是每 row；
- 三场景百万行结果、fingerprint 与 P1-P16 curve 均保持一致；
- 吞吐没有被包装成超出噪声的性能 claim，本修改以确定的 allocation removal 为成立依据。

### 5.3 未继续修改的热点

最终 profile 的主要成本回到 repeated point add 的 atomic StateRoot publication、Index maintenance、
managed-memory accounting、compression work 与 GC。V1 明确没有 Batch/Loader；贸然加入 mutable bulk
path 会触及 publication、failure、memory 与 API Design。因此本专题没有通过弱化原子合同、加入
benchmark 特化或预建 Loader 来追求 ingest 数字。

若未来真实项目证明百万行初始化对象和 repeated add 是主要业务瓶颈，应作为独立 Loader/ingest
专题重新完成 Design、failure/resource contract 和 differential evidence，而不是在现实现上补丁化。

## 6. 百万行性能与回归 ratchet

最终 `scripts/qualify.sh` 在 `3e19bca` 产生 18 条 raw record、6 个 summary group，所有 correctness
与 fingerprint 校验通过。相对 baseline 的 SOMA AUTO 判定如下：

| Scenario | Scan | Parallel scan | Join | Top / Group | 结论 |
|---|---:|---:|---:|---:|---|
| Real-time dispatch | 33.166 -> 32.341 ms | 34.247 -> 33.488 ms | 10.081 -> 10.221 ms | - | stable |
| Scheduling | 37.825 -> 38.786 ms | 42.397 -> 43.292 ms | 12.118 -> 12.424 ms | group 31.722 -> 34.318 ms | within ratchet |
| Simulation | 28.444 -> 28.989 ms | 25.652 -> 26.411 ms | - | top 180.863 -> 12.822 ms | major improvement |

`compare.py` 默认只有相同 workload/fingerprint 下同时超过 15% 与 2 ms 的退化才失败。最终资格
before/after 为 `PASS`。双阈值避免把微秒指标和日常抖动当作产品回归；调用方可以针对稳定的专用
runner 显式收紧。Repository 不提交当前机器的绝对 threshold 或 SLA。

## 7. Parallel scaling

百万行 SOMA AUTO、3 fresh JVM runs 的 parallel scan 中位数：

| Scenario | P1 | P16 | P1 -> P16 |
|---|---:|---:|---:|
| Real-time dispatch | 31.678 ms | 33.103 ms | +4.5% |
| Scheduling | 37.437 ms | 40.876 ms | +9.2% |
| Simulation | 27.408 ms | 23.156 ms | -15.5% |

当前三个 scan kernel 都较轻；更多 participant 只在 simulation 获得有限收益，在另两个场景中调度与
merge 成本抵消计算收益。这支持现有产品合同：sequential 是默认值，`parallel()` 必须显式，并应
按真实 pipeline、row count 和共享 executor 负载测量后启用。SOMA 不应自动把所有 query 并行化，
也不应仅依据 core 数决定 parallelism。

## 8. Compression 与 memory

百万行 AUTO 的 SOMA-managed memory：

| Scenario | Retained | Representation | Plain equivalent | Representation saving |
|---|---:|---:|---:|---:|
| Real-time dispatch | 375.5 MB | 40.9 MB | 48.8 MB | 16.1% |
| Scheduling | 202.2 MB | 40.8 MB | 41.7 MB | 2.0% |
| Simulation | 160.7 MB | 40.8 MB | 44.7 MB | 8.8% |

一次独立 extended-RSS diagnostic 的 peak RSS 为：dispatch 1778.4 MiB、scheduling 884.0 MiB、
simulation 855.7 MiB（AUTO）。RSS 包含 JVM heap layout、GC、generated/application object 与测量开销，
不能等同 SOMA retained bytes。

同一 clean commit 的 AUTO/OFF 三次对照显示 AUTO ingest 约比 OFF 慢 37%-45%，而 representation
节省依 schema 分布而变化。该结论不改变已裁决的 AUTO 默认值：普通用户继续使用 AUTO；只有实际
profile 证明 mutation/ingest latency 优先且内存有余量时，维护者才显式选择 OFF，并重新验证真实
workload。压缩结果通过 `_metadata()` 观察，不根据猜测提前微调。

10M diagnostic 中 scheduling P1 成功完成：retained 约 1.715 GB、ingest 49.7 s、scan 419.0 ms、
Join 124.0 ms、GroupBy 934.7 ms；随后宿主只剩约 640 MiB 可用物理内存，下一场景 JVM 被操作系统
终止。因此 10M 不是本轮 qualification，也不构成 SOMA OOM 或 10M claim。未来扩大规模应使用
空闲内存明确、隔离的专用 host，并逐级重放，不能在繁忙开发机上把 OS kill 误判成引擎结果。

## 9. 当前最佳实践

以下是当前 executable evidence 的维护者准则，可在未来用户文档专题中投影，但不是新的 Design：

1. 已知规模时先 `reserve(expectedRows)`，随后使用 V1 canonical repeated atomic add；不要自行绕过
   Table publication。
2. 只为真实稳定 access pattern 建 Key/Index。Index lookup 在本轮约 0.013-0.226 ms，而全 scan
   约 26-39 ms；但每个 Index 都增加 ingest、retained memory 与 mutation 维护成本。
3. 优先 typed expression，让 optimizer 可以做 Index substitution、predicate analysis 与 specialized
   execution；application callback 是语义 barrier，不要期待同等优化。
4. 小 k 排名优先使用 typed `top(k, order)`，无需手写全量 materialization/sort；需要完整有序结果时
   才承担完整 sort 成本。
5. 只需要 count/sum/min/max/findFirst 等 scalar 时，不先 `toList()`/`toArray()`；避免无必要 detached
   result 与 temporary memory。
6. sequential 保持默认；只有较重 pipeline 和实测收益时显式 `parallel()`。Executor 为 SOMA 全局
   共享配置，application 负责容量与生命周期。
7. compression 默认 AUTO。OFF 是 profile 后的高级取舍，不是普遍更快的推荐默认值。
8. memory budget 同时为 retained state、terminal temporary、JVM/application headroom 留余量；
   `_metadata()` 观察 managed/representation，RSS 用进程工具单独确认。
9. 同一 SomaGroup 的外部 mutation 仍由 application 串行化；不同 Group 的并发边界由 application
   设计，parallel terminal 不改变逻辑上的单线程语义。
10. 用 `scripts/check.sh` 做日常 correctness；性能变更用同机 fresh-JVM before/after；高成本百万行
    与 packaging 只在 `scripts/qualify.sh` 或明确资格节点重放。

## 10. 验证与 replacement closure

最终执行：

- `mvn -pl soma-runtime test`：56 tests，0 failure/error；
- `scripts/check.sh`：`PASS`；runtime 56、processor 34、Examples、package 与 source delivery 闭合；
- `scripts/qualify.sh`：`PASS`；百万行 18 records / 6 groups、package、SBOM、checksum、provenance；
- 100K 与 1M manual/AUTO/OFF、P1/P2/P4/P8/P16、CPU/allocation、extended RSS diagnostic；
- `compare.py` 正向 before/after `PASS`，反向 top regression 能稳定 `FAIL`；
- `git diff --check`、Markdown relative-link、stale Temporary route 与 committed build artifact 检查。

Replacement closure：

| Temporary responsibility | Stable Owner | Disposition |
|---|---|---|
| benchmark surface 与运行合同 | `benchmarks/`、`scripts/benchmark.sh` | PROMOTED |
| relative performance ratchet | `benchmarks/tools/compare.py` | PROMOTED |
| correctness/optimization/memory/scale evidence | 本 Conformance 记录 | PROMOTED |
| best-practice candidates | 本记录第9节；未来 docs 只做角色投影 | PROMOTED WITH BOUNDARY |
| 未准入 Loader、AUTO policy 改写、自动 parallel | 不进入代码或 Design | REJECTED / DEFERRED BY EVIDENCE |
| bounded Temporary | 删除 | RETIRED |

本记录关闭本次性能与正确性联合治理，不取代既有 I0-I8 provenance，也不扩大 G9、release 或
publication claim。
