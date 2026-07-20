# soma-examples

Java 8 教学示例模块。第一职责是用可运行、可阅读的算法流程展示 SOMA
如何承载 hot runtime state；E2E smoke 与 gate evidence 是第二职责，并与教学主流程隔离。

FJSP 建议直接从 `FjspScenario` 开始：它只展示 `FjspProblem -> FjspInstance ->
FjspSolver -> FjspSolveResult`。SOMA schema 集中在 `fjsp.schema`；错误、lifecycle 和
determinism 断言位于 test source。性能规模与计时由 `soma-benchmarks` 拥有，不在教学
入口中混入 stopwatch 或 gate 断言。

目标场景：[Blueprint](../docs/blueprints/README.md)

当前可执行场景：[soma-examples/docs/README.md](docs/README.md)

正式报告：[soma-examples/reports/README.md](reports/README.md)

模块 README 只负责导航。
