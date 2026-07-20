# SOMA Java 系统架构

类型：Design

状态：候选

Owner：SOMA Java 系统架构

服务蓝图：[SOMA Java 产品蓝图](../blueprints/soma-java-product-blueprint.md)

事实范围：模块职责、编译链、运行时分层、依赖方向和公共边界

非事实范围：schema 字段语义、runtime 具体算法、代码位置和构建过程

最后审查日期：2026-07-19

## 1. 总体数据流

```text
Java 8 source + SOMA annotations
  -> javac 8 @SomaValue lowering
  -> JSR 269 processing / validation / normalization / schema hash
  -> schema-specific generated facade
  -> annotation-agnostic generated-runtime protocol
  -> handwritten runtime kernel and packed columns
```

编译期拥有 schema 理解和代码生成；运行期只消费预绑定的 typed layout、runtime plan 和生成代码，不重建 metadata interpreter。

## 2. 模块职责

| 模块 | 设计职责 | 禁止承担 |
|---|---|---|
| `soma-annotations` | public schema vocabulary | processing、storage 或 runtime logic |
| `soma-processor` | javac 8 integration、validation、normalization、hash、generation | live runtime state |
| `soma-runtime-core` | storage、lifecycle、plan、errors、diagnostics 和机械性能 primitive | application schema interpretation |
| `soma-testkit` | compile/golden/invariant/evidence helpers | production runtime shortcut |
| `soma-examples` | canonical Java 8 scenarios 和 Access Pattern | 发明核心语义 |
| `soma-benchmarks` | benchmark 方法、runner 和 evidence artifact | 用结果反向静默改写设计 |

Production consumer 的最小 runtime 依赖由 `soma-annotations`、`soma-processor` 和 `soma-runtime-core` 构成。testkit、examples 和 benchmarks 是验证面，不进入生产运行时依赖。

## 3. 接口分层

### 3.1 Public schema/API

Application 只使用 annotation、handwritten runtime configuration/error/value types 和生成 facade。公共签名不泄漏 column、bucket、RowSlot、raw handle 或 runtime internal protocol。

### 3.2 Generated facade

每个 schema 生成 table、batch、cursor/mutator、selector、column view 和 child facade 等类型。它们是 schema-specific contract，不使用反射解释字段。

### 3.3 Generated-runtime protocol

生成代码通过 `com.hgtech.soma.runtime.generated` 的窄 typed protocol 绑定 runtime kernel。该 package 是 generated artifact 的版本化协议，不是 application API/SPI。

### 3.4 Internal implementation

`com.hgtech.soma.runtime.internal` 和 processor internal type 可以自由优化，但不能泄漏到 public/generated signature，也不能成为外部扩展点。

## 4. Runtime aggregate

一张 generated table 的运行时组成概念上包括：

```text
TableStore
  -> RowSpace
  -> PrimaryLocator       // keyed only
  -> ColumnStore
  -> ExactAccessStructures
  -> AccessPath / IndexBuffer
  -> MutationCoordinator
  -> Lifecycle / Stats
  -> ChildOwnershipRegistry when needed
```

这些组件共同维护一个 table fact，不是多个可独立提交的数据源。Root table 与所有 owned child 构成一个 lifecycle/ownership aggregate。

## 5. 依赖方向

- annotation 不依赖 processor 或 runtime；
- processor 可以理解 annotation 和生成 runtime protocol binding；
- runtime-core 不依赖 application schema、examples 或 processor implementation；
- examples/benchmarks 可以消费 production modules；production modules 不反向依赖它们；
- testkit 只能提供验证能力，不得让 production path 在测试环境下拥有额外语义；
- 未经正式设计，不增加第三方 runtime 依赖。

## 6. 应用边界

SOMA 拥有单 table/ownership aggregate 的 storage correctness。Application 拥有业务数据角色、跨表一致性、专用数据结构、策略、external DTO/wire mapping、持久化和并发协调。
