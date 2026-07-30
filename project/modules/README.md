# 模块事实导航

类型：Module Entry

状态：正式

Owner：SOMA Java 模块导航

事实范围：当前模块职责、正式 Design、实现地图和验证入口

非事实范围：复制跨模块 Design、public surface 或代码清单

最后审查日期：2026-07-30

| 模块 | 职责与实现入口 |
|---|---|
| [`soma-annotations`](soma-annotations/README.md) | public schema annotations |
| [`soma-processor`](soma-processor/README.md) | javac 8 integration、processing、normalization 与 code generation |
| [`soma-runtime-core`](soma-runtime-core/README.md) | Table、storage、access、lifecycle、runtime plan 与 errors |
| [`soma-dataflow`](soma-dataflow/README.md) | typed Transformation/DataFlow、execution、result 与 controlled effect |
| [`soma-examples`](soma-examples/README.md) | 三个独立 Java 8 reference consumers |
| [`soma-benchmarks`](soma-benchmarks/README.md) | 领域中性 component benchmark 与 runtime-scale lanes |

完整 Maven reactor、依赖方向和 artifact 角色见
[项目与模块地图](../implementation-map/project-and-module-map.md)。
