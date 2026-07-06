# soma-examples 正式设计文档

本目录保存 `soma-examples` 的正式设计文档。

## 当前正式设计文档

- [Runtime state schema 典型示例](runtime-state-schema-examples.md)
- [FJSP E2E 场景契约](fjsp-e2e-scenario.md)

## 职责边界

`soma-examples` 拥有 Java 8 usage examples 和 end-to-end smoke scenarios。示例文档可以覆盖、验证并解释 annotation schema、generated API 和 runtime core 的使用方式，但不拥有这些契约本身；如有冲突，以对应 owner 文档为准。

## 临时设计目录

临时设计草案只能放在 `docs/temp/`。草案被接受后，必须把稳定事实迁移进正式设计文档。
