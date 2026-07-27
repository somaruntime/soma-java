# SOMA Runtime Boundary 与产品化治理 Goal Traceability

类型：Temporary

状态：active

Owner：SOMA runtime scale and productization Goal execution

正式事实源：否

实施授权：本文件只记录当前 Goal 的目标、Owner、evidence 与闭环状态；正式语义仍由
Blueprint、Design、Conformance、Engineering 和 Report 的唯一 Owner 持有。

事实范围：当前 Goal 的稳定目标编号、产品价值、正式 Owner、evidence、implementation、
validation、Example、阶段状态与剩余差距

非事实范围：正式产品或设计语义、已实现能力、public readiness、性能 SLA 或对
Blueprint/Design/Conformance 的替代定义

上位专题：[SOMA Runtime Boundary、Group 与 Scale Readiness 治理指导](README.md)

最后审查日期：2026-07-28

## 1. 使用规则

- Goal ID 在本专题内稳定，不因实现拆分或重命名改变；
- 每项 production、test、benchmark、文档或 Example 工作必须映射到至少一个 Goal ID；
- `satisfied` 只表示正式 Owner、实现和 evidence 已共同闭合，Lab 通过本身不满足该条件；
- `conformance-gap` 表示正式目标已明确但 production 尚未一致；
- 每阶段关闭时更新 Status、Evidence、Implementation 与 Remaining gap；
- 不能为了提前完成 Goal 删除 Goal ID 或缩小 Product Value。

## 2. Traceability Matrix

| Goal ID | Product Value | Blueprint / Design Owner | Evidence | Implementation / Validation | Example | Status | Remaining gap |
|---|---|---|---|---|---|---|---|
| G01 | 用户能用一句稳定叙事理解 SOMA，并沿一条 canonical journey 完成首次成功 | Product Blueprint；设计宪法；系统架构 | 现有 Blueprint、三个 Example、TV0–TV9 | guides、generated facade、external consumer、docs Gate | 三个 Example | pending | P4/P5 冻结产品叙事，P6 原子固化目标语义，P8–P11 闭合实现与产品 evidence |
| G02 | Schema、Metadata、Plan 与运行数据职责清晰，配置在冻结前可控，执行期无 Metadata interpreter | Schema/Metadata Design；runtime plan | TV0；A01/A02 | annotations、processor、runtime-core；compile/golden/runtime invariant | N/A | pending | 完整 Metadata hierarchy、freeze/effective/observation 与 generated binding |
| G03 | Runtime 能力可局部替换但语义稳定，hot path 保持 compiler-specialized | 系统架构；Capability/模块边界 Design | TV0–TV9 rejected/accepted 清单 | processor/runtime-core/dataflow；API/codegen/component tests | 三个 Example按需 | pending | 封闭 Capability Set、binding、internal strategy 与 public contract |
| G04 | V1 类型安全且不允许任意对象绕过 ownership/mutation/index 边界 | Schema/type/storage Design | TV8；现有 value/child evidence | annotations、processor、runtime-core；compile/runtime/external consumer | 三个 Example按需 | conformance-gap | 四类类型正式化；String selector/access/operator/资源能力尚未闭合 |
| G05 | Group 是多 Table 的所有权、生命周期和一致性边界 | Group/Table/ownership Design | TV4、TV7、TV8 | runtime-core/dataflow；ownership/lifecycle/failure tests | 三个 Example按需 | pending | SomaGroup、parent-owned Table、version/guard/release 与 canonical API |
| G06 | Small/Medium 无固定税回退，Large 可使用受限 storage/locator physical plan | storage/access/runtime-plan Design | TV1、TV2、TV7、TV8 | runtime-core/processor；component + production-shape benchmark | N/A | pending | flat/head-tail、compact locator、cost formula、resource plan 的 production adoption |
| G07 | Candidate、Group/Join、Delta、Window 避免无用中间结果与额外 pass | access/transformation/materialization Design | TV3–TV5、TV7 | dataflow/runtime-core；differential/allocation/failure tests | 自然使用者按需 | pending | multi-shape、fusion/preaggregation、staging/incremental 的正式 Owner 与实现 |
| G08 | 一个 bounded scheduler 同时服务单/多 Segment，并保持确定性、缓存友好和 executor ownership | execution/parallel Design | TV6、TV7 | dataflow execution context；parallel/cancel/determinism benchmark | 按 workload 审计 | pending | split/coalesce/direct fallback、cost formula、managed/borrowed ownership |
| G09 | 默认结果完整原子且易推理；大结果可在受限只读场景按需消费 | Result Delivery/Materialization Design | TV3 eager；TV9 A21/O27、`bbc13e8` | dataflow/generated contract；correctness/allocation/failure/compatibility | P9 按产品价值决定 | pending | 冻结 Eager default + callback limited pilot 的正式 contract，并完成 production pilot/qualification |
| G10 | 不可行操作在 source touch 或大分配前以 typed failure 拒绝，资源口径可解释 | resource/failure/diagnostics Design | TV0、TV3、TV4、TV7、TV8 | runtime-core/dataflow；preflight/overflow/OOM-avoidance tests | 三个 Example diagnostics | pending | retained/transient/scratch/output/String/headroom estimator 与 explain |
| G11 | String 在白名单 immutable value 语义下支持 payload、Key/Unique/Index、Group/Join 和 lifecycle | type/access/relation Design | TV8 A19/A20 | processor/runtime-core/dataflow；compile/differential/GC/scale | 按真实业务字段审计 | conformance-gap | 当前 selector 明确拒绝 String；需正式实现和 production qualification |
| G12 | public/generated API、diagnostics、compatibility 与 internal strategy 不混淆 | generated/public contract；compatibility Design | external consumers、golden、TV evidence | annotations/processor/runtime/dataflow/testkit | 三个 Example | pending | P4/P5 先审计 contract，再随 semantic slice 原子迁移 |
| G13 | 代码和测试围绕核心抽象，删除不制造能力或 evidence 缺口 | complexity/testing/documentation governance | 当前 code/test inventory | 全模块 disposition + replacement closure + full Gate | 三个 Example按需 | pending | P7 inventory/court decision，P8 后删除旧路径 |
| G14 | 三个 Example 是相互独立、符合最佳实践的 reference consumers | Product Blueprint；Example portfolio/report | 当前 G5/portfolio evidence | example docs/correctness/performance/full checks | 三个 Example | pending | P9 先审计；无新偏差则 RETAIN/no change |
| G15 | Small/Medium、1M/10M、单表/双表 100M 与 String 的 claim 均有 production-shape evidence | performance/benchmark/validation governance | TV7/TV8；TV9 synthetic delivery | benchmarks/scripts/reports；Zulu 8 local qualification | N/A | pending | 生产实现后建立 bounded profiles；Lab synthetic delivery 不外推 SLA/support matrix |
| G16 | G6、release 与支持声明保持诚实，治理通过不冒充 public readiness | compatibility/security/versioning；release governance | reports/conformance/G6 | release Gate/report only | N/A | pending | closeout 更新真实状态；缺失外部事实继续 blocked |
| G17 | 稳定事实自包含，不依赖一次性 Lab 或 Temporary | documentation governance；各正式 Owner | TV0–TV9 reports + final governance report | docs checks、reference scan、Git closeout | N/A | pending | P6 固化目标与差距；P11 固化实现/evidence、删除 Lab/Temporary |

## 3. 阶段记录

| Phase | 状态 | Decision / Evidence | Scope non-regression |
|---|---|---|---|
| P0 | satisfied | SOMA `develop` 基线 `6cdc34f`；Lab `main` 基线 `b70e9a2`；治理契约经 docs/diff Gate 后提交为 `380722c` | 仅固化既有 Temporary；未修改 production、正式 Design 或 claim |
| P1 | satisfied | Goal matrix `e90d2dd`；TV9 experiment brief/workload checkpoint `8927b28` | G01–G17 全部保留；唯一新增独立验证仍为 TV9 |
| P2 | satisfied | corrected protocol `75fe7a7`；raw evidence `bbc13e8`；独立复核无 blocker | Eager default 保留；callback 只接受 limited read-only pilot；100M 不外推 readiness |
| P3 | satisfied | TV9 decision `cf322ab`；A21/O27/I14 与本 Temporary evidence transfer | TV0–TV9 只提供设计输入；未修改 production、正式 Design 或 claim |
| P4–P11 | pending | 按 Goal 执行 | 不得以耗时、token、代码规模或当前实现反向降低目标 |
