![SOMA Java](assets/soma-banner.png)

# SOMA Java

SOMA 是嵌入 Java application、运行在单 JVM 进程内、面向 schema-known mutable Tables 的
编译式列式计算引擎。Application 使用自然 Java object、generated typed API 与 Stream-like
operation 表达 point、scan、aggregate、GroupBy 和 binary relation；SOMA 通过编译生成、
long-domain chunked storage、typed IR 与专门化执行获得可预测的性能与资源边界。

长期 North Star 是“一亿行以上、编译式、支持关系计算的单进程 Table 引擎”。一亿行是
架构不得封死的愿景，不是当前性能承诺；第一阶段以百万行数据上的高效、低分配、资源受控
操作建立资格证据。

## 当前状态

SOMA Java 已完成clean-slate V1产品设计、正式晋升与实施前最终全局一致性审核：

- Blueprint、九个分责Design Owner、I0-I8 Plan与G1-G10 Gate已生效；
- readiness 结论为 `READY_FOR_IMPLEMENTATION`；
- Product Owner 已于 2026-08-03 授权按I0-I8与G1-G10自主实施；I0-I8已经完成本地
  implementation与qualification，当前没有active implementation slice；
- active checkout已有Java 8 Maven reactor、`soma-runtime`与`soma-processor`两项production
  artifact源码、processor/full-regeneration baseline、I2 generated schema/type/Key/Index API、paged
  primitive/reference PLAIN storage、Value flattening、direct selection、point add/update/remove、
  sequential query、typed IR、reference/optimized sequential execution、Selection mutation、atomic
  StateRoot/Group accounting、GroupBy、binary Equality/Cross Join、bounded parallel execution、
  AUTO/OFF compression、四级metadata、structured failure与资格测试，以及三个reference
  application、million-row profile、package/SBOM/provenance、CI与non-publishing release workflow；
- G1-G9为`PASS`；G10为`LOCAL_PASS / REMOTE_QUALIFICATION_PENDING`；本地package已完成资格但
  未远端发布，因此没有GitHub Release/Package、签名或正式release声明。

正式设计说明产品应当是什么；它不等于代码和证据已经存在。

## 产品模型

```text
Object-oriented application boundary
    -> compiler-generated typed surface
        -> logical IR and semantics-preserving optimizer
            -> specialized execution over data-oriented state
```

SOMA 借鉴 Java Stream 的 source/intermediate/terminal、lazy、one-shot、熟悉命名、sequential
default 与 explicit parallel，但不是 Stream replacement。它额外拥有 Table identity、Key/
Index、mutable authoritative state、relation planning、atomic publication 与 structured failure。

普通用户不需要操作 physical Column、Chunk、row position、scratch、planner、worker 或
storage backend。1:M/N:M 使用普通 Table、endpoint ID 与 Index 表达；SOMA 不提供 ChildTable、
cross-Table transaction、persistence 或 distributed execution。

## 正式入口

- [项目状态与事实边界](project/README.md)
- [SOMA Java V1 产品蓝图](project/blueprint/README.md)
- [正式 Design 总览](project/design/README.md)
- [核心抽象、叙事与不变量证明链](project/design/core-abstractions-and-narratives.md)
- [V1 Production Implementation Plan](project/engineering/v1-implementation-plan.md)
- [实施前最终全局一致性审核](project/conformance/v1-final-pre-implementation-global-consistency-review.md)
- [I0 Build Spine Qualification](project/conformance/i0-build-spine-qualification.md)
- [I1 Primitive Keyed Table Qualification](project/conformance/i1-primitive-keyed-table-qualification.md)
- [I2 Schema、Type 与 Storage Breadth Qualification](project/conformance/i2-schema-type-storage-breadth-qualification.md)
- [I3 Query IR 与 Reference Execution Qualification](project/conformance/i3-query-ir-reference-qualification.md)
- [I4 Selection Mutation 与 Resource Qualification](project/conformance/i4-selection-mutation-resource-qualification.md)
- [I5 GroupBy 与 Relation Qualification](project/conformance/i5-group-relation-qualification.md)
- [I6 Bounded Parallel Execution Qualification](project/conformance/i6-parallel-execution-qualification.md)
- [I7 Compression 与 Metadata Qualification](project/conformance/i7-compression-metadata-qualification.md)
- [I8 Product Qualification](project/conformance/i8-product-qualification.md)
- [当前 Conformance 与 G1-G10](project/conformance/README.md)
- [品牌资产](assets/README.md)
- [安全报告方式](SECURITY.md)

三个可执行reference application位于[`soma-examples`](soma-examples/README.md)，按独立下游项目
构建并展示正常SOMA使用路径；它们是角色投影，不是第二份Design。

## 当前构建入口

当前I8 canonical qualification要求Java 8与Maven 3.9.x：

```sh
./scripts/check.sh
```

该命令回归I0-I8，并验证三个Examples、million-row profile、package/SBOM/checksum/provenance、
independent packaged consumer与non-publishing release boundary。精确环境、结果、approved threshold
与claim boundary见[I8资格记录](project/conformance/i8-product-qualification.md)。

## Clean-slate 与产品身份

重启前的 predecessor 固定在 Git ref `archive/pre-product-reset-2026-07-31`，仅用于历史追溯，
不拥有当前 API/implementation。Active checkout 不保留 legacy module、compatibility layer 或
两套 canonical API。

- 产品品牌：SOMA；
- 仓库：`somaruntime/soma-java`；
- 发布主体与维护者：ArthurFeng；
- Java package / Maven group baseline：`io.github.somaruntime.soma`；
- 语言方向：Java 8；
- 许可证：[Apache License 2.0](LICENSE)。

品牌资产权利边界见 [NOTICE](NOTICE) 和 [assets/README](assets/README.md)。
