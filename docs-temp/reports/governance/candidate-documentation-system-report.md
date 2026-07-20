# 候选文档体系评估

类型：Report / 治理评估

状态：候选，切换准备完成

Owner：SOMA Java 文档体系评估

受众：项目 Owner 与后续文档维护者

事实范围：候选文档体系的结构、迁移完整性、验证结果、限制和切换前判定

适用版本：框架 `1.0.0-rc.2`，现行文档 `fe163b8`，实现 `b991f4c`

输入事实源：候选体系、现行正式 Owner、代码/golden/fixtures、scripts与current reports

审查日期：2026-07-20

审查环境：本地 macOS aarch64；target-path rehearsal使用明确的`/bin/sh`

审查方法：分类/Owner审查、32份Owner迁移覆盖、target-path metadata/link/index rehearsal、现行docs Gate与full project Gate

最后审查日期：2026-07-20

## 1. 当前事实

当前正式入口和Owner仍是现行`docs/`、module docs、`guides/`与`reports/`；候选体系尚未启用。SOMA实现以`b991f4c`为最后implementation-affecting baseline，之后到`fe163b8`只有文档与报告变化。

候选体系现有49份Markdown，覆盖Blueprint、Design、Implementation Map、Conformance、Engineering、Report和Temporary。它们当前只用于切换前审查，不改变产品能力、实施授权或G0–G6状态。

## 2. 目标体系的主要改进

- 五份 Blueprint从使用者目标出发；FJSP、VRP、Simulation和Game均包含annotation/API journey、Access Pattern和application/SOMA边界；
- Design保持约1,000行的高层长期语义，而不是复制现行10,000余行的实现/签名清单；
- exact current surface由代码、`javap` golden、schema artifact、validator与external consumer拥有，并通过Executable Contract Map定位；
- Implementation Map保持简短，只给关键流程、hot path、测试入口与immutable implementation baseline；
- Conformance区分产品目标差距、evidence限制、release blocker和文档切换lifecycle；
- Engineering覆盖文档、build/dependency、testing/evidence、G0–G6、benchmark和release过程；
- Report作为逻辑分类映射到现有`guides/`与`reports/`，不制造第三个输出入口；
- Temporary保持到原子切换最后，不把正式Design暴露为中间状态。

## 3. 迁移与验证

- 32份现行正式Owner全部获得Design、executable contract、Engineering或Report归宿；
- 四份现行长期研究Blueprint已逐份吸收，旧copies有删除与link rewrite方案；
- 旧Design统一退出current导航并标记superseded；current example docs转为developer/current-executable Report；
- 目标目录rehearsal覆盖35份正式分类Markdown，metadata、category index、relative links、Implementation Map baseline和prohibited Temporary reference均通过；
- 当前`./scripts/check-docs.sh`、candidate metadata audit、`git diff --check`与完整`./scripts/check.sh`通过；完整Gate以`project-check: ok`结束。

正式checker尚未切换，因为当前现行文档仍是Owner；它必须与正式入口、旧Owner处置和Temporary删除在同一cutover变更中更新。

## 4. 已知差距与边界

VRP、Simulation和Game仍有Blueprint到example code的已裁决目标差距；性能结论仍限于已测环境；G6仍blocked。这些事实不阻塞文档体系切换，也不会被切换自动关闭。

本报告不声称已经正式切换，不声称candidate Reports都会原样保留，也不声明public RC、production ready或support matrix完成。

## 5. 判定

候选体系已具备承载“Blueprint约束Design、Design指导实现、Map定位代码、Conformance识别偏差、Engineering形成证据、Report输出结果、Temporary原子退役”的能力。切换前未发现blocking framework/content defect。

当前状态因此为：**ready for cutover, not enabled**。下一步是在明确授权下执行单次原子切换、更新checker、运行完整Gate、形成正式Governance Report并删除Temporary。
