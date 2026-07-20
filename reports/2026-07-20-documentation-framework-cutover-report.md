# 设计驱动文档体系正式切换报告

类型：Report / Governance

状态：完成

Owner：SOMA Java 文档体系切换输出

受众：项目 Owner、维护者与后续治理执行者

事实范围：候选文档体系正式切换的基线、Owner处置、入口/checker变化、验证和scope non-regression

非事实范围：修改产品/API/Schema/runtime能力、关闭场景差距或声明public release readiness

适用版本：框架 `1.0.0-rc.2`，切换候选 `74f8f3c`，最后 implementation-affecting baseline `b991f4c`

输入事实源：候选体系、32份现行Owner迁移审计、代码/golden/fixtures、scripts与current reports

审查日期：2026-07-20

审查环境：Azul Zulu OpenJDK `1.8.0_492-b09`，Maven Wrapper `3.9.16`，macOS `26.5.2`，aarch64

审查方法：原子Owner切换、全仓metadata/link/navigation审计、目标checker与完整`./scripts/check.sh`

最后审查日期：2026-07-20

## 1. 切换目标与边界

本专题把已在`docs-temp/`完成两轮审查的候选体系正式启用，使项目按以下关系工作：Blueprint约束目标，Design拥有长期规范，代码/可执行产物拥有当前实现事实，Implementation Map负责导航，Conformance识别偏差，Engineering治理过程，Report形成正式输出，Temporary只在专题期间存在并最终删除。

本次只改变文档权威、分类、导航和检查规则，不修改Java代码、annotation、generated API、Schema、runtime protocol、benchmark算法、artifact或Gate状态。

## 2. Owner与入口处置

- 35份Blueprint、Design、Implementation Map、Conformance和Engineering文档进入正式`docs/<category>/`；
- 14份旧root Design与12份旧module Design保留原路径、统一标记`superseded`并链接当前取代者，退出current导航；
- 6份`soma-examples/docs`场景文档改为current-executable developer Report，目标形态转由Blueprint拥有；
- 四份旧`docs/temp/*blueprint.md`在rich Blueprint接管后删除，历史报告链接改向正式Blueprint；
- 用户/开发者持续输出由`guides/`承载；性能、治理、Gate和release evidence继续由`reports/`承载；
- root/module README、AGENTS、CONTRIBUTING和PR模板全部改为从新权威关系进入。

32份旧正式Owner因此均获得`superseded`或Report reclassification处置，没有与新Design并列为current Owner。

## 3. 可执行治理变化

`scripts/check-docs.sh`改为验证分类metadata、Design→Blueprint、Map→Design/baseline、索引完整性、旧Owner superseded隔离、current Report metadata、Temporary lifecycle和全仓相对链接；rich Blueprint不再受到统一500行上限的错误约束。

`scripts/check-v1-scope.sh`不再依赖已superseded的phase/capability路线图，而是直接锁定设计宪法、Gate non-regression、Java 8、release identity、G6 blocked、文档治理、Agent护栏和PR防缩水规则。Benchmark smoke的治理输入同步指向正式Performance Design、Benchmark Engineering和Scenario Map。

## 4. 验证

切换后验证结果如下：

- `./scripts/check-docs.sh`：`scope-check: ok`、`doc-check: ok`；
- `git diff --check`：通过；
- 结构核验：35份current分类文档、26份superseded Design、6份current-executable example Report均与预期一致，`docs-temp/`已不存在；
- `./scripts/check.sh`：`project-check: ok`，覆盖完整JDK 8编译、测试、external consumer、生成契约、runtime、scenario、benchmark与scope/doc检查；
- implementation non-regression：相对最后implementation-affecting baseline `b991f4c`，产品源码与POM没有变化。

完整Gate后又执行了一轮独立交叉引用审查，发现两份current-executable example Report仍把已superseded契约写成正式事实源。该引用已改向当前Design与Executable Contract Map，`check-docs.sh`同时增加解析目标路径后的superseded Design反向引用检查；最终状态再次通过完整`./scripts/check.sh`。

第一次受限网络执行已通过scope、doc与reactor verify，随后在Maven Central依赖读取处因TLS握手终止；获准使用正常网络后，同一完整命令未经过产品或文档修正即通过。该中断归类为外部传输失败，不是项目断言失败。

## 5. V1 scope non-regression

- 受影响的是文档权威与工程治理，不是产品能力；
- Java-only边界、packed SoA、keyed/dense、swap-remove、incremental exact access、IndexBuffer/IndexSnapshot、ownership、materialization、RuntimePlan、structured failure和performance-shape均保持；
- G0–G5既有evidence与G6 blocked状态不变；
- VRP、Simulation和Game的Blueprint→Code差距、有限环境性能证据与G6外部事实差距继续由Conformance/Report记录；
- 没有新增temporary public/generated API、parallel fact source、test-only bypass或canonical hot-path迁移；
- 后续产品工作仍是additive completion或contract-preserving internal refinement；新的重大方向另开Temporary并获得授权。

## 6. 当前结论

设计驱动文档体系已经正式切换：新入口、唯一Owner、历史处置、checker、Report与module导航均已启用，本专题`docs-temp/`已在正式事实固化后删除，完整Gate通过。包含本报告的最终提交标识本次原子切换，候选输入保持为`74f8f3c`。

本结论只表示文档体系切换完成；不会关闭第5节保留的产品差距，也不会改变G6 blocked或public release readiness状态。
