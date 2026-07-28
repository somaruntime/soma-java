# SOMA Java V1 G4 Public Consumer and Package Report

类型：Report / Gate Snapshot

状态：passed

Owner：SOMA Java G4 public consumer/package evidence

受众：SOMA maintainer、consumer/package reviewer 与 release owner

适用版本：`soma-java` `0.2.0-SNAPSHOT`

输入事实源：public/generated golden、independent Maven consumers、four-module
package smoke 与 runtime dependency tree

事实范围：当前 public/generated compatibility、Java 8 consumer 与 publishable
artifact shape

非事实范围：signed artifact、public repository、G6 release readiness

最后审查日期：2026-07-28

执行日期：2026-07-28

输入 commit：`d3f2e354fd553b3d2923cc2c145413930e06ba8c`

环境：Azul Zulu OpenJDK `1.8.0_492-b09`、full `javac 1.8.0_492`、
Maven `3.9.16`、macOS `26.5.2` `aarch64`

方法：independent-M2 compile/run、exact `javap` golden、classfile major、
archive content、two-clean-build checksum comparison

## 1. 结论

G4 保持 `passed`。普通 Java 8 consumer 可以在不继承 reactor parent 的独立 Maven
project 中显式使用 annotations/processor、runtime-core 和 dataflow，生成并运行
schema-specific API。当前 executable golden 登记 224 个 `PUBLIC`、2 个
`INTERNAL` entry；每个 entry 均分类为 application authoring、build provider、
handwritten runtime/dataflow 或 generated bridge。

## 2. 当前发布形态

Publishable production module 为：

- `soma-annotations`；
- `soma-processor`；
- `soma-runtime-core`；
- `soma-dataflow`。

`soma-examples`、`soma-benchmarks`、仓库级 `tests/fixtures` 均不进入 production
runtime artifact。Direct Access consumer 可以只依赖 runtime-core；使用 reusable
DataFlow 的完整 consumer 同时依赖 dataflow。Processor/build-time `jdk:tools` 不
进入 application runtime。

## 3. 当前可重放证据

```sh
./scripts/check-public-api.sh
./scripts/check-external-consumer.sh
./scripts/check-reference-applications.sh
SOMA_PACKAGE_ALLOW_DIRTY=true ./scripts/package-smoke.sh
```

本轮 package diagnostic 产生 17 个文件：parent POM，以及四个 module 各自 POM、
binary、source、javadoc。两次 isolated repository clean build 的全部 SHA-256
逐字节一致；binary/source archive 包含与 root 完全相同的 LICENSE/NOTICE，全部
classfile major 为 52。该结果记录 `dirty=true`、`signature=not-performed`。

## 4. Claim boundary

G4 证明当前本机 public/generated consumer 和 unsigned local package shape；它不
是 immutable/signed release artifact。SCM/contact、namespace ownership、
signing/publishing、clean provenance 和正式支持矩阵仍由 G6 阻塞。
