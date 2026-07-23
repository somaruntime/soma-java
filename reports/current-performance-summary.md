# 当前性能摘要

类型：Report / 性能快照

状态：当前（只限本报告环境与 lanes）

Owner：SOMA Java 性能输出

受众：评估当前 runtime 形状和后续优化价值的维护者

适用版本：core product baseline `fd82eba`；reference-application Stage 5 cutover candidate

输入事实源：[Access Model / Candidate Scan 性能报告](2026-07-23-access-model-candidate-scan-performance-report.md)、neutral component artifact、两个 reference application canonical Gate

事实范围：当前 Candidate Scan component、三类 representative generated footprint 与两个应用的 multi-fork diagnostic

非事实范围：跨环境 SLA、正式支持矩阵、普遍性能优势或 G6

测量日期：2026-07-23

环境：Azul Zulu OpenJDK `1.8.0_492-b09`，macOS `26.5.2`，arm64/aarch64

方法：component ThreadMXBean exact allocation；三 surface clean code-size；每个应用 3 个独立 JVM fork × 3 measurements

最后审查日期：2026-07-23

## 1. 当前边界

Runtime 使用 packed `[0,size)`、keyed/dense swap-remove、eager grouped exact index、table-local `IndexBuffer` 与 compatibility v4。完整 Access Model 区分 Point、Candidate、Column、Key、Bulk 和 Ownership；只有 Candidate Scan 使用 lazy one-shot stage plan。

`soma-benchmarks` 只拥有领域中性的 component evidence；真实应用的 generation/bootstrap/correctness 与 integrated measurement 分别由两个 child project 拥有。全部 artifact 为 `claimAllowed=false`，G6 仍 blocked。

## 2. Neutral component

本轮重新执行得到 16 条 allocation 与 24 条 exact-index memory record，全部通过 strict validator。代表值：

| Lane | allocated B/op |
|---|---:|
| packed zero-stage count | 4.4272 |
| exact zero-stage count | 88.0928 |
| exact one-filter count | 192.0736 |
| exact three-stage count | 240.0736 |
| exact five-stage overflow count | 464.0736 |
| exact filter-sort scalar Index | 272.0736 |
| long ColumnTraversal | 44.5968 |

Cardinality-aware exact-index primitive payload 相对 right-sized estimate 的 retained slack 为 `0..63 bytes`。Snapshot、materialization、plan/handle 和 table-retained scratch 分开计量。

## 3. Application integrated lanes

### 3.1 Industrial dynamic scheduler

Default profile 为 192 operations；三个 fork 的 input/result/schema/runtime-plan identity 完全一致。每个 fork执行 1 次 warmup + 3 次 measurement：

- `solveNanos` 总计范围 `30,269,375..30,632,291`；
- allocated bytes 范围 `8,089,768..8,089,848`，约 `14,045 B/scheduled operation`；
- Young/Full GC count 与 pause 均为 `0`；
- exact-index、update scratch、operation scratch high-water 分别为 `3,015`、`7,560`、`368 bytes`。

Correctness、large 与 long-run profile 另行证明 6/8,000/10,000 operations、完整约束 validator、failure/lifecycle 和持续 frontier churn；这些不被混入 default timing。

### 3.2 Grassing individual simulation

Default profile 为 800 initial individuals、500 ticks，观测到 maximum population 1,139；三个 fork 的全部 identity/checksum 一致。每个 fork执行 1 次 warmup + 3 次 measurement：

- `tickNanos` 总计范围 `64,023,791..64,369,041`；
- allocated bytes 均为 `7,425,824`，约 `4,951 B/measured tick`；
- Young/Full GC count 与 pause均为 `0`；
- exact-index、update scratch、operation scratch high-water 分别为 `64,333`、`27,336`、`12,776 bytes`；
- population table growth count 为 `1`。

Correctness 使用逐 tick AoS 位级 oracle；large/long-run 分别覆盖 30,000 individuals × 300 ticks 与 5,000 individuals × 2,000 ticks。性能 lane 不替代这些 correctness guard。

## 4. Generated footprint

首个两应用切换候选采用 per-surface fixed candidate + 15% ceiling：

| Surface | Scan count | Scan source bytes | source lines | Scan family class bytes | nested classes |
|---|---:|---:|---:|---:|---:|
| neutral benchmark | 6 | 144,720 | 644 | 203,536 | 45 |
| industrial scheduler | 12 | 296,763 | 1,279 | 425,166 | 90 |
| grassing simulation | 2 | 47,835 | 213 | 68,276 | 15 |

Checker 同时生成 surface、逐 Scan 与逐 schema footprint，并要求三层汇总闭合。该 Gate 防止同一候选的生成规模无意膨胀；它不是长期容量承诺，也不能证明某个 feature 的单独因果。

## 5. 解释边界

旧 FJSP 100k A/B 仍是当时实现的历史证据，不再是 current integrated lane。当前应用数据来自本机短时 diagnostic，不能外推到其他机器、workload、production SLA、正式支持矩阵或 public release claim。
