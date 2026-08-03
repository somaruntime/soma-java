# I1 实施临时记录

状态：ACTIVE；仅记录当前 I1 slice 的实现边界，不改写正式 Blueprint/Design。

## 自主裁决

1. 正式 runtime 已经拥有 io.github.somaruntime.soma.SomaField 注解，因此 generated
   logical Field marker 采用 SomaFieldEndpoint<R,V>；SomaKeyableField<R,V> 继承该
   marker。这样保留 annotation FQN，同时使 generated endpoint 与 annotation 在 Java 8 中
   可共存。
2. SomaExpression<R> 与函数式 SomaPredicate<T> 保持两个不同 Java 类型。typed
   expression 由 internal evaluator node 承载；application callback 不进入 typed IR。
3. I1 只准入一个 composition 内恰好一张、一个 long @SomaKey 与一个 long @SomaField
   的 primitive keyed Table。其他 schema 仍完成 I0 carrier/full-regeneration，但暂不生成
   I1 public Table surface；其 type breadth 归入 I2，不使用 placeholder API。
4. I1 backend 使用两级 paged ChunkDirectory（root page -> directory page -> Chunk）、primitive
   long leaves、独立 occupancy 的 sharded open-addressed long-key index。Point update 同时保留
   candidate-root 与小记录 journaled in-place 两条路径；in-place 路径在 callback 后预分配 descriptor，
   final 阶段只写 bounded payload、CAS StateRoot，CAS 失败回滚 journal。Group guard 与 callback
   owner/thread scope 共同拒绝 overlap、escape 和跨线程 View/Editor 使用。I1 暂不准入 Index
   selection、remove、parallel、metadata、compression、Join、GroupBy 或 materialization API。
5. Generated expression node 携带 generated Table-instance owner token；同一 expression 只能在
   其创建 Table instance 的 Selection 中执行。application 可见的 internal 类型不是 SPI，foreign
   node 在消费时必须得到 structured INVALID_ARGUMENT。Selection 每个 intermediate/terminal
   独立 claim receiver，不能通过共享 consumed flag 延迟到 terminal 才拒绝 branch。
6. `SomaExpression.and/or(SomaRelationExpression)` 使用 relation-level 返回类型，只有同一
   `SomaExpression<R>` 输入保留 covariant typed return；这样为后续 binary relation expression
   保留正确的类型升级路径，I1 只使用 same-row overload。

## 退出条件

本记录在 I1 Conformance evidence 晋升后删除或归档；它不是长期产品语义 Owner。若后续实现
需要改变 public signature、failure/order/null/mutation 语义，必须重新建立 bounded Temporary
并停止当前 slice。
