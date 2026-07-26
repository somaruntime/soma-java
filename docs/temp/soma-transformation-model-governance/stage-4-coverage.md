# Stage 4 冻结范围覆盖审计

状态：实施中  
审计基线：`82628ec`

本文件只服务当前专题退出审计，不改变冻结设计。Stage 6 将把稳定事实固化到正式
Owner 后删除本文件。

## 1. 已闭合

| 责任 | 当前 evidence |
|---|---|
| Foundation | 独立 `soma-dataflow`、Definition/Template/Invocation、v5 generated binding、Context/Policy/Budget、lifecycle/identity |
| Candidate linear algebra | packed/exact source，lazy filter/skip/limit/stable sort/top-k，count/match/current Index snapshot/best-one |
| Primitive Projection/Aggregation | long/double/boolean/object detached column，sum/average/min/max，integral Prefix Scan |
| Multi-shape baseline | predicate Partition + ordered Combine、四种 left-driven equi Join、GroupBy、count/time Window、owned-child Expand |
| Effect/Handoff | candidate update/remove、typed keyed Delta、expected epoch、ordered Insert/Update/Delete、single aggregate safe-point |
| Parallel | managed/borrowed executor，contiguous pure packed kernels，fixed-tree long reduction，stable projection，parallel freeze + deterministic commit，floating/opaque/order fallback |

专项 evidence：`check-dataflow-slice-a.sh` 至
`check-dataflow-slice-d.sh`；当前最新综合专项 Gate 为
`check-dataflow-slice-d.sh`。

## 2. Stage 4 退出前必须闭合

| Slice | 冻结责任 | 当前缺口 |
|---|---|---|
| E Access/Result | Point、Column、Key、IndexSnapshot gather、Probe/Borrow/Materialize | direct Access 保留，但 DataFlow 中 Point、gather、Candidate borrow/materialize 尚未闭合 |
| F Shape Closure | Projected/Partitioned/Grouped/Joined/Windowed 的合法组合与低物化 terminal | 当前只实现各 Shape 的基础 terminal，尚未形成冻结矩阵要求的组合闭包 |
| G Expression | Parameter、registered typed function/reducer、opaque fence | generated expression 已有；Parameter 和 registered contract 尚缺 |
| H Graph | controlled `DataFlowDefinition.Builder`、finite DAG、shared pure branch、fan-out/fan-in | 当前 Definition 仍是单 terminal tree，没有 reusable multi-result DAG |
| I Diagnostics | bound specialization explain、stats policy、resource/failure detail | BASIC stats 已有；模式与 bound physical decision 尚缺 |
| J Evidence | reference oracle、golden/external consumer、footprint/component/application trace | A–D 有定向 fixture；完整 replacement/evidence matrix 尚未执行 |

## 3. 实施顺序与防循环规则

1. E：先补 canonical access/result，不改变现有 direct Access；
2. F：以 Shape/lineage 为边界补组合闭包，不建立万能 object executor；
3. G–H：Parameter/function/reducer 先闭合 contract，再接入 controlled DAG；
4. I–J：最后完成 diagnostics、oracle、footprint 和 replacement closure。

同一失败只允许“首次发现 + 一次根因修复 + 一次验证”。若再次出现，停止运行并
检查抽象、生成契约或 fixture 设计，不反复调参或扩大 Gate。
