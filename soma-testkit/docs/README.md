# soma-testkit 正式设计文档

本目录保存 `soma-testkit` 的正式设计文档。

## 当前正式设计文档

当前尚无正式契约文档。

## 待决策设计缺口

本次治理只确认缺口，不替代设计：

- compile test helper 的输入、输出、诊断断言和 Java 8 toolchain 约束尚未形成正式契约；
- golden output helper 的比较口径、允许忽略的非语义 whitespace、文件组织和更新流程尚未形成正式契约；
- runtime invariant helper 的覆盖边界、与 `soma-runtime-core` 单元测试的分工尚未形成正式契约。

这些缺口会影响 G1/G2/G3 的可审计性，但不阻塞当前文档归位治理。

## 临时设计目录

临时设计草案只能放在 `docs/temp/`。草案被接受后，必须把稳定事实迁移进本目录正式设计文档。
