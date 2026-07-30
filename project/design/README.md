# Design 导航、层次与 Owner

类型：Design 入口

状态：正式

Owner：SOMA Java 系统设计

事实范围：正式 Design 的抽象层次、关注点 Owner、上位/下位关系与 Blueprint 追踪

非事实范围：当前实现位置、验证结果和迁移过程

最后审查日期：2026-07-29

Design 拥有系统应当遵守的长期规范性设计。它同时按抽象层次展开责任、按关注点分配唯一 Owner；层次不是目录结构，关注点也不是重复定义同一事实的理由。

## 1. 抽象层次

| 层次 | 回答的问题 | 展开关系 |
|---|---|---|
| `D0` 系统原则 | SOMA 是什么、边界在哪里、哪些不变量不可违反 | 直接服务 Blueprint，约束全部下位 Design |
| `D1` 系统结构 | 系统如何分层、模块如何协作、跨模块使用什么语言 | 展开 D0，不进入能力内部机制 |
| `D2` 能力设计 | 每项核心能力应提供什么语义、边界和机制约束 | 展开 D1；必要机制留在其唯一 Owner 内 |
| `Q` 横切质量 | correctness、performance、compatibility 等如何约束所有层次 | 直接受 D0 约束，并横切 D1/D2 |

一份文档可以涉及相邻层次，但必须声明承担主要设计责任的层次和上位设计。机制尚未形成独立责任时，不为追求形式新增 `D3` 文件；形成新 Owner 时再通过专题治理拆分。

## 2. 关注点 Owner

| 层次 | Design Owner | 唯一拥有的事实 |
|---|---|---|
| `D0` | [设计宪法](soma-java-design-constitution.md) | 总体原则、产品边界和不可违反的系统级不变量 |
| `D1` | [系统架构](system-architecture.md) | 模块职责、编译链、运行时分层和依赖方向 |
| `D1` | [领域语言](domain-language.md) | 跨模块 canonical 术语和“不等同于”边界 |
| `D2` | [Schema 与生成 API](schema-and-generated-api.md) | annotation 语义、schema normalization、生成接口、公开 IndexSnapshot 消费契约和命名边界 |
| `D2` | [Table、存储与访问](table-storage-and-access.md) | keyed/dense、packed SoA、primary/exact structures、swap-remove 和 IndexBuffer |
| `D2` | [Access Model 与 Candidate Scan](access-model-and-candidate-scan.md) | 访问族、组合代数、Candidate Scan、terminal、one-shot 与成本边界 |
| `D2` | [Transformation Model](transformation-model.md) | Logical Shape、Value、Expression、Operator、Result、Effect 与组合合法性 |
| `D2` | [DataFlow 执行模型](dataflow-execution-model.md) | Definition/Template/Invocation、binding、资源、并行、safe point 与执行 identity |
| `D2` | [Ownership 与 lifecycle](ownership-and-lifecycle.md) | SomaGroup、root/child ownership、view、epoch/currentness 和资源生命周期 |
| `D2` | [Result Delivery 与 Materialization 边界](materialization-boundary.md) | Eager/callback delivery、detached object graph、预算、导出与 allocation admission |
| `D2` | [Runtime Plan 与可观测性](runtime-plan-and-observability.md) | Metadata phases、Runtime/Group Plan、resource hierarchy、Observation/Explain Owner |
| `Q` | [Correctness 与 failure](correctness-and-failure.md) | 原子性、一致性、结构化错误和失败后的可信状态 |
| `Q` | [性能模型](performance-model.md) | hot-path 机械形状、成本模型、优化约束与证据要求 |
| `Q` | [兼容性、安全与版本](compatibility-security-and-versioning.md) | 兼容面、协议身份、输入信任边界、产品与发布身份 |

## 3. Blueprint → Design 追踪

| Blueprint | 直接约束其目标形态的 Design |
|---|---|
| [SOMA Java 产品蓝图](../blueprint/soma-java-product-blueprint.md) | 全部正式 Design；D0/D1 定义系统方向，Access/Transformation/DataFlow 展开本地计算能力，其余 D2/Q 闭合状态与质量边界 |

参考应用的应用级 Blueprint 不进入本表，也不拥有 SOMA Design；它们只通过 public artifacts 消费这里定义的产品能力。

## 4. 核心抽象叙事闭环

每个跨模块或长期核心抽象必须在其唯一 Design Owner 及必要链接中闭合下列问题。
这是一套理解和替换约束，不要求每份文档复制相同十段，也不要求为每个实现类型
建立独立 Design。

| 维度 | 必须回答的问题 |
|---|---|
| Why | 它服务哪个 Blueprint journey、Capability 或不可变量，为什么需要独立存在？ |
| Owns | 它唯一拥有哪些事实、语义、资源或协调责任？ |
| Not | 哪些相邻责任明确不属于它，application 或其他 Owner 继续负责什么？ |
| Relationships | 它与上位抽象、相邻 Capability、consumer 和下位实现怎样组合？ |
| Lowering | 逻辑语义怎样降低为 generated carrier、runtime plan 与 physical strategy，哪些内部替换不泄漏？ |
| Lifecycle | 它何时创建、freeze/bind/execute、失效、完成和释放，谁拥有每个阶段？ |
| Resource | 它怎样 admission、预算、分配、复用、观测和拒绝无界资源？ |
| Failure | expected failure、invariant failure 与部分提交怎样关闭，失败后哪个事实仍可信？ |
| Evidence | 哪些 compile/golden/contract/consumer/benchmark/qualification 直接证明上述责任？ |
| Evolution | additive、breaking、internal replacement 如何判断，predecessor 与 migration artifact 怎样退出？ |

同一维度可以由 Owner 内的一段说明或指向另一唯一 Owner 的链接回答。`不适用`
必须说明原因；`以后补充`、当前只有一个调用者、代码能运行或 benchmark 更快都不
构成闭环。精确 signature、路径和当前测量继续由代码、Implementation Map 与
Report 拥有，不能为了叙事完整在 Design 中复制实现清单。

专题 closeout 必须抽查受影响的核心抽象是否仍能回答这十项，并确认新增类型或
拆分确实对应独立语义、Owner、生命周期或失败域；否则不为降低 LOC 或满足模板
制造表面抽象。

## 5. 阅读与变更规则

新增或修改功能时，从相关 Blueprint 进入本页，先读上位 Design，再读承担该关注点的唯一 Owner；随后通过 Implementation Map 找到代码，用 Conformance 识别已知偏差。一个事实跨越多个关注点时，由最直接决定其语义的文档拥有，其他文档只给必要上下文并链接 Owner。

设计宪法不复制下位机制；复合 Design 必须在文内说明能力语义与机制约束的展开边界。精确的当前 signature、field、error code 和默认常量由代码及[可执行契约地图](../implementation-map/executable-contract-map.md)定位，不能在 Design 中维护第二份实现清单。实现路径进入 Implementation Map，测量结果进入 Report。
