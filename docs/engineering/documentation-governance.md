# 文档治理

类型：Engineering

状态：正式

Owner：SOMA Java 文档过程

事实范围：文档分类、Owner、状态、生命周期、变更闭环和切换规则

非事实范围：产品语义、代码状态和具体专题结论

采用框架：设计驱动项目文档框架 `1.0.0-rc.2`

最后审查日期：2026-07-23

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

每份受治理文档至少声明类型、状态、Owner、事实范围和最后审查日期。Blueprint 还必须声明设计约束入口；Design 必须声明主要设计层次、主要关注点、上位设计和服务 Blueprint；Implementation Map 必须声明核对 commit；snapshot Report 声明日期、commit、环境和方法。

### 1.1 Design 的两个组织维度

Design 入口必须同时维护：

- 抽象层次：从系统原则、系统结构到能力设计，并由横切质量约束各层；
- 关注点：为长期语义分配唯一 Owner，避免沿上下层或相邻能力重复定义。

层次不是目录模板，也不要求一层一文件。每份 Design 只声明主要层次；涉及相邻层次时，由上位设计给出方向，下位 Owner 展开语义或机制。项目 Blueprint 必须能够反向追踪到直接约束其目标形态的 Design。

### 1.2 本项目目录映射

| 逻辑分类 | 正式物理入口 |
|---|---|
| Blueprint / Design / Implementation Map / Conformance / Engineering / Temporary | `docs/<category>/` |
| Report / 用户与开发者输出 | `guides/`；参考应用聚合导航位于 `soma-examples/docs/`，应用自有文档位于各 child `docs/` |
| Report / 性能、治理、Gate、release evidence | `reports/` 与已登记的 module contributor reports |

Report只使用上表已登记入口，不创建`docs/reports/`等平行输出根。目录位置不改变分类权威；每个正式入口必须登记它拥有和不拥有的事实。

## 2. 唯一 Owner

一个正式事实只在一个文档或外部登记源中定义。其他文档优先链接和摘要，不复制完整规则。发现重复时先确认语义 Owner，再删除或降级副本；不能依靠“两个地方同步更新”维持一致性。

同一事实沿抽象层次出现时，上位 Design 只保留系统方向或不变量，下位 Design 拥有可执行语义与机制约束；若下位内容尚未形成独立责任，则留在现有 Owner 内展开，不为形式拆文档。

精确当前 public/generated/schema/protocol surface 可以由代码、`javap` golden、schema artifact 或 validator 拥有，但必须在 Implementation Map 登记同步规则。Design 仍拥有该 surface 的目标语义与演进边界；“代码是当前事实”不等于“代码自动符合设计”。

### 2.1 信息预算

不对所有分类套用统一行数上限。Blueprint可以为完整使用者 journey保留较丰富的示例；Design按唯一 Owner和关注点保持可读；Implementation Map必须简短，避免复制可搜索的代码清单；Report长度由受众和证据决定。需要拆分时以Owner清晰、导航成本和重复事实为依据，而不是为了满足任意行数。

正式 checker不得对所有分类施加统一500行限制；可以对不同分类提供独立的提醒阈值，但不能用行数替代内容和Owner审查。

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

## 5. 文档体系变更

修改分类、权威关系、目录映射或框架版本属于文档体系变更，必须先在Temporary中完成候选、事实迁移审计和明确授权，再在一个可审查变更中同步入口、Owner、链接、checker和旧文档处置。禁止让两套current入口或Owner长期并列。

旧文档若因历史链接需要保留，必须标记 `superseded`、声明当前取代者，并从所有 current 入口移除；它不再拥有正式事实。Temporary 不使用 superseded/archive 逃避删除义务。

## 6. 可执行文档门禁

正式文档检查至少 fail closed 地验证：

- 所有受治理文档的 required metadata 和分类特有 metadata；
- 每个 current 文档被唯一入口索引，superseded 文档不出现在 current 导航；
- relative links 可解析，current/Design/Engineering 不引用 Temporary 作为事实源；
- Design Owner 无重复，Implementation Map 有对应 Design和实现基线，Report 有受众/输入事实源/适用版本，snapshot Report另有日期/commit/环境/方法；
- Design 层次值合法、上位关系和 Blueprint 追踪入口存在；Blueprint 有设计约束入口且不承载当前实现盘点、自审或一致性判定栏目；
- `docs/temp/` 只包含 active topic，topic 有 README、授权边界和退役条件；
- current Blueprint/Design/Map/Conformance/Engineering、Guide 与 registered current-executable Report 不得继续使用已完成 clean cutover 的旧 canonical API/术语；历史 Report 和 superseded Design 保留 provenance；
- 历史报告可以链接 superseded input，但必须通过 current index 区分历史与当前结论。

目录或 metadata 规则变化必须先更新本 Owner，再更新 checker；checker 通过不能替代事实迁移审查。
