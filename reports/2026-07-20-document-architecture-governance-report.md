# SOMA Java 文档架构专题治理报告

类型：Report / Governance

状态：完成

Owner：SOMA Java 文档架构专题输出

受众：项目 Owner、维护者与后续专题执行者

适用版本：文档框架 `1.0.0-rc.2`，治理输入 commit `a8f91db`，产品实现核对基线 `b991f4c`

输入事实源：正式 Blueprint/Design/Implementation Map/Conformance/Engineering、代码与 checker

事实范围：抽象层次与关注点治理的意图、Owner 裁决、文档迁移、门禁和 scope non-regression

非事实范围：修改产品 API、Schema、Runtime 语义、场景实现差距或 Gate 状态

审查日期：2026-07-20

审查环境：Azul Zulu OpenJDK `1.8.0_492-b09`，Maven Wrapper `3.9.16`，macOS `26.5.2`，aarch64

审查方法：职责审计、层次/追踪审计、重复规范事实审计、Blueprint 边界审计、checker 与完整项目验证

最后审查日期：2026-07-20

## 1. 意图与范围

本专题使正式文档体系真正遵循“抽象层次 × 关注点”：Blueprint 描述目标使用形态；Design 从系统原则逐层展开能力和横切约束；每项长期语义只有一个关注点 Owner；Implementation Map、Conformance 和 Report 分别承担当前投影、偏差判断和时点证据。

治理只改变文档责任、内容归位、追踪关系和检查规则。Java 源码、测试语义、annotation、generated API、Schema、Runtime、benchmark 算法以及 G0–G6 判定均不在变更范围。

## 2. 层次与 Owner 结果

Design 入口现以四类主要责任组织既有 Owner：

| 层次 | 责任 |
|---|---|
| `D0` | 产品边界、设计优先级和系统级不变量 |
| `D1` | 系统结构、模块协作和跨模块语言 |
| `D2` | Schema/API、存储访问、ownership、materialization 与 runtime plan 能力 |
| `Q` | correctness、performance、compatibility/security/version 横切约束 |

11 份 Design 均声明主要层次、主要关注点、上位设计和服务 Blueprint；入口同时维护关注点 Owner 表和五个 Blueprint 的反向追踪。层次没有被机械映射为目录或“一层一文件”，复合 Owner 则明确区分能力语义与内部机制约束。

设计宪法已下沉 annotation、swap-remove 和具体索引机制，只保留物理顺序非契约、读取不得依赖隐藏全表重建、候选范围不得静默扩张等系统不变量。

`IndexSnapshot` 的唯一 Owner 裁决如下：

- Schema 与生成 API 拥有公开消费契约；
- Ownership 与 lifecycle 拥有 epoch/currentness 和可选 `requireCurrent` 机制；
- Table、存储与访问只拥有 `IndexBuffer`、candidate operation 和 snapshot terminal 的存储隔离；
- 领域语言只定义术语并链接上述 Owner。

## 3. Blueprint 与当前事实分离

产品、FJSP、VRP、连续仿真和 Game 蓝图均声明 Design 约束入口。VRP、Simulation、Game 已把“当前示例盘点、自审、坏味道、待验证、当前判定”改写为：

- 目标数据角色与 Table 形态；
- 使用者可读的 annotation/API journey；
- 场景对 Design 的压力；
- 条件式方案的采用边界；
- 目标决策与证明义务。

当前 executable 入口和代码形态继续由 Scenario/Benchmark Map 拥有，目标与当前示例的偏差继续由 Conformance 拥有。丰富代码示例和 Access Pattern Card 均保留，没有把 Blueprint 降级为摘要。

## 4. 防回归与一致性

文档治理新增 Design 双维度规则，并明确上位只保留方向/不变量、下位 Owner 展开语义/机制。`check-docs.sh` 现在验证：

- Design 的合法层次、主要关注点、上位设计和服务 Blueprint；
- Design 入口的层次、Owner 与 Blueprint 追踪区段；
- 每个 Blueprint 的设计约束入口和反向追踪；
- Blueprint 不恢复当前实现参考、自审、坏味道、待验证或当前判定栏目。

门禁不限制文档数量、目录层级或统一篇幅，也不替代语义审查。Scenario Map 已增加逐场景 Blueprint 链接；Conformance 已登记并关闭 `CF-008`，原有场景、性能 evidence 与 G6 差距保持不变。

## 5. 验证与 scope non-regression

最终候选完成以下验证：

- `./scripts/check-docs.sh`：通过；
- `git diff --check`：通过；
- `./scripts/check.sh`：通过；
- 文档结构复审：11 份 Design 均有合法层次/上位关系，5 份 Blueprint 均有约束入口和 Design 反向追踪，3 份混合型 Blueprint 不再包含越界栏目；
- implementation non-regression：相对产品实现核对基线 `b991f4c`，没有产品源码、测试、POM 或 executable contract 变更。

本专题没有改变 keyed/dense、packed storage、swap-remove、exact access、IndexBuffer/IndexSnapshot、ownership、materialization、RuntimePlan 或 structured failure 语义。G0–G5 保持 passed，G6 保持 blocked；VRP、Simulation、Game 的 Blueprint → Code 差距和有限环境性能 evidence 仍由既有 Conformance 处置。

## 6. 结论

本次治理已把“抽象层次 × 关注点”从原则落实为正式 Design 导航、逐文档 metadata、Blueprint 追踪、唯一 Owner 裁决和可执行防回归规则。长期事实已固化到正式 Owner，专题 Temporary 已删除；新的文档体系可以继续承载功能变更、实现优化和后续专题治理。

本结论只表示文档架构治理完成，不扩大产品实施授权，也不改变任何 release readiness 声明。
