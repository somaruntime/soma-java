# 工业动态调度验证

类型：应用验证

状态：当前

Owner：industrial-dynamic-scheduler

对 SOMA 产品规范性：否

最后审查日期：2026-07-24

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

## 性能 artifact

`industrial-scheduler-benchmark-v3` 的每个 record 来自独立 JVM fork，包含：

- config/input/result checksum 与 schema/runtime-plan identity；
- commit、fork/configured forks、实际 JDK/JVM/OS/architecture/CPU/max heap；
- profile、jobs/operations/machines/candidates、warmup/measurement 与实际
  operation executions；
- preparation/solve nanos、nanos/operation；
- current-thread allocated bytes；
- Young/Full GC count 与 pause；
- exact-index、update scratch、operation scratch high-water；
- maximum frontier capacity；
- `claimAllowed=false`。

Application-owned baseline 位于 test resources。每个普通 profile Gate 使用
3 fork；9-fork 校准候选为 `1af43ac`：

| Profile | 9-fork hot operation range / median | Timing limit | Allocation range / limit |
|---|---:|---:|---:|
| default | `30.387..32.105 / 31.159 ms` | `46.738 ms` | `16.070..16.075 / 16.878 MB` |
| large | `8.552..8.874 / 8.694 s` | `13.041 s` | `411.337..414.294 / 435.008 MB` |
| long-run | `130.108..138.278 / 133.318 ms` | `199.976 ms` | `39.731..42.415 / 44.536 MB` |

| Profile | exact/update/operation high-water | Frontier | 校准最大 GC / baseline envelope |
|---|---:|---:|---|
| default | `1,438 / 2,520 / 160 B` | `30` | Young `0/0 ms`，Full `0/0 ms` |
| large | `106,479 / 160,545 / 14,380 B` | `3,000` | Young `5/17 ms -> 6/22 ms`，Full `0/0 ms` |
| long-run | `15,086 / 15,015 / 1,256 B` | `300` | Young `1/4 ms -> 2/5 ms`，Full `0/0 ms` |

表中 MB/ms 仅用于阅读，baseline 保存原始整数 bytes/nanos。Default、large、
long-run 分别由 Fast、Scale、Soak Gate 承担，Full 组合全部六个应用 workload。

Comparator 在 exact environment/workload 下判断 `passed/failed`，环境不同时为
`not-applicable`；无论结果如何，invalid schema/shape/claim/fork/identity 都失败。
它证明该 workload 在记录的 Zulu JDK 8 本机可回归比较，不证明 SOMA 普遍优于其他
存储，也不外推为支持矩阵或 public claim。

Gate 还要求：

- clean/repeat 生成源码和 schema artifact byte-stable；
- production JAR 不含 fixture/oracle/verification/benchmark；
- application 不绕过 Solver facade；
- config/problem 不依赖 SOMA runtime/generated code；
- 旧 `state`、bootstrap、runner 和 config identity 无 current 残留。
