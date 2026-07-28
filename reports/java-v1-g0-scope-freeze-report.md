# SOMA Java V1 G0 Scope Freeze Report

类型：Report / Gate Snapshot

状态：passed

Owner：SOMA Java G0 scope freeze

受众：SOMA maintainer、Gate reviewer 与 release owner

适用版本：`soma-java` `0.2.0-SNAPSHOT`

输入事实源：产品 Blueprint、正式 Design、Conformance、Validation Gate 与
`check-v1-scope.sh`

事实范围：当前 Java-only V1 scope、唯一 Owner、Capability 与 release claim boundary

非事实范围：G1–G6 结果、跨环境支持、public release readiness

最后审查日期：2026-07-28

执行日期：2026-07-28

输入 commit：`d3f2e354fd553b3d2923cc2c145413930e06ba8c`

环境：Azul Zulu OpenJDK `1.8.0_492-b09`、Maven `3.9.16`、macOS `26.5.2`
`aarch64`

方法：正式 Owner 追踪、scope fail-closed checker、文档结构与 executable surface
重放

## 1. 结论

G0 保持 `passed`。SOMA Java V1 的目标不是一个通用 collection/query/stream
framework，而是 Java 8、Schema-Defined、Compiler-Specialized、JVM
Heap-Resident 的 runtime-state computing library。

产品以 `State/Owner + Capability + Plan/Lifecycle` 三个轴表达。Blueprint 定义
目标体验，Design 定义长期语义，Implementation Map 定位当前代码，Conformance
记录偏差，Engineering 定义验证与发布过程；README、Report、历史文档和当前实现
均不得反向缩小目标。

## 2. 冻结范围

- Schema/Metadata、Storage/Layout、Access、Mutation、Exact/Relation、
  Transformation、Execution、Result Delivery、Resource、
  Observation/Failure 构成封闭 V1 Capability Set；
- field 只属于 primitive-backed scalar、白名单 reference-backed `String`、
  compiler-flattened `@SomaValue` 或 parent-owned `@SomaChild`；
- Descriptor、mutable-before-freeze Plan、Effective Metadata、Runtime Metadata
  与 Observation 分相；
- packed/primitive/compiler-bound hot path 不保存 schema object、DTO、
  arbitrary Object 或 Java Collection graph；
- Eager Detached 是默认 Result Delivery；唯一 lazy 形态是同步、read-only、
  callback-scoped delivery；
- Small/Medium、单表与双表 100M、String profile、bounded intermediate/output
  与 adaptive parallel 均保留在正式性能目标；
- 三个 reference application 是独立普通 Java 8 consumer，不拥有 core Design；
- Java 8 之外的 JVM、native/C ABI、Python、持久化、分布式执行、开放 runtime
  strategy SPI 不属于 V1。

## 3. 当前可重放证据

```sh
sh ./scripts/check-v1-scope.sh
./scripts/check-docs.sh
./scripts/check-public-api.sh
./scripts/check-external-consumer.sh
./scripts/check.sh
```

`check-v1-scope.sh` 固定产品边界与 scope non-regression；文档 Gate 固定唯一 Owner
和 current navigation；public/external Gate 防止实现清理绕过实际 consumer。

## 4. Claim boundary

G0 只证明 V1 scope 与治理边界没有被缩小。当前 G0–G5 的实现与 evidence 状态由
当前综合 Report 拥有；G6 仍因真实 SCM/ownership/contact、签名发布、clean
provenance 与正式支持矩阵不足而 `blocked`。本机 smoke、benchmark 或
`0.2.0-SNAPSHOT` 均不能替代 G6。
