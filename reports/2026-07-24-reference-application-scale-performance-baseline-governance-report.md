# Reference Application 大规模性能基线治理报告

类型：Report / Governance

状态：正式收口

Owner：SOMA reference application scale performance baseline governance

受众：SOMA Java 维护者、reference application owner 与 Gate reviewer

适用版本：起点 `7252646`；scale candidate `1af43ac`；baseline cutover
`938b3d5`；结果审查 `8cac118`；final codegen stability `c0fa1c9`

输入事实源：六份 application baseline、9-fork calibration、Fast/Scale/Soak/Full
Gate、应用 correctness evidence 与完整项目 Gate

事实范围：目标 workload、性能归因、六份 baseline、Gate、旧 baseline
provenance、scope non-regression 与正式收口结论

非事实范围：跨环境 SLA、正式支持矩阵、public performance claim 或 G6 readiness

最后审查日期：2026-07-24

## 1. 结论

本专题完整收口，目标没有缩水：

1. 两个 reference application 各自拥有 default、large、long-run 三份
   application-integrated baseline；
2. 六个 profile 精确绑定规模、heap、artifact v3、checksum、Schema/RuntimePlan、
   hot-operation 执行次数和 `claimAllowed=false`；
3. 正式校准均为 9 个独立 JVM fork，普通比较均为 3 fork；
4. Fast、Scale、Soak 分别承担 default、large、long-run，Full 组合六个
   workload 和三层结构检查；
5. performance architecture 当前固定为
   `component=1`、`reference-application=6`、`public-claim=0`；
6. 大规模运行暴露的问题已区分 measurement、应用算法、应用 runtime
   acceleration 与 SOMA core；没有证据指向 core defect；
7. public/generated API、annotation Schema、Access Model、Index/ownership/
   lifecycle/failure 语义及两个应用的领域语义均未改变。

这是 application evidence completion 与同语义内部优化，不是 public 性能声明。
G6 继续 blocked。

## 2. Workload 与 Gate

| Application | Default / Fast | Large / Scale | Long-run / Soak |
|---|---|---|---|
| industrial scheduler | 10 jobs、1,000 operations、10 machines、3 candidates/op | 1,000 jobs、100,000 operations、100 machines、3 candidates/op | 100 jobs、10,000 operations、100 machines、3 candidates/op，增强 delay 与持续 update/remove |
| grassing simulation | 1,000 individuals × 1,000 ticks、128 × 72 | 100,000 individuals × 1,000 ticks、1280 × 720 | 10,000 individuals × 10,000 ticks、400 × 225，持续 birth/death churn |

`correctness` 继续拥有小规模 oracle、失败路径和原子性，不承担性能 baseline。
Input generation、setup/preparation 和 correctness validation 位于 measurement
窗口之外，但必须先通过；hot operation 只测 solve 或 tick systems。

Scheduler large 固定使用 512 MiB heap，其余五个 profile 固定使用 256 MiB。
Default 为 warmup 1、measurements 3；large/long-run 为 warmup 1、
measurement 1。Heap、measurement 和 profile 都属于 baseline identity。

## 3. 9-fork baseline

环境为 Azul Zulu `1.8.0_492-b09`、OpenJDK 64-Bit Server VM
`25.492-b09`、macOS `26.5.2`、aarch64、Apple M5 Pro。

校准规则：

```text
allocation = ceil(max × 1.05)
timing     = ceil(max(p50 × 1.50, p90 × 1.25))
p90        = nearest-rank
GC count   = 0 if max=0, otherwise max+1
GC pause   = 0 if max=0, otherwise ceil(max × 1.25)
deterministic high-water = all-equal
```

| Application/profile | Hot operation range / median | Timing limit | Allocation range / limit |
|---|---:|---:|---:|
| scheduler default | `30.387..32.105 / 31.159 ms` | `46.738 ms` | `16.070..16.075 / 16.878 MB` |
| scheduler large | `8.552..8.874 / 8.694 s` | `13.041 s` | `411.337..414.294 / 435.008 MB` |
| scheduler long-run | `130.108..138.278 / 133.318 ms` | `199.976 ms` | `39.731..42.415 / 44.536 MB` |
| simulation default | `145.689..165.054 / 146.905 ms` | `220.357 ms` | `11.530..11.531 / 12.108 MB` |
| simulation large | `5.913..6.080 / 5.948 s` | `8.922 s` | `426.403 / 447.723 MB` |
| simulation long-run | `3.577..3.649 / 3.604 s` | `5.406 s` | `47.352..47.358 / 49.726 MB` |

MB/ms 只用于摘要阅读；checked-in baseline 保存原始整数 bytes/nanos。

GC 校准最大值与 envelope：

| Application/profile | Young | Full |
|---|---|---|
| scheduler default | `0/0 ms` | `0/0 ms` |
| scheduler large | `5/17 ms -> 6/22 ms` | `0/0 ms` |
| scheduler long-run | `1/4 ms -> 2/5 ms` | `0/0 ms` |
| simulation default | `0/0 ms` | `0/0 ms` |
| simulation large | `15/11 ms -> 16/14 ms` | `1/28 ms -> 2/35 ms` |
| simulation long-run | `0/0 ms` | `0/0 ms` |

Simulation large 的一次 Full GC 在 9 个 fork 中稳定出现，最大 pause 只占 hot
operation 的不到 `0.5%`；没有 GC thrash 或扩大 heap 的证据。

## 4. 归因与优化

Stage 1 单 fork feasibility 首先暴露 scheduler large 约 21.61 GB 的临时对象。
归因按 Owner 顺序完成：

1. 共享 application `target` 被 IDE 后台编译覆盖，属于 measurement integrity；
   runner 改用 evidence-local application target，并检查精确 artifact identity；
2. scheduler callback Value Object、boxed resource lookup 和对未变化 candidate
   的重复 refresh 属于 example runtime/access acceleration；
3. application 内部改为 primitive projection、resource readiness cache 和
   version-aware stale refresh；
4. comparator、event ordering、revalidation、commit、tie-break 与 result
   checksum 保持不变；
5. 优化后 scheduler large allocation 降到约 411–414 MB，Full GC 为零；
6. 当前剩余耗时来自应用拥有的全局 frontier 动态排序，没有领域中性
   component reproduction 指向 SOMA generated/runtime。

Simulation large 的 timing、scratch high-water 与 maximum population 同阶增长；
long-run 在 10,000 ticks 下约 47 MB allocation 且 GC 为零。没有证据表明将
runtime state 拆 Table、增加索引或修改 SOMA core 会改善当前模型。

旧 FJSP 100k 的约 262 ms 证据使用 machine-local arg-min；当前 scheduler large
维护完整全局 frontier，领域语义不同，不能直接作为回归阈值。

## 5. Baseline Owner 与 provenance

本报告收口时的 application baselines：

```text
industrial-dynamic-scheduler:
  performance-baseline-default-zulu8-macos-aarch64-v2.json
  performance-baseline-large-zulu8-macos-aarch64-v1.json
  performance-baseline-long-run-zulu8-macos-aarch64-v1.json

grassing-individual-simulation:
  performance-baseline-default-zulu8-macos-aarch64-v2.json
  performance-baseline-large-zulu8-macos-aarch64-v1.json
  performance-baseline-long-run-zulu8-macos-aarch64-v1.json
```

两个旧 `performance-baseline-zulu8-macos-aarch64-v1.json` 在六份候选全部通过
后退役，不保留双 Owner。历史内容可从起点提交读取：

```sh
git show 7252646:soma-examples/industrial-dynamic-scheduler/src/test/resources/benchmark/performance-baseline-zulu8-macos-aarch64-v1.json
git show 7252646:soma-examples/grassing-individual-simulation/src/test/resources/benchmark/performance-baseline-zulu8-macos-aarch64-v1.json
```

三层性能基线治理报告保留建立 component + 两份初始 application baseline 的
时点事实；本报告接管当前六 profile 结果，不回写历史数字。

工业调度三份 baseline 后续由设计与性能治理重新校准并退出 current Owner。
本报告中的历史版本可从本报告收口提交读取：

```sh
git show 22d8846:soma-examples/industrial-dynamic-scheduler/src/test/resources/benchmark/performance-baseline-default-zulu8-macos-aarch64-v2.json
git show 22d8846:soma-examples/industrial-dynamic-scheduler/src/test/resources/benchmark/performance-baseline-large-zulu8-macos-aarch64-v1.json
git show 22d8846:soma-examples/industrial-dynamic-scheduler/src/test/resources/benchmark/performance-baseline-long-run-zulu8-macos-aarch64-v1.json
```

当前 baseline 必须从 Implementation Map 或当前性能摘要进入，不能由本历史报告
的时点清单反向恢复 Owner 身份。

## 6. 实施与验证

| Commit | Slice |
|---|---|
| `0a89b5e` | Temporary 协议与 Stage 0 immutable starting point |
| `87f996a` | workload、measurement、Gate 与归因详细设计 |
| `0965c31` | 大规模 workload、artifact v3、runner 与同语义优化 |
| `1af43ac` | immutable scale candidate |
| `938b3d5` | 六份 9-fork baseline 与 Owner/Gate cutover |
| `8cac118` | 增长解释、归因和 scope non-regression 审查 |
| `c0fa1c9` | 稳定 selector-less Table 的私有 exact-index stage 生成字节码 |

在 baseline 切换后通过：

- 独立 3-fork Fast、Scale、Soak；
- `check-reference-application-full-performance.sh`；
- `check-performance-baseline-architecture.sh`；
- 两个 application correctness/architecture Gate；
- 完整 `./scripts/check.sh`，结果 `project-check: ok`；
- `git diff --check`。

首次最终全量重放在 `scan-code-size -> benchmark-smoke` 的相邻 clean compile
边界暴露一次 `NoSuchMethodError`。字节码核对证明 Zulu javac 8 对
selector-less Table 私有内部类的隐式构造器选择了不一致的 synthetic access
marker；`c0fa1c9` 改为显式私有 stage 构造形状，并由 codegen admission 的完整
生成/编译断言防回归。该问题属于 build/codegen stability，不是性能阈值失败或
runtime 语义缺陷；修复后重新执行最终性能与完整 Gate。

正式 Owner、Report、current navigation、Temporary 退役和最终重放由本报告所在
收口提交共同完成。

## 7. Scope non-regression

- 未修改 `soma-annotations` 或 `soma-runtime-core`；`soma-processor` 只为
  selector-less Table 的私有 `ExactIndexStage` 增加显式构造器，并增加对应
  codegen admission；
- 未修改正式 Blueprint/Design；
- public/generated API、Schema/hash、Access Model、Index 生命周期、ownership、
  swap-remove、failure atomicity 与 compatibility v4 均保持；
- scheduler 的全局 comparator、event、revalidation、commit 与结果语义保持；
- simulation 的 system order、deterministic random、AoS correctness 与结果语义
  保持；
- input generation 与 runtime state、production 与 test/evidence 的边界保持；
- component baseline 数量仍为 1，public claim 仍为 0；
- 未引入第三方依赖、temporary public API、平行 canonical path、缩小 workload
  或弱化 Gate。

剩余差距只有既有 `CF-005` 环境覆盖边界和 `CF-006` G6 外部发布事实。本专题不
留下 active Temporary、双 baseline Owner、待迁移实现或隐式 public claim。
