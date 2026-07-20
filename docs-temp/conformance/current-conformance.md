# 当前一致性基线

类型：Conformance

状态：候选

Owner：SOMA Java 一致性审查

核对对象：候选 Blueprint/Design、commit `4b6fa43` 的代码与当前正式 evidence

事实范围：主要设计能力的一致性判断和直接依据

非事实范围：授权修复、重新定义 Design 或声明 public release readiness

最后审查日期：2026-07-20

## 1. 判定口径

- **一致且 evidenced**：代码形态与候选 Design 一致，并有对应 Gate/consumer evidence；
- **一致但 evidence 有限**：没有发现设计偏差，但结论只在当前测量/环境成立；
- **目标差距**：Blueprint 目标形态尚未完整投影到当前示例；不等同于 core Design 失败；
- **blocked**：明确目标尚缺必需外部事实或 evidence；
- **候选未启用**：本目录已形成候选内容，但尚未成为正式体系。

## 2. 能力矩阵

| 关注点 | 当前判定 | 依据 | 处置 |
|---|---|---|---|
| Java 8 annotation/schema/compiler | 一致且 evidenced | processor/plugin、compile fixtures；G1 passed | 保持 |
| deterministic normalization/hash | 一致且 evidenced | schema JSON/hash golden、Unicode fixture | 保持 |
| schema-specific generated API | 一致且 evidenced | external Maven consumers、public `javap` golden；G2/G4 passed | 保持 |
| packed keyed/dense storage | 一致且 evidenced | generated/runtime checks；G3 passed | 保持 |
| primary identity 与 exact access | 一致且 evidenced | V3 Hash KeySpace、GroupedExactIndex、access fixtures；packed exact cutover passed | 保持 |
| swap-remove 与 IndexBuffer pipeline | 一致且 evidenced | generated access/remove tests、benchmark smoke | 保持 |
| Index / IndexSnapshot caller-responsibility | 一致且 evidenced | detached `IndexSnapshot`、optional `requireCurrent`、wrong/stale consumer tests；正式 Owner 已明确非 stable identity/row snapshot | 保持 raw detached API，不增加强制 hot-path guard |
| child ownership/lifecycle | 一致且 evidenced | child external consumer、ownership/materialization Gate | 保持 |
| structured failure/plan/stats | 一致且 evidenced | runtime diagnostics、compatibility/error fixtures | 保持 |
| detached materialization/budget | 一致且 evidenced | child/materialization fixtures、testkit comparator | 保持 |
| hot-path performance shape | 一致但 evidence 有限 | allocation/GC Gate、benchmark artifacts、2026-07-17 local diagnostic | 结论限制在已测环境 |
| FJSP 目标场景 | 部分一致 | 当前 solver 已有 split schema、incremental frontier、by-machine exact access 和 explicit dispatch sort；machine selection 仍按 table 动态排序 | external heap 仍是目标选择，见 [known-gaps](known-gaps.md) |
| VRP / Simulation / Game 目标形态 | 目标差距 | 当前 examples 仍保留蓝图拟拆分的混合 data-role row/cache | 见 [known-gaps](known-gaps.md) |
| G0–G5 功能与 package Gate | passed | 当前 [报告入口](../../reports/README.md) | 保持 evidence 可重放 |
| G6 public release evidence | blocked | SCM/ownership/signing/publishing/support matrix 等真实事实不足 | 不影响本次文档候选建设，不得误报 release ready |
| 新文档体系 | 候选未启用 | `docs-temp/` 未被当前入口采用，也未执行原子切换 | 继续专题验证 |

## 3. 当前结论

候选 Design 对 core compiler/runtime 的描述与 `4b6fa43` 当前实现基本一致，没有发现需要立即修改代码的 blocking Design deviation。主要未闭合项属于三类：

1. 四个场景 Blueprint 仍有不同程度的目标差距，其中 FJSP 只剩 machine selection 结构选择，其他三个主要是数据角色拆分；
2. 性能结论仍受测量环境与 lane 范围约束；
3. G6 和新文档体系切换分别因外部发布事实与候选治理流程未完成而保持未收口。

本结论不扩大当前任务授权。本任务只建设 `docs-temp/`，不修改示例、runtime、正式 Design 或 release 状态。

## 4. Evidence 入口

- [当前 G0–G6 状态](../../reports/java-v1-goal-execution-status.md)
- [Packed Index / Exact Access / IndexBuffer 收口](../../reports/2026-07-17-packed-exact-index-runtime-redesign-report.md)
- [G5 examples/benchmark Gate](../../reports/java-v1-g5-examples-benchmark-gate-report.md)
- [性能优化后本机诊断](../../reports/2026-07-17-post-optimization-g6-diagnostic-report.md)
