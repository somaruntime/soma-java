# Changelog

本文件记录 SOMA Java 的用户可见变化。当前条目保持 `Unreleased`，直到 G0–G6 全部通过且 release 操作得到明确授权。

## [Unreleased] — Java-only SOMA V1.0

### Added

- Java 8 annotation schema、full-javac-8 `@SomaValue` lowering 与 deterministic annotation processing；
- generated table-first Java API、packed columnar runtime、Row Pipeline、Column Pipeline、ColumnView 与 structured stats/errors；
- primitive、String、enum、semantic scalar和recursive immutable value fields/keys；
- keyed/dense tables、index/unique/order、dynamic sort、mutation、compaction、capacity和scratch治理；
- parent-owned required/optional dense/keyed child tables与recursive schema-object/List/Map materialization；
- RuntimePlan、五维MaterializationBudget、lifecycle/borrow/pin/release与failure-atomic semantics；
- FJSP、VRP、Simulation与Game Java 8 formal scenarios；
- benchmark runner、required smoke lanes和结构化JSONL evidence；
- Apache License 2.0、source/javadoc artifact、checksum/reproducibility和external Maven consumer路径。

### Changed

- 无已发布版本，因此不存在用户迁移或removed public API。

### Fixed

- 首个V1实现过程中发现的schema validation、staged KeySpace publication、child replacement/materialization、optional bitmap和metrics一致性问题均已进入对应Gate regression evidence。

### Security

- Compiler source/resource injection、unsupported compiler fail-closed、capacity/domain/collision、ownership/lifecycle、resource budget和diagnostic redaction边界进入V1验证门禁。

### Compatibility and limitations

- 正式JDK/OS/architecture支持范围以G6 compatibility matrix为准；未验证组合不作支持声明；
- 本条目不构成release announcement。SCM/contact、signing/provenance和G6未通过前不得公开发布或创建immutable release tag；
- V1不包含Python、C ABI、native runtime、cross-table transaction、persistence或无证据性能优势声明。

### Rollback and withdrawal

- 三个publishable artifact必须保持同version并一起回退；
- protocol/plan/schema mismatch必须fail closed；
- 已发布artifact不覆盖，严重问题通过withdrawal/advisory和新的修复版本处理。
