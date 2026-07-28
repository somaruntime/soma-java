# 可执行契约地图

类型：Implementation Map

状态：正式

Owner：SOMA executable contract 实现导航

对应 Design：[Schema 与生成 API](../design/schema-and-generated-api.md)、[Access Model 与 Candidate Scan](../design/access-model-and-candidate-scan.md)、[Transformation Model](../design/transformation-model.md)、[DataFlow 执行模型](../design/dataflow-execution-model.md)、[Runtime Plan 与可观测性](../design/runtime-plan-and-observability.md)、[兼容性、安全与版本](../design/compatibility-security-and-versioning.md)

事实范围：当前精确 public/generated/schema/protocol surface 的代码、golden、fixture 和 Gate 位置

最近实现核对基线：2026-07-28 runtime-scale working-tree candidate（base
`6cde5d5`；production/evidence source
`content-sha256:dfe8fa98b2a411708359a378e05f22e2ad89a7b900c70d1f71e8dd1a6b7f8e69`）

最后审查日期：2026-07-28

## 1. 为什么单独登记

Design 规定长期语义、边界和演进规则；代码与可执行产物拥有当前精确实现事实。可以由编译器、`javap`、validator 或 external consumer直接得到的完整 signature/field/code 清单，不在 Design 中手工复制第二份。

这不表示当前代码天然正确。若 executable surface 与 Design 冲突，应记录 Conformance；若 surface 被批准改变，应同时更新代码、golden、consumer evidence 和必要的 Design。

## 2. 当前 surface

| Surface | 当前事实入口 | 直接 evidence |
|---|---|---|
| annotation public API | [`com.hgtech.soma.annotation`](../../soma-annotations/src/main/java/com/hgtech/soma/annotation) | [`check-public-api.sh`](../../scripts/check-public-api.sh) 与 phase public `javap` golden |
| `@SomaValue` effective class shape | javac plugin + generated class | compiler `value-success`/modifier fixtures与 external Maven value consumer |
| normalized schema/hash | processor normalized model/output | compiler fixture `expected/*.schema.json`、`.sha256`、两个 isolated application 的 clean/repeat schema/hash manifest，以及 Unicode/negative diagnostics |
| handwritten runtime public API | [`com.hgtech.soma.runtime`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime) | [`public-api/phase1/public-api.javap.txt`](../../soma-testkit/src/test/fixtures/public-api/phase1/public-api.javap.txt) |
| generated public API | generated source/class output；canonical family 为 Table/Batch/Scan/Cursor/UpdateCursor/KeyTraversal/ColumnTraversal/View/child/DataFlow companion/keyed Delta | external dense/keyed/access/child/breadth `javap` golden + compile/run |
| DataFlow public API | [`com.hgtech.soma.dataflow`](../../soma-dataflow/src/main/java/com/hgtech/soma/dataflow) 与 generated companion | public `javap`、Slice A–F、external consumer、reference differential |
| generated-runtime protocol | [`com.hgtech.soma.runtime.generated`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated) 与 generator binding；compatibility v11、transformation v3、kernel v4、planner v4、plan v6及全部physical formula identity | runtime/generated Gate scripts + old-protocol fail-closed oracle + external consumers |
| runtime plan/Group/Metadata/stats/error codes | runtime public/internal sources；application从generated `SchemaMetadata.newPlan()`进入，并通过implicit `create`或显式`attach(SomaGroup, slot)`组合root；generated Table和Group公开detached runtime metadata，raw construction只由generated bridge持有 | runtime-core Group/plan/metadata checks、public API absence rule、diagnostics Gate、external dense/access/child/breadth consumers |
| Result Delivery | `ResultDeliveryMode`、typed `*Visitor`、callback Definition/Template/Invocation与generated delivery binding | public/golden、Slice F、reference differential、delivery qualification |
| benchmark artifact schema | benchmark model/validator及`runtime-scale-qualification-schema-v1.json` | smoke runner、strict validator、negative artifact cases、十条required production lanes |

## 3. Surface 变更规则

- Design 不维护每个当前 method/field 的镜像清单；需要精确 surface 时运行或读取上表 executable evidence；
- golden 是治理过的 compatibility snapshot，不能为迁就未批准实现而更新；
- source、golden、external consumer 三者不一致时，不能只选择其中一个宣布通过；
- internal class/path 可以重构，但 public/generated/protocol/schema identity 的变化按 Compatibility Design 处理；
- error code、plan field或artifact field一旦成为稳定兼容面，不得在同名下复用为不同语义。

`7925a10` 完成一次有意的 pre-1.0 clean cutover：generated/runtime 升级为 v6、
transformation 升级为 v2，generic Object value protocol 被 concrete String
protocol替代，并新增 schema-scoped Metadata projection。Public/generated
golden、old-protocol fail-closed、compiler diagnostics、clean/repeat external
consumer和reference differential共同拥有该变更证据。

`3c8d425` 继续完成有意的 runtime plan v4 cutover：application raw builder与
free-form physical strategy退出authoring surface，generated SchemaMetadata播种
one-shot metadata-scoped builder；plan冻结 immutable Effective Metadata、
String profile状态与hard maximum rows。Public/generated golden、compiler
classpath isolation、runtime atomic failure、external access/child/breadth journey
和DataFlow component baseline共同约束该surface；该时点尚未实现的Group部分已由
下一slice关闭，Table/Segment/access Runtime Metadata与Observation仍是后续
Conformance gap。

`515bf91` 完成 generated/runtime v7 clean cutover：新增 `SomaGroupPlan`、
`SomaGroup`、Group/member Metadata、atomic explicit attach与implicit Group，
以 `GroupLedger` 取代 `StorageBudget`，`TableLedger`只作attribution；root与Group
fault/lifecycle保持分层。Public/generated golden、runtime/child/breadth external
journey、cross-Group/cross-schema/self DataFlow与partial-acquire reverse-release
共同约束该surface。Plan protocol仍为v4，因为本slice没有改变plan canonical
schema或hash语义。

`6cde5d5` 完成 generated/runtime v8、plan v5 与 storage-layout formula v1
clean cutover：generated Metadata冻结structural row width，application只声明
planning/hard maximum/workload，Effective Metadata投影最终layout；runtime以
`FLAT`或flat-head/fixed-tail columns、bitmap和child handle执行atomic growth。
Public `javap`、runtime stage-failure oracle、dense/keyed/child跨32K boundary
consumer与compiler clean/repeat共同约束该surface；本slice不改变Schema hash、
row identity、String后端、locator或DataFlow logical semantics。

2026-07-28 working-tree candidate完成generated/runtime v11、transformation v3、
kernel/planner v4 clean cutover：closed Candidate/relation策略、bounded morsel
scheduler、Invocation phase ledger、Eager + callback-scoped delivery、完整
Table/Segment/access Runtime Metadata与component stats进入唯一协议。旧consumer-in-
Definition、generic Object、平行parallel executor和universal borrow surface均已
删除；public `javap`、runtime/generated/external consumer、reference
differential、三个Example和production-scale qualification共同约束该surface。
String仍只有reference-backed后端；实际GC及三层memory accounting已在限定
profile的qualification中闭合，不外推任意String或public claim。

## 4. Baseline 约定

Implementation Map 的“实现核对基线”指最后一次影响产品代码、配置、测试或 executable product contract 的 commit，不要求等于最新 docs/governance-only commit。若其后只有文档与治理脚本变化，应在审计材料中证明相关产品实现树未变化；一旦 implementation surface 变化，所有受影响地图必须更新到新的 immutable implementation baseline。
