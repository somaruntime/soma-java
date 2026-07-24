# Implementation Map 导航

类型：Implementation Map 入口

状态：正式

Owner：SOMA Java 实现导航

事实范围：按核对基线映射当前代码、配置、测试和执行入口

非事实范围：规范性设计、未来计划和实现正确性裁决

最近实现核对基线：commit `c0fa1c9`

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
继续沿用各自既有 immutable baseline。两个参考应用的架构起点分别为
`69e5dc6` / `287350d`，当前大规模 workload 与 scheduler 同语义内部优化由
`1af43ac` 核对；它们都不建立新的 SOMA 产品契约版本。六份应用 baseline、
profile-aware artifact 与 Fast/Scale/Soak/Full Gate 由 `938b3d5` 核对；这些
变化只扩展 application evidence，并保持 core 和应用领域语义不变。最终全量
重放发现的 JDK 8 私有生成构造器不稳定性由 `c0fa1c9` 核对；该修复只稳定
selector-less Table 的私有生成字节码，不改变 generated API、Schema 或 runtime
语义。
