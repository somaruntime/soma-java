# SOMA Java V1 产品蓝图

类型：Blueprint

状态：Active Baseline

正式事实源：是

Owner：SOMA Java V1 产品意图、用户模型、能力边界与成功标准

最后审查日期：2026-08-01

## 1. 文档责任

本文回答：SOMA Java V1 最终希望成为什么、面向谁、怎样被使用，以及哪些边界必须
长期保持。它是正式 Design 的上游，不拥有具体存储算法、generated Java signature、
并行调度实现或构建命令。

精确合同由[正式 Design 体系](../design/README.md)拥有；实现与 Design 的符合程度由
[Conformance](../conformance/README.md)拥有。根 README、未来开发手册、白皮书和
Examples 都是面向角色的投影，不得重新定义本文。

## 2. 产品意图

SOMA 是面向 Java application 中大规模、频繁变化的进程内 runtime state 的
schema-defined、compiler-specialized Table library。

Application 使用自然的 Java 值、对象、注解和 generated typed API 表达业务语义；
SOMA 在内部把 logical Table/Field lowering 为 data-oriented storage 和专门化执行。

```text
Object-oriented application boundary
    -> compiler-generated lowering
        -> data-oriented SOMA storage and execution
```

SOMA 要解决的核心矛盾是：高性能计算希望获得紧凑、可预测、适合扫描和索引的内存
布局，而 application developer 仍希望在业务边界使用类型安全、可理解的 Java API，
不直接管理物理 Column、row position、cursor、scratch 或调度器。

## 3. 目标用户与场景

V1 优先服务以下 Java 8 进程内场景：

- 调度与优化算法维护的大规模动态状态；
- 仿真过程中高频读取、筛选和更新的实体状态；
- 实时派工、规则计算和其他对内存布局及 CPU 使用敏感的状态计算；
- 需要 exact Key、non-unique Index、typed Field traversal 和受控并行执行的
  application。

SOMA 面向 library consumer，而不是要求用户把业务逻辑迁入 SOMA。算法、业务规则、
跨 Table 编排、补偿和外部对象不变量仍属于 application。

## 4. 四层产品模型

### 4.1 用户语义层

用户只需要理解：

- Java schema declaration；
- generated `Soma`、`SomaGroup`、Table、Field 与 typed Stream；
- Table direct operation；
- `source -> intermediate operations -> terminal -> result/state change`；
- detached result、structured failure 和可选的 library-wide parallel execution。

### 4.2 编译生成层

Compiler 负责验证 schema、生成 typed API、展开 logical Value、选择合法 capability，
并把逻辑操作 lowering 为 storage/execution access。非法类型、命名冲突和不支持的
capability 应尽可能在编译期失败，而不是依赖 reflection 或运行期万能接口。

### 4.3 存储层

Table 的 authoritative live state 由 primitive/reference leaf arrays、Key、Index、
size、capacity 和内部 currentness 组成。Detached Java object、Collection graph、
callback cursor 和执行 scratch 都不是长期 authoritative state。

### 4.4 执行层

执行层拥有 selection、intermediate operation、terminal、resource admission、
sequential/bounded-parallel scheduling、mutation staging、atomic publish 和 stable
failure mapping。执行细节不能成为普通用户必须操作的第二套模型。

## 5. 核心认知模型

### 5.1 对象层级

```text
Soma
    -> SomaGroup
        -> Table
            -> Field
                -> nested Field
```

一个 Group 可以包含多个 Table；同一 Group 中每种 generated Table type 恰好一个
instance。Default Group 为普通场景提供最短路径，显式 Group 用于双缓存或多份隔离
状态。

### 5.2 单一执行模型

```text
数据源
    -> 零个或多个中间操作
        -> 一个终止操作
            -> detached 结果或受控 Table-local 状态变化
```

这个模型借鉴 Java Stream 的 lazy pipeline、中间操作和 terminal，但 SOMA 的 source
是 Table record selection 或 logical Field，且 Update/Remove 可以修改来源 Table。

### 5.3 Source 与 Field

```text
Record Selection
    -> Logical Field/Value Projection
        -> Physical Column Access
```

Whole Table 和 Index 产生 Record selection；Field-first 与 Record-first 都是 logical
projection；physical Column 完全退出普通用户 API。Composite Value 即使在内部展开
为多个 leaf arrays，用户仍把它作为一个 logical Field 使用。

### 5.4 Table 间关系

1:M 和 N:M 使用普通 composition Table、endpoint ID 和 Index 表达。SOMA 不建立
ChildTable、Table ownership graph、foreign key、implicit cascade 或 cross-Table
transaction。统一关系模型用少量 endpoint storage 换取单一 Table lifecycle 和单一
执行模型。

### 5.5 与 Java Collections / Stream 的关系

SOMA 学习 Java Stream 的用户认知，不把自己叙述为 Stream replacement 或“任何场景都
更快的 Stream”。两者的根本差异是：

| 维度 | Java Collections / Stream | SOMA |
|---|---|---|
| Authoritative state | 任意 collection/source 由 application 持有 | schema-defined Table 由 SOMA Group 持有 |
| 元素模型 | 通用 object/primitive stream element | Record selection、logical Field、Index 和 flattened leaf |
| 类型形成 | library generic + application lambda | processor 依据 schema 生成 exact typed capability |
| Access path | iterator/spliterator 与 application structure | whole Table、Key、non-unique Index、Field projection |
| Mutation | Stream pipeline 通常不维护 source structure | Table direct add/point operation；Record selection 可 atomic Update/Remove |
| Storage | 不规定 collection 物理表示 | data-oriented primitive/reference leaf arrays |
| 并发边界 | 由 source/collector/application共同决定 | Table-local admission + library-wide bounded parallel terminal |
| 失败 | Java exception 与 source-specific behavior | stable structured failure + zero partial publication |

SOMA 应坚持从 Java Stream 借鉴：

- `source -> intermediate -> terminal -> result` 的单一主线；
- lazy、finite、one-shot pipeline；
- familiar 的 `filter/map/sorted/skip/limit/findFirst/min/max/toList/toArray` 命名；
- sequential 默认、parallel 显式 opt-in；
- intermediate 与 terminal 的清晰边界；
- 尽量用类型缺席表达不支持，而不是 runtime surprise。

SOMA 不复制 Java Stream 的 universal `Stream<T>`、arbitrary source/Spliterator、Collector
生态、generic reduce/flatMap 或“pipeline 只读”假设。SOMA 的额外复杂度必须只来自它
真正拥有的 Table storage、Key/Index、mutation atomicity 和 predictable execution，不能
向用户暴露另一套 planner/Column/runtime 心智模型。

因此 V1 的产品叙事是：

> 当 application 需要长期持有、反复索引/扫描/更新大量进程内状态时，SOMA 用 schema
> 和编译生成把自然 Java 业务模型 lowering 为紧凑、类型安全、可预测的 Table 执行；
> 对小集合、一次性转换、任意对象流、数据库查询或跨 Table transaction，继续使用
> Java Collections/Stream、数据库或 application OOP 更合适。

## 6. V1 Blueprint requirements

| ID | 必须成立的产品能力 |
|---|---|
| BP-1 | Application 在普通路径只面对自然 Java object、generated typed API 和 Stream-like operation，不面对 physical storage/protocol |
| BP-2 | Schema 在编译期决定合法类型、Field role、Key/Index 和 generated capability；非法能力从 API 缺席 |
| BP-3 | Group/Table identity 单一且可预测：同 Group 每种 Table type 一个 instance，不同 Group 完全隔离 |
| BP-4 | Table 使用 data-oriented authoritative state，并支持 whole Table、Index、Field 和 point access 的统一 lowering |
| BP-5 | Query、Update 和 Remove 使用 finite、single-source、one-shot pipeline；mutation 保持 Table-local all-or-nothing |
| BP-6 | Sequential 是明确默认；parallel 是显式 opt-in、资源有界、同步完成且与顺序路径逻辑等价 |
| BP-7 | 正常 absence/no-op 与 contract failure 分离；失败具有稳定、machine-readable、fail-closed 语义 |
| BP-8 | Primitive hot path 不因统一抽象被迫 boxing；Record/Editor/Value View 对象规模保持 O(1)/O(P) 而非 O(N) |
| BP-9 | Application 拥有跨 Table 编排、ordinary Object referent、日期时间编码和外部 side effect；SOMA 不伪装拥有这些状态 |
| BP-10 | 实现、性能和发布声明必须由相称 evidence 证明；Design 本身不构成 production/release claim |

## 7. V1 reference user experience

Schema 由专用 `.schema` package 声明，application 使用 processor 在父 package
生成的 public object 与 Table API：

```java
@SomaTable(defaultCapacity = 4096)
final class TransportTime {
    @SomaKey MachinePairKey machinePair;
    @SomaField long transportMinutes;
}
```

普通使用路径应保持紧凑：

```java
TransportTimeTable times = Soma.transportTimeTable();

times.add(new TransportTime(pair, 18L));

Optional<TransportTime> found = times.find(pair);
TransportTime required = times.get(pair);

long count = times.stream()
        .filter(record -> record.transportMinutes() > 30L)
        .count();

UpdateResult delayed = times.stream()
        .filter(record -> record.transportMinutes() > 30L)
        .update(editor ->
            editor.transportMinutes(
                Math.addExact(editor.transportMinutes(), 5L)));
```

普通用户不需要显式书写 generated `Record`、`Editor` 或 Stream type，也不需要释放
Group/Table、持有 iterator、选择物理 Index implementation 或管理 per-stream pool。

## 8. V1 capability boundary

V1 包含：

- package-scoped schema composition；
- generated immutable Value 与 mutable detached Table object；
- default/explicit Group；
- optional unique Key 与多个 non-unique exact-match Index；
- whole Table、Index、Field source；
- Record、Field、Mapped Stream capability narrowing；
- Query、Table-local Update 与 Record-selection Remove；
- explicit sequential/parallel source；
- typed detached materialization；
- stable Result 与 structured failure；
- advanced、read-only `_metadata()` namespace。

V1 明确不包含：

- ORM、数据库、SQL/DataFrame 或 distributed execution；
- ChildTable、`@SomaChild`、foreign key、cascade 或 cross-Table transaction；
- generic join/binary relation、Batch、Segment、public physical Column；
- Transformation/DataFlow model、workflow or solver VM；
- generic `flatMap`、Collector、async/Future、infinite source；
- per-Stream Executor、runtime pool replacement 或隐藏并行；
- reflection-driven runtime schema；
- manual `release()`/`close()`；
- predecessor compatibility layer、legacy module 或双 API。

详细的 API absence 与技术边界由各 Design Owner 定义。

## 9. 质量属性

### 9.1 类型安全与可理解性

Generated type 应表达合法 capability；普通 Java 用户通过 familiar 的 object、Field
和 Stream-like API 完成工作。不能为了内部通用性把 storage/execution abstraction
泄漏到用户层。

### 9.2 性能可预测性

V1 优先避免 hot-path reflection、per-record DTO、primitive boxing collection、
per-record task 和无界 scratch/task growth。真实性能必须在 production implementation
出现后通过调度、仿真和实时派工 reference scenario profile 证明。

### 9.3 确定性

相同 terminal-start state 与 deterministic callback 下，顺序和并行路径应得到相同
logical result、order、mutation 和非资源型 failure。并行不得把 worker completion
race 变成产品语义。

### 9.4 Fail closed

Schema、resource、arithmetic、concurrency、callback 或 publish 失败时，不得留下
partial Result、partial Table state 或损坏的 Key/Index。真实 JVM `Error` 不被伪装为
普通可恢复 failure。

### 9.5 生命周期与资源边界

Group/Table 由 Java reachability 与 GC 管理；SOMA parallel resource 为 library-wide
且只接受 `ForkJoinPool`。Application-owned pool 和 ordinary Object referent 的生命
周期仍由 application 管理。

## 10. 成功标准

Blueprint 只有在下列事实同时成立时才能投影为“可用产品”：

1. 正式 Design 对 BP-1 至 BP-10 都有唯一 Owner；
2. production compiler/runtime surface 经过独立 surface admission；
3. generated Java 8 consumer、negative capability、runtime correctness、concurrency、
   failure、build integration 与 security/supply-chain Gate 通过；
4. 三个 reference scenario 证明表达力与性能适用边界；
5. 用户文档、Examples、包内容和 release claim 与正式事实一致；
6. Conformance 没有未裁决或未披露的阻断差异。

当前仓库尚无 production implementation，因此本文不声明 API 可用、性能达标、兼容性、
artifact 或 release readiness。

## 11. 下游事实入口

- [Design 总览](../design/README.md)
- [Schema 与编译生成](../design/schema-and-generation.md)
- [数据模型与存储](../design/data-model-and-storage.md)
- [逻辑层 API](../design/logical-api.md)
- [Generated Java API Signature](../design/generated-api-signatures.md)
- [执行、并发与并行](../design/execution-and-concurrency.md)
- [结果与失败](../design/results-and-failures.md)
- [Production Implementation Architecture](../design/implementation-architecture.md)
- [Production Implementation Plan](../engineering/v1-implementation-plan.md)
- [Conformance](../conformance/README.md)
