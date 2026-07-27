# 决策与证据

类型：Temporary

状态：Stage 2 candidate

Owner：SOMA correctness preservation and software structure governance

正式事实源：否

事实范围：Stage 1 五条证明链、CP-001–CP-007 裁决和 Stage 2 实施候选

非事实范围：尚未验证的最终实现事实、正式 Design 和 release claim

最后审查日期：2026-07-27

## 1. 审计结论

SOMA 的产品、模块、能力与局部机制保持连续：

```text
Schema
  -> normalized compiler fact
  -> generated contract
  -> packed ownership aggregate
  -> typed Access / Transformation
  -> one-shot Invocation
  -> detached Result or safe-point Effect
  -> evidence-backed claim
```

没有证据要求推翻模块拓扑、Access Model、Transformation/DataFlow、public API、
annotation Schema 或 generated/runtime identity。真实偏差集中在一条横切证明链：
正式 Design 已声明 internal invariant 后 aggregate 不再 normal access，但当前
runtime 没有共享的 aggregate trust state；部分 structured internal failure 和
unexpected failure 会清除 operation guard 后重新开放访问。

## 2. 五个 vertical slice

| Slice | Invariant 与唯一 Owner | Fact-origin defense | Failure / Evidence | Verdict |
|---|---|---|---|---|
| Compiler/Codegen | schema legality、canonical identity 和 artifact publish 由 normalized model、artifact plan 与 output boundary 拥有 | immutable normalized facts、admission、deterministic order、Filer publish | diagnostics、clean/repeat source、schema hash、golden、external consumer | 主链闭合；不拆 processor 主叙事 |
| Table Mutation | stable table fact 由 generated mutation coordinator 与共享 ownership aggregate 拥有 | validation/preflight、staging、locator/exact/column coordination、one publish | expected failure 原子性已有 invariant/property/consumer evidence；internal/unexpected 后 trust enforcement 缺失 | `P1`，进入实施 |
| Access/Lifecycle | aggregate exclusivity 由共享 `ChildOwnershipRegistry`，table currentness 由 `DenseTableState`，handle lifecycle 由 shape-specific facade 拥有 | epoch、one-shot、view pin、operation/DataFlow guard、detached snapshot | access/child/DataFlow contract 与 stale/release evidence | 正常 lifecycle 闭合；共享 registry 是 fault scope 的自然 Owner |
| Transformation/DataFlow | graph legality、Invocation lifecycle 和 effect commit 分别由 Builder、Invocation、generated safe-point binding 拥有 | immutable Template、one-shot state、canonical guard、fixed-order failure、freeze/preflight/commit | semantic slice、reference differential、parallel/effect evidence | 主链闭合；DataFlow-local failure 保持 Invocation terminal，不另建 table fault state |
| Evidence/Claim | Gate/artifact identity 与正式 claim 由 validator、Conformance、Report 各自拥有 | environment/commit/schema/workload identity、strict validator、`claimAllowed=false` | compiler/runtime/DataFlow/application/component Gates | Owner 清楚；CP-007 是 Gate publication safety，而非产品性能回归 |

## 3. CP 裁决

### CP-001：internal invariant 后 aggregate 状态

- 当前事实：`DenseTableState` 没有 fault state；generated
  `SomaRuntimeException` 通常进入 `endOperationFailure()` 并重新开放 operation
  guard。
- 设计要求：`correctness-and-failure.md` 规定 internal invariant 后当前
  aggregate fail fast，不再 normal access。
- 偏差：真实 `P1 Conformance` 偏差。
- 处置：由 root/child 共享的 ownership registry 持有 internal `HEALTHY/FAULTED`
  trust state；internal failure 在事实产生处或最外层 operation boundary 原子标记。

### CP-002：unexpected RuntimeException / Error

- 当前事实：active operation 的 raw `RuntimeException`/`Error` 调用
  `abortOperation()` 后重新开放访问；非 operation mutation 的 cleanup 也可能原样
  传播。
- 设计要求：只有能够证明旧 stable state 的 expected failure 才可继续使用。
- 偏差：真实 `P1 proof` 缺口。
- 处置：callback 普通异常继续先包装为 `CALLBACK`，旧状态可信；table-owned
  unexpected exception 在无法由阶段证明可信时 fault aggregate。raw `Error`
  原样传播，但已发布 aggregate 若仍可触达则 fail closed。

### CP-003：fault scope 与 ownership propagation

- 当前事实：所有 root/child table 已共享 `ChildOwnershipRegistry`，但它只拥有
  ownership、scope 和 storage budget，没有 trust state。
- 设计要求：root ownership aggregate 是 correctness 与 lifecycle 边界。
- 偏差：真实 `P1 design` 缺口。
- 处置：fault scope 是整个 root aggregate；child fault 通过共享 Owner 自动传播。
  不增加 public fault query，不改变 root/table transaction 边界。

### CP-004：跨层证明链

- 当前事实：正式 Design、Implementation Map 和 executable evidence 均存在，但
  internal failure 的 Design → runtime state → representative evidence 尚未闭合。
- 裁决：`P2 governance`，由本文件的五条有限证明链和最终 Report 关闭；不创建
  永久逐类 inventory。

### CP-005：validation/test 重复

- 当前事实：测试已分为 compile/golden、contract/invariant、property/reference
  differential、少量 E2E 和 performance；未发现会产生第二事实源的系统性重复。
- 裁决：保持当前分层。只增加 aggregate fault Owner 的集中构造/契约测试和一个
  generated representative consumer，不删除已有能力 evidence，不逐 forwarding
  method复制 failure case。

### CP-006：abstraction/narrative 复杂度

- 当前事实：processor 已分离 normalized model、orchestrator、Table/Auxiliary/
  DataFlow/Exact emitters 和共享 source support；DataFlow 已分离 Definition、
  Template、Invocation、kernel 与 generated binding。大 emitter 是 artifact
  narrative 的集中投影，不等同于多 Owner。
- 裁决：没有证据支持再次拆分或引入 emitter framework。只把本次跨多个生成
  terminal 的 failure routing 收敛到一个 generated table helper，避免平行规则。

### CP-007：Zulu javac 8 synthetic-access Gate

- 当前事实：Stage 0 曾出现 outer class 调用 `$1` marker、nested class 提供
  `LongSum` marker 的 `NoSuchMethodError`。
- 有界复核：Zulu `1.8.0_492-b09` 下两次 `clean test-compile` 均成功；
  outer/nested SHA-256 分别稳定为
  `41e844b9...aac847` / `bd1f896d...efc78`；两次 descriptor 都是
  `LongSum(LongSum)`；`--unknown` class-load smoke 稳定到达参数校验。
- 根因裁决：不是 SOMA runtime 或性能问题。私有 nested implicit constructor
  依赖 javac 8 synthetic marker，incremental/stale outer/nested artifact 可以形成
  不配套 class set；当前 Gate 又在五个 fork 之后才做 class-load smoke。
- 处置：为 `LongSum` 增加非 private 显式无参构造器以消除 synthetic access，
  并把 class-load smoke 前移到 fork 之前。候选稳定前不运行 multi-fork。

## 4. Failure 与 trust 设计

### 4.1 分类

| Failure phase | 可信度与动作 |
|---|---|
| create/publish 前 | 清理 staging，不发布 aggregate；无需对外暴露 fault |
| operation preflight expected failure | 旧 state 可信；结构化失败，正常关闭 guard |
| staging expected failure | 仅在 staging/rollback 协议证明旧 state 完整时可信 |
| publish 中/后 internal 或 unexpected failure | aggregate `FAULTED` |
| callback ordinary RuntimeException | 包装 `CALLBACK`；scratch-only mutation 保持旧 state |
| callback supplied SOMA failure | 保留 envelope；`INTERNAL` category 按 internal 处理 |
| raw JVM `Error` | 原样传播；已发布 aggregate fail closed，不承诺 JVM 可恢复 |
| cleanup/release internal 或 unexpected failure | 保持/进入 `FAULTED`；不得重新开放 normal access |
| child failure | 共享 registry 使整个 root aggregate `FAULTED` |
| DataFlow-local compute failure | Invocation 进入 terminal failure；只读 source aggregate 保持可信 |
| generated Effect commit 的 table internal failure | 目标 ownership aggregate `FAULTED` |

### 4.2 唯一 Owner

`ChildOwnershipRegistry` 是 aggregate trust 的唯一 Owner：

```text
HEALTHY
  -- first untrusted failure --> FAULTED(first operation + stable internal code)
FAULTED
  -- normal access -----------> existing INTERNAL fail-fast envelope
  -- diagnostics -------------> allowed, but not a new stable-data claim
  -- root release ------------> allowed cleanup attempt
  -- reset/recovery -----------> forbidden
```

只保存 bounded primitive/String metadata，first failure wins；不暴露新的 public
状态、DTO、error category 或 generated public method。后续拒绝继续使用既有
`INTERNAL/internal_invariant_violation` envelope，避免建立第二套 public failure
contract。

### 4.3 Runtime 协作

- `DenseTableState.checkActive()` 通过 registry 统一拒绝 faulted aggregate；
- stats 使用独立 diagnostics preflight，允许观察已有计数，但不清除 fault；
- root release 使用独立 release preflight，允许 terminal cleanup；
- internal invariant 由 aggregate-bound `DenseTableState`、`ColumnGroup`、
  `ChildOwnershipRegistry`、`StorageBudget` 在抛出前标记；
- generated table 自身检测到的不变量通过一个 `state.internalInvariant(...)`
  helper 标记；
- active terminal 对 structured failure 传递 category；只有非 `INTERNAL` failure
  使用普通 `endOperationFailure()`；
- raw unexpected failure 使用 faulting abort；callback 已经转成 `CALLBACK` 的路径
  不误伤；
- cleanup 必须先保存原 failure，随后关闭 operation/table scope；cleanup 不能用
  第二个异常覆盖 first failure。

### 4.4 Release 与 diagnostics

Faulted aggregate 不允许 point access、Scan、View、Snapshot consumption、
mutation、DataFlow acquire、stats reset 或 owned-child navigation。允许：

- 已有 runtime plan/identity 这类 detached metadata；
- `statsSnapshot()` 的 bounded diagnostic attempt；
- root `release()` 的一次或幂等 cleanup attempt。

若底层已损坏到无法完成 stats 或 release，原 internal failure 继续传播，aggregate
保持 faulted；不会重新开放半释放 forest。

### 4.5 成本与兼容

- normal table entry 复用现有 registry preflight，只增加一个 predictable boolean
  branch；不增加 allocation、hash、lock 或 ThreadLocal；
- hot-loop 内部 row/column primitive 不重复检查；
- fault metadata 只在 first failure 写入；
- public/generated signature、annotation Schema、runtime protocol identity、
  Access/DataFlow 语义和 application contract 不变；
- runtime protocol 只做 additive internal binding helper，并保留旧 failure method
  以兼容同版本已生成 class。

## 5. Stage 3–4 slices

1. Aggregate trust slice：registry/state/core primitive fault Owner、normal access、
   diagnostics/release 和 focused runtime invariant tests。
2. Generated routing slice：统一 terminal failure helper、generated invariant origin、
   direct mutation untrusted failure routing，以及 clean/golden/external consumer。
3. CP-007 slice：消除 benchmark synthetic constructor，class-load smoke 前移，
   clean/repeat descriptor 验证；稳定后只运行现有 component Gate。
4. Evidence slice：runtime/generated/DataFlow representative Gates、完整
   scope non-regression；没有新的性能机制变化时不 rebaseline。

未触发停止条件。下一步唯一关键路径是实现 aggregate trust slice。
