# Result Delivery 与 Materialization 边界设计

类型：Design

状态：正式

Owner：SOMA Result Delivery 与 detached materialization semantics

设计层次：`D2` 能力设计

主要关注点：Eager/callback Result Delivery、Detached object graph、预算、递归投影与导出边界

上位设计：[系统架构](system-architecture.md)

服务蓝图：[SOMA Java 产品蓝图](../blueprint/soma-java-product-blueprint.md)

事实范围：Result Delivery modes、materialized object graph、预算、递归 ownership
traversal、allocation admission 和导出边界

非事实范围：external DTO/wire format、application serializer 和 benchmark 结果

最后审查日期：2026-07-28

## 1. 定位

Materialization 把当前 table facts 投影为 detached Java object graph，用于可读性、边界输出、测试 reference path 和 application adapter。它不是 live storage、zero-copy view 或 hot-loop 默认访问方式。

Transformation 的 detached columnar result 是另一种显式 output boundary：它保存 typed/primitive heap arrays、presence 和 shape identity，不构造 per-element object graph。其 Shape 与 output budget 由 [Transformation Model](transformation-model.md)拥有；本 Owner 只拥有 schema object graph materialization。

```text
SOMA live table
  -> detached @SomaTable / @SomaValue / List / Map graph
  -> application DTO mapping
  -> wire / file / UI
```

这三个阶段必须分开计量和归责。

## 1.1 Result Delivery modes

V1 只有两种 terminal delivery：

| mode | semantics |
|---|---|
| Eager Detached | 默认；完整构造、publish-once；返回后不持有 source guard；failure/cancel/rejection 不暴露 partial result |
| Callback Scoped | 显式 opt-in 的 synchronous one-shot read-only visitor；terminal 返回前关闭 guard/scratch/lease |

Callback Scoped 是唯一 Lazy Output，不是新的 execution model。它复用
[DataFlow 执行模型](dataflow-execution-model.md)的
Definition→Template→Invocation lifecycle，允许 bounded early stop，但不减少 logical
cardinality/source work，也不授予 mutation/Effect/transaction/partial publication。
普通 Iterator、closeable pull cursor、Generator、Publisher、async push 和
terminal-returned live result 均不支持。

## 2. 结果语义

- 单 row materialization 返回对应 detached `@SomaTable` shape；
- `@SomaValue` 按 compiler-defined immutable value semantics 构造；
- optional absent 保持 absence，不使用 payload sentinel；
- `List` child 按当前 materialization traversal order 形成 detached list；
- `Map` child 按 logical child key 形成 detached map；
- 递归结果不保存 live cursor、view、Index、child handle 或 runtime backing array；
- materialized graph 后续修改不得改变 table，table 后续 mutation 也不得改变既有 graph。

物理遍历顺序不是业务顺序。需要稳定导出时，调用方必须先使用显式 total order 或 exporter 自己排序。

## 3. Budget

每次递归 materialization 必须使用显式 `MaterializationBudget` 或 runtime plan 的确定默认值。预算至少约束：

- table instance count；
- total rows；
- leaf value count；
- maximum ownership depth；
- deterministic estimated allocation bytes。

预算 identity 和 estimator identity 进入 diagnostics。Estimated bytes 是版本化、确定的 admission estimate，不宣称等于 JVM profiler/object-layout 实测值。

Detached columnar/callback delivery 也必须在 Invocation budget 中约束 output
elements/bytes、shared/worker scratch 和 tasks。Known high-expansion output 先 checked
add/multiply；无法由 compiler/plan/validated maintained facts 证明 finite bound 时，
除独立有界 scalar/fused terminal 外 fail closed。Callback/early stop 不能替代这一
preflight。

## 4. All-or-nothing

Materialization 先遍历/估算并检查 ownership、lifecycle 和 budget，再发布结果。Expected failure 时不返回部分 object graph，也不改变 live table、epoch、view 或 child state。

受控 allocation provider 可以在明确 phase 拒绝预计分配，并产生 typed resource failure。Raw `OutOfMemoryError` 原样传播；不能包装成可恢复 allocation failure。

## 5. Reentrancy 与安全

- materialization 是 aggregate-level active operation，期间禁止 mutation 和同 aggregate 重入；
- ownership traversal 必须检测 dangling、wrong-owner、cycle 和 excessive depth；
- error context 使用 logical path，不渲染任意 row/key payload；
- materializer 不调用 application callback、serializer 或 arbitrary `toString()`；
- materialization failure 不 release 或改变 source table。

## 6. 使用准则

Hot loop 优先使用 Candidate Scan、typed Cursor、ColumnView 或 schema-specific primitive path。Materialization 适合：

- 单次 point fetch 的可读 reference path；
- solve/simulation/action boundary 的 snapshot；
- external DTO adapter 的输入；
- correctness oracle 和测试内容比较。

大量递归 export、深 child forest 或高频 per-row materialization 必须单独建模和 benchmark，不能混入 storage hot-path 声明。

Callback delivery 只在能实际降低 retained output 且业务不需要 complete atomic result
时选择。需要完整 result 后才做业务判断、排序/barrier输出、Effect或跨系统发布时，
继续使用 Eager Detached。
