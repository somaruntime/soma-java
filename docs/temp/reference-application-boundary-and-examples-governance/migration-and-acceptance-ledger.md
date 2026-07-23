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
| primary key / unique / exact access | FJSP/VRP/Game schema + external fixtures | core external fixtures + scheduler | core Gate + scheduler isolated run | pending |
| Candidate exact group update/filter/sort/best/remove | FJSP frontier | core access fixture + scheduler | differential/invariant + application run | pending |
| packed dense scan/update | Simulation/Game | core access fixture + simulation | AoS oracle + long-run | pending |
| ColumnView hot access | scenario source-shape + core fixture | core fixture + simulation | isolated app + source/import check | pending |
| owner child | FJSP/VRP | core child fixture + scheduler | child lifecycle/replacement + app import | pending |
| Batch add/replace/clear | all scenarios | core fixture + both apps | build/run/oracle | pending |
| Index/IndexSnapshot invalidation | FJSP verification | core access fixture + both apps | stale/wrong-source + order-independence | pending |
| swap-remove relocation | FJSP/Game | core runtime fixture + both apps | removal/checksum/tie-break | pending |
| materialization/budget | scenario result/export | core fixture + app final output | budget/graph/result validation | pending |
| failure/lifecycle | FJSP suite + scenario protocols | core invariant + both aggregate owners | negative/fail-stop/release tests | pending |
| exact-index component cost | FJSP schema benchmark | neutral benchmark schema | multi-fork artifact | pending |
| dense replace/sort component cost | VRP schema benchmark | neutral benchmark schema | multi-fork artifact | pending |
| integrated allocation/GC | FJSP 100k | scheduler + simulation own lanes | semantics-specific multi-fork artifact | pending |
| generated breadth/schema/hash | four scenario manifest | core external breadth + two app manifests | clean/repeat generated evidence | pending |
| Java 8 executable consumer | ScenarioSuite | two isolated applications | separate Maven build/class major | pending |
| workload/initial-state construction | scenario code inline fixtures | versioned app config + detached generator | deterministic input checksum + bootstrap equivalence | pending |
| FJSP/VRP/Game business algorithms | old example code/docs | retire | domain-only classification | pending |
| old simulation business model | old example | retire/replaced by new independent model | no current owner/reference | pending |

## 2. Asset closure ledger

| Asset family | 最终处置 | 前置 |
|---|---|---|
| four runtime-state Blueprints | delete | product Blueprint中性化、Design trace切换 |
| old example Java packages/tests | delete | replacement ledger全部通过 |
| old example current docs | delete | new application docs current |
| old module contributor reports | archive/provenance or delete if duplicate | current navigation closure |
| phase-6 scenario script | replace | two isolated app Gates + core contract Gate |
| old schema/hash/public/generated manifests | delete | app/core manifests registered |
| benchmark domain imports | delete | neutral schema + app integrated lanes |
| FJSP scale scripts/classes | move/rewrite under scheduler or retire | same-semantics evidence decision |
| current Map/Conformance/G5 wording | atomic replace | Stage 5 |
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
- [ ] Stage 1 committed。

### Stage 2

- [ ] standalone child projects and isolated consumer build；
- [ ] benchmark neutral schema；
- [ ] component lanes no longer use domain schema；
- [ ] existing old scenario Gate still passes until replacement；
- [ ] clean/repeat/source-shape evidence；
- [ ] Stage 2 commit。

### Stage 3

- [ ] scheduler domain loop/validator/oracle；
- [ ] versioned configs + detached problem generator + bootstrap；
- [ ] mandatory industrial constraints；
- [ ] SOMA access breadth；
- [ ] isolated build；
- [ ] long-run and multi-fork evidence；
- [ ] Stage 3 commit。

### Stage 4

- [ ] simulation systems/deterministic random/oracle；
- [ ] versioned configs + detached initial-state generator + bootstrap；
- [ ] SOMA access breadth；
- [ ] physical-order independence；
- [ ] isolated build；
- [ ] long-run and multi-fork evidence；
- [ ] Stage 4 commit。

### Stage 5

- [ ] replacement ledger complete；
- [ ] top-level aggregator activated；
- [ ] `soma-benchmarks` no domain-example dependency；
- [ ] formal facts atomically switched；
- [ ] old four scenarios and committed artifacts deleted；
- [ ] reference closure and provenance audit clean；
- [ ] G5 not weakened；
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
