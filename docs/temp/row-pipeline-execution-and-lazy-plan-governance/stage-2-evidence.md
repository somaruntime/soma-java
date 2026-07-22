# SOMA Access Model / Candidate Scan Stage 2 Evidence

类型：Temporary

状态：immutable Stage 2 executable candidate 已形成；正式 Owner 固化待单独授权

Owner：SOMA Java Access Model / Candidate Scan 专题治理

事实范围：Stage 2 production/public API/runtime 候选，以及 correctness、allocation、JFR、code-size 与 FJSP 多 fork 证据

非事实范围：跨机器性能声明、正式 Design、G6 或 release readiness

Stage 1 基线：`2f0d11676ae6f11ac7c0bb88590d109a6a24dc2b`

Stage 2 executable candidate：`fd82ebaa8dc4331b7cd9b3c62392d2472185f25f`

最后审查日期：2026-07-23

## 1. Evidence 边界

`fd82eba` 是本轮 API、runtime、processor、测试、场景、benchmark 与 Gate 的固定
可执行候选。本文件作为后续 docs-only evidence 提交，不改变被测字节。Stage 1 A/B 从
`2f0d116` 的 clean git archive 构建，Stage 2 从 `fd82eba` 构建；不再把未提交工作树
或单进程 timing 当作最终候选证据。

环境：Azul Zulu OpenJDK `1.8.0_492-b09`、javac 8、Maven `3.9.16`、
macOS `26.5.2` / Darwin `25.5.0`、arm64 / JVM aarch64。

## 2. 实施与审查结论

- generated API clean cutover 到 Table packed source、`*Scan`、`*Cursor`、
  `*UpdateCursor`、`*KeyTraversal`、typed `*ColumnTraversal`、scalar Index terminal、
  `@SomaIndex scanByX` 与 `@SomaUnique` point family；没有兼容 alias；
- public/generated 访问词汇统一为 `Index`、`Scan`、`Cursor`、`Traversal`；物理存储
  internal、合法 materialized row/count 与用户 Schema 名不做机械词频清零；
- Candidate Scan 使用 per-operation typed source plan、inline 3 + overflow 三数组，
  terminal 使用局部 evaluation；没有 per-stage linked node、Stream、iterator 或逐元素
  candidate object；
- packed zero-stage terminal 直接执行；exact source 直接绑定 maintained group；单 Sort
  best-one 使用 stable arg-min；
- generated/runtime compatibility 升级到 v4，Maven development version 为
  `0.2.0-SNAPSHOT`；Schema identity 与 runtime plan protocol v3 不变，plan hash 因
  compatibility 输入升级而确定性变化。

### 2.1 P1：terminal lifecycle

无显式 budget 的 `Scan` materialization terminal 与 `KeyTraversal` terminal 现在先进入
one-shot operation，再解析 plan-default budget。若 default-budget 解析或 reentrant
`Table.begin` 失败，handle 仍已消费；重复 terminal 分别返回 `pipeline_consumed` 或
`traversal_consumed`。显式 overload 的 null 参数仍在 operation 前验证，因此不误消费
handle。

Candidate mutation/read terminal 在 `begin` / mutation preflight 前消费 plan；这些边界
失败时立即清理 callback、reference selector leaf 与 Table strong reference。reentrant
default-budget、repeat terminal 与 reflection retention oracle 已进入 dense/keyed Gate。

### 2.2 P2：source-only exact count

零 stage exact `count()` 在 terminal-time 绑定 current exact group，直接读取 group
cardinality，不遍历 group Index；逻辑 stats 仍报告 `scanned=matched=cardinality`。构造
Scan 后、执行 terminal 前发生 mutation 的 oracle 证明它读取 current fact，而非缓存
旧 cardinality。

### 2.3 Handle 裁决

public generated `Scan` handle 最终只持有：

```text
typed Source plan reference + int generation
```

Table、source path 与 selector leaf 由 typed source plan 拥有；exact subtype不保存重复
Table reference。terminal `finally` 清除 plan stage/reference source。generated source
shape checker与 consumed-plan reflection oracle共同防止重复 owner或长期 retention 回归。

## 3. Correctness 与 identity

在 `fd82eba` 上执行 `./scripts/check.sh`，结尾为 `project-check: ok`。覆盖 reactor、
public `javap`、compiler/codegen、dense/keyed/access/child/breadth external consumer、
四场景、component validator、FJSP allocation/GC、code-size 与 `git diff --check`。

四份 canonical Schema hash 与 Stage 1 完全一致：

| Schema | SHA-256 |
|---|---|
| FJSP | `194193e45183a724774828595744dc14bb45365f45f23442c9586a5f3a08632d` |
| VRP | `da1e5bdd4fad6492f6502e3ec4454811934dee2d3b72d7aad5fd0559744df59d` |
| Simulation | `b617064e99e112d9a8865a8f6d0301ea4e915cd5adb2d207bd58657e5d43a616` |
| Game | `e1b47a2a18c0455b5442b2d40fda3a6cee7fc997f2c02a3b5441666bd321b50f` |

Stage 1 plan hash 为 `24c525f1082841041843cc84b1c19eecb1a7719e5ac268ab36f56ea1ce6a1962`；
Stage 2 为 `4040304b25ac865ff1e098629410a8ddd31f81a8db05fe98adad74b0c8769b06`，
各自全部 measurement 内唯一且稳定。

## 4. Component allocation 与 memory

条件：4096 elements、64 exact groups、warmup 2000、measurement 5000、current-thread
ThreadMXBean exact allocation。16 条 allocation 与 24 条 memory record 均通过 validator：

| Lane | Stage 1 B/op | Stage 2 B/op | 变化 | Gate |
|---|---:|---:|---:|---:|
| packed zero-stage count | 83.7216 | 4.4272 | -94.71% | `<=16` |
| exact zero-stage count | 112.0736 | 88.0928 | -21.40% | `<=89` |
| exact one filter count | 400.0736 | 192.0736 | -51.99% | `<=193` |
| exact three-stage count | 560.0736 | 240.0736 | -57.14% | `<=241` |
| exact five-stage overflow | 1000.0736 | 464.0736 | -53.60% | `<=465` |
| exact filter-sort scalar Index | 648.0960 snapshot baseline | 272.0736 | -58.02% | `<=273` |
| long Column traversal | 564.7280 | 44.5968 | -92.10% | `<=160` |

另有 exact zero Index `120.096`、snapshot `336.0736`、materialization `600.312`、
primary point `0.0736 B/op`。24 条 exact-index cardinality memory lane 的 retained
capacity slack 仍为 `0..63 bytes`。

Artifact：`target/post-cutover-components.rj19ih/post-cutover-components.jsonl`，
SHA-256 `5d8930823e2502dca95e69087dfc981374d06e55c44a589360e9678d8dc3473a`；
全部 record 的 commit 均为 `fd82eba`、`status=passed`、`claimAllowed=false`。

## 5. JFR allocation attribution

20,000 iteration 的 JFR profile 记录 84 个 `ObjectAllocationInNewTLAB` 与 67 个
`ObjectAllocationOutsideTLAB` sample，其中 main thread 136 个。主要可控 sample 为：

- 24 B `MachineCandidateScan` handle；
- 64 B typed exact source；
- 56 B inline storage、24 B overflow owner、32 B terminal evaluation；
- overflow arrays，以及 benchmark exact-index memory matrix 的大 primitive arrays。

main thread 只有一个 String sample，位于 Table `addBatch` 初始化，不位于 Scan、
ColumnTraversal 或 ColumnView hot traversal。除显式 materialization lane 的 schema
object外，未发现 Stream、Iterator 或非物化逐 candidate object allocation。

JFR recording 会扰动 ThreadMXBean B/op，因此本节只用于类型/栈归因，不使用该 JSON
判断 envelope；第 4 节无 JFR artifact 才是 validator-grade 定量证据。JFR：
`target/stage2-candidate-scan/component-allocation-final.jfr`，SHA-256
`b969136e247cbc8152c4f8d87dd1e5765609206a192d14f93c7f0b1fcf26a8c4`；
对应 attribution-only JSON SHA-256 为
`a86d487189bf09b85b4ca2c121f8f8ee874133abb278eaab0275b6d2196a5f13`。

## 6. Generated code size

33-table clean compile：

| 维度 | Stage 1 | Stage 2 | 增幅 | Gate |
|---|---:|---:|---:|---:|
| generated Scan source bytes | 684,384 | 785,446 | 14.77% | `<=15%` |
| generated Scan source lines | 2,980 | 3,392 | 13.83% | `<=15%` |
| Scan family class bytes | 957,266 | 1,091,692 | 14.04% | `<=15%` |
| nested class count | 231 | 239 | 3.46% | `<=15%` |

clean javac wall time 为 `7,448 ms`。source bytes 距当前 Gate 上限还有 1,595 bytes，
属于敏感但已被自动 Gate 防回归的边界，不构成本轮未完成项。

Evidence：`target/scan-code-size.FWl5xJ/scan-code-size.properties`，SHA-256
`313a55fc7e439514ef1726c7242c9873f4982e5a207cbc72078cbf8def4880eb`。
`GeneratedScanPlan.java` SHA-256 为
`52a2b7baeeb384691928d731269e604908ddcd632861c49081a0f9c4132cd14b`；
`GeneratedScanEvaluation.java` 为
`9ea2d340cfaf5aa9ca1d0b7b5b72d127a16152b27145210ba6cd5e368805e977`。

## 7. FJSP 100k 多 fork A/B

Stage 1 与 Stage 2 使用相同 seed/dataset、Zulu JDK 8 与 JVM 参数：
`-Xms512m -Xmx512m -Xmn96m -XX:+UseParallelGC`。A/B 交错运行 5 个独立 JVM；
每 fork warmup 2、measurement 3，共 15 个有效样本/版本。

| 指标 | Stage 1 | Stage 2 | 变化 |
|---|---:|---:|---:|
| solve median | 296.967 ms | 261.666 ms | -11.89% |
| solve range | 275.744–396.261 ms | 251.340–320.053 ms | — |
| solve allocation median | 3380.292 B/op | 2550.615 B/op | -24.54% |
| total allocation median | 9319.638 B/op | 8491.112 B/op | -8.89% |
| Young GC 合计 | 279 / 744 ms | 264 / 755 ms | count -5.38%，time +1.48% |
| Full GC 合计 | 5 / 247 ms | 5 / 262 ms | count相同，time +6.07% |

逐 fork solve median（Stage 1 → Stage 2）：`292.501 → 274.137`、
`296.967 → 265.301`、`326.631 → 252.232`、`281.593 → 257.435`、
`282.624 → 261.666 ms`；5/5 均更快。GC time 的小幅反向波动没有伴随 count、
allocation 或 solve 回退，按本专题 Gate 作为噪声诊断记录，不作收益声明。

30 条记录全部保持 assignments `100000`、completed jobs `1000`、makespan `33891`、
total tardiness `12234615`、checksum `-1508405863224505168`。Artifacts：

- `target/stage2-multifork/stage1/fork-{1..5}.jsonl`，按 fork 顺序拼接 SHA-256
  `1107155d806daf7796690cd54ccf2540a93790247df10c39c245d98a9e1d4230`；
- `target/stage2-multifork/stage2/fork-{1..5}.jsonl`，按 fork 顺序拼接 SHA-256
  `6fe8df1343af3df598f39b1eb562335ca111b46f1295627fd7b6822a903442bb`。

这些结果只证明当前机器、固定 workload 下无性能回退并有明确 allocation 收益；
不外推为跨机器 SLA、统计置信区间或 release claim。

## 8. Scope non-regression 与候选出口

- annotation 与 Schema semantics、packed SoA、swap-remove、exact incremental
  maintenance、child ownership、Index caller-responsibility 均未改变；
- 未新增 range/order/join/top-k/reduction、第三方 dependency、reflection、metadata
  interpreter、generic Object executor 或 Table-global/ThreadLocal mutable plan；
- P1、P2、命名、handle、correctness、allocation、code-size 与多 fork Gate 均已收口；
- release/G6 未触碰，也不声明 public release readiness；
- `fd82eba` 可以作为 immutable Stage 2 executable candidate。下一步仅是独立的 S2.7
  正式文档原子切换：固化唯一 Owner、形成正式 Governance/Performance Report，并删除
  Temporary；该动作不属于本次授权范围。
