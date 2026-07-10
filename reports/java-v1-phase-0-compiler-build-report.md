# Java-only SOMA V1 Phase 0 compiler/build checkpoint report

Checkpoint：Phase 0 compiler/build vertical slice
状态：passed
唯一协调 Owner：root
执行日期：2026-07-10
执行人：Codex，在唯一完整 V1 Goal 下
实现 commit：`23462527ba3fd350bd288c6d75273a98207ff4bf`
artifact version：`0.1.0-SNAPSHOT`
输出 artifact：`reports/java-v1-phase-0-compiler-build-report.md`

## 1. Outcome

Commit `2346252` 满足 [实现策略](../docs/implementation-strategy.md)定义的 Phase 0 出口：full JDK 8 javac direct compilation 与独立 Maven consumer 看到同一 `@SomaValue` effective type；plugin/processor 双向 activation、unsupported compiler、annotation identity 歧义和 source conflict 均 fail closed；classfile、canonical schema/hash、clean repeatability、真实 incremental mutation、public API manifest 及 application runtime dependency graph 均有可重复验证的 fixture。

Phase 0 状态为 `passed`，但它只关闭 compiler/build foundation checkpoint。相关 Capability 仍为 `in-progress`，G1-G6 仍为 `not-started`；本报告不构成 G2、G4、V1.0 RC、release readiness、正式 package 或 support matrix 声明。

## 2. Slice declaration

### 2.1 完整目标与 Capability

唯一产品目标保持为“完成完整 Java-only SOMA V1.0，并通过 G0-G6”。本切片涉及：

- `V1-ANNOTATION-SCHEMA`；
- `V1-COMPILER-LOWERING`；
- `V1-PROCESSING-MODEL`；
- `V1-SCHEMA-HASH`；
- `V1-PUBLIC-COMPATIBILITY`；
- `V1-SECURITY-INTEGRITY`；
- `V1-EVIDENCE-TOOLING`；
- `V1-CONSUMER-PACKAGE`。

### 2.2 唯一 Owner

| Capability / surface | 唯一事实 Owner |
|---|---|
| annotation/value schema | `soma-annotations/docs/annotation-schema-contract.md` |
| javac 8 lowering/activation | `soma-processor/docs/compiler-integration-contract.md` |
| normalized model/hash/diagnostics | `soma-processor/docs/schema-processing-contract.md` |
| public/internal/provider classification | `docs/public-api-compatibility-contract.md` |
| build graph/external consumer | `docs/build-and-dependency-contract.md` |
| integrity boundary | `docs/security-model.md` |
| evidence helpers | `soma-testkit/docs/testkit-contract.md` |

### 2.3 本切片出口

- direct javac 与 Maven external consumer 观察相同 canonical constructor、`equals`、`hashCode` 和 `toString`；
- transformer/processor 任一缺失、`-XD` 伪造、JDK 9+ adapter mismatch、annotation 同名欺骗、wildcard import 歧义及冲突 source declaration 稳定失败；
- 同一输入在普通环境与 Turkish locale/不同时区生成 byte-identical class/resource；
- schema fact 真实变更会重编译、更新 package-stable resource，恢复后与 clean golden 一致；
- processor 为 build-only，runtime Maven graph 包含 `soma-runtime-core` 且不包含 `soma-processor`；
- handwritten public API、build provider 与 internal bytecode surface 有分类和 `javap -public` golden。

### 2.4 仍保留在 V1 的 breadth

- Phase 1-5 / G1-G2：完整 `@SomaTable`、type/optional/default/key/index/unique/order/child annotation schema、完整 normalized model、generated Table/materializer/API 和全部 diagnostic location；
- Phase 1-5 / G2-G4：generated/runtime compatibility metadata、完整 incremental rename/delete stale-output 治理、full external consumer functionality；
- Phase 1 / G3-G4：packed dense storage、primitive columns/presence、Row/Column Pipeline、mutation、materialization、lifecycle/errors/plan 与 performance-shape；
- Phase 2-4 / G2-G4：keyed identity、access structures、child ownership 和 sidecar lifecycle；
- Phase 5 / G1-G4：schema/compiler/runtime compatibility 收口和全量 negative matrix；
- Phase 6 / G5-G6：formal examples、Access Pattern Cards、benchmark JSONL、package/reproducibility、License/release metadata、SCM/contact、signing/provenance 与 support matrix。

### 2.5 禁止捷径

本切片没有使用 processor-only 降级、新 JDK `--release 8`、SOURCE marker/命令行字符串伪造 handshake、simple-name annotation 匹配、partial schema hash、IDE/loose classpath、全局旧 snapshot、test-only bypass、temporary public API 或 temporary runtime storage。Runtime core 尚未进入实现，因此也没有以 DTO/List/metadata interpreter 冒充 hot path。

## 3. 实现与架构摘要

- `soma-annotations` 提供 SOURCE-retained `SomaSchema`、`SomaValue`、`SomaField`、`SomaIgnore`、`SomaSemantic` handwritten API；
- javac 8 plugin `SomaValue` 在 Enter 前识别无歧义 annotation identity，并 lower canonical value effective shape；
- plugin 与 JSR 269 processor 通过同一 javac `Context.Key<Session>` 对象身份握手，命令行和用户 source 无法伪造；
- processor 从 lowered Element model 验证 value/schema、enum 和 nested-value graph，生成 package-stable canonical JSON 与 SHA-256；
- processor/provider service metadata、public API manifest、direct javac fixture 和独立 Maven consumer 共同锁定 build contract；
- generated/application runtime graph 不包含 javac internal 或 processor artifact。

## 4. Validation environment

- JDK：Azul Systems Zulu OpenJDK `1.8.0_492-b09`，Zulu `8.94.0.17`，64-Bit Server VM build `25.492-b09`；
- javac authority：`javac 1.8.0_492`，来自本机完整 Zulu JDK 8；
- Maven Wrapper distribution：Apache Maven `3.9.16`，revision `2bdd9fddda4b155ebf8000e807eb73fd829a51d5`；
- OS：macOS `26.5.2` build `25F84`，Darwin kernel `25.5.0`；
- architecture：`arm64`，Maven/JVM 报告为 `aarch64`；
- unsupported negative lane：Homebrew OpenJDK `javac 25.0.2`，只证明 adapter mismatch 被拒绝，不构成支持。

本机结果只表示上述 commit/artifact 在该环境通过，不能外推为正式 JDK vendor/minor、OS 或 architecture support matrix。正式矩阵仍属于 G6 required evidence，状态为 `not-started`。

## 5. Commands and results

```text
./scripts/check.sh
  -> scope-check: ok
  -> doc-check: ok
  -> Maven reactor 7/7 SUCCESS
  -> public-api-check: ok
  -> compiler-phase0-check: ok
  -> external-consumer-check: ok
  -> project-check: ok

SOMA_UNSUPPORTED_JAVAC=/opt/homebrew/opt/openjdk/bin/javac \
  ./scripts/check-compiler-phase0.sh
  -> javac 25.0.2 rejected with SOMA-COMP-002
  -> full JDK 8 positive/negative matrix: ok

git diff --check
  -> passed

java -version
./mvnw -version
sw_vers
uname -a
shasum -a 256 soma-annotations/target/soma-annotations-0.1.0-SNAPSHOT.jar \
  soma-processor/target/soma-processor-0.1.0-SNAPSHOT.jar \
  soma-runtime-core/target/soma-runtime-core-0.1.0-SNAPSHOT.jar
  -> environment and artifact identity captured
```

提交后本机 evidence directories：

- public API：`target/phase0-public-api.1BxytB`；
- JDK 8 compiler matrix：`target/phase0-compiler.kZ3IVX`；
- external Maven consumer：`target/phase0-external-consumer.cFQI8y`；
- JDK 25 fail-closed + JDK 8 matrix：`target/phase0-compiler.NsLYKj`。

这些 `target/` 目录是可重新生成的本机执行产物；durable evidence 是 commit `2346252` 中的 scripts、fixtures、goldens 和正式 Owner contract。

## 6. Artifact evidence

| Artifact | SHA-256 |
|---|---|
| `soma-annotations-0.1.0-SNAPSHOT.jar` | `a70e8f33b5089e620520e065811f09af69c7aa620d8baecf4d220de7997d396f` |
| `soma-processor-0.1.0-SNAPSHOT.jar` | `9e7f28b5b6f96f17c56410c7d9470b9c335640fa9a78c05663912ef6396b01a1` |
| `soma-runtime-core-0.1.0-SNAPSHOT.jar` | `3d7dfc0e29f710ec62bd0c35fbf80a9f280d1941208b7a294cf79438a5a0bfa1` |

External consumer 不继承 root parent，使用隔离 local Maven repository 安装同次构建 artifacts；实际 Maven runtime classpath 包含 runtime core、排除 processor，并同时以 Maven runtime graph 和最小 classpath 执行成功。

## 7. Capability status

| Capability | Before | After | 当前层级 evidence |
|---|---|---|---|
| `V1-ANNOTATION-SCHEMA` | `not-started` | `in-progress` | 五个 handwritten annotation、retention/target/default metadata、value fixture |
| `V1-COMPILER-LOWERING` | `not-started` | `in-progress` | JDK 8 effective-type/classfile/runtime golden、activation/unsupported/conflict negative matrix |
| `V1-PROCESSING-MODEL` | `not-started` | `in-progress` | lowered Element validation、schema/value/enum/nested graph diagnostics |
| `V1-SCHEMA-HASH` | `not-started` | `in-progress` | deterministic canonical JSON/SHA-256、locale/timezone repeatability、real mutation/restore |
| `V1-PUBLIC-COMPATIBILITY` | `not-started` | `in-progress` | JAR-derived classification、`javap -public` 和 annotation metadata golden |
| `V1-SECURITY-INTEGRITY` | `not-started` | `in-progress` | unforgeable activation、annotation spoof/injection/size/identity fail-closed fixtures |
| `V1-EVIDENCE-TOOLING` | `not-started` | `in-progress` | public API/compiler/external-consumer scripts 与 committed fixtures/goldens |
| `V1-CONSUMER-PACKAGE` | `not-started` | `in-progress` | isolated external Maven compile/run、runtime dependency graph、incremental mutation |

其余 15 项 Capability 保持 `not-started`，全部仍保留在原 Phase/Gate：`V1-GENERATED-API`、`V1-DENSE-STORAGE`、`V1-ROW-PIPELINE`、`V1-COLUMN-ACCESS`、`V1-KEYED-IDENTITY`、`V1-ACCESS-STRUCTURES`、`V1-MUTATION`、`V1-CHILD-OWNERSHIP`、`V1-MATERIALIZATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-RUNTIME-PLAN`、`V1-PERFORMANCE-SHAPE`、`V1-SCENARIO-BENCHMARK`、`V1-RELEASE-EVIDENCE`。

## 8. Gate status

| Gate | 状态 | 说明 |
|---|---|---|
| G0 | `passed` | scope freeze 已刷新到本 checkpoint 的正式 Owner 状态 |
| G1 | `not-started` | 完整 annotation schema evidence 未落地 |
| G2 | `not-started` | 当前只是 compiler foundation，不是完整 processor/codegen gate |
| G3 | `not-started` | runtime core 尚未实现 |
| G4 | `not-started` | Phase 0 consumer 不包含完整 generated/runtime/package breadth |
| G5 | `not-started` | examples/benchmark 尚未实现 |
| G6 | `not-started` | release metadata、support matrix 和 readiness evidence 尚未落地 |

## 9. V1 scope non-regression

- Capability 变化仅为上述八项 `not-started -> in-progress`；没有 `dropped`、`optional`、waiver 或移出 V1；
- SomaTable 宪法、Capability Ledger、G0-G6 定义和 release claim 未改变；
- 正式 Owner 在编码前/同切片中固化 stable plugin/provider identity、unforgeable handshake、annotation identity、canonical value/hash、schema graph/hash、diagnostic family、public manifest 和真实 consumer graph，这些是 fail-closed/additive refinement，不是为捷径降低契约；
- 五个当前 public annotation 是完整 V1 public schema 的 additive foundation；后续补全 annotation、generated API 和 runtime 不需要迁移已有 consumer contract；
- canonical value lowering、schema resource ownership 和 external build activation 已采用最终 V1 方向，后续只允许 additive completion 或 contract-preserving internal refinement；
- 没有 temporary public/generated API、temporary canonical live storage/hot path、DTO graph、reflection/metadata interpreter、Stream、boxing/per-row allocation hot path或 test-only bypass；
- 达到 V1 不需要迁移当前公共契约、核心事实或主执行路径；因此 checkpoint 不因 migration/rewrite 而 blocked。

## 10. Known limitations

- 完整 V1 annotation/schema/codegen/runtime breadth 尚未实现，相关 Capability 不能标记 `evidenced`；
- source package/schema rename 或删除后的 stale output 清理尚无完整 G2 incremental evidence；
- canonical constructor conflict 的 textual type comparison 对不同 package 的同 simple-name type 可能保守拒绝，完整 symbol-aware diagnostic breadth留在 Phase 5/G2；
- JDK 25 只是 unsupported negative lane；launcher 可能附带 javac plugin initialization stack trace，稳定产品语义仅是 `SOMA-COMP-002` fail-closed；
- CI 只验证了仓库内配置/脚本，本次没有 remote GitHub Actions run；
- `soma-runtime-core` 在 Phase 0 仍为空 JAR，只用于验证最终 runtime dependency edge，未据此声明任何 runtime capability；
- external consumer 隔离 repository 首次填充 Maven plugins 时仍可能需要网络/cache；
- License artifact、SCM/contact、signing/provenance、reproducible release 和 support matrix 均未完成，G6 保持 `not-started`。

## 11. Release claim boundary

允许声明：commit `2346252` 的 Phase 0 compiler/build checkpoint 在记录的本机环境通过，且完整 V1 scope 未回退。

不允许声明：G1-G6 通过、完整 annotation/processor/runtime/generated API 已实现、V1.0 RC、package/release ready、性能优势、跨 JDK/OS/architecture 支持或公开发布就绪。

## 12. 下一入口

下一 checkpoint 是 Phase 1 dense table 最小闭环。它继续同一个完整 V1 Goal，优先落地 primitive/presence/packed storage、generated dense binding、Row Pipeline、mutation、materialization、lifecycle/error/plan minimum 和结构化 allocation-shape evidence；不会创建独立版本或替代目标。
