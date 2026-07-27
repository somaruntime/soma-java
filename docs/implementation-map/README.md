# Implementation Map 导航

类型：Implementation Map 入口

状态：正式

Owner：SOMA Java 实现导航

事实范围：按核对基线映射当前代码、配置、测试和执行入口

非事实范围：规范性设计、未来计划和实现正确性裁决

最近实现核对基线：commit `3c8d425`

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

“实现核对基线”是最后一次影响对应 surface 的 immutable commit；它可以早于后续
docs/governance-only commit。当前 `3c8d425` 在 S1 schema-scoped Descriptor
Metadata、four-kind classifier、typed String value/access 与 v6/v2 protocol
之上，继续完成 S2 schema-seeded Plan Builder、immutable Effective Metadata、
String resource profile、hard maximum rows 与 runtime plan v4 clean cutover；
更早的 aggregate fault、runtime hot-path 和 DataFlow 历史仍由正式治理 Report
追踪，不在各地图重复完整提交链。
