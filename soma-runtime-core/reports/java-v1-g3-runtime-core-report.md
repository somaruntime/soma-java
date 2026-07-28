# SOMA Java V1 G3 Runtime Core Report

类型：Report / Gate Snapshot

状态：passed

Owner：SOMA Java runtime core

受众：SOMA maintainer、runtime/DataFlow reviewer 与 Gate owner

适用版本：`soma-java` `0.2.0-SNAPSHOT`

输入事实源：runtime/dataflow production source、capability contracts、generated
consumer、reference differential 与 component benchmark

事实范围：Plan/Metadata、Group/ownership、storage/access、resource/failure 和
DataFlow execution boundary

非事实范围：任意 profile 的规模承诺、application algorithm、G6

最后审查日期：2026-07-28

执行日期：2026-07-28

输入 commit：`d3f2e354fd553b3d2923cc2c145413930e06ba8c`

环境：Azul Zulu OpenJDK `1.8.0_492-b09`、Maven `3.9.16`、
macOS `26.5.2` `aarch64`

方法：capability contract、randomized/differential oracle、failure/lifecycle
invariant、component baseline 与 bounded qualification

## 1. 结论

G3 保持 `passed`。Runtime core 以 packed authoritative Table state、显式
SomaGroup/root ownership、create-time immutable plan、closed physical
Capability、hard resource ledger 和 detached observation 为主线。DataFlow 在
one-shot Invocation 内组合 typed Transformation、bounded scheduler、Result
Delivery 与 safe-point Effect，不把 Table 变成 concurrent API。

## 2. 当前能力

- Descriptor/Plan/Effective/Runtime Metadata 与 Observation 分相；
- flat 或 flat-head/fixed-tail storage、primitive locator、Unique/Exact、
  Candidate、Column/Point/Key/Bulk/Ownership access；
- String reference-backed value semantics、presence、Key/Unique/Index、
  Group/Join、mutation/clear/release 与三层内存口径；
- SomaGroup attach/rollback/reverse release、root fault containment 与 resource
  attribution；
- Delta、Group/Join、Window、bounded expansion、morsel scheduler 与 adaptive
  sequential/parallel crossover；
- Eager Detached 默认 delivery，以及同步 read-only callback-scoped delivery；
- atomic mutation/failure、one-shot handle、non-escape guard、cancel/deadline、
  executor ownership 与 ledger cleanup。

Runtime `StatsMode` 固定 Table observation 成本；DataFlow `StatsMode` 固定
Invocation diagnostics。二者 Owner 与生命周期不同，保持为独立 capability policy。

## 3. 当前可重放证据

```sh
./scripts/check-runtime-contracts.sh
./scripts/check-dataflow-contracts.sh
./scripts/check-dataflow-reference.sh
./scripts/check-generated-access-contract.sh
./scripts/check-generated-ownership-contract.sh
./scripts/check-access-performance.sh
./scripts/check-dataflow-performance.sh
./scripts/check-benchmark-smoke.sh
```

完整十 lane scale qualification 是显式重型 Gate，不隐式放入普通
`check.sh`；Small/Fast smoke 与 strict artifact validation 仍进入日常 Gate。

## 4. Claim boundary

G3 不表示任意 Schema/String/expansion 都能达到 100M，也不表示 public latency
SLA。当前规模事实只适用于预注册 profile；G6 仍 `blocked`。
