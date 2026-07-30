# SOMA Java 精简源码包

这是 SOMA Java 经过 allowlist 筛选并验证可构建的源码交付物。它包含 Maven
reactor、四个 production module、三个可执行 reference consumer、领域中性
benchmark module、Maven Wrapper、LICENSE、NOTICE、CHANGELOG 和 exact toolchain
check。

本包有意排除内部项目设计与治理事实、qualification Report、repository fixture、
CI 配置、原始 evidence、本机文件和 credential。这些材料仍在经过身份验证的
`somaruntime/soma-java` repository 中按同一 commit 保存。

依赖正式 Design Owner 的 `use-soma-java` Agent Skill 也不在本精简包内；需要
该 Skill 时，必须从完整 authenticated repository 的同一 immutable commit 获取，
不能从 archive 中拼装不完整副本。

V1 唯一支持的 compiler/runtime authority 是 Amazon Corretto 8.502.07.1 full
JDK 8。选择该 JDK 后运行：

```sh
./scripts/check-toolchain.sh
./mvnw -B -ntp install
```

本 archive 的 checksum 和 source commit 与它一同交付；qualification evidence
还记录从解包内容自身执行 `./mvnw -B -ntp verify` 的成功结果。

该 private-source artifact 不表示 public GitHub release、Maven Central
publication、production readiness、跨平台性能声明或公开 SLA。
