# 当前文档清单

类型：Temporary

状态：进行中

Owner：SOMA Java 文档体系重构专题

事实范围：切换前现有文档类别、当前权威角色和候选去向

核对基线：`4b6fa43` 加当前未提交文档状态

最后审查日期：2026-07-19

## 1. 根级正式设计

当前 [`docs/README.md`](../../../docs/README.md) 登记的长期 Owner 包括：

- Design constitution、architecture、domain glossary；
- annotation/schema、generated API、materialization；
- runtime correctness/performance、public compatibility、安全；
- build/dependency、implementation strategy、validation gates、versioning/release。

这些文件当前仍是正式事实源。候选体系把内容重组到 `design/` 与 `engineering/`，但在切换前不得删除或降级。

## 2. Module 设计

| Module | 当前正式文档 | 候选主要去向 |
|---|---|---|
| annotations | annotation schema contract | Schema Design |
| processor | compiler integration、schema processing、code generation | Schema Design + compiler Implementation Map |
| runtime-core | TableStore、lifecycle、plan、errors/diagnostics、performance implementation | 多个 Design Owner + runtime Implementation Map |
| testkit | testkit contract | Engineering testing + evidence map |
| examples | FJSP/VRP/simulation/game schema/scenario docs | Blueprints + scenario map + user/developer Reports |
| benchmarks | evidence/runtime-state contracts | Performance Design + benchmark Engineering/Map/Report |

Module docs 中的规范性长期事实必须迁入对应 Design；纯代码投影只进入 Implementation Map；某次 evidence 进入 Report。

## 3. 当前 Blueprint/Temporary

`docs/temp/` 当前包含四份长期研究蓝图：FJSP、VRP、Simulation、Game。它们不是正式 Design source，但包含目标场景、风险和待验证方向。候选体系已提炼为正式分类下的 Blueprint；未固化的研究细节仍需逐项复核。

工作区还存在用户维护的 `docs/temp/packed-exact-index-post-cutover-tails/` 和 `docs/temp/README.md` 修改。本专题不修改它们。切换前必须由其 Owner 决定哪些观察进入 Conformance、Governance Report 或新 Temporary，不能静默丢失。

## 4. Guides 与 Reports

- 当前 `guides/` 拥有安装/consumer 使用输出；候选框架把用户与开发者文档归入 Reports；
- 当前 `reports/` 及 module reports 拥有审查、Gate、benchmark、implementation 和 release evidence；
- 历史 report 不应改写成 Design；切换时可以保留原路径、迁移到新 Reports，或建立只读历史索引，但必须避免断链和当前状态混淆。

## 5. 可执行治理

当前 `scripts/check-docs.sh`、`scripts/check.sh`、public API/compiler/runtime/consumer/benchmark/package scripts 是工程事实。候选 Engineering 已描述其职责，Implementation Map 已链接入口；未来切换若改变目录规则，必须同步脚本而不是只改文字。

## 6. 清单结论

现有信息没有单一“一对一搬家”方案。新体系需要按事实性质拆分：长期规范进入 Design，当前实现导航进入 Implementation Map，偏差进入 Conformance，过程进入 Engineering，正式输出进入 Reports，未决候选留在 Temporary。
