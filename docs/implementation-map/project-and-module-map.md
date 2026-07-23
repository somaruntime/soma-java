# 项目与模块地图

类型：Implementation Map

状态：正式

Owner：SOMA Java 项目实现导航

对应 Design：[系统架构](../design/system-architecture.md)

事实范围：当前 Maven reactor、模块职责、主要产物和顶层执行入口

最近实现核对基线：Stage 5 reference-application cutover candidate

最后审查日期：2026-07-23

## 1. Reactor

根 [`pom.xml`](../../pom.xml) 当前版本为 `0.2.0-SNAPSHOT`，Java source/target 1.8，并按以下顺序声明模块：

| Module | 当前主要代码入口 | 当前产物角色 |
|---|---|---|
| `soma-annotations` | [`com.hgtech.soma.annotation`](../../soma-annotations/src/main/java/com/hgtech/soma/annotation) | public schema annotations |
| `soma-runtime-core` | [`com.hgtech.soma.runtime`](../../soma-runtime-core/src/main/java/com/hgtech/soma/runtime) | handwritten runtime + generated protocol |
| `soma-processor` | [`SomaProcessor.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/SomaProcessor.java) | javac plugin、processor、generator |
| `soma-testkit` | [`MaterializedGraphComparator.java`](../../soma-testkit/src/main/java/com/hgtech/soma/testkit/MaterializedGraphComparator.java) | fixtures/evidence helpers |
| `soma-examples` | [`pom.xml`](../../soma-examples/pom.xml)、[应用入口](../../soma-examples/docs/README.md) | 两个独立 Java 8 reference consumer 的 aggregator；不产出共享领域 JAR |
| `soma-benchmarks` | [`BenchmarkSmokeRunner.java`](../../soma-benchmarks/src/main/java/com/hgtech/soma/benchmarks/BenchmarkSmokeRunner.java) | 领域中性 component benchmark runners/artifacts |

Production runtime boundary 是 annotations + processor + runtime-core；testkit、examples aggregator 和 benchmarks 不应出现在普通 consumer runtime classpath。两个 reference application 是普通 consumer，不是 SOMA runtime artifact。

## 2. Build 和 Gate 入口

- Maven Wrapper：[`mvnw`](../../mvnw)；
- 全局验证：[`scripts/check.sh`](../../scripts/check.sh)；
- 文档检查：[`scripts/check-docs.sh`](../../scripts/check-docs.sh)；
- package smoke：[`scripts/package-smoke.sh`](../../scripts/package-smoke.sh)；
- benchmark smoke：[`scripts/check-benchmark-smoke.sh`](../../scripts/check-benchmark-smoke.sh)。

`check.sh` 顺序执行 docs、Maven verify、build/public API/compiler/codegen/runtime/keyspace/access/child/testkit/breadth/diagnostics/external consumer、两个 reference application、neutral benchmark、integrated multi-fork 与 generated-footprint 检查，最后执行 `git diff --check`。

## 3. 实现数据流

```text
application schema source
  -> soma-annotations compile contract
  -> soma-processor javac plugin + JSR 269
  -> target/generated-sources/annotations schema-specific code
  -> soma-runtime-core public config/errors + generated protocol
  -> application runtime tables
```

详细入口分别见[编译器与代码生成地图](compiler-and-codegen-map.md)和[Runtime Core 地图](runtime-core-map.md)。

## 4. 核对边界

本地图以 commit 为基线，不把未提交文档、target 生成物或本机 benchmark artifact 当成稳定实现入口。新增模块、production dependency 或顶层 Gate 后需要更新本地图。
