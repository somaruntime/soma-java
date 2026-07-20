# 现行事实迁移审计

类型：Temporary

状态：复核完成，待切换执行

Owner：SOMA Java 文档体系重构专题

事实范围：现行正式 Owner、目标 Owner、executable current fact 与切换处置的逐项裁决

非事实范围：直接修改现行 Owner、执行正式切换或重新定义产品能力

现行文档输入基线：`fe163b8`

实现核对基线：`b991f4c`

最后审查日期：2026-07-20

## 1. 审计口径

本审计追求事实无损，不追求旧文本逐段搬运：

- 长期规范性语义进入唯一 Design；
- 精确当前 signature、field、default、error code、protocol 和 artifact shape若可直接执行验证，则由代码/golden/validator拥有，并在 Implementation Map 登记；
- build/test/benchmark/release 方法进入 Engineering；
- 当前能力、测量和历史结论进入 Report；
- 当前 example 文档改为 developer/current-executable Report，不继续拥有产品设计；
- 旧 Design 在切换时标记 `superseded`、链接新 Owner并退出 current 导航；历史链接可以继续读取它，但不能把它当成当前事实。

因此，“不复制精确方法清单”不是事实遗失；“既没有新的语义 Owner，也没有 executable owner”才是迁移失败。

## 2. 根级 Owner

| 现行 Owner | 长期事实归宿 | 当前精确事实 / evidence | 切换处置 |
|---|---|---|---|
| `docs/architecture-design.md` | 设计宪法、系统架构 | project/module map、`pom.xml` | supersede |
| `docs/build-and-dependency-contract.md` | 系统架构；Engineering build/validation | `pom.xml`、Wrapper、build scripts | supersede |
| `docs/documentation-governance.md` | Engineering 文档治理 | candidate checker/cutover Gate | supersede |
| `docs/domain-glossary.md` | Design 领域语言 | 无独立 executable surface | supersede |
| `docs/generated-table-api-contract.md` | Schema/generated、table/access、ownership、materialization、runtime plan | executable contract map、generated `javap`、external consumers | supersede |
| `docs/implementation-strategy.md` | 设计宪法的防缩水规则；Engineering validation gate；Conformance | current code/evidence与历史 phase reports | supersede；不保留 roadmap/平行 capability ledger |
| `docs/materialization-contract.md` | Materialization、ownership、runtime plan | generated golden、child/breadth consumers | supersede |
| `docs/public-api-compatibility-contract.md` | Compatibility/security/versioning | public/generated `javap`、schema/protocol identity checks | supersede |
| `docs/runtime-correctness-model.md` | Correctness/failure、ownership、table/access、materialization | invariant tests、external consumers | supersede |
| `docs/runtime-performance-model.md` | Performance、table/access、runtime plan | benchmark governance/map与结构化 artifacts | supersede |
| `docs/security-model.md` | Compatibility/security/versioning；Engineering build/testing/release | security/package scripts与reports | supersede |
| `docs/soma-table-design-constitution.md` | 新设计宪法；机制细节分配给相应 Design | Conformance tests | supersede |
| `docs/validation-gates.md` | Engineering validation gates、testing、benchmark、release | scripts、Gate reports | supersede |
| `docs/versioning-and-release-contract.md` | Compatibility/security/versioning；Engineering release | POM/package/release reports | supersede |

## 3. Annotation、processor 与 runtime-core Owner

| 现行 Owner | 长期事实归宿 | 当前精确事实 / evidence | 切换处置 |
|---|---|---|---|
| `soma-annotations/docs/annotation-schema-contract.md` | Schema 与 generated API、table/access、ownership | annotation source、public `javap`、compiler fixtures | supersede |
| `soma-processor/docs/compiler-integration-contract.md` | System architecture、Schema/generated、Engineering build | javac plugin/processor map、compiler Gate | supersede |
| `soma-processor/docs/schema-processing-contract.md` | Schema/generated、compatibility | normalized schema/hash artifacts、diagnostic fixtures | supersede |
| `soma-processor/docs/code-generation-contract.md` | Schema/generated、materialization、ownership | generator map、generated golden、external consumer | supersede |
| `soma-runtime-core/docs/table-store-contract.md` | Table/access、ownership、correctness、performance | runtime-core map、kernel/generated tests | supersede |
| `soma-runtime-core/docs/runtime-lifecycle-contract.md` | Ownership/lifecycle、correctness/failure、materialization | runtime/generated lifecycle tests | supersede |
| `soma-runtime-core/docs/runtime-plan-contract.md` | Runtime Plan 与可观测性、compatibility、performance | runtime public surface、plan/hash tests、external consumers | supersede |
| `soma-runtime-core/docs/runtime-errors-and-diagnostics-contract.md` | Correctness/failure、Runtime Plan 与可观测性 | runtime sources、public golden、diagnostics tests | supersede |
| `soma-runtime-core/docs/runtime-performance-implementation-contract.md` | Table/access、performance、Runtime Plan 与可观测性 | runtime map、allocation/GC/component evidence | supersede |

这些旧 module docs包含大量当前 class、constant、method和测试清单。切换后需要精确实现时从 Implementation Map 进入代码/golden；不得把 superseded 文本当作第二份 current implementation map。

## 4. Testkit、benchmark 与 example Owner

| 现行 Owner | 长期事实归宿 | 当前精确事实 / evidence | 切换处置 |
|---|---|---|---|
| `soma-testkit/docs/testkit-contract.md` | Engineering testing/evidence | test/evidence map、testkit source与tests | supersede |
| `soma-benchmarks/docs/benchmark-evidence-contract.md` | Performance Design；Engineering benchmark/validation | benchmark map、validator/artifact | supersede |
| `soma-benchmarks/docs/runtime-state-benchmark-contract.md` | 各场景 Blueprint；Engineering benchmark | scenario/benchmark map、runner/artifact | supersede |
| `soma-examples/docs/fjsp-e2e-scenario.md` | FJSP Blueprint；Design；scenario map | executable example/G5 evidence | reclassify 为 developer/current-executable Report |
| `soma-examples/docs/fjsp-runtime-state-example.md` | FJSP Blueprint；Design；scenario map | schema/example source | reclassify 为 developer/current-executable Report |
| `soma-examples/docs/vrp-runtime-state-example.md` | VRP Blueprint；Design；scenario map | schema/example source | reclassify 为 developer/current-executable Report |
| `soma-examples/docs/simulation-runtime-state-example.md` | Simulation Blueprint；Design；scenario map | schema/example source | reclassify 为 developer/current-executable Report |
| `soma-examples/docs/game-runtime-state-example.md` | Game Blueprint；Design；scenario map | schema/example source | reclassify 为 developer/current-executable Report |
| `soma-examples/docs/runtime-state-schema-examples.md` | Blueprint/Report navigation | current example source | reclassify 为 developer Report index |

`soma-examples/docs/` 在目标目录映射中是 Report/developer 的 registered module output，不拥有 core Design；它可以描述当前 executable example，并必须明确目标差距。

## 5. Blueprint、Guide、Report 与 Temporary

| 当前材料 | 目标处置 |
|---|---|
| 四份 `docs/temp/*blueprint.md` | 已逐份吸收到候选 Blueprint；切换时删除旧 Temporary copies，并修复历史报告链接到新 Blueprint |
| `guides/` | 保留为 Report/user+developer 正式物理入口；候选 getting-started/developer 内容按事实合并，不创建平行指南 |
| root/module `reports/` | 保留原路径、日期、commit和历史结论；current index区分 current 与 archive/superseded |
| `docs-temp/reports/` | staging only；切换时分别 merge、转化或删除，不形成 `docs/reports/` 第三入口 |
| 文档体系重构 Temporary | 仅在原子切换、验证和 Governance Report形成后删除 |

## 6. 结论

32 份现行正式 Owner、四份长期研究蓝图、Guides/Reports、可执行契约与检查入口均已获得目标归宿。未发现必须原文长期保留但没有 Design/executable/Engineering/Report Owner 的事实类别。

本结论只证明迁移方案完整；`supersede`、reclassify、入口切换、link rewrite 和 checker 更新仍必须在获授权的正式切换变更中一次完成。
