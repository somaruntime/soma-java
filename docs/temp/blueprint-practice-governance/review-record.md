# Blueprint 逐份审查与一致性记录

类型：Temporary

状态：待统一裁决

Owner：SOMA Java Blueprint 内容质量治理

事实范围：本专题对正式 Blueprint 的审查证据、已完成修正、跨文档一致性结论和待裁决引用

非事实范围：正式 Design、当前实现能力、性能结论和越界修改授权

最后审查日期：2026-07-21

## 1. 审查基线

审查覆盖 `docs/blueprints/README.md` 以及产品、FJSP、VRP、连续仿真、Game 五份正式 Blueprint。判断顺序为：先读相关 Design，再核对 current Implementation/代码/测试/Report，最后只在 Blueprint 内修正目标表达；当前实现差距进入[越界影响登记](external-impact-register.md)。

统一采用以下示例口径：未标为算法伪代码的片段按目标 Java 8 使用代码审查；reference path 可以物化但要披露成本；hot path 必须披露 snapshot、view、lookup、sort、scratch 和 allocation；条件式方案必须说明采用条件与证明义务。

## 2. 逐份审查结论

| 文档 | 用户旅程 | SOMA 建模/API | 应用场景与 Java 8 | 性能与失败边界 | 结论 / 越界项 |
|---|---|---|---|---|---|
| Blueprint 导航 | 增加统一阅读规则 | 明确 Blueprint 不拥有精确 signature | 区分目标代码、伪代码、条件式方案 | 明确 total order、物理 Index 与 allocation 口径 | 通过 |
| 产品蓝图 | 补齐 create/import → exact/update/select → child/export/release | 统一 key/unique/index、IndexBuffer、IndexSnapshot、ColumnView 与 detached materialization | FJSP 示例字段与策略语义自洽 | 区分 readable/hot path，明确生命周期与跨表非事务 | 通过；实现采纳见 `EXT-001/005` |
| FJSP | release、refresh、select、commit、successor、queue membership 闭环 | `by_job_sequence` 改为 unique，删除无消费方 selector；保留 keyed frontier | FCFS=`effectiveReady`，SPT=`setup+processing`；setup、时间溢出、deadlock 与 heap stale entry 规则明确 | Batch/scratch 可复用但不冒充零分配；失败后丢弃 instance | 目标表达通过；见 `EXT-001/005` |
| VRP | 默认 dense rebuild 与条件式 keyed frontier 分开 | route-owned dense child、secondary unique、exact access 与 staging 均有采用条件 | 统一 meter/second/load；完整 insertion ordinal、suffix propagation、hard constraints 与 total comparator | route rewrite、stale guard、authoritative/derived commit failure 分层 | 目标表达通过；见 `EXT-002/005` |
| 连续仿真 | definition → state vector → event boundary → integration → trace 闭环 | dense long-lived vector；heap 是 queue，Table 只是 projection | relative nanoseconds、immutable heap node、derivative staging、finite/scale 校验 | session scratch 复用；view/read 与 write 分阶段；失败使用 checkpoint/fail-stop | 目标表达通过；见 `EXT-003/005` |
| Game | turn selection → move workspace → prepared commit → occupancy recovery → damage resolution 闭环 | keyed tile/occupancy、dense phase workspace、Index 不跨表复用 | total turn/move/damage order；action context/revision；cache rebuild 与 command validation | readable allocation 明示；authoritative commit 后 cleanup 不可伪装回滚 | 目标表达通过；见 `EXT-004/005` |

## 3. 关键修正摘要

### 3.1 产品蓝图

- 把使用者旅程从抽象能力列表补成可读与低物化两条目标路径；
- 明确一次 pipeline 逐级缩小当前 Index 集，而不是每个 stage 回到全表；
- 统一 IndexSnapshot 的 caller-responsibility 契约、ColumnView 关闭、Batch detached copy 和 aggregate lifecycle；
- 明确未排序 terminal、swap-remove、range scan 与 external queue 的能力边界。

### 3.2 FJSP

- 使 annotation 只服务真实 access pattern：job/sequence 是 secondary unique；不保留未消费的 machine-state 和 setup secondary selector；
- 修正 derived indicator 定义和初始化，使 comparator 只消费 ready indicator；
- 补齐 first-operation/successor release 对 external indexed heap 的激活协议、stale entry 淘汰和无进展结局；
- 让 reference lookup、flattened lookup、snapshot terminal、Batch 复用和潜在 Value Object 分配各自拥有诚实口径。

### 3.3 VRP

- 固定 canonical CVRPTW 的单位、hard-constraint 与 required directed travel-cost 语义；
- 以 `[0, visitCount]` insertion ordinal 代替含糊的 insert-after 位置，并用 route version 防止 stale candidate；
- 选择 parent-owned dense route visits；flat root 只保留同语义 benchmark baseline；
- 让 candidate builder、route rewrite、commit preflight、authoritative failure 与 derived rebuild 的职责可检查。

### 3.4 连续仿真

- 把 application heap 确认为 pending-event 唯一 queue，Table projection 不反向驱动 event loop；
- 统一 relative nanoseconds，并给相同时刻事件建立 `(time, sequence)` total order；
- 把 derivative staging、Euler reference step、coupled-vector scratch、finite/scale 校验和 trace 批量追加串成完整流程；
- 明确 `StateVectorRow` 是 numeric source-of-truth，definition/entity cache 不再拥有可写 shadow。

### 3.5 Game

- 把 immutable tile definition 与 derived occupancy cache 拆为两个 keyed roots，并明确 position 的唯一事实源；
- 把 selected-unit dense move workspace 与 application action context 绑定，避免在每行重复 unit identity；
- 为 move commit 增加 origin/AP/pathing revision/cache guards，并规定 cache failure 的 rebuild 协议；
- 为 damage buffer 增加 sequence total order、完整 primitive staging 和不可重放的 fail-stop 边界。

## 4. 跨 Blueprint 一致性检查

| 一致性维度 | 统一结论 | 覆盖结果 |
|---|---|---|
| 目标与当前 | Blueprint 只定义目标形态；当前支持与证据仍由 Implementation/Conformance/Report 判断 | 五份一致 |
| stable identity | 跨 operation 保存引用使用 `@SomaKey` 或 application identity；dense position/current Index 不是 identity | 五份一致 |
| exact access | `@SomaUnique` 为 0/1 secondary exact，`@SomaIndex` 为 0..n exact；都不表达 order/range | 五份一致 |
| 候选顺序 | 业务顺序来自显式 total comparator；跨轮动态优先级归 application-owned heap/scheduler | 五份一致 |
| 物理顺序 | swap-remove 后不保证物理顺序；未排序 terminal 不能承担业务语义 | 五份一致 |
| Index 消费 | Index/IndexSnapshot 只在同一同步只读批次立即消费；任意来源 mutation/lifecycle 后失效；不能跨 Table 复用 | 五份一致 |
| materialization | `fetch/first/fetchAll` 返回 detached value/object graph；hot path 可用 cursor/view，但成本仍显式 | 五份一致 |
| Batch/scratch | Batch 是 detached staging，copy 完成后才复用；大型 scratch 有 owner、上限和生命周期 | 五份一致 |
| ColumnView | 使用 try-with-resources；同表 mutation 与 active read view 分阶段 | 五份一致 |
| failure | 单次 Table operation 失败原子；跨 root/heap/cache 不具备 transaction，application 明确 fail-stop/rebuild/checkpoint | 五份一致 |
| Java 8 | comparator total、PriorityQueue node immutable、checked arithmetic、时间单位与资源关闭明确 | 五份一致 |
| 性能 claim | reference 与 hot path 分开；snapshot、Value Object、materialization、sort、lookup、builder 和 DTO 成本不隐藏 | 五份一致 |

## 5. 一致性裁决

Blueprint 集合内部没有发现仍未处置的规范性冲突。五个场景共享同一套 SOMA 边界，但没有机械复制同一种 Table 形态：FJSP 使用跨轮 keyed frontier，VRP 默认 dense candidate workspace，Simulation 使用 long-lived dense vector 与外部 heap，Game 使用 selected-unit dense workspace 和 keyed derived cache。这些差异均由访问模式、identity 和 lifecycle 解释。

当前未收口项全部属于 Blueprint 之外的采纳、实现与 evidence 差距，详见 `EXT-001` 至 `EXT-005`。它们阻塞“当前实现已经符合新目标”的结论，但不阻塞 Blueprint 作为目标文档完成内部收口。

## 6. 验证与范围审计

| 检查 | 结果 | 结论边界 |
|---|---|---|
| 旧 order/time/API/Index/Stream 语义搜索 | 通过 | 命中只剩明确的禁止、替代或迁移说明 |
| Markdown fence 与跨文档术语检查 | 通过 | 代码块闭合，核心术语一致 |
| 目标 Java 8 snippet 人工审查 | 通过 | API family、total order、资源/异常/数值边界成立；片段允许省略 import/owner/helper，不是独立编译证据 |
| `./scripts/check-docs.sh` | 通过 | scope 与链接/文档规则通过 |
| `git diff --check` | 通过 | 无 whitespace error |
| `./scripts/check.sh` | `project-check: ok` | 当前 build、runtime、generated consumers、examples、benchmark smoke 无回归；不证明新 target 已实现 |
| changed-path audit | 通过 | 仅 `docs/blueprints/` 与本专题 Temporary 有版本库改动 |

最终结论：五份 Blueprint 已能共同承载 SOMA Java 的目标最佳实践、能力边界、场景差异和 Java 8 使用形态；内部一致性通过。Implementation/Conformance/evidence 的采纳工作尚待用户对越界登记统一裁决。
