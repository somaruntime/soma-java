# SOMA Operation Pipeline Stage 0 审查与冻结记录

类型：Temporary

状态：Stage 0 完成

Owner：SOMA Java Operation Pipeline 专题治理

事实范围：Stage 0 的审查输入、发现、候选冻结项、待决分类、Stage 1 入口与范围核对

非事实范围：正式 Design 决定、实施完成声明、性能收益、public compatibility 结论和 release readiness

正式事实源：否

实施授权：无；本记录只关闭 Stage 0，不自动启动或授权 Stage 1/Stage 2 的文档、benchmark、代码或正式 Owner 变更

上位专题：[专题治理总纲](README.md)

审查基线：`5217c27ae4a07a5ec8ec3b70ae92224aac5706d2`

最后审查日期：2026-07-22

后续处置：本文件保留 Stage 0 历史输入；其中 `Pending`/working term 已由 [Stage 1 决策](stage-1-decisions.md)全部关闭，不再是当前待决项。

## 1. Stage 0 目标

Stage 0 不是确定最终 public 名称或开始重写 generator，而是关闭四个设计前置问题：

1. 当前 Table/Key/Column DSL 到底具备哪些真实能力；
2. 产品 family、DSL algebra 和 IR 层次是否能完整承载当前语义；
3. 哪些优化可由既有 Design 推导，哪些必须依赖 evidence 或 Owner 决定；
4. 核心命名的候选空间和 breaking surface 是否已经有限、可审查。

## 2. 审查输入

### 2.1 正式语义 Owner

- `docs/design/soma-java-design-constitution.md`
- `docs/design/system-architecture.md`
- `docs/design/domain-language.md`
- `docs/design/table-storage-and-access.md`
- `docs/design/schema-and-generated-api.md`
- `docs/design/ownership-and-lifecycle.md`
- `docs/design/correctness-and-failure.md`
- `docs/design/performance-model.md`
- `docs/design/runtime-plan-and-observability.md`
- `docs/design/compatibility-security-and-versioning.md`

### 2.2 当前实现与 public surface

- `soma-processor/src/main/java/com/hgtech/soma/processor/DenseTableSourceGenerator.java`
- `soma-processor/src/main/java/com/hgtech/soma/processor/DenseExactIndexSourceEmitter.java`
- `soma-runtime-core/src/main/java/com/hgtech/soma/runtime/AbstractColumnPipeline.java`
- primitive/enum `*ColumnPipeline` 与 `*ColumnView`
- `soma-testkit/src/test/fixtures/external-maven-*/expected/*.javap.txt`
- `soma-examples/src/test/fixtures/phase6/expected-public-api-facts.txt`

### 2.3 场景与 evidence

- FJSP、VRP、Simulation、Game executable scenario；
- `reports/current-performance-summary.md`；
- `soma-examples/docs/fjsp-e2e-scenario.md`。

Stage 0 使用性能 Report 只确认“需要重新归因测量”，没有把旧测量直接转成新实现目标或收益承诺。

## 3. 关键审查发现

| ID | 发现 | 对候选设计的影响 |
|---|---|---|
| `S0-F-01` | 当前 multi-field `*Rows` 是 lazy intermediate、terminal-time evaluation、one-shot handle | 它是本专题主要实现对象，不应被 generic abstraction降级 |
| `S0-F-02` | 当前 plan 用 source wrapper、五组 parallel arrays、derived handle 和 terminal-specific branch 表达 | compact representation 有真实优化空间，但不能让现状反向定义 IR |
| `S0-F-03` | `@SomaIndex` 与 `@SomaUnique` 都生成 `findBy*` multi-field source；unique 语义额外提供 `0..1` trait | semantic source 必须区分 group 与 max-one，即使当前 physical structure相近 |
| `S0-F-04` | `*Keys` 当前无 intermediate、可重复调用，只有 visit/materialize/first terminal | 不能把它描述为已经具有 Stream-style lazy/one-shot lifecycle |
| `S0-F-05` | typed Column Pipeline 当前无 intermediate、可重复调用，只有 typed `forEach` | reduction/match/filter 都是新能力；optional absence 当前被跳过 |
| `S0-F-06` | `ColumnView`、key/current-Index direct access、Batch 和 full materialization都有独立 lifecycle/resource 语义 | 它们是邻接能力，不应绕进 Pipeline planner |
| `S0-F-07` | exact source direct traversal、same-loop fusion、streaming、stable full sort 和 stable arg-min 已经存在 | 新架构必须先保持这些能力，再用 evidence证明收益 |
| `S0-F-08` | `rowIndexes()`、`findRowIndex/rowIndexOf` 和 `*Row/*Rows` 都在 generated public/golden/scenario 中出现 | 命名治理是实质 breaking surface，不是内部重命名 |
| `S0-F-09` | source-only count 等 shortcut 会改变当前 scanned/matched 或 comparator execution | optimizer legality 依赖 stats/callback Owner，不能只按数学结果判断 |
| `S0-F-10` | scenario 主要用 multi-field selector/update/remove/sort/snapshot 与 ColumnView；Key/Column traversal使用很少 | 当前专题不应扩大为对称 API 填充工程 |

## 4. Stage 0 候选冻结项

“冻结”表示后续 Stage 1 以此为唯一候选基线；它仍不是正式 Design。若要推翻，必须记录新 evidence 和影响，不在实施中静默漂移。

| ID | 冻结内容 |
|---|---|
| `S0-FR-01` | `Operation Pipeline` 作为本专题唯一 umbrella working term，待 `OP-DEC-01` 决定是否正式采用 |
| `S0-FR-02` | family 只含 multi-field、key、typed column 三个 Pipeline shape；View/direct/bulk access 明确在外 |
| `S0-FR-03` | DSL algebra 固定为 `Source Stage* Terminal`；terminal-only shape 是 `Stage* = 0`，不要求方法对称 |
| `S0-FR-04` | IR 责任固定为 Semantic Pipeline、Evaluation Request、Bound Evaluation、Physical Plan、Representation/Executor 五层 |
| `S0-FR-05` | semantic descriptor 使用 [操作目录](operation-catalog.md)中的最小字段；实现可把对象编译成 generated fields/locals |
| `S0-FR-06` | shared architecture 只共享 descriptor vocabulary、binding/legality protocol；不共享 generic runtime object graph |
| `S0-FR-07` | multi-field 为 Stage 2 默认实现范围；Key/Column 只要求现有 terminal 能被体系准确解释 |
| `S0-FR-08` | 新 `allMatch`、Column reduction、public top-k、map/flatMap/reduce/collect 不计入既定能力 |
| `S0-FR-09` | optimizer 必须同时保持 result、sequence、callback、failure、lifecycle、resource、stats 和 mutation atomicity |
| `S0-FR-10` | 术语 shortlist、保留词和 breaking inventory 以 [核心术语](core-terminology.md)为唯一 Temporary 登记 |

## 5. 待决项分类

分类含义：

- `Design-derived`：既有正式 Design 已给出方向，Stage 1 只需证明实现等语义；
- `Evidence-dependent`：必须由 benchmark、allocation、code-size、oracle 或 consumer evidence 选择；
- `Owner-decision`：会改变长期语义、public/generated surface、stats 或 capability，必须明确裁决；
- `Mixed`：先由 evidence收窄，再由 Owner 接受或拒绝。

| ID | 分类 | Stage 0 处置 | Stage 1 所需输入 |
|---|---|---|---|
| `OP-DEC-01` | Owner-decision | umbrella 与 shape 名只保留 shortlist | 四场景可读性、collision scan、formal terminology decision |
| `OP-DEC-02` | Owner-decision | `schema carrier/table element/cursor` 概念已分层 | callback escape/lifecycle 说明与最终 canonical terms |
| `OP-DEC-03` | Owner-decision | generated replacement map 和影响面已登记 | javap delta、consumer migration、diagnostic mapping |
| `OP-DEC-04` | Design-derived + Owner-decision | generic runtime排除；typed specialization冻结；Key/Column lifecycle 子问题未决 | component design、no-boxing proof、正式 lifecycle decision |
| `OP-DEC-05` | Mixed | clean cutover 为首选候选，不假定 identity 结论 | schema/generated/protocol diff、external consumer evidence |
| `OP-DEC-06` | Owner-decision | 当前 Key/Column 可复用事实已登记；目标是否 one-shot 未宣称 | 使用场景、compatibility、统一 lifecycle收益与成本 |
| `RP-DEC-01` | Owner-decision | count/normalization shortcut保持 Pending | logical/physical work模型、TableStats contract |
| `RP-DEC-02` | Owner-decision | 默认不消除或重排 callback | predicate/comparator purity、调用/失败可观察边界 |
| `RP-DEC-03` | Evidence-dependent | source descriptor只冻结语义字段 | exact source allocation/throughput/class-size matrix |
| `RP-DEC-04` | Evidence-dependent | 不从当前 capacity=4 推导目标值 | chain length distribution、allocation/retained/code-size |
| `RP-DEC-05` | Mixed | top-k 只在 legality matrix 中保留 | differential oracle、threshold/resource benchmark、Owner接受 |
| `RP-DEC-06` | Owner-decision | `allMatch` 排除出既定 Stage 2 scope | 若重开，需 capability admission 与 public API 决定 |
| `RP-DEC-07` | Owner-decision | typed reduction 排除出既定 Stage 2 scope | 若重开，逐 type 定义 absence/empty/overflow/NaN |
| `RP-DEC-08` | Mixed | plan retained accounting 只作 evidence lane | estimator overhead、字段/模式语义、Stats Owner决定 |

## 6. Stage 1 入口任务

Stage 1 只能围绕以下闭合任务推进：

1. 建立 `source × stage chain × terminal × callback capture` component baseline，分开 plan、scratch、snapshot、materialization 和 application allocation；
2. 记录 canonical scenario 的 chain length、source kind、terminal distribution，而不是凭 generator 首次扩容值选择 inline capacity；
3. 对 exact source descriptor、compact stage storage、overflow strategy 和 top-k 建立可复现实验；
4. 建立 reference execution/differential oracle，覆盖 sequence、callback/failure、stats、lifecycle、resource 和 mutation；
5. 用 FJSP integrated checksum、throughput、allocated B/op 与 GC 作为 end-to-end non-regression；
6. 对术语 shortlist 做四场景代码演练、generated collision scan、javap/diagnostic/identity impact；
7. 形成逐项 Owner decision package，并在获得明确实施授权前停止。

若用户后续明确启动 Stage 1，应先确认 Temporary evidence/design 与 benchmark harness 的具体范围；修改正式 Design、public/generated API、runtime semantics 或进入 Stage 2 仍需明确授权。

## 7. Stage 0 出口检查

| 检查 | 结果 |
|---|---|
| 当前 operation 是否完整盘点 | 通过；见 [操作目录](operation-catalog.md) |
| current 与 target 是否分开 | 通过；Key/Column 差异已显式登记 |
| 产品 family 与非 Pipeline 边界是否明确 | 通过 |
| IR 层次是否避免语义/存储混同 | 通过 |
| optimizer 是否有逐项 legality | 通过 |
| 术语是否有限 shortlist 而非开放脑暴 | 通过 |
| breaking surface 是否登记 | 通过 |
| 决策是否完成三类归档 | 通过；Mixed 项明确前置顺序 |
| 是否越权修改正式文档或代码 | 未发生 |
| 是否已授权实施 | 否 |

## 8. 审查结论

Stage 0 已完成。候选产品模型能够解释当前 multi-field、Key、Column 三类 operation，又没有把 `ColumnView`、direct access 或尚不存在的 Stream 对称能力混入 Pipeline。IR、操作目录、优化合法性和术语 breaking surface 已形成可进入 evidence/Owner decision 的闭合输入。

尚未完成的是 Stage 1：性能归因、表示选择、命名与 lifecycle/stats/callback/compatibility 裁决。当前不能据此声明 public API 已决定、代码可直接重写或性能收益已经成立。
