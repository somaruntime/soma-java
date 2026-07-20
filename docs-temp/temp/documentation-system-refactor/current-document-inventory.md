# 当前文档清单

类型：Temporary

状态：清单完成

Owner：SOMA Java 文档体系重构专题

事实范围：切换前现有文档类别、当前权威角色和候选去向

现行文档输入基线：`fe163b8`

实现核对基线：`b991f4c`

最后审查日期：2026-07-20

## 1. 根级正式设计

当前 [`docs/README.md`](../../../docs/README.md) 及 module indexes合计登记 32 份正式 Owner，包括：

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

`docs/temp/` 当前包含四份长期研究蓝图：FJSP、VRP、Simulation、Game。它们不是正式 Design source，但包含目标场景、风险和待验证方向。候选体系已把四份内容逐项吸收为正式分类下的 rich Blueprint；旧 copies 在切换时删除。

`docs/temp/packed-exact-index-post-cutover-tails/` 已完成正式Owner迁移、代码与evidence验证，并由2026-07-20 dated closeout report承接长期证据后删除。候选 Conformance、Implementation Map和性能Report已同步其当前投影，没有把Temporary过程文档保留为第二事实源。

## 4. Guides 与 Reports

- 当前 `guides/` 拥有安装/consumer 使用输出；目标体系把它登记为 Report/user+developer 的正式物理入口；
- 当前 `reports/` 及 module reports 拥有审查、Gate、benchmark、implementation 和 release evidence；
- 历史 report 不改写成 Design并保留原路径；现行 `reports/README.md` 已区分 current Gate、治理 checkpoint 与 archive，切换只更新必要链接和分类说明。

## 5. 可执行治理

当前 `scripts/check-docs.sh`、`scripts/check.sh`、public API/compiler/runtime/consumer/benchmark/package scripts 是工程事实。候选 Engineering 已描述其职责，Implementation Map 已链接入口；未来切换若改变目录规则，必须同步脚本而不是只改文字。

## 6. 清单结论

现有信息不能一对一搬家。逐份归宿已经在[现行事实迁移审计](fact-migration-audit.md)裁决：长期规范进入 Design，当前精确 surface进入 executable contract，代码导航进入 Implementation Map，偏差进入 Conformance，过程进入 Engineering，正式输出进入 Reports，旧 Owner退出 current导航。
