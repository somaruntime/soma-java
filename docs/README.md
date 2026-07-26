# SOMA Java 文档入口

类型：文档体系入口

状态：正式

Owner：SOMA Java 文档治理

事实范围：正式文档分类、权威关系、当前入口和历史文档边界

非事实范围：具体产品语义、当前代码细节和某次验证结果

采用框架：设计驱动项目文档框架 `1.0.0-rc.2`

最后审查日期：2026-07-24

## 1. 权威关系

SOMA Java 采用以下设计驱动关系：

```text
Blueprint  ->  Design  ->  Code / Tests
                    \       /
Implementation Map -- maps --
Conformance -------- compares Blueprint / Design / Code / Tests
Engineering -------- governs how changes and evidence are produced
Current facts + evidence  ->  Reports
Temporary  ->  candidate decision  ->  atomic promotion or deletion
```

- Blueprint 拥有目标形态和目标使用方式；
- Design 拥有长期规范性设计；
- 代码、配置和可执行产物拥有当前实现事实；
- Implementation Map 只提供实现投影和导航；
- Conformance 识别偏差并记录 Owner 处置，不扩大实施授权；
- Engineering 拥有可靠推进过程；
- Report 拥有正式输出或特定时点结论，不反向定义产品能力；
- Temporary 只承载专题候选设计，收口后必须删除。

## 2. 正式入口

| 分类 | 回答的问题 | 入口 |
|---|---|---|
| Blueprint | SOMA Java 最终希望成为什么、怎样被使用？ | [Blueprint](blueprints/README.md) |
| Design | 系统从原则到能力应当怎样逐层展开，各关注点由谁负责？ | [Design](design/README.md) |
| Implementation Map | 当前关键实现和 evidence 在哪里？ | [Implementation Map](implementation-map/README.md) |
| Conformance | Blueprint、Design、代码和测试是否一致？ | [Conformance](conformance/README.md) |
| Engineering | 项目怎样可靠构建、验证和治理？ | [Engineering](engineering/README.md) |
| Report | 当前输出、测量和 Gate 结论是什么？ | [用户/开发者指南](../guides/README.md)、[报告](../reports/README.md)、[参考应用输出](../soma-examples/docs/README.md) |

修改功能或实施专题时，先从 Blueprint 与对应 Design 理解目标，再通过 Implementation Map 进入代码和测试；发现不一致时进入 Conformance，而不是让当前实现反向降低 Design。

## 3. 模块实现入口

| 模块 | 当前实现导航 |
|---|---|
| `soma-annotations` | [Compiler 与 codegen Map](implementation-map/compiler-and-codegen-map.md)、[模块文档入口](../soma-annotations/docs/README.md) |
| `soma-processor` | [Compiler 与 codegen Map](implementation-map/compiler-and-codegen-map.md)、[模块文档入口](../soma-processor/docs/README.md) |
| `soma-runtime-core` | [Runtime Core Map](implementation-map/runtime-core-map.md)、[模块文档入口](../soma-runtime-core/docs/README.md) |
| `soma-testkit` | [测试与 evidence Map](implementation-map/test-and-evidence-map.md)、[模块文档入口](../soma-testkit/docs/README.md) |
| `soma-examples` | [参考应用与 benchmark Map](implementation-map/scenario-and-benchmark-map.md)、[参考应用输出](../soma-examples/docs/README.md) |
| `soma-benchmarks` | [参考应用与 benchmark Map](implementation-map/scenario-and-benchmark-map.md)、[模块文档入口](../soma-benchmarks/docs/README.md) |

模块 `docs/` 中保留的旧契约路径均为 `superseded` tombstone，只提供历史链接稳定性和 Git provenance，不再拥有正文或当前事实。精确 public/generated/schema/protocol surface 由代码、golden、artifact 和 validator 拥有，并由[可执行契约地图](implementation-map/executable-contract-map.md)登记。

## 4. Temporary 与历史材料

重大长期设计变化在 `docs/temp/<topic>/` 中保持独立，直到实现、验证和授权完成后原子固化；最后删除 Temporary，不归档。

当前 active Temporary topic：

- [SOMA Transformation Model 与 Typed DataFlow 产品化治理](temp/soma-transformation-model-governance/README.md)：建立 Access 之上的 Transformation、Expression、Effect、Reusable DataFlow 与受控并行执行模型；Stage 1–3 已闭合为 immutable implementation candidate，Stage 4 尚未授权，不改变正式产品语义。

新的重大设计变化必须建立独立 topic，不能复用已退役专题作为平行事实源。

旧 root/module 契约保留原路径只为历史链接稳定，必须标记 `superseded`、登记正文 provenance 并退出所有 current 导航。历史 Report 可以引用其当时输入，但当前工作不得把它们当作 Design Owner。

文档分类、Owner、metadata 和生命周期以[文档治理](engineering/documentation-governance.md)为准；综合验证入口是 `./scripts/check.sh`。
