# 第一轮 runtime allocation/GC 优化报告

状态：passed（诊断 evidence；不构成性能 claim）
日期：2026-07-17
唯一 Owner：`soma-benchmarks`（benchmark evidence）；实现 Owner 仍分别归属 `soma-runtime-core` 与 `soma-processor`
Capability：`V1-ROW-PIPELINE`、`V1-COLUMN-ACCESS`、`V1-CHILD-OWNERSHIP`、`V1-RUNTIME-PLAN`、`V1-PERFORMANCE-SHAPE`、`V1-EVIDENCE-TOOLING`、`V1-SCENARIO-BENCHMARK`

> 当前性说明（2026-07-20）：本文记录 packed/exact v3 切换前的第一轮 allocation 优化证据。仍适用的实现纪律由正式 Owner 负责；当前 runtime 形态和 post-cutover A/B 见 [2026-07-17 专题收口报告](../../reports/2026-07-17-packed-exact-index-runtime-redesign-report.md)。

本报告记录第一轮不改变 public/generated API 的 runtime allocation 优化及本机诊断 evidence。所有 FJSP record 固定 `claimAllowed=false`；本报告不建立绝对性能阈值，不支持吞吐、延迟、内存优势、G6 support matrix、RC 或 release readiness 声明。

## 1. 优化范围

- ColumnView 在 view 获取边界预绑定 presence/value operation，逐行 `isPresent`、`get*` 与 optional-absent 路径不再拼接诊断字符串；Column Pipeline 同样缓存 callback operation。
- generated Row Pipeline 使用 one-shot 计划缓冲：首个 stage 分配 4 槽，之后按 2 倍扩容；前序 pipeline 被消费后，后继安全复用四组 stage 数组与 `seen[]`，不再每个 stage 复制四个 `n+1` 数组。
- terminal selection 直接保存在 one-shot Rows 实例，移除 `Selection` 边界对象；无 filter 的 count/select 不创建 predicate Cursor；`seen[]` 在 plan 内复用并按实际 stage 数清零。
- root `create(RuntimePlan)` 仍执行完整 aggregate schema/runtime/access 验证；验证后为每个 child field 预绑定含 child initial-capacity override 的不可变 `TablePlan`。owned child 创建只接收该 plan，不重复执行全 Schema 验证或 `TablePlan.toBuilder()`。
- FJSP 100k runner 新增 import/solve/export/total allocated bytes、allocated bytes/op、Young/Full/Unknown GC count 与 collection time；`scripts/check-fjsp-allocation-gc.sh` 固定 JVM/GC 参数并验证字段可观测、phase total 一致、schema 与 `claimAllowed=false`。

## 2. A/B 方法

基线与优化后各在独立 JVM 中执行同一 FJSP workload；除本报告 §1 的 runtime/generator 改动外，使用相同 worktree 场景、输入与 benchmark instrumentation：

- 规模：1,000 jobs × 100 operations，100 machines，3 candidates/operation，共 100,000 operations；
- seed：`1397706049`；dispatch rule：`effective-ready,fcfs,spt,identity`；
- JVM：Azul Zulu OpenJDK `1.8.0_492-b09`，`-Xms512m -Xmx512m -Xmn96m -XX:+UseParallelGC`；
- OS：macOS `26.5.2` / Darwin `25.5.0`，`arm64` / JVM `aarch64`；
- A/B：`warmup=0`、`measurement=1`，用于 allocation/GC 方向诊断，不把 timing 作为 claim-grade 结论；
- allocation：`com.sun.management.ThreadMXBean#getThreadAllocatedBytes`，当前 benchmark 线程精确累计；FJSP runner 为单线程；
- GC：`GarbageCollectorMXBean` 累计值做 phase delta；固定 ParallelGC 下 `PS Scavenge` 归入 Young、`PS MarkSweep` 归入 Full。

## 3. A/B 结果

| 指标 | 优化前 | 优化后 | 变化 |
|---|---:|---:|---:|
| import allocated bytes | 547,179,080 | 188,288,848 | -65.59% |
| solve allocated bytes | 1,176,833,552 | 979,283,472 | -16.79% |
| export allocated bytes | 17,026,192 | 17,009,664 | -0.10% |
| total allocated bytes | 1,741,038,824 | 1,184,581,984 | **-31.96%** |
| allocated bytes/operation | 17,410.38824 | 11,845.81984 | **-31.96%** |
| Young GC count | 37 | 29 | -21.62% |
| Young GC collection time | 57 ms | 45 ms | -21.05% |
| Full GC count / time | 0 / 0 ms | 0 / 0 ms | 不变 |
| solve time（仅诊断） | 8,650.896 ms | 8,394.299 ms | -2.97% |

语义一致性：两侧均为 100,000 assignments、1,000 completed jobs、makespan `54571`、total tardiness `20509698`、checksum `-678377626428749715`，且 runtime plan hash 均为 `ccc11ac218fee7246abe25d0fba64376eb60681d8cbdf21c5a9db2e3d00e6758`。

A/B JSONL SHA-256：

- before：`6aba24614df72dfd67a8acd3df4bcb3128f3ce42ecf6b093f73a6c6da54ca816`；
- after：`bdf1150ba7153da88bddaf6ae918a5603e6cc6678eea6d960b1dd17ededa115b`。

## 4. 持续门禁

`scripts/check-fjsp-allocation-gc.sh` 使用 `warmup=1`、`measurement=1` 再次执行固定 FJSP 100k workload。2026-07-17 本机 record：

- allocated bytes/operation：`11714.93528`；total allocated bytes：`1,171,493,528`；
- Young GC：`27` 次 / `51 ms`；Full GC：`0` 次 / `0 ms`；
- solve time：`8473.684875 ms`（仅诊断）；
- artifact SHA-256：`e9c30cc3d9293680244b74fc2473c44c7e95ca4041a55690cf39de2fe8b445c4`；
- schema、scenario、operation count、allocation observability、phase sum、numeric bytes/op、GC metrics 与 `claimAllowed=false` 均通过。

最终 `./scripts/check.sh` 中的独立重复执行同样通过：allocated bytes/operation `11770.75056`、total allocated bytes `1,177,075,056`、Young GC `26` 次 / `43 ms`、Full GC `0` 次 / `0 ms`，artifact SHA-256 为 `ebab1c5f52a860099a3cbabdddcc7ee4c5fc8a0a68ef7ad609f81f7b76d6c690`。

门禁刻意不固定 allocation/GC 绝对阈值：当前正式 Gate 没有批准跨机器绝对 memory/performance hard metric。它防止 evidence 字段消失、采集失效或合计不一致；相对回归应通过同环境 A/B 与后续经过批准的统计方法裁决。

## 5. Validation record

已通过：

- `soma-runtime-core` Maven tests；
- generated dense、child ownership、examples、external consumer targeted gates；
- Column Pipeline、ColumnView、Row Pipeline allocation-shape regression fixtures；
- deep 9-stage Row Pipeline growth/语义 fixture；
- public API/golden fixture 检查（无 public/generated signature 变化）；
- `scripts/check-benchmark-smoke.sh`：schema v3、20 primary + 20 repeat、36 negative paths；
- `scripts/check-fjsp-allocation-gc.sh`；
- `git diff --check` 与 shell syntax check。

最终 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home ./scripts/check.sh` 全量通过，结尾为 `project-check: ok`；失败 0。unsupported javac lane 按既有规则因未提供 `SOMA_UNSUPPORTED_JAVAC` 而跳过，不影响当前 full JDK 8 validation baseline。

## 6. V1 scope non-regression

| 审计项 | 结果 |
|---|---|
| Capability 状态 | 上述 capability 仅做 contract-preserving internal refinement 与新增 evidence；无 dropped、optional 或无目标 deferred |
| public/schema/runtime semantics | 未改变；pipeline one-shot、error code、统计、顺序、lifecycle 与 child ownership 语义保持 |
| 唯一 Owner / Gate | 未改变；未修改正式设计 Owner 或 root Gate |
| G6 / release claim | 未改变；本机 Java 8 evidence 不外推 support matrix，仍不声明 RC/release readiness |
| 后续性质 | additive completion / internal refinement；不要求 consumer、public/generated API 或 canonical hot path migration/rewrite |

## 7. Known limitations

- 单机、单进程、单 measurement A/B 不能排除 JIT、class loading、OS 与 GC 噪声；solve timing 只作诊断。
- 当前 thread allocation counter 只覆盖 benchmark 当前线程；若未来 runner 并行化，必须升级为跨工作线程聚合后才能保持同一含义。
- `GarbageCollectorMXBean#getCollectionTime()` 是 JVM collector 累计 collection time；在本门禁固定 ParallelGC 的条件下用于暂停时间诊断，不外推到其他 collector。
- 当前门禁保证 metrics 可观测和结构一致，不等于批准了 allocation budget 或性能 SLA。
