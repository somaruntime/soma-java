# soma_java Agent Guide

`soma_java` 是 Java 8 annotation schema + Java columnar runtime 项目。当前功能与性能状态以 Conformance 和 Report 为准；G6 未通过前不得声明 public release readiness。

## 必读入口

- 文档权威关系与全部正式入口：[docs/README.md](docs/README.md)；
- 目标形态：[Blueprint](docs/blueprints/README.md)；
- 长期规范性语义：[Design](docs/design/README.md)；
- 当前代码导航：[Implementation Map](docs/implementation-map/README.md)；
- 已知偏差：[Conformance](docs/conformance/README.md)；
- 构建、测试、benchmark、release 与文档过程：[Engineering](docs/engineering/README.md)；
- 当前 Gate、性能和治理结论：[reports/README.md](reports/README.md)。

README、AGENTS、Guide、Report、Implementation Map 和模块历史文档都不重新定义 Design。代码、配置和可执行产物拥有当前实现事实，但当前实现不因此天然正确。

## Project Boundary

- 只承载 Java 8 使用场景；
- 不承诺 Python、C ABI、native runtime 或跨语言 FFI；
- 不在正式 Design 决策前增加第三方依赖；
- 实现不得把 schema object、DTO、Java Collection graph 或 metadata interpreter 变成 runtime hot storage/path。

完整边界以 [SOMA Java 设计宪法](docs/design/soma-java-design-constitution.md)和[系统架构](docs/design/system-architecture.md)为准。

## Design-driven Scope Preservation

- 实现必须服务于 Design，Design 必须服务于 Blueprint；当前代码不得反向降低目标；
- 变更前先读取相关 Blueprint/Design，再通过 Implementation Map、代码和测试确认当前事实；
- 当前 slice 必须是最终设计的有效子集，不得依赖未来 public consumer、核心事实或 canonical hot-path migration 才成立；
- 不得用 temporary public/generated API、`List<Row>`/DTO live storage、reflection/metadata interpreter、Java Stream hot path、share/reparent child、generic error 或 test-only bypass 代替正式语义；
- 未实现或不一致的目标进入 Conformance，并由相关 Owner 裁决；不维护用 future/MVP/optional 掩盖差距的平行 roadmap；
- 修改 Blueprint、Design、public/schema/runtime semantics、最终 Gate 或 release claim 前必须停止并请求用户明确决定；重大长期设计先在 `docs/temp/<topic>/` 形成独立 Temporary；
- Conformance 结论本身不扩大实施授权；
- closeout 必须包含 scope non-regression：受影响 Design、实际 evidence、仍存在的偏差、Owner/Gate变化，以及结果是否只需 additive completion/internal refinement。

## Module Implementation Ownership

- `soma-annotations`：public schema annotation；
- `soma-processor`：javac 8 integration、processing、normalization/hash、diagnostics、code generation；
- `soma-runtime-core`：TableStore、lifecycle、runtime plan、errors/diagnostics、runtime性能实现；
- `soma-testkit`：compile/golden/invariant/evidence helpers；
- `soma-examples`：current executable Java 8 scenarios；
- `soma-benchmarks`：benchmark runner、validator 与 runtime-state lanes。

跨模块长期语义由根级 Design 拥有；模块 `docs/` 中的旧契约已 `superseded`，只保留历史上下文。模块修改前仍应读取对应 `<module>/docs/README.md` 以定位当前实现与历史边界。

## Release Identity and Validation

- 组织与发布主体为 HGTECH，产品品牌为 SOMA，Maven `groupId` / Java package root 为 `com.hgtech.soma`，artifact 名保持 `soma-*`；
- HGTECH 只进入真实组织、SCM、POM、publishing 和 provenance 边界，不成为 SOMA annotation、generated API、runtime type、error 或 schema 概念前缀；
- release identity 与安全边界以[兼容性、安全与版本](docs/design/compatibility-security-and-versioning.md)为准；过程以[Release 治理](docs/engineering/release-governance.md)和[Validation Gate 治理](docs/engineering/validation-gates.md)为准；
- full JDK 8 javac 是当前 compiler authority；新 JDK `--release 8` 不能冒充受支持 transformer；
- 每次 validation 记录实际 JDK vendor/version/build、Maven、OS、architecture 和命令；本机通过不得外推为支持矩阵；
- G6 support matrix、SCM/contact、signing/publishing 等真实事实不足时保持 `blocked`，不得用 placeholder 或单机 smoke 替代。

## Documentation Workflow

- 文档、报告和代码注释默认使用中文；
- Blueprint、Design、Implementation Map、Conformance 和 Engineering 位于 `docs/` 对应分类；
- 用户/开发者输出进入 `guides/`，性能、治理、Gate 和 release evidence进入 `reports/`；
- `soma-examples/docs/` 是 current-executable developer Report，不拥有 core Design；
- Temporary 只在 active topic 期间存在，稳定事实原子固化后删除，不归档；
- 文档规则以[文档治理](docs/engineering/documentation-governance.md)为准。

## Validation and Git

- 修改后运行 `./scripts/check.sh`；窄反馈至少运行 `./scripts/check-docs.sh`、`git diff --check` 和与 surface 相称的验证；
- 使用 `rg` / `rg --files` 搜索，使用 `apply_patch` 编辑；
- 保留用户现有未提交修改，不覆盖无关内容；
- Java 保持 Java 8；
- 长期分支只使用 `main`、`develop`、`release`，常规工作在 `develop`；
- 未经用户要求不创建其他长期分支，不执行 destructive Git 操作。
