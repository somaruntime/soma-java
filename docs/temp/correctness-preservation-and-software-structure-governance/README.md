# SOMA 正确性保持与软件结构治理

类型：Temporary

状态：active（Stage 0 complete）

Owner：SOMA correctness preservation and software structure governance

正式事实源：否

实施授权：仅 Temporary、Stage 0 只读审计、文档入口、验证和 immutable starting point

事实范围：专题意图、方法、范围、非回归约束、阶段、停止条件和验收协议

非事实范围：当前正式产品语义、已确认实现缺陷、已批准 production 优化和发布声明

起始仓库基线：`975a28b657028991272b04efe21e5584343a89a6`

最后审查日期：2026-07-27

## 1. 意图

SOMA 已形成从 Schema、processor/codegen、packed columnar runtime、Access Model 到
Transformation/DataFlow、parallel execution 和 safe-point Effect 的完整产品链。
本专题使用两份跨项目方法文档作为审查视角：

- `/Users/arthur/Documents/HGTECH/projects/software-engineering-key-concepts-and-principles.md`；
- `/Users/arthur/Documents/HGTECH/projects/invariant-driven-correctness-preservation.md`。

它们不是 SOMA 的规范性事实源，也不取代正式 Blueprint、Design、代码和 evidence。
本专题只把其中与 SOMA 有关的推理方式应用到当前事实：

```text
Design Intent
  -> Abstraction / Ownership / Boundary
  -> Same-level Narrative / State Transition
  -> Representation selected by Semantics + Access Pattern
  -> Invariant Owner and Proof Chain
  -> Evidence-backed Conformance / Optimization
```

治理目标不是按方法文档制造新层次、类型、测试或流程，而是判断 SOMA 的设计主张
是否由 production abstraction 构造性保证，代码是否保持设计的抽象与叙事，
evidence 是否证明这些防线有效。

## 2. 目标

本专题必须同时完成：

1. 审核产品到实现的核心抽象、责任、依赖方向和主叙事；
2. 为关键抽象建立有限的 `Invariant -> Owner -> Defense -> Failure -> Evidence`
   证明链；
3. 审核构造、发布、推导、决策、迁移、失败、失效和释放的完整生命周期；
4. 裁决 internal/unexpected failure 后 table/ownership aggregate 的可信状态；
5. 识别重复校验、平行事实、伪抽象、叙事断裂和无证据复杂性；
6. 只实施有证据支持、保持最终设计的内部优化；
7. 保持 public/generated/schema/runtime 语义、性能和 evidence 不缩水；
8. 最终完成正式事实原子固化、Conformance、Report、Temporary 退役和完整验证。

成功不以 LOC、文件数、删除测试数或新增 fault state 为前提。若审计证明某个设计
已经正确且证据充分，明确保留也是有效结论。

## 3. 审计模型

### 3.1 纵向抽象

```text
Product Boundary
  -> System / Module Architecture
  -> Capability Semantics
  -> Runtime Ownership and State
  -> Local Mechanism
```

每次向下展开必须保持上层语义、输入、输出、失败和结果宣称，不允许当前数组、
emitter 形状、历史 helper 或测试 fixture 反向定义产品。

### 3.2 横向叙事

```text
Input / Current Facts
  -> Validate / Derive
  -> Decide
  -> Transition / Commit / Effect
  -> Observe / Return
  -> Invalidate / Release
```

Derivation、Decision 和 Transition 必须区分；只有状态 Owner 可以修改或宣称
权威状态。父叙事中的步骤可以展开为子叙事，但不能只靠任意调用链维持因果。

### 3.3 正确性保持

```text
Construct valid
  -> Validate relationships
  -> Publish complete state
  -> Preserve invariants across transition
  -> Keep failure state trustworthy or explicitly faulted
  -> Reject stale facts
  -> Release according to ownership
```

Public/generated 边界使用真实、稳定校验。Internal `assert` 只能守护关闭断言也
不会破坏语义的纯推导事实；所有 publish、lineage、lifecycle、ownership、
resource 和原子性防线必须使用真实 failure。

## 4. 审计切片

审计沿少量完整 vertical slice 进行，不逐方法建立清单：

| Slice | 起点与终点 | 主要关注点 |
|---|---|---|
| Compiler/Codegen | annotation → normalized model → generated artifact publish | legality、identity、determinism、protocol |
| Table Mutation | operation → preflight/stage → one publish/failure | packed state、locator/index、epoch、atomicity |
| Access/Lifecycle | Table → Scan/View/Snapshot/Cursor → consume/invalidate | lineage、one-shot、escape、currentness |
| Transformation/DataFlow | Definition → Template → Invocation → Result/Effect | Shape、binding、parallel、budget、failure |
| Evidence/Claim | implementation → Gate artifact → Conformance/Report | claim identity、publication safety、closure |

Processor 或 runtime 大文件只是导航信号。只有当完整 slice 显示多个独立变化原因、
平行 Owner、层级混杂或证据负担时，才进入拆分或合并候选。

## 5. 非回归约束

本专题不得静默改变或削弱：

- Schema-Defined、Compiler-Specialized、JVM Heap-Resident、Java 8 产品定位；
- annotation Schema、public/generated API 与 generated/runtime protocol；
- packed SoA、key/unique/exact access、swap-remove、Index/IndexSnapshot 契约；
- Access Model、Candidate Scan、Transformation/DataFlow operator 与 Shape 语义；
- root/child ownership、single-aggregate 原子性、lifecycle 和 safe-point mutation；
- sequential oracle、parallel determinism、failure ordering 和 executor ownership；
- structured failure、detached Result、materialization/resource budget；
- 两个 reference application 的职责、场景目标和隔离边界；
- compiler/golden/external consumer/property/differential/E2E/performance Gate；
- Zulu JDK 8 唯一验真边界、G6 blocked 和 `claimAllowed=false`。

优化不得把 reflection、Java Stream、per-element DTO/tuple、boxed collection、
generic object graph executor 或 hidden materialization 引入 hot storage/path。

## 6. 当前重点问题边界

正式 Design 声明 internal invariant failure 后当前 aggregate fail fast、不能继续
normal access。Stage 0 只确认以下审计候选，不预设修复：

- `DenseTableState` 当前没有显式 `FAULTED/POISONED` 状态；
- generated operation 通常把 `SomaRuntimeException` 交给
  `endOperationFailure()`，把 unexpected `RuntimeException`/`Error` 交给
  `abortOperation()`；
- 需要区分 create 前、preflight、staging、publish、callback、cleanup 和
  ownership propagation 中的 failure 可信度；
- 需要裁决 fault scope、允许的 diagnostics/release、child/root 传播和 hot-path
  检查成本。

不能因为合法调用无法主动触发 internal invariant 就忽略 Design enforceability；
也不能在没有分类证据前把所有 failure 一律 poison。

## 7. 授权边界与停止条件

当前授权只覆盖 Stage 0。Stage 1 之后需要用户继续授权。

即使获得后续内部优化授权，出现以下情况仍必须停止并请求决定：

- public/generated API、annotation Schema 或 protocol 需要不兼容变化；
- Access Model、Transformation/DataFlow、ownership、Index lifecycle、原子性或
  public failure 语义需要改变；
- 需要删除产品能力、场景目标或弱化 Gate；
- 需要引入第三方依赖、非 Java 8 runtime 或第二套存储/lifecycle model；
- 需要把 internal fault 状态暴露为新的 public contract；
- 证据要求扩大到本专题之外的产品、应用或 release 设计。

Conformance 发现本身不扩大实施授权。正式 Design 在最终候选获得授权并完成验证前
保持稳定。

## 8. 阶段

| Stage | 当前状态 | 责任 | 退出条件 |
|---|---|---|---|
| 0 | COMPLETE | 协议、事实基线、初步风险和候选问题 | Temporary 自洽；现状、边界与 Gate instability 有证据 |
| 1 | PENDING | 五个 vertical slice 的抽象、叙事和证明链审计 | 问题分级；无逐文件泛化审计 |
| 2 | PENDING | internal/unexpected failure、fault scope 与可信状态详细设计 | Design candidate 自洽；性能/兼容影响明确 |
| 3 | PENDING | 证据支持的 production/test 内部实施 | 每个 slice 独立正确、可验证、可保留 |
| 4 | PENDING | property/differential/contract/E2E 与窄性能验证 | 防线有效；无洪水式重复测试 |
| 5 | PENDING | scope non-regression、完整 Gate、正式固化和退役 | 唯一 Owner、Report、无 Temporary 尾项 |

Stage 1 可以判定某个候选为“保持现状”。Stage 2 不能以将来重写为理由批准当前
不正确的中间状态。Stage 3 每个实施 slice 必须是最终设计的有效子集。

## 9. 验证策略

- 文档与协议：`./scripts/check-docs.sh`、`git diff --check`；
- compiler/codegen：normalization、golden、external consumer、clean/repeat；
- runtime：stable-state invariant、expected/internal/unexpected failure path；
- Transformation/DataFlow：构造契约、reference differential、parallel/effect；
- 性能敏感状态检查：窄 component benchmark，多 fork 后才能形成性能结论；
- 最终收口：一次完整 `./scripts/check.sh`。

测试只证明唯一 Owner 的防线有效，不为每个 forwarding method 重复相同输入和
lifecycle case，也不冻结无契约意义的 private helper 或内部数组布局。

## 10. 完成条件

专题只有在以下条件全部满足后才能退役：

- 五个 vertical slice 的抽象、叙事、Owner 和证明链已审查；
- internal/unexpected failure 后 aggregate 可信度与 fault scope 已明确，并由相应
  production Owner 构造性强制；
- 发现的真实偏差关闭，保留项有证据，优化不靠 LOC 或测试数量证明；
- public/API/Schema/runtime semantics、性能、应用和 Gate 没有缩水；
- 正式长期事实提升至唯一 Owner，Implementation Map/Conformance 同步；
- 形成正式 Governance Report，记录决策、证据和未改变项；
- 完整 Gate 与 `git diff --check` 通过；
- Temporary 删除，`docs/README.md` 恢复无 active topic 状态；
- 最终源码、文档、evidence 和提交相互对应。

Stage 0、审计报告、单个 fault-state patch 或部分测试不能代替最终收口。
