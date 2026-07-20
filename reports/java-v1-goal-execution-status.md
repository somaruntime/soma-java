# Java-only SOMA V1 Goal execution status

类型：Report / Goal 状态

状态：blocked（G6真实发布事实不足；不能完成 Goal）

Owner：SOMA Java Goal 状态输出

受众：项目 Owner、维护者与 Gate reviewer

事实范围：完整 Java-only V1 Goal、功能/Gate状态、验证记录和当前阻塞

非事实范围：重新定义 Blueprint/Design 或授权 release

适用版本：最后 implementation-affecting baseline `b991f4c`

输入事实源：当前 Gate reports、专题治理 reports与可重放验证输出

最后审查日期：2026-07-20

更新日期：2026-07-20
唯一 Codex Goal：`完成完整 Java-only SOMA V1.0，并通过 G0–G6。`
Goal thread：`019f4bf2-6fb4-7d71-ad13-72e1abe9ba03`
packed/exact v3切换提交：`4b6fa43 perf: adopt packed exact indexes and swap removal`；post-cutover功能/性能尾项代码基线：`b991f4c docs: define keyspace as primary locator`；完整evidence见两份dated专题报告

本文件是中断恢复和进度审计入口，不是 Design。目标与正式语义从 `docs/blueprints/`、`docs/design/` 进入；当前代码由代码本身拥有，并通过 `docs/implementation-map/` 导航。

2026-07-11专题治理已完成对完整功能V1的无缩水再审计：P0、V1 blocker和required P1均关闭，G0-G5在Zulu JDK 8完整`./scripts/check.sh`上fresh通过，独立reviewer最终PASS。完整findings、Capability矩阵、package重放边界与evidence见[`soma-java-v1-topical-governance-report.md`](soma-java-v1-topical-governance-report.md)。该专题不替代本文件的原Goal；G6仍blocked。

2026-07-17经用户明确批准，项目完成首个公开发布前的Packed Index / Exact Access / IndexBuffer breaking cutover：删除Sparse Set、maintained order与dirty selector rebuild，keyed/dense统一swap-remove，exact index改为mutation-boundary eager incremental维护，public row-index sequence改为epoch-bearing `IndexSnapshot`，runtime/generated/plan protocol升为v3。正式Owner、consumer、Schema/hash/golden、examples、benchmark与Gate同步迁移；当前事实与evidence见[`2026-07-17-packed-exact-index-runtime-redesign-report.md`](2026-07-17-packed-exact-index-runtime-redesign-report.md)。该迁移不处理也不解除G6。

2026-07-20完成post-cutover尾项治理：固化caller-responsibility Index契约，按distinct-group cardinality收紧exact-index容量，优化single snapshot，FJSP machine selection改为application-owned indexed heap，拆分exact-index source emitter，并保留/澄清`KeySpace` primary-locator术语。当前实现与component/FJSP A/B evidence见[`2026-07-20-packed-exact-index-post-cutover-closeout-report.md`](2026-07-20-packed-exact-index-post-cutover-closeout-report.md)。该治理不处理也不解除G6。

2026-07-20正式启用设计驱动文档体系：Blueprint、Design、Implementation Map、Conformance与Engineering成为当前入口；32份旧Owner按迁移审计标记superseded或改为current-executable Report。该切换只改变文档权威与导航，不改变代码、能力、Gate或G6状态；详见[`2026-07-20-documentation-framework-cutover-report.md`](2026-07-20-documentation-framework-cutover-report.md)。

## 1. 当前总进度

| 总体工作 | 状态 | 可核验出口 |
|---|---|---|
| Phase 0–Phase 5：compiler、generated API、runtime 完整 V1 breadth | completed | commits 至 `060a6df`；Phase 0–5、G0–G4 reports；21 项 Capability evidenced |
| Phase 6：四个正式场景、Access Pattern Cards、benchmark、release mechanics | implementation-complete | 70 个 scenario source、200 个 generated type、711 个 Java 8 class；20 条真实 integrated benchmark workload；License/POM/source/javadoc/package/security scripts |
| 集中验证与修复 | passed | Zulu 与 Corretto 两套完整 JDK 8均得到`project-check: ok`；post-fix benchmark两vendor通过；package/security diagnostic通过 |
| G5 examples/benchmark gate | passed | examples 与 benchmark contributor reports；root G5 closeout report |
| G6 release readiness | blocked | 本地 release mechanics 已落地；真实 SCM/contact、namespace ownership、signing/publishing provenance、clean public history 与最终授权仍缺失 |
| V1 总 Goal | blocked | G6 未通过，禁止标记 completed、公开发布、tag 或声明 release ready |

Phase 0–Phase 6 只是同一 V1 Goal 的实施顺序。这里没有 v0.x、MVP、Lite、Basic 或缩水后的替代目标。

## 2. 历史 Capability evidence inventory

以下ID保留为既有实施/Gate evidence的追踪标签，不再构成当前Design、路线图或平行能力事实源；当前目标、规范和差距分别由Blueprint、Design和Conformance拥有。

22 项为 `evidenced`：

- `V1-ANNOTATION-SCHEMA`、`V1-COMPILER-LOWERING`、`V1-PROCESSING-MODEL`、`V1-SCHEMA-HASH`、`V1-PUBLIC-COMPATIBILITY`；
- `V1-GENERATED-API`、`V1-DENSE-STORAGE`、`V1-ROW-PIPELINE`、`V1-COLUMN-ACCESS`、`V1-KEYED-IDENTITY`、`V1-ACCESS-STRUCTURES`、`V1-MUTATION`；
- `V1-CHILD-OWNERSHIP`、`V1-MATERIALIZATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-RUNTIME-PLAN`、`V1-PERFORMANCE-SHAPE`、`V1-SECURITY-INTEGRITY`；
- `V1-EVIDENCE-TOOLING`、`V1-CONSUMER-PACKAGE`、`V1-SCENARIO-BENCHMARK`。

`V1-RELEASE-EVIDENCE` 为 `blocked`：本地 package/reproducibility/SBOM/security/license mechanics 已实施，但真实组织发布边界和 clean immutable public provenance 未满足。该 Capability 仍完整保留在 V1，不是 optional、dropped 或 deferred。

## 3. Gate 状态

| Gate | 状态 | 主要报告 |
|---|---|---|
| G0 | passed | `reports/java-v1-g0-scope-freeze-report.md` |
| G1 | passed | `soma-processor/reports/java-v1-g1-schema-processing-report.md` |
| G2 | passed | `soma-processor/reports/java-v1-g2-code-generation-report.md` |
| G3 | passed | `soma-runtime-core/reports/java-v1-g3-runtime-core-report.md` |
| G4 | passed | `reports/java-v1-g4-package-smoke-report.md` |
| G5 | passed | `reports/java-v1-g5-examples-benchmark-gate-report.md` |
| G6 | blocked | `reports/java-v1-g6-release-readiness-report.md` |

因此“完整 V1 功能范围 + G0–G5”的功能 RC 边界已满足；它不是可公开发布的 RC artifact。当前 artifact 仍是 `0.1.0-SNAPSHOT`，G6 未通过前不得公开分发或声明正式支持矩阵。

## 4. 集中验证记录

### 4.1 2026-07-11 专题治理 fresh validation

实现提交`aa7a466`上执行：

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home SOMA_UNSUPPORTED_JAVAC=/opt/homebrew/opt/openjdk/bin/javac ./scripts/check.sh
```

结果`project-check: ok`；20条benchmark lane各聚合2次measurement，36条serialized negative artifact均fail closed。最后一次现存benchmark目录为`target/benchmark-smoke.PJkPCM`。clean package mechanics在`2490406`已通过；`aa7a466`的fresh package重放在项目编译前因Maven Central TLS中断，当前实现内容由Maven verify和多组external Maven consumer覆盖，具体限制不作为G6证据并在专题报告§12披露。

### 4.2 Phase 6 原集中验证记录

完整命令：

```text
./scripts/check.sh
env JAVA_HOME=/tmp/corretto8-soma/Contents/Home PATH=/tmp/corretto8-soma/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin ./scripts/check.sh
```

Zulu 环境：Azul Zulu OpenJDK `1.8.0_492-b09`、`javac 1.8.0_492`、Maven Wrapper `3.9.16`、macOS `26.5.2` / Darwin `25.5.0`、arm64/aarch64。最终结果`project-check: ok`。Phase 6 examples：`target/phase6-examples.Wt58xz`；post-fix benchmark：`target/benchmark-smoke.LY9fqL`。

Corretto 环境：Amazon Corretto `1.8.0_492-b09` / `8.492.09.2`、`javac 1.8.0_492`、Maven Wrapper `3.9.16`、macOS `26.5.2` / Darwin `25.5.0`、arm64/aarch64。完整结果`project-check: ok`；examples：`target/phase6-examples.BxKJHi`；post-fix focused benchmark：`target/benchmark-smoke.YL4tuu`，20+20 records、12 negative通过。

两次均验证 compiler fixtures、schema/hash、public/generated API、runtime invariants、external Maven consumer、四场景、错误/生命周期/stats、20 条 benchmark workload、JSONL strict validator、Java 8 class major 52 和 `git diff --check`。本机结果只证明上述环境，不外推为其他 OS/architecture/JDK vendor 的正式支持承诺。

Release diagnostics：`SOMA_PACKAGE_ALLOW_DIRTY=true ./scripts/package-smoke.sh` passed，evidence `target/package-smoke.f8IUL8`；`OSV_SCANNER=/tmp/osv-scanner-v2.3.8-darwin-arm64 ./scripts/security-release-scan.sh` passed，evidence `target/security-release-scan.3iyUxx`。两者明确dirty/unsigned，不替代G6。

## 5. V1 scope non-regression

- Capability：21 项既有 `evidenced` 状态未回退；`V1-SCENARIO-BENCHMARK` 从 `in-progress` 进入 `evidenced`；`V1-RELEASE-EVIDENCE` 从 `not-started` 进入实施后因外部发布事实不足保持 `blocked`。
- Owner、正式语义与Gate经用户批准按packed/exact v3目标先行迁移，没有为实现捷径反向降低Capability或release claim。
- 四个场景使用 generated live facade 和正式 runtime path；benchmark 只记录真实执行的 20 条 minimum integrated workload，不把 generic kernel 换名冒充证据。
- packed/exact breaking migration已在首个公开发布前一次完成；后续功能性能工作应是additive completion或contract-preserving internal refinement，不应再次迁移public/generated API、核心事实、consumer或canonical hot path。
- 未引入 temporary public/generated contract、temporary storage/hot path、test-only bypass、未来 migration 或 rewrite。

## 6. 当前唯一剩余工作

G6 只能在以下真实事实补齐并在 clean immutable candidate 上重放后关闭：SCM/project/issue URL、真实 maintainer/support/private-security contact、namespace ownership、CODEOWNERS/community policy、Apache-2.0 最终授权确认、非禁用历史身份的公开 Git provenance、签名或 OIDC provenance、publishing endpoint/account，以及经批准的正式支持矩阵。未经明确发布授权不 push、tag 或 publish。
