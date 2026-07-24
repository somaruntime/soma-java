# Validation Gate 治理

类型：Engineering

状态：正式

Owner：SOMA Java validation gate 过程

事实范围：G0–G6 的职责、状态、evidence、blocking 与 claim 规则

非事实范围：产品语义、当前 Gate 结果、具体测试实现和 release 授权

最后审查日期：2026-07-24

## 1. Gate 不是阶段折扣

Gate 是完整目标的验证 checkpoint，不是缩小产品范围的版本。一个单元测试、示例、benchmark smoke 或本机 package 成功都不能单独代表 V1 readiness。未覆盖能力保持其真实状态，不得通过改名为 optional、future 或 MVP 来消除。

## 2. G0–G6

| Gate | 证明的边界 |
|---|---|
| G0 | Java-only scope、Blueprint/Design/Owner、non-goal 和 claim boundary 已稳定 |
| G1 | annotation/schema/value/type/ownership/selector/default/hash/diagnostic 语义可编译验证 |
| G2 | Azul Zulu full JDK 8 integration、normalization、deterministic codegen、generated API 与 negative fixture |
| G3 | packed storage、locator/exact access、完整 Access Model、Candidate Scan/Traversal lifecycle、child、plan、error/stats 和性能机械形状 |
| G4 | 普通 external Maven Java 8 consumer 能生成、编译、绑定 runtime 并执行 package surface |
| G5 | Access Pattern/API mapping、普通 Java 8 reference consumer、领域 correctness/failure/lifecycle、领域中性 component benchmark、应用自有 integrated evidence 与环境感知 baseline Gate 可执行 |
| G6 | license、SCM/ownership/contact、package/provenance、security、support matrix、publishing 和 sign-off |

V1 不增加 Python、C ABI、native package 或其他产品边界之外的 Gate。

当前 JDK Gate 只要求在记录版本的 Azul Zulu full JDK 8 上重放；不要求 Corretto 或其他 distribution 的并行验真。其他 distribution 的历史运行结果只属于当时 evidence，不扩展当前支持范围。

性能 baseline 结果使用 `passed`、`failed`、`not-applicable` 子状态：
`not-applicable` 只表示当前环境未覆盖该 baseline，不改变 G5 功能 Gate，也不能被
表述为性能通过。具体顺序、阈值和更新纪律由
[Benchmark 治理](benchmark-governance.md)拥有。

## 3. 状态与证据

Gate 状态只表达 `not-started`、`blocked`、`waived`、`passed` 或 `informational`。`waived` 必须有唯一 Owner、影响和明确 sign-off；执行 Agent不能自行豁免产品能力。

每次 Gate 结论至少记录 gate id、commit/artifact、命令或审查方法、实际 JDK/Maven/OS/architecture、输入/输出 artifact、checksum、passed/failed/waived、known limitations 和允许的 claim。没有可追踪 Report 的 Gate 不得视为 passed。

## 4. Scope non-regression

跨模块、capability 或专题收口必须说明：

- 哪些长期能力和 Design Owner 被影响；
- evidence 如何直接覆盖这些能力；
- 未涉及能力是否仍保留原目标和 Gate；
- 当前结果是 additive completion/contract-preserving refinement，还是需要未来 public/事实/hot-path migration；
- 是否引入 temporary public API、parallel fact source、test-only bypass 或临时 canonical path。

需要未来迁移才能符合当前 Design 的 slice 不能 closeout。能力状态账本的历史实现阶段可以进入 Governance Report；当前未闭合偏差进入 Conformance，不再维护一份与代码平行的路线图。

## 5. Blocking 与 claim

以下情况至少阻塞对应 Gate：Design/Implementation/Tests 不一致且未裁决；required evidence 缺失或不可重放；unsupported compiler 静默降级；compatibility mismatch 延迟到 hot path；failure path、ownership、resource 或 collision correctness 未覆盖；benchmark 无 correctness guard；本机结果被外推为支持矩阵；release 缺真实 identity、license、contact、signing或publishing facts。

当前状态由 [Report 入口](../../reports/README.md) 陈述。Engineering 只规定怎样形成可信结论，不把历史 passed 自动外推到新 commit。
