# SOMA Logical IR 后续治理意图

状态：`QUEUED_FUTURE_TOPIC / INTENT_ONLY / NOT_ACTIVE / NOT_DESIGN`

日期：2026-08-10

Owner：下下次 Logical IR 治理的意图、问题边界与前序治理约束

> 本文只保存未来治理意图，不是当前 Logical IR Design、JSON/DAG admission、implementation plan
> 或 Conformance。前序
> [32位结构域与即时增量 Key/Index 维护资格](../../conformance/v1-incremental-structural-mutation-governance.md)
> 已经闭合；只有 Product Owner 正式启动后，
> 本文才能转化为新的 bounded governance topic。

## 1. 治理意图

SOMA 当前首先由 Java schema annotation、processor 和 generated typed API 驱动。未来 Product
Owner 可能增加新的 frontend：

```text
JSON
    -> schema binding
        -> Logical IR
            -> optimizer
                -> execution DAG
                    -> SOMA execution engine
```

长期目标不是把当前 Java object graph 直接序列化成 JSON，而是建立：

> 一个 frontend-independent、typed、immutable、data-oriented Canonical Logical IR。Java
> generated API、未来 versioned JSON model 和其他 DSL 都是 frontend；它们完成各自的 validation
> 和 schema binding 后，lower 到同一套 Canonical IR，再共同复用 optimizer、reference semantics
> 和 execution engine。

未来目标形态：

```text
Java Generated API ───────┐
                          │
Versioned JSON Model ─────┼──> Validate / Bind / Lower
                          │              -> Canonical Typed Logical IR
Future DSL / UI ──────────┘                   -> Optimizer
                                                -> Physical Execution DAG
                                                    -> Execution Engine
                                                        -> Storage / Key / Index
```

## 2. 为什么需要独立治理

Logical IR 是 Java API、optimizer、reference interpreter、execution engine 和未来 frontend 的共同
语义边界。若它直接拥有 generated Java object、runtime binding、physical locator、Index class 或
application callback 的执行职责，则未来 JSON/DAG 只能以 wrapper、adapter 或第二套 IR 叠加，形成
平行语义和长期补丁。

反过来，如果为了 JSON 提前把所有 internal node 变成 public DTO，也会把内部类名、字段布局、
optimizer phase 和 physical mechanism 冻结为兼容性债务。

因此该问题必须独立治理，不能作为 Key/Index mutation 实施中的顺手重构。

## 3. 当前初步只读观察

以下只用于确定未来审查方向，不构成完整 audit 或 finding：

- `PredicateIr` 当前是 immutable、data-only predicate tree，predicate evaluation 已由独立 evaluator
  承担；这是正确方向；
- `PredicateIr` 仍使用 `fieldIndex` 和 `GeneratedProbe` 表达 Field/literal，未来需要审查它们是
  canonical identity，还是 Java/runtime-specific carrier；
- `LogicalRowPlan` 当前是 immutable linked plan，但直接持有 `GeneratedTable`、`GeneratedProbe`、
  `GeneratedRelation`、`GeneratedOrder` 和 Java callback object；
- 当前 row plan 主要表达线性 parent/stage pipeline，而未来 JSON workflow 可能需要显式 node ID、
  multi-input edge、fan-out 与 shared subexpression；
- callback filter/order 已被明确视为 optimizer barrier，但 portable typed IR 与 host-only callback
  capability 尚未形成独立的 frontend portability contract；
- normalized/physical plan 已区分 `KEY_LOOKUP` / `INDEX_LOOKUP`，未来需要确保这些是 physical
  access-path choice，而不是 JSON/Canonical Logical IR 必须指定的存储机制。

这些观察说明当前基础并非推倒重来：typed predicate、fixed optimization phases、reference
interpreter 和 physical plan separation 已存在。未来治理应保留正确部分，收敛 frontend/runtime
耦合，而不是复制第二套 IR。

## 4. 下下次治理的核心问题

### 4.1 Canonical IR 的身份

需要裁决 Canonical node 如何表达：

- composition/schema identity；
- Table identity；
- Field logical path 或 stable field ID；
- operator kind；
- typed literal；
- input node identity；
- output element shape；
- dependency set、nullability、lineage、order 与 cardinality；
- query-only / mutation capability；
- portable / host-bound capability。

Canonical IR 不应以 generated Java facade instance identity 作为唯一语义身份；runtime Group/Table/
StateRoot binding 应保持 terminal-start lifecycle。

### 4.2 Data-only 与执行责任

Canonical logical node 应描述“做什么”，不能拥有：

- `execute()` / `matches()` 等执行实现；
- worker、cursor、locator 或 scratch；
- bound `StateRoot`；
- physical Key/Index/Chunk implementation；
- resource lease；
- application callback invocation lifecycle。

Reference interpreter、optimizer、physical planner 和 execution engine 分别消费同一个 logical
semantics，但不反向污染 Canonical IR。

### 4.3 Java callback capability

Java frontend 可以继续支持 opaque callback：

```java
.filter(view -> applicationCheck(view))
.map(view -> applicationMapping(view))
```

但 callback 无法安全表达为 portable JSON operator。推荐在同一 IR 中区分：

```text
Portable typed node
    可来自 Java / JSON / future DSL
    可被分析、优化和稳定诊断

Host-bound callback node
    仅由 Java frontend 构造
    持有受控 host handle
    是 optimization / serialization barrier
```

这不是两套 IR。未来 JSON frontend 只准入 portable capability subset；不得分析 lambda bytecode，
不得允许 JSON 指定任意 Java class、method 或 callback name。

### 4.4 线性 pipeline 与 DAG

线性 Java pipeline 是 logical DAG 的简单特例。未来治理需要裁决：

- stable node ID；
- zero/one/multiple input edges；
- fan-out；
- shared subexpression；
- cycle detection；
- materialization boundary；
- deterministic output identity；
- common-subplan 是否只共享 logical node，还是允许 execution reuse/cache。

不得因为“未来 DAG”提前引入不具 consumer 的 cache、scheduler service 或 public graph SPI。

### 4.5 Query DAG、Workflow DAG 与 Physical DAG

三者必须分责：

```text
Logical Query DAG
    filter / map / join / group / sort / aggregate 的数据语义

Workflow DAG
    多个 query、materialization、受控 mutation 与外部步骤的依赖关系

Physical Execution DAG
    operator、partition、task、worker、scratch 和 merge
```

未来 JSON 能力建议先从 query-only DAG 开始。跨 Table mutation、external I/O、retry、exactly-once、
compensation、workflow persistence 和 crash recovery 不因“支持 DAG”自动进入 SOMA。

### 4.6 JSON frontend 边界

JSON 应拥有独立、versioned external model：

```text
JSON bytes
    -> bounded parser
    -> version/schema validation
    -> logical name/type resolution
    -> capability validation
    -> lower to Canonical IR
```

不能直接把 internal Java IR class 作为 JSON DTO。未来至少要处理：

- 超过 `2^53` 的 `long` literal，建议使用 typed decimal string；
- Enum、String、null 和 nested Value；
- unknown schema/Table/Field/operator；
- type mismatch；
- node/edge数量、深度、literal总量与解析资源上限；
- duplicate node ID、missing input、cycle；
- structured diagnostic 与 source location；
- JSON schema/version 的 backward/forward compatibility。

### 4.7 Reference 与 optimizer proof

Java frontend 与 JSON frontend lowering 为同一 Canonical IR 后，必须满足：

```text
equivalent Java request
    == equivalent JSON request
        -> same Canonical IR semantics
        -> same reference result/failure
        -> same optimized sequential result/failure
        -> same optimized parallel result/failure
```

内部 node object identity、hash table order 或 JSON property order 不能影响 logical result。

## 5. 对前序 Key/Index mutation 治理的约束

下一轮 mutation 治理不实施 JSON/DAG，但必须避免封死下下次 IR 治理。

### 5.1 Logical IR 不拥有 physical Index

Canonical/logical predicate 只能表达：

```text
Field equality / null / order / relation semantics
```

不能表达：

```text
IdentityHashIndex
Hash Bucket number
posting block/slot
locator flags
cleanup generation
```

Key/Index substitution 属于 optimizer/physical planning。Logical IR 不能要求某个 predicate 必须通过
Index 执行。

### 5.2 Access-path capability 与实现类型分离

Planner 应依据 logical descriptor/capability 选择：

```text
TABLE_SCAN
KEY_LOOKUP
INDEX_LOOKUP
```

但 physical executor 才绑定：

```text
Typed Unique Hash Index
Typed Hash-backed Inverted Index
```

未来替换 probing、posting 或 cleanup mechanism 时，不应要求修改 Java/JSON Logical IR。

### 5.3 延迟失效对 logical semantics 透明

`REMOVED`、`INDEX_DIRTY`、stale membership 和 cleanup 都是 storage/execution concern：

- logical scan 只看到 live Records；
- logical IndexSelection 只看到当前 Field value 匹配的 Records；
- `count()` 返回 logical live count，不返回 physical posting count；
- reference interpreter 基于 authoritative live state裁决；
- optimized Index operator负责 candidate recheck；
- cleanup 前后 Canonical IR result/failure/order 等价。

不得为了适应新的 physical Index，把“过滤 stale locator”注入用户 predicate 或 Java generated API。

### 5.4 Schema identity 不依赖 Java facade 名称

新 Key/Index descriptor 必须保留未来 binder 所需的稳定 composition/Table/Field identity。Generated
Java endpoint 可以携带该身份，但不能成为唯一可构造来源。当前治理只保留 seam，不新增 JSON
public identity 或 serialization contract。

### 5.5 Mutation 与 DAG side effect 隔离

本轮继续保持一次 Table-local mutation 的 atomicity。不得因为未来 workflow DAG 而引入：

- cross-Table transaction；
- asynchronous mutation；
- retry/compensation；
- workflow scheduler；
- persisted plan；
- public physical plan handle。

未来 Workflow Owner 必须显式决定这些责任是否属于 SOMA。

## 6. 下下次治理的建议交付物

1. 当前 Java-generated -> logical -> normalized -> physical 全链 inventory；
2. frontend/runtime coupling finding matrix；
3. Canonical node identity、type、lineage、order、cardinality 与 portability contract；
4. Java frontend lowering design；
5. versioned JSON request model candidate；
6. Query DAG / Workflow DAG / Physical DAG responsibility map；
7. callback host-bound capability contract；
8. schema binder、validation 与 structured diagnostics；
9. reference interpreter 与 differential proof matrix；
10. extreme graph/type/size feasibility fixture；
11. Design Owner promotion matrix；
12. implementation readiness review。

## 7. 非目标

在 Logical IR 治理正式启动前，不授权：

- JSON parser/dependency；
- public JSON API；
- public/internal IR serialization compatibility；
- workflow runtime；
- distributed execution；
- remote execution或跨进程通信；
- persisted query/cache；
- arbitrary Java method invocation；
- cross-Table transaction；
- 第三 production artifact；
- 为未来 capability 创建 placeholder module/interface/service。

## 8. 进入治理的前置条件

Logical IR governance 只有在以下条件成立后才启动：

1. Key/Index mutation governance 已完成正式 Design 晋升、implementation 和 Conformance
   （已满足）；
2. 前序 Temporary 已完成 replacement closure（已满足）；
3. scheduling reference journey 已恢复并验证新的 mutation/Index 行为；
4. Product Owner 明确启动新的 bounded topic；
5. 当前 code、formal Design、Gate 与 worktree baseline 重新核对；
6. JSON/DAG仍被确认为真实产品方向，而非仅凭未来可能性扩张 surface。

## 9. 未来治理的成功标准

未来专题应至少证明：

- Java generated API 是 frontend，不是 Canonical IR Owner；
- Canonical IR typed、immutable、data-only，且不绑定 StateRoot/locator/physical Index；
- Java callback 有明确 host-only boundary；
- linear pipeline 可无歧义表示为 DAG 特例；
- JSON model 与 internal IR 分离并具备版本/资源/诊断合同；
- reference、optimized sequential、parallel 对等价 frontend request 结果一致；
- Key/Index/Storage/Execution replacement 不要求改变 logical semantics；
- 没有第二套平行 IR、reflection fallback 或 public physical runtime model；
- 正式 Blueprint/Design/Engineering/Conformance proof chain闭合。

## 10. 当前交接结论

当前只记录意图和前序约束：

> 前序 Key/Index 与 mutation 治理已保持 Logical IR 对 immediate physical access-path
> replacement 透明；未来必须独立治理 frontend-neutral Canonical Logical IR、versioned
> JSON lowering 与 DAG capability。

未经Product Owner启动，不得顺手实现 JSON/DAG，也不得用“以后再重构 IR”
作为新physical coupling的理由。任何不可逆的 IR coupling 都必须在对应Design审查阶段
暴露并暂停。
