# soma-runtime-core 正式设计文档

本目录保存 annotation-agnostic Java runtime kernel 的正式设计事实。

## 正式设计文档

| 文档 | Owner | 单一职责 |
|---|---|---|
| [TableStore 契约](table-store-contract.md) | `soma-runtime-core` | storage components、KeySpace、AccessStructures、AccessPath 和 Batch |
| [Runtime lifecycle 契约](runtime-lifecycle-contract.md) | `soma-runtime-core` | ownership、mutation、epoch/view、errors、concurrency 和 release |
| [Runtime 性能实现契约](runtime-performance-implementation-contract.md) | `soma-runtime-core` | packed/primitive/fused/allocation-bounded kernel discipline |

Runtime-core 不解析 annotation、不生成 Java source，也不拥有 public Schema/API semantics。

临时专题进入 `docs/temp/`，不得成为正式事实源。
