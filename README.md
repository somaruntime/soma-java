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
- Product Owner 已于 2026-08-03 授权按I0-I8与G1-G10自主实施；I0 build/generation spine已经
  完成并通过正式资格，当前没有active slice，下一项从I1开始；
- active checkout已有Java 8 Maven reactor、`soma-runtime`与`soma-processor`两项production
  artifact源码、processor/full-regeneration baseline与资格测试；
- 尚没有I1+公开Table API/runtime、Example、benchmark、CI/release workflow或remote package；
- G1为`PASS`；G2/G10仅I0范围`PASS`且整体仍`IN_PROGRESS`，因此没有完整可用性、性能、
  兼容性或release readiness声明。

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
- [当前 Conformance 与 G1-G10](project/conformance/README.md)
- [品牌资产](assets/README.md)
- [安全报告方式](SECURITY.md)

产品使用手册、Quick Start 与可执行 Examples 将在 production surface 真正建立并通过相应
Conformance Gate 后提供；当前不使用伪示例制造“已经可用”的印象。

## 当前构建入口

I0 canonical qualification要求Java 8与Maven 3.9.x：

```sh
./scripts/check.sh
```

该命令验证两个artifact、processor、full regeneration、independent consumer、linkage negative
与package baseline；它不是完整V1使用或性能资格入口。精确环境、结果与claim boundary见
[I0资格记录](project/conformance/i0-build-spine-qualification.md)。

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
