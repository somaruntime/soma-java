# 旧体系到候选体系迁移映射

类型：Temporary

状态：第一版，待逐条事实复核

Owner：SOMA Java 文档体系重构专题

事实范围：现有 Owner 到候选 Owner 的覆盖关系、迁移状态和待决事项

核对基线：`4b6fa43`

最后审查日期：2026-07-19

## 1. 状态

- **covered**：候选 Owner 已覆盖主要长期事实，仍需最终逐条核对；
- **split**：旧文件混合多类事实，候选体系已按职责拆分；
- **partial**：只提炼了稳定部分，未决细节仍在原文/Temporary；
- **retain-report**：保持 evidence/history，不迁入 Design；
- **pending**：切换前仍需 Owner 决定。

## 2. 根级与 module Design

| 当前 Owner | 候选 Owner | 状态 | 说明 |
|---|---|---|---|
| `docs/soma-table-design-constitution.md` | Design constitution、table/access、ownership | split | 总原则与机制不变量分开 |
| `docs/architecture-design.md` | System architecture | covered | module/data-flow/public boundary |
| `docs/domain-glossary.md` | Domain language | covered | 压缩术语，行为仍链接对应 Owner |
| `soma-annotations/.../annotation-schema-contract.md` | Schema & generated API | covered | annotation 长期语义 |
| processor compiler/schema/codegen contracts | Schema & generated API + compiler map | split | 规范性规则进 Design，类/fixture 进 Map |
| `docs/generated-table-api-contract.md` | Schema & generated API、table/access、ownership、materialization | split | 按语义 Owner 拆分 |
| `docs/materialization-contract.md` | Materialization boundary | covered | detached/budget/all-or-nothing |
| `docs/runtime-correctness-model.md` | Correctness & failure、ownership | covered | single-aggregate correctness |
| `docs/runtime-performance-model.md` | Performance model | covered | claim/evidence 过程另入 Engineering |
| `docs/public-api-compatibility-contract.md` | Compatibility/security/versioning | covered | surface matrix 与 identity |
| `docs/security-model.md` | Compatibility/security/versioning + Engineering release/testing | split | 永久边界和扫描过程分开 |
| runtime-core TableStore contract | Table/storage/access + runtime map | split | semantics 与 class/protocol projection 分开 |
| runtime-core lifecycle contract | Ownership/lifecycle + correctness | covered | transition细节需最终逐条核对 |
| runtime-core runtime plan contract | Performance + compatibility + runtime map | split | plan semantics 与实现入口分开 |
| runtime-core errors/diagnostics contract | Correctness/failure | covered | stats detailed surface需最终核对 |
| runtime-core performance implementation contract | Performance + runtime map | split | 永久 mechanics 与当前 protocol 分开 |

## 3. Engineering、evidence 与输出

| 当前 Owner | 候选 Owner | 状态 | 说明 |
|---|---|---|---|
| `docs/documentation-governance.md` | Engineering documentation governance | covered | 采用 rc.2 的轻量闭环 |
| `docs/build-and-dependency-contract.md` | System architecture + Engineering build | split | dependency direction 与过程分开 |
| `docs/implementation-strategy.md` | Engineering build/testing + Conformance + historical report | pending | 实施顺序不是长期产品设计；capability状态需保留可追踪性 |
| `docs/validation-gates.md` | Engineering build/testing/benchmark/release | split | Gate 语义与结果分开 |
| `docs/versioning-and-release-contract.md` | Compatibility/versioning + Engineering release | split | identity 与 release过程分开 |
| testkit contract | Engineering testing + evidence map | split | helper contract 的长期 public面需最终判断 |
| benchmark contracts | Performance Design + Benchmark Engineering/Map | split | 方法、设计成本与实现入口分开 |
| `guides/` | Reports/user | partial | 已有安装细节保留，候选用户输出需完整对照 |
| current Gate/implementation reports | Reports/governance/release/history | retain-report | 不升级为 Design，不丢失 checksum/环境/结论 |
| archived reports | read-only history index | pending | 决定保留原路径还是迁移历史区 |

## 4. Blueprint 与场景

| 当前材料 | 候选 Owner | 状态 | 说明 |
|---|---|---|---|
| FJSP long-lived temp blueprint + formal example | FJSP Blueprint + scenario map | partial | 稳定目标已提炼，研究待验证项仍需核对 |
| VRP long-lived temp blueprint + formal example | VRP Blueprint + Conformance gaps | partial | 目标 data-role split 尚未实现 |
| Simulation long-lived temp blueprint + formal example | Simulation Blueprint + Conformance gaps | partial | numeric source-of-truth 仍有代码差距 |
| Game long-lived temp blueprint + formal example | Game Blueprint + Conformance gaps | partial | tile/occupancy split 尚未实现 |
| packed-exact post-cutover tails | future Temporary/Conformance/Report 由 Owner 裁决 | pending | 本专题不修改用户现有材料 |

## 5. 切换前必须关闭

- 对每份旧正式 Owner 建立事实级 checklist，而不只是文件级映射；
- 决定 implementation strategy/capability ledger 的长期 Owner；
- 核对 generated API、stats/error code、runtime plan 等详细 contract 没有被过度压缩丢失；
- 决定 module-level Design 是否保留独立文件或由根级 Design 完全拥有；
- 设计旧 report/history 的稳定链接与 current-index 规则；
- 处理当前 `docs/temp` 研究蓝图与 user-owned tails；
- 更新并 rehearsal 文档检查脚本；
- 确认切换后不存在旧/新双重 Owner。

本映射是迁移工具，不是正式 Owner，也不意味着上述 pending 已获实施授权。
