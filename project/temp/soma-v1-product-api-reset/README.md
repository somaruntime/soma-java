# SOMA V1 产品心智模型与 API 重构

类型：Temporary

状态：active

Owner：SOMA V1 产品与 API 重构专题

事实范围：本专题讨论中的候选心智模型、关键决策、理由、约束和待裁决问题

非事实范围：正式 Blueprint/Design、当前 public/generated API、runtime 实现、
V1 release readiness 和实施授权

最后审查日期：2026-07-30

## 文档角色、专题意图与授权边界

当前 SOMA Java V1 已暴露过多内部执行概念，普通使用路径无法形成简单、稳定的
用户心智模型。本专题逐项讨论并裁决产品心智模型和关键 API 决策。

本文件是专题内唯一候选决策记录，保存“决定了什么、为什么、还缺什么”；它不是
正式 Blueprint/Design。面向 API 评估的当前投影位于
[SOMA Java 逻辑层 API 草稿](logical-api-draft.md)，后者不重新拥有决策，发生
差异时以本文件的最新决策状态为准。

Product Owner 已授权使用本 Temporary 记录讨论。本授权只包含候选设计记录，
不授权修改正式 Blueprint、Design、public/generated API、runtime、测试或
release claim。每项决策必须先在这里说清楚；专题形成完整、可验证的目标模型并
获得实施授权后，才能进入正式 Owner 和实现。

推荐阅读方式：

- Product Owner/维护者先看下面的决策索引，再进入需要复核的 D 编号；
- API reviewer 直接阅读逻辑层草稿的“最短普通路径”和对应详细契约；
- implementation work 在获得独立授权前只把本文作为 candidate input，不能把
  示例当作当前 generated signature。

## 当前决策索引

所有“已确认”都只表示 Product Owner 已在本 Temporary 中确认目标方向，不表示已
promotion、实现、验证或具备 release claim。

| 决策 | 当前结论 | 尚未闭合的主要内容 |
|---|---|---|
| D-001 | 用户语义、编译生成、存储、执行四层职责 | 正式 architecture promotion |
| D-002 | finite single-source Stream-like pipeline；无状态/有状态 intermediate；Query/Update/Remove terminal | 精确 operation/signature |
| D-003 | Transformation Model、DataFlow Model 退出普通用户模型 | 模块与 predecessor replacement |
| D-004 | primitive/reference 两类 flat payload leaf；V1 无 Segment | child physical slot、capacity evidence |
| D-005 | primitive always-present/zero；reference nullable；ordinary Object referent 由 application 管理 | outer `@SomaValue`、Key/Index/Object operation |
| D-006 | 无 public Batch；重复 atomic add；terminal 可有 private staging | import 性能与 partial-progress evidence |
| D-007 | V1 无通用 Table binary relation/join | 三个 reference application 的 unary replacement evidence |
| D-008 | 已由 D-013 supersede，不再拥有当前语义 | 无 |
| D-009 | public Record/Field source；physical Column 自动 lowering | direct-array 性能证明 |
| D-010 | lazy、one-shot、单向 source shape、encounter-order 继承 | Index 默认顺序与精确 projection syntax |
| D-011 | `.stream()`、`_metadata()`、Table direct operation 分离意图；Soma/Group/Table/Field 层级 | metadata/Field 精确 surface |
| D-012 | 每 Group 每 root Table identity 唯一；不同 Group 可各有一个 | identity/duplicate diagnostic |
| D-013 | selection Query/Update/Remove；Update/Remove whole-selection atomic | epoch/currentness 与 failure 细节 |
| D-014 | GC lifecycle；detached add/find/get object boundary；Value immutable/Table carrier mutable | detached update candidate、materialization constructor 与 point result 细节 |
| D-015 | default Group singleton accessor；`Soma.<tableType>()`；Table-nested Record/Editor/Stream | generated naming/collision 与 Java 8 evidence |

## D-001 四层职责模型

决策状态：Product Owner 已确认专题方向；尚未 promotion 为正式 Design

SOMA 区分以下四层职责：

```text
用户语义层
    -> 编译生成层

运行时：
    存储层 <-> 执行层
```

这四层不是一条简单的调用栈。用户语义和编译生成属于 authoring/compile-time
链路；存储与执行属于 runtime，执行层通过窄协议消费存储层，普通 direct access
也可以由 generated facade 直接调用存储层。

由当前 `add(detachedObject)` 讨论进一步得到一条候选边界原则：

```text
application-facing object-oriented semantics
    -> compile-time generated lowering
        -> data-oriented columnar storage/execution
```

“OOP 在外、data-oriented runtime 在内”是合理的主方向，但不能解释成
application 只能使用 object graph，或 SOMA hot path 必须逐行 materialize DTO。
primitive、array、lambda、immutable value 和普通 Java 控制流仍属于自然的
application 代码。

其中 `@SomaValue` 是 immutable Value Object；`@SomaTable` object 是 mutable
detached carrier。只有后者允许通过完整改写字段在同步 operation 之间复用，两者
都不是 SOMA 的 live storage。

### 用户语义层（User Semantic Layer）

用户语义层是 SOMA 唯一面向应用开发者的产品表面，提供：

- 易于理解和使用的 annotation；
- schema-specific、类型安全且符合普通 Java 习惯的 generated API；
- 面向业务数据、Key、Index、查询、更新和结果的语义；
- 隐藏代码生成协议、物理存储、执行计划和调度机制的默认使用路径。

用户只需要表达“数据是什么、怎样定位、希望完成什么操作”，不应为了普通使用而
理解 Batch、Cursor、View、physical Index、RuntimePlan、Definition、Template、
Invocation、Binding、Context、Segment、scheduler 或 resource ledger。

### 编译生成层（Compile-time Generation Layer）

编译生成层读取用户 annotation 和 schema declaration，完成：

- schema validation、normalization 和稳定 identity；
- 将 Key、Index、字段和允许的操作编译为 schema-specific facade；
- 生成用户 API 与存储层、执行层之间的窄桥接代码；
- 把能够在编译期发现的类型、命名和非法操作问题前移；
- 将用户表达降低为存储层或执行层可直接消费的专门化代码或描述。

本层是 lowering boundary，不是第二套用户编程模型。生成产物可以依赖窄的
runtime protocol，但 application source 不应直接依赖或理解该 protocol。

### 存储层（Runtime Storage Layer）

存储层唯一拥有 SOMA runtime state 及其同步派生访问结构，包括：

- Table storage、Key locator、secondary Index 和 mutation consistency；
- flat column、物理 row address 和 capacity growth；
- append、replace、update、remove、clear、compaction 和 lifecycle；
- storage retained/current/high-water 与必要的结构性 observation；
- 为 direct access 和执行层提供窄、类型化、可验证的读取与 mutation protocol。

存储层不拥有 filter/sort/group 等 pipeline 语义，不维护 application rule
graph，也不决定 parallel scheduling。Key/Index 的逻辑含义来自用户语义层，编译
生成层负责投影，存储层只负责正确、高效地维护其物理实现。

### 执行层（Runtime Execution Layer）

执行层拥有一次 operation 怎样从 source 得到结果或受控 Table-local state change，
包括：

- source、intermediate operation、Query/Update/Remove terminal 和 result；
- candidate selection、filter、sort、projection、aggregation；
- mutation candidate freeze、private staging、preflight 和 atomic publish
  coordination；
- physical execution plan、scratch、parallel scheduling、cancel 和 diagnostics；
- 保持顺序、determinism、failure、结果与 mutation atomicity。

本层类似 Java Stream 的执行实现层：它可以复杂，但复杂性不能等比例投影到用户
API。执行层通过窄 typed read/mutation protocol 请求存储层执行最终 publish，
但不成为 live state Owner；存储层继续拥有 authoritative Table fact 和
invariant。Table direct mutation 也经 generated facade 使用同一 storage
mutation boundary。除极少数明确的高级诊断或调优入口外，内部类型不进入用户
心智模型。

### 分层与依赖约束

```text
authoring / compile time:
    application schema and code
        -> compiler / generator
            -> generated semantic facade

runtime:
    generated facade
        -> direct storage access
        -> execution engine -> storage access protocol
```

- generated facade 只依赖存储层和执行层的稳定窄协议；
- 执行层可以依赖存储 access protocol，存储层不得反向依赖执行层；
- 存储层和执行层都不得反向定义用户领域语言；
- public 不等于 user-facing；Java 8 下为生成桥接所需的可访问类型仍必须收敛到
  最小协议，并从用户文档、IDE 默认路径和业务 Result 中隔离；
- 内部优化可以替换，但必须保持用户语义、顺序、Key、Index、failure 和结果一致。

## Java Stream 的借鉴边界

### 应当学习的产品体验

SOMA 应学习 Java Stream 的以下产品经验：

- 少量稳定概念即可完成常见操作；
- fluent pipeline 按业务意图从 source 组合 intermediate operation；
- terminal operation 自然表示一次计算的执行和结束；
- 默认路径简单，高级执行机制不要求普通用户理解；
- pipeline 的内部实现复杂度不泄漏为同等数量的 public application concepts。

这里借鉴的是用户体验和分层方式，不要求 SOMA hot path 使用
`java.util.stream.Stream`、Spliterator、boxing collection 或其具体执行实现。

### Java Stream 与 SOMA 的本质区别

这里的“一元”指 source arity，而不是 callback 或 operator 的 Java 参数个数。
Java Stream 的核心抽象是一条以单一数据源为起点的 pipeline；`Comparator`、
`reduce` 等操作即使使用二元函数，也没有把两个独立、可按 Key/Index 访问的
数据源建模为一等关系输入。`Stream.concat` 也只是顺序拼接，不提供
schema-aware、key-aware 的关系语义。

SOMA 的 source 是 schema-defined Table，而不是普通元素序列。Table 具有：

- 稳定的字段和逻辑类型；
- 可选的 stable Key；
- 编译期声明、运行期维护的 Unique/Index access；
- mutable runtime state 与明确 operation boundary；
- 由编译生成层提供的 schema-specific access 和 computation facade。

目标 SOMA 只保留一类 pipeline：

```text
一个 Table/row/value source
    -> filter / sort / project / aggregate
        -> one Query/Update/Remove terminal
            -> result or controlled Table-local state change
```

当前专题明确收敛为：

- pipeline 只有一个 source；
- 多张 Table 由 application 通过多个独立 unary operation、Key/Index lookup 和
  普通 Java 控制流协调；
- 不设计 binary relation、三个及以上 source 的多元 operation、通用 DataFlow
  graph 或任意 DAG；
- Key/Index 是 SOMA 与 Java Stream 的核心差异，但它们应表现为用户语义和执行优势，
  不能迫使用户选择 physical scan、locator 或 buffer。

### 当前 SOMA 的执行模型

当前实现并非没有采用 Stream-like 模型，而是同时存在两条层次不同、部分重叠的
用户路径。

第一条是 direct Candidate Access：

```text
CandidateSource
    -> Stage*
        -> CandidateTerminal
            -> Result 或 Table-local Effect
```

其中：

- Source 是 Packed Table、Exact Group、Unique bridge 或 owned child；
- Stage 是 Filter、Skip、Limit、Sort；
- Terminal 是 count/anyMatch、forEach、Index、snapshot、materialize、
  update 或 remove；
- Point、Column、Key、Bulk 和 Ownership 又作为不进入这条 pipeline 的独立
  access family 存在。

第二条是 typed DataFlow：

```text
Generated DSL / Builder
    -> Definition
        -> analyze / compile
            -> Template
                -> Invocation
                    -> bind Table / parameter / Context
                        -> sequential / parallel kernel
                            -> Result 或 Effect
```

DataFlow 内部需要逻辑定义、静态 lowering、绑定和一次执行等阶段，这些阶段本身
合理；当前问题是它们被投影成 Source、Binding、Definition、Template、
Invocation、Context、Budget、Policy、Stats 等大量 application-facing 类型。

因此，当前模型的问题不是“不像 Stream”，而是：

- Candidate 已采用 Source/Stage/Terminal，却只覆盖 Access 的一个子集；
- direct access family 与 DataFlow 各自要求一套额外心智模型；
- 编译阶段、执行阶段和诊断阶段的内部对象被要求由用户手工组织；
- 同一个业务意图需要先选择 Access family，再选择是否进入 DataFlow。

### 已确认的用户执行模型

用户语义层可以统一借鉴以下模型：

```text
数据源
    -> 零个或多个中间操作
        -> 一个 Query/Update/Remove 终止操作
            -> 结果或受控 Table-local state change
```

SOMA 的“数据源”比 Java Stream 更丰富：

- 任意 SOMA Table object，包括 root Table 和通过 parent 导航得到的 owned child
  Table；ownership 不产生新的 source kind；
- Key/Unique 定位形成的 `0..1` record source；
- Index 定位形成的 `0..N` record source；
- Table 或 record source 上的 scalar、`@SomaValue` 和 nested value field source。

中间操作表达 filter、sort、skip/limit、projection 等业务计算；Query terminal
表达 count、match、first、forEach、toList/toArray 或 aggregate；Update/Remove
terminal 表达对最终 selection 的受控状态变化。用户不显式构造 Invocation、
staging、transaction 或调用额外 commit。

Point convenience 仍然可以保留，例如 `table.get(key)`；但它应是上述
`Key source -> first/required terminal` 的自然快捷入口，不形成另一套心智模型。
编译生成层和执行层仍可对 point、exact、full scan、best-one 选择完全不同的
专门化路径，而不改变用户看到的统一模型。

这里的 source 是“一个有限、类型化、可继续组合 operation 的逻辑序列”，不等于
独立 storage Owner。以：

```java
@SomaTable(name = "transport_times", defaultCapacity = 4096)
public final class TransportTime {
    @SomaKey public MachinePairKey machinePair;
    @SomaField public long transportMinutes;
}
```

为例，用户语义中同时存在：

```text
TransportTimeTable
    record source: TransportTime

TransportTimeTable.machinePair
    value source: MachinePairKey

TransportTimeTable.machinePair.fromMachine
    nested value source: MachineId

TransportTimeTable.machinePair.fromMachine.value
    scalar source: long
```

以上是语义路径示意，不预先裁决最终 Java member 使用 field、method 还是 generated
selector facade。

必须区分两个方向：

```text
TransportTimeTable.machinePair
    当前 record source -> MachinePairKey value source

TransportTimeTable + 一个 MachinePairKey
    key lookup -> 0..1 TransportTime record source
```

前者是 field projection，后者是 identity selection；两者都应类型安全，但不能用
同一个含混操作表达。

`machinePair` 在用户语义中始终是一个完整字段。存储层把它递归展开为
`fromMachine.value[]` 与 `toMachine.value[]` 两个 `long` leaf column，只是物理
实现。filter、equality、order、group 和 aggregate 可以直接作用于 leaf
columns，不构造 per-record `MachinePairKey`；只有 `toList`、object callback 等明确
交付领域 object 的 terminal 才重建 value object。

Field/value source 继承上游 record source 的候选集合、encounter order、currentness
和 lifecycle。它不是新的 Table、可独立修改的 live state 或另一份事实。

## D-002 Stream-like 候选操作集合

决策状态：Product Owner 已确认 unified single-source pipeline、source shape、
无状态/有状态分类；D-013 随后确认 terminal 分为 Query/Update/Remove，取代本节
最初的 read-only 限制。具体 signature 与标记为“候选/延后”的 operation 仍待裁决

Java 8 Stream 的核心用户模型是：

```text
source -> intermediate operation* -> terminal operation -> result
```

SOMA 借用这一形状，但不复制全部 Stream API，也不把当前 Transformation Model
全部保留为 V1 核心。候选操作必须同时满足：

- 能从目标 workload 或三个 reference application 找到真实 consumer；
- 可以由 schema、Key、Index 和 typed value 自然表达；
- 不要求 application 理解 physical plan、cursor、scratch 或 execution lifecycle；
- sequential/parallel 保持同一结果、顺序和 failure；
- result、materialization 和外部 side effect 边界明确。

### 数据源

| 数据源 | Java Stream 对应 | SOMA 候选 |
|---|---|---|
| SOMA Table object | `Collection.stream()` | 核心；Table 本身是有限 record source，root/owned child 使用同一模型 |
| Key/Unique 定位结果 | 无一等对应 | 核心；定位形成 `0..1` record source，不形成另一套 Point 心智模型 |
| Index 定位结果 | 无一等对应 | 核心；定位形成 `0..N` record source |
| scalar field | `map` / primitive stream | 核心；上游 record source 的 typed value source |
| `@SomaValue` field | `map`，但 Stream 不理解 flattening | 核心；语义保持完整 value，执行可递归作用于 leaf columns |
| nested value field/path | 连续 `map` | 核心；保持 generated type、presence、equality 与 parent record alignment |
| `of/generate/iterate/range` | Stream 静态 source | 不进入 SOMA Table execution；SOMA source 是有限、schema-defined runtime state |
| `concat` | 两条同类型 Stream 顺序拼接 | 暂不进入核心；需要真实 union-all consumer 后再裁决 |

Key/Unique/Index 应改变 source cardinality 和可用的自然快捷入口，但不要求用户知道
运行时选择 hash locator、exact links、bitmap、scan 还是其他物理结构。

Owned child 不是独立 source category。通过 parent field 导航得到的 child object
本身就是另一张 SOMA Table；它只在 ownership、resource、Group reachability 和
GC 规则上受 parent aggregate 约束。

### 无状态中间操作

无状态是用户可观察的语义分类：处理当前元素时不依赖此前或此后的其他元素。
实现是否融合循环、读取几个 leaf column 或采用 vector kernel 不改变此分类。

| Java Stream | 当前 Transformation | SOMA 候选 |
|---|---|---|
| `filter` | Selection / Filter | 核心 |
| `map`、`mapToInt/Long/Double` | Projection | 核心；生成 scalar/value/nested-value source，不默认产生任意 object graph |
| 连续 field navigation | 当前散落在 generated leaf API | 核心；例如 `machinePair -> fromMachine -> value`，编译后直接访问 leaf column |
| `peek` | 当前无正式对应 | 不支持；它把外部 side effect 混入可优化 intermediate |

generic `flatMap` 不属于无状态核心。它会改变 cardinality、ownership 和
materialization shape；owned child 直接导航为另一张 Table，不需要用 `flatMap`
解释。

### 有状态中间操作

有状态操作需要观察多个元素、encounter position 或另一个 source，因而可能需要
scratch、排序、hash/group structure 或 short-circuit coordination。

| Java Stream | SOMA 候选 |
|---|---|
| `distinct` | 核心候选；按 schema-defined scalar/`@SomaValue` equality 保留第一次出现，资源准入失败时 fail closed |
| `sorted` | 核心；稳定排序，必须明确 comparator、tie-break 与 encounter order |
| `skip`、`limit` | 核心；依赖 encounter position，其中 `limit` 可 short-circuit |
| 无直接 intermediate 对应 | 受限 `groupBy(key)`；先只允许 built-in aggregate terminal |
| 无需独立 API | Top-K / best-one 作为 `sorted + limit/first` 的内部 lowering |

Prefix Scan、Window、union/concat、binary relation 和 arbitrary Expand 不进入
V1 核心。

### 核心 Query 终止操作

| Java Stream | SOMA 候选 |
|---|---|
| `count` | 核心 |
| `anyMatch/allMatch/noneMatch` | 核心 |
| `findFirst` | 核心；结果为显式 absence 或 required failure |
| `findAny` | 不支持；不以并行换取可观察的不确定结果 |
| `forEach/forEachOrdered` | 只保留一个有明确 encounter order 的 `forEach`；callback 不得修改来源 Table，其他外部副作用与线程安全由 application 负责 |
| `toArray` | 核心；primitive projection 优先返回 primitive array |
| `collect(toList())` | 核心 `toList`，明确 detached object/materialization 成本 |
| `sum/average/min/max` | 核心 built-in typed aggregation |
| `min/max(comparator)` | 核心 `minBy/maxBy` 或等价 row terminal，可内部降低为 arg-min/arg-max |
| `summaryStatistics` | 延后；可以由一次专门化 aggregate 实现，但不是最小 V1 所需 |
| generic `reduce` | 高级候选；只有声明 identity、associativity、determinism、overflow/failure 和 parallel merge 后才允许 |
| generic `collect` / `Collector` | 不进入核心；避免任意 mutable container、boxing 和 parallel combiner 成为执行契约 |

Grouped source 的首批 terminal 只包含 `count/sum/average/min/max` 等 built-in
aggregate。普通 grouped members materialization、任意 downstream graph 和
custom collector 不作为最小核心。

### Query、Update 与 Remove terminal

D-013 已确认 single-source selection pipeline 可以拥有三类 terminal：

```text
Query terminal
    -> result

Update terminal
    -> Table-local row-domain-preserving state change
    -> UpdateResult

Remove terminal
    -> Table-local row-domain-changing state change
    -> RemoveResult
```

用户只表达 selection 与 mutation intent。执行层负责冻结最终 candidate、
validation、Key/Unique/Index/owned-child maintenance 和 all-or-nothing publish；
这些机制不成为用户 API 的 staging、commit 或 transaction protocol。

`add` 没有 existing-record selection，仍是 Table direct operation。Key/Unique point
update/remove 也保留为单行快捷路径。predicate、mapper、comparator、`forEach`
callback 或 updater 不能重入同一个 source Table；受控状态变化只能由当前
mutation terminal 执行。

`reserve/clear` 属于 Table direct storage/capacity API，不是 pipeline
intermediate 或 terminal；其精确去留与原子边界另行裁决。`IndexSnapshot`、
Delta、handoff、raw current Index 和 execution staging 不成为普通路径的必要概念。

### 不提供 Table 二元操作

V1 pipeline 只有一个 source，不提供 generic `join`、pair/relation source、
left/right expression/order/result 或双 Table read boundary。多个 Table 由
application 通过独立 unary operation、Key/Index lookup、detached result 和普通
Java 控制流协调。

这不否认 fused join 可能带来性能收益；它只确认该收益不足以在 V1 引入第二套
relation 心智模型。重新引入任何 binary capability 必须满足 D-007 的新 evidence
条件。

### 不照搬的 Stream API

- 不提供无限 `generate/iterate` source；
- 不提供普通 `Iterator`、`Spliterator` 或 pull cursor；
- 不提供 `parallel()`、隐式 common pool、`unordered()` 或 `findAny()`；
- 不提供允许任意 object expansion 的 generic `flatMap`；
- 不提供带外部 side effect 的 `peek`；
- 不把通用 `Collector`、mutable accumulator 或 arbitrary callback graph 作为
  canonical high-performance path；
- 不因 API 像 Stream 而要求执行层采用 Java Stream 的内部实现。

并行执行如果保留，由 application 显式提供 executor；是否分 Segment、morsel 或
vector 是执行层物理策略，不进入 pipeline operation。

### 旧 Transformation Model 能力的迁移映射

本表只用于确保旧 Design 中仍然有效的能力和约束不会在迁移时丢失。迁移完成后，
它不再作为一套与 pipeline 并列的用户模型存在。

| 旧 family | 迁移到统一 pipeline 的位置 |
|---|---|
| Selection | 保留为核心：filter、skip、limit |
| Projection | 保留为核心：typed map/select、primitive projection |
| Aggregation | built-in 保留；generic reducer 降为高级候选 |
| Prefix Scan | 延后；当前 reference application 没有证明核心需求 |
| Partition | 不保留独立核心概念；优先由 filter 或受限 groupBy 表达 |
| Combine | 延后；等待 concat/union-all 的真实 consumer |
| Rearrangement | stable sort 保留；top-k/best-one 内部化；GroupBy 只保留 aggregate 子集 |
| Join | 不迁移到 V1 用户 pipeline；由多个 unary operation 和 application coordination 替代 |
| Expand | generic Expand 删除；child navigation 直接返回普通 SOMA Table，不形成 Expand operation |
| Window | 延后；不作为最小 V1 能力 |
| Effect | Table-local Update/Remove terminal 保留；candidate freeze、staging 和 atomic publish 内部化 |

目标主干收敛为：

```text
Key/Index/whole Table source
    -> filter / sort / projection
    -> Query / Update / Remove terminal
```

现有使用 Batch 或 join 的 reference application 必须改写为该主干、repeated
direct add 和 application multi-Table coordination；Table-local mutation terminal
迁移到统一 stream。它们尚未证明 Prefix Scan、Partition、Combine、generic
Expand、Window、通用 Collector 或多 source graph 是 V1 核心。

## D-003 单一用户执行模型与旧模型退役

决策状态：Product Owner 已确认普通用户只保留统一 single-source execution model，
Transformation Model 与 DataFlow Model 不再作为目标用户模型；具体 operation、
内部模块边界和迁移闭环仍待裁决，尚未 promotion 为正式 Design

SOMA 面向 application developer 只保留一套 canonical 执行心智模型：

```text
有限 typed source
    -> 无状态或有状态 intermediate operation*
        -> 一个 Query/Update/Remove terminal
            -> scalar、detached result、同步 callback 或受控 Table-local state change
```

- Transformation Model 不再作为独立产品模型或用户 API taxonomy；其中获批的
  operation 语义迁移到 source/intermediate/terminal 模型。
- DataFlow Model 不再作为用户必须学习和装配的模型；Definition、Template、
  Invocation、Binding、Context、Kernel 等概念从普通 generated API、产品文档和
  reference application 中退出。
- 编译生成层和执行层仍然需要 internal operation IR、lowering、binding、plan、
  kernel、scratch 和 scheduling。这些是实现机制，不是第二套用户模型，也不能以
  public lifecycle 反向支配用户 API。
- `Transformation` 仍可作为普通技术用词描述 filter、projection 等计算，
  但不再指一套独立于统一 pipeline 的 SOMA 编程模型。

退役不能用直接删除文档或类型代替 replacement closure。正式实施时必须：

1. 把旧 Transformation/DataFlow 中仍有效的 operation、determinism、resource、
   failure、binding 和并行约束迁移到新的唯一 Design Owner；
2. 以三个 reference application 验证完整业务旅程不再依赖旧 user-facing
   Definition/Template/Invocation/Context；
3. 更新 Blueprint、Design、generated API、public artifact、测试与产品文档；
4. 只有在 predecessor consumer、并行 Owner 和 migration-only artifact 为零后，
   才退役旧文档、public type、module 或命名。

本决策当前不等于授权删除 `soma-dataflow` module、修改 public API 或开始迁移。
是否保留一个内部 execution module，以及它最终叫什么，由后续系统结构和实施
计划裁决。

## D-004 原始 Table 数据的存储模型

决策状态：Product Owner 已确认原始 payload 只使用 primitive/reference 两类 flat
leaf column，V1 不引入 Segment；primitive/reference value-state 与 ordinary
Object 的最小契约见 D-005，owned child physical slot 与 capacity evidence 仍待闭合

### 本轮事实范围

本节只讨论 SOMA Table 中 application schema 所声明的原始事实及其 per-row
value-state：

- 包含普通 field、Key field 和 `@SomaValue` 展开后的 authoritative payload；
- primitive leaf 永远有值，reference leaf 直接以 `null` 表示 absence，不包含
  独立 per-row presence bitmap；
- 暂不讨论 Schema/Effective/Runtime Metadata；
- 暂不讨论 Primary locator、Unique/Index、row link 等派生访问结构；
- 暂不讨论 pipeline scratch、sort/group/join intermediate 和 detached result。

因此，“原始 Table 数据”与“整个 Table runtime 所占内存”不是同一个统计范围。

### 两类 payload leaf column

Schema field tree 经编译生成层递归 flatten 和 lowering 后，只产生两类
authoritative payload leaf column：

```text
SOMA Table logical field tree
    -> compiler flatten / logical-type lowering
        -> primitive payload leaf column
        -> typed reference payload leaf column
```

#### Primitive payload leaf column

使用 Java primitive array 保存，例如 `byte[]`、`int[]`、`long[]`、`double[]`。
来源包括：

1. `@SomaTable` 中直接声明的 primitive scalar；
2. `@SomaValue` 逐层展开后最终为 primitive 的 leaf；
3. Enum、Date、Time、Instant 等 logical type lowering 后的 primitive carrier。

logical type 不能因为物理上使用 `int/long` 就丢失。Enum ordinal、epoch day、
nano-of-day 和 epoch-millis 的构造、比较、范围、overflow 与 materialization
仍由生成的 typed semantics 负责。

#### Typed reference payload leaf column

使用 Java reference array 保存，例如当前 V1 的 `String[]`。Java array slot 保存的
是 JVM 管理的 object reference，不是 application 可观察的稳定内存地址，也不是
SOMA 自己维护的 integer index。Java 中可以说“访问引用指向的对象”或概念上的
“解引用”，但语言没有 C/C++ 的显式 `*pointer` 操作；字段和方法访问会隐式完成。
GC 即使移动对象，也负责保持 live reference 有效。

Reference leaf 是不可再由 SOMA flatten 的一个 physical leaf。目标模型按 D-005
允许普通 Java Object reference：SOMA 只拥有 slot 指向哪个 object，不 deep-copy、
冻结或版本化 referent 内部状态。ordinary Object 是否能参与
Key/Unique/Index/equality/order 仍需按稳定语义单独裁决，不能仅因“能够存进
reference array”自动开放。

`@SomaValue` 不保证所有 leaf 都落入 primitive family。若未来或当前白名单允许：

```text
@SomaValue
    primitive leaf -> primitive column
    reference leaf -> typed reference column
```

同一个 composite value 可以同时由两类 leaf column 组成，但在用户语义中仍是一个
完整 value。

### Predecessor Optional presence 与目标 replacement

predecessor 使用 per-row presence bits 区分 primitive/reference 的 present 与
absent：

```text
optional logical field
    -> presence bits
    -> primitive/reference payload leaf column
```

Presence 不是 Metadata 或 secondary Index，而是 predecessor 的 authoritative
value-state。Product Owner 已确认替代语义：primitive 永远有值并使用 Java zero
default，reference 直接用 `null` 表示 absence。目标原始 Table 不再需要 presence
bitmap，也不区分“primitive 未提供”与“显式 zero”，或“reference 未提供”与
“显式 null”。

### Owned child 的物理槽位待裁决

用户语义已经确认：owned child 导航得到的是另一张普通 SOMA Table object，
ownership 不产生新的 source kind。原始 parent field 在物理上究竟：

- 直接保存 typed child Table reference；
- 保存由内部 registry 解析的 opaque handle；
- 或以其他受控 reference 形式连接；

仍属于 storage/ownership 联合决策。本节不沿用旧实现自动裁决，但最终形态必须让
ownership、absence、transitive Group reachability、GC 与 failure atomicity 一致，
且不能把 handle/registry 暴露为用户心智模型。

### Segment 是否必要

Product Owner 决定：V1 原始 Table storage 暂不引入 Segment。

Segment 不是 Table、field 或 value 的用户语义，只是上述 primitive/reference
columns 的候选物理布局：

```text
FLAT:
    one logical leaf column -> one Java array

SEGMENTED:
    one logical leaf column -> several fixed-size Java arrays
```

它对正确性不是必需的；单一 Java array 足以实现逻辑 column。它可能有价值的唯一
核心场景是“大 Table 在运行中持续增长”：

- flat growth 需要为每个 leaf 分配更大 array 并复制历史 payload；
- 原 array 与新 array 在 publish 前同时存活，宽 Table 会放大 transient peak；
- 大型单 array 也会形成更大的 GC object；
- segmented growth 可以只为每个 leaf 增加下一段，避免复制已发布的历史 segments。

相应成本是：

- point get/set 增加 layout branch、segment ordinal/offset 和二次 array access；
- packed scan 必须在外层解析 segment，才能避免逐 row dispatch；
- 更多 array object、directory、边界 copy/clear/swap-remove 和测试组合；
- primitive/reference/presence 的每种 column 都承担更高实现与维护复杂度。

当前 predecessor 实现采用自适应 `FLAT` 与 `FLAT_HEAD_SEGMENTED_TAIL`，固定
`32,768` rows 的 internal segment。历史技术验证支持“Small/Medium/point-heavy
保持 flat，Large scan/growth 才 segmented”，并否决 universal segmented getter。
这些 evidence 证明 predecessor 方案可以成立，但不要求新的最小 storage model
继续承担双 layout、逐 column 分段和边界操作复杂度。

V1 目标 storage 收敛为：

```text
one logical leaf column -> one contiguous Java array
```

- primitive、reference 以及仍存在的其他 per-row raw state 使用 contiguous flat
  storage；
- Table capacity 不超过 Java array/index 可表达边界和声明的 maximum rows；
- create/reserve 应尽可能一次解析目标 capacity；
- 动态 growth 需要为所有受影响 columns private stage 新 array、复制旧 payload，
  通过 memory preflight 后一次 publish；
- staging 期间 old/new arrays 同时存活的 transient peak 必须显式核算，预算不足时
  在改变 visible state 前 fail closed；
- packed scan、point access、copy、clear、remove 和 parallel range 不再包含
  segment branch、ordinal、offset 或 topology；
- parallel range/morsel 只是执行层对 contiguous `[from,to)` 的切分，不依赖
  Storage Segment。

该决定会使 predecessor 的 `FLAT_HEAD_SEGMENTED_TAIL`、`segmentRows`、
`flatHeadRows`、Segment Metadata、segmented column branches 和相应测试成为未来
实施阶段的 replacement/retirement scope；本 Temporary 本身不授权立即删除。

V1 不因这次删除物理复杂度而承诺无界 Large growth。未来如果真实生产 workload
证明 flat growth 的 copy/transient peak 无法接受，只能在新的 cost model、
production-shape A/B evidence 和 Product Owner 决策后重新引入分块方案；不能把
predecessor 的 `32K` 参数自动恢复为产品事实。

## D-005 Primitive zero、Reference null 与 ordinary Object

决策状态：Product Owner 已确认 primitive always-present/zero、reference nullable
以及 ordinary Object referent 由 application 维护；outer `@SomaValue`、zero Key
和 Object 的 equality/order/Key/Index capability 仍待裁决，尚未 promotion 为正式
Design

### 已确认的最小 value-state

```text
primitive leaf:
    always present
    omitted initialization -> Java zero value
    never null

reference leaf:
    null or one Java object reference
    omitted initialization -> null
```

这一模型已经确认，并要求删除目标原始 Table 的 optional presence bitmap。它明确
放弃以下区分：

- primitive omitted 与显式 `0/false/'\0'`；
- reference omitted 与显式 `null`；
- absent 与 schema default，除非以后重新增加显式 default rule。

这不是存储优化细节，而是用户语义。正式 replacement 应删除或重新定义
`@SomaOptional`、required-field failure、presence expression 和相关 generated API，
不能继续在文档中声称 SOMA 能区分上述状态。

Enum、Date、Time、Instant 等 primitive-lowered logical type 也必须接受其 carrier
zero 的领域含义，例如 ordinal `0`、epoch day `0`、nano-of-day `0` 和
epoch-millis `0`。是否允许 Key field omitted 后自然成为 zero key，需要 Product
Owner 明确接受；否则只有 staging assignment tracking 才能在存储仍为 zero 的同时
保持 Key 必填。

### `@SomaValue` 的 null 边界

`@SomaValue` 在 Java authoring 中是 reference type，但在 SOMA storage 中不是
reference payload，而是递归展开的 logical value。因此必须选择：

1. `@SomaValue` field 永远非 null；未提供时每个 leaf 使用自己的 zero/null
   default，重建时得到一个完整 value object；
2. 允许整个 `@SomaValue` 为 null，并为 outer value 保留 presence state。

若目标是彻底删除 presence，推荐选择 1。一个 value 内部的 reference leaf 仍然
可以为 null，但完整 value 本身不为 null。

### Ordinary Java Object reference

物理存储上可以允许任意 Java reference type：

```text
one reference leaf column -> one typed Java reference array
```

- slot 保存 caller 提供的同一个 strong reference；
- SOMA 不 deep-copy、不序列化、不冻结，也不检查 referent 是否 immutable；
- `null` 是合法 slot value；
- update/remove/clear/compaction/capacity shrink 必须清除 dead slot，避免无意
  retained object graph；
- materialization/读取默认返回同一个 reference，而不是对象副本。

但“SOMA 不限制对象类型”不能等同于“SOMA 对对象内部状态负责”。已确认的最小
契约为：

- SOMA 只拥有并版本化“某行的 slot 当前指向哪个 object”；
- referent 内部字段由 application 拥有，其原地修改不经过 SOMA mutation
  boundary，不更新 Table epoch，也不进入 rollback/failure atomicity；
- application 必须负责 referent 的线程安全，并保证在一次 SOMA operation 中不发生
  会破坏该 operation determinism 的并发修改；
- ordinary Object 默认是 opaque payload。它是否允许作为 Key/Unique/Index、
  built-in equality/order、distinct/group/join operand，必须按稳定
  equality/hash/order 能力另行裁决，不能仅因“能够存进 Object array”自动开放。

Owned child Table 可以使用同一 reference-column 物理机制，但语义角色不同：
child Table 是 SOMA-managed reference，SOMA 仍拥有其 ownership、transitive
Group reachability、GC aggregate 和 operation boundary；它不能用来证明 arbitrary application
object 的内部状态也受 SOMA 管理。

因此，本专题确认：

> raw reference column 可保存 ordinary Java Object，SOMA 不强制 immutability；
> SOMA 只保证 reference slot，referent state 由 application 负责。

明确不接受：

> referent 可以被任意修改，同时仍把 SOMA 的 epoch、index、deterministic
> parallel execution 和 failure atomicity 保证外推到该对象内部。

## D-006 不引入 Batch 操作

决策状态：Product Owner 已决定；尚未 promotion 为正式 Design 或授权实施

V1 用户模型不引入 application-visible Batch object、Batch staging API 或 Batch
commit：

```text
不提供：
    XxxBatch
    table.addBatch(...)
    table.replaceAll(batch)
    application-managed batch.clear()/reuse()

提供：
    table.reserve(expectedRows)
    table.add(...)
    table.update(...)
    table.remove(...)
```

多个 row 的导入和追加由 application 显式循环调用多次单次原子 `add`。没有
selection source 的 replace/import 也不获得 application-managed Batch。

这个决定必须同时接受以下语义：

- 多次 direct `add` 不是一个 all-or-nothing transaction；
- 第 `N` 次 add 失败时，前 `N-1` 次已经成功发布的事实继续存在；
- `clear()` 后循环 `add()` 不能伪装成原子 `replaceAll()`，中途失败会留下部分新
  state；
- application 拥有重试、补偿、导入 checkpoint 和跨多次调用的一致性；
- `reserve()` 只预留 capacity，不承诺后续多次 `add()` 共同提交；
- 不因取消 Batch 而在内部偷偷维护一份 application-visible staged row graph。

本决策只删除“Batch 作为 application 数据输入、暂存和提交模型”。D-013 随后
恢复 Table-local `filter(...).update/remove` terminal，并确认一次最终 selection
在 ownership aggregate 内 all-or-nothing。它不是 public Batch：

- application 不创建、填充、复用或 commit Batch object；
- mutation source 来自当前 Table 的 record/field selection；
- execution staging 是一次 terminal 的私有实现细节，terminal 后立即释放或复用；
- 用户不参与 preflight/publish protocol。

因此 D-006 与 D-013 可以同时成立：没有 public Batch model，但允许执行层为一次
Table-local atomic mutation 使用 private bounded scratch/staging。

当前三个 reference application 大量使用 generated Batch 完成初始化投影，
Grassing reproduction 和 Industrial chunked import 也依赖它。因此正式实施必须：

1. 把 import consumer 改写为 `reserve + repeated atomic add`；
2. 删除全部 generated Batch/public manifest/guide/golden/fixture surface；
3. 重新测量大规模初始化、reproduction、allocation 和 add failure
   partial-progress；
4. 单独验证 selection update/remove 的 atomicity、Key/Index/Unique/ownership
   maintenance 和 private scratch；
5. 不用历史 Batch 性能 baseline 继续声明新模型的 import 性能。

简单性是本决策的主要收益；重复 validation、epoch/guard 和 access maintenance
造成的吞吐成本是明确接受并需要重新校准的代价。

## D-007 Table 二元操作的必要性

决策状态：Product Owner 已决定 V1 不引入通用 binary relation/join；尚未 promotion
为正式 Design 或授权实施

### 表达能力与执行能力必须分开

任意有限双 Table 计算在表达能力上都可以分解为多个单 Table operation 加
application coordination，例如：

```text
Table A unary selection
    -> 对每个 A key 调用 Table B point/index lookup

或

Table A unary terminal -> detached A result
Table B unary terminal -> detached B result
application combines A and B
```

因此 binary operation 不是 SOMA 完成业务计算的必要语义原语。

但分解并不保证执行等价：

- nested full scan 可能把 `O(A+B)` 或 `O(A+matches)` 退化为 `O(A*B)`；
- repeated point/index lookup 会增加 operation/guard/callback 固定税；
- 两份 detached result 可能比 fused relation 消耗更多 allocation；
- application 需要自己定义 pair order、absence、duplicate、failure 和 stale-read；
- 多次 operation 之间允许 Table 变化，不能冒充一个双 Table read boundary。

因此，binary operation 的潜在价值是性能融合、统一 relation order 和一次双 source
read guard，不是提供原本无法表达的业务能力。

### 三个 reference application 的证据

- Industrial Scheduler 的核心 hot path 已主要表现为一个当前 row/candidate 驱动，
  再对其他 Table 执行 Key `requireIndex`/point lookup；它没有证明需要通用 join
  pipeline。
- Grassing Simulation 的核心路径没有证明 Table binary relation 是必要能力。
- RTD 当前显式使用 Work × Resource 的 capability equi `innerJoin`，随后排序并由
  application 做 one-work/one-resource selection。该业务可以改写为：分别按
  capability 对 Work 和 Resource 做 unary filter/order，再由 application 配对；
  代价是需要明确 materialization、lookup 次数和跨 operation currentness。

现有 RTD 证明“一个真实场景使用了 join”，但尚未证明“只有 public generic
binary relation 才能以可接受复杂度和性能完成它”。

### 已确认目标

V1 canonical 用户模型先收敛为：

```text
one Table/value source
    -> stateless/stateful intermediate*
        -> one Query/Update/Remove terminal
```

- 不提供 generic `join`、JoinedFlow、pair source、left/right expression/order/result；
- application 可以依次执行多个 Table 的 unary operation，并用普通 Java 控制流、
  Key/Index lookup 或 detached result 组合；
- 不把“callback 捕获另一个 live Table 并任意嵌套 operation”默认为合法 hot path，
  其 reentrancy、lock/guard order 和 determinism 需要另行裁决；
- compiler/runtime 可以继续优化单 Table scan、point 和 Index lookup，不因为没有
  public join 而引入通用 object pair materialization。

只有后续真实项目同时证明以下条件，才重新考虑最小 binary capability：

1. unary decomposition 在正确算法下仍有不可接受的 lookup/materialization 成本；
2. 问题不是 application 数据模型或缺少 Key/Index 导致；
3. 一个窄的 compiler-specialized equality lookup/join 能稳定解决；
4. 它不重新引入 Relation/Definition/Template/Invocation 等第二套用户模型；
5. sequential/parallel、order、failure、resource 和双 Table currentness 可以形成
   简单稳定契约。

D-002 中此前的 binary relation source 与 4.6 二元操作候选已由本决策
supersede；旧 Transformation Join/DataFlow relation 只作为 predecessor 迁移
输入，不再进入目标用户 API。

## D-009 Record selection、Field source 与 Column lowering

决策状态：Product Owner 已确认该分层认知模型，并确认 physical Column 退出普通用户
API，只由 Field source 自动 lowering

### 三个概念不是并列 source kind

“对 Index、Field、Column 的访问”抓住了执行过程的三个真实部分，但若把它们作为
三个并列用户数据源，会混合用户语义与物理实现。更一致的模型是：

```text
record selection
    -> logical field/value projection
        -> physical leaf column access
```

- record selection 回答“本次 operation 处理哪些 logical record、以什么顺序处理”；
- field/value projection 回答“用户希望从这些 record 观察哪个逻辑值”；
- column access 回答“执行层从哪些 primitive/reference arrays 读取 carrier”。

它们共享同一个 Table record domain；执行层内部仍可用 row positions 表示该
domain，但这不是三份数据，也不需要三套独立 pipeline。

### Record source：逻辑记录，内部携带 row positions

整张 Table 的遍历、筛选和排序，用户语义上处理的是 logical records；执行层不需要
构造 per-record Java object，而是携带 current row positions：

```text
whole Table
    -> contiguous positions [0, size)

Key / Unique lookup
    -> zero or one position

@SomaIndex lookup
    -> zero or more positions

filter
    -> selected positions

sorted
    -> reordered positions

skip / limit
    -> sliced positions
```

因此“得到 Table 的某一条或几条记录”是正确的用户语义；物理上得到的是本次
operation 内有效的 position sequence，terminal 才按需要读取字段、调用 callback
或 materialize detached result。

不建议把这里统一简称为 `Index`：

- row position/current index 是 `[0,size)` 内、随 Table mutation 变化的物理位置；
- `@SomaIndex` 是 schema 声明、运行时维护的 secondary access structure；
- application stable identity 是 Key。

用户语义和 generated API 使用 `Record` / `record selection`；存储与执行层保留
`row position` 作为物理位置术语，并对 `@SomaIndex` 使用 qualified name，避免
把 logical record、物理位置和 secondary access structure 混成一个词。

### Field/value source：用户语义上的投影

任意 record source 都可以投影一个 logical field：

```text
TransportTime record source
    -> machinePair field source: MachinePairKey
        -> fromMachine field source: MachineId
            -> value field source: long
```

- scalar field 映射一个 physical leaf column；
- `@SomaValue` field 可以递归映射多个 primitive/reference leaf columns；
- nested field source 仍继承上游 record selection、order 和 lifecycle；
- 投影是一对一的无状态 intermediate，不改变 record cardinality；
- composite equality、filter、sort、distinct 和 group 可以直接融合读取多个 leaf，
  不先构造 composite Java object；
- 只有 object-returning terminal 才按 logical field 语义重建 value。

Field source 是用户需要理解的概念，因为它保持 schema naming、logical type、
value equality 和 nested structure。

### Column access：执行层 lowering，不是第三套用户 source

编译生成层把 field/value source lowering 为一个或多个 flat arrays：

```text
machinePair field source
    -> selected positions
    -> fromMachineValueColumn: long[]
    -> toMachineValueColumn: long[]

machinePair.fromMachine.value field source
    -> same selected positions
    -> fromMachineValueColumn: long[]
```

执行层当然需要直接遍历 Column；这正是 columnar runtime 的性能来源。但普通用户
不应再选择“访问 Field 还是访问 Column”：

- primitive field source 已经可以降低为同样的 direct primitive-array loop；
- composite field 自动选择全部相关 leaf columns；
- nested field path 自动选择一个或多个子 leaf columns；
- Enum/Date/Time/Instant 的 raw carrier column 不能绕过 logical type 暴露为普通
  `int/long` API；
- physical column ordinal、array identity 和 storage carrier 不是 schema
  compatibility contract。

因此，已确认：

> public 用户模型只有 record source 与 field/value source；physical Column 是执行层
> 的内部读取机制，不作为第三种 canonical source。

现有 `ColumnTraversal`、`ColumnView` 和 raw leaf-column generated API 是
predecessor surface。正式实施必须先用生成代码、benchmark 和 reference
application 证明 field source 获得等价 direct-array 性能，再完成其内部化或退役；
不能保留一套要求普通用户理解 Column lifecycle 的平行 public API。必要的底层
诊断能力也只能位于非普通用户 API 的明确边界内，且不得泄漏 array identity、
physical ordinal 或 raw carrier contract。

### 统一执行示意

```text
用户表达：
    table
        .filter(record predicate)
        .machinePair
        .fromMachine
        .value
        .sum()

编译/执行：
    initial row-position range
        -> predicate 所需 leaf columns
        -> selected row positions
        -> fromMachine.value long[] direct loop
        -> checked sum
        -> scalar result
```

这保留了 Stream-like 用户体验，同时让 schema、Key、`@SomaIndex`、composite
field flattening 和 columnar execution 各自在正确层次发挥作用。

## D-010 Access order、lazy evaluation 与 source shape

决策状态：Product Owner 已确认 lazy execution、单向 source-shape transition、
encounter-order 继承与 terminal boundary；精确 generated syntax 和
`@SomaIndex` 多行顺序仍待后续裁决

### 构造 pipeline 不等于已经遍历

首先必须区分两件事：

- `filter`、Field projection、`sorted` 等 intermediate 只构造 computation
  description；
- 只有 terminal 才触发执行；stateful intermediate 可能在执行期使用 scratch、
  buffering 或多个阶段，但不会因为用户先写 Table 还是先写 Field 就提前扫描。

因此，下式中的 `filter` 并没有“先遍历一次 Table”，而是先限定 record selection，
再在同一次 terminal execution 中读取目标 Field：

```java
table.filter(recordPredicate).field.forEach(consumer);
```

编译生成层可以将无状态路径融合为 position/predicate/leaf-array loop，不构造
record object，也不产生仅由链式写法导致的中间集合或额外 pass。

### 两种顺序都成立，但 source shape 和谓词语义不同

Field projection 是一个单向的 source-shape transition：

```text
RecordSource<Record>
    -> FieldSource<FieldType>
```

- projection 前，intermediate 可以观察当前 record 的多个 logical fields；
- projection 后，后续 intermediate 只观察所选 Field 的 logical value；
- nested `@SomaValue` 仍可继续投影其 child field；
- 执行层可以继续携带 row positions，但 public API 不允许从 Field source
  回到整行或访问 Table sibling field。

所以以下两种表达都合理，但不是任意情况下都等价：

```java
// 先限定 records，再投影 field
table.filter(recordPredicate).field.forEach(consumer);

// 先投影 field，再按 field value 筛选
table.field.filter(valuePredicate).forEach(consumer);
```

只有当 `recordPredicate` 完全等价于对该 Field 的 `valuePredicate`，并且 null、
failure、order 等语义也一致时，二者结果才等价。若筛选或排序需要 sibling
fields，应在 projection 前完成；若只关心一个 Field，应尽早 projection，让
编译生成层获得最窄的 column read set。

整张 Table 上直接选择 Field 是 whole-record source projection 的简写：

```text
table.field
    == table whole-record source -> field projection
```

它们共享同一个 current row domain，不创建第二份数据源。

### terminal 之后不能再追加 intermediate

若“先遍历 Table”是指已经调用 terminal：

```java
table.forEach(row -> consume(row.field));
```

那么 Field access 发生在 terminal callback 内，并不是 terminal 之后又追加一个
Field intermediate；`table.forEach(...).field` 不成立。若 callback 只读取这个
Field，候选 API 应优先鼓励：

```java
table.field.forEach(consumer);
```

后者表达的 read set 更窄，更容易 lowering 为 direct leaf-column loop。前者仍可
用于同一次 callback 读取多个 fields，但其 record callback/extractor 的精确
generated shape 尚待 API 专题裁决，不能因此引入可缓存 live record proxy 或
per-record DTO allocation。

### Encounter order 由上游 record selection 拥有

Field projection 不改变 cardinality，也不自行重新排序；它严格继承上游 record
selection 的 encounter order：

- whole Table 初始顺序是本次 operation 的 current row-position order
  `[0,size)`，不是跨 mutation 稳定的业务顺序；
- Key/Unique lookup 的零或一行没有排序歧义；
- `@SomaIndex` 多行 lookup 的默认 encounter order 必须另行明确，不能在没有
  contract 时暗示稳定业务顺序；
- `filter` 和 projection 保序；
- `sorted` 建立显式的新顺序；
- `distinct` 保留哪个值、`skip/limit` 选择哪些值，都依赖其上游顺序。

因此：

```text
record selection
    -> logical field/value projection
        -> physical leaf column access
```

不仅是 storage lowering 模型，也同时定义了 cardinality 和 order 的传播方向。

### 优化不得改写可观察语义

编译生成层可以进行 loop fusion、只读取必要 leaf columns，并在等价条件可证明时
进行 predicate/projection specialization；但不得仅因物理上更快就跨越以下边界
重排：

- stateful intermediate；
- user callback 或 comparator；
- nullable reference、logical value reconstruction；
- checked overflow 或其他 structured failure；
- encounter order、first-occurrence 和 short-circuit contract。

直接 Field source 与“whole Table record source 后立即投影该 Field”应 lowering
为同一 canonical plan。其他操作顺序只有在完整结果、顺序、failure 与 callback
可观察行为都一致时才可重写。

## D-011 显式 operation-intent surface

决策状态：Product Owner 已确认用 `.stream()`、`_metadata()` 与 Table direct
operation 区分意图，并确认 `Soma -> SomaGroup -> Table -> Field -> nested
Field` 访问层级及 member-style navigation；Field projection、metadata facade 和
callback functional interface 的精确 generated syntax 仍待裁决

### 方向成立：入口表达意图，而不是暴露实现

显式入口可以把一个 generated Table 的能力分成四个易于理解的 surface：

```text
table.stream()
    -> 对 logical records 建立 single-source selection pipeline

table.<field source>.stream()
    -> 对整个 current record domain 的 logical field values 建立 selection pipeline

table._metadata() / table.<field source>._metadata()
    -> Table instance 或 logical Field 的冷路径 introspection

Soma._metadata()
    -> generated composition、compatibility 与 runtime capability metadata

Soma.defaultGroup()
    -> 取得稳定共享的 generated default Group

Soma.<table type accessor>()
    -> 取得 default Group 中对应的唯一 Table instance

Soma.createGroup()
    -> 创建一个新的独立 Group

somaGroup._metadata()
    -> composition、member 与 resource introspection

somaGroup.<table member>
    -> 导航到该 generated Table identity 唯一的实际 root Table instance

table.<direct operation>()
    -> point access、single-row mutation、capacity 等 Table-local operation
```

这比让 `filter`、`sum`、`metadata` 和 `add` 全部平铺在 Table 根对象上
更清晰：

- `.stream()` 明确表示“开始描述一次 lazy Table-local operation”；
- Field source 明确表示“从 whole-record domain 投影这个 logical value”；
- metadata 明确表示“观察描述或状态”，不是读取业务 rows；
- Table 根对象保留“直接作用于这个 storage owner”的操作。

这个分层只改变用户入口，不改变 D-009 的 lowering：

```text
Record Selection
    -> Logical Field/Value Projection
        -> Physical Column Access
```

完整 capability hierarchy 是：

```text
Soma
    -> _metadata()
    -> defaultGroup()
    -> schema-specific default Table accessor
    -> createGroup()
        -> SomaGroup
        -> _metadata()
        -> root Table member
            -> stream()
            -> _metadata()
            -> direct Table operation
            -> logical Field
                -> stream()
                -> _metadata()
                -> nested Field
            -> owned Child Table
                -> same Table capability hierarchy
```

### Record-first 与 Field-first 汇合为同一个 canonical plan

候选 API 需要同时表达两种自然意图：

```java
// Record-first：先限定 records，再投影 field
table.stream()
     .filter(recordPredicate)
     .transportMinutes()
     .sum();

// Field-first：whole Table 上直接选择 field
table.fields()
     .transportMinutes()
     .stream()
     .filter(valuePredicate)
     .sum();
```

这里的 `fields()` 只是用于说明 namespace 隔离的一种候选 Java 拼写；
`table.transportMinutes().stream()` 或生成的 field member 也可在 naming/collision
专题中比较。需要确认的长期语义是：

```text
table.<field source>.stream()
    == table.stream() -> immediate projection of the same field
```

二者必须 lowering 为同一 canonical field plan，不能拥有两套 executor、
currentness 或 performance contract。已经进入 record stream 后再投影 Field，不再调用
第二次 `stream()`。

### `_metadata()` 可以成为统一的 meta-operation namespace

若 `_metadata()` 只出现在 Table 上，leading underscore 确实容易被理解为模糊的
“内部接口”。但 Product Owner 进一步提出：

```java
table._metadata().***;
table.<field source>._metadata().***;
somaGroup._metadata().***;
Soma._metadata().***;
```

此时 `_metadata()` 可以拥有明确且统一的语言含义：

> 从当前 receiver 的业务操作空间，显式进入 SOMA 的只读元信息空间。

这也避免用户定义的 Field 名称与 `metadata` 发生普通命名冲突。该方案要求把
`_metadata` 定义为唯一、受保留的 SOMA meta-operation namespace，而不是
允许各生成类型任意增加 `_xxx` public method。

Metadata 内部仍需拆分两个不同事实：

1. **Schema/descriptor metadata**
   - schema、logical field/type、Key/Unique/Index declaration、compatibility
     identity；
   - 由编译生成，immutable，原则上是 Table type/schema-scoped；
   - 在没有 Table instance 时仍必须能从 generated schema/type companion 获取。
2. **Runtime metadata/observation**
   - rows、capacity、epoch、运行时 index/unique observation 等实例状态；
   - 返回 detached read-only snapshot；
   - 只有 Table instance、SomaGroup 等 runtime owner 才拥有。

不同 receiver 的 capability 不相同：

| Receiver | `_metadata()` 观察内容 | 明确不拥有 |
|---|---|---|
| Soma | generated composition、schema/compatibility identity、runtime capability | live Group registry、跨 Group 数据操作 |
| Table instance | 对应 descriptor identity、rows/capacity/epoch、Key/Index/Unique observation | schema mutation、physical array access |
| Field source | logical path/type、zero/null/default、nested value shape、Key/Index/Unique role | 独立 row domain、raw Column identity、绕过 Table invariant 的 mutation |
| SomaGroup | stable member identity、schema/Table composition、resource、member runtime snapshots | 业务 record stream、跨 Table transaction control |

对于 composite `@SomaValue`，Field metadata 也按 logical path 层级导航：

```java
table.machinePair()._metadata();
table.machinePair().fromMachine()._metadata();
table.machinePair().fromMachine().value()._metadata();
```

以上是概念示意；Field 使用 generated member 还是 method、metadata 的具体返回类型
和导航方法，仍需在 generated syntax 专题中裁决。

Metadata 只用于冷路径 introspection、诊断、工具和资源治理：

- application 不得修改 schema metadata；
- runtime metadata 不是 live mutable control plane；
- application 不得通过 metadata 直接修改 index、capacity、epoch 或 physical
  layout；
- metadata 不暴露已确认退出普通用户 API 的 raw physical Column、array identity
  或 carrier mutation；
- 真实状态变化只能经过 Table direct operation，并由 storage invariant 约束。

若最终选择 `_metadata()`，其返回的 metadata facade 应继续用明确的
`schema()`、`runtime()`、`members()`、`fields()` 等能力区分事实，不能把 descriptor
与 observation 混成一个 free-form Map。现有 `Table.metadata()`、
`table.runtimeMetadata()` 和 `SomaGroup.metadata()` 只是 predecessor API 事实；
正式 replacement 时必须选择一个 canonical surface，不能同时长期保留平铺入口和
`_metadata()` 平行入口。

### `SomaGroup -> Table` 是 instance navigation

`somaGroup.table.**` 是必要的自然层级，但其语义应是“从 composition owner
导航到一个实际 Table instance”，而不是把 Group 变成新的业务数据源。

确认的 member-style navigation 是：

```java
somaGroup.transportTimes.stream();
somaGroup.transportTimes._metadata();
somaGroup.transportTimes.add(...);
```

这些 Table/Field endpoint 应是 generated `public final` typed member；processor
需要对与 SOMA 保留 operation 名发生的 schema naming collision 给出 compile-time
diagnostic。长期约束是：

- 返回正常的 generated Table facade，后续复用同一套 `stream()`、
  `_metadata()`、Field 和 direct operation；
- Table resolution 使用 generated Table contract identity，不要求 application
  再提供 member instance identity；
- 同一个 Group 可以包含多个不同 Table，但同一个 generated root Table identity
  最多一个 instance；
- Group navigation 不创建复制品、snapshot 或第二个 Table owner；
- Group 不提供 Group-wide record stream、隐式 join、跨 Table filter 或跨 root
  transaction；
- 分别调用两个 member Table 的 `stream()` 仍是两个独立 single-source
  operations。

Group 直接组合的是 root ownership aggregate。Owned Child Table 继续经其 parent
Table/row 的 child field 导航，不在 Group 根部获得第二条平行 owner path：

```text
Soma -> SomaGroup -> root Table -> owned Child Table
```

这与“Child 本身仍是另一张 SOMA Table”一致，同时保留唯一 owner、cascade 和
不可 share/reparent 的 ownership 边界。

### Table 根对象只保留 direct operation

候选 Table root surface 包括：

- `size`、`capacity` 等低成本 container fact；
- Key/Unique 等 exact point read；
- 单行 atomic `add/update/remove`；
- `reserve`；
- 经后续裁决保留的 `clear` 等 whole-Table operation。

Group construction 与 default Table access 统一属于 generated `Soma`；Table 根对象
不再提供 `create/attach/defaultInstance` 等平行 construction surface。

`filter`、`map`、`sorted`、`distinct`、`sum`、`forEach` 等 computation operation
不直接平铺在 Table 根对象上，只通过 `.stream()` 出现。这样 IDE completion 也能
按意图区分 mutation/capacity 与 stream pipeline。

### 使用 `stream()` 名称的条件

`stream()` 是合适且熟悉的入口，但它会自然带来 Java Stream 预期。因此若确认该
名称，SOMA 必须明确保证共同语义：

- lazy intermediate、terminal-triggered execution；
- one-shot pipeline；
- encounter order、stateful/stateless、short-circuit 的稳定 contract；
- callback non-interference：callback 不得重入 source Table；受控 update/remove
  只能由当前 terminal 执行；
- Query、Update、Remove terminal 的 effect 与 atomicity 明确；
- 不把 per-record DTO、Java Collection graph 或 `java.util.stream.Stream`
  变成 runtime hot path。

返回类型应是 schema-specific SOMA Record/Field Stream，而不是伪装成
`java.util.stream.Stream<Record>`。V1 未提供的 `parallel()`、通用 collector 或
object materialization 能力应在类型上缺席，不能只在运行时报错。

当前整合结论是：

> 确认“显式 Table-local stream operation、只读 metadata introspection、Table
> direct operation”三类意图边界；stream terminal 可为 Query、Update 或 Remove，
> 保留 Record-first 与 Field-first 两个自然入口但统一 lowering；将 `_metadata()`
> 保留为 Soma、SomaGroup、Table 与 Field 共享的 canonical meta-operation
> namespace，并严格禁止 mutable metadata；SomaGroup 通过 generated Table
> identity 导航到唯一正常 Table facade，但不形成 Group-wide data operation。精确
> generated method/member spelling、保留名称与 replacement 在下一步单独裁决。

## D-012 SomaGroup root Table uniqueness

决策状态：Product Owner 已决定同一个 Group 可以包含多个不同 Table，但同一个 Table
只能有一个 root instance；尚未 promotion 为正式 Design 或授权实施

### “同一个 Table”的 identity

这里的“同一个 Table”不是某个 Java object reference，而是 generated root Table
contract identity。候选 identity 至少由 schema compatibility identity 与 logical
Table identity 共同确定，不能只比较易碰撞的 simple class name 或用户显示名称。

由此得到：

- 同一个 generated root Table 在一个 Group 内是 zero-or-one；
- 同一个 generated root Table 可以分别存在于两个不同 Group，不是 JVM-global
  singleton；
- 两个不同 schema Table definition 即使 row shape 相同，也仍是两个 Table；
- 若 application 确实需要两个不同业务角色，应定义两个不同 Table identity，或
  放入两个不同 Group，而不是给同一 Table 增加 instance alias。

### Group API 的直接结果

Group navigation 不再需要 application member instance id：

```java
somaGroup.transportTimes;
```

- 存在时返回该 Group 中唯一的 `TransportTimeTable` facade；
- 缺失时使用明确 absent result 或 stable structured failure，精确 contract
  后续裁决；
- Group plan/build/attach 若发现重复 Table identity，必须在 publish 前 fail
  closed；
- runtime 内部仍可使用 stable slot 实现 composition 与 resource ledger，但
  slot 不成为普通用户选择同一 Table 多实例的 public concept；
- Group metadata 对一个 Table identity 也只返回一个 root member observation。

### Owned Child Table 不属于 root uniqueness 计数

该唯一性约束作用于 `SomaGroup` 的 root membership。Owned Child Table 由 parent
record 的 child field 独占；不同 parent records 可以各自拥有同一 child Table definition
的不同 child instance。它们不是 Group root members，也不能通过 Group 根部以
平行路径选择：

```text
SomaGroup
    -> one root instance per generated root Table identity
        -> zero or more parent-row-owned child Table instances
```

若把“一 Table 一实例”扩展到所有 owned child，将与已经确认的 parent-owned child
模型冲突，因此当前决策不得作这种外推。

### 与当前正式 Design 的差异

当前正式 Design 和实现允许同一 root descriptor 通过不同 stable member slot
在一个 Group 中拥有多个 instance。D-012 明确替换这一目标方向，但本 Temporary
不直接修改正式 Design、generated API、runtime protocol 或 compatibility claim。

后续若获 implementation 授权，replacement closure 必须同时处理：

- `SomaGroupPlan` 中 duplicate root descriptor admission；
- `attach(group, memberId)` 等 multi-instance public/generated surface；
- Group member identity、metadata、GC reachability 与无隐藏 retention；
- compiler golden、external consumer、runtime ownership 与 structured failure
  evidence；
- 正式 Design、Implementation Map、Conformance 和 reference application 投影。

## D-013 Mutable Table、operation effect 与失效边界

决策状态：Product Owner 已确认 single-source selection pipeline 可以使用
Table-local Update/Remove terminal，并确认一次 selection 在单个
Table/ownership aggregate 内 all-or-nothing；D-008 已 superseded。精确 epoch、
row-lineage typestate 与 generated syntax 仍待裁决

### Table 可变与 Stream 可变是两个命题

SOMA Table 与 Java Stream 的差异必须说准确：

- Java Collection 本身也可以 `add/remove/replace`；
- Java Stream 是 Collection 之上的 non-interfering computation view；
- SOMA Table 不只是数据容器，还同时拥有 columnar storage、Key/Unique/Index、
  owned child、capacity、Group ownership 和 generated operation；
- 因此 SOMA Table 必须拥有 mutation capability，但这本身并不能逻辑推出
  `.stream()` 必须提供 mutation terminal。

两个可能方向是：

1. `.stream()` 继续只表达 query，所有 mutation 由 Table direct operation 承担；
2. `.stream()` 表达 record/field selection，并允许 query/update/remove 三类
   terminal。

Product Owner 已选择第 2 种方向，D-008 的永久 read-only 假设不再是当前目标。

### “是否破坏结构”需要拆成多个 effect

“Query/Update 不破坏结构，Add/Remove 改变结构”在“row domain 是否变化”这一维上
基本正确，但 `update` 不能笼统称为结构不变：

| Effect | 典型操作 | Record count / row position | 其他可观察变化 |
|---|---|---|---|
| Read-only query | filter、sum、find、forEach | 不变 | 无 Table state change |
| Payload update | 修改普通 payload field | 通常不变 | value/currentness 改变 |
| Access-structure update | 修改 Key/Unique/Index field、order field 或 owned-child reference | 通常不变 | locator、index membership/order、unique、ownership 可能改变 |
| Record-domain mutation | add、remove、clear | 变化或可能 relocation | row position、全部 access structure 与 selection 失效 |
| Capacity/layout mutation | reserve/growth | logical records 可不变 | backing layout 与 retained capacity 改变 |

因此更准确的语言是：

- `update` 通常 **row-domain preserving**，但不一定
  **access-structure preserving**；
- `add/remove/clear` 是 **row-domain changing**；
- `reserve/growth` 是 **physical-layout changing**；
- Group/Table 不提供 public lifecycle-terminating operation；不可达对象图由
  Java GC 回收。

编译生成层已知被修改 Field 的 schema role，可以在内部选择正确 preflight、
index maintenance、ownership cascade 和 epoch 变化；不要求普通用户手工分类。

### Stream terminal family 显式区分 effect

候选统一模型是：

```text
Table/Field source
    -> stateless/stateful intermediate*
        -> one terminal
            -> Query terminal
            -> Record-preserving Update terminal
            -> Record-domain-changing Remove terminal
```

概念 API：

```java
table.stream()
     .filter(predicate)
     .count();                  // Query

table.stream()
     .filter(predicate)
     .update(updater);          // row-domain preserving，可能维护 Index/Unique

table.stream()
     .filter(predicate)
     .remove();                 // row-domain changing

table.add(value);               // 没有 existing-record selection，仍是 Table direct operation
```

`add` 不自然地属于“遍历已有 rows 后的 terminal”，因此即使恢复 mutation
terminal，也应保留为 Table direct operation。Key/Unique point update/remove 也可
作为单行快捷路径，与 selection pipeline lowering 到同一个 mutation kernel。

Field stream 是否允许 `update`，必须受 record lineage 约束：

- direct Field projection、filter、sort、skip/limit 仍可保持 one-to-one source-record
  identity；
- distinct、group、aggregate、detached materialization 等丢失 one-to-one lineage
  后，类型上不得再出现 update/remove terminal；
- Field update 即使只写一个 logical field，也必须维护其 Key/Unique/Index/owned
  child role，不能绕过 Table invariant。

### Mutation terminal 不能边遍历边原地破坏 candidate set

执行模型必须是：

```text
bind current Table
    -> evaluate and freeze final candidate identity
        -> validate/stage mutation
            -> publish
                -> result or stable structured failure
```

- update/remove 不能在 predicate、sort 或 callback 遍历过程中立即改变来源；
- callback 不得重入同一个 Table/ownership aggregate 执行 add/update/remove；
- remove 必须在 candidate selection 完成后处理 relocation；
- update 必须基于 selection 前的 source semantics，不能因修改 Index field 而改变
  后续 candidate membership；
- concurrent/reentrant conflict 必须 serialize 或 fail closed，不能产生
  C++ 风格 undefined behavior；
- current Index、cursor、snapshot 或其他 epoch-scoped handle 在相关 mutation 后
  必须产生 stable stale failure，不能继续指向已 relocation 的 row。

因此“类似 C++ iterator invalidation”是有用的理解类比，但 SOMA 的目标应更强：
不把长期 live iterator 暴露给普通用户，并用 operation guard、epoch 与 structured
failure 把失效变成受控契约。

### D-008 已 superseded；D-006 只排除 public Batch

D-008 的“pipeline 永远 read-only，修改只能逐个 direct single-row operation”
已由本决策 supersede，不能再作为 current contract。

一个 filter 命中的多行 `update/remove` 在语义上就是 multi-row mutation，即使 API
不叫 `Batch`。Product Owner 已确认其 whole-selection all-or-nothing：

- callback、validation、Unique/Index/ownership 或 resource failure 时零行发布；
- 成功时整个 selection 一次发布；
- sequential/parallel 必须具有一致的结果、failure 和 atomic boundary；
- application 不创建、持有或 commit staging object。

因此 D-006 继续只排除 public Batch model，不排除 execution 为一次 atomic
terminal 使用 private bounded staging。仅用多次单行原子操作无法获得相同的
stable candidate semantics，也无法安全表达两个 Unique values 的原子交换。

当前整合结论是：

> 当前逻辑模型是“Query、row-domain-preserving Update、row-domain-changing
> Add/Remove”；Update 对 access structure 的影响和 reserve/capacity adjustment
> 由执行层分别处理。`.stream()` 可以暴露 Table-local update/remove terminal，
> 一次 selection all-or-nothing；用户不感知 candidate freeze、staging、
> preflight 或 publish。

## D-014 Soma 根层级、Group ownership、GC 与 point object API

决策状态：Product Owner 已确认 Soma/Group/Table/Field 层级、各级 `_metadata()`、
multiple Group、可显式取得且共享的 default Group、GC ownership，以及 point `find/get`
语义、`add(detachedObject)`，并确认 `@SomaValue` immutable、
`@SomaTable` mutable detached carrier 的复用边界；V1 不提供 `trimToSize()`。
default Group 的最终入口和 nested Record/Editor/Stream contract 由 D-015
继续收紧；`update(detachedObject)` 是基于这些决定形成的当前候选签名，等待进一步评估

### 唯一逻辑层级

逻辑对象层级确认收敛为：

```text
Soma
    -> SomaGroup
        -> Table
            -> Field
                -> nested Field
```

对应的只读 metadata namespace 是：

```text
Soma/Group/Table/Field
    -> _metadata()
```

候选 generated navigation：

```java
SomaGroup active = Soma.createGroup();
SomaGroup backup = Soma.createGroup();

TransportTimeTable transportTimes = active.transportTimes;
MachineStateTable machineStates = active.machineStates;

active.transportTimes.machinePair.fromMachine.value._metadata();
```

`Soma` 与 `SomaGroup` 必须由 compiler-known composition 生成，不能变成依赖
reflection/metadata interpreter 的 generic registry。`Soma._metadata()` 也不得为
枚举 live Group 而建立 JVM-global strong-reference registry。

### 每个 Table 都属于一个 Group

显式 Group 中同一 root Table identity 仍只有一个实例，但不同 Group 可以分别拥有
该 Table：

```java
active.transportTimes != backup.transportTimes;
```

普通场景不再由 Table 自己创建隐式 Group，而是访问 generated `Soma` 持有的
default Group；其唯一入口、identity、ClassLoader scope 和禁止的 predecessor
surface 统一由 D-015 定义，本节不保留第二份 accessor contract。

Owned Child Table 的直接 owner 仍是 parent record，但它传递地属于 parent 所在的同一
Group，不能 share、reparent 或跨 Group。

### Java GC 是唯一 public lifecycle

Group/Table 不提供 `release()`、`close()` 或 `AutoCloseable`。显式创建的 Group
对象图不可达后，JVM GC 回收 facade、metadata snapshot 之外的 live state 与底层
heap arrays；default Group 的 application-lifetime retention 由 D-015 定义。

这一决定要求 runtime：

- 显式 `Soma.createGroup()` 创建的 Group 在 application object graph 不可达后
  才可由 GC 回收；
- 不以 background thread、永久 ThreadLocal 或长期 observation 意外强引用显式
  Group；
- callback、candidate scratch 与 private staging 不得逃逸 operation；
- 不承诺对象不可达后立即归还 heap，回收时间仍由 JVM 决定。

仍然可达的 Table 通过 `reserve/size/capacity` 管理容量。Product Owner 已决定
V1 不提供 `trimToSize()`：它不是使用 SOMA 的必要能力，不应为理论上的收缩增加
API、reallocation failure 和性能心智负担。

### Schema object 只作为 detached boundary value

为了避免 NewRow builder/callback 抽象，已确认的 add 是：

```java
transportTimes.add(new TransportTime(pair, 18L));
```

SOMA 在 operation boundary 读取并拆列输入对象，完成 validation 后一次发布；它
不把 `TransportTime` carrier 作为 live storage 保存。`@SomaValue` 继续展开，
ordinary Object field 只复制 reference slot。

两类 Schema object 的构造与可变性必须明确区分：

| Schema object | 构造与可变性 | 允许的复用 |
|---|---|---|
| `@SomaValue` | compiler 生成 canonical 全字段 constructor、final Field、value equality/hash；无默认构造 | 同一个值可共享；不能改写为另一个值 |
| `@SomaTable` | mutable detached carrier；目标 API 使用 application 所需的全字段 constructor，不要求默认构造 | 可在同步 operation 之间完整改写并复用 |

`@SomaValue` application source 不手写 canonical constructor；processor/javac
plugin 负责生成。`get/find` 反向 materialize `@SomaTable` carrier 时，processor
必须在编译期绑定明确的 Table constructor；当前候选是按 logical top-level Field
declaration order 匹配全字段 constructor。精确 overload/schema-evolution
diagnostic 仍待裁决，但不得用 reflection、`Unsafe` 或重新强制默认构造函数。

这里形成一条候选产品边界原则：

> application 面向普通 Java/OOP 语义对象；SOMA 内部由编译生成层 lowering 为
> data-oriented columnar storage 与专门化执行。

这比“所有 SOMA 外部代码都必须是 OOP”更准确。application 仍可自然使用
primitive、array、lambda、immutable value 和普通 Java 控制流；OOP 是 SOMA 的
主要语义/集成边界，不是强迫执行热路径 materialize object 的实现规则。

由于 `add(value)` 是同步 operation，且返回前已读取并拆列 detached carrier，
application 可以在顺序导入中复用同一个 mutable `@SomaTable` carrier。该复用
必须满足：

- 不改写 immutable `@SomaValue`；需要不同值时给 Table carrier 重新赋入另一个
  value object；
- 每次 add 前完整覆盖全部逻辑字段；
- 前一次 add 返回后才能修改 carrier；
- 同一 carrier 不得并发用于多个 operation；
- primitive 与 flattened `@SomaValue` 已复制；
- ordinary Object field 只复制 reference slot，复用 carrier 不等于 deep-copy
  referent。

对象复用是 application-owned optimization，不是 SOMA Batch 或 object-pool
contract。短生命周期对象通常也是 JVM 擅长处理的形状；没有 profile/benchmark
证据时，不把复用或对象池作为默认最佳实践。若百万级导入证明 allocation 成为
瓶颈，先比较 application-owned reuse；仍不足时才评估 generated field-argument
overload，V1 不维护第二套 canonical add API。

point read 也返回 detached materialization：

```java
Optional<TransportTime> found = transportTimes.find(pair);
TransportTime required = transportTimes.get(pair);
```

- `find` 使用标准 `Optional` 表达 absence；
- `get` 在 missing 时产生 stable structured failure；
- `get` 不返回 `null`，也不插入 default row；
- 高性能 primitive-only hot path 仍应使用 Field projection，避免整行
  materialization。

### Point update 不挂在 Optional 上

以下两个要求不能同时在 Java 类型系统中成立：

```java
Optional<TransportTime> found = transportTimes.find(pair);
transportTimes.find(pair).update(replacement);
```

标准 `Optional` 不拥有 `update`。为了支持第二种拼写而引入自定义
`OptionalRowRef`/live handle，会重新暴露 currentness、relocation 与结构失效，
违背本专题降低用户心智负担的目标。

当前推荐是 Table direct replacement：

```java
UpdateResult result =
    transportTimes.update(new TransportTime(pair, 24L));
```

replacement 自带 `@SomaKey`；missing fail closed，不 upsert，不 rekey，不保留输入
carrier。若 application 先 `find` 再决定是否更新，使用普通 Java 控制流再次调用
Table direct `update`。

### 与正式 Design/实现的差异

当前正式 Design、generated API 与 runtime 仍包含显式 lifecycle、旧 Group
composition 和 predecessor mutation surface。D-014 只建立目标决策，不宣称这些
差异已经实现。

后续若获 implementation 授权，replacement closure 至少包括：

- generated `Soma` / `SomaGroup` composition 与 package/naming；
- manual release/attach/multi-instance predecessor 退出；
- detached add/get/find/update materialization contract；
- generated `@SomaValue` canonical constructor/final/no-default contract；
- compile-time `@SomaTable` materialization constructor mapping；
- application-owned mutable Table carrier reuse correctness/performance evidence；
- structured missing/duplicate/resource failure；
- no hidden retention、independent Group、Corretto 8 consumer 与性能证据。

## D-015 default Group 快捷入口与 nested Record API

决策状态：Product Owner 已确认 default Group 是 generated Soma composition 的
共享默认实例，确认 `Soma.defaultGroup()` 与 schema-specific
`Soma.<tableType>()` 快捷入口，并确认 Table stream 使用 nested
`Record` / `Editor` / `Stream` public generated contract；尚未 promotion 为正式
Design 或授权实施

### default Group 与默认 Table 访问

普通单 Group 场景使用：

```java
TransportTimeTable transportTimes = Soma.transportTimeTable();
```

需要完整 composition、metadata 或第二个 Group 时使用：

```java
SomaGroup active = Soma.defaultGroup();
SomaGroup backup = Soma.createGroup();

TransportTimeTable activeTimes = active.transportTimes;
TransportTimeTable backupTimes = backup.transportTimes;
```

以下 identity 是当前候选约束：

```java
Soma.transportTimeTable()
    == Soma.defaultGroup().transportTimes;

Soma.transportTimeTable()
    == Soma.transportTimeTable();

Soma.defaultGroup()
    == Soma.defaultGroup();

Soma.defaultGroup()
    != Soma.createGroup();
```

`Soma.transportTimeTable()` 是 pure typed forwarding accessor，不创建或缓存第二个
Table，不通过 reflection 或 runtime registry 查找，也不因重复调用抛异常。
`Soma.createGroup()` 才表示创建新的 ownership aggregate。

因此目标 API 中不存在：

```java
TransportTimeTable.create();
TransportTimeTable.defaultInstance();
Soma.setDefaultGroup(...);
Soma.resetDefaultGroup();
```

这使“创建第二个同 identity Table”的错误在 generated surface 上不可表达，而不是
依赖调用顺序、全局竞争或第二次调用的运行时异常。default Group 是稳定 ownership
anchor，不是可切换的“当前 active Group”指针；双缓存中的 active/backup 角色由
application variable 管理。

generated `Soma` 可以用 thread-safe static initialization 或 lazy holder 发布
default Group，但这只是初始化安全，不改变 Table 的 concurrency contract。
singleton 的精确 Java scope 是每个 generated Soma composition、每个 ClassLoader
一个实例；default Group 的 static strong reference 是一个有界、typed 的
application-lifetime Owner，不是 live Group registry。

### Record、Editor 与 Stream 的可见性

用户语义不再使用容易把心智带到物理行列布局的顶级
`TransportTimeRow` / `TransportTimeMutableRow` / `TransportTimeRowStream`。
每个 generated Table 把三个最小 contract 收纳为 nested public type：

```java
TransportTimeTable.Record
TransportTimeTable.Editor
TransportTimeTable.Stream
```

职责是：

| Nested type | Contract |
|---|---|
| `Record` | callback-scoped、只读 logical record accessor |
| `Editor` | callback-scoped、受 schema mutation rule 约束的 logical record editor；可读取当前值 |
| `Stream` | finite、single-source、one-shot Table record pipeline |

`Editor` 是 `Record` 的 capability refinement：概念 Java 形状为
`Editor extends Record`，读取 getter 只定义一次，Editor 只补充 schema 允许的
mutation operation。精确 Key/rekey policy 仍由后续独立决策约束。

`Record` 与 `Editor` 的 implementation、row position、cursor、column accessor 和
staging object 继续完全 internal。`TransportTime` 仍是 detached OOP carrier，
不能与 callback-scoped `Record` 混为同一个对象或允许后者逃逸 operation。

正常 Java 8 调用不写出以上类型名：

```java
Soma.transportTimeTable()
    .stream()
    .filter(record -> record.transportMinutes() > 30L)
    .update(editor ->
        editor.transportMinutes(
            Math.addExact(editor.transportMinutes(), 5L))
    );
```

Java 编译器根据 `filter` 和 `update` 的 generated callback target type 推断
`record` 与 `editor`；用户不需要 import、构造或实现 nested contract。只有保存
pipeline、编写 helper method 或检查 generated API 时才显式写出：

```java
TransportTimeTable.Stream stream = table.stream();

private static boolean isSlow(TransportTimeTable.Record record) {
    return record.transportMinutes() > 30L;
}
```

因此它们是“public 但普通路径低感知”的 generated semantic contract，不是
package-private implementation。若 public method 的返回或 callback signature
引用不可访问类型，Java 8 consumer 无法解析链式方法或 lambda member，因此不能
用 package-private type 假装完全隐藏。

### Replacement 与验证义务

正式实施必须在同一 replacement 中：

- 退出 `TransportTimeTable.create()` 和每次调用创建隐式 Group 的 predecessor；
- 生成 `Soma.defaultGroup()` 与每个 root Table 的 typed default accessor；
- 保证同 Group 同 Table identity 唯一，不建立 runtime service locator；
- 将顶级 Row/MutableRow/RowStream surface 替换为 Table-nested
  Record/Editor/Stream，隐藏 implementation；
- 用 generated source、`javap` 和独立 Corretto 8 consumer 固定 public
  accessibility 与 lambda inference；
- 验证 repeated default access identity、显式 Group isolation、ClassLoader scope、
  no reset、GC reachability、callback non-escape 与 one-shot Stream failure；
- 更新正式 Blueprint/Design、Implementation Map、Conformance、指南和三个
  reference application，不长期保留新旧两套入口。

## 跨决策当前约束

以下是 D-001～D-015 交叉后形成的当前目标，不再由任一单独章节平行定义：

- 普通用户只面对 annotation、`Soma -> SomaGroup -> Table -> Field`、Table direct
  operation、`.stream()` 和 `_metadata()`；
- default 路径是 `Soma.<tableType>()`，独立状态由 `Soma.createGroup()` 创建；
  每个 Group 内每个 root Table identity 唯一；
- Table stream 是 finite、single-source、lazy、one-shot pipeline，以 Query、
  Update 或 Remove 结束；多 Table coordination 由普通 Java 控制流承担；
- public logical element 是 Table-nested `Record` / `Editor`，physical row position、
  Column、cursor、staging 和 plan 不进入普通用户模型；
- Table direct operation 拥有 add、point read/update/remove 与 capacity；V1 无
  public Batch、通用 binary relation/join 和 Segment；
- primitive leaf always-present/zero，reference leaf nullable；ordinary Object 的
  referent state 由 application 维护；
- storage 拥有 authoritative columns 与 access structure，执行层拥有 selection、
  staging 和 scheduling；执行层不得成为第二份 live state；
- selection Update/Remove whole-selection all-or-nothing，direct repeated add 允许
  已成功前缀保留；overflow、conflict 和 resource failure 必须 fail closed；
- `TransportTime` 等 schema object 只作为 detached OOP boundary value，不能成为
  hot storage 或 callback-scoped live Record；
- 性能专门化在编译生成、存储和执行层完成，不要求用户选择 physical access path，
  也不以牺牲上述语义换取局部 benchmark 数字。

## 待裁决清单与专题退役条件

### Generated surface 与命名

1. generated `Soma` / `SomaGroup` 的 composition declaration、package、Table/Field
   保留名和 compile-time collision diagnostic；
2. Field-first 与 Record-first projection 的精确 syntax；
3. nested `Record` / `Editor` getter/setter、callback functional interface 和
   direct Field source 的精确 generated signature；
4. Key、Unique、Index 的自然 API、Table contract identity、duplicate/missing
   failure 与 `@SomaIndex` 多行 encounter order；
5. Soma/Group/Table/Field metadata facade 的最小 capability 与返回类型。

### Schema、storage 与 ownership

1. `@SomaValue` outer null 是否禁止；
2. zero-default Key 是否接受，还是 authoring/operation boundary 仍要求显式赋值；
3. ordinary Object 可以参与哪些 equality/order/Key/Index operation；
4. owned child 的 physical reference slot、absence 和 operation failure boundary；
5. predecessor `@SomaOptional` / presence surface 的 replacement；
6. detached `@SomaTable` materialization constructor 的 compile-time 映射与
   evolution。

### Mutation、currentness 与结果

1. content/access/layout currentness 使用统一 mutation epoch 还是分离 epoch；
2. keyed/dense Table 的 point update/remove、`clear`、`UpdateResult` /
   `RemoveResult` 精确 signature；
3. 已确认 atomic boundary 之上的 stable failure code、stale identity 和 reentrant
   behavior；
4. Field stream 是否保留 `removeRows()` convenience，以及失去 record lineage 后的
   capability narrowing。

### Replacement 与 evidence

1. predecessor public concepts 的 hide/retire mapping 和内部 module boundary；
2. repeated add、application-owned mutable carrier reuse、flat growth peak 与新
   Stream execution 的 correctness/performance evidence；
3. 三个 reference application 的完整表达、可读性和性能验证；
4. generated source、`javap`、独立 Corretto 8 consumer 与 negative fixture。

专题收口前，本文件不替代任何正式 Design。收口时应将获批结论原子 promotion 到
唯一 Blueprint/Design Owner；未实施差异进入 Conformance；随后更新实现、Map、
consumer evidence、产品投影和 reference applications，验证 replacement closure，
最后删除本 Temporary。任何待裁决项都必须被正式决定或明确进入后续 active topic，
不能通过把本文件长期保留为平行事实源来规避收口。
