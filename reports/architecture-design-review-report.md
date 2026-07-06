# 架构设计审核报告

状态：正式审查报告
日期：2026-07-06
Owner：根项目协调层

## 1. 审核目标

本次审核目标是对 `docs/architecture-design.md` 做正式架构审核，确认它能作为 `soma_java` 项目级架构设计的唯一事实源，并服务 Java-only SOMA V1 的项目目标。

审核重点：

- 是否完整覆盖项目级三明治结构；
- 是否清晰拆解 annotation、processor、generated API、runtime 和 evidence；
- 是否保持 owner 边界和依赖方向；
- 是否避免 V1 目标缩水；
- 是否为后续 correctness model、performance model 和实现阶段提供足够架构基础；
- 是否避免与 annotation/API/runtime owner 契约冲突。

## 2. 输入文档

本次审核读取并对照以下文档：

- `docs/architecture-design.md`；
- `docs/implementation-strategy.md`；
- `docs/domain-glossary.md`；
- `docs/documentation-governance.md`；
- `docs/row-pipeline-api-contract.md`；
- `docs/validation-gates.md`；
- `soma-annotations/docs/annotation-schema-contract.md`；
- `soma-processor/docs/processor-codegen-contract.md`；
- `soma-runtime-core/docs/runtime-core-contract.md`；
- `reports/design-document-governance-report.md`。

## 3. 审核维度

| 维度 | 审核问题 | 结论 |
|---|---|---|
| 唯一事实源 | 是否删除旧架构文件，并把项目级架构事实迁移到 `architecture-design.md` | 通过 |
| 项目覆盖面 | 是否覆盖整个项目，而不是只覆盖 runtime internal | 通过 |
| 三明治结构 | annotation 声明面、generated API 用户面、中间实现层是否被清晰表达 | 通过 |
| 架构单元拆解 | 是否把复杂实现层拆成可审查的小单元 | 通过 |
| Owner 边界 | root、module docs、reports、examples 是否边界清楚 | 通过，已做措辞修正 |
| 依赖方向 | runtime-core 是否避免依赖 processor / annotation element | 通过 |
| Public/internal 边界 | generated API 是否避免暴露 sidecar、bitmap word、row pointer 等 internal | 通过 |
| V1 不缩水 | key/index/unique/order、Row Pipeline、ColumnView、DTO、typed errors、schema hash、gate evidence 是否保留 | 通过 |
| 正确性基础 | 是否定义 correctness 信心来源和后续 correctness model 入口 | 通过；后续已补 `docs/runtime-correctness-model.md` |
| 性能基础 | 是否定义 hot path / allocation / benchmark claim 边界和后续 performance model 入口 | 通过；后续已补 `docs/runtime-performance-model.md` |
| Evidence 架构 | G0-G6、reports、smoke、release claim 边界是否闭环 | 通过 |

## 4. Findings

### F1: 已修正 - owner 文档措辞不够精确

严重度：低

`architecture-design.md` 初稿将 implementation strategy、validation gates、domain glossary 与模块 owner 契约统一描述为“具体模块契约”，容易让根级下游设计文档和模块契约的层级关系变得不够精确。

修正：

- 将措辞改为“具体下游设计与模块契约仍由对应 owner 文档定义”；
- 保持 `architecture-design.md` 为项目级架构唯一事实源；
- 保持 `implementation-strategy.md` 作为下游实现路线文档。

### F2: 已修正 - implementation strategy 冲突处理措辞不够中文化

严重度：低

`implementation-strategy.md` 中“architecture 或 owner 文档”表述不够清晰。

修正：

- 改为“项目级架构或 owner 文档”；
- 明确实现方案不能绕过架构事实源或模块 owner 契约。

### F3: 已收口 - correctness / performance 后续设计已补齐

严重度：中

`architecture-design.md` 已经建立项目级架构分解，但它不是 runtime correctness model，也不是 runtime performance model。中间实现层的正确性和性能信心仍需要后续文档单独固化。

处理：

- 已新增 `docs/runtime-correctness-model.md`，定义 runtime invariants、状态机、typed errors、differential oracle 和 gate mapping；
- 已新增 `docs/runtime-performance-model.md`，定义 hot path、复杂度、allocation、sidecar rebuild、benchmark evidence level 和 claim 边界；
- `architecture-design.md` 已从“待补”更新为引用这两份正式下游设计文档。

### F4: 已记录 - testkit / benchmark owner 契约仍待补

严重度：中

`soma-testkit` 和 `soma-benchmarks` 的正式契约仍未设计完成，会影响 G1-G5 evidence 的可审计性。

处理：

- 已在 `documentation-governance.md` 和 `architecture-design.md` 中保留为待补 owner 设计；
- 不在本次架构文档中编造 testkit 或 benchmark 细节。

## 5. 架构判断

审核后的判断：

- `architecture-design.md` 可以作为项目级架构设计唯一事实源；
- 它已经覆盖整个 `soma_java` 项目，不局限于 SOMA runtime internal；
- 它把复杂问题拆成了 schema、normalization、codegen、generated API、runtime store、execution、boundary、evidence 等可审查单元；
- 它没有降低 V1 目标，没有把 dense table 写成缩水版 table，也没有把 primary key lookup 混同为普通 secondary index；
- 它没有用 implementation strategy 覆盖 annotation/API/runtime owner 契约；
- 它明确承认 testkit、benchmark 仍需后续 owner 设计；correctness 和 performance 已由正式下游设计文档补齐。

## 6. 遗留工作

建议后续按顺序补齐：

1. `soma-testkit/docs/testkit-contract.md` 或等价正式契约；
2. `soma-benchmarks/docs/benchmark-evidence-contract.md` 或等价正式契约；
3. G0 scope freeze report。

## 7. 验证记录

本次审核执行了以下验证：

| 检查项 | 命令 / 方法 | 结果 |
|---|---|---|
| 旧架构路径扫描 | `rg -n "architecture[.]md" .` | 无命中 |
| 新架构事实源存在 | `test -f docs/architecture-design.md` | 通过 |
| 旧架构文件删除 | 显式检查旧架构文件不存在 | 通过 |
| 未完成标记扫描 | 扫描常见未完成标记关键词 | 无命中 |
| Markdown diff whitespace | `git diff --check` | 通过，无输出 |
| 根级文档链接目标 | 显式检查 `docs/README.md` 中正式文档目标 | 全部存在 |

本次没有运行 Maven 编译或测试，因为变更范围仅为 Markdown 架构设计、文档索引和审核报告，没有修改 Java 源码、POM 或构建脚本。
