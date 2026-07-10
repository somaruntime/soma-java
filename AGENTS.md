# soma_java Agent Guide

`soma_java` 是 Java 8 annotation schema + Java columnar runtime 原型仓库。当前仍处于设计阶段。

## 必读入口

- 所有正式设计事实从 [docs/README.md](docs/README.md) 进入；
- 文档分类、Owner 和生命周期遵守 [docs/documentation-governance.md](docs/documentation-governance.md)；
- 模块内部修改前阅读对应 `<module>/docs/README.md`；
- README、AGENTS、guides、reports 和 `docs/temp/` 都不是设计事实源。

## Project Boundary

- 只承载 Java 8 使用场景；
- 不承诺 Python、C ABI、native runtime 或跨语言 FFI；
- 不在正式设计决策前增加第三方依赖；
- 实现不得把 schema object、DTO、Java Collection graph 或 metadata interpreter 变成 runtime hot storage/path。

以上是 Agent 操作护栏；完整语义仍以正式 owner 文档为准。

## Module Ownership

- `soma-annotations`：public schema annotation；
- `soma-processor`：javac 8 integration、processing、normalization/hash、diagnostics、code generation；
- `soma-runtime-core`：TableStore、lifecycle、runtime plan、errors/diagnostics、runtime 性能实现；
- `soma-testkit`：compile/golden/invariant/evidence helpers；
- `soma-examples`：formal Java 8 scenarios 和 Access Pattern Cards；
- `soma-benchmarks`：benchmark evidence 与 runtime-state lanes。

跨模块 public semantics 由根级正式契约拥有；模块只能拥有自己的实现义务，不使用联合 Owner。

## Documentation Workflow

- 文档、报告和代码注释默认使用中文；
- 正式设计进入 `docs/` 或 `<module>/docs/`；
- 普通临时设计在固化后迁入 Owner 并删除；
- 四份长期研究蓝图保留在 `docs/temp/`，但不能被实现/gate 当作事实源；
- 用户/开发者指南未来进入 `guides/`，不进入 `reports/`；
- 报告只记录审查、验证、benchmark 和 release evidence；
- 修改后运行 `./scripts/check.sh`；需要缩小验证时，至少运行 `./scripts/check-docs.sh`、`git diff --check` 和与变更 surface 相称的 Maven validation。

## File and Git Rules

- 使用 `rg` / `rg --files` 搜索；
- 保留用户现有未提交修改，不覆盖无关内容；
- 本地文件编辑使用 `apply_patch`；
- Java 保持 Java 8；
- V1 compiler authority 是正式契约中的 full JDK 8 javac；不能用新 JDK 的 `--release 8` 冒充 supported transformer；
- 长期分支只使用 `main`、`develop`、`release`；常规工作在 `develop`；
- 未经用户要求不创建其他长期分支，不执行 destructive Git 操作。
