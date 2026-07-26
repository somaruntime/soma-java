# 工业动态调度验证

类型：应用验证

状态：当前

Owner：industrial-dynamic-scheduler

对 SOMA 产品规范性：否

最后审查日期：2026-07-27

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
- `AssignmentSummaryFlow` 使用真实 generated binding 完成 one-shot Invocation，
  count/makespan/job completion 覆盖当前 assignment facts；
- DataFlow Definition/Template/policy identity、source/scanned/output stats 完整，
  detached metrics 仍通过独立领域 validator。

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

Application-owned baseline 位于 test resources。每个普通 profile Gate 使用
3 fork；原始阈值来自 9-fork immutable calibration candidate `a7d4fde`：

| Profile | hot solve range / median | Timing limit | hot allocation range / limit |
|---|---:|---:|---:|
| default | `13.459..15.522 / 14.174 ms` | `21.261 ms` | `3.956 / 4.946 MB` |
| large | `1.278..1.295 / 1.287 s` | `1.931 s` | `111.032..117.538 / 143.338 MB` |
| long-run | `44.056..49.702 / 47.050 ms` | `70.575 ms` | `11.008..15.022 / 17.270 MB` |

| Profile | canonical end-to-end range / median | Timing limit | end-to-end allocation range / limit |
|---|---:|---:|---:|
| default | `22.255..24.496 / 22.830 ms` | `34.245 ms` | `8.097..8.098 / 10.123 MB` |
| large | `1.318..1.335 / 1.327 s` | `1.990 s` | `227.001..241.513 / 288.904 MB` |
| long-run | `70.904..77.993 / 73.962 ms` | `110.943 ms` | `31.035..35.049 / 42.303 MB` |

| Profile | exact/update/operation high-water | Frontier | 校准最大 GC / baseline envelope |
|---|---:|---:|---|
| default | `106,384 / 0 / 4,256 B` | `30` | Young `0/0 ms`，Full `0/0 ms` |
| large | `11,832,761 / 0 / 553,012 B` | `3,000` | Young `2/8 ms -> 3/10 ms`，Full `0/0 ms` |
| long-run | `1,162,144 / 0 / 48,544 B` | `300` | Young `1/3 ms -> 2/4 ms`，Full `0/0 ms` |

表中 MB/ms 仅用于阅读，baseline 保存原始整数 bytes/nanos。应用级 allocation
使用跨 fork 中位数和 `allocation=ceil(p50*1.25)` fitness envelope；timing 使用
`timing=ceil(max(p50*1.50,p90*1.25))`，不是由目标倒推。Default、large、
long-run 分别由 Fast、Scale、Soak Gate 承担，Full 组合全部六个应用 workload。
Long-run 在首次普通重放中暴露 ThreadMXBean/TLAB allocation 分布超出首轮
9-fork 最大值，因此不再用 maximum 驱动反复 rebaseline。确定性 high-water
继续 `all-equal`，GC 继续取 maximum。

日常应用性能 Gate 固定 3 fork。新 baseline 通常使用 5 fork；9 fork 只用于获得
明确授权的方差诊断或 public claim 准备，不属于普通开发、治理收口或失败后的
自动重跑。

Typed DataFlow 接入后，default、large、long-run 各执行一次有界 5-fork
non-regression；没有重新计算或放宽上述阈值。Schema、input/result checksum
保持不变，generated-runtime protocol 升为 v5 后只迁移 RuntimePlan hash 与
baseline provenance：

| Profile | 5-fork hot solve range | hot allocation range | Young / Full GC |
|---|---:|---:|---:|
| default | `14.68..15.75 ms` | `4.06 MB` | `0 / 0` |
| large | `1.30..1.33 s` | `121.93..126.59 MB` | `2 / 0` |
| long-run | `47.45..50.91 ms` | `11.86..14.99 MB` | `1 / 0` |

这组证据只证明新增 summary DataFlow 没有破坏既有应用性能包络。它不把应用
frontier 迁入通用 graph，也不形成跨环境 claim。

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
它证明该 workload 在记录的 Zulu JDK 8 本机可回归比较，不证明 SOMA 普遍优于其他
存储，也不外推为支持矩阵或 public claim。

Gate 还要求：

- clean/repeat 生成源码和 schema artifact byte-stable；
- production JAR 不含 fixture/oracle/verification/benchmark；
- production `prepare()` 不执行 test-only 全投影逐值复核；
- application 不绕过 Solver facade；
- config/problem 不依赖 SOMA runtime/generated code；
- 旧 `state`、bootstrap、runner、candidate Table、runtime diagnostics Result
  和 config-as-problem-identity 无 current 残留。
