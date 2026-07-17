# soma-annotations 正式设计文档

本目录只保存 `soma-annotations` 拥有的 public schema annotation 事实。

## 正式设计文档

| 文档 | Owner | 单一职责 |
|---|---|---|
| [Java annotation schema 契约](annotation-schema-contract.md) | `soma-annotations` | annotation、类型系统、field role、optional/default、key/index/unique和child declaration |

Processing、normalized schema、hash、diagnostics 和 code generation 属于 [soma-processor](../../soma-processor/docs/README.md)。

临时专题进入 `docs/temp/`，不得成为正式事实源。
