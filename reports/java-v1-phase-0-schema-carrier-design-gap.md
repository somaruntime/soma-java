# Java V1 Phase 0 schema carrier construction 设计缺口

状态：resolved
记录日期：2026-07-10
记录人：Codex
输入 commit：`b3b2f05`
性质：实施前 public-contract hard stop；本报告是审查证据，不是设计事实源
项目所有者决策：2026-07-10 同意方案 A

## 1. 结论

Phase 0 审查发现：正式设计尚未唯一确定 generated materializer 如何构造位于 schema package 的 `@SomaTable` detached row instance。该选择会改变 schema source 约束、compiler lowering、generated package 访问方式和 public compatibility，不能由实现代码私自决定。

项目所有者已选择方案 A。本次 closeout 先同步 annotation、materialization、compiler、codegen、processing、compatibility 与 testkit 唯一正式 Owner；Owner validation 通过后解除相关 implementation hard stop。

## 2. 涉及 Capability 与 Owner

| Capability | 唯一正式 Owner | 影响 |
|---|---|---|
| `V1-ANNOTATION-SCHEMA` | `soma-annotations/docs/annotation-schema-contract.md` | schema class、field 与 constructor 的合法 source shape |
| `V1-COMPILER-LOWERING` | `soma-processor/docs/compiler-integration-contract.md` | 是否把 lowering 从 `@SomaValue` 扩展到 `@SomaTable` |
| `V1-GENERATED-API` | `soma-processor/docs/code-generation-contract.md` | materializer 的构造与 field 写入方式 |
| `V1-MATERIALIZATION` | `docs/materialization-contract.md` | detached schema object 的构造边界 |
| `V1-PUBLIC-COMPATIBILITY` | `docs/public-api-compatibility-contract.md` | constructor、field visibility、generated package 和 consumer migration |

## 3. 当前正式事实

- Annotation schema 要求 `@SomaTable` class 是 detached single-row materialization carrier，并说它必须保持 processor 可构造；但把具体 no-arg/canonical construction lowering 交给 codegen contract 与 golden，未固定访问规则。
- Compiler integration 只定义 `@SomaValue` 的 parse-phase lowering，没有定义 `@SomaTable` constructor/field lowering。
- Code generation 要求 materializer 构造 caller-owned schema class instance，但未定义 generated package 如何调用 constructor 和写入 schema fields。
- 正式 FJSP、VRP、Simulation、Game 示例使用不同的 generated package；其中 `@SomaTable` classes 是 `public final`、schema fields 是 `public`、且没有显式 constructor。Java 因此提供 public no-arg constructor，但示例不能独自建立完整 public contract。
- Materialization Owner 把 table row 称为 detached `read-only data carrier`，而 annotation/codegen Owner 明确允许 caller 修改 detached schema object、且修改不 write-back。这里需要统一为不会暗示 immutable carrier 的单一表述。

## 4. 方案

### 方案 A：显式 public carrier contract，推荐

- `@SomaTable` 必须是 public、可实例化的 detached carrier；
- schema fields 必须是 public mutable fields；
- class 必须保留 public no-arg constructor，Java 隐式 public no-arg 可接受；
- 用户可以添加其他 constructor，但不得移除 public no-arg construction path；
- materialized row 是 caller-owned、可修改的 detached carrier；修改不自动 write-back，不等于 runtime mutable row；
- processor 对 inaccessible class/field/constructor fail closed；
- generated materializer 在 configured generated package 中直接 `new` 并赋值；
- javac plugin 继续只 lower `@SomaValue`。

优点：与四类正式示例一致；不扩大 compiler-internal lowering；generated source 形状直接、可审查、无 reflection；不会引入额外 schema-package companion。

代价：schema author 必须接受 public mutable detached carrier shape；constructor/field 可见性成为 public schema contract。

### 方案 B：transformer 为 `@SomaTable` 合成 construction shape

- source 可以省略 public field/no-arg 要求；
- javac plugin 在 Enter 前为 table class 合成或调整 constructor/field visibility；
- processor 与 generated materializer 消费 lowered table effective type。

优点：schema source 更简洁，SOMA 可以控制 construction shape。

代价：扩大 compiler-specific surface 和 compatibility identity；table materialization 也强绑定 javac lowering；冲突、IDE model 和 golden 矩阵显著增加。

### 方案 C：在 schema package 生成受控 access companion

- schema class 保持 package-private construction/member access；
- processor 在 schema package 生成 materialization access companion，再由 configured generated package 调用。

优点：不需要 transformer 修改 table class。

代价：generated artifact 跨两个 package；增加命名冲突、public/internal surface、source injection 和 package ownership复杂度；仍不能处理 private member，整体收益最低。

## 5. 推荐

采用方案 A。它最符合现有正式示例和“schema-specific generated facade + small runtime kernel”，保持 compiler plugin 专注 `@SomaValue` effective immutability，并给 materializer 一个无需 reflection、无需额外 bridge 的确定 construction path。

正式 Owner 已固定：class/field/constructor accessibility、implicit no-arg 合法、额外 constructor 的保留条件、detached copy 可修改但不 write-back、field initializer 不构成 schema default，以及对应 fail-closed diagnostics/golden obligation。

## 6. V1 scope non-regression

- Capability 状态：上述 Capability 均保持 `not-started`；本报告与 Owner 更新只解除设计阻塞，不构成 implementation evidence。
- G0：保持 `passed`；G1-G6 保持 `not-started`。
- 未实现 breadth：23 项 V1 capability 全部仍保留原 Phase/Gate 和完整出口。
- Owner：已按项目所有者决定更新各自唯一正式 Owner；Gate/Ledger/release claim 未改变。
- 临时 contract/hot path/migration：未引入。
- 后续要求：相关实现必须直接遵守已选定 construction contract，并通过 additive completion 收敛；不允许引入 alternative bridge/lowering temporary path。
