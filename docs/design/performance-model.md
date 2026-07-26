# Runtime 性能模型

类型：Design

状态：正式

Owner：SOMA 跨模块性能设计

设计层次：`Q` 横切质量

主要关注点：Hot-path 成本模型、优化约束与性能证据

上位设计：[设计宪法](soma-java-design-constitution.md)

服务蓝图：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

事实范围：性能北极星、成本分解、hot-path 约束、优化决策和 evidence 义务

非事实范围：某次 benchmark 数值、机器支持声明和永久 Gate 阈值

最后审查日期：2026-07-27

## 1. 北极星

SOMA 的性能目标是让目标 runtime-state access pattern 由有效数据工作量主导，而不是由临时对象、装箱、反射、全表 hidden rebuild、过度 indirection 或 boundary materialization 主导。

性能不是独立于正确性的排名。任何优化必须保持 schema、identity、ownership、determinism、failure、compatibility 和 public API 语义。

## 2. Access Pattern Card

性能判断前必须描述：

- table role、kind、ownership 与 lifecycle；
- row/cardinality、capacity 和 growth pattern；
- hot columns、touched bytes 和 working-set size；
- scan/exact lookup/update/remove/sort/materialize 比例；
- exact group size/selectivity、key distribution/load/collision；
- child instance count 与 per-child cardinality；
- stats mode、snapshot/export frequency 和 allocation boundary。

没有 Access Pattern 的“换一种数据结构会更快”不构成设计结论。

完整 Access Pattern Catalog、组合合法性和基础成本模型由 [Access Model 与 Candidate Scan](access-model-and-candidate-scan.md)拥有。本 Owner 负责跨模块机械约束与 evidence 义务。

## 3. 成本分解

一次 operation 至少区分：

```text
source cost
  + scanned/matched candidate work
  + callback/comparator work
  + sort/top-k scratch
  + locator/exact-index maintenance
  + compaction/column movement
  + growth/transient allocation
  + retained storage/scratch
  + stats overhead
  + optional materialization/export
```

DataFlow 还需区分 author/build、compile、bind、execute、barrier、parallel task/merge、effect preflight/commit 和 detached output。Definition/Template 的一次性成本不能混进 steady-state Invocation，也不能从报告中隐藏。

Benchmark 必须避免把 setup、input build、external DTO mapping 或 JVM warmup 混进待测 hot path，除非目标就是 end-to-end。

## 4. 永久机械约束

- live storage 为 packed SoA；
- hot leaf access 使用 concrete typed columns/static binding；
- steady-state path 不创建 per-row object、tuple、iterator、lambda capture graph 或 boxed key；
- Candidate Scan 使用 compact typed plan、fused traversal 与可复用 candidate scratch，stage 不复制完整 candidate arrays；
- exact selector 从 group 直接产生候选，读取不重建全表；
- delete 不创建 `boolean[size]` mark；
- capacity growth 在明确 boundary stage/publish；
- diagnostics 默认低干扰，详细模式显式启用；
- schema object/DTO/Collection graph/metadata interpreter/Stream 不进入 canonical hot storage/path。
- 每张 Table 最多一个 generated DataFlow companion；shared operator/kernel 不按 `operator × Table` 展开；
- graph hot path 不创建 per-element generic node/tuple/result object；large result allocation 必须归属于显式 detached output；
- adaptive parallel 只在稳定证据支持的 cardinality/kernel 上启用，小规模、opaque callback、order-sensitive reduction 和 memory-bound lane 确定性回退。

## 5. 关键路径设计

### 5.1 Scan 与 filter

连续 scan 应按实际 touched columns 衡量，不把全部 row width 当作固定成本。Filter 只遍历当前候选 Index，并尽可能在一个 primitive buffer 内压缩。

Packed zero-stage 与 exact source-only terminal 应有直接执行路径。短 stage 链不得为每个 stage 建立 linked node 或复制完整 stage arrays；overflow 表示也必须保持 primitive kind/argument 与 callback reference 分离。

### 5.2 Exact lookup

Exact access 的 read cost 与命中 group 相关；write cost显式承担 hash probe、group/link delta 和 compaction relocation。必须同时测 lookup throughput 与 mutation maintenance，不能只展示单边收益。

### 5.3 Dynamic sort

排序只作用于当前候选集。若 terminal 只需要 first，可以在保持 stable first-on-equal、callback/failure 和 logical stats 的前提下采用 arg-min；本 Design 不因此准入 public top-k。跨轮次 order 由 application heap/tree 等专用结构承担。

### 5.4 Child locality

Child table 避免 flat global scan，但会增加 registry、handle、小数组 header 和 over-reservation。Evidence 必须覆盖 empty/singleton/small/high cardinality 以及 instance-count × row-count 组合。

### 5.5 Transformation 与 parallel

Selection/Projection 尽量 fuse；GroupBy、Join、Sort、Window、Prefix Scan 和 fan-out 只在语义需要时建立 barrier。`skip/limit/top-k/best-one` 应把可证明的 candidate/output 上界下推到 scratch admission，不能先复制完整 source 再截断。

Sequential 是唯一语义基准。Parallel 使用 deterministic partition 和 fixed logical-order merge；只在不改变 floating/order/failure/Effect identity 时准入。Managed executor 由 Context 复用，borrowed executor 不由 SOMA 关闭，默认不隐式使用 common pool。

## 6. Runtime plan 的性能约束

Capacity、memory limit、stats mode、locator/index load 策略、materialization budget 和 estimator identity 由 create-time immutable runtime plan 预绑定。读取 hot path 不解析动态 metadata；plan 变化产生不同 plan hash，不能静默改变既有 table。Plan/Stats 的规范性语义由 [Runtime Plan 与可观测性](runtime-plan-and-observability.md)拥有，本节只拥有其性能约束。

## 7. Evidence 义务

性能结论至少记录实际 JDK、OS、architecture、commit、命令、warmup/measurement、fork、dataset、stats mode，并报告：

- throughput/latency 与 sample count；
- allocated bytes/op；
- Young/Full GC count、time 和 pause；
- current/high-water retained storage 与 scratch；
- scanned/matched/changed；
- locator/index probes、collisions、rehashes；
- output checksum 或等价 correctness guard。

Transformation component 还需分别覆盖 direct Access 对照、Definition/Template/Invocation 固定税、operator barrier、parallel crossover、detached output、safe-point Effect 和 generated footprint。性能测试不能替代 reference differential、构造契约或 failure evidence。

单机 diagnostic 只支持对应环境的结论。阈值和 Gate 由 Engineering 拥有；测量结果由 Report 拥有。
