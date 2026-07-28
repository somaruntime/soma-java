# SOMA Java

SOMA Java 是 Schema-Defined、Compiler-Specialized、JVM Heap-Resident 的
Java 8 高性能运行时状态计算库。它以 annotation schema、compile-time
generation、packed columnar runtime 和 typed DataFlow 支撑进程内状态访问、
变换与 safe-point effect。

## 当前状态

Java-only V1 的 annotation、compiler/processor、generated API、columnar
runtime、独立 reference applications 与 benchmark runner 已进入完整实施和
门禁验证。源码当前由 ArthurFeng 维护在 `somaruntime/soma-java` 私有仓库，
使用 Apache-2.0；尚未公开发布，也未发布到 Maven Central。正式 G6 profile
结论以前，不得把 snapshot artifact 称为 public release。

## 开发起步

本仓库使用 Azul Zulu 8.94.0.17 full JDK 8 和固定的 Maven Wrapper：

```text
./scripts/check-toolchain.sh
./scripts/check.sh
```

Codex Cloud Environment 使用
`./scripts/setup/setup-codex-cloud.sh`；完整 Linux/CI/Cloud 边界见
[GitHub 私有仓库与 Codex Cloud 开发](docs/engineering/github-and-cloud-development.md)。
本地 Maven consumer 配置见
[Java 8 安装与 consumer 指南](guides/java-v1-install-and-consumer-guide.md)。

目标与长期设计从 [文档入口](docs/README.md) 进入，当前实现由代码拥有并通过
[Implementation Map](docs/implementation-map/README.md) 导航。

## 模块

| Module | 当前导航 |
|---|---|
| `soma-annotations` | [docs](soma-annotations/docs/README.md) |
| `soma-processor` | [docs](soma-processor/docs/README.md) |
| `soma-runtime-core` | [docs](soma-runtime-core/docs/README.md) |
| `soma-dataflow` | [docs](soma-dataflow/docs/README.md) |
| `soma-examples` | [docs](soma-examples/docs/README.md) |
| `soma-benchmarks` | [docs](soma-benchmarks/docs/README.md) |

Compiler、golden 与独立 consumer 验证资产位于
[`tests/fixtures`](tests/fixtures)，不产出 Maven artifact。

## 其他入口

- [Blueprint](docs/blueprints/README.md)
- [Design](docs/design/README.md)
- [文档治理](docs/engineering/documentation-governance.md)
- [构建与验证](docs/engineering/build-and-validation.md)
- [贡献说明](CONTRIBUTING.md)
- [Java 8 安装与 Maven consumer 指南](guides/java-v1-install-and-consumer-guide.md)
- [Apache License 2.0](LICENSE)
- [Changelog](CHANGELOG.md)
- [正式报告](reports/README.md)
- [用户与开发者指南](guides/README.md)
- 项目检查：`./scripts/check.sh`
- reference applications：`./scripts/check-reference-applications.sh`
- benchmark smoke：`./scripts/check-benchmark-smoke.sh`
- release package smoke：`./scripts/package-smoke.sh`
- 单独 Maven reactor：`./mvnw -B -ntp verify`

README 只负责导航，不是设计事实源。
