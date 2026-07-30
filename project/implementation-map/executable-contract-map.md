# 可执行契约地图

类型：Implementation Map

状态：正式

Owner：SOMA executable contract 实现导航

对应 Design：[Schema 与生成 API](../design/schema-and-generated-api.md)、[Access Model 与 Candidate Scan](../design/access-model-and-candidate-scan.md)、[Transformation Model](../design/transformation-model.md)、[DataFlow 执行模型](../design/dataflow-execution-model.md)、[Runtime Plan 与可观测性](../design/runtime-plan-and-observability.md)、[兼容性、安全与版本](../design/compatibility-security-and-versioning.md)

事实范围：当前精确 public/generated/schema/protocol surface 的代码、golden、fixture 和 Gate 位置

非事实范围：重新定义 public/generated/schema/protocol 语义或兼容性政策

最近实现核对基线：包含本文件的 V1 `1.0.0` private-source sign-off commit；
精确commit由Git与同SHA qualification artifact记录

最后审查日期：2026-07-29

## 1. 为什么单独登记

Design 规定长期语义、边界和演进规则；代码与可执行产物拥有当前精确实现事实。可以由编译器、`javap`、validator 或 external consumer直接得到的完整 signature/field/code 清单，不在 Design 中手工复制第二份。

这不表示当前代码天然正确。若 executable surface 与 Design 冲突，应记录 Conformance；若 surface 被批准改变，应同时更新代码、golden、consumer evidence 和必要的 Design。

## 2. 当前 surface

| Surface | 当前事实入口 | 直接 evidence |
|---|---|---|
| annotation public API | [`io.github.somaruntime.soma.annotation`](../../soma-annotations/src/main/java/io/github/somaruntime/soma/annotation) | [`check-public-api.sh`](../../scripts/check-public-api.sh) 与 current public `javap` golden |
| `@SomaValue` effective class shape | javac plugin + generated class | compiler `value-success`/modifier fixtures与 external Maven value consumer |
| normalized schema/hash | processor normalized model/output | compiler fixture `expected/*.schema.json`、`.sha256`、两个 isolated application 的 clean/repeat schema/hash manifest，以及 Unicode/negative diagnostics |
| handwritten runtime public API | [`io.github.somaruntime.soma.runtime`](../../soma-runtime-core/src/main/java/io/github/somaruntime/soma/runtime) | [`public-api/current/public-api.javap.txt`](../../tests/fixtures/public-api/current/public-api.javap.txt) |
| generated public API | generated source/class output；canonical family 为 Table/Batch/Scan/Cursor/UpdateCursor/KeyTraversal/ColumnTraversal/View/child/DataFlow companion/keyed Delta | external dense/keyed/access/child/breadth `javap` golden + compile/run |
| DataFlow public API | [`io.github.somaruntime.soma.dataflow`](../../soma-dataflow/src/main/java/io/github/somaruntime/soma/dataflow) 与 generated companion | public `javap`、capability contracts、external consumer、reference differential |
| generated-runtime protocol | [`io.github.somaruntime.soma.runtime.generated`](../../soma-runtime-core/src/main/java/io/github/somaruntime/soma/runtime/generated) 与 generator binding；compatibility v12、transformation v5、kernel v6、planner v4、plan v6及全部physical formula identity | runtime/generated Gate scripts + old-protocol fail-closed oracle + external consumers |
| runtime plan/Group/Metadata/stats/error codes | runtime public/internal sources；application从generated `SchemaMetadata.newPlan()`进入，并通过implicit `create`或显式`attach(SomaGroup, slot)`组合root；generated Table和Group公开detached runtime metadata，raw construction只由generated bridge持有 | runtime-core Group/plan/metadata checks、public API absence rule、diagnostics Gate、external dense/access/child/breadth consumers |
| Result Delivery | `ResultDeliveryMode`、typed `*Visitor`、callback Definition/Template/Invocation与generated delivery binding | public/golden、Point/Delivery 与 Shape/Graph contracts、reference differential、delivery qualification |
| benchmark artifact schema | benchmark model/validator及`runtime-scale-qualification-schema-v2.json` | smoke runner、strict validator、negative artifact cases、八条required production lanes与四条optional research/stress lanes |

当前 executable golden 登记 229 个 `PUBLIC`、2 个 `INTERNAL` entry：
annotations 14、build provider 2、handwritten runtime 77、handwritten dataflow 87、
generated runtime 38、generated DataFlow protocol 10、generated construction
protocol 1。这里的 `PUBLIC` 表示 JVM 可执行可见性；generated bridge 因 consumer
class 位于 application package 而必须跨 package 调用，不等同于 application
authoring API，也不能用普通 source 的直接引用数判定为 dead code。

## 3. Surface 变更规则

- Design 不维护每个当前 method/field 的镜像清单；需要精确 surface 时运行或读取上表 executable evidence；
- golden 是治理过的 compatibility snapshot，不能为迁就未批准实现而更新；
- source、golden、external consumer 三者不一致时，不能只选择其中一个宣布通过；
- internal class/path 可以重构，但 public/generated/protocol/schema identity 的变化按 Compatibility Design 处理；
- error code、plan field或artifact field一旦成为稳定兼容面，不得在同名下复用为不同语义。

当前 canonical identity 为 generated/runtime v12、transformation v5、kernel v6、
planner v4 与 plan v6；Candidate/relation formula为v2。当前 surface包含logical
enum/date/time/instant facade、closed numeric kernel、formula-bound Bitmap、
primitive join runtime filter、closed Candidate/relation strategy、bounded morsel
scheduling、Invocation phase ledger、Eager + callback-scoped delivery 和完整
Table/Segment/access Runtime Metadata；String仍只有reference-backed后端。其
正确性由 public/golden、
runtime/generated contracts、external consumer、reference differential、三个
Example 与限定 profile qualification 共同约束，不外推任意 String 或 public
performance claim。

## 4. Baseline 约定

Implementation Map 的“实现核对基线”指最后一次影响产品代码、配置、测试或 executable product contract 的 commit，不要求等于最新 docs/governance-only commit。若其后只有文档与治理脚本变化，应在审计材料中证明相关产品实现树未变化；一旦 implementation surface 变化，所有受影响地图必须更新到新的 immutable implementation baseline。
