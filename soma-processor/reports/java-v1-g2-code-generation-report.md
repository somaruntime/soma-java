# SOMA Java V1 G2 Code Generation Report

类型：Report / Gate Snapshot

状态：passed

Owner：SOMA Java code generation

受众：SOMA maintainer、generated API reviewer 与 Gate owner

适用版本：`soma-java` `0.2.0-SNAPSHOT`

输入事实源：processor emitter、generated source/class、schema/hash 与 external
consumer golden

事实范围：当前 schema-specific generated facade、protocol identity 与 Java 8 shape

非事实范围：application domain API、跨环境性能、G6

最后审查日期：2026-07-28

执行日期：2026-07-28

输入 commit：`d3f2e354fd553b3d2923cc2c145413930e06ba8c`

环境：Azul Zulu OpenJDK `1.8.0_492-b09`、full `javac 1.8.0_492`、
Maven `3.9.16`、macOS `26.5.2` `aarch64`

方法：source/bytecode shape、exact public `javap`、clean/repeat generation、
negative admission 与 independent consumer compile-run

## 1. 结论

G2 保持 `passed`。每个 schema 生成唯一的 Metadata companion，以及 Table、Batch、
Scan/Cursor/UpdateCursor、Key/Column traversal、child/materialization、Delta 和
DataFlow companion。当前 canonical identity 为 generated/runtime v11、
transformation v3、kernel/planner v4 与 runtime plan v6。

Generated facade 是 compiler-specialized product surface；runtime/generated
bridge 因 consumer class 位于 application package 而需要 JVM `public`
visibility，但不因此成为 application 手写 SPI。

## 2. Shape 与边界

- generated Table 绑定 primitive/reference columns、presence、locator、exact、
  ownership 与 lifecycle，不生成 per-row live entity graph；
- primitive/String API 保持 typed specialization，不退化到 `Object`、boxing、
  Java Stream 或 collection hot path；
- Metadata/Plan、Group attachment、Runtime Metadata、callback-scoped delivery 和
  DataFlow binding 由 schema-specific protocol 固化；
- clean/repeat、locale/timezone 与 external Maven generation 保持 canonical
  schema/hash/source/class identity；
- generated name collision、invalid declaration、unsupported compiler 和 protocol
  mismatch fail closed。

## 3. 当前可重放证据

```sh
./scripts/check-codegen-admission.sh
./scripts/check-generated-dense-contract.sh
./scripts/check-generated-keyed-contract.sh
./scripts/check-generated-access-contract.sh
./scripts/check-generated-ownership-contract.sh
./scripts/check-generated-breadth-contract.sh
./scripts/check-generated-naming-contract.sh
./scripts/check-public-api.sh
./scripts/check-external-consumer.sh
./scripts/check-scan-code-size.sh
```

## 4. Claim boundary

G2 证明当前 V1 generated surface 和 Java 8 bytecode shape；不承诺 arbitrary object
storage、开放 generator protocol、其他 javac/JDK distribution 或 public release
readiness。
