# soma-processor 正式设计文档

本目录保存 `soma-processor` 的 compile-time processing 与 code generation 事实。

## 正式设计文档

| 文档 | Owner | 单一职责 |
|---|---|---|
| [Schema processing 契约](schema-processing-contract.md) | `soma-processor` | validation、normalized schema、exact hash、compatibility 和 diagnostics |
| [Code generation 契约](code-generation-contract.md) | `soma-processor` | generated artifacts、static binding、deterministic output、golden/package smoke |

Public annotation semantics 属于 [soma-annotations](../../soma-annotations/docs/README.md)；Generated Table 用户语义属于根级 [API 契约](../../docs/generated-table-api-contract.md)。

临时专题进入 `docs/temp/`，不得成为正式事实源。
