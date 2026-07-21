# 四场景 Blueprint 采纳专题治理

类型：Temporary

状态：进行中

Owner：FJSP / VRP / Simulation / Game Blueprint 采纳治理

事实范围：本专题的采纳决定、意图、目标、范围、实施切片、验证矩阵、阶段结论与退役条件

非事实范围：长期 Design、当前实现能力、已通过 Gate、性能结论和 release readiness

最后审查日期：2026-07-21

## 1. 已获授权的决定

项目所有者已明确接受以下四个正式目标，并授权将其作为一个专题治理完整实施：

- FJSP 新目标；
- VRP canonical model；
- Simulation numeric/event/time model；
- Game definition/cache/action/damage model。

输入来自正式 [Blueprint](../../blueprints/README.md) 与上一专题的[越界影响登记](../blueprint-practice-governance/external-impact-register.md)。本决定授权修改为采纳这些目标所必需的场景 schema、application code、测试、fixtures、benchmark、Implementation Map、Conformance、开发者文档和当前 evidence；不自动授权修改 core Design/public API 或 release/publishing 边界。

## 2. 意图

消除“Blueprint 已经给出值得模仿的目标，但正式 executable scenarios 仍展示旧模型”的分裂状态。治理完成后，使用者从 Blueprint 进入当前示例时，应看到同一套 data role、identity、访问、顺序、失败和性能边界；测试与 benchmark 应直接执行新的 canonical journey，而不是继续证明旧 schema 能运行。

## 3. 目标

1. **四场景实现符合 Blueprint**：schema、workflow、application helper 与 failure protocol 不再保留 `EXT-001..004` 中的旧 canonical path。
2. **当前事实只有一个 Owner**：输入、working state、derived workspace/cache 与 result 不形成平行权威事实。
3. **Java 8 executable journey 可信**：关键 helper 不隐藏 total order、stale guard、checked arithmetic、时间单位、可行性或失败后的可信状态。
4. **性能边界诚实**：复用 Batch/scratch，避免 Stream/boxing/object graph 进入 canonical hot path；reference allocation 与 measured lane 分开。
5. **证据重新绑定**：golden/schema fixtures、scenario checks、Access Pattern markers、benchmark smoke 与 FJSP allocation/GC 都验证迁移后的实现。
6. **正式文档收口**：Implementation Map、Conformance、example developer Report 与 current evidence 只描述完成后的代码；历史 Report 不改写。
7. **无 scope 回归**：不引入 temporary public API、core metadata interpreter、maintained order/range index、跨 root transaction 或发布声明。

## 4. 共享不变量

- 物理 Index 只在当前同步只读批次内立即消费，不作为跨 operation identity；
- 业务顺序来自 total comparator 或 application-owned heap/scheduler；
- comparator 不访问其他 Table、不 mutation、不产生业务 side effect；
- Batch 是 detached staging，publish 完成后才 clear/reuse；
- 同一 Table callback 内不做该 Table structural mutation；
- 单次 Table operation 失败原子，跨 roots/heap/cache 的提交与恢复由场景 application 明确拥有；
- checked arithmetic、非负输入、引用完整性、unique/sequence/layout invariants 在首个 authoritative mutation 前验证；
- current scenario、fixture 和 benchmark 不保留旧 canonical schema 的兼容分支。

## 5. 实施切片

### 5.1 FJSP

- `by_job_sequence` 改为 secondary unique；删除未消费的 machine/setup selector；
- FCFS=`effectiveReady`，SPT=`setup+processing`，未 refresh indicator 不可消费；
- release 先完整 stage/publish frontier，再激活 application indexed heap；successor/committed machine 使用统一 queue-membership refresh；
- 导入验证 sequence、references、candidate uniqueness、setup completeness 和时间上界；commit 使用 checked arithmetic；
- solver-owned Batch 与 bounded machine-set scratch 复用；更新 verification、fixtures、benchmark checksum/allocation evidence。

### 5.2 VRP

- 用 `CustomerDefinition`、`CustomerAssignment`、`UnassignedCustomerRow` 分离 input/result/derived workspace；
- `Route.by_vehicle` 在 one-active-route model 中使用 unique；route visits 继续为 parent-owned dense child，并预投影 immutable `LocationId`；
- 统一 meter/second/load；candidate 使用 insertion ordinal、route version、完整 hard-constraint projection；
- executable constructor 覆盖 empty/non-empty route、required directed travel lookup、route rewrite、stale preflight、authoritative commit 与 derived cleanup；
- 删除旧 `CustomerState`/assignment shadow 与含糊 penalty canonical path；更新 fixtures、Access Pattern 与 benchmark lane。

### 5.3 Simulation

- definition 与 numeric state 分离，`StateVectorRow` 成为唯一 authoritative numeric state；
- application immutable min-heap 成为 pending event 唯一 queue；Table event row只保留可选 projection；
- 时间统一为 session-origin relative nanoseconds，不使用 `DATE_TIME` 表达 simulation clock；
- 实现 event-boundary advance、derivative staging、finite/scale validation、reusable integration scratch 与 trace batch；
- 覆盖 same-time event total order、late event、numeric failure、projection 非权威性与 trace tuple invariant；更新 fixtures/benchmark。

### 5.4 Game

- player/unit definition 与 mutable state 分离；tile definition 与 occupancy cache 拆成以 `GridPosition` keyed 的两个 roots；
- selected-unit move workspace 不重复 unit identity，使用 application action context、generation 和 pathing revision；
- move commit 先验证 authoritative facts，再提交 unit，随后增量更新或从 units 重建 occupancy；
- pending damage 增加 `(resolutionOrder, sequenceNo)` total order，先复制到 reusable primitive staging，再做跨表 mutation；
- 删除旧 mixed map/move/damage canonical path；更新 fixtures、cache/failure checks 与 benchmark mapping。

## 6. 横向收口范围

在四场景代码和窄验证全部通过后，一次性更新：

- `docs/implementation-map/` 的当前代码投影与 implementation baseline；
- `docs/conformance/`，关闭或重写 `CF-001..003`，并重新确认 FJSP；
- `soma-examples/docs/` 与 module current Report；
- phase-6 schema/hash/public/generated/lane fixtures；
- benchmark model/runner/validator、current benchmark/G5 evidence；
- 新的根级 Governance Report 和 Report 导航。

历史 snapshot Report 不回写成新证据。G6 与 release/publishing 继续保持既有状态。

## 7. 验证矩阵

| 层次 | Required evidence |
|---|---|
| Schema/codegen | 四 schema artifact/hash、generated type/public facts、deterministic repeat |
| FJSP | import/release/indicator/select/commit/successor/heap/infeasible/failure + allocation/GC |
| VRP | split facts、ordinal enumeration、route child rewrite、hard constraints、stale/failure/rebuild |
| Simulation | vector layout、heap total order、event boundary、numeric atomicity、trace/export |
| Game | keyed coordinate、position/cache invariant、stale action、cache rebuild、damage total order/fail-stop |
| 横向 | Access Pattern markers、scenario suite、benchmark artifact checksum、current docs/conformance |
| 完整 Gate | `./scripts/check.sh`、`git diff --check`、changed-path/scope non-regression |

性能 evidence 必须记录实际环境，并只支持本次 workload/机器的结论。旧 benchmark 数值只作 baseline；schema/workflow 改变后不得直接比较为纯 runtime 性能提升。

## 8. 阶段与推进策略

```text
baseline + adoption contract
  -> FJSP migration + narrow evidence
  -> VRP migration + narrow evidence
  -> Simulation migration + narrow evidence
  -> Game migration + narrow evidence
  -> fixtures / benchmark / docs / conformance synchronization
  -> full Gate + cross-scenario review
  -> Governance Report
  -> delete both adoption Temporary topics
```

每个场景以“schema + application workflow + verification”作为不可拆分切片。窄验证失败时先在当前切片修复，不用兼容分支保留旧 canonical path。只有发现现有 V1 capability 无法表达已接受目标时，才暂停并请求 core Design/public API 裁决。

## 9. 完成与退役条件

本专题只有在以下条件全部成立后才能完成：

- `EXT-001..004` 的代码和证据差距关闭；
- `EXT-005` 的 current projection/evidence 已同步；
- Conformance 不再把已实现目标列为 open gap，也不保留虚假的 aligned 结论；
- 无旧 schema class、旧 generated fixture、旧 developer current wording 或临时 canonical path；
- 完整 Gate 通过且结论未越过 evidence 边界；
- 形成正式 Governance Report；
- 本目录与 `docs/temp/blueprint-practice-governance/` 删除，且正式文档无 Temporary 引用。
