# 当前性能与规模摘要

类型：Report / Performance / Qualification Snapshot

状态：三个 reference application profiling 与规模优化已闭合；final same-SHA G5
artifact resolves

Owner：SOMA Java 性能与规模 evidence

受众：SOMA maintainer、runtime/compiler/DataFlow 开发者和产品决策者

适用版本：`soma-java` `1.0.0`

输入事实源：[V1 release governance](java-v1-release-governance-report.md)、
component/application baseline、runtime-scale qualification、固定 workload 的
CPU/allocation/GC profiling 与 correctness checksum

事实范围：当前 Corretto production shape 的 component、三个 reference
application、Small/Medium/单1M/双1M/String/Expansion/Delivery/Soak evidence，
以及本轮100K/1M application diagnostic

非事实范围：跨环境 SLA、任意 Schema/String row-count 承诺、production
telemetry、public release、Maven Central readiness或任意业务模型性能保证

最后审查日期：2026-07-30

测量环境：Amazon Corretto `1.8.0_502-b07`、Maven `3.9.16`、macOS
`26.5.2` / Darwin `25.5.0`、`aarch64`、Apple M5 Pro。物理机为48 GiB，
本专题限制为最多16核、32 GiB总资源；正式与diagnostic JVM均保持在该上限内，
required runtime-scale lane最大`-Xmx6g`且串行执行。

## 1. 当前结论

本轮没有把“小 workload 跑得快”当作规模结论，而是先对三个真实 reference
application分别采集CPU、allocation、GC和thread evidence，再以1M或10倍工作量
检验热点是否随规模放大。结果形成三个不同裁决：

1. Grassing的主要浪费来自generated update scratch按每次新cardinality精确扩容，
   动态population使五组primitive array被反复复制。Processor现改为受
   `maximumUpdateScratchBytes`与Group ledger共同约束的有界几何增长；preferred
   capacity不被Plan接纳时自动退回exact required capacity，保持fail closed。
2. Industrial的主要浪费来自同一machine candidate group内重复读取不变的
   version/family/availability以及重复lifecycle guard。Example现按group边界只读
   一次authoritative SOMA事实，并把已读取snapshot传入refresh；没有缓存跨mutation
   的current Index或绕过SOMA owner。
3. RTD的主要CPU成本是稳定顺序join所需的primitive sort barrier。Worker大部分
   时间parked是bounded executor等待，不是锁争用；在没有新语义或稳定profile证明
   前，不以不稳定排序、额外索引或并发Table改写换取局部数字。

优化保持Schema、输入、runtime plan、业务结果checksum与failure/resource语义不变。
没有新增public/generated API、module、production dependency或parallel Owner。

## 2. Profiling 与优化结果

下表是同一Corretto/macOS/aarch64环境中的固定workload diagnostic。它用于解释
因果，不是checked-in baseline或公开性能声明；百分比由单次before/after测量计算，
正式回归仍以多fork baseline为准。

| Application / workload | 优化前 | 优化后 | 直接结论 |
|---|---:|---:|---|
| Grassing 100K initial × 1,000 ticks allocation | 426.45 MB | 61.14 MB | 下降约85.7%；generated scratch allocation sample从275降到1 |
| Grassing 1M initial × 100 ticks allocation | 2.229 GB | 308.18 MB | 下降约86.2%；Young GC 3→0，Full GC保持0 |
| Grassing 1M tick time | 15.260 s | 15.521 s | +1.7%，属于单fork诊断波动；没有用放宽timing Gate换取allocation结果 |
| Grassing 100K update scratch high-water | 3,799,632 B | 5,400,048 B | retained增加约42.1%，但有Plan hard limit、ledger与exact fallback |
| Industrial 100K operations solve | 2.329 s | 2.122 s | 下降约8.9%；candidate refresh CPU sample占比22.56%→9.63% |
| Industrial 1M operations / fixed 100 machines solve | 196.020 s | 135.892 s | 下降约30.7%；Full GC保持0，业务checksum不变 |
| RTD 15K work dispatch | 约0.779 s | 未改实现 | stable long sort约占CPU sample的66.8%；没有发现值得修复的锁瓶颈 |
| RTD 150K work diagnostic | 10.814 s | 未改实现 | 符合stable sort主导的非线性增长；作为应用算法边界保留 |

Grassing几何增长把动态数组copy从“每个新size一次”降为摊销增长。它有意用少量
bounded retained bytes换取显著更低的transient allocation/GC；这不是silent
unbounded growth。External Maven dense consumer覆盖4→5几何增长、继续消费retained
tail以及Plan只容纳exact capacity时的fallback。实现过程中一次draft曾只更新
capacity accounting而仍按required复制，真实Grassing回放立即触发越界；最终实现
与回归已同步修复，失败草稿未进入候选。

Industrial优化仍读取SOMA column作为authoritative source，只把同一次
`recomputeMachine`中不会变化的machine snapshot提升到group边界。1M固定100
machine的单位耗时仍约为100K workload的6.4倍，说明`CandidateFrontier`随
machine candidate集合重算的应用级复杂度仍存在；本轮没有把局部读取优化误写成
全局scaling closure。

## 3. 三应用正式回归

当前checked-in baseline为：

- Access component Corretto v1；
- DataFlow component Corretto v5；
- Industrial default/large/long-run：v7/v6/v6；
- Grassing default/large/long-run：v6/v5/v5；
- RTD default/large/long-run：v4/v4/v5。

Grassing baseline replacement来自clean executable commit `b189d1130055…`上的
default/large/long-run各5个独立JVM calibration。每个profile的schema、input、
result、runtime-plan identity与deterministic high-water在fork间一致：

- default allocation固定`5,924,176 B`，update scratch `36,024 B`；
- large allocation固定约`61.145 MB`，update scratch `5,400,048 B`；
- long-run allocation约`27.531 MB`，update scratch `360,024 B`。

新的allocation envelope分别收紧为`7,405,220 B`、`76,431,180 B`与
`34,417,510 B`。Large/long-run继续保留旧的、更严格timing limit，没有借本轮
calibration放宽时间Gate；Full GC envelope保持0。

九个application profile随后在clean commit
`eac9b60fdbbbc1c71713e09be2d7c9f74a84ce29`各以3 forks重放并全部通过。
三个scale profile的中位数为：

| Profile | 时间中位数 | allocation中位数 | GC边界 |
|---|---:|---:|---:|
| Industrial large / 100K operations | 2.061 s；20,611 ns/op | 118,949,080 B | Young最多2；Full 0 |
| Grassing large / 100K × 1,000 ticks | 5.901 s；5,900,939 ns/tick | 61,147,760 B | Young最多1；Full 0 |
| RTD large / 15K work | 0.795 s；52,992 ns/work | 80,374,456 B | Young 0；Full 0 |

上述artifact的`claimAllowed=false`；最终release candidate仍必须在其自身clean
immutable SHA重放application Full。Source Report不复制易漂移的最终artifact
目录，由同SHA benchmark JSONL解析passed/blocked。

## 4. Runtime-scale 与声明边界

V1 required runtime-scale仍由八条production-exact lane组成：

- Small/Fast；
- Medium；
- 单1M narrow numeric root；
- 两个同时resident的1M narrow numeric roots；
- 两张1M String角色Table；
- high-expansion fail-closed；
- Result Delivery；
- 100次lifecycle Soak。

本轮processor generated source发生变化，因此上一candidate的8/8 retained
qualification只能作为回归参照，不能自动外推到当前candidate。最终clean SHA必须
重新生成精确source manifest、逐lane record、schema/checksum与combined artifact，
且八条记录都满足`status=passed`、`required=true`、
`profile=production-exact-v1`、`claimAllowed=false`，G5才成立。

`10m-research`、`100m-single-stress`、`100m-double-stress`与
`100m-string-stress`继续是`required=false` research入口。用户给出的32 GiB专题
上限不足以运行全部预注册100M lane，且这些lane不是V1 blocker；本轮没有用高成本
research代替更贴近真实application的1M/10倍诊断。

即使required qualification全部通过，仍不得扩大为：

- 其他CPU/JDK build/OS/architecture支持；
- 任意wide schema、String length/cardinality/sharing或relation skew；
- Industrial任意machine/frontier规模的线性复杂度承诺；
- RTD任意工作量的stable-order SLA；
- production telemetry、public performance claim或公开发布。

## 5. 从三个应用归纳的最佳使用方法

1. **先区分authoritative state与derived structure。** Live业务事实留在SOMA
   Table；application heap/frontier/calendar只保存可重建的派生调度结构，不把DTO、
   `List<Row>`或Collection graph变成并行事实Owner。
2. **在语义稳定边界提升重复读取。** 同一group/version内只获取一次
   `ColumnView`值或snapshot；不跨mutation保存current Index、view、cursor或
   callback对象。
3. **按增长形状规划scratch。** 不只看最终row count，还要申报population/frontier
   growth pattern、`maximumUpdateScratchBytes`、Group retained上限和transient
   heap余量；同时观察retained high-water与JVM allocation/GC。
4. **只在语义允许处并行。** 重用DataFlow Definition/Template/Context，让
   Invocation保持one-shot；stable order、deterministic merge与Effect safe point
   是真实barrier，不因线程空闲就删除。
5. **优化必须绑定correctness guard。** 至少固定schema/input/config/runtime plan/
   result checksum，并同时看CPU、allocation、Young/Full GC、scratch/high-water；
   单次最好值不校准baseline。
6. **先修形状，再调JVM。** DTO materialization、逐row对象、反射、Stream、boxing
   collection、candidate扩回全表、exact read隐藏rebuild/sort、无界scratch或跨
   lifecycle对象逃逸都应先回到Schema/Access/Plan/Owner处理。

这些规则已经进入[soma-examples开发者报告入口](../modules/soma-examples/README.md)，
但不替代各应用自己的领域建模与恢复责任。

## 6. Scope non-regression 与冻结判断

本专题相对进入profiling前的candidate：

- production public/generated type delta：0；
- module与production dependency delta：0；
- processor内部generated scratch growth策略：1处contract-preserving refinement；
- example production实现：Industrial 2个已有type内部优化；RTD与Grassing领域代码
  不变；
- external consumer regression：增加1个resource/growth journey；
- application baseline：3个Grassing文件原位版本化replacement，baseline总数仍为9；
- script：只更新这3个replacement路径；没有新增benchmark lane或migration checker；
- 文档：更新唯一scenario map、Grassing validation、example最佳实践与本Report；
- active performance Temporary、parallel Owner与未裁决`UNKNOWN`：0。

当前结果是Design-consistent internal refinement，不依赖未来public API、核心事实
迁移或test-only bypass才成立。就“进入真实项目试用前的V1性能冻结”而言，三个
reference application已提供足够的不同形状证据，SOMA generated scratch的明确
浪费已修复，另一个高价值应用热点已优化，RTD风险性改写已被证据否决。

冻结仍有两个诚实边界：Industrial frontier不是任意规模线性算法；真实项目尚未
提供production telemetry。二者是下一阶段使用profile与业务建模输入，不是当前
SOMA V1 Design/Code差距。最终freeze-ready状态由同一clean SHA的canonical Full、
application Full、runtime-scale required qualification以及G6 retained
package/security/CI bundle共同解析。

## 7. 重放入口

```sh
./scripts/check.sh
./scripts/check-reference-application-performance.sh full
./scripts/check-runtime-scale-qualification.sh qualification
./scripts/package-smoke.sh
OSV_SCANNER=<verified-v2.3.8> ./scripts/security-release-scan.sh
```

高成本qualification只在source、环境、假设或证据目标变化时重跑；同一immutable
输入已通过的artifact直接复用。
