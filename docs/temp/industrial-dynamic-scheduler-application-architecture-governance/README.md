# 工业动态调度参考应用架构治理

类型：Temporary

状态：active

Owner：industrial-dynamic-scheduler application architecture governance

事实范围：工业动态调度参考应用的内部软件架构候选、迁移边界、非回归约束、验收与退役条件

非事实范围：SOMA 产品 Blueprint、核心 public/generated API、annotation Schema 语义、Access Model、runtime 语义、跨环境性能声明和发布准备度

最后审查日期：2026-07-23

## 1. 意图

把 `industrial-dynamic-scheduler` 从“功能和 evidence 完整，但由 runner 直接拼装的参考样例”
提升为可以被真实 Java 8 项目模仿的正式 reference application。治理对象是应用内部
责任、依赖方向、source-set 和使用入口，不重新定义 SOMA 产品。

实现继续服务于应用 Blueprint/Design；现有代码不能因为已经通过 Gate，就反向降低
本专题的架构目标。

## 2. 目标

- 建立唯一、自然的 `Problem -> Solver -> Result` production journey；
- 明确 Config、Problem/Factory、Solver、Runtime、Schema、Result 和 Application
  的责任与依赖方向；
- `ScheduleResult` 成为关闭 runtime 后仍完整可用的 detached result；
- runtime、solver 不依赖 validation、evidence、fixture 或 benchmark；
- production JAR 不包含 fixture、oracle、runtime checks、verification、benchmark
  和 JVM measurement helper；
- synthetic input generation 与 production solving 解耦；问题规模仍由版本化配置控制；
- benchmark 参数退出 production application config；
- 拆解当前集中变化的 `SchedulingProblem`、`IndustrialScheduler`、
  `SchedulerRuntimeBootstrap` 和 `ScheduleValidator`；
- 保持 Java 8、零新增第三方依赖、ordinary consumer isolation 和现有完整 evidence。

## 3. 非回归约束

治理不得删除或弱化：

- flexible machine、precedence、release/material readiness、sequence-dependent setup、
  maintenance、transport、secondary resource、due/priority/tardiness；
- application-owned event queue、frontier refresh、total-order selection、version
  revalidation、显式跨 Table commit 和 successor release；
- stable identity、primary/unique/exact/owned-child、ColumnView、Batch、Candidate
  update/filter/sort/remove 与最终 materialization 的 SOMA 使用；
- correctness/default/large/long-run 四类 workload；
- deterministic config/input/result checksum、手算 fixture、独立领域 validator、
  lifecycle/IndexSnapshot 负路径和物理顺序独立性；
- isolated clean/repeat build、Java 8 class、schema/generated reproducibility、
  long-run 与至少三 fork allocation/GC/runtime high-water evidence；
- 所有本机 artifact 的 `claimAllowed=false` 边界。

SOMA 核心 public/generated API、annotation Schema 语义、Access Model、ownership、
Index 生命周期、失败原子性和 runtime protocol 均不得改变。

## 4. 授权边界

本专题可以修改该 child application 的 Java、resources、POM、应用文档和专项 Gate；
可以拆分、重命名或删除被替代的应用内部类型，可以重排应用自己的 schema/generated
package，但必须保持 Table、字段、identity、cardinality、ownership 和访问语义，
并重新验证 application schema/runtime-plan identity。

只可对根级 Implementation Map、Conformance、Report、入口和 checker 做该应用所需
的同步。不得修改 SOMA production modules、其他参考应用、neutral benchmark
语义或 release/G6。

以下情况必须停止并请求项目 Owner 决定：

- 需要修改 SOMA public/generated API、annotation 或 runtime；
- 需要改变本应用领域能力、算法语义、结果语义或 evidence 强度；
- 需要引入第三方依赖；
- 需要扩大到另一个参考应用或 SOMA 产品设计。

## 5. 实施阶段

1. 冻结当前事实、建立架构审计和 immutable Stage 0 baseline；
2. 完成目标依赖、类型责任、source-set、结果与迁移设计；
3. 建立 canonical application/solver/result 边界，先消除依赖环和重复编排；
4. 拆分 Problem、Config、Runtime、Solver 和 Schema 责任；
5. 将 fixture/oracle/verification/benchmark 迁出 production source-set并强化 Gate；
6. 在 immutable candidate 上执行专项与完整 Gate、scope non-regression 审查；
7. 将长期事实原子固化到应用正式文档和必要地图，形成 Governance Report；
8. 删除本 Temporary，恢复无 active topic 状态并提交最终收口。

每个实施 slice 必须独立正确，不得依赖未来重写才成立。

## 6. 完成条件

- production 使用者只需要理解 `SchedulingProblem`、`SchedulingSolver` 和
  `ScheduleResult`，不需要手工管理 `SchedulerRuntime`；
- package/source-set 与正式 Design 一致，依赖方向无环；
- production JAR 与 runtime classpath 不包含 test/evidence implementation；
- synthetic generator、runtime state、result、validation 和 benchmark 各有唯一 Owner；
- 四个 workload、领域能力、SOMA Access Model 覆盖和全部 Gate 没有缩水；
- `./scripts/check-industrial-scheduler.sh`、`./scripts/check-reference-applications.sh`、
  `./scripts/check.sh` 和 `git diff --check` 全部通过；
- 形成正式 Governance Report，长期事实提升至唯一正式 Owner；
- 引用闭包完成，本 Temporary 删除，`docs/README.md` 恢复“当前没有 active
  Temporary topic”，工作树干净且全部修改已提交。

## 7. 当前专题材料

- [当前架构审计](current-architecture-audit.md)
- [目标架构](target-architecture.md)
- [详细设计](detailed-design.md)
- [迁移与验收账本](migration-and-acceptance-ledger.md)
