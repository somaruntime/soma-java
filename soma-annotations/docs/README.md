# soma-annotations 正式设计文档

本目录保存 `soma-annotations` 的正式设计文档。

## 当前正式设计文档

- [Java annotation schema 契约](annotation-schema-contract.md)

## 职责边界

`soma-annotations` 只拥有用户可见 schema annotation API。annotation 语义、类型系统、normalized schema model 和 schema hash 口径以本目录契约为准；runtime storage、Row Pipeline 执行和 benchmark 证据不在本模块定义。

## 临时设计目录

临时设计草案只能放在 `docs/temp/`。草案被接受后，必须把稳定事实迁移进本目录正式设计文档。
