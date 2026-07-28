# SOMA Java 系统架构

类型：Design

状态：正式

Owner：SOMA Java 系统架构

设计层次：`D1` 系统结构

主要关注点：模块边界、编译/运行时分层与依赖方向

上位设计：[设计宪法](soma-java-design-constitution.md)

服务蓝图：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

事实范围：模块职责、编译链、运行时分层、依赖方向和公共边界

非事实范围：schema 字段语义、runtime 具体算法、代码位置和构建过程

最后审查日期：2026-07-28

## 1. 总体数据流

```text
Java 8 source + SOMA annotations
  -> javac 8 @SomaValue lowering
  -> JSR 269 processing / validation / normalization / schema hash
  -> immutable Descriptor Metadata + schema-specific generated facade
  -> mutable-before-freeze Plan Builder
  -> immutable RuntimePlan / SomaGroupPlan / Effective Metadata
  -> implicit or explicit Group + root ownership aggregates
  -> annotation-agnostic generated-runtime protocol
  -> handwritten runtime kernel and packed columns
  -> typed Access / Transformation Definition
  -> reusable analyzed Template
  -> bound one-shot Invocation + resource preflight
  -> Eager Detached Result, scoped callback delivery or safe-point Effect
  -> module-owned immutable Observation / Explain
```

编译期拥有 schema 理解和代码生成；运行期只消费预绑定的 typed layout、runtime plan 和生成代码，不重建 metadata interpreter。

系统由 `State/Owner + Capability + Plan/Lifecycle` 三个轴描述。V1 Capability Set
封闭为 Schema/Metadata、Storage/Layout、Access、Mutation、Exact/Relation、
Transformation、Execution、Result Delivery、Resource 与 Observation/Failure。
这是一张责任 taxonomy，不要求每项都成为 public interface。内部实现只能在
compiler/create/compile/bind/operation boundary 选择 concrete strategy；V1 不提供
ServiceLoader、开放 SPI 或 application-supplied physical strategy。

## 2. 模块职责

| 模块 | 设计职责 | 禁止承担 |
|---|---|---|
| `soma-annotations` | public schema vocabulary | processing、storage 或 runtime logic |
| `soma-processor` | javac 8 integration、validation、normalization、hash、generation | live runtime state |
| `soma-runtime-core` | metadata/plan control plane、Group、storage、lifecycle、errors、runtime observation 和机械性能 primitive | application schema interpretation、DataFlow strategy |
| `soma-dataflow` | typed expression/result、Definition/Template/Invocation、binding/guard coordination、planner/kernel、Invocation resource/并行和 DataFlow diagnostics | annotation interpretation、live storage、Group topology 或 application control flow |
| `soma-examples` | 聚合三个独立 Java 8 reference consumer；应用各自拥有领域 Blueprint、Design 和 integrated evidence | 产出领域共享 JAR、共享领域模型或发明核心语义 |
| `soma-benchmarks` | 领域中性的 component benchmark、runner 和 evidence artifact | 依赖参考应用 schema或用结果反向静默改写设计 |

只使用直接 storage/access 的 production consumer 运行时依赖 `soma-runtime-core`；完整 typed transformation consumer 还依赖 `soma-dataflow`。`soma-annotations` 与 `soma-processor` 参与编译生成；`tests/fixtures`、examples 和 benchmarks 是验证面，不进入生产运行时依赖。`tests/fixtures` 是仓库级证据资产，不是 module 或可发布 artifact。

## 3. 接口分层

### 3.1 Public schema/API

Application 只使用 annotation、handwritten runtime configuration/error/value types 和生成 facade。公共签名不泄漏 column、bucket、RowSlot、raw handle 或 runtime internal protocol。

### 3.2 Generated facade

每个 schema 生成一个 `SchemaMetadata` companion，以及 Table、Batch、Scan、
Cursor/UpdateCursor、selector point/source、Key/Column Traversal、ColumnView、
child facade 和每 Table 一个 DataFlow companion。它们是 schema-specific
contract，不使用反射解释字段，也不按 `operator × Table` 展开 executor。

### 3.3 Generated-runtime protocol

生成代码通过 `io.github.somaruntime.soma.runtime.generated` 和 `io.github.somaruntime.soma.dataflow.generated` 的窄 typed protocol 绑定 storage kernel 与 transformation execution。它们是 generated artifact 的版本化协议，不是 application API/SPI。

### 3.4 Internal implementation

`io.github.somaruntime.soma.runtime.internal` 和 processor internal type 可以自由优化，但不能泄漏到 public/generated signature，也不能成为外部扩展点。

Transformation 的 internal logical/physical plan、kernel、scratch 和 adaptive strategy 同样不进入 public SPI。DSL 是 authoring surface，不是公开 IR。

## 4. Runtime aggregate

runtime state 先由 Group composition，再由独立 ownership aggregate 构成：

```text
SomaGroup
  -> stable member slots + GroupLedger
  -> zero or more root ownership aggregates
       -> TableStore
            -> RowSpace / ColumnStore
            -> PrimaryLocator / ExactAccessStructures
            -> AccessPath / Candidate scratch
            -> MutationCoordinator
            -> Lifecycle / Runtime Metadata / Observation
            -> ChildOwnershipRegistry when needed
```

这些组件共同维护一个 table fact，不是多个可独立提交的数据源。Root table 与所有 owned child 构成一个 lifecycle/ownership aggregate。

Group 是可选 composition/resource/release Owner；simple Table create 使用
single-root implicit Group。需要共同 logical identity、resource envelope 和 release
的 roots 使用 explicit `SomaGroupPlan`。同一个 Group 可以包含多个 schema 和同一
root descriptor 的多个 member instance。Read-only DataFlow 仍可以跨 Group/schema/
instance；Invocation 是 alias de-dup、guard 排序/acquire/release 的唯一 Owner。

## 5. 依赖方向

- annotation 不依赖 processor 或 runtime；
- processor 可以理解 annotation 和生成 runtime protocol binding；
- runtime-core 不依赖 application schema、examples 或 processor implementation；
- dataflow 只依赖 runtime-core，不依赖 annotation、processor、example、benchmark 或第三方 runtime；
- runtime-core 的 Metadata/Observation 不引用 Candidate、relation、scheduler 或
  Result Delivery type；dataflow 只组合 runtime observation component，禁止反向
  模块依赖；
- reference applications/benchmarks 可以消费 production modules；production modules 不反向依赖它们，benchmark 也不依赖 reference application domain；
- `tests/fixtures` 只能提供验证输入与 golden，不得让 production path 在测试环境下拥有额外语义；
- 未经正式设计，不增加第三方 runtime 依赖。

## 6. 应用边界

SOMA 拥有 Group composition/lifecycle/resource、单 ownership aggregate storage
correctness，以及 application 独占边界内的 typed local transformation execution。
Application 拥有业务数据角色、object sidecar/registry、rule/control graph、跨表
提交一致性、专用数据结构、external DTO/wire mapping、I/O、持久化、恢复和并发
协调。
