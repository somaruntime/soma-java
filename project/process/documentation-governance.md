# 项目信息与文档治理

类型：Process

状态：正式

Owner：SOMA Java 项目信息过程

事实范围：事实分类、角色投影、目录映射、Owner、生命周期、变更和交付边界

非事实范围：产品语义、代码状态、具体测量和 release 结果

采用框架：面向角色与场景的项目组织框架 `2.0.0-rc.1`

最后审查日期：2026-07-30

## 1. 总体模型

SOMA Java 分开治理：

- 正式项目事实：`project/`；
- 当前实现事实：代码、配置、schema、测试和可执行产物；
- 产品信息投影：根 README、`docs/`、examples、API 文档和白皮书；
- 交付物：Maven artifact、精简源码包和 selected release profile。

同一正式事实只有一个 Owner。投影可以选择教学顺序、示例和表达方式，但不能重新
定义产品事实。

## 2. 分类与权威

| 分类 | Owner 规则 | 当前入口 |
|---|---|---|
| Blueprint | 拥有目标形态和目标使用方式，不拥有精确 API | `project/blueprint/` |
| Design | 拥有全部长期规范性设计 | `project/design/` |
| Code/config/executable | 拥有当前实现事实 | Maven modules、tests、scripts |
| Modules | 导航模块职责、Design、实现和验证，不复制跨模块语义 | `project/modules/` |
| Implementation Map | 只投影当前代码并记录核对基线 | `project/implementation-map/` |
| Conformance | 识别偏差和 Owner 处置，不自动授权修改 | `project/conformance/` |
| Process | 拥有可靠构建、验证、benchmark、发布和治理过程 | `project/process/` |
| Report | 拥有正式测量或审查结论，不发明产品能力 | `project/reports/` |
| Temporary | 只承载活跃专题候选设计 | `project/temp/<topic>/` |
| Product Projection | 面向角色解释正式事实 | `README.md`、`docs/`、examples |

实现服务于 Design，Design 服务于 Blueprint。代码拥有“现在是什么”的事实，但不
因此天然正确。

## 3. 角色与信息入口

- 使用者、集成者和技术选型者从根 README 与 `docs/` 进入；
- 贡献者从 `CONTRIBUTING.md`、`project/modules/` 和 `project/process/` 进入；
- Owner、维护者和 release reviewer 从 `project/README.md` 进入完整事实；
- Agent 从 `AGENTS.md` 和当前任务 Owner 进入，不因可搜索全仓库而自动扩大授权。

用户完成安装、运行、排障和升级不得以阅读 `project/` 为前置。影响使用决策的
安全、兼容性、性能适用条件和限制必须形成用户可行动的投影，并链接正式事实。

## 4. 文档 metadata 与索引

Blueprint、Design、Module、Implementation Map、Conformance、Process、Report
和 Temporary 至少声明：

- 类型、状态、Owner；
- 事实范围、非事实范围；
- 最后审查日期。

Blueprint 另声明设计约束入口；Design 声明层次、关注点、上位设计和服务蓝图；
Implementation Map 声明实现核对基线；snapshot Report 声明适用版本、输入事实源、
日期、commit、环境和方法。

产品投影必须能追踪到输入事实源和目标受众。为了不把治理 metadata 暴露为用户首屏
噪声，可以把投影 metadata 放在 Markdown HTML comment 中；checker 仍必须验证。

每个 current 项目事实由唯一入口索引。普通代码和可搜索符号不在 Map 或模块文档中
复制完整清单。

## 5. Design 与模块文档

Design 按“抽象层次 + 关注点”组织，不按源码目录机械分割。上位 Design 保留系统
方向或不变量，下位 Owner 展开语义和机制。

模块文档集中在 `project/modules/<module>/`，可以拥有模块职责、依赖方向、
lifecycle、实现、测试、benchmark 和局部偏差入口，但不得重新拥有跨模块 Design。
模块根 README 只因 GitHub、构建或源码导航需要而保留为薄入口；模块目录不再建立
平行 `docs/`。

三个 reference application 各自拥有应用 Blueprint、Design 和 Validation，但
它们对 SOMA 产品规范性为否。用户运行说明保留在 application 根 README。

## 6. Report、evidence 与交付物

Report 声明受众、适用版本、输入事实源和结论边界。性能、Gate、support 和 release
报告位于 `project/reports/`；用户需要的结论另行投影到 `docs/`。

原始日志、JFR、profiler 输出、临时 benchmark 结果和可重复生成 artifact 默认
进入 `target/`、CI artifact 或批准的 evidence store，不进入 Git。长期结论进入
Report，机械约束进入测试、baseline、manifest 或脚本。

Maven artifact 与精简源码包采用显式允许集合；不得把整个 checkout 当成客户
交付物。完整 private Git repository 仍包含 `project/`，目录不是访问控制边界。

## 7. Temporary 生命周期

重大长期设计或项目体系变化先进入 `project/temp/<topic>/`。Temporary 必须声明
授权边界、事实范围和退役条件，但不拥有长期事实。

专题收口顺序：

```text
promote accepted facts to unique Owners
  -> refresh Map / Conformance / Report / projections
  -> validate links, role journeys, delivery and Gates
  -> delete Temporary
```

Temporary 不归档，也不得改名为永久 notes、history 或 roadmap。没有 active
topic 时不创建空目录或占位文件。

## 8. 变更闭环

执行者按风险自主合并或展开步骤，但必须完成：

1. 明确角色、目标、授权和 selected delivery profile；
2. 从 Blueprint、Design、当前实现和 evidence 确认事实；
3. 重大变化在 Temporary 中形成候选；
4. 在授权范围内实现和验证；
5. 更新唯一 Owner、Map、Conformance 和 Report；
6. 更新受影响的角色投影；
7. 生成并验证交付物；
8. 删除 Temporary、旧入口和 migration-only checker；
9. 从使用者、贡献者、维护者和 Agent 入口重新验证可达性。

修改分类、目录映射或框架版本时，必须在一个可审查 cutover 中同步入口、链接、
checker 和旧路径处置。除已有明确外部链接承诺外，不保留 current tombstone。

## 9. 可执行门禁

文档 checker 至少 fail closed 地验证：

- project facts metadata、分类特有 metadata 和唯一索引；
- Product Projection 的受众、输入事实源和角色入口；
- Design Owner 无重复，Map 有 Design 和实现基线；
- module facts 全部位于 `project/modules/`，代码模块没有平行 `docs/`；
- relative links 可解析，正式 Owner 不引用 Temporary 作为长期事实；
- Report metadata、current index 和 snapshot 边界完整；
- `project/temp/` 只包含 active topic；
- legacy current 路径、superseded tombstone 和未登记文档为零；
- 根 README、`docs/README.md`、`project/README.md` 和交付 manifest 均可达。

checker 通过不能替代事实迁移、内容 Owner 和 scope non-regression 审查。
