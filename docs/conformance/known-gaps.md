# 已知差距与处置

类型：Conformance

状态：正式

Owner：SOMA Java 一致性审查

实现核对基线：`b991f4c`

事实范围：当前已确认的 Blueprint/Design/Code/Evidence 差距、分类与 Owner 处置

非事实范围：自动授权实施、未来 roadmap 或重新定义 Design

最后审查日期：2026-07-20

## 1. 未闭合差距

| ID | 分类 | 差距 | 影响 | 当前 Owner 处置 |
|---|---|---|---|---|
| `CF-001` | Blueprint → Code | VRP `Customer` 仍把 input、working state 和 assignment fields 放在同一 row；正式 Blueprint 要求分离 definition/assignment/workspace | 易形成 assignment 与 workspace 的多重表达 | 记录为场景目标差距；若要实施，另开有授权的场景专题并先验证 end-to-end cost |
| `CF-002` | Blueprint → Code | Simulation `Tank`/`Valve` 仍保存 numeric mutable state，同时存在 dense `StateVectorRow` | 需要明确唯一 numeric source-of-truth 和同步失败语义 | 记录为场景目标差距；在场景实现完成前不得宣称已完成拆分 |
| `CF-003` | Blueprint → Code | Game `MapTileRow` 混合 immutable terrain 与 mutable `occupantUnit` cache | cache 与 `GameUnit.position` 可能 drift | 记录为场景目标差距；未来专题需定义 rebuild invariant 和 coordinate access lane |
| `CF-005` | Evidence | 当前性能数据来自有限 JDK/OS/architecture 和指定 benchmark lanes | 不能外推为正式支持矩阵或普遍性能优势 | Report 必须保留环境限定；新增 claim 前补对应 evidence |
| `CF-006` | Release evidence | G6 所需真实 SCM、ownership、contact、signing/publishing、clean provenance 和支持矩阵不完整 | 禁止 public RC/release-ready/production-ready 声明 | 保持 `blocked`；发布工作不在当前专题范围 |

以上差距都已有明确处置；它们尚未实施或受外部事实限制，但不存在借Conformance自动扩权的未裁决项。正式文档体系切换没有改变其状态。

## 2. 已关闭但需防回归的差距

| 主题 | 当前状态 | 防回归点 |
|---|---|---|
| Sparse Set / dirty selector / maintained order | 已由 V3 packed exact cutover关闭 | 不恢复读时全表 rebuild、稳定物理顺序或 Sparse Set public model |
| dense stable compaction 假设 | 已关闭 | keyed/dense 均保持 swap-remove；未排序 terminal 不承诺顺序 |
| public row-index list | 已由 caller-responsibility `IndexSnapshot` + internal `IndexBuffer` 取代 | snapshot只在同步只读批次立即消费；跨operation使用`@SomaKey`，不把内部scratch或Index冒充stable identity |
| FJSP machine selection | 已由application-owned indexed min-heap关闭 | Table继续拥有machine事实；heap只保存MachineId/slot，不把SOMA Index作为长期identity |
| `CF-007` 文档候选完整性 | 已关闭 | 32份旧Owner已按迁移审计处置；正式入口、checker与Report已切换，Temporary已删除 |
| `CF-008` 文档抽象层次与职责混合 | 已关闭 | Design 已建立 `D0/D1/D2/Q`、上位设计和场景追踪；Blueprint 不再承载当前实现盘点、自审或一致性结论；checker 防止结构回退 |

## 3. 不构成差距的观察

Generator 规模、测试文件颗粒度、内部命名或可选性能优化可以是 maintainability/optimization 候选，但在没有证据表明违反 Design 前，不应被 Conformance 伪装成语义偏差。此类问题应进入独立审查或 Temporary，而不是借本表扩大实施范围。

## 4. 关闭规则

差距只有在相关 Owner 已作出决定、实现和测试完成、必要 evidence 通过、Implementation Map 已核对后才能关闭。若 Owner 决定修改 Blueprint/Design，应先在 Temporary 形成候选并获得授权；Conformance 自身不做该决定。
