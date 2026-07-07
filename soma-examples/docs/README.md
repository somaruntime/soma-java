# soma-examples 正式设计文档

本目录保存 `soma-examples` 的正式设计文档。

## 当前正式设计文档

- [Runtime state schema 典型示例](runtime-state-schema-examples.md)：总览入口、通用建模规则和覆盖矩阵。
- [FJSP runtime state 示例与 E2E 场景契约](fjsp-runtime-state-example.md)：FJSP schema、`MachineCandidate` frontier、dispatch flow 和 G5 E2E 契约。
- [VRP 构造解 runtime state 示例](vrp-runtime-state-example.md)：VRP 构造解 runtime state schema。
- [连续仿真 runtime state 示例](simulation-runtime-state-example.md)：time-step simulation runtime state schema。
- [Game runtime state 示例](game-runtime-state-example.md)：game loop runtime state schema。

## 历史入口

- [FJSP E2E 场景契约](fjsp-e2e-scenario.md)：已合并到 FJSP 独立场景文档，仅保留迁移说明。

## 职责边界

`soma-examples` 拥有 Java 8 usage examples 和 end-to-end smoke scenarios。示例文档可以覆盖、验证并解释 annotation schema、generated API 和 runtime core 的使用方式，但不拥有这些契约本身；如有冲突，以对应 owner 文档为准。

## 临时设计目录

临时设计草案只能放在 `docs/temp/`。草案被接受后，必须把稳定事实迁移进正式设计文档。
