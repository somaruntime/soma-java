# Java-only SOMA V1 G1 schema-processing report

Gate：G1 annotation schema gate
状态：passed
唯一 Owner：`soma-processor`
Evidence contributors：`soma-annotations`、`soma-testkit`
执行日期：2026-07-11
执行人：Codex，在唯一完整 V1 Goal 下
验证 commit：`060a6df`（`feat: complete phase 5 capability breadth`）
artifact version：`0.1.0-SNAPSHOT`
输出 artifact：`soma-processor/reports/java-v1-g1-schema-processing-report.md`

## 1. 结论

Commit `060a6df` 的 annotation/schema-processing breadth 满足 [G1 required evidence](../../docs/validation-gates.md)：schema-backed table carrier、immutable `@SomaValue`、required/optional field state、primitive/String/enum/value/semantic scalar、strict floating access、schema default、key/index/unique/order、List/Map child ownership、normalization、exact schema hash与breaking diagnostics均已进入最终 V1 contract 对应的实现路径，并由仓库 fixture、golden、独立 Maven consumer和结构化错误路径验证。

G1 状态为 `passed`，失败项与豁免项均为无。该结论只覆盖 G1；唯一 Goal `完成完整 Java-only SOMA V1.0，并通过 G0–G6。` 继续保持 active。本报告不构成 G3/G4/G5/G6、V1.0 RC、public release、性能优势或跨平台支持声明。

## 2. Capability 与 Owner

本 Gate 直接验证以下 Capability：

| Capability | Gate 前状态 | Gate 后状态 | G1 evidence |
|---|---|---|---|
| `V1-ANNOTATION-SCHEMA` | `in-progress` | `evidenced` | 完整 table/value/type/optional/default/key/access/child annotation surface、retention/target/default metadata、valid/invalid matrix |
| `V1-PROCESSING-MODEL` | `in-progress` | `evidenced` | collection、recursive value flatten、selector/ownership validation、default normalization、stable diagnostics |
| `V1-SCHEMA-HASH` | `in-progress` | `in-progress` | G1 exact canonical JSON/SHA-256 已通过；Capability 仍保留 G2/G4 compatibility evidence |
| `V1-CHILD-OWNERSHIP` | `in-progress` | `in-progress` | G1 declaration graph/List/Map/cycle/key-kind evidence 已通过；G3/G4 runtime/package evidence 仍由对应 Gate 关闭 |
| `V1-MATERIALIZATION` | `in-progress` | `in-progress` | G1 schema-backed shape、List/Map shape和budget declaration evidence 已通过；G2/G3/G4仍保留 |
| `V1-SECURITY-INTEGRITY` | `in-progress` | `in-progress` | schema name/path injection、annotation identity、resource growth和diagnostic exposure evidence；最终贯穿 G0–G6 |
| `V1-EVIDENCE-TOOLING` | `in-progress` | `in-progress` | compile/golden/hash/diagnostic/external-consumer helpers；最终贯穿 G1–G5 |

`V1-ANNOTATION-SCHEMA` 和 `V1-PROCESSING-MODEL` 的最终 Gate 均为 G1/G2；其全局 `evidenced` 状态须与同次 [G2 report](java-v1-g2-code-generation-report.md) 配对理解。其他 Capability 未因 G1 通过而提前关闭其余正式 Gate。

唯一事实 Owner 保持不变：public annotation semantics 由 `soma-annotations/docs/annotation-schema-contract.md` 拥有；collection/validation/normalization/hash/diagnostics 由 `soma-processor/docs/schema-processing-contract.md` 拥有；compiler effective shape、generated API、child runtime与materialization runtime分别继续由其正式 Owner拥有。本报告只记录 evidence，不拥有或改写设计事实。

## 3. Schema evidence

### 3.1 Public declaration surface

`target/phase1-public-api.N2AFix` 从实际 JAR 重建 83 项 public/provider/runtime classification，并将 875 行 `javap -public` 与 committed golden 精确比较；`AnnotationContractConsumer` 对 SOURCE retention、FIELD/TYPE/PACKAGE target、annotation member与默认值执行 Java 8 compile/run。`@SomaDefault` 与既有 table/value/type/field/optional/key/access/child declaration 进入同一 public manifest，没有未分类 public type或历史排除身份扩散。

### 3.2 Valid schema breadth

`target/phase5-breadth.K7CTTu` 和 commit 后 targeted repeat `target/phase5-breadth.YJbpHd` 验证：

- required/optional String、enum、nested/scalar `@SomaValue`；
- boolean、byte、short、int、long、float、double、String、enum及 DATE/TIME/DATE_TIME default normalization；
- ordinary NaN/infinity default 与 strict selector path non-finite rejection边界；strict `-0.0` canonicalize为 `0.0`；
- String key full equality/hash collision、Map<String, Row> keyed child、List/Map recursive schema-backed materialization；
- required value的递归 leaf defaults与optional reference absence；
- key、index、unique、order及value/composite selector normalization；
- canonical schema显式包含default literal/normalized value、reference/enum/value materialized type、child container/key facts，runtime-only capacity/plan hint不进入logical schema hash。

既有 dense/keyed/access/child golden 分别由 `target/phase1-generated-dense.4XOb8Y`、`target/phase2-generated-keyed.xJmSe0`、`target/phase3-access.BVqqoU`、`target/phase4-child.5QCUpv` 复验，证明 Phase 5 additive breadth 未改写已接受 declaration 的既有 normalized semantics。

### 3.3 Immutable value与ownership graph

`target/phase0-compiler.Snm2Mc` 验证同 compilation unit/cross-unit 可见的 effective final class、public-final field、canonical constructor、value equality/hash/toString及classfile golden；mutable write、member conflict、generic/local value、ignored state、value cycle和annotation spoof均 fail closed。

List/Map child declaration、dense/keyed row-kind、Map key匹配、direct/indirect ownership cycle及cross-schema edge由 Phase 4 fixture与table diagnostic matrix验证。Schema object和Java Collection仅是输入快照/ detached materialization shape，不是 runtime live storage事实。

### 3.4 Diagnostic matrix

以下执行产物记录了stable diagnostic code、source element location和阻止codegen的结果，且检查无内部 stack 泄漏：

- `target/defaults-phase5.TgBTCY/javac.log`：default落在key/optional/child/outer-value、invalid enum literal、strict NaN、strict nested value non-finite及value-key default；
- `target/value-modifiers-phase5.yjarrf/{fqn,imported}.log`：`@SomaValue` field 上 `@SomaKey`/`@SomaChild`/`@SomaOptional` 以 `SOMA-VALUE-003`拒绝；ordinary同名annotation不被误识别；
- `target/phase1-table-diagnostics.xDRXwR/*.log`：carrier shape、constructor、capacity、child shape/cycle、selector placement/type/collision、optional primitive及generated name collision；
- `target/phase0-compiler.Snm2Mc/*.log`：activation、schema/value declaration、cycle、injection、wildcard identity、mutation/conflict等compiler/schema基础错误；
- `target/phase2-generated-keyed.xJmSe0/invalid-keyed.log`：key declaration/shape fail-closed。

这些报告不把message全文当作唯一机器语义；stable code、severity、location和blocking behavior由fixture约束。

## 4. Canonical、hash、clean/incremental evidence

- `target/phase5-breadth.K7CTTu` 的 default环境与 Turkish locale / `Pacific/Kiritimati` clean build对generated source、schema JSON和`.sha256`逐文件比较；
- 同一source执行clean build后touch schema source并non-clean package，classes与generated sources均和clean snapshot逐文件相等；
- `target/phase0-external-consumer.3bCPA4` 对package-owned schema执行真实metadata rename、non-clean重编译、hash变化、resource count和restore，恢复后schema/hash/class与clean snapshot相等；
- `target/phase0-compiler.Snm2Mc` 对direct javac默认/alternate locale output执行recursive diff；
- breadth committed schema JSON的文件SHA-256为 `ddf7a29f55819bfe4803820f50ee08290cdae7691afb19f97333656df78bb232`，其logical schema hash为 `eb4ebee10e77b7f22ada9ad59d9fe064e8a867a7ca03fae105664a3f056dc3ad`。

上述结果证明当前 Maven/full-javac-8 lane 的确定性和受测 incremental behavior；不外推 IDE incremental compiler或未验证build tool。

## 5. Validation commands 与环境

项目根目录预先设置：

```text
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home
./scripts/check.sh
git diff --check

# commit 060a6df 后，针对最终 addMetrics input/self-consistency refinement：
./scripts/check-keyspace-phase2.sh
./scripts/check-breadth-phase5.sh
```

`./scripts/check.sh` 实际串行执行：

```text
./scripts/check-docs.sh
./mvnw -B -ntp verify
./scripts/check-public-api.sh
./scripts/check-compiler-phase0.sh
./scripts/check-runtime-core-phase1.sh
./scripts/check-keyspace-phase2.sh
./scripts/check-generated-keyed-phase2.sh
./scripts/check-access-phase3.sh
./scripts/check-child-phase4.sh
./scripts/check-testkit-phase4.sh
./scripts/check-value-modifiers-phase5.sh
./scripts/check-defaults-phase5.sh
./scripts/check-breadth-phase5.sh
./scripts/check-table-diagnostics-phase1.sh
./scripts/check-generated-dense-phase1.sh
./scripts/check-external-consumer.sh
git diff --check
```

结果：完整命令 exit `0` 并输出 `project-check: ok`；commit后两个targeted命令再次通过。G1相关positive、negative、golden、clean/non-clean、locale/timezone、consumer和public manifest全部通过；失败项无；豁免项无。

实际环境：

- JDK vendor/version/build：Azul Systems, Inc. Zulu OpenJDK `1.8.0_492-b09`，Zulu `8.94.0.17-CA-macos-aarch64`，VM build `25.492-b09`；
- compiler authority：本机完整 JDK 8 `javac 1.8.0_492`，不是新JDK `--release 8`；
- Maven Wrapper：Apache Maven `3.9.16`，revision `2bdd9fddda4b155ebf8000e807eb73fd829a51d5`；
- OS：macOS `26.5.2` build `25F84`，Darwin `25.5.0`；
- architecture：machine `arm64`，JVM/Maven `aarch64`。

## 6. Artifact identity

| Artifact | SHA-256 |
|---|---|
| `soma-annotations-0.1.0-SNAPSHOT.jar` | `682aec1e79ae236eeb354f8aa6999458123a1286dfdf3ab3fd541fdb96def3e8` |
| `soma-processor-0.1.0-SNAPSHOT.jar` | `cfea0bbe4dfff4ee41532bc980dcd865ecb4a30e6328680702e200215f75eb27` |
| `soma-runtime-core-0.1.0-SNAPSHOT.jar` | `af6bc36dd120f32976249acbd2642406005670da8edf1446693a87c09f82bc07` |

`target/` evidence目录是可重复生成的本机执行产物；durable evidence是commit `060a6df`中的实现、fixtures、expected schema/hash、goldens、scripts和本报告。

## 7. V1 scope non-regression

- Capability状态只发生真实evidence驱动的变化：`V1-ANNOTATION-SCHEMA`、`V1-PROCESSING-MODEL`在G1/G2配对后由`in-progress`进入`evidenced`；其余受影响Capability保持`in-progress`直到原最终Gate；
- 全部23项V1 Capability仍在正式Ledger；Phase 6 formal examples、Access Pattern Cards、benchmark JSONL、package/reproducibility、License/SCM/contact/provenance/support matrix仍保留原Phase 6/G5–G6；
- SomaTable宪法、唯一Owner、Capability Ledger、Gate定义和release claim未改变；正式Owner只做与最终实现一致的additive/exact refinement；
- current normalized model、logical schema/hash、diagnostic family和public annotation是最终V1架构，无temporary schema、temporary public/generated API或test-only bypass；
- canonical live storage/hot path没有引入schema object、DTO graph、`List<Row>`、reflection、metadata interpreter、Java Stream、boxing或per-row allocation；
- 后续工作是additive completion或contract-preserving internal refinement，不要求迁移public/generated consumer、核心事实或canonical hot path；不存在必须标记`blocked`的migration/rewrite。

## 8. Known limitations 与release claim

- `SOMA_UNSUPPORTED_JAVAC` 未设置，本次完整 `check.sh` 的unsupported-javac negative lane为 `skipped`；full JDK 8上的missing plugin/plugin-only/identity-spoof均已验证；
- 本机结果只证明记录的Zulu JDK 8/macOS/arm64组合，不能外推其他JDK vendor/minor、OS或architecture；正式support matrix仍属于G6；
- IDE/JPS/ECJ、Gradle/Ant、其他javac family和cross-vendor incremental不在本报告passed claim内；
- G3/G4必须由各自唯一Owner report独立关闭；G5 formal examples/benchmark与G6 release evidence尚不由本报告证明；
- Apache-2.0正式License artifact、SCM/contact、signing/provenance、reproducibility与support matrix不由G1覆盖。

允许引用的结论仅为：commit `060a6df` 的 G1 annotation schema gate在上述本机环境与完整命令下通过，且V1 scope未回退。不得据此声明V1.0 RC、release ready、public package ready、性能优势或跨平台支持。
