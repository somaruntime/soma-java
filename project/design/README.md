# SOMA Java V1 Design 总览

类型：Design Entry

状态：Active V1 Baseline

正式事实源：是

Owner：SOMA Java V1 正式 Design 路由、职责边界与权威关系

最后审查日期：2026-08-12

## 1. 设计责任

本目录回答：为了实现[产品蓝图](../blueprint/README.md)，SOMA Java V1 长期必须遵守哪些
规范性合同。Design服务Blueprint；code/config/tests在出现后实现Design；
[Conformance](../conformance/README.md)记录二者是否一致。

本目录不构成implementation、performance或release声明；current executable fact 与 Gate 状态
统一由[Conformance](../conformance/README.md)记录，不在 Design 中复制易漂移的实现清单。

## 2. 唯一 Owner 地图

| Design Owner | 唯一拥有的长期事实 | Blueprint |
|---|---|---|
| [核心抽象、叙事与不变量证明链](core-abstractions-and-narratives.md) | 跨Design architecture skeleton、typed relation、A0-A27/N1-N8/INV-01..19 proof-chain routing、Canonical/Bound/Physical responsibility skeleton与M0-M2变更协议 | BP-1至BP-15 |
| [Schema 与编译生成](schema-and-generation.md) | composition、annotation、Field role/type、generated identity/object、命名、diagnostic、full regeneration | BP-1、BP-2、BP-3、BP-14 |
| [数据模型与存储](data-model-and-storage.md) | Group/Table identity、logical Field、32位结构域/64位累计域、StateRoot/Chunk、leaf/null、Key/Index、capacity/order、compression、GC/backend seam | BP-3、BP-4、BP-7、BP-10、BP-12、BP-13 |
| [逻辑层 API](logical-api.md) | generated hierarchy、direct source、point/Selection operation、View、query/aggregate/Group/Join、metadata/explain user surface | BP-1、BP-5、BP-6、BP-7、BP-12 |
| [Generated Java API Signature](generated-api-signatures.md) | annotation/shared/generated exact Java 8 type family、method grammar、functional interface、compatibility boundary | BP-1、BP-2、BP-5、BP-7、BP-11、BP-14 |
| [规划与优化](planning-and-optimization.md) | Java lowering边界、Canonical/Bound/Normalized/Predicate IR、PhysicalPlan decision与ResourceEstimate、rewrite、Index substitution、Join/Group planning、statistics、reference interpreter | BP-7、BP-8、BP-9、BP-10 |
| [执行、并发与并行](execution-and-concurrency.md) | pipeline binding、Group guard、currentness、resource admission、operation-local ExecutionFrame、mutation publish、parallel scheduling与quiescence | BP-5、BP-6、BP-9、BP-10、BP-12、BP-13 |
| [结果与 Structured Failure](results-and-failures.md) | normal result、failure carrier/code、mapping、precedence、sanitization与failed-state guarantee | BP-6、BP-11、BP-15 |
| [Production Implementation Architecture](implementation-architecture.md) | artifact/build topology、Canonical-to-frame runtime seam、specialized operator、storage/Index/compression/scheduler baseline mechanism与complexity boundary | BP-4、BP-8、BP-9、BP-10、BP-14、BP-15 |

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
        -> Canonical/Bound semantics, legal rewrite and physical decision
    -> Execution and Concurrency
        -> binding, resource admission, ExecutionFrame, execution and publish
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
- Planning拥有Canonical/Bound/Normalized语义、合法重写、PhysicalPlan decision与ResourceEstimate，
  包括Physical Pipeline、Segment、Breaker、Kernel、Morsel与完整ResourceEstimate的单次decision；Execution拥有actual resource lease、
  ExecutionFrame、shared ordinal-work lifecycle、调度、发布与quiescence；
- Storage拥有authoritative state，Execution只能读取、暂存和一次发布；
- Architecture拥有可替换internal mechanism，不能把机制抬升成用户模型；
- Failure Design不能用runtime `UNSUPPORTED_OPERATION`代替compile-time absence；
- Conformance记录evidence与gap，不反向创造产品语义。

## 5. 跨 Design 不变量

1. 普通API不暴露physical Column、Chunk、row position、array、scratch、lock、plan node或worker；
2. Schema/generated/runtime只有一套产品模型；
3. 不支持的capability从generated type缺席；
4. 单Table size/capacity/raw locator使用checked `int`；Stream/Relation/Group count/cardinality、
   memory与version使用checked `long`；
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
与Conformance Gates已经正式化。I0-I8 production implementation与G1-G10 qualification已经闭合；
当前 executable fact、性能/场景证据及publication边界由
[Conformance](../conformance/README.md)唯一记录，不由Design复制。

Canonical Logical IR与执行引擎M1 responsibility baseline已正式晋升并冻结；Physical Execution Engine
M2的Pipeline/Segment/Breaker/Kernel/Frame/Morsel责任也已正式晋升，当前implementation状态由
[M2 Conformance](../conformance/v1-physical-execution-engine-m2-promotion-readiness.md)拥有。其
[S1-S6 implementation plan](../engineering/canonical-ir-execution-engine-implementation-plan.md)保留实施前
冻结快照，[最终S6资格](../conformance/canonical-ir-execution-s6-final-qualification.md)已经证明production
replacement、performance guard与Owner closure全部`PASS`。当前实现状态由Conformance拥有，不反向改写
已冻结Design或Engineering Plan。

Design仍是implementation的上游合同；后续优化只能在Owner边界内替换内部机制，不能把既有证据
反向解释为新的产品语义、正式release、跨硬件SLA或一亿行性能承诺。

Finite primitive Chunk kernel与Chunk-morsel partial aggregate已经通过
[正式晋升记录](../conformance/v1-vectorized-physical-pipeline-phase1-promotion.md)作为M1内部机制纳入
baseline：PhysicalPlan一次拥有kernel/resource decision，Row range与Chunk morsel共享同一parallel
lifecycle。[扩展治理](../conformance/v1-vectorized-physical-pipeline-expansion-governance.md)进一步正式
准入encoded-native integral count/sum/predicate与ordered `long[]` representation-native sequential/
parallel materialization，并保持single final Physical decision、complete pre-work admission与existing
optimized fallback。两次晋升均没有新增public API、Canonical node、production artifact或dependency，
也没有把其他primitive array、callback/stateful、GroupBy或Join能力提前解释为已实现。

## 8. 下游入口

- [Implementation Plan](../engineering/v1-implementation-plan.md)
- [Canonical IR 与执行引擎实施计划](../engineering/canonical-ir-execution-engine-implementation-plan.md)
- [Conformance](../conformance/README.md)
- [Final Pre-implementation Global Consistency Review](../conformance/v1-final-pre-implementation-global-consistency-review.md)
