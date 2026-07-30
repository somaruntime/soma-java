# 实时派工规则引擎应用 Design

类型：应用 Design

状态：当前

Owner：real-time-dispatch-rule-engine

对 SOMA 产品规范性：否

事实范围：应用分层、领域不变量、DataFlow execution、提交和 lifecycle

最后审查日期：2026-07-28

## 责任与依赖方向

```text
application
  -> config -> feed
  -> dispatch
       -> runtime
       -> rule
       -> detached result

test benchmark / evidence / reference / validation
  -> production contracts
```

- `config` 拥有严格 key 集、范围预检、canonical text 和 checksum；
- `feed` 拥有 immutable snapshot、delta、cycle、scenario 和 synthetic Factory；
- `schema` 只声明 `WorkState`、`ResourceState` 与 stable identity；
- `runtime` 拥有`real-time-dispatch-horizon`显式SomaGroup、两张live Table、
  snapshot/delta projection和fail-stop lifecycle；
- `rule` 拥有 reusable Definition/Template、one-shot Invocation 和立即消费的
  joined Index；
- `dispatch` 拥有 horizon 编排、全批次预检和跨 Table 顺序提交；
- `result` 只依赖 Java platform，拥有 detached domain output；
- `benchmark/evidence/reference/validation` 只位于 test source-set。

应用不依赖另外两个 reference application，也不共享领域 parent、fixture 或
baseline。`config/feed/support/result` 不依赖 SOMA runtime。

## 构造与不变量 Owner

| 不变量 | 唯一 Owner | 构造防线 |
|---|---|---|
| config key 完整、规模/预算有界 | `DispatchConfigLoader` / `DispatchConfig` | strict parse、checked arithmetic、immutable fields |
| scenario identity、cycle 单调、delta 唯一 | `DispatchScenario` / Factory | detached defensive construction 与稳定 checksum |
| Table projection 与 lifecycle | `DispatchRuntimeFactory` / `DispatchRuntime` | unpublished aggregate、Batch/Delta、OPEN/FAULTED/CLOSED |
| 规则 shape、source、order | `DispatchRulePlan` | constructor 一次建立 immutable Definition/Template |
| Index 消费不越过 mutation | `DispatchRuleExecutor` | Invocation 后立即复制 detached command |
| command 全批次可提交 | `DispatchCommitter` | stable-key relookup、完整 preflight 后顺序 mutation |
| Result cardinality 与 identity | `DispatchResult` | immutable copy、唯一 work、覆盖计数和 checksum |

边界错误使用真实稳定异常。仅由上游构造已经证明的内部推导事实才适合 `assert`；
关闭断言后可能破坏领域状态的检查仍为真实 failure。

## DataFlow execution

`DispatchRulePlan` 建立两个 Source：pending Work exact source 与 Resource packed
source。它输出 ready count、按 capability 的 demand group 和 stable-sorted
compatible joined pair sequence；另一个 reusable Template 聚合已接纳 work 的
processing load。

每个 cycle：

1. Runtime 推进严格递增的 current minute，并在 Work safe point 应用 arrival
   Delta；
2. Invocation 绑定当前 Work/Resource Table、parameter、budget 和 cancellation；
3. DataFlow 执行 filter、GroupBy、inner Join 和 stable sort；
4. rule 在同步只读边界内读取 joined Index，按 stable business order形成 command；
5. committer 对整批 command 预检后，按 stable key 顺序更新两个 root；
6. diagnostics 只累计 detached stats，不进入领域 Result。

sequential 是语义基准；managed/borrowed parallel 只能改变物理执行。
`maximumOutputElements`、`maximumTasks`、worker 上限和 cancellation 由调用边界
显式传入。Context 可以跨 Invocation 复用；Invocation 和 Runtime horizon
one-shot。

## 失败与一致性

SOMA 只保证单次 Table operation 的失败原子性。跨 Work/Resource 的业务一致性由
应用负责：所有可预见错误必须在首个 authoritative write 前关闭；首个 write 后
发生 unexpected failure 时，`DispatchRuntime` 进入 fail-stop 并释放 aggregate，
不发布 `DispatchOutcome`。本应用不声称 rollback 或 transaction。

managed Context 由应用在 composition root 关闭；borrowed executor 始终归调用者。
Result/Diagnostics 不保存 Table、Index、IndexSnapshot、Cursor、ColumnView、
binding、executor 或 mutable collection。

Runtime factory先创建冻结Group并atomic attachWork/Resource root；partial-create、
projection failure和fail-stop cleanup统一由Group收口。`runtimeMetadata()`只公开
detached `SomaGroupMetadata`，不进入rule identity、command或领域Result。
