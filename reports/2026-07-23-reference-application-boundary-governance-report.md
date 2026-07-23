# SOMA 参考应用边界与 `soma-examples` 重构治理报告

类型：Report / Governance

状态：当前（治理已完成）

Owner：SOMA Java 参考应用边界治理输出

受众：项目 Owner、SOMA 维护者、参考应用维护者与 Gate reviewer

适用版本：core product baseline `fd82eba`；reference-application implementation baseline `955c956`

正式治理收口：commit `f6c3646`

输入事实源：正式 Blueprint/Design、两个参考应用源码与文档、neutral/application benchmark artifacts、Implementation Map、Conformance、Git provenance 与专项 Gate

事实范围：参考应用与 SOMA 产品的所有权边界、旧四场景 evidence replacement、`soma-examples`/benchmark 拓扑、Stage 5 原子切换及 scope non-regression

非事实范围：重新定义 SOMA public/generated API、Schema、Access Model、runtime 语义、跨环境性能声明、G6 或 release readiness

治理日期：2026-07-23

审查环境：Azul Zulu OpenJDK `1.8.0_492-b09`，Maven Wrapper `3.9.16`，macOS `26.5.2`，aarch64

最后审查日期：2026-07-23

## 1. 当前结论

本专题已完成参考应用边界的原子切换与正式收口：

- SOMA 根级 Blueprint、Design 与 Conformance 不再拥有 FJSP、VRP、Simulation、Game 领域目标；
- `soma-examples` 成为只登记两个普通 Java 8 consumer 的 Maven 聚合器，不再生产共享领域 JAR；
- 工业动态调度与个体生态仿真分别拥有应用 Blueprint、Design、配置、detached generator、bootstrap、runtime、validator 和 evidence；
- `soma-benchmarks` 只拥有领域中性的 component evidence，应用 integrated lane 回到各应用；
- 旧四场景源码、测试、golden、脚本、模块报告和 benchmark 依赖已退出 current tree；
- 旧报告只保留其当时事实和 Git provenance，不再拥有 current G5 或当前能力声明。

本次切换没有把“四个产品内嵌场景”替换成“两个新的产品内嵌场景”。两个参考应用是 SOMA public artifacts 的普通消费者，其领域目标和算法不进入 SOMA Design。

## 2. 目标拓扑

```text
soma-examples                         Maven aggregator only
├── industrial-dynamic-scheduler      ordinary Java 8 consumer
└── grassing-individual-simulation    ordinary Java 8 consumer

soma-benchmarks                       neutral component evidence
```

两个 child project 可以在 evidence-local repository 中独立构建，不依赖 `soma-testkit`、processor/runtime internal package、其他 example、reactor-only classpath 或第三方库。聚合器自身不声明 SOMA dependency，也不包含 `src/`。

## 3. 两个参考应用

### 3.1 工业动态调度

应用覆盖 flexible machine、precedence、release/material readiness、sequence-dependent setup、calendar/maintenance、transport、secondary resource、due date/priority、application-owned event queue、incremental frontier 与完整 commit/release 闭环。

四个受版本控制的 profile 覆盖 6、192、8,000 和 10,000 个 operation。Correctness profile 具有手工 oracle、完整 validator 和 lifecycle 负路径；default profile 由三个独立 JVM fork 记录 allocation、GC 和 runtime high-water。

### 3.2 个体生态仿真

应用独立实现 grasser–grass 模型，覆盖 growth、metabolism、reproduction、grassing、searching、birth/death、mode transition、显式 system 顺序和 deterministic random。

四个 profile 覆盖 5/800/30,000/5,000 个初始 individual 与 12/500/300/2,000 ticks。Correctness 逐 tick 与独立 AoS oracle 位级等价；large/long-run 保持真实 birth/death churn；输入顺序扰动不改变规范性结果。

两个应用都遵守：

```text
versioned config
  -> detached input generator
  -> immutable validated input
  -> one-time runtime bootstrap
  -> authoritative runtime loop
```

Generator 不持有 SOMA runtime，runtime 不反向调用 generator；相同配置与 seed 产生相同 input checksum。

## 4. Evidence replacement

| 能力/责任 | 当前唯一 Owner |
|---|---|
| public/generated API、Schema/hash、Point/Candidate/Column/Key/Bulk/Ownership | core fixtures、external consumer 与正式 Gate |
| scheduler exact-group、child、update/remove、failure workflow | core fixture + industrial scheduler |
| packed/column/bulk/swap-remove、物理顺序独立性 | core fixture + grassing simulation |
| exact-index 与 dense scratch/component allocation/memory | neutral benchmark schema |
| application integrated allocation/GC | 两个应用各自的 multi-fork lane |
| Java 8 ordinary-consumer isolation | reference-application isolation Gate |
| 旧 FJSP/VRP/Game 算法与旧 simulation 模型 | 领域探索事实，正式退役 |

因此，旧 generated-type 总量、四份领域 schema/hash、`ScenarioSuite` 和 FJSP 100k runner 不再作为 current product evidence。所需产品责任已经由 core、neutral component 或两个应用接管；领域专属行为没有被伪装成 SOMA 能力。

## 5. 当前性能与 footprint 边界

Neutral benchmark 已通过 16 条 allocation 与 24 条 exact-index memory record。两个应用各自具有三 fork、同 identity 的 diagnostic，均保持 `claimAllowed=false` 且未观察到 Young/Full GC。

Generated footprint 现在按三个 surface 分开：

| Surface | Scan count | Scan source bytes | source lines | Scan family class bytes |
|---|---:|---:|---:|---:|
| neutral benchmark | 6 | 144,720 | 644 | 203,536 |
| industrial scheduler | 12 | 296,763 | 1,279 | 425,166 |
| grassing simulation | 2 | 47,835 | 213 | 68,276 |

这些数据只证明 `955c956` 在本机和对应 workload 下的执行形状，不建立跨环境 SLA、普遍性能优势、正式支持矩阵或 release claim。

## 6. 验证与收口

Stage 5 原子切换候选在提交前通过完整 Gate，并提交为 immutable implementation baseline `955c956`。Stage 6 随后在该提交上重新执行 `./scripts/check.sh`，结果为 `project-check: ok`：

- 文档 metadata、链接、唯一产品 Blueprint、应用非规范性边界与 Temporary lifecycle 检查；
- 根 Maven reactor `verify`；
- 两个 child 的 isolated repository/build/runtime graph/repeat-manifest Gate；
- scheduler correctness/default/large/long-run 与三 fork evidence；
- simulation AoS oracle/order-independence/default/large/long-run 与三 fork evidence；
- neutral benchmark smoke 的 20+20 records 和 36 条 fail-closed negative path；
- 16 条 allocation、24 条 memory component evidence；
- 三 surface generated-footprint Gate。

最终引用闭包按旧 package、`ScenarioSuite`、旧 Gate、FJSP runner 与“四场景 current claim”等精确 identity 审计：剩余命中只属于历史 Report、Git provenance 或 checker 的禁止恢复断言。长期事实已提升到 Blueprint、Design、Implementation Map、Conformance、Engineering、current Report 与两个应用文档的唯一 Owner；Temporary 在 `f6c3646` 删除，不归档。

最终候选继续使用 Azul Zulu full JDK 8；未增加其他 JDK 验真或支持声明。`git diff --check` 通过，G6 状态保持不变。

## 7. Scope non-regression

- SOMA public/generated API、annotation Schema、Access Model 和 compatibility v4 未改变；
- packed storage、Index/IndexSnapshot、ownership、lifecycle、failure atomicity 与 runtime protocol 未改变；
- G0–G4 的 core evidence 未削弱，G5 由更清晰的 core + neutral + ordinary-consumer evidence 接管；
- 两个应用没有使用 internal API、testkit、共享领域源码、reactor classpath 捷径或第三方依赖；
- 没有增加第三个参考应用，没有把 SOMA 扩展为 ECS、调度器或仿真框架；
- 性能 Gate 未放宽，本机 artifact 不外推；
- G6 继续 `blocked`，本专题不处理发布事实。

本次变更是产品边界纠正和 evidence Owner 重组，不是产品能力删除，也不要求未来重写才能成立。专题没有残留平行 Owner、共享 example runtime、旧场景 current 导航或未接管 evidence。
