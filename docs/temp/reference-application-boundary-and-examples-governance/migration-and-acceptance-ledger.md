# 迁移与验收账本

类型：Temporary

状态：active

Owner：SOMA 参考应用迁移与验收

事实范围：旧责任的替代 Owner、实施切片、删除前置条件、验证 Gate 和最终收口检查

非事实范围：正式 Gate 状态、最终 Conformance、public API 变更授权或 release claim

最后审查日期：2026-07-23

## 1. Evidence replacement ledger

| 旧责任 | 起始 evidence | 目标 Owner | 删除前证据 | 状态 |
|---|---|---|---|---|
| primary key / unique / exact access | FJSP/VRP/Game schema + external fixtures | core external fixtures + scheduler | core Gate + scheduler isolated run | passed |
| Candidate exact group update/filter/sort/best/remove | FJSP frontier | core access fixture + scheduler | differential/invariant + application run | passed |
| packed dense scan/update | Simulation/Game | core access fixture + simulation | AoS oracle + long-run | passed |
| ColumnView hot access | scenario source-shape + core fixture | core fixture + simulation | isolated app + source/import check | passed |
| owner child | FJSP/VRP | core child fixture + scheduler | child lifecycle/replacement + app import | passed |
| Batch add/replace/clear | all scenarios | core fixture + both apps | build/run/oracle | passed |
| Index/IndexSnapshot invalidation | FJSP verification | core access fixture + both apps | stale/wrong-source + order-independence | passed |
| swap-remove relocation | FJSP/Game | core runtime fixture + both apps | removal/checksum/tie-break | passed |
| materialization/budget | scenario result/export | core fixture + app final output | budget/graph/result validation | passed |
| failure/lifecycle | FJSP suite + scenario protocols | core invariant + both aggregate owners | negative/fail-stop/release tests | passed |
| exact-index component cost | FJSP schema benchmark | neutral benchmark schema | multi-fork artifact | passed |
| dense replace/sort component cost | VRP schema benchmark | neutral benchmark schema | multi-fork artifact | passed |
| integrated allocation/GC | FJSP 100k | scheduler + simulation own lanes | semantics-specific multi-fork artifact | passed |
| generated breadth/schema/hash | four scenario manifest | core external breadth + two app manifests | clean/repeat generated evidence | passed |
| Java 8 executable consumer | ScenarioSuite | two isolated applications | separate Maven build/class major | passed |
| workload/initial-state construction | scenario code inline fixtures | versioned app config + detached generator | deterministic input checksum + bootstrap equivalence | passed |
| FJSP/VRP/Game business algorithms | old example code/docs | retire | domain-only classification | retired |
| old simulation business model | old example | retire/replaced by new independent model | no current owner/reference | retired |

## 2. Asset closure ledger

| Asset family | 最终处置 | 前置 |
|---|---|---|
| four runtime-state Blueprints | deleted | product Blueprint已中性化，Design trace已切换 |
| old example Java packages/tests | deleted | replacement ledger全部通过 |
| old example current docs | deleted | new application docs为current |
| old module contributor reports | deleted，Git保留provenance | current navigation已闭合 |
| phase-6 scenario script | replaced | two isolated app Gates + core contract Gate |
| old schema/hash/public/generated manifests | deleted | app/core manifests已登记 |
| benchmark domain imports | deleted | neutral schema + app integrated lanes |
| FJSP scale scripts/classes | retired | scheduler拥有独立同语义integrated evidence |
| current Map/Conformance/G5 wording | atomically replaced | Stage 5 candidate |
| historical root reports | retain as historical provenance | current index labels/links correct |
| active Temporary | delete | final Report + all Gates |

## 3. Stage acceptance

### Stage 0

- [x] starting commit recorded；
- [x] full topic protocol drafted；
- [x] active Temporary registration candidate prepared；
- [x] doc Gate passed；
- [x] full starting-point Gate passed；
- [x] Stage 0 committed as `169e1f6`。

### Stage 1

- [x] all current references inventoried；
- [x] every old evidence responsibility classified；
- [x] target Maven topology proven feasible；
- [x] both application designs complete；
- [x] config/generator/input/bootstrap/runtime boundaries complete；
- [x] exact deletion/provenance list complete；
- [x] no stop condition triggered；
- [x] Stage 1 scope non-regression review passed；
- [x] Stage 1 committed as `9ee6556`。

### Stage 2

- [x] standalone child projects and isolated consumer build；
- [x] benchmark neutral schema；
- [x] component lanes no longer use domain schema；
- [x] existing old scenario Gate still passes until replacement；
- [x] clean/repeat/source-shape evidence；
- [x] Stage 2 committed as `ccd1f30`。

### Stage 3

- [x] scheduler domain loop/validator/oracle；
- [x] versioned configs + detached problem generator + bootstrap；
- [x] mandatory industrial constraints；
- [x] SOMA access breadth；
- [x] isolated build；
- [x] long-run and multi-fork evidence；
- [x] Stage 3 committed as `7e50b29`。

Stage 3 的 canonical Gate 为 `check-industrial-scheduler.sh`：四个受版本控制的
profile 分别覆盖 6、192、8,000 和 10,000 个 operation；correctness 另有手算
oracle 与 lifecycle 负路径，default 由三个独立 JVM fork 记录 allocation、GC
和 runtime high-water，所有 artifact 均保持 `claimAllowed=false`。

### Stage 4

- [x] simulation systems/deterministic random/oracle；
- [x] versioned configs + detached initial-state generator + bootstrap；
- [x] SOMA access breadth；
- [x] physical-order independence；
- [x] isolated build；
- [x] long-run and multi-fork evidence；
- [x] Stage 4 committed as `a4b8a3e`。

Stage 4 的 canonical Gate 为 `check-grassing-simulation.sh`：四个受版本控制的
profile 覆盖 5/800/30,000/5,000 个初始 individual 与 12/500/300/2,000 ticks；
correctness 逐 tick 与独立 AoS 位级等价，default/large/long-run 均保持非零种群
和真实 birth/death churn。default 的三个独立 JVM fork 具有相同
input/result/schema/runtime-plan identity，记录 allocation、Young/Full GC、exact
index、scratch、capacity growth 和 runtime high-water，全部 artifact 保持
`claimAllowed=false`。

### Stage 5

- [x] replacement ledger complete；
- [x] top-level aggregator activated；
- [x] `soma-benchmarks` no domain-example dependency；
- [x] formal facts atomically switched；
- [x] old four scenarios and committed artifacts deleted；
- [x] reference closure and provenance audit clean；
- [x] G5 not weakened；
- [ ] Stage 5 immutable candidate commit。

### Stage 6

- [ ] all specialty Gates；
- [ ] full `./scripts/check.sh`；
- [ ] `git diff --check`；
- [ ] final scope non-regression；
- [ ] Governance Report；
- [ ] Temporary deleted and README no-active state；
- [ ] all changes committed and tree clean；
- [ ] G6 unchanged。

## 4. Anti-tail checklist

收口时必须通过 search/checker 证明：

- current Blueprint/Design/Conformance 不含旧四场景 Owner；
- new application Blueprint 不进入 SOMA Design trace；
- `soma-benchmarks` 不 import example domain；
- applications 不 import internal/testkit/other-example；
- applications 可在隔离 repository 独立构建；
- generators 不依赖 SOMA runtime，runtime loop 不反向调用 generator；
- correctness/default/large/long-run 配置受版本控制且同 seed input checksum 可重放；
- current docs 不含失效“四场景”“canonical FJSP”声明；
- old package/schema/table/lane/golden/script 没有 current 引用；
- historical Report 只保留当时事实；
- 新仿真不依赖 physical order；
- 新调度不是 toy scenario；
- 没有 `examples-common`、parallel canonical path 或隐藏 third-party；
- 没有 active Temporary；
- G6 status 未变化；
- Git working tree clean。
