# soma_java Agent Guide

`soma_java` 是 Schema-Defined、Compiler-Specialized、JVM Heap-Resident 的
Java 8 columnar runtime-state computing 项目。当前功能与性能状态以
Conformance 和 Report 为准；当前G5/G6因Corretto规模与Linux/release evidence
待补而blocked，不得沿用历史Zulu结论或外推为public、Maven或production
readiness。

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
- `soma-dataflow`：typed Transformation、Definition/Template/Invocation、
  execution context、detached result、controlled effect 与并行执行；
- `soma-examples`：三个独立 Java 8 reference consumer 的聚合边界，不产出领域共享 JAR；
- `soma-benchmarks`：领域中性 component benchmark、runner、validator 与 runtime-state lanes。

仓库级 compile/golden/external-consumer fixture 位于 `tests/fixtures/`，它不是
Maven module，不产出 artifact，也不得成为 production 或 reference application
依赖。

跨模块长期语义由根级 Design 拥有；历史模块契约由 Git 保存，不在 current
checkout 建立平行 Owner。模块修改前仍应读取对应 `<module>/docs/README.md`
以定位当前实现与边界。

## Repository Surface Discipline

- 新增 production type、接口、module、测试、benchmark lane、脚本或正式文档前，
  必须说明独立 Capability/语义/失败域/evidence、consumer、生命周期，以及为何
  当前 Owner 不能承载；
- replacement 必须在同一变更中列出并退出 predecessor、旧入口和 migration-only
  checker；不保留“新旧并行，稍后清理”；
- 测试和 benchmark 按 Capability、cross-capability journey、external consumer、
  reference application、qualification 组织，不以 phase/slice/治理批次作为
  canonical taxonomy；
- current Map/Report 只描述当前事实；已完成迁移和旧 checkpoint 由 Git 保存，
  不累计 tombstone、旧 token blacklist 或历史路径枚举；
- 不以 LOC、文件数、单实现、单调用者或浅层 unused scan 删除抽象；必须追踪
  Design intent、完整调用链、不变量与 replacement closure；
- 专题 closeout 报告 production/public/test/fixture/benchmark/script/doc surface
  delta，并确认 parallel Owner、migration artifact、未退役专题目录和未裁决
  `UNKNOWN` 为零。

## Release Identity and Validation

- copyright owner 与发布主体为 ArthurFeng，产品品牌为 SOMA，GitHub Organization
  为 `somaruntime`；
- Maven `groupId` / Java package root 为 `io.github.somaruntime.soma`，artifact
  名保持 `soma-*`；
- 当前选择的发布渠道是 private GitHub source repository
  `somaruntime/soma-java`；public repository 与 Maven Central 未选择、未声明；
- 个人或组织 identity 只进入真实 copyright、SCM、POM、support 和 provenance
  边界，不成为 SOMA annotation、generated API、runtime type、error 或 schema
  概念前缀；
- release identity 与安全边界以[兼容性、安全与版本](docs/design/compatibility-security-and-versioning.md)为准；过程以[Release 治理](docs/engineering/release-governance.md)和[Validation Gate 治理](docs/engineering/validation-gates.md)为准；
- Amazon Corretto 8.502.07.1 full JDK 8 javac/runtime 是当前唯一 compiler 与 validation authority；新 JDK `--release 8` 不能冒充受支持 transformer；
- 每次 validation 记录实际 JDK vendor/version/build、Maven、OS、architecture 和命令；本机通过不得外推为支持矩阵；
- Zulu 或其他 JDK distribution 不属于当前验真或目标支持范围，不要求新增、补跑或维持多 vendor Gate；历史运行记录不构成当前支持声明；
- G6 必须按实际选择的 release profile 判断；private-source readiness、
  Codex Cloud development readiness 与 public/Maven readiness 分开，任何缺失的
  SCM/contact/support matrix/provenance 事实不得用 placeholder 或单机 smoke 替代。

## Documentation Workflow

- 文档、报告和代码注释默认使用中文；
- Blueprint、Design、Implementation Map、Conformance 和 Engineering 位于 `docs/` 对应分类；
- 用户/开发者输出进入 `guides/`，性能、治理、Gate 和 release evidence进入 `reports/`；
- `soma-examples/docs/` 是 reference application developer Report 入口；child application docs 各自拥有领域事实，但不拥有 core Design；
- Temporary 只在 active topic 期间存在，稳定事实原子固化后删除，不归档；
- 文档规则以[文档治理](docs/engineering/documentation-governance.md)为准。

## Validation and Git

- 日常反馈运行 `./scripts/check.sh fast` 和与变更 surface 相称的直接 Gate；
  Markdown-only 只要求 `./scripts/check-docs.sh`、`git diff --check` 与链接/Owner
  自审；跨模块、专题收口或明确要求时才运行一次默认的完整 `./scripts/check.sh`；
- Full Gate 的独立功能检查默认最大并行度为4，并自动受可用处理器数约束；
  performance、package、security和qualification保持串行；
- 重复高成本动作前必须说明输入、假设或目标发生了什么变化以及将获得什么新
  evidence；无变化时禁止重跑、固定间隔sleep或盲目轮询，已通过且输入未变化的
  evidence直接复用；
- 使用 `rg` / `rg --files` 搜索，使用 `apply_patch` 编辑；
- 保留用户现有未提交修改，不覆盖无关内容；
- Java 保持 Java 8；
- 长期分支只使用 `main`、`develop`、`release`，常规工作在 `develop`；
- 未经用户要求不创建其他长期分支，不执行 destructive Git 操作。
