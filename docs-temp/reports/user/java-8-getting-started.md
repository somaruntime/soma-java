# SOMA Java 8 使用入门

类型：Report / 用户文档

状态：候选

Owner：SOMA Java 用户输出

受众：准备在个人 Java 8 Maven 项目中试用 SOMA 的开发者

事实范围：当前本地试用前置条件、安装路径、建模提示和已验证能力边界

适用版本：当前仓库 `0.1.0-SNAPSHOT`，commit `4b6fa43`

输入事实源：候选 Design、当前 `pom.xml`、external Maven fixtures、现有安装指南

最后审查日期：2026-07-19

## 1. 当前可以做什么

当前代码已经实现并通过 G0–G5 验证的主要能力包括：annotation schema、immutable value lowering、keyed/dense generated table、exact key/index/unique access、Row Pipeline、ColumnView、batch mutation、owned child、detached materialization、runtime plan、structured errors/stats 和四类 executable examples。

当前 artifact 仍是本地 `0.1.0-SNAPSHOT`，没有通过 G6，也没有公开发布 endpoint 或正式支持矩阵。个人项目试用应从本地仓库安装，不把它当作 public release。

## 2. 前置条件

- 完整 JDK 8，包含 `java`、`javac` 和 compiler APIs；
- Maven 3.8.6–3.x，或本仓库 Maven Wrapper；
- UTF-8 source；
- annotations、processor、runtime-core 使用同一版本。

新 JDK 的 `--release 8` 不能代替当前 compiler integration 所需的 full JDK 8 javac。

## 3. 本地安装

在 SOMA 仓库根目录执行：

```text
./mvnw -B -ntp install
```

个人项目中使用相同 snapshot version，并让 processor 只存在于 build path：

```xml
<dependencies>
  <dependency>
    <groupId>com.hgtech.soma</groupId>
    <artifactId>soma-annotations</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  <dependency>
    <groupId>com.hgtech.soma</groupId>
    <artifactId>soma-runtime-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  <dependency>
    <groupId>com.hgtech.soma</groupId>
    <artifactId>soma-processor</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>provided</scope>
  </dependency>
</dependencies>
```

`maven-compiler-plugin` 需要启用 `-Xplugin:SomaValue`，并把 `soma-processor` 与 `soma-annotations` 放入 `annotationProcessorPaths`。可直接参考当前独立 consumer fixture 的 [`pom.xml`](../../../soma-testkit/src/test/fixtures/external-maven-breadth-phase5/pom.xml)。

## 4. 建模顺序

1. 先区分 input facts、working state 和 result facts；
2. 有稳定业务 identity 的 row 使用 `@SomaKey` keyed table；只依赖 packed traversal/current Index 的 row 使用 dense table；
3. 只为稳定 exact access 声明 `@SomaIndex`/`@SomaUnique`；
4. parent 独占且同生命周期的数据使用 `@SomaChild`；
5. 可变业务顺序在调用处显式 `sorted(totalComparator)`，跨轮次队列使用 application heap；
6. hot loop 使用 generated pipeline/view，object graph 只在边界 materialize。

## 5. 重要语义

- keyed identity 稳定，但 current Index 不稳定；
- keyed/dense 删除都采用 swap-remove，不保证物理顺序；
- 未排序的 first/limit/fetchAll 只基于当时 source sequence；
- exact index 在 mutation 时增量维护，不在读取时全表 rebuild；
- child 不能 share/reparent，owned child 不能独立 release；
- pipeline/view/snapshot 有 lifecycle/epoch 约束；
- SOMA 不提供跨 table transaction，业务提交与恢复由 application 负责。

## 6. 从哪里开始

- FJSP 完整入口：[`FjspScenario.java`](../../../soma-examples/src/main/java/com/hgtech/soma/examples/fjsp/FjspScenario.java)；
- 简洁 external consumer：[`external-maven-access-phase3`](../../../soma-testkit/src/test/fixtures/external-maven-access-phase3)；
- 完整 breadth consumer：[`external-maven-breadth-phase5`](../../../soma-testkit/src/test/fixtures/external-maven-breadth-phase5)。

试用时建议先用实际 Access Pattern 做正确性和 allocation/GC 基线，再决定哪些 state 值得进入 SOMA。
