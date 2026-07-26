# Java-only SOMA V1 Goal execution status

类型：Report / Goal 状态

状态：blocked（G6真实发布事实不足；不能完成 Goal）

Owner：SOMA Java Goal 状态输出

受众：项目 Owner、维护者与 Gate reviewer

事实范围：完整 Java-only V1 Goal、功能/Gate状态、验证记录和当前阻塞

非事实范围：重新定义 Blueprint/Design 或授权 release

适用版本：Transformation/DataFlow production candidate `2aa8c15`；
industrial application trace `586d523`；其余 reference-application baseline
evidence `938b3d5`

输入事实源：当前 Gate reports、专题治理 reports与可重放验证输出

最后审查日期：2026-07-27

更新日期：2026-07-27
唯一 Codex Goal：`完成完整 Java-only SOMA V1.0，并通过 G0–G6。`
Goal thread：`019f4bf2-6fb4-7d71-ad13-72e1abe9ba03`
packed/exact v3切换提交：`4b6fa43 perf: adopt packed exact indexes and swap removal`；post-cutover功能/性能尾项代码基线：`b991f4c docs: define keyspace as primary locator`；完整evidence见两份dated专题报告

本文件是中断恢复和进度审计入口，不是 Design。目标与正式语义从 `docs/blueprints/`、`docs/design/` 进入；当前代码由代码本身拥有，并通过 `docs/implementation-map/` 导航。

2026-07-11专题治理已完成对完整功能V1的无缩水再审计：P0、V1 blocker和required P1均关闭，G0-G5在Zulu JDK 8完整`./scripts/check.sh`上fresh通过，独立reviewer最终PASS。完整findings、Capability矩阵、package重放边界与evidence见[历史专题治理报告](archive/soma-java-v1-topical-governance-report.md)。该专题不替代本文件的原Goal；G6仍blocked。

2026-07-17经用户明确批准，项目完成首个公开发布前的Packed Index / Exact Access / IndexBuffer breaking cutover：删除Sparse Set、maintained order与dirty selector rebuild，keyed/dense统一swap-remove，exact index改为mutation-boundary eager incremental维护，public row-index sequence改为epoch-bearing `IndexSnapshot`，runtime/generated/plan protocol升为v3。正式Owner、consumer、Schema/hash/golden、examples、benchmark与Gate同步迁移；当前事实与evidence见[`2026-07-17-packed-exact-index-runtime-redesign-report.md`](2026-07-17-packed-exact-index-runtime-redesign-report.md)。该迁移不处理也不解除G6。

2026-07-20完成post-cutover尾项治理：固化caller-responsibility Index契约，按distinct-group cardinality收紧exact-index容量，优化single snapshot，FJSP machine selection改为application-owned indexed heap，拆分exact-index source emitter，并保留/澄清`KeySpace` primary-locator术语。当前实现与component/FJSP A/B evidence见[`2026-07-20-packed-exact-index-post-cutover-closeout-report.md`](2026-07-20-packed-exact-index-post-cutover-closeout-report.md)。该治理不处理也不解除G6。

2026-07-20正式启用设计驱动文档体系：Blueprint、Design、Implementation Map、Conformance与Engineering成为当前入口；32份旧Owner按迁移审计标记superseded或改为current-executable Report。该切换只改变文档权威与导航，不改变代码、能力、Gate或G6状态；详见[`2026-07-20-documentation-framework-cutover-report.md`](2026-07-20-documentation-framework-cutover-report.md)。

2026-07-21完成四场景 Blueprint canonical journey 采纳：FJSP、VRP、Simulation和Game的data role、identity、ordering、failure与performance boundary同步到当时的 executable examples，phase-6 fixtures、benchmark lane、Implementation Map、Conformance和developer Report一并重绑。实现基线为`a137b10 feat(examples): adopt four scenario blueprints`；该时点证据见[`2026-07-21-four-scenario-blueprint-adoption-report.md`](2026-07-21-four-scenario-blueprint-adoption-report.md)。该报告现只保留历史 provenance，不再拥有 current G5 或当前示例。

2026-07-23完成Access Model / Candidate Scan产品化治理：建立Point/Candidate/Column/Key/Bulk/Ownership完整访问模型，generated API clean cutover到Table/Scan/Cursor/UpdateCursor/Traversal与Unique point family，runtime采用compact typed plan、source/terminal specialization和stable arg-min；compatibility升级到v4、development version升级到`0.2.0-SNAPSHOT`。实现基线为`fd82eba feat: implement Stage 2 access model`；正式Owner、scope non-regression与证据见[治理报告](2026-07-23-access-model-candidate-scan-governance-report.md)和[性能报告](2026-07-23-access-model-candidate-scan-performance-report.md)。该治理不处理也不解除G6。

2026-07-23随后完成项目复杂度与可维护性治理及后续可持续性审计：processor selector/admission与Table source helper责任核对到`79c0a89`，benchmark lane分责和generated-footprint evidence核对到`8f685e2`，最终正式收口为`75ee658`。该治理在其候选时点保持generated Java、schema/hash、public/generated API、runtime语义、四场景和Gate不变；结论与证据见[项目复杂度与可维护性治理报告](2026-07-23-project-complexity-and-maintainability-governance-report.md)和[复杂度可持续性后续治理报告](2026-07-23-complexity-sustainability-governance-report.md)。

2026-07-23参考应用边界治理进一步解除旧四场景与SOMA产品设计的所有权耦合：`soma-examples`成为工业动态调度与个体生态仿真两个普通Java 8 consumer的聚合器，输入生成与运行时状态严格分离；component benchmark完成领域中性化，应用integrated evidence回到各应用。旧四场景产品Owner、共享JAR、源码/测试/golden/脚本和benchmark dependency已在`955c956`原子退出，所需G0–G5责任由core fixtures、neutral benchmark与两个应用接管。`955c956`已在Azul Zulu full JDK 8重跑完整`./scripts/check.sh`并得到`project-check: ok`；正式Owner、报告、引用闭包与Temporary退役在`f6c3646`收口。当前结论见[参考应用边界治理报告](2026-07-23-reference-application-boundary-governance-report.md)，G6不在本专题范围内。

2026-07-23工业动态调度参考应用进一步完成应用架构治理：建立Problem/Factory/Solver/Runtime/Schema/Result责任和唯一canonical journey，detached Result在runtime关闭后仍可消费，fixture/oracle/verification/benchmark全部退出production source-set，production JAR与package DAG由专项Gate保护。实现候选为`69e5dc6`，正式文档切换为`6ae9eb6`；四profile、10,000-operation long-run、三fork allocation/GC/high-water、isolated clean/repeat与完整`./scripts/check.sh`均通过。该治理不改变SOMA产品语义或G6状态，详见[工业动态调度参考应用架构治理报告](2026-07-23-industrial-dynamic-scheduler-architecture-governance-report.md)。

2026-07-23个体生态仿真参考应用完成同等架构治理：建立Config/Scenario Factory/Simulator/Session/Engine/System/Runtime/Schema/Result责任和唯一canonical journey，detached Result在runtime关闭后仍可消费，oracle/verification/benchmark及非default profile全部退出production source-set，production JAR、package DAG与retired identity由专项Gate保护。实现候选为`287350d`，正式文档切换为`8ca1ab2`；四profile、2,000-tick long-run、逐tick AoS位级等价、物理顺序独立性、三fork allocation/GC/high-water、isolated clean/repeat与完整`./scripts/check.sh`均通过。该治理不改变SOMA产品语义或G6状态，详见[个体生态仿真参考应用架构治理报告](2026-07-23-grassing-individual-simulation-architecture-governance-report.md)。

2026-07-24完成Reference Application大规模性能基线治理：scheduler 建立
1,000/100,000/10,000 operations，simulation 建立
1,000×1,000/100,000×1,000/10,000×10,000 individuals/ticks 的六份
application-owned baseline；每份使用9-fork校准和3-fork普通比较，并由
Fast/Scale/Soak/Full Gate分责。实现候选为`1af43ac`，baseline切换为
`938b3d5`。归因只发现measurement与scheduler应用内部加速问题，没有core defect
证据；最终重放发现的JDK 8私有生成构造器不稳定性由`c0fa1c9`以不改变公开契约
和runtime语义的方式封闭。正式结论见[大规模性能基线治理报告](2026-07-24-reference-application-scale-performance-baseline-governance-report.md)。

2026-07-27 完成 Transformation Model 与 Typed DataFlow 产品化治理：Access
Model 之上建立 Shape/Expression/Operator/Result/Effect 语义，新增独立
`soma-dataflow`、generated `<Table>DataFlow`/keyed Delta、Definition/Template/
one-shot Invocation、safe-point effect 和 managed/borrowed parallel。production
candidate 为 `2aa8c15`；工业调度在不改变 Schema、Solver API 或 primitive
frontier 的前提下，以真实 assignment summary DataFlow 验证应用闭环。构造契约、
48-trial reference differential、external consumer、generated footprint、
3-fork component baseline、三个 profile 5-fork non-regression 与完整
`./scripts/check.sh` 均通过；正式结论见
[Transformation/DataFlow 治理报告](2026-07-27-transformation-dataflow-governance-report.md)。
该治理不处理也不解除 G6。

## 1. 当前总进度

| 总体工作 | 状态 | 可核验出口 |
|---|---|---|
| Phase 0–Phase 5：compiler、generated API、runtime 完整 V1 breadth | completed | commits 至 `060a6df`；Phase 0–5、G0–G4 reports；21 项 Capability evidenced |
| Phase 6：Access Pattern Cards、两个独立参考应用、benchmark、release mechanics | implementation-complete | core fixtures与external consumer；两个application-owned correctness/long-run/multi-fork Gate；20条neutral benchmark workload；License/POM/source/javadoc/package/security scripts |
| 集中验证与修复 | passed | `2aa8c15` 的 generated/runtime v5、transformation/kernel v1、既有 Access/Schema 与 reference applications 在 Azul Zulu full JDK 8 得到 `project-check: ok` |
| G5 reference applications/benchmark gate | passed | core Access/Transformation evidence、neutral/DataFlow component benchmark、两个独立参考应用、六份 profile baseline、Fast/Scale/Soak/Full Gate 与对应治理报告 |
| G6 release readiness | blocked | 本地 release mechanics 已落地；真实 SCM/contact、namespace ownership、signing/publishing provenance、clean public history 与最终授权仍缺失 |
| V1 总 Goal | blocked | G6 未通过，禁止标记 completed、公开发布、tag 或声明 release ready |

Phase 0–Phase 6 只是同一 V1 Goal 的实施顺序。这里没有 v0.x、MVP、Lite、Basic 或缩水后的替代目标。

## 2. 历史 Capability evidence inventory

以下ID保留为既有实施/Gate evidence的追踪标签，不再构成当前Design、路线图或平行能力事实源；当前目标、规范和差距分别由Blueprint、Design和Conformance拥有。

23 项为 `evidenced`：

- `V1-ANNOTATION-SCHEMA`、`V1-COMPILER-LOWERING`、`V1-PROCESSING-MODEL`、`V1-SCHEMA-HASH`、`V1-PUBLIC-COMPATIBILITY`；
- `V1-GENERATED-API`、`V1-DENSE-STORAGE`、`V1-ROW-PIPELINE`、`V1-COLUMN-ACCESS`、`V1-KEYED-IDENTITY`、`V1-ACCESS-STRUCTURES`、`V1-MUTATION`；
- `V1-CHILD-OWNERSHIP`、`V1-MATERIALIZATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-RUNTIME-PLAN`、`V1-PERFORMANCE-SHAPE`、`V1-SECURITY-INTEGRITY`；
- `V1-EVIDENCE-TOOLING`、`V1-CONSUMER-PACKAGE`、`V1-SCENARIO-BENCHMARK`。
- `V1-TRANSFORMATION-DATAFLOW`。

`V1-RELEASE-EVIDENCE` 为 `blocked`：本地 package/reproducibility/SBOM/security/license mechanics 已实施，但真实组织发布边界和 clean immutable public provenance 未满足。该 Capability 仍完整保留在 V1，不是 optional、dropped 或 deferred。

## 3. Gate 状态

| Gate | 状态 | 主要报告 |
|---|---|---|
| G0 | passed | `reports/java-v1-g0-scope-freeze-report.md` |
| G1 | passed | `soma-processor/reports/java-v1-g1-schema-processing-report.md` |
| G2 | passed | `soma-processor/reports/java-v1-g2-code-generation-report.md` |
| G3 | passed | `soma-runtime-core/reports/java-v1-g3-runtime-core-report.md` 与 `reports/2026-07-27-transformation-dataflow-governance-report.md` |
| G4 | passed | `reports/java-v1-g4-package-smoke-report.md` |
| G5 | passed | `reports/2026-07-23-reference-application-boundary-governance-report.md`、`reports/2026-07-24-reference-application-scale-performance-baseline-governance-report.md`、两份 reference application 架构治理报告与 Access Model 治理 |
| G6 | blocked | `reports/java-v1-g6-release-readiness-report.md` |

因此“完整 V1 功能范围 + G0–G5”的功能 RC 边界已满足；它不是可公开发布的 RC artifact。当前 artifact 是 `0.2.0-SNAPSHOT`，G6 未通过前不得公开分发或声明正式支持矩阵。

## 4. 集中验证记录

### 4.1 2026-07-27 Transformation/DataFlow 正式收口

`2aa8c15` 是完整 promotion preflight 使用的 production/public/generated/runtime
候选；Zulu JDK 8 上完整 `./scripts/check.sh` 返回 `project-check: ok`。
Transformation contract、reference differential、external consumer、
generated footprint、DataFlow 3-fork component baseline、工业调度
correctness/5-fork identity migration 均通过。annotation Schema、Access Model、
Index/ownership/lifecycle、单 Table 失败原子性、两个 reference application 和
既有阈值未缩水；grassing 只因 generated companion/protocol 原子迁移而重新编译，
没有业务 DataFlow 迁移。

### 4.2 2026-07-24 大规模性能基线正式收口

`1af43ac`冻结六个目标workload与同语义scheduler内部优化，`938b3d5`建立六份
9-fork baseline并完成Owner/Gate切换；`c0fa1c9`封闭最终重放发现的JDK 8私有
生成构造器不稳定性。独立3-fork Fast、Scale、Soak、Full、performance
architecture、codegen admission、两个应用correctness/architecture与完整
`./scripts/check.sh`均通过；当前结构为component=1、reference-application=6、
public-claim=0。目标规模、API/Schema/Access Model/runtime语义和应用领域语义均
未缩水，详见[大规模性能基线治理报告](2026-07-24-reference-application-scale-performance-baseline-governance-report.md)。

### 4.3 2026-07-23 参考应用边界正式收口

`955c956`已通过文档、reactor、artifact isolation、两个应用的correctness/default/large/long-run、多fork evidence、20+20条neutral benchmark record、36条negative path、16条allocation、24条memory与三surface generated-footprint Gate，并在immutable commit上得到完整`project-check: ok`。旧四场景已退出current Blueprint/Design/Conformance、共享example JAR和benchmark dependency；正式Owner、引用闭包、Governance Report与Temporary退役均已闭合。详见[参考应用边界治理报告](2026-07-23-reference-application-boundary-governance-report.md)。

### 4.4 2026-07-23 复杂度可持续性正式收口（历史候选事实）

`79c0a89`修正compiler/codegen内部责任，`8f685e2`完成benchmark分责与generated-footprint诊断，`75ee658`完成正式Owner、Governance Report、入口/checker与Temporary退役。当时的最终候选在Azul Zulu full JDK 8通过完整`./scripts/check.sh`，结果为`project-check: ok`；20条benchmark record、36条negative path、222个generated type、782个major-52 class与四场景保持，FJSP 100k allocation/GC Gate无Full GC。该段只记录该治理时点的非回归事实，不拥有当前参考应用边界；详见[后续治理报告](2026-07-23-complexity-sustainability-governance-report.md)。

### 4.5 2026-07-23 Access Model / Candidate Scan验证（历史基线）

`fd82eba`的production/public API/runtime、external consumers、当时四场景、16条allocation、24条memory、code-size与FJSP多fork证据均已在Azul Zulu full JDK 8通过完整`./scripts/check.sh`，结果为`project-check: ok`。该段记录v4产品基线的验证来源；旧场景证据现已由core、neutral benchmark和两个参考应用替换。详细环境、artifact与claim边界见[治理报告](2026-07-23-access-model-candidate-scan-governance-report.md)和[性能报告](2026-07-23-access-model-candidate-scan-performance-report.md)。

### 4.6 2026-07-21 四场景采纳验证（历史 provenance）

`a137b10`的实现、当前正式文档与退役后 Temporary 已由Zulu JDK 8完整`./scripts/check.sh`重新验证，结果为`project-check: ok`。四场景生成222个type/782个major-52 class，benchmark产生20+20 records且36个negative path全部fail closed，component与FJSP allocation/GC Gate也通过。完整命令、环境、artifact与claim边界见[2026-07-21 专题治理报告](2026-07-21-four-scenario-blueprint-adoption-report.md)。

### 4.7 2026-07-11 专题治理 fresh validation

实现提交`aa7a466`上执行：

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home SOMA_UNSUPPORTED_JAVAC=/opt/homebrew/opt/openjdk/bin/javac ./scripts/check.sh
```

结果`project-check: ok`；20条benchmark lane各聚合2次measurement，36条serialized negative artifact均fail closed。最后一次现存benchmark目录为`target/benchmark-smoke.PJkPCM`。clean package mechanics在`2490406`已通过；`aa7a466`的fresh package重放在项目编译前因Maven Central TLS中断，当前实现内容由Maven verify和多组external Maven consumer覆盖，具体限制不作为G6证据并在专题报告§12披露。

### 4.8 Phase 6 原集中验证记录（历史，不构成当前JDK支持范围）

完整命令：

```text
./scripts/check.sh
env JAVA_HOME=/tmp/corretto8-soma/Contents/Home PATH=/tmp/corretto8-soma/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin ./scripts/check.sh
```

Zulu 环境：Azul Zulu OpenJDK `1.8.0_492-b09`、`javac 1.8.0_492`、Maven Wrapper `3.9.16`、macOS `26.5.2` / Darwin `25.5.0`、arm64/aarch64。最终结果`project-check: ok`。Phase 6 examples：`target/phase6-examples.Wt58xz`；post-fix benchmark：`target/benchmark-smoke.LY9fqL`。

Corretto 环境：Amazon Corretto `1.8.0_492-b09` / `8.492.09.2`、`javac 1.8.0_492`、Maven Wrapper `3.9.16`、macOS `26.5.2` / Darwin `25.5.0`、arm64/aarch64。完整结果`project-check: ok`；examples：`target/phase6-examples.BxKJHi`；post-fix focused benchmark：`target/benchmark-smoke.YL4tuu`，20+20 records、12 negative通过。

两次均验证 compiler fixtures、schema/hash、public/generated API、runtime invariants、external Maven consumer、四场景、错误/生命周期/stats、20 条 benchmark workload、JSONL strict validator、Java 8 class major 52 和 `git diff --check`。这些内容只记录当时基线的执行事实；当前项目只以Azul Zulu full JDK 8作为验真与目标支持distribution，不再要求Corretto重放，也不把这次历史结果外推为当前支持承诺。

Release diagnostics：`SOMA_PACKAGE_ALLOW_DIRTY=true ./scripts/package-smoke.sh` passed，evidence `target/package-smoke.f8IUL8`；`OSV_SCANNER=/tmp/osv-scanner-v2.3.8-darwin-arm64 ./scripts/security-release-scan.sh` passed，evidence `target/security-release-scan.3iyUxx`。两者明确dirty/unsigned，不替代G6。

## 5. V1 scope non-regression

- Capability：23 项 `evidenced`，既有 22 项未回退并新增
  `V1-TRANSFORMATION-DATAFLOW`；`V1-RELEASE-EVIDENCE` 因外部发布事实不足继续
  保持 `blocked`。
- Owner、正式语义与Gate经用户批准按packed/exact v3目标先行迁移，没有为实现捷径反向降低Capability或release claim。
- 旧四场景只保留历史 provenance；当前 G5 由 core Access Model fixtures、领域中性 component benchmark和两个普通 Java 8 reference consumers共同承担。
- 两个参考应用分别拥有领域 Blueprint/Design、版本化配置、detached generator、bootstrap、runtime、correctness/long-run/multi-fork evidence；它们不进入 SOMA 产品 Design。
- 六个 default/large/long-run profile 由 application 自有 Fast/Scale/Soak/Full
  Gate 回归；9-fork calibration、3-fork ordinary comparison 和目标 workload
  均未缩水，且不形成 public claim。
- Access Model / Candidate Scan v4 clean migration已在首个公开发布前完成；后续功能性能工作应是additive completion或contract-preserving internal refinement，不应再次引入双轨public/generated API、平行事实或temporary canonical hot path。
- Transformation/DataFlow 以 generated/runtime v5 与 transformation/kernel v1
  原子 clean break 完成；没有 v4 adapter、generic object executor、隐式 common
  pool、第二存储后端或未来 rewrite 依赖。
- 复杂度可持续性治理只调整internal implementation/evidence责任与诊断，不改变产品语义；其正式Owner、Report、checker和Temporary退役已在`75ee658`闭环。当前专题只重新划分参考应用与evidence Owner，不反向修改该历史结论。
- 未引入 temporary public/generated contract、temporary storage/hot path、test-only bypass、未来 migration 或 rewrite。

## 6. 当前唯一剩余工作

G6 只能在以下真实事实补齐并在 clean immutable candidate 上重放后关闭：SCM/project/issue URL、真实 maintainer/support/private-security contact、namespace ownership、CODEOWNERS/community policy、Apache-2.0 最终授权确认、非禁用历史身份的公开 Git provenance、签名或 OIDC provenance、publishing endpoint/account，以及经批准的正式支持矩阵。未经明确发布授权不 push、tag 或 publish。
