# 项目复杂度与可维护性治理

类型：Temporary

状态：active（Stage 0.1 已完成，Stage 1–5 已授权）

Owner：SOMA Java 项目复杂度与可维护性治理专题

事实范围：本专题的意图、目标、授权边界、非回归约束、2026-07-23 基线证据、待裁决事项、阶段与验收设计

非事实范围：SOMA 长期产品语义、当前 public/schema/runtime 契约、具体删改清单和未经验证的最终实现方案

正式事实源：否；专题收口前，本文件只承载候选设计与过程证据

核对基线：commit `2ff09b1`

最后审查日期：2026-07-23

## 1. 意图

在 Access Model 与产品化治理完成后，主动控制项目的认知成本、职责耦合、变更放大和历史材料负担，使 SOMA 能继续演进为接近 Java 标准库质量的长期库。

本专题治理的是“复杂度是否服务于产品设计”，不是追求更少的文件或代码行。任何删除、合并或拆分都必须有责任边界、使用路径和可执行证据支撑。

## 2. 目标

1. 区分产品必要复杂度、证据复杂度、历史负担和偶然实现复杂度；
2. 缩小日常工作需要理解的 active surface，并维持正式事实的唯一 Owner；
3. 降低 compiler/codegen 的职责集中度与跨关注点变更放大；
4. 评估测试、fixture、scenario、benchmark 和脚本中的重复机制，但保留独立证据价值；
5. 建立可持续的复杂度观测与防回归 Gate；
6. 在全过程证明功能、API、Schema、运行时语义和性能目标没有缩水或偏移。

成功不以总行数下降为必要条件；若结构更清楚、变更面更小且证据完整，即使行数持平也可成立。

## 3. 当前授权边界

本专题已授权在项目边界内自主完成 Stage 1–5，包括：

- 修改与本专题相关的正式文档、Report、Java 生产代码、测试、fixture、scenario、benchmark、脚本和 checker；
- 基于证据拆分、合并、重命名或删除内部实现与历史材料；
- 完成内部架构裁决、阶段性 immutable candidate 和 Git 提交；
- 最终固化长期事实、形成 Governance Report 并退役本 Temporary。

下列事项超出授权，必须停止并请求项目所有者决定：

- public/generated API 或 annotation Schema 的不兼容变化；
- Access Model、运行时语义、ownership、Index 生命周期或失败原子性变化；
- 删除产品能力或场景目标、弱化验证 Gate、引入第三方依赖；
- 扩大到本专题之外的产品设计、push、发布或 release readiness 工作。

## 4. 不变量与非目标

- Design 服务 Blueprint，实现服务 Design；当前代码或 LOC 不能反向降低目标；
- 保持完整 Access Model、组合代数、能力边界与四个正式场景目标；
- 不破坏 public/generated API、annotation Schema、schema hash、错误语义、ownership、Index 生命周期和失败原子性；
- 不改变 packed columnar storage、exact access path、swap-remove 及既有性能设计；
- 不以“未被静态搜索使用”作为删除依据；generated consumer、fixture、外部消费和反射边界必须纳入追踪；
- 不以通用 emitter DSL、metadata interpreter、reflection、Java Stream 或对象图替代显式 Java 8 hot path；
- 不为满足任意文件数、类数或行数指标而合并不同 Owner，或拆出没有独立责任的薄层；
- 不在本专题处理新功能、release readiness、其他 JDK、第三方依赖或产品边界扩张。

## 5. Stage 0 基线

以下数字只统计 commit `2ff09b1` 的 Git tracked 内容；IDE 截图中的 `target/`、生成源码、缓存和 `.DS_Store` 不属于可维护源码基线。

| 观察面 | 文件 | 行数 | 初步含义 |
|---|---:|---:|---|
| 全部 tracked | 608 | — | 与截图的 782 个文件不是同一口径 |
| Java | 362 | 33,613 | 包含生产、测试、fixture、example、benchmark |
| Markdown | 129 | 19,040 | 包含 current、historical 与 Report |
| Shell | 29 | 3,857 | 主要承载可执行 Gate 与专项检查 |
| 正式五类文档 | 36 | 4,850 | Blueprint 2,598；Design 仅 1,383 行 |
| `superseded` 文档 | 26 | 7,214 | 最大的文档历史负担候选 |
| `reports/` 全树 | 30 | 3,959 | 同时包含 current 与 archive evidence |
| annotations + processor + runtime 主源码 | — | 16,372 | 核心产品实现口径 |
| 其余 Java | — | 17,241 | 测试、fixture、场景和 benchmark 等证据口径 |

核心生产源码中，`DenseTableSourceGenerator` 为 4,432 行，`SomaProcessor` 为 3,054 行；二者合计 7,486 行，约占上述核心产品实现的 45.7%。这只是职责集中度信号，不自动构成拆分类或删除代码的结论。

### 5.1 初步判断

- 当前正式 Design 本身没有显示出规模失控；主要文档机会在 historical surface、current 导航和重复事实；
- compiler/codegen 的两个核心类同时承载 discovery、normalization、admission、artifact orchestration 与多类 emitter 责任，是首要变更放大候选；
- runtime core 当前不是第一瘦身目标，除非后续责任与变更证据证明存在问题；
- 测试、fixture、scenario 和 benchmark 的体量大多是产品可信度成本，不能与生产膨胀等同；
- 脚本数量本身不是坏味道，应先证明重复机制、漂移或维护成本。

## 6. 质量与成本模型

后续裁决同时观察：

- **active cognitive surface**：完成一种变更必须阅读和修改多少 Owner；
- **change amplification**：一个关注点变化波及的类、模板、golden、fixture 与脚本数量；
- **fact duplication**：同一规范性事实的定义位置和同步成本；
- **responsibility concentration**：单文件独立变化原因、内部耦合和测试隔离难度；
- **evidence value**：重复外观是否对应不同 compiler/runtime/consumer 证据；
- **generated footprint**：生成源码、class size、编译时间与 runtime 代价；
- **validation cost**：完整 Gate 的时间、脆弱性和问题定位成本。

LOC、文件数和类数只用于发现异常，不作为独立验收指标。

## 7. 待裁决事项

1. **历史设计**：删除 `superseded` 正文并依赖 Git/Governance Report，还是仅为仍需稳定链接的路径保留薄 tombstone；须先完成引用与证据价值审计。
2. **Report 拓扑**：current 结论与历史 evidence 是否需要更强的入口隔离；不得丢失 provenance。
3. **Blueprint 重复**：共用约束是否可由唯一 Design Owner 承担，Blueprint 继续保留完整用户 journey 与必要示例。
4. **Processor/codegen 分责**：是否形成 `Discovery -> Normalized Model -> Admission -> Artifact Emitters -> Output`；拆分点必须来自稳定责任，不先预定文件数量。
5. **Evidence 组织**：test/fixture/scenario/benchmark/script 中哪些是共享机制，哪些必须保持独立证据 lane。
6. **复杂度预算**：为职责集中、生成物尺寸、编译时间和 Gate 成本建立何种软阈值；阈值只触发审查，不自动要求删改。

上述事项均未在 Stage 0 作最终决定。

## 8. 候选演进方向

若证据支持，compiler/codegen 可朝下列责任流演进：

```text
schema discovery
  -> normalized schema model
  -> admission / compatibility decisions
  -> artifact-specific emitters
  -> deterministic outputs
```

artifact-specific emitter 可以围绕 Table、Scan、Point/Exact、Mutation、Ownership、Materialization、Column/Key 等稳定关注点组织。它们共享显式 normalized model，但不引入通用模板语言，也不改变生成契约。该结构是调查假设，不是已批准设计。

## 9. 阶段设计

| 阶段 | 结果 | 停止点 |
|---|---|---|
| Stage 0 | 真实基线、边界、不变量、待裁决问题 | 本文完成后停止 |
| Stage 1 | 引用/Owner/变更放大审计与逐项决策 | 未授权实施前停止 |
| Stage 2 | 经授权的文档拓扑治理 | immutable docs candidate |
| Stage 3 | 经授权的 processor/codegen 内部重构 | 每个 slice 均可独立保留 |
| Stage 4 | 经授权的 evidence 与 Gate 结构优化 | immutable evidence candidate |
| Stage 5 | 全量审查、正式事实原子固化与 Temporary 退役 | 完整 closeout |

阶段可在证据允许时合并，但目标、不变量、授权停止点和完整 Gate 不得省略。每个实施 slice 必须是最终设计的有效子集，不能依赖未来重写才正确。

## 10. 验收与非回归

实施阶段至少证明：

- 受影响 Design、public/generated API、Schema、hash 与 golden 无未经授权变化；
- compiler golden、external consumer、runtime invariant 和四场景 evidence 继续通过；
- 生成源码/class size、编译时间、allocation 与受影响 benchmark lane 没有未解释退化；
- 文档 current/historical 导航、唯一 Owner、链接和 metadata 通过检查；
- `./scripts/check.sh`、`git diff --check` 及受影响专项 Gate 通过；
- 删除项具有替代路径、引用闭包和 provenance 证据；内部拆分没有产生平行模型或转发层堆积。

性能判断使用既有多 fork/稳定口径，不以单次 wall-clock 作为结论。

## 11. 已知外部影响

Stage 0.1 已在 `docs/README.md` 登记本专题，并使 `scripts/check-docs.sh` 根据 `docs/temp/` 的实际目录验证 active 状态。本专题退役后，入口恢复“当前没有 active Temporary topic”，同一通用规则验证无 active topic 状态。

## 12. 退役条件

专题完成后，将长期事实分别固化到唯一的 Blueprint、Design 或 Engineering Owner，刷新必要的 Implementation Map、Conformance 与 current Report；若过程证据具有长期价值，形成 Governance Report。完成全量 Gate 和切换授权后删除本目录，不归档 Temporary。
