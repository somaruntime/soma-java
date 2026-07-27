# 已知差距与处置

类型：Conformance

状态：正式

Owner：SOMA Java 一致性审查

实现核对基线：P6 target promotion baseline `aea5cc0`

事实范围：当前已确认的 Blueprint/Design/Code/Evidence 差距、分类与 Owner 处置

非事实范围：自动授权实施、未来 roadmap 或重新定义 Design

最后审查日期：2026-07-28

## 1. 未闭合差距

| ID | 分类 | 差距 | 影响 | 当前 Owner 处置 |
|---|---|---|---|---|
| `CF-009` | Product/runtime contract | 完整 Descriptor/Plan/Effective/Runtime Metadata、generated SchemaMetadata、SomaGroupPlan/SomaGroup 与 atomic attach/parent ledger 尚未实现 | canonical simple/advanced journey、multi-root composition、resource/lifecycle target 未闭合 | 按 Schema、Runtime Plan、Ownership Design 原子实现；保留 cross-Group/schema/instance DataFlow，不引入双 plan/metadata path |
| `CF-010` | Type/generated protocol | 当前 generic Object value surface仍存在，String selector仍被拒绝，四类 closed type与String完整语义未投影 | arbitrary object边界、String Key/Unique/Index/Group/Join/lifecycle与schema hash不一致 | 迁移 primitive/String/flattened typed protocol，删除 superseded Object path；compile/golden/external/differential/GC closure |
| `CF-011` | Storage/access | 当前以flat/universal IndexBuffer baseline为主，缺flat-head/segmented-tail、Segment publication、closed Candidate shapes与locator formula qualification | Large可增长性、point tax、retained/transient peak和Small固定税目标未闭合 | Flat保持baseline；新physical binding只在formula与production evidence后启用，禁止universal segmented或full-key duplication |
| `CF-012` | Transformation/execution/resource | Group/Join/Delta/Window specialization、unknown-bound fail-closed、one bounded morsel/vector scheduler、parent/root/Invocation ledger与module-owned Observation尚未闭合 | 高展开可能缺preflight，中小规模/单Segment并行、资源与诊断不可按新目标解释 | 保持现有logical semantics；实现specialized/cost-model paths与sequential differential，全部resource phase lease可验证 |
| `CF-013` | Result Delivery | Eager baseline存在，但incumbent Candidate/Value/Group/Join/Window borrow仍保留consumer-in-Definition lifecycle；generated callback facade未实现/qualification | Definition identity、non-escape、cancel/deadline、partial publication与allocation目标不一致 | 统一迁入标准 Definition→Template→Invocation；Eager继续默认；不得保留legacy、Iterator/pull/async path |
| `CF-014` | Qualification/product evidence | 缺Small/Medium String、1M/10M全workload、single/double100M、String100M、expansion、delivery、Soak production-shape evidence与新Guide fixtures | 不能声明scale readiness、callback supported或完整product journey | 先预注册环境/seed/oracle/budget/timeout/fork/comparator/status，结果保持claimAllowed=false；Guide用external/snippet Gate验收 |
| `CF-015` | Migration/complexity/examples | 尚未完成exact public/generated/protocol disposition、replacement closure、code/test scale审查与三个Example的最终设计复核 | 可能残留parallel fact、死代码/测试或展示性Example迁移 | P7逐surface RETAIN/MIGRATE/REMOVE；P8只做contract-required migration；P9无偏差则RETAIN，不做装饰性重构 |
| `CF-005` | Evidence | 当前十一份性能 baseline 只覆盖指定 Zulu JDK 8/macOS/aarch64 环境与固定 lanes/workload | 其他环境只能得到 `not-applicable`，不能外推为支持矩阵或普遍性能优势 | 保留环境限定和 `claimAllowed=false`；新增环境需独立校准，public claim 需另行授权 |
| `CF-006` | Release evidence | G6 所需真实 SCM、ownership、contact、signing/publishing、clean provenance 和支持矩阵不完整 | 禁止 public RC/release-ready/production-ready 声明 | 保持 `blocked`；发布工作不在当前专题范围 |

以上差距都已有正式 Design Owner 和用户对当前综合治理 Goal 的明确实施授权；这不
表示它们已完成，也不允许 Conformance 自行改变目标、扩大到 release 或降低 Gate。

## 2. 已关闭但需防回归的差距

| 主题 | 当前状态 | 防回归点 |
|---|---|---|
| Sparse Set / dirty selector / maintained order | 已由 V3 packed exact cutover关闭 | 不恢复读时全表 rebuild、稳定物理顺序或 Sparse Set public model |
| dense stable compaction 假设 | 已关闭 | keyed/dense 均保持 swap-remove；未排序 terminal 不承诺顺序 |
| public row-index list | 已由 caller-responsibility `IndexSnapshot` + internal `IndexBuffer` 取代 | snapshot只在同步只读批次立即消费；跨operation使用`@SomaKey`，不把内部scratch或Index冒充stable identity |
| Row-oriented generated access vocabulary | 已由 Access Model / Candidate Scan clean cutover关闭 | current surface保持 Table/Scan/Cursor/UpdateCursor/Traversal、`findIndex/requireIndex/indexSnapshot`；不恢复双轨alias |
| group-shaped secondary unique access | 已关闭 | Unique优先保持0..1 point family；只有需要stage时使用`scanByX` bridge |
| Candidate plan allocation与best-one snapshot | 已关闭 | compact typed plan、Packed/exact specialization与scalar Index terminal保持component/code-size Gate |
| Transformation/DataFlow semantic closure | 已关闭 | 全部 admitted Shape/operator/result/effect 由 typed contract、reference differential 和 external consumer 防回归；Candidate Scan 仍是 specialized fast path |
| DataFlow lifecycle/resource/parallel | 已关闭 | immutable Definition/Template、one-shot Invocation、managed/borrowed ownership、budget/cancel/fixed-order merge 和 sequential fallback 保持 contract evidence |
| generated v5/Delta/safe point | 已关闭 | 每 Table 一个 companion、protocol fail-closed、keyed ordered Delta 全量 preflight、single-aggregate commit 与 generated fixture closure |
| reference application portfolio | 已关闭 | industrial、grassing、RTD 各自从 business model 推导 Access/Transformation/DataFlow 最佳实践；互不依赖、不共享领域模型或 evidence |
| DataFlow application trace | 已关闭 | 独立 RTD 应用拥有 reusable multi-source DataFlow、Join/GroupBy、受控并行、budget/cancel、detached command 与 application commit；industrial 不再承担展示性 coverage |
| industrial summary responsibility | 已关闭 | `AssignmentSummarizer` 只从 authoritative assignments 单遍推导 detached metrics；primitive frontier、Problem/Result/API/Schema 与三个性能阈值不变 |
| application-owned priority structure | 已关闭 | SOMA 只拥有 Table facts；应用长期 queue/heap 保存 stable domain identity，不保存 current Index |
| reference application ownership | 已关闭 | 应用 Blueprint/Design/correctness/integrated evidence 由 child project 自有，不进入 SOMA Design trace |
| config/factory/solver/runtime/result lifecycle | 已关闭 | Problem config 与 benchmark options 分离；detached Factory 先产生可重放 input；canonical Solver/Session 独占 Runtime 并返回 detached Result；hot loop 不反向依赖 Factory |
| reference application production/test 边界 | 已关闭 | industrial scheduler production JAR 不含 fixture/oracle/verification/benchmark；source-shape 与 JAR Gate 防止 evidence 回流生产 |
| performance baseline 可持续性 | 已关闭 | component=2、reference application=9、public claim=0；baseline 只读、环境感知、strict shape，统一 comparator 不拥有应用 workload |
| industrial scheduler 语义/Schema/frontier 闭环 | 已关闭 | Problem semantic identity 与 generation provenance 分离；Result 不携带 runtime diagnostics；eligible option 使用 flat exact-group；derived candidate 由 application primitive pool/heap 拥有；hot 与 canonical path 均有 9-fork evidence |
| `CF-007` 文档候选完整性 | 已关闭 | 32份旧Owner已按迁移审计处置；正式入口、checker与Report已切换，Temporary已删除 |
| `CF-008` 文档抽象层次与职责混合 | 已关闭 | Design 已建立 `D0/D1/D2/Q`、上位设计和场景追踪；Blueprint 不再承载当前实现盘点、自审或一致性结论；checker 防止结构回退 |
| aggregate internal/unexpected failure 后可信状态 | 已关闭 | `ChildOwnershipRegistry` 是共享 trust Owner；faulted root/child aggregate 拒绝 normal access，只允许 bounded diagnostics 与 root release；public/API/protocol 不变 |

## 3. 不构成差距的观察

Generator 规模、测试文件颗粒度、内部命名或可选性能优化可以是 maintainability/optimization 候选，但在没有证据表明违反 Design 前，不应被 Conformance 伪装成语义偏差。此类问题应进入独立审查或 Temporary，而不是借本表扩大实施范围。

## 4. 关闭规则

差距只有在相关 Owner 已作出决定、实现和测试完成、必要 evidence 通过、Implementation Map 已核对后才能关闭。若 Owner 决定修改 Blueprint/Design，应先在 Temporary 形成候选并获得授权；Conformance 自身不做该决定。
