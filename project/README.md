# SOMA Java 项目入口

类型：Project Entry

状态：V1 implementation `COMPLETED`；G1–G10 `PASS`；publication `NOT_AUTHORIZED`

Owner：SOMA Java 当前项目事实与内部文档路由

最后审查日期：2026-08-10

## 当前结论

SOMA（State-Oriented Memory Architecture）已经在 clean-slate V1 Blueprint 下完成 I0–I8
implementation 与 qualification：

- 正式 Blueprint、九个 Design Owner、I0–I8 Engineering Plan 和 G1–G10 Gate 已生效；
- Java 8 下的两项 production artifact、generated API、chunked storage、Key/Index、query/optimizer、
  mutation、GroupBy/Join、parallel、compression/metadata、三个 reference application 和本地交付链已建立；
- G1–G10 当前为 `PASS`；百万行证据是同机 qualification，不是一亿行承诺或跨硬件 SLA；
- 当前没有active implementation slice；[32位结构域与即时增量Key/Index维护治理](temp/soma-incremental-structural-mutation-governance/README.md)
  是唯一active bounded Temporary，专题Design已获Product Owner批准，S0 predecessor baseline已提交，
  S1/S2尚未实施或qualification；
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
| 工程状态与入口 | [Engineering](engineering/README.md) |
| G1–G10 定义 | [V1 Implementation Gates](conformance/v1-implementation-gates.md) |
| 当前实现与 Design 的一致程度 | [Conformance](conformance/README.md) |
| I0–I8 历史实施资格 | [Conformance records](conformance/README.md#4-active-records) |
| 仓库交付结构治理 | [Delivery-centered governance](conformance/v1-delivery-centered-repository-governance.md) |
| 性能、正确性与长期 benchmark 治理 | [Performance and correctness governance](conformance/v1-performance-correctness-governance.md) |
| 千万行组合负载、资源与优化证据 | [Ten-million composed workload governance](conformance/v1-ten-million-composed-workload-governance.md) |
| Design/Execution/Memory/CPU 四维归因与优化 | [Four-dimensional performance architecture governance](conformance/v1-four-dimensional-performance-architecture-governance.md) |
| Operator × Type × Distribution 性能资格 | [Type and distribution performance qualification](conformance/v1-operator-type-distribution-performance-qualification.md) |
| 内存归因与低分配执行资格 | [Memory attribution and low-allocation governance](conformance/v1-memory-attribution-low-allocation-governance.md) |
| 全面性能前沿、10K/1M/10M、Profile 与剩余边界 | [Performance frontier qualification](conformance/v1-performance-frontier-qualification.md) |
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
- `temp/`：只服务active bounded topic；当前唯一topic为
  [32位结构域与即时增量Key/Index维护治理](temp/soma-incremental-structural-mutation-governance/README.md)。

日常检查使用：

```sh
./scripts/check.sh
```

完整 non-publishing qualification 使用：

```sh
./scripts/qualify.sh
```

## 固定产品身份

- 品牌：SOMA（State-Oriented Memory Architecture）；
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

当前唯一active topic是
[32位结构域与即时增量Key/Index维护治理](temp/soma-incremental-structural-mutation-governance/README.md)：
其bounded专题Design已获批准，S0 predecessor baseline已成为executable fact，但candidate B仍不是
项目正式Design或Conformance结论。此前性能
前沿Temporary已完成replacement closure并退役。Temporary仍只服务active topic，不能变成平行
Design或历史档案。
