# 工业动态调度参考应用架构治理报告

类型：Report / Governance

状态：当前（治理已完成）

Owner：industrial-dynamic-scheduler application architecture governance output

受众：项目 Owner、SOMA 维护者、参考应用维护者与 Gate reviewer

适用版本：治理前基线 `048b225`；实现候选 `69e5dc6`；正式文档切换 `6ae9eb6`

输入事实源：应用 Blueprint/Design、production/test source、isolated consumer artifact、四 profile、multi-fork evidence、Implementation Map、Conformance、Git diff 与完整 Gate

事实范围：工业动态调度参考应用的软件架构、source-set、canonical journey、验证结果和 scope non-regression

非事实范围：重新定义 SOMA public/generated API、annotation Schema 语义、Access Model、runtime 语义、跨环境性能声明、G6 或 release readiness

治理日期：2026-07-23

审查环境：Azul Zulu OpenJDK `1.8.0_492-b09`，Maven Wrapper `3.9.16`，macOS `26.5.2`，aarch64

最后审查日期：2026-07-23

## 1. 当前结论

本专题已经正式收口。`industrial-dynamic-scheduler` 不再是由单一 runner、
bootstrap 和若干 evidence helper 直接拼装的示例，而是具有明确应用边界的普通
Java 8 reference consumer：

```text
versioned problem config
  -> SchedulingProblemFactory
  -> immutable SchedulingProblem
  -> SchedulingSolver
  -> detached ScheduleResult
```

应用的长期目标和设计分别由[应用 Blueprint](../soma-examples/industrial-dynamic-scheduler/docs/blueprint.md)
与[应用 Design](../soma-examples/industrial-dynamic-scheduler/docs/design.md)拥有；
当前实现由代码拥有，并由
[Implementation Map](../docs/implementation-map/scenario-and-benchmark-map.md)
导航。本报告只记录治理结论与时点 evidence。

## 2. 架构结果

生产代码已经按共同变化原因形成以下责任：

- `application`：唯一 composition root；
- `config`：problem-generation 配置读取、严格 key 校验与 canonical text；
- `problem`：top-level Spec、预检、lookup、input checksum 与 Factory；
- `solver`：canonical facade、one-shot session、dispatch 状态机和 result assembly；
- `runtime`：Table aggregate、Problem projection、projection verification、event
  queue 与 resource calendar；
- `schema`：application-owned annotation schema；
- `result`：detached immutable result、checksum 与领域 validator；
- `support`：无状态、领域中性的应用内部稳定哈希辅助。

原 `IndustrialScheduler` 已拆为 engine、event processor、candidate frontier 和
assignment committer；原 runtime bootstrap 已拆为 factory、projector 和 verifier；
`SchedulingProblem` 把校验、索引和 checksum 委托给单一责任类型。应用入口不再了解
SOMA runtime，runtime 也不反向依赖 application、config、solver、result 或 evidence。

`ScheduleResult` 在 runtime 关闭前完成复制，关闭后仍可独立校验和消费，不保存
ColumnView、Index、IndexSnapshot、Cursor、Batch 或 mutable schema record。
需要区分 preparation 与 solve measurement 时使用 `SchedulingSession`；普通使用者
只需调用 `SchedulingSolver.solve(problem)`。

## 3. 数据生成、运行时与 evidence 边界

问题生成与运行时状态已经彻底分离：

- production 资源只保留 `config/default.properties`；
- correctness、large、long-run problem profile 位于 test resources；
- benchmark fork/measurement 参数独立位于 test benchmark config；
- synthetic factory 只生成 detached input，不持有 SOMA runtime；
- fixture、oracle、verification、benchmark、JVM metrics 和 runtime test access
  全部位于 `src/test`。

production JAR 不包含上述 evidence implementation。应用 package DAG、JAR purity
和 retired identity 均由
[`check-industrial-scheduler.sh`](../scripts/check-industrial-scheduler.sh)
fail-closed 验证。

应用自己的 schema package 从含混的 `state` 明确为 `schema`，schema identity
同步更新并完成 clean/repeat reproducibility 验证。Table、字段、primary/unique/exact、
owned-child、cardinality 与访问语义没有变化；这不是 SOMA annotation Schema
语义或 core generated API 的变更。

## 4. 能力与 evidence 非回归

下列能力全部保留：

- flexible machine、precedence、release/material readiness；
- sequence-dependent setup、maintenance、transport、secondary resource；
- due/priority/tardiness、application-owned event queue、incremental frontier；
- total-order selection、version revalidation、显式跨 Table commit 与 successor
  release；
- stable identity、primary/unique/exact/owned-child、ColumnView、Batch、
  Candidate filter/sort/update/remove 与最终 materialization；
- deterministic config/input/result checksum、手算 oracle、独立 validator、
  lifecycle/IndexSnapshot 负路径和物理顺序独立性。

correctness、default、large、long-run 四个 workload 均通过；long-run 保持
10,000 operations。default workload 的三个独立 JVM fork 保持相同 input/result、
schema 与 runtime-plan identity，并通过 allocation、GC 和 runtime high-water
检查。所有本机 artifact 继续声明 `claimAllowed=false`。

## 5. 验证

实现候选和正式文档切换先后通过：

- `./scripts/check-industrial-scheduler.sh`；
- `./scripts/check-reference-applications.sh`；
- `./scripts/check-docs.sh`；
- `git diff --check`；
- `./scripts/check.sh`，最终输出 `project-check: ok`。

验证使用项目唯一 authority——Azul Zulu full JDK 8。没有增加其他 JDK
distribution 的验真或支持声明，也没有引入第三方依赖。

## 6. Scope non-regression

从治理前基线 `048b225` 到正式候选的路径审计表明：

- 没有修改 `soma-annotations`、`soma-processor`、`soma-runtime-core`、
  `soma-testkit` 或 `soma-benchmarks` production source；
- SOMA public/generated API、annotation Schema 语义、Access Model、ownership、
  Index 生命周期、失败原子性和 runtime protocol 均未改变；
- 没有删除领域能力、应用场景目标或降低 Gate；
- 没有把 test-only helper、DTO graph、Java Stream、metadata interpreter 或
  runtime shortcut 引入 hot path；
- 变更只涉及该参考应用、对应专项 Gate，以及必要的正式导航、Conformance 和 Report。

因此，本专题是 application architecture refinement，不是 SOMA 产品设计变更，也
不依赖未来重写才成立。

## 7. 正式收口

正式 Blueprint、Design、Validation、Implementation Map 与 Conformance 已接管全部
长期事实；旧 identity 的 current 引用闭包已完成，剩余字符串只属于 checker 的
禁止恢复断言或 test-only fixture/oracle 的合法名称。专题 Temporary 已删除，
`docs/README.md` 恢复无 active topic 状态。

本专题范围内没有未裁决尾项、平行 Owner 或已知架构坏味道。G6 继续 `blocked`；
发布事实和 release readiness 不属于本专题，也未被本报告扩大。
