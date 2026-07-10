# SOMA Java V1 实施架构蓝图

状态：临时实施蓝图
正式事实源：否
生命周期：普通临时设计；稳定事实迁入唯一 Owner 后删除
对齐基线：[项目架构设计](../architecture-design.md)、[实现策略](../implementation-strategy.md)、[V1 验证门禁](../validation-gates.md) 与各模块正式 Owner 契约
当前细化范围：完整 V1 轮廓，重点细化 Phase 0、Phase 1 和 generated/runtime binding
允许演进：内部 package、class/interface 形态、协作方式、算法、helper、fixture 和 benchmark 参数
不得自行改变：annotation/public API、schema/hash、ownership、lifecycle、error、materialization、compiler support、V1 gate 和 release claim
最后审查日期：2026-07-10

## 1. 文档定位

本文把正式设计映射为首批可实现、可验证、可调整的代码结构。它用于回答：

- 第一个 vertical slice 从哪些组件开始；
- 每个组件由哪个模块拥有，保存什么状态，依赖谁；
- 哪些概念值得形成 Java type 或 interface；
- build-time、generated binding 和 runtime mutation 的主流程如何闭合；
- 哪些决定必须在编码前解决，哪些应等待实现或 benchmark 证据。

本文不定义新产品语义。与正式 Owner 文档冲突时，以正式 Owner 为准并修改本文。实现、gate 或 release report 不得把本文作为唯一事实依据。

## 2. 决策标记

| 标记 | 含义 | 处理方式 |
|---|---|---|
| `Baseline` | 正式 Owner 已接受的约束 | 实现必须遵守；修改时先改 Owner |
| `P0` | 首个相关 vertical slice 编码前必须决定 | 在 slice review 中给出选择和证据 |
| `P1` | 可以在首个实现中试验并固化 | 由代码、测试和结构审查决定 |
| `P2` | 需要后续功能、benchmark 或运行证据 | 只保留扩展位，不提前抽象 |
| `Open` | 设计方向尚未确定 | 不猜测，不进入实现承诺 |

候选 type/package 名只表达职责，不构成 public API。只有进入代码、API manifest、golden 和对应 Owner 文档后，名称才获得相应稳定性。

`P0`、`P1`、`P2` 只表示决策和证据应当出现的时间，不是产品版本或 capability 等级。Phase 0 至 Phase 6 只表示实施顺序；本项目不从蓝图派生 `v0.x`、MVP、Lite、Basic 或任何替代完整 V1 的阶段目标。

## 3. 架构基线与切片边界

`Baseline`：V1 保持以下主链：

```text
annotation source
  -> javac 8 parse-phase @SomaValue lowering
  -> JSR 269 collection / validation / normalization / hash
  -> schema-specific generated binding
  -> annotation-agnostic TableStore kernel
  -> examples / testkit / benchmark / gate evidence
```

第一轮实施只冻结足以启动 Phase 0 和 Phase 1 的结构：

- Phase 0：compiler plugin、processor pipeline、deterministic output 和 external consumer fixture；
- Phase 1：dense table、primitive/presence storage、Batch、default Row Pipeline、lifecycle/error minimum；
- 两个阶段之间：generated code 如何一次性绑定 runtime storage，并在 hot loop 直接访问 concrete column。

Phase 2 至 Phase 6 只保留兼容的组件位置和 admission trigger，不提前列出完整 class catalog。

所有 Phase 遵守正式 [防缩水执行协议](../implementation-strategy.md#8-防缩水执行协议)：当前 breadth 可以尚未实现，但已经落地的 public/generated semantics、canonical storage 和 hot path 必须是最终 V1 架构的有效子集。后续应 additive completion 或 contract-preserving internal refinement；需要 consumer migration、核心事实迁移或 canonical hot-path rewrite 的候选方案不得进入实现。

## 4. Maven 模块与拆分策略

### 4.1 当前模块保持不变

| 模块 | 实施责任 | 当前细化深度 |
|---|---|---|
| `soma-annotations` | public schema annotation declarations | 只确认 public/internal package 边界 |
| `soma-processor` | javac 8 lowering、processing、normalization/hash、diagnostics、codegen | Phase 0 详细设计 |
| `soma-runtime-core` | generated-facing runtime protocol 与 TableStore kernel | Phase 1 详细设计 |
| `soma-testkit` | compile/golden/invariant/performance-shape helper | Phase 0/1 evidence 详细设计 |
| `soma-examples` | formal schema/usage/E2E scenarios | 只建立 fixture 接入位置 |
| `soma-benchmarks` | benchmark runner、lane 和 evidence artifact | 只建立 Phase 1 smoke 接入位置 |

`Baseline`：只有一个 javac 8 adapter 时，不预拆 `soma-javac8-adapter` 空模块。

新增 Maven 模块至少需要满足一个真实条件：

- 独立 artifact consumer 或 runtime classpath 边界；
- 独立发布、版本或 compiler support 生命周期；
- 当前模块依赖方向无法在不循环的情况下表达；
- 两个以上已实现 adapter/provider 共享稳定 pure core；
- build-only 与 runtime dependency 无法通过现有 artifact/POM 正确隔离。

仅因为 package 较多、类较多或“未来可能复用”，不足以新增模块。

### 4.2 候选 package role map

以下是 role map，不是最终 FQN：

```text
soma-annotations
  annotation/                 public annotation declarations

soma-processor
  provider/                   javac Plugin / JSR 269 Processor entry
  javac8/                     compiler-specific AST lowering
  processing/                 round/session orchestration
  model/                      collected / normalized / generation models
  canonical/                  canonical serialization and exact hash
  codegen/                    source/resource generation
  diagnostic/                 stable diagnostic code and reporting

soma-runtime-core
  api/                        owner-authorized handwritten public types only
  protocol/                   generated-facing runtime protocol; P0 boundary
  internal/store/             TableStore / layout / row space
  internal/column/            primitive/object columns and presence bitmap
  internal/access/            scan, RowSequence, later index/order paths
  internal/mutation/          prepare/publish/repair/epoch coordination
  internal/lifecycle/         release, view pin, ownership registry
  internal/materialize/       schema-agnostic budget/path tracker
  internal/diagnostic/        errors, stats and invariant hooks

soma-testkit
  compile/ golden/ runtime/ shape/ evidence/

soma-examples
  <scenario>/schema/ <scenario>/usage/ <scenario>/smoke/

soma-benchmarks
  runner/ scenario/ artifact/
```

物理 package 是否完全照此拆分属于 `P1`。禁止创建没有 owner、状态、边界或证据职责的 `utils`、`helpers`、`manager` 聚合包。

## 5. Phase 0：processor 核心结构

### 5.1 组件目录

| 候选角色 | 责任 | 建议形态 | admission reason |
|---|---|---|---|
| Plugin provider | 注册 stable plugin name，接收 javac lifecycle | public provider class | 外部 compiler boundary |
| `Javac8ValueLowerer` | Enter 前完成 `@SomaValue` effective-type lowering | concrete internal class | 只有一个 adapter，无需 strategy interface |
| Processor provider | JSR 269 entry、round 和 finalization | public provider class | 外部 processor boundary |
| Processing session | 一次 compilation 的 collection/codegen 状态 | concrete state owner | 拥有 round 去重和完成状态 |
| Schema collector | 把 lowered element model 转成 collected model | session-local collaborator | source model 到 pure model 边界 |
| Schema validator | 解析引用、selector、ownership、name collision | concrete collaborator | 集中 semantic validation |
| Normalized schema | validated immutable canonical facts | immutable value graph | codegen/hash 的唯一输入事实 |
| Canonical encoder | normalized model -> canonical UTF-8 JSON | stateless function/class | exact hash boundary |
| Schema hasher | version prefix + canonical bytes -> SHA-256 | stateless function/class | compatibility identity boundary |
| Generation model | 面向 Java emission 的 immutable projection | immutable value graph | 隔离 schema facts和 source formatting |
| Java/resource emitter | deterministic generated source/resources | concrete collaborator | output system boundary |
| Diagnostic reporter | stable code/context -> compiler diagnostic | narrow concrete boundary | plugin/processor 共享错误语义 |

`P1`：stateless normalization、encoding、hashing 和 naming logic 优先放入已有 owner 或使用 domain-specific package-private static function holder；只有需要持有 compilation state、domain invariant 或外部 boundary 时才升级为独立 class，禁止产生泛化 `Utils`。

### 5.2 模型分层

```text
javac Tree / Element
  -> CollectedSchemaDraft       // 允许 unresolved reference，不进入 codegen
  -> ValidatedSchema            // 所有 semantic reference 已解析
  -> NormalizedSchema           // canonical order/default/name/type/hash input
  -> GenerationModel            // Java artifact/name/static-binding projection
  -> generated source/resource
```

约束：

- javac internal type 只能存在于 `provider/javac8` 边界，不能进入 normalized/generation model；
- validation failure 不创建可用 `NormalizedSchema` 或 generated artifact；
- codegen 不重新读取 annotation/Element 来补语义；
- canonical encoder 不读取 path、timestamp、output directory 或 formatter 状态；
- diagnostic code/context 与 message prose 分离；
- generated source 和 resource emission 使用同一 immutable generation session input。

`P1`：`ValidatedSchema` 是否作为独立 Java type，取决于 validator 与 normalizer 之间是否存在可单独断言的不变量；如果只是一次函数边界，可直接生成 `NormalizedSchema`，不为了流程图增加空模型。

### 5.3 Compiler/processor 激活

`P0` 由第一个 external-consumer fixture 固化：

- plugin stable name；
- plugin provider FQN；
- processor provider FQN；
- service registration files；
- Maven Compiler Plugin 的 plugin/processor path 与参数；
- transformer 缺失、unsupported compiler、identity mismatch 的诊断代码。

这些名称在 fixture/golden 之前保持 symbolic，不在本文中伪造最终值。

## 6. Phase 1：runtime-core 核心结构

### 6.1 Dense table aggregate

```text
generated XxxTable facade
  -> generated XxxStorageBinding
       -> TableStore
            -> TableLayout
            -> RuntimePlan
            -> RowSpace
            -> ColumnStore
                 -> Int/Long/Double/Float/Byte/Object columns
                 -> PresenceBitmap
            -> ScanAccessPath / RowSequence
            -> MutationCoordinator
            -> LifecycleState
            -> Error/Stats state
```

| 候选角色 | 持有状态/不变量 | 初始形态 |
|---|---|---|
| `TableLayout` | schema hash、leaf kind、column slot、binding metadata | immutable concrete value |
| `RuntimePlan` | capacity/growth/stats/budget 等 effective plan | immutable public或generated-facing type，最终可见性 `P0` |
| `TableStore` | aggregate root 和 component lifecycle | concrete state owner |
| `RowSpace` | size/capacity、packed `[0,size)` | concrete state owner |
| typed columns | primitive/reference payload、capacity、dead-reference clearing | concrete final classes |
| `PresenceBitmap` | bit words、present count、row move/clear | concrete final class |
| scan path/sequence | 当前 terminal 的 primitive row traversal | Phase 1 concrete path；接口延后 |
| `MutationCoordinator` | prepare/publish/repair/epoch 顺序 | concrete aggregate collaborator |
| `LifecycleState` | released、epoch、active view count | concrete state owner |
| error/stats state | structured failure context、summary counters | concrete state owner |

禁止把所有 columns 统一成 `Column<Object>`。Capacity orchestration 可以通过 `ColumnStore` 的明确 bulk operation 完成，但 hot getter/setter 保持 concrete primitive binding。

### 6.2 后续 phase 的 admission points

| Phase | 新增稳定变化 | 何时引入抽象 |
|---|---|---|
| Phase 2 | `SparseIntKeySpace`、int/long/composite `HashKeySpace` | 两个实现出现时决定 `KeySpace` interface/abstract base |
| Phase 3 | scan/index/unique/order/dynamic-sort source | 第二种真实 source 出现时固化 `AccessPath`/`RowSequence` protocol |
| Phase 4 | ownership registry、child handle、recursive tracker | child aggregate slice 中引入，不污染 Phase 1 dense core |
| Phase 5 | full codegen breadth | 扩展 generation model/emitter，不复制 processor pipeline |
| Phase 6 | runners/scenarios/reports | 只消费正式 API 和 evidence contract |

`KeySpace`、`AccessPath` 是正式概念，但不因此要求 Phase 1 立即创建空 Java interface。

## 7. Generated/runtime binding 边界

这是 Phase 1 的首要 `P0` 结构决策。

### 7.1 必须同时满足的条件

- generated facade/Rows/Mutator 能静态绑定 concrete column 和 selector leaf；
- table create 时完成 schema/runtime/plan compatibility check；
- hot terminal 不逐 row 查 metadata、反射或按 type switch；
- generated public API 不暴露 `TableStore`、`RowSlot`、column mutation、bitmap、sidecar 或 handle；
- Java accessibility 需要的 generated-facing type 不自动进入用户 API manifest；
- processor/runtime protocol mismatch 在 create boundary fail fast。

### 7.2 候选方案

`P0 candidate`：在 `soma-runtime-core` 中建立小型 generated-facing `protocol` surface。相关 Java type 可以因为跨 package 调用而是 `public`，但其 consumer 仅是 processor-generated source，并由 runtime compatibility version 管理；它们不属于用户手写 API manifest。

绑定过程候选：

```text
generated metadata + RuntimePlan
  -> runtime create/compatibility validation
  -> TableStore + typed columns
  -> generated XxxStorageBinding resolves column references once
  -> generated Rows/Cursor/Mutator cache typed binding
  -> terminal inner loop uses primitive/final/static call sites
```

替代方案及当前判断：

- generated code 与 runtime implementation 放入同一 package：会污染用户 generated package 和 package ownership，暂不采用；
- 对 generated code 暴露全部 runtime internals：破坏 internal boundary，不采用；
- runtime metadata interpreter：违反正式 hot-path 契约，不采用；
- 为每个 schema 生成完整 storage engine：超出 V1 维护预算，不采用。

`Open`：generated-facing protocol 的确切 Java visibility、package、API manifest 排除方式和 compatibility test 形态，需要由最小 dense-table spike 验证后确认。若该选择改变正式 public/internal 分类，必须回写 [Public API 与兼容性契约](../public-api-compatibility-contract.md)。

## 8. 核心流程

### 8.1 Compile-time flow

```text
parse source
  -> plugin detects @SomaValue
  -> pre-Enter lowering or blocking diagnostic
  -> processor validates lowering/compiler identity
  -> collect declarations across rounds
  -> resolve and validate complete schema
  -> normalize and canonical encode
  -> compute exact hash
  -> build generation model
  -> emit source/resources once
  -> compile generated source
```

Failure boundary：lowering、identity、validation、canonicalization 或 emission 任一步失败，都不能留下可被 package smoke 当作成功结果的 schema-specific artifact。

### 8.2 Table create flow

```text
generated metadata + caller/default RuntimePlan
  -> canonicalize and validate effective plan
  -> check generated target/schema hash/protocol/runtime compatibility
  -> allocate layout/components locally
  -> bind generated typed access
  -> publish active empty table
```

任何 expected failure 都不发布 half-initialized aggregate。

### 8.3 Dense Batch mutation flow

```text
generated Batch
  -> validate/default/presence and estimate rows
  -> precheck lifecycle/view pin/resource limit
  -> prepare all fallible capacity/scratch/derived state
  -> enter no-expected-failure commit region
  -> write invisible tail or swap prepared replacement buffers
  -> publish size/base references and derived dirty/current state
  -> increment epoch and publish stats
```

`P1`：prepare buffer、copy strategy 和 publish point 的具体算法由 implementation spike 决定。`addBatch` 可以先写当前 size 之外的不可见 tail；`replaceAll` 可以在所有 expected failure 已排除后复用 live capacity，或切换 staged buffers。任何 expected failure 都必须发生在 commit region 之前，并保持旧 visible facts和旧 epoch。

### 8.4 Row Pipeline flow

```text
generated rows()/source method
  -> construct one-shot lazy pipeline description
  -> terminal checks lifecycle and captures execution epoch
  -> source produces primitive RowSequence
  -> fused filter/skip/limit loop with reusable cursor
  -> read result or MutationCoordinator terminal
  -> publish terminal stats
```

Phase 1 只实现 packed scan source；index/order source 在 Phase 3 接入同一 terminal contract。Cursor 不逃逸 callback，non-materializing terminal 不按 row 分配对象。

### 8.5 Materialization/lifecycle flow

Phase 1 只建立 single-table detached materialization 和 budget hook 的最小接入位置；recursive child tracker 在 Phase 4 实现：

```text
generated materializer
  -> acquire runtime budget/path tracker
  -> read typed columns and presence
  -> construct detached schema object
  -> return complete result or discard partial construction
```

release、stale view、released view 和 view-pinned error 必须保持可区分。V1 不引入 cross-table transaction、concurrent access、persistence 或 hidden write-back。

## 9. Vertical slice 与证据

### 9.1 Phase 0 最小 slice

输入：一个有效 `@SomaValue` + minimal schema fixture、一个冲突 fixture、一个 transformer-missing fixture。

输出：

- provider/service activation；
- effective class/field/constructor/equality/hash/toString shape；
- processor 读取 lowered model；
- deterministic generated companion/resource；
- direct javac 与 external Maven consumer 的 compile/classfile/golden evidence；
- build-only processor 不进入 application runtime graph。

### 9.2 Phase 1 最小 slice

输入：一个 dense table fixture，至少覆盖 required primitive、optional primitive、Batch 和 Row Pipeline。

输出：

- generated Table/Batch/Rows/Cursor/Mutator、single-table materializer 最小闭环；
- runtime create/compatibility/binding；
- packed RowSpace、typed columns、presence bitmap；
- addBatch/replaceAll/clear、scan/filter/limit/count/forEach/update；
- dense direct access 和最小 readonly ColumnView acquire/read/release；
- expected create/mutation/released/view error path；
- packed/presence invariant、generated golden、allocation/shape smoke。

该 fixture 只用于证明结构，不删除后续 keyed/index/unique/order/child/materialization gate。

### 9.3 Evidence placement

| Evidence | Owner/位置 |
|---|---|
| compile/diagnostic/classfile/golden | `soma-testkit` helper + processor tests/reports |
| runtime invariant/differential | runtime-core tests + testkit helper |
| generated API/package smoke | processor golden + external consumer fixture |
| allocation/performance shape | testkit counters/inspection + benchmarks smoke |
| gate/release claim | 正式 `reports/`，不得由本文直接声明 |

## 10. 决策清单

### 10.1 P0：编码前或首个 slice 内必须解决

- generated-facing runtime protocol 的 visibility/package/API-manifest classification；
- plugin name、provider FQN、processor FQN 和 service activation；
- Phase 0 compilation session/round completion 与 stale-output cleanup 机制；
- Phase 1 `TableLayout + RuntimePlan + TableStore` create/publish owner；
- generated typed binding 的一次性 resolve 方式和 compatibility check；
- structured error envelope 与 stable diagnostic code 的首批 concrete Java types；
- external consumer fixture 如何证明 processor 是 build-only dependency。

### 10.2 P1：实现中固化

- role map 到实际 package 的最小映射；
- `ValidatedSchema` 是否值得独立建模；
- emitter 使用直接 writer、small template 还是局部 builder；不增加第三方依赖；
- RowSpace growth、column bulk reserve 和 replaceAll prepare/publish 算法；
- Phase 1 scan path 是否需要独立 type；
- Cursor reuse 和 no-per-row-allocation 的实现形态；
- test-scoped invariant/allocation hooks 的最小 surface。

### 10.3 P2：等待真实变化或证据

- 多 compiler adapter 后是否拆 processor core/adapter artifact；
- `KeySpace` 的 interface/abstract-base 形态；
- index/order/dynamic-sort 的统一 `AccessPath` protocol；
- child table locality、registry 和 materialization tracker 的 concrete structure；
- pooling、scratch、stats、growth/hash/index 默认策略；
- benchmark runner CLI、JSONL schema、scale/seed/warmup/repetition 默认值。

## 11. 复审清单

每次更新本文或完成 vertical slice 后检查：

- 是否把 working hypothesis 写成了正式 API/schema/runtime 事实；
- 是否出现两个模块共同拥有同一 state/policy；
- generated code 是否开始重新解释 annotation/metadata；
- runtime-core 是否开始依赖 annotations/processor；
- public/generated surface 是否泄漏 RowSlot、column mutation、bitmap、sidecar、handle 或 javac type；
- 是否为尚不存在的变体创建 interface/空模块；
- 是否出现无 owner 的 `Manager/Helper/Utils`；
- 是否把 Phase、P0/P1/P2 或 Maven artifact version 改写成 `v0.x`、MVP、Lite、Basic 等替代产品目标；
- 未实现 capability 是否仍对应正式 ledger 中的完整出口和最终 Gate；
- 后续达到 V1 是否只需 additive completion/internal refinement，而不需要 consumer、核心事实或 canonical hot-path migration；
- expected failure 是否可能发布 partial state；
- hot path 是否引入 reflection、boxing、per-row allocation 或 intermediate Collection；
- 当前 slice 是否同时具备 contract、error、invariant、golden 和 performance-shape evidence；
- 是否因开发顺序删除后续 V1 gate。

## 12. 演进与固化流程

```text
更新临时蓝图
  -> 针对 P0/P1 做只读设计审查
  -> 实现一个 vertical slice
  -> compile/golden/invariant/error/performance-shape 验证
  -> 用实现证据修正蓝图
  -> 稳定事实迁入唯一 Owner 文档
  -> 更新 API manifest/examples/gates
  -> 删除已失效假设或在蓝图中标记 superseded
```

本文可以跨多个 slice 演进，但不得长期保留与正式 Owner 或已实现结构冲突的描述。待核心结构稳定后，应把 durable facts 分别迁入 processor/runtime/testkit Owner 文档，并删除本文；如果用户明确决定长期保留，再按文档治理规则改为长期研究蓝图。

## 13. 非目标

本文不：

- 固化最终 package/FQN/class/interface 数量；
- 创建 `v0.x`、MVP、Lite、Basic 或其他替代完整 V1 的产品阶段；
- 新增 annotation、public API、schema kind 或 query DSL；
- 承诺具体算法参数、benchmark 数字、排期或 release readiness；
- 提前实现 Phase 2 至 Phase 6；
- 引入第三方依赖、native runtime、C ABI、Python、并发、事务、持久化或 FFI；
- 把 scenario 蓝图中的应用建模选择提升为 runtime 通用机制。
