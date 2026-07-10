# Java-only SOMA V1 G0 scope-freeze report

Gate：G0 Java-only scope freeze
状态：passed
唯一 Owner：root
执行日期：2026-07-10
执行人：Codex，在用户明确授权的完整 V1 Goal 下
输入 commit：`23462527ba3fd350bd288c6d75273a98207ff4bf`
输入 artifact version：`0.1.0-SNAPSHOT`
输出 artifact：`reports/java-v1-g0-scope-freeze-report.md`
输入事实源：[正式设计文档索引](../docs/README.md) 与各 module `docs/README.md`

## 1. Outcome

Commit `2346252` 的 Java-only SOMA V1 scope 满足 [G0 required evidence](../docs/validation-gates.md)：项目边界、模块与唯一 Owner、schema/API/runtime contract、correctness/performance/security、build/version/release、non-goals、Capability Ledger、Phase 0-6 checkpoint 语义、V1.0 RC 完整性和 release claim boundary 均位于正式 Owner 文档，没有 `docs/temp/`、README、report 或实现代码反向拥有产品语义。

自上一份 G0 evidence 后，Phase 0 在正式 Owner 中固化了 stable javac plugin/provider identity、不可伪造 activation handshake、canonical value effective shape、schema graph/hash/resource ownership、diagnostic code family、public/provider/internal manifest 和真实 Maven consumer graph。这些决策关闭 implementation 细节并强化 fail-closed/compatibility 边界；没有修改 SomaTable 宪法、Capability Ledger、最终 Gate 或 release scope。

G0 结论保持 `passed`。该结论只冻结完整 V1 scope；Phase 0 compiler/build checkpoint 的通过不表示 G1-G6、V1.0 RC、package 或 release readiness。

## 2. Scope inventory

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
- `soma-examples/docs/runtime-state-schema-examples.md` 及场景 Owner；
- `soma-benchmarks/docs/benchmark-evidence-contract.md`；
- `soma-benchmarks/docs/runtime-state-benchmark-contract.md`。

正式索引、Owner 与生命周期由 `docs/documentation-governance.md` 管理；普通报告和四份长期研究蓝图都不是设计事实源。

## 3. Frozen decisions

| G0 area | 冻结结论 |
|---|---|
| Product boundary | Java 8 annotation schema + Java columnar runtime；不承诺 Python、C ABI、native runtime 或 FFI |
| Identity | 发布主体 HGTECH、产品 SOMA、`com.hgtech.soma`、`soma-*`；Apache-2.0 方向；历史排除身份零扩散 |
| Ownership | 每项正式事实只有一个 Owner；root 管跨模块 contract，module 管实现义务 |
| Carrier | `@SomaTable` 是 public top-level detached carrier、public no-arg、public mutable schema field；不自动 write-back |
| Value compiler | full JDK 8 javac plugin `SomaValue` lower immutable `@SomaValue`；processor-only/unsupported compiler fail closed |
| Runtime | packed/primitive/static binding/fused/no-per-row allocation；禁止 schema object、DTO/Collection graph、reflection/metadata interpreter 成为 live hot path |
| Scope convergence | 唯一目标为完整 V1；Phase 0-6 只是 checkpoint，后续只允许 additive completion/internal refinement |
| RC/release | V1.0 RC 必须是完整 V1 功能 + G0-G5；总 Goal 和正式 release 还要求 G6 |
| Validation | 本机 full JDK 8/Maven/OS 只证明该环境；正式 support matrix 属于 G6 |

## 4. Commands and environment

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
  -> full JDK 8 matrix: ok

git diff --check
  -> passed

java -version
./mvnw -version
sw_vers
uname -a
shasum -a 256 docs/implementation-strategy.md docs/validation-gates.md \
  AGENTS.md scripts/check-v1-scope.sh
  -> scope identity captured
```

实际环境：

- Azul Systems Zulu OpenJDK `1.8.0_492-b09`，Zulu `8.94.0.17`，VM build `25.492-b09`；
- full JDK `javac 1.8.0_492`；
- Apache Maven Wrapper distribution `3.9.16`，revision `2bdd9fddda4b155ebf8000e807eb73fd829a51d5`；
- macOS `26.5.2` build `25F84`，Darwin `25.5.0`；
- `arm64` / JVM-Maven `aarch64`。

本次结果不能外推为跨 vendor/minor、OS 或 architecture 支持；正式 support matrix 仍为 G6 `not-started`。

## 5. Scope lock identity

| Artifact | SHA-256 |
|---|---|
| `docs/implementation-strategy.md` | `5dc5438808d7963375ce18b9833b7a57555fa549c3f0a87866c2f00264dd8334` |
| `docs/validation-gates.md` | `a1f6f13659a1e75670b1cfe32d2e220f99344782ccde23dcc6ce5c6476576eaa` |
| `AGENTS.md` | `2c0babfdae3c74a0e7cc842e4a50012ce8571d769d808d9be900c8bb79e23f72` |
| `scripts/check-v1-scope.sh` | `28c40d061330e53ccb584dbba21d0cb58be0201b8306f67e3760b5df7c31be33` |
| `docs/build-and-dependency-contract.md` | `6ece644db4dcdbd6b8184036265bb626fc267d15b440faa2695b9109f210f7c0` |
| `docs/public-api-compatibility-contract.md` | `60bb99b07433a3ae6473ef936948311fb05165b09ba1527f394837642d293405` |
| `soma-annotations/docs/annotation-schema-contract.md` | `599fb2de88f54e21f269b982303009d40fb4bd5bdd99a3522cc0a8502559988b` |
| `soma-processor/docs/compiler-integration-contract.md` | `7a02d59962737e412b80d42a0e268773cc82856a04cfe8f23ec40fb6525828ef` |
| `soma-processor/docs/schema-processing-contract.md` | `2f8d5065ea4a8a7614786a7e63238baba07d0851bce6571ce3935b2c6dae1a56` |
| `soma-testkit/docs/testkit-contract.md` | `427e76b24c01407b98c564392a79855991d1ac63c6124ba8fa5ffdfcaab23479` |

Scope check 要求原则十五、防缩水协议、Agent/PR guard、non-regression Gate 和 23 个 Capability ID 均存在。任何 deliberate scope change 必须产生正式 Owner、Ledger、Gate 和 scope-lock diff，不能由实现或 report 静默改变。

## 6. Capability status

G0 冻结并保留以下 23 项 V1 Capability：

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

Phase 0 后，`V1-ANNOTATION-SCHEMA`、`V1-COMPILER-LOWERING`、`V1-PROCESSING-MODEL`、`V1-SCHEMA-HASH`、`V1-PUBLIC-COMPATIBILITY`、`V1-SECURITY-INTEGRITY`、`V1-EVIDENCE-TOOLING`、`V1-CONSUMER-PACKAGE` 为 `in-progress`；其余 15 项为 `not-started`。具体状态变化和 evidence 见 [Phase 0 report](java-v1-phase-0-compiler-build-report.md)。没有 Capability 被删除、waive、降为 optional 或移出 V1。

## 7. Gate status

| Gate | 状态 | 结论 |
|---|---|---|
| G0 | `passed` | 完整 V1 scope、Owner、Ledger、Gate、RC/release claim 已冻结 |
| G1 | `not-started` | 完整 annotation schema required evidence 尚未落地 |
| G2 | `not-started` | Phase 0 foundation 不等于完整 processor/codegen gate |
| G3 | `not-started` | runtime core required evidence 尚未落地 |
| G4 | `not-started` | 完整 generated API/package consumer 尚未落地 |
| G5 | `not-started` | formal examples/benchmark evidence 尚未落地 |
| G6 | `not-started` | License/release metadata、support matrix 和 readiness 尚未落地 |

## 8. V1 scope non-regression

- 相对上一份 G0 evidence，八项 Capability 从 `not-started` 进入 `in-progress`，其余 15 项保持 `not-started`；状态变化来自真实 Phase 0 evidence，不改变完整出口；
- 未实现 breadth 仍保留在原 Phase 1-6 和 G1-G6，没有 `dropped`、`optional`、indefinite backlog 或新版本迁移；
- SomaTable 宪法、implementation strategy/Capability Ledger、validation gates 和 version/release claim 没有变化；
- Owner contract 的变化是 stable compiler identity、canonical semantics、diagnostic/API manifest 和 consumer graph 的 additive/fail-closed refinement，不降低目标；
- 当前 public annotation、compiler handshake、canonical schema/hash 和 external build shape 是最终 V1 架构的有效子集；后续通过 additive completion 或 contract-preserving internal refinement 收敛；
- 没有 temporary public/generated API、temporary canonical live storage/hot path、migration、rewrite 或 test-only bypass；
- 当前实现不要求未来迁移公共契约、核心事实或 canonical main path，因此 G0/Phase 0 不因 non-regression 条件而 blocked。

## 9. Failures, waivers and limitations

- G0 失败项：无；
- G0 豁免项：无；
- 隐式延期或移出 V1：无；
- G1-G6 尚未开始，不能被 G0 或 Phase 0 evidence 替代；
- runtime/generated API/examples/benchmark/release breadth 尚未实现；
- remote CI、正式 License artifact、SCM/contact、signing/provenance、reproducible release 和 support matrix 尚无 evidence；
- 当前本机 JDK 8 结果不是正式 support declaration。

## 10. Release claim boundary

允许声明：

- commit `2346252` 的完整 Java-only SOMA V1 scope 已通过 G0 freeze；
- 23 项 Capability、唯一 Owner、Phase/Gate 和禁止替代继续被正式 Ledger 与 scope check 固化；
- Phase 0 compiler/build checkpoint 已通过，但相关 Capability 仍为 `in-progress`；
- V1.0 RC 必须覆盖全部 V1 功能和 G0-G5，正式 release/总 Goal 还必须通过 G6。

不允许声明：

- G1-G6 任一 Gate 已通过；
- 完整 annotation/compiler/processor/runtime/generated API 已实现；
- V1.0 RC、package、benchmark、public release、production、性能或跨平台支持 ready；
- 本机 smoke、Phase 0 fixture 或 Maven `0.1.0-SNAPSHOT` 定义了缩小产品目标。

## 11. 下一入口

Phase 0 已作为同一 Goal 下的 checkpoint 关闭。下一入口是 Phase 1 dense table 最小闭环；它必须继续完整 V1 的 primitive/packed/static-binding/fused 架构，并同步产出 correctness 与 performance-shape evidence。总 Goal 继续保持 active，直到 G0-G6 全部通过。
