# 复杂度可持续性 Stage 0 审计

类型：Temporary

状态：active（Stage 1 实施与验证完成；等待项目 Owner 结果审查）

Owner：SOMA Java 复杂度可持续性 Stage 0 审计专题

事实范围：本专题的意图、限定范围、授权边界、Stage 0 证据结论、待裁决问题和停止条件

非事实范围：SOMA 长期产品语义、正式复杂度结论、实现方案、实施授权或 public release readiness

正式事实源：否

实施授权：已完成（当前停止点不包含正式固化或 Temporary 退役）

Stage 0 不可变基线：commit `e69eec2`

最后审查日期：2026-07-23

## 1. 意图

上一轮项目复杂度与可维护性治理已经减少历史文档负担，并完成 processor/codegen 第一层责任拆分。本专题不预设还需要第二轮全项目治理，而是检查四个剩余信号是否代表真实、可持续的维护风险。

审计继续遵守“Design 服务 Blueprint，实现服务 Design”。文件大小、LOC 和静态依赖只是定位信号；只有责任边界、变化原因、扩展成本和 evidence 共同支持时，才可进入后续裁决。

## 2. Stage 0 目标

1. 区分接近专题回归阈值与接近产品/compiler 极限；
2. 判断 benchmark lane 的集中是否已经产生独立变化原因和依赖循环；
3. 识别 processor emitter 分责后仍存在的逆向或 sibling 依赖；
4. 判断 runtime 大状态机是偶然职责堆积，还是原子性要求下的有意协调边界；
5. 给出下一阶段需要裁决的问题，但不选择或实施方案。

详细证据见 [Stage 0 审计记录](stage-0-audit.md)。

## 3. 限定范围

本专题只覆盖：

- generated Scan footprint 的规模、增长形状、Gate 口径和长期可持续性；
- benchmark smoke lane 的登记、执行、聚合、校验与 evidence 责任组织；
- processor normalized/admission/emitter/orchestrator 之间的剩余依赖；
- `DenseTableState`、`ChildOwnershipRegistry` 等 runtime 状态机是否具有共同变化原因。

本专题不重新审计：

- Blueprint、正式 Design、文档历史拓扑和 Report 归档；
- public/generated API、annotation Schema、Access Model 或 runtime 语义；
- 性能优化本身、第三方依赖、其他 JDK、G6、发布或 support matrix；
- tests、fixtures 和全部脚本的通用瘦身。

## 4. 授权边界

Stage 0 只授权读取正式 Owner、代码、Git 历史与可执行 evidence，并维护本 Temporary 及 `docs/README.md` 的 active-topic 登记。项目 Owner 已在 Stage 0 基线提交后授权以下 Stage 1：

- benchmark lane 的内部责任重组；
- processor selector model、emitter support 与 artifact helper 的内部所有权修正；
- generated footprint 的归一化诊断 evidence；
- 与上述内部实现直接相关的测试、fixture、脚本和 checker；
- 每个 slice 的独立验证与提交。

当前仍没有以下授权：

- 修改正式 Blueprint、Design、Implementation Map、Conformance、Engineering 或 Report；
- 修改 Gate 阈值、既有基线、Schema、API、运行时协议或性能语义；
- 拆分 runtime 状态机；
- 引入第三方依赖、删除 evidence lane 或降低验证；
- 正式固化长期事实或删除本 Temporary。

Stage 1 详细设计见 [Stage 1 设计](stage-1-design.md)。

Stage 1 的实际结果、非回归证据与残余风险见
[Stage 1 结果审查](stage-1-result-review.md)。

## 5. 非回归约束

后续若获授权，仍必须保持：

- public/generated API、annotation Schema、schema hash 与四场景能力不缩水；
- Candidate Scan one-shot、typed source、callback order、stable sort、logical stats 和 failure atomicity 不变；
- generated hot path 不退化为 reflection、metadata interpreter、Java Stream、boxing graph 或 per-candidate polymorphic dispatch；
- benchmark lane 的独立 failure/consumer/measurement 价值不因抽象而合并；
- ownership forest、resource preflight、Index 生命周期和 Table operation 原子性不变；
- 不通过放宽 code-size Gate 或删除验证来制造“可持续”结论。

## 6. Stage 0 摘要

| 关注点 | Stage 0 判断 | 是否支持立即实施 |
|---|---|---|
| generated footprint | 当前相对回归 Gate 已接近上限，但离单 artifact/schema admission 上限仍很远；现有 Gate 不是长期归一化容量模型 | 否；需要 Stage 1 裁决 measurement model |
| benchmark lane | 已确认执行、登记、lane-specific validation、聚合集中，并与 `BenchmarkModel` 双向依赖 | 否；证据足以进入 Stage 1 责任裁决 |
| processor emitter | 已确认 admission 依赖 emitter support，以及 Table emitter 依赖 Auxiliary emitter 的 Table helper | 否；证据足以进入 Stage 1 依赖归属裁决 |
| runtime 状态机 | 大小信号成立，但历史变化与 table/ownership 原子协调共同发生；目前没有拆分收益证据 | 否；默认保持，仅定义未来触发条件 |

Stage 0 没有发现功能缺陷、性能回退或必须立即修改 public Design 的偏差。

## 7. Stage 1 裁决

项目 Owner 已批准一次有边界的 Stage 1：

```text
Stage 1A  benchmark lane responsibility
Stage 1B  processor dependency ownership
Stage 1C  generated footprint evidence
Stage 1D  runtime retain decision：无代码变更
```

三个实施 slice 必须可以独立保留并分别验证。Stage 1 最后停在结果审查处；未经新的明确授权，不修改正式 Owner、不形成最终 Governance Report、不删除 Temporary。

三个实施 slice 已分别完成并提交；完整 Gate 已通过。专题当前停在项目
Owner 结果审查处。

## 8. Stage 0 完成与停止点

Stage 0 完成条件：

- 四个范围均有 live code、Git history、正式 Owner 和 Gate 证据；
- 明确区分 confirmed finding、观察信号与未证明假设；
- `docs/README.md` 登记本 active Temporary；
- 文档 Gate、完整项目 Gate 和 `git diff --check` 通过；
- 没有发生任何实现或正式事实变更。

上述条件已经在 commit `e69eec2` 达成。Stage 1 完成后，本专题继续保持 active，等待项目 Owner 审查实施收益与范围非回归；不自动删除或晋升。

## 9. 退役条件

只有在项目 Owner 决定不继续，或后续授权阶段完成全部裁决、实施、验证与正式事实固化后，才能删除本 Temporary。若形成长期治理结论，应先进入唯一 Engineering/Implementation Map Owner 或 Governance Report，再删除本目录；Temporary 不归档。
