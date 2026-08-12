# SOMA Finite Physical Pipeline与Chunk-Morsel Execution实施计划

类型：Active Bounded Temporary / Frozen Implementation Plan

状态：`FROZEN / READY_FOR_IMPLEMENTATION / IMPLEMENTATION_AUTHORIZATION_NOT_GRANTED`

Baseline ID：`vector-pipeline-expansion-vp1`

Owner：本专题VP1-VP3顺序、每个slice的范围、exit evidence、性能守卫、恢复点和replacement closure

冻结日期：2026-08-12

上游：[冻结临时设计](design.md) · [current-state盘点](current-state-audit.md) ·
[bounded feasibility](feasibility-validation.md) ·
[Baseline Freeze与Readiness](baseline-freeze-and-readiness.md)

正式约束：[Planning](../../design/planning-and-optimization.md) ·
[Execution](../../design/execution-and-concurrency.md) ·
[Architecture](../../design/implementation-architecture.md) ·
[Storage](../../design/data-model-and-storage.md) ·
[Core](../../design/core-abstractions-and-narratives.md)

> 本计划已经冻结，但没有授权production修改。只有Product Owner后续明确授予本专题implementation
> authorization后，才能激活VP1；已有I0-I8或Canonical IR S1-S6授权不能自动扩张到本计划。

## 1. 目标与交付边界

本计划只实现已经由1M AUTO/OFF证据支持的两项扩展：

```text
VP1  encoded-native integral count/sum/predicate
    -> VP2 ordered long[] representation-native + parallel materialization
        -> VP3 global qualification + formal promotion + Temporary closure
```

它不新增Library user能力，不改变Canonical semantics。最终交付应当让已有Java frontend在相同API下选择
更低dispatch与数据搬运成本的physical mechanism，同时保留现有optimized path作为不适合shape的唯一
production fallback。

本计划明确不交付：

- boolean、其他primitive array、min/max/average/summary或floating kernel扩张；
- callback、mapped、stateful、IndexSelection residual、GroupBy、Join或mutation specialization；
- general pipeline DAG、Batch/Vector container、runtime codegen、sub-Chunk scheduler或plan cache；
- public/generated API、第三production artifact、dependency、Java版本或SOMA Engine变化。

## 2. 固定实施协议

1. 一次只有一个active slice；VP1关闭前不进入VP2，VP2关闭前不进入VP3；
2. 每个slice按`Reference/正式合同 -> final Physical decision -> admitted execution -> differential/profile`
   顺序推进；
3. 不建立并长期保留old/new feature flag；新机制未达Gate时只撤销该slice自己的增量，回到上一个已验证的
   干净checkpoint，不覆盖用户或其他slice修改；
4. 只验证changed surface，不重复无新输入的全量profile；重型证据集中在VP3；
5. Gate只需要一次bounded独立只读审查；不得以重复审查替代修复或交付；
6. 每个slice同时关闭功能、代码、架构和工程四类Claim，且Conformance/current route准确后才能提交；
7. 未获明确授权前，本计划只作为implementation input，不得触碰production source。

启动时production baseline：

```text
branch: develop
source HEAD: e2ce237736cd6042fbce2d90c2522e1a280e7d7c
```

实施实际开始时必须重新记录HEAD、worktree、Java 8、Maven和fixed-host facts；dated benchmark只作比较输入，
不能假装是当前结果。

## 3. 全部slice共享的不变量

| ID | 必须成立的事实 | 最早防线 | 主要Evidence |
|---|---|---|---|
| VP-INV-01 | Canonical/Bound/Normalized语义与Reference独立性不变 | Planner/Reference边界 | independent-state differential |
| VP-INV-02 | terminal、representation、partition和resource只形成一次final Physical decision | Planning | code-shape、plan/explain、replacement scan |
| VP-INV-03 | Execution只消费admitted plan，不重新eligibility或增加scratch | ExecutionFrame | tiny-budget、fault injection、code review |
| VP-INV-04 | borrowed typed/run access不逃逸operation，也不成为第二storage truth | Storage access token/scope | currentness、mutation regression、escape review |
| VP-INV-05 | PLAIN/AUTO/overlay拥有相同logical result、order、numeric和failure | kernel/fallback | Reference + AUTO/OFF differential |
| VP-INV-06 | parallel只改变participation，不改变result；返回前quiescent | shared ordinal-work lifecycle | P=1/2/4/16、rejection/interrupt |
| VP-INV-07 | known result、partial、prefix、task与scratch在work前checked admission | ResourceEstimate/lease | tiny-budget pre-work failure、allocation attribution |
| VP-INV-08 | 未冻结cell不能被实施“顺手”激活 | frozen capability matrix | source/capability diff + review |

## 4. VP1 — Single final decision与encoded-native integral scan

状态：`FROZEN / AUTHORIZATION_REQUIRED`。

### 4.1 目标

把当前base row plan之后的terminal-family refinement收口到同一Planning Owner的一次final decision，并让
integral encoded representation以run-level机制执行已准入的count/sum/predicate，不再逐logical row展开。

### 4.2 精确范围

准入：

- Table scan count，无predicate或simple pure typed integral predicate；
- schema-known integral Field sum，无predicate或simple pure typed integral row predicate；
- PLAIN direct typed array；
- integral encoded plain direct handler；
- single-distinct-required-leaf RLE run handler；
- mixed root once-per-Chunk handler dispatch；
- overlay/current-value scalar handler或whole-operation existing optimized fallback；
- sequential与现有Chunk-morsel aggregate parallel；
- checked count与signed-128 exact integral accumulation。

不准入：VP2 materialization、boolean、floating、callback、mapped/stateful、Index/Group/Relation和任何新terminal。

### 4.3 实施叙事

```text
closed terminal requirement
    -> existing Canonical planner produces final PhysicalPlan once
        -> bind integral leaf + finite predicate + representation handlers
        -> project final ResourceEstimate once
            -> lease
                -> ExecutionFrame dispatches each Chunk once
                    -> PLAIN typed loop
                    -> encoded run-level count/sum
                    -> overlay scalar/current fallback
                        -> ordinal exact merge
```

允许的最小代码形状变化：

- 重塑现有private planner request/decision carrier，使terminal requirement在final plan构造时可见；
- 为integral encoded column增加closed、package-private、operation-scoped run access；
- 按Owner拆开data-only decision、representation access和finite kernel loop。

必须删除或退役：

- 完成使命的`PhysicalRefinement`/`executeFamilyRefined`分支及其重复eligibility/resource delta；
- kernel热循环内对已经由plan决定的shape/representation重新规划；
- 只服务实验的adapter、flag、fallback branch或重复explain truth。

如果实际代码证明某个旧helper仍被非finite family合法使用，可以保留其通用职责，但不能继续作为第二个
terminal/kernel decision Owner；此类偏差必须在slice Conformance中说明。

### 4.4 Resource与失败

- sequential count/sum：固定Frame/predicate state，`O(1)` row-independent allocation；
- parallel count/sum：`checked(C × partialWidth + taskState)`，不得建立O(N) locator membership；
- encoded run不构造run object、不整体decode、不跨terminal cache；
- 不建立general multi-leaf run zipper；不同run boundary的多leaf shape显式使用encoded scalar/whole fallback；
- `raw × runLength`直接进入signed-128 state；只有最终long结果越界才产生正式overflow；
- pool、resource、interrupt与unexpected Error沿用现有failure/quiescence合同。

### 4.5 Exit evidence

功能：

- PLAIN、encoded plain direct、single-leaf RLE native、multi-leaf RLE scalar fallback、
  mixed representation与overlay/current fallback；
- zero/one/many Chunk、empty、all-match/no-match、boundary integral values与long final overflow；
- Reference vs optimized sequential vs parallel exact differential；
- unsupported callback/stateful/mapped shape仍走existing optimized path。

架构与资源：

- final plan/explain证明single decision和明确handler/fallback；
- exact-symbol/call-path审查证明无第二terminal refinement Owner；
- 对需要temporary的路径，tiny-budget在任何O(N)/task allocation或data work前拒绝；operation后temporary归零；
- borrowed run/typed access不跨Frame/current root逃逸。

性能：

- 固定主机、Java 8、1M、相同AUTO/OFF分布，各取得至少3个fresh JVM baseline/candidate；
- admitted AUTO cell收益必须大于`max(10%, 3 × observed relative MAD)`；
- PLAIN与unsupported fallback不得出现超过`max(5%, 3 × noise)`的稳定退化；
- 若encoded收益不能覆盖新增复杂度，删除encoded-native实现，VP1不得以“结构已完成”关闭。

交付：targeted tests、`mvn -pl soma-runtime test`、changed generated/consumer path、Markdown/status、
`git diff --check`、一次bounded review、Conformance和干净slice commit。

## 5. VP2 — Ordered `long[]` materialization与Chunk-morsel parallel

状态：`FROZEN / BLOCKED_BY_VP1 / AUTHORIZATION_REQUIRED`。

### 5.1 目标

在VP1 single decision和representation handler基础上，让现有ordered `long[]` materialization获得
encoded-native write与bounded parallel path，不引入O(N) locator staging或第二scheduler。

### 5.2 精确范围

准入：

- schema-known long Field materialization；
- 无predicate：Chunk logical prefix直接决定不重叠output ranges；
- simple pure typed integral predicate：conservative-admitted two-pass count/prefix/write；
- PLAIN direct、encoded plain/RLE direct write、overlay scalar/current fallback；
- sequential与explicit parallel共享同一final plan和ordinal-work lifecycle。

不准入：其他primitive array、reference materialization、callback filter/map、stateful operation或sub-Chunk。

### 5.3 实施叙事

无predicate：

```text
admit upper-bound result + task state
    -> allocate exact detached long[]
        -> each Chunk writes its canonical fixed range
            -> quiesce -> hand off
```

有pure typed predicate：

```text
admit upper-bound result + O(C) counts/prefix + task state
    -> pass 1: per-Chunk qualifying count
        -> caller checked ordinal prefix + exact result length
            -> allocate exact detached long[]
                -> pass 2: same pure predicate writes fixed ranges
                    -> quiesce -> hand off
```

两次读取只允许用于data-only pure typed predicate；callback或外部可变state不得进入该路径。

### 5.4 Resource与失败

- 无predicate peak：`checked(N × 8 + taskState + bounded ordinal state)`；不分配count/prefix数组；
- 有predicate peak：`checked(N × 8 + C × countPrefixWidth + taskState)`；lease覆盖upper-bound result，
  实际数组在exact prefix后分配；
- 不构造O(N) locator/result staging，不整体decode encoded Chunk；
- pool rejection、interrupt、worker failure或allocation failure均不得发布partial result，返回前quiescent。

### 5.5 Exit evidence

- empty/partial/all-match、multi-Chunk、RLE run跨boundary、mixed/overlay；
- sequential/parallel exact array、canonical order和detached result differential；
- P=1/2/4/16、pool rejection/interrupt/failure arbitration；
- tiny-budget pre-pass拒绝与operation后accounting归零；
- allocation证明除detached result与O(C + P) bounded state外没有O(N) companion buffer；
- fixed-host matched profile证明AUTO和parallel收益超过slice噪声门槛，PLAIN/fallback无实质退化；
- runtime full、受影响generated consumer、一次bounded review、Conformance和干净slice commit。

如果two-pass在matched workload没有稳定收益，则保留sequential representation-native路径并删除parallel
分支；不得保留关闭的feature flag或dead path。该收窄是M1 mechanism裁决，不得改变public语义。

## 6. VP3 — 全局资格、正式晋升与Temporary closure

状态：`FROZEN / BLOCKED_BY_VP1-VP2 / AUTHORIZATION_REQUIRED`。

VP3不新增能力，只证明最终candidate可以成为正式baseline：

1. targeted与runtime full；processor/generation只按受影响surface重放；
2. `./scripts/check.sh`、Java 8、two-artifact、package/source delivery与no public ABI drift；
3. 10K fixed cost、1M matched profile、10M capacity；PLAIN/AUTO、sequential/parallel、eligible/fallback；
4. allocation/temporary/heap与Reference differential；
5. scheduling、simulation、第三reference application的correctness与受影响journey回归；
6. old refinement、adapter、flag、dead fixture和重复Owner exact scan；
7. 将稳定M1增量晋升到Planning、Execution、Architecture、Storage/Core中实际需要的唯一Owner；
8. 建立最终Conformance，更新project/AGENTS routes，删除本Temporary。

VP3不得把同机数字外推成跨硬件SLA、一亿行承诺、GitHub Release/Package或正式release授权。

## 7. Stop rules

出现以下任一情况立即停止当前slice并等待Product Owner裁决：

- 需要改变Blueprint、public/generated API、Canonical node或Reference semantics；
- 需要第三artifact、新dependency、Java版本、Vector/native API或runtime codegen；
- 需要第二planner/executor/scheduler/resource/storage truth；
- 需要整体decode、跨terminal cache、sub-Chunk或通用DAG才能取得收益；
- 想激活冻结矩阵之外的type/terminal、GroupBy或Join；
- 正确性、资源和性能不能同时成立；
- matched profile没有新证据，工作却继续扩张测试、review或抽象；
- implementation authorization、proof chain或恢复点不明确。

## 8. Definition of Done

本计划只有在VP1-VP3依次关闭后才完成。最终状态必须同时满足：

- production只保留一个final Physical decision和一个parallel lifecycle；
- 已准入encoded integral与ordered `long[]`路径满足正式semantic/resource/failure合同；
- 未准入cell没有placeholder、flag、dead class或暗示性文档；
- 相关normal path没有无法解释的性能或分配退化；
- 稳定事实由正式Design/Conformance接管；
- 本Temporary完成replacement closure并删除。
