# SOMA Java V1 Implementation Readiness Review

类型：Conformance Review

状态：Passed

结论：`READY_FOR_IMPLEMENTATION`

正式事实源：是（仅拥有 2026-08-01 实施准备审查的范围、发现、处置和结论）

Owner：SOMA Java V1 pre-implementation completeness、repository readiness 与实现准入结论

审查日期：2026-08-01

## 1. 审查问题

本审查只回答：进入 production implementation 前，产品、Design、exact surface、实现
基线、证据计划和仓库边界是否已经准备充分，实施者是否仍会被迫现场决定产品语义。

它不回答也不暗示：代码是否已实现、性能是否达标、artifact 是否可用、是否可以发布。

## 2. 结论

结论：`READY_FOR_IMPLEMENTATION`。

理由：V1 普通用户心智、产品边界、schema/type/storage/API/failure/concurrency 语义、
exact Java 8 projection、两 artifact topology、full-regeneration contract、baseline internal
mechanism、量化 performance/security/release Gate 和有 stop rule 的 implementation slices 均已有唯一
正式 Owner。没有剩余 Product Owner 裁决阻塞 I0。

`READY_FOR_IMPLEMENTATION` 的精确含义是：收到单独的 implementation authorization 后，
可以从 [I0](../engineering/v1-implementation-plan.md) 开始；不需要继续写一轮 Blueprint，
也不能跳到全量编码或 release。

## 3. 产品完整性审查

### 3.1 产品叙事

SOMA 不是数据库、ORM、DataFrame、solver VM 或 Java Stream replacement。它服务于长期
持有、反复 Key/Index access、扫描和更新的大规模进程内 Table state：application 用
自然 Java object/generated API，compiler lowering 到 data-oriented storage/execution。

它从 Java Stream 学习 pipeline/terminal、lazy one-shot、熟悉命名、sequential default
和 explicit parallel；它额外拥有 Table identity、Key/Index、storage、add/update/remove、
admission、atomic publish 与 structured failure。这个差异已经进入
[Blueprint 5.5](../blueprint/README.md)，不会再依赖实现者或营销文档重新解释。

### 3.2 Use / do-not-use boundary

| 适合 | 不适合 |
|---|---|
| 大规模、频繁变化的进程内 runtime state | 小集合或一次性 object transformation |
| primitive/Value dominant scan 与 exact Key/Index | SQL、持久化、分布式查询 |
| 调度、仿真、实时派工 | arbitrary source、infinite/async stream |
| Table-local controlled mutation | cross-Table transaction、referential integrity |
| 显式、资源有界 parallel compute | hidden concurrency 或 per-stream executor |

### 3.3 产品风险是否被隐藏

没有。性能价值仍是待 production evidence 验证的 hypothesis；Blueprint 不声明“已经
更快”。Batch、join、ChildTable、Segment、physical Column、Collector/flatMap 等 absence
不是遗漏，而是明确边界。若 reference scenario 无法自然表达或未达 G7，stop rule 要求
回到产品审查，不能用 hidden API 粉饰结果。

## 4. Design readiness matrix

| 实施前问题 | 正式 Owner | 审查结果 |
|---|---|---|
| 产品目标/用户/边界/成功 | [Blueprint](../blueprint/README.md) | CLOSED |
| composition/annotation/declaration/diagnostic | [Schema Design](../design/schema-and-generation.md) | CLOSED |
| Group/Table/type/null/Key/Index/order/lifecycle | [Storage Design](../design/data-model-and-storage.md) | CLOSED |
| 用户 hierarchy/source/Stream/direct operation/metadata | [Logical API](../design/logical-api.md) | CLOSED |
| exact package/type/signature/operation property | [Signature Design](../design/generated-api-signatures.md) | CLOSED |
| late binding/admission/atomicity/parallel/determinism | [Execution Design](../design/execution-and-concurrency.md) | CLOSED |
| Result/code/mapping/precedence/state guarantee | [Failure Design](../design/results-and-failures.md) | CLOSED |
| artifact/build/runtime/storage/index/publish algorithm | [Architecture Design](../design/implementation-architecture.md) | CLOSED |
| 验证范围与 pass/fail | [G1-G8](v1-implementation-gates.md) | CLOSED AS CONTRACT |
| 实施顺序、exit、stop/change protocol | [Implementation Plan](../engineering/v1-implementation-plan.md) | CLOSED |

这里的 `CLOSED` 表示“不再由 implementation 自行裁决”，不是 Gate 已通过。G1-G8 仍
全部未 PASS，只能保持 historical partial、`NOT_IMPLEMENTED` 或 `NOT_EVALUABLE`。

## 5. 关键技术空白的处置

| 原空白/坏味道 | 处置 |
|---|---|
| API 草稿只有示例，没有 exact Java grammar | 新增 Signature Design，固定 annotation、generated/shared type、Stream family、terminal result、metadata 和 absence |
| Java 8 same-package construction 被误写成不可见 | 改为可编译但不可伪造有效 token，并规定 runtime guard negative |
| Record/View “永远一个对象”无法覆盖 comparator | 改为 participant 常数个，总量 O(1)/O(P) |
| `minBy/maxBy` 偏离 Java Stream | 统一为 `min/max(Comparator)`，无 alias |
| Metadata exact carrier 未定 | Signature Design 固定 immutable carrier/enum/order/no-side-effect |
| module/artifact/dependency 未定 | 固定 `soma-runtime` + `soma-processor` 两 artifact 与 exact version |
| full regeneration 只有原则 | 固定 `-Asoma.fullSourceSet=true`、manifest、Maven/IDE/raw javac/Gradle 支持矩阵 |
| storage/Index/publish/scheduler 可能各自发明 | Architecture 固定 dense root、hash Key/Index、journaled update、deterministic swap-compaction、CAS admission、bounded partition |
| integer/float parallel reduction 可能结果漂移 | 固定 exact wider accumulator、signed 128 long 与 1024-block pairwise floating tree |
| repeated add 无 Batch 的性能风险 | G7 设 70% baseline stop threshold，失败即回产品边界复审 |
| 性能“越快越好”不可验收 | G7 固定 baseline、规模、内存/吞吐/并行/峰值阈值和 evidence protocol |
| Compile-time processor/supply-chain 没有独立 Gate | G8 固定 Filer/path/source-injection、dependency、package、workflow/provenance boundary |
| P2 fixture 已删除却仍像 current evidence | 标记 Historical / Non-Replayable，只作为风险线索，production 必须重验 |
| 无 `pom.xml` 却残留 Maven Wrapper | 删除 wrapper scripts/properties 和专属 attributes，不留下假 build entry |

## 6. Known implementation risks and controls

| Risk | 影响 | 已有控制 | 是否阻塞 |
|---|---|---|---|
| JSR 269 看不见 host 未提交的 source | stale/incomplete composition | clean-full host contract、handshake、manifest、delete/rename Gate | 否 |
| 八 primitive + generated Field type surface 较宽 | generator/test matrix 复杂 | exact grammar、golden/javap、I1 narrow slice 后 I2/I3 breadth | 否 |
| structural candidate root / Index rebuild peak | 32 GB 下可能放大 | point update 不复制整列；complexity/2.5x peak/dual-1M threshold、explicit Group lifecycle | 否 |
| parallel deterministic stateful operation | scheduler 和 failure arbitration 复杂 | fixed partition/order/tree、允许 deterministic barrier、I5 独立 slice | 否 |
| repeated single add | 大导入 admission/index overhead | reserve、carrier reuse、70% stop rule；不隐藏 Batch | 否 |
| default Group high-water retention | singleton 长期持有大数组 | 文档化 high-water；替换/双缓存使用 explicit Group | 否 |
| Historical P2 无 current executable fixture | 旧结论会漂移 | I1/I6 在 production topology 重新验证，P2 不让 Gate PASS | 否 |

风险之所以不阻塞，是因为它们不再需要先做产品决策，且每项都有最早验证 slice、量化
失败条件和停止/升级路径。若 evidence 失败，它会成为真实 blocker，而不是继续消耗时间
和 token 重跑。

## 7. Blueprint-to-implementation traceability

BP-1 至 BP-10 已由[实施计划 Traceability matrix](../engineering/v1-implementation-plan.md)
双向映射到 Design、I0-I7 和 G1-G8。抽查高风险链：

```text
BP-2 compile-time capability
    -> Schema + Signature
        -> I0/I1/I2/I6
            -> G1/G3

BP-5 atomic mutation
    -> Logical + Execution + Architecture + Failure
        -> I1/I4
            -> G4/G6

BP-6 bounded deterministic parallel
    -> Execution + Architecture
        -> I5
            -> G5/G7

BP-8 no hidden boxing/O(N) cursor
    -> Storage + Logical + Signature + Architecture
        -> I2/I3/I5/I7
            -> G2-G5/G7
```

没有 Blueprint 承诺缺少 Design/implementation/Gate 承接，也没有 implementation slice
找不到上游产品理由。

## 8. Repository surface closure

本次审查后的 active checkout 应满足：

- 只有 Blueprint、Design、Engineering、Conformance、入口/治理与品牌资产；
- 无 active Temporary；
- 无 predecessor/production source、module、`pom.xml`、build artifact、Example、benchmark、
  workflow 或 release claim；
- 无 orphan Maven Wrapper/build command；
- project/root/agent route 指向正式 Owner；
- Markdown relative links、status language、forbidden stale API 与 whitespace Gate 通过。

Git commit/push、remote synchronization、implementation、Release/Package 都不属于本次
readiness review；它们必须分别获得授权和证据。未提交 worktree 也不能冒充 remote
项目事实。

## 9. Verification performed

适用 checkout 完成：

- `rg` stale/forbidden/Open-decision search；
- Blueprint -> Design -> Engineering -> Conformance Owner/traceability review；
- Markdown relative-link resolution；
- tracked/untracked repository surface inventory；
- `git diff --check`；
- no production/build/release-claim inventory；
- no active Temporary check。

本次为文档与 repository-surface 变更；没有 production compile/runtime/performance 可
执行，因此没有伪造这些 Gate。

## 10. Authorization boundary

下一次获得明确 implementation authorization 后，只执行 I0；I0 exit evidence 未通过前
不进入 I1。不得把本结论解释为允许 push、release、发布 Maven artifact、恢复 predecessor
或并行铺开所有 modules。

如果实施者在 I0 前仍能指出一个会改变普通用户 capability、failure/order/numeric/
lifecycle 的未决选择，本审查应重新打开；单纯 class naming、local data structure coding
和 test implementation 不是产品未决项。
