# R2.4 一致性、过度设计与Implementation Readiness审查

类型：Bounded Temporary / Self-review and Readiness Recommendation

状态：`PASS / PRE_FREEZE_REVIEW / SUPERSEDED_AS_CURRENT_STATUS`

日期：2026-08-12

审查者：Codex主Agent（非独立第三方审查）

审查输入：[R2.1 current state](current-state-audit.md)、
[R2.2 Candidate Design，现已固化](design.md)、
[R2.3 bounded validation](feasibility-validation.md)及本专题[治理入口](README.md)。

Current status由后续
[Baseline Freeze与Readiness审查](baseline-freeze-and-readiness.md)唯一拥有。本文件保留pre-freeze自审
provenance，不再作为当前Freeze、Readiness或slice scope的Owner。

## 1. 审查目标

本次R2最终自审不是继续寻找更多功能，而是判断：

1. Candidate Design是否统一、自洽地服务SOMA Java V1目标；
2. 是否引入第二套IR、planner、executor、scheduler、resource或storage truth；
3. 是否把实现细节过早冻结为长期抽象；
4. 能否把implementation分成可验证、可删除失败方案的bounded slices；
5. Product Owner在授权前是否仍需裁决产品语义或重大架构方向。

## 2. Traceability审查

| Blueprint/正式目标 | Candidate承接方式 | 结论 |
|---|---|---|
| schema-known高性能runtime-state computation | schema-known primitive leaf + finite typed predicate + Chunk kernel | PASS |
| Canonical语义与physical execution分离 | Canonical不变；final Physical Plan拥有kernel decision | PASS |
| 单进程、低延迟、低分配 | borrowed representation、run/bit direct、O(chunks) partial | PASS |
| 资源受控、fail closed | complete checked ResourceEstimate before work | PASS |
| sequential/parallel逻辑等价 | shared scheduler、ordinal partial/range merge | PASS |
| AUTO对普通用户透明 | representation handler由planner选择；public API不变 | PASS |
| Reference correctness oracle | production kernel不复用Reference traversal | PASS |
| Java 8、two artifacts | 无新module/dependency/runtime codegen/Vector API | PASS |
| 按证据演进而非填满路线图 | cell级ADMIT/DEFER/REJECT；complex operator默认none | PASS |

## 3. 与正式Design Owner的一致性

### 3.1 Planning and Optimization

- normalized semantic input保持唯一；
- access、kernel、representation、partition与resource进入final Physical Plan；
- fallback在plan时确定；
- 不引入跨terminal cache或第二planner。

结论：一致。

### 3.2 Execution and Concurrency

- guard、binding、admission、Frame、cleanup主路径不变；
- Chunk morsel复用shared bounded ordinal-work lifecycle；
- callback、interrupt、failure arbitration与quiescence不变；
- O(chunks) partial与ordered two-pass在work前计费。

结论：一致。

### 3.3 Implementation Architecture

- finite private mechanism，不生成public Batch/Vector surface；
- 不建立operation × type class hierarchy；
- exact private class名不在R2冻结；
- migration要求删除terminal refinement residue，不长期保留两套路径。

结论：一致。

### 3.4 Data Model and Storage

- authoritative storage仍是Chunk representation；
- borrowed typed/run/bit access不可逃逸operation；
- 不整体decode、不复制第二份PLAIN truth；
- overlay先明确scalar fallback，不为未来预建cache。

结论：一致。

### 3.5 Core abstraction/narrative

Candidate只成熟化Physical Plan、ExecutionFrame、representation与morsel mechanism，不新增新的用户可见核心
抽象，也不为SOMA Engine改变Java-first semantic model。

结论：一致。

## 4. 过度设计审查

| 潜在过度设计 | Candidate处理 | Verdict |
|---|---|---|
| general vector DAG | 明确拒绝；只保留finite closed shape | CLOSED |
| 第二套IR/planner | Physical request不是semantic IR；由现有planner一次决策 | CLOSED |
| 第二executor | finite kernel由现有ExecutionFrame/lifecycle消费 | CLOSED |
| 第二scheduler | Chunk/row共享现有ordinal-work lifecycle | CLOSED |
| universal Batch/Vector container | 不准入 | CLOSED |
| runtime codegen/JIT/Vector API | 不准入 | CLOSED |
| per-cell class cross-product | finite enum/binding/handler；按证据逐cell | CLOSED |
| decode cache/第二storage truth | 不准入；overlay scalar fallback | CLOSED |
| sub-Chunk morsel | 无skew evidence前拒绝 | CLOSED |
| GroupBy与Join同时治理 | S1-S3均不准入；S4默认none | CLOSED |
| 为SOMA Engine预留workflow abstraction | 不准入；只保留Canonical/physical clean boundary | CLOSED |
| 把private名称冻结成Design | vocabulary只定义职责，不固定class名 | CLOSED |

未发现需要删除整个Candidate方向的过度设计。剩余复杂性与已有正式合同直接对应：numeric、resource、order、
parallel quiescence不是可省略的“异常场景”，而是高性能执行机制的正确性边界。

## 5. 坏味道与替换闭环

| Current smell | Candidate replacement | 删除/收口要求 |
|---|---|---|
| base plan之后逐terminal refinement | closed terminal request进入single planner decision | S1结束前删除完成使命的refinement分支 |
| representation在kernel内隐式决定 | final plan记录per-representation handler/fallback | 不保留双重eligibility |
| RLE逐row visitor | Storage提供closed run access | 旧visitor保留给generic fallback，不继续承载vector特例 |
| vector class职责持续膨胀 | decision、representation access、kernel mechanism按Owner拆分 | 不建立通用hierarchy；同层代码可读 |
| generic parallel O(rows) prefix | eligible finite terminal使用O(chunks) partial/range | fallback仍可保留正式generic成本 |
| explain无法区分native/scalar | 不稳定handler summary与fallback reason | 不冻结字符串/阈值 |

Replacement closure是implementation exit的一部分；只新增新路径而不删除旧decision residue，S1不能关闭。

## 6. 风险登记

| Risk | Likelihood | Impact | Defense/Stop rule |
|---|---|---|---|
| planner输入扩张为第二套IR | medium | high | request只保存closed terminal物理需求；Canonical仍唯一semantic truth |
| mixed representation导致热循环分支 | medium | medium | once-per-Chunk handler dispatch；matched profile |
| run-level sum中乘法溢出 | medium | high | raw × length直接进入signed-128；boundary differential |
| two-pass materialization读两次反而变慢 | medium | medium | 只对pure typed；planner可选sequential/fallback；profile gate |
| conservative result lease过大拒绝合法任务 | medium | medium | 先保证安全；后续以bound precision独立优化，不先work求exact |
| floating并行改变bit result | high if admitted early | high | S1/S2默认defer；单独numeric proof |
| boolean nullable lowering被误判 | low/medium | high | conditional admission；presence/null fixture先行 |
| cell matrix扩张代码规模 | medium | high | profile-driven admission；未达收益直接删除，不留flag |
| complex operator拖垮专题 | high | high | S4默认none，需新profile与独立Product Owner裁决 |
| microbenchmark收益不进入真实场景 | medium | medium | S5三个reference application + composed regression |

没有风险需要在R2改变Blueprint或public semantics。风险均可在bounded implementation gate中防守。

## 7. 实施准备Gate建议

### 7.1 通用Gate

每个slice必须同时满足：

1. **Semantic**：Reference differential覆盖eligible与fallback；order/null/numeric/failure一致；
2. **Resource**：work前lease覆盖complete peak；tiny-budget在callback/data work前拒绝；
3. **Concurrency**：sequential/parallel等价；rejection/interrupt/failure后quiescent；
4. **Architecture**：无第二Owner、无dead adapter、无public/artifact/dependency扩张；
5. **Performance**：matched fresh-JVM候选收益超过噪声边界；未影响相关正式normal path；
6. **Delivery**：Java 8、runtime full、processor/generation、examples、package与`./scripts/check.sh`按变更面闭合；
7. **Review**：一个bounded独立只读审查，只验证该slice的主要claim，不启动无限审查循环；
8. **Closure**：Conformance更新、Temporary slice状态和干净提交后才进入下一slice。

### 7.2 性能判定方法

不为所有cell预设同一百分比。每个slice开始时：

- 在固定主机、JDK、rows、representation和operation上取得至少3个fresh JVM baseline；
- 用median与MAD/范围确定噪声；
- candidate效果必须大于`max(10%, 3 × observed relative MAD)`；
- 相关fallback/PLAIN normal path不得出现超过`max(5%, 3 × noise)`的稳定退化；
- 若收益只出现在synthetic microbenchmark而reference application无相应consumer，则删除该cell。

这是一种准入方法，不是跨硬件SLA。

## 8. Slice Readiness

### S1

状态：`DESIGN_READY / AUTHORIZATION_REQUIRED`。

输入完整：single decision、encoded integral handler、typed count/sum、overlay fallback、resource与evidence都已定义。

### S2

状态：`CONDITIONALLY_DESIGNED / DEPENDS_ON_S1`。

operation/type矩阵与defer边界已定义；精确cells必须由S1后profile选择，不允许一次铺满。

### S3

状态：`CONDITIONALLY_DESIGNED / DEPENDS_ON_S1-S2`。

ordered two-pass和O(chunks) resource模型已定义；需用真实materialization workload验证两次scan成本。

### S4

状态：`NOT_ADMITTED / DEFAULT_NONE`。

GroupBy/Join均不属于当前implementation input。未来若开启，必须重新设计和授权。

### S5

状态：`QUALIFICATION_MAP_READY / DEPENDS_ON_IMPLEMENTATION`。

## 9. 未决裁决

### 9.1 已由Candidate关闭

- 默认morsel：existing Chunk；
- 首批complex operator：none；
- first priority：AUTO encoded integral predicate/aggregate；
- callback/stateful/reference/index：fallback/defer；
- representation decode：不准入；
- floating aggregate：不准入S1/S2首批；
- public API/artifact/dependency：无变化；
- exact private type naming：implementation detail，不作为Product Owner裁决项。

### 9.2 Product Owner需要决定

只有两个生命周期裁决：

1. 是否接受本Candidate为Baseline Freeze输入；
2. 冻结后是否单独授予S1 implementation authorization。

两者不能因本次`PASS`自动推断。没有尚未讨论的产品语义需要Product Owner逐项补充。

## 10. R2交付标准核对

| R2要求 | Evidence | Result |
|---|---|---|
| R2.1 current state与capability inventory | `current-state-audit.md` | PASS |
| R2.2完整Candidate Design | `design.md`（现已固化） | PASS |
| R2.3有限feasibility/performance validation | `feasibility-validation.md` | PASS |
| R2.4一致性、过度设计与readiness | 本文件 | PASS |
| 不修改production/formal Design | git diff scope终检 | PASS |
| Java 8 targeted evidence | 4 tests + 1M AUTO/OFF benchmark | PASS |
| 无subagent | execution record | PASS |
| 无implementation authorization推断 | 全部文档status/claim boundary | PASS |

## 11. 自审结论

结论：`PASS / READY_FOR_PRODUCT_OWNER_REVIEW`。

本文件在pre-freeze时的结论为：Candidate Design已经足够完整，可以进入Product Owner最终审核，不应继续
增加设计内容或profile组合。Product Owner随后授权临时设计固化；最终裁决、收窄后的VP1-VP3范围和当前
authorization boundary见[Freeze记录](baseline-freeze-and-readiness.md)。
