# SOMA Java 项目状态

类型：Project Entry

状态：最终全局一致性审核`PASS`；正式baseline `READY_FOR_IMPLEMENTATION`；
implementation authorization `GRANTED`；I0-I8 `COMPLETED_LOCALLY`；远端G10待验证

Owner：SOMA Java 当前项目事实与文档路由

最后审查日期：2026-08-09

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
- Product Owner 于 2026-08-03 授予完整 V1 implementation authorization；I0-I8已经完成本地
  implementation与qualification；I0-I7分别通过
  [I0资格](conformance/i0-build-spine-qualification.md)与
  [I1资格](conformance/i1-primitive-keyed-table-qualification.md)、
  [I2资格](conformance/i2-schema-type-storage-breadth-qualification.md)与
  [I3资格](conformance/i3-query-ir-reference-qualification.md)与
  [I4资格](conformance/i4-selection-mutation-resource-qualification.md)、
  [I5资格](conformance/i5-group-relation-qualification.md)与
  [I6资格](conformance/i6-parallel-execution-qualification.md)与
  [I7资格](conformance/i7-compression-metadata-qualification.md)，I8通过
  [产品资格与G9 Owner sign-off](conformance/i8-product-qualification.md)；
- 当前没有active implementation slice；只剩`develop`远端workflow完成G10闭环；
- G1-G9为`PASS`；G10为`LOCAL_PASS / REMOTE_QUALIFICATION_PENDING`。

“核心抽象、叙事与不变量证明链”已经正式晋升为第九个Design Owner；Temporary replacement
closure与targeted readiness delta review已完成。当前没有active Temporary。

当前active checkout已有I0-I8 production source、Maven reactor、恰好两个production artifact、
完整I2 generated schema/type/Key/Index API breadth、paged primitive/reference PLAIN/encoded/overlay storage、Value
flattening、multiple Index、direct selection与point add/update/remove，以及sequential query、typed IR、
reference interpreter、optimized sequential execution、Selection mutation、Group accounting、GroupBy、
binary Equality/Cross Join、bounded parallel execution、AUTO/OFF compression与四级metadata的源码、测试和资格脚本，
以及三个reference application、million-row profile、package/SBOM/provenance、CI与non-publishing
release qualification workflow。Package只在本地完成资格且没有committed build artifact；远端workflow尚未验证，
也没有GitHub Release/Package、签名或正式release声明。

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
| I1 primitive keyed Table、runtime与qualification evidence | [I1 Primitive Keyed Table Qualification](conformance/i1-primitive-keyed-table-qualification.md) |
| I2 schema/type/storage/Key/Index breadth与qualification evidence | [I2 Schema、Type 与 Storage Breadth Qualification](conformance/i2-schema-type-storage-breadth-qualification.md) |
| I3 sequential query、IR、reference/optimized execution与qualification evidence | [I3 Query IR 与 Reference Execution Qualification](conformance/i3-query-ir-reference-qualification.md) |
| I4 Selection mutation、atomic publication、resource与Group accounting evidence | [I4 Selection Mutation 与 Resource Qualification](conformance/i4-selection-mutation-resource-qualification.md) |
| I5 GroupBy、binary Equality/Cross Join与G6 evidence | [I5 GroupBy 与 Relation Qualification](conformance/i5-group-relation-qualification.md) |
| I6 bounded parallel execution与G7 evidence | [I6 Bounded Parallel Execution Qualification](conformance/i6-parallel-execution-qualification.md) |
| I7 compression、metadata/explain与G8 evidence | [I7 Compression 与 Metadata Qualification](conformance/i7-compression-metadata-qualification.md) |
| I8 Examples、performance、package与G9/G10 evidence | [I8 Product Qualification](conformance/i8-product-qualification.md) |
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
| Engineering | `project/engineering/`；I0-I8 locally completed，当前无active implementation slice |
| Conformance | `project/conformance/`；G1-G9 PASS，G10 local PASS且remote qualification pending |
| Temporary | 当前无active topic；目录不拥有current事实 |
| Product Docs | 三个reference application已建立；Manual/White Paper不在本次implementation scope |
| Modules/Implementation Map/Process/Reports | production module与资格入口由root/project README和Conformance路由，不创建重复Owner |

Selected source delivery profile已经建立明确allowlist；package/source bundle不会把整个checkout
默认交付给library consumer。

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
        -> I1 primitive keyed Table vertical slice (COMPLETED)
            -> I2 schema/type/storage breadth (COMPLETED)
                -> I3 direct query/IR/reference interpreter (COMPLETED)
                    -> I4 selection mutation/resource/failure (COMPLETED)
                        -> I5 GroupBy/relation (COMPLETED)
                            -> I6 bounded parallel execution (COMPLETED)
                                -> I7 compression/metadata closure (COMPLETED)
                    -> one active slice at a time
                    -> matching Conformance evidence
                        -> stop or next slice
                            -> I8 product qualification (COMPLETED LOCALLY)
                                -> remote G10 qualification
                                    -> separate release authorization
```

I0-I8本地exit evidence已经闭合；当前只剩`develop`远端CI与non-publishing release qualification。
当前授权同时允许每个slice证据闭合后的
干净commit与`develop` push，以及
[Conformance authorization contract](conformance/README.md#6-implementation-authorization-contract)
限定的CI、internal benchmark/profile、local package qualification和JUnit Jupiter 5.x
test-only stack；不包含remote artifact publication、签名或正式发布声明。

## 当前验证边界

I0已执行Java 8 build/artifact/full-regeneration基线；I1建立第一套generated Table纵向闭环；I2已执行
完整schema/type/Key/Index breadth、paged primitive/reference storage、point mutation、failed-state、
独立consumer和百万行functional/scale journey；精确边界分别见
[I0 Qualification](conformance/i0-build-spine-qualification.md)、
[I1 Qualification](conformance/i1-primitive-keyed-table-qualification.md)、
[I2 Qualification](conformance/i2-schema-type-storage-breadth-qualification.md)与
[I3 Qualification](conformance/i3-query-ir-reference-qualification.md)与
[I4 Qualification](conformance/i4-selection-mutation-resource-qualification.md)与
[I5 Qualification](conformance/i5-group-relation-qualification.md)与
[I6 Qualification](conformance/i6-parallel-execution-qualification.md)与
[I7 Qualification](conformance/i7-compression-metadata-qualification.md)与
[I8 Qualification](conformance/i8-product-qualification.md)。G9同机threshold不是跨硬件SLA或
一亿行性能承诺；G10本地PASS也不替代远端workflow或独立release authorization。
