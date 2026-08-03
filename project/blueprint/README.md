# SOMA Java V1 产品蓝图

类型：Blueprint

状态：Active V1 Baseline

正式事实源：是

Owner：SOMA Java V1 产品意图、用户模型、能力边界与成功标准

最后审查日期：2026-08-03

## 1. 文档责任

本文回答：SOMA V1 最终希望成为什么、面向谁、怎样被使用、哪些能力必须共同成立，
以及什么证据出现后才可以声称产品可用。它不拥有 exact Java signature、存储算法、
optimizer rewrite、并行调度或构建命令。

精确合同由[正式 Design](../design/README.md)分责拥有；当前实现与合同的一致程度由
[Conformance](../conformance/README.md)拥有。README、未来 Manual、White Paper 与
Examples 都是角色投影，不得成为第二份 Blueprint 或 Design。

## 2. 产品定义

SOMA 是嵌入 Java application、运行在单 JVM 进程内、面向 schema-known mutable
Tables 的编译式列式计算引擎：

> Application 使用自然 Java object、generated Table/Field/Index API 和 Stream-like
> pipeline 表达 point、scan、aggregate、group 与 binary relation；processor 把 schema
> 转换为类型安全的专门化 API；runtime 把 pipeline lowering 为 logical plan，经优化后
> 在 chunked column storage、Key/Index 与压缩表示上执行。

```text
Object-oriented application boundary
    -> compiler-generated typed surface
        -> logical IR and semantics-preserving optimizer
            -> specialized execution over data-oriented state
```

SOMA 解决的核心矛盾是：高性能计算需要紧凑、可预测、适合扫描、索引和关系计算的
内存布局，而 application developer 仍希望使用自然、类型安全的 Java API，不直接管理
physical Column、row position、Chunk、scratch、planner 或 worker。

## 3. North Star 与阶段目标

- 长期 North Star：一亿行以上、编译式、支持关系计算的单进程 Table 引擎；
- 第一阶段资格目标：百万行数据上的高效、低分配、资源受控操作；
- 一亿行是架构不得封死的愿景，不是没有 implementation/profile 时预先承诺的 Release
  Gate；
- 所有 logical size、capacity、position、count 与 cardinality 使用 `long`；
- 第一条 production storage 路径就必须是 chunked、long-domain、IR-driven，不能先用
  flat `int` engine 再承诺重写；
- 量化吞吐、内存与规模 Gate 只能由真实 implementation、reference scenario 和 profile
  固定。

## 4. 目标用户与场景

V1 优先服务 Java 8 进程内场景：

1. 调度与优化算法维护的大规模动态状态；
2. 仿真中的事件、实体状态与确定性步骤；
3. 实时派工中的高频 point lookup、Index candidate selection 与关系计算；
4. 其他结构稳定、值频繁变化、对 CPU/内存边界敏感的 runtime-state computation。

SOMA 面向 library consumer，不要求用户把完整业务逻辑迁入 SOMA。Application 仍拥有：

- 跨 Table 编排、顺序与补偿；
- ordinary Object referent 的不变性与线程安全；
- 日期/时间到 primitive/Value 的业务编码；
- 外部 I/O、side effect、transaction 与业务状态机。

Application是同一JVM中的受信任参与者，SOMA不是sandbox。正式封装合同约束supported Java
source/API与未篡改的generated/runtime artifact；使用`setAccessible`、`Unsafe`、instrumentation/
agent或自造/改写bytecode突破Java访问控制，不属于SOMA的进程内隔离承诺。

## 5. 四层产品模型

### 5.1 用户语义层

普通用户只需要理解：

- schema declaration；
- generated `Soma`、`SomaGroup`、Table、Field、Index 与 typed pipeline；
- direct source、point operation、intermediate、terminal 与 detached result；
- sequential default、显式 `parallel()`；
- structured failure 与 advanced `_metadata()` / `_explain()`。

### 5.2 编译生成层

Compiler 负责 schema discovery、类型/命名验证、generated object/API、Value flattening、
expression node、Key/Index equality/hash 与 runtime linkage。非法能力应尽可能从 generated
type 缺席，不依赖 reflection 或 runtime universal dispatch。Java 8不能seal跨package generated
marker；application手写的foreign marker/IR实现不是SPI，必须在operation lifecycle开始前由hidden
owner/provenance验证拒绝。

### 5.3 存储层

Table authoritative state 由 long-domain StateRoot、chunked primitive/reference leaves、
Key、Index、compression representation、size/capacity/version 与 managed-memory accounting
组成。Detached object、Collection graph、callback View 与 scratch 不是长期 authoritative
state。

### 5.4 执行层

执行层拥有 pipeline binding、logical planning、resource admission、sequential/bounded
parallel scheduling、mutation staging、atomic publish 与 failure arbitration。内部实现不能
成为普通用户必须操作的第二套模型。

## 6. 核心认知模型

### 6.1 对象层级

```text
Soma
    -> SomaGroup
        -> Table
            -> Field
                -> nested Field
```

同一 Group 中每种 generated Table type 恰好一个 instance；不同 Group 状态隔离。Default
Group 提供普通场景最短路径，显式 Group 用于双缓存或独立状态副本。

### 6.2 单一执行主线

```text
reusable source
    -> zero or more lazy intermediate operations
        -> one terminal
            -> detached result or controlled Table-local publication
```

Table、Field、IndexSelection 直接是 reusable source，不要求先调用 `stream()`。一旦产生
linked pipeline，它就是 lazy、finite、one-shot；terminal-start 时绑定当前 published
state。

### 6.3 Logical 与 physical

```text
Table membership selection
    -> logical Field/Value projection
        -> typed logical IR
            -> physical leaf/chunk kernel
```

Composite Value 即使在内部展开为多个 leaf，用户仍把它作为一个 logical Field。Physical
Column、Chunk、locator 与 codec 不进入普通 API。

### 6.4 关系模型

1:M/N:M 使用普通 Table、endpoint ID 与双向 Index 表达。SOMA 不建立 ChildTable、ownership
graph、foreign key、cascade 或 cross-Table transaction。

V1正式支持同一Group两个不同generated Table type的typed Equality Join与typed GroupBy；不形成
arbitrary multi-way planner，也不提供relation alias/self-Join。关系计算是query-only，跨Table
mutation仍由application编排。

## 7. 与 Java Stream 的关系

SOMA 学习 Java Stream 的 source/intermediate/terminal、lazy、one-shot、familiar naming、
sequential default 与 explicit parallel，但不是 Stream replacement。

| 维度 | Java Collections / Stream | SOMA |
|---|---|---|
| Authoritative state | application collection/source | `SomaGroup` 持有 schema-defined Tables |
| 元素 | 通用 object/primitive element | Table View、logical Field、Index、Join Pair |
| 类型形成 | generic library + lambda | processor 依据 schema 生成 exact capability |
| 访问路径 | iterator/spliterator | Table、Key、Index、Field、relation |
| 存储 | 不规定 | chunked data-oriented primitive/reference leaves |
| Mutation | pipeline 通常不维护 source structure | point operation 与 Selection atomic mutation |
| Planning | library/source specific | typed IR、Predicate IR、optimizer、reference oracle |
| 并行 | source/pool/collector共同决定 | Group guard、resource admission、bounded participation |
| 失败 | Java/source-specific exception | stable structured failure + zero publication |

SOMA 不复制 universal `Stream<T>`、arbitrary Spliterator、Collector/reduce ecosystem、
generic `flatMap` 或“pipeline 永远只读”的假设。额外复杂度只能来自 SOMA 真正拥有的
Table state、Index、relation、resource 与 atomicity。

## 8. V1 Blueprint requirements

| ID | 必须成立的产品能力 |
|---|---|
| BP-1 | 普通路径只暴露自然 Java object、generated typed API 与 Stream-like operation，不暴露 physical storage/runtime protocol |
| BP-2 | Package schema 在编译期决定合法 Field role、type、Key/Index、generated object 与 capability；非法能力从generated type缺席，手写foreign marker/IR不能绕过owner/provenance validation |
| BP-3 | 同一 Group 每种 Table type 一个 instance；default/explicit Group 身份明确且不同 Group 隔离 |
| BP-4 | Authoritative Table state 使用 long-domain chunked data-oriented storage，不被单个 Java array 或 `int` row domain 限制 |
| BP-5 | Table/Field/Index 是 direct reusable source；linked pipeline lazy、finite、one-shot，terminal-start binding |
| BP-6 | Point mutation 与 Selection mutation保持单 Table all-or-nothing、zero partial publication；Key immutable |
| BP-7 | 提供 typed aggregate、GroupBy 与 same-Group binary Equality Join，并保持明确 null、duplicate、order 与 cardinality合同 |
| BP-8 | Typed Field/Relation expression进入 Logical IR；optimizer只能做语义等价 rewrite，并由 sequential reference interpreter 差分裁判 |
| BP-9 | Sequential 是默认；`parallel()`显式、同步、资源有界，并与sequential保持相同logical result、order、numeric、mutation与non-resource failure semantics；parallel-specific resource/interrupt failure必须显式fail closed |
| BP-10 | Global managed-memory admission覆盖 retained 与 temporary peak；compression透明、默认 AUTO、无隐式 spill |
| BP-11 | Normal absence/no-op 与 contract failure 分离；failure stable、machine-readable、fail-closed、worker quiescent |
| BP-12 | Primitive hot path不因统一抽象被迫boxing；View/Editor数量为 O(1)/O(P)，不是 O(N) |
| BP-13 | Application拥有跨 Table transaction、ordinary referent、external side effect 与业务补偿；SOMA不伪装拥有它们 |
| BP-14 | Processor/runtime采用明确artifact与full-regeneration合同；不存在stale/partial composition或reflection fallback |
| BP-15 | 可用性、性能、兼容性与release声明必须由相称implementation、scenario、security、package evidence证明 |

## 9. Reference user experience

```java
@SomaTable(defaultCapacity = 4_096L)
final class TransportTime {
    @SomaKey MachinePairKey machinePair;
    @SomaField long transportMinutes;
}
```

```java
TransportTimeTable times = Soma.transportTimeTable();
MachineStateTable states = Soma.machineStateTable();

times.reserve(1_000_000L);
times.add(new TransportTime(pair, 18L));

Optional<TransportTime> found = times.find(pair);

long count = times
        .filter(times.transportMinutes.gt(30L))
        .count();

UpdateResult delayed = times
        .filter(times.transportMinutes.gt(30L))
        .update(editor -> editor.transportMinutes(
                Math.addExact(editor.transportMinutes(), 5L)));

GroupedLongResult<MachineId> totals = times
        .groupBy(times.machinePair.fromMachine)
        .sum(times.transportMinutes);

long combined = times
        .join(states)
        .on(times.machinePair.fromMachine, states.machineId)
        .inner()
        .filter(states.enabled.eq(true))
        .mapToLong(pairView -> Math.addExact(
                pairView.left().transportMinutes(),
                pairView.right().availableMinute()))
        .sum();
```

SOMA-owned aggregate/arithmetic按Numeric合同checked；application callback中的Java算术仍由
application拥有，因此参考用法在需要fail-closed时显式使用`Math.addExact`。其异常按
`CALLBACK_FAILED`保留safe cause，不伪装成SOMA-owned `ARITHMETIC_OVERFLOW`。

普通用户不需要显式管理 physical Column、View constructor、row position、planner、pool、
Chunk 或 release token。

## 10. V1 capability boundary

V1 包含：

- package-level `@SomaSchema` composition 与 full regeneration；
- generated immutable Value、mutable detached Table object 与 typed facade；
- default/explicit Group；optional Key 与多个 non-unique exact Index；
- direct Table/Index/Field source、typed expression 与 callback；
- point add/find/get/update/remove、Selection update/remove；
- query operation、typed aggregate、GroupBy、Equality/Cross Join；
- sequential/explicit parallel；
- long-domain chunked on-heap storage、transparent AUTO/OFF compression；
- detached materialization、structured failure、`_metadata()` 与 `_explain()`。

V1 明确不包含：

- database service、ORM、SQL/DataFrame、persistence、WAL 或 distributed execution；
- ChildTable、`@SomaChild`、foreign key、cascade、cross-Table transaction；
- generic multi-way/non-equality/range/as-of/interval Join 或 Right Join convenience；
- Batch/Loader、Segment、public Column、backend SPI、off-heap/mmap implementation；
- implicit spill、background compression 或 hidden parallel；
- generic reduce/collect/flatMap、window、approximate aggregate、prepared query；
- per-pipeline Executor、runtime pool replacement、manual release/close；
- predecessor compatibility layer、legacy module或predecessor public
  `DataFlow`/`Transformation`/`Candidate` model。

Loader、off-heap 与 mmap 只保留未来 additive seam；没有 public placeholder。

## 11. 质量属性

### 11.1 可理解与类型安全

Generated type表达合法 capability；不支持的操作缺席。普通用户不需要同时理解逻辑模型与
physical execution model。

### 11.2 可预测资源

已知 cardinality、memory、array/container、arithmetic 与 task peak在不可逆工作前准入。
默认memory budget由freeze时的versioned conservative policy自动决定，不把固定比例写成
兼容合同；生产环境可显式配置。

### 11.3 确定性

相同terminal-start state与deterministic callback下，sequential/parallel产生相同result、
order、mutation与非资源型failure。Table没有稳定业务顺序；依赖first/tie的业务必须显式
sort。

### 11.4 Fail closed

Schema、resource、arithmetic、concurrency、callback、planner或publish失败不留下partial
Result、partial Table state或损坏Index。真实JVM `Error`不伪装为普通failure。

### 11.5 可持续优化

Public API不依赖array identity、codec、hash、build side或backend。Optimizer/codec/scheduler
可依据profile演进，但不能改变logical contract。

## 12. 三个产品旅程

正式实施与资格验证必须覆盖：

1. **调度**：Job/Machine/Option关系、Key/双向Index、typed Join、候选选择与分阶段状态发布；
2. **仿真**：显式事件顺序、detached decision、point mutation与已消费事件删除；
3. **实时派工**：小批待派工Job、Index缩窄、Predicate IR/Join、低分配决策循环与application
   compensation。

如果任一场景不能只用公开API自然表达，必须回到Design治理，不能增加example-only hidden API。

## 13. 成功标准

只有以下事实同时成立，SOMA 才可投影为“可用产品”：

1. BP-1至BP-15均有唯一Design Owner；
2. production compiler/runtime完成surface admission并在真实Java 8独立consumer中工作；
3. generated API、negative capability、storage、optimizer、mutation、parallel、failure、
   security与package Gate通过；
4. reference interpreter与optimized sequential/parallel差分成立；
5. 三个产品旅程达到经profile固定的百万行资格目标；
6. documentation、Examples、package与release claim不超过evidence；
7. Conformance无未披露blocking deviation。

当前仓库没有production implementation，因此本Blueprint不声明API可用、性能达标、artifact、
compatibility或release readiness。

## 14. 下游入口

- [Design 总览](../design/README.md)
- [核心抽象、叙事与不变量证明链](../design/core-abstractions-and-narratives.md)
- [Production Implementation Plan](../engineering/v1-implementation-plan.md)
- [Conformance](../conformance/README.md)
