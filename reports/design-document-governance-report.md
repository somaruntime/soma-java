# 设计文档治理报告

状态：正式治理报告
日期：2026-07-06
Owner：根项目协调层

## 1. 治理目标

本次治理目标是把 `soma_java` 的根级设计文档、模块级设计文档、README、reports 和临时目录整理成一套正式、固定、可审计的文档体系。

本次治理明确保护两条已定基线：

- Java annotation schema 契约；
- Row Pipeline / Soma API 契约。

治理过程中没有改写 annotation schema 的核心语义，也没有重新设计 Row Pipeline / Soma API。涉及 generated API 术语漂移的地方，按已确认决策统一到 `findFirst()` / `firstOrThrow()`。

## 2. 输入范围

本次纳入治理范围：

- 根 `README.md`；
- 根 `AGENTS.md`；
- 根 `docs/` 下正式设计文档；
- 各模块 `docs/` 下正式设计文档和 README；
- 各模块 `README.md`；
- 根 `reports/`；
- 各模块 `reports/`；
- `docs/temp/` 和各模块 `docs/temp/` 的当前状态。

本次未修改 Java 源码、Maven 配置、构建脚本或 git 分支策略。

## 3. 文档归位

本次将 owner 明确的正式契约移动到对应模块：

| 原路径 | 新路径 | Owner |
|---|---|---|
| `docs/annotation-schema-contract.md` | `soma-annotations/docs/annotation-schema-contract.md` | `soma-annotations` / `soma-processor` |
| `docs/codegen-contract.md` | `soma-processor/docs/processor-codegen-contract.md` | `soma-processor` |
| `docs/java-runtime-core-contract.md` | `soma-runtime-core/docs/runtime-core-contract.md` | `soma-runtime-core` |

根 `docs/` 保留跨模块事实：

- `docs/architecture.md`；
- `docs/row-pipeline-api-contract.md`；
- `docs/validation-gates.md`；
- `docs/documentation-governance.md`；
- `docs/README.md`。

## 4. 索引和入口治理

已更新：

- `docs/README.md`：改为根级正式设计文档索引，并链接各模块文档入口；
- `soma-annotations/docs/README.md`：列出 annotation schema 契约和职责边界；
- `soma-processor/docs/README.md`：列出 processor / codegen 契约和职责边界；
- `soma-runtime-core/docs/README.md`：列出 runtime core 契约和职责边界；
- `soma-examples/docs/README.md`：明确 examples 只拥有示例和 E2E smoke，不拥有 annotation/API/runtime 契约；
- `soma-testkit/docs/README.md`：记录 testkit 待决策设计缺口；
- `soma-benchmarks/docs/README.md`：记录 benchmarks 待决策设计缺口；
- `README.md`：改为项目入口和正式文档导航，不承载新的契约事实；
- `reports/README.md`：增加本治理报告入口。

## 5. 新增治理规则

新增 `docs/documentation-governance.md`，明确：

- 根级正式设计、模块正式设计、README、reports、临时草案的职责；
- 各类事实源的 owner；
- README 不作为事实源；
- reports 是证据，不是设计事实源；
- 临时草案只能放在 `docs/temp/` 或 `<module>/docs/temp/`；
- 草案被接受后必须迁移稳定事实，不能直接作为 release claim；
- benchmark smoke 不能写成性能优势声明。

## 6. 术语和基线同步

已同步以下漂移：

- `AGENTS.md` 中 generated pipeline 从 `Table / Batch / Record / View / ColumnView` 改为 `Table / Batch / Row Pipeline / DTO materialization / ColumnView`；
- `AGENTS.md` 中文档事实源规则扩展为根 `docs/` 和模块 `<module>/docs/`；
- `docs/architecture.md` 中 DTO materialization 入口从 `fetchFirst()` 同步为 `findFirst()` / `firstOrThrow()`；
- `soma-processor/docs/processor-codegen-contract.md` 中 common API 和 materialization 说明同步为 `findFirst()` / `firstOrThrow()`；
- `soma-runtime-core/docs/runtime-core-contract.md` 中 benchmark smoke 场景同步为 ordered `findFirst` / `firstOrThrow`；
- `docs/validation-gates.md` 中 benchmark smoke 场景同步为 ordered `findFirst` / `firstOrThrow`；
- `soma-examples/docs/fjsp-e2e-scenario.md` 中 `lazy generated view`、`lazy view take / fetchFirst` 改为 Row Pipeline lazy terminal 和 `findFirst()` / `firstOrThrow()`；
- `soma-examples/docs/runtime-state-schema-examples.md` 中“对 annotation contract 的反向约束”改为“对正式契约的覆盖说明”，避免 examples 层反向定义 annotation contract。

## 7. 当前待决策缺口

本次治理只记录缺口，不补写设计。

### 7.1 soma-testkit

`soma-testkit` 尚缺正式契约：

- compile test helper 的输入、输出、诊断断言和 Java 8 toolchain 约束；
- golden output helper 的比较口径、文件组织和更新流程；
- runtime invariant helper 与 runtime core 单元测试的分工。

该缺口影响 G1/G2/G3 的可审计性。

### 7.2 soma-benchmarks

`soma-benchmarks` 尚缺正式契约：

- benchmark runner 输入参数和输出 JSONL schema；
- benchmark smoke 场景、规模、环境字段、失败语义；
- Row Pipeline、ColumnView / primitive loop、DTO materialization / Java Stream 三类路径的证据口径。

该缺口影响 G5 benchmark smoke 和未来性能声明。

## 8. 审查结论

本次治理后的事实源边界为：

- 根 `docs/` 负责项目级和跨模块事实；
- 模块 `<module>/docs/` 负责模块 owner 事实；
- examples 文档只负责示例和 E2E smoke；
- reports 只负责证据；
- temp 目录只负责草案。

本次治理没有降低 V1 目标，也没有把未决实现方案写成正式设计事实。secondary index / unique、ColumnView、Row Pipeline、runtime stats、package smoke、benchmark smoke 等 V1 gate 项仍保留在正式门禁中。

## 9. 后续建议

建议下一轮设计治理专题按顺序处理：

1. `soma-testkit` 契约；
2. `soma-benchmarks` evidence schema；
3. release runbook / G0-G6 报告模板；
4. implementation-layer design document。

在前三项完成前，不建议开始把实现方案写成正式 runtime/codegen 细节。

## 10. 审查与验证记录

本次治理完成后执行了以下审查：

| 审查项 | 命令 / 方法 | 结果 |
|---|---|---|
| Markdown diff whitespace | `git diff --check` | 通过，无输出 |
| 旧 API / 旧术语扫描 | `rg -n "fetchFirst\\(\\)|lazy view|Record / View|反向约束" AGENTS.md README.md docs soma-* reports -g '*.md'` | 仅治理报告历史记录和 Row Pipeline 契约中“禁止再引入 fetchFirst”的规则说明命中 |
| Markdown 链接存在性 | 本地脚本检查所有 Markdown 相对链接目标 | 通过，所有 Markdown 链接目标存在 |
| 正式文档结构 | `find docs soma-*/docs -maxdepth 2 -type f | sort` | 根级和模块级正式文档归位符合本报告第 3 节 |
| temp 目录状态 | `find . -path '*/docs/temp/*' -print -o -path '*/reports/temp/*' -print | sort` | 仅 `.gitkeep` 文件 |

本次没有运行 Maven 编译或测试，因为变更范围仅为 Markdown 文档、索引、治理报告和 `AGENTS.md` 指南，没有修改 Java 源码、POM 或构建脚本。
