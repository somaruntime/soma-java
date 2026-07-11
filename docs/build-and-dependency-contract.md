# Build 与依赖契约

状态：正式设计文档
Owner：根项目协调层
事实范围：Maven reactor、模块依赖方向、artifact classification、consumer build path、dependency policy、reproducibility 和 CI baseline
非事实范围：release approval、public API behavior、compiler lowering internals、test implementation和具体发布结果
最后审查日期：2026-07-10

## 1. 目标

本文把项目架构转换为可执行 build graph，确保开发机、CI、external consumer 和 release package 使用同一入口，不依赖 IDE 私有 classpath。

Compiler-specific behavior 由 [compiler integration contract](../soma-processor/docs/compiler-integration-contract.md) 拥有；artifact version/release 由 [versioning and release contract](versioning-and-release-contract.md) 拥有。

## 2. Canonical build

Canonical repository build 使用：

```text
full JDK 8
  + Maven Wrapper
  + root reactor pom
  + UTF-8
  + pinned plugin versions
```

正式入口最终统一为：

```text
./mvnw -B -ntp verify
```

在 wrapper 尚未引入的 commit 上可以使用受控 Maven 版本，但从第一个 Java implementation commit 起，CI、贡献说明和 package report 必须使用 wrapper。JRE-only、IDE-only 或未记录的本机 Maven 不构成 release evidence。

### 2.1 实施验证基线

V1 实施阶段先使用当前开发机的完整 JDK 8 javac、repository Maven Wrapper 和当前 OS/architecture 完成功能实现、correctness、external consumer 和 benchmark 开发。该环境是 implementation validation baseline，不是正式 support matrix，也不产生其他 vendor/minor、OS 或 architecture 的支持承诺。

每次正式 validation、phase/checkpoint closeout 和 gate report 必须记录：

- 实际 JDK vendor、version 和 build；
- Maven Wrapper distribution/version；未执行 Maven 时明确记为 not applicable；
- OS 和 architecture；
- 完整执行命令；
- 结果只覆盖该次实际环境，不外推为未验证组合的支持结论。

Machine-local `JAVA_HOME`、Homebrew path、Maven cache 或其他绝对路径可以出现在 evidence report 的环境记录中，但不得进入 distributable build、generated artifact、schema hash 或正式支持承诺。正式 JDK vendor/minor、runtime JVM、OS 和 architecture matrix 仍由 G6 report 拥有；在 public RC/release sign-off 前未形成并验证时，其状态必须保持 `not-started` 或 `blocked`。

## 3. Module dependency graph

允许的 production/build dependency：

```text
soma-annotations
      ^
      |
soma-processor            soma-runtime-core
      ^                           ^
      |                           |
      +------ generated code -----+

soma-testkit -> annotations + processor + runtime-core
soma-examples -> annotations + processor(build-only) + runtime-core
soma-benchmarks -> runtime-core + selected example fixtures
```

| Module | Allowed direct dependencies | Forbidden direction |
|---|---|---|
| `soma-annotations` | JDK only | processor/runtime/examples/testkit/benchmarks |
| `soma-processor` | annotations、JDK compiler APIs | runtime/examples/testkit/benchmarks |
| `soma-runtime-core` | JDK only | annotations/processor/examples/testkit/benchmarks |
| `soma-testkit` | annotations/processor/runtime-core、test-scoped tools | production modules depend on testkit |
| `soma-examples` | annotations/runtime-core、processor build-only | core/processor depend on examples |
| `soma-benchmarks` | runtime/generated/example fixture、benchmark harness | production modules depend on benchmarks |

任何 circular dependency 阻塞 build gate。

## 4. Artifact classification

| Artifact | Classification | Publish policy |
|---|---|---|
| `soma-java-parent` | build metadata | public release 时随 child POM 发布 |
| `soma-annotations` | public compile API | publishable |
| `soma-processor` | public build-time tool | publishable，非 runtime dependency |
| `soma-runtime-core` | public runtime dependency | publishable |
| `soma-testkit` | internal evidence helper | V1 默认不发布；外部 use case 经决策后再开放 |
| `soma-examples` | repository scenario/evidence | 不发布到 dependency repository |
| `soma-benchmarks` | repository benchmark/evidence | 不发布到 dependency repository |

不在没有独立消费者、版本边界和依赖收益时新增 BOM、runtime-api、schema-model、integration-test 或 compiler-adapter module。

## 5. Consumer dependency model

External Java 8 consumer 需要三类 artifact：

- `soma-annotations`：compile；
- `soma-runtime-core`：compile/runtime；
- `soma-processor`：build-only，同时供 javac plugin discovery 和 annotation processing。

Consumer build 必须显式激活 SOMA transformer，不能依赖偶然 classpath discovery。最终 Maven snippet 由 external-consumer fixture 生成和验证；其语义等价于：

```xml
<dependencies>
  <!-- soma-annotations: compile -->
  <!-- soma-runtime-core: compile/runtime -->
  <!-- soma-processor: provided/build-only for javac plugin discovery -->
</dependencies>

<plugin>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <source>1.8</source>
    <target>1.8</target>
    <compilerArgs>
      <arg>-Xplugin:SomaValue</arg>
    </compilerArgs>
    <!-- soma-processor also appears on annotationProcessorPaths -->
  </configuration>
</plugin>
```

Plugin provider 和 processor FQN 以 compiler integration contract 为准。G4 前必须有一个不继承 root parent、不依赖 reactor 隐式 classpath 的 external Maven fixture，证明 published-shape artifacts 可独立消费。

External-consumer evidence 必须从同一次验证安装的 artifacts 构造 Maven runtime dependency graph，至少证明 `soma-runtime-core` 在 runtime graph、`soma-processor` 不在 runtime graph。手工只执行 `java -cp target/classes` 可以补充证明 generated class 无隐藏 linkage，但不能替代 Maven scope evidence。验证应使用隔离的 local repository，避免把旧 snapshot 或用户全局 Maven state 当作当前 artifact。

## 6. Dependency policy

V1 baseline：

- `soma-annotations` 和 `soma-runtime-core` runtime graph 只有 JDK；
- generated application runtime 不需要第三方库；
- 新增任何第三方 dependency 前必须有正式设计决策，说明 owner、用途、public/transitive exposure、license、security、size 和替代方案；
- test/benchmark/build dependency 与 production dependency 分开审查；
- third-party type 不进入 public/generated signature；
- 不在 POM 中添加 arbitrary repository；依赖只从批准的 central/internal repository 解析；
- dynamic/range/SNAPSHOT dependency 不进入 release build。

Benchmark harness 允许在 evidence owner 决策后引入 JMH 等 test-only tool，不改变 runtime zero-dependency rule。

## 7. Plugin and toolchain pinning

Root parent 统一拥有：

- Java source/target；
- encoding；
- internal artifact version；
- compiler/test/jar/source/javadoc/enforcer plugin version；
- dependency inspection plugin version；任何`tree`、`build-classpath`、`resolve-plugins`
  invocation使用root property绑定的exact
  `org.apache.maven.plugins:maven-dependency-plugin:<version>:<goal>`坐标，不能依赖
  Maven prefix解析或调用方本机plugin cache；
- reproducible build timestamp policy；
- CI/wrapper Maven version。

Module POM 不重复版本，不覆盖 toolchain，除非 module owner contract 有明确理由。Plugin version upgrade 是 build-governance change，必须运行 reactor、consumer 和相关 golden。

V1 compiler adapter 只以 full JDK 8 javac 为 compiler authority。实施阶段只声明记录过的本机环境验证通过；public RC/release 只支持正式 G6 matrix 中有证据的 full JDK 8 javac/runtime 组合。JDK 9+、ECJ 或其他 compiler 不得仅因为 `source=8` 就被视为支持。

`soma-processor` 自举编译使用 forked full-JDK javac，并在 JDK 8-only Maven profile 中把 `${java.home}/../lib/tools.jar` 作为 system-scoped build input。该 JDK artifact 不进入 published POM 的跨 JDK dependency claim、不进入 generated/application runtime graph，也不能被第三方 `jdk.tools` substitute 替代。Processor 自身编译关闭 annotation processing，避免尚未构建的 service provider 自加载。

## 8. Reproducible artifacts

Release build 必须做到：

- same commit + same declared toolchain -> byte-for-byte reproducible artifact，或记录不可复现字段和修复 gate；
- generated source/resource 不包含 local path、timestamp、random id；
- archive entry time 使用 `project.build.outputTimestamp`；
- source order、manifest、service provider、resource order stable；
- locale/timezone 不改变 schema hash/generated output；
- source/javadoc jar 与 binary 使用同一 source revision；
- artifact checksum 进入 release report。

Reproducibility 必须通过两次 clean build 和 checksum comparison 证明，不能只由 POM property 推断。

## 9. CI baseline

Implementation 开始后的 required CI lanes：

1. documentation governance；
2. JDK 8 reactor verify；
3. compile/golden/determinism；
4. external Maven consumer；
5. runtime invariant/error path；
6. package smoke；
7. benchmark smoke（达到 G5 后）；
8. dependency/license/security checks（出现第三方 dependency 或 public release 前）。

PR fast lane 可以缩短 benchmark scale，但不得把 smoke 写成 claim-grade。Release gate 使用 pinned commit/artifact 和正式 report。

## 10. Maven reactor rules

- root POM 是唯一 version/plugin/dependency coordination owner；
- module build 必须也能在 `-pl <module> -am` 下工作；
- generated source 位于 Maven generated-sources directory；
- test fixtures 不进入 main artifact；
- examples/benchmarks 不被 production artifact transitive 引用；
- `mvn validate` 只证明 reactor model 可解析，不代表 compile/test/package 成功；
- release 只接受 clean reactor output。

## 11. Repository hygiene

- `target/`、generated build output、IDE workspace 和 local repository 不提交；
- checked-in golden/fixture 必须有明确 owner 和 deterministic regeneration path；
- Maven Wrapper 文件是 repository-owned toolchain input；
- CI workflow、POM、wrapper、consumer fixture 变化必须同步更新 build contract 或 guide；
- machine-local JDK/Maven path 不进入正式 build files。

## 12. Evidence

Build-governance change 至少运行：

```text
./scripts/check.sh

# 或按 surface 拆分执行：
./scripts/check-docs.sh
./mvnw -B -ntp validate
./mvnw -B -ntp verify        # implementation exists after
git diff --check
```

Validation closeout 同时记录第 2.1 节要求的 JDK、Wrapper/Maven、OS、architecture 和命令；仅列出 `Java 8` 或“本机通过”不构成充分 evidence。

Compiler/processor change 额外运行 external consumer、clean/incremental compare 和 unsupported compiler negative fixture。

## 13. 非目标

本文不定义 Gradle plugin、Bazel、OS package、Docker image、native packaging、multi-release JAR、JPMS module、Maven Central credentials 或 IDE-specific setup。
