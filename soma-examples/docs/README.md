# soma-examples 正式设计文档

本目录保存 Java 8 formal usage scenarios。Scenario 的第一职责是教学：让读者从简洁的
算法 loop 看见 SOMA schema、generated table、索引、Row Pipeline、ColumnView 和
detached export 如何协作。Access Pattern Card、data role 和 E2E evidence boundary 是
第二职责；错误矩阵、lifecycle/golden 和 gate 断言必须进入独立 verification 入口，不能
淹没教学主流程。Scenario 不拥有 annotation、API、runtime contract 或 benchmark 结果。

FJSP production source 以 application package + 单一 `fjsp.schema` package 组织；V1
processor 的 schema package ownership 不允许把 value/table 声明拆到两个 Java package。
Verification 位于 `src/test/java`，100k synthetic input 和计时位于 `soma-benchmarks`。

## 正式设计文档

| 文档 | Owner | 单一职责 |
|---|---|---|
| [Runtime-state scenarios 总览](runtime-state-schema-examples.md) | `soma-examples` | 通用建模规则、场景索引和覆盖矩阵 |
| [FJSP runtime state schema 示例](fjsp-runtime-state-example.md) | `soma-examples` | FJSP data role、Access Pattern Card 和 schema |
| [FJSP E2E 场景契约](fjsp-e2e-scenario.md) | `soma-examples` | release/dispatch/commit/error/G5 flow |
| [VRP runtime state 示例](vrp-runtime-state-example.md) | `soma-examples` | VRP schema 与 access pattern |
| [连续仿真 runtime state 示例](simulation-runtime-state-example.md) | `soma-examples` | Simulation schema 与 access pattern |
| [Game runtime state 示例](game-runtime-state-example.md) | `soma-examples` | Game schema 与 access pattern |

完整 API/schema/runtime 语义必须引用对应 owner；示例冲突时修改示例，不修改 owner contract。

临时专题进入 `docs/temp/`，不得成为正式事实源。
