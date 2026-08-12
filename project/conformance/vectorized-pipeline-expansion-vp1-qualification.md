# SOMA Vectorized Physical Pipeline扩展 VP1 资格

类型：Conformance / Implementation Slice Qualification

状态：`PASS / VP1_COMPLETED / VP2_READY / RELEASE_NOT_AUTHORIZED`

Owner：single final Physical decision、encoded-native integral count/sum/predicate及其资源、正确性与性能证据

最后更新：2026-08-12

## 1. 结论

VP1已经按冻结的Temporary Design和Implementation Plan完成。Production现在只有一个final Physical
decision Owner：closed terminal requirement在resource admission前进入Canonical planner，planner一次性决定
access path、finite primitive kernel、representation handler、parallel ownership与全部temporary bytes；
ExecutionFrame只消费该admitted plan，不再执行terminal refinement或追加scratch。

本slice同时准入以下内部执行能力：

- Table scan `count`：无predicate或simple pure typed integral predicate；
- schema-known integral Field `sum`：无predicate或simple pure typed integral row predicate；
- PLAIN direct typed loop；
- integral encoded plain direct loop；
- single-required-leaf RLE run-level count/sum；
- mixed root once-per-Chunk dispatch；
- multi-leaf RLE与overlay/current-value scalar fallback；
- sequential与既有bounded Chunk-morsel parallel exact aggregate。

Public/generated API、Canonical/Reference语义、Java 8、两项production artifact、dependency与release边界均未改变。
VP2 materialization、boolean、floating、callback、mapped/stateful、Index/Group/Relation仍未被本slice准入。

## 2. Replacement closure

本slice删除了完成使命的第二套physical decision机制：

- `PhysicalRefinement`；
- `executeFamilyRefined`；
- `terminalRefined`；
- `withVectorDecision`；
- `withAdditionalTemporaryBytes`。

替代关系为：

```text
CanonicalRowPhysicalRequest
    -> CanonicalRowPlanner.plan(normalized, request)
        -> CanonicalRowPhysicalPlan(vectorDecision + ResourceEstimate)
            -> admitted CanonicalRowExecutionFrame
                -> representation handler
```

`EncodedChunk`只借出package-private、immutable、operation-bounded `IntegralChunkAccess`；它不成为第二份
storage truth，不跨terminal缓存，也不向application surface泄漏。RLE运行时不创建run object、不整体解码，
不同leaf的RLE boundary不建立通用zipper。

## 3. 正确性、资源与失败证据

覆盖的主要合同：

- PLAIN、encoded plain、single-leaf RLE、multi-leaf RLE fallback、mixed root和overlay；
- 无predicate encoded count、all-match、typed predicate、boundary long值与final overflow；
- Reference与optimized的count/sum exact differential；
- RLE `raw × runLength`进入signed-128 accumulator，只有最终long越界才产生
  `ARITHMETIC_OVERFLOW`；
- parallel partial只按Chunk数量分配；final plan删除旧O(rows) locator-prefix scratch；
- tiny-budget在pool submission和data work前以`RESOURCE_LIMIT_EXCEEDED`拒绝；operation结束后temporary归零；
- unavailable pool、interrupt/rejection、unsupported callback/stateful/mapped fallback由既有runtime breadth继续覆盖。

最终Java 8 runtime重放：

```text
mvn -q -pl soma-runtime test
81 tests, 0 failures, 0 errors
```

源码exact-symbol扫描确认上述五个旧refinement symbol均为零命中；`git diff --check`通过。Production/public
generated surface未改变，因此本slice没有需要重新冻结的generated consumer ABI。

冻结计划原要求一次bounded独立只读审查；Product Owner在实施授权中明确要求不使用subagent。本slice遵循该
更高优先级过程约束，改由主Agent在targeted与runtime breadth之后执行一次有边界的code/call-path复核；没有
用多轮审查替代实施或证据。

## 4. 同机性能资格

固定环境：Amazon Corretto `1.8.0_502`、Maven `3.9.16`、Apple M5 Pro、ParallelGC、1M rows、
P=16、`-Xms2g -Xmx8g`、SOMA budget 6 GiB。Baseline为冻结授权commit `f627203`；baseline与candidate
均使用3个fresh JVM，每个JVM 1次warmup和3个sample。以下数值是cross-run median：

| Cell | Baseline | Candidate | 变化 | 判断 |
|---|---:|---:|---:|---|
| AUTO integral Field sum | 3.431 ms | 0.290 ms | -91.5%，约11.8x | PASS |
| AUTO typed filter + projected sum | 7.445 ms | 3.549 ms | -52.3%，约2.10x | PASS |
| AUTO parallel integral sum | 0.299 ms | 0.156 ms | -47.8%，约1.92x | PASS |
| OFF typed filter + projected sum | 2.747 ms | 2.757 ms | +0.4% | no stable regression |
| OFF integral Field sum | 0.366 ms | 0.363 ms | -0.8% | no regression |
| callback fallback | 18.906 ms | 19.115 ms | +1.1% | within noise |
| mapped fallback | 20.038 ms | 19.731 ms | -1.5% | no regression |

AUTO三个admitted cell均越过冻结门槛；PLAIN/OFF和unsupported fallback未出现超过5%的稳定退化。结果只
证明该固定主机与负载下的slice regression，不外推为跨硬件SLA。

## 5. Slice边界与下一步

VP1的功能、结构、资源、性能与恢复点已经闭合，允许进入VP2。VP2只实施冻结范围内的ordered `long[]`
representation-native materialization与bounded parallel count/prefix/write；不得顺手加入其他primitive
array、reference materialization、callback/stateful、sub-Chunk、general run zipper或新scheduler。

GitHub Release/Package、签名、Maven publication和正式release声明仍未授权。
