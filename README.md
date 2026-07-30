![SOMA Java](assets/soma-banner.png)

# SOMA Java

SOMA Java 正在从一个干净起点重新定义。产品身份、仓库和 Java 8 方向保持不变，
旧实现、旧模块和旧公开 API 不再约束新产品。

当前阶段只有产品基础设计，没有可构建的 SOMA library、可用 API、发布版本、
兼容性承诺或性能声明。仓库暂时不提供 `pom.xml`，这是为了避免在产品模型闭合前
预设模块和 artifact 结构。

## 目标方向

SOMA 面向 Java application 中大规模、频繁变化的进程内状态。Application 使用
普通 Java 对象、注解和 generated typed API 表达业务语义；SOMA 在内部通过
编译生成、列式存储和专门化执行获得性能。

当前候选模型是：

```text
用户语义层
    -> 编译生成层
        -> 存储层
            -> 执行层
```

用户操作从一个 Table、record selection 或 logical Field source 开始，经过零个或
多个中间操作，由 Query、Update 或 Remove terminal 结束。physical Column、
scratch、plan 和 scheduler 不进入普通用户 API。

## 当前入口

- [项目状态与事实边界](project/README.md)
- [产品基础决策](project/temp/soma-product-foundation/README.md)
- [逻辑层 API 草稿](project/temp/soma-product-foundation/logical-api-draft.md)
- [品牌资产](assets/README.md)
- [安全报告方式](SECURITY.md)

上述两份产品文档仍是 Temporary，不是正式 Blueprint 或 Design，也不构成实施或
发布证明。

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
