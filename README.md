# soma_java

`soma_java` 是 SOMA Java-only 原型项目，目标是以 Java annotation schema、compile-time generation 和 Java columnar runtime 支撑高性能进程内 runtime state。

## 当前状态

项目已经完成 implementation-readiness 文档与 build 治理，仍未进入 Java 功能实现，也未达到 public release readiness。当前设计事实从 [正式设计文档索引](docs/README.md) 进入；模块内部事实以各模块 `docs/` 为准。

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
- [Build 与依赖契约](docs/build-and-dependency-contract.md)
- [贡献说明](CONTRIBUTING.md)
- [正式报告](reports/README.md)
- 未来用户/开发者指南按需进入 `guides/`
- 项目检查：`./scripts/check.sh`
- 单独 Maven reactor：`./mvnw -B -ntp verify`

README 只负责导航，不是设计事实源。
