# SOMA V1 32 位结构域与即时增量 Key/Index 维护资格

类型：Conformance / Post-V1 Storage and Mutation Governance

状态：`PASS / FORMALLY PROMOTED / TEMPORARY REPLACEMENT CLOSED`

日期：2026-08-11

Owner：32位结构域、64位累计域、Key/Index唯一物理truth、point即时增量维护、
Selection一次candidate rebuild及其正确性/原子性/资源/性能证据边界

## 1. 最终结论

本专题以`a0f1cd9`的即时linked-posting predecessor为可重放对照，分离完成了两项
长期修正：

1. SOMA Table-local可寻址结构改为checked 32位结构域；天然可超过单Table
   结构上限的count/cardinality、memory bytes/budget与stateVersion保持checked 64位累计域；
2. Key与secondary Index不再使用per-record linked posting；Key slot内联唯一raw
   `int` locator，Index singleton内联一个locator，multi仅拥有一个严格升序的
   `int[]`。

Point add/update/remove在单个Table-local terminal中只准备并发布受影响的Key slot与
Index Bucket；packed remove同步将tail locator从`T`调整到hole `R`。Selection
mutation仍从最终candidate payload一次重建全部sidecar，不通过多次point mutation部分
发布。

实现不含fastutil、tagged locator、延迟失效、dirty Bucket、reverse Index、第二套
membership truth、background cleanup、container strategy/SPI或第三production artifact。

## 2. 正式合同与实现 Owner

| 语义 | 当前正式合同 |
|---|---|
| Table结构 | `int size/capacity/raw locator/Chunk offset/Table-local mutation result`；`-1`只是internal missing/end |
| 累计域 | `long count/cardinality/memory bytes/budget/stateVersion`，全部checked |
| 结构超界 | 超出Table/array/container表达能力在publication前`RESOURCE_LIMIT_EXCEEDED` |
| 累计溢出 | 合法累计无法由`long`表达时`ARITHMETIC_OVERFLOW` |
| Key | typed sharded hash slot内联唯一live `int` locator；zero locator合法 |
| Index | typed exact directory；singleton inline与multi ordered `int[]`互斥；一套canonical membership |
| Point mutation | prevalidated affected-Bucket maintenance + non-throwing final commit + one publication |
| Selection mutation | frozen membership + one complete candidate payload/sidecar rebuild + one root swap |
| Order | Bucket locator严格升序；IndexSelection是current Table encounter order的subsequence |
| Product boundary | public API不暴露locator/Hash/Bucket；Logical IR与Planner不感知physical replacement |

唯一规范性Owner已更新：[Blueprint](../blueprint/README.md)、
[Storage](../design/data-model-and-storage.md)、[Schema](../design/schema-and-generation.md)、
[Logical API](../design/logical-api.md)、[Signature](../design/generated-api-signatures.md)、
[Failure](../design/results-and-failures.md)、[Architecture](../design/implementation-architecture.md)、
[Planning](../design/planning-and-optimization.md)与
[Core abstractions](../design/core-abstractions-and-narratives.md)。

## 3. 实施 provenance

| Slice | Commit | 完成事实 |
|---|---|---|
| S0 | `a0f1cd9` | 固化可重放的即时linked-posting predecessor；冻结scheduling改造 |
| Governance approval | `43a1ca2` | Product Owner批准candidate B与分slice路线 |
| S1 | `3d90184` | annotation/generated/runtime/storage/selection/metadata/result全面迁移到精确32/64位数值域 |
| S2 | `51fd7b2` | 用singleton-inline + ordered `int[]`替换linked posting；建立point mutation局部维护与invariant validator |
| S3 | `6e2df98` | Selection rebuild、Key/Index/optimizer/Join/parallel读取路径与全仓qualification闭合 |

`scheduling` reference application的未完成重构在全过程中保持冻结，不是实现或资格
证据。

## 4. 正确性、原子性与资源证据

### 4.1 Targeted evidence

- runtime 64项测试PASS；processor 34项测试PASS；Java 8 generated source/`javap`/
  consumer已冻结exact `int` Table structural surface与`long` cumulative surface；
- 600-step randomized add/update/remove state machine每次publication后同时对照reference model，
  并校验payload/Key/全Index、Bucket order/duplicate、managed accounting与root version；
- high-cardinality singleton、hot Bucket、`1→2→1→0`、same/different-Bucket packed remove、
  representative move、nullable/collision/re-add等边界PASS；
- `IdentityHashIndex.validateForTesting`证明每个live locator在每个Index中恰好出现一次，
  locator live/current、Bucket严格升序无duplicate，typed hash/equality与accounting一致；
- failed mutation保持old root/version/payload/Key/Index/accounting，point prepare完成全部可恢复
  allocation/hash/equality/fault injection后才进入bounded non-throwing commit；
- `./scripts/check.sh` PASS，覆盖Maven reactor、I0-I8 breadth、三个stable examples、
  benchmark compile、package/source consumer、SBOM与provenance。

### 4.2 Resource ownership

Managed retained计入Shard arrays、hashes、inline locator/count、Bucket reference与multi `int[]`
完整capacity/slack；prepare期的replacement Shard/Bucket/candidate由temporary admission覆盖。
S2后1M mutation fixture retained从`296,460,816` bytes降为`221,843,648` bytes，降低
`25.2%`；不存在per-record next-link retained owner或unaccounted第二membership。

## 5. Fixed-host A/B 性能资格

### 5.1 Evidence boundary

| 项目 | 固定事实 |
|---|---|
| Baseline | detached worktree `a0f1cd9`，即时linked posting |
| Candidate | S1-S3 active checkout，singleton-inline / ordered `int[]` |
| Host | Apple M5 Pro，48 GiB physical memory，macOS/Darwin arm64 |
| Java / Maven | Amazon Corretto `1.8.0_502` / Maven `3.9.16` |
| 10K / 1M | `-Xms2g -Xmx8g`，6 GiB SOMA budget，P8，3 fresh-JVM runs |
| 10M | `-Xms8g -Xmx24g`，16 GiB SOMA budget，P8，1 capacity/complexity run |
| Correctness | 每项measurement独立value/fingerprint；全部PASS |
| Profile | async-profiler CPU/allocation JFR + collapsed stacks；GC/rusage |

绝对latency只是fixed-host evidence，不是跨硬件SLA。10M单run只用于capacity、复杂度和
主要回归判断，不声称稳定百分位。

### 5.2 1M mutation（3-run median）

| Operation | `a0f1cd9` | Candidate B | 变化 |
|---|---:|---:|---:|
| Ingest | 662.875 ms | 500.422 ms | `-24.5%` |
| Point update 10K | 516.091 ms | 465.729 ms | `-9.8%` |
| Selection update | 42.949 ms | 36.467 ms | `-15.1%` |
| Selection remove | 1,012.823 ms | 797.793 ms | `-21.2%` |
| Post-mutation state | 3.204 ms | 1.374 ms | `-57.1%` |
| Retained | 296,460,816 B | 221,843,648 B | `-25.2%` |

集中收益来自删除linked predecessor/next storage与pointer traversal，不是弱化correctness、
canonical order或publication合同。

### 5.3 Clean family 与 10M complexity

1M clean 3-run median中，Index exact count/residual从`0.291/0.571 ms`降为
`0.131/0.422 ms`，Key 10K probe从`3.633 ms`降为`2.789 ms`，Join count/filtered
从`192.342/359.426 ms`降为`171.178/287.905 ms`。大部分source/stateful/Group/
Relation路径相当或更快；1M semi Join约`+5%`为边缘波动，10M下为`+2.7%`，
未出现新的复杂度或allocation Owner。

10M candidate/baseline主要对照：

| Operation | `a0f1cd9` | Candidate B |
|---|---:|---:|
| Ingest | 5.821 s | 5.435 s |
| Point update 10K | 958.471 ms | 890.260 ms |
| Selection update | 192.152 ms | 173.279 ms |
| Selection remove | 9.642 s | 8.668 s |
| Index exact count / residual | 3.827 / 11.328 ms | 0.777 / 4.884 ms |
| Join count / filtered | 1.783 / 3.161 s | 1.721 / 2.713 s |

原始10M source单run曾出现parallel typed filter波动；candidate独立3-run重放的median为
`165.287 ms`，因此不构成架构回归。10K固定成本路径也已重放，差异为微秒级
噪声，无新的固定复杂度。

### 5.4 Profile conclusion

CPU/allocation profile中原linked predecessor lookup和per-record next allocation已退出主要热点。
当前point update主要余量在`TableChunkDirectory.copyForUpdate -> OverlayChunk.mutableCopy
-> TypedValues`；Selection remove主要余量在candidate payload与`rebuildIndexes`。它们分别
属于Chunk overlay与Selection whole-candidate Owner，不证明当前Bucket方案需要延迟失效、
reverse Index或另一套container；本专题不为追逐次要热点扩张范围。

## 6. Exit 与 claim boundary

候选Design的20项Exit已全部闭合：正式Owner已替换、单一Index truth已建立、point
mutation无whole-sidecar rebuild、Selection仅一次candidate rebuild、failed-state/resource/
reference clearing/differential/10K-10M/performance/package/full-repository证据均PASS，无已知
P0/P1。

本记录允许声称：

> SOMA V1当前使用32位Table结构域、64位累计域和即时增量Key/Index维护；
> singleton-inline / ordered `int[]` 在当前固定主机10K/1M/10M资格中同时改善了
> mutation、Index lookup与retained memory，未发现有因果的clean-family性能退化。

本记录不证明：超过`Integer.MAX_VALUE`的单Table、一亿行性能、跨硬件SLA、
任意million-locator Bucket的最优查找、scheduling新建模、GitHub Release/Package、签名或
正式release。

此前延迟失效/tagged-locator candidate A被本记录明确取代；实施HANDOFF与S1 delta
已被正式Owner、code与本Conformance吸收。原bounded Temporary完成replacement
closure后退役，不保留平行Design。
