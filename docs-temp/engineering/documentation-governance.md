# 文档治理

类型：Engineering

状态：候选

Owner：SOMA Java 文档过程

事实范围：文档分类、Owner、状态、生命周期、变更闭环和切换规则

非事实范围：产品语义、代码状态和具体专题结论

采用框架：设计驱动项目文档框架 `1.0.0-rc.2`

最后审查日期：2026-07-19

## 1. 分类与权威

| 分类 | 本项目 Owner 规则 |
|---|---|
| Blueprint | 拥有目标形态和目标使用方式，不拥有精确 API |
| Design | 拥有所有长期规范性设计；按抽象层次和关注点组织 |
| Code/config/executable | 拥有当前实现事实 |
| Implementation Map | 只投影代码并记录核对基线 |
| Conformance | 识别偏差并记录 Owner 处置，不重新定义事实或授权 |
| Engineering | 拥有可靠推进过程，不静默改变产品语义 |
| Report | 拥有某次正式输出/测量/审查结论，不独立发明能力 |
| Temporary | 承载专题候选设计，不拥有长期事实 |

每份受治理文档至少声明类型、状态、Owner、事实范围和最后审查日期。Implementation Map 还必须声明核对 commit；snapshot Report 声明日期、commit、环境和方法。

## 2. 唯一 Owner

一个正式事实只在一个文档或外部登记源中定义。其他文档优先链接和摘要，不复制完整规则。发现重复时先确认语义 Owner，再删除或降级副本；不能依靠“两个地方同步更新”维持一致性。

## 3. 轻量治理闭环

执行者根据风险自主合并或展开步骤，但必须完成：

1. 理解相关 Blueprint/Design，并用 Implementation Map、代码和测试确认当前事实；
2. 重大长期设计变化先进入 Temporary，当前正式 Design 在验证前保持稳定；
3. 在明确授权范围内实现和验证；
4. 收口时把长期事实原子固化到唯一 Owner，更新必要地图、Conformance 和 Report；
5. 最终验证后删除 Temporary，并确认没有正式引用指向它。

局部实现修复、内部重构或文案勘误若不改变长期设计，可以不创建 Temporary。流程不是固定脚本；不能省略的是权威关系、授权边界、验证和 Temporary 退役。

## 4. Temporary 生命周期

专题进行期间，Temporary 保持独立，正式 Design 不写入中间状态。完成条件包括候选设计稳定、实现/测试完成、差距裁决、证据通过和切换授权。

最后一次变更应按以下顺序原子完成：

```text
promote long-lived facts to Blueprint/Design/Engineering as appropriate
  -> refresh Implementation Map / Conformance / Reports
  -> validate all references and Gates
  -> delete Temporary
```

若专题过程本身具有长期证据价值，先转化为 Governance Report，再删除 Temporary。Temporary 不归档。

## 5. 候选体系切换

`docs-temp/` 当前只是候选。正式切换必须另获授权，并在一个可审查变更中完成入口、Owner、链接、检查脚本和旧文档处置。禁止先让一部分正式入口指向候选体系，形成长期双重 Owner。
