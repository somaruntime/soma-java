![SOMA Java](assets/soma-banner.png)

# SOMA Java

SOMA Java 正在同一个产品和仓库身份下从干净起点重新定义。正式 V1 Blueprint、
Design、Engineering plan 和 Conformance baseline 已经建立，实施准备审查结论为
`READY_FOR_IMPLEMENTATION`；旧实现、旧模块和旧公开 API 不再约束新产品。

当前仍没有 production SOMA library、可用 consumer API、Maven artifact、Example、
性能承诺或发布版本。仓库根目录不提供 `pom.xml`。正式设计说明系统应当是什么，
实施准备说明可以开始做什么；二者都不等于 implementation、qualification 或 release
已经完成。

## 目标方向

SOMA 面向 Java application 中大规模、频繁变化的进程内状态。Application 使用
普通 Java 对象、注解和 generated typed API 表达业务语义；SOMA 在内部通过
编译生成、列式存储和专门化执行获得性能。

SOMA 借鉴 Java Stream 的 pipeline 心智，但不是 Stream replacement：它额外拥有
Table state、Key/Index、data-oriented storage 和受控 mutation；小集合、一次性对象
转换、数据库查询或跨 Table transaction 仍应使用更合适的 Java/application 工具。

V1 产品模型是：

```text
用户语义层
    -> 编译生成层
        -> 存储层
            -> 执行层
```

用户操作从一个 Table、record selection 或 logical Field source 开始，经过零个或
多个中间操作，由 Query、Update 或 Remove terminal 结束。physical Column、
scratch、plan 和 scheduler 不进入普通用户 API。

1:M 与 N:M 通过普通 Table、endpoint ID 和 Index 表达。SOMA 不引入 ChildTable、
Table ownership graph、隐式 cascade 或 cross-Table transaction。

## 当前入口

- [项目状态与事实边界](project/README.md)
- [SOMA Java V1 产品蓝图](project/blueprint/README.md)
- [正式 Design 总览](project/design/README.md)
- [Production Implementation Plan](project/engineering/v1-implementation-plan.md)
- [Implementation Readiness Review](project/conformance/v1-implementation-readiness-review.md)
- [当前 Conformance 与证据边界](project/conformance/README.md)
- [品牌资产](assets/README.md)
- [安全报告方式](SECURITY.md)

产品使用文档、Quick Start 和可执行 Examples 将在 production surface 真正建立并
通过 Conformance Gate 后提供；现在不会用伪示例制造“已经可用”的印象。

## 历史边界

重启前的 SOMA Java predecessor 已固定在 Git ref
`archive/pre-product-reset-2026-07-31`，对应 commit
`b69477432b44c4bc75c5f62fff741a729a33def2`。该 ref 仅用于历史追溯，不是 release、
support 或 qualification 声明。active checkout 不保留 legacy 目录、兼容层或两套
canonical API。

## 产品身份

- 产品品牌：SOMA；
- 仓库：`somaruntime/soma-java`；
- 发布主体与维护者：ArthurFeng；
- Java package / Maven group 基线：`io.github.somaruntime.soma`；
- 语言方向：Java 8；
- 许可证：[Apache License 2.0](LICENSE)。

品牌资产权利边界见 [NOTICE](NOTICE) 和 [assets/README](assets/README.md)。
