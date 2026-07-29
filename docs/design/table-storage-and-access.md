# Table、存储与访问设计

类型：Design

状态：正式

Owner：SOMA table storage 与 access semantics

设计层次：`D2` 能力设计

主要关注点：Table kind、packed storage、identity、exact access structure 与 relocation

上位设计：[系统架构](system-architecture.md)

服务蓝图：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

事实范围：table kind、packed storage、primary/exact structures、swap-remove、IndexBuffer 和 mutation storage shape

非事实范围：ownership lifecycle、公开 IndexSnapshot 消费契约、error envelope、materialization 和具体 hash/sort 实现类

最后审查日期：2026-07-29

本 Owner 先定义 Table、identity 与 access 的能力语义，再展开 packed relocation、exact structure 和 candidate scratch 等机制约束。具体 hash/sort 类、数组字段和生成方法是当前实现事实，不在此维护。

## 1. Table kind

SOMA 只有两种 public table kind：

| Kind | Identity | 典型访问 |
|---|---|---|
| keyed table | `@SomaKey` 定义稳定 logical identity | key fetch/mutate/delete、exact access、scan |
| dense table | 无 stable row identity | packed scan、current Index、exact access、workspace replace/update |

Root/child 是 ownership 维度，不是第三种 table kind。Input/working/result 是 application data role，也不改变 table kind。

## 2. Packed columnar storage

每张 live table 必须满足：

```text
live Index = [0, size)
每个 leaf field -> 同长度 typed column
optional field -> presence bitmap + payload/handle column
```

Primitive、enum、date/time、semantic scalar 和对应 `@SomaValue` leaf 使用
primitive columns；String V1 使用 typed reference columns；child field 只保存
opaque handle，不保存 live Java Collection 或 row object。完整四类 type semantics
由 [Schema 与生成 API](schema-and-generated-api.md)拥有。

Capacity growth 必须先 stage 所有相关 column/bitmap/locator storage，再一次 publish。失败时旧 size、capacity、epoch 和 live values 保持一致。

### 2.1 受限 layout 与 Segment publication

一个 logical Table 可以按 Effective Plan 绑定：

- `FLAT`：Small/Medium、point-heavy 或安全 contiguous maximum；
- `FLAT_HEAD_SEGMENTED_TAIL`：Large scan/growth，保留 flat head，并以 fixed-size
  tail Segments扩展。

Application 只声明 planning rows、hard maximum、workload profile 与 resource
bounds，不设置 Segment 魔数。Versioned formula 选择 layout/segment rows并进入
Effective Metadata/Explain；超过 hard maximum 在 allocation 前拒绝。

Segment 是 storage/growth/GC publication unit。同一个 ordinal 的所有 payload、
presence、row-link 和 child-handle columns 必须先 private stage，全部 allocation/
budget admission 成功后一次发布 directory/capacity。失败不改变 visible capacity、
size、epoch 或 access state。Scan 使用 segment-aware outer loop；point access 使用
stable row-to-segment formula。`SomaSegmentMetadata` 只描述 topology，不暴露 backing
array；retained/high-water/lifecycle 属于 Table Observation。

## 3. Identity 与 exact access

### 3.1 Primary identity

Keyed table 使用 hash-based primary locator 完成 `key -> current Index`。Locator
只保存 current row locator 与 compact fingerprint，不复制 full key；hash/fingerprint
命中后仍回查 authoritative columns 的完整 value equality。Dense table不创建
primary locator。

V1 使用 `soma-primary-locator-layout-v1` 公式把无主定位器的 Table 解析为
`NONE`，把任意已支持主定位器解析为 `FLAT_COMPACT`。该公式、结果与 locator
identity进入 Effective Metadata 和 Plan identity；runtime create 必须 fail closed
验证。紧凑平面定位器是当前唯一生产实现，不能因 Table storage segmented 就自动
跟随分段。

`BOUNDED_SEGMENTED` 只保留为未来 physical candidate：只有在
production point/collision/rehash/growth evidence 证明收益且无语义退化后，才能
通过新的 formula identity 和 compatibility cutover 纳入；当前 V1 Metadata 枚举和
公式不提供该值，避免未经验证的实现成为隐含承诺。

### 3.2 Secondary exact access

- `@SomaUnique`：一个 exact value 对应零或一个 row；insert/update 冲突在提交前拒绝；
- `@SomaIndex`：一个 exact value 对应零个、一个或多个 current Index；
- exact structure 在 append、update、remove、replace 和 compaction boundary eager/incremental 维护；
- read path 不允许以 dirty 标记触发全表 rebuild/sort，也不允许静默 scan fallback；
- bucket/group 只保存访问结构，不成为业务事实；row move 后必须同步 relocate links。

V1 不提供 range lookup。用户可以用列式全量 filter 实现范围条件。V1 也不提供 maintained order；跨操作持久顺序由 application 专用结构表达。

Floating key/exact value 必须使用稳定 canonicalization：拒绝 non-finite access value，并将 `-0.0` 与 `+0.0` 归一到同一 identity。普通 floating payload 的业务有效性仍由 application 定义。

Required String leaf 可以参与 Primary、Unique 和 Exact Index；optional selector
component 非法。String hash 只定位候选，最终以 `String.equals` 回查；order 使用
`compareTo` 定义的 value order。Equal-value different-object mutation 是 no-op，
不更新 locator/index/epoch；remove/clear/replace/rollback/release 清除 dead
reference。

低基数 Bitmap 不是新的 Schema annotation 或第二份 authoritative index。只有
多个单字段 primitive `@SomaIndex` 具备 equality-intersection 消费形态时，
generated exact substrate 才可由 `soma-candidate-physical-v2` 在 link 与 bitmap
布局间确定性选择。Bitmap 必须同步维护 append/update/remove/packed relocation/
clear/release，保留 group hash/full-equality 回查，并把 retained/high-water 纳入
Table ledger；不适用时 Exact links 是 canonical fallback。

## 4. Delete 与 compaction

Keyed 和 dense table 均使用 swap-remove/tail-fill：

1. 识别待删除 Index；
2. 从末尾选择未删除 survivor 填补前部 hole；
3. 同步移动所有 columns、presence、child handle；
4. 修复 primary locator 和全部 exact structures；
5. 清理 tail 并提交 size/epoch。

删除后不保证物理遍历顺序。未显式排序的 `firstOrThrow()`、`limit(n)`、`fetchAll()` 或等价 terminal 只基于执行时的当前物理顺序；业务不能把它当成稳定顺序。

Multi-row remove 使用当前候选 Index 的 primitive scratch，不分配或长期保留 `boolean[size]` mark，也不留下 tombstone row/hole。

## 5. IndexBuffer 与 Candidate execution

`IndexBuffer` 是 table-local、可复用、primitive `int[]` scratch，但只是一种
Candidate physical shape，不是 universal representation。Candidate 还可以绑定
Range、SegmentRange、maintained Exact single-pass cursor、Bitmap 或 SparseIndexes；
精确选择由 Access/DataFlow cost model 拥有。

需要排序、stable random access、复用或 mutation freeze 的 operation 可以使用
`IndexBuffer`。它只保存当前 operation 的候选 Index，不保存 row object：

```text
Packed/exact source     -> L1
filter(L1)              -> L2 in place
sorted(L2)              -> L3
terminal(L3)            -> result/update/remove/materialization
```

每个 stage 只处理上一个 stage 的候选；exact source 可以被 scalar/single-pass
terminal 直接消费，不先生成 full-table 或 group-sized Index；dynamic sort 只排序
当前候选。Terminal 结束或失败后 buffer reset 供下一 operation 复用，retained
capacity 受 runtime plan 和 memory budget 约束。

Candidate Scan 的组合、one-shot、terminal 与执行约束由 [Access Model 与 Candidate Scan](access-model-and-candidate-scan.md)拥有。本 Owner 只规定 `IndexBuffer` 不保存 schema object、不暴露给 application，且任何 executor 都必须保持 packed/exact structures 与 current Index 一致。

`IndexSnapshot` terminal 可以复制当前候选 Index，但其公开消费契约由 [Schema 与生成 API](schema-and-generated-api.md)唯一拥有；本 Owner 只规定它不复用或暴露内部 `IndexBuffer`。

## 6. Mutation boundary

- `Batch` 是 typed import/append/replace staging boundary，不是 live storage；
- `RowKey` 不原地修改；identity change 使用 delete + insert。unique、index 和 child 相关更新必须先完成全部 validation/preflight；
- remove 只作用于当前 candidate set；
- `clear()` 释放 live rows但可以复用已准入 capacity；`release()` 进入 terminal lifecycle；
- success result 记录实际 scanned、matched、changed/removed；failure 不伪造已提交 changed；
- comparator/filter/update callback 不能逃逸 Cursor、嵌套访问同一 aggregate 或执行未声明的结构变更。

## 7. 顺序与确定性

物理顺序不是业务契约。需要确定性结果时，caller 提供覆盖全部 tie-break 的 total comparator；一次排序不改变 table 的 canonical physical order，也不建立长期索引。Priority queue、event queue 和 maintained leaderboard 由 application-owned data structure 实现。
