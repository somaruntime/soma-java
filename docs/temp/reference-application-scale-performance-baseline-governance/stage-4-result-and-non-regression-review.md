# Stage 4 结果与非回归审查

类型：Temporary

状态：completed（Stage 4）

Owner：SOMA reference application scale performance baseline governance

正式事实源：否

事实范围：规模增长解释、性能归因、软件叙事与 scope non-regression

非事实范围：public performance claim、支持矩阵和 release readiness

审查范围：`7252646..938b3d5`

最后审查日期：2026-07-24

## 1. 目标规模没有缩水

六个 baseline 的 exact identity、runner 的目标检查和 Full Performance Gate 共同
证明：

- scheduler：`1,000 / 100,000 / 10,000` operations，均为每 operation 3 个
  candidate；machines 为 `10 / 100 / 100`；
- simulation：`1,000 × 1,000`、`100,000 × 1,000`、
  `10,000 × 10,000` individuals/ticks；logical world 分别为
  `128×72 / 1280×720 / 400×225`；
- default/large/long-run 的 warmup、measurement、固定 heap 和普通 3-fork /
  校准 9-fork 责任均未降低；
- correctness、result validation、持续 event/churn 和
  `claimAllowed=false` 没有从测量链中删除。

## 2. Scheduler 增长解释

9-fork calibration 的中位归一化结果：

| Profile | Nanos/operation | Bytes/operation | Frontier capacity | Index / update / operation high-water |
|---|---:|---:|---:|---:|
| default | `10,387` | `5,359` | `30` | `1,438 / 2,520 / 160 B` |
| large | `86,943` | `4,114` | `3,000` | `106,479 / 160,545 / 14,380 B` |
| long-run | `13,332` | `4,067` | `300` | `15,086 / 15,015 / 1,256 B` |

三个 profile 不是只改变一个参数的 micro scaling series。Large 同时把 operations
提高 100 倍、machine 提高 10 倍，并把最大 live frontier 从 30 提高到 3,000；
当前领域算法必须维护完整 frontier 的全局动态顺序，因此 per-operation cost 高于
default/long-run 是可解释的应用算法成本。它不能与旧 FJSP 的 machine-local
arg-min 数字直接比较。

allocation 的增长反而低于 operation execution 增长：large 的 bytes/operation
低于 default，long-run 也保持同量级；三个 scratch/index high-water 随 frontier
和工作集增长，没有反复扩容或失控证据。Stage 1 暴露的 21.61 GB 临时对象问题
已归因并由 application-owned primitive projection 与 version-aware refresh
修复；Stage 3 的 large 为约 411–414 MB、Full GC `0`。

## 3. Simulation 增长解释

| Profile | Nanos/tick | Bytes/tick | Maximum population | Index / update / operation high-water |
|---|---:|---:|---:|---:|
| default | `48,969` | `3,844` | `1,433` | `67,933 / 34,392 / 12,776 B` |
| large | `5,948,031` | `426,403` | `158,318` | `9,152,156 / 3,799,632 / 1,659,040 B` |
| long-run | `360,405` | `4,736` | `13,837` | `693,389 / 332,088 / 121,364 B` |

Large 的 maximum population 约为 default 的 110 倍，per-tick timing 约为
121 倍，三个 high-water 约为 110–135 倍，符合大 live-set 与逐个体系统执行的
成本模型。其约 426 MB allocation 主要与更大的 population growth/capacity
路径相关；15 次 Young GC 和 1 次 Full GC 均稳定，Full pause 最大 `28 ms`，
不足总 hot-operation 的 `0.5%`，不是 GC thrash。

Long-run 把 tick 提高到 10,000，maximum population 约为 default 的 9.7 倍，
但只发生一次 growth high-water；总 allocation 约 47 MB，GC 为零。这说明持续
birth/death churn 没有造成随 tick 累积的临时对象失控。由于没有为测量而把
population-tick counter 加入 hot path，`bytes/tick` 只作辅助解释，总量、
population high-water 和 workload identity 仍是主要证据。

## 4. 归因裁决

本轮发现按协议完成了 Owner 排除：

1. shared application `target` 曾被 IDE 后台编译覆盖，属于 measurement
   integrity；已通过 evidence-local build 隔离；
2. scheduler large 的主要异常 allocation 来自 example callback Value Object、
   boxed resource lookup 和过度 candidate refresh，属于 example access/runtime
   acceleration；
3. 当前 scheduler large 的剩余耗时对应全局 frontier 领域算法；
4. simulation large 的时间、high-water 与 live population 同阶，GC pause 不是
   主导项；
5. 没有领域中性 component reproduction 指向 SOMA generated/runtime defect。

因此 Stage 4 不增加 core 修改，也不以拆 Table、增加索引、扩大 heap、改变排序
或缩小 workload 追逐更低数字。

## 5. 抽象与行为叙事审查

参考“问题/领域 → 行为 → 信息 → runtime → contract → application shell”的
推导方向复核两个 example：

- scheduler 仍由 `SchedulingProblem -> SchedulingSolver/Session ->
  DispatchEngine -> ScheduleResult` 展开；主循环保持 event、candidate、
  revalidation、commit 的同层因果顺序；
- simulation 仍由 `Config -> Scenario -> Simulator/Session -> Result`
  展开；`SimulationEngine` 固定 growth、death、reproduction、grassing、
  searching、trace 的系统顺序；
- input factory 与 runtime state、production 与 test/evidence、领域语义与
  primitive projection 的 Owner 继续分离；
- 新 benchmark options、artifact 和 baseline 没有进入 production journey；
- scheduler 的缓存/投影附着于 runtime/frontier lifecycle，不是第二领域事实源。

没有发现需要在本性能专题内进行目录或核心抽象重构的证据。为 LOC 或名称继续
拆分会扩大导航成本，并不能改善当前叙事。

## 6. Scope non-regression

`7252646..938b3d5` 没有修改 `soma-annotations`、`soma-runtime-core`、
`soma-processor`、正式 Blueprint/Design、Implementation Map、Conformance 或
Report。生产 Java 修改只位于 scheduler application 的 runtime/solver 内部。

下列边界均保持：

- public/generated API token 与 annotation Schema：未变；
- Access Model、Index lifecycle、ownership、swap-remove、failure atomicity：
  未变；
- scheduler comparator、event ordering、revalidation、commit 和 result
  checksum：未变；
- simulation system order、deterministic random、result checksum：未变；
- application production JAR、package DAG、external consumer 和 Java 8：
  全部通过；
- component baseline 与 public claim：仍为 `1` 和 `0`。

## 7. Gate

在 baseline 切换后运行：

- 3-fork Fast、Scale、Soak：全部通过；
- `check-reference-application-full-performance.sh`：通过；
- `check-performance-baseline-architecture.sh`：`component=1`、
  `reference-application=6`、`public-claim=0`；
- `./scripts/check.sh`：`project-check: ok`；
- `git diff --check`：通过。

Stage 4 没有触发停止条件，可以进入 Stage 5 正式 Owner 原子固化。
