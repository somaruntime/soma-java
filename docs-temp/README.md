# SOMA Java 候选文档体系

类型：候选文档体系入口

状态：候选，切换准备完成（未启用）

Owner：SOMA Java 文档治理

事实范围：`docs-temp/` 的分类、入口、权威边界和成熟条件

非事实范围：SOMA 当前正式设计、当前实现状态和 release 声明

采用框架：[设计驱动项目文档框架 1.0.0-rc.2](../../../project-documentation-framework.md)

实现核对基线：`b991f4c`

切换前审查输入基线：`fe163b8`

最后审查日期：2026-07-20

## 1. 当前边界

本目录是一套隔离建设的候选文档体系。它用于验证新的分类、Owner、导航和内容颗粒度，**当前不拥有项目正式事实，也不改变任何实现授权**。

在正式切换完成前：

- 当前设计事实仍从 [`docs/README.md`](../docs/README.md) 进入；
- 当前实现事实仍由代码、配置、测试和可执行产物拥有；
- 当前验证与 release 状态仍以 [`reports/README.md`](../reports/README.md) 为入口；
- `docs-temp/` 与当前文档发生冲突时，以当前正式 Owner 为准，并在本体系的 Conformance 或迁移专题中处理。

## 2. 框架采用说明

- 采用版本：`1.0.0-rc.2`；
- 项目 Owner：SOMA Java 文档治理；
- 核心权威关系偏差：无；
- 临时目录偏差：候选期使用 `docs-temp/`，而不是正式 `docs/`；只有原子切换获批后才恢复框架推荐的正式目录语义；
- 正式目录映射：Blueprint、Design、Implementation Map、Conformance、Engineering 和 Temporary 进入 `docs/` 的同名子目录；Report 是逻辑分类，其中用户/开发者输出继续位于 `guides/`，性能、治理、Gate 和 release evidence继续位于 `reports/`；
- 候选期偏差：`docs-temp/reports/` 只用于验证 Report 分类与内容，不会在切换时机械复制成第三个正式输出入口；
- 旧 root/module Design 的逐项处置由本专题迁移审计决定，不能因为保留原路径就继续拥有当前事实。

## 3. 分类入口

| 分类 | 本项目中的职责 | 入口 |
|---|---|---|
| Blueprint | SOMA Java 的目标形态、目标体验和代表性场景 | [blueprints/README.md](blueprints/README.md) |
| Design | 长期规范性设计；回答系统应当遵守什么 | [design/README.md](design/README.md) |
| Implementation Map | 当前代码的关键投影、导航与核对基线 | [implementation-map/README.md](implementation-map/README.md) |
| Conformance | Blueprint、Design、代码和测试之间的真实偏差 | [conformance/README.md](conformance/README.md) |
| Engineering | 文档、构建、测试、benchmark 和 release 过程 | [engineering/README.md](engineering/README.md) |
| Reports | 面向用户、开发者和治理决策的正式候选输出；正式物理入口映射到 `guides/` 与 `reports/` | [reports/README.md](reports/README.md) |
| Temporary | 正在进行的专题设计；收口后必须固化并删除 | [temp/README.md](temp/README.md) |

## 4. 权威关系

```text
Blueprint  ->  Design  ->  Code / Tests
                    \       /
Implementation Map -- maps --
Conformance -------- compares Blueprint / Design / Code / Tests
Engineering -------- governs how changes and evidence are produced
Current facts + evidence  ->  Reports
Temporary  ->  candidate decision  ->  atomic promotion or deletion
```

同一正式事实只能有一个 Owner。入口文档只索引，不复制下级 Owner 的完整规则；Implementation Map 只映射代码，不反向定义设计；Report 只陈述其输入事实和证据允许支持的结论。

## 5. 候选体系成熟条件

只有同时满足以下条件，才可以另行决定是否替换当前体系：

- Blueprint 能清楚约束 Design 的方向，并覆盖主要目标场景；
- Design 已覆盖当前全部长期规范性事实，且 Owner 唯一；
- Implementation Map 已按切换候选 commit 重新核对；
- Conformance 中没有未裁决的关键偏差；已裁决但尚未实施的场景目标可以保留，但必须明确不阻塞文档体系切换；
- Engineering 能覆盖当前实际构建、测试、benchmark 和发布过程；
- Reports 明确区分当前能力、目标能力和已知差距；
- 旧文档到新 Owner、executable contract 或历史输出的迁移映射完整，没有未说明的事实遗失或双重 Owner；
- 链接、文档检查和与范围相称的代码 Gate 全部通过；
- 切换得到明确授权，并以一个原子变更完成；
- 本次文档体系重构的 Temporary 在切换完成后删除。

成熟不等于自动启用。本目录通过自审后仍保持候选，直到项目 Owner 明确批准切换。

当前切换前审查已满足上述内容与治理条件；剩余事项是明确授权及原子切换本身。正式切换前，现行Owner和入口继续有效。
