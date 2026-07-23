# 引用闭包清单

类型：Temporary

状态：active

Owner：SOMA 旧场景引用闭包

事实范围：旧四场景资产的 current/historical 分类、最终处置和替代 Owner

非事实范围：历史报告内容改写、SOMA Design 变更或尚未通过的最终 Gate

最后审查日期：2026-07-23

## 1. Formal current

| 资产 | 最终处置 |
|---|---|
| `docs/blueprints/{fjsp,vrp,simulation,game}-runtime-state-blueprint.md` | 删除；应用 Blueprint 在 child docs 自有 |
| `docs/blueprints/README.md` | 只导航 SOMA 产品 Blueprint |
| `soma-java-product-blueprint.md` | 将 FJSP 示例代码改为领域中性的 Access Model journey；参考应用只作非规范性导航 |
| `docs/design/README.md` | 删除四场景反向追踪，只保留产品 Blueprint → Design |
| `materialization-boundary.md` | 将 Game 名称替换为中性 aggregate 示例，不改变规则 |
| Implementation Map | 改为 aggregator、两个 consumer、neutral/app benchmark owners |
| Conformance | G5/current evidence 改为 public artifact + 两个 app；不改变 G6 |
| Validation Gate | G5 从 canonical scenarios 改为 Access Model + external/reference consumers |

## 2. Current code/evidence

| 资产 | 数量/入口 | 最终处置 |
|---|---|---|
| old domain source | 82 Java / 3,991 lines | replacement通过后删除 |
| suite/test | `ScenarioSuite` + `FjspVerificationSuite` | 由两个 app verification + core Gate替换 |
| fixtures | 8 schema/hash + 3 manifests | 由 core manifest + 两个 app manifest替换 |
| generated surface | 222 types / 782 classes | 不是 SOMA public compatibility；替换后删除 golden |
| examples docs | 6 current Reports + module README | child各自拥有 Blueprint/Design/validation/README |
| examples reports | 2 dated module reports | 无独立 current Owner；Git保留 provenance后删除 |
| example Gate | `check-examples-phase6.sh` | 替换为 reference-app isolation/G5 Gate |
| benchmark imports | 7 Java owners + POM dependency | component中性化，integrated迁入应用，dependency删除 |
| FJSP allocation scripts | check/run + 5 runner classes | scheduler benchmark接管后删除 |
| code-size checker | old generated path/count | 改为 core + neutral benchmark + 两 app分项统计 |

## 3. Reports

处理原则是“不改写历史事实，只撤销 current Owner”：

- `2026-07-21-four-scenario-blueprint-adoption-report.md` 转为历史治理证据并从 current G5 Owner 退出；
- old module reports删除，Git commit/path即 provenance；
- Access Model、packed/exact、complexity reports继续保留其产品治理结论，文中旧场景只作为当时 evidence，不作为 current导航；
- `current-performance-summary.md` 和 `java-v1-goal-execution-status.md` 必须更新到 neutral component + 两个应用；
- `reports/README.md` 的 current G5、性能与治理导航原子切换；
- archive下旧 FJSP/四 Blueprint报告不修改。

## 4. Checker/scripts

最终需要修改：

- `check-docs.sh`：移除六个 old example current Report硬编码，增加 child docs metadata/owner边界和产品Blueprint唯一性；
- `check-examples-phase6.sh`：删除并由 `check-reference-applications.sh` 取代；
- `check-benchmark-smoke.sh`：classpath移除 examples，source-shape禁止领域import；
- `check-post-cutover-components.sh`：neutral generated schema；
- `check-fjsp-allocation-gc.sh`、`run-fjsp-100k-benchmark.sh`：由 scheduler evidence脚本取代；
- `check-scan-code-size.sh`：拆分 core/neutral/app generated footprint；
- `check.sh`：按 core → neutral benchmark → two isolated apps 编排。

所有脚本改名或删除都必须关闭 `README/Map/Report` 引用，不能留下“命令存在但不在 Gate”或“Gate 调用不存在命令”。

## 5. 历史与 accidental files

- dated/archive Report 允许保留旧术语和路径，因为它们记录当时事实；
- target evidence不提交，也不成为 replacement Owner；
- 当前发现的 `.DS_Store` 均 untracked，不纳入专题提交；checker继续防止其被登记为事实；
- 删除 old source后，Git history是最终 provenance，不创建墓碑源码或 `legacy` package。

## 6. Closure query

Stage 5 不能只搜索 `FJSP|VRP|Game|Simulation`，因为新调度和仿真仍会合法使用领域词。应搜索精确旧 identity：

```text
com.hgtech.soma.examples.fjsp
com.hgtech.soma.examples.vrp
com.hgtech.soma.examples.simulation
com.hgtech.soma.examples.game
ScenarioSuite
check-examples-phase6
fjsp-100k
four-scenario / 四场景 current claim
```

命中必须分类为 deleted、historical provenance 或 current defect；不能用宽泛 allowlist 隐藏新残留。
