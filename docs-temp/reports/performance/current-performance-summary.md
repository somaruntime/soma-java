# 当前性能摘要

类型：Report / 性能快照

状态：候选

Owner：SOMA Java 性能输出

受众：评估当前 runtime 形状和后续优化价值的维护者

事实范围：指定 commit、环境和方法下的 packed exact 性能诊断结果及允许解释

适用 commit：`4b6fa43`

测量日期：2026-07-17 A/B；2026-07-19 fresh Gate refresh

环境：Azul Zulu OpenJDK `1.8.0_492-b09`，macOS `26.5.2`，aarch64

方法：FJSP 100,000 operations，同机 A/B，`warmup=1`、`measurement=1`、ParallelGC，`claimAllowed=false`

输入 evidence：当前 packed exact redesign report、2026-07-19 `./scripts/check.sh` 与 fresh FJSP JSONL

最后审查日期：2026-07-19

## 1. 当前能力

Packed exact cutover 已消除 dirty selector 的 read-time全表重建/排序。当前 exact source 沿增量维护的 group links 产生候选，dynamic sort 只处理当前候选 Index；full project check 在该专题收口时通过。

## 2. 同机诊断结果

| 指标 | 旧 dirty/rebuild | 当前 packed incremental exact | 变化 |
|---|---:|---:|---:|
| total allocated bytes | 598,120,376 | 647,593,784 | +8.271% |
| allocated bytes/operation | 5,981.20376 | 6,475.93784 | +8.271% |
| Young GC count/time | 18 / 40 ms | 19 / 42 ms | +1 / +2 ms |
| Full GC count/time | 0 / 0 ms | 0 / 0 ms | 无变化 |
| solve time，仅诊断 | 8,063.565 ms | 597.573 ms | -92.589% |
| throughput，仅诊断 | 12,401.463 op/s | 167,343.513 op/s | 13.494× |

两侧结果均为 100,000 assignments、1,000 completed jobs、makespan `54571`、total tardiness `20509698`、checksum `-678377626428749715`。

## 3. Fresh Gate refresh

2026-07-19 在相同 Zulu JDK 8/macOS aarch64 基线上运行完整 `./scripts/check.sh`，结果为 `project-check: ok`。最终 FJSP 100k lane 记录：

- solve median `572.881459 ms`，throughput `174,556.1816 operations/s`，均只作诊断；
- total allocated `647,973,256 bytes`，`6,479.73256 B/op`；
- Young GC `19 / 45 ms`，Full GC `0 / 0 ms`；
- `claimAllowed=false`；
- JSONL SHA-256：`b3ae8da966aa14df1ecf4090322b41c746a96797236dbdad8e50247d550b74fd`。

Artifact 位于本机临时 `target/fjsp-allocation-gc.93ez1X/`，不是版本库长期事实源。该 refresh 与历史 A/B 数值接近，但仍不是统计 claim 或支持矩阵。

## 4. 解释

当前实现以较高的 exact-index retained/growth、snapshot 和一致性维护 allocation，换取消除主要 CPU 热点。这个 trade-off 对该 workload 的单次同机诊断明显有利，但 allocation/op 比旧 baseline 高约 8.3%。

这不是跨环境或 production claim。单次 timing 只用于诊断；结果不能外推为正式 SLA、支持矩阵或其他 workload 的普遍优势。

## 5. 已知机会与约束

后续值得独立测量：exact-source/Rows boundary wrapper、snapshot 次数、exact-index retained growth 与 steady-state lookup allocation、allocation-free single-index terminal 的 API 价值。

任何优化都必须保留 detached snapshot safety、resource preflight、collision full equality、mutation atomicity 和 public API 兼容；不能为回到旧 allocation 数字恢复 dirty rebuild 或暴露 unsafe raw Index。

直接 evidence：[Packed Index / Exact Access / IndexBuffer 收口报告](../../../reports/2026-07-17-packed-exact-index-runtime-redesign-report.md)。
