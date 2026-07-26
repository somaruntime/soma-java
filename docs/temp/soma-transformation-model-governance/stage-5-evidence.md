# Stage 5 正确性与性能证据

类型：Temporary

状态：Stage 5 candidate

Owner：SOMA Transformation Model governance

事实范围：构造契约、reference differential、generated footprint、component
performance 与 P2 physical decision

非事实范围：正式产品能力、跨环境性能声明和 public release readiness

最后审查日期：2026-07-27

## 1. 按构造即正确

生产代码拥有正确性；测试只证明防线有效。当前闭环如下：

| 关键抽象/不变量 | 唯一 Owner | 事实产生处的防线 | 代表性证据 |
|---|---|---|---|
| Expression/Parameter 的 carrier、presence、source、parallel trait | typed Expression、`ParameterSlot` | immutable expression；跨 source、缺参、类型错误在 Definition/Invocation 边界拒绝 | Slice F、reference differential |
| Shape、lineage 与 operator legality | shape-specific DSL handle | 只有合法 shape 暴露对应 operator/terminal；Join absence 与 writable lineage 不由 generic node 恢复 | Slice B/F、public API golden |
| Definition identity 与 source/output/effect 唯一性 | `DataFlowDefinition`、one-shot `Builder` | length-prefixed canonical identity；source/parameter/output 冲突和 effect fan-out 在 publish 前关闭 | Slice F |
| Template/Invocation lifecycle | immutable `DataFlowTemplate`、one-shot `DataFlowInvocation` | bind/parameter 不覆盖；compatibility、budget、context、guard 和 terminal state 使用真实失败 | Foundation、Slice D/F、external consumer |
| Context、executor 与 shared budget | `DataFlowContext`、`ExecutionAccounting` | managed/borrowed ownership、active invocation、worker/task/scratch 上限在 owner 处关闭 | Slice D/F |
| generated binding 与 Table lifecycle | processor emitter、generated v5 bridge | schema/protocol/hash/root ownership、epoch 和 acquire/release fail closed | codegen admission、access phase 3 |
| detached Result 与 safe-point Effect | shape-specific Result、generated commit boundary | Result 构造不公开非法状态；Delta 使用 validated factory；Effect 只在 freeze 成功后 commit | Slice C/F |
| Candidate scratch 上界 | `CandidateProgram` | `skip/limit` 下推为 streaming selection capacity；任何推导上界失配使用真实 internal failure，不依赖 `assert` | reference differential、Effect allocation lane |

当前 production path 没有依赖可关闭的 Java assertion。这里不是遗漏：候选上界、
lineage、parallel eligibility、publish 与 lifecycle 若失配都可能损坏语义，必须
保留真实 failure；只有关闭断言后仍绝对安全的纯推导事实才允许使用 `assert`。

测试组合为：

- 48 个固定 seed trial 的 plain-array reference differential，覆盖 Candidate、
  Projection/Reduction/Prefix、Partition/Combine、Group、四种 Join 和 Window；
- sequential/parallel result identity；
- 一个真实外部 Maven/Java 8 consumer，同时消费 direct Access 与 generated
  `<Table>DataFlow`；
- public `javap`、schema/hash/codegen、one-companion-per-Table footprint；
- 少量 canonical Slice A–F 与 15-lane component performance evidence。

没有为每个方法复制 null/lifecycle 用例，也没有冻结 private helper 或内部数组
布局。

## 2. Generated footprint

clean compile 保持每张 Table 恰好一个 DataFlow companion：

| Surface | Table/companion | source bytes / lines | family class bytes / nested class |
|---|---:|---:|---:|
| neutral benchmark | 6 / 6 | 68,269 / 684 | 232,402 / 61 |
| industrial scheduler | 9 / 9 | 100,661 / 987 | 363,990 / 91 |
| grassing simulation | 2 / 2 | 21,780 / 224 | 77,658 / 20 |

每项 Gate 上限为冻结 candidate 的 `+15%`。本证据只证明 generated footprint；
grassing source、设计和应用验证不在本专题实施范围。

## 3. Component 方法

`DataFlowComponentBenchmark` 使用 Zulu JDK 8、macOS aarch64、`-Xms256m
-Xmx512m`，3 个独立 JVM fork，每 fork `32` warmup、`64` measurement。
workload 为 4,096/65,536 行，并分离：

- authoring/compile；
- direct Candidate Scan 与 DataFlow bind+execute；
- sequential/parallel count、Projection 和 integral Reduction；
- GroupBy、inner hash Join、finite Window；
- shared graph 与单行 safe-point Effect。

artifact 记录环境、strategy、workers、allocation、GC、平均 timing、
per-Invocation p50/p90/p99/max 和 checksum，
`claimAllowed=false`。Parallel allocation 覆盖全部 live Java thread；sequential
allocation 只覆盖 benchmark thread。第一次校准发现 Effect checksum 为零后只修正
一次 evidence identity；promotion 自审发现批次平均不能冒充 tail 后，在最终
immutable runner 上补齐 latency distribution 并只运行一次最终 3-fork
calibration。没有失败自动重跑、缩小 workload 或放宽阈值。

## 4. 结果与归因

最终 3-fork median：

| Lane | allocation B/op | median ns/op | 结论 |
|---|---:|---:|---|
| authoring + compile | 32,116 | 52,174 | 非 hot-path，一次构建可由 Template 复用 |
| direct filter count, 65,536 | 150 | 83,199 | Candidate Scan 仍是最低抽象税路径 |
| DataFlow filter count sequential | 1,670 | 254,589 | 无 per-row object，但通用表达式/Invocation 有固定税 |
| DataFlow filter count parallel | 2,092 | 90,023 | 大规模已接近 direct lane |
| sum 4,096 sequential / parallel | 1,750 / 2,222 | 35,389 / 87,158 | 小规模不应默认并行 |
| sum 65,536 sequential / parallel | 1,750 / 2,211 | 366,600 / 109,320 | 大规模 fixed-tree reduction 有收益 |
| Projection sequential / parallel | 526,006 / 264,761 | 446,294 / 184,826 | allocation 由 detached output 主导；parallel exact-sized output 更小 |
| GroupBy sum | 2,104,102 | 1,037,352 | 线性 hash/member scratch，无 Full GC |
| inner Join count | 1,312,550 | 847,900 | left-driven hash join，无 materialized pair output |
| Window sum | 329,382 | 399,043 | finite offset/member traversal，无 retained state |
| shared graph | 528,171 | 488,512 | shared terminal 只求值一次 |
| update one | 1,838 | 8,349 | bounded selection 后不再分配整表 `int[]` |

所有 lane 三 fork checksum 全等，Full/unknown GC 为零。Group 最大 Young GC 为
`2`，已按 `max + 1` 建立本机 Gate；每个 lane 的 invocation p90 以校准最大值
`×1.50` 建立 tail Gate。

feasibility 首次暴露 `limit(1)` Effect 仍分配约 `264 KiB/op`。根因是
`CandidateProgram.select()` 在应用 `limit` 前先复制整个 source。唯一修复将
`skip/limit` 上界下推到 streaming selection，分配降至约 `1.8 KiB/op`，同时
reference differential 与 Effect atomicity 保持通过。

## 5. P2 physical decision

| 候选 | Stage 5 裁决 | 理由 |
|---|---|---|
| scalar / fixed-tree integral reduction | 保留两者，adaptive 选择 | 4,096 行顺序更快，65,536 行 parallel 更快 |
| default parallel admission | 最低 cardinality 设为 65,536 | 这是当前全部被测 parallel kernel 都有收益的保守证据点；调用者仍可显式覆盖 |
| branchy / branchless | 保留 branchy scalar | 当前没有 selectivity/hardware 证据支持额外 kernel 与 code size |
| Join | 保留 stable left-driven hash join | equi Join 语义和当前成本闭合；无 Merge/Sort-Merge workload 前不增加路径 |
| Group/Partition | 保留 hash + buffered stable representation | in-place partition 会改变已冻结 order/lineage，且当前没有收益证据 |
| Window | 保留 invocation-local finite offsets | 不引入 retained incremental state |
| vectorization | 不引入显式 vector kernel | Java 8 无受支持的 portable Vector API；由 JIT 在当前 primitive loop 上决定 |

P2 没有改变 Transformation 语义、public/generated signature、annotation Schema、
RuntimePlan、ownership、Index 生命周期或失败原子性。

## 6. Stage 5 退出条件

- 构造不变量、Owner、防线和代表性证据已闭合；
- reference/golden/external consumer/footprint 已闭合；
- component baseline 为只读 3-fork Gate，不形成跨环境 claim；
- accidental full-source Effect scratch 已消除；
- parallel crossover 已作保守默认裁决；
- 未发现需要改变冻结 Product/Access/Transformation 目标的 P0/P1 问题。

正式 Design、Implementation Map、Engineering、Conformance 与 Report 仍由
Stage 6 原子固化；本文件随 Temporary 一并退役。
