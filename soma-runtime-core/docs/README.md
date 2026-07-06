# soma-runtime-core 正式设计文档

本目录保存 `soma-runtime-core` 的正式设计文档。

## 当前正式设计文档

- [Runtime core 契约](runtime-core-contract.md)

## 职责边界

`soma-runtime-core` 拥有 Java columnar runtime kernel、primitive columns、bitmap、sparse set、indexes、order sidecar、lifecycle 和 runtime errors。它不解析 annotation，不生成 Java source，也不拥有用户 schema 语义。

## 临时设计目录

临时设计草案只能放在 `docs/temp/`。草案被接受后，必须把稳定事实迁移进本目录正式设计文档。
