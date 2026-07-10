# soma-testkit 正式设计文档

本目录保存可重复 compile/golden/runtime assertion helper 的正式设计事实。

## 正式设计文档

| 文档 | Owner | 单一职责 |
|---|---|---|
| [soma-testkit 契约](testkit-contract.md) | `soma-testkit` | compile/golden/invariant/materialization/performance-shape helper 语义 |

Concrete Java package、helper method、fixture/golden layout 和 structured serialization 在实现阶段固化，但不得削弱本契约。

临时专题进入 `docs/temp/`，不得成为正式事实源。
