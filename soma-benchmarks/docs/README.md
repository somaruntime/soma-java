# soma-benchmarks 文档入口

性能目标由[性能模型](../../docs/design/performance-model.md)拥有，benchmark 方法由[Benchmark 治理](../../docs/engineering/benchmark-governance.md)拥有；当前 runner、validator、lane 和 artifact 从[参考应用与 benchmark Map](../../docs/implementation-map/scenario-and-benchmark-map.md)进入。

当前测量和 Gate evidence 位于[根报告](../../reports/README.md)；历史 module
benchmark 报告由 Git 保存。

当前 component runner、artifact validator、baseline definition/comparator 和
三个 baseline Owner 的实现入口见
[参考应用与 benchmark Map](../../docs/implementation-map/scenario-and-benchmark-map.md)；
baseline 方法、环境适用性和更新纪律见
[Benchmark 治理](../../docs/engineering/benchmark-governance.md)。

历史契约由 Git 保存，不在 current checkout 维持平行 Design 或链接 tombstone。
