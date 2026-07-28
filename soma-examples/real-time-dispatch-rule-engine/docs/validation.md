# 实时派工规则引擎验证

类型：应用验证

状态：当前

Owner：real-time-dispatch-rule-engine

对 SOMA 产品规范性：否

事实范围：配置、正确性、架构、并行、规模和性能 evidence

最后审查日期：2026-07-28

## 配置责任

| Config | 规模 | 责任 |
|---|---:|---|
| correctness | 48 work、8 resources、6 cycles | reference differential、失败和资源 ownership |
| default | 1,024 work、32 resources、24 cycles | production CLI、Fast multi-fork |
| large | 15,000 work、128 resources、20 cycles | 大 Join/group、Scale multi-fork |
| long-run | 13,000 work、64 resources、500 cycles | 重复 Invocation/Delta/commit、Soak multi-fork |

production resources 只包含 default。problem profile 与
`benchmark.warmup/forks/measurements` 分开保存；相同 config/seed 重复生成必须
得到相同 scenario checksum。

## Correctness 与 architecture

专项 Gate 验证：

- plain-Java `ReferenceDispatcher` 不依赖 SOMA，逐命令比较 Result；
- sequential、managed 和 borrowed context 的 Result、Definition、Template 与
  demand identity 一致，managed 实际使用多个 worker；
- borrowed executor 不被 SOMA 关闭，managed Context 可确定关闭；
- cancellation 和 output budget 使用稳定 typed failure；
- keyed Delta、current Index 消费、全批次 commit preflight、fault/release 与
  detached Result 边界；
- config/feed/support/result 与 runtime 解耦，production 不依赖 test evidence；
- isolated repository、ordinary runtime graph、production JAR purity、Java 8
  classfile、generated/schema clean-repeat reproducibility；
- 不依赖 industrial/grassing application，不包含 JDBC/MES/transaction 能力。
- 两槽SomaGroup在create后报告2个active member/Table，release后全部资源归零；
  partial-create与fail-stop cleanup不遗留单独root。

## 性能 evidence

三份application-owned baseline当前均为v2，使用同一Zulu JDK 8/macOS/aarch64
环境的5-fork校准建立；普通Gate固定3 fork。allocation是同步caller thread指标，
GC是进程指标，不把caller allocation冒充全部worker allocation。

| Profile | RuntimePlan | timing limit | caller allocation limit | tasks / workers |
|---|---|---:|---:|---:|
| default | `acf26a…` | `92,416,062 ns` | `10,430,910 B` | `222 / 2` |
| large | `826938…` | `1,146,275,750 ns` | `100,465,780 B` | `75 / 4` |
| long-run | `9e1a8c…` | `91,788,876 ns` | `34,894,600 B` | `1,513 / 4` |

三个profile的Template identity均保持`d3a285…`，GC上限均为零。校准source为
`content-sha256:1ce64235908394ff8c12e99d678aa55f9d356b109d5c025335113516bc060eef`；
config/input/result、Schema、Definition/Template/demand identity保持，
RuntimePlan/Group protocol按真实变化重新登记。

Baseline 同时固定 config/input/result、Schema、RuntimePlan、Definition、Template
和 demand identity。Comparator 在精确环境/workload 下给出 `passed/failed`，
其他环境为 `not-applicable`；invalid artifact 始终失败。全部记录
`claimAllowed=false`，不构成 SLA、支持矩阵或 public claim。

Canonical Gate：

```bash
./scripts/check-real-time-dispatch-rule-engine.sh
```
