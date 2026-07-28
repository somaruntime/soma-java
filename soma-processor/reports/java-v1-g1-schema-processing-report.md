# SOMA Java V1 G1 Schema Processing Report

类型：Report / Gate Snapshot

状态：passed

Owner：SOMA Java schema processing

受众：SOMA maintainer、compiler/runtime reviewer 与 Gate owner

适用版本：`soma-java` `0.2.0-SNAPSHOT`

输入事实源：annotation/processor source、compiler fixtures、schema/hash golden、
public API 与 external Maven consumer

事实范围：V1 schema authoring、validation、normalization、diagnostics 与 identity

非事实范围：generated implementation mechanics、runtime performance、G6

最后审查日期：2026-07-28

执行日期：2026-07-28

输入 commit：`d3f2e354fd553b3d2923cc2c145413930e06ba8c`

环境：Azul Zulu OpenJDK `1.8.0_492-b09`、full `javac 1.8.0_492`、
Maven `3.9.16`、macOS `26.5.2` `aarch64`

方法：positive/negative javac contracts、clean/repeat/locale identity、
schema/hash golden 与独立 Maven consumer

## 1. 结论

G1 保持 `passed`。Schema processing 以 annotation declaration 和 full JDK 8
javac integration 为唯一 authoring/compilation path；processor 在编译期完成
validation、normalization、graph/ownership 检查与 canonical identity，不把 schema
object 或 metadata interpreter 带入 runtime hot path。

## 2. 当前能力

- primitive、enum、date/time 与显式 semantic scalar 归一为 primitive-backed
  storage；
- V1 reference-backed immutable scalar 只白名单支持 `String`；
- `@SomaValue` 在编译期递归 flatten，不保存 value object reference；
- structured state 只通过 parent-owned child table 表达；
- arbitrary Object/array/DTO/Collection graph、非法 key/selector/child/default、
  value cycle、name collision 与 annotation spoof 均 fail closed；
- schema JSON/hash、Unicode/locale/timezone order、default normalization 和
  generated name identity 可重复；
- Descriptor Metadata 与 schema-seeded Plan entry 由 generator 固化，运行期不重新
  解释 schema。

## 3. 当前可重放证据

```sh
./scripts/check-compiler-contracts.sh
./scripts/check-schema-diagnostics-contract.sh
./scripts/check-value-shape-contract.sh
./scripts/check-default-value-contract.sh
./scripts/check-public-api.sh
./scripts/check-external-consumer.sh
```

Fixture 位于仓库级 `tests/fixtures/`，不再由 test artifact 或 Maven module 承载。
Public/generated identity 由实际 `javac`/Maven compile-run、schema/hash 与 `javap`
golden 共同证明。

## 4. Claim boundary

G1 不证明任意 Java object 可作为 schema field，也不证明跨 JDK vendor/compiler
支持。当前唯一 validation authority 是 Azul Zulu full JDK 8；G6 仍单独
`blocked`。
