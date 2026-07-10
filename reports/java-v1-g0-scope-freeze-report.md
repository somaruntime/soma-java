# Java-only SOMA V1 G0 scope-freeze report

Gate：G0 Java-only scope freeze
状态：passed
唯一 Owner：root
执行日期：2026-07-10
执行人：Codex，在用户明确授权的完整 V1 Goal 下
输入 commit：`03f04a1e043e08bc4b5ede3f296b19897bff730e`
输入 artifact version：`0.1.0-SNAPSHOT`
输出 artifact：`reports/java-v1-g0-scope-freeze-report.md`
输入事实源：[正式设计文档索引](../docs/README.md) 与各 module `docs/README.md`

## 1. Outcome

Commit `03f04a1` 的 Java-only SOMA V1 scope 已满足 [G0 required evidence](../docs/validation-gates.md)：项目边界、模块 Owner、架构、schema/API/runtime contract、correctness/performance/security、build/version/release、non-goals、实施顺序、capability ledger、V1.0 RC 完成边界和 release claim boundary 均已进入正式 Owner 文档，且没有临时文档充当事实源。

相对于上一次 G0 输入 commit `5a27642`，本次重新执行还纳入了项目所有者确认的发布身份与验证边界：组织/发布主体 HGTECH、产品品牌 SOMA、`com.hgtech.soma`、`soma-*` artifact、Apache-2.0 推荐方向、历史排除身份零扩散，以及“本机实现验证不能外推为 G6 support matrix”。这些变化强化发布治理，没有删除或降级任何 V1 capability。

G0 结论：`passed`。

该结论只冻结 V1 scope 并允许进入 Phase 0 implementation。它不表示任何 Java capability 已实现，不表示 G1-G6 通过，也不表示 package、benchmark、public release 或 production readiness。

## 2. 输入范围

### 2.1 Root formal owners

- `docs/soma-table-design-constitution.md`；
- `docs/architecture-design.md`；
- `docs/domain-glossary.md`；
- `docs/build-and-dependency-contract.md`；
- `docs/public-api-compatibility-contract.md`；
- `docs/generated-table-api-contract.md`；
- `docs/materialization-contract.md`；
- `docs/runtime-correctness-model.md`；
- `docs/runtime-performance-model.md`；
- `docs/security-model.md`；
- `docs/implementation-strategy.md`；
- `docs/validation-gates.md`；
- `docs/versioning-and-release-contract.md`。

### 2.2 Module formal owners

- `soma-annotations/docs/annotation-schema-contract.md`；
- `soma-processor/docs/compiler-integration-contract.md`；
- `soma-processor/docs/schema-processing-contract.md`；
- `soma-processor/docs/code-generation-contract.md`；
- `soma-runtime-core/docs/table-store-contract.md`；
- `soma-runtime-core/docs/runtime-lifecycle-contract.md`；
- `soma-runtime-core/docs/runtime-plan-contract.md`；
- `soma-runtime-core/docs/runtime-errors-and-diagnostics-contract.md`；
- `soma-runtime-core/docs/runtime-performance-implementation-contract.md`；
- `soma-testkit/docs/testkit-contract.md`；
- `soma-examples/docs/runtime-state-schema-examples.md` 及场景 Owner 文档；
- `soma-benchmarks/docs/benchmark-evidence-contract.md`；
- `soma-benchmarks/docs/runtime-state-benchmark-contract.md`。

共检查 32 份正式设计文档；32 份均声明唯一 `Owner` 并进入同级 `docs/README.md`。

## 3. 审查与验证步骤

执行：

```text
./scripts/check.sh
  -> scope-check: ok
  -> doc-check: ok
  -> 7/7 Maven reactor modules SUCCESS
  -> project-check: ok

git diff --check
  -> passed

sh -n scripts/check-v1-scope.sh scripts/check-docs.sh scripts/check.sh
  -> passed

sh scripts/check-v1-scope.sh
  -> scope-check: ok

/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home/bin/java -version
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home ./mvnw -version
sw_vers
uname -srm
  -> validation environment captured
```

实际验证环境：

- JDK：Azul Systems Zulu OpenJDK `1.8.0_492-b09`，Zulu `8.94.0.17`，64-Bit Server VM build `25.492-b09`；
- Maven Wrapper：Wrapper `3.3.4`，Apache Maven `3.9.16`；
- OS：macOS `26.5.2` build `25F84`，Darwin `25.5.0`；
- architecture：`arm64` / Maven 报告 `aarch64`。

本次结果只表示上述环境和输入 commit 通过；正式 JDK vendor/minor、runtime JVM、OS 与 architecture support matrix 仍属于 G6 required evidence，当前不得由本机结果外推。

附加人工审查：

- 对照 SomaTable 宪法原则十五与完整 V1 capability ledger；
- 对照 Phase 0-6 首次进入、完整出口与 G0-G6；
- 检查 implementation strategy、validation gates、Agent guard 和 PR checklist 的 stop/closeout 规则；
- 检查正式文档没有引用 `docs/temp/` 作为事实源；
- 检查四份长期研究蓝图均声明 `状态：长期研究蓝图` 和 `正式事实源：否`；
- 检查普通实施蓝图声明 `正式事实源：否`，且 capability scope 仍由正式 implementation strategy 拥有；
- 检查 module dependency direction、Java 8 toolchain 和 no-third-party runtime baseline 未变化。

## 4. 通过项

| G0 area | 结论 |
|---|---|
| Java-only boundary | 通过；不包含 Python、C ABI、native runtime 或 FFI |
| Unique Owner | 通过；32/32 正式文档有唯一 Owner |
| Module boundary | 通过；六模块责任和依赖方向已正式化 |
| Schema/API/runtime contract | 通过；annotation、processing/codegen、generated API、TableStore/lifecycle/plan/errors/performance 均有 Owner |
| Correctness/performance/security | 通过；模型、实现纪律、evidence obligation 和 claim boundary 已正式化 |
| Build/version/release | 通过；JDK 8、Wrapper/reactor、artifact、compatibility、G6 blocker 和 rollback 已正式化 |
| Release identity | 通过；HGTECH/SOMA、`com.hgtech.soma`、`soma-*`、Apache-2.0 方向和历史身份零扩散边界已正式化 |
| RC/support boundary | 通过；完整 V1 功能 + G0-G5 才构成功能 RC，本机 validation 不替代 G6 support matrix |
| Implementation scope | 通过；唯一 V1 目标、Phase 语义和单调收敛原则已固化 |
| Capability traceability | 通过；23 个 Capability ID 被 scope check 锁定 |
| Gate/report path | 通过；G0-G6 Owner、路径、状态和 blocking rule 已固化 |
| Temporary-source isolation | 通过；临时蓝图不拥有正式事实，也不作为 Gate 输入 |
| Agent/PR enforcement | 通过；Agent hard stop、slice closeout 和 PR non-regression checklist 已固化 |

## 5. V1 scope non-regression

### 5.1 唯一目标和单调收敛

- 唯一产品实施目标是完整 Java-only SOMA V1；
- Phase 0 至 Phase 6 只是 implementation checkpoint，不是 `v0.x`、MVP、Lite、Basic 或独立产品目标；
- 未进入当前 Phase 的 capability 仍保留在 V1，不能被移入未批准的新版本或 indefinite backlog；
- 每个 Phase 必须是最终 V1 架构的有效子集；后续只能 additive completion 或 contract-preserving internal refinement；
- public/generated consumer migration、核心事实迁移或 canonical hot-path rewrite 会阻塞 phase closeout；
- implementation PR 不能为合理化 shortcut 而反向修改宪法、Owner、ledger、non-goal 或 Gate。

### 5.2 Capability inventory

以下 23 项 capability 均存在于正式 ledger，G0 只确认 scope 和 Owner chain；Java implementation 状态仍为 `not-started`：

```text
V1-ANNOTATION-SCHEMA
V1-COMPILER-LOWERING
V1-PROCESSING-MODEL
V1-SCHEMA-HASH
V1-PUBLIC-COMPATIBILITY
V1-GENERATED-API
V1-DENSE-STORAGE
V1-ROW-PIPELINE
V1-COLUMN-ACCESS
V1-KEYED-IDENTITY
V1-ACCESS-STRUCTURES
V1-MUTATION
V1-CHILD-OWNERSHIP
V1-MATERIALIZATION
V1-RUNTIME-LIFECYCLE
V1-RUNTIME-ERRORS
V1-RUNTIME-PLAN
V1-PERFORMANCE-SHAPE
V1-SECURITY-INTEGRITY
V1-EVIDENCE-TOOLING
V1-CONSUMER-PACKAGE
V1-SCENARIO-BENCHMARK
V1-RELEASE-EVIDENCE
```

Compiler feasibility spike 只证明 `V1-COMPILER-LOWERING` 的最小机制可行，不改变其 `not-started` implementation 状态，也不能替代 G2/G4 evidence。

相对上一次 G0 evidence，23 项 capability 状态均保持 `not-started -> not-started`。本次只更新正式 release/validation governance 与 G0 证据身份，没有把设计、spike、空 reactor build 或本机 smoke 转换为 capability implementation evidence。

### 5.3 Scope lock identity

```text
docs/implementation-strategy.md
  sha256 5dc5438808d7963375ce18b9833b7a57555fa549c3f0a87866c2f00264dd8334

docs/validation-gates.md
  sha256 a1f6f13659a1e75670b1cfe32d2e220f99344782ccde23dcc6ce5c6476576eaa

AGENTS.md
  sha256 2c0babfdae3c74a0e7cc842e4a50012ce8571d769d808d9be900c8bb79e23f72

scripts/check-v1-scope.sh
  sha256 28c40d061330e53ccb584dbba21d0cb58be0201b8306f67e3760b5df7c31be33
```

Scope check 要求原则十五、防缩水协议、non-regression Gate、Agent/PR guard 和每个 Capability ID 恰好存在一次。任何 deliberate scope change 必须同时产生正式 Owner、ledger、Gate 和 scope-lock diff，不能静默删除 capability。

## 6. 失败项与豁免

- 失败项：无；
- 豁免项：无；
- 隐式延期：无；
- 移出 V1 的 capability：无。

## 7. Known limitations

- 仓库仍没有 Java functionality source；
- G1-G6 全部为 `not-started`；
- real processor/generated/runtime external consumer 尚不存在；
- GitHub-hosted CI 尚未在 remote repository 执行；
- benchmark/performance claim 尚无实现证据；
- Apache-2.0 仍只是已确认的推荐方向；正式 `LICENSE`、POM license metadata、namespace ownership、SCM/contact、publishing/signing/provenance 和 support matrix 继续阻塞 G6，但不阻塞 Phase 0；
- 当前 Maven reactor SUCCESS 只证明 build skeleton 和 governance checks，不证明 product behavior。

## 8. 允许引用的结论

允许声明：

- commit `03f04a1` 的 Java-only SOMA V1 scope 已完成 G0 freeze；
- 23 项 V1 capability、Owner chain、Phase/Gate 和禁止替代已被正式 ledger 和 scope check 固化；
- V1.0 RC 是完整功能 checkpoint；HGTECH/SOMA 发布身份和本机 validation/G6 support-matrix 边界已冻结；
- 项目可以按唯一完整 V1 目标进入 Phase 0；
- Phase 不能被改写成 `v0.x`、MVP、Lite、Basic 或独立完成目标。

不允许声明：

- annotation/compiler/processor/runtime/generated API 已实现；
- G1-G6 任一 Gate 已通过；
- package、examples 或 benchmark 可运行；
- public release、Maven Central、production 或性能 ready；
- 临时蓝图、feasibility spike 或本机 reactor build 已证明产品 capability。

## 9. 下一入口

正式实施入口是 [Phase 0 compiler/build vertical slice](../docs/implementation-strategy.md#phase-0compilerbuild-vertical-slice)。当前 Codex Goal 只有一个：“完成完整 Java-only SOMA V1.0，并通过 G0-G6”；Phase 0-6 只作为 plan checkpoint，不能独立完成总 Goal。

Phase 0 将从 `V1-ANNOTATION-SCHEMA`、`V1-COMPILER-LOWERING`、`V1-PROCESSING-MODEL`、`V1-SCHEMA-HASH`、`V1-EVIDENCE-TOOLING` 和 `V1-CONSUMER-PACKAGE` 的最终架构子集开始。任何确实缺失的 public/schema/runtime 语义必须回到唯一 Owner 决策，不能由实现捷径补写。
