# soma_java

`soma_java` 是 SOMA Java-only 项目，以 Java annotation schema、compile-time generation 和 Java columnar runtime 支撑高性能进程内 runtime state。

## 当前状态

Java-only V1 的 annotation、compiler/processor、generated API、columnar runtime、formal scenarios 与 benchmark runner 已进入完整实施和门禁验证。仓库尚未达到 public release readiness；正式 G6 report通过前不得把本地artifact称为公开release。目标与长期设计从 [文档入口](docs/README.md) 进入，当前实现由代码拥有并通过 [Implementation Map](docs/implementation-map/README.md) 导航。

## 模块

| Module | 当前导航 |
|---|---|
| `soma-annotations` | [docs](soma-annotations/docs/README.md) |
| `soma-processor` | [docs](soma-processor/docs/README.md) |
| `soma-runtime-core` | [docs](soma-runtime-core/docs/README.md) |
| `soma-testkit` | [docs](soma-testkit/docs/README.md) |
| `soma-examples` | [docs](soma-examples/docs/README.md) |
| `soma-benchmarks` | [docs](soma-benchmarks/docs/README.md) |

## 其他入口

- [Blueprint](docs/blueprints/README.md)
- [Design](docs/design/README.md)
- [文档治理](docs/engineering/documentation-governance.md)
- [构建与验证](docs/engineering/build-and-validation.md)
- [贡献说明](CONTRIBUTING.md)
- [Java 8安装与Maven consumer指南](guides/java-v1-install-and-consumer-guide.md)
- [Apache License 2.0](LICENSE)
- [Changelog](CHANGELOG.md)
- [正式报告](reports/README.md)
- [用户与开发者指南](guides/README.md)
- 项目检查：`./scripts/check.sh`
- examples验证：`./scripts/check-examples-phase6.sh`
- benchmark smoke：`./scripts/check-benchmark-smoke.sh`
- release package smoke：`./scripts/package-smoke.sh`
- 单独 Maven reactor：`./mvnw -B -ntp verify`

README 只负责导航，不是设计事实源。
