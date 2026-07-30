![SOMA Java — Schema-Defined, High-Performance Runtime-State Computing for Java](assets/soma-banner.png)

# SOMA Java

SOMA Java 是面向 Java 8 的类型安全、高性能进程内运行时状态计算库。应用声明稳定的
Schema 和访问方式，SOMA 在编译期生成领域化 API，并在运行期使用 packed columnar
storage、精确访问、typed DataFlow 和显式资源边界处理大量、频繁变化的状态。

SOMA 适合状态结构稳定、读写频繁、需要持续筛选、聚合、关联或更新的 JVM 应用。
它不是 ORM、SQL/DataFrame、通用 Collection、工作流引擎或分布式计算平台。

## 当前获取边界

当前 V1 坐标为 `1.0.0`，发布渠道仍是受控的 private GitHub source repository。
尚未创建 `v1.0.0` tag、GitHub Release，也未发布到 Maven Central；当前资料和本机
验证不能外推为 public release、production readiness、跨环境性能或公开 SLA。

## 最快开始

SOMA 当前唯一 compiler/runtime validation authority 是 Amazon Corretto
8.502.07.1 full JDK 8。取得仓库访问权后：

```sh
brew install --cask corretto@8
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
./scripts/check-toolchain.sh
./mvnw -B -ntp install
```

随后按照 [Java 8 Maven consumer 指南](docs/getting-started/java-v1-install-and-consumer-guide.md)
在普通项目中声明 Schema、生成 API 并完成第一条查询和更新。完整的 Word 版路径见
[SOMA Java V1 应用开发者手册](docs/guides/soma-java-v1-application-developer-manual.docx)。

## 工作方式

```text
Java annotation schema
  -> javac 8 plugin + annotation processor
  -> schema-specific Metadata / Table / Access / DataFlow API
  -> packed columnar runtime state
  -> point / candidate / column / key / bulk / ownership access
  -> detached result 或受限 callback-scoped delivery
```

应用继续拥有业务规则、event loop、I/O、跨表提交和恢复。SOMA 只拥有 Schema
定义的运行时状态平面和封闭计算能力。

## 选择入口

| 我想要…… | 入口 |
|---|---|
| 安装并开始使用 | [Getting Started](docs/getting-started/README.md) |
| 学习建模、生命周期、DataFlow 和排障 | [应用开发指南](docs/guides/README.md) |
| 评估架构、性能适用性和限制 | [架构与技术选型](docs/architecture/README.md) |
| 查看可执行参考应用 | [Examples](docs/examples/README.md) |
| 获得支持或报告安全问题 | [SUPPORT](SUPPORT.md)、[SECURITY](SECURITY.md) |
| 参与开发 | [CONTRIBUTING](CONTRIBUTING.md) |
| 维护、治理或发布项目 | [项目事实入口](project/README.md) |

全部产品文档从 [docs/README.md](docs/README.md) 进入。内部设计、实现地图、
Conformance、过程和证据统一位于 `project/`，普通使用者不需要先理解它们。

## 参考应用

- [工业动态调度引擎](soma-examples/industrial-dynamic-scheduler/README.md)；
- [个体生态仿真](soma-examples/grassing-individual-simulation/README.md)；
- [实时派工规则引擎](soma-examples/real-time-dispatch-rule-engine/README.md)。

三个应用都是彼此独立的普通 Java 8 consumer，不共享领域 runtime，也不拥有 SOMA
产品语义。

## AI coding tools

仓库提供 project-scoped
[`use-soma-java` Agent Skill](.agents/skills/use-soma-java/SKILL.md)，用于 Maven
接入、Schema 建模、generated API 取证、Access/DataFlow 路由以及 lifecycle 和
resource 检查。它是 instruction-only，不执行 bundled script；安装、升级和卸载
边界见 [Consumer Guide](docs/getting-started/java-v1-install-and-consumer-guide.md#3-ai-coding-tools-skill)。
Skill 与 consumer source 必须绑定正式 release tag；tag 不可用时只接受 Owner
提供的 immutable commit SHA。

## 许可与品牌

代码与文档使用 [Apache License 2.0](LICENSE)，Copyright 2026 ArthurFeng。
SOMA 名称、Logo 和 Banner 的品牌权利由 ArthurFeng 保留；详见
[品牌资产说明](assets/README.md)和 [NOTICE](NOTICE)。
