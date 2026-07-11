# Java-only SOMA V1 Phase 4 child/materialization closeout report

状态：passed
日期：2026-07-11
实现 commits：`aaed474`、`d9ad752`
Goal：`完成完整 Java-only SOMA V1.0，并通过 G0–G6。`（继续 active）

本报告只关闭 Phase 4 实施 checkpoint，不关闭任一完整 Capability、RC、G1–G6 或 release readiness。

## 1. Scope 与 Owner

本阶段涉及 `V1-ANNOTATION-SCHEMA`、`V1-PROCESSING-MODEL`、`V1-SCHEMA-HASH`、`V1-GENERATED-API`、`V1-CHILD-OWNERSHIP`、`V1-MATERIALIZATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-RUNTIME-PLAN`、`V1-PERFORMANCE-SHAPE`、`V1-EVIDENCE-TOOLING`、`V1-CONSUMER-PACKAGE` 和 `V1-PUBLIC-COMPATIBILITY`。`V1-CHILD-OWNERSHIP` 从 `not-started` 推进为 `in-progress`，其余保持 `in-progress`。

唯一 Owner 仍按职责分离：annotation schema contract 拥有 `@SomaChild` declaration；schema processing/code generation contract 拥有 validation、normalization、hash 与 static generated binding；Generated Table API/materialization contract 拥有 exact typed child facade 与 detached recursive result；TableStore、runtime lifecycle/errors/plan/performance contract 分别拥有 handle/registry material、forest/cascade/replacement、typed failure/stats、plan/budget identity 与 allocation shape；testkit 只拥有 comparator/evidence helper。

## 2. 实际实现

- 新增 `@SomaChild`、`ChildPlan`、required/optional List/Map child exact generated API；processor fail closed 拒绝 raw/shape/key mismatch、跨 schema ownership 与 declaration cycle，并把 child edge 纳入 canonical schema/hash；
- parent row 只保存 opaque generation handle；`ChildOwnershipRegistry` 使用 primitive/object parallel arrays、identity open addressing 与 atomic growth，拒绝 share/reparent、dangling handle、duplicate identity 与 runtime cycle；
- ensure/unset/replace/delete/clear/release、row compaction 和 owned facade 生命周期形成 parent-owned forest，级联前执行 subtree pin/materialization preflight，失败不部分 publication；
- recursive materialization 使用共享 `MaterializationTracker` 两遍 account/build，覆盖 ownership depth、table instances、rows、leaf values、estimated bytes、path、overflow 与 cycle；dense whole result 为 List，keyed whole result为 Map，Row Pipeline 保持 sequence；
- default/per-call budget、`RuntimePlan` child plan identity、`TableStats` Phase 4 materialization facts 与 reset/recovery接入；受控 allocation preflight发生在完整 account 后、公开 carrier/List/Map 构造前；
- testkit 增加 detached graph comparator；external Maven consumer覆盖 required/optional empty/present/data、dense/keyed/grandchild、signed-zero key、cascade/replacement/compaction、全部 materializing overload、五维预算边界、allocation failure、reentrant descendant mutation/reset、carrier RuntimeException/Error原样传播、schema/hash与全部 generated top-level public type exact javap repeatability及形状证据。

## 3. Validation record

验证对象：实现 commits `aaed474`、`d9ad752`；最新完整 Phase 4 evidence 目录为 `target/phase4-child.w2dK8F`，仓库级完整验证最终返回 `project-check: ok`。临时目录可重复生成，正式可追溯证据是仓库 fixture、golden、脚本和本报告。

执行命令：

```text
./scripts/check-child-phase4.sh
./scripts/check-runtime-core-phase1.sh
./scripts/check-testkit-phase4.sh
./scripts/check-table-diagnostics-phase1.sh
./scripts/check-public-api.sh
./scripts/check-generated-dense-phase1.sh
./scripts/check-generated-keyed-phase2.sh
./scripts/check-access-phase3.sh
./scripts/check.sh
git diff --check
```

环境：Azul Zulu OpenJDK `1.8.0_492-b09`（64-Bit Server VM build `25.492-b09`），`javac 1.8.0_492`；Maven Wrapper / Apache Maven `3.9.16`；macOS `26.5.2` / Darwin `25.5.0`，`arm64`。

结果：全部专项和完整 `check.sh` 通过；external child consumer 在默认环境与 locale/timezone repeat lane通过；45个 generated top-level public type exact javap、schema/hash、runtime/public manifest、graph comparator、required-empty/optional-empty/Optional-wrapper exact estimator、budget/overflow/cycle/allocation/reentrant/carrier failure/lifecycle/failure-atomicity证据通过。unsupported-javac negative lane因未设置 `SOMA_UNSUPPORTED_JAVAC` 跳过。

独立只读复核确认 allocation failure、counter overflow、runtime ownership cycle 三个 blocker均关闭，未发现阻止 Phase 4 closeout 的实质问题。

## 4. Gate 与 V1 scope non-regression

- G0 保持 `passed`；G1–G6 保持 `not-started`。Phase 4 通过不构成 RC、release 或 performance superiority claim；
- `V1-CHILD-OWNERSHIP` 从 `not-started` 推进为 `in-progress`；没有 Capability 被删除、optional 化、移动 Gate 或提前标记 evidenced；
- Phase 5 field/compiler/generated/runtime breadth与G1–G4 closeout、Phase 6 examples/benchmark/package/release/support matrix全部保留原 Phase/Gate；
- Owner 边界、Capability Ledger、Gate 和 release claim 均未改变；Owner 文档只补充与实现一致的正式 exact protocol；
- 后续只能 additive completion 或 contract-preserving internal refinement；本阶段没有 temporary public/generated API、temporary child storage/hot path、consumer migration、核心事实迁移或 rewrite；
- canonical live storage/path 是 packed primitive/object leaf columns、opaque generation handle、primitive registry/sidecar与static generated traversal；Java Collection只存在于 input snapshot 或 detached materialization boundary，不是 live runtime storage。

## 5. Known limitations

本机结果不能外推为正式 JDK vendor/minor、OS 或 architecture support matrix；G6 support matrix 仍为 `not-started`。unsupported-javac negative lane本次未执行。完整 annotation/processor/generated/runtime breadth、G1–G4、formal scenarios、benchmark/package和G5–G6仍未关闭，因此 V1 RC/release readiness尚未成立，唯一 Goal继续 active。
