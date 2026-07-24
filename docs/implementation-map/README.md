# Implementation Map 导航

类型：Implementation Map 入口

状态：正式

Owner：SOMA Java 实现导航

事实范围：按核对基线映射当前代码、配置、测试和执行入口

非事实范围：规范性设计、未来计划和实现正确性裁决

最近实现核对基线：commit `5be618a`

最后审查日期：2026-07-24

Implementation Map 是当前代码的简短投影。代码变化后，以代码为当前事实并更新这里；不得为保持本地图“正确”而扭曲实现。

- [项目与模块地图](project-and-module-map.md)
- [编译器与代码生成地图](compiler-and-codegen-map.md)
- [Runtime Core 地图](runtime-core-map.md)
- [可执行契约地图](executable-contract-map.md)
- [测试与 evidence 地图](test-and-evidence-map.md)
- [参考应用与 benchmark 地图](scenario-and-benchmark-map.md)

普通类、字段和方法清单由代码搜索获得，不在这里维护第二份完整目录。

“实现核对基线”是最后一次影响对应 surface 的 immutable commit；它可以早于后续
docs/governance-only commit。Compiler/codegen、Access Model 和 core runtime
继续沿用各自既有 immutable baseline；工业调度参考应用由 `69e5dc6` 核对，个体
生态仿真参考应用由 `287350d` 核对。两次应用架构治理都不建立新的 SOMA 产品
契约版本。三层性能 baseline/comparator/Gate 的当前实现由 `5be618a` 核对；它只
改变 evidence tooling 和工程 Gate，不改变 core/application production 语义。
