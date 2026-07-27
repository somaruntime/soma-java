# P4 集成设计到正式 Owner 的转移记录

类型：Temporary

状态：P5 audit passed；P6 promoted at `d5f713d`

Owner：P4/P5/P6 integrated-design promotion trace

正式事实源：否

事实范围：候选 revision、独立审计、正式 Owner 转移和 claim boundary

非事实范围：长期设计、当前 production conformance、qualification 或 readiness

初始候选：`335a962`

审计修正：`f226d56`

独立审计收口：`aea5cc0`

正式 promotion：`d5f713d`

最后审查日期：2026-07-28

## 1. 转移结论

P4 候选的产品、系统和工程决策已经原子进入正式 Blueprint、Design、Conformance
与 Engineering。P6 同时把尚未实现的 production 工作登记为 `CF-009`–`CF-015`，
因此“设计已采纳”不会被误读为“代码已支持”或“规模资格已通过”。

| P4 关注点 | 正式 Owner |
|---|---|
| canonical product narrative / three axes | [产品蓝图](../../blueprints/soma-java-product-blueprint.md)、[设计宪法](../../design/soma-java-design-constitution.md) |
| closed Capability / module boundary | [系统架构](../../design/system-architecture.md) |
| four type kinds / String / generated Metadata | [Schema 与生成 API](../../design/schema-and-generated-api.md) |
| Metadata phases / Plan / Group / resource / observation | [Runtime Plan 与可观测性](../../design/runtime-plan-and-observability.md) |
| Group/Table/child ownership and lifecycle | [Ownership 与生命周期](../../design/ownership-and-lifecycle.md) |
| flat/head-tail storage / locator / Candidate shapes | [Table 存储与访问](../../design/table-storage-and-access.md)、[Access Model](../../design/access-model-and-candidate-scan.md) |
| relation specialization / bound / scheduler | [Transformation](../../design/transformation-model.md)、[DataFlow execution](../../design/dataflow-execution-model.md) |
| Eager/callback delivery | [Result Delivery 与物化边界](../../design/materialization-boundary.md) |
| performance/resource/failure/compatibility | [Design 入口](../../design/README.md) |
| production gaps | [Conformance](../../conformance/README.md) |
| qualification / tests / Gate | [Engineering](../../engineering/README.md) |

## 2. 审计边界

P5 三路审计分别覆盖 evidence 外推、production 可实施性与产品/Example/qualification
完整性。初始 blocker 在 `f226d56` 修正，三路对该 revision 的复核均为 PASS；
精确 finding 见 [独立审计记录](independent-design-and-scope-audit.md)。

P5 PASS 只授权 P6 promotion 和后续 semantic-slice implementation，不证明当前
production conformance、performance、G1–G5 或 G6。

## 3. 后续使用

P7–P10 只从正式 Owner 读取目标；本文仅用于定位候选 revision、promotion commit
和审计链。P8 不得从旧段落复制 temporary API、class、constant 或阈值。原 1,177
行候选正文保留在 `aea5cc0` 的 Git history；本文件随 P11 删除。
