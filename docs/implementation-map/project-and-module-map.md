# 项目与模块地图

类型：Implementation Map

状态：正式

Owner：SOMA Java 项目实现导航

对应 Design：[系统架构](../design/system-architecture.md)

事实范围：当前 Maven reactor、模块职责、主要产物和顶层执行入口

最近实现核对基线：`e68c4e4` production/evidence identity与当前private-source
engineering surface

最后审查日期：2026-07-28

## 1. Reactor

根 [`pom.xml`](../../pom.xml) 当前版本为 `0.2.0-SNAPSHOT`，Java source/target 1.8，并按以下顺序声明模块：

| Module | 当前主要代码入口 | 当前产物角色 |
|---|---|---|
| `soma-annotations` | [`io.github.somaruntime.soma.annotation`](../../soma-annotations/src/main/java/io/github/somaruntime/soma/annotation) | public schema annotations |
| `soma-runtime-core` | [`io.github.somaruntime.soma.runtime`](../../soma-runtime-core/src/main/java/io/github/somaruntime/soma/runtime) | handwritten runtime + generated protocol |
| `soma-dataflow` | [`io.github.somaruntime.soma.dataflow`](../../soma-dataflow/src/main/java/io/github/somaruntime/soma/dataflow) | typed Transformation/DataFlow production runtime |
| `soma-processor` | [`SomaProcessor.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/SomaProcessor.java) | javac plugin、processor、generator |
| `soma-examples` | [`pom.xml`](../../soma-examples/pom.xml)、[应用入口](../../soma-examples/docs/README.md) | 三个独立 Java 8 reference consumer 的 aggregator；不产出共享领域 JAR |
| `soma-benchmarks` | [`BenchmarkSmokeRunner.java`](../../soma-benchmarks/src/main/java/io/github/somaruntime/soma/benchmarks/BenchmarkSmokeRunner.java) | 领域中性 component benchmark runners/artifacts |

完整 production boundary 是 compile-time annotations/processor 与
runtime-core/dataflow；只使用 direct Access 的源码不引用 DataFlow type。
仓库级 [`tests/fixtures`](../../tests/fixtures)、examples aggregator 和 benchmarks
不应出现在普通 consumer runtime classpath。fixtures 不属于 reactor、不产出
artifact；三个 reference application 是相互独立的普通 consumer，不是 SOMA
runtime artifact。

## 2. Build 和 Gate 入口

- Maven Wrapper：[`mvnw`](../../mvnw)；
- exact toolchain：[`scripts/check-toolchain.sh`](../../scripts/check-toolchain.sh)；
- Codex Cloud bootstrap：
  [`scripts/setup/setup-codex-cloud.sh`](../../scripts/setup/setup-codex-cloud.sh)；
- 全局验证：[`scripts/check.sh`](../../scripts/check.sh)；
- 文档检查：[`scripts/check-docs.sh`](../../scripts/check-docs.sh)；
- package smoke：[`scripts/package-smoke.sh`](../../scripts/package-smoke.sh)；
- benchmark smoke：[`scripts/check-benchmark-smoke.sh`](../../scripts/check-benchmark-smoke.sh)；
- GitHub CI：[`ci.yml`](../../.github/workflows/ci.yml)；
- private-source qualification：
  [`release-qualification.yml`](../../.github/workflows/release-qualification.yml)。

`check.sh` 顺序执行 docs、Maven verify、build/public
API/compiler/codegen/runtime/keyspace/access/child/breadth/diagnostics/external
consumer、三个 reference application、neutral benchmark、integrated multi-fork
与 generated-footprint 检查，最后执行 `git diff --check`。

## 3. 实现数据流

```text
application schema source
  -> soma-annotations compile contract
  -> soma-processor javac plugin + JSR 269
  -> target/generated-sources/annotations schema-specific code
  -> soma-runtime-core public config/errors + generated protocol
  -> application runtime tables
  -> soma-dataflow typed Definition/Template/Invocation
  -> generated companion binding
  -> Result / safe-point Effect
```

详细入口分别见[编译器与代码生成地图](compiler-and-codegen-map.md)、[Runtime Core 地图](runtime-core-map.md)和[DataFlow 实现地图](dataflow-map.md)。

## 4. 核对边界

本地图以 commit 为基线，不把未提交文档、target 生成物或本机 benchmark artifact 当成稳定实现入口。新增模块、production dependency 或顶层 Gate 后需要更新本地图。
