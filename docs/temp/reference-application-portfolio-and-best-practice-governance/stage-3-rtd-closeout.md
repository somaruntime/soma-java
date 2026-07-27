# Stage 3 RTD Reference Application 收口

类型：Temporary

状态：complete

Owner：SOMA reference application portfolio and best-practice governance

正式事实源：否

事实范围：Stage 3 实施事实、局部证据和进入 Stage 4 的条件

非事实范围：正式产品语义、尚未完成的 portfolio 性能证据和 release 声明

最后审查日期：2026-07-27

## 1. 结果

新增独立的 `real-time-dispatch-rule-engine` Java 8 reference application。它不是
工业调度的复制，也不是 operator demo，而是从 RTD business model 推导出的有限、
可重放 dispatch horizon：

```text
versioned Config
  -> detached Scenario Factory
  -> initial Snapshot + ordered Arrival Delta
  -> WorkState / ResourceState Runtime
  -> reusable Rule Definition / Template
  -> repeated one-shot Invocation
  -> detached DispatchCommand
  -> application-owned preflight and sequential commit
  -> detached Result + Diagnostics
```

输入生成与 live runtime 完全分离。生产资源只含 default 配置；correctness、large 和
long-run 属于 test/evidence。应用没有数据库、MES、JDBC/CDC、retry、transaction
或 distributed execution。

## 2. 抽象与责任

| 抽象 | 唯一责任 |
|---|---|
| Config | 严格解析版本、规模、并行和预算边界，拒绝未知键与非法组合 |
| Scenario Factory | 生成确定性的 detached snapshot/delta，不接触 SOMA Runtime |
| Runtime Factory | 依据 Config 建立两张 root Table，并通过 Batch 投影初始状态 |
| Runtime | 持有 live Table、cycle safe point 与 fail-stop lifecycle |
| Rule Plan | 一次构造 reusable Definition/Template，不绑定 live Table |
| Rule Executor | 每 cycle 创建 one-shot Invocation，并立即消费 current Index |
| Command Selector | 在同步只读批次中完成确定性 matching，复制 detached command |
| Committer | stable-key 全批预检后顺序提交；不宣称跨 Table transaction |
| Result / Diagnostics | 分离领域 identity 与运行诊断，均不保存 live handle |

两张 Schema 只维护有真实 consumer 的访问路径：Work 的 `by_status` 服务 pending
exact source；Resource capability 由 Join 消费，不建立重复 exact index。

## 3. DataFlow 与受控并行

主 Rule graph 由 pending exact source、release filter、ready count、capability
GroupBy、resource filter、inner Join 和稳定 total order 组成。它同时产生 ready、
demand group 和 ranked joined Index；应用在来源未 mutation 时立即转换为 detached
command，随后才进入 commit。

Join、GroupBy 和 Sort 是当前物理执行中的 barrier。应用不伪造“主 graph 已并行”
结论，而以同一 reusable plan 中的 packed admitted-load reduction 建立真实、
业务有意义的 parallel evidence。managed 与 borrowed Context 都能执行该 reduction；
borrowed executor ownership 保持在 caller。一次 horizon 的 diagnostics 中
`invocations = cycle count + admitted-load invocation`。

## 4. 构造与失败边界

- Config、Scenario、Snapshot、Delta、Command、Result 和 Diagnostics 在构造边界
  防御 cardinality、范围、顺序、identity 和 arithmetic；
- arrival 只在 cycle safe point 通过 generated Delta 写入单个 aggregate；
- command batch 在任何 authoritative mutation 前完成 duplicate、version、
  capability、status、availability 和 overflow 预检；
- 两个 root 的顺序提交由 application 负责；首个 mutation 后的 unexpected failure
  进入 fail-stop，不发布 partial Outcome；
- cancellation 与 budget 使用稳定的 DataFlow failure typing；失败后 Context 仍可按
  ownership contract 使用或关闭；
- fail-stop 会释放 live Table，最终可观察状态为 closed，不暴露短暂 fault state。

## 5. Evidence

Stage 3 只运行语义、架构和 package 窄 Gate，没有提前进入 Stage 5 多 fork性能：

- Zulu JDK 8 reactor package：通过，32 个 production source 和 5 个 test source
  重新编译；
- correctness profile：独立 Java reference 与 sequential、managed、borrowed 三条
  SOMA 路径的 Result 完全相同；
- managed parallel 确认实际 worker count 为 3；borrowed Context 不关闭 caller
  executor；
- cancellation、output budget、Context reuse、snapshot/delta、release 和
  application-owned commit representative：通过；
- production JAR 不包含 oracle/evidence/validation、test profile 或 benchmark；
- source、POM 和 JAR 均不依赖另外两个 application。

correctness 的 input checksum 为
`60baa1a9ee5dd0624d2d02f10a1a8e0f4f9d306a6a8f376fe25295e2660b241b`，
SOMA/reference Result checksum 均为
`d2fc291ab51bf66883a11aa038933d5000ab7c490b065ebe9e6e3899c65f05be`。
默认 journey 的 Result checksum 为
`837f1e23f8906ee4246b21b27be25c3912dc4813f24728bfe0ef6772a48c2a9a`。

scale、soak 和稳定多 fork 性能由 Stage 5 在 portfolio 候选稳定后执行。

## 6. Scope non-regression

- 未修改 SOMA public/generated API、annotation Schema 或核心运行时语义；
- 未借用工业调度或 grassing 的领域模型、fixture、helper 或 artifact；
- 未引入第三方依赖、数据库同步、跨 Table transaction 或通用线程安全语义；
- RTD 已建立独立的 multi-source、Join、GroupBy、parallel、budget/cancellation、
  detached command/result 和 application-owned commit evidence；
- Stage 4 可以原子删除工业调度的展示性 DataFlow，而不留下 application coverage
  空窗。
