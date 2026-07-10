# soma_java 正式设计文档索引

本目录是 Java-only SOMA 根级正式设计事实入口。模块内部事实进入对应模块的 `docs/`；README、AGENTS、guides、reports 和 `docs/temp/` 都不是设计事实源。

## 根级正式设计文档

| 文档 | 唯一 Owner | 单一职责 |
|---|---|---|
| [文档治理规则](documentation-governance.md) | 根项目协调层 | 文档分类、Owner、生命周期和质量门禁 |
| [领域术语表](domain-glossary.md) | 根项目协调层 | canonical 术语及“不等同于”边界 |
| [SomaTable 设计宪法](soma-table-design-constitution.md) | 根项目协调层 | 总心智模型和跨模块永久原则 |
| [项目架构设计](architecture-design.md) | 根项目协调层 | 系统边界、模块、依赖方向和数据流 |
| [Build 与依赖契约](build-and-dependency-contract.md) | 根项目协调层 | Maven reactor、模块依赖、consumer build、dependency 与 CI baseline |
| [Public API 与兼容性契约](public-api-compatibility-contract.md) | 根项目协调层 | public/generated/internal surface、compatibility identity 和 migration |
| [Generated Table API 契约](generated-table-api-contract.md) | 根项目协调层 | Direct API、Row/Key/Column Pipeline、Mutator、ColumnView 用户语义 |
| [Materialization 契约](materialization-contract.md) | 根项目协调层 | detached object、递归 child、collection shape 和 budget |
| [Runtime 正确性模型](runtime-correctness-model.md) | 根项目协调层 | runtime 不变量、状态机、失败原子性和 oracle |
| [Runtime 性能模型](runtime-performance-model.md) | 根项目协调层 | access pattern、复杂度、allocation 和 claim 边界 |
| [Security model](security-model.md) | 根项目协调层 | trust boundary、integrity、resource abuse、diagnostic exposure 和 supply chain |
| [实现策略](implementation-strategy.md) | 根项目协调层 | 实现架构、垂直切片顺序和防缩水出口 |
| [V1 验证门禁](validation-gates.md) | 根项目协调层 | readiness gate、evidence 和 release claim |
| [Versioning 与 release 契约](versioning-and-release-contract.md) | 根项目协调层 | artifact version、publishing readiness、rollback 和 release evidence |

## 模块正式设计入口

| 模块 | 正式设计入口 | 事实范围 |
|---|---|---|
| `soma-annotations` | [docs](../soma-annotations/docs/README.md) | public schema annotation |
| `soma-processor` | [docs](../soma-processor/docs/README.md) | compiler integration、processing、normalization、hash、diagnostics、code generation |
| `soma-runtime-core` | [docs](../soma-runtime-core/docs/README.md) | TableStore、lifecycle、runtime plan、errors/diagnostics、performance implementation |
| `soma-testkit` | [docs](../soma-testkit/docs/README.md) | compile/golden/invariant/evidence helper contract |
| `soma-examples` | [docs](../soma-examples/docs/README.md) | formal usage scenarios and Access Pattern Cards |
| `soma-benchmarks` | [docs](../soma-benchmarks/docs/README.md) | evidence methodology and runtime-state benchmark lanes |

## 非正式设计入口

- 长期研究蓝图与临时专题位于 [docs/temp](temp/)；
- 正式审查、验证和 release evidence 位于 [reports](../reports/README.md)；
- 用户和开发者指南未来按需进入 `guides/`，不进入 `reports/`。

正式实现、gate 或 release claim 不得把 README、guides、reports 或临时蓝图当作设计事实源。
