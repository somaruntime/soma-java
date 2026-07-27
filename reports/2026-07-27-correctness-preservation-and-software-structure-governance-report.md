# SOMA 正确性保持与软件结构治理报告

类型：Report / Governance

状态：当前

Owner：SOMA correctness preservation governance output

适用版本：production candidate `b4dc203`

输入事实源：正式 Blueprint/Design/Implementation Map/Conformance、production
code、generated artifacts、contract/reference/consumer evidence、Zulu JDK 8 Gate

事实范围：本专题意图、审计结论、设计裁决、实施、证据、scope non-regression 和收口

非事实范围：重新定义产品能力、跨环境性能声明、G6 或 public release readiness

最后审查日期：2026-07-27

## 1. 结论

本专题使用“抽象与叙事”和“不变量驱动正确性保持”审查 SOMA 的五条 vertical
slice。产品、模块、Access、Transformation/DataFlow 与 generated v5 主叙事没有
偏移；真实偏差是 Design 已要求 internal invariant 后 aggregate fail fast，但
runtime 缺少 root/child 共享的 trust Owner。

实现提交 `9114321` 已关闭该偏差：first internal 或 table-owned unexpected
failure 使整个 ownership aggregate 单向 faulted，normal access fail closed；
runtime plan、released state、bounded stats snapshot 和 root release 仍可尝试。
`dd17071` 随后消除健康路径重复 preflight；`b4dc203` 关闭 DataFlow component
Gate 的 javac 8 class-set 与 authoring warmup 稳定性问题。没有新增 public fault
API、Schema、generated signature 或 protocol identity。

## 2. 审计与裁决

| 项目 | 裁决 |
|---|---|
| Compiler/Codegen | normalized model、artifact plan 与 publish 主链闭合；不拆分为新 framework |
| Table Mutation | expected failure 原子性保留；internal/unexpected 后可信状态缺口已修复 |
| Access/Lifecycle | 共享 ownership registry 是 aggregate trust 的唯一 Owner |
| Transformation/DataFlow | 本地 compute failure 只终止 Invocation；Effect target failure 遵循 aggregate fault |
| Evidence/Claim | contract/property/differential/E2E/performance 分责保持，不增加洪水式测试 |
| Processor 结构 | 多 emitter 的 failure routing 收敛到 generated Table 私有 helper，不建立第二 Owner |
| Zulu javac 8 instability | 两个 component benchmark 消除不必要的 private synthetic access，完整 class-load/correctness admission 前移到 fork 前 |

CP-001–CP-003 由 aggregate trust implementation 关闭；CP-004 由有限证明链与本
Report 关闭；CP-005/CP-006 经证据裁决为保持当前测试分层和 emitter 主叙事；
CP-007 由构造器与 Gate admission 修复关闭。

## 3. 长期语义

- `ChildOwnershipRegistry` 拥有 root/child 共享 trust state，first failure wins；
- expected invalid-input/lookup/conflict/lifecycle/resource/callback failure 保持旧
  stable state 可信；
- structured `INTERNAL`、table-owned raw `RuntimeException` 和 raw `Error`
  使 aggregate faulted；fatal JVM error 仍原样传播，不承诺 JVM 可恢复；
- child fault 自动传播到 root；fault 不提供 reset、recovery 或 public query；
- faulted aggregate 拒绝 data access、mutation、child navigation、DataFlow
  acquire 和 stats reset；
- bounded diagnostics 与 root cleanup 不代表事实恢复可信；
- 只读 DataFlow compute failure 不误伤 source aggregate。

## 4. 实施

- runtime：`ChildOwnershipRegistry`、`DenseTableState`、`ColumnGroup` 和
  `StorageBudget` 在唯一事实 Owner 处标记与传播 fault；
- codegen：Table/Auxiliary/Scan/Selector/Exact emitter 使用统一生成 helper
  路由 structured、expected 与 unexpected failure；
- compatibility：helper 只调用既有 runtime v5 method，public API golden 与
  protocol identity 未变化；
- evidence：runtime invariant 与 dense/access/child consumer 集中证明
  normal rejection、expected failure、root/child propagation、diagnostics 和
  release；
- benchmark：`LongSum` 显式无参构造器消除 synthetic marker；DataFlow direct
  predicate 改为不捕获 `Workload` 的命名对象并使用直接构造器；两个 Gate 均先做
  class-load/correctness admission，再进入多 fork。

## 5. Stage 4 evidence

环境：Azul Zulu OpenJDK `1.8.0_492-b09`、Maven `3.9.16`、macOS
`26.5.2`、aarch64。

| Evidence | 结果 |
|---|---|
| processor clean compile / codegen admission | passed |
| runtime-core / generated dense / generated keyed | passed |
| access / child representative consumer | passed |
| DataFlow reference differential | passed |
| public API baseline | passed，golden 未修改 |
| post-cutover component baseline | passed，5 forks，`claimAllowed=false` |
| DataFlow component baseline | passed，3 forks，`claimAllowed=false` |
| clean/repeat benchmark class | Access 与 DataFlow 两组 outer/nested SHA 均稳定；不再依赖 private synthetic constructor |
| docs / whitespace | passed |

五 fork artifact 是本机临时 Gate evidence，不构成跨机器 SLA 或 public claim。
首次 final Gate 在 `candidate_scan.packed_zero_count` 得到 `85.3916 ns/op`，高于
`84.0` threshold。归因发现健康路径执行 operation-name 字符串比较且重复
aggregate preflight；`dd17071` 改为先判断 `faulted` 并复用 preflighted scope。
修复后的五 fork 为 `62.1916..66.425 ns/op`，median `63.625`，component baseline
重新通过；阈值和 baseline 未修改。

后续完整确认在 DataFlow component 启动时识别出增量 class set 的 synthetic
accessor 不一致。`b4dc203` 将 direct predicate 改为不捕获外部状态的命名对象，
在相邻两次 clean Zulu javac 8 编译中取得一致 class SHA，并把完整 15-lane
admission 前移到三 fork 之前。同一证据还显示 `authoring.compile` 的首 fork
p90 混入 tiered compilation；该 lane 现在记录并执行独立最小 warmup，三 fork
baseline 随后通过。fork、workload、阈值和 checked-in baseline 均未改变。

production candidate `b4dc203` 上的最终完整 `./scripts/check.sh` 已通过并输出
`project-check: ok`。其中 Access component 五 fork、DataFlow component admission
与三 fork baseline 均为 `passed`，所有 artifact 保持 `claimAllowed=false`。
本报告的最终状态更新只改变正式文档，随后由 docs Gate 与
`git diff --check` 单独确认。

## 6. Scope non-regression

- Schema-Defined、Compiler-Specialized、JVM Heap-Resident、Java 8 定位不变；
- annotation Schema、public/generated API、v5 runtime protocol identity 不变；
- packed SoA、key/unique/exact、swap-remove、Index/IndexSnapshot、Access、
  Transformation/DataFlow、parallel、ownership 与失败原子性没有缩水；
- 两个 reference application、八份 baseline、G0–G5 和现有阈值未弱化；
- normal hot path 只增加 registry 上一个 predictable trust branch，无 allocation、
  hash、lock、ThreadLocal 或 per-element check；
- 没有第三方依赖、恢复 API、临时 public surface、第二套 lifecycle 或 future
  rewrite dependency；
- G6 继续 blocked，本专题不处理 release readiness。

## 7. 正式 Owner

长期 failure 语义固化至
[Correctness 与 failure](../docs/design/correctness-and-failure.md)，aggregate
边界固化至 [Ownership 与 lifecycle](../docs/design/ownership-and-lifecycle.md)；
当前实现导航进入 Runtime/Compiler/Test/Executable/Benchmark Implementation
Map；fork admission 进入 [Benchmark 治理](../docs/engineering/benchmark-governance.md)；
偏差关闭进入 Conformance。Temporary 在这些 Owner 原子接管后删除。
