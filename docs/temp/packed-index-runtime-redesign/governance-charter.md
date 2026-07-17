# 重设计治理章程与决策台账

状态：治理专题草案，待用户独立审查
正式事实源：否
实施授权：无
最后审查日期：2026-07-17

## 1. 意图

本次重设计不是单点替换 `RowPermutationSidecar`，而是重新收敛 SOMA Table 的身份、物理位置、exact access、候选序列与排序职责，使实现服务于已确认的数据访问模型：

```text
authoritative columnar facts
  -> primary/secondary exact access 缩小 candidate domain
  -> fused pipeline 只处理 candidate Index
  -> explicit dynamic sort 或 application-owned priority structure
  -> terminal / mutation / materialization
```

希望同时解决：

- dirty selector 首次访问触发全表 rebuild/sort 的不可预测延迟；
- maintained order 与业务策略顺序混淆；
- Sparse Set/Entity 映射对 Value key、capacity 和扩容语义带来的额外复杂度；
- Row Pipeline intermediate/terminal 临时数组与对象分配不透明；
- stable compaction 与“不保证物理顺序”的目标不一致；
- public plan/stats 仍围绕旧 sidecar 生命周期建模。

## 2. 目标

### 2.1 功能与语义目标

- keyed/dense table 继续是唯一两种 table kind；
- `@SomaKey`、`@SomaUnique`、`@SomaIndex` 职责清晰且互不替代；
- exact access、scan、dynamic sort 与 external priority structure 各自拥有明确边界；
- 所有 stable state 保持 packed `[0,size)`；
- 所有 Index 都明确是 current physical location，不是 business identity；
- candidate source、intermediate stage 和 terminal 的顺序语义可逐项推导；
- mutation、locator/index maintenance、child ownership 与 epoch 一次提交闭合。

### 2.2 性能与 GC 目标

- exact access read path 不出现 hidden full-table rebuild；
- non-materializing steady-state terminal 不按 row/field 分配对象；
- pipeline stage allocation 只与 stage 数量相关，不与 candidate 数量相关；
- candidate/sort/remove scratch 使用 table-local primitive buffer，并在 logical reset 后复用；
- `findByMachine -> filter -> sorted -> firstOrThrow` 只处理 index group candidates，且 top-1 不要求完整排序；
- swap-remove 的 row move 数由删除数量约束，不因删除首行而稳定搬移几乎全表；
- rehash、buffer growth、bulk build 等分配只发生在明确 mutation/resource boundary，并可观察。

### 2.3 治理目标

- 先完成临时详细设计和独立审查；
- 设计获批后先修改唯一 Owner 与 capability ledger，再实现；
- public/schema/runtime/protocol breaking change 使用一次明确 cutover，不遗留两套 canonical 模型；
- examples、testkit、benchmarks 和 gates 与实现同轮迁移；
- 不把 release、SCM、publishing、support matrix 混入当前功能/性能专题。

## 3. 非目标

本专题不设计或承诺：

- range index、B+ tree、skip list 或通用 ordered map；
- maintained `@SomaOrder` 或 automatic physical sorting；
- stable insertion order、stable physical order 或 stable `@SomaIndex` group order；
- Sparse Set、Entity id、generation id 或 key-to-bounded-domain 映射；
- public custom index SPI、query planner、predicate pushdown、join 或 Java Stream；
- concurrent table、snapshot isolation、cross-table transaction；
- off-heap、native、SIMD、parallel sort；
- application event queue、priority queue 或 heap 的内部实现；
- 对外发布、license、SCM、signing、support matrix 或 G6 release 工作；
- 尚未有用户决策的 public result/stats 新签名。

## 4. 术语

| 术语 | 本专题定义 | 不等同于 |
|---|---|---|
| Index | 当前 table stable state 中 `[0,size)` 的 packed physical position | key、entity id、业务 sequence |
| IndexBuffer | terminal 执行期复用的 `int[] + logical size` 内部材料 | public collection、stable identity、每个 stage 的快照 |
| PrimaryLocator | `@SomaKey` canonical value 到当前 Index 的唯一定位结构 | secondary unique、Sparse Set |
| UniqueLocator | `@SomaUnique` canonical selector 到当前 Index 的 secondary unique 结构 | primary identity |
| GroupedExactIndex | `@SomaIndex` canonical selector 到 0..K current Index 的 non-unique exact 结构 | range tree、ordered permutation |
| source sequence | terminal 本次执行看到的初始 Index 序列 | business order、跨 mutation 稳定顺序 |
| dynamic sort | 单次 pipeline 显式 comparator 顺序 | maintained order、physical reorder |
| swap-remove / tail-fill | 从尾部 survivor 填补删除 hole 并缩短 size | stable compaction |

正式迁移时应审查现有 `RowSlot`、`RowSequence`、`KeySpace`、sidecar 等术语；本专题提出的新名在迁入 [领域术语表](../../domain-glossary.md) 前不是正式术语。

## 5. 决策状态规则

| 状态 | 含义 | 后续动作 |
|---|---|---|
| 已确认 | 用户已经在本专题讨论中明确选择 | 设计审查只检查后果与闭合性，不重新猜测方向 |
| 必然推导 | 为满足已确认决定而必须成立的后果 | 用户可否决；未否决时随正式 Owner 一起固化 |
| 设计建议 | 为闭合实现而提出的具体选择 | 必须在正式迁移前获得明确接受或修改 |
| 待决 | 当前信息不足，多个选择会改变 public/runtime contract | 不得实现或由 Codex自行选择 |

## 6. 已确认决策

| ID | 决策 |
|---|---|
| `C-01` | `@SomaKey` 保留，表达 primary unique identity；identity 不原地修改，使用 delete + insert。 |
| `C-02` | `@SomaUnique` 保留，表达 secondary unique exact access。 |
| `C-03` | `@SomaIndex` 保留，表达 secondary non-unique exact access；不支持 range lookup。 |
| `C-04` | `@SomaOrder` 不再由 SOMA 支持；需要顺序时显式 `.sorted(...)`。 |
| `C-05` | priority/event queue 使用 application-owned min-heap 等专用结构，不强行用 SOMA maintained order 模拟。 |
| `C-06` | Sparse Set 与 key-to-Entity bounded-domain 方向删除；不引入 stable EntityDirectory。 |
| `C-07` | keyed 和 dense table 都保持 packed columnar storage。 |
| `C-08` | keyed、dense 的删除都采用 swap-remove/tail-fill，不采用 stable compaction。 |
| `C-09` | keyed、dense、`@SomaIndex` 都不保证物理遍历顺序。 |
| `C-10` | 当前候选下标缓冲统一使用名称 `IndexBuffer`。 |
| `C-11` | 当前专题只关注功能、API、runtime correctness、性能与 GC；发布工作排除。 |

## 7. 必然推导

| ID | 推导结论 | 原因 |
|---|---|---|
| `D-01` | Primary/unique/index access 必须直接定位 current Index，并在 row move 后修复。 | 不再有 stable Entity；columns 仍按 packed Index 存储。 |
| `D-02` | exact access 必须 eager/incremental 维护；读取时不得因 dirty 做全表 rebuild。 | `C-03` 与消除 rebuild storm 的目标共同要求。 |
| `D-03` | default source 按当时 `[0,size)` physical Index 遍历，但 structural mutation 后该顺序可改变。 | packed + swap-remove + no order guarantee。 |
| `D-04` | non-unique index source 只承诺成员集合，不承诺 group 内 physical、insertion 或 business order。 | grouped hash maintenance 会因 append/update/remove/rehash 改变枚举顺序。 |
| `D-05` | 未显式排序的 `findFirst/firstOrThrow/limit/fetchAll/rowIndexes/update` 服从本次 source sequence。 | terminal 必须有可执行顺序，但该顺序不是稳定业务契约。 |
| `D-06` | dynamic sort equal-comparator ties 最多保留本次 source order；跨 mutation 确定性要求 comparator 提供完整 tie-break。 | source 本身可能无稳定顺序。 |
| `D-07` | dense whole table/child `List` 是本次 physical traversal 的 detached snapshot，不是业务 sequence。 | `List` container shape 不得反向创建 maintained order。 |
| `D-08` | Index 在 structural mutation 后失效；`IndexBuffer` 不能跨 terminal/epoch 保存。 | tail-fill 会让 survivor 改变位置。 |
| `D-09` | external heap 必须保存 stable key/id，不得保存 packed Index。 | heap 生命周期可能跨越 table structural mutation。 |
| `D-10` | `Map<HashValue,int[]>` 不能作为 primitive selector 的 canonical runtime 实现。 | object-per-group、数组扩容、hash collision 和 row-move repair 都与 GC/维护目标冲突。 |

## 8. 设计建议

| ID | 建议 | 审查重点 |
|---|---|---|
| `P-01` | PrimaryLocator、UniqueLocator 使用 primitive open addressing；GroupedExactIndex 使用 primitive bucket/group/row-link arrays。 | full equality、rehash staging、memory overhead、String/reference selector breadth。 |
| `P-02` | `IndexBuffer` 保持 internal，不公开 live/reusable buffer；public index export 的最终shape单独由 `O-01` 决定。 | raw `int[]` 无法携带 epoch，public snapshot与internal scratch不能混为一谈。 |
| `P-03` | multi-row update 的 unique validation 基于 terminal 最终 staged state，允许合法 value swap。 | delta validation scratch、commit 无 expected failure。 |
| `P-04` | single/multi remove 使用 sorted selected Index + tail survivor fill，不使用 `boolean[size]` mark 和 full forward compaction。 | selected 去重、child cascade、locator/index relocate。 |
| `P-05` | pipeline plan 使用 shared one-shot stage owner + generation token；每个 intermediate 最多分配小 wrapper，不复制四组 stage arrays。 | alias consumed 语义和 stage overflow。 |
| `P-06` | `sorted(...).firstOrThrow()` 使用 stable arg-min；general sorted terminal 才填 candidate/sort buffer。 | comparator callback/lifecycle、tie semantics、scanned/matched stats。 |
| `P-07` | 直接移除 order annotation surface，而不是保留无效 deprecated annotation。 | 项目尚未发布，但仍需 schema/golden/consumer migration note。 |
| `P-08` | runtime generated/runtime/plan compatibility identity 统一提升到下一 protocol generation。 | exact token、manifest 与 mismatch fixture必须原子更新。 |

## 9. 待决问题

| ID | 问题 | 为什么不能由实现自行决定 |
|---|---|---|
| `O-01` | public `rowIndexes(): int[]` 是否继续保留，还是增加携带 epoch 的 `IndexSnapshot`/等价类型？ | raw int 能在 swap-remove 后静默指向另一 row；改变它会影响 generated public API。 |
| `O-02` | `UpdateResult.sidecarMaintained/sidecarRebuilt` 与 `RemoveResult` 对应字段如何迁移？ | 继续沿用会命名失真；删除、替换或 deprecated coexistence 都是 public contract 决策。 |
| `O-03` | `TableStats` 的 exact-index public getter 最小集合是什么？ | probe/rehash/group/storage 全公开会膨胀 API，只保留汇总又可能不足以诊断。 |
| `O-04` | `TablePlan` 是直接删除 sparse/sidecar 字段，还是保留兼容构造窗口？ | 影响 source/binary/runtime-plan-hash compatibility；项目虽未发布，也应明确选择。 |
| `O-05` | 是否在本次 baseline 增加 allocation-free `firstIndexOrThrow` 或 callback-scoped first terminal？ | 可以减少每轮 chosen-row materialization，但属于新增 public API，不是 runtime internal optimization。 |
| `O-06` | selector 是否继续沿用当前“不含 String/optional/table leaf”的类型范围？ | 扩大 selector type breadth 会改变 annotation/schema/codegen/runtime 复杂度，不应被本次结构优化顺带决定。 |

在 `O-01` 至 `O-06` 被明确处理前，可以审查和原型化 internal 算法，但不能完成正式 public/protocol cutover。

## 10. Capability 与唯一 Owner 影响

| Capability ID | 本专题影响 | 唯一事实 Owner |
|---|---|---|
| `V1-ANNOTATION-SCHEMA` | 删除 order declaration，收敛 key/unique/index exact semantics | `soma-annotations/docs/annotation-schema-contract.md` |
| `V1-PROCESSING-MODEL` | normalized selector/order model、diagnostics 和 generated naming 变化 | `soma-processor/docs/schema-processing-contract.md` |
| `V1-SCHEMA-HASH` | order fact退出 canonical model；旧 schema 需要重新生成 | `soma-processor/docs/schema-processing-contract.md` |
| `V1-PUBLIC-COMPATIBILITY` | annotation、generated source、plan/stats/result/protocol breaking cutover | `docs/public-api-compatibility-contract.md` |
| `V1-GENERATED-API` | 删除 order source，保留 exact index/unique source，明确 unordered terminal | `docs/generated-table-api-contract.md` |
| `V1-DENSE-STORAGE` | dense remove 改为 swap-remove，继续 packed | `soma-runtime-core/docs/table-store-contract.md` |
| `V1-ROW-PIPELINE` | source sequence、IndexBuffer、fusion、arg-min 与 allocation shape | `docs/generated-table-api-contract.md` / runtime implementation Owner 各自负责其事实 |
| `V1-KEYED-IDENTITY` | 删除 SparseInt path，PrimaryLocator hash-only family | API 与 TableStore 各自唯一 Owner |
| `V1-ACCESS-STRUCTURES` | 删除 order/dirty rebuild，建立 eager unique/grouped exact structures | `soma-runtime-core/docs/table-store-contract.md` |
| `V1-MUTATION` | final-state unique validation、swap-remove 和 index repair | `docs/runtime-correctness-model.md` / TableStore 实现义务 |
| `V1-CHILD-OWNERSHIP` | packed parent row move 必须保持 owner token/handle identity | `soma-runtime-core/docs/runtime-lifecycle-contract.md` |
| `V1-MATERIALIZATION` | dense `List` 与 unordered source sequence后果 | `docs/materialization-contract.md` |
| `V1-RUNTIME-LIFECYCLE` | Index/epoch、buffer acquire/reset、row move/child facade | `soma-runtime-core/docs/runtime-lifecycle-contract.md` |
| `V1-RUNTIME-ERRORS` | sidecar stats退出、exact-index metrics 与 result迁移 | `soma-runtime-core/docs/runtime-errors-and-diagnostics-contract.md` |
| `V1-RUNTIME-PLAN` | sparse/sidecar plan dimensions退出，新 access identity | `soma-runtime-core/docs/runtime-plan-contract.md` |
| `V1-PERFORMANCE-SHAPE` | no rebuild-on-read、primitive grouped index、IndexBuffer、swap-remove | runtime performance implementation Owner |
| `V1-EVIDENCE-TOOLING` | oracle、collision、mutation trace、allocation/GC helper | `soma-testkit/docs/testkit-contract.md` |
| `V1-CONSUMER-PACKAGE` | old order/sparse consumer migration与新 protocol mismatch | `docs/build-and-dependency-contract.md` |
| `V1-SCENARIO-BENCHMARK` | FJSP/VRP/Simulation/Game 和 component lanes迁移 | examples/benchmark 各自唯一 Owner |

`V1-COLUMN-ACCESS`、materialization budget、child exclusive ownership、single-owner concurrency、typed errors和Java 8边界不应因本专题缩水；它们是必须持续通过的 non-regression surface。

## 11. 本轮 slice 边界

本轮出口只有：

- 临时设计包完整；
- 已确认、推导、建议、待决分层清楚；
- 正式 Owner 与 capability 影响可追踪；
- 后续迁移阶段、禁止捷径和 evidence 可执行；
- 文档检查通过。

本轮故意不做：

- 不修改 capability ledger 状态；
- 不修改任何正式 Owner；
- 不删除 annotation/runtime class；
- 不改 generated output、fixtures、examples 或 benchmark；
- 不宣称新设计已经实现、验证或更快。
