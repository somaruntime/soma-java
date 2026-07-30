# SOMA Java 项目事实入口

类型：Project Facts Entry

状态：正式

Owner：SOMA Java 项目治理

事实范围：项目事实分类、角色入口、目录映射、权威关系和 selected delivery profile

非事实范围：替代各 Blueprint、Design、代码、Report 或发布证据的具体内容

采用框架：面向角色与场景的项目组织框架 `2.0.0-rc.1`

最后审查日期：2026-07-30

## 1. 项目组织

`project/` 保存 SOMA 怎样被设计、实现、验证和演进的完整事实体系。代码、配置、
schema、测试和可执行产物继续拥有当前实现事实；`docs/`、README、白皮书和示例是
针对角色与场景的信息投影。

```text
Blueprint -> Design -> Code / Config / Tests
                  \        /
Implementation Map -- maps
Conformance ------- compares
Process ----------- governs evidence and delivery
Current facts + evidence -> Reports
Temporary -> candidate decision -> promotion or deletion
```

同一正式事实只有一个 Owner。Implementation Map 不重新定义代码，Conformance
不自动授权修改，Report 不发明产品能力，产品文档不反向改变 Design。

## 2. 正式事实入口

| 分类 | 回答的问题 | 入口 |
|---|---|---|
| Blueprint | SOMA 最终希望成为什么、怎样被使用？ | [Blueprint](blueprint/README.md) |
| Design | 系统长期必须遵守什么？ | [Design](design/README.md) |
| Modules | 每个模块承担什么，如何进入实现和验证？ | [Modules](modules/README.md) |
| Implementation Map | 当前代码、测试、数据流和 hot path 在哪里？ | [Implementation Map](implementation-map/README.md) |
| Conformance | Blueprint、Design、实现和 evidence 是否一致？ | [Conformance](conformance/README.md) |
| Process | 项目怎样构建、验证、benchmark、发布和治理？ | [Process](process/README.md) |
| Report | 当前性能、Gate、support 和 release 结论是什么？ | [Reports](reports/README.md) |

当前 active Temporary：

- [项目组织框架 2.0 迁移](temp/project-organization-v2/README.md)；
- [SOMA V1 产品心智模型与 API 重构](temp/soma-v1-product-api-reset/README.md)。

Temporary 完成事实 promotion、验证和切换后必须删除；没有 active topic 时不保留
空目录或“当前没有内容”的占位文件。

## 3. 角色入口

| 当前角色 | 默认入口 |
|---|---|
| 使用者、集成者、技术选型者 | [产品文档](../docs/README.md) |
| 贡献者 | [CONTRIBUTING](../CONTRIBUTING.md)、[Modules](modules/README.md)、[Process](process/README.md) |
| Owner、维护者、release reviewer | 本页和全部正式事实入口 |
| Agent | [AGENTS.md](../AGENTS.md)及当前任务对应 Owner |

Agent 不因能够搜索整个仓库而自动获得维护者授权。

## 4. 项目目录映射

| 逻辑表面 | 当前物理位置 |
|---|---|
| 产品信息投影 | `README.md`、`docs/`、examples、`SUPPORT.md`、`SECURITY.md` |
| 完整项目事实 | `project/` |
| 生产实现 | 四个 `soma-*` production Maven module |
| 普通 reference consumers | `soma-examples/` |
| benchmark 与可执行 evidence source | `soma-benchmarks/`、`tests/`、`scripts/` |
| Agent consumer workflow | `.agents/skills/use-soma-java/` |
| 原始或可重建 evidence | `target/`、CI artifact 或批准的外部 evidence store |

模块根 README 因 GitHub 和源码导航保留为薄入口；模块内部不再维护平行 `docs/`
事实体系。

## 5. Delivery profile

当前 selected profile 是 `private-github-source`：

- 完整 Git repository 包含 `project/`，用于版本、审查和 release evidence 追踪；
- Maven release-shaped artifacts 只包含 parent/module POM 与四个 production
  module 的 binary、sources、Javadoc JAR；
- 精简源码交付物由显式 allowlist 生成，不默认打包整个 checkout；
- public GitHub 与 Maven Central 仍是 `not-selected`，不得从 private CI 或本机
  evidence 外推。

具体过程见 [Release 治理](process/release-governance.md)和
[G6 readiness](reports/java-v1-g6-release-readiness-report.md)。
