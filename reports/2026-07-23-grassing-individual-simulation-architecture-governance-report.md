# 个体生态仿真参考应用架构治理报告

类型：Report / Governance

状态：当前（治理已完成）

Owner：grassing-individual-simulation application architecture governance output

受众：项目 Owner、SOMA 维护者、参考应用维护者与 Gate reviewer

适用版本：治理前基线 `1c1bc22`；实现候选 `287350d`；正式文档切换 `8ca1ab2`

输入事实源：应用 Blueprint/Design、production/test source、isolated consumer
artifact、四 profile、AoS oracle、multi-fork evidence、Implementation Map、
Conformance、Git diff 与完整 Gate

事实范围：个体生态仿真参考应用的软件架构、source-set、canonical journey、验证
结果和 scope non-regression

非事实范围：重新定义 SOMA public/generated API、annotation Schema 语义、Access
Model、runtime 语义、跨环境性能声明、G6 或 release readiness

治理日期：2026-07-23

审查环境：Azul Zulu OpenJDK `1.8.0_492-b09`，Maven Wrapper `3.9.16`，
macOS `26.5.2`，aarch64

最后审查日期：2026-07-23

## 1. 当前结论

本专题已经正式收口。`grassing-individual-simulation` 不再是 production、oracle、
verification 和 benchmark 共处一个 source-set，并由入口直接拼装 runtime 的
可执行样例，而是具有明确应用边界的普通 Java 8 reference consumer：

```text
versioned config
  -> SimulationScenarioFactory
  -> immutable SimulationScenario
  -> Simulator / SimulationSession
  -> detached SimulationResult
```

应用长期目标和设计分别由[应用 Blueprint](../soma-examples/grassing-individual-simulation/docs/blueprint.md)
与[应用 Design](../soma-examples/grassing-individual-simulation/docs/design.md)拥有；
当前实现由代码拥有，并由
[Implementation Map](../docs/implementation-map/scenario-and-benchmark-map.md)
导航。本报告只记录治理结论与时点 evidence。

## 2. 架构结果

production 已按共同变化原因形成以下责任：

- `config`：strict properties、typed immutable config、canonical text/checksum；
- `scenario`：detached `SimulationScenario`、`IndividualSeed` 与 Factory；
- `simulation`：canonical `Simulator`、one-shot `SimulationSession` 与
  `SomaSimulator`；
- `runtime`：factory、projector、projection verifier、aggregate、engine、ordered
  systems、primitive staging 与 result assembly；
- `schema`：application-owned annotation schema；
- `result`：detached immutable Result/Diagnostics；
- `support`：无状态 deterministic random 与 stable hash；
- `SimulationApplication`：唯一 composition root 和 CLI renderer。

`SimulationEngine` 只按
`growth -> metabolism/death -> reproduction -> grassing -> searching -> trace`
固定编排六个独立过程。Runtime creation、Scenario projection、projection verification
和 aggregate ownership 已分责；部分创建失败和多资源关闭都具备完整释放闭包。

`SimulationResult` 在 runtime 关闭前完成 detached assembly，关闭后仍可独立读取，
不保存 Table、Index、IndexSnapshot、Cursor、ColumnView、Batch、record 或 mutable
array。当前 Blueprint 不要求完整 world 导出，因此本专题没有引入默认
`SimulationSnapshot` 或隐藏对象图物化。

## 3. 输入、运行时与 evidence 边界

数据生成和实际运行时状态已经彻底分离：

- production resources 只保留 `config/default.properties`；
- correctness、large、long-run profiles 位于 test resources；
- benchmark fork/measurement 参数独立位于 test benchmark config；
- synthetic Scenario Factory 只生成 detached input，不持有 live runtime；
- AoS oracle、verification、negative checks、benchmark、JVM metrics 和 runtime
  test access 全部位于 `src/test`。

production JAR 不包含上述 evidence implementation。应用 package DAG、JAR purity、
canonical contract 与 retired identity 均由
[`check-grassing-simulation.sh`](../scripts/check-grassing-simulation.sh)
fail-closed 验证。

应用 schema 从含混的 `state` clean cutover 为 `schema`，schema name 更新为
`grassing_individual_simulation_schema`，并完成 clean/repeat reproducibility。
这只改变 application-owned identity；Table、字段、key、exact index、capacity 和
SOMA Access Model 语义没有变化。

## 4. 领域与性能非回归

四个 profile 的 domain input/result checksum 与治理前逐项相同；correctness 继续
逐 tick 与独立 AoS 实现比较 IEEE-754 bits，initial packed order 反转不改变结果，
long-run 继续执行 2,000 ticks。logistic growth、metabolism/death、reproduction、
grassing、searching、mode transition、确定性随机、stable child identity、
seed-bank floor、cell budget、two-phase publish 和 fail-stop 语义均保留。

三 fork 对照中：

- 治理前平均 allocated bytes 为 `7,425,416`，正式切换树为 `7,439,536`，变化约
  `+0.19%`；
- 两侧 Young/Full GC count 均为 `0`；
- exact-index、update scratch、operation scratch high-water 均保持
  `64,333`、`27,336`、`12,776 bytes`；
- population table growth count 均为 `1`；
- 候选仍显著低于 `64 KiB/measured tick` 防回归阈值。

本机 wall-clock 约变化 `+1.5%`，只作诊断，不形成可归因性能结论。全部 artifact
保持 `claimAllowed=false`。

## 5. 验证

实现候选通过：

- `./scripts/check-grassing-simulation.sh`；
- `./scripts/check-reference-applications.sh`；
- `./scripts/check-docs.sh`；
- `git diff --check`；
- `./scripts/check.sh`，最终输出 `project-check: ok`。

四 profile 的 input/result checksum、AoS oracle、order independence、lifecycle/
IndexSnapshot 负路径、long-run、三个独立 JVM fork、ordinary production classpath、
isolated repository、clean/repeat manifest、Java major 52、production JAR purity、
package DAG 和 generated/schema reproducibility 均通过。

验证使用项目唯一 authority——Azul Zulu full JDK 8。没有增加其他 JDK
distribution 的验真或支持声明，也没有引入第三方依赖。

## 6. Scope non-regression

从治理前基线 `1c1bc22` 到实现候选 `287350d` 的路径审计表明：

- 没有修改 `soma-annotations`、`soma-processor`、`soma-runtime-core`、
  `soma-testkit`、`soma-benchmarks` 或工业动态调度应用；
- SOMA public/generated API、annotation Schema 语义、Access Model、ownership、
  Index 生命周期、失败原子性和 runtime protocol 均未改变；
- 没有删除领域过程、场景目标或 evidence lane，也没有降低 Gate；
- 没有把 test helper、Java Stream、DTO live graph、metadata interpreter 或
  runtime shortcut 引入 hot path；
- 变更只涉及该参考应用、对应专项 Gate，以及必要的正式导航、Conformance 和
  Report。

因此，本专题是 application architecture refinement，不是 SOMA 产品设计变更，也
不依赖未来重写才成立。

## 7. 正式收口

正式 Blueprint、Design、Validation、Implementation Map、Conformance、性能摘要
与 Goal 状态已接管全部长期事实；旧 identity 的 current 引用闭包已完成，剩余
字符串只属于 checker 的禁止恢复断言、历史 Report 或 test oracle 的合法名称。
专题 Temporary 已删除，`docs/README.md` 恢复无 active topic 状态。

本专题范围内没有未裁决尾项、平行 Owner 或已知架构坏味道。G6 继续 `blocked`；
发布事实和 release readiness 不属于本专题，也未被本报告扩大。
