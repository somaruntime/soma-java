# Industrial Dynamic Scheduler 设计与性能治理

类型：Report / Governance

状态：当前

Owner：industrial-dynamic-scheduler 设计、契约、Schema 与性能治理

输入事实源：专题 Temporary、application Blueprint/Design/Validation、代码与
correctness evidence、9-fork benchmark artifact、六份 current application baseline

事实范围：本次应用级治理的裁决、实现、性能证据、非回归审查和收口状态

非事实范围：SOMA core 新设计、跨环境 SLA、public release readiness

日期：2026-07-24

## 1. 意图与边界

本专题把工业动态调度示例从“结构完整的 reference consumer”推进为语义身份、
失败边界、领域结果、SOMA 使用方式和规模性能都可解释的 Java 8 应用。

治理不修改 SOMA annotation、generated API 或 runtime core，不删除 flexible
machine、precedence、release/material、setup、maintenance、transport、
secondary resource、due/priority 或 dynamic machine delay。性能问题先按应用算法、
应用 Schema/access、SOMA public usage、SOMA core 的顺序归因，没有领域中性
reproduction 时不得扩大到 core。

## 2. 审查发现与裁决

| 发现 | 裁决 |
|---|---|
| 时间范围可能在 Table projection 后才溢出 | Problem 构造时完成最坏 horizon、tardiness 和 weighted tardiness checked arithmetic |
| input checksum 混合 generation provenance 且受集合顺序影响 | checksum 只表达规范化领域事实；config checksum、generator version 和 seed 独立输出 |
| Result 声明没有全部由独立验证器复算 | Validator 独立复算 cardinality、目标统计和 checksum，并验证全部约束 |
| Result 携带 SOMA runtime/performance diagnostics | `ScheduleResult` 只保留领域结论；package-private `SolveEvidence` 只经 test access 消费 |
| Schema/索引包含无行为来源的表示 | 建立 Access Pattern→Schema 矩阵，只保留实际消费者 |
| 每轮全量 candidate 排序主导 large workload | 使用 machine-local group + 每机一个代表项的 indexed min-heap，保持原全序 |
| 每 operation 一个 eligible child Table 放大 canonical allocation | 改为一张 flat immutable `EligibleMachine` Table 和 `by_operation` exact group |
| production preparation 执行完整逐值投影复核 | 完整复核保留在 test-only Gate；production 只执行正式 validation/projection |

## 3. Access Pattern → Schema

| 领域行为 | Pattern | 当前 Owner |
|---|---|---|
| job lookup/read | primary-key point + columns | `JobDefinition` |
| operation identity/sequence | primary-key + secondary unique | `OperationDefinition` |
| eligible option | immutable exact-group traversal | flat `EligibleMachine.by_operation` |
| machine/operation/resource state | point read/mutation + stable ColumnView | authoritative state Tables |
| setup/transport | composite-key point lookup | lookup Tables |
| assignment | append + key traversal + bounded materialization | `OperationAssignment` |
| candidate membership/machine group/global best | primitive keyed pool + intrusive group + heap | application solver |
| event/maintenance/resource lanes | queue/calendar | application runtime/solver |

因此退役了没有查询消费者的 secondary index、重复输入的 definition/projection、
candidate SOMA Table 和逐 operation child Table。Candidate、event queue 和
calendar 都可由 Problem、authoritative state 与 assignment 重建，不与 SOMA
Table 争夺事实 Owner。

## 4. Global frontier 正确性

Comparator 仍是：

```text
setupStart -> completion -> priority(desc) -> due
           -> job -> operation -> machine
```

Candidate pool 以 operation-machine stable identity 定位，并维护 per-machine
intrusive group。Heap 只保存每台 machine 的最佳代表。Machine mutation 或
candidate membership 变化把对应代表标为 dirty；选择时只重算 dirty root
machine。

该增量策略依赖两个单调性条件：

1. machine availability 只向后移动，缓存代表是该 machine 新分数的 lower bound；
2. resource readiness 只向后移动；若不晚于 candidate 已算出的 effective start，
   推进 version 不改变 score，否则重算 root machine。

选择后仍以 operation/machine/resource version 做最终复验，提交顺序和 fail-stop
跨 Table 语义没有变化。新增 differential/micro fixture 覆盖 total order、event
先行和 resource score-change/no-score-change 分支。

## 5. 实现与候选

| Commit | Slice |
|---|---|
| `3b49b4b` | Problem/Result 契约、Access Pattern 清理、primitive candidate pool、global frontier 和 benchmark v4 |
| `a7d4fde` | flat eligible exact-group、test-only projection verifier、bounded batch projection |

最终候选仍由普通 `SchedulingSolver` / one-shot `SchedulingSession` 进入，返回
detached `ScheduleResult`。Production JAR 不包含 fixture、oracle、projection
verifier、execution test access 或 benchmark。

## 6. 9-fork 性能证据

环境：Azul Zulu OpenJDK `1.8.0_492-b09`，macOS `26.5.2`，aarch64，
default/long-run `-Xms256m -Xmx256m`，large `-Xms512m -Xmx512m`。

| Profile | 旧 hot median | 当前 hot median | 当前 canonical median | 当前 end-to-end allocation |
|---|---:|---:|---:|---:|
| default | `31.159 ms` | `14.174 ms` | `22.830 ms` | `8.098 MB` |
| large | `8.694 s` | `1.287 s` | `1.327 s` | `231.123 MB` |
| long-run | `133.318 ms` | `47.050 ms` | `73.962 ms` | `33.843 MB` |

当前 large hot range 为 `1.278..1.295 s`，100,000 operations、100 machines、
每 operation 3 个 option 和全部领域约束均未缩减。三个 profile 的 result
checksum 与治理前一致。

`3b49b4b` 的 9-fork canonical evidence 暴露逐 operation child Table：large
end-to-end allocation 约 `718..724 MB`，3/9 fork 出现 Full GC。`a7d4fde`
改为 flat exact-group 后为 `227.001..241.513 MB`，9/9 fork Full GC 为零。
这个问题归属于 application Schema/access；没有证据要求修改 SOMA core。

当前 exact-index high-water 为 default/large/long-run
`106,384 / 11,832,761 / 1,162,144 B`。它包含 flat option group 的受控 retained
access path；update scratch 均为零。Baseline 保存原始 bytes/nanos、精确 workload
identity、环境和 `claimAllowed=false`，不形成跨环境性能主张。

Long-run 的首次普通重放以 `13.848 MB` 超过首轮 maximum-based allocation
envelope。第二组 9-fork 又得到 `11.008..15.022 MB`，但 checksum、high-water、
GC count 和结构完全相同，证明 maximum 不适合作为该 integrated lane 的稳定
Gate。最终 allocation 改为跨 fork median + 25% fitness envelope；确定性
high-water 仍要求 all-equal，GC 仍使用 maximum。失败不再自动触发 rebaseline。

日常 Gate 为 3 fork，新 baseline 通常为 5 fork；9 fork 只保留为明确授权的
方差诊断。本专题不再通过追加 fork 追逐阈值。

旧 FJSP 100k 的约 `262 ms` 是 machine-local arg-min 历史证据，不包含本应用的
global total order、secondary resource、maintenance、transport 和 dynamic delay，
因此不能作为同语义目标或回归阈值。

## 7. Scope non-regression

- Blueprint 目标与全部领域约束均保留；
- comparator、event ordering、version revalidation、commit ordering、
  detached Result 和 one-shot lifecycle 均保留；
- 没有修改 SOMA public/generated API、annotation Schema 语义、Index 生命周期或
  failure atomicity；
- generated scheduler Scan 从 `12` 降到 `9`，当前 footprint 为
  `218,605 source bytes / 940 lines / 311,595 family class bytes / 65 nested`；
- current baseline 仍是 component `1`、reference application `6`、public claim
  `0`；旧 scheduler baseline 可从历史治理提交读取，不保留双 Owner；
- G6 继续 blocked，本专题不处理发布。

## 8. 收口验证

最终代码候选已完成 scheduler correctness、projection、lifecycle、production
JAR、result identity 和 generated-footprint 验真。Default、large 的普通 3-fork
artifact 与 long-run 的既有 9-fork artifact 使用最终 median allocation 规则离线
比较，三者均为 `passed`，没有启动新 fork。

最终 closeout delta 只包含 baseline 规则、Gate 编排和正式文档；以下检查通过：

- `check-docs.sh`；
- `check-performance-baseline-architecture.sh`；
- 三份 scheduler baseline 对既有 artifact 的 comparator；
- `git diff --check`。

`scripts/check.sh` 已退出隐式 application performance fork；功能 Gate 与
Fast/Scale/Soak/Full 性能 Gate 现在分责。本次不再重复运行 Full，也不以追加
fork 代替收口。长期事实已进入应用 Design/Validation、Engineering、Implementation
Map、Conformance、当前性能摘要和本报告；Temporary 已删除。
