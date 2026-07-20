# Packed Index / Exact Access / IndexBuffer 重设计实施收口报告

状态：passed；专题实施、证据回放与临时设计退役均已完成
日期：2026-07-17
专题基线：`6f91e57 perf: establish FJSP baseline and packed-index redesign`
实施提交：`4b6fa43 perf: adopt packed exact indexes and swap removal`
唯一事实源：本报告只记录实施与 evidence；设计语义仍由 `docs/README.md` 登记的各唯一 Owner 拥有
Capability：`V1-ANNOTATION-SCHEMA`、`V1-PROCESSING-MODEL`、`V1-SCHEMA-HASH`、`V1-PUBLIC-COMPATIBILITY`、`V1-GENERATED-API`、`V1-DENSE-STORAGE`、`V1-ROW-PIPELINE`、`V1-KEYED-IDENTITY`、`V1-ACCESS-STRUCTURES`、`V1-MUTATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-RUNTIME-PLAN`、`V1-PERFORMANCE-SHAPE`、`V1-EVIDENCE-TOOLING`、`V1-CONSUMER-PACKAGE`、`V1-SCENARIO-BENCHMARK`

## 1. 结论

本专题完成了首个公开发布前的一次批准 breaking cutover：SOMA Table 不再使用 Sparse Set、maintained order 或 dirty selector rebuild；keyed/dense table 统一保持 packed `[0,size)`，删除采用 swap-remove/tail-fill，primary/unique/nonunique exact access 在 mutation boundary eager incremental 维护，业务顺序统一由显式 `sorted(totalComparator)` 或 application-owned 专用结构产生。

新实现保留 `@SomaKey` 作为 primary unique identity，保留 `@SomaUnique` 与 `@SomaIndex` 作为 secondary exact access；没有把 key 降级成普通 secondary unique，也没有引入 range lookup、B+ tree、skip list、boxed `Map<Value,List<Integer>>`、read-time full scan fallback 或 hash-only equality。

专题实施前先提交全部既有修改，形成 immutable 对照基线 `6f91e57`；随后按正式 Owner、annotation/processor/runtime、consumer/evidence、cleanup 的顺序完成迁移。发布、签名、SCM、support matrix 与 G6 不属于本专题，也没有被本次 G0–G5 功能验证替代。

## 2. 最终设计模型

```text
@SomaTable columns + presence + packed Index [0,size)
  + @SomaKey     -> primary hash locator    -> 0/1 current Index
  + @SomaUnique  -> GroupedExactIndex       -> 0/1 current Index
  + @SomaIndex   -> GroupedExactIndex       -> 0..K current Index sequence
  + default scan -> current physical Index sequence
  -> filter / skip / limit / explicit sorted
  -> terminal
```

关键顺序语义：

- default source 只按当时物理 `0..size-1`；
- exact source 只沿当时 current group row links；group 内枚举顺序不构成业务契约；
- 未显式排序的 `firstOrThrow()`、`limit(n)`、`fetchAll()` 只服从当时 source sequence；
- `sorted(...)` 只排序本次 candidate Index，不移动真实 columns；`sorted(...).limit(1)` 在合法形态下使用 stable arg-min；
- `IndexSnapshot` 是 detached Index sequence，携带 source-table owner 与 structural epoch；`requireCurrent(snapshot)` 拒绝 wrong-table 或 stale snapshot；
- packed Index 不是 identity。任何 structural mutation 后，旧 raw Index 都不能作为长期引用。

## 3. Schema、API 与 protocol cutover

### 3.1 保留与删除

保留：

- `@SomaKey`：primary unique identity；
- `@SomaUnique`：secondary unique exact access；
- `@SomaIndex`：secondary nonunique exact access；
- `sorted(comparator)`：唯一 Table/Pipeline business-order primitive；
- packed compaction：keyed 与 dense 均使用 swap-remove。

直接删除：

- `@SomaOrder`、`@SomaOrders`、`@SomaSort`、`SomaDirection`；
- `SparseIntKeySpace`；
- `RowPermutationSidecar`；
- `TablePlan` 的 sparse/sidecar/maintained-order 配置；
- `TableStats`、`UpdateResult`、`RemoveResult` 中旧 dirty/rebuild/stable-compaction 口径。

没有保留 deprecated annotation、no-op compatibility shell、旧 getter 空壳或 runtime fallback。旧 source 必须重新生成和编译；这是同一 Java-only V1 在首个公开发布前完成的有意兼容性切换。

### 3.2 新 surface 与 identity

- public detached row-index sequence 统一为 `IndexSnapshot`；内部复用 scratch 统一名为 `IndexBuffer`；
- `UpdateResult = scanned/matched/changed`；
- `RemoveResult = scanned/matched/removed/compacted`，其中 `compacted` 是实际 tail-fill move 数，不表达稳定前移；
- `TableStats` 提供 exact-index count/entry/group/probe/collision/rehash/current/high-water storage；
- `TablePlan.accessStrategy` 使用 `primitive-exact-hash-v1`，key strategy 使用 `none`、`hash-int-v2`、`hash-long-v2` 或 `hash-composite-v2`；
- generated/runtime/plan protocol 统一提升为 `soma-generated-runtime-v3`、`soma-runtime-java8-v3`、`soma-runtime-plan-v3`。

Schema hash、generated golden、public/runtime manifest 与 benchmark artifact schema 已随新语义整体刷新，不存在一半旧协议、一半新实现的混用状态。

## 4. Runtime 实现

### 4.1 Primary locator 与 grouped exact index

Primary key 继续使用 generated primitive/composite open-address hash locator，collision 后执行 full canonical key equality。`GroupedExactIndex` 使用：

```text
primitive bucket heads
  -> same-hash group chain
  -> group head/size
  -> per-row group/previous/next links
```

Generated code拥有selector canonical hash与full leaf equality；runtime不保存selector DTO、boxed key或Collection graph。Append link、update unlink/publish/relink、remove unlink、tail-fill relocate、replaceAll detached fresh build均在结构边界维护一致性。Ordinary read不检查dirty、不重建全表，也不以full scan维持正确性。

### 4.2 Packed swap-remove

单行和多行删除先冻结并校验candidate Index，按Index排序后删除primary/exact identities；`[newSize,oldSize)`中的未删除survivor依次填入低位hole，同步：

- 所有primitive/reference/presence columns；
- primary locator中的current Index；
- 每个exact index的row links；
- child ownership handle/registry binding；
- structural epoch与reference cleanup。

物理遍历顺序不稳定；业务determinism必须来自total comparator或外部稳定结构。

### 4.3 IndexBuffer 与 allocation shape

Row Pipeline不为L1/L2/L3分别复制candidate arrays。Table-local `IndexBuffer`复用candidate、pipeline与sort scratch；intermediate只形成small one-shot wrapper，terminal完成后scratch可复用。Public `IndexSnapshot`必须复制最终Index sequence，以保证detached ownership、wrong-table与stale检测；调用方不需要独立数组时应直接使用`size()/indexAt()`，避免额外`toArray()`复制。

本轮JFR还关闭了两个稳态临时对象来源：

- `GroupedExactIndex` capacity target改为primitive arithmetic，不再为每次capacity足够的preflight构造`Capacity` descriptor；
- selector全部位于immutable key leaves时，generated update/mutator不再执行无效capacity preflight、unlink或relink；mixed selector只维护实际可变selector。

`reserve(expected)` 现在对columns、primary locator与exact indexes做combined preflight并预留同一row envelope；external consumer证明reserve后的import不再触发locator/index regrowth。

## 5. Mutation、failure 与 lifecycle

- append在visible size commit前完成domain、duplicate、unique、capacity与resource检查；
- multi-row unique update按整次terminal final state校验，允许合法value swap，拒绝最终冲突；
- callback failure、typed resource failure、active view/pin与duplicate/unique failure不发布partial facts；
- raw `OutOfMemoryError`保持JVM Error语义，已分配但尚未成为logical row的reference payload会清理；
- `IndexSnapshot` structural epoch、ColumnView pin/stale/released、mutator epoch与child ownership规则保持闭合；
- same-table callback reentrancy继续fail closed，cross-table transaction仍由application拥有。

## 6. Consumer 与场景迁移

- FJSP：`findByMachine -> update`和`findByMachine -> filter -> sorted -> first`保留为canonical exact-group路径；consumer直接消费`IndexSnapshot`，successor复用已定位Index；machine availability若需长期队列可由外部heap承担；
- VRP/Game：dense candidate workspace使用`replaceAll + explicit sorted`；不再声明策略order；
- Simulation：event queue由application min-heap维护，`PendingEventRow`只承担batch ingest/diagnostic/export projection；trace与state vector只在需要时显式排序；
- child/export：position/time字段仍是业务事实，顺序由boundary comparator产生；child exclusive ownership不依赖物理Index稳定性。

四份长期研究蓝图已同步到新正式baseline；本专题临时设计在所有事实迁移和Gate通过后删除，不作为永久事实源保留。

## 7. Correctness evidence

已完成并通过的定向证据包括：

- runtime-core `GroupedExactIndex` group lifecycle、collision、growth、unlink/relocate、clear/release与result invariant；
- dense/keyed randomized swap-remove differential oracle；
- exact index 0/1/many、same-hash/full-equality、rehash、mutable selector update、unique final-state swap/conflict；
- wrong-table/stale/current `IndexSnapshot`；
- append/update/remove/replaceAll expected failure atomicity与reserve combined preflight；
- child compaction/cascade/pin/release；
- external Maven dense、keyed、access、child、breadth consumer；
- schema JSON/hash、generated/public javap、locale/timezone repeat generation；
- FJSP、VRP、Simulation、Game formal examples与benchmark artifact strict validator。

定向命令已通过：

```text
./scripts/check-public-api.sh
./scripts/check-runtime-core-phase1.sh
./scripts/check-keyspace-phase2.sh
./scripts/check-generated-dense-phase1.sh
./scripts/check-generated-keyed-phase2.sh
./scripts/check-access-phase3.sh
./scripts/check-child-phase4.sh
./scripts/check-breadth-phase5.sh
./scripts/check-examples-phase6.sh
./scripts/check-benchmark-smoke.sh
./scripts/check-fjsp-allocation-gc.sh
```

最终`./scripts/check.sh`已在同一环境完整通过，结果见§10。

## 8. FJSP 同机 A/B

方法：

- baseline：commit `6f91e57`独立worktree；
- current：本专题实现工作树；
- workload：1,000 jobs × 100 operations，100 machines，3 candidates/operation，共100,000 operations；
- seed `1397706049`，dispatch rule `effective-ready,fcfs,spt,identity`；
- Azul Zulu OpenJDK `1.8.0_492-b09`，macOS `26.5.2`，aarch64；
- JVM：`-Xms512m -Xmx512m -Xmn96m -XX:+UseParallelGC`；
- 两侧均`warmup=1`、`measurement=1`，`claimAllowed=false`；
- allocation使用current-thread `ThreadMXBean`精确phase delta；GC使用MXBean delta；单次time只作诊断。

| 指标 | old dirty/rebuild sidecar | packed incremental exact index | 变化 |
|---|---:|---:|---:|
| import allocated bytes | 179,032,624 | 185,361,728 | +6,329,104 |
| solve allocated bytes | 402,287,128 | 445,278,424 | +42,991,296 |
| export allocated bytes | 16,800,624 | 16,953,632 | +153,008 |
| total allocated bytes | 598,120,376 | 647,593,784 | +8.271% |
| allocated bytes/operation | 5,981.20376 | 6,475.93784 | +8.271% |
| Young GC count/time | 18 / 40 ms | 19 / 42 ms | +1 / +2 ms |
| Full GC count/time | 0 / 0 ms | 0 / 0 ms | 不变 |
| solve time（仅诊断） | 8,063.565 ms | 597.573 ms | -92.589% |
| throughput（仅诊断） | 12,401.463 op/s | 167,343.513 op/s | 13.494× |

语义结果完全一致：100,000 assignments、1,000 completed jobs、makespan `54571`、total tardiness `20509698`、checksum `-678377626428749715`。RuntimePlan hash变化是v3 plan shape的预期结果。

Artifact SHA-256：

- baseline JSONL：`1adb48dec35cf2d53cdbef1e1aeff42de93fdcd3c47c4e8120938dab131e7767`；
- current JSONL：`9bc05291548f285498d00887b9d5d16857bd4607cbe62774ee4df01bf417e01a`。

裁决：本轮成功消除了CPU第一热点和read-time全表排序，Full GC仍为0；但exact-index retained/growth arrays、epoch-bearing snapshot和新一致性维护使该单次record的allocation/op高于旧baseline约8.3%，Young GC多1次。该trade-off对当前FJSP workload显著有利，但不被升级为跨机器或production性能claim。后续allocation优化必须保持detached snapshot、resource preflight、full equality与mutation atomicity，不能为追求旧allocation数字恢复dirty rebuild或暴露unsafe raw Index。

## 9. 明确未进入本专题

- range lookup、B+ tree、skip list或通用query planner；
- maintained order、Table-owned priority/event queue；
- allocation-free `firstIndexOrThrow`、callback-scoped first terminal或新的public selector API；
- stable compaction、stable physical traversal或key-order承诺；
- concurrency、cross-table transaction、persistence、serialization；
- publishing、SCM、签名、support matrix、G6或release readiness。

后续最值得独立评估的性能议题是：减少exact-source/Rows boundary wrapper与snapshot次数、用同语义component benchmark分离retained index allocation和steady-state lookup allocation，以及在明确新public contract后评估allocation-free single-index terminal。任何一项都不得在本专题尾部临时扩张API。

## 10. V1 scope non-regression 与最终 Gate

- Capability：本专题涉及的17项功能/evidence Capability在迁移完成并重放Gate后保持`evidenced`；没有dropped、optional或无目标deferred；
- Owner：经用户明确批准后先迁移唯一Owner，再实施代码；正式Owner与Gate已切到packed/exact v3语义；
- compatibility：这是首个公开发布前批准的breaking migration，不是contract-preserving internal refinement；旧annotation/generated/runtime consumer必须重编译；
- canonical architecture：当前实现是最终V1架构的有效完整形态，不需要再迁移回Sparse Set、order sidecar或stable compaction；
- prohibited shortcut：未引入Collection hot storage、metadata interpreter、reflection、Stream、per-row Cursor、read fallback、hash-only identity或test-only bypass；
- G6：状态不因本专题改变；本报告不执行也不评价发布前置条件。

最终验证环境为 Azul Zulu OpenJDK `1.8.0_492-b09`、Apache Maven `3.9.16`、macOS `26.5.2`、aarch64。实际执行：

```text
./scripts/check.sh
```

结果为 `project-check: ok`，覆盖 scope/docs、全模块 Maven build、public API、compiler/codegen、runtime-core、dense/keyed/access/child/breadth external consumer、四个正式场景、benchmark artifact validator、benchmark smoke 与 FJSP allocation/GC Gate。最终 FJSP evidence 位于本机 `target/fjsp-allocation-gc.Q1G3lR/`，其 JSONL SHA-256 已在§8登记；`target/` artifact 不是版本库中的长期事实源。

临时专题目录 `docs/temp/packed-index-runtime-redesign/` 已在正式 Owner 与本报告接管事实后删除，四份用户批准的长期研究蓝图继续保留。报告最终回填后又执行 `./scripts/check-docs.sh` 与 `git diff --check`；本报告与实现共同由提交 `4b6fa43` handoff。
