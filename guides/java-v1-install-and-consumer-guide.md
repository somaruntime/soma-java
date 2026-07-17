# SOMA Java V1 安装与 Maven consumer 指南

状态：V1 release candidate 准备中

本指南说明 Java 8 Maven consumer 如何使用 SOMA annotations、compiler transformer、annotation processor 和 runtime-core。正式支持的 JDK vendor/minor、OS 与 architecture 只能引用 G6 compatibility matrix；未进入矩阵的环境均为 untested/unsupported。

## 1. 前置条件

- 完整 JDK 8，必须同时包含 `java`、`javac` 和 JDK compiler APIs；
- Maven 3.8.6–3.x；
- UTF-8 source encoding；
- SOMA 三个同版本 artifact：`soma-annotations`、`soma-processor`、`soma-runtime-core`。

不能用新 JDK 的 `--release 8` 代替受支持的完整 JDK 8 compiler。ECJ、JDK 9+ javac 或未进入正式矩阵的 IDE incremental compiler 不能被视为支持环境。

## 2. Maven 配置

将 `${soma.version}` 替换为同一正式版本。`soma-processor` 只属于 build path，不应进入 application runtime graph。

```xml
<properties>
  <maven.compiler.source>1.8</maven.compiler.source>
  <maven.compiler.target>1.8</maven.compiler.target>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  <soma.version>1.0.0</soma.version>
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
2. 使用 `@SomaTable`、`@SomaValue`、`@SomaField`、`@SomaKey`、`@SomaIndex`、`@SomaUnique`、`@SomaChild` 等定义 logical schema；`@SomaIndex`/`@SomaUnique`只提供exact access，业务顺序在generated Row Pipeline上显式调用`sorted(totalComparator)`；
3. Maven compile 同时激活 `SomaValue` javac plugin 与 annotation processor；
4. application 只依赖 generated typed API 和 `soma-runtime-core`，不直接访问 generated-sources directory 或 runtime internal type；
5. 所有同一应用中的 generated source、runtime-core、runtime plan 和 schema hash必须通过初始化兼容性检查。

正式可执行示例位于 `soma-examples`；独立于 reactor parent 的完整 consumer fixture 位于 `soma-testkit/src/test/fixtures/external-maven-breadth-phase5`。

## 4. 验证安装

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

## 5. Upgrade、rollback 与 withdrawal

- annotation、processor和runtime-core必须使用同一artifact family version；
- schema、generated/runtime protocol或runtime plan identity不匹配时必须fail closed；
- released artifact immutable，不覆盖同version binary；
- 回退时同时回退三个artifact并重新生成consumer generated source，不能把旧generated class与新runtime混用；
- 如果某version被标记withdrawn，停止新部署并迁移到公告指定的修复版本；
- SOMA V1不提供持久化schema migration，detached object/wire/database迁移由application adapter拥有。

## 6. Known limitations

- Java-only；不提供Python、C ABI、native runtime或跨语言FFI；
- 只支持正式G6 matrix列出的full JDK 8 javac/runtime组合；
- runtime不是跨table transaction、ORM、ECS、query engine或persistence layer；
- callback的业务副作用、超时、取消和外部一致性由application拥有；
- benchmark smoke只证明路径和结构化证据可运行，不代表性能优势；
- public publishing endpoint、SCM/contact或签名机制未进入正式G6 report前，不得把本地artifact当作公开release。
