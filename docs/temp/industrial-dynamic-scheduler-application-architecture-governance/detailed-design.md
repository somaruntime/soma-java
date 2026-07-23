# 详细设计

类型：Temporary

状态：active

Owner：industrial-dynamic-scheduler architecture detailed design

事实范围：目标类型契约、生命周期、数据转换、失败、测试和 Gate 的实施约束

非事实范围：SOMA 核心语义、当前实现结论、最终性能数据和发布授权

最后审查日期：2026-07-23

## 1. Canonical journey

CLI 是 composition root，只负责：

1. 读取 `ProblemGenerationConfig`；
2. 通过 `SchedulingProblemFactory` 创建 Problem；
3. 调用 `SchedulingSolver.solve(problem)`；
4. 独立验证 Result；
5. 输出 config/input/result checksum、统计和总耗时。

CLI 不创建或关闭 Runtime，不 import schema/generated 类型，不拥有 benchmark 参数。

Verification 和 benchmark 也必须调用相同 Solver/Session，不得复制 runtime 装配。

## 2. Solver/session lifecycle

`SomaSchedulingSolver` 无可变 per-solve 状态，可以顺序复用。`prepare()`：

1. 对 null/problem 状态 fail fast；
2. 调用 `SchedulerRuntimeFactory.create(problem)`；
3. 返回唯一拥有该 runtime 的 `SomaSchedulingSession`。

Session 状态为 `READY -> SOLVING -> CLOSED`。任何状态下失败最终进入 `CLOSED`。
`solve()`：

1. 创建一次性 `DispatchEngine`；
2. 得到 `DispatchSummary`；
3. materialize assignment schema records；
4. 捕获 `RuntimeSnapshot`；
5. 组装完整 detached Result；
6. 在 `finally` 中关闭 Runtime。

组装完成后的 Result 不再引用任何 runtime-owned object、ColumnView、IndexSnapshot、
Batch、Cursor 或 mutable schema record。Assembler 必须逐项复制。

## 3. Result model

`ScheduledOperation` 使用 primitive/stable identity 值：

```text
jobId, operationId, machineId, resourceId, setupFamilyId
setupStart, setup, transport, start, processing, end, due, priority
```

字段与当前 `OperationAssignment` 一一对应。构造时检查 identity 正数、时间非负和
基本区间关系；完整领域约束仍由 `ScheduleValidator` 检查。

`ScheduleResult.assignments()` 返回 unmodifiable detached list。Result metrics 与
assignment checksum 必须和现有语义相同。`ScheduleValidator`：

- 接收 `(SchedulingProblem, ScheduleResult)`；
- 从 Result 内部取得 assignments 和 claimed summary；
- 验证全部既有领域 invariant；
- 返回 `ValidationSummary`，但不修改 Result；
- checksum 委托给 `ScheduleChecksum`，避免 Owner 重复。

## 4. Algorithm preservation

重构不得改变以下顺序：

```text
frontier empty -> process next event
refresh all current candidates
select by setupStart/completion/priority/due/stable identity
earlier event wins over selected setupStart
revalidate operation/machine/resource versions
append assignment
mutate machine
commit resource calendar and state
mutate operation
retire operation candidates
release successor or finish job
```

`CandidateFrontier` 拥有 candidate 计算和 selected scratch；`AssignmentCommitter`
不得重新实现 comparator 或 refresh。`ExternalEventProcessor` 通过
`CandidateFrontier.releaseOperation()` 发布首工序。

跨 Table 失败继续 fail-stop；本专题不增加 transaction/rollback 语义。

## 5. Problem construction

`SyntheticSchedulingProblemFactory` 保持现有 `Random(seed)` 调用顺序，避免无意改变
workload。先机械迁移 nested input 类型，再拆 validator/index/checksum；每一步比较
四 profile input checksum。

Application-level package/schema 重命名不会影响 input checksum。若输入 record 拆分
导致 checksum 变化，必须证明字段序列或生成顺序改变；未经裁决不得接受。

Problem constructor：

1. defensive copy 所有顶层 list；
2. `SchedulingProblemValidator.validateAndIndex(...)`；
3. 计算 maximum candidates/frontier capacity；
4. 计算并冻结 checksum。

Lookup key 暂保留内部稳定编码，不进入 public contract；不在本专题引入第三方
collection 或新索引结构。

## 6. Config 与 resources

`ProblemConfigLoader` 只识别 problem-generation keys。原四份配置拆为：

```text
src/main/resources/config/default.properties
src/test/resources/config/correctness.properties
src/test/resources/config/large.properties
src/test/resources/config/long-run.properties
src/test/resources/benchmark/default.properties
```

四份 problem profile 删除 `benchmark.*`。Benchmark options 单独 strict load，
至少保留 warmup、forks、measurements；fork 数仍由 shell outer runner控制，并与
Java options 一致。

外部文件和 CLI `key=value` override 继续可用。Config checksum 只标识 problem
generation config，不再混入测量过程。

## 7. Test/evidence

迁移到 test source-set：

| 当前类型 | 目标 |
|---|---|
| `SchedulingProblemFixtures` | `fixture/SchedulingProblemFixtures` |
| `TinyScheduleOracle` | `oracle/TinyScheduleOracle` |
| `SchedulerRuntimeChecks` | `verification/SchedulerRuntimeChecks` |
| `SchedulerVerification` | `verification/SchedulerVerification` |
| `SchedulerBenchmark` | `benchmark/SchedulerBenchmark` |
| `JvmMetrics` | `benchmark/JvmMetrics` |

Main-based verification 保持 zero-dependency。Maven `test-compile` 必须真实编译 test
sources；专项脚本 classpath 为 `target/test-classes:target/classes:<runtime>`。

生产 JAR Gate 必须拒绝：

- `fixture/`、`oracle/`、`verification/`、`benchmark/` class；
- `*Fixtures`、`*Oracle`、`*RuntimeChecks`、`JvmMetrics`；
- production source 对 test/evidence package 的 import。

## 8. Architecture Gate

专项脚本新增 source-shape 检查：

- main package 必须存在 application/config/problem/solver/runtime/result/schema；
- `src/test/java` 必须存在四个 evidence 关注点；
- runtime/solver 不得 import validation、benchmark、fixture、oracle、verification；
- application 不得 import runtime/schema/generated；
- problem/config 不得 import SOMA runtime/generated；
- main source 不得包含 benchmark keys 或 evidence main；
- JAR 不得包含 evidence classes；
- CLI、verification 和 benchmark 必须通过 Solver/Session contract；
- 旧 package/type identity 不得残留。

这些规则只保护已裁决结构，不用 LOC 阈值替代设计审查。

## 9. 验证矩阵

| Slice | 最小验证 | 保留事实 |
|---|---|---|
| Result/Solver facade | child package + correctness | assignment/result checksum、one-shot |
| Problem split | 四 profile generator replay | input checksum、capacity |
| Solver split | correctness/default/long-run | algorithm/result checksum |
| Runtime split | projection/lifecycle negative | Table/child/index semantics |
| Schema rename | clean/repeat manifest/hash | table/field/access semantics |
| source-set | package + JAR inspection | evidence strength、artifact purity |
| final | reference app + full `check.sh` | product/application scope |

性能结论必须在最终代码上重新跑至少三 fork；允许测量波动，不允许 workload、
measurement boundary 或 claim policy 静默变化。

## 10. 删除与兼容

旧 application package/type 不保留 deprecated wrapper。所有调用方、脚本、文档和
checker在同一 candidate 内切换。删除前执行 `rg` 引用闭包。

这不承诺 application-level binary compatibility；SOMA core compatibility 不受影响。
