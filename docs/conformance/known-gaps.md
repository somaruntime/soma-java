# 已知差距与处置

类型：Conformance

状态：正式

Owner：SOMA Java 一致性审查

实现核对基线：core product `fd82eba`；compiler/codegen stability `c0fa1c9`；
industrial scheduler `a7d4fde`；其余 reference application / performance
baseline evidence `938b3d5`

事实范围：当前已确认的 Blueprint/Design/Code/Evidence 差距、分类与 Owner 处置

非事实范围：自动授权实施、未来 roadmap 或重新定义 Design

最后审查日期：2026-07-24

## 1. 未闭合差距

| ID | 分类 | 差距 | 影响 | 当前 Owner 处置 |
|---|---|---|---|---|
| `CF-005` | Evidence | 当前七份性能 baseline 只覆盖指定 Zulu JDK 8/macOS/aarch64 环境与固定 lanes/workload | 其他环境只能得到 `not-applicable`，不能外推为支持矩阵或普遍性能优势 | 保留环境限定和 `claimAllowed=false`；新增环境需独立校准，public claim 需另行授权 |
| `CF-006` | Release evidence | G6 所需真实 SCM、ownership、contact、signing/publishing、clean provenance 和支持矩阵不完整 | 禁止 public RC/release-ready/production-ready 声明 | 保持 `blocked`；发布工作不在当前专题范围 |

以上差距都已有明确处置；它们受 evidence 范围或外部事实限制，但不存在借Conformance自动扩权的未裁决项。

## 2. 已关闭但需防回归的差距

| 主题 | 当前状态 | 防回归点 |
|---|---|---|
| Sparse Set / dirty selector / maintained order | 已由 V3 packed exact cutover关闭 | 不恢复读时全表 rebuild、稳定物理顺序或 Sparse Set public model |
| dense stable compaction 假设 | 已关闭 | keyed/dense 均保持 swap-remove；未排序 terminal 不承诺顺序 |
| public row-index list | 已由 caller-responsibility `IndexSnapshot` + internal `IndexBuffer` 取代 | snapshot只在同步只读批次立即消费；跨operation使用`@SomaKey`，不把内部scratch或Index冒充stable identity |
| Row-oriented generated access vocabulary | 已由 Access Model / Candidate Scan clean cutover关闭 | current surface保持 Table/Scan/Cursor/UpdateCursor/Traversal、`findIndex/requireIndex/indexSnapshot`；不恢复双轨alias |
| group-shaped secondary unique access | 已关闭 | Unique优先保持0..1 point family；只有需要stage时使用`scanByX` bridge |
| Candidate plan allocation与best-one snapshot | 已关闭 | compact typed plan、Packed/exact specialization与scalar Index terminal保持component/code-size Gate |
| application-owned priority structure | 已关闭 | SOMA 只拥有 Table facts；应用长期 queue/heap 保存 stable domain identity，不保存 current Index |
| reference application ownership | 已关闭 | 应用 Blueprint/Design/correctness/integrated evidence 由 child project 自有，不进入 SOMA Design trace |
| config/factory/solver/runtime/result lifecycle | 已关闭 | Problem config 与 benchmark options 分离；detached Factory 先产生可重放 input；canonical Solver/Session 独占 Runtime 并返回 detached Result；hot loop 不反向依赖 Factory |
| reference application production/test 边界 | 已关闭 | industrial scheduler production JAR 不含 fixture/oracle/verification/benchmark；source-shape 与 JAR Gate 防止 evidence 回流生产 |
| performance baseline 可持续性 | 已关闭 | component=1、reference application=6、public claim=0；baseline 只读、环境感知、strict shape，统一 comparator 不拥有应用 workload |
| industrial scheduler 语义/Schema/frontier 闭环 | 已关闭 | Problem semantic identity 与 generation provenance 分离；Result 不携带 runtime diagnostics；eligible option 使用 flat exact-group；derived candidate 由 application primitive pool/heap 拥有；hot 与 canonical path 均有 9-fork evidence |
| `CF-007` 文档候选完整性 | 已关闭 | 32份旧Owner已按迁移审计处置；正式入口、checker与Report已切换，Temporary已删除 |
| `CF-008` 文档抽象层次与职责混合 | 已关闭 | Design 已建立 `D0/D1/D2/Q`、上位设计和场景追踪；Blueprint 不再承载当前实现盘点、自审或一致性结论；checker 防止结构回退 |

## 3. 不构成差距的观察

Generator 规模、测试文件颗粒度、内部命名或可选性能优化可以是 maintainability/optimization 候选，但在没有证据表明违反 Design 前，不应被 Conformance 伪装成语义偏差。此类问题应进入独立审查或 Temporary，而不是借本表扩大实施范围。

## 4. 关闭规则

差距只有在相关 Owner 已作出决定、实现和测试完成、必要 evidence 通过、Implementation Map 已核对后才能关闭。若 Owner 决定修改 Blueprint/Design，应先在 Temporary 形成候选并获得授权；Conformance 自身不做该决定。
