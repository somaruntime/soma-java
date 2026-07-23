# soma-examples 可执行场景输出

类型：Report / 开发者文档入口

状态：当前

Owner：SOMA Java example output

事实范围：当前 executable example 的导航、适用实现基线和目标差距边界

非事实范围：核心 Design、公共 API、benchmark 结果和 release claim

适用版本：最后 implementation-affecting baseline `fd82eba`

最后审查日期：2026-07-23

本目录描述当前代码实际提供的 Java 8 scenario，不拥有 SOMA 设计。目标使用形态从 [Blueprint](../../docs/blueprints/README.md) 进入，长期语义从 [Design](../../docs/design/README.md) 进入，当前代码从[场景与 benchmark Map](../../docs/implementation-map/scenario-and-benchmark-map.md)进入；目标与示例的差距由 [Conformance](../../docs/conformance/known-gaps.md)记录。

## 当前可执行场景

- [Runtime-state scenarios 总览](runtime-state-schema-examples.md)
- [FJSP runtime state](fjsp-runtime-state-example.md)
- [FJSP E2E](fjsp-e2e-scenario.md)
- [VRP runtime state](vrp-runtime-state-example.md)
- [连续仿真 runtime state](simulation-runtime-state-example.md)
- [Game runtime state](game-runtime-state-example.md)

四个 current executable scenario 已采用对应 Blueprint 的 canonical data role、identity、ordering、Access Model 和 failure boundary；这一结论由 `fd82eba` 的 source、phase-6 fixtures、external consumers 与 scenario Gate共同支持。后续目标变化仍先进入 Blueprint/Design，并由 Conformance 重新判断实现差距。
