# 可执行契约地图

类型：Implementation Map

状态：正式

Owner：SOMA executable contract 实现导航

对应 Design：[Schema 与生成 API](../design/schema-and-generated-api.md)、[Access Model 与 Candidate Scan](../design/access-model-and-candidate-scan.md)、[Transformation Model](../design/transformation-model.md)、[DataFlow 执行模型](../design/dataflow-execution-model.md)、[Runtime Plan 与可观测性](../design/runtime-plan-and-observability.md)、[兼容性、安全与版本](../design/compatibility-security-and-versioning.md)

事实范围：当前精确 public/generated/schema/protocol surface 的代码、golden、fixture 和 Gate 位置

最近实现核对基线：commit `3c8d425`

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
| generated-runtime protocol | [`com.hgtech.soma.runtime.generated`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated) 与 generator binding；compatibility v6、transformation v2、kernel v1、plan v4 | runtime/generated Gate scripts + old-protocol fail-closed oracle + external consumers |
| runtime plan/default/effective metadata/stats/error codes | runtime public/internal sources；application从generated `SchemaMetadata.newPlan()`进入，raw construction只由generated bridge持有 | runtime-core check、public API absence rule、diagnostics Gate、external access/child/breadth consumers |
| benchmark artifact schema | benchmark model/validator | smoke runner、strict validator、negative artifact cases；exact-index incremental lane 与无索引 dense-workspace lane 分开记录 |

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
和DataFlow component baseline共同约束该surface；Runtime/Group/Observation
Metadata仍是后续Conformance gap。

## 4. Baseline 约定

Implementation Map 的“实现核对基线”指最后一次影响产品代码、配置、测试或 executable product contract 的 commit，不要求等于最新 docs/governance-only commit。若其后只有文档与治理脚本变化，应在审计材料中证明相关产品实现树未变化；一旦 implementation surface 变化，所有受影响地图必须更新到新的 immutable implementation baseline。
