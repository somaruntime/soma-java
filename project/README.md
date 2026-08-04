# SOMA Java 项目状态

类型：Project Entry

状态：最终全局一致性审核`PASS`；正式baseline `READY_FOR_IMPLEMENTATION`；
implementation authorization `GRANTED`；I0 `COMPLETED`；I1-I8 `NOT_STARTED`

Owner：SOMA Java 当前项目事实与文档路由

最后审查日期：2026-08-04

## 当前事实

SOMA Java 已在同一个 repository/product identity 下完成 clean-slate 产品重新定义，并于
2026-08-03 完成“大规模编译式 Table 引擎候选设计收口、正式晋升与实施准入审查”：

- Product Owner 确认当前 V1 North Star 为“一亿行以上、编译式、支持关系计算的单进程
  Table 引擎”；
- 一亿行以上是 long-domain/chunked 架构愿景，不是当前硬 Release Gate；
- 第一阶段以百万行数据上的高效、低分配、资源受控操作建立 qualification；
- 正式 Blueprint、九个分责 Design Owner、I0-I8 Implementation Plan 与 G1-G10 Gate 已生效；
- [Formal Promotion](conformance/large-scale-engine-formal-promotion.md)为 `PASS`；
- [实施前最终全局一致性审核](conformance/v1-final-pre-implementation-global-consistency-review.md)
  为`PASS`，Design/Plan为`READY_FOR_IMPLEMENTATION`；
- Product Owner 于 2026-08-03 授予完整 V1 implementation authorization；I0 build spine已于
  2026-08-04完成并通过[正式资格](conformance/i0-build-spine-qualification.md)；
- 当前没有active slice，I1-I8为`NOT_STARTED`，下一项从I1开始；
- G1为`PASS`；G2/G10为I0范围`PASS`但整体仍`IN_PROGRESS`；G3-G9为`NOT_RUN`。

“核心抽象、叙事与不变量证明链”已经正式晋升为第九个Design Owner；Temporary replacement
closure与targeted readiness delta review已完成。当前没有active Temporary。

当前active checkout已有I0 production source、Maven reactor、恰好两个production artifact的
源码、测试与资格脚本；没有I1+公开generated consumer API、Table runtime、benchmark、Example、
CI/release workflow、remote package或committed build artifact。I0完成不等于完整
implementation、performance、compatibility或release成立。

此前 P2 Java 8 feasibility spike 已退役。只有被当前正式晋升记录重新采纳的 bounded
type-shape/mechanism evidence 仍是设计可行性输入；它不是 production test 或 runtime evidence。

## 当前事实入口

| 需要了解的内容 | 唯一入口 |
|---|---|
| 产品定义、用户模型、North Star、能力边界与成功标准 | [SOMA Java V1 产品蓝图](blueprint/README.md) |
| 正式 Design Owner、权威关系与跨 Design 不变量 | [Design 总览](design/README.md) |
| A0-A27核心抽象、N1-N8主叙事、INV-01..19证明链与M0-M2变更协议 | [核心抽象与叙事](design/core-abstractions-and-narratives.md) |
| Schema、annotation、generated object 与 full regeneration | [Schema 与编译生成](design/schema-and-generation.md) |
| Group/Table、long-domain storage、Field type、Key/Index 与 compression | [数据模型与存储](design/data-model-and-storage.md) |
| Direct source、Table/Field operation、View、Group/Join 与 metadata | [逻辑层 API](design/logical-api.md) |
| Exact annotation/shared/generated Java 8 surface | [Generated API Signature](design/generated-api-signatures.md) |
| Typed IR、rewrite、Index substitution、Join/Group planning 与 reference oracle | [规划与优化](design/planning-and-optimization.md) |
| Currentness、Group guard、mutation、parallel 与 resource admission | [执行、并发与并行](design/execution-and-concurrency.md) |
| Result、failure code、mapping、precedence 与 failed-state guarantee | [Result 与 Structured Failure](design/results-and-failures.md) |
| Artifact/build/runtime/storage/Index/compression/scheduler baseline | [Implementation Architecture](design/implementation-architecture.md) |
| I0-I8 实施顺序、exit、stop 与 change protocol | [V1 Implementation Plan](engineering/v1-implementation-plan.md) |
| G1-G10 最低 production evidence | [V1 Implementation Gates](conformance/v1-implementation-gates.md) |
| I0 implementation、artifact与qualification evidence | [I0 Build Spine Qualification](conformance/i0-build-spine-qualification.md) |
| 候选来源、晋升矩阵与 replacement closure | [Formal Promotion](conformance/large-scale-engine-formal-promotion.md) |
| 当前实施准入结论、findings closure与授权边界 | [最终全局一致性审核](conformance/v1-final-pre-implementation-global-consistency-review.md) |
| 当前实现差距与 evidence 状态 | [Conformance](conformance/README.md) |
| 产品与角色入口 | [根 README](../README.md) |
| 品牌资产与权利 | [Assets](../assets/README.md)、[NOTICE](../NOTICE) |
| 安全报告方式 | [Security Policy](../SECURITY.md) |

## 权威关系

```text
Blueprint
    -> Design
        -> Code / Config / Tests
            -> Conformance
                -> role/scenario projections
```

- Blueprint 拥有产品最终希望成为什么；
- Design 拥有系统长期必须遵守的规范性合同；
- code/config/tests 在出现后拥有当前 executable fact；
- Conformance 记录 implementation 与 Design 是否一致及证据边界；
- README、未来 Manual/White Paper/Examples 是投影，不得成为第二份 Design；
- 新的重大长期变化先进入 bounded Temporary，经裁决、验证、晋升和 replacement closure 后
  删除；当前没有active Temporary。

## 项目组织框架映射

本项目采用《面向项目生命周期、角色与场景的项目组织框架》`2.0.0-rc.2`：

| Framework role | SOMA Java path/status |
|---|---|
| Product/role entry | `README.md` |
| Project entry | `project/README.md` |
| Blueprint | `project/blueprint/`；Active V1 Baseline |
| Design | `project/design/`；九个 active Owner |
| Engineering | `project/engineering/`；I0 completed，当前无active slice，下一项I1 |
| Conformance | `project/conformance/`；G1 PASS，G2/G10 I0-scope PASS且整体IN_PROGRESS |
| Temporary | 当前无active topic；目录不拥有current事实 |
| Product Docs | 尚未建立；等待 production surface 与 Gate |
| Modules/Implementation Map/Process/Reports | production 尚未出现，不创建假 map/空目录 |

Selected delivery profile 尚未建立；未来 package/source bundle 必须采用明确 allowlist，不能把
整个 checkout 默认交付给 library consumer。

## 固定产品身份

- 品牌：SOMA；
- GitHub Organization：`somaruntime`；
- repository：`somaruntime/soma-java`；
- copyright owner / maintainer / publishing identity：ArthurFeng；
- Java package / Maven group baseline：`io.github.somaruntime.soma`；
- 语言方向：Java 8；
- License：Apache License 2.0。

这些身份事实不预先证明 artifact、version、dependency、runtime algorithm 或 release profile。

## Predecessor 边界

重启前的完整项目由 annotated Git tag
`archive/pre-product-reset-2026-07-31` 固定，对应 commit
`b69477432b44c4bc75c5f62fff741a729a33def2`。

该 ref 是历史 provenance 和恢复点，不是 release/qualification/API Owner。不得复制、
cherry-pick、包装或通过 compatibility layer 恢复 predecessor source。

## 下一阶段

大规模引擎与核心抽象候选都已收口、正式晋升并通过最终实施前审核。Product Owner 已授予
implementation authorization；当前执行路径为：

```text
I0 build/full-regeneration spine (COMPLETED)
        -> I1 primitive keyed Table vertical slice
            -> one active slice at a time
            -> matching Conformance evidence
                -> stop or next slice
                    -> I8 product qualification
                        -> separate release authorization
```

I0 exit evidence已经闭合；下一项只允许从I1开始。当前授权同时允许每个slice证据闭合后的
干净commit与`develop` push，以及
[Conformance authorization contract](conformance/README.md#6-implementation-authorization-contract)
限定的CI、internal benchmark/profile、local package qualification和JUnit Jupiter 5.x
test-only stack；不包含remote artifact publication、签名或正式发布声明。

## 当前验证边界

I0已执行Java 8 clean build、processor/full-regeneration、independent consumer、failed-state、
artifact/dependency/security baseline与独立审查；详细边界见
[I0 Qualification](conformance/i0-build-spine-qualification.md)。I1-I8的runtime、performance、
package和release Gate只能在相应surface出现后执行，不得用I0或documentation evidence替代。
