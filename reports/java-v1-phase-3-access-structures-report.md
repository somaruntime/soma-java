# Java-only SOMA V1 Phase 3 AccessStructures closeout report

状态：passed
日期：2026-07-11
实现 commit：`3292e7a`
Goal：`完成完整 Java-only SOMA V1.0，并通过 G0–G6。`（继续 active）

> 当前性说明（2026-07-20）：本报告是 commit `3292e7a` 的 v2 历史 checkpoint，其中 `@SomaOrder`、`RowPermutationSidecar`、dirty/lazy rebuild 与 stable tie-break 已由 `4b6fa43` 的 packed/exact v3 切换取代。当前实现与验证入口见 [2026-07-17 专题收口报告](2026-07-17-packed-exact-index-runtime-redesign-report.md)；本文保留当时证据，不定义当前架构。

本报告只关闭 Phase 3 实施 checkpoint，不关闭任一完整 Capability、RC、G1–G6 或 release readiness。

## 1. Scope 与 Owner

本阶段涉及 `V1-ANNOTATION-SCHEMA`、`V1-PROCESSING-MODEL`、`V1-SCHEMA-HASH`、`V1-GENERATED-API`、`V1-ACCESS-STRUCTURES`、`V1-ROW-PIPELINE`、`V1-MUTATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-RUNTIME-PLAN`、`V1-PERFORMANCE-SHAPE`、`V1-EVIDENCE-TOOLING`、`V1-CONSUMER-PACKAGE` 和 `V1-PUBLIC-COMPATIBILITY`。这些 Capability 均保持 `in-progress`。

唯一 Owner 仍按职责分离：annotation schema contract 拥有 selector declaration；schema processing/code generation contract 拥有 normalization、validation、hash 和 static binding；Generated Table API contract 拥有 typed/grouped source；TableStore、runtime lifecycle/errors/plan/performance contract 分别拥有 sidecar material、mutation/lifecycle、typed failure/stats、plan identity 和 hot-path shape；testkit 只拥有 evidence helper。

## 2. 实际实现

- 新增 repeatable `@SomaIndex`、`@SomaUnique`、`@SomaOrder`、`@SomaSort` 与 direction，processor 对 selector name/path/type、container placement、API collision 和 canonical schema/hash 作稳定处理；
- generated table 提供 typed/grouped index、unique、order source，并用 primitive `RowPermutationSidecar`、binary candidate range、dirty/lazy rebuild 与 stable row tie-break 实现；
- mutation 只 dirty 实际依赖的 selector，unique 在 publication 前完成 full staging/validation；strict float/double、enum/value leaf 使用静态 primitive binding；
- source 保持 lazy `size/rowAt`，不复制 eager row list；clean selector terminal 的 allocation 不随 table rows 线性增长；
- `TablePlan`/compatibility identity、sidecar dirty/rebuild/scratch stats、result delta、clear/release 和 operation failure atomicity已接入；
- external Maven consumer、deterministic randomized oracle、compile diagnostics、schema/hash/javap/source/bytecode/allocation shape evidence 已进入仓库脚本。

## 3. Validation record

验证对象：实现 commit `3292e7a` 的工作树；最新专项临时 evidence 目录为 `target/phase3-access.WH86yU`，可重复生成，正式可追溯证据是仓库 fixture、golden、脚本和本报告。

执行命令：

```text
./scripts/check-access-phase3.sh
./scripts/check-table-diagnostics-phase1.sh
./scripts/check-public-api.sh
./scripts/check-generated-keyed-phase2.sh
./scripts/check-runtime-core-phase1.sh
./scripts/check.sh
./scripts/check-docs.sh
git diff --check
```

环境：Azul Zulu OpenJDK `1.8.0_492-b09`（64-Bit Server VM build `25.492-b09`），`javac 1.8.0_492`；Maven Wrapper / Apache Maven `3.9.16`；macOS `26.5.2` build `25F84`，`arm64`。

结果：以上相关专项与完整 `check.sh` 全部通过；external consumer、192-row/320-mutation randomized oracle、32/512-row allocation scaling、generated source/bytecode shape、invalid selector/collision/placement/string diagnostics 均通过。unsupported-javac negative lane因未设置 `SOMA_UNSUPPORTED_JAVAC` 跳过。

## 4. Gate 与 V1 scope non-regression

- G0 保持 `passed`；G1–G6 保持 `not-started`。Phase 3 通过不构成 RC、release 或 performance superiority claim；
- `V1-ACCESS-STRUCTURES` 从 `not-started` 推进为 `in-progress`，其余 Capability 无降级、删除、optional 化或 Gate 移动；
- Phase 4 child ownership/recursive materialization、Phase 5 string/optional enum/value/default与compiler breadth、Phase 6 examples/benchmark/package/release/support matrix全部保留原 Phase/Gate；
- Owner 边界、Capability Ledger、Gate 和 release claim 均未改变；Owner 内容只补充与实现一致的正式 protocol/behavior；
- 后续只能 additive completion 或 contract-preserving internal refinement；本阶段没有 temporary public/generated API、temporary hot path、consumer migration、核心事实迁移或 rewrite；
- runtime canonical access path 是 primitive packed/static binding 与 primitive sorted permutation，不使用 `List<Row>`、DTO graph、reflection、metadata interpreter、Stream、boxing 或 per-row allocation 作为正式 hot path。

## 5. Known limitations

本机结果不能外推为正式 JDK vendor/minor、OS 或 architecture support matrix；G6 support matrix 仍为 `not-started`。当前 RC/release readiness 尚未成立。Phase 4–Phase 6 与 G1–G6 仍须在同一 V1 Goal 下完成。
