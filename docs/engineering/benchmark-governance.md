# Benchmark 治理

类型：Engineering

状态：正式

Owner：SOMA Java benchmark 过程

事实范围：benchmark lane、环境、artifact、对照和 claim 审批规则

非事实范围：性能设计目标和某次测量数值

最后审查日期：2026-07-23

## 1. Lane 设计

每个 lane 必须绑定一个 Access Pattern Card 和一个可验证问题，例如 exact-group lookup、frontier update/remove、dense scan、dynamic sort、child locality 或 materialization。

Lane 需要明确：setup、warmup、measurement、fork、dataset、stats mode、thread/ownership model、output checksum，以及哪些 allocation/GC 属于被测 operation。

## 2. 对照

需要设计结论时，使用同语义对照：

- full scan vs exact source；
- rebuild workspace vs incremental frontier；
- dynamic sort/best-one vs application heap；public top-k 未准入时不作为产品路径；
- flat table vs parent-owned child；
- keyed lookup vs受控 dense preprojection；
- Candidate Scan vs scalar Index/IndexSnapshot vs ColumnTraversal/ColumnView/primitive path；
- hot path vs materialization/DTO export。

对照必须保持相同结果、tie-break、failure 和生命周期，不能通过减少语义换取数字。

## 3. Artifact 与指标

结构化 artifact 至少记录 identity/environment/method、throughput或latency、allocation/op、Young/Full GC、scanned/matched/changed、retained/high-water storage/scratch、locator/index metrics 和 checksum。

Runner/validator schema 是 evidence compatibility surface；字段变更需要版本化和 parser validation。

Candidate Scan 治理还必须分开报告 Packed/exact source、stage count/overflow、terminal shape、snapshot/materialization、plan/handle allocation与table-retained scratch，并用 source/class bytes、nested class count和clean javac时间约束generated code膨胀。Integrated A/B使用多个独立JVM fork；单进程重复measurement不能单独证明收益。

## 4. Claim

- smoke 只证明 lane 可执行、artifact 合法和基本 invariant；
- local diagnostic 只支持该环境；
- Gate 说明明确阈值在指定环境/规模下通过；
- 跨机器、跨 JDK 或 production claim 需要对应矩阵；
- 单次最好结果、无 warmup/fork、无 correctness guard 或混入 setup 的结果不得进入正式性能声明。

性能退化可以触发调查；是否改变 Design 由相关 Design Owner 决定，不由 benchmark 自动决定。
