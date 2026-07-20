# 第二轮 runtime 性能优化与机会审查报告

状态：passed（诊断 evidence；不构成性能 claim）
日期：2026-07-17
唯一 Owner：`soma-benchmarks`（benchmark evidence）；实现事实仍分别归属 `soma-runtime-core` 与 `soma-processor`
Capability：`V1-COLUMN-ACCESS`、`V1-KEYED-IDENTITY`、`V1-PERFORMANCE-SHAPE`、`V1-EVIDENCE-TOOLING`、`V1-SCENARIO-BENCHMARK`

> 当前性说明（2026-07-20）：本文记录 packed/exact v3 切换前的第二轮诊断；其中 selector dirty/rebuild CPU 热点已由 `4b6fa43` 消除。当前实现和同机 A/B 见 [2026-07-17 专题收口报告](../../reports/2026-07-17-packed-exact-index-runtime-redesign-report.md)，本文只保留历史归因证据。

本报告记录第二轮不改变 public/generated API、Schema、Owner、Gate 或 runtime 业务语义的性能审查与实现。本轮 FJSP record 均固定 `claimAllowed=false`；单机单次数据只用于定位分配来源与验证优化方向，不建立跨机器吞吐、延迟、内存、G6 support matrix、RC 或 release readiness 声明。

## 1. 审查结论与本轮选择

优化前 JFR 显示两个可以在现有契约内安全消除的 allocation source：

- `AbstractColumnView.<init>` 每次获取 view 都重新生成 `field.column`、`field.column.isPresent` 与 typed getter operation；FJSP 因结构变更频繁获取新 view，JFR TLAB 采样归因合计 `488,861,888` bytes / 659 events。
- singleton keyed append 仍创建批内去重 KeySpace；单行 batch 不存在批内重复的可能，但仍必须执行 key domain guard 和主 KeySpace duplicate lookup。

本轮实现：

1. 每个 concrete ColumnView kind 持有一个内部开放寻址 operation cache，以 generated schema field literal 为 key，首次绑定通过 immutable array snapshot 发布，命中路径无锁、无分配且不使用 Java Collection。绑定后复用三条完全相同的诊断 operation；lifecycle、pin、stale/released error 与 operation 字符串不变。
2. generated primitive、enum、single-leaf value、String 与 composite key append 在 `batch.size()==1` 时只执行 domain/canonicalization 与主 KeySpace lookup；多行 batch 继续使用原 staged KeySpace，保留批内重复检查和原子失败路径。

`GeneratedColumnAccess` 及 schema-specific generated API 的签名均未改变。operation cache 是 package-private runtime implementation；其正常 cardinality 由 generated-only bridge 传入的已加载 Schema field 数量约束。

## 2. A/B 方法

基线与优化后分别在独立 JVM 中执行相同 FJSP workload，并同时开启 JFR `profile`：

- workload：1,000 jobs × 100 operations，100 machines，3 candidates/operation，共 100,000 operations；
- seed：`1397706049`；dispatch rule：`effective-ready,fcfs,spt,identity`；
- JVM：Azul Zulu OpenJDK `1.8.0_492-b09`；
- JVM 参数：`-Xms512m -Xmx512m -Xmn96m -XX:+UseParallelGC -XX:+FlightRecorder`；
- OS：macOS `26.5.2` / Darwin `25.5.0`，`arm64` / JVM `aarch64`；
- A/B：`warmup=0`、`measurement=1`；timing 只作噪声敏感的诊断；
- allocation：`ThreadMXBean#getThreadAllocatedBytes` 对单线程 runner 做精确 phase delta；
- GC：`GarbageCollectorMXBean` phase delta；`PS Scavenge` 为 Young，`PS MarkSweep` 为 Full；
- JFR allocation event 采用 TLAB sampling，只用于归因，不当作精确 allocation total。

## 3. A/B 结果

| 指标 | 第二轮优化前 | 第二轮优化后 | 变化 |
|---|---:|---:|---:|
| import allocated bytes | 186,249,696 | 186,249,592 | 近似不变 |
| solve allocated bytes | 982,745,128 | 414,264,680 | **-57.85%** |
| export allocated bytes | 18,627,992 | 17,010,024 | -8.69% |
| total allocated bytes | 1,187,622,816 | 617,524,296 | **-48.00%** |
| allocated bytes/operation | 11,876.22816 | 6,175.24296 | **-48.00%** |
| Young GC count | 29 | 13 | **-55.17%** |
| Young GC collection time | 46 ms | 39 ms | -15.22% |
| Full GC count / time | 0 / 0 ms | 0 / 0 ms | 不变 |
| solve time（仅诊断） | 8,602.563 ms | 8,198.441 ms | -4.70% |

语义一致性：两侧均为 100,000 assignments、1,000 completed jobs、makespan `54571`、total tardiness `20509698`、checksum `-678377626428749715`；runtime plan hash 均为 `ccc11ac218fee7246abe25d0fba64376eb60681d8cbdf21c5a9db2e3d00e6758`。

相对第一轮报告最初基线的 `1,741,038,824` total allocated bytes，当前同规模诊断 record 为 `617,524,296`，两轮累计下降 `64.53%`。该累计值只表达同机同 workload 的优化方向，不升级为跨环境 claim。

A/B artifact SHA-256：

- before JSONL：`53fff07d7d585195a4274a1d8b4c4c746addab23c89752c456cc107365323f3a`；
- after JSONL：`0261342c16a3c8aa7bfebfa1da0b087386fc948fd3b107806750cfcb19f418a8`；
- before JFR：`3b3e8e752ab02181029af17bc81124bb1b66a9c856499bf66ac0847aec70a341`；
- after JFR：`875ec163975a47439314cf4faf33214ea444aef66e4bef05be81e3dd6f4601c9`。

## 4. JFR 归因复核

- 优化前 `AbstractColumnView.<init>` 为 659 个 sampled TLAB events、`488,861,888` attributed bytes；优化后该 allocation site 为 0 sampled events。`GeneratedColumnAccess.longView` 的 handle attribution 也从 `75,657,224` 降到 `4,276,640` bytes（-94.35%）；这是轻量数组 cache 使 JIT 更容易消除未逃逸 handle 的诊断现象，不代表 lifecycle contract 或所有调用点都变成零分配。
- `HashCompositeKeySpace.<init>` sampled attribution 从 `37,151,648` 降到 `20,307,792` bytes（-45.34%）；`releaseStorage` 从 `16,839,384` 降到 `2,288,272` bytes（-86.41%）。剩余事件包括真实 table KeySpace 和多行 staged validation，不能仅凭 site name继续删除。
- 优化后 573 个 main-thread execution samples 中，`MachineCandidateTable.sortSelector0/1` 合计 478 个。该采样说明当前 CPU 首要机会已经转为 selector sidecar 的重复全量排序，而不是本轮已处理的 allocation source。

## 5. 持续门禁

本轮新增或加强以下门禁：

- ColumnView acquisition：预热后 4,096 次获取/读取/关闭只允许 view handle 级分配，禁止诊断字符串分配重新进入热构造；
- singleton keyed append：预热后 2,048 次预构造单行 batch append 的当前线程分配上限为 8 KiB，防止 validation KeySpace 回归；
- primitive/value/composite/String singleton source shape 均要求存在 fast path；
- primitive、String、composite 多行 batch-internal duplicate 继续返回 `duplicate_key`，失败后 table facts 不变；
- public API manifest、generated API golden、Schema JSON/hash、locale/timezone determinism 继续由原 Gate 验证。

最终 `./scripts/check.sh` 内置、固定 `warmup=1`、`measurement=1` 的 `scripts/check-fjsp-allocation-gc.sh` 本机 record：

- total allocated bytes：`598,120,624`；allocated bytes/operation：`5,981.20624`；
- Young GC：18 次 / 41 ms；Full GC：0 次 / 0 ms；
- solve time：`8227.042916 ms`（仅诊断）；
- artifact SHA-256：`9907143b38fb71654aad399fdd4a4edd586265ba25d3e8b7d3d71dada80912a0`；
- schema、scenario、operation count、allocation observability、phase sum、numeric bytes/op、GC metrics 与 `claimAllowed=false` 全部通过。

## 6. 剩余性能机会与裁决

| 机会 | 当前证据 | 本轮裁决 | 后续约束 |
|---|---|---|---|
| selector sidecar 增量维护 | 478 / 573 main-thread CPU samples 位于两个全量 sort | 最高优先级，留作下一轮 internal refinement | 必须同时覆盖 append、remove/compaction、双 selector、rollback、scratch/stats 与一次 terminal 最多一次 rebuild；不改变 `@SomaIndex`/API |
| Row source / stage wrapper | `findByMachine` 与 `Rows.append` 仍有可见边界对象 | 保留在 Phase 5/G3/G5 internal refinement | 不能破坏 one-shot consumed semantics，不能引入 per-row object 或 metadata interpreter |
| 多行 append validation scratch 复用 | multi-row staged KeySpace 仍有 allocation | 保留在 Phase 2/5 G3 refinement | 必须保留 bulk budget、原子失败、collision/full-equality 与并发 ownership 边界 |
| ColumnView handle pooling | `LongColumnView` handle 仍是显著 allocation site | 不采用当前简单 pooling | closed/stale handle 被重新激活会破坏 lifecycle；若要做必须先有不可混淆的 generation/token 设计，不得偷偷改变 API 语义 |
| Batch/value/application object | MachineCandidateBatch、value materialization 仍有 allocation | 属于显式允许边界，先由场景 profile 决定 | runtime 不得以 DTO/object graph 作为 live hot storage；应用复用也必须保持 detached ownership |

## 7. Validation record

已通过：

- `soma-runtime-core` / `soma-processor` Maven tests；
- generated dense、keyed Phase 2、String breadth Phase 5 定向 Gate；
- ColumnView acquisition 与 singleton keyed append allocation fixtures；
- primitive、enum、value、String、composite key 语义、批内/表内 duplicate 与 atomicity；
- public API manifest（无 public/protected surface 变化）；
- Schema JSON/hash golden 与 repeat-generation determinism；
- JFR A/B、固定 FJSP allocation/GC Gate；
- `git diff --check`。

最终 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home ./scripts/check.sh` 全量通过，结尾为 `project-check: ok`，失败 0。unsupported javac lane 按既有规则因未提供 `SOMA_UNSUPPORTED_JAVAC` 而跳过；这不改变当前 full JDK 8 validation baseline，也不补足 G6 support matrix。

## 8. V1 scope non-regression

| 审计项 | 结果 |
|---|---|
| Capability 状态 | 本轮 capability 从 `in-progress` 回到既有 `evidenced` 层级，只增加 contract-preserving implementation/evidence；无 dropped、optional 或无目标 deferred |
| public/generated API 与 Schema | 未改变；`GeneratedColumnAccess`、schema-specific generated signature、Schema JSON/hash 均由 golden 证明一致 |
| runtime semantics | key canonicalization/domain/main-table duplicate、多行批内 duplicate、error operation、atomicity、ColumnView pin/stale/released/optional semantics 均保持 |
| 唯一 Owner / Gate | 未改变；未修改正式设计 Owner、Capability ledger 或 G0-G6 定义 |
| G6 / release claim | 未改变；本机 Java 8 evidence 不外推 support matrix，仍不声明 public RC/release readiness |
| 后续性质 | additive completion / internal refinement；不要求 consumer、public/generated API、Schema 或 canonical hot path migration/rewrite |

## 9. Known limitations

- 单机、单进程、单 measurement A/B 不能排除 JIT、OS 与 GC timing 噪声；solve time 的单次 -4.70% 不支持提升结论。
- JFR TLAB attribution 是 sampling evidence，不等于 ThreadMXBean 精确 total；site bytes只用于定位和复核热点消失方向。
- operation cache 的 bounded-cardinality 前提来自正式 generated-only construction bridge；application 不应手工用任意动态 field 字符串调用该 bridge。
- 当前 allocation counter 只覆盖 runner 当前线程；若 benchmark 并行化，必须升级为跨工作线程聚合。
- allocation/GC Gate 验证 evidence 可观测性与同机方向，不等于批准绝对 allocation budget、性能 SLA 或支持矩阵。
