# soma-processor 正式设计文档

本目录保存 `soma-processor` 的正式设计文档。

## 当前正式设计文档

- [Processor / codegen 契约](processor-codegen-contract.md)

## 职责边界

`soma-processor` 拥有 annotation processing、validation、normalized schema model、schema hash 和 codegen。它可以依赖 `soma-annotations`，但 generated public API 不得暴露 processor internal model。

## 临时设计目录

临时设计草案只能放在 `docs/temp/`。草案被接受后，必须把稳定事实迁移进本目录正式设计文档。
