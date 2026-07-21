# Blueprint 最佳实践专题治理

类型：Temporary

状态：待统一裁决

Owner：SOMA Java Blueprint 内容质量治理

事实范围：本专题的意图、目标、授权边界、审查方法、阶段结论与退役条件

非事实范围：正式 Blueprint/Design 语义、当前实现事实、性能结论和实施授权

最后审查日期：2026-07-21

## 1. 意图

本专题审查 SOMA Java 的全部正式 Blueprint 是否真正展示了值得使用者模仿的目标用法，而不只检查文档分类和结构是否正确。审查同时覆盖 SOMA 建模、能力边界、应用场景可信度、Java 8 工程写法与跨场景一致性。

## 2. 授权边界

本专题允许修改：

- `docs/blueprints/` 中的正式 Blueprint；
- `docs/temp/blueprint-practice-governance/` 中的专题材料。

本专题不修改 Design、Implementation Map、Conformance、Engineering、Report、Guide、代码、测试、benchmark、生成物或构建配置。若审查发现这些范围需要变化，只登记到 [越界影响登记](external-impact-register.md)，不把 Blueprint 的目标草案伪装成已经实现的事实。

## 3. 治理目标

1. **目标用法可信**：每份 Blueprint 都从使用者视角给出清晰的 canonical journey，并说明 reference path、hot path 与条件式方案。
2. **SOMA 用法正确**：Table kind、identity、ownership、exact access、pipeline、Index、materialization、lifecycle 和 failure 的使用不违反正式 Design。
3. **能力边界诚实**：不暗示 maintained order、range index、稳定物理 Index、跨 Table transaction、并发访问、持久化或 SOMA 所不拥有的业务能力。
4. **应用场景成立**：非 SOMA 代码足以表达正确的 FJSP、VRP、连续仿真或回合制游戏流程，不用含糊 helper 隐藏决定结果正确性的关键语义。
5. **Java 8 写法可取**：示例遵守 total comparator、资源关闭、异常/失败边界、数值与时间单位、lambda/回调边界和 allocation-aware hot-path 原则。
6. **跨 Blueprint 一致**：相同概念使用相同术语、责任边界和示例层级；场景差异由 Access Pattern 解释，而不是偶然写法造成。

## 4. 审查矩阵

每份 Blueprint 按以下问题逐项检查：

| 维度 | 核心问题 | 不通过示例 |
|---|---|---|
| 用户旅程 | 用户能否理解何时、为何以及怎样使用 SOMA | 只有结构说明，没有完整 operation flow |
| 数据角色 | input、working、workspace/frontier、result 是否只有一个事实 Owner | authoritative state 与 cache/shadow row 并存 |
| Table/访问 | keyed/dense、root/child、key/unique/index 是否符合访问模式 | 因为“有字段可当 key”就使用 keyed table |
| 候选与顺序 | stage 是否逐级缩小，业务顺序是否显式且 total | 假设 physical order 或虚构 maintained-order API |
| 生命周期/失败 | Index、view、snapshot、root/child、跨表失败边界是否明确 | 保存 current Index，或暗示跨表自动回滚 |
| 性能诚实性 | reference 与 hot path 是否区分，allocation/lookup/sort 是否显式 | 把每轮新数组或 materialization 称为低分配路径 |
| 场景语义 | application helper、队列、积分、提交和 cache 是否足够自洽 | comparator、event ordering 或 stale guard 不完整 |
| Java 8 | 代码形态是否可编译、确定、可关闭且不过度分配 | 不完整 tie-break、资源泄漏、回调中危险副作用 |
| 证据边界 | 目标、当前能力和待证明事项是否区分 | 用 Blueprint 宣称当前性能或实现已具备 |

## 5. 示例分级规则

- **目标使用代码**：应尽量是可编译的 Java 8 形态；允许省略声明和样板，但不能依赖未说明的虚构语义。
- **算法伪代码**：用于表达阶段和责任，必须显式说明不是 API 或完整算法实现。
- **条件式方案**：必须给出采用条件、失效条件和需要的 evidence，不能与默认推荐路径并列为无条件最佳实践。
- **当前实现证据**：不进入 Blueprint；由 Implementation Map、代码、测试和 Conformance 承担。

## 6. 验收标准

- 产品、FJSP、VRP、连续仿真和 Game 五份 Blueprint 均完成矩阵审查和必要优化；
- annotation 与 generated API 示例不与已读 Design 相矛盾，目标草案与当前 surface 的差异均被明确标注或登记；
- 所有业务 comparator 覆盖决定性 tie-break，所有长期顺序归 application，未排序 terminal 不被赋予业务语义；
- 所有声称 allocation-aware/hot-path 的示例不包含未披露的 per-row/per-step materialization、boxing collection 或大块临时分配；
- 应用代码不在同一 Table callback 中做该 Table 的结构变更，也不把跨 Table side effect 误写成 SOMA 原子操作；
- 五份文档对 Index、IndexSnapshot、ColumnView、materialization、swap-remove、exact access 和 external queue 的表述一致；
- 越界影响具有目标 Owner、证据、建议处置和是否阻塞 Blueprint 收口的结论；
- `./scripts/check-docs.sh` 与 `git diff --check` 通过，且最终 diff 不包含授权范围外文件。

## 7. 阶段

```text
baseline / Design / current evidence read
  -> product Blueprint
  -> FJSP Blueprint
  -> VRP Blueprint
  -> simulation Blueprint
  -> Game Blueprint
  -> cross-Blueprint consistency review
  -> external-impact register and validation
```

专题收口时，若仍有未获授权的越界事项，本 Temporary 保留为待统一裁决输入；其存在不表示相关修改已经获授权。所有越界事项完成裁决和必要实施后，再将长期事实原子固化到相应 Owner 并删除本专题 Temporary。

## 8. 本轮收口结果

- 五份 Blueprint 与导航均已按审查矩阵完成优化；逐份证据和跨文档结论见[审查记录](review-record.md)；
- Blueprint 内部已统一目标/当前边界、identity/exact access、total order、Index 消费、materialization、Batch/scratch、ColumnView、failure 和 Java 8 示例口径；
- 未识别出 canonical 路径必须新增 core Design 或 public API 才能成立的 blocker；
- 发现的 FJSP、VRP、Simulation、Game 采纳差距和横向 evidence 影响已登记为 `EXT-001..005`，尚未获得越界修改授权；
- `./scripts/check-docs.sh`、`git diff --check` 和完整 `./scripts/check.sh` 均通过；完整 Gate 只证明当前工程未因本轮文档修改回归，不证明代码已经达到新 Blueprint。

因此本次 Blueprint 内容治理本身已经收口，Temporary 转为专题后统一裁决输入，暂不删除。
