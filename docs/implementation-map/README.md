# Implementation Map 导航

类型：Implementation Map 入口

状态：正式

Owner：SOMA Java 实现导航

事实范围：按核对基线映射当前代码、配置、测试和执行入口

非事实范围：规范性设计、未来计划和实现正确性裁决

最近实现核对基线：2026-07-28 runtime-scale working-tree candidate（base
`6cde5d5`；production/evidence source
`content-sha256:dfe8fa98b2a411708359a378e05f22e2ad89a7b900c70d1f71e8dd1a6b7f8e69`）

最后审查日期：2026-07-28

Implementation Map 是当前代码的简短投影。代码变化后，以代码为当前事实并更新这里；不得为保持本地图“正确”而扭曲实现。

- [项目与模块地图](project-and-module-map.md)
- [编译器与代码生成地图](compiler-and-codegen-map.md)
- [Runtime Core 地图](runtime-core-map.md)
- [DataFlow 实现地图](dataflow-map.md)
- [可执行契约地图](executable-contract-map.md)
- [测试与 evidence 地图](test-and-evidence-map.md)
- [参考应用与 benchmark 地图](scenario-and-benchmark-map.md)

普通类、字段和方法清单由代码搜索获得，不在这里维护第二份完整目录。

“实现核对基线”通常是最后一次影响对应 surface 的 immutable commit；working-tree
candidate 必须显式标注 base 和内容身份，不能冒充 commit。当前 candidate 已完成
Metadata/Group/String、受限 storage/locator/Candidate/relation、bounded morsel
scheduler、Invocation resource ledger、Eager + callback-scoped delivery、
runtime observation、三个 Example Group ownership 和 runtime-scale qualification。
精确 executable identity 与 Gate 结果由各地图及
[综合治理报告](../../reports/2026-07-28-runtime-boundary-group-scale-readiness-governance-report.md)
追踪；更早 slice 提交链不在地图中重复。
