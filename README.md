# soma_java

`soma_java` 是 SOMA Java-only 原型项目，目标是以 Java annotation schema、compile-time generation 和 Java columnar runtime 支撑高性能进程内 runtime state。

## 当前状态

项目仍处于正式设计阶段，尚未进入 Java 功能实现。当前设计事实从 [正式设计文档索引](docs/README.md) 进入；模块内部事实以各模块 `docs/` 为准。

## 模块

| Module | 设计入口 |
|---|---|
| `soma-annotations` | [docs](soma-annotations/docs/README.md) |
| `soma-processor` | [docs](soma-processor/docs/README.md) |
| `soma-runtime-core` | [docs](soma-runtime-core/docs/README.md) |
| `soma-testkit` | [docs](soma-testkit/docs/README.md) |
| `soma-examples` | [docs](soma-examples/docs/README.md) |
| `soma-benchmarks` | [docs](soma-benchmarks/docs/README.md) |

## 其他入口

- [文档治理规则](docs/documentation-governance.md)
- [正式报告](reports/README.md)
- 未来用户/开发者指南按需进入 `guides/`
- 文档检查：`./scripts/check-docs.sh`

README 只负责导航，不是设计事实源。
