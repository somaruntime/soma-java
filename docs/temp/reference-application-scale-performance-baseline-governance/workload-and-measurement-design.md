# Workload 与测量详细设计

类型：Temporary

状态：active（Stage 1 candidate）

Owner：SOMA reference application scale performance baseline governance

正式事实源：否

实施授权：六个目标 workload、application-owned artifact 与 feasibility

事实范围：profile identity、测量窗口、artifact 字段、归一化和校准候选

非事实范围：已校准阈值、public claim、SOMA 产品语义和未验证环境

最后审查日期：2026-07-24

## 1. Profile identity

`correctness` 不变。性能 profile 使用以下唯一 identity：

### 1.1 Scheduler

| Profile | Jobs | Operations/job | Operations | Machines | Candidates/operation |
|---|---:|---:|---:|---:|---:|
| `default` | 10 | 100 | 1,000 | 10 | 3 |
| `large` | 1,000 | 100 | 100,000 | 100 | 3 |
| `long-run` | 100 | 100 | 10,000 | 100 | 3 |

其他 setup、transport、maintenance、resource、due/priority 参数保持领域有效。
`long-run` 使用更多 machine-delay event 和足够长的 release/horizon，使
update/remove、event replay 与 calendar 贯穿 solve；不得通过制造无解或只增加
setup 阶段工作伪造压力。

### 1.2 Simulation

| Profile | World | Cells | Initial population | Ticks | Initial density |
|---|---:|---:|---:|---:|---:|
| `default` | 128 × 72 | 9,216 | 1,000 | 1,000 | 0.1085 |
| `large` | 1280 × 720 | 921,600 | 100,000 | 1,000 | 0.1085 |
| `long-run` | 400 × 225 | 90,000 | 10,000 | 10,000 | 0.1111 |

三个 profile 保持接近的初始 density。`large` 与 `long-run` 都约为一亿
initial-population ticks，但前者强调 live set，后者强调时间与 churn。birth/death
参数必须产生非零且持续的 mutation，不能因 population 很快归零而使 workload
名义规模失真。

## 2. 测量窗口

### 2.1 Scheduler

```text
problem generation              outside
session prepare/runtime project outside, report preparationNanos
session.solve                   measured timing/allocation/GC
detached result validation      outside
session close                   outside
```

solve 内的 candidate refresh/select、calendar/resource calculation、authoritative
mutation、result assembly 和正常 lifecycle 都保留。不得只测 SOMA operation
片段并称为 application-integrated 结果。

### 2.2 Simulation

```text
config/scenario generation      outside
session prepare/bootstrap       outside, report setupNanos
session.finish tick systems     measured timing/allocation/GC
detached result validation      outside
session close                   outside
```

growth、metabolism/death、reproduction、grassing、searching、trace 和最终 result
assembly 均属于完整 finish。diagnostic attribution 可以另做阶段计时，但正式
integrated timing 不删减阶段。

## 3. Artifact v3

现有 v2 无法显式区分全部 profile 规模，因此 application artifact 升级为 v3。
保留所有 v2 contract 字段，并增加：

### 3.1 Scheduler 字段

- `jobs`、`machines`、`candidatesPerOperation`；
- `operationExecutions = operations × measurements`；
- `solveNanosPerOperation`、`allocatedBytesPerOperation`；
- `maximumFrontier` 或可证明等价的实际 high-water；
- 既有 preparation/solve min/max、allocation、GC、scratch/index high-water。

### 3.2 Simulation 字段

- `worldWidth`、`worldHeight`、`worldCells`；
- `tickExecutions = ticks × measurements`；
- `tickNanosPerTick`、`allocatedBytesPerTick`；
- `populationTickUnits` 仅在能以低扰动、定义为每 tick 开始 population 求和时加入；
- 既有 setup/tick min/max、allocation、GC、maximum population、growth count 和
  scratch/index high-water。

归一化字段使用整数 ceiling division，避免 floating serialization 和平台差异。
它们用于跨 profile 解释；固定 identity 下总量仍是 primary regression metric。
如果 `populationTickUnits` 需要改变领域系统执行或产生 hot-path allocation，则本轮
不加入，不能用模糊的“individual-update”字段替代。

v3 record 继续 strict exact shape；v2 baseline 在原子切换前保持 current。

## 4. Measurement options

候选配置：

| Profile | Warmup | Measurements/fork | Ordinary forks | Calibration forks |
|---|---:|---:|---:|---:|
| `default` | 1 | 3 | 3 | 9 |
| `large` | 1 | 1 | 3 | 9 |
| `long-run` | 1 | 1 | 3 | 9 |

`large/long-run` 每次完整 execution 已包含足够多 hot-loop repetition，因此每 fork
一个 measurement；仍保留一个同 workload warmup 和 9 个独立 JVM，不退化为单次
wall-clock。Stage 1 feasibility 必须确认总运行成本；若同 workload warmup 会因
资源限制不可行，必须保留证据并请求裁决，不能自行改为无 warmup baseline。

## 5. Heap

每个 application/profile 拥有固定 `-Xms/-Xmx`，并进入 exact environment。
Stage 1 从当前 256 MiB 开始，只在 OOM、live-set 或 GC-thrash 证据下提高候选：

```text
256 MiB -> 512 MiB -> 1 GiB
```

选择满足 live set 且不会以异常 GC 主导 workload 的最小固定档位。基线的目的
不是通过无限增大 heap 获得 GC 0；allocation 与 GC 都要如实保留。

## 6. Feasibility 输出

六个单 fork feasibility 必须记录：

- exact config/input/result/schema/runtime-plan identity；
- setup 与 hot-operation wall-clock；
- allocated bytes、Young/Full GC 与 pause；
- index/scratch/capacity/population/frontier high-water；
- max heap 和完成状态；
- attribution observation，但不产生 threshold。

feasibility artifact 使用 `claimAllowed=false`，保存于临时 evidence 目录或 Stage 1
Temporary 摘要，不 checked-in 大体积原始 profiler 文件。只有 9-fork calibration
可以生成 baseline 候选。
