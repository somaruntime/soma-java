# 当前性能与规模摘要

类型：Report / Performance / Qualification Snapshot

状态：当前Corretto component/application evidence；runtime-scale为历史Zulu evidence

Owner：SOMA Java 性能与规模 evidence

最后审查日期：2026-07-29

受众：SOMA maintainer、runtime/compiler/DataFlow 开发者和产品决策者

适用版本：`soma-java` `0.2.0-SNAPSHOT`；Corretto baseline commit
`092617b67247cbaed354857daed9d6e1457b876e`；历史runtime-scale production
source `sha256:dfe8fa98b2a411708359a378e05f22e2ad89a7b900c70d1f71e8dd1a6b7f8e69`

输入事实源：[Runtime Boundary、Group、Scale Readiness 与产品化综合治理报告](2026-07-28-runtime-boundary-group-scale-readiness-governance-report.md)、
runtime-scale strict qualification artifact、九份 checked-in application baseline
及 Fast/Scale/Soak/Full Gate

事实范围：当前Corretto production shape的component与三个reference application；
历史Zulu candidate的Small/Medium、1M、10M、single/double 100M、String、
Expansion、Result Delivery、Soak和generated footprint本机证据

非事实范围：跨环境 SLA、任意 Schema/String 的 100M 承诺、production telemetry、
正式支持矩阵、public release readiness 或 G6

测量日期：2026-07-28（runtime-scale历史artifact）；2026-07-29
（Corretto component/application）

环境：当前为Amazon Corretto `1.8.0_502-b07`；历史runtime-scale为Azul Zulu
`1.8.0_492-b09`。二者均为Maven `3.9.16`、macOS `26.5.2` / Darwin
`25.5.0`、`aarch64`、Apple M5 Pro、48 GB、G1 GC。

方法：production module 编译和 generated code；required lane 预注册；strict
schema、complete-set、checksum、negative artifact 与 classfile major 52 验证；
application baseline 使用 5-fork 校准和 3-fork comparator

## 1. 当前判断

当前Corretto candidate已证明SOMA的Java 8、Schema-Defined、
Compiler-Specialized、JVM Heap-Resident产品形态、两个component和九个
application profile可执行且可回归。旧Zulu production candidate还曾证明：

- Small/Medium 不被 fixed tax 或“只有一个 Segment”锁死；
- 1M/10M 覆盖完整 operation family、mutation、relation、Window、String 与 GC；
- single 100M 以及两个 100M root 同时驻留能够在明确窄 profile 下完成；
- impossible high-expansion 在 child enumeration、callback 和巨量 allocation 前拒绝；
- Eager Detached 默认与 callback-scoped delivery 共享同一 lifecycle/resource 边界；
- 三个独立 reference application 在显式 Group owner 下保持结果正确并通过完整性能
  Gate。

上述规模结论是一组`claimAllowed=false`的历史本机qualification事实，不是
“当前Corretto已经重验100M”、任意workload都支持100M或已经可以public release
的声明。JDK authority迁移后G5/G6保持blocked，直至Corretto scale与Linux
release evidence分别形成。

## 2. 历史Runtime-scale qualification（Zulu 8）

Qualification ID：
`runtime-scale-qualification-20260728-dfe8fa98b2a4`。

Artifact identity：

- production source content：
  `dfe8fa98b2a411708359a378e05f22e2ad89a7b900c70d1f71e8dd1a6b7f8e69`；
- combined artifact：
  `71f332598b897f9319afe08dbf12ba6292e44e52f64e7cf053b1c37e79d89e63`；
- strict schema：
  `b124a985cc6581ba25e41dd3aabbbfbd906d76c955f2bf04770f3a50f534c8ea`。

| Lane | 状态 / 耗时 | 当前 production-shape 事实 |
|---|---:|---|
| Small/Fast | passed / 56.1 ms | 0、1、16、256、1K、4K；primitive 与 String；create/point/exact/scan/column/batch/mutate/Group/Join/callback fixed-tax matrix |
| Medium | passed / 108.2 ms | 32K、64K、256K；sequential/parallel 都触发；1/2/8 storage segments 与 1/8/16 tasks 证明 Segment 和 morsel 分离 |
| 1M | passed / 639.9 ms | Point/Exact/Scan/Column/Batch/Delta/Join/Group/Window、String selector/presence/no-op 及实际 weak-reference GC |
| 10M | passed / 1.220 s | segmented growth、fused aggregate、bounded relation、4,096-cardinality shared String 与 10M distinct String objects |
| 100M Single | passed / 5.185 s | 实际 100,000,000 行；`long Key + int groupId + long metric`；structural high-water 5,493,306,072 B |
| 100M Double | passed / 10.352 s | 两个 root 各 100,000,000 行同时 resident；same-Group 65,536 与 cross-Group 4,096 bounded join；structural high-water 10,983,204,272 B |
| 100M String | passed / 2.422 s | 两个 100M root；长度 18..34、cardinality 1,024、跨表 100% 共享对象；structural 4,027,622,496 B、reachable String model 90,112 B |
| Expansion | passed / 41.0 ms | known over-budget、checked overflow、unknown-unprovable 均提前拒绝；`childBindingCallsBeforeReject=0` |
| Delivery | passed / 57.3 ms | Eager 及 Candidate/Value/Group/Join/Window/String 共 7 种；early stop、failure、cancel、deadline、non-escape、GC、ledger 归零 |
| Soak | passed / 136.7 ms | 100 次完整 lifecycle；100 个 String weak reference 清除；Group/Invocation ledger 归零；managed executor 关闭 |

十条required lane在该历史Zulu candidate上均满足`applicable=true`、
`status=passed`、`claimAllowed=false`。validator还拒绝缩小后的伪double-100M、
非法claim、缺失required lane和extra field。当前Corretto authority必须按原
schema、预算和validator重跑，不能改名复用本artifact。

当前DataFlow component baseline为Corretto v3，Access为Corretto v1。真实
Corretto 3/5-fork candidate均在旧envelope内，因此checksum、allocation、timing、
tail、GC和memory ceiling未放宽。bounded morsel继续把task与worker解耦；
count与large-sum parallel allocation ceiling保持`3,996`和`4,519 B/op`。

## 3. String 结论与内存口径

V1 正式支持 reference-backed immutable `String` baseline：

- 保存 caller reference，不 copy、intern 或 normalize；
- Key/Unique/Index/Group/Join 使用 authoritative value equality/hash/order；
- required null 拒绝，optional absence 与 empty String 分离；
- equal-value different-object update 是 no-op，不替换引用或推进 epoch；
- replace/failure/remove/clear/release 后，dead reference 不再由 SOMA 结构保留。

任何 String 规模结论必须同时声明 UTF-16 长度、value cardinality、distinct object
identity、共享率、presence、字段角色和同时 live Table 数量。资源报告必须分开：

1. SOMA-owned structural bytes；
2. SOMA-retained reachable String bytes/model；
3. JVM observed heap。

当前双 100M profile 是高共享 payload profile；10M lane 另行覆盖 distinct String
objects。高 cardinality String Key、多 String 列、长文本和 mutation-heavy 100M
不能由这两条 lane 外推。

## 4. 三个 reference application

三应用均保留原业务模型、算法叙事、detached Result和领域validator。JDK authority
迁移后，九份baseline在Corretto上以5 fork重新校准，并以普通3 fork全部回放通过：

| 应用 / profile | baseline | timing 上限 | allocation 上限 | GC 上限 |
|---|---|---:|---:|---:|
| industrial default | v6 | hot 25,213,563 ns；E2E 40,426,248 ns | hot 4,959,510 B；E2E 10,978,790 B | young/full 0 |
| industrial large | v5 | hot 3,488,431,001 ns；E2E 3,549,411,188 ns | hot 148,686,770 B；E2E 303,741,530 B | young 3 / 10 ms；full 0 |
| industrial long-run | v5 | hot 89,828,187 ns；E2E 129,993,626 ns | hot 15,374,160 B；E2E 40,671,230 B | young 2 / 4 ms；full 0 |
| grassing default | v4 | 225,824,688 ns | 14,494,170 B | young/full 0 |
| grassing large | v3 | 8,986,689,750 ns | 533,070,360 B | young 16 / 14 ms；full 2 / 39 ms |
| grassing long-run | v3 | 5,452,954,251 ns | 59,967,160 B | young/full 0 |
| RTD default | v3 | 95,301,063 ns | 10,429,430 B | young/full 0 |
| RTD large | v3 | 1,192,005,438 ns | 100,467,110 B | young/full 0 |
| RTD long-run | v3 | 95,392,626 ns | 34,886,160 B | young/full 0 |

每份baseline登记自己的5-fork candidate content checksum。
allocation 使用 `ceil(p50 * 1.25)`，timing 使用
`ceil(max(p50 * 1.50, p90 * 1.25))`，确定性字段 all-equal；Fast、Scale、Soak
和Full 3-fork comparator全部通过。校准时发现的旧Schema/RuntimePlan identity
漂移在Zulu和Corretto对同一当前source上结果一致，因此属于旧evidence漂移，不是
JDK不确定性。

## 5. Generated footprint 与 fixed tax

当前有 22 张 generated Table，每张恰有一个 Scan 和一个 DataFlow companion。
qualification 新增三张 neutral schema Table 后，neutral fixed candidate 从 6
变为 9；code-size Gate 已在首个完整 production candidate 上重新建立 15% ceiling。

| Surface | Tables | Table source bytes / lines | Table family bytes / nested | DataFlow source bytes / lines | DataFlow family bytes / nested |
|---|---:|---:|---:|---:|---:|
| neutral | 9 | 215,416 / 944 | 306,239 / 77 | 101,772 / 1,064 | 349,331 / 92 |
| industrial | 9 | 218,411 / 949 | 315,029 / 74 | 100,071 / 1,024 | 359,855 / 91 |
| grassing | 2 | 47,807 / 215 | 69,151 / 17 | 22,266 / 235 | 77,965 / 20 |
| RTD | 2 | 47,586 / 213 | 67,775 / 17 | 22,682 / 236 | 78,726 / 21 |

这些指标控制 compiler specialization 的产品成本，但不允许仅凭 LOC、单实现或单
调用者删除具有设计意图和完整 replacement chain 的能力。

## 6. 声明边界

当前可以确认：

- Java 8 production implementation 支撑 Metadata/Group/closed Capability 产品叙事；
- Corretto/macOS上的component和九application profile成立；
- Small/Medium adaptive execution、single/double 100M窄numeric profile、
  明确String profile和两种Result Delivery曾在历史Zulu candidate成立；
- impossible expansion 可 fail-closed；
- 三个reference application的当前Corretto correctness与性能Gate成立。

扩大下列声明前仍需新的预注册 qualification：

- 其他 CPU/JDK build/OS 和正式 support matrix；
- wide schema、composite Key、mutation-heavy 100M；
- 更长、更多列或高 cardinality Key 的 String；
- 新 relation multiplicity/skew、global materialization/sort/window；
- 更高强度 soak、production telemetry 与 public performance claim。

当前必须保持的较低声明是：Corretto 100M尚待重验；历史100M只代表明确
schema/workload/resource profile；
String 只代表白名单 immutable value；Lazy Output 只代表同步 read-only
callback-scoped delivery；不可证明展开上界时拒绝执行；G6 不因本机技术成功而改变。

## 7. 重放入口

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home \
  ./scripts/check-runtime-scale-qualification.sh

JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home \
  ./scripts/check-reference-application-performance.sh full
```

普通 `./scripts/check.sh` 负责 fresh build、public/generated/external consumer、
correctness、component、Fast application、文档和静态 Gate；重型 scale
qualification 与 Full application performance 保持显式入口，防止日常反馈被大规模
workload 淹没。所有结果仍必须按报告 metadata 和 `claimAllowed` 边界解释。
