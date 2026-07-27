# Stage 0 基线审计

类型：Temporary

状态：active（Stage 0 evidence）

Owner：SOMA correctness preservation and software structure governance

正式事实源：否

实施授权：只读审计与 Stage 0 文档证据

事实范围：起始基线上的正式设计、实现结构、failure 状态和 evidence 现状

非事实范围：最终缺陷判定、Stage 1 详细审计、production 修复和正式 Conformance

最后审查日期：2026-07-27

## 1. 基线与方法

起始仓库基线为：

```text
975a28b657028991272b04efe21e5584343a89a6
```

该 commit 完成 Transformation/DataFlow 正式文档收口；当前 production candidate
由其父 commit `2aa8c15` 提供。Stage 0 读取了：

- SOMA Blueprint、Design、Implementation Map、Conformance 和 Engineering；
- processor normalized/codegen 主链与 generated emitter；
- runtime `DenseTableState`、`RuntimeFailures` 和 generated operation pattern；
- DataFlow Definition/Template/Invocation/Context/failure；
- runtime/DataFlow 代表性测试和 Gate 脚本；
- 两份跨项目方法文档。

方法文档只提供问题框架。所有 SOMA 当前结论都必须回到项目正式 Design、代码、
测试和可执行 evidence。

## 2. 当前设计链

当前正式架构已经形成清楚的纵向展开：

```text
SOMA product boundary
  -> compiler/runtime/dataflow module architecture
  -> Schema / Storage / Access / Transformation / DataFlow capabilities
  -> generated protocol and runtime state
  -> specialized kernel / storage mechanism
```

系统主叙事已经由正式 Design 明确：

```text
Java 8 Schema
  -> javac lowering / processing / normalization
  -> schema-specific generated facade
  -> packed runtime state
  -> typed Access / Transformation Definition
  -> one-shot Invocation
  -> detached Result or safe-point Effect
```

这与“抽象定义当前语义层级，叙事组织真实因果”一致。Stage 0 没有发现需要推翻
当前模块拓扑或产品定位的证据。

## 3. 当前正确性防线

| 抽象 | 当前 Owner | 当前主要防线 | 初步判断 |
|---|---|---|---|
| schema legality/identity | processor normalized model | compile validation、immutable normalized facts、canonical hash | 已有清晰 Owner |
| generated artifact | generator/output boundary | admission、deterministic order、publish、golden/consumer | 已有发布边界 |
| Table stable state | generated facade + `DenseTableState` | preflight/stage/commit、epoch、operation guard | 主链成立 |
| locator/exact index | generated mutation + runtime primitive | incremental link/unlink、compaction repair、capacity preflight | 派生表示有维护协议 |
| Scan/View/Snapshot/Cursor | shape/lifecycle-specific API | one-shot、epoch、active cursor、detached snapshot | 生命周期明确 |
| Transformation shape | shape-specific DSL | 类型只公开合法 operator/result | 构造防线明确 |
| Definition graph | one-shot Builder | `OPEN/PUBLISHED/FAILED` | 非法 graph 不发布 |
| Invocation | immutable Template + one-shot Invocation | binding、budget、guard、`FAILED/CANCELLED` | 状态机明确 |
| Effect/Delta | generated binding + safe-point commit | freeze、preflight、deterministic commit | 迁移边界明确 |
| engineering claim | validator/Conformance/Report | identity、environment、`claimAllowed=false` | 发布主张有防线 |

正式测试策略也已采用 contract/property/reference differential/代表性
end-to-end/benchmark 的分工，没有把所有正确性压力交给逐方法 case。

## 4. 初步静态证据

在起始基线的 production Java source 上进行导航性扫描：

| 信号 | 结果 | 含义限制 |
|---|---:|---|
| processor internal-invariant source lines | 26 | 包含 emitter 中生成的 guards，不等于 26 个独立语义 |
| runtime-core internal-invariant source lines | 67 | 用于定位 storage/protocol 不变量 Owner |
| dataflow internal-failure source lines | 18 | 用于定位 execution/lifecycle 防线 |
| production Java `assert` | 0 | 当前正确性不依赖可关闭 assertion |
| runtime/processor `FAULTED/POISONED` token | 0 | 未发现显式 aggregate fault state |
| emitter Soma failure/end-failure pattern lines | 23 | 说明 structured failure 通常恢复 operation guard |
| emitter unexpected abort pattern lines | 21 | 需要进一步审计失败后可信度 |

这些数字只是 Stage 1 导航信号，不能用作删除、合并或缺陷判定。DataFlow reference
Gate 已使用 `-ea`，因此 Stage 0 不把 assertion enablement 认定为当前缺口。

## 5. 已确认的一致方向

### 5.1 表示由语义与 Access Pattern 推导

Packed SoA、primary locator、exact group、IndexBuffer、IndexSnapshot 和
application-owned frontier 分别服务不同语义、生命周期和访问模式。它们不能因为
都是数组、索引或映射而机械合并。

### 5.2 派生表示没有成为第二事实源

- locator/exact index 随 table mutation 增量维护；
- IndexSnapshot 是 detached index sequence，并由 epoch/currentness 契约约束；
- Definition/Template immutable，Invocation-local scratch 不跨 invocation；
- performance artifact 与 Report 由 identity/validator 约束，不反向成为产品事实。

### 5.3 DataFlow 生命周期比 Table failure lifecycle 更显式

`DataFlowDefinition.Builder` 使用 `OPEN/PUBLISHED/FAILED`；Invocation 使用
`NEW/BINDING/READY/RUNNING/COMMITTING/COMPLETED/FAILED/CANCELLED`。失败后的
Invocation 不能重新执行，符合 one-shot 与失效终止旧事实原则。

### 5.4 真实 internal failure 边界成立

可能导致错误 publish、越界、错误 lineage、失效 Index 或资源记账破坏的条件使用
真实 structured failure，而不是 production assertion。该方向应保持。

## 6. Stage 1 审计候选

### `CP-001` internal invariant 后 aggregate 状态

正式 Design 规定 internal invariant 后当前 aggregate 不再 normal access。
`DenseTableState` 当前持有 released、operation、callback、materialization 和 view
状态，但未发现统一 fault state。generated code 的 `SomaRuntimeException` 通常
调用 `endOperationFailure()` 后重新开放 operation guard。

Stage 1 必须证明：

- internal category 是否可能从 table-owned operation 逸出后仍允许继续访问；
- Design 的“不再 normal access”是由状态机强制，还是只靠 caller 契约；
- create boundary、无 aggregate helper 和 active aggregate 应如何区分。

当前分类：`P1 conformance candidate`，尚未判定 implementation defect。

### `CP-002` unexpected RuntimeException/Error 的可信状态

generated operation 对未结构化 `RuntimeException` 和 `Error` 通常调用
`abortOperation()` 清除 guard 后原样传播。Callback 已有专门包装，fatal JVM
error 也明确不承诺恢复；仍需确认内部 unexpected failure 在 preflight、staging
或 publish 的不同阶段是否可能留下无法证明的状态。

当前分类：`P1 proof obligation`。

### `CP-003` fault scope 与 ownership propagation

若需要 fault state，必须裁决：

- table、root aggregate 还是 owned subtree 为 fault scope；
- child failure 是否传播到 root；
- diagnostics、stats、release 是否仍允许；
- close/release 自身失败如何处理；
- 状态检查是否复用既有 `checkActive()`，避免散落分支。

当前分类：`P1 design candidate`，不预设新增 public 状态。

### `CP-004` 跨层证明链的可追踪性

正式 Design 和 evidence 已分别存在，但 compiler → generated contract → runtime
stable state → DataFlow Result/Effect → invalidation 的证明链分散在多个 Owner。
Stage 1 应建立有限矩阵，识别真实断点；不创建逐类永久 inventory。

当前分类：`P2 governance optimization`。

### `CP-005` validation/test 重复与责任漂移

当前测试策略已经要求集中在 Owner 边界。Stage 1 只在证明 production defense 和
代表性 evidence 闭合后，识别重复 null/lifecycle/ownership case、private-layout
冻结或平行 oracle；不设删除数量目标。

当前分类：`P2 evidence optimization`。

### `CP-006` abstraction/narrative 局部复杂度

Processor emitter 和 generated operation 是必要复杂性候选，不因规模直接失败。
只有完整 vertical slice 发现反向依赖、多 Owner、同一能力需跨无共同 Owner 的
位置同步，或行为叙事被容器/字符串拼接淹没时才实施结构优化。

当前分类：`P2 evidence-triggered review`。

### `CP-007` Zulu javac 8 synthetic-access Gate 稳定性

Stage 0 完整 Gate 在 `check-post-cutover-components.sh` 首次启动
`PostCutoverComponentBenchmark` 时出现：

```text
NoSuchMethodError:
PostCutoverComponentBenchmark$LongSum.<init>(
    PostCutoverComponentBenchmark$1)
```

该 benchmark 的 private nested `LongSum` 位于 anonymous classes 之前，Zulu
javac 8 会为 private constructor 生成 synthetic access constructor。失败后的
`javap` 显示当前 outer call 与 nested constructor 都使用 `LongSum` marker，
直接启动也能正常进入 unknown-option 校验，因此 Stage 0 不能把它判定为稳定源码
缺陷或产品回归。

Stage 1 应用 clean/repeat bytecode 和构建顺序证据区分：

- Zulu javac 8 synthetic marker 非确定性；
- incremental/stale class artifact；
- benchmark source 对 private synthetic access 的不必要依赖；
- Gate 是否缺少在运行前验证 bytecode 自洽性的责任。

当前分类：`P1 evidence/Gate reliability candidate`。不得通过反复运行直至通过来
掩盖不稳定性，也不把 benchmark-only 问题误归因于 SOMA runtime。

## 7. 风险

1. 把方法论变成逐类模板，制造文档和 token 负担。
2. 把 `internalInvariant` 数量误当缺陷数量。
3. 在每个 hot method 增加重复 fault/lifecycle check。
4. 把 expected、internal、unexpected 和 fatal failure 混成一个 poison 策略。
5. 用测试删除量或 LOC 下降替代语义与 evidence。
6. 在未授权时改变 public failure、ownership 或 aggregate lifecycle。

控制方式是 Owner-first、vertical-slice、单一状态入口、窄性能证据和停止条件。

## 8. Stage 0 结论

- 两份方法文档适合作为本专题审查视角，但不成为 SOMA Design Owner；
- SOMA 当前产品定位、模块边界和主要执行叙事方向正确，不需要整体推翻；
- 构造正确、one-shot、派生表示生命周期和 evidence 策略已有较强基础；
- internal/unexpected failure 后 aggregate 可信状态存在值得进入 Stage 1 的
  `P1` 证明义务；
- 完整 Gate 暴露一个独立的 Zulu javac 8 synthetic-access 稳定性候选，需要
  clean/repeat 证据而不是重跑碰运气；
- 当前没有 production 修改授权，也没有证据支持全面重写、API 变化或测试清洗；
- Stage 1 应沿五个 vertical slice 完成有限审计，再决定详细设计和实施范围。

Stage 0 完成不表示 `CP-001`–`CP-007` 已被确认或获得修复授权。

## 9. Stage 0 验证环境

```text
JDK: Azul Zulu 1.8.0_492-b09
Maven: 3.9.16
OS: macOS 26.5.2 / Darwin 25.5.0
Architecture: aarch64
```

Stage 0 只修改 Temporary 与 active-topic 导航，没有形成新的性能或 release
claim。实际验证：

- `./scripts/check-docs.sh`：`doc-check: ok`；
- `git diff --check`：通过；
- `./scripts/check.sh`：前序 scope/docs/build/public/compiler/codegen/runtime/
  access/child/generated/external-consumer/reference-application/DataFlow differential/
  code-size/benchmark-smoke Gate 通过；在 post-cutover component benchmark 首次
  class initialization 处因 `CP-007` 退出；
- 失败后 `javap` 显示 outer/nested synthetic signature 当前一致；
- quick startup 到达预期 unknown-option validation，没有再次运行 benchmark fork。

因此 Stage 0 固定的是“功能与大部分 evidence 基线通过，同时存在一个尚未归因的
Gate reliability candidate”，不是完整 Gate passed 声明。后续不得沿用本次
中间输出宣称最终 Conformance。
