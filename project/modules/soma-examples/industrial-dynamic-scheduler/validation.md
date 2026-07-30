# 工业动态调度验证

类型：应用验证

状态：当前

Owner：industrial-dynamic-scheduler

对 SOMA 产品规范性：否

最后审查日期：2026-07-30

## 配置责任

| Config | 规模 | 责任 |
|---|---:|---|
| correctness | 6 operations | generator replay、手算 oracle、负路径 |
| default | 10 jobs、1,000 operations、10 machines、3 candidates/operation | 普通 CLI journey、Fast multi-fork |
| large | 1,000 jobs、100,000 operations、100 machines、3 candidates/operation | 大 frontier、capacity、throughput 与 Scale multi-fork |
| long-run | 100 jobs、10,000 operations、100 machines、3 candidates/operation | machine delay、持续 update/remove、event 与 Soak multi-fork |

每个 profile 都生成两次 input 并比较 checksum，再创建两个独立 runtime 比较结果；
problem generation 与 preparation 不计入 solve measurement。`default` 是生产
resource；其余三个 profile、fixture 和 benchmark options 位于 test resources。
Problem profile 不包含 `benchmark.*`，因此输入 identity 与测量过程相互独立。

## 独立领域 validator

Validator 不读取 SOMA table 内部状态，基于 detached Problem 和完整 Result
重新验证：

- operation cardinality/identity 与 eligible processing duration；
- release、material、precedence、transport；
- setup arithmetic、machine no-overlap 与 sequence-dependent setup；
- maintenance 和 dynamic machine delay；
- secondary-resource sweep-line capacity；
- due、priority、makespan、tardiness；
- checked arithmetic、非负 duration 与稳定 result checksum。

手算 fixture 预期：

```text
operation 101 -> machine 10, [setup/start/end] = [3/3/7]
operation 102 -> machine 20, transport = 2, [setup/start/end] = [9/9/13]
makespan = 13, tardiness = 1, weighted tardiness = 2
```

该 fixture 同时让另一候选机受 maintenance 和 machine-delay 影响。

## Runtime 负路径

correctness lane 验证：

- invalid detached input 在 Runtime projection 前被拒绝；
- solver 第二次执行被拒绝；
- assignment mutation 后旧 `IndexSnapshot` stale；
- other-table snapshot 作为 wrong source 被拒绝；
- aggregate release 后访问被拒绝；
- 反转 materialized result 不改变 checksum。
- `AssignmentSummarizer` 使用 direct primitive ColumnView 单遍覆盖当前
  assignment facts 和全部 job；
- count/makespan/tardiness 在事实产生处完成覆盖与一致性校验，detached metrics
  仍通过独立领域 validator。
- 九槽SomaGroup在create后报告9个active member/Table，release后member、
  Table和structural resources全部归零；partial-create只关闭Group一次。

## 性能 artifact

`industrial-scheduler-benchmark-v4` 的每个 record 来自独立 JVM fork，包含：

- config/input/result checksum 与 schema/runtime-plan identity；
- commit、fork/configured forks、实际 JDK/JVM/OS/architecture/CPU/max heap；
- profile、jobs/operations/machines/candidates、warmup/measurement 与实际
  operation executions；
- preparation、hot solve 与 canonical end-to-end nanos；
- hot solve 与 end-to-end current-thread allocated bytes；
- Young/Full GC count 与 pause；
- exact-index、update scratch、operation scratch high-water；
- maximum frontier capacity；
- `claimAllowed=false`。

Application-owned baseline位于test resources，当前版本为default v7、large v6、
long-run v6。Amazon Corretto 8成为唯一JDK authority后，三个profile均在同一
Corretto 8本机以5-fork candidate重新校准；旧Zulu baseline只由Git保存其历史
evidence含义：

| Profile | RuntimePlan | hot time/allocated limit | end-to-end time/allocated limit | Young/Full GC envelope |
|---|---|---:|---:|---|
| default | `cd8095…` | `25,213,563 ns / 4,959,510 B` | `40,426,248 ns / 10,978,790 B` | `0/0 ms；0/0 ms` |
| large | `3eddfd…` | `3,488,431,001 ns / 148,686,770 B` | `3,549,411,188 ns / 303,741,530 B` | `3/10 ms；0/0 ms` |
| long-run | `9b56e4…` | `89,828,187 ns / 15,374,160 B` | `129,993,626 ns / 40,671,230 B` | `2/4 ms；0/0 ms` |

当前identity replacement的校准commit为
`a24bb4d48eec430d6188cb0588b28300a407da6d`，每个baseline登记自己的candidate
content checksum；
allocation使用`ceil(p50×1.25)`，timing使用
`ceil(max(p50×1.50,p90×1.25))`，确定性high-water仍为`all-equal`，GC取maximum
包络。Config/input/result和领域validator identity保持。校准时还发现旧baseline
的Schema/RuntimePlan identity已落后于当前生成物；在旧Zulu和新Corretto上对同一
当前source独立生成得到相同新identity，因此该变化归因于旧evidence漂移，而不是
JDK不确定性。

日常Gate固定3 fork；Fast、Scale、Soak分别承担三个profile，Full组合九个应用
workload。5-fork用于新baseline，9-fork只用于明确方差诊断或public claim准备，
不能在失败后自动升级fork或循环放宽阈值。DataFlow application coverage由独立
RTD拥有；工业frontier继续application-owned。

冻结前profiling进一步发现`CandidateFrontier`在同一machine candidate group内
重复读取不会变化的machine version/family/availability。`b189d1130055…`把这些
authoritative ColumnView读取提升到group边界，并把已读取snapshot传入refresh；
没有跨mutation保存current Index或View。100K diagnostic solve约下降8.9%，1M
fixed-100-machine diagnostic约下降30.7%，随后九profile Full在`eac9b60fdbbb…`
全部通过且Industrial baseline无需放宽。1M单位成本仍约为100K的6.4倍，因此
application-owned frontier的非线性规模边界保持公开，不外推为任意调度模型SLA。

该校准同时保护两条不同责任的路径：

- hot solve 排除 generation 和 preparation，观察算法循环与 SOMA runtime access；
- canonical end-to-end 从 `prepare(problem)` 开始，包含 Table construction、
  batch projection、solve、result materialization 和 close，不包含 synthetic
  generation。

早期候选在 large profile 中为每个 operation 建立一个 eligible-machine child
Table，9 fork 端到端分配约 `718..724 MB`，并在 3/9 fork 发生 Full GC。最终
Schema 改为一张 flat immutable exact-group Table，并把逐值 projection
verification 留在 test-only Gate；最终 9 fork 端到端分配降至
`227.001..241.513 MB`，Full GC 为零。exact-index high-water 的增长是 300,000
option 的受控 flat access path，不是未界定的临时对象。

Comparator 在 exact environment/workload 下判断 `passed/failed`，环境不同时为
`not-applicable`；无论结果如何，invalid schema/shape/claim/fork/identity 都失败。
它证明该 workload 在记录的 Corretto JDK 8 本机可回归比较，不证明 SOMA 普遍优于其他
存储，也不外推为支持矩阵或 public claim。

Gate 还要求：

- clean/repeat 生成源码和 schema artifact byte-stable；
- production JAR 不含 fixture/oracle/verification/benchmark；
- production `prepare()` 不执行 test-only 全投影逐值复核；
- application 不绕过 Solver facade；
- config/problem 不依赖 SOMA runtime/generated code；
- 旧 `state`、bootstrap、runner、candidate Table、runtime diagnostics Result
  和 config-as-problem-identity 无 current 残留。
