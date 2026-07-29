# 实时派工规则引擎验证

类型：应用验证

状态：当前

Owner：real-time-dispatch-rule-engine

对 SOMA 产品规范性：否

事实范围：配置、正确性、架构、并行、规模和性能 evidence

最后审查日期：2026-07-30

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

三份application-owned baseline当前为default v4、large v4、long-run v5，使用
同一Amazon Corretto JDK 8/macOS/aarch64环境建立；普通Gate固定3 fork。旧Zulu
baseline只由Git保存其历史evidence含义。allocation是同步caller thread指标，
GC是进程指标，不把caller allocation冒充全部worker allocation。

| Profile | RuntimePlan | timing limit | caller allocation limit | tasks / workers |
|---|---|---:|---:|---:|
| default | `f0edf1…` | `95,301,063 ns` | `10,429,430 B` | `222 / 2` |
| large | `cf01af…` | `1,192,005,438 ns` | `100,467,110 B` | `75 / 4` |
| long-run | `f23b6e…` | `95,392,626 ns` | `34,886,160 B` | `1,513 / 4` |

三个profile的Template identity均为`674e32…`。Default/large的GC上限为零；
long-run在两组5-fork calibration与一次3-fork final confirmation的13个独立JVM中
有两个出现单次1 ms young GC，因此只按既有公式把young count/pause envelope更新
为`2/2 ms`，allocation、timing和Full GC阈值不变。当前identity replacement
commit为`a24bb4d48eec430d6188cb0588b28300a407da6d`；每个baseline登记自己的
candidate content checksum，config/input/result、Definition/Template/demand
identity保持。

Baseline 同时固定 config/input/result、Schema、RuntimePlan、Definition、Template
和 demand identity。Comparator 在精确环境/workload 下给出 `passed/failed`，
其他环境为 `not-applicable`；invalid artifact 始终失败。全部记录
`claimAllowed=false`，不构成 SLA、支持矩阵或 public claim。

冻结前profiling显示large workload的stable primitive sort约占CPU sample的66.8%；
wall profile中worker parked来自bounded executor等待，主线程block占比很低，没有
形成锁瓶颈。150K work diagnostic为10.814 s，符合stable sort主导的非线性增长。
因为stable first-on-equal、deterministic merge与command顺序属于当前语义，本轮不
以新索引、并发Table或不稳定排序做高风险改写；15K large profile随后在九profile
Full中继续通过。该结论是明确的application算法边界，不是遗漏的SOMA性能修复。

Canonical Gate：

```bash
./scripts/check-real-time-dispatch-rule-engine.sh
```
