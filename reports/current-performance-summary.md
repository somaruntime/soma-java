# 当前性能与规模摘要

类型：Report / Performance / Qualification Snapshot

状态：`1.0.0` G5 passed

Owner：SOMA Java 性能与规模 evidence

受众：SOMA maintainer、runtime/compiler/DataFlow 开发者和产品决策者

适用版本：`soma-java` `1.0.0`

输入事实源：[V1 release governance](java-v1-release-governance-report.md)、
当前strict runtime-scale artifact、两份component baseline与九份reference
application baseline

事实范围：当前 Corretto production shape 的 component、三个 reference
application、Small/Medium/单1M/双1M/String/Expansion/Delivery/Soak evidence

非事实范围：跨环境 SLA、任意 Schema/String row-count 承诺、production
telemetry、G6、public release 或 Maven Central readiness

最后审查日期：2026-07-29

测量环境：Amazon Corretto `1.8.0_502-b07`、Maven `3.9.16`、macOS
`26.5.2` / Darwin `25.5.0`、`aarch64`、Apple M5 Pro、48 GiB；runtime-scale
使用G1 GC和lane-specific heap。

## 1. 当前结论

当前clean Corretto executable candidate在记录环境下已经证明：

- Small/Fast没有被统一planner、DataFlow lifecycle或scale architecture的固定税
  锁死；
- Medium覆盖32K/64K/256K、FLAT与segmented layout、sequential与bounded
  parallel crossover；
- 一张实际resident的1M narrow numeric-Key root可完成Point、Exact、Scan、
  Column、Batch、Delta、Join、Group、Window与closed numeric kernel；
- 两个同时resident的1M narrow numeric roots可完成双侧aggregate、min/max filter、
  Bloom filter、dense fallback与same/cross Group bounded relation；
- 两张1M String角色Table可同时覆盖payload、Key、Unique、Index、Group、Join、
  mutation、presence、clear、release和actual GC；
- high-expansion在不可接受的enumeration/allocation前fail closed；
- Eager Detached与callback-scoped delivery、100次lifecycle soak均能清理ledger、
  executor和String reference；
- 两个component和三个application的九个profile仍在各自checked-in baseline内。

这些是`claimAllowed=false`的单机qualification与回归事实，不是public latency
SLA、跨环境支持矩阵或任意wide Schema保证。Runtime-scale与DataFlow的精确
clean-commit证据已形成；canonical Full又覆盖其余component、reference
application与contract Gate，因此当前G5为passed。

## 2. 当前 Runtime-scale qualification v2

Qualification ID：
`runtime-scale-qualification-bd25e1194931-5669bf68ddf5`。

Artifact identity：

- clean commit：
  `bd25e1194931df2a9869c2164df658839449785d`；
- executable source tree：
  `content-sha256:5669bf68ddf502495b4a323743f9251a4fab9a3f2c73b4f73fb7eb3a919e5f81`；
- combined artifact：
  `6d52d2ae4bd0ec4472604fb17d39375a90b39ac55e4da031f5df43c992449e32`；
- strict schema v2：
  `eeb8eb1e0f5beda9b3970746b796eb0c5e58a7b8ccd5a9f7cc21dbafc98b4ce2`。

| Lane | 状态 / 诊断耗时 | Structural high-water | JVM heap peak | 直接结论 |
|---|---:|---:|---:|---|
| Small/Fast | passed / 54.8 ms | 1,118,912 B | 15,728,640 B | 0、1、16、256、1K、4K primitive/String fixed-tax matrix |
| Medium | passed / 109.6 ms | 27,568,936 B | 75,497,840 B | 32K、64K、256K；1/2/8 segments与1/8/16 tasks |
| 1M Single | passed / 133.7 ms | 50,993,056 B | 124,519,032 B | actual resident 1M root与完整operation family |
| 1M Double | passed / 209.6 ms | 98,578,240 B | 238,848,744 B | 两个actual resident 1M roots；三种Join filter/fallback与cross-Group |
| String | passed / 668.5 ms | 168,252,058 B | 566,284,160 B | 两张1M角色Table；reachable String model 192,753,664 B；release后heap 3,330,040 B |
| Expansion | passed / 38.2 ms | 0 | 7,875,872 B | over-budget、overflow、unknown-unprovable均提前拒绝 |
| Delivery | passed / 47.0 ms | 600,992 B | 11,534,336 B | 7种delivery与early-stop/failure/cancel/deadline/non-escape |
| Soak | passed / 140.3 ms | 600,992 B | 51,642,368 B | 100次lifecycle、100个weak reference、ledger/executor归零 |

八条required lane全部满足`applicable=true`、`status=passed`、
`claimAllowed=false`。Validator拒绝非法claim、缩小1M、extra field以及不完整或
重复lane；runner/validator classfile为Java 8 major 52。唯一source manifest覆盖
实际production/build/runner closure并包含两个shell library；commit、source tree、
schema与combined artifact均已封存为上列identity。

## 3. String 结论与内存口径

V1正式支持reference-backed immutable `String`：

- 保存caller reference，不copy、intern、normalize、dictionary encode或进入arena；
- Key/Unique/Index/Group/Join使用authoritative Java value equality/hash/order；
- required null拒绝，optional absence与empty String分离；
- 不同长度mutation只替换reference slot，不改变column layout；
- equal-value different-object mutation是no-op，不替换reference；
- clear/release后tracked reference实际可被GC回收。

String lane必须同时解释长度、value cardinality、distinct object identity、共享率、
presence、字段角色和同时live Table数。本次payload为长度12..48、cardinality
4,096、高共享；access角色包含1M-cardinality Key/Unique与高共享Index。

资源口径始终分开：

1. SOMA-owned structural bytes；
2. SOMA-retained reachable String bytes/model；
3. JVM observed heap。

上述长度和共享只属于workload/evidence profile，不是Schema约束、hard cap或其他
String profile的替代证据。

## 4. Component 与三个 reference application

当前checked-in baseline为：

- Access component Corretto v1；
- DataFlow component Corretto v4；
- industrial default/large/long-run：v6/v5/v5；
- grassing default/large/long-run：v4/v3/v3；
- RTD default/large/long-run：v3/v3/v3。

Component覆盖direct/Candidate、fixed tax、parallel crossover、Effect、delivery、
allocation与stats；九个application profile覆盖三种领域叙事的default、large和
long-run integrated correctness、allocation、GC和high-water。Baseline只服务本
环境回归，不形成public claim。

DataFlow v4在clean commit
`733db714f0b5f1edc41ada0ddf86352187b989bd`完成固定3-fork重放；workload与
threshold未改变，result hash为
`270704b7e42fb5d7d7160de09df3f4edb02465768982f84455697e46b33e00d5`。
其后候选只改变baseline provenance checker、文档与G6 NOTICE checksum，不改变
DataFlow executable source；clean commit
`fa934c24996f37367843e2e2a1ac06cb97c7affd`的canonical Full已再次执行并通过
相同baseline。

## 5. 10M/100M 的当前定位

2026-07-28旧Zulu candidate曾完成1M/10M/single-double100M和高共享String
100M。这些仍是历史research事实，但不属于当前JDK authority，也不再是V1
readiness Gate。

当前artifact v2保留以下`required=false`、`research-stress-v1`入口：

- `10m-research`；
- `100m-single-stress`；
- `100m-double-stress`；
- `100m-string-stress`。

它们只回答明确的可完成性、扩展曲线和资源问题；缺失、失败或inconclusive不阻塞
G5，成功也不能升级为任意Schema/String或public SLA。

## 6. 声明边界

当前可以确认：

- 当前clean executable candidate的Small/Medium、单1M、双1M、String、
  Expansion、Delivery、Soak在记录Corretto/macOS/aarch64环境成立；
- logical type facade、closed numeric kernel、formula-bound Bitmap与primitive
  Join runtime filter的production Java实现未在本轮修改；
- checked-in component/application baseline仍是当前重放的唯一baseline Owner。

当前`1.0.0` G5可以陈述为passed，但只能绑定上述clean commit、source closure、
环境与profile。

扩大下列声明前仍需新的预注册qualification：

- 其他CPU/JDK build/OS及正式support matrix；
- wide schema、composite Key、不同relation multiplicity/skew；
- 其他String长度/cardinality/sharing/column-role组合；
- 全局materialization/sort/window、高强度soak或production telemetry；
- public performance claim、private-source G6或公开发布。

## 7. 重放入口

```sh
./scripts/check-runtime-scale-qualification.sh qualification
./scripts/check-runtime-scale-qualification.sh research
./scripts/check-reference-application-performance.sh full
```

普通`./scripts/check.sh`负责fresh build、public/generated/external consumer、
correctness、component、Fast application、文档和静态Gate。Qualification仅在
source、环境、假设或证据目标变化时重跑。
