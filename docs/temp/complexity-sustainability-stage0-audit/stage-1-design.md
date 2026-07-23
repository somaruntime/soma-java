# 复杂度可持续性 Stage 1 详细设计

类型：Temporary

状态：active（approved implementation design）

Owner：SOMA Java 复杂度可持续性 Stage 1

事实范围：Stage 1 的内部责任设计、实施切片、非回归约束、验证和停止点

非事实范围：长期产品语义、正式实现地图、性能 claim、public release readiness

正式事实源：否

实施授权：有（仅限本文三个 slice）

起点：Stage 0 immutable commit `e69eec2`

最后审查日期：2026-07-23

## 1. 意图

Stage 1 只处理 Stage 0 已确认的责任问题和 measurement 缺口，不以减少 LOC、文件数或 Gate 数量为目标。

```text
Blueprint / Design
  -> preserved executable capability
  -> clearer implementation ownership
  -> equally strong or stronger evidence
```

实施不得把 typed workload、schema-specific codegen 或 runtime 原子协调换成 generic framework。每个 slice 必须在单独提交时已经正确，不依赖后续 slice 才成立。

## 2. 全局非回归边界

以下内容必须逐字节或逐语义保持：

- annotation Schema、normalized schema/hash、public/generated API 和 runtime protocol；
- Candidate Scan source/stage/terminal、ordering、callback、one-shot、stats 与 failure 语义；
- 四场景目标、20 个 smoke lane、lane workload identity、artifact record schema 与 `claimAllowed=false`；
- benchmark setup/warmup/measurement、checksum、aggregation invariant 与 lane-specific validation；
- existing 33-table fixed-candidate code-size Gate、阈值和基线；
- runtime `DenseTableState`、`ChildOwnershipRegistry` 代码与状态协议。

禁止引入第三方依赖、反射、metadata interpreter、Java Stream hot path、boxed/generic lane DSL 或仅为缩短源码而增加 per-candidate indirection。

## 3. Slice A：Benchmark lane 责任治理

### 3.1 问题

当前 `SmokeLaneSuite` 同时拥有 manifest、运行编排、workload、evidence 组装、validation 和 aggregation；`BenchmarkModel` 又反向调用该类的 validator，形成双向责任依赖。

### 3.2 目标结构

```text
BenchmarkSmokeRunner
  -> SmokeLaneSuite                 // compatibility facade + run orchestration
       -> SmokeLaneContract         // manifest, identity, metadata, validation
       -> SmokeLaneWorkloads        // typed workload and fixture execution
       -> SmokeLaneEvidence         // LaneObservation evidence assembly
       -> SmokeLaneAggregation      // repeated-measurement merge
       -> BenchmarkModel.record

BenchmarkModel.validateArtifact
  -> SmokeLaneContract
```

依赖规则：

- `SmokeLaneContract` 不依赖 `BenchmarkModel`、runner 或 workload implementation；
- `SmokeLaneWorkloads` 不拥有 required-lane manifest 或 artifact validation；
- `SmokeLaneAggregation` 只合并 `LaneObservation`，不决定 lane identity；
- `BenchmarkModel` 不再依赖 `SmokeLaneSuite`；
- `SmokeLaneSuite` 保留既有 package-local alias/delegate，以保持测试入口和 JSON schema 中现有 `x-soma-laneBinding` 不变，但不再拥有对应事实。

### 3.3 保留策略

- 不删除、合并或改名任何 lane；
- 不改变 lane 执行顺序、iteration seed、workload 方法体和 evidence map；
- 不引入 inheritance hierarchy、service registry、反射或 Map-driven dispatch；
- workload 继续直接使用 concrete generated/runtime types；
- 只移动责任并增加防回归 source-shape check。

### 3.4 验收

- valid artifact 仍为 20 records；
- 当前全部 negative artifact cases 继续失败；
- smoke JSONL 与 schema 保持既有字段、顺序和 lane binding；
- `BenchmarkModel` 源码不出现 `SmokeLaneSuite`；
- `SmokeLaneSuite` 不再包含具体 workload、validation 或 aggregation 实现。

## 4. Slice B：Processor emitter 所有权治理

### 4.1 Selector public shape

Selector 的 public 参数分组同时影响 signature admission 与 emission，因此属于 codegen model，而不是 source emitter support。

目标依赖：

```text
SomaProcessor admission
  -> DenseSelectorCodegenModel

Table / Exact emitters
  -> DenseSelectorCodegenModel
  -> DenseSelectorSourceSupport
```

`DenseSelectorCodegenModel` 只拥有：

- selector parameter/group binding；
- canonical public parameter type sequence；
- leaf-to-parameter mapping所需的 immutable codegen facts。

它不生成 source、不读取 processing environment，也不拥有 runtime exact-index 算法。

### 4.2 Scan/Table source helper

- `appendPackedTableTerminalExecutors` 移入 `DenseScanExecutionSourceSupport`；它是写入 Table artifact 的 Scan execution source fragment，不属于 Auxiliary artifact emitter；
- `appendSourceLeafArguments` 移入 `DenseSelectorSourceSupport`，作为 selector source argument emission helper；
- `DenseTableSourceEmitter` 不再依赖 `DenseAuxiliarySourceEmitter`；
- `DenseTableSourceEmitter -> DenseExactIndexSourceEmitter` 暂时保留，Stage 0 没有证明该 artifact-internal composition 错误。

### 4.3 Generated-byte 约束

本 slice 只移动 hand-written processor ownership。相同 schema 必须生成 byte-identical：

- Java sources；
- schema JSON 与 SHA-256；
- class/public manifest；
- external consumer behavior。

Codegen admission checker必须新增依赖方向检查，但不得更改 admission limits。

## 5. Slice C：Generated footprint evidence

### 5.1 双层模型

保留当前 Gate：

```text
fixed 33-table candidate
  -> aggregate source/class/nested count
  -> Stage 1 baseline + 15% regression ceiling
```

新增 diagnostic，不替换或放宽 Gate：

```text
artifact footprint
  -> each Scan source/class family

schema footprint
  -> table / field / physical leaf / selector counts
  -> generated source and Scan source totals

aggregate envelope
  -> min / max / average artifact size
  -> largest artifact vs processor admission owner
```

### 5.2 Evidence 输出

`check-scan-code-size.sh` 在原 properties 旁新增：

- `scan-artifact-footprint.tsv`：每个 Scan 的 source bytes/lines、top-level class、nested count/bytes 与 family bytes；
- `schema-footprint.tsv`：每个 schema 的 table/field/physical-leaf/selector 数量与生成源码规模；
- properties 中增加 min/max/average 和 feature totals，明确 `fixed-candidate-gate` 与 `normalized-diagnostic` 的不同角色。

旧 aggregate 指标和阈值继续输出；容易误读的 `topClassBytes` 保留为 compatibility alias，并新增准确的 `topLevelClassBytes`。

### 5.3 解释边界

归一化数据用于定位增长来源，不把相关性冒充单 feature 的因果系数。Stage 1 不：

- 重设 15% 基线；
- 抽取 generated executor 到 generic runtime；
- 新增 public API；
- 宣称已覆盖 256-table/256-leaf compiler envelope；
- 用单次 compile wall-clock 建立性能 claim。

## 6. Runtime retain 裁决

本 Stage 1 不修改 runtime 状态机。保留 Stage 0 事件触发条件：

> 只有未来至少两次独立 metrics/resource 变化持续绕开 mutation coordinator，才重新审查 satellite state。

Runtime 源码、protocol 和 benchmark 均不得因本专题发生结构变化。

## 7. 实施与提交顺序

```text
A benchmark responsibility
  -> benchmark unit/artifact/smoke validation
  -> independent commit

B processor ownership
  -> codegen admission + byte-stable/golden/external validation
  -> independent commit

C footprint evidence
  -> code-size diagnostic + full Gate
  -> independent commit

scope non-regression review
  -> stop for Owner review
```

如果任一 slice 需要改变 public/generated/schema/runtime semantics、删除 lane、降低 Gate 或引入依赖，立即停止并请求项目 Owner 决定。

## 8. 最终审查材料

Stage 1 完成后在本 Temporary 中补充：

- 实际文件与依赖变化；
- before/after responsibility map；
- generated byte stability 与 benchmark artifact evidence；
- full Gate 结果；
- 未实施事项和 residual risk；
- 是否建议正式固化。

未经新的明确授权，不修改正式 Implementation Map/Engineering/Report，不删除本 Temporary。
