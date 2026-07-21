# 可执行契约地图

类型：Implementation Map

状态：正式

Owner：SOMA executable contract 实现导航

对应 Design：[Schema 与生成 API](../design/schema-and-generated-api.md)、[Runtime Plan 与可观测性](../design/runtime-plan-and-observability.md)、[兼容性、安全与版本](../design/compatibility-security-and-versioning.md)

事实范围：当前精确 public/generated/schema/protocol surface 的代码、golden、fixture 和 Gate 位置

最近实现核对基线：`a137b10`

最后审查日期：2026-07-21

## 1. 为什么单独登记

Design 规定长期语义、边界和演进规则；代码与可执行产物拥有当前精确实现事实。可以由编译器、`javap`、validator 或 external consumer直接得到的完整 signature/field/code 清单，不在 Design 中手工复制第二份。

这不表示当前代码天然正确。若 executable surface 与 Design 冲突，应记录 Conformance；若 surface 被批准改变，应同时更新代码、golden、consumer evidence 和必要的 Design。

## 2. 当前 surface

| Surface | 当前事实入口 | 直接 evidence |
|---|---|---|
| annotation public API | [`com.hgtech.soma.annotation`](../../soma-annotations/src/main/java/com/hgtech/soma/annotation) | [`check-public-api.sh`](../../scripts/check-public-api.sh) 与 phase public `javap` golden |
| `@SomaValue` effective class shape | javac plugin + generated class | compiler `value-success`/modifier fixtures与 external Maven value consumer |
| normalized schema/hash | processor normalized model/output | compiler fixture `expected/*.schema.json`、`.sha256`，四场景 phase-6 schema/hash，以及 Unicode/negative diagnostics |
| handwritten runtime public API | [`com.hgtech.soma.runtime`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime) | [`public-api/phase1/public-api.javap.txt`](../../soma-testkit/src/test/fixtures/public-api/phase1/public-api.javap.txt) |
| generated public API | generated source/class output | external dense/keyed/access/child/breadth `javap` golden + compile/run |
| generated-runtime protocol | [`com.hgtech.soma.runtime.generated`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime/generated) 与 generator binding | runtime/generated Gate scripts + external consumers |
| runtime plan/default/stats/error codes | runtime public/internal sources | runtime-core check、diagnostics Gate、external access/child/breadth consumers |
| benchmark artifact schema | benchmark model/validator | smoke runner、strict validator、negative artifact cases；exact-index incremental lane 与无索引 dense-workspace lane 分开记录 |

## 3. Surface 变更规则

- Design 不维护每个当前 method/field 的镜像清单；需要精确 surface 时运行或读取上表 executable evidence；
- golden 是治理过的 compatibility snapshot，不能为迁就未批准实现而更新；
- source、golden、external consumer 三者不一致时，不能只选择其中一个宣布通过；
- internal class/path 可以重构，但 public/generated/protocol/schema identity 的变化按 Compatibility Design 处理；
- error code、plan field或artifact field一旦成为稳定兼容面，不得在同名下复用为不同语义。

## 4. Baseline 约定

Implementation Map 的“实现核对基线”指最后一次影响产品代码、配置、测试或 executable product contract 的 commit，不要求等于最新 docs/governance-only commit。若其后只有文档与治理脚本变化，应在审计材料中证明相关产品实现树未变化；一旦 implementation surface 变化，所有受影响地图必须更新到新的 immutable implementation baseline。
