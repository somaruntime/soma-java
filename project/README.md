# SOMA Java 项目入口

类型：Project Entry

状态：V1 implementation `COMPLETED`；G1–G10 `PASS`；Canonical IR/Execution M1
`S1-S6_COMPLETED / NO_ACTIVE_IMPLEMENTATION_SLICE`；Vectorized Pipeline扩展
`VP1-VP3_COMPLETED / FORMALLY_PROMOTED / NO_ACTIVE_IMPLEMENTATION_SLICE`；Physical Execution Engine M2
`FORMALLY_PROMOTED / IMPLEMENTATION_AUTHORIZED / P1-P2_COMPLETED / P3_ACTIVE`；publication `NOT_AUTHORIZED`

Owner：SOMA Java 当前项目事实与内部文档路由

最后审查日期：2026-08-12

## 当前结论

SOMA（Scheme-Oriented Memory Architecture）已经在 clean-slate V1 Blueprint 下完成 I0–I8
implementation 与 qualification：

- 正式 Blueprint、九个 Design Owner、I0–I8 Engineering Plan 和 G1–G10 Gate 已生效；
- Java 8 下的两项 production artifact、generated API、chunked storage、Key/Index、query/optimizer、
  mutation、GroupBy/Join、parallel、compression/metadata、三个 reference application 和本地交付链已建立；
- G1–G10 当前为 `PASS`；百万行证据是同机 qualification，不是一亿行承诺或跨硬件 SLA；
- Canonical Logical IR与执行引擎M1责任调整已经完成
  [正式晋升、Baseline Freeze与实施准入审查](conformance/v1-canonical-ir-execution-engine-promotion-readiness.md)，
  Product Owner已于2026-08-11授权完整实施；[S1资格](conformance/canonical-ir-execution-s1-qualification.md)
  、[S2资格](conformance/canonical-ir-execution-s2-qualification.md)、
  [S3资格](conformance/canonical-ir-execution-s3-qualification.md)与
  [S4资格](conformance/canonical-ir-execution-s4-qualification.md)与
  [S5资格](conformance/canonical-ir-execution-s5-qualification.md)和
  [S6最终资格](conformance/canonical-ir-execution-s6-final-qualification.md)均已`PASS`；S1-S6已经
  `COMPLETED`，当前没有active implementation slice；
  [大范围优化后全局完整性与回归审查](conformance/v1-post-governance-global-integrity-regression-review.md)
  已`PASS`，operation provenance、Index allocation admission与正式Owner状态漂移均已闭合；
  [Scheduling性能治理](conformance/v1-scheduling-performance-governance.md)已`PASS`，标准100K
  FJSP纯dispatch在固定主机降至fresh约0.6秒、warm约0.51秒；
  [32位结构域与即时增量Key/Index维护资格](conformance/v1-incremental-structural-mutation-governance.md)
  已`PASS`：Table-local结构域统一为`int`，累计域保持`long`，Key/Index仅保留
  singleton-inline / ordered `int[]`一套membership truth；
- [性能与正确性联合治理](conformance/v1-performance-correctness-governance.md)已`PASS`，长期
  benchmark、相对回归判定与当前最佳实践已经建立；
- [四维性能架构治理](conformance/v1-four-dimensional-performance-architecture-governance.md)已`PASS`，
  Design、Execution、Memory、CPU因果模型与本轮GroupBy/Relation优化已经闭合；
- [Operator × Type × Distribution 性能资格](conformance/v1-operator-type-distribution-performance-qualification.md)
  已`PASS`，类型/分布 kernel、cost-aware RLE、Bound cardinality 与 Field materialization 已闭合；
- [内存归因与低分配执行治理](conformance/v1-memory-attribution-low-allocation-governance.md)已`PASS`，
  retained、temporary reservation、Java allocation、heap/RSS 已分责归因，正常路径低分配优化已闭合；
- [全面性能前沿资格](conformance/v1-performance-frontier-qualification.md)已`PASS`，Table、Field、
  IndexSelection 与主要派生 operation 在 10K/1M/10M 下完成固定主机资格，三轮 profile-driven
  优化、最终 memory attribution、reference application 与 composed journey 已闭合；
- [Scheduling reference application治理](conformance/v1-scheduling-reference-application-governance.md)
  已`PASS`：标准100K FJSP使用四张runtime Table和自然waiting point `add/remove`完成FCFS + SPT，
  fixed slot与application frontier workaround已退出；
- [Grassing Simulation reference application治理](conformance/v1-grassing-simulation-reference-application-governance.md)
  已`PASS`：两张runtime Table、五阶段tick、UI/headless、deterministic validation、Application JFR/A-B
  与source/package delivery闭合；
- [Selection mutation write-set治理](conformance/v1-selection-mutation-write-set-governance.md)
  已`PASS`：PLAIN Selection update/remove退出touched-Chunk全leaf copy，使用columnar write set、
  dense move plan和final-locator sidecar projection，同时保持encoded candidate与zero-publication；
- [Vectorized Physical Pipeline第一阶段正式晋升](conformance/v1-vectorized-physical-pipeline-phase1-promotion.md)
  已`PASS`：finite primitive Chunk kernel、PhysicalPlan单次decision、shared ordinal-work lifecycle与
  Chunk-morsel partial已纳入production baseline；
  [扩展治理](conformance/v1-vectorized-physical-pipeline-expansion-governance.md)也已`PASS`：VP1
  encoded-native integral与VP2 ordered `long[]` sequential/parallel路径完成资格，VP3已关闭正式Owner
  晋升、全仓qualification与Temporary replacement closure；当前没有active implementation slice；
- [Physical Execution Engine M2正式晋升与实施准入](conformance/v1-physical-execution-engine-m2-promotion-readiness.md)
  已`PASS`：Pipeline/Segment/Breaker/Kernel/Frame/Morsel责任已进入正式Owner，P1-P6 Baseline冻结且
  Product Owner已授权完整实施；[P1资格](conformance/physical-execution-engine-m2-p1-qualification.md)
  与[P2资格](conformance/physical-execution-engine-m2-p2-qualification.md)已`PASS`，当前P3为唯一active slice；
- GitHub Release/Package、Maven publication、签名和正式 release 声明仍未授权。

根 [`README`](../README.md) 是 Library user 入口。本目录集中服务 Product Owner/维护者和
Codex/Agent，不向普通使用者投射完整设计与实施过程。

## 三个角色的入口

| 角色 | 首要入口 | 目的 |
|---|---|---|
| Library user | [Root README](../README.md)、[Examples](../soma-examples/README.md) | 了解、构建和使用 SOMA |
| Codex/Agent | [AGENTS.md](../AGENTS.md) | 当前约束、Owner、验证与 Git 边界 |
| Product Owner/维护者 | 本页、[Conformance](conformance/README.md) | 产品状态、裁决、证据和 release boundary |

## 正式事实地图

| 问题 | 唯一入口 |
|---|---|
| 产品目标、用户模型、边界和成功标准 | [V1 Blueprint](blueprint/README.md) |
| Design Owner 与权威关系 | [Design 总览](design/README.md) |
| 核心抽象、叙事、不变量和变更协议 | [核心抽象与叙事](design/core-abstractions-and-narratives.md) |
| 实施顺序与 slice exit | [V1 Implementation Plan](engineering/v1-implementation-plan.md) |
| Canonical IR/Execution S1-S6替换顺序与证据 | [Canonical IR/Execution Plan](engineering/canonical-ir-execution-engine-implementation-plan.md) |
| 工程状态与入口 | [Engineering](engineering/README.md) |
| G1–G10 定义 | [V1 Implementation Gates](conformance/v1-implementation-gates.md) |
| 当前实现与 Design 的一致程度 | [Conformance](conformance/README.md) |
| 大范围优化后的全局完整性、资源与回归审查 | [Post-governance global review](conformance/v1-post-governance-global-integrity-regression-review.md) |
| I0–I8 历史实施资格 | [Conformance records](conformance/README.md#4-active-records) |
| 仓库交付结构治理 | [Delivery-centered governance](conformance/v1-delivery-centered-repository-governance.md) |
| 性能、正确性与长期 benchmark 治理 | [Performance and correctness governance](conformance/v1-performance-correctness-governance.md) |
| 千万行组合负载、资源与优化证据 | [Ten-million composed workload governance](conformance/v1-ten-million-composed-workload-governance.md) |
| Design/Execution/Memory/CPU 四维归因与优化 | [Four-dimensional performance architecture governance](conformance/v1-four-dimensional-performance-architecture-governance.md) |
| Operator × Type × Distribution 性能资格 | [Type and distribution performance qualification](conformance/v1-operator-type-distribution-performance-qualification.md) |
| 内存归因与低分配执行资格 | [Memory attribution and low-allocation governance](conformance/v1-memory-attribution-low-allocation-governance.md) |
| 全面性能前沿、10K/1M/10M、Profile 与剩余边界 | [Performance frontier qualification](conformance/v1-performance-frontier-qualification.md) |
| 32位结构域、即时增量 Key/Index 与 mutation A/B | [Incremental structural mutation qualification](conformance/v1-incremental-structural-mutation-governance.md) |
| 标准100K FJSP、Solver架构与自然waiting mutation回归 | [Scheduling reference governance](conformance/v1-scheduling-reference-application-governance.md) |
| Grassing空间仿真、UI/headless与Application性能治理 | [Grassing simulation governance](conformance/v1-grassing-simulation-reference-application-governance.md) |
| Selection mutation write-set、原位提交与A/B | [Selection mutation governance](conformance/v1-selection-mutation-write-set-governance.md) |
| Finite primitive Chunk kernel与morsel partial正式晋升 | [Vectorized physical pipeline phase 1](conformance/v1-vectorized-physical-pipeline-phase1-promotion.md) |
| Encoded integral、ordered `long[]`与VP1-VP3扩展资格 | [Vectorized pipeline expansion governance](conformance/v1-vectorized-physical-pipeline-expansion-governance.md) |
| Physical Pipeline/Segment/Breaker/Kernel/Frame/Morsel实施与资格 | [Physical Execution Engine M2](conformance/v1-physical-execution-engine-m2-promotion-readiness.md) |
| 安全、支持、品牌和许可 | [SECURITY](../SECURITY.md)、[SUPPORT](../SUPPORT.md)、[NOTICE](../NOTICE)、[LICENSE](../LICENSE) |

精确 schema、storage、logical API、generated signature、planning、execution、failure 和 artifact
合同由 [`design/`](design/README.md) 中的分责 Owner 拥有，本页不复制正文。

## Executable fact 的位置

```text
Blueprint
    -> Design
        -> code / build-support / tests
            -> Conformance
                -> README / Examples / future user docs
```

- `soma-runtime/`、`soma-processor/`：恰好两项 production artifact；
- `soma-examples/`：三个真实下游 reference application；
- `tests/`：按长期 capability 组织的跨 artifact executable evidence；
- `build-support/`：linkage、codegen、qualification 和 delivery machinery；
- `scripts/`：`check`、`qualify`、`benchmark`、`package-local` 四个稳定入口；
- `docs/`：仅占位，等待独立用户文档专题；
- `benchmarks/`：reference application、type-kernel 与 performance-frontier 的长期非 production 证据；
- `conformance/`：资格和 claim boundary；
- `temp/`：只服务bounded topic或明确标记的queued intent；当前唯一active bounded topic是
  [Physical Execution Engine M2治理](temp/soma-physical-execution-engine-m2-governance/README.md)，其稳定事实
  已正式晋升，Temporary仅保存实施期provenance并等待P6 closure；Vectorized
  Physical Pipeline扩展已经由
  [正式Conformance记录](conformance/v1-vectorized-physical-pipeline-expansion-governance.md)接管并完成
  Temporary replacement closure；Grassing治理已由
  [正式Conformance记录](conformance/v1-grassing-simulation-reference-application-governance.md)接管并退役Temporary；
  [SOMA Engine产品构思](temp/soma-engine-product-concept/README.md)只是未来产品intent，不是Design或
  implementation input。

日常检查使用：

```sh
./scripts/check.sh
```

完整 non-publishing qualification 使用：

```sh
./scripts/qualify.sh
```

## 固定产品身份

- 品牌：SOMA（Scheme-Oriented Memory Architecture）；
- repository：`somaruntime/soma-java`；
- copyright owner / maintainer / publishing identity：ArthurFeng；
- Java package / Maven group：`io.github.somaruntime.soma`；
- 语言：Java 8；
- License：Apache License 2.0。

这些身份事实不证明远端 artifact、compatibility、performance 或 release 已成立。

## Clean-slate 与 Temporary

Predecessor 仅由 Git ref `archive/pre-product-reset-2026-07-31` 保存，不能复制、cherry-pick、包装
或通过 compatibility layer 恢复。历史记录只用于 provenance，不能覆盖 current Blueprint、
Design、code 或 Conformance。

当前唯一active bounded topic是
[Physical Execution Engine M2治理](temp/soma-physical-execution-engine-m2-governance/README.md)；其设计已由
正式Owner接管，Product Owner已授权P1-P6实施，P1已完成，当前P2为唯一active slice。
Vectorized Physical Pipeline第一阶段由
[正式晋升记录](conformance/v1-vectorized-physical-pipeline-phase1-promotion.md)接管，后续VP1-VP3由
[扩展治理记录](conformance/v1-vectorized-physical-pipeline-expansion-governance.md)完成正式Owner晋升、
qualification与Temporary replacement closure。Grassing治理已由
[正式Conformance记录](conformance/v1-grassing-simulation-reference-application-governance.md)接管并完成
Temporary replacement closure。Canonical IR与执行引擎治理已由
[正式Conformance记录](conformance/v1-canonical-ir-execution-engine-promotion-readiness.md)完成Owner晋升、
Baseline Freeze、readiness与Temporary replacement closure；production implementation与S1–S6 qualification
均已完成，当前没有active implementation slice。
[SOMA Engine产品构思](temp/soma-engine-product-concept/README.md)独立保留为queued intent。
[Scheduling性能治理](conformance/v1-scheduling-performance-governance.md)
已完成正式Owner晋升、Conformance与replacement closure。此前
[32位结构域与即时增量Key/Index维护](conformance/v1-incremental-structural-mutation-governance.md)
已完成正式Owner晋升、Conformance与replacement closure；候选Design与交接材料不作为平行历史
档案保留。
