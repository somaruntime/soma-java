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
| default | 192 operations | 普通 CLI journey、multi-fork |
| large | 8,000 operations | capacity、frontier 与 allocation smoke |
| long-run | 10,000 operations | 持续 update/remove、event 和 checksum |

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

`industrial-scheduler-benchmark-v2` 的每个 record 来自独立 JVM fork，包含：

- config/input/result checksum 与 schema/runtime-plan identity；
- commit、fork/configured forks、实际 JDK/JVM/OS/architecture/CPU/max heap；
- warmup、measurement、preparation/solve nanos；
- current-thread allocated bytes；
- Young/Full GC count 与 pause；
- exact-index、update scratch、operation scratch high-water；
- `claimAllowed=false`。

Application-owned baseline 位于 test resources。普通 Gate 使用 3 fork；9-fork
校准 `ab28350` 得到：

- allocation `8,319,504..8,320,064 bytes`，上限 `8,736,068`；
- solve p50 `37,619,668 ns`、p90/max `39,133,584 ns`，median 上限
  `56,429,502 ns`；
- exact/update/operation scratch high-water 固定为
  `3,015 / 7,560 / 368 bytes`；
- Young/Full GC count 与 pause 均为 `0`。

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
