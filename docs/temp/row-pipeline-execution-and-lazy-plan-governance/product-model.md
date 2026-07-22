# SOMA Access Model 产品模型

类型：Temporary

状态：Stage 1 产品候选已由 `fd82eba` 实现；待正式固化

Owner：SOMA Java Access Model 产品模型

事实范围：目标使用者心智模型、Access family、Candidate DSL、产品边界与能力准入

非事实范围：具体 compact layout、正式产品承诺

输入：[Access Model](access-model.md)、[Stage 1 决策](stage-1-decisions.md)

最后审查日期：2026-07-23

## 1. 产品定位

SOMA 是 Java 8 annotation schema + generated columnar runtime。用户通过 schema-specific Table 保存 packed runtime facts，并按访问意图选择 Point、Candidate、Column、Key、Bulk 或 Ownership 路径。

SOMA 借鉴 Java Stream 的惰性 stage、one-shot、short-circuit 和 terminal specialization，但不把整个产品变成 Stream。只有 Candidate Scan 是 lazy Pipeline；其他访问路径保持更直接的 shape。

## 2. 使用者心智模型

```text
SomaTable
  ├─ PointAccess       已知 Index/Key/Unique，立即定位一项
  ├─ CandidateAccess   从 Packed/Exact source 逐步筛选、排序并终结
  ├─ ColumnAccess      遍历或按 current Index 读取一个 typed leaf
  ├─ KeyAccess         遍历或物化 stable logical keys
  ├─ BulkAccess        Batch append/replace/clear
  └─ Ownership         定位 child Table 或替换 owned child facts
```

用户首先选择正确的 access family，而不是把所有操作都写成一条万能链。

## 3. Candidate Scan

CandidateAccess 的语法是：

```text
CandidateSource Stage* CandidateTerminal
```

- Packed source 由 Table 本身表达；
- `@SomaIndex` exact group 由 `scanByX(...)` 表达；
- `@SomaUnique` 默认走 point API，需要组合时显式 `scanByX(...)`；
- child 先取得 owner-scoped child Table，再使用其 Packed/Exact source。

Stage：filter、skip、limit、sorted。Terminal：probe、borrow、current Index、snapshot、materialize、update、remove。

一次 Scan 在 terminal 开始时绑定 current source、同步执行并 consumed。Intermediate 不执行候选、不复制 Index sequence；它只构造受控 semantic plan。

## 4. 目标使用体验

### 4.1 exact group + readable materialization

```java
MachineCandidate chosen = frontier.scanByMachine(machineId)
    .filter(candidate -> candidate.indicatorReady())
    .sorted(byFcfsThenSpt)
    .firstOrThrow();
```

`chosen` 是 detached schema carrier，不是 live element 或 cursor。

### 4.2 exact group + hot current Index

```java
int chosen = frontier.scanByMachine(machineId)
    .filter(candidate -> candidate.indicatorReady())
    .sorted(byFcfsThenSpt)
    .requireIndex();

try (LongColumnView processing = frontier.processingMinutesColumn()) {
    consume(processing.getLong(chosen));
}
```

这条路径不先创建 `IndexSnapshot`。`chosen` 只在当前同步只读批次有效，任何 mutation 后必须丢弃。

### 4.3 Packed candidate mutation

```java
state.filter(candidate -> candidate.expired())
    .remove();

state.update(candidate -> candidate.setDirty(false));
```

Table 是 Packed source，因此不需要 `rows()` 或 `scan()` 前缀。`update/remove` 仍先冻结最终 candidate，保证 failure atomicity。

### 4.4 secondary unique point

```java
int operation = definitions.requireIndexByJobSequence(jobId, sequenceNo);
OperationDefinition detached = definitions.fetchByJobSequence(
    jobId, sequenceNo);
```

`@SomaUnique` 的 `0..1` cardinality直接体现在 API；只有需要 stage 时才调用 `scanByJobSequence(...)`。

### 4.5 Column 与 Key

```java
state.valueValues().forEachDouble(value -> consume(value));
definitions.keys().forEach(key -> consume(key));
```

它们是 one-shot Traversal，不支持 Candidate stage。需要多列 filter/sort 时使用 Table/Candidate Scan；需要 indexed sparse read 时使用 ColumnView。

## 5. Access family 边界

| Family | Source/result shape | 可组合能力 | 不应获得 |
|---|---|---|---|
| Point | `0..1` current Index/materialization/mutator | fixed terminal family | filter/sort chain |
| Candidate Scan | current Index sequence | filter/skip/limit/sort + terminals | map/join/generic collect |
| Column Traversal | typed leaf sequence | typed forEach terminal | cross-column predicate |
| ColumnView | scoped indexed leaf read | repeated get within pin | detached snapshot语义 |
| Key Traversal | logical key sequence | borrow/materialize terminal | element mutation |
| Batch/Bulk | detached staged values | append/replace/clear | Candidate stage |
| Child | owner-scoped Table | child自己的Access Model | join/share/reparent |

API 不追求各 family 方法数量对称。能力完整是“每个 Access Pattern 有自然路径”，不是“每个类型都有 filter/sort/reduce”。

## 6. Product invariants

1. Packed SoA 是 live storage；schema carrier只在materialization/import边界存在。
2. Key是stable identity；Index和IndexSnapshot不是。
3. Candidate stage只处理上一candidate sequence；exact source不扩回全表。
4. 未显式sort的first/limit/snapshot只基于当前source sequence。
5. Candidate Scan、KeyTraversal和ColumnTraversal one-shot；ColumnView按close/pin lifecycle。
6. callback cursor不逃逸，不允许同aggregate reentrancy。
7. snapshot、materialization和mutation是不同terminal成本/正确性边界。
8. point、bulk、ownership不绕进Pipeline planner。
9. optimizer不破坏full equality、stable tie、typed failure、stats或mutation atomicity。
10. generated API保持Java 8、schema-specific、typed，不暴露runtime bucket/IR/backing array。

## 7. 能力准入

新增 Access Pattern、Stage 或 Terminal 前必须回答：

- 它不能由现有 Access algebra 自然表达的原因；
- source/cardinality/sequence/validity；
- callback、empty、absence、failure和resource语义；
- snapshot/materialization/mutation边界；
- typed/no-boxing实现；
- generated naming/collision/identity；
- oracle、component benchmark和真实Blueprint需求。

Java Stream、数据库或Arrow中存在同名能力，不构成准入理由。

## 8. 本专题不扩张的能力

- 不增加allMatch、generic reduction、map/flatMap/collect；
- 不增加public top-k、range lookup、maintained order；
- 不增加automatic gather、stable Index handle或generic query object；
- 不把application heap/event queue/transaction放入SOMA；
- 不为Key/Column补齐对称Candidate stage。

## 9. Stage 1 baseline / Stage 2 candidate 边界

Stage 1 baseline 使用`*Rows/*Row/*MutableRow/rows()/findByX/rowIndexes/*ColumnPipeline`。
`fd82eba` 已实现本文的`*Scan/*Cursor/scanBy/indexSnapshot/*Traversal`候选；它是当前
executable candidate，但在正式 Owner 固化前仍不是正式文档支持声明。

目标已通过 external consumer 与完整 Gate；只有在 S2.7 固化到正式 Blueprint/Design
后，才能替换正式文档事实。

## 10. 产品完成条件

- FJSP、VRP、Simulation、Game可以只靠本模型解释全部SOMA访问；
- 用户能从返回shape判断cardinality、validity和allocation边界；
- 每个重要Pattern有唯一canonical path；
- 当前Index、borrow、snapshot、materialization和identity不混称；
- implementation可由Access Pattern成本推导，而不是让数组结构反向定义API；
- 新能力可以通过准入规则被接受或拒绝，不靠API对称性扩张。
