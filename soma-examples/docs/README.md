# soma-examples 可执行场景输出

类型：Report / 开发者文档入口

状态：当前

Owner：SOMA Java example output

事实范围：当前 executable example 的导航、适用实现基线和目标差距边界

非事实范围：核心 Design、公共 API、benchmark 结果和 release claim

适用版本：最后 implementation-affecting baseline `b991f4c`

最后审查日期：2026-07-20

本目录描述当前代码实际提供的 Java 8 scenario，不拥有 SOMA 设计。目标使用形态从 [Blueprint](../../docs/blueprints/README.md) 进入，长期语义从 [Design](../../docs/design/README.md) 进入，当前代码从[场景与 benchmark Map](../../docs/implementation-map/scenario-and-benchmark-map.md)进入；目标与示例的差距由 [Conformance](../../docs/conformance/known-gaps.md)记录。

## 当前可执行场景

- [Runtime-state scenarios 总览](runtime-state-schema-examples.md)
- [FJSP runtime state](fjsp-runtime-state-example.md)
- [FJSP E2E](fjsp-e2e-scenario.md)
- [VRP runtime state](vrp-runtime-state-example.md)
- [连续仿真 runtime state](simulation-runtime-state-example.md)
- [Game runtime state](game-runtime-state-example.md)

FJSP 当前实现与目标形态基本一致；VRP、Simulation 和 Game 仍有已裁决的 Blueprint→Code 差距。本文档分类不会把这些差距改写为已完成。
