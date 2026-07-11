# Java-only SOMA V1 G4 generated API/package gate report

状态：passed
Gate：G4 generated API/package gate
Owner：root
执行日期：2026-07-11
执行人：Codex
Goal：`完成完整 Java-only SOMA V1.0，并通过 G0–G6。`（继续 active）

本报告只证明 G4 required evidence 在本文记录的本机环境通过。它不构成 V1.0 RC、public release、正式支持矩阵、性能优势或 G5/G6 通过声明。

## 1. 验证对象与 artifact

- implementation commit：`060a6df feat: complete phase 5 capability breadth`；
- reactor artifact：`com.hgtech.soma:soma-annotations:0.1.0-SNAPSHOT`、`com.hgtech.soma:soma-runtime-core:0.1.0-SNAPSHOT`、`com.hgtech.soma:soma-processor:0.1.0-SNAPSHOT`；
- external consumer artifact：`com.example.soma:external-maven-breadth-phase5-consumer:1.0.0-SNAPSHOT`；
- 可重放输入：`soma-testkit/src/test/fixtures/external-maven-breadth-phase5/`；
- 可重放验证入口：`scripts/check-breadth-phase5.sh` 与 `scripts/check.sh`；
- 完整 `check.sh` 中的 breadth evidence目录：`target/phase5-breadth.K7CTTu`；implementation commit后再次执行的targeted breadth evidence目录：`target/phase5-breadth.YJbpHd`；这些目录是可删除的运行产物，正式长期证据是已提交fixture、expected schema/hash、脚本和本报告；
- canonical schema hash：`eb4ebee10e77b7f22ada9ad59d9fe064e8a867a7ca03fae105664a3f056dc3ad`；
- implementation commit后targeted external consumer JAR SHA-256：`c9ee527700d4b77e2e84b47e2cd9afe0a7ed8d644b43e18e31dc185818209139`。该 checksum只绑定本次SNAPSHOT validation artifact，不是G6 immutable release checksum/provenance evidence。

## 2. Package topology 与 compiler activation

external consumer 的 POM 不继承 root reactor parent，使用独立 Maven project 和独占 local repository。验证先把发布形态 artifact 安装到该 repository，再由 consumer 显式配置：

- `soma-annotations` 为 compile dependency；
- `soma-runtime-core` 为 compile/runtime dependency；
- `soma-processor` 为 provided/build-only dependency；
- `maven-compiler-plugin` 同时传入 `-Xplugin:SomaValue` 并声明 annotation processor path；
- compilation 使用本机完整 JDK 8 `javac`，没有用新 JDK `--release 8` 冒充 supported compiler；
- runtime dependency tree 仅含 `soma-annotations` 和 `soma-runtime-core`，没有 `soma-processor`、`jdk:tools` 或第三方 runtime dependency。

实际 dependency graph：

```text
external-maven-breadth-phase5-consumer
+- com.hgtech.soma:soma-annotations:jar:0.1.0-SNAPSHOT:compile
+- com.hgtech.soma:soma-runtime-core:jar:0.1.0-SNAPSHOT:compile
\- com.hgtech.soma:soma-processor:jar:0.1.0-SNAPSHOT:provided
   \- jdk:tools:jar:1.8:system
```

runtime graph：

```text
external-maven-breadth-phase5-consumer
+- com.hgtech.soma:soma-annotations:jar:0.1.0-SNAPSHOT:compile
\- com.hgtech.soma:soma-runtime-core:jar:0.1.0-SNAPSHOT:compile
```

## 3. Required evidence 结果

| G4 required evidence | 结果 | 可核验证据 |
|---|---|---|
| 独立 external Maven Java 8 consumer | passed | fixture 无 root parent；独占 repository 中 `clean package` 和直接 `java` 运行成功 |
| transformer + processor 显式激活 | passed | compiler plugin 的 `-Xplugin:SomaValue` 与 `annotationProcessorPaths`；lowered `@SomaValue` consumer 编译运行 |
| recursive schema object / List / Map child | passed | required/optional value、String-keyed `Map<String,StringKeyRow>` child live access 和 detached materialization |
| default / explicit MaterializationBudget | passed | row、Key Pipeline、recursive child 默认/显式 overload；row limit typed failure |
| schema/compiler/runtime metadata | passed | checked-in canonical schema JSON/hash、generated compatibility metadata、runtime public/protocol manifest |
| RuntimePlan / stats metadata | passed | plan identity/child/access facts由 Phase 1–4 external consumers回归；本 fixture验证 reserve、operation scratch、KeySpace capacity/used/probe/collision/rehash/reset stats |
| generated public API | passed | 从所有 generated source 重建 manifest，对 19 个顶层 public type 逐一执行 exact public `javap`；没有 internal handle/registry/state 泄漏 |
| primitive/static runtime shape | passed | required/optional primitive、String、enum、flat/nested value、String key、optional bitmap word boundary、packed/live child路径均通过；source-shape检查拒绝 Collection key hot path |
| Java 8 target | passed | consumer、schema class、lowered/generated companion 共 71 个 `.class` 全部为 classfile major `52` |
| clean/incremental determinism | passed | clean输出复制后，对同一 source 执行 non-clean recompilation，classes 与 generated source 逐文件等价 |
| locale/timezone determinism | passed | 默认环境与 `tr_TR` / `Pacific/Kiritimati` clean build 的 generated source、canonical schema、schema hash 逐文件一致 |
| package/runtime separation | passed | dependency tree、runtime tree、runtime classpath证明 processor未进入 application runtime graph |

external consumer 还覆盖：完整 scalar/String/enum/value defaults、semantic DATE/TIME/DATE_TIME defaults、optional reference clear、65-row optional bitmap跨 word 扫描、String hash collision full equality、duplicate/missing/null key、compaction、staged KeySpace replacement、reserve、materialization budget error、released lifecycle error与递归 child materialization。

## 4. 实际执行命令

顶层验证命令：

```text
./scripts/check-breadth-phase5.sh
./scripts/check.sh
git diff --check
```

G4 脚本实际执行的关键命令形状：

```text
./mvnw -B -ntp -Dmaven.repo.local=<evidence>/repository \
  -pl soma-runtime-core,soma-processor -am install -DskipTests
./mvnw -B -ntp -Dmaven.repo.local=<evidence>/repository \
  -f <evidence>/consumer/pom.xml clean package
MAVEN_OPTS='-Duser.language=tr -Duser.country=TR -Duser.timezone=Pacific/Kiritimati' \
  ./mvnw -B -ntp -Dmaven.repo.local=<evidence>/repository \
  -f <evidence>/repeat-consumer/pom.xml clean package
./mvnw -B -ntp -Dmaven.repo.local=<evidence>/repository \
  -f <evidence>/consumer/pom.xml package
./mvnw -B -ntp -Dmaven.repo.local=<evidence>/repository \
  -f <evidence>/consumer/pom.xml \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree
$JAVA_HOME/bin/javap -classpath <consumer>/target/classes -public \
  com.example.soma.breadth.generated.<GeneratedType>
$JAVA_HOME/bin/javap -verbose <each-consumer-class>
$JAVA_HOME/bin/java -cp <consumer-classes>:<runtime-classpath> \
  com.example.soma.breadth.BreadthConsumer
```

## 5. Validation environment

- JDK vendor/version/build：Azul Systems, Inc. Zulu `1.8.0_492-b09`，Zulu `8.94.0.17-CA-macos-aarch64`，64-Bit Server VM build `25.492-b09`；
- compiler：`javac 1.8.0_492`；
- Maven Wrapper / Maven：Apache Maven `3.9.16`，distribution build `2bdd9fddda4b155ebf8000e807eb73fd829a51d5`；
- OS：macOS `26.5.2`，build `25F84` / Darwin `25.5.0`；
- architecture：`arm64`（Maven 报告为 `aarch64`）；
- 结果：通过项如第 3 节；失败项 `0`；waived 项 `0`；
- 完整`check.sh`最终返回`project-check: ok`；此后只增加KeySpace metrics输入自洽保护，implementation commit `060a6df`上重新执行keyspace与breadth targeted validation并通过；
- 跳过：完整仓库 validation 中可选的 unsupported-javac 环境 lane 因未设置 `SOMA_UNSUPPORTED_JAVAC` 未执行；supported JDK 8 fail-closed/compiler negative matrix仍由 Phase 0 与 Phase 5 diagnostics evidence覆盖。

## 6. V1 scope non-regression

受影响 Capability：`V1-ANNOTATION-SCHEMA`、`V1-COMPILER-LOWERING`、`V1-PROCESSING-MODEL`、`V1-SCHEMA-HASH`、`V1-PUBLIC-COMPATIBILITY`、`V1-GENERATED-API`、`V1-DENSE-STORAGE`、`V1-ROW-PIPELINE`、`V1-COLUMN-ACCESS`、`V1-KEYED-IDENTITY`、`V1-CHILD-OWNERSHIP`、`V1-MATERIALIZATION`、`V1-RUNTIME-PLAN`、`V1-RUNTIME-ERRORS`、`V1-PERFORMANCE-SHAPE`、`V1-SECURITY-INTEGRITY`、`V1-EVIDENCE-TOOLING`、`V1-CONSUMER-PACKAGE`。这些能力的 G4 evidence 从 `in-progress` 推进为 `evidenced`。

- 没有 Capability 被删除、optional 化、waive、移入新版本或移出原 Gate；
- Phase 6 的 formal examples、Access Pattern Cards、benchmark runner/JSONL 和 G5 evidence完整保留；G6 的 License artifact、SCM/contact、source/javadoc/checksum/signing/provenance、reproducibility、support matrix、known limitations和release review完整保留；
- Owner 身份、Capability Ledger、Gate 定义与 release claim boundary没有变化；Owner 文档仅把既有 V1语义落实为可执行的 exact contract；
- external consumer 直接使用最终命名的 generated Table/Batch/Rows/Keys/child/materialization API，不存在后续 consumer migration；
- canonical live storage/hot path仍是 packed primitive/reference columns、presence words、primitive/hash KeySpace、primitive sidecar和static generated traversal；没有 `List<Row>`、DTO graph、reflection、metadata interpreter、Stream、boxing/per-row allocation被当作 runtime hot path；
- 没有 temporary public/generated API、temporary storage、test-only bypass、主路径 migration 或 rewrite。后续只允许 additive completion 或 contract-preserving internal refinement。

## 7. Known limitations 与允许结论

- 本机通过只证明上述 JDK/OS/architecture 和命令组合通过，不能外推为 JDK vendor/minor、OS 或 architecture正式支持矩阵；
- artifact仍为 `0.1.0-SNAPSHOT` validation artifact，不是 immutable signed release artifact；
- G5 examples/benchmark 与 G6 release evidence 尚未通过，因此不得声明 V1.0 RC、release ready或公开发布；
- 本报告没有绝对性能硬指标或性能优势结论；Phase 6仍必须产出 required benchmark runner和结构化 JSONL evidence；
- unsupported-javac 可选环境 lane本次未执行，不改变正式 support matrix尚未开始的状态。

release claim 可引用的最强结论仅为：在本文记录的本机完整 JDK 8 环境中，SOMA Phase 5 artifact 可以通过独立 Maven consumer显式激活 transformer与processor，生成并运行完整 Java 8 generated API/package smoke，且 build/runtime dependency graph保持分离。G4 状态为 `passed`。
