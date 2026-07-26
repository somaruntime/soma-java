# SOMA Java V1 安装与 Maven consumer 指南

类型：Report / 用户指南

状态：当前

Owner：SOMA Java 用户输出

受众：准备在 Java 8 Maven 项目中试用 SOMA 的开发者

事实范围：当前本地安装、consumer build、建模提示、升级边界和已验证能力限制

非事实范围：重新定义 public/schema/runtime Design 或声明 public release readiness

适用版本：当前仓库 `0.2.0-SNAPSHOT`，最后 implementation-affecting baseline `2aa8c15`

输入事实源：[正式文档入口](../docs/README.md)、当前 `pom.xml`、external Maven fixtures 与 Gate reports

最后审查日期：2026-07-27

本指南说明 Java 8 Maven consumer 如何使用 SOMA annotations、compiler
transformer、annotation processor、runtime-core 和 typed DataFlow。当前项目
只以 Azul Zulu JDK 8 作为 compiler/runtime 验真与目标支持 distribution；
Corretto 和其他 JDK distribution 均为 untested/unsupported。精确 Zulu
version/build、OS 与 architecture 边界仍只能引用 G6 compatibility matrix。

当前代码已通过 G0–G5，但 G6 仍 blocked，artifact 也仍是本地 snapshot。个人项目试用应先在本仓库执行 `./mvnw -B -ntp install`，不得把它描述为 public RC、production-ready 或已发布 artifact。

## 1. 前置条件

- Azul Zulu 完整 JDK 8，必须同时包含 `java`、`javac` 和 JDK compiler APIs；
- Maven 3.8.6–3.x；
- UTF-8 source encoding；
- SOMA 四个同版本 artifact：`soma-annotations`、`soma-processor`、
  `soma-runtime-core`、`soma-dataflow`。

不能用新 JDK 的 `--release 8` 代替 Zulu full JDK 8 compiler。Corretto、其他 JDK distribution、ECJ、JDK 9+ javac 或未进入正式矩阵的 IDE incremental compiler 不能被视为支持环境。

## 2. Maven 配置

当前本地试用使用 `0.2.0-SNAPSHOT`；未来正式发布后再把 `${soma.version}` 替换为对应 release version。`soma-processor` 只属于 build path，不应进入 application runtime graph。

```xml
<properties>
  <maven.compiler.source>1.8</maven.compiler.source>
  <maven.compiler.target>1.8</maven.compiler.target>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  <soma.version>0.2.0-SNAPSHOT</soma.version>
</properties>

<dependencies>
  <dependency>
    <groupId>com.hgtech.soma</groupId>
    <artifactId>soma-annotations</artifactId>
    <version>${soma.version}</version>
  </dependency>
  <dependency>
    <groupId>com.hgtech.soma</groupId>
    <artifactId>soma-runtime-core</artifactId>
    <version>${soma.version}</version>
  </dependency>
  <dependency>
    <groupId>com.hgtech.soma</groupId>
    <artifactId>soma-dataflow</artifactId>
    <version>${soma.version}</version>
  </dependency>
  <dependency>
    <groupId>com.hgtech.soma</groupId>
    <artifactId>soma-processor</artifactId>
    <version>${soma.version}</version>
    <scope>provided</scope>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <version>3.11.0</version>
      <configuration>
        <source>1.8</source>
        <target>1.8</target>
        <encoding>UTF-8</encoding>
        <compilerArgs>
          <arg>-Xplugin:SomaValue</arg>
        </compilerArgs>
        <annotationProcessorPaths>
          <path>
            <groupId>com.hgtech.soma</groupId>
            <artifactId>soma-processor</artifactId>
            <version>${soma.version}</version>
          </path>
          <path>
            <groupId>com.hgtech.soma</groupId>
            <artifactId>soma-annotations</artifactId>
            <version>${soma.version}</version>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
  </plugins>
</build>
```

## 3. Schema 与 generated API

1. 在 `package-info.java` 声明 `@SomaSchema`；
2. 使用 `@SomaTable`、`@SomaValue`、`@SomaField`、`@SomaKey`、`@SomaIndex`、`@SomaUnique`、`@SomaChild` 等定义 logical schema；`@SomaIndex` 提供 exact-group `scanByX`，`@SomaUnique` 优先提供 0..1 point family，业务顺序在 Candidate Scan 上显式调用 `sorted(totalComparator)`；
3. Maven compile 同时激活 `SomaValue` javac plugin 与 annotation processor；
4. application 依赖 generated typed API、`soma-runtime-core` 和
   `soma-dataflow`，不直接访问 generated-sources directory、logical IR 或
   runtime internal type；
5. 所有同一应用中的 generated source、runtime-core、runtime plan 和 schema hash必须通过初始化兼容性检查。

两个正式参考应用位于 `soma-examples` 的独立 child projects；独立于 reactor parent 的完整 core consumer fixture 位于 `soma-testkit/src/test/fixtures/external-maven-breadth-phase5`。

## 4. 建模顺序与关键语义

1. 先区分 input facts、working state 和 result facts；
2. 有稳定业务 identity 的 row 使用 `@SomaKey` keyed table；只依赖 packed traversal/current Index 的 row 使用 dense table；
3. 只为稳定 exact access 声明 `@SomaIndex`/`@SomaUnique`；
4. parent 独占且同生命周期的数据使用 `@SomaChild`；
5. 可变业务顺序在调用处显式 `sorted(totalComparator)`，跨轮次队列使用 application-owned heap；
6. hot loop 按 Point/Candidate/Column/Key/Bulk/Ownership 选择自然路径；
   Candidate Scan、ColumnTraversal 或 ColumnView 负责局部访问；
7. 需要 Selection、Projection、Aggregation、Partition、Join、GroupBy、
   Window、detached Result 或 safe-point Effect 时，使用生成的
   `<Table>DataFlow` 定义一次或可复用的 typed transformation，不把 generic
   object graph、Java Stream 或 I/O 放进执行热路径。

Keyed identity 稳定，但 current Index 不稳定。Keyed/dense 删除都使用
swap-remove，未排序的 first/limit/indexSnapshot/fetchAll 只基于当时 source
sequence。`findIndex/requireIndex` 不物化 carrier；`IndexSnapshot` 显式复制并
只在紧接着的同步只读批次消费；跨 operation 保存引用必须使用 `@SomaKey`。
SOMA 不提供跨 table transaction，业务提交与恢复由 application 负责。
DataFlow 的 Definition/Template 不持有 live Table；每次 Invocation one-shot，
并行执行只在 Invocation 独占的只读/受控 Effect 边界内发生。外部状态同步先形成
detached Batch/Delta，再在 application safe point 提交。

## 5. 验证安装

Repository contributor 使用：

```text
./mvnw -B -ntp verify
./scripts/check.sh
```

External consumer 至少确认：

- generated source实际产生并编译；
- application classfile major version为52；
- `soma-processor`不在runtime dependency tree；
- schema hash、runtime plan identity与runtime stats可读取；
- duplicate/missing/stale/released/view-pinned/materialization-budget错误可观察。

## 6. Upgrade、rollback 与 withdrawal

- annotation、processor、runtime-core 和 dataflow 必须使用同一 artifact family
  version；
- schema、generated/runtime protocol或runtime plan identity不匹配时必须fail closed；
- released artifact immutable，不覆盖同version binary；
- 回退时同时回退四个 artifact 并重新生成 consumer generated source，不能把
  旧 generated class 与新 runtime/dataflow 混用；
- 如果某version被标记withdrawn，停止新部署并迁移到公告指定的修复版本；
- SOMA V1不提供持久化schema migration，detached object/wire/database迁移由application adapter拥有。

## 7. Known limitations

- Java-only；不提供Python、C ABI、native runtime或跨语言FFI；
- 只支持正式G6 matrix列出的Azul Zulu full JDK 8 javac/runtime组合；
- runtime 不是跨 table transaction、ORM、ECS、SQL/query engine 或
  persistence layer；
- DataFlow 只处理有限、typed、heap-resident 的 transformation；不提供无限流、
  自动增量视图、full/cross/theta join、隐式 common pool 或 blocking I/O；
- callback的业务副作用、超时、取消和外部一致性由application拥有；
- benchmark smoke只证明路径和结构化证据可运行，不代表性能优势；
- public publishing endpoint、SCM/contact或签名机制未进入正式G6 report前，不得把本地artifact当作公开release。
