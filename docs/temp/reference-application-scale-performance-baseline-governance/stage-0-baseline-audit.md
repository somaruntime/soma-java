# Stage 0 基线审计

类型：Temporary

状态：active（Stage 0 evidence）

Owner：SOMA reference application scale performance baseline governance

正式事实源：否

实施授权：只读审计与 Stage 0 文档证据

事实范围：`7252646` 上的 workload、benchmark、baseline、Gate 和成本现状

非事实范围：新 workload 已实现、正式性能结论、Stage 1 详细设计和 public claim

最后审查日期：2026-07-24

## 1. 审计方法

审计以 commit `7252646` 为起点，读取：

- 两个应用的 production/test config、generator 和 runtime loop；
- `SchedulerBenchmark`、`SimulationBenchmark` 与 benchmark options；
- 两个应用专项 Gate 和三层 ownership Gate；
- 两份 application baseline、正式 benchmark Engineering 和最近治理报告。

Stage 0 未修改或运行任何目标大规模 workload，也未生成校准数字。

## 2. 当前 workload

### 2.1 Industrial scheduler

| Profile | 当前规模 | 当前责任 |
|---|---|---|
| `correctness` | 2 jobs × 3 operations，3 machines，2 candidates | oracle、失败路径 |
| `default` | 24 × 8 = 192 operations，12 machines，3 candidates | production CLI、3-fork baseline |
| `large` | 400 × 20 = 8,000 operations，64 machines，4 candidates | capacity/frontier/allocation verification |
| `long-run` | 250 × 40 = 10,000 operations，80 machines，5 candidates | update/remove/event verification |

当前 frontier 每次决策执行全 candidate refresh，再执行
`filter -> sorted -> limit(1)`。由于每个 job 同时最多发布一个 operation，
目标 workload 的典型 frontier 上界约为：

| Target | Operations | Jobs × candidates | 主要压力 |
|---|---:|---:|---|
| `default` | 1,000 | 30 | 快速端到端回归 |
| `large` | 100,000 | 3,000 | 约 3 亿 candidate refresh，并反复 dynamic sort |
| `long-run` | 10,000 | 300 | 持续 mutation、event 和较长 one-shot solve |

`large` 可能暴露完整排序、frontier refresh 或 detached Problem live set 的真实
瓶颈；这是目标证据，不是降低规模的理由。

### 2.2 Grassing simulation

| Profile | 当前规模 | 当前责任 |
|---|---|---|
| `correctness` | 8 × 6、5 individuals、12 ticks | 逐 tick AoS oracle、失败路径 |
| `default` | 96 × 72、800 individuals、500 ticks | production CLI、3-fork baseline |
| `large` | 512 × 384、30,000 individuals、300 ticks | capacity/column/group/bulk verification |
| `long-run` | 256 × 192、5,000 individuals、2,000 ticks | birth/death churn 与长期 invariant |

每 tick 至少包含一次 grass growth、三次 cell scratch fill、一次 share scan 和
一次 consumption publish，约六次全 logical-cell 访问；另外还有多次 population
scan/update。

目标 logical world 保持接近 `0.11 initial individual/cell`：

| Target | Individual-ticks | Logical cells | 约计全 cell 访问 |
|---|---:|---:|---:|
| `default` | 1,000,000 | 9,216 | 55,296,000 |
| `large` | 100,000,000 | 921,600 | 5,529,600,000 |
| `long-run` | 100,000,000 | 90,000 | 5,400,000,000 |

该选择让 `large` 测大 live set，让 `long-run` 测时间长度，同时避免
`1920 × 1080` 空 world 把基线变成外部 primitive-array bandwidth 测试。

## 3. 当前 benchmark contract

两个 runner 当前共同采用：

- `warmup=1`、`measurements=3`；
- 普通 Gate 为 3 个独立 JVM fork；
- `-Xms256m -Xmx256m`；
- current-thread allocated bytes；
- Young/Full GC count 与 pause；
- exact-index、update scratch、operation scratch high-water；
- input/result/schema/runtime-plan identity；
- `claimAllowed=false`。

Scheduler measurement 只覆盖 `session.solve()`；problem generation 和
`prepare(problem)` 在 timing/allocation 外。Simulation measurement 只覆盖 tick
systems；config、scenario generation、bootstrap 和 tick-0 trace 在外。
Correctness validation 在测量后执行，不污染 hot-operation timing/allocation。

当前两个专项脚本都硬编码 `default`，各自只读取一份精确 workload baseline。
现有 comparator 协议可以复用，但尚未证明支持一个应用同时治理三个 profile 的
文件命名、调用和架构检查。

## 4. 当前 baseline 与 Gate

当前 checked-in 数量：

```text
component baseline             = 1
reference-application baseline = 2
public performance claim       = 0
```

两份应用 baseline 均绑定旧 `default` identity、Zulu JDK 8/macOS/aarch64/Apple
M5 Pro、9-fork calibration 和 `artifactVersion=*benchmark-v2`。三层结构 Gate
把 application baseline 数量固定为 2；应用脚本把文件名固定为
`performance-baseline-zulu8-macos-aarch64-v1.json`。

因此后续不能只新增 JSON：

- runner 必须选择并输出 profile-specific identity；
- comparator 调用必须逐 profile 绑定唯一 baseline；
- architecture Gate 必须验证 1 component、6 application、0 public claim；
- baseline 不得进入 production JAR；
- child application 不能依赖 `soma-benchmarks`；
- old baseline 必须在原子切换前继续 current，在切换后退出唯一 Owner。

## 5. 差距

| 关注点 | 当前事实 | 后续需要 |
|---|---|---|
| workload | 仅旧 default 建基线 | 实现六个已确认 target |
| runner selection | 脚本硬编码 default | profile-specific fail-closed selection |
| options | 单一 warmup/forks/measurements | 各 profile 的固定测量身份 |
| heap | 两应用统一 256 MiB | feasibility 后裁决固定 profile heap |
| artifact | 只记录旧 workload 字段 | 保持版本化并补充必要归一化分母 |
| baseline | 每应用一份 | 每应用三个独立 workload baseline |
| Gate | application baseline 数固定为 2 | Fast/Scale/Soak/Full 和数量/Owner 检查 |
| docs/report | 只陈述旧 default 数据 | 最终原子更新 current Owner，历史报告不改写 |

## 6. 主要风险

1. Scheduler `large` 可能因 100,000 次 refresh/sort 使运行时间显著增长。
2. 100,000-operation detached input 与 100,000-individual simulation 可能超过
   当前 256 MiB heap；扩大 heap 会改变 baseline identity 和 GC 解释。
3. Simulation `large/long-run` 各约 55 亿次 logical-cell 访问，校准和 Full
   Performance Gate 可能耗时较长。
4. 过大 heap 可能隐藏 GC，过小 heap 会把 OOM/GC thrash 误判为 SOMA throughput。
5. 把六个 workload 全部塞进普通 `check.sh` 可能降低快速反馈；但完全移出正式
   Gate 又会使 scale/soak baseline 名存实亡。
6. timing、allocation 或 high-water 新字段会触发 artifact compatibility surface，
   必须版本化并增加 negative paths。
7. 旧 baseline 与新 baseline 并存期间容易形成双 current Owner，必须依靠最终
   原子切换而非长期兼容分支。

## 7. Stage 0 裁决

- 六个目标 workload 已冻结为专题目标，不在 Stage 0 修改配置；
- `correctness` profile 保持原规模和职责；
- current 三层模型、两份 application baseline、脚本和正式报告不修改；
- Stage 1 先完成详细设计与六个单 fork feasibility，再决定 heap、measurement
  和 Gate 运行频率；
- feasibility 失败只产生诊断和停止决定，不允许自动缩小规模；
- core runtime 优化、产品语义和 public claim 明确排除。

Stage 0 已具备进入 Stage 1 的设计输入；是否开始 Stage 1 仍需后续明确授权。

## 8. Stage 0 验证

在 Azul Zulu `1.8.0_492-b09`、Maven `3.9.16`、macOS `26.5.2`、
aarch64 环境完成：

- `./scripts/check-docs.sh`：`doc-check: ok`；
- `./scripts/check.sh`：`project-check: ok`；
- 三层结构：component `1`、reference application `2`、public claim `0`；
- 两个现有 application `default` baseline：环境适用并通过；
- component baseline：5 fork、9 个 comparator negative-path case 通过；
- `git diff --check`：通过。

以上只确认 `7252646` 的既有产品、应用和性能基线没有被 Stage 0 文档变更破坏。
没有执行目标 `default/large/long-run`、单 fork feasibility 或大规模校准，也没有
形成任何新的性能结论。
