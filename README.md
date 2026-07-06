# soma_java

`soma_java` 是 SOMA Java-only 方向的原型项目。当前目标是先建立 Java annotation schema + Java columnar runtime 的设计和工程骨架，不写实现代码。

## 当前状态

本仓库目前只初始化项目骨架、Maven reactor、文档目录和 Git。后续实现前应先完成正式设计文档。

## 模块

| Module | 责任 |
|---|---|
| `soma-annotations` | schema annotation API |
| `soma-processor` | annotation processor、schema validation、normalized model、schema hash、codegen |
| `soma-runtime-core` | Java columnar runtime kernel |
| `soma-testkit` | compile/golden/runtime invariant test helpers |
| `soma-examples` | Java 8 examples and E2E smoke scenarios |
| `soma-benchmarks` | benchmark scenarios and evidence output |

## 非目标

- 不实现 native runtime；
- 不实现 C ABI / FFI；
- 不承诺 Python binding；
- 不把 Java DTO 对象图作为 runtime storage；
- 不在设计收口前添加第三方依赖。
