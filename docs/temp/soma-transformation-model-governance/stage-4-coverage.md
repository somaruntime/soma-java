# Stage 4 冻结范围覆盖审计

类型：Temporary

状态：Stage 4 COMPLETE；Stage 5 evidence pending

Owner：SOMA Transformation Model governance

事实范围：Stage 4 实施覆盖、构造契约和专项 evidence

非事实范围：正式产品能力、正式 Design 和最终性能声明

最后审查日期：2026-07-27

实施候选：`190f90f`

本文件只服务当前专题退出审计，不改变冻结设计。Stage 6 将把稳定事实固化到正式
Owner 后删除本文件。

## 1. 闭合矩阵

| 责任 | 当前 evidence |
|---|---|
| Foundation | 独立 `soma-dataflow`、Definition/Template/Invocation、v5 generated binding、Context/Policy/Budget、lifecycle/identity |
| Candidate linear algebra | packed/exact source，lazy filter/skip/limit/stable sort/top-k，count/match/current Index snapshot/best-one |
| Primitive Projection/Aggregation | long/double/boolean/object detached column，sum/average/min/max，integral Prefix Scan |
| Multi-shape baseline | predicate Partition + ordered Combine、四种 left-driven equi Join、GroupBy、count/time Window、owned-child Expand |
| Effect/Handoff | candidate update/remove、typed keyed Delta、expected epoch、ordered Insert/Update/Delete、single aggregate safe-point |
| Parallel | managed/borrowed executor，contiguous pure packed kernels，fixed-tree long reduction，stable projection，parallel freeze + deterministic commit，floating/opaque/order fallback |
| Access/Result | Point/current Index/key/unique、IndexSnapshot gather parameter、Candidate borrow/materialize、primitive/object borrowed traversal |
| Shape closure | key Partition、Group having/order、Joined select/stable sort/top-k/group/window、outer absence-aware projection、Window select/aggregate |
| Expression | invocation Parameter、primitive/composite Key、registered long function/reducer、opaque fence、expression dependency/parallel traits |
| Finite graph | one-shot controlled Builder、typed multi-result、shared pure terminal once、single terminal Effect、independent safe branches deterministic parallel |
| Diagnostics | OFF/BASIC/DETAILED stats、bound explain、redacted parameter/budget、failure phase、scratch/output accounting |
| Construction contract | length-prefixed identity、source/parameter/output/effect collision Owner、one-shot Builder/Invocation、shared parallel budget、managed executor leak prevention |

专项 evidence：`check-dataflow-slice-a.sh` 至
`check-dataflow-slice-f.sh`；当前最新综合专项 Gate 为
`check-dataflow-slice-f.sh`，覆盖 Foundation 与 A–F 全部代表性契约。

## 2. Stage 4 退出审计

| Slice | 冻结责任 | 当前缺口 |
|---|---|---|
| E Access/Result | Point、Column、Key、IndexSnapshot gather、Probe/Borrow/Materialize | CLOSED |
| F Shape Closure | Projected/Partitioned/Grouped/Joined/Windowed 的合法组合与低物化 terminal | CLOSED |
| G Expression | Parameter、registered typed function/reducer、opaque fence | CLOSED |
| H Graph | controlled `DataFlowDefinition.Builder`、finite DAG、shared pure branch、fan-out/fan-in | CLOSED |
| I Diagnostics | bound specialization explain、stats policy、resource/failure detail | CLOSED |
| J Evidence | reference oracle、golden/external consumer、footprint/component/application trace | STAGE 5 OWNER |

Stage 4 没有改变 annotation Schema、RuntimePlan v3、Index/ownership/lifecycle、
单 Table failure atomicity 或现有 direct Access。generated/runtime v5 与
transformation/kernel v1 保持唯一协议；没有 v4 adapter、generic object executor、
Java Stream hot path、隐式 common pool或第二 memory backend。

## 3. Stage 5 防循环规则

同一失败只允许“首次发现 + 一次根因修复 + 一次验证”。若再次出现，停止运行并
检查抽象、生成契约或 fixture 设计，不反复调参或扩大 Gate。

Stage 5 只补 reference/property differential、generated/golden/external consumer、
footprint、component/application performance 与证据支持的优化；不得借性能实现
改变本文件已闭合的语义。
