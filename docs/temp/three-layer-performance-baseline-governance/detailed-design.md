# Stage 1 三层性能基线详细设计

类型：Temporary

状态：active

Owner：SOMA three-layer performance baseline detailed design

事实范围：baseline evidence 架构、artifact contract、环境适用性、比较/校准/更新规则、实现切片与验收

非事实范围：尚未校准的具体阈值、public performance claim、产品/runtime 语义和跨环境支持声明

最后审查日期：2026-07-24

## 1. 设计结论

采用“**领域中性协议工具 + 各 lane 自有 baseline**”：

```text
component runner ───────────────┐
scheduler-owned runner ─────────┼─> versioned JSONL measurement
simulation-owned runner ────────┘
                                      |
                                      v
                         neutral baseline comparator
                                      |
                 +--------------------+--------------------+
                 |                    |                    |
          component baseline   scheduler baseline   simulation baseline
          soma-benchmarks owns child test owns      child test owns
```

`soma-benchmarks` 可以拥有无领域知识的 parser/comparator 和 component baseline；
不得导入 application domain，也不拥有两个应用的 workload、identity 或阈值。两个
child POM 不新增 `soma-benchmarks` 依赖；专项脚本只把 comparator 当工程工具运行。

应用各自保留小型 environment projection，是 ordinary consumer isolation 的有意
重复，不建立领域共享 JAR，也不让一个应用的 evidence 依赖另一个应用。

## 2. Measurement artifact v2

三条 lane 的 record 保持 JSONL，并统一以下 environment/provenance 字段：

```text
artifact / schemaVersion
commit
fork / configuredForks
javaVersion / javaVendor / javaVmName / javaVmVersion
jvmArgs
osName / osVersion / architecture / cpu / maxHeapBytes
claimAllowed=false
```

- `commit` 只用于 provenance，不要求等于 calibration commit；
- `cpu` 由只输出 CPU/Chip 型号的跨平台脚本提供，不采集 serial、UUID 或设备标识；
- fork 参数通过环境变量传入，不能污染 `jvmArgs`；
- component artifact 因增加 fork/environment contract 升级 schema/artifact version；
- 两个 application artifact 也升级版本，不保留含义重复的旧字段 alias；
- runner/validator schema 属于 evidence compatibility surface，修改必须有 strict
  parser 和 negative paths。

Component 每个 fork 仍产生 16 allocation + 24 memory records；aggregate validator
验证每个 fork 的完整覆盖、顺序、唯一性和跨 fork environment/workload 一致。两个
应用每 fork 产生一个 aggregate record，并继续验证 result/schema/runtime-plan
identity 一致。

## 3. Baseline definition v1

Baseline 是 checked-in JSON，不是 measurement 的原样拷贝，最小结构为：

```text
schemaVersion = soma-performance-baseline-v1
baselineId / layer / subject / artifact
calibration = commit + date + forks + formula
environment = exact field/value map
minimumForks
identity = exact field/value map
recordShapes[] = selector + exact root fields
metrics[] = selector + field path + aggregation + comparison + limit/value
claimAllowed = false
```

`selector` 是字段等值集合；空 selector 表示全部 records。`field` 支持
`gcStats.youngCount` 这类 object path。准入 aggregation：

- `all-equal`：全部样本相等，然后与 baseline value 比较；
- `maximum`：取所有适用样本最大值；
- `median`：排序后取中位数；偶数样本取中间两项平均；
- `sum` 不准入 v1，避免把 fork 数变化误当回归。

准入 comparison：

- `equal`：deterministic identity/metric 精确相等；
- `at-most`：allocation、GC、high-water 或 timing 不超过 limit。

每个 measurement record 必须且只能匹配一个 `recordShape`；缺少字段、额外字段或
shape 歧义均失败。未知 schema、selector、aggregation、comparison、重复 rule、
无匹配样本、非有限数、少于 minimum forks 或 `claimAllowed != false` 必须
fail closed。

Baseline 所有权位置：

- component：`soma-benchmarks/src/main/resources/META-INF/soma/performance-baselines/`；
- scheduler：child `src/test/resources/benchmark/`；
- simulation：child `src/test/resources/benchmark/`。

它们不得进入两个应用的 production JAR。

## 4. 环境适用性

Comparator 先做 artifact/schema/identity 验证，再比较 baseline environment：

| 结果 | 条件 | 进程结果 | 可表述结论 |
|---|---|---:|---|
| `passed` | environment 完全适用且全部 rule 通过 | 0 | 该环境/该 workload 的 baseline Gate 通过 |
| `failed` | contract、identity 或适用环境中的 metric 失败 | 非 0 | 证据无效或存在性能回归 |
| `not-applicable` | artifact 合法，但任一环境字段不匹配 | 0 | 本次只完成 measurement/validation，未验真该 baseline |

环境不匹配不能早于结构和 claim 检查，否则坏 artifact 可被伪装成
`not-applicable`。Comparator result 自身也是 versioned JSON，必须列出 mismatch
或 metric summary，并继续声明 `claimAllowed=false`。

当前只校准一份本机 Zulu JDK 8 / macOS / aarch64 baseline。其他 Zulu JDK 8 或 OS
可以运行完整功能 Gate，但该性能 baseline 返回 `not-applicable`；这不新增支持矩阵，
也不要求 Corretto 或其他 JDK。

## 5. 指标规则

### 5.1 Component

- 所有 16 allocation 和 24 memory records 继续由 artifact validator 做结构、
  cardinality、checksum 与估算关系验证；
- 7 条已有 representative allocation envelope 从 Java hardcode 迁移到 baseline，
  保持原最大值，不借治理放宽；
- 同 7 条 representative lane 增加 `nanosPerOperation` 的 multi-fork median Gate；
- 24 条 memory lane 的 `retainedBytes` 使用 `all-equal + equal`；artifact
  validator 继续验证 `overRetainedBytesEstimate`、`storageHighWaterBytes` 与
  estimator 的派生关系，避免在 baseline 重复保存可推导事实；
- allocation lane 的 Young/Full/Unknown GC count 使用 `maximum + at-most(0)`。

普通 Gate 使用 5 个独立 JVM fork；校准使用 9 fork。Smoke lane 不进入性能阈值。

### 5.2 Industrial scheduler

Baseline 绑定 default profile、192 operations、warmup/measurement、input/result/
schema/runtime-plan identity：

- `allocatedBytes`：`maximum + at-most`；
- `exact/update/operation scratch high-water`：`all-equal + equal`；
- Young/Full GC count/pause：`maximum + at-most(0)`；
- `solveNanos`：`median + at-most`；
- `preparationNanos` 继续报告但不作 hot-operation 回归判定。

### 5.3 Grassing simulation

Baseline 绑定 default profile、initial population、ticks、maximum population、
growth count、input/result/schema/runtime-plan identity：

- `allocatedBytes`：`maximum + at-most`；
- three scratch high-water、maximum population、growth count：
  `all-equal + equal`；
- Young/Full GC count/pause：`maximum + at-most(0)`；
- `tickNanos`：`median + at-most`；
- `setupNanos` 继续报告但不作 hot-operation 回归判定。

两个应用普通 Gate 保持 3 independent forks；校准使用 9 fork。Correctness、
large、long-run 仍由原专项 lane 验证，不能为了 baseline 重复计时或缩小。

## 6. 校准规则

校准必须在 clean、固定 heap、无并行 benchmark 的同一环境执行 9 个独立 JVM fork。
候选阈值：

- component 7 条 allocation：保持治理前已通过的机械 envelope；
- application allocation：`ceil(calibration maximum × 1.05)`；
- deterministic/high-water：使用 9 fork 全等值；不全等则先调查，不能生成 baseline；
- GC：9 fork 都为 0 才准入 0；
- timing：
  `ceil(max(p50 × 1.50, p90 × 1.25))`，其中 p90 使用 nearest-rank；
- timing 样本、p50、p90、maximum、公式和最终 limit 进入 Governance Report。

该公式优先减少本机短 Gate 的假阳性；它只捕获明显回归，不构成性能目标或 public
claim。校准异常、热降频或后台负载明显时必须重跑，不通过放宽公式掩盖。

## 7. 更新纪律

- 普通 Gate 和 comparator 永远只读 checked-in baseline；
- 不提供 `--update-in-place`；
- rebaseline 先在 `target/` 产生候选 measurement/result，再由独立 Git diff 修改
  baseline；
- baseline diff 必须说明触发原因、旧/新 identity、校准环境、9 fork 统计、
  correctness Gate 和是否改变 claim；
- environment 变化新增 baseline，不覆盖原环境事实；失去可维护价值时按引用闭包
  删除；
- workload/identity 变化导致 `failed`，不能降级为 `not-applicable`；
- public claim 需要独立授权、矩阵和 Report，任何 local baseline 都不能自动晋升。

## 8. 实施切片

1. **Comparator slice**：实现 baseline parser/comparator/result、negative paths 和
   CPU identity helper，不改变任何 runner；
2. **Component slice**：升级 artifact v2、多 fork validator，迁移 allocation
   envelope，建立 component baseline 与专项 Gate；
3. **Application slice**：分别升级两个 artifact、增加 environment/commit/fork，
   建立 child-owned baseline 并接入各自专项 Gate；
4. **Calibration candidate**：9 fork 校准、异常复核、全部 rule/负路径和综合 Gate；
5. **Formal cutover**：只在 immutable candidate 通过后原子更新正式 Owner。

每个 slice 的 Gate 必须在同一 commit 内保持不弱于起点；特别是移除 hardcoded
envelope 必须和等价 baseline Gate 同时发生。

## 9. 风险与验收

主要风险及控制：

- flaky timing：独立 fork + median + 宽但明确的 calibration formula；
- 假环境匹配：完整 environment map 精确匹配；
- 两应用耦合：共享 comparator protocol，不共享 domain baseline 或 POM dependency；
- baseline 自我批准：normal Gate 无写能力；
- evidence 膨胀：一个 generic comparator、一套 schema、三个 Owner 文件，不建立
  新 Maven module；
- claim 越界：baseline、measurement、result 三处都要求
  `claimAllowed=false`。

Stage 1 的裁决不修改 Blueprint、Design 或 runtime 语义，符合专题授权。后续若发现
必须改变产品/领域语义或引入依赖，立即停止。
