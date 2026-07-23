# 复杂度可持续性 Stage 0 审计记录

类型：Temporary

状态：active（Stage 0 evidence）

Owner：SOMA Java 复杂度可持续性 Stage 0 审计专题

事实范围：四个限定关注点在 commit `2ca309b` 的代码、生成物、Git 变化和 Gate 证据

非事实范围：正式长期设计、已批准实施方案、跨环境性能声明或 release readiness

正式事实源：否

实施授权：无

审查环境：Azul Zulu OpenJDK `1.8.0_492-b09`，macOS aarch64，Maven Wrapper `3.9.16`

审查方法：source/生成物规模分析、依赖追踪、Git co-change、责任映射与现有专项 Gate 重放

最后审查日期：2026-07-23

## 1. 判定口径

- **confirmed**：当前代码或 Git 变化直接证明存在责任集中、逆向依赖或测量缺口；
- **signal**：规模或形态值得观察，但尚不能证明需要重构；
- **not supported**：现有证据不支持实施，默认保持当前结构；
- **question**：只有进入获授权的 Stage 1 后才能裁决。

Stage 0 不把任何 finding 转化为实施任务。

## 2. Generated footprint 可持续性

对应正式 Owner：

- [Schema 与生成 API](../../design/schema-and-generated-api.md)；
- [Access Model 与 Candidate Scan](../../design/access-model-and-candidate-scan.md)；
- [性能模型](../../design/performance-model.md)；
- [Compiler 与 codegen Map](../../implementation-map/compiler-and-codegen-map.md)；
- [当前性能摘要](../../../reports/current-performance-summary.md)。

### 2.1 当前可执行数据

`./scripts/check-scan-code-size.sh` 在 33 张 examples Table 上得到：

| 指标 | 当前值 | 当前专题 Gate | 剩余 |
|---|---:|---:|---:|
| Scan source bytes | 785,446 | 787,041 | 1,595 |
| Scan source lines | 3,392 | 3,427 | 35 |
| Scan family class bytes | 1,091,692 | 1,100,855 | 9,163 |
| nested classes | 239 | 265 | 26 |

这组 Gate 使用 Access Model Stage 1 基线加 15%，并要求 examples 中恰好存在 33 个 Scan。它能阻止同一固定场景 candidate 静默膨胀，但“已使用约 99.8% source-byte Gate”不等于接近 compiler 产品上限：

| Admission 边界 | 当前观察 | 上限 | 使用比例 |
|---|---:|---:|---:|
| 最大单个 Scan source | 28,284 bytes | 单 generated source 1,048,576 bytes | 约 2.7% |
| 最大单 schema generated source（FJSP） | 1,093,229 bytes | 单 schema 67,108,864 bytes | 约 1.6% |
| 最大顶层 Scan class | 25,212 bytes | 当前没有触发 javac/class admission | 非临界 |

四个 examples schema 的全部 generated source 合计为 3,374,809 bytes；该合计不与单 schema admission 上限直接比较。因此当前没有 imminent compiler-limit failure。风险是下一次合理的 Scan surface 增长会耗尽“本专题变化预算”，届时缺少可以区分必要增长、table 数量增长和偶然模板膨胀的归一化模型。

### 2.2 增长形状

33 个 `*Scan.java` 分布在 22,057–28,284 bytes、91–132 source lines：

- 无 exact source 的最小 Scan 仍约 22 KiB；
- 具有两个 exact source 和较宽 Cursor 的最大 Scan 约 28 KiB；
- 全部 Scan 的尺寸跨度只有约 6.2 KiB，固定 typed executor/terminal body 是主要底座；
- fields、flattened leaves 和 exact sources 在底座上增量增长；
- 当前 aggregate 指标随 Table 数量线性增长，并把固定底座、字段宽度、selector 数量和产品能力增长混在一起。

这不证明应把 executor 抽到 generic runtime。当前 typed、schema-specific 生成有利于 Java 8 类型安全、无反射和 hot-path specialization；共享实现可能引入间接调用、对象 carrier、codegen/runtime protocol 扩张或 allocation 回退。

### 2.3 Gate 能力与缺口

当前 Gate 已经证明：

- 固定 33-table schema 相对 Stage 1 的总量没有超过获批的 15%；
- source、class family、nested class 和 clean compile 可以一起重放；
- candidate 不能用单次 wall-clock 或只看 source LOC 通过。

当前 Gate 尚不能单独回答：

- 每增加一张 Table 的固定成本是否可接受；
- 每个 field/value leaf/selector/stage/terminal 对 source 与 class 的边际成本；
- 最大单 artifact、最大方法、constant-pool 和 javac compile-memory 余量；
- 新增正式能力后应该怎样建立新基线，而不把“更新阈值”变成默认动作；
- consumer schema 接近 256 Table 或 256 physical leaves 时的实际 compile envelope。

脚本中的 `topClassBytes` 实际统计全部顶层 `*Scan.class` 的 aggregate，而不是最大单 class；名称容易产生错误解释，但 Stage 0 不修改脚本。

### 2.4 Findings

| ID | 判定 | 内容 |
|---|---|---|
| GF-01 | confirmed | 当前 15% Gate 是固定 candidate 的回归预算，不是长期 normalized footprint model |
| GF-02 | confirmed | aggregate 同时受 Table 数、schema 宽度、selector 和固定 executor 底座影响，无法直接定位增长来源 |
| GF-03 | signal | 每 Table 约 22 KiB 的 Scan 底座会线性放大 source/class/compile 成本 |
| GF-04 | not supported | 当前数据不支持“即将触碰 javac 或 processor admission 极限” |
| GF-05 | question | 是否需要 per-table/per-feature/max-artifact/compile-cost 四层 evidence，须由 Stage 1 裁决 |

## 3. Benchmark lane 责任组织

对应正式 Owner：

- [测试与 evidence](../../engineering/testing-and-evidence.md)；
- [场景与 benchmark Map](../../implementation-map/scenario-and-benchmark-map.md)；
- `soma-benchmarks/docs/benchmark-evidence-contract.md`。

### 3.1 当前结构

`SmokeLaneSuite.java` 当前为 2,369 行，集中承载：

1. 20 个 required lane 的 canonical manifest；
2. warmup/measurement runner 与字符串分派；
3. 16 组 lane implementation/fixture；
4. workload id、phase、hot-column 与 Access Pattern Card 绑定；
5. lane-specific record validation；
6. 多 iteration aggregation 与 invariant merge；
7. benchmark-only `KernelTable`。

其中 lane implementation 约位于 134–1,176 行，metadata/validation 约位于 1,177–2,035 行，aggregation/support 约位于 2,036–2,369 行。它们具有不同变化原因。

Git 历史提供了真实 change-amplification 证据：

- 文件从 Phase 6 建立至今有 7 个功能/性能专题提交；
- 累计约 `+2,904/-535` 行变化；
- 四场景、packed exact、Access Model 和 evidence hardening 都修改同一文件；
- lane 之间虽然证明不同 failure/measurement domain，却共享同一个源码冲突面。

### 3.2 双向依赖

当前依赖为：

```text
SmokeLaneSuite
  -> BenchmarkModel.record/object/limitations

BenchmarkModel.validateArtifact
  -> SmokeLaneSuite.REQUIRED_LANES
  -> SmokeLaneSuite.workloadId
  -> SmokeLaneSuite.validateWorkloadEvidence
  -> SmokeLaneSuite.validateLaneRecord
```

这是一条 confirmed 的双向责任依赖。Runner/implementation 拥有 validation oracle，使 artifact model 无法在不依赖全部 workload implementation 的情况下完成完整校验。

同时，JSON schema 通过 `x-soma-laneBinding=SmokeLaneSuite.validateLaneRecord` 绑定具体类名。它是当前 evidence contract 的实现事实；若未来移动 Owner，必须同步 schema、validator、fixture 和 external artifact checks，不能只移动方法。

### 3.3 不应做的“瘦身”

以下方向没有证据支持：

- 合并 20 个 lane 的语义或删除看似重复的 lane；
- 用一个 generic Map-driven benchmark DSL 隐藏 typed fixture；
- 让所有 lane 继承复杂 runner framework；
- 把 validator 降为只检查 JSON 字段存在；
- 为减少文件数继续保留单一超大类。

独立 evidence lane 应继续独立；需要治理的是责任组织和源码变化面，不是证明义务本身。

### 3.4 Findings

| ID | 判定 | 内容 |
|---|---|---|
| BM-01 | confirmed | manifest、runner、20-lane dispatch、workload implementation、validation 与 aggregation 集中于一个 Owner |
| BM-02 | confirmed | `SmokeLaneSuite` 与 `BenchmarkModel` 双向依赖 |
| BM-03 | confirmed | 多个独立产品专题持续修改同一文件，已出现真实 change amplification |
| BM-04 | not supported | 现有证据不支持合并或删除 lane |
| BM-05 | question | canonical lane specification、runner、lane implementation、artifact validator 应怎样单向依赖，须由 Stage 1 裁决 |

这是四个范围中最充分的可维护性 finding。

## 4. Processor emitter 剩余依赖

对应正式 Owner：

- [系统架构](../../design/system-architecture.md)；
- [Schema 与生成 API](../../design/schema-and-generated-api.md)；
- [Compiler 与 codegen Map](../../implementation-map/compiler-and-codegen-map.md)。

### 4.1 目标与当前结构

上一轮治理形成的目标方向为：

```text
discovery / validation
  -> normalized schema model
  -> admission / artifact plan
  -> dense codegen model
  -> artifact emitters
  -> deterministic output
```

当前 `DenseTableSourceGenerator` 已收敛为 47 行 artifact orchestrator，normalized model、codegen model、Table/Auxiliary/Exact emitter 和 selector support 也已分离。生成源码和 class manifest 在上一轮证明 byte-stable。

Stage 0 仍确认两条未封闭依赖。

### 4.2 Admission 依赖 emitter support

`SomaProcessor.validateGeneratedSignatures()` 为 selector public/generated signature 做 collision admission 时，直接调用：

```text
DenseSelectorSourceSupport.selectorPublicParameterTypes(...)
```

`DenseSelectorSourceSupport` 同时拥有 source binding、comparison、change、unique 与 exact-source emission support。这样形成：

```text
admission -> emitter support
```

它与“admission 先于 artifact emitter”的目标方向不完全一致。公共参数规范应有唯一 Owner；当前 admission 和 emission 通过调用同一个 emitter helper 保持一致，避免了复制，但 helper 的责任归属偏下游。

### 4.3 Table emitter 依赖 Auxiliary emitter

`DenseTableSourceEmitter` 静态依赖 `DenseAuxiliarySourceEmitter` 的：

- `appendPackedTableTerminalExecutors()`；
- `appendSourceLeafArguments()`。

前者由 Auxiliary owner 实现，却把 packed Scan terminal executor 生成到 Table artifact；后者是 source argument 的通用片段。当前实际关系为：

```text
orchestrator -> Table emitter
             -> Auxiliary emitter

Table emitter -> Auxiliary emitter helper
Table emitter -> Exact emitter
```

`Table emitter -> Exact emitter` 可能是一个 Table artifact 内部 composition 边界；现有证据不足以判定错误。`Table emitter -> Auxiliary emitter helper` 则至少说明 Auxiliary 的命名责任与实际 output ownership 不完全一致。

### 4.4 Gate 覆盖

当前 codegen admission source-shape check 能防止：

- normalized model 或 codegen model 回流到原大类；
- generator 重新拥有 nested `TableSpec`；
- exact emitter 反向依赖 orchestrator；
- generator 不再编排 Table/Auxiliary artifact。

它没有检查：

- `SomaProcessor -> DenseSelectorSourceSupport`；
- `DenseTableSourceEmitter -> DenseAuxiliarySourceEmitter`；
- sibling emitter 的允许依赖集合。

Stage 0 不修改 checker，因为依赖归属尚未裁决。

### 4.5 Findings

| ID | 判定 | 内容 |
|---|---|---|
| PE-01 | confirmed | generated-signature admission 依赖 emitter-layer selector support |
| PE-02 | confirmed | Table emitter 依赖 Auxiliary emitter 中实际生成 Table terminal 的 helper |
| PE-03 | signal | Table emitter 组合 Exact emitter 可能是合理 artifact-internal composition，也可能说明 orchestrator 粒度不一致 |
| PE-04 | signal | 新 emitter 只经历拆分提交，尚无拆分后的真实 feature-change 样本可证明 co-change 已下降 |
| PE-05 | question | selector public shape、Scan execution support 和 artifact composition 的唯一内部 Owner，须由 Stage 1 裁决 |

## 5. Runtime 状态机的共同变化原因

对应正式 Owner：

- [系统架构](../../design/system-architecture.md)中的 Runtime aggregate；
- [Correctness 与 failure](../../design/correctness-and-failure.md)；
- [Runtime Plan 与可观测性](../../design/runtime-plan-and-observability.md)；
- [Runtime Core Map](../../implementation-map/runtime-core-map.md)。

### 5.1 `DenseTableState`

当前规模为 872 行、38 个 fields、62 个 public methods。代码可以按职责阅读为：

| 区域 | 责任 |
|---|---|
| membership/lifecycle | size、epoch、release、owned state |
| access/operation guard | View、operation、callback、materialization reentrancy |
| mutation coordinator | append/replace/clear/remove/release preflight 与 commit |
| resource accounting | update/operation/bulk scratch、KeySpace、exact-index storage |
| result/observability | Update/Remove result、TableStats snapshot/reset、last outcome |

这些是多个概念，但都参与一次 Table operation 的稳定状态转换。历史上该类在 remove、column access、exact access、child/materialization、resource hardening 和 packed exact 切换中被修改；同一提交通常也修改 `TableStats`、`RuntimeFailures`、plan/result type 或 generated binding。

这说明它确实有多个变化入口，同时也说明变化原因往往是“Table operation 的原子协调契约”，并非任意工具函数堆积。自 `4b6fa43` packed/exact 切换后，Access Model 主实施没有修改该类；`2ff09b1` 只清理一处术语。当前没有持续 churn 证据。

可能独立观察的边界是 resource counters 与 stats snapshot，但任何 extraction 都可能：

- 增加每 Table retained object/indirection；
- 改变 generated-runtime protocol binding；
- 分散 begin/preflight/commit/failure 的单一可信状态；
- 影响 hot-path allocation、class manifest 和 failure atomicity。

Stage 0 不支持实施拆分。

### 5.2 `ChildOwnershipRegistry`

当前规模为 815 行、31 个 fields、23 个 public methods，内部同时包含：

- opaque handle slot/generation/free-list；
- child identity open-address map；
- stage/publish/release lifecycle；
- cascade preflight/commit scratch；
- aggregate storage budget 和 materialization mutation guard。

该类只有 Phase 4 建立及两个紧随其后的 hardening 提交；之后的 packed exact、四场景和 Access Model 变化均未修改它。Handle、identity、cascade 和 budget 共同维护同一 ownership forest 的失败原子性，当前历史不支持存在独立演进的拆分边界。

Stage 0 明确排除“因为 815 行就拆分”。

### 5.3 Findings

| ID | 判定 | 内容 |
|---|---|---|
| RT-01 | signal | `DenseTableState` 同时承载 operation coordinator、resource accounting 与 observability，规模较大 |
| RT-02 | confirmed | 这些区域在历史能力提交中常与同一原子 operation 契约共同变化 |
| RT-03 | not supported | 当前没有证据证明拆分 `DenseTableState` 会降低变化放大而不增加 runtime 成本 |
| RT-04 | not supported | `ChildOwnershipRegistry` 历史稳定且 ownership 内聚，不支持拆分 |
| RT-05 | question | 只有未来两次以上独立 metrics/resource 变化持续绕开 mutation coordinator，才重新审查 satellite state |

## 6. 综合优先级

| 顺序 | 主题 | Stage 0 结论 |
|---:|---|---|
| 1 | benchmark lane responsibility | confirmed structural issue；适合进入 Stage 1 裁决 |
| 2 | processor emitter dependencies | confirmed residual boundary issue；适合进入 Stage 1 裁决 |
| 3 | generated footprint model | measurement model 不足；需要先裁决长期指标，不应直接重构 |
| 4 | runtime state machines | 默认保持；只建立事件驱动的再审触发条件 |

该排序不是实施授权，也不表示前三项都必须修改代码。

## 7. Stage 1 必须回答的问题

1. Footprint Gate 是保留 fixed-candidate regression lane，并新增 normalized diagnostic，还是重定义同一 Gate？
2. 哪个内部 Owner 同时拥有 lane manifest、workload identity 和 lane validator，且能让 runner/model 单向依赖？
3. Selector public parameter shape 应由 normalized/codegen model 还是独立 support Owner 提供？
4. Packed terminal executor 属于 Scan emission、Table emission，还是独立 execution-source support？
5. Runtime retain 决策是否只记录触发条件，还是需要无代码变更的 component boundary specification？

在这些问题获得明确授权和裁决前，不得开始拆类、移动 helper、改 checker 或重设 benchmark/code-size 基线。

## 8. Evidence 命令

本 Stage 0 使用或重放：

```text
./scripts/check-scan-code-size.sh
./scripts/check-codegen-admission.sh
git log --numstat -- <audited source>
git blame --line-porcelain <runtime state>
rg / wc / javac generated artifact inspection
./scripts/check-docs.sh
./scripts/check.sh
git diff --check
```

现有多 fork 性能与 allocation 结论继续由正式 Report 拥有；本次没有实现变化，因此不生成新的性能 claim。
