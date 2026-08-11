# SOMA V1 Scheduling Reference Application 治理

> 本文的约18秒数据保留为初始实现provenance；current性能与profile驱动优化由
> [Scheduling性能治理](v1-scheduling-performance-governance.md)拥有。

类型：Conformance / Reference Application Governance

状态：`PASS / STANDARD 100K FJSP QUALIFIED / TEMPORARY REPLACEMENT CLOSED`

日期：2026-08-11

Owner：标准FJSP application topology、运行时状态建模、FCFS + SPT执行、结果校验、
100K正常路径与增量Index治理的application-level回归证据边界

## 1. 最终结论

暂停的scheduling重构已经完成。当前project不再是三张Table和一次Join组成的示例片段，而是具有
configuration、immutable modeling、deterministic factory、runtime schema、solver API/core、
result query与independent validation的完整reference application。

默认journey固定为：

```text
1,000 Jobs × 100 Operations
100 Machines
3 candidate Machines / Operation
100,000 Operations / 300,000 Processing Options
machine-first FCFS + SPT
```

四张SOMA Table拥有求解期状态：`MachineState`、`JobState`、`OperationState`与
`MachineWaitingOperation`。`OperationState`是工序状态和最终结果的唯一权威事实；waiting Table
是READY工序面向dispatch的派生访问结构。

暂停期用于绕开旧runtime缺陷的fixed waiting slots与application `PriorityQueue` frontier已经全部
删除。当前solver对waiting rows执行自然point `remove/add`，并通过`machineId`、`operationId`
两个真实访问方向的secondary Index完成缩窄。没有tombstone、stale membership、reverse Index、
application sidecar或第二套状态truth。

## 2. Application architecture

| 层 | Owner |
|---|---|
| `configuration` | properties到immutable `SchedModelFactoryConfig` |
| `modeling` | Machine/Job/Operation/ProcessingOption immutable input model与模型校验 |
| `factory` | 给定seed和规模后完全确定的标准FJSP生成 |
| `runtime/schema` | 四张SOMA Table的solver runtime state |
| `solver/api` | solve、配置、状态、summary与operation-oriented result query |
| `solver/core` | 唯一machine-first FCFS + SPT实现 |
| `validation` | precedence、candidate、duration、machine non-overlap、completeness、makespan |
| `application` | external config与完整reference journey入口 |

没有新增production artifact或dependency。JUnit Jupiter保持test-only；未准入Lombok。schema
generation仍使用既有full-regeneration processor path，ordinary application source使用`proc:none`。

## 3. Runtime modeling 与访问路径

每次dispatch先在有waiting membership的100台Machine中按`availableTime/machineId`选择一台，再在
其IndexSelection中按`readyTime/processingTime/business IDs`选择FCFS + SPT候选。被选Operation
在所有候选Machine上的waiting rows通过Key逐条删除；若存在后继，则更新权威`readyTime/status`
后逐条加入新rows。

Index准入以访问模式而不是“可能查询”驱动：

- `OperationState.jobId`服务结果`byJob`；
- `MachineWaitingOperation.machineId`服务machine queue；
- `MachineWaitingOperation.operationId`服务一次dispatch的全部candidate membership；
- `assignedMachineId`在求解前是低选择性零值且每条记录会改变，结果期才需要`byMachine`，因此保持
  ordinary Field并使用typed filter；
- Job/Operation status没有当前Index consumer，因此不建立高重复、高维护成本的Index。

这同时验证了Storage Design的当前合同：point remove的成本由受影响Index与Bucket大小主导，而非
每次重建整张Table的全部sidecar。跨Table发布仍由application顺序拥有；SOMA没有被误描述成
cross-Table transaction Owner。

## 4. Correctness evidence

### 4.1 Focused Java 8 tests

```text
mvn -f soma-examples/scheduling/pom.xml clean test
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

覆盖：标准properties与无`releaseTime`、factory确定性和规模、machine-first选择、同readyTime下SPT、
不同readyTime下FCFS优先于SPT、
后继`readyTime`传播，以及完整独立结果验证。注：正式规则名为SPT；测试名与实现均按FCFS + SPT
解释，没有准入可配置dispatch rule。

### 4.2 Standard 100K journey

固定环境：Apple M5 Pro、48 GiB物理内存、Amazon Corretto `1.8.0_502`、Java 8、
`-Xms128m -Xmx1g`。完整factory、solve和result validator：

```text
scheduling-reference: PASS
operations=100000 makespan=50281 elapsedMs=17815
```

另一次`-Xms2g -Xmx16g` fresh process重放得到`elapsedMs=17665`；完整`./scripts/check.sh`中的
isolated package consumer再次得到`elapsedMs=18377`。三次结果规模与makespan一致。
`elapsedMs`覆盖runtime Tables初始化与solve，不包含main之前的input factory和solve之后的独立结果
validation。绝对值只是本机治理证据，不是跨硬件SLA。

validator对100,000个Operation逐项证明precedence、candidate-machine、processing time和point query，
并按1,000个Job与100台Machine证明结果完整、机器不重叠及makespan。solve结束时waiting Table
必须为空。

## 5. Governance effectiveness

暂停记录中的三次100K实验分别在`148.53s`、`154.55s`与`419.26s`被主动终止，均未产生完整结果；
其中后两次已经叠加fixed slots，最后一次还叠加application frontier。它们是历史诊断，不是严格
受控A/B，因此本记录不计算加速比。

当前更直接的实现删除上述两层workaround，仅依赖SOMA自然point `add/remove/update`，却在本轮
三次约`17.7–18.4s`的solve时间内完成同一确定性100K模型并通过全结果校验。结合
[32位结构域与即时增量Key/Index资格](v1-incremental-structural-mutation-governance.md)中独立的
detached predecessor A/B，可以得出有边界的结论：

> 本轮底层治理已经把原先无法完成的正常scheduling mutation journey恢复为可运行、可校验的
> application路径；应用不再需要用固定槽位或第二套frontier掩盖SOMA mutation缺陷。

这不证明约18秒已经是FJSP solver的最终性能。当前每个Operation仍包含多个Table terminal与跨Table
point publication；后续若优化，应先profile并在不改变四张Table事实Owner和算法语义的前提下处理，
不能重新引入parallel truth或application shadow state。

## 6. Benchmark 与 delivery closure

既有`scheduling` benchmark需要保持长期ingest/scan/Key/Index/Join/top/GroupBy时间序列，但不应
约束FJSP runtime schema。它已迁移为`benchmarks`内部non-production schema，operation family和
fingerprint保持不变：

```text
SOMA_BENCHMARK_WORKLOAD=core
SOMA_BENCHMARK_SCENARIOS=scheduling
SOMA_BENCHMARK_IMPLEMENTATIONS=soma-auto
SOMA_BENCHMARK_ROWS=10000
SOMA_BENCHMARK_RUNS=1
benchmark-summary: PASS (1 records, 1 groups)
```

`scripts/qualify.sh`的source/package consumer路径已从旧`scheduling/schema`迁移到
`scheduling/runtime/schema`；source-delivery allowlist与archive assertion同时包含标准properties。
Root example导航与scheduling README已改为当前事实。

本记录允许声称standard 100K FJSP reference journey在上述固定环境通过；不允许外推为通用FJSP
算法质量、最优排程、生产SLA、一亿行资格、remote artifact publication或正式release。
