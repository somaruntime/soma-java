# SOMA Physical Execution Engine M2 有限可行性与Profile验证

状态：`PASS / DESIGN_EVIDENCE_ONLY / NOT_PRODUCTION_QUALIFICATION`

日期：2026-08-12

基线：`develop@d106484161d70f8d3f5f344fe06c61308c1bf400`

## 1. 验证问题

本阶段不实现候选架构，只回答两个问题：

1. stateless typed pipeline能否由一个Physical decision、Chunk-aware kernel、ResourceEstimate、Frame和
   Morsel lifecycle完整解释，并保持Reference等价？
2. stateful operation是否存在清晰的streaming segment与breaker边界，且其性能热点和资源Owner符合
   候选模型？

验证故意只取两个vertical slice，不用更多benchmark堆叠替代架构判断。

## 2. 环境与命令

环境：

- Amazon Corretto `1.8.0_502` arm64；
- Maven `3.9.16`；
- 当前macOS arm64 fixed host；
- benchmark：1,000,000 rows，parallelism 16；
- `-Xms2g -Xmx8g -XX:+UseParallelGC`；
- SOMA managed memory budget 6 GiB；
- one fresh-JVM run、1 inner warmup、3 timed samples；
- JFR profile；
- benchmark产物位于untracked `target/benchmark/m2-design-validation/`，不作为source交付。

Targeted differential：

```sh
mvn -pl soma-runtime \
  -Dtest=GeneratedTableTest#vectorChunkKernelsMatchReferenceAcrossPlainEncodedAndTypedFilters+groupAndRelationOptimizersMatchIndependentReferenceAlgorithms \
  test
```

结果：`2 tests / 0 failure / 0 error`。

Group/Relation broader consumer：

```sh
./build-support/qualification/group-relation.sh
```

结果：runtime `86 tests`、processor `34 tests`以及Java 8 generated consumer/negative/javap链全部`PASS`。

Profile workload：

```sh
SOMA_BENCHMARK_WORKLOAD=frontier \
SOMA_BENCHMARK_SCENARIOS='frontier-source frontier-relation' \
SOMA_BENCHMARK_IMPLEMENTATIONS=soma-auto \
SOMA_BENCHMARK_ROWS=1000000 \
SOMA_BENCHMARK_RUNS=1 \
SOMA_BENCHMARK_PARALLELISM=16 \
SOMA_BENCHMARK_INNER_WARMUPS=1 \
SOMA_BENCHMARK_INNER_SAMPLES=3 \
SOMA_BENCHMARK_PROFILER=jfr \
SOMA_BENCHMARK_OUTPUT_ROOT=target/benchmark/m2-design-validation \
./scripts/benchmark.sh
```

结果：`2 records / 2 groups / all correctness and fingerprint PASS`。

## 3. Stateless验证

### 3.1 纵向切片

```text
Table scan
    -> optional pure typed integral predicate
        -> schema-known integral Field projection
            -> integral sum OR ordered long[] materialization
```

current executable fact已经满足候选模型：

- final decision由Row PhysicalPlan中的finite vector decision拥有；
- handler按PLAIN、encoded/RLE、overlay能力选择；
- sum使用typed accumulator；
- materialization按Chunk count/prefix/disjoint write保持order；
- parallel使用Chunk ordinal Morsel并复用shared scheduler；
- output/partial/scratch在work前进入ResourceEstimate与lease；
- unsupported shape回到existing optimized primitive path；
- Reference独立执行同一Bound semantics。

### 3.2 正确性与representation

Targeted test对Compression OFF/AUTO、PLAIN/encoded、typed predicate、sum与ordered materialization执行
Reference/optimized exact comparison，全部通过。既有VP1/VP2证据继续拥有RLE/mixed/overlay、parallel、
overflow与resource边界；本阶段不重复宣称它们是新资格。

### 3.3 1M fixed-host结果

| Operation | Sequential median | Parallel median | Interpretation |
|---|---:|---:|---|
| Field sum | 0.303 ms | 0.131 ms | no-predicate Chunk kernel与Morsel partial成立 |
| Field ordered materialization | 0.358 ms | 0.172 ms | schema-known output、Chunk partition与disjoint write成立 |
| Table typed filter -> primitive sum | 3.459 ms | 0.457 ms | typed filter/projection/aggregate可作为一个fused Segment |
| typed filter -> ordered `long[]` | 6.732 ms | 4.426 ms | predicate count/prefix/write的bounded materialization成立 |

这些数字是本机单次architecture validation，不是回归baseline或SLA。它们只证明该物理模型解释了当前
高性能路径，并为未来实施后的同机A/B提供环境快照。

### 3.4 Profile归因

JFR source profile的SOMA top execution samples集中在：

- `CanonicalPrimitiveVectorKernel.countChunk`；
- typed predicate的PLAIN/encoded handler；
- `sumEncoded`与typed accumulator；
- unsupported mapped/callback路径仍进入Primitive/Row fallback。

这支持两个判断：

1. Physical Segment与representation-specific Kernel是有现实性能含义的边界；
2. M2必须保留specialized kernel，不能用通用boxed operator loop换取表面统一。

## 4. Stateful验证

### 4.1 纵向切片

候选切片：

```text
Table scan
    -> optional typed filter Segment
        -> key/value projection
            -> GroupBy hash aggregation Breaker
                -> typed grouped result
```

Java 8 I5 consumer已直接覆盖：

```java
events.filter(events.enabled.eq(true))
    .groupBy(events.machineId)
    .sum(events.duration);
```

并验证first-encounter group order和aggregate结果。Runtime targeted differential对optimized与independent
Reference GroupBy结果exact比较；broader group-relation qualification全部通过。

### 4.2 Current physical mapping

当前GroupBy执行是：

- upstream Row Canonical/Bound/Normalized/PhysicalPlan；
- `CanonicalRowExecution.visit`流式产生locator；
- family-local visitor计算key并写入`GroupState`；
- `GroupState`拥有hash buckets、representatives、aggregate与floating sequence；
- finish后materialize typed key/value result。

这已经是“streaming Segment + aggregation Breaker”，但Breaker没有进入完整PhysicalPlan，scratch通过
family-local extra estimate附加。这正是M2需要统一的架构缺口。

### 4.3 1M fixed-host结果

现有frontier-relation harness没有把typed filter与GroupBy组合成单独timed metric；本次不为了设计验证
扩张benchmark surface。它测量同一个GroupBy breaker在低/高cardinality与parallel模式下的正常路径：

| Operation | Sequential median | Parallel median | Interpretation |
|---|---:|---:|---|
| low-cardinality GroupBy | 16.749 ms | 16.363 ms | hash breaker主导，当前parallel只并行upstream，收益有限 |
| high-cardinality GroupBy | 49.724 ms | 52.320 ms | breaker state/growth与key processing主导；并行前缀不足以覆盖serial breaker |

这不是“应立即并行GroupState”的结论。它证明M2必须把Breaker state与merge strategy纳入PhysicalPlan，
以后才能依据证据决定partitioned partial aggregation；不能只扩大upstream Row partitions。

### 4.4 Profile归因

JFR relation profile中，GroupBy execution samples明确落在：

- `CanonicalRowExecution.visitSource/visitStreaming`；
- `CanonicalGroupingQueryOperation`的visitor边界；
- `GroupState.groupFor`；
- `GroupState.add`；
- grouped result materialization。

Profile同时显示Join热点仍在equality/index lookup与pair emission；这支持将Join视为独立binary
build/probe breaker/operator，而不是把全部Relation逻辑塞进unary Segment。

## 5. Resource验证

两项slice均在data/callback work前完成conservative resource admission：

- stateless vector decision把partials、counts、prefix与result ownership一次进入PhysicalPlan estimate；
- GroupBy在Row physical estimate之外追加保守group state scratch；
- Group/Relation qualification覆盖resource/failure与generated consumer；
- benchmark fingerprint全部通过、major fault为0；
- JFR各有2次young GC，未出现allocation-requiring-GC事件。

注意：这些事实不证明current GroupBy estimate已经是最紧，也不授权dynamic lease。M2目标是让估算从
explicit Breaker state/liveness推导，并继续保持admission-before-work。

## 6. 可行性结论

| 问题 | 结论 |
|---|---|
| 统一Physical Pipeline语言能否解释current code | PASS |
| Segment/Breaker是否具有真实性能与资源边界 | PASS |
| specialized Kernel能否保留而不形成第二executor | PASS |
| Morsel能否复用current scheduler | PASS |
| Reference能否继续独立 | PASS |
| GroupBy是否值得作为首个stateful slice | PASS |
| 是否需要general DAG/VectorBatch/runtime codegen | NO EVIDENCE / REJECT |
| 是否具备进入正式晋升审查的证据 | YES |

## 7. Claim boundary

本验证没有证明：

- M2 production实现已经完成；
- GroupBy/Join已经获得新的并行算法；
- 所有operation已进入Chunk-at-a-time执行；
- 当前结果可作为跨硬件SLA；
- release或publication已获授权。

它只证明冻结候选架构与current semantics/code/performance相容，并给实施计划提供了两个有真实证据的
纵向入口。
