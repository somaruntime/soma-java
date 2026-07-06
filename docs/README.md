# soma_java 正式设计文档索引

本目录保存 Java-only SOMA 原型的根级正式设计文档。根级文档负责项目边界、跨模块架构、跨模块 API 契约、验证门禁和文档治理；模块内部事实应进入对应模块的 `docs/`。

## 当前正式设计文档

- [架构设计](architecture.md)
- [领域术语表](domain-glossary.md)
- [SOMA 实现方案设计](implementation-strategy.md)
- [Row Pipeline API 契约](row-pipeline-api-contract.md)
- [V1 验证门禁](validation-gates.md)
- [文档治理规则](documentation-governance.md)

## 模块文档入口

- [soma-annotations 文档](../soma-annotations/docs/README.md)
- [soma-processor 文档](../soma-processor/docs/README.md)
- [soma-runtime-core 文档](../soma-runtime-core/docs/README.md)
- [soma-testkit 文档](../soma-testkit/docs/README.md)
- [soma-examples 文档](../soma-examples/docs/README.md)
- [soma-benchmarks 文档](../soma-benchmarks/docs/README.md)

## 临时设计目录

临时设计草案只能放在 `docs/temp/`。草案被接受后，必须把稳定事实迁移进正式设计文档。正式 release claim 不得引用 `docs/temp/`。
