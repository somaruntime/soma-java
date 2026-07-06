# 文档治理规则

状态：正式设计文档
日期：2026-07-06
Owner：根项目协调层

## 1. 目标

本文定义 `soma_java` 的正式设计文档、模块文档、README、reports 和临时草案的职责边界。

本规则用于避免三类问题：

- 根文档与模块文档重复定义同一事实；
- 示例、报告或 README 成为事实源；
- 临时草案、benchmark smoke 或本机执行结果被误写成 release claim。

## 2. 文档层级

| 层级 | 位置 | 责任 |
|---|---|---|
| 根级正式设计 | `docs/` | 项目边界、跨模块架构、跨模块 API 契约、验证门禁、文档治理 |
| 模块正式设计 | `<module>/docs/` | 模块拥有的契约、内部设计边界、模块级非目标 |
| README | `README.md` / `<module>/README.md` | 入口、导航、当前状态摘要 |
| reports | `reports/` / `<module>/reports/` | 验证报告、审查报告、benchmark 报告、release evidence |
| 临时草案 | `docs/temp/` / `<module>/docs/temp/` | 尚未接受的设计草案、迁移草稿、治理工作稿 |

README 不应定义新的产品事实。README 中需要出现事实时，应链接到正式设计文档。

Reports 是证据，不是设计事实源。报告可以引用正式设计文档并记录某次验证结果，但不能单独改变契约。

## 3. 事实源归属

V1 当前事实源归属：

| 事实类型 | 正式位置 |
|---|---|
| 项目边界、模块结构、依赖方向、release claim 边界 | `docs/architecture.md` |
| 跨模块领域术语、public/runtime/internal 词汇边界 | `docs/domain-glossary.md` |
| Row Pipeline / generated Soma API 用户模型 | `docs/row-pipeline-api-contract.md` |
| V1 gate、evidence、package smoke、benchmark smoke 边界 | `docs/validation-gates.md` |
| Java annotation schema、annotation 语义、类型系统、schema hash | `soma-annotations/docs/annotation-schema-contract.md` |
| annotation processing、validation、normalized model、codegen | `soma-processor/docs/processor-codegen-contract.md` |
| Java columnar runtime kernel、`TableStore` 组合模型、lifecycle、runtime errors | `soma-runtime-core/docs/runtime-core-contract.md` |
| examples 场景和 E2E smoke 边界 | `soma-examples/docs/` |
| testkit helper 契约 | 待设计 |
| benchmark runner / evidence schema | 待设计 |

同一事实只能有一个 owner。如果多个文档需要提到同一事实，应由非 owner 文档引用 owner 文档，不要复制完整定义。

## 4. 受保护基线

当前已定基线：

- Java annotation schema 以 `soma-annotations/docs/annotation-schema-contract.md` 为准；
- Row Pipeline / Soma API 用户模型以 `docs/row-pipeline-api-contract.md` 为准；
- Java-only V1 不承诺 Python、C ABI、native runtime 或跨语言 FFI；
- runtime hot path 不以 DTO object graph、reflection 或 `List<DTO>` / `Map<Key, DTO>` 为基础；
- V1 table 只分为 keyed table 和 dense table。

修改受保护基线前，必须先明确：

- 修改的是事实源文档还是引用文档；
- 是否影响 generated API、runtime behavior、validation gate 或 release claim；
- 是否需要用户确认。

## 5. 临时草案规则

临时设计草案只能放在 `docs/temp/` 或 `<module>/docs/temp/`。

草案被接受后，必须把稳定事实迁移进正式设计文档。正式 release claim 不得引用 `docs/temp/`。

如果临时草案包含未决设计，不得在迁移时补写答案；应记录为待决策缺口并交给对应 owner。

## 6. 报告规则

正式报告放在 `reports/` 或 `<module>/reports/`。

报告必须说明：

- 对应 gate、模块或治理目标；
- 输入文档、输出文档和变更范围；
- 审查命令或人工审查步骤；
- 通过项、修复项、遗留缺口；
- release claim 是否允许引用该报告。

Benchmark smoke 只能证明工具链和场景可运行。任何性能优势声明都必须有 baseline、规模、环境、重复次数、统计口径和可复现命令。

## 7. 命名与抽象原则

核心抽象必须先获得清晰、正向、边界明确的名称，再进入正式实现或契约文档。命名不是表面修饰，而是抽象是否成立的审查手段。

如果一个核心抽象只能通过“非 X”“无 X”“类似 X 但不是 X”来解释，或者需要用某个内部数据结构代表整个对象，应先重新审查抽象边界，不应急于落地实现。

命名审查至少检查：

- 名称是否说明该抽象拥有的职责；
- 名称是否避免把实现细节误提升为产品概念；
- 名称是否与 public/generated API、schema annotation 和 runtime internal 术语边界一致；
- 名称是否能自然区分 logical identity、physical slot、traversal order 和 access sidecar；
- 名称是否能承载 V1 已定能力，而不是暗示缩水版实现。
