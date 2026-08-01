# SOMA Java 项目状态

类型：Project Entry

状态：正式 V1 Design baseline 已建立；production implementation 尚未开始

Owner：SOMA Java 当前项目事实与文档路由

最后审查日期：2026-08-01

## 当前事实

SOMA Java 已在同一个 repository/product identity 下完成 clean-slate 产品基础治理，
建立正式 Blueprint、五份分责 Design、Conformance 和 implementation Gate。当前
active checkout：

- 有正式产品 Blueprint、Design 和 Conformance；
- 没有 production source；
- 没有 production Maven reactor、module 或 artifact；
- 没有 production generated API；
- 没有 production test、benchmark 或 Example；
- 没有 CI、release qualification 或 package workflow；
- 没有可用性、兼容性、性能、支持矩阵或 release readiness 声明；
- 没有 active Temporary 或 parallel current fact Owner。

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
| Currentness、admission、mutation、并发与 parallel execution | [执行、并发与并行](design/execution-and-concurrency.md) |
| Result、failure code、mapping、precedence 与状态保证 | [Result 与 Structured Failure](design/results-and-failures.md) |
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
| Conformance | `project/conformance/` |
| Temporary | `project/temp/`；当前无 active topic |
| Product Docs | 尚未建立；等待 production surface |
| Modules/Implementation Map/Process/Reports | 尚无独立 capability，不创建空目录 |

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
    -> capability/module surface admission
        -> production compiler/runtime implementation authorization
            -> compile/consumer/negative/runtime correctness Gate
                -> reference scenario correctness/performance profile
                    -> packaging/release process and qualification
```

下一阶段不是恢复 predecessor reactor，也不是一次性建立所有 module。每个新 surface
必须说明 capability/consumer、Owner、lifecycle、failure boundary、现有 surface 为何
不足，以及成立所需 evidence。

Metadata exact Java carrier、完整 compiler diagnostic、production module topology 和
build handshake carrier 等技术空白已在 Design/Conformance 中明确拥有；它们不得由
实现静默决定。如果 implementation proposal 需要改变产品语义，必须建立新的
Temporary，并由 Product Owner 裁决后再推进。

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
