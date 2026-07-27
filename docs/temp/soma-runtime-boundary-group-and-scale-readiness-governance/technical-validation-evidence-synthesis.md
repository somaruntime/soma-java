# SOMA Runtime Scale Technical Validation Evidence Synthesis

类型：Temporary

状态：TV0–TV9 evidence transfer complete；P6 consumed

Owner：SOMA runtime scale governance evidence synthesis

正式事实源：否

实施授权：本文只保留已由 P6 正式 Owner 消费的独立 Lab 稳定结论；不授权直接
移植 Lab class/API/constant，也不形成 production readiness、支持矩阵或 SLA

事实范围：TV0–TV9 accepted/rejected/inconclusive、适用 profile、成本来源、
潜在 Owner 与必须保留的 claim boundary

非事实范围：正式 public/generated API、默认阈值、production implementation、
G6、release readiness

上位专题：[治理指导](README.md)

验证协议：[Scale Architecture 技术假设与验证协议](scale-architecture-technical-validation.md)

正式设计去向：[Design 入口](../../design/README.md)

最后审查日期：2026-07-28

## 1. Evidence identity

独立 Lab：

`/Users/arthur/Documents/HGTECH/projects/prototype/soma_runtime_scale_technical_validation_lab`

关键 revision：

| 范围 | revision |
|---|---|
| TV0–TV7 completion | `591bbab` |
| TV8 raw / synthesis | `b2970d2` / `fdadb6d` |
| TV9 implementation / raw / decision | `75fe7a7` / `bbc13e8` / `cf322ab` |

TV9 曾因 independent audit 发现 preflight、oracle、callback stop、Group workload 和
stale-evidence harness 缺陷而拒绝使用第一次 raw run。修正后只执行一次预注册允许的
diagnostic confirmation；复核结论为无 correctness/measurement/validator blocker。

## 2. 总裁决

证据支持的系统方向不是“为 100M 选择一种万能结构”，而是：

> stable logical semantics + compiler-bound capabilities +
> operation-boundary deterministic physical plan。

优化必须共同处理 retained、transient/peak、intermediate、pass count、locality、
fixed cost、cache contention、parallel budget、failure safety、String reachable
memory 和 Result Delivery。单一 Data Representation 不能独自支撑目标。

TV7/TV8/TV9 的 100M evidence 都有窄边界：

- TV7：两个 long column、numeric Key、read-heavy、exact-reserved composite；
- TV8：一个 reference-backed String field，length 16、cardinality/identity
  1,024 的 low-cardinality shared profile；
- TV9：deterministic synthetic Result Delivery source identity，不是 resident
  SOMA Table、真实 Join/Group 或 readiness。

## 3. Accepted directions

| ID | 进入最终设计的方向 | 主要成本收益 | 必须保留的边界 | Evidence |
|---|---|---|---|---|
| A01 | Descriptor → mutable Plan Builder → immutable Effective Metadata → immutable Observation | hot path 移除 Metadata interpretation | Metadata 是 cold control plane；bind 后只消费 ordinal/primitive | TV0 |
| A02 | flat storage 作为 Small/Medium 与 point-heavy plan | fixed cost、point locality | operation-boundary 选择 | TV1 |
| A03 | flat head + fixed tails + segment-aware outer loop 作为 Large scan/growth plan | retained、growth peak、locality | 32K 只是 representative internal parameter | TV1/TV7 |
| A04 | compact current-row + fingerprint locator，回查 authoritative Key | retained、rehash peak | Primary/Unique/Exact 分责；fingerprint 不是 equality | TV2/TV7 |
| A05 | Range/SegmentRange/Bitmap/SparseIndexes 等 multi-shape Candidate | intermediate bytes、pass count | 按 cardinality/locality/reuse/budget 选择 | TV3 |
| A06 | single-pass exact result 直接消费 | 避免 materialization | downstream 不要求 reuse/sort/random access；不是 public pull cursor | TV3 |
| A07 | operation-specialized Eager terminal + publish-after-complete | pass count、atomicity | output/scratch preflight 与 lease/guard | TV3/TV7 |
| A08 | aggregate-only Group 与 member Group 分责 | 避免无用 member links | member state 只由真实语义触发 | TV4 |
| A09 | maintained-access N:1、semi/anti/exists fusion | 消除 pair intermediate/second pass | access 与 scratch 成本可解释 | TV4/TV7 |
| A10 | bounded 1:N Join→aggregate preaggregation | 降低 repeated traversal | aggregate 可分解、state 有 bound | TV4 |
| A11 | changed-row Delta staging | transient 与 Delta cardinality 成正比 | relocation、epoch、atomic publication 同步 | TV5/TV7 |
| A12 | count/sum/min/max 增量 Window state | 消除 repeated rescans | algebra、order/frame 条件明确 | TV5/TV7 |
| A13 | Definition/Template/Invocation 分相，bind 后 ordinal slots | fixed cost、hot lookup/allocation | Map/List 只在 cold path/reference | TV5/TV7 |
| A14 | 一个 bounded scheduler：direct fallback、大 Segment split、小 Segment coalesce | bounded parallelism、load balance | 无 nested executor；task/worker/queue 有硬边界 | TV6/TV7 |
| A15 | worker-local scratch/stats + ordinal deterministic merge | cache contention、determinism | worker hot path 无 shared atomic | TV6/TV7 |
| A16 | managed/borrowed executor ownership 显式 | lifecycle correctness | shutdown/cancel 责任清晰 | TV6 |
| A17 | narrow Large storage + compact locator + fused terminal + bounded scheduler composite | retained/peak/intermediate/parallel | narrow primitive numeric/read-heavy profile | TV7 |
| A18 | high expansion 在 allocation/source touch 前 typed preflight | 避免 OOM/swap/partial result | checked cardinality、width、budget 可先验 | TV4/TV7/TV9 |
| A19 | reference column 只对白名单 immutable String 开放，所有 access/operator 使用 authoritative value semantics | 简单 V1 baseline、封闭 object boundary | 不复制/intern/normalize；不支持 arbitrary object | TV8 |
| A20 | String structural/reachable/observed 三层核算与 profile admission | 显式 retained/GC 风险 | length/cardinality/sharing/role/table count/estimator 明示 | TV8 |
| A21 | Eager Detached 默认 + callback-scoped streaming optional Result Delivery | 消除 output-sized detached allocation、callback early stop | 只限同步 read-only generated typed terminal；无 partial detached；operation preflight 不得绕过 | TV9 |

## 4. Rejected directions

`rejected` 只否决其作为统一或默认生产方向：

| ID | 方向 | 否决依据 |
|---|---|---|
| R01/R02 | simple segmented getter / 所有场景统一 segmented | Small/point 固定税，Large 收益不构成统一默认 |
| R03 | locator bucket 重复完整 Key | retained 多 61.5%，无不可替代收益 |
| R04 | fixed 16-way sharded locator | steady tax；skew 时 peak 收益消失 |
| R05/R06 | 所有 Candidate 统一 indexes / Bitmap | contiguous/dense/sparse 反例同时存在 |
| R07 | steady segmented indexes 或 generic per-row cursor hot loop | dispatch/segment resolution 留在逐 row hot path |
| R08 | aggregate-only Group 仍维护 member links | 1M 多约 4.7 MB 且无语义收益 |
| R09 | unified pair-first/member-first relation | 无条件 materialize，阻断 fusion/preaggregation |
| R10 | unbounded/high-expansion eager allocation | 819.2 GB/2.46 TB 等 output 必须 preflight reject |
| R11 | full-copy 作为小 Delta 默认 | transient 与 Table rows 而非 Delta rows 成正比 |
| R12 | 只增量 count/sum，min/max 仍 rescan 的 partial Window | dominant pass 未消除 |
| R13 | hot Map/List parameter interpreter | ordinal binding 可消除 lookup/object tax |
| R14 | segment-only 唯一 parallel decomposition | 单大 Segment 无法充分拆分；many-small task tax |
| R15 | nested executor / worker-side submission | ownership、budget、cancel、contention 不可控 |
| R16 | Small/Medium 无条件 parallel | fixed tax 与 crossover 不稳定 |
| R17 | 用 String identity/hash/fingerprint 作为 equality | equal-value/distinct-object 与 collision correctness 失败 |

未参加 TV9 的 Iterator、pull cursor、Publisher、async、mutation/effect streaming 和
partial detached publication是 V1 scope boundary，不冒充实验否决。
dictionary、arena、intern、compression 与 arbitrary-object backend 同理。

## 5. Inconclusive / out-of-claim

| ID | 未决范围 | 最终设计处理 |
|---|---|---|
| I01–I04 | Segment size、Candidate crossover、Exact materialization、Join reuse/fan-out crossover | internal cost formula，不写 public 魔数 |
| I05 | staged Delta 与真实 full replacement crossover | 依据 changed rows、width、index rebuild |
| I06–I08 | Block/Morsel/parallel crossover、8 workers 以上 ceiling | internal bounded plan；不得外推 |
| I09 | wide Schema、composite Key、mutation-heavy 100M | 不进入本轮 claim |
| I10 | 跨硬件/JDK、NUMA、多机、稳定吞吐 | 后续 support/qualification |
| I11 | Metadata/API 类型名、层级、默认阈值 | integrated final design |
| I12 | SOMA readiness / G6 | 保持 blocked，正式 Gate 裁决 |
| I13 | 其他 String 100M profile | profile estimator + 独立 production qualification |
| I14 | callback public/generated signature、首批 terminal、production crossover、真实 Group/Join/GC | Design 冻结 contract；production pilot 再裁决 |

## 6. TV9 精确结论

三个隔离 JVM final fork：

| workload | Eager allocation | callback allocation | 关键事实 |
|---|---:|---:|---|
| Small 1K primitive | 16,624 B | 184 B | fixed-tax gate 通过 |
| Medium 32K String | 655,616 B | 184 B | fixed-tax gate 通过 |
| 1M primitive | 16,000,240 B | 184 B | 无 output-sized detached arrays |
| 10M String | 200,000,256 B | 184 B | 无 String reference output array |
| Group 1M→1,024 | 37,208 B | 16,656 B | callback 的 128 B 只是 delivery state，仍有 bounded operation scratch |
| Join 262,144 | 5,243,200 B | 248 B | callback 不保留 pair output |

correctness/failure protocol：

- independent direct-loop projection/relation/group oracle；
- 每项 ordinal、primitive、String `equals()` 与 source reference `==`；
- callback 返回 `false` 实际触发 first/1K/1%/50% stop；
- Eager publication 与 consume phase 分离；
- consumer exception、cancel/deadline、callback 内 mutation/release conflict；
- 所有路径 guard/scratch cleanup；
- checked arithmetic overflow 与 high expansion typed rejection。

100M caveat：

- single logical source 消费/touch 1M；
- 两个 logical 100M roots 的 bounded relation 消费 1,024、合计 touch 2,048；
- 它们不实际驻留两个 100M SOMA Table，不覆盖 locator/scan/Join plan；
- GC delta 包含 measurement 主动 `System.gc()`，不是 steady production GC。

## 7. Owner handoff

| Owner family | 必须消费的 evidence | 最终设计责任 |
|---|---|---|
| Product/Root Design | A01–A21、R01–R17、I01–I14 | canonical narrative、产品边界、核心抽象与默认体验 |
| Schema/Processor | A01、A13、A19、A21 | Descriptor/Metadata、四类类型、generated binding/callback |
| Runtime Core | A02–A04、A11、A17–A20 | Group/Table ownership、storage/access/String/resource/lifecycle |
| DataFlow | A05–A10、A12–A16、A18、A21 | Candidate/operation/scheduler/Result Delivery/failure |
| Testkit/Engineering | 全部 | oracle、production qualification、claim boundary |
| Examples | 仅最终 contract 产生真实偏差的项 | 后置消费者治理，不为展示能力重写 |

## 8. 设计消费规则

1. accepted 是设计输入，不是复制 Lab 实现的授权；
2. rejected 方向不能改名后回到 canonical path；
3. inconclusive 转为显式 cost input、保守 fallback 或后续 qualification；
4. representative `32K/1K/8 workers` 不成为 public/default semantic；
5. Lab 100M 不能替代 production-shape evidence；
6. Eager 默认不因 streaming pilot 被静默替换；
7. callback streaming 不绕过 operation intermediate/resource preflight；
8. 正式事实完成原子 promotion、全部 Gate 与引用清理后，删除 Lab 和本 Temporary。
