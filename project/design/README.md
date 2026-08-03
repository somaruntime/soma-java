# SOMA Java V1 Design 总览

类型：Design Entry

状态：Active V1 Baseline

正式事实源：是

Owner：SOMA Java V1 正式 Design 路由、职责边界与权威关系

最后审查日期：2026-08-03

## 1. 设计责任

本目录回答：为了实现[产品蓝图](../blueprint/README.md)，SOMA Java V1 长期必须遵守哪些
规范性合同。Design服务Blueprint；code/config/tests在出现后实现Design；
[Conformance](../conformance/README.md)记录二者是否一致。

本目录不构成implementation、performance或release声明。当前没有production source、
build、generated consumer API或benchmark。

## 2. 唯一 Owner 地图

| Design Owner | 唯一拥有的长期事实 | Blueprint |
|---|---|---|
| [核心抽象、叙事与不变量证明链](core-abstractions-and-narratives.md) | 跨Design architecture skeleton、typed relation、A0-A27/N1-N8/INV-01..19 proof-chain routing与M0-M2变更协议 | BP-1至BP-15 |
| [Schema 与编译生成](schema-and-generation.md) | composition、annotation、Field role/type、generated identity/object、命名、diagnostic、full regeneration | BP-1、BP-2、BP-3、BP-14 |
| [数据模型与存储](data-model-and-storage.md) | Group/Table identity、logical Field、long-domain StateRoot/Chunk、leaf/null、Key/Index、capacity/order、compression、GC/backend seam | BP-3、BP-4、BP-7、BP-10、BP-12、BP-13 |
| [逻辑层 API](logical-api.md) | generated hierarchy、direct source、point/Selection operation、View、query/aggregate/Group/Join、metadata/explain user surface | BP-1、BP-5、BP-6、BP-7、BP-12 |
| [Generated Java API Signature](generated-api-signatures.md) | annotation/shared/generated exact Java 8 type family、method grammar、functional interface、compatibility boundary | BP-1、BP-2、BP-5、BP-7、BP-11、BP-14 |
| [规划与优化](planning-and-optimization.md) | typed Logical/Predicate IR、normalization、rewrite、Index substitution、Join/Group planning、statistics、reference interpreter | BP-7、BP-8、BP-9、BP-10 |
| [执行、并发与并行](execution-and-concurrency.md) | pipeline binding、Group guard、currentness、mutation publish、parallel scheduling、resource configuration/admission、quiescence | BP-5、BP-6、BP-9、BP-10、BP-12、BP-13 |
| [结果与 Structured Failure](results-and-failures.md) | normal result、failure carrier/code、mapping、precedence、sanitization与failed-state guarantee | BP-6、BP-11、BP-15 |
| [Production Implementation Architecture](implementation-architecture.md) | artifact/build topology、runtime component seams、storage/Index/compression/scheduler baseline mechanism与complexity boundary | BP-4、BP-8、BP-9、BP-10、BP-14、BP-15 |

同一语义只能由一个Owner定义。其他文档可以摘要并链接，不能复制出第二套合同。

## 3. 新增正式 Owner 的 surface admission

### 3.1 Core Abstractions and Narratives

`core-abstractions-and-narratives.md`不新增public API、module或runtime container：

1. **独立capability/consumer**：跨Design architecture comprehension、父子叙事与不变量证明链
   routing；consumer是implementer、test author、reviewer与AI Agent；
2. **Owner/lifecycle/failure boundary**：从正式产品语义到implementation skeleton，随evidence按
   M0-M2演进；它不创造runtime failure或覆盖精确Design；
3. **现有surface不足**：Design index只拥有文档authority，分责Design各自拥有精确合同；把
   dynamic narrative/proof map复制到各Owner会形成平行事实；
4. **Blueprint trace**：BP-1至BP-15，尤其BP-8、BP-11、BP-12、BP-15；
5. **成立evidence**：A0-A27 owner/cycle review、N1-N8 lifecycle closure、INV-01..19 proof chain、
   three journeys与I0-I8 fill map。

### 3.2 Planning and Optimization

`planning-and-optimization.md`是此前大规模引擎正式晋升新增的Design surface：

1. **独立capability/consumer**：typed plan与semantics-preserving optimization；consumer是
   runtime/compiler implementer、Conformance与diagnostic author；
2. **Owner/lifecycle/failure boundary**：从pipeline logical node形成到physical plan选择；
   terminal结束后plan失效；对外failure仍由Results/Execution Owner仲裁；
3. **现有surface不足**：Logical API只应定义“用户能表达什么”，Implementation Architecture
   已拥有artifact、storage、Index、publish与scheduler；把IR/rewrite塞入任一方都会形成
   过宽Owner；
4. **Blueprint trace**：主要承接BP-7、BP-8、BP-9、BP-10；
5. **成立evidence**：Java 8 expression/callback type shape、reference-vs-optimized differential、
   rewrite/Join-kind/null/order/Index substitution golden与`_explain()`验证。

它不是public planner SPI、query language或新的runtime module。

## 4. 权威关系

```text
Blueprint
    -> Core Abstractions and Narratives
        -> cross-Owner skeleton, narratives and proof-chain routing
    -> Schema and Generation
        -> generated type universe
    -> Data Model and Storage
        -> authoritative state
    -> Logical API
        -> legal user expression
    -> Generated Signature
        -> exact Java projection
    -> Planning and Optimization
        -> legal lowering and rewrite
    -> Execution and Concurrency
        -> binding, admission, execution and publish
    -> Results and Failures
        -> observable outcome
    -> Implementation Architecture
        -> artifact and internal mechanism
            -> Code / Tests / Build
                -> Conformance
```

边界规则：

- Schema决定“生成什么”，Logical API决定“用户能表达什么”；
- Core Owner只路由跨Design skeleton/proof chain，不复制或覆盖精确合同；
- Signature只机械投影已成立能力，不重新发明语义；
- Planning决定“怎样合法重写”，Execution决定“怎样绑定、调度和发布”；
- Storage拥有authoritative state，Execution只能读取、暂存和一次发布；
- Architecture拥有可替换internal mechanism，不能把机制抬升成用户模型；
- Failure Design不能用runtime `UNSUPPORTED_OPERATION`代替compile-time absence；
- Conformance记录evidence与gap，不反向创造产品语义。

## 5. 跨 Design 不变量

1. 普通API不暴露physical Column、Chunk、row position、array、scratch、lock、plan node或worker；
2. Schema/generated/runtime只有一套产品模型；
3. 不支持的capability从generated type缺席；
4. 所有logical size/capacity/count/cardinality使用`long`；
5. Table-local mutation成功时一次发布，失败zero publication；
6. 同一Group外部operation不重叠；不同Group并发由application拥有；
7. sequential/parallel共享logical result、order、numeric、mutation与non-resource failure
   semantics；pool/resource/interrupt可以产生显式mode-specific failure，但不能产生alternate result；
8. optimizer/compression/Index不能改变callback、null/missing、duplicate、order或failure；
9. ordinary referent、跨Table transaction与external side effect不属于SOMA atomicity；
10. production surface必须先有capability、consumer、Owner、lifecycle、failure与evidence；
11. 设计空白必须显式阻断readiness，不能让implementation静默决定；
12. predecessor只提供历史问题/evidence，不提供compatibility要求。

## 6. 明确排除

正式Design不包含predecessor public `DataFlow`/`Transformation`/`Candidate` model、public
Batch/Loader、public Column、
Segment、ChildTable、`@SomaChild`、ownership graph、manual release、secondary unique、
multi-way/non-equality Join、backend SPI或predecessor compatibility adapter。

Off-heap/mmap只保留Chunk representation seam；没有public placeholder或V1 implementation
claim。

## 7. 当前实现程度

产品语义、exact Java 8 surface、planning/execution/storage architecture、implementation plan
与Conformance Gates已经正式化。仍待implementation/evidence证明：

- JSR 269完整processor、diagnostic与full-regeneration；
- production storage、Index、compression、planner、scheduler与atomic publish；
- reference interpreter与optimized engine differential；
- Java 8 independent consumer、negative、runtime、failure、security、performance与package Gate；
- 三个reference scenario与release qualification。

这些是implementation gap，不是implementation可以自行解释的产品语义。

## 8. 下游入口

- [Implementation Plan](../engineering/v1-implementation-plan.md)
- [Conformance](../conformance/README.md)
- [Final Pre-implementation Global Consistency Review](../conformance/v1-final-pre-implementation-global-consistency-review.md)
