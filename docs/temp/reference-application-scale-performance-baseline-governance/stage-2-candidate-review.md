# Stage 2 候选审查

类型：Temporary

状态：completed（等待 immutable candidate 提交）

Owner：SOMA reference application scale performance baseline governance

正式事实源：否

事实范围：Stage 2 workload、evidence、Gate、measurement 隔离和同语义候选

非事实范围：9-fork baseline、正式 threshold、public performance claim

环境：Zulu JDK `1.8.0_492-b09`、macOS `26.5.2`、aarch64、Apple M5 Pro

最后审查日期：2026-07-24

## 1. 实现结果

Stage 2 已实现：

- 六个目标 profile 与独立 benchmark options；
- application artifact v3、规模和归一化字段；
- profile-aware runner 以及 Fast、Scale、Soak、Full Performance Gate；
- profile 固定 heap、连续 fork、精确目标规模和跨 fork identity 检查；
- benchmark build 输出与 IDE/shared `target` 隔离；
- reference-application repeat codegen/Schema 构建也使用两个独立 evidence target；
- compiler-error stub、stale log 和 append artifact 的 fail-closed 防护。

隔离不是性能优化。诊断曾发现 IDE 后台编译可以覆盖 application `target`
classfile，并留下抛出 `Unresolved compilation problem` 的 stub。Runner 现在只消费
evidence 目录中的 Zulu javac 输出，避免把错误 classfile 当作性能或产品结果。

## 2. Scheduler 同语义候选

Stage 1 的 application-owned primitive projection 已把 large 从约 `61.8 s /
21.6 GB` 降至约 `40.1 s / 493.9 MB`。Stage 2 继续只刷新 source version 已失效的
candidate，并按 resource version 缓存相同 units 的 earliest-ready 派生值：

- operation 在 frontier 生命周期内不发生中间 mutation，发布时的 version 保持
  当前；commit 后该 operation 的全部 candidate 立即 swap-remove；
- machine version 变化时完整重算 setup、maintenance fit 和 completion；
- resource version 变化时只在新 readiness 推迟 candidate 时重新 fit；
- cache 由 `CandidateFrontier` 私有拥有，以 resource version 失效，可由
  authoritative calendar 重建。

全局 comparator、event-before-setupStart、三个 source version 复验、commit
顺序、约束和结果均未改变。未引入新 SOMA API、Schema 字段或 core 修改。

## 3. 3-fork calibration-only 证据

以下只证明 Stage 2 候选可执行和稳定，不建立 baseline：

| Application/profile | Hot operation range | Allocation range | Young/Full GC |
|---|---:|---:|---:|
| scheduler default | `30.23..31.46 ms` | `15.88..16.05 MB` | `0 / 0` |
| scheduler large | `8.532..8.637 s` | `411.334..411.342 MB` | `5 / 0` |
| scheduler long-run | `132.00..140.35 ms` | `40.51..41.52 MB` | `1 / 0` |
| simulation default | `144.49..148.08 ms` | `11.505 MB` | `0 / 0` |
| simulation large | `5.908..5.918 s` | `426.378 MB` | `15 / 1` |
| simulation long-run | `3.607..3.631 s` | `47.351..47.354 MB` | `0 / 0` |

Simulation large 的一次 Full GC 在三个独立 fork 中稳定出现，pause 为
`25..30 ms`，不足 hot operation 的 `0.6%`，不属于 GC thrash。Stage 1 的非隔离
单 fork `Full GC=0` 不再用作候选事实；Stage 3 必须按 9-fork 实际 maximum 与
明确余量建立 envelope，不能手工改为 0。

六个 profile 的 input/result/schema/runtime-plan identity 均在 fork 内全等；
correctness profile 继续通过 tiny scheduler oracle 和逐 tick simulation AoS
oracle。Large/long-run 还分别验证完整 domain result 与持续 event/churn。

## 4. 历史 FJSP 对照边界

旧 FJSP 100k 的约 `262 ms` 中位数采用“最早可用 machine -> machine exact group
局部刷新/arg-min”，且没有当前应用的 secondary resource、maintenance、
transport、material 和动态 delay 组合。当前应用必须在 live frontier 上维护
`setupStart -> completion -> priority -> due -> stable identity` 的全局全序。

因此两者不是相同 workload 或相同算法语义。Stage 2 没有为了接近旧数字而改成
machine-local 选择；这会改变领域 tie-break，超出本专题授权。

## 5. Stage 2 裁决

- 六个目标规模没有缩小，warmup、measurement 和 fork 责任没有弱化；
- 现有证据归因为 measurement 完整性与 example 内部 runtime acceleration；
- 没有证据要求修改 SOMA generated/runtime core；
- public/generated API、annotation Schema、Access Model、Index/ownership/
  lifecycle/failure/runtime 语义均未改变；
- 两个应用的领域算法、system 顺序、determinism、结果和配置/运行时职责未改变；
- 候选可以提交为 immutable starting point，并进入 9-fork Stage 3。

Stage 2 收口验证包括六个 profile 的 3-fork calibration-only Gate、
`./scripts/check-docs.sh`、完整 `./scripts/check.sh` 与 `git diff --check`，均通过。
完整 Gate 继续验证 public API、codegen、runtime、external consumer、generated
footprint、component baseline 和 application production-JAR 边界。

本文件不把上述 3-fork 数字晋升为 current Report 或 public claim。
