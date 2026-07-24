# Gate 与 Baseline 详细设计

类型：Temporary

状态：active（Stage 1 candidate）

Owner：SOMA reference application scale performance baseline governance

正式事实源：否

实施授权：application performance runner、comparator orchestration 与 Gate

事实范围：六份 baseline 的 ownership、命名、执行入口和原子切换

非事实范围：未校准阈值、运行频率承诺、public claim 和 release Gate

最后审查日期：2026-07-24

## 1. Ownership

三层模型不变：

```text
soma-benchmarks
  owns component baseline + neutral comparator

child application
  owns default / large / long-run workload + six application baselines

approved public Report
  owns public claim (current count remains 0)
```

一个应用的三个 profile 可以共用 application-owned runner/assembler，但不能把
workload、threshold 或 baseline 移到 `soma-benchmarks`。两个 child POM 继续不依赖
benchmark module；脚本使用 comparator 的已编译 CLI。

## 2. Baseline naming

候选文件：

```text
benchmark/performance-baseline-default-zulu8-macos-aarch64-v2.json
benchmark/performance-baseline-large-zulu8-macos-aarch64-v1.json
benchmark/performance-baseline-long-run-zulu8-macos-aarch64-v1.json
```

每个 child 各三份。`default` 使用 v2 是因为 workload/artifact identity 取代现有
v1；`large/long-run` 是首次建立。文件版本与 baseline schema version 独立；
baseline schema 继续 `soma-performance-baseline-v1`，除非 strict protocol
确实缺少必要能力。

## 3. Runner

建立 application-specific、profile-aware runner：

```text
run-industrial-scheduler-performance <profile> <forks> <evidence-dir>
run-grassing-simulation-performance  <profile> <forks> <evidence-dir>
```

runner 必须：

- 只接受 `default|large|long-run`；
- 从 profile-specific benchmark options 读取 warmup/measurements；
- 由调用方显式指定 forks，artifact 内校验连续 fork；
- 根据 profile 选择固定 heap；
- 每 fork 启动独立 Zulu JDK 8 JVM；
- 验证 exact shape、identity stability 和 `claimAllowed=false`；
- 普通模式读取唯一 checked-in baseline，校准模式只生成 artifact，不改文件。

应用专项 correctness/architecture Gate 不应复制 runner 逻辑。兼容入口可以调用
Fast Gate，但不成为第二 workload Owner。

## 4. Gate topology

| Gate | Workload | Ordinary fork | 综合入口 |
|---|---|---:|---|
| Fast | 两个 `default` | 各 3 | 普通 `check.sh` |
| Scale | 两个 `large` | 各 3 | 专项 |
| Soak | 两个 `long-run` | 各 3 | 专项 |
| Full Performance | Fast + Scale + Soak + ownership | 复用各结果或串行运行 | 性能专题、rebaseline、最终收口 |

普通 `check.sh` 保持可用反馈时间，只强制 Fast；Scale/Soak 不是可选 baseline，
而是由独立命令执行的正式 Gate。本专题最终 closeout、任何 application baseline
更新以及归因性能优化必须运行 Full Performance。正式 Engineering 最终登记何种
变更必须运行专项 Gate，不能把它们描述为未来计划。

## 5. Architecture Gate

原子切换后检查：

```text
component baseline             = 1
reference-application baseline = 6
public claim                   = 0
```

并逐个验证 layer、subject、profile identity、文件命名、application test-resource
归属、production JAR purity、child POM independence、runner/comparator connection。
只计数不足以证明 ownership。

## 6. Comparator 与 threshold

复用 strict v1 comparator：

- deterministic identity/high-water：`all-equal/equal`；
- allocation、GC、capacity growth 上界：`maximum/at-most`；
- timing：`median/at-most`；
- allocation limit：`ceil(max × 1.05)`；
- timing limit：`ceil(max(p50 × 1.50, p90 × 1.25))`，p90 nearest-rank；
- 9-fork calibration，ordinary minimum 3 fork。

若目标 workload 本身稳定地产生 GC，baseline 记录经过审查的 count/pause envelope，
不得为了沿用旧规则而要求 GC 必须为 0。GC envelope 必须依据多 fork maximum 和
明确余量公式，不能手填宽松数字。

## 7. 原子切换

切换顺序：

```text
v3 artifact + six target workloads + runner/Gates
  -> 9-fork candidate artifacts and baselines
  -> ordinary Fast/Scale/Soak pass
  -> architecture Gate changes 2 -> 6
  -> formal Engineering/Map/Conformance/Report
  -> remove old two default-v1 baselines
  -> delete Temporary
```

旧 v2 artifact 和 default-v1 baseline 的事实保留在
`2026-07-24-three-layer-performance-baseline-governance-report.md` 与 Git
provenance，不把历史 Report 改写为新结果。切换不得留下双 current baseline。
