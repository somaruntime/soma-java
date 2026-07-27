# Stage 2 Grassing 一致性治理收口

类型：Temporary

状态：complete

Owner：SOMA reference application portfolio and best-practice governance

正式事实源：否

事实范围：Stage 2 实施事实、局部证据和进入 Stage 3 的条件

非事实范围：正式产品语义、尚未完成的 portfolio evidence 和 release 声明

最后审查日期：2026-07-27

## 1. 结果

`grassing-individual-simulation` 继续以 direct Access/Transformation 为自然业务
路径，没有为了 portfolio coverage 引入 reusable DataFlow。既有 Config、Scenario、
Runtime、tick system、Result、workload 和 checksum contract 均保持不变。

本 Stage 关闭了四项经 Stage 1 证实的问题：

| 裁决 | 实施结果 |
|---|---|
| Session fail-stop | 独立 lifecycle Owner 同时处理 `RuntimeException` 与 unexpected `Error`；cleanup 不覆盖 primary failure |
| projection defense | 全量逐值复核从 production Factory 移至 test-only boundary evidence |
| Result projection | accumulator 改为 operation-local；energy 与 checksum 在一次 stable-ID traversal 中推导 |
| dependency narrative | 保留生成 companion 所需 `soma-dataflow`，删除“迁移中”含混措辞 |

`SimulationDiagnostics` 仍是现有 detached Result contract 的组成部分；本专题没有
借治理之名进行不兼容拆分。application-owned primitive world、cell scratch、
staged birth/death、Batch、exact group 和 swap-remove 均保持原设计。

## 2. 不变量闭包

```text
Scenario Factory
  -> detached Scenario
  -> Runtime Factory + Batch projection
  -> one-shot Session lifecycle
  -> fixed-order tick systems
  -> detached Result
  -> runtime release
```

- Session 的 active/finished/closed 判定由一个 lifecycle Owner 负责；
- 成功 `finish()` 按“构造 detached Result -> 关闭 runtime -> 发布 Result”执行；
- 任一执行失败进入 fail-stop，关闭后拒绝 normal runtime access；
- production 不再以第二次全量读取重复证明 generated Batch/lookup contract；
- test boundary 保留逐值 projection evidence；
- Result assembler 不保存跨调用的 hash 或 aggregate mutable state。

## 3. Evidence

Stage 2 使用语义与架构窄 Gate，未提前运行 Stage 5 的多 fork 性能：

- Zulu JDK 8 reactor package：通过，production/test source 均重新编译；
- correctness profile（`-ea`）：SOMA Result 与独立 AoS oracle checksum 相同；
- lifecycle representative：ordinary runtime failure 与 unexpected `Error` 均保留
  primary failure、释放 runtime，并允许幂等 cleanup；
- production package DAG、retired package、test leakage 和 production JAR 扫描：
  通过；
- production JAR 包含当前 generated `GrasserStateTable`，不包含 test/evidence、
  retired identity 或旧 projection verifier。

既有 checksum 仍为
`3bf43d0c79094aefbc4d30e168508402f2b7e38eca36c74d467020db2994df67`。
默认、large、long-run 的三 fork 性能与 scale/soak 证据由 Stage 5 在稳定 portfolio
候选上统一执行。

## 4. Scope non-regression

- 未修改 SOMA public/generated API、Schema 或运行时语义；
- 未修改 simulation domain behavior、Result contract、canonical journey；
- 未缩小任何 workload、阈值或 Gate；
- 未增加依赖，也未引入跨 Table transaction；
- Stage 3 可以独立实现 RTD application，不依赖未来修复 grassing 才成立。
