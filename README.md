# SOMA Java

SOMA Java 是 Schema-Defined、Compiler-Specialized、JVM Heap-Resident 的
Java 8 高性能运行时状态计算库。它以 annotation schema、compile-time
generation、packed columnar runtime 和 typed DataFlow 支撑进程内状态访问、
变换与 safe-point effect。

## 当前状态

Java-only V1 的 annotation、compiler/processor、generated API、columnar
runtime、独立 reference applications 与 benchmark runner 已进入完整实施和
门禁验证。源码当前由 ArthurFeng 维护在 `somaruntime/soma-java` 私有仓库，
使用 Apache-2.0。Amazon Corretto 8本机G0–G4与component/application evidence
已通过；G5等待runtime-scale重验，selected private-source G6等待Corretto Linux
和release qualification。仓库尚未公开，也未发布到Maven Central，snapshot
artifact不能被称为public release。

## 开发起步

本仓库使用 Amazon Corretto 8.502.07.1 full JDK 8 和固定的 Maven Wrapper。
macOS 推荐通过 Homebrew 安装，便于跟踪安全更新：

```text
brew install --cask corretto@8
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
./scripts/check-toolchain.sh
./scripts/check.sh fast
```

跨模块、PR候选和专题收口使用默认的完整`./scripts/check.sh`；release、安全、
规模与重型性能qualification保持独立人工触发。

GitHub 私有仓库、Linux CI 与未形成就绪结论的 Codex Cloud 实验边界见
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
