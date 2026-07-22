# SOMA Access Model 与 Pipeline Stage 1 Evidence

类型：Temporary

状态：完成

Owner：SOMA Java Access Model / Candidate Scan 专题治理

事实范围：Stage 1 的当前 API、场景链、allocation、JFR、generated code size、oracle 与 FJSP 诊断证据

非事实范围：跨机器性能声明、Stage 2 已实现收益、release readiness

production 基线：`5217c27ae4a07a5ec8ec3b70ae92224aac5706d2`

最后审查日期：2026-07-22

## 1. 环境与证据边界

| 项 | 实际值 |
|---|---|
| JDK | Azul Zulu `1.8.0_492-b09` |
| Maven | `3.9.16` |
| OS | macOS `26.5.2` build `25F84` |
| architecture | `aarch64` |
| branch | `develop` |
| production commit | `5217c27ae4a07a5ec8ec3b70ae92224aac5706d2` |

Component v1 artifact 使用 commit 中原始 harness，属于 clean source-bound diagnostic。Component v2 为本 Stage 新增归因 lane 后生成：production code 仍在同一 commit，但 benchmark harness 有未提交修改，因此必须同时记录 harness checksum，不能称为 clean commit-bound release evidence。

所有 artifact 都包含 `claimAllowed=false`，只用于本专题设计决策。

## 2. 当前 generated 表示

代表性 `MachineCandidateRows` 当前包含：

- 一个 `*Rows` handle，持有 Table、Source、五组 parallel arrays 和 terminal state；
- `byte[] kinds`；
- `Predicate[] predicates`；
- `Comparator[] comparators`；
- `long[] counts`；
- `long[] seen`；
- 每次 intermediate 创建新的 `*Rows`；首个 stage 将五组数组一次扩到 capacity 4；
- 第 5 个 stage 将五组数组再次扩到 capacity 8；
- exact source 额外创建匿名 `Source` object；
- filter/count 创建 callback Cursor；sort 创建 comparator Cursor；
- snapshot/materialization 在 terminal 另有显式结果成本。

当前实现已经 fused 非 sort traversal、支持 stable arg-min、table-local IndexBuffer/sort scratch 和 one-shot failure；本 Stage 优化的是 plan/source/handle 表示，不是否定这些已有能力。

## 3. 四场景 CandidateAccess 分布

审查范围为当前 executable FJSP、VRP、Simulation、Game Java source。静态调用点按 source 到 terminal 计为一条 CandidateAccess；Table forwarding 与显式 `rows()` 都按其实际 stage 数统计，Key/Column/point/bulk 不混入。

| 场景 | 总调用点 | 0 stage | 1 stage | 2 stages | 3 stages | `>3` |
|---|---:|---:|---:|---:|---:|---:|
| FJSP | 10 | 8 | 1 | 0 | 1 | 0 |
| Simulation | 9 | 6 | 3 | 0 | 0 | 0 |
| VRP | 9 | 2 | 6 | 1 | 0 | 0 |
| Game | 7 | 2 | 3 | 2 | 0 | 0 |
| 合计 | 35 | 18 | 13 | 3 | 1 | 0 |

分布结论：

- `31/35` 调用点不超过 1 个 stage；
- 全部当前调用点不超过 3 个 stage；
- FJSP 唯一 3-stage 热链是 exact → filter → sort → limit → snapshot；
- 当前测试仍覆盖 5-stage 以上 deep chain，因此 overflow 不能删除；
- 场景分布支持 inline capacity 3，但不支持把最大 chain length 限制为 3。

FJSP refresh 的 updater 捕获 machine state 和 `ColumnView`；selection predicate 不捕获，comparator 由 dispatch rule 预创建。Component lane 使用 static callback object，以隔离 library plan allocation；application lambda capture 必须作为独立层解释。

## 4. Component allocation baseline

命令入口：

```text
./scripts/check-post-cutover-components.sh
```

v2 artifact：`target/post-cutover-components.0Oiwn3/post-cutover-components.jsonl`

Artifact SHA-256：`6a54ee16f96301d05ffa39495f4d198a4441c940654d58fddad33a0c29d439d7`

Harness SHA-256：

```text
f32fc542ef96312790070566dc9016e820e795730c76514dc7c577440519dfa4  PostCutoverComponentBenchmark.java
20a2ccb339cc5a556e23d08f1179244a06b574db8a246b515ce2371f8006f99f  PostCutoverComponentArtifactValidator.java
```

测量参数：4096 rows、64 exact groups、warmup 2000、measurement 5000、current-thread ThreadMXBean exact allocation。

| Lane | stages | terminal/output | allocated B/op |
|---|---:|---|---:|
| packed source | 0 | count | 83.7216 |
| packed filter | 1 | count | 346.6656 |
| exact source | 0 | count | 112.0736 |
| exact skip | 1 | count | 376.0736 |
| exact filter | 1 | count | 400.0736 |
| exact filter-skip-limit | 3 | count | 560.0736 |
| exact filter-skip-limit-filter | 4 | count | 640.0736 |
| exact filter-skip-limit-filter-skip | 5 | count | 1000.0736 |
| exact filter-sort-limit | 3 | IndexSnapshot size 1 | 648.0960 |
| exact filter-sort | 2 | materialized single | 1080.6736 |
| Key traversal | 0 | materialized first Key | 248.1328 |
| long Column traversal | 0 | primitive forEach | 564.7280 |

本次单进程纳秒值存在明显 lane/JIT 顺序噪声，不用于选择物理设计；Stage 2 throughput Gate 必须多 fork、随机化或拆分 lane。allocated B/op 才是本轮的主要定量输入。

原始 v1 clean harness artifact：`target/post-cutover-components.vGub1m/post-cutover-components.jsonl`，SHA-256 `cb3ce57e331228787321c31bc89c9598cfa0d2ee1c9c338037b3e04af932a344`。四条原始 lane 分别为 `116.1696`、`400.0736`、`648.0960`、`1085.3824 B/op`，与 v2 重合 lane 足以确认数量级稳定，但不构成统计置信区间。

## 5. Allocation 类型与 stack 归因

JFR 命令在同一 production/harness 上以 20,000 measurement iterations 运行，记录：

- `target/stage1-operation-pipeline/component-allocation.jfr`；
- SHA-256 `b9904f23ffe777a951e833b300229552281e91858558f3b66feedbc580061aeb`；
- 78 个 `ObjectAllocationInNewTLAB` 与 46 个 `ObjectAllocationOutsideTLAB` sample。

JFR 是 sampled attribution，不用于合计 bytes；ThreadMXBean artifact 用于精确 B/op。两者交叉得到：

| Sampled allocation | size | top generated/runtime stack | 解释 |
|---|---:|---|---|
| `MachineCandidateRows` | 80 B | constructor / `append` | root 与每个 derived handle |
| exact anonymous `MachineCandidateTable$1` | 32 B | `findByMachine` | exact Source wrapper |
| `byte[4]` | 24 B | `Rows.append` | stage kind capacity 4 |
| `Predicate[4]` | 32 B | `Rows.append` | predicate slots |
| `Comparator[4]` | 32 B | `Rows.append` | comparator slots |
| `long[4]` | 48 B | `Rows.append` | counts/seen slots |
| `Rows.Cursor` | 24 B | `Rows.count` | filter callback borrow |
| `MaterializationTracker` | 64 B | required materializer | terminal materialization layer |
| Value/Key objects | 24 B each sample | key materializer | detached object graph layer |
| `char[]` | 88 B sample | `AbstractColumnPipeline.<init>` | `field + ".values"` / callback operation string construction |

第 1 个 stage 的 `+264..288 B/op` 与五组 capacity-4 arrays、derived 80 B handle 和必要 Cursor 相符；第 2–4 个 stage 每个约 `+80 B/op`，与 derived handle 相符；第 5 个 stage 额外 `+360 B/op`，与新 handle 加五组 capacity-8 arrays 相符。

因此当前主要可控 library allocation 不是 candidate Index 本身，而是：

1. exact Source wrapper；
2. 过大的 `*Rows` per-stage handle；
3. 首 stage 五组 parallel arrays；
4. capacity 4 越界时五组同步扩容；
5. Column traversal 构造时的动态诊断字符串。

Snapshot、materialization、application callback capture 是不同责任层，不能被计入“plan 全部可消除”的收益承诺。

## 6. Generated source/class size

基于当前 `soma-examples/target` 的 33 张 generated table：

| 指标 | 当前值 |
|---|---:|
| `*Rows.java` 数量 | 33 |
| `*Rows.java` aggregate bytes | 684,384 |
| `*Rows.java` aggregate lines | 2,980 |
| 最大单文件 | 23,519 B |
| top-level `*Rows.class` aggregate | 698,598 B |
| nested `*Rows$*.class` 数量 | 231 |
| nested class aggregate | 258,668 B |
| Rows family class aggregate | 957,266 B |

代表性 `MachineCandidateRows.java` 为 23,311 B / 116 lines；top-level class 21,467 B，`Source` 588 B，`Cursor` 3,764 B，`MutableCursor` 5,641 B。

这不是“生成代码一定过大”的结论，而是 Stage 2 必须同时衡量 class count、bytecode 和 source size。把 Source specialization、更多 point API 与 compact executor直接展开到每张表，可能用 allocation 换 code size；设计必须限制这种转移。

当前四场景 schema 共 33 tables、8 secondary selectors；当前 selector leaf width 都是 1–2 个 primitive leaf。未发现 generated/source 中现有 `*Scan`、`*Cursor`、`*UpdateCursor`、`scan()`、`findIndex()` 或 `requireIndex()` 冲突；processor 仍必须在通用 schema 上执行保留名和 collision admission。

## 7. FJSP integrated baseline

命令：

```text
SOMA_FJSP_JVM_ARGS='-Xms512m -Xmx512m -Xmn96m -XX:+UseParallelGC' \
  ./scripts/run-fjsp-100k-benchmark.sh \
  --warmup 2 --measurements 5 \
  --output target/stage1-operation-pipeline/fjsp-100k-baseline.jsonl
```

Artifact SHA-256：`cfa01fe052126c18ee250ab35c3f3d311095945692c613c26c20d4adc5734d92`

| 指标 | 5 次结果/汇总 |
|---|---|
| solve time | 269.026–299.833 ms；median 282.045 ms |
| solve allocated/op | 3281.302–3521.193 B；median 3300.045 B |
| total allocated/op | 9221.570–9460.538 B；median 9292.257 B |
| Young GC | 92 次 / 250 ms 合计 |
| Full GC | 2 次 / 86 ms 合计 |
| assignments/jobs | 100,000 / 1,000，全部一致 |
| makespan/tardiness | 33,891 / 12,234,615，全部一致 |
| checksum | `-1508405863224505168`，全部一致 |
| runtime plan hash | `24c525f1082841041843cc84b1c19eecb1a7719e5ac268ab36f56ea1ce6a1962` |

FJSP allocation 包含 input/value construction、Batch、application objects、ColumnView、Pipeline、snapshot 和 export，不能由 component lane 数字直接相减。Stage 2 需要在 checksum 不变下同时比较 solve allocation、total allocation、latency 和 GC。

## 8. 当前 correctness/oracle coverage

已有 external/runtime evidence 可以作为 Stage 2 reference oracle：

- one-shot consumed、deep chain growth、filter/skip/limit 顺序；
- stable sort、stable arg-min first-on-equal、linear comparison count；
- exact source + sort、hash collision + full equality；
- empty/single/multi IndexSnapshot、currentness 边界；
- callback failure、cursor escape、reentrant access；
- update staging、remove/swap-remove、exact relocation；
- randomized exact/remove/dynamic-sort differential；
- ColumnView lifecycle、child ownership、materialization budget；
- generated public `javap` 与 external Maven consumer。

Stage 2 新增 API 仍需要新 oracle，不能用“旧底层方法已测”替代：SecondaryUnique point family、scalar Index terminal、新 one-shot traversal 名称、clean rename、protocol mismatch 和 generated collision admission。

## 9. Access Pattern / benchmark matrix

| Pattern group | 当前主要 evidence | Stage 2 必补 |
|---|---|---|
| Packed Candidate | packed source/filter component、packed scan smoke | target Scan source-only/short-chain allocation |
| PrimaryKey point | normal/collision key lookup smoke | rename 后 hit/miss/materialize/Index parity |
| SecondaryUnique | write conflict + group source tests | point hit/miss/collision、mutate/delete、Candidate bridge |
| ExactGroup | exact component、incremental/storm smoke | source-specialized target allocation |
| Current Index | fetchAt/mutateAt tests、ColumnView | scalar terminal + stale/current boundary |
| stages | deep-chain/differential/component | inline 0..3、overflow 4/5/16、failure atomic append |
| best-one | stable arg-min oracle | `findIndex/requireIndex` allocation/comparison count |
| snapshot/materialize | component + budget tests | keep separate from plan allocation |
| Key traversal | materialized-first component | renamed traversal lifecycle and borrow/materialize split |
| Column traversal/View | component + view smoke | prebound diagnostics、one-shot、no per-element allocation |
| update/remove | fusion/frontier/compaction/exact storm | target executor parity and scratch retained bytes |
| Batch/bulk | reserve/growth/dense replace | no regression；不并入 Pipeline |
| child | child-locality + lifecycle fixture | renamed access does not alter ownership |
| stats | stats overhead smoke | shortcut logical stats parity |
| integrated | four examples + FJSP 100k | checksums、allocation、GC、latency、class size |

## 10. Evidence 支持的裁决边界

本 evidence 足以支持：

- inline capacity 3 + tested overflow；
- exact source descriptor 与 public handle 合并/去匿名 wrapper；
- compact single stage storage，禁止五组 capacity-4 arrays；
- lightweight derived generation handle，避免 80 B full handle；
- scalar first/best current-Index terminal；
- Column traversal diagnostic strings 预绑定；
- code-size 与 allocation 双 Gate。

本 evidence 不足以支持：

- 宣称目标 plan 为零分配；
- hard-coded 跨机器 latency/throughput 百分比；
- public top-k、generic reduction 或 range index；
- 删除 overflow、failure checks、stable tie 语义或 materialization budget；
- 把 application lambda/value-object allocation归咎于 SOMA plan；
- 把 TableStats 扩大为 caller-held plan retained heap accounting。
