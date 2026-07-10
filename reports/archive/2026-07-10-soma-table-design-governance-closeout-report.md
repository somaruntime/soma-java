# SomaTable 设计文档治理收尾报告

状态：正式治理收尾报告
日期：2026-07-10
Owner：根项目协调层

## 1. 收尾结论

本次专题已经完成设计文档层面的收口：原临时设计宪法已晋升为正式跨模块事实源，核心术语、Schema、codegen、generated API、runtime、correctness、performance、testkit、benchmark 和 validation gate 的 owner 文档已同步。

本结论只表示“设计文档治理完成”，不表示 Java 实现、性能优势、package smoke 或 release gate 已完成。

## 2. 固化的核心模型

本次固化以下不可缩水的设计基线：

- `@SomaValue` 是 compiler-defined immutable value；用户无需重复书写 `public final`、constructor 或 canonical equality/hash；
- `@SomaTable` class 同时定义 row schema 与 detached single-row materialization shape，但不是 live runtime row storage；
- `List<R>` / dense 与 `Map<K,R>` / keyed 是 schema/materialization mapping，runtime storage 仍为 columnar `TableStore`；
- SomaTable ownership aggregate 是运行时数据的唯一事实源，Materialized Object、view、index、cache 和外部 DTO 都不是第二事实源；
- keyed table / dense table 是访问与身份分类，parent / child 是所有权角色，两组概念彼此正交；
- child table storage 是独立 SomaTable storage，其所有权属于唯一 parent row / field slot；禁止共享和 reparent；
- parent materialization 遇到 dense/keyed child 时沿 ownership edge 递归构造完整 `List`/`Map`，不隐式追踪普通 cross-table key reference；
- 普通浮点 payload 保留 Java IEEE-754 值域；进入 key、unique、index 或 order 的浮点值必须有限，并把 `-0.0` canonicalize 为 `+0.0`；
- `@SomaTable` row 不生成 structural equality/hash；`List`/`Map` 使用 Java Collection contract，值相等属于 immutable `@SomaValue`；
- child replacement 全有或全无，delete、clear、unset 和 release 按 ownership subtree 级联；borrowed view 会阻止不安全替换；
- deep materialization 受 runtime-plan budget 约束，超限返回 typed `materialization_budget_exceeded`，不返回 partial object graph；
- SomaTable 不支持并发访问，也不提供序列化或持久化能力；这些边界由上层应用负责。
- Runtime hot path 必须保持 packed、primitive-specialized、fused 和 allocation-bounded；列式存储、SoA、Sparse Set 和 DOD 只提供性能先验，最终由 Access Pattern Card、runtime plan 和同语义 benchmark 形成性能信心。

## 3. 正式事实源与 owner

| 事实 | 正式 owner 文档 |
|---|---|
| SomaTable 总心智模型与跨模块原则 | `docs/soma-table-design-constitution.md` |
| 规范术语 | `docs/domain-glossary.md` |
| 项目结构、依赖方向与模块边界 | `docs/architecture-design.md` |
| 文档层级、草案晋升和报告边界 | `docs/documentation-governance.md` |
| annotation schema 与 normalized schema | `soma-annotations/docs/annotation-schema-contract.md` |
| processor、value lowering、generated API 和 materialization shape | `soma-processor/docs/processor-codegen-contract.md` |
| Row Pipeline、terminal 和 materialization API | `docs/row-pipeline-api-contract.md` |
| TableStore、ownership、lifecycle 和 runtime errors | `soma-runtime-core/docs/runtime-core-contract.md` |
| runtime invariants、状态机和 oracle | `docs/runtime-correctness-model.md` |
| hot path、局部性、预算指标和性能声明边界 | `docs/runtime-performance-model.md` |
| packed/primitive/fused/allocation-bounded implementation、capacity/scratch、KeySpace/sidecar 和 stats overhead | `soma-runtime-core/docs/runtime-performance-implementation-contract.md` |
| compile/golden/runtime evidence helper | `soma-testkit/docs/testkit-contract.md` |
| benchmark lane、artifact 和 claim discipline | `soma-benchmarks/docs/benchmark-evidence-contract.md` |
| G0-G6 阻断条件 | `docs/validation-gates.md` |
| 实现阶段和 no-shrink 顺序 | `docs/implementation-strategy.md` |

宪法不复制各 owner 文档的完整 API、内部布局或 runner schema；owner 文档若与宪法原则冲突，应先修正文档冲突，不能由实现自行选择一套语义。

## 4. 文档治理动作

### 4.1 临时事实源晋升

- `docs/temp/soma-table-design-constitution.md` 已晋升为 `docs/soma-table-design-constitution.md`；
- 正式宪法增加了事实源定位、术语边界、value/table materialization、浮点域、equality、child lifecycle、deep materialization budget 和 owner mapping；
- 本次执行计划在完成验证后删除，不作为长期设计事实源。

### 4.2 跨层同步

- `docs/domain-glossary.md` 已把 immutable `@SomaValue`、schema-backed row class、Materialized Object、List/Map mapping、ownership aggregate、ChildTableHandle 和 MaterializationBudget 纳入规范术语；
- architecture、annotation、processor、row pipeline 和 runtime contract 已删除旧的 public `XxxRecord` / `ChildRecords` 第二类型；
- correctness 与 performance 文档已增加 ownership subtree、递归物化、预算、浮点 canonicalization 和 child locality 约束；runtime-core 另行固化 packed/primitive/fused/allocation-bounded implementation discipline；
- `soma-testkit` 和 `soma-benchmarks` 已建立正式 owner 契约，不再保留“暂无正式契约”的设计缺口；
- examples 已统一为 schema-backed row / generated live table / external DTO adapter 三层模型；
- README、AGENTS 和 validation gates 已更新入口、保护基线和阻断条件。

### 4.3 历史报告处理

2026-07-06 的治理与架构审核报告保留当时证据，不回写为当前事实；报告头部已明确标记为历史快照，并指向本报告。当前设计状态应以正式 owner 文档和本报告为准。

## 5. Deep materialization 初始 runtime plan

以下数值作为 V1 初始工程默认值固化在 runtime plan 语义中，但不是 schema、schema hash 或永久兼容性承诺：

| 预算维度 | 初始默认值 |
|---|---:|
| ownership depth | 32 |
| materialized table instances | 100,000 |
| materialized rows | 1,000,000 |
| materialized present leaf values | 50,000,000 |
| estimated allocation bytes | 256 MiB |

默认不使用 wall-clock timeout；elapsed time 只记录为 diagnostics。上述数值必须由后续 deep-materialization benchmark lane 校准。

## 6. 验证记录

| 检查 | 方法 | 结果 |
|---|---|---|
| Markdown 相对链接 | 扫描根级、reports 和各模块正式 Markdown 的相对链接目标 | 通过 |
| 旧 Record 分离契约 | 扫描 generated `XxxRecord` / `ChildRecords` 被继续当作 public materialization contract | 正式文档无有效命中 |
| Runtime performance implementation owner | 检查新正式契约、owner map、G2/G3/G5、benchmark/testkit、四个正式 examples 与四蓝图 Access Pattern Card | 已同步 |
| 临时宪法引用 | 扫描正式文档对旧 `docs/temp` 路径的引用 | 无正式引用 |
| 未决占位语 | 扫描 `待补`、`待定`、`待设计`、`TBD`、`TODO`、`FIXME` | 正式设计文档无命中 |
| Markdown whitespace | `git diff --check` | 通过 |
| Maven 聚合配置 | `JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home mvn -Dmaven.repo.local=../.m2-temp -q validate` | 通过 |

这些验证只证明当前文档集内部可导航、关键术语已同步且 Maven 项目配置仍可解析。

## 7. 进入实现后的证据义务

设计治理结束后，以下事项仍必须由实现和 gate evidence 证明：

- supported compiler transformation 对 `@SomaValue` effective shape 的实现与 IDE/build compatibility；
- annotation processor 对 List/Map mapping、ownership cycle、浮点 selector 域和 schema default 的编译期诊断；
- schema-object/List/Map materializer、child API 和 budget overload 的实际签名与 Java 8 package smoke；
- ownership registry、cascade release、replacement atomicity、view pinning 和 typed error 的 runtime invariant；
- 显式 deep comparator 与 budget assertion helper 的 testkit 实现；
- child locality、deep materialization 和 allocation estimate 的 benchmark artifact；
- packed scan、pipeline fusion/Cursor reuse、KeySpace domain/load/collision、sidecar rebuild storm、compaction/capacity/scratch 和 stats-overhead 的 component performance-shape evidence；
- G0-G6 对应报告、可复现命令和 release claim。

在这些证据产生前，不得把设计完成写成实现完成，也不得声明性能优势或 release readiness。

## 8. 最终判断

SomaTable 设计已经形成一套可执行、可追踪、各关注点彼此分离的正式契约链。后续实现应以 owner 文档为输入，以 correctness、testkit、benchmark 和 validation gates 为验收边界；任何为了降低实现成本而删除 immutable `@SomaValue`、List/Map mapping、child ownership、递归 materialization、预算、浮点确定性或生命周期语义的做法，都属于 V1 目标缩水。
