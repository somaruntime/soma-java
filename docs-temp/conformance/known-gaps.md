# 已知差距与处置

类型：Conformance

状态：候选

Owner：SOMA Java 一致性审查

核对基线：`4b6fa43`

事实范围：当前已确认的 Blueprint/Design/Code/Evidence 差距、分类与 Owner 处置

非事实范围：自动授权实施、未来 roadmap 或重新定义 Design

最后审查日期：2026-07-19

## 1. 未闭合差距

| ID | 分类 | 差距 | 影响 | 当前 Owner 处置 |
|---|---|---|---|---|
| `CF-001` | Blueprint → Code | VRP `Customer` 仍把 input、working state 和 assignment fields 放在同一 row；候选蓝图希望分离 definition/assignment/workspace | 易形成 assignment 与 workspace 的多重表达 | 记录为场景目标差距；若要实施，另开有授权的场景专题并先验证 end-to-end cost |
| `CF-002` | Blueprint → Code | Simulation `Tank`/`Valve` 仍保存 numeric mutable state，同时存在 dense `StateVectorRow` | 需要明确唯一 numeric source-of-truth 和同步失败语义 | 记录为场景目标差距；在正式采用蓝图前不得宣称已完成拆分 |
| `CF-003` | Blueprint → Code | Game `MapTileRow` 混合 immutable terrain 与 mutable `occupantUnit` cache | cache 与 `GameUnit.position` 可能 drift | 记录为场景目标差距；未来专题需定义 rebuild invariant 和 coordinate access lane |
| `CF-004` | Blueprint → Code | FJSP machine selection 当前对 machine rows 做显式动态排序，候选蓝图把跨轮 availability queue 交给 application heap | machine count 增大时可能让全量 selection 成为热点 | 保持为目标/性能选择；先用同语义 benchmark 比较，不在本专题改代码 |
| `CF-005` | Evidence | 当前性能数据来自有限 JDK/OS/architecture 和指定 benchmark lanes | 不能外推为正式支持矩阵或普遍性能优势 | Report 必须保留环境限定；新增 claim 前补对应 evidence |
| `CF-006` | Release evidence | G6 所需真实 SCM、ownership、contact、signing/publishing、clean provenance 和支持矩阵不完整 | 禁止 public RC/release-ready/production-ready 声明 | 保持 `blocked`；发布工作不在当前专题范围 |
| `CF-007` | Documentation governance | `docs-temp/` 尚未完成与全部现有事实的逐项迁移核对，也未获得切换授权 | 新体系不能成为正式 Owner | 由 active Temporary 专题继续管理，成熟后另行请求原子切换 |

## 2. 已关闭但需防回归的差距

| 主题 | 当前状态 | 防回归点 |
|---|---|---|
| Sparse Set / dirty selector / maintained order | 已由 V3 packed exact cutover关闭 | 不恢复读时全表 rebuild、稳定物理顺序或 Sparse Set public model |
| dense stable compaction 假设 | 已关闭 | keyed/dense 均保持 swap-remove；未排序 terminal 不承诺顺序 |
| public row-index list | 已由 `IndexSnapshot` + internal `IndexBuffer` 取代 | 不把内部 scratch 暴露为 stable row identity |

## 3. 不构成差距的观察

Generator 规模、测试文件颗粒度、内部命名或可选性能优化可以是 maintainability/optimization 候选，但在没有证据表明违反 Design 前，不应被 Conformance 伪装成语义偏差。此类问题应进入独立审查或 Temporary，而不是借本表扩大实施范围。

## 4. 关闭规则

差距只有在相关 Owner 已作出决定、实现和测试完成、必要 evidence 通过、Implementation Map 已核对后才能关闭。若 Owner 决定修改 Blueprint/Design，应先在 Temporary 形成候选并获得授权；Conformance 自身不做该决定。
