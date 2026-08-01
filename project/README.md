# SOMA Java 项目状态

类型：Project Entry

状态：`READY_FOR_IMPLEMENTATION`；production implementation 尚未开始

Owner：SOMA Java 当前项目事实与文档路由

最后审查日期：2026-08-01

## 当前事实

SOMA Java 已在同一个 repository/product identity 下完成 clean-slate 产品基础治理与
实施准备收口，建立正式 Blueprint、七份分责 Design、Engineering plan、Conformance
和 implementation Gate。当前
active checkout：

- 有正式产品 Blueprint、Design 和 Conformance；
- 没有 production source；
- 没有 production Maven reactor、module 或 artifact；
- 没有 production generated API；
- 没有 production test、benchmark 或 Example；
- 没有 CI、release qualification 或 package workflow；
- 没有可用性、兼容性、性能、支持矩阵或 release readiness 声明；
- 没有 active Temporary 或 parallel current fact Owner。

实施准备审查结论为 `READY_FOR_IMPLEMENTATION`：产品语义、exact Java surface、
two-artifact/full-regeneration architecture、baseline mechanism、量化 performance/security Gate 和
I0-I7 实施计划已经关闭。它不等于 production capability；开始 I0 仍需下一项明确授权。

此前 P2 Java 8 validation spike 已完成使命并退役。成立的 generated type-shape、
cursor/View、typed array、primitive、owner guard 和 full-regeneration 边界已经分别
进入正式 Design；环境、结果、限制和 snapshot fingerprint 进入正式 Conformance。
P2 只解除 selected feasibility risk，不等于 production capability。

## 当前事实入口

| 需要了解的内容 | 唯一入口 |
|---|---|
| 产品意图、用户模型、能力边界与成功标准 | [SOMA Java V1 产品蓝图](blueprint/README.md) |
| 正式 Design Owner、权威关系与实施前技术空白 | [Design 总览](design/README.md) |
| Schema、annotation、generated object 与 full regeneration | [Schema 与编译生成](design/schema-and-generation.md) |
| Group/Table、存储、Field type、Key/Index、关系与 lifecycle | [数据模型与存储](design/data-model-and-storage.md) |
| Generated hierarchy、Table/Field/Stream API 与 metadata | [逻辑层 API](design/logical-api.md) |
| Exact annotation/shared/generated Java 8 signature | [Generated API Signature](design/generated-api-signatures.md) |
| Currentness、admission、mutation、并发与 parallel execution | [执行、并发与并行](design/execution-and-concurrency.md) |
| Result、failure code、mapping、precedence 与状态保证 | [Result 与 Structured Failure](design/results-and-failures.md) |
| Artifact/build/runtime/storage/Index/publish baseline | [Production Implementation Architecture](design/implementation-architecture.md) |
| I0-I7 实施顺序、exit 与 stop rule | [V1 Implementation Plan](engineering/v1-implementation-plan.md) |
| 实施准备审查结论 | [Implementation Readiness Review](conformance/v1-implementation-readiness-review.md) |
| 当前实现差距、P2 evidence 与 production Gate | [Conformance](conformance/README.md) |
| 产品与角色入口 | [根 README](../README.md) |
| 品牌资产与权利 | [Assets](../assets/README.md)、[NOTICE](../NOTICE) |
| 安全报告方式 | [Security Policy](../SECURITY.md) |

## 权威关系

```text
Blueprint
    -> Design
        -> Code / Config / Tests
            -> Conformance
                -> role/scenario projections
```

- Blueprint 拥有产品最终希望成为什么；
- Design 拥有系统长期必须遵守的规范性合同；
- code/config/tests 拥有当前可执行事实；
- Conformance 记录二者是否一致及证据边界；
- README、未来 Manual/White Paper/Examples 是投影，不得成为第二份 Design；
- 新的重大设计变化必须在新的 Temporary topic 中形成 candidate，不能静默覆盖正式
  Owner。

## 文档框架采用

本项目采用《面向角色与场景的项目组织框架》`2.0.0-rc.1`，当前映射：

| Framework role | SOMA Java path/status |
|---|---|
| Product/role entry | `README.md` |
| Project entry | `project/README.md` |
| Blueprint | `project/blueprint/` |
| Design | `project/design/` |
| Engineering | `project/engineering/`；I0-I7 ready/not started |
| Conformance | `project/conformance/` |
| Temporary | `project/temp/`；当前无 active topic |
| Product Docs | 尚未建立；等待 production surface |
| Modules/Implementation Map/Process/Reports | production 尚未出现，不创建假 map/空目录 |

Selected delivery profile 尚未建立；未来 package/source bundle 必须采用明确 allowlist，
不能把整个 checkout 默认交付给 library consumer。

## 固定产品身份

- 品牌：SOMA；
- GitHub Organization：`somaruntime`；
- repository：`somaruntime/soma-java`；
- copyright owner / maintainer / publishing identity：ArthurFeng；
- Java package / Maven group baseline：`io.github.somaruntime.soma`；
- 语言方向：Java 8；
- License：Apache License 2.0。

这些身份事实不预先决定 artifact、version、module、dependency、runtime algorithm 或
release profile。

## Predecessor 边界

重启前的完整项目由 annotated Git tag
`archive/pre-product-reset-2026-07-31` 固定，对应 commit
`b69477432b44c4bc75c5f62fff741a729a33def2`。

该 ref：

- 是历史 provenance 和恢复点；
- 包含旧设计、代码、测试、Example、benchmark 和报告；
- 不是 release tag 或 qualification evidence；
- 不是当前 API/implementation Owner；
- 不能被 copy、cherry-pick、wrapper 或 compatibility layer 方式恢复为新产品。

## 下一阶段

```text
Formal Blueprint/Design baseline（已完成）
    -> implementation readiness（已完成）
        -> production implementation authorization
            -> I0 build spine
                -> I1-I6 compiler/runtime slices and Gate
                    -> I7 reference scenario/performance/security/package qualification
```

下一阶段不是继续补写 Blueprint、恢复 predecessor reactor，或一次性铺开所有 module；
收到明确授权后只从 I0 开始。Exact signature、diagnostic、module topology、handshake、
storage/Index/publish/scheduler baseline 已由正式 Design 固定。若 evidence 迫使改变产品
语义，必须停止 slice、建立新的 Temporary，并由 Product Owner 裁决。

## 当前验证边界

Repository/documentation Gate：

- formal Blueprint/Design/Conformance 相对链接有效；
- 每项长期事实只有一个 Owner；
- active checkout 没有 predecessor code、legacy surface 或 release claim；
- root/project/agent entry 与正式 Owner 一致；
- predecessor tag 解析到精确 commit；
- Git diff 无 whitespace error；
- no active Temporary、build artifact 或失效 current route。

Production compile/runtime/performance/release Gate 只有在相应 surface 出现后才能执行，
完整最低证据集合见
[V1 Implementation Conformance Gates](conformance/v1-implementation-gates.md)。
