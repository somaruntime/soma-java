# SOMA Access Model 与 Pipeline Stage 1 决策

类型：Temporary

状态：Stage 1 冻结；待 Stage 2 明确实施授权与正式 Owner 固化

Owner：SOMA Java Access Model / Candidate Scan 专题治理

事实范围：本专题对产品模型、API、命名、lifecycle、stats、callback、表示、migration 与 identity 的唯一候选裁决

非事实范围：当前已经实现的能力、正式 Design、Stage 2 已获授权或性能收益

输入：[Access Model](access-model.md)、[API 覆盖矩阵](access-api-coverage.md)、[Stage 1 Evidence](stage-1-evidence.md)

最后审查日期：2026-07-22

## 1. 决策权边界

本文件关闭 Stage 1 内部待决项，使后续实现只有一套目标，不再保留平行 shortlist。它仍是 Temporary：只有在用户授权 Stage 2、实现与 Gate 完成、正式 Owner 原子固化后，才能成为产品事实。

若 Stage 2 evidence 否定某个性能表示，可以在不降低 Access Model/API 目标的前提下调整 internal representation；若要改变 public shape、cardinality、lifecycle 或 callback 语义，必须停止并重开对应决策。

## 2. 产品与 Access 决策

| ID | 决策 | 结论 |
|---|---|---|
| `AM-DEC-01` | 产品 umbrella | 接受 **SOMA Access Model**；Operation/Candidate Pipeline 不是 umbrella |
| `AM-DEC-02` | Packed source canonical API | Table 本身是 Packed CandidateSource；删除 `rows()`，保留 Table stage/terminal 作为唯一 packed 入口 |
| `AM-DEC-03` | secondary unique | 接受 point-shaped family；另提供显式 `scanByX` Candidate bridge |
| `AM-DEC-04` | first/best-one | 接受 scalar current-Index terminal `findIndex()/requireIndex()` |
| `AM-DEC-05` | snapshot gather | 保持 `IndexSnapshot + ColumnView` 显式组合；拒绝 generic gather facade |
| `AM-DEC-06` | direct/bulk/ownership | 保持 Access Model 独立路径，不进入 Candidate planner |

### 2.1 Packed Table 是 source

目标调用形态：

```java
state.filter(candidate -> candidate.indicatorReady())
    .update(candidate -> candidate.setProcessed(true));

state.forEach(candidate -> consume(candidate.idValue()));

int first = state.sorted(BY_PRIORITY).requireIndex();
```

`rows()` 删除且不替换为 `scan()`。理由：

- Table 已经是自然、typed、schema-specific Packed source；
- 保留 `scan()` 会再次形成 Table shortcut 与 source factory 两套 canonical 入口；
- zero-stage Table terminal 可以直接 specialized，不必先分配空 plan handle；
- Table stage 方法直接创建以 Packed 为 source 的首个 active Scan，不再先建空 source handle。

这里的 Table stage/terminal 不再定义为 convenience forwarding，而是 Packed CandidateAccess 的 canonical API。`*Scan` 只在 exact source或已有至少一个 stage 时出现。

### 2.2 Exact group 与 unique

`@SomaIndex(name="by_machine")`：

```java
MachineCandidateScan scanByMachine(MachineId machineId);
```

`@SomaUnique(name="by_job_sequence")`：

```text
containsByJobSequence(...)
findIndexByJobSequence(...)       // missing -> -1
requireIndexByJobSequence(...)    // missing -> typed failure
findByJobSequence(...[, budget])  // Optional<Carrier>
fetchByJobSequence(...[, budget]) // required Carrier
mutateByJobSequence(...)
deleteByJobSequence(...)
scanByJobSequence(...)            // explicit CandidateAccess bridge
```

Point family 是 `@SomaUnique` 的 canonical access；`scanByX` 只在调用者确实需要 filter/skip/limit/sort、candidate update/remove 或 snapshot 时使用。两者不是重复语义：一个立即执行 point operation，一个建立 lazy CandidateAccess。

`@SomaIndex` 不生成 point-shaped materialization API，因为其 cardinality 是 `0..M`。

### 2.3 scalar Index terminal

Table 和 Scan 均提供：

```text
findIndex()     -> missing 为 -1
requireIndex()  -> missing 为 typed failure
```

它们返回最终 candidate sequence 的第一项 current Index。未排序时只基于当前 source sequence；显式 sort 后为该 comparator 下的 first/best-one。返回值遵循既有 caller-responsibility，任何 mutation/lifecycle 后失效。

不使用 `OptionalInt`，因为 present value 会产生不必要的 result object；`-1` 只在 locate API 中作为 documented missing result，不进入 schema absence/value-state。

## 3. 核心术语与 generated mapping

| Stage 0 ID | 决策 |
|---|---|
| `OP-DEC-01` | Access Model 为 umbrella；multi-field lazy family 正式候选名为 **Candidate Scan** / `*Scan` |
| `OP-DEC-02` | live logical combination 为 table element；materialized declaration 为 schema carrier；callback borrow 为 Cursor/UpdateCursor |
| `OP-DEC-03` | 接受下表 clean replacement；不保留 alias |
| `OP-DEC-04` | Candidate Scan、Key Traversal、Column Traversal 只共享语义 vocabulary/lifecycle原则；实现与 callback specialization 分离 |
| `OP-DEC-05` | 接受一次 breaking cutover、migration map 与 protocol/version 变更 |
| `OP-DEC-06` | Key/Column facade 改为 one-shot Traversal；不增加对称 stage |

### 3.1 public/generated replacement

| 当前 | 目标 |
|---|---|
| `<Type>Rows` | `<Type>Scan` |
| `<Type>Row` callback interface | `<Type>Cursor` |
| `<Type>MutableRow` callback interface | `<Type>UpdateCursor` |
| `rows()` | 删除；Table 是 Packed source |
| `findByX(...)` for `@SomaIndex` | `scanByX(...)` |
| `findByX(...)` for `@SomaUnique` | point materialization；Candidate bridge 为 `scanByX(...)` |
| `rowIndexes()` | `indexSnapshot()` |
| `findRowIndex(key)` | `findIndex(key)` |
| `rowIndexOf(key)` | `requireIndex(key)` |
| parameter/local public name `rowIndex` | `index` |
| `<Type>Keys` | `<Type>KeyTraversal` |
| typed `*ColumnPipeline` | typed `*ColumnTraversal` |
| internal/formal `RowSpace` | `PackedIndexSpace` |

保留：Table、Column、Key、Index、IndexBuffer、IndexSnapshot、Batch、ColumnView、Mutator、RuntimePlan、TableStats。`Row` 不做词频清零：user schema class、application domain type、materialized row/row count 等确实表达 record/table count 的位置可保留。

### 3.2 callback API

```java
public interface MachineCandidateCursor {
    long processingMinutes();
}

public interface MachineCandidateUpdateCursor
        extends MachineCandidateCursor {
    void setSetupMinutes(long value);
}

public interface Predicate {
    boolean test(MachineCandidateCursor candidate);
}
```

Direct `MachineCandidateMutator` 保留：它是 caller-owned one-shot point mutation builder；`UpdateCursor` 是 candidate update callback 中的 staged borrow，两者不能合并。

### 3.3 Traversal family

```text
table.keys()        -> <Type>KeyTraversal
table.fieldValues() -> Int/Long/...ColumnTraversal
```

Key/Column Traversal 只有 terminal，不增加 filter/sort/match/reduction。每个 Traversal one-shot；成功或失败开始 terminal 后 consumed，重复 terminal 返回 typed `traversal_consumed`。这使其明确是同步 operation，而不是可长期缓存的 live Collection/View。

`ColumnView` 保持 scoped、可多次 indexed read、必须 close 的 live borrow，不进入 Traversal lifecycle。

## 4. API 完整性决策

### 4.1 保持的 Candidate operations

```text
Stage:
  filter | skip | limit | sorted

Probe:
  count | anyMatch | noneMatch

Borrow:
  forEach

Current Index:
  findIndex | requireIndex

Detach/materialize:
  indexSnapshot | findFirst | firstOrThrow | fetchAll

Mutation:
  update | remove
```

`findFirst/firstOrThrow/fetchAll` 保持 detached schema carrier 语义；`findIndex/requireIndex/indexSnapshot` 明确为 current Index 语义。方法名和返回类型足以区分成本，不再用 `rowIndexes` 混淆。

### 4.2 拒绝的新能力

- `allMatch`：拒绝；`noneMatch(negatedPredicate)` 已能表达，尚无独立语义/场景收益；
- Column reductions：拒绝；没有足够场景，且数值/absence/overflow/NaN 语义不能批量猜测；
- public `top(k)`/`minBy`：拒绝；`sorted(...).find/requireIndex` 覆盖 best-one；`k>1` 没有证据；
- generic `map/flatMap/reduce/collect/peek/findAny`：拒绝；偏离 typed columnar access；
- automatic snapshot gather：拒绝；会隐藏 currentness、列触碰和 allocation；
- range/maintained-order API：拒绝；保持全量 column filter 与 application-owned heap/tree。

拒绝不是 roadmap。若未来出现新 Blueprint 需求，重新走 capability admission。

## 5. Stats 与 callback 裁决

| Stage 0 ID | 决策 |
|---|---|
| `RP-DEC-01` | `scanned/matched` 采用 semantic reference counts；physical shortcut 不改变 public stats |
| `RP-DEC-02` | Predicate/terminal callback 顺序受约束；Comparator 次数/配对算法相关但不可完全静默删除 semantic Sort |

### 5.1 logical stats

- `scanned`：reference evaluator 在 source sequence 中拉取的 candidate 数，计至 semantic short-circuit；
- `matched`：到达 terminal 的 candidate 数；short-circuit terminal 只计实际到达/测试部分；
- `changed`：成功 publish 的实际变更/删除数；failure 不伪造提交；
- source-only count shortcut 可以直接返回 cardinality，但报告与 reference traversal 相同的 `scanned=matched=N/G`；
- `limit(0)` 仍执行 lifecycle/source binding，candidate count 为零；
- physical probe、loop、comparison 和 allocation 不扩展为新的 public TableStats 字段，由 benchmark/diagnostic evidence 观察。

相邻 pure Skip/Limit 可以 overflow-safe normalize；individual append validation、最终 sequence、logical stats 和 lifecycle必须不变。

### 5.2 callback observability

- 每个 Filter predicate 对每个到达该 stage 的 candidate 至多一次，按 stage 声明顺序；
- terminal predicate 对最终 sequence 按顺序调用，直到 match short-circuit；
- Consumer/Updater 对每个最终 candidate 恰好一次；
- Comparator 必须 pure、non-reentrant、无外部 side-effect 依赖；pair/order/count 由 stable full sort 或 stable arg-min 算法决定，不是跨版本常量；
- 当 Sort 的输入 cardinality ≥2 且 terminal 语义经过该 Sort 时，不为 count 等结果便利而完全删除 Sort，避免静默吞掉全部 comparator failure；
- input 0/1 不调用 comparator是自然语义；stable arg-min 可以少于 full sort 的比较次数；
- 任何实际调用的 callback failure 保留 typed attribution/cause，mutation不发布 partial facts。

## 6. 物理表示决策

| Stage 0 ID | 决策 |
|---|---|
| `RP-DEC-03` | exact source 使用 generated typed source-plan subtype；删除 per-call anonymous Source wrapper |
| `RP-DEC-04` | inline capacity 3；overflow 使用一组 kind/callback/primitive-argument arrays，不保留五组 parallel arrays |
| `RP-DEC-05` | 只接受 internal stable arg-min（k=1）；拒绝 k>1 top-k specialization |
| `RP-DEC-08` | caller-held plan bytes 不进入 RuntimePlan/TableStats；以 allocation/code-size benchmark治理 |

### 6.1 plan/handle

- 每条 Scan 拥有一个 generated schema-specific plan owner；
- public Scan handle 只持 plan reference 与 generation token；
- successful intermediate append 先完成 validation/allocation，再 publish 新 generation；旧 handle立即 stale/consumed；
- allocation/validation failure 发生在 publish 前时，旧 handle仍可使用；
- Table Packed zero-stage terminal不创建 plan；Table 首 stage直接创建含该 stage 的 plan；
- exact/unique bridge 在 source 调用时验证并展开 canonical typed leaf；plan 只保存执行 lookup 所需的 primitive/reference leaf，group/size/epoch 在 terminal-time绑定；
- 每条独立 Scan 拥有独立 plan，不使用 Table-global 或 ThreadLocal mutable plan。

### 6.2 stage storage

- 前 3 个 stage 使用 plan-local inline kind/callback/long argument slots；
- 第 4 个 stage 才进入 overflow；overflow 只使用 `byte[] kinds`、`Object[] callbacks`、`long[] arguments`；
- Predicate/Comparator 按 kind typed cast，不把 candidate values或primitive count装箱；
- 不分配独立 `seen[]`；terminal consumed 后允许 executor 用 primitive locals或原位 remaining count执行 Skip/Limit；
- overflow 必须覆盖至少 4、5、16-stage oracle；不设置 3-stage public 限制；
- terminal `finally` 清除 callback、reference selector leaf和table strong reference；plan 不保存可避免的 selector wrapper，避免 consumed plan 长期保留 application graph。

### 6.3 source/terminal specialization

- Packed source、ExactGroup、ExactUnique bridge由 generated typed plan specialization表达；
- exact source直接遍历 maintained group，不先填全表 candidate list；
- no-sort Probe/Borrow 可 streaming；
- `findIndex/requireIndex` 在无 sort 时 short-circuit，在单 Sort + compatible tail 时使用 stable arg-min `O(M)`；
- snapshot显式复制，materialization显式创建对象，update/remove先冻结 candidate；
- k>1 继续使用 stable full sort，直到新 evidence 重新准入 top-k。

### 6.4 Column access diagnostics

JFR 已证明 `AbstractColumnPipeline` 每次构造会拼接 operation/callback String。Stage 2 必须由 generator传入预绑定 String literal：

- ColumnTraversal 不在构造时做 `field + suffix`；
- ColumnView 的 field-operation cache 同样由 generated literals 取代，不保留跨 schema 动态 cache；
- 不创建 metadata descriptor/interpreter；typed factory仍静态绑定 column class；
- operation label 保持 bounded、deterministic，不包含 payload或本地路径。

## 7. Identity、migration 与 diagnostics

### 7.1 identity

| Identity | Stage 2 目标 |
|---|---|
| schema annotation/model | 不变 |
| schema JSON/hash | 不变；generated rename不进入 schema fact/hash |
| compiler lowering identity | 不变：`soma-value-javac8-v1` |
| generated protocol | `soma-generated-runtime-v3` → `v4` |
| runtime compatibility | `soma-runtime-java8-v3` → `v4` |
| runtime plan protocol/hash inputs | protocol与输入集合不变：`soma-runtime-plan-v3`；compatibility字段值升级到v4，因此最终plan hash必须确定性变化 |
| dense algorithm | 不变：`dense-soa-v1` |
| exact/key strategy | 不变 |
| materialization estimator | 不变 |
| Maven development version | breaking cutover目标 `0.2.0-SNAPSHOT`；不构成发布工作或 readiness |

若实际 Stage 2 发现 schema artifact bytes因 generator name误入 normalized hash而变化，必须停止并修正 ownership；不能用更新 expected hash 掩盖不应发生的 schema contract变化。

### 7.2 clean migration

- 不保留 `*Rows/*Scan`、`rows()/scan`、`findBy/scanBy` 双 public API；
- 不提供 runtime adapter或deprecated alias；当前未对外发布，consumer统一重新生成；
- 更新 generated manifest、`javap` golden、collision fixture、external Maven consumers与四场景；
- 提供一张 mechanical migration map，不写长期兼容层；
- checker 对正式 current owner 中的旧 canonical generated token做受控残留检查，历史 Report/superseded 文档除外。

### 7.3 diagnostics

- Candidate operation label `rows.*` → `scan.*`；
- `pipeline_consumed` error code保留，它仍准确表达 Candidate Pipeline lifecycle；
- escaped callback diagnostic 使用 `escaped_cursor` / `escaped_update_cursor`；
- Key/Column one-shot新增 `traversal_consumed`；
- stats字段中确实表示 table record count的 `rows` 保留；
- error/operation rename属于 migration evidence，不静默视为 internal text。

## 8. Resource 与 retained accounting

`RP-DEC-08` 的理由：Scan plan由caller创建、短生命周期、terminal后清空引用，不是Table retained storage；把它计入 TableStats 会产生所有权错误且需要追踪任意 caller reference。

因此：

- RuntimePlan继续约束 table storage、IndexBuffer/sort/update scratch、materialization和ownership；
- TableStats继续报告 table-owned current/high-water bytes；
- Scan plan/handle通过 ThreadMXBean allocation、JFR type/stack和code-size Gate衡量；
- callback capture属于application allocation，component lane使用static callback隔离；
- OOME仍是fatal JVM allocation failure，不伪装为可恢复 resource rejection。

## 9. 决策关闭表

| ID | 处置 |
|---|---|
| `OP-DEC-01..06` | 全部接受并在本文件给出唯一答案 |
| `RP-DEC-01..04` | 全部接受并给出stats/callback/source/inline设计 |
| `RP-DEC-05` | k=1 arg-min接受；k>1拒绝 |
| `RP-DEC-06` | `allMatch`拒绝 |
| `RP-DEC-07` | Column reductions拒绝 |
| `RP-DEC-08` | 拒绝进入 RuntimePlan/TableStats；接受benchmark治理 |
| `AM-DEC-01..06` | 全部关闭 |

Stage 1 已无阻塞详细实现设计的产品/语义待决项。剩余动作是把这些裁决投影到 Pipeline IR、Stage 2 slice 与验证 Gate；是否开始实施仍由用户另行授权。
