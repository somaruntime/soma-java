# 迁移与验收账本

类型：Temporary

状态：active

Owner：grassing-individual-simulation migration and acceptance ledger

事实范围：实施阶段、责任接管、非回归 evidence、阶段状态和最终引用闭包

非事实范围：正式应用 Design、未执行的性能结论、SOMA 产品能力和发布状态

最后审查日期：2026-07-23

## 1. Immutable points

| 阶段 | Commit | 状态 | 证据 |
|---|---|---|---|
| 治理前实现 | `1c1bc22` | passed | Zulu JDK 8 完整 `project-check: ok` |
| Stage 0 Temporary | `f852028` | passed | 文档 Gate、现状审计与治理协议 |
| Stage 1 详细设计 | `5c8f781` | passed | vocabulary、DAG、Result/Snapshot、system/source-set裁决 |
| Stage 2 canonical boundary | 待提交 | in progress | Scenario/Factory、Simulator/Session、detached Result、composition root |
| implementation candidate | 待形成 | pending | 专项、四 profile、AoS、multi-fork、isolated/repeat |
| final cutover | 待形成 | pending | 完整 Gate、Report、Temporary退役 |

## 2. 责任接管候选

| 当前责任 | 目标唯一 Owner | 状态 |
|---|---|---|
| CLI 手工 runtime 编排 | `Simulator` / `SimulationSession` | passed |
| Config + benchmark keys | domain Config + test BenchmarkOptions | pending |
| static initial generator | `SimulationScenarioFactory` implementation | passed |
| nested `IndividualInput` | top-level Scenario input type | passed |
| bootstrap 全部职责 | runtime factory/projector/verifier | pending |
| Engine 全部 system | engine orchestrator + ordered systems | pending |
| Engine result/checksum | detached `result` boundary | passed |
| runtime materialization/stats | test access / evidence | pending |
| live Runtime validator | detached result + test-only oracle | in progress |
| `state` 技术包 | `schema` 技术投影 | pending |
| main oracle/checks | test oracle/verification | pending |
| main benchmark/JVM metrics | test benchmark | pending |

## 3. Stage 1 裁决

- canonical journey 为
  `Config -> Scenario Factory -> Scenario -> Simulator/Session -> Result`；
- Scenario 同时拥有 Config 与 detached initial state；
- `Simulator`/`SomaSimulator` 是 facade，Session 是 canonical stepwise API；
- Result 是轻量 detached summary；本专题不新增完整 Snapshot public contract；
- Engine 只编排，六个 ordered process 各有独立 package-private Owner；
- Runtime factory、projector、projection verifier 和 live aggregate 分责；
- `state -> schema` clean cutover，不保留兼容壳；
- correctness/large/long-run、oracle、verification、benchmark 和 runtime test access
  全部进入 test source-set；
- input/result checksum 和 random addressing 必须保持；config/schema/plan identity
  按已声明的责任与 package 迁移建立新稳定基线。

## 4. 非回归验收

| 事实 | 基线 | Candidate | 最终 |
|---|---|---|---|
| 四 profile deterministic input | passed | pending | pending |
| correctness per-tick AoS bit equality | passed | pending | pending |
| initial-order independence | passed | pending | pending |
| domain invariant / churn | passed | pending | pending |
| lifecycle / IndexSnapshot negative | passed | pending | pending |
| long-run 2,000 ticks | passed | pending | pending |
| multi-fork allocation/GC/high-water | passed | pending | pending |
| ordinary consumer clean/repeat | passed | pending | pending |
| Java 8 + generated/schema reproducibility | passed | pending | pending |
| production JAR excludes evidence | failed by design | pending | pending |
| package dependency DAG | not gated | pending | pending |
| canonical Scenario/Simulator/Result journey | failed by design | passed | pending |

Stage 2 在 Zulu JDK 8 通过现有专项 Gate。四 profile 的 input/result checksum、
correctness AoS oracle、replay/order independence、long-run 和三 fork evidence 均
保持稳定；专项 Gate 的 source-set/JAR/DAG 规则将在 Stage 4 加固。

## 5. 实施原则

- 先建立最终边界，再移动内部责任；
- 每次只迁移一个共同变化原因，并保持当前 checksum/evidence；
- System 拆分不得改变浮点、随机、遍历或 publish 顺序；
- source-set 迁移必须同步 test classpath 和 Gate，不能暂时丢失 evidence；
- schema identity 变更必须原子完成 source、generated、hash、map 和 checker；
- 不以 LOC 或文件数作为拆分/删除依据；
- 不借本专题修改 SOMA core 或工业动态调度应用。

## 6. Gate 目标

专项 Gate 最终至少 fail-closed 验证：

- production/test 必需 package 和 source-set；
- config/scenario/support 不依赖 SOMA runtime/generated；
- application 只通过 Simulator facade；
- runtime/result/schema/system 依赖方向；
- production 不依赖 test/evidence；
- production JAR purity；
- retired package/type identity；
- 四 profile、AoS、order independence、long-run、negative path；
- 三 fork checksum/identity、allocation、GC 和 high-water；
- isolated local repository、clean/repeat manifest 和 Java major 52。

## 7. 最终引用闭包

最终 current 导航与 production source 不应继续依赖：

```text
com.hgtech.soma.examples.grassing.state
com.hgtech.soma.examples.grassing.evidence
validation.ReferenceSimulation
runtime.SimulationRuntimeChecks
runtime.SimulationRuntimeBootstrap
runtime.SimulationResult
SimulationConfig benchmark accessors
```

Checker 的禁止恢复断言和历史 Governance Report 可以保留旧 identity，但不得成为
current API 或实现导航。
