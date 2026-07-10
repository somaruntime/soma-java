# 文档治理规则

状态：正式设计文档
Owner：根项目协调层
事实范围：文档分类、权威顺序、Owner、命名、生命周期、质量门禁
非事实范围：SOMA 产品语义、API、runtime 实现和验证结果
最后审查日期：2026-07-10

## 1. 目标

本文定义 `soma_java` 文档体系本身的规则。它保证设计事实可定位、可审查、可演进，并防止 README、示例、报告或临时草案成为平行事实源。

核心约束：

> 一项设计事实只有一个 Owner；一份正式设计文档只有一个职责和一个 Owner。

## 2. 文档分类与权威顺序

| 类型 | 位置 | 职责 | 是否拥有设计事实 |
|---|---|---|---:|
| 根级正式设计 | `docs/*.md` | 跨模块原则、架构、公共契约、模型和门禁 | 是 |
| 模块正式设计 | `<module>/docs/*.md` | 模块拥有的契约和内部边界 | 是 |
| 长期指南 | `guides/` / `<module>/guides/` | 用户、开发者、贡献者的当前使用说明 | 否 |
| README | 根目录、模块、docs/reports/guides 入口 | 状态摘要和导航 | 否 |
| AGENTS | `AGENTS.md` | Agent 工作护栏和必读入口 | 否 |
| 正式报告 | `reports/` / `<module>/reports/` | 审查、验证、benchmark、release evidence | 否 |
| 临时设计 | `docs/temp/` / `<module>/docs/temp/` | 未接受的专题草案和研究蓝图 | 否 |
| 临时报告 | `reports/temp/` / `<module>/reports/temp/` | 尚未完成的报告工作稿 | 否 |

发生冲突时按以下顺序处理：

1. 找到该事实在 [正式设计文档索引](README.md) 中登记的唯一 Owner；
2. 以 Owner 文档为准；
3. 修改非 Owner 文档的摘要、示例、指南或报告；
4. 如果两个正式文档都声称拥有同一事实，必须先拆分 Owner，不能用“双 Owner”维持冲突。

## 3. 单一 Owner 和引用规则

正式文档的 `Owner` 必须是一个根级责任域或一个模块，不允许使用 `A / B`、`A 与 B` 等联合 Owner。

跨模块能力由根级契约拥有语义，模块契约分别拥有本模块的实现义务。例如：

- Generated Table API 的用户语义由根级 API 契约拥有；
- processor 只拥有如何生成该 API；
- runtime-core 只拥有如何执行并维护它。

非 Owner 文档可以采用：

```text
owner 链接
+ 一句话语义摘要
+ 本模块的局部后果
```

非 Owner 文档不得复制 owner 的完整规则、错误表、状态机、默认值或代码示例。跨文档重复超过一个短段落时，应当改为链接。

## 4. 正式文档职责与元数据

每份正式设计文档必须在 H1 后声明：

```text
状态：正式设计文档
Owner：唯一 Owner
事实范围：本文完整拥有的事实
非事实范围：明确排除的相邻事实
最后审查日期：YYYY-MM-DD
```

正式文档应回答一个主问题，并采用以下最小组织：

1. 定位与边界；
2. canonical 模型或契约；
3. 不变量、生命周期或失败语义；
4. 与相邻 Owner 的关系；
5. 非目标。

行数只是职责膨胀的信号：

- 超过 350 行必须复审是否混入第二职责；
- 超过 500 行原则上必须拆分；
- exhaustive public annotation/reference contract 可以例外，但必须保持单一职责；
- 长代码示例应进入 `soma-examples`，owner contract 只保留最小 API shape。

## 5. 命名规则

文件名统一使用 lowercase kebab-case，并以职责后缀表达文档类型：

| 后缀 | 含义 |
|---|---|
| `*-constitution.md` | 不随局部实现变化的跨模块原则 |
| `*-architecture.md` / `architecture-design.md` | 系统结构、模块和依赖方向 |
| `*-contract.md` | normative API、schema、runtime 或 evidence 契约 |
| `*-model.md` | 正确性、性能等分析模型 |
| `*-strategy.md` | 已接受的实施路线和阶段出口 |
| `*-example.md` / `*-scenario.md` | examples 模块拥有的正式场景 |
| `*-report.md` | 带时间点的证据或审查结论 |
| `*-guide.md` | 持续维护的使用说明 |

文档标题默认使用中文；API、类型、模块、协议和机器可读名称保留英文。

## 6. README、AGENTS 和 guides

README 只负责入口、状态摘要和导航。README 中的模块职责必须链接到正式 owner，不应扩写实现细节。

AGENTS 只负责：

- 项目边界和必读入口；
- Agent 操作限制；
- 文档、验证和 Git 工作规则。

AGENTS 可以引用少量高风险护栏，但不能成为设计事实源。

`guides/` 在出现真实用户流程、开发流程或贡献流程时按需创建。Guide 可以解释正式契约，但不得改变 API、默认值、错误、兼容性或性能声明。

## 7. 临时设计生命周期

普通临时设计遵循：

```text
专题草案
  -> 独立审查
  -> 稳定事实迁入 owner 文档
  -> 更新索引和下游引用
  -> 删除临时草案
```

如果未决设计仍存在，不得在迁移时猜测答案；应保留为明确待决项。

### 7.1 长期研究蓝图例外

用户明确保留的长期蓝图可以继续位于 `docs/temp/`，但必须标记：

- `状态：长期研究蓝图`；
- `正式事实源：否`；
- 已固化到哪些正式 owner 文档；
- 仍在研究哪些问题；
- 最后审查日期。

长期蓝图可以持续演进，但正式实现、gate 和 release claim 不得引用其作为权威事实。

## 8. 报告生命周期

Report 是不可反向修改设计的证据快照。正式报告至少记录：

- 日期、目标、输入事实源和范围；
- 执行或审查步骤；
- 通过项、修复项、遗留限制；
- 允许支持的 claim；
- 对应 commit/artifact（存在时）。

失去当前性的报告迁入 `reports/archive/`，文件名使用日期前缀。历史报告不必重写为当前事实，但其索引必须标明已被哪个新报告取代。

## 9. 变更工作流

任何设计变更按以下顺序执行：

1. 识别唯一 Owner；
2. 先修改 Owner 文档；
3. 更新 glossary 中的术语，不在 glossary 重定义行为；
4. 更新模块实现义务、examples 和 gates；
5. 更新 docs/README 和模块 docs/README；
6. 执行文档自动检查和全局语义复审；
7. 需要时生成报告。

仅移动内容时不得顺便改变 API、默认值或 V1 范围。语义修改必须作为独立、可审查的设计决策。

## 10. 文档质量门禁

代码实施前以及每次正式文档变更后至少检查：

- 每份正式设计文档恰好一个 Owner；
- 每份正式文档进入同级 `docs/README.md`；
- Markdown 相对链接有效；
- 正式文档不引用 `docs/temp/` 作为事实；
- 没有 `迁移说明`、空 owner contract 或 orphan 文档；
- fenced code block 配对，且无 trailing whitespace；
- 根 README、AGENTS 和各模块 README 没有重新定义 owner 事实；
- `git diff --check` 和 Maven reactor validate 通过。

仓库级自动检查入口是 `./scripts/check-docs.sh`。

## 11. 非目标

本文不规定 SOMA schema、generated API、runtime storage、性能实现或 benchmark 结论；这些事实必须进入 [正式设计文档索引](README.md) 登记的对应 Owner。
