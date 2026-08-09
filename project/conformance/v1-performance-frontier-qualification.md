# SOMA V1 全面性能前沿资格

类型：Conformance / Fixed-host Performance Frontier Qualification

状态：`PASS / FIXED_HOST PERFORMANCE FRONTIER QUALIFIED`

日期：2026-08-10

Owner：SOMA V1 direct source、operator、composition、规模与 profile 的综合性能事实，accepted
optimization、使用准则、剩余性能边界与 claim boundary

## 1. 结论

本专题在不改变 Blueprint、正式 Design、public/generated API、dependency、production artifact、
numeric/order/resource/failure/publication 合同或 release boundary 的前提下，完成了 SOMA V1 迄今覆盖
最广的一轮性能探索、正确性复核与实现优化：

- 新增长期 `frontier` benchmark，以同一确定性 schema/distribution 覆盖 Table、Field、
  IndexSelection、mapped primitive/reference、stateful query、GroupBy、Join 与 mutation；
- 在 10K、1M、10M 三个规模分别观察固定开销、steady-state throughput 与 bandwidth/capacity；
- 使用 manual loop、Java Stream、SOMA AUTO/OFF、sequential/explicit parallel、三个 reference
  application 与三个 10M composed journey 建立互补证据；
- 使用 async-profiler CPU/allocation、GC/rusage、SOMA managed-memory observer 与 correctness fingerprint
  将问题路由到 Design、Execution、Memory、CPU 四个 Owner；
- 保留三项正常路径优化：direct primitive Field 的 representation-aware bulk traversal、borrowed View
  的 demand-driven lazy leaf read、integral primitive natural sort 的 JDK kernel lowering；
- 1M direct primitive Field `sum/filter/materialize` 分别降低 `84.5%/69.9%/79.5%`，callback Table
  filter、mapped primitive/reference 分别降低 `61.4%/41.0%/52.2%`；
- 1M low/high-cardinality GroupBy 与 filtered Join 分别降低 `41.0%/30.6%/25.9%`；
- 1M primitive Field `sort/limit` 与 `top` 从 `94.527/94.669 ms` 降至 `8.977/8.730 ms`，
  分别降低 `90.5%/90.8%`；10M 对应路径从约 `764/765 ms` 降至 `90/90 ms`；
- 最终 10M source、stateful、relation、mutation 和 composed journeys 全部 fingerprint `PASS`，
  major page fault 为 0，16 GiB SOMA managed budget 内完成；
- 最终 10M frontier composition retained 为 `1,817,973,200` bytes，query 前后不增长；retained、temporary
  reservation、Java allocation、heap 与 RSS 已分责测量。

结论不是“SOMA 已被证明为所有 workload 上世界最快”。简单 raw loop 仍是硬件下界，ingest、point
mutation、Relation/Group 的 participant-local parallelism、compression-aware random access 与 Index
rebuild 仍有明确空间。当前可以成立的声明是：**SOMA V1 已在固定 Apple Silicon/Corretto 8 环境中，
对 10K/1M/10M 的主要正常路径完成 performance-frontier qualification，具备低分配、资源受控、
结果可验证并可继续优化的 production-performance 基础。**

本记录不是跨硬件 SLA、一亿行资格、Linux/x86/512 GiB 服务器资格、GitHub Release/Package、Maven
publication、签名或正式 release 声明。

## 2. Evidence boundary

| 项目 | 固定事实 |
|---|---|
| Input baseline | `develop@e6b594421e19b08365933ccad14762325bb76256` |
| Candidate | 本记录所在 commit |
| Host | Apple M5 Pro，48 GiB physical memory |
| OS | macOS / Darwin arm64 |
| Java | Amazon Corretto `1.8.0_502` |
| Maven | Apache Maven `3.9.16` |
| GC | `-XX:+UseParallelGC` |
| 10K / 1M heap | `-Xms2g -Xmx8g` |
| 10M heap | `-Xms8g -Xmx24g` |
| SOMA budget | 1M/10K 为 6 GiB；10M 为 16 GiB |
| Parallelism | P16；scaling 另测 P1/P2/P4/P8/P16 |
| Stable sample | 10K/1M 为 3 个 fresh-JVM run，每个 operation 内部 warmup 后取 median；10M 为固定主机单 run、1 warmup + 3 samples |
| Profile | async-profiler CPU/allocation JFR + collapsed stack + flame graph；GC log；extended rusage；managed-memory attribution |
| Correctness | 每个 measurement 计算独立 expected value/fingerprint；不通过只计时不消费结果的路径 |

绝对 latency 是 fixed-host evidence。10M 使用单个 fresh-JVM run，因此只用于 capacity、复杂度趋势与
热点确认，不构成跨机器稳定延迟 SLA。raw JFR、flame graph、JSONL 与 log 位于 ignored temporary
输出，不提交仓库；可重放 source、script 与 result schema 已提交。

## 3. Coverage closure

### 3.1 Source 与 operation family

| Family | 覆盖的正常路径 | 结果 |
|---|---|---|
| Table source | count、typed/callback filter、map、top、slice、materialization、sequential/parallel | PASS |
| Primitive Field | sum、filter、materialize、distinct、sort/limit、top、sequential/parallel | PASS |
| Reference Field | projection、distinct、materialization、sequential/parallel | PASS |
| Key / IndexSelection | 10K point get、exact Index count、residual filter、post-mutation verification | PASS |
| Mapped primitive/reference | mapToLong aggregate、reference map/materialize、callback barrier、parallel | PASS |
| Group result | low/high cardinality GroupBy、primitive aggregate、parallel surface | PASS |
| Relation | Equality Join count、typed side filter、semi/anti、scalar pipeline、parallel surface | PASS |
| Mutation | point update、Selection update/remove、subsequent Table/Index fingerprint | PASS |

10K 还由既有 type-kernel 和 qualification consumer 覆盖 boolean、byte、short、char、int、long、float、
double、String、Enum、nested `@SomaValue` 与 ordinary Object；1M/10M 只保留会改变 physical kernel、
allocation 或 relation behavior 的代表性形态。没有为 benchmark 发明新的 public source 或 operation。

### 3.2 Scale responsibility

| Rows | 本轮责任 | Closure |
|---:|---|---|
| 10,000 | public family breadth、固定开销、small-data latency、parallel overhead、baseline shape | 24 个 fresh-JVM records，全部 PASS |
| 1,000,000 | cache/steady-state、CPU/allocation profile、Pn scaling、matched before/after | 主要 family 3-run stable，全部 PASS |
| 10,000,000 | bandwidth、temporary、heap/RSS、AUTO/OFF、Join/Group/mutation、真实组合 | source/stateful/relation/mutation + 3 composed journeys，全部 PASS |

## 4. Final performance map

### 4.1 10K：固定成本边界

10K 下 SOMA 的 plan、guard、resource admission 与 generated carrier 固定成本可见；这是正常产品合同
成本，不应通过弱化正确性消除。最终 representative median：

| Operation | Manual | Java Stream | SOMA AUTO | Interpretation |
|---|---:|---:|---:|---|
| Field sum | 0.041 ms | 0.109 ms | 0.182 ms | SOMA 固定成本约 0.14 ms |
| Field filter | 0.055 ms | 0.048 ms | 0.256 ms | small-data 不适合 explicit parallel |
| Field sort/limit | 0.157 ms | 0.382 ms | 0.384 ms | 最终 kernel 已接近 Stream |
| Field top | 0.155 ms | 0.239 ms | 0.306 ms | 正常 bounded result |
| Table typed filter | 0.062 ms | 0.120 ms | 0.425 ms | Table View/IR 合同成本仍高于 raw loop |
| Index exact count | N/A | N/A | 0.011 ms | sidecar 路径避免 Table scan |

manual/Java Stream baseline 不拥有 SOMA 的 StateRoot、Index、resource preflight、structured failure 与
canonical numeric 全部合同；它们只提供 hardware/JDK 方向性下界。

### 4.2 1M：最终正常路径

| Family | Operation | Final median |
|---|---|---:|
| Table | typed / callback filter | 19.451 / 23.136 ms |
| Field | sum / filter / materialize | 5.637 / 15.568 / 8.043 ms |
| Index | exact count / residual | 0.238 / 0.478 ms |
| Point | Key get 10K probes | 3.159 ms |
| Mapped | primitive / reference | 22.144 / 16.174 ms |
| Stateful Field | distinct / sort-limit / top | 4.546 / 8.977 / 8.730 ms |
| Stateful Table | top / slice | 15.413 / 2.526 ms |
| GroupBy | low / high cardinality | 23.513 / 63.842 ms |
| Relation | Join count / filtered / semi / anti | 167.008 / 295.181 / 127.151 / 180.234 ms |
| Mutation | point update 10K / Selection update / remove | 510.598 / 36.730 / 845.631 ms |

primitive sort/top 最终与本轮 manual/JDK baseline（约 `6.9/7.4 ms`）处于同一数量级；simple Field sum
相对 raw loop（约 `0.22 ms`）仍有明显差距，原因包含 encoded Chunk traversal、operation lifecycle、
numeric accumulator 与 representation abstraction，不能宣称 hardware-near。

### 4.3 10M：capacity 与复杂度

| Family | Operation | Final median | Scale observation |
|---|---|---:|---|
| Source | Field sum / filter / materialize | 57.644 / 158.340 / 77.977 ms | 相对 1M 近线性 |
| Stateful | distinct / sort-limit / top | 45.041 / 90.456 / 90.009 ms | 最终 kernel 近线性 |
| Table stateful | top / slice | 142.350 / 24.625 ms | bounded materialization |
| GroupBy | low / high cardinality | 200.445 / 648.041 ms | cardinality 成本明确 |
| Relation | Join count / filtered / semi / anti | 1.674 / 3.002 / 1.303 / 1.740 s | 仍为主要 CPU/lookup hotspot |
| Mutation | Selection update / remove | 180.396 ms / 9.378 s | dense remove/sidecar rebuild 是主要 mutation hotspot |
| Construction | frontier ingest | 5.46–5.86 s | repeated atomic add/index/compression 成本可见 |

所有 10M family 与三个 composed journeys 均为 correctness/fingerprint `PASS`、major fault `0`。
三项 composed journey 的 broad Join、high GroupBy、primitive pipeline、mapped reference、Selection
update/remove 共同证明微基准收益没有隐藏端到端回归。

## 5. Accepted optimization ledger

### 5.1 Representation-aware direct primitive Field traversal

**Profile finding**：Field pipeline 经 generic row locator、borrowed View、callback mapper 和逐值 directory
dispatch；simple sum 主要时间没有用于 numeric work。

**Change**：`PrimitivePlan` 保留 schema-known root Field identity，execution 在一次 bound operation 内将
direct primitive Field lowering 为 `TableChunk.visitPrimitive`；PLAIN、encoded 与 overlay 各自拥有正确
解码，RLE 可按 run 遍历。普通 callback/mapped path 不复制此特例。

**Effect**：1M Field sum `36.364 -> 5.637 ms`，filter `51.734 -> 15.568 ms`，materialize
`39.316 -> 8.043 ms`，distinct `34.426 -> 4.5 ms` 量级。encoded、overlay update、remove/tail
materialization tests 保证 representation-independent result。

### 5.2 Demand-driven borrowed View leaf read

**Profile finding**：Table callback、mapped、Group/Relation pair path 每次进入 View 都 eagerly 将宽 row 的
全部 physical leaves 解码进 `TypedValues`，即使 callback 只读一个 Field。

**Change**：`GeneratedQueryCursor` 只绑定 bound `TableChunk + offset`，generated View accessor 在 scope
和 thread 校验后按需读取目标 leaf；View 仍是 borrowed、callback-scoped、复用且不可逃逸。

**Effect**：1M callback filter `59.992 -> 23.136 ms`，mapped primitive `37.516 -> 22.144 ms`，
mapped reference `33.846 -> 16.174 ms`，low/high GroupBy `39.834/91.966 -> 23.513/63.842 ms`，
filtered Join `398.181 -> 295.181 ms`。这是 Information Demand 对 physical work 的直接约束，不是
benchmark-only shortcut。

### 5.3 Integral primitive natural sort lowering

**Profile finding**：boolean/byte/short/char/int/long 的 value-only stream 使用 SOMA 通用稳定 recursive
merge sort 和同尺寸 scratch；1M Field top 约 95 ms，而 JDK/raw 基线约 7 ms。

**Change**：integral primitive value stream lowering 到 Java 8 `Arrays.sort(long[])`；raw 值对
byte/short/int 保持 signed extension、char 保持 non-negative、boolean 为 0/1、long 保持原值。等值
primitive 没有可观察的随行 identity，stable tie 不改变结果。float/double 继续使用 SOMA 的
`Float/Double.compare` total-order stable merge，不偷换 NaN、signed-zero 语义。

**Effect**：1M sort-limit `94.527 -> 8.977 ms`、top `94.669 -> 8.730 ms`；10M 相对上一候选的
`763.995/765.211 ms` 降至 `90.456/90.009 ms`。新增 signed min/max differential test，reference
interpreter 与 optimized result 相同。

## 6. Profile diagnosis after optimization

最终 CPU profile 已不再显示“所有 Field 都经过 generic row decode”或 integral merge sort 为主要热点。
剩余 samples 集中于：

- encoded RLE random value decode；
- `GeneratedTableLayout.joinFieldEquals` 与 relation equality evaluation；
- `TableChunkDirectory` locator-to-chunk lookup；
- nullable/reference equality 与 `IdentityHashIndex` probe；
- ingest 的 `IdentityHashIndex.prepareAdd`、rehash、Chunk finish/encode 与 immutable publication；
- stateful/reference result buffer、distinct set 与 detached materialization 的必要 arrays。

allocation profile 证明 direct scalar Field terminal 不按 row 分配 View/Value；主要 allocation 来自结果
array、stateful hash/buffer、parallel membership、Index growth 与 repeated immutable StateRoot publication。
因此本专题停止局部 patch：下一步收益需要 Relation/Group parallel、codec/random-access cost model、
bulk construction 或 mutation/index rebuild 的独立架构专题，而不是继续在通用 query path 堆特例。

## 7. Memory attribution

最终 10M frontier attribution：

| Fact | Value | Meaning |
|---|---:|---|
| Final SOMA retained | 1,817,973,200 B（约 1.69 GiB） | frontier composition 中 Table physical representation、directory、Key/Index 与 engine-owned state |
| Source peak RSS | 7,392.2 MiB | 整个 JVM process resident pages |
| Stateful peak RSS | 7,598.2 MiB | 同上；不是 Table 或 temporary object size |
| Ingest participant allocation | 7,199,385,208 B | 累计 allocation traffic；不是 simultaneous live set |
| Ingest peak temporary reservation | 1,733,581,064 B | fail-closed conservative admission high-water |
| Field sum allocation / reservation | 12,816 B / 0 B | scalar direct Field 为低分配 streaming terminal |
| Field sort allocation / reservation | 80,020,408 B / 640,016,448 B | actual value buffer vs conservative resource upper bound |
| Field materialize allocation / reservation | 160,011,888 B / 160,000,032 B | detached result 主导 |
| Reference distinct allocation / reservation | 382,059,776 B / 960,018,496 B | object/result/hash shape 的保守上界 |

每个 query 的 retained before/after 都是 `1,817,973,200 B`；没有 query 导致 persistent retained growth。
`peakTemporaryBytes` 是 resource admission 的逻辑上界，不代表 JVM 同时分配同量对象。heap used 没有在
每个 operation 前强制 full GC，RSS 还包含 heap committed、native JVM、JIT/code cache、class metadata、
thread stack、profiler 与 page residency。禁止使用 `RSS - retained` 命名“临时对象”。

## 8. Parallel execution boundary

1M P1/P2/P4/P8/P16 scaling 显示：

- typed Table filter 从 P1 约 `19.5 ms` 到 P16 约 `16.3 ms`，收益有限；
- simple direct Field sum 的 explicit `parallel()` 反而承担 scheduler/availability 固定成本；最终
  sequential `5.637 ms`，parallel `8.869 ms`；
- filtered Join P16 不优于 sequential（最终约 `305 ms` vs `295 ms`）；10M 也基本持平；
- process average CPU 多数为约 `1.1–1.8 cores`，不是机器缺 CPU，而是当前 physical plan 大多没有
  participant-local Relation/Group work 和 deterministic merge。

SOMA 的合同是 explicit `parallel()` 允许最多 P participants，不承诺每个 terminal 一定创建 P 份工作。
当前正确做法是：默认 sequential；只有 profile 证明 source/filter/primitive composition 有稳定收益时显式
`parallel()`；不要用 P16 label 推断 16 核已被利用。未来真正并行 Relation/Group 需要 participant-local
state、partition/build/probe、资源准入与 canonical merge 的整体设计。

## 9. AUTO / OFF boundary

相同 10M source A/B：

- AUTO/OFF Field sum `57.644/54.422 ms`，materialize `77.977/79.271 ms`，主要 scan 已接近；
- OFF ingest `4.604 s`，AUTO `5.457 s`，当前 distribution 下 encoding/representation choice 增加
  construction 成本；
- callback/mapped 路径差异约在个位到十几个百分点，受 codec random access 与 run distribution 影响；
- 两侧 fingerprint 相同，AUTO 仍是正式默认。

不因为一套 synthetic distribution 将默认改成 OFF。普通用户保持 AUTO；只有真实 workload profile
证明 ingest/random-access trade-off 不合适时才显式 OFF。codec、Index、Join/Group cost model 仍是内部
优化空间。

## 10. Correctness closure

本轮不穷举伪造 marker 等非正常使用，而是围绕优化可能改变的核心不变量验证：

- every benchmark operation 具有 independent expected value/fingerprint；10K/1M/10M 全部 PASS；
- direct primitive Field 在 PLAIN、encoded、overlay update 与 remove/tail materialization 后一致；
- signed integral sort 包含 `Integer.MIN_VALUE/MAX_VALUE`，并与 boxed reference interpreter 差分；
- lazy View 保留 scope/thread/epoch currentness，callback、mapped、Group 与 Relation journeys 结果不变；
- sequential/parallel、AUTO/OFF、Index/residual、post-mutation Table/Index fingerprint 一致；
- mutation 后 size、payload、Key/Index、compression/accounting 同 generation；
- numeric、resource preflight、one-shot carrier、structured failure 与 atomic publication 的既有 Gate
  evidence 由 changed-surface tests 和最终 qualification 重放承接；
- full-regeneration 重新生成 runtime consumer、三个 examples 与 benchmark generated surface；没有用
  stale generated source 拼接新 runtime。

最终 full repository check/qualification、runtime/processor tests、三个 reference application、local
package/SBOM/provenance、Markdown route 与 `git diff --check` 均为 `PASS`；精确命令见稳定入口
`./scripts/check.sh`。

## 11. 使用准则

1. 已知容量时先 `reserve()`；当前 repeated atomic `add()` 正确但 construction 成本明显。
2. 能由 schema-known Field 表达的 scalar、filter、materialize、distinct/sort/top 优先使用 Field source；
   它允许 leaf pruning、primitive specialization 与 Chunk-aware execution。
3. 能用 typed expression 表达的条件不要先退化成 callback；typed predicate 才能被分析、Index substitute
   或安全下推。
4. exact point result 使用 Key `get/find`；重复值选择使用 IndexSelection，避免 scan。
5. 只读取 View 中真正需要的 Field；当前 lazy access 会按 Information Demand 避免宽 row 全量解码。
6. `parallel()` 必须由真实 profile 证明收益；simple Field scan、Join/Group 当前通常 sequential 更好。
7. AUTO 是默认；OFF 是 profile 后的高级选择，不是通用“更快”开关。
8. `toArray()/toList()/distinct/sort/top/Join/Group` 会产生 result/scratch；配置 memory budget 时同时考虑
   retained 与保守 temporary admission，不用 RSS 反推。
9. 对 mutation-heavy journey，优先减少不必要的 remove/rebuild 周期；一次 Selection remove 当前会维护
   Table、Key/Index 与 representation 的原子一致性。

## 12. Remaining performance roadmap

按证据优先级保留以下方向，但本专题不预建 API、module 或第二 runtime path：

1. **Relation/Group participant-local parallel architecture**：当前最大 CPU utilization gap；需要独立
   logical partition、local state、memory admission 与 deterministic merge 设计。
2. **Compression-aware random access / Join kernel**：RLE raw decode、directory lookup、reference equality
   与 Index probe 是最终 relation profile 的主要 Owner。
3. **Construction 与 Index build/rebuild**：10M ingest 约 5–9 s；需要由百万级真实 import 证据决定是否
   重新准入 Loader/bulk construction，不能静默弱化 per-add atomicity。
4. **Dense Selection remove**：10M 约 9.4 s；physical compaction、locator movement、Index/representation
   rebuild 需要整体算法优化。
5. **Temporary bound precision**：例如 primitive sort actual allocation 约 80 MB、reservation 约 640 MB；
   可在不降低 fail-closed 上界的前提下让 planner 获得更精确 shape proof。
6. **Target-host qualification**：Linux/x86/512 GiB、NUMA、100M North Star 与真实 application traffic
   当前均 `NOT_EVALUATED`。

这些是有 Owner 的 future work，不是当前 V1 correctness defect，也不构成未关闭的 Temporary。

## 13. Performance frontier gates

| Gate | Result | Evidence |
|---|---|---|
| PF-1 Coverage | PASS | Table/Field/IndexSelection + mapped/stateful/Group/Relation/mutation matrix |
| PF-2 Measurement integrity | PASS | fixed env、fresh JVM、warmup/sample、fingerprint、raw JSONL/profile |
| PF-3 Correctness equivalence | PASS | reference/optimized、sequential/parallel、AUTO/OFF、post-mutation |
| PF-4 Operator efficiency | PASS | 10K/1M/10M、manual/Stream、matched before/after |
| PF-5 Memory efficiency | PASS | retained/temp/allocation/heap/RSS 分责，10M attribution |
| PF-6 CPU/parallel efficiency | PASS_WITH_BOUNDARY | async profile + P1/P2/P4/P8/P16；Relation/Group parallel gap 已明确 |
| PF-7 Stability/regression | PASS | 3-run stable、reference applications、10M composed journeys、existing soak regression |
| PF-8 Claim boundary | PASS | fixed-host only；production host/100M/public SLA 明确排除 |
| PF-9 Replacement closure | PASS | stable harness/report/route 晋升；Temporary 删除 |

## 14. Replacement closure

| Temporary responsibility | Stable Owner | Disposition |
|---|---|---|
| source/operator/scale matrix | `benchmarks/` source、README、本记录 | PROMOTED |
| stable command/result schema/profile routing | `scripts/benchmark.sh`、benchmark support | PROMOTED |
| accepted production optimization | runtime code + changed-surface tests | PROMOTED |
| fixed-host results、best practices、limitations | 本记录 | PROMOTED |
| raw JSONL/JFR/flame/log | ignored `/tmp` evidence | NOT_COMMITTED |
| target-host/100M/public SLA | 本记录 claim boundary | NOT_EVALUATED / NOT_CLAIMED |
| bounded coordination plan | former Temporary | RETIRED |

本记录生效后，`project/temp/`恢复为空；未来性能专题必须从本记录的 measured boundary 出发，不得把
旧 profile、单次最快值或开发机 RSS 提升为新的产品合同。
