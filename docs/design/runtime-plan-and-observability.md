# Runtime Plan 与可观测性设计

类型：Design

状态：正式

Owner：SOMA runtime configuration 与 observability semantics

设计层次：`D2` 能力设计

主要关注点：Create-time execution plan、resource admission、stats 与诊断边界

上位设计：[系统架构](system-architecture.md)

服务蓝图：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

事实范围：create-time runtime plan、resource admission、plan identity、stats mode、snapshot/reset 与诊断副作用边界

非事实范围：Java builder 的当前完整方法清单、内部计数器字段、某次统计结果和 benchmark 阈值

最后审查日期：2026-07-23

本 Owner 把 plan 与 observability 放在同一能力边界：plan 决定实例创建时准入哪些资源和观测成本，observability 只能暴露该实例已选择的模式，不能反向引入隐藏策略。当前 builder 方法和计数器布局属于实现事实。

## 1. Runtime Plan 的角色

`RuntimePlan` 是 generated table create boundary 的 immutable execution contract。它在 aggregate 发布前确定 capacity、resource、access structure、stats 和 materialization 等运行策略；hot path 不重复解析 schema metadata，也不从全局配置、环境变量或动态 registry 隐式取值。

Plan 至少覆盖以下维度：

- schema/generated/runtime protocol identity；
- root table 与所有 reachable child table 的有效 table plan；
- initial capacity、growth、primary locator 与 secondary exact-access policy；
- aggregate storage、bulk scratch、retained scratch 和 materialization budget；
- stats mode 与诊断采集级别；
- estimator、algorithm 和 plan protocol identity。

这里规定语义类别；当前 Java signature、默认常量和字段清单由[可执行契约地图](../implementation-map/executable-contract-map.md)定位。

## 2. Scope 与绑定

- generated facade 提供 schema-specific default plan，并允许 caller 在 create 前基于 builder 形成显式 override；
- override precedence 必须确定，未识别 table/child/field/selector path 必须 fail closed；
- root plan 覆盖整个 ownership aggregate，child 不能在 attach 后拥有脱离 root 的隐式 plan；
- effective plan 在 create 时完成 schema、protocol、resource 和 ownership validation，再绑定到 aggregate；
- table 已创建后不能替换 plan，也不能因后续全局配置变化而改变行为；
- `clear()` 不改变 plan；`release()` 后只能按 lifecycle contract 读取允许保留的 plan/diagnostic identity。

Schema default 是生成时的稳定输入，runtime default 是协议版本的一部分，application override 是本次 instance 的显式选择。三者不能用未记录的 fallback 混合。

## 3. Validation 与 resource admission

Plan construction/create 必须拒绝：

- 非法、负值、overflow 或彼此矛盾的 capacity/byte/count 限制；
- 缺失或多余的 table/child plan；
- 与 schema/generated/runtime identity 不匹配的 algorithm、estimator 或 protocol；
- 无法覆盖最小合法 storage、required child 或 operation scratch 的预算；
- 会把 bounded resource policy 退化为 silent unbounded growth 的配置。

Mutation/materialization 在执行前使用 effective plan 做资源 preflight。Expected resource rejection 保持旧 stable state；raw JVM fatal allocation error 不包装成可恢复业务失败。

## 4. Identity 与兼容性

Effective plan 必须有 deterministic identity/hash。Hash 输入使用明确的 canonical order、width 和 encoding，不依赖 locale、filesystem、reflection/hash iteration 或 builder 调用顺序。

Schema hash 与 runtime plan hash 表达不同事实：前者标识 schema contract，后者标识 instance execution policy。Plan hash 不证明结果相同、性能相同或支持矩阵通过；它只证明参与 identity 的 effective plan fields 相同。

改变 plan protocol、hash input、default strategy 或 compatibility meaning 时，必须经过 compatibility review，并同步 generated metadata、runtime verification、golden 与 external consumer evidence。

## 5. Stats model

Stats 是 immutable observation，不是业务事实。Snapshot 必须区分：

- current facts：rows、capacity、active view/operation、released state、current retained bytes；
- since-reset counters：operations、scanned、matched、changed、probes、collisions、rehashes 等；
- lifetime/high-water：capacity、storage、scratch、child instance 等不可因 reset 伪造回落的历史高点；
- last-operation detail：只描述最近一次已完成 operation，失败时不得伪造已提交 changed/removed。

Candidate Scan 的 source/cardinality shortcut 可以减少 physical traversal，但 `scanned/matched/changed` 仍按公开 operation 的 logical reference semantics 发布；physical loop、comparison 和 allocation 进入 benchmark evidence，不混入业务统计。Caller-owned Scan plan/handle 不是 Table retained storage，不计入 TableStats；Table-owned `IndexBuffer`、sort/update scratch 仍进入 current/high-water accounting。

`resetStats()` 只重置明确允许重置的观测窗口；不能修改 table rows、capacity、epoch、ownership、plan、lifecycle 或 lifetime high-water。Snapshot 不返回 live mutable counter view。

## 6. 采集模式与副作用

Summary mode 只承担低干扰的核心计数；diagnostic mode 可以增加 probe、collision、memory 和 last-operation detail，但必须显式启用并单独 benchmark。任何模式都不得：

- 在 hot loop 拼接诊断字符串或构造 per-row event；
- 写 stdout/stderr、安装全局 logger、发起网络/文件 I/O；
- 暴露 absolute path、credential、raw handle/bucket/Index 或任意 payload `toString()`；
- 让统计失败改变 table operation 的成功语义。

## 7. Evidence 边界

Plan 和 stats 的长期语义由本 Design 拥有；当前 surface 由代码/public golden拥有；具体计数值和测量结果进入 Report。验证至少覆盖 deterministic plan hash、invalid plan、create mismatch、override precedence、stats current/reset/high-water、summary/diagnostic overhead 和 release 后允许观察的边界。
