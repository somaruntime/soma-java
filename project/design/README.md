# SOMA Java V1 Design 总览

类型：Design Entry

状态：Active Baseline

正式事实源：是

Owner：SOMA Java V1 正式 Design 路由、职责边界与权威关系

最后审查日期：2026-08-01

## 1. 设计责任

本目录回答：为了实现[产品蓝图](../blueprint/README.md)，SOMA Java V1 长期必须遵守
哪些规范性合同。

Design 服务于 Blueprint；代码、配置和测试实现并验证 Design。当前没有 production
implementation，因此这些文档不构成可用 API、性能或 release 声明，也不授权从
predecessor 恢复模块或代码。

## 2. 唯一 Owner 地图

| Design Owner | 唯一拥有的长期事实 | 主要 Blueprint requirement |
|---|---|---|
| [Schema 与编译生成](schema-and-generation.md) | composition、annotation、declaration、generated identity/object、命名、编译诊断与 full-regeneration support contract | BP-1、BP-2、BP-3、BP-10 |
| [数据模型与存储](data-model-and-storage.md) | Group/Table identity、logical Field、leaf storage、null/type、Key/Index、关系 Table、order、capacity 与 GC lifecycle | BP-3、BP-4、BP-8、BP-9 |
| [逻辑层 API](logical-api.md) | generated navigation、Table direct operation、source、Stream capability、terminal、cursor/View、materialization 与 metadata surface | BP-1、BP-2、BP-4、BP-5、BP-8 |
| [Generated Java API Signature](generated-api-signatures.md) | public/shared/generated Java 8 exact signature、type family、operation property、metadata carrier 与 compatibility boundary | BP-1、BP-2、BP-5、BP-7、BP-8 |
| [执行、并发与并行](execution-and-concurrency.md) | pipeline binding、admission、currentness、atomic publish、sequential/parallel scheduling、determinism 与 resource boundary | BP-5、BP-6、BP-8、BP-9 |
| [结果与失败](results-and-failures.md) | normal result、failure carrier/code、mapping、precedence、sanitization 与失败后状态保证 | BP-7、BP-10 |
| [Production Implementation Architecture](implementation-architecture.md) | artifact/build topology、runtime component boundary、storage/Index/admission/publish/scheduler baseline algorithm | BP-4、BP-5、BP-6、BP-8、BP-10 |

同一语义只能由表中一个文档拥有。其他文档可以摘要并链接，但不得复制出第二套规则。

## 3. 跨文档关系

```text
Blueprint
    -> Schema and Generation
        -> generated logical surface
    -> Data Model and Storage
        -> authoritative Table state
    -> Logical API
        -> legal user expression
    -> Execution and Concurrency
        -> operation semantics and publication
    -> Results and Failures
        -> observable outcome
    -> Generated Signature
        -> exact Java projection
    -> Production Implementation Architecture
        -> artifact/build/internal mechanism
            -> Code / Tests / Build
                -> Conformance
```

关键边界：

- Schema Design 决定“生成什么”，Logical API Design 决定“用户能够表达什么”；
- Signature Design 把已经成立的用户能力机械投影成 exact Java 8 surface，不重新发明
  语义；
- Storage Design 决定 authoritative state，Execution Design 只能暂存、读取和发布；
- Implementation Architecture 决定怎样承载合同，不能把 internal baseline 上升为第二套
  用户模型；
- Logical API 决定 capability 是否存在，Failure Design 不能用 runtime
  `UNSUPPORTED_OPERATION` 代替 compile-time absence；
- Execution Design 决定 operation phase，Failure Design拥有 phase 对外怎样仲裁；
- Conformance 记录证据和差距，不反向发明产品或 Design。

## 4. 设计不变量

以下规则跨全部 Design 成立：

1. 普通用户 API 不暴露 physical Column、row position、backing array、scratch、lock、
   transaction token 或内部 scheduler；
2. Schema/generated/runtime 不建立两份独立产品模型；
3. 不支持的 capability 从 generated type 缺席；
4. Table-local mutation 在成功时一次发布，失败时 zero publication；
5. 顺序/并行路径共享 logical semantics；
6. ordinary Object referent、跨 Table 编排和外部 side effect 不属于 SOMA atomicity；
7. 任何内部优化都不能改变 logical order、null/equality、Result 或 failure contract；
8. predecessor 只提供问题与历史 evidence，不提供当前兼容性要求；
9. production surface 必须先完成 capability、consumer、lifecycle、failure boundary 和
   evidence 的 surface admission；
10. 设计空白必须显式记录为 Conformance/implementation Gate，不能由实现静默选择。

## 5. 当前正式程度

本 Design 集合已经关闭进入实现前必须由设计决定的 V1 产品语义、exact Java surface、
production topology 与 baseline mechanism。仍待 production evidence 证明，而不是待
实现者自行裁决的内容包括：

- annotation processor 的完整 diagnostic 实例与 generated golden；
- Maven/IDE full-regeneration 的真实 integration/stale cleanup；
- storage、Index、atomic publish 与 scheduler 的代码正确性和 fault injection；
- correctness、performance、security、packaging 与 release Gate；
- 未来可能支持但当前明确排除的 Gradle incremental path。

这些属于[实施计划](../engineering/v1-implementation-plan.md)和 Conformance，不重新打开
已经固定的产品语义。若实施证明某项合同不可成立，必须建立新的 Temporary 专题并由
相应 Owner 裁决，不能在代码中静默选择。

## 6. 明确不恢复的 predecessor surface

当前 Design 不包含 `DataFlow`、`Transformation`、`Candidate`、public `Batch`、
public physical `Column`、`Segment`、ChildTable、`@SomaChild`、ownership graph、
manual `release()`、secondary unique、generic join 或 compatibility adapter。

## 7. 验证与 Conformance

[Conformance 入口](../conformance/README.md)记录：

- 当前 active checkout 是否实现这些 Design；
- P2 Java 8 feasibility evidence 的正式结论与边界；
- production 实施前必须建立的 compile、consumer、negative、runtime、performance
  build、security 与 packaging Gate；
- 已知差异是否阻断 implementation/release claim。
