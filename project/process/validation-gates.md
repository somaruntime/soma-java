# Validation Gate 治理

类型：Process

状态：正式

Owner：SOMA Java validation gate 过程

事实范围：G0–G6 的职责、状态、evidence、blocking 与 claim 规则

非事实范围：产品语义、当前 Gate 结果、具体测试实现和 release 授权

最后审查日期：2026-07-29

## 1. Gate 不是阶段折扣

Gate 是完整目标的验证 checkpoint，不是缩小产品范围的版本。一个单元测试、示例、benchmark smoke 或本机 package 成功都不能单独代表 V1 readiness。未覆盖能力保持其真实状态，不得通过改名为 optional、future 或 MVP 来消除。

## 2. G0–G6

| Gate | 证明的边界 |
|---|---|
| G0 | Java-only scope、Blueprint/Design/Owner、non-goal 和 claim boundary 已稳定 |
| G1 | annotation/schema、四类V1 storage kind、closed logical type catalog、String、ownership/selector/default、Metadata/hash/diagnostic 语义可编译验证 |
| G2 | Amazon Corretto full JDK 8 integration、normalization、deterministic codegen、SchemaMetadata/logical enum-date-time-instant/String/callback generated API、negative fixture与old-token absence |
| G3 | Group/ledger/lifecycle、flat/head-tail storage、locator/exact、formula-bound Bitmap、Candidate shapes、numeric closed kernel、primitive join runtime filter、Transformation/DataFlow、bounded scheduler、Result Delivery、plan/observation/failure 和 safe-point Effect |
| G4 | 普通 external Maven Java 8 consumer 能生成、编译、绑定 Metadata/Group/storage/DataFlow runtime，并执行simple/advanced/callback/diagnostics journey |
| G5 | property/reference differential、全部runtime-scale production qualification、领域中性 component benchmark、三个应用审计及各自 correctness/default/large/long-run evidence |
| G6 | selected release profile 的 license、SCM/ownership/contact、适用 package/provenance、security、support matrix、distribution/publishing applicability 和 sign-off |

V1 不增加 Python、C ABI、native package 或其他产品边界之外的 Gate。

当前JDK Gate只要求在记录版本的Amazon Corretto full JDK 8上重放；不要求Zulu或
其他distribution的并行验真。其他distribution的历史运行结果只属于当时
evidence，不扩展当前支持范围。JDK authority迁移会使依赖旧vendor的support、
performance与qualification evidence失去当前适用性，必须显式重新取证或将相应
Gate标为blocked，不能靠改名baseline维持passed。

性能 baseline 结果使用 `passed`、`failed`、`not-applicable` 子状态：
`not-applicable` 只表示当前环境未覆盖该 baseline，不改变 G5 功能 Gate，也不能被
表述为性能通过。具体顺序、阈值和更新纪律由
[Benchmark 治理](benchmark-governance.md)拥有。

Runtime-scale qualification额外允许`inconclusive`记录无法得出结论的合法artifact，
但required applicable lane的`inconclusive`阻塞G5，不等同passed或waived。
Required runtime-scale artifact 至少分别覆盖 Small、Medium、单表1M、两个同时驻留
1M root、String、high-expansion、Delivery与Soak。10M/100M可以作为
`informational research/stress` lane运行，但其缺失、失败或inconclusive不阻塞V1
G5；它们不得混入required通过率或改名为V1 guarantee。

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

以下情况至少阻塞对应 Gate：Design/Implementation/Tests 不一致且未裁决；required
evidence 缺失或不可重放；unsupported compiler 静默降级；compatibility mismatch
延迟到 hot path；failure path、ownership、resource 或 collision correctness 未覆盖；
benchmark 无 correctness guard；本机结果被外推为支持矩阵；release 缺 selected
profile 所需的真实 identity、license、contact、provenance 或 distribution facts。
Signing/publishing 只在 selected profile 实际分发相应 artifact 时适用；未选择的
public/Maven profile必须明确保持 `not-selected`，不能借 private-source G6
冒充通过。

当前状态由 [Report 入口](../reports/README.md) 陈述。Process 只规定怎样形成可信结论，不把历史 passed 自动外推到新 commit。

Design-first promotion只改变target Owner；此前G0–G5 passed记录不自动证明新
Metadata/Group/String/scale/delivery candidate。Production cutover后必须在精确新
commit上重放适用Gate并更新Report。
