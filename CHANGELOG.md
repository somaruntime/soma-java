# Changelog

本文件记录 SOMA Java 的用户可见变化。当前条目以 `1.0.0` 为 release candidate
坐标；selected private-source G6已经签署，但tag、GitHub Release、repository
visibility与publishing仍未获得授权，因此本条目保持`Unreleased`，不构成公开
发布声明。

## [Unreleased] — 1.0.0

### Added

- Java 8 annotation Schema、full-javac-8 `@SomaValue` lowering、deterministic
  annotation processing 与 schema/generated/runtime compatibility identity；
- schema-specific Metadata、Plan、Batch、Table、Point/Candidate/Column/Key/
  Bulk/Ownership access 与 typed mutation facade；
- primitive-backed、白名单 `String`、compiler-flattened `@SomaValue` 和
  parent-owned child 四类 V1 live storage；
- stable Key、current Index、Unique/Index exact access、packed columnar storage、
  ownership forest、`SomaGroup`、resource ledger 与 structured failure；
- typed Transformation、reusable DataFlow Template、one-shot Invocation、受控
  parallel、Eager Detached result 与同步 callback-scoped delivery；
- `soma-annotations`、`soma-processor`、`soma-runtime-core`、`soma-dataflow`
  四个 production artifact，以及 parent POM、source/javadoc/checksum/
  reproducibility 取证路径；
- 工业动态调度、个体生态仿真、实时派工规则三个彼此独立的 Java 8 reference
  consumer；它们不产出领域共享 JAR；
- component、application、runtime-scale、external consumer、package、
  SBOM/license/vulnerability 和 support-matrix Gate；
- canonical `use-soma-java` Agent Skill，用于 AI consumer 建模、generated API
  取证、能力路由、lifecycle/resource 检查与真实 Maven consumer 验证。

### Changed

- 首个正式版本从历史开发坐标 `0.2.0-SNAPSHOT` 收敛为 `1.0.0`；
- Pipeline 不再代表完整访问模型；V1 以 Point/Candidate/Column/Key/Bulk/
  Ownership 与 typed DataFlow 表达不同能力边界；
- 10M/100M 保留为非阻塞 research/stress，V1 required qualification 只对记录的
  Small、Medium、单/双 1M、String、Expansion、Delivery 与 Soak profile 作结论。

### Fixed

- 首个 V1 候选中发现的 schema validation、generated naming、locator/exact
  currentness、ownership cascade、failure atomicity、resource accounting、
  logical type、DataFlow identity 与 result delivery 问题均进入对应 executable
  regression evidence。

### Security

- Compiler source/resource injection、unsupported compiler fail-closed、capacity/
  domain/collision、ownership/lifecycle、resource budget、diagnostic privacy、
  dependency/SBOM/license/vulnerability 与 release provenance 进入 V1 Gate；
- AI Skill 为 instruction-only，不包含 bundled script、宽泛 `allowed-tools`、
  静默全局安装或未经确认的网络/Git/Shell 权限。

### Compatibility and limitations

- 唯一 compiler/runtime authority 是正式 support matrix 中记录的 Amazon
  Corretto full JDK 8；其他 vendor、JDK 9+、ECJ 或新 JDK `--release 8` 不属于
  当前支持范围；
- selected profile 仅为 private GitHub source；public GitHub 与 Maven/binary
  publishing 保持 `not-selected`；
- V1 不包含 Python、C ABI、native/off-heap runtime、任意 object storage、并发
  Table API、跨 Table transaction、持久化、SQL/query language、普通 Iterator、
  Publisher 或 async lazy result；
- 本机/CI evidence 不外推为 production SLA、公开性能承诺或未列环境支持。

### Rollback and withdrawal

- parent POM 与四个 production artifact 必须保持同一版本；回退时同步回退并重新
  生成 consumer generated source，不能混用不同 protocol 代际；
- schema/generated/runtime/plan identity mismatch 必须 fail closed；
- 已发布坐标不可覆盖；若发现错误 source/artifact 或安全问题，停止分发、保留
  审计记录并通过新的修复版本替代。
