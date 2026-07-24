# Industrial Dynamic Scheduler 设计与性能治理

类型：Temporary

状态：active（implementation candidate）

Owner：industrial-dynamic-scheduler 设计、契约、Schema 与性能治理

事实范围：本专题的意图、授权边界、候选设计、实施阶段、非回归约束和验收条件

非事实范围：SOMA 产品语义、SOMA core 修改授权、治理完成后的长期正式设计

最后审查日期：2026-07-24

## 1. 意图

把 `industrial-dynamic-scheduler` 从“结构完整的 SOMA reference consumer”
进一步治理为语义身份、失败边界、公共契约、数据表示和规模性能均自洽的严肃
Java 8 应用。实现继续服务应用 Blueprint/Design；benchmark、synthetic generation
和 SOMA runtime 细节不得反向定义领域 Problem、Result 或 Solver 契约。

## 2. 目标

1. 在任何 SOMA Table mutation 前拒绝会导致时间运算溢出的 detached input；
2. 将 Problem 的语义 identity 与生成 provenance、集合物理顺序分离；
3. 让独立 Validator 覆盖 Result 的全部领域声明；
4. 将领域 Result 与 SOMA runtime/performance diagnostics 分离，消除无语义增益的
   evidence wrapper 和意外 public implementation surface；
5. 建立 `行为 -> Access Pattern -> Schema/Index/派生结构` 闭环，删除没有当前
   行为来源的索引、字段和投影；
6. 在保持 global total-order、event ordering、version revalidation、commit 顺序和
   结果语义的前提下，优化 global frontier 执行；
7. 同时保护 `prepare + solve` canonical journey 和独立 hot solve 的多 fork 性能。

## 3. 非回归约束

- 不修改 SOMA annotation、generated API 或 runtime core；
- 不删除 flexible machine、precedence、release/material、setup、maintenance、
  transport、secondary resource、due/priority、machine delay 等应用能力；
- 不改变 comparator 全序、事件先行规则、candidate version 复验、swap-remove、
  fail-stop 跨 Table 语义和 detached Result 生命周期；
- 不把 synthetic factory、fixture、oracle、verification 或 benchmark 带入 hot loop；
- 不增加第三方依赖，不降低 Java 8/Zulu 8、普通 consumer、production JAR purity、
  checksum replay、lifecycle、IndexSnapshot、Fast/Scale/Soak/Full Gate；
- 不以 LOC、文件数或静态未使用单独决定删除；每项表示必须回到实际行为、Owner、
  生命周期和替代闭包裁决。

## 4. 候选设计

### 4.1 Problem 与 Result

- `SchedulingProblem` 只保存可求解的领域事实；generator version、seed 和 config
  checksum 属于 generation/evidence envelope；
- semantic checksum 按业务 identity 规范化，忽略无语义的集合物理顺序；
- `ScheduleResult` 只承载 schedule 和可由 Problem/assignments 独立验证的领域结论；
- SOMA schema/runtime-plan、allocation 和 scratch 数据进入独立 execution
  diagnostics，不成为通用 `SchedulingSolver` 的领域结果要求。

### 4.2 Access Pattern 与表示

| 行为 | Access Pattern | 当前候选表示 |
|---|---|---|
| job/operation/machine/resource 定义读取 | key/unique/column | 只保留求解实际读取的 Table 字段 |
| operation 的 eligible machine 读取 | immutable exact-group | 单一 flat Table + `by_operation`，避免每 operation 一个 owned child Table |
| operation 发布与退役 | operation key + owned eligible child | application-owned primitive candidate pool 与每机 intrusive list |
| stale candidate 刷新 | machine/resource version invalidation | machine dirty marker；resource version 先作“不改变得分”证明，再按需刷新 |
| global total-order best-one | current candidate global arg-min | 每机一个代表项的 indexed min-heap，保留原 comparator 全序 |
| assignment 产生与导出 | append/key traversal/materialize | 不维护没有查询消费者的 secondary index |
| maintenance/resource availability | immutable/derived calendar | maintenance 二分定位冲突区间；resource lane 由 solver 单次生命周期拥有 |

精确删除清单必须由调用链、生成 surface、测试和性能证据共同确认。

9-fork canonical evidence 曾暴露 100,000 个 owned eligible-machine child Table
导致约 `718..724 MB` 端到端分配并偶发 Full GC。该表示已按实际 exact-group
访问改为 flat immutable Table；完整投影逐值复核继续由 test-only Gate 承担，
不再进入 production `prepare()`。

### 4.3 性能归因

优化顺序固定为 application access/algorithm、example Schema、SOMA public usage，
没有领域中性 component reproduction 时不修改 SOMA core。global selection 的候选
结构保存 stable key 和 source version，不保存长期 SOMA Index；Table mutation 后按
key/current state refresh 或丢弃 stale entry。

## 5. 阶段

1. Stage 0：冻结基线、登记 Temporary、记录当前 correctness/performance；
2. Stage 1：输入范围、semantic checksum、Result claim 与微型 oracle；
3. Stage 2：Result/diagnostics 契约和 implementation visibility；
4. Stage 3：Access Pattern/Schema/派生结构闭环；
5. Stage 4：global-frontier 优化和 canonical/hot 性能 Gate；
6. Stage 5：scope non-regression、正式文档原子固化、Governance Report 与
   Temporary 退役。

## 6. 验收

- 新增的 overflow、semantic-order、result-claim、total-order/event/resource
  语义测试全部通过；
- 生产 Problem/Result 不再携带 synthetic 或 SOMA benchmark 专属事实；
- 每个保留的 Schema index、字段和 application structure 都能追踪到当前行为；
- large workload 不降低规模、不改变 checksum，且 global-frontier 优化有稳定
  multi-fork 证据；canonical preparation 进入 regression evidence；
- `./scripts/check-industrial-scheduler.sh`、三类应用性能 Gate、reference application
  Gate、`./scripts/check.sh` 和 `git diff --check` 全部通过；
- 长期事实固化到应用 Design/Validation、Implementation Map、Conformance 和正式
  Governance Report，随后删除本 Temporary，`docs/README.md` 恢复无 active topic。
