# Java-only SOMA V1 G2 processor/code-generation report

Gate：G2 processor/codegen gate
状态：passed
唯一 Owner：`soma-processor`
Evidence contributor：`soma-testkit`
执行日期：2026-07-11
执行人：Codex，在唯一完整 V1 Goal 下
验证 commit：`060a6df`（`feat: complete phase 5 capability breadth`）
artifact version：`0.1.0-SNAPSHOT`
输出 artifact：`soma-processor/reports/java-v1-g2-code-generation-report.md`

## 1. 结论

Commit `060a6df` 满足 [G2 required evidence](../../docs/validation-gates.md)：full JDK 8 javac transformer与processor fail-closed activation、`@SomaValue` effective type/classfile、validated normalized model与exact hash、deterministic generated Table/Batch/Rows/Keys/Mutator/materializer、List/Map child及budget overload、primitive/static KeySpace/access binding、Cursor reuse/fused non-materializing path、no-boxing/no-intermediate-collection source/bytecode shape、public/generated manifest和diagnostic matrix均已由可重复脚本验证。

G2 状态为 `passed`，失败项与豁免项均为无。唯一完整 V1 Goal继续 active；本报告不关闭G3/G4/G5/G6，也不构成RC、release readiness、性能优越或跨平台support claim。

## 2. Capability status

| Capability | Gate 前状态 | Gate 后状态 | G2 evidence / 未关闭边界 |
|---|---|---|---|
| `V1-ANNOTATION-SCHEMA` | `in-progress` | `evidenced` | 与G1配对完成完整declaration到generated binding |
| `V1-COMPILER-LOWERING` | `in-progress` | `in-progress` | G2 transformer/effective-class evidence通过；G4 external package activation仍保留 |
| `V1-PROCESSING-MODEL` | `in-progress` | `evidenced` | 与G1配对完成validated normalized model到emitter单向输入 |
| `V1-SCHEMA-HASH` | `in-progress` | `in-progress` | G2 canonical/hash/determinism通过；G4 initialization/package pairing仍保留 |
| `V1-PUBLIC-COMPATIBILITY` | `in-progress` | `in-progress` | handwritten/provider/generated/internal manifest通过；G4/G6仍保留 |
| `V1-GENERATED-API` | `in-progress` | `in-progress` | G2 exact API/golden通过；G4 external package Gate仍保留 |
| `V1-ROW-PIPELINE` | `in-progress` | `in-progress` | G2 generated fusion/Cursor/no-boxing shape通过；G3/G4 runtime Gate仍保留 |
| `V1-COLUMN-ACCESS` | `in-progress` | `in-progress` | primitive/enum optional word-lane与typed view binding通过；G3/G4仍保留 |
| `V1-KEYED-IDENTITY` | `in-progress` | `in-progress` | all primitive/String/enum/value/composite key static binding通过；G3仍保留 |
| `V1-ACCESS-STRUCTURES` | `in-progress` | `in-progress` | index/unique/order/grouped/dynamic source generation通过；G3仍保留 |
| `V1-MUTATION` | `in-progress` | `in-progress` | generated direct/mutator/pipeline mutation、no-key-setter与atomic staging通过；G3仍保留 |
| `V1-CHILD-OWNERSHIP` | `in-progress` | `in-progress` | generated required/optional List/Map child surface通过；G3/G4仍保留 |
| `V1-MATERIALIZATION` | `in-progress` | `in-progress` | generated row/List/Map/Rows/Keys default+explicit budget overload通过；G3/G4仍保留 |
| `V1-PERFORMANCE-SHAPE` | `in-progress` | `in-progress` | packed/static/fused/no-boxing shape通过；G3/G5仍保留 |
| `V1-SECURITY-INTEGRITY` | `in-progress` | `in-progress` | injection/activation/public leakage/resource bound evidence；最终贯穿G0–G6 |
| `V1-EVIDENCE-TOOLING` | `in-progress` | `in-progress` | compile/golden/shape/consumer scripts；最终贯穿G1–G5 |

状态遵守Capability的全部最终Gate，不以G2局部通过提前关闭G3/G4/G5/G6义务。

## 3. Compiler integration evidence

`target/phase0-compiler.Snm2Mc` 由完整JDK 8 direct `javac`执行：

- plugin `SomaValue` 与processor `com.hgtech.soma.processor.SomaProcessor`同次编译双向activation成功；
- class effective final、field public-final、canonical constructor、equals/hashCode/toString对user source、processor element model、generated companion与classfile一致；
- default与Turkish locale/`Pacific/Kiritimati` output递归相等；
- missing plugin、plugin-only、`-XD` spoof、wildcard annotation identity、同名annotation spoof、member conflict、field mutation、generic/local/ignored/cyclic value均按stable code fail closed；
- runtime执行value consumer时不携带processor/annotation compiler artifact。

`target/value-modifiers-phase5.yjarrf`补齐FQN/single-import `@SomaValue` field-role modifier拒绝，并证明ordinary同名annotation不被误lower。Unsupported-javac专用lane本次未设置环境变量，见known limitations。

## 4. Generated artifact 与 exact API

### 4.1 Phase 5完整breadth

`target/phase5-breadth.K7CTTu` 通过完整 `check.sh`；commit后targeted repeat `target/phase5-breadth.YJbpHd`再次通过。两者从generated source重建固定19项top-level type manifest，并对全部类型生成417行`javap -public`：

```text
FullRowBatch / FullRowMutableRow / FullRowMutator / FullRowRow /
FullRowRows / FullRowTable
StringKeyRowBatch / StringKeyRowKeys / StringKeyRowMutableRow /
StringKeyRowMutator / StringKeyRowRow / StringKeyRowRows / StringKeyRowTable
StringParentBatch / StringParentMutableRow / StringParentMutator /
StringParentRow / StringParentRows / StringParentTable
```

Manifest明确检查`reserve(int)`、Keys `fetchAll/findFirst/firstOrThrow(MaterializationBudget)`、table/row materialization budget overload，不允许`runtime.generated`、DenseTableState、child registry/handle等internal protocol泄入generated public API。71个consumer/generated/lowered classfile全部为Java 8 major `52`。

### 4.2 Dense、keyed、access、child生成矩阵

- `target/phase1-generated-dense.4XOb8Y`：Batch/Table/Rows public golden、packed primitive static column binding、default scan/filter/sort/limit/update/remove/materialization；
- `target/phase2-generated-keyed.xJmSe0`：boolean/byte/short/int/long/float/double、enum、semantic scalar、nested/composite value、String key direct/Keys APIs；`HashIntKeySpace`/`HashLongKeySpace`/`HashCompositeKeySpace` static binding、strict float canonicalization、no key setter、staged append/replace publication与metrics carry；
- `target/phase3-access.BVqqoU`：index/unique/order、value selector parameter folding、grouped source与dynamic order public golden；
- `target/phase4-child.5QCUpv`：45项Phase4 generated type exact public golden、required/optional List/Map child facade、recursive materialization与budget overload；
- `target/phase5-breadth.YJbpHd`：String/enum/value optional mutator/materializer、String KeySpace collision equality、String keyed child、reserve/scratch/key metrics及最终addMetrics self-consistency。

Generated code只读取validated normalized model；未在emitter/runtime中重新解释raw annotation metadata。

## 5. Determinism、incremental与hash

- breadth consumer不继承root parent，使用本次evidence独占local Maven repository重新install artifacts，再clean package；
- default与Turkish locale/`Pacific/Kiritimati`两次clean build的generated source、schema JSON和hash逐文件相等；
- 同一source clean后touch schema source执行non-clean package，classes和generated sources与clean snapshot递归相等；
- `target/phase0-external-consumer.3bCPA4`执行package-owned schema metadata mutation/restore，resource保持唯一、hash按事实变化并恢复clean bytes；
- dense/keyed/access/child fixtures各自执行default/repeat locale-timezone generated source与schema/hash comparison；
- breadth expected logical schema hash为`eb4ebee10e77b7f22ada9ad59d9fe064e8a867a7ca03fae105664a3f056dc3ad`。

Generated output不含timestamp、local path或random id；type/method/import顺序由committed golden约束。

## 6. Hot-path shape evidence

G2不设绝对性能硬指标，但以下required structural evidence均通过：

- Row Pipeline generated source/bytecode拒绝Java Stream、Iterator、boxed primitive `valueOf`、`Object[]`/boxed row index、intermediate ArrayList/LinkedList和per-row Cursor/MutableCursor construction；
- non-sorted `count`/`forEach`直接fused traversal，Cursor在terminal invocation内复用；materializing terminal的carrier/List/Map allocation与traversal stats分离；
- primitive/enum Column Pipeline按required/all-present/all-absent/mixed bitmap word lane静态执行，0/63/64边界由breadth consumer验证；
- key lookup/import不使用`HashMap<Key,Integer>`、transient tuple或object key array作为canonical path；String/composite key使用primitive hash substrate与generated full equality；
- access source直接绑定generated sidecar protocol；key mutation setter不生成；stage完成后才publication live columns/KeySpace；
- operation scratch、materialization budget、capacity和key metrics均有显式generated/runtime binding，不以隐藏unbounded temporary collection代替。

该证据只证明shape/correctness，不构成相对或绝对performance advantage claim；benchmark runner与JSONL仍属于G5。

## 7. Diagnostics 与failure shape

- `target/phase0-compiler.Snm2Mc/*.log`：compiler activation/value lowering/schema identity；
- `target/defaults-phase5.TgBTCY/javac.log`：default placement/literal/strict-floating/value-key default；
- `target/value-modifiers-phase5.yjarrf/*.log`：value field role modifier及ordinary same-name control；
- `target/phase1-table-diagnostics.xDRXwR/*.log`：table carrier、constructor、capacity、child、selector、optional primitive和generated-name conflict；
- `target/phase2-generated-keyed.xJmSe0/invalid-keyed.log`：unsupported/invalid key declaration；
- external Maven consumers覆盖duplicate/missing/null key、budget、released/stale/view-pinned、child/materializer carrier failure等generated-to-runtime error mapping。

Invalid declaration阻止schema-specific generated artifact成为可用输出；diagnostic scripts拒绝内部stack泄漏。失败项无；豁免项无。

## 8. Validation commands、环境与结果

项目根目录实际使用：

```text
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home
./scripts/check.sh
git diff --check

# commit 060a6df 后的最终targeted复验
./scripts/check-keyspace-phase2.sh
./scripts/check-breadth-phase5.sh
```

完整 `check.sh` 包含以下与G2直接相关的原始命令：

```text
./mvnw -B -ntp verify
./scripts/check-public-api.sh
./scripts/check-compiler-phase0.sh
./scripts/check-generated-dense-phase1.sh
./scripts/check-generated-keyed-phase2.sh
./scripts/check-access-phase3.sh
./scripts/check-child-phase4.sh
./scripts/check-value-modifiers-phase5.sh
./scripts/check-defaults-phase5.sh
./scripts/check-breadth-phase5.sh
./scripts/check-table-diagnostics-phase1.sh
./scripts/check-external-consumer.sh
git diff --check
```

完整仓库验证exit `0`并输出`project-check: ok`；commit后keyspace与breadth targeted复验再次通过。实际环境：

- Azul Systems, Inc. Zulu OpenJDK `1.8.0_492-b09`，Zulu `8.94.0.17-CA-macos-aarch64`，VM build `25.492-b09`；
- full JDK 8 `javac 1.8.0_492`；
- Maven Wrapper / Apache Maven `3.9.16`，revision `2bdd9fddda4b155ebf8000e807eb73fd829a51d5`；
- macOS `26.5.2` build `25F84`，Darwin `25.5.0`；
- machine `arm64`，JVM/Maven `aarch64`。

Artifact SHA-256：

| Artifact | SHA-256 |
|---|---|
| `soma-annotations-0.1.0-SNAPSHOT.jar` | `682aec1e79ae236eeb354f8aa6999458123a1286dfdf3ab3fd541fdb96def3e8` |
| `soma-processor-0.1.0-SNAPSHOT.jar` | `cfea0bbe4dfff4ee41532bc980dcd865ecb4a30e6328680702e200215f75eb27` |
| `soma-runtime-core-0.1.0-SNAPSHOT.jar` | `af6bc36dd120f32976249acbd2642406005670da8edf1446693a87c09f82bc07` |

`target/`目录是可再生本机evidence；durable evidence是commit `060a6df`内的implementation、fixtures、goldens、expected schemas/hashes、scripts和本报告。

## 9. V1 scope non-regression

- Capability状态严格按全部最终Gate推进：只有`V1-ANNOTATION-SCHEMA`与`V1-PROCESSING-MODEL`在G1/G2配对后进入`evidenced`；G2涉及但还需G3/G4/G5/G6的Capability保持`in-progress`；
- 全部未涉及breadth仍保留原Phase/Gate：Phase 6 formal examples/Access Pattern Cards/benchmark JSONL/package/release metadata/reproducibility/support matrix未删除、optional化或移入新版本；
- SomaTable宪法、唯一Owner、Capability Ledger、Gate和release claim未改变；Owner contract变化仅是与最终实现一致的additive/exact protocol；
- generated Table/Batch/Rows/Keys/Mutator/Column/child/materializer public contract与canonical packed/static/fused path均为最终V1架构的有效实现；后续只允许additive completion或contract-preserving internal refinement；
- 没有temporary V0 facade、temporary public/generated API、temporary canonical storage、temporary hot path、test-only bypass或未来consumer migration；
- canonical hot path没有`List<Row>`、DTO graph、reflection、metadata interpreter、Java Stream、boxing、per-row allocation或`HashMap<Key,Integer>`；
- 达到完整V1不要求迁移public/generated contract、核心事实或主执行路径，因此G2不存在必须标记`blocked`的migration/rewrite。

## 10. Known limitations 与release claim

- 本次未设置`SOMA_UNSUPPORTED_JAVAC`，unsupported-javac negative lane为`skipped`；它不改变本机full JDK 8上的supported activation/missing-plugin/plugin-only fail-closed结果；
- 本机通过不能外推其他JDK vendor/minor、OS或architecture；正式support matrix仍是G6 required evidence；
- IDE/JPS/ECJ、Gradle/Ant、JDK 9+ adapter和其他compiler family不在G2 passed claim内；
- G3 runtime core invariants/performance shape、G4 package/consumer、G5 examples/benchmark、G6 License/release metadata必须由对应唯一Owner report独立关闭；
- G2 shape evidence不等于benchmark evidence或性能优势；绝对性能硬指标当前不属于RC正确性验收，但G5 runner/JSONL仍required；
- Apache-2.0正式License、SCM/contact、source/javadoc/checksum/provenance、reproducibility、support matrix与release sign-off不由本报告完成。

允许引用：commit `060a6df` 的G2 processor/codegen gate在记录环境与完整命令下通过，且V1 scope没有回退。禁止据此单独声明V1.0 RC、release ready、public package ready、正式support matrix或性能优势。
