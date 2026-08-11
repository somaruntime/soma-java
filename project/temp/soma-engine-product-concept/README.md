# SOMA Engine 产品构思与初步设计

状态：`QUEUED_PRODUCT_CONCEPT / INTENT_ONLY / NOT_ACTIVE / NOT_BLUEPRINT / NOT_DESIGN`

日期：2026-08-11

Owner：未来 SOMA Engine 的产品意图、初步能力模型与对SOMA Core的消费约束

> 本文保存Product Owner与Codex已讨论形成的产品构思，不授权module、artifact、dependency、
> JSON API、Workflow runtime或implementation。SOMA Engine不是当前Canonical IR治理的交付物；
> 当前正式产品仍是SOMA Java V1及其Java generated frontend。

## 1. 产品定义

SOMA Engine是未来建立在SOMA Core之上的嵌入式声明式状态计算引擎：

> 它绑定一套已编译、已冻结的SOMA composition，运行在application同一JVM中，以versioned JSON
> command/workflow document作为主要操作入口，完成schema-bound validation、planning、optimization
> 和execution，并返回structured result/failure。

它不能独立构成最终业务应用，需要集成到RTD Dispatcher、Scheduling、Simulation或其他薄应用层。
上层application继续拥有领域模型、external I/O、业务状态机、权限、补偿和跨系统一致性。

本产品替代此前“Declarative Engine + Computation Engine”两层设想：不再额外建立一款通用
Computation Engine。候选产品层级是：

```text
RTD Dispatcher / Scheduling / Simulation / Thin Application
                         │
                         ▼
                    SOMA Engine
       JSON command + computational workflow language
                         │
              validate / bind / lower / plan
                         │
                         ▼
                     SOMA Core
       Canonical IR / optimizer / execution / storage
```

SOMA Engine在产品责任上可以是独立产品，在部署上可以是同JVM可选module/artifact。Core不得反向依赖
Engine。

## 2. 候选用户入口

运行期只提供一个主要operation入口：

```java
public interface SomaEngine {
    SomaEngineResult execute(String commandDocumentJson);
}
```

初始化与operation分离：

```java
SomaEngine engine = SomaEngine.create(configuration);
SomaEngineResult result = engine.execute(commandDocumentJson);
```

以上只是产品形态示例，不是正式Java signature。是否返回Java result、JSON response、streaming sink
或多种投影，需要未来Blueprint/Design根据真实consumer裁决。

## 3. 三阶段配置与运行模型

### 3.1 构建期：Schema configuration

Product Owner已确认配置文件生成Table需要区分构建期与运行期。推荐Schema配置在构建期进入SOMA
schema compiler：

```text
Schema configuration
    -> validate composition/Table/Field/Key/Index/type
        -> compile generated composition/layout/binder
            -> package with application
```

这样保留compiler specialization、primitive hot path、无reflection和提前diagnostic。第一阶段不因
SOMA Engine引入runtime arbitrary schema或运行时代码生成。

### 3.2 启动期：Runtime configuration

启动配置候选责任包括：

- composition identity；
- memory budget；
- application-owned parallel executor；
- initial capacity；
- compression policy；
- optional initial data source。

Engine实例创建后schema冻结；运行期command不能改变Table/Field/Index definition。

### 3.3 运行期：Command/Workflow document

`execute()`接收versioned JSON document，先完成bounded parse、schema/type/capability binding与
resource preflight，再交给planner/execution。

## 4. Primitive command document

第一版候选限制是：一个primitive document只表达一种command family。

```text
Add Document
Remove Document
Update Document
Query Document
Explain Document
```

候选envelope：

```json
{
  "version": "1",
  "kind": "query",
  "requestId": "request-1001",
  "composition": "dispatcher",
  "table": "machineStates",
  "payload": {}
}
```

JSON描述logical intent，不指定physical Index、Join algorithm、Chunk、worker、codec或locator。
SOMA Engine与Core optimizer决定怎样执行。

## 5. Workflow language

SOMA Engine需要支持`SOMA Computational Workflow Language`。它面向SOMA内部数据计算与状态维护，
不是通用企业工作流或分布式编排平台。

候选Workflow document自身只有一个`kind = workflow`；每个node仍恰好属于一种operation family：

```json
{
  "version": "1",
  "kind": "workflow",
  "nodes": [
    {
      "id": "select-machine",
      "kind": "query",
      "payload": {}
    },
    {
      "id": "update-operation",
      "kind": "update",
      "inputs": {
        "machineId": {
          "$from": "select-machine",
          "$path": "machine.machineId"
        }
      },
      "payload": {}
    }
  ]
}
```

JSON数组顺序不是默认执行顺序。Workflow planner依据显式数据边、控制依赖、Table read/write set、
state visibility、failure与resource合同形成可执行DAG，并在语义等价时重排、融合或并行。

## 6. Node result作为下游输入

Node可以把前序node的结果作为typed input。Result reference同时形成数据依赖：

```text
Producer typed output
    -> data edge
        -> Consumer typed input
```

每个output至少拥有：

- logical value type；
- shape：scalar、optional scalar、record、optional record、row set、grouped result或mutation result；
- cardinality：exactly one、zero-or-one或zero-or-many；
- null/missing contract；
- schema/Field lineage。

Workflow必须在任何node执行前验证result path、type、shape与cardinality compatibility。结构化
`$from/$path`引用优于字符串插值或运行时expression eval。

中间结果不通过JSON重新序列化。Planner可以：

- 把scalar放入workflow value slot；
- 融合单consumer pure query；
- 对fan-out结果选择一次materialization或经证明等价的重算；
- 在stateful/mutation boundary后受资源准入地物化；
- 在最后consumer完成后立即释放ephemeral result。

Mutation后依赖它的query必须在publication之后绑定新StateRoot。Result dependency不自动提供跨node
或跨Table rollback。

## 7. 优化与重排

SOMA Engine的关键目标不是顺序解释JSON，而是把完整document编译为计划：

```text
JSON document
    -> bounded parse / version validation
        -> schema/type/capability binding
            -> Workflow / Operation IR
                -> dependency + read/write analysis
                    -> semantics-preserving rewrite
                        -> resource admission
                            -> Physical Execution DAG
                                -> deterministic execution
```

重排边界候选：

| 场景 | 候选规则 |
|---|---|
| pure typed query operator | 可按Core Design重写、下推、融合 |
| 无依赖只读node | 可重排；并行仍受Group与resource合同限制 |
| 不同Group独立operation | 可并行 |
| 同一Group operation | 可规划重排；当前Core guard不允许任意重叠执行 |
| producer-result consumer | 不得跨越data edge |
| mutation后读取同一Table | 必须在publication后绑定 |
| 同Key冲突mutation | 必须reject或由未来语义显式定义 |
| external side effect | 不由SOMA optimizer推断重排 |

Optimization只能改变成本，不能改变logical result、state visibility、failure、atomicity或deterministic
output。

## 8. Set-based mutation延后裁决

Product Owner已确认：homogeneous batch add/remove/update的set-based logical/physical plan在SOMA
Engine基础功能完成后再单独治理和准入。

目标候选是：

```text
one homogeneous mutation document
    -> full schema/type/Key/conflict validation
        -> capacity/Column/Key/Index planning
            -> batch execution
                -> one Table-local publication
```

在该能力正式成立前：

- 不增加current Core public Batch/Loader；
- 不创建placeholder；
- 不把repeated point mutation宣传为batch atomicity或等价性能；
- row-set result不能静默lower为大量point mutation并声称整体all-or-nothing。

基础阶段可以优先支持scalar/optional result到point mutation；row-set到atomic batch mutation等待该
专题关闭。

## 9. Atomicity与failure边界

候选基本规则：

- 一个Core point/Selection operation继续遵守current Table-local atomicity；
- 未来一个正式set-based mutation node可以承诺单Table一次publication；
- Workflow中的多个node不自动形成cross-Table transaction；
- 已成功发布的上游mutation不因下游failure自动回滚；
- producer failure使dependent node不可执行；
- independent branch的取消、quiescence与primary failure需要未来Workflow Design确定；
- Error、resource、callback与structured failure不得因JSON包装而改变Owner或伪装成成功结果。

## 10. 初步能力边界

候选基础能力：

- build-time schema configuration；
- embedded same-JVM Engine instance；
- versioned JSON command/workflow document；
- schema binding与structured diagnostic；
- query、explain与existing point/Selection operation；
- acyclic workflow node、typed result reference、dependency analysis；
- semantics-preserving planning与deterministic execution。

尚未准入：

- runtime arbitrary schema；
- arbitrary Java class/method/callback invocation；
- general loop、timer、human task；
- external HTTP/database step；
- durable workflow persistence与crash recovery；
- automatic retry/compensation/exactly-once；
- cross-Table transaction；
- remote/distributed execution；
- public/internal IR serialization compatibility；
- set-based mutation；
- 第三production artifact或新dependency。

这些边界是当前构思，不是永久排除；未来必须由真实consumer、Blueprint与evidence逐项准入。

## 11. 与当前Canonical IR治理的关系

已正式晋升的[Canonical Logical IR与执行引擎Design](../../design/planning-and-optimization.md)只交付
SOMA Core内部IR与execution boundary治理。SOMA Engine在其中仅作为未来consumer，提供以下约束：

- Java generated facade不能成为IR唯一identity Owner；
- Canonical IR必须typed、immutable、data-only；
- frontend lowering、terminal binding与physical execution必须分层；
- host-bound callback必须可识别；
- future frontend不能要求复制optimizer/reference/execution语义。

当前IR治理不设计本文件的JSON grammar、Workflow runtime或Engine public API。

## 12. 后续产品化路径

候选顺序：

```text
1. 完成SOMA Core Canonical Logical IR与执行引擎一体化治理
2. 单独建立SOMA Engine产品构思/Blueprint治理
3. 完成JSON/config/schema binder可行性验证
4. 裁决artifact/repository/API/result/lifecycle
5. 实现基础query/point-operation Engine
6. 独立准入set-based mutation
7. 实现typed computational workflow
8. 以RTD Dispatcher等真实产品进行资格验证
```

在第2步正式启动前，本文保持product concept，不更新current Blueprint、Design、Engineering、
Conformance或artifact topology。
