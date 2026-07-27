# Stage 1 三应用详细设计

类型：Temporary

状态：active（detailed design candidate）

Owner：SOMA reference application portfolio and best-practice governance

正式事实源：否

实施授权：本专题 Stage 1–6

事实范围：三个应用的目标结构、执行叙事、迁移顺序与实现切片

非事实范围：当前正式产品语义、尚未实现的代码事实和 release 声明

最后审查日期：2026-07-27

## 1. 设计判定

三个应用只共享 SOMA public artifacts 和项目级质量标准，不共享领域模型、Schema、
Config、Runtime、Result、fixture、helper、baseline 或领域 JAR。

```text
industrial scheduler
  -> direct Access + application frontier/event loop

grassing simulation
  -> packed/exact/batch + deterministic tick systems

real-time dispatch rule engine
  -> reusable multi-source DataFlow + detached command + Java commit
```

这三条路径是三个独立 business model 的投影，不是同一目录模板的三份复制。

## 2. Grassing 详细裁决

### 2.1 保留

- `Config -> Scenario Factory -> Scenario -> Simulator/Session -> Result`；
- application-owned `double[] grass` 与 primitive cell scratch；
- `GrasserStateTable`、`TraceSampleTable`、`@SomaIndex by_mode`；
- fixed-order tick systems、stable random addressing、staged birth/death；
- direct Candidate Scan、ColumnView、Batch、swap-remove 和 exact group；
- detached `SimulationResult` 与低频 diagnostics；
- AoS differential、packed-order reversal、lifecycle 和性能 evidence。

### 2.2 修改

| ID | 裁决 | 实施 |
|---|---|---|
| `RA-GRA-003` | confirmed | Session 对 ordinary `RuntimeException` 与 unexpected `Error` 都 fail-stop；cleanup failure 作为 suppressed，不能覆盖 primary |
| `RA-GRA-004` | confirmed | 全量逐值 projection verification 移至 test source；production 只依赖 Batch/replaceAll 自身契约及必要的 cardinality publish |
| `RA-GRA-005` | retain | `SimulationDiagnostics` 是显式 detached diagnostics；保留现有 Result contract，避免不兼容变更 |
| `RA-GRA-006` | confirmed | Result boundary 使用局部 accumulator；最终 Result 的 energy 与 checksum 在同一次 stable-ID traversal 中推导 |
| `RA-GRA-007` | retain with explanation | 完整 schema generation 产生 DataFlow companion，因此 ordinary compile/runtime classpath 仍需要 `soma-dataflow`；POM 不再称其为迁移 |
| `RA-GRA-008` | conforming | direct pipeline 已是 natural canonical path，不增加 reusable business DataFlow |

Session 状态收敛为：

```text
READY -> RUNNING -> FINISHED -> CLOSED
   \        \           \
    \-------- failure ---> CLOSED
```

成功 `finish()` 必须先构造 detached Result，再关闭 runtime，最后发布 Result。
关闭失败不发布成功结果。失败路径关闭 runtime，保留 primary failure；关闭后不再提供
live access。

## 3. RTD business model

### 3.1 业务边界

应用处理一个有限、可重放的 dispatch horizon：

```text
versioned properties
  -> detached DispatchScenario
       -> initial RuntimeSnapshot
       -> ordered DispatchCycle + detached WorkArrivalDelta
  -> SOMA WorkState / ResourceState
  -> reusable DispatchRule Template
  -> one Invocation per cycle
  -> detached DispatchCommand batch
  -> application-owned sequential commit
  -> detached DispatchOutcome
```

它模拟上层同步器已经交付的 snapshot/delta，不实现 JDBC、CDC、MES、retry、
checkpoint、transaction 或 distributed execution。

### 3.2 领域事实

`WorkItem`：

- stable `WorkId`；
- required capability；
- release/due minute；
- priority；
- processing minutes。

`ResourceSnapshot`：

- stable `ResourceId`；
- capability；
- available minute；
- enabled。

`DispatchCycle`：

- strictly increasing current minute；
- detached work-arrival delta；
- cycle 内每个 work/resource 最多产生一个 command。

`DispatchCommand`：

- stable work/resource identity；
- issue/start/completion minute；
- capability、priority 和 work version；
- detached、immutable，不保存 current Index、View、Cursor 或 binding。

### 3.3 Schema

```text
WorkState
  @SomaKey WorkId
  @SomaIndex by_status
  capability, release, due, priority, processing, status, version

ResourceState
  @SomaKey ResourceId
  capability, available, enabled, version
```

`by_status` 有真实 Source consumer：DataFlow 从 `PENDING` exact group 开始。
Resource 不建立 capability index，因为 multi-source equi Join 已拥有该访问语义，
额外 index 没有 direct consumer。

初始 snapshot 使用两个 Batch。后续 work arrival 使用 generated keyed Delta，在
单个 Work aggregate safe point apply。Runtime 不保存 Scenario Factory、Config
parser 或 generator state。

### 3.4 Rule Definition

Definition/Template 只创建一次，不绑定 live Table：

```text
pending work exact source
  -> filter(release <= :now)
  -> groupBy(capability).counts
  -> innerJoin(
       resources.filter(enabled && available <= :now),
       work.capability == resource.capability)
  -> stable sort(
       priority desc,
       due asc,
       release asc,
       workId asc,
       resource available asc,
       resourceId asc)
  -> JoinedIndexResult
```

同一个 graph 输出：

- ready work count；
- demand count by capability；
- ranked compatible pair Index sequence。

一次 Invocation 结束后，应用在来源 Table 未 mutation 的同步只读批次中立即消费
joined current Index，使用 primitive selected-work/resource marks 进行确定性
greedy matching，并立刻复制为 detached commands。Index 不进入 command、Result
或下一 cycle。

### 3.5 Commit

所有 command 先通过 stable key 重新定位并预检：

- work 仍是 `PENDING`、version/capability/release 匹配；
- resource 仍 enabled、version/capability/available 匹配；
- completion arithmetic 不 overflow；
- command identities 在本 batch 内唯一。

随后按 command logical order 顺序提交 Work 和 Resource 两个独立 root。SOMA
不提供跨 root transaction；任一 authoritative mutation 后出现失败，当前
dispatcher fail-stop、关闭 Runtime，不发布 partial `DispatchOutcome`。

### 3.6 Execution ownership

`SomaDispatcher` 接受 application-owned `DataFlowContext` 和 immutable
`ExecutionBudget`：

- sequential Context 不创建 worker；
- managed Context 由 SOMA 拥有并在 close 时关闭；
- borrowed Context 不关闭 caller executor；
- common pool 只有 caller 显式传入时才可能使用；
- cancellation 作为 Invocation token 显式传入；
- budget 由 Config/constructor 预检并在每次 Invocation 收紧。

同一 Scenario 在 sequential、managed 和 borrowed Context 下必须得到相同
Result checksum。Parallel fallback 是合法物理选择，不改变结果。

### 3.7 Result 与 diagnostics

```text
DispatchOutcome
  -> DispatchResult       domain commands/count/checksum
  -> DispatchDiagnostics DataFlow/runtime/evidence facts
```

两者均 detached。Diagnostics 不参与 Result checksum，也不成为业务成功条件。

## 4. Industrial 迁移

RTD 的 canonical correctness、multi-source、Join、GroupBy、parallel、
budget/cancellation 和 executor ownership evidence 全部成立后，才执行：

1. 新增 application-owned `AssignmentSummarizer`；
2. 对 authoritative `OperationAssignmentTable` 单遍扫描，推导 count、makespan、
   per-job completion、total/weighted tardiness；
3. 保持 `ScheduleResult`、checksum、validator、dispatch behavior 和 workload；
4. 删除 `AssignmentSummaryFlow`；
5. 删除 `SolveEvidence` 与 test bridge 中只服务旧展示责任的 DataFlow 字段；
6. 根据 generated companion 的 compile/runtime 事实保留 `soma-dataflow`
   dependency，并用准确注释说明。

不得在 dispatch commit 中维护累计 tardiness 或第二份 completion fact。

## 5. RA-GATE-001

当前 `DataFlowComponentBenchmark` 的 private outer helper 被 nested workload 调用，
Zulu javac 8 为其生成 `access$N`。增量 class set 若混合不同一轮 outer/nested
class，会在 admission 产生 `NoSuchMethodError`。

Stage 1 修复：

- 将 nested workload 使用的 outer helper 改为 package-private static，消除 outer
  synthetic accessor；
- Gate 在 admission 前使用 `javap -p` 拒绝 outer `access$N`；
- 执行 clean/repeat class-family/descriptor 检查和一次
  `warmup=0/iterations=1` admission；
- 不运行三 fork baseline，不改变 workload、threshold 或 baseline。

实际窄诊断确认：

- outer `access$N` 已消失，outer/Workload family 和 descriptor 闭合；
- 15-lane admission 一次通过；
- 两次 clean Zulu javac 8 对同一 source 的 loop 与 try/finally 产生语义等价但
  raw bytecode SHA 不同，因此 raw class SHA 不能作为本 Gate 的正确身份；
- Gate 以稳定 family/descriptor preflight 加完整 class-load/correctness
  admission 拒绝真实 class-set mismatch，不以重复 fork 或伪造 byte-stable
  结论掩盖。

## 6. 实施切片

| Slice | 内容 | 最小验证 |
|---|---|---|
| `S1-A` | RA-GATE-001 | class family/descriptor、single admission |
| `S2-A` | grassing Session/failure | lifecycle/failure representative checks |
| `S2-B` | projection/result/POM | AoS、architecture、default correctness |
| `S3-A` | RTD config/scenario/schema/runtime | compile、snapshot/delta、replay |
| `S3-B` | rule/template/commit/result | reference result、sequential journey |
| `S3-C` | parallel/budget/cancel/borrowed | equivalence与negative evidence |
| `S4-A` | industrial summary replacement | oracle、validator、DataFlow residue check |
| `S5-A` | portfolio checker/evidence | isolation、Fast/Scale/Soak topology |
| `S6-A` | formal atomic cutover | scope audit、full Gate、docs closeout |

每个切片必须独立正确；不存在“以后补上 consumer/validation 才成立”的中间实现。
