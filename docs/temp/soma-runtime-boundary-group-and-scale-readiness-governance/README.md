# SOMA Runtime Boundary、Group 与 Scale Readiness 治理执行追踪

类型：Temporary

用途：Goal execution trace

状态：active（P0–P7 已完成；P8 in progress）

Owner：SOMA runtime scale and productization Goal execution

正式事实源：否

事实范围：阶段、提交、evidence、差距、replacement closure 与最终退役条件

非事实范围：产品目标、长期设计、当前实现能力、性能 claim 或 release readiness

当前 production 基线：`3c8d425`

最后审查日期：2026-07-28

## 1. 正式事实入口

P6 已把通过独立审计的目标原子固化到正式 Owner。此目录不再复述或拥有设计：

| 关注点 | 正式 Owner |
|---|---|
| 产品定位、目标体验与非目标 | [产品蓝图](../../blueprints/soma-java-product-blueprint.md) |
| 原则、系统叙事与模块/Capability 边界 | [设计宪法](../../design/soma-java-design-constitution.md)、[系统架构](../../design/system-architecture.md) |
| Schema、四类类型、String 与 generated contract | [Schema 与生成 API](../../design/schema-and-generated-api.md) |
| Metadata、Plan、Group、resource 与 observation | [Runtime Plan 与可观测性](../../design/runtime-plan-and-observability.md) |
| ownership、storage、access、transformation 与 execution | [Design 入口](../../design/README.md) |
| Eager 与 callback-scoped delivery | [Result Delivery 与物化边界](../../design/materialization-boundary.md) |
| 当前生产偏差 | [Conformance](../../conformance/README.md) |
| qualification、Gate、test/evidence 与文档过程 | [Engineering](../../engineering/README.md) |
| 当前 Gate 与 claim 边界 | [Report](../../../reports/README.md) |

Implementation Map 在 P8 production cutover 后按实际代码更新，不提前把目标写成实现。

## 2. 当前阶段

| Phase | 状态 | 可核验出口 |
|---|---|---|
| P0 baseline / governance checkpoint | completed | `380722c` |
| P1 traceability / TV9 preregistration | completed | SOMA `e90d2dd`；Lab `8927b28` |
| P2 TV9 corrected experiment | completed | Lab `75fe7a7`、`bbc13e8` |
| P3 evidence synthesis / Owner decision | completed | SOMA `994e059`；Lab `cf322ab`、`33dda17` |
| P4 integrated design | completed | `335a962` |
| P5 independent design/scope audit | completed | `f226d56`、`aea5cc0`；三路复核 PASS |
| P6 formal promotion | completed | `d5f713d`；Design target 与 `CF-009`–`CF-015` 同批固化 |
| P7 production disposition | completed | [逐项 RETAIN/MIGRATE/REMOVE 与 replacement closure](production-disposition.md) |
| P8 production implementation | in progress | 按语义 slice clean cutover |
| P9 three-example audit | pending | 先审计；只有真实偏差才修改 |
| P10 qualification / fresh Gate | pending | production-shape evidence 与完整验证 |
| P11 closeout / deletion | pending | 自包含 Report、Conformance/Map 收口、删除 Lab 与本目录 |

同时只能有一个 phase `in progress`。任何 production、test、benchmark、Guide、
Report 或 Example 变更都必须映射到 [Goal Traceability](goal-traceability.md) 的
G01–G17 或正式 Conformance gap。

## 3. 执行证据

- [Goal Traceability](goal-traceability.md)：Goal ID、phase 与剩余闭环；
- [TV0–TV9 验证协议](scale-architecture-technical-validation.md)：预注册问题、
  预算、协议和历史技术边界；
- [TV0–TV9 Evidence Synthesis](technical-validation-evidence-synthesis.md)：
  accepted/rejected/inconclusive 与 Lab revision；
- [P4 promotion map](integrated-final-design.md)：候选设计到正式 Owner 的转移；
- [P5 独立审计](independent-design-and-scope-audit.md)：blocking findings 与复核；
- [P7 production disposition](production-disposition.md)：逐项 surface 与 successor；

其余四份专题文件只保留原候选议题、正式 Owner 和 Git provenance 指针，不拥有
长期事实。

## 4. 防缩水与防打转

- 不因实现困难、耗时、token、现有代码或旧 Gate 缩减正式目标；
- 每个问题最多三个候选、一次主验证和一次确认；相同失败不第三次原样重试；
- slice 内使用窄验证，phase boundary 才运行重型 Gate；
- 不做命名漫游、装饰性重构、无界调优或未映射工作；
- Lab evidence 只支持设计裁决，不能替代 production-shape qualification；
- G6 缺真实外部事实时保持 blocked；不 push、PR、tag、publish 或声明 ready。

## 5. 退役条件

只有 P11 的完整 Definition of Done 成立后，才删除独立 Lab 和本目录：

1. 正式 Design、production、generated API、test、Guide、Report、Conformance 一致；
2. `CF-009`–`CF-015` 有实现和 production evidence；
3. String、Small/Medium、1M/10M、single/double-100M、Delivery、high-expansion
   preflight 与 Soak qualification 闭合；
4. 三个 Example 完成独立审计与必要治理；
5. replacement closure、Implementation Map、scope non-regression 与 fresh Gate 闭合；
6. closeout Report 已自包含持久事实，删除后无 current reference 指向 Lab/Temporary。
