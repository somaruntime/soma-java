# SOMA Java 项目状态

类型：Project Entry

状态：产品重新定义中

Owner：SOMA Java 当前项目事实与文档路由

最后审查日期：2026-07-31

## 当前事实

SOMA Java 已在同一个仓库和产品身份下建立 clean-slate 起点。当前 active checkout：

- 没有 production source；
- 没有 Maven reactor 或 artifact；
- 没有 generated API；
- 没有 test、benchmark 或 example；
- 没有 CI、release qualification 或 package workflow；
- 没有正式 Blueprint、Design、Conformance、Report 或 Product Docs；
- 不声明可用性、兼容性、性能、支持矩阵或 release readiness。

这些缺失是有意的。项目先闭合产品语义，再让模块和实现从已批准的 capability
自然产生。

## 当前事实入口

| 需要了解的内容 | 唯一入口 |
|---|---|
| 候选产品模型、确认方向和未决问题 | [产品基础决策](temp/soma-product-foundation/README.md) |
| 候选 generated API 与用户示例 | [逻辑层 API 草稿](temp/soma-product-foundation/logical-api-draft.md) |
| 产品介绍 | [根 README](../README.md) |
| 品牌资产与权利 | [Assets](../assets/README.md)、[NOTICE](../NOTICE) |
| 安全报告方式 | [Security Policy](../SECURITY.md) |

产品基础决策是当前 Temporary 中的候选事实 Owner。逻辑层草稿只能投影这些决策，
不能独立增加产品语义。

## 固定产品身份

- 品牌：SOMA；
- GitHub Organization：`somaruntime`；
- repository：`somaruntime/soma-java`；
- owner / maintainer：ArthurFeng；
- Java package / Maven group 基线：`io.github.somaruntime.soma`；
- 语言方向：Java 8；
- License：Apache License 2.0。

上述身份不等于 artifact、版本、module、annotation 或 release profile 已经决定。

## Predecessor 边界

重启前的完整项目由 annotated Git tag
`archive/pre-product-reset-2026-07-31` 固定，对应 commit
`b69477432b44c4bc75c5f62fff741a729a33def2`。

该 ref：

- 是历史 provenance 和恢复点；
- 包含旧设计、代码、测试、example、benchmark、报告和两份完整讨论稿；
- 不是 release tag；
- 不是当前支持或 qualification 证据；
- 不能作为新产品 API 或实现 Owner。

active checkout 不建立 legacy 目录、历史文档副本或 compatibility surface。

## 后续治理顺序

```text
产品基础 Temporary
    -> 未决问题逐项裁决
        -> 独立一致性审查
            -> 按职责建立正式 Blueprint / Design
                -> 最小 generated Java 8 consumer 验证
                    -> capability-driven module 与实现
                        -> correctness / performance / release evidence
                            -> 删除 Temporary
```

在正式 Blueprint/Design 建立前，不应创建 production module、公开 API 或大规模
实现。设计成立后也不得一次性恢复旧项目结构；每个 surface 必须由独立 capability、
consumer、lifecycle、failure boundary 和 evidence 证明。

## 当前验证边界

当前只需要验证：

- active checkout surface 与本页声明一致；
- Markdown 相对链接有效；
- 品牌和法律资产完整；
- predecessor tag 可解析到精确 commit；
- Git diff 无 whitespace error；
- 没有旧 release、version、module 或 capability claim 泄漏到当前入口。

代码、编译、consumer、性能和 release Gate 将在相应 surface 真正出现时建立。
