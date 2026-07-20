# Packed Exact Index 切换后尾项治理收口报告

状态：passed；实现、证据、正式事实提升与Temporary退役均已完成

日期：2026-07-20

治理基线：`4b6fa43 perf: adopt packed exact indexes and swap removal`

代码证据基线：`b991f4c docs: define keyspace as primary locator`

范围：报告有效性、Index 使用契约、exact-index 组件证据与容量、FJSP machine selection、Generator 职责和 KeySpace 命名

明确排除：发布、SCM、签名、support matrix、G6 和 release-readiness

## 1. 结论

Packed/exact v3 切换后的七类尾项均已完成裁决、实现或证据关闭，没有重新打开已经确定的 API、Schema、packed storage、swap-remove 或 eager exact-index 语义。

| 尾项 | 最终处置 | 关键提交 |
|---|---|---|
| report current/superseded 导航 | 当前结论统一指向 packed/exact v3 报告，旧报告明确为历史快照 | `5735de2` |
| Index / IndexSnapshot 安全 | 固化 caller-responsibility；`requireCurrent` 保持可选防御，不进入强制 hot path | `45c8151` |
| exact-index 组件测试 | 补齐 group lifecycle、collision、growth、unlink/relocate、clear/release 与 retained accounting | `8c61927` |
| snapshot 与 exact-index allocation | 单元素 snapshot inline；append/replace 按真实 distinct-group cardinality 预检和分配 | `c9684b2` |
| FJSP machine selection | application-owned indexed min-heap 取代每轮全 machine dynamic sort | `97313c6` |
| Generator 集中 | exact-index runtime source 迁入独立 byte-stable emitter | `74e3085` |
| `KeySpace` 命名 | 保留兼容性名称，正式定义为 primary-locator umbrella term | `b991f4c` |

本专题只做兼容性内化和场景实现优化。没有新增 annotation、public terminal、artifact dependency、range/order capability 或 runtime transaction 语义。

## 2. Index 使用契约

最终契约为 caller-responsibility：

- `Index` 只是来源 Table 当前 packed `[0,size)` 中的物理位置，不是 stable identity；
- `IndexSnapshot` 是某一时刻 Index 序列的 detached copy，不是 row snapshot；
- caller 只能在一个同步、只读的消费批次中立即使用，期间不得修改来源 Table；
- 来源 Table 发生任意 mutation 或 lifecycle 变化后，既有 Index 与 IndexSnapshot 全部失效；
- 候选行修改使用同一 Pipeline 的 `update()` / `remove()` terminal；跨 operation 长期引用使用 `@SomaKey`；
- `requireCurrent(snapshot)` 只用于测试、调试或边界防御，不自动证明非结构字段的 filter/order 语义，也不进入强制 hot path；
- failure atomicity 只覆盖单次同步 Table Operation，不表示多次调用或跨 Table 事务。

这一裁决同时保留 public API 安全边界和 hot-path 自主性，没有引入 per-index guard、稳定物理顺序或隐藏 pin。

## 3. Component allocation 与 exact-index memory

### 3.1 方法

当前 artifact 由以下命令生成并由 strict validator 校验：

```text
./scripts/check-post-cutover-components.sh
```

环境为 Azul Zulu OpenJDK `1.8.0_492-b09`、macOS `26.5.2`、aarch64，JVM `-Xms256m -Xmx512m`。Allocation 使用 current-thread `ThreadMXBean`，每条 lane 为 2,000 次 warmup 和 5,000 次 measurement；memory matrix 是 primitive array payload 的确定性估算，不包含 JVM header/alignment。所有记录均为 `claimAllowed=false`。

当前 artifact 绑定 commit `b991f4cf164258903ef8f3370b6a42bcf48b7bbb`，SHA-256：

```text
a232d7c4033d738844c5f876b4579ade5578aebf23213d60dbd718e545c14460
```

### 3.2 Pipeline allocation

| Lane | 当前 allocated B/op | Young / Full GC |
|---|---:|---:|
| exact source → `count` | 116.1696 | 0 / 0 |
| exact source → filter → `count` | 400.0736 | 0 / 0 |
| exact source → filter → sort → limit(1) → `rowIndexes` | 648.0960 | 0 / 0 |
| exact source → filter → sort → `firstOrThrow` materialization | 1,085.0784 | 0 / 0 |

开发期同 runner 前后观测中，single-result snapshot 从 `664.096` 降至 `648.096 B/op`，减少 `16 B/op`（`-2.409%`）。当前 commit-bound artifact只记录优化后值；该差值是单机诊断，不是跨 JVM claim。实现采用 shared empty snapshot 与 single-index inline，multi-index 仍保持 detached array copy。

### 3.3 Distinct-group capacity

旧 worst-case 形状按 row count 同时预留 group arrays；当前实现把 row-link capacity 与 group capacity 分开，append 使用 primitive `ExactGroupCounter` 统计各 selector 新 group，replaceAll 按 batch distinct groups 创建 fresh index。

以下为 65,536 rows 的 retained primitive payload：

| Distinct groups | row-worst-case | cardinality-aware | 变化 |
|---:|---:|---:|---:|
| 1 | 3,211,264 B | 786,532 B | -75.507% |
| 16 | 3,211,264 B | 787,024 B | -75.492% |
| 256 | 3,211,264 B | 795,904 B | -75.215% |
| 65,536 | 3,211,264 B | 3,211,264 B | 0% |

高基数 unique/worst-case selector 不退化；低基数 grouped selector 不再为不存在的 groups 支付接近全 row count 的容量。Resource preflight、stats current/high-water、collision full equality 和 failure atomicity继续由组件测试与 generated consumer 覆盖。

## 4. FJSP dynamic sort 与 application heap

### 4.1 裁决

`MachineTable` 继续拥有 machine authoritative state；machine availability queue 改为 FJSP application-owned indexed primitive min-heap。Heap 保存 `MachineId -> heap slot`，不保存 SOMA `Index`；machine 状态更新后显式 refresh，inactive/stale entry 在队首按 key/current state 丢弃。MachineCandidate frontier 仍使用 SOMA exact source、filter、dynamic candidate sort 和 mutation terminal。

这符合场景边界：跨轮 event/priority structure 由 application 拥有，SOMA Table 不为此恢复 maintained order。

### 4.2 同机 A/B

Workload：1,000 jobs × 100 operations、100 machines、每 operation 3 candidates，共 100,000 operations；seed `1397706049`；每侧 2 次 warmup、5 次 measurement；JVM `-Xms2g -Xmx4g`。Reference 为 `c9684b2` 的全 machine dynamic sort，current 为 `97313c6` 的 indexed heap。

| 指标（5次中位数） | dynamic sort | application heap | 变化 |
|---|---:|---:|---:|
| solve time | 443.032250 ms | 207.396500 ms | -53.187% |
| solve allocated bytes | 458,683,080 | 369,316,168 | -19.483% |
| total allocated bytes | 672,136,808 | 585,138,456 | -12.944% |
| Young GC，5次合计 | 3 / 63 ms | 2 / 21 ms | -1 / -42 ms |
| Full GC，5次合计 | 0 / 0 ms | 0 / 0 ms | 无变化 |

两侧 5/5 measurement 均得到 100,000 assignments、1,000 completed jobs、makespan `54571`、total tardiness `20509698`、checksum `-678377626428749715` 和同一 RuntimePlan hash。

Artifact SHA-256：

- dynamic sort：`eccee52369d85c164da7a6ce5a822529c7a6d830fda428d5ab979466c7392c8a`；
- application heap：`f23eb2eb6441f5ea5581c815c62c5b873636574c90aa3aff121f897c822e92cb`。

该结果只支持“在此 FJSP workload、机器和 JVM 上，application heap 是更合适的 machine-selection 实现”这一诊断结论，不升级为 SOMA 通用结构或 production SLA。

## 5. Generator 与命名维护

`DenseTableSourceGenerator` 的 exact-index runtime source 编排迁入 `DenseExactIndexSourceEmitter`，主文件由约 4,411 行降至 4,144 行。拆分前后的 `soma-examples/target/generated-sources/annotations` 执行 `diff -qr` 无差异；processor compile、keyed external consumer、repeat generation 与现有 generated shape Gate 通过。共享 selector normalization/hash/comparison仍由同一 validated model 绑定，没有复制第二套语义。

`KeySpace` 不执行 breaking rename。正式术语现在区分：

- `PrimaryLocator`：职责名称；
- `KeySpace`：现行代码、runtime protocol、plan canonical JSON 与 stats 的兼容性 umbrella term；
- `HashIntKeySpace` / `HashLongKeySpace` / `HashCompositeKeySpace`：open-addressed concrete implementation。

`KeySpace` 不表示 Sparse Set、bounded entity-id space、stable Index 或新的 schema/public access concept。重命名会无收益地改变 `TablePlan.keySpaceStrategy`、canonical JSON/hash、generated protocol、stats、golden 与 external consumer，因此本轮明确保留。

## 6. V1 scope non-regression

- `@SomaKey`、`@SomaUnique`、`@SomaIndex`、generated API 与 schema hash语义未改变；
- keyed/dense 均继续 packed `[0,size)` 与 swap-remove，不承诺物理遍历顺序；
- exact index 继续 eager/incremental，不存在 dirty rebuild、read full-scan fallback或 maintained order；
- 没有引入 Sparse Set、boxed Collection hot storage、reflection/metadata interpreter、Stream hot path或 per-row Cursor allocation；
- machine heap 是 example/application implementation，不进入 SOMA public/runtime protocol；
- 后续工作是 additive completion/internal refinement，不需要 consumer migration 或 architecture rewrite；
- G6 状态、Owner 与 Gate 均未被本专题改变或评价。

## 7. 最终验证

在本报告、候选 Implementation/Conformance 投影和 Temporary 退役全部完成后执行：

```text
./scripts/check.sh
```

结果为`project-check: ok`。实际环境为Azul Zulu OpenJDK `1.8.0_492-b09`、javac `1.8.0_492`、Apache Maven `3.9.16`、macOS `26.5.2` / Darwin `25.5.0`、aarch64。未设置`SOMA_UNSUPPORTED_JAVAC`，因此unsupported-javac diagnostic lane按脚本契约skip；正式Java 8 compiler、public API与codegen lanes均通过。

完整门禁覆盖scope/docs、全模块Maven、build governance、public API、compiler/codegen admission、internal names、runtime-core/grouped exact index、KeySpace、dense/keyed/access/child/breadth external consumers、diagnostics、四个正式场景、benchmark smoke、post-cutover component artifact和FJSP allocation/GC。

最终门禁artifact：

- component：`target/post-cutover-components.qz8pWn/post-cutover-components.jsonl`，SHA-256 `a232d7c4033d738844c5f876b4579ade5578aebf23213d60dbd718e545c14460`；
- FJSP allocation/GC：`target/fjsp-allocation-gc.EApy1a/fjsp-100k-allocation-gc.jsonl`，SHA-256 `523f9838b43d7286e47e9c5f91d4c337251184168aff259aa958b1e84ec1869b`。

`target/`只保存本机可重放evidence，不是长期事实源。原`docs/temp/packed-exact-index-post-cutover-tails/`已在本报告和正式Owner接管稳定事实后删除；四份长期研究蓝图继续保留。本报告回填后另行执行`./scripts/check-docs.sh`与`git diff --check`。
