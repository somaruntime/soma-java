# 个体生态仿真参考应用架构治理

类型：Temporary

状态：active

Owner：grassing-individual-simulation application architecture governance

事实范围：个体生态仿真参考应用的候选软件架构、迁移边界、非回归约束、阶段计划与验收条件

非事实范围：SOMA 产品 Blueprint、核心 public/generated API、annotation Schema 语义、Access Model、runtime 语义、跨环境性能声明和发布准备度

最后审查日期：2026-07-23

## 1. 意图

把 `grassing-individual-simulation` 从“领域语义和 evidence 完整，但 production、
oracle、verification 与 benchmark 共处一个 source-set 的可执行样例”，提升为可以
被真实 Java 8 仿真项目模仿的正式 reference application。

治理对象是应用自己的 canonical journey、责任分层、依赖方向、result lifecycle、
source-set 和 Gate。实现继续服务于应用 Blueprint/Design；当前代码已经通过 Gate，
不代表其目录与责任形态天然适合作为长期设计。

## 2. 目标

- 建立唯一、自然的
  `Config -> Scenario Factory -> Scenario -> Simulator -> Result` production journey；
- 明确 Application、Config、Scenario/Factory、Simulator/Session、Engine/System、
  Runtime、Schema、Result 和 Support 的责任与依赖方向；
- 普通调用者不需要直接创建、关闭或验证 `SimulationRuntime`；
- `SimulationResult` 在 runtime 关闭后仍可独立消费；
- 完整状态导出采用显式、带成本边界的 Snapshot，而不是默认物化整个 world；
- production JAR 不包含 AoS oracle、verification、benchmark、JVM metrics、
  runtime negative checks 或测试 fixture；
- synthetic scenario generation 与 live runtime 完全解耦；
- problem/domain 参数与 benchmark measurement 参数分离；
- 将 `SimulationEngine` 的 system 编排与各 system 实现分责，同时保持固定顺序；
- 将 runtime aggregate、projection、verification 和 evidence access 分责；
- 保持 Java 8、单一 child Maven project、零新增第三方依赖和 ordinary consumer
  isolation。

## 3. 非回归约束

治理不得删除、改变或弱化：

- logistic growth、metabolism/death、reproduction、grassing、searching 和 mode
  transition；
- `growth -> metabolism/death -> reproduction -> grassing -> searching -> trace`
  的规范性顺序；
- `random(seed, tick, grasserId, processKind, drawIndex)` 确定性寻址；
- stable-ID child allocation、group-order independence、swap-remove independence；
- grass grid 作为 application-owned row-major `double[]` 的权威地位；
- seed-bank floor、cell budget、two-phase publish 和 fail-stop 边界；
- key、exact group、Candidate update/remove、Batch、ColumnView、IndexSnapshot gather
  等现有 SOMA Access Model 覆盖；
- correctness/default/large/long-run 四类 workload；
- 逐 tick AoS 位级等价、initial-order independence、稳定 checksum、长期 invariant、
  lifecycle/IndexSnapshot 负路径；
- isolated clean/repeat、Java 8 class、schema/generated reproducibility、三 fork
  allocation/GC/runtime high-water evidence；
- 所有本机 artifact 的 `claimAllowed=false` 边界。

SOMA 核心 public/generated API、annotation Schema 语义、Access Model、ownership、
Index 生命周期、失败原子性和 runtime protocol 均不得改变。

## 4. 非目标

- 不把 SOMA 扩展成 ECS、仿真框架或游戏引擎；
- 不增加 GUI、renderer、持久化、网络或分布式能力；
- 不把 grass grid 强行迁移到 SOMA Table；
- 不借架构治理改变仿真规则、数值顺序或性能结论；
- 不拆成多个 Maven child，也不治理工业动态调度应用；
- 不处理 release/G6。

## 5. 当前授权边界

项目 Owner 已在 2026-07-23 授权使用 Goal Mode 完成本专题，包括 production
Java、资源、测试、应用正式文档、Implementation Map、Conformance、Report、Gate
和阶段性 Git 提交。该授权不扩大本协议的事实范围。以下情况在任何阶段都必须停止
并请求决定：

- 需要修改 SOMA public/generated API、annotation 或 runtime；
- 需要改变仿真领域语义、结果语义、随机语义或 evidence 强度；
- 需要引入第三方依赖；
- 需要扩大到另一个参考应用或 SOMA 产品设计；
- 需要默认物化完整对象图或建立新的长期 public contract。

## 6. 整体阶段

1. **Stage 0：基线与协议**——冻结当前事实、建立审计和 immutable starting point；
2. **Stage 1：详细设计**——裁决 vocabulary、Facade/Session、Result/Snapshot、
   package DAG、system 边界、source-set 与迁移顺序；
3. **Stage 2：canonical boundary**——先建立 Scenario、Simulator、Session 和
   detached Result，切换 composition root；
4. **Stage 3：内部责任**——拆 Config/Factory、Runtime projection、Engine/System，
   原子完成 `state -> schema`；
5. **Stage 4：source-set 与 Gate**——迁移 oracle/verification/benchmark/checks，
   分离配置并增加 package DAG、JAR purity 和 retired identity 规则；
6. **Stage 5：候选验证与正式切换**——四 profile、AoS、order independence、
   long-run、multi-fork、isolated/repeat 和完整 Gate；
7. **Stage 6：正式收口**——形成 Governance Report，原子固化正式文档，删除
   Temporary，恢复无 active topic 状态并提交。

每个实施 slice 必须独立正确，不得依赖未来重写才成立。

## 7. 完成条件

- production 使用者只需理解 Config、Scenario、Simulator/Session 和 Result；
- package/source-set 与最终应用 Design 一致，依赖方向无环；
- production JAR 和 runtime classpath 不包含 test/evidence implementation；
- generator、runtime state、system、result、validation、oracle 和 benchmark 各有唯一
  Owner；
- 默认 Result 不隐式复制完整 world；完整 Snapshot 的成本和生命周期明确；
- 四 workload、领域语义、SOMA Access Model 覆盖和全部 Gate 没有缩水；
- 专项 Gate、reference applications Gate、`./scripts/check.sh` 和
  `git diff --check` 全部通过；
- 形成正式 Governance Report，长期事实提升至唯一正式 Owner；
- 引用闭包完成，本 Temporary 删除，工作树干净且全部授权修改已提交。

## 8. 当前专题材料

- [当前架构审计](current-architecture-audit.md)
- [目标架构候选](target-architecture.md)
- [详细设计](detailed-design.md)
- [迁移与验收账本](migration-and-acceptance-ledger.md)
