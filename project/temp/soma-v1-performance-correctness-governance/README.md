# SOMA V1 性能与正确性联合治理

类型：Bounded Temporary / Performance and Correctness Governance

状态：`ACTIVE`

日期：2026-08-09

Owner：本专题的候选范围、实验计划、临时发现、优化假设与收口协调

> 本文不是 Blueprint、Design、Conformance 或公开性能声明。稳定事实必须在专题结束时晋升到
> 对应唯一 Owner；未成立的假设必须删除或明确回退，随后退役本 Temporary。

## 1. 目标

在不改变 SOMA V1 Blueprint、public/generated API、两项 production artifact topology 与 release
边界的前提下，以三个 reference application 和核心 runtime capability 为载体：

1. 建立可重复、可比较、能长期发现回归的 benchmark 基础设施；
2. 从 CPU、allocation、GC、RSS、SOMA-managed memory、compression 与并行扩展性识别正常路径瓶颈；
3. 验证实现是否保持正式 Design 的核心正确性、不变量和顺序/并行等价合同；
4. 对有端到端证据的瓶颈实施最小优化，对没有可信收益的实现修改予以回退；
5. 区分 SOMA runtime、generated code、Example access pattern 与 JVM/configuration 的责任；
6. 形成可供未来用户文档投影的最佳实践，但本专题不编写完整用户文档；
7. 形成有环境和 workload 边界的内部性能结论，不将本机结果外推为 SLA 或正式发布声明。

## 2. Design Intent 与事实边界

SOMA 的性能价值来自：application 使用自然 typed API 表达计算，compiler/runtime 在不改变逻辑
语义的前提下，把它 lowering 到 chunked column state、Key/Index、typed IR、specialized kernel、
compression 与 bounded parallel execution。

因此本专题不接受以下“优化”：

- 牺牲 result、order、null/missing、duplicate、numeric 或 failure 合同；
- 绕过 Group guard、resource admission、atomic publication 或 worker quiescence；
- 为 benchmark 场景加入 production 特化分支；
- 用 manual baseline 较弱的语义冒充等价 SOMA 产品能力；
- 把单次、warm-process 或不可重放的数字写成产品事实；
- 因异常误用或极端非法输入扩张正常路径实现复杂度。

现有正式事实保持不变：I0-I8 implementation 与 G1-G10 qualification 已完成；I8 百万行结果是
本机 qualification baseline，不是本专题优化结论或跨硬件 SLA。

## 3. 正确性职责

本专题承担 correctness verification，但采用风险与正常使用路径成比例的边界。

### 3.1 必须验证

- 三个 reference application 在每次实验中的业务 fingerprint 稳定；
- reference interpreter、optimized sequential 与 optimized parallel 在被优化能力上的结果等价；
- Key/Index、payload、compression representation 与 metadata 在 mutation 后属于同一 published state；
- point/Selection mutation 成功时结果与发布状态一致，失败时 zero partial publication；
- sequential/parallel 的 result、encounter order、numeric 与 non-resource failure 一致；
- Join/GroupBy 的 null、duplicate、missing、order 与 cardinality 不因 physical algorithm 改变；
- resource lease、Group guard、parallel worker 在正常完成和已覆盖的可恢复失败后释放；
- detached result 不持有 borrowed View、locator 或 backing storage；
- checked cardinality、array/container boundary 与 managed-memory admission 不被优化绕过；
- compression AUTO/OFF 只改变 representation 与 cost，不改变 logical value。

### 3.2 不扩张为本专题重点

- 使用 reflection、Unsafe、instrumentation 或改写 bytecode 突破封装；
- 已由 compiler negative suite 稳定覆盖、且本次没有改变的罕见非法 schema；
- application 自身的 data race、ordinary referent mutation、callback side effect 或跨 Table transaction；
- 没有新的实现路径、反例或风险信号时，对同一异常边界反复审查。

任何 correctness failure 都优先于性能结论。若无法在正式 Design 内修复，立即触发停止规则。

## 4. 性能证据等级

| 等级 | 目的 | 可以证明 | 不可以证明 |
|---|---|---|---|
| Smoke | benchmark 能构建、运行、产出完整 schema | 测量链可用 | 性能提升 |
| Diagnostic | 单次或少量重复、阶段与 profile 归因 | 候选热点与优化假设 | 稳定回归或公开 claim |
| Comparative | 同机、同 JDK/JVM/workload，独立 fresh JVM 的 before/after | 当前变更是否有可信端到端收益 | 跨机器 SLA |
| Qualification | 固定 fixture、重复运行、correctness/variance/resource Gate | 当前版本在限定环境达到内部资格线 | 一亿行承诺或正式发布 |

优化提交至少需要 Comparative evidence；长期阈值变化需要 Qualification evidence。

## 5. 工作负载矩阵

### 5.1 Reference journeys

| 场景 | 数据形态 | 正常路径重点 |
|---|---|---|
| Scheduling | Medium | ingest、scan、Key/Index、Join、top、GroupBy、point update |
| Simulation | Narrow | ingest、scan、Key/Index、stable top、remove |
| Real-time dispatch | Reference-mixed | ingest、scan、Key/Index、Join、ordinary reference、remove |

### 5.2 Core capability lanes

- reserve/repeated add 与 StateRoot growth；
- typed scan/filter/map 与 materialization；
- Key lookup、non-unique Index selection 与 canonical order；
- stable sort/top/distinct 与 numeric aggregate；
- GroupBy；
- Equality Join 的 lookup/hash/build-side 与 predicate pushdown；
- point/Selection mutation 与 candidate publication；
- AUTO/OFF compression、overlay/rebuild 与 read kernel；
- sequential/parallel scaling、task/participant 与 merge cost；
- retained/temporary accounting 与 peak RSS。

### 5.3 Scale curve

默认比较至少覆盖 `100K -> 1M`；在证据和本机资源允许时逐级扩大，而不是直接跳到最大规模。
本轮最多使用 16 个并行参与者和 32 GiB 内存 envelope。更高规模只能作为 diagnostic，不预先设定
一亿行硬 Gate。

## 6. Measurement contract

每份可比较结果必须记录：

- exact Git commit/tree state；
- OS、CPU、physical memory；
- Java vendor/version、Maven、GC、heap、SOMA budget、parallelism；
- schema/workload version、row count、seed 与 operation count；
- fresh JVM run count、warmup/measurement schedule；
- 每阶段 raw duration 与 min/median/max；
- fingerprint、sequential/parallel equivalence；
- RSS、GC、allocation/profile availability；
- retained、representation、plain-equivalent 与 temporary peak（可观测时）；
- baseline、candidate 与差值；
- claim level、异常与已知工具限制。

计时基础设施开销、manual baseline 与 SOMA 完整合同成本必须分别陈述。JFR method sampling 在当前
Corretto 8 / Apple Silicon 环境已有覆盖限制，不能假装细粒度 CPU hotspot 已被穷尽。

## 7. 优化控制循环

一次只允许一个 active optimization hypothesis：

```text
Reproducible baseline
    -> profile evidence
        -> one Owner and one bottleneck hypothesis
            -> smallest internal change
                -> targeted correctness
                    -> comparative fresh-JVM benchmark
                        -> full affected regression
                            -> keep or revert
```

保留条件：

- correctness 与资源合同全部成立；
- 受影响的端到端或核心 capability 指标有可重复收益；
- 没有把成本不可接受地转移到其他主要场景；
- complexity、memory peak 和维护成本与收益相称。

否则回退 production 修改，可以保留有长期价值的测量设施与反例测试。

## 8. 推进阶段

### P0 — Scope 与 baseline freeze

- 冻结当前 commit、环境和 I8 qualification provenance；
- 审核现有 profile 的测量偏差、正确性覆盖和规模限制；
- 定义 machine-readable benchmark result schema 与 runner contract；
- 建立 `benchmarks/` 的正式非 production benchmark surface admission。

### P1 — Correctness and benchmark spine

- 把三个 profile 从 Example test source 的临时 main 提升为长期 benchmark consumers；
- 保持 Example application 独立、benchmark 不进入 production artifacts；
- 建立 deterministic workload、fresh JVM orchestration、raw result 与 comparison；
- 为被测能力连接 reference/optimized/sequential/parallel correctness evidence。

### P2 — Baseline and attribution

- 重放 100K/1M 与必要的扩展曲线；
- 归因 ingest、query、relation、mutation、compression、parallel 和 memory；
- 排出按端到端影响排序的 bottleneck ledger；
- 只选择第一个有稳定证据的优化假设。

### P3 — Evidence-driven optimization

- 按第7节循环逐项优化；
- 每项保留 before/after、correctness、复杂度与回退结论；
- 若正常 application access pattern 是主要问题，优先修正 Example 并说明原因；
- 若 runtime/generated code 是 Owner，修正相应实现且不改变 public contract。

### P4 — Scale, ratchet and practices

- 重放三场景与受影响 capability；
- 建立不过拟合当前机器噪声的 regression ratchet；
- 总结 sequential/parallel、schema/Index、materialization、compression 和 memory 配置最佳实践；
- 区分内部 qualification 与未来可公开 claim。

### P5 — Formal closure

- 运行与变更范围相称的完整 correctness/qualification；
- 将长期 benchmark surface、测试与稳定结论晋升到各自 Owner；
- 在 Conformance 记录 exact evidence 与边界；
- 删除本 Temporary，不保留平行事实或失败 patch。

## 9. 停止规则

出现以下任一情况立即暂停当前优化并等待 Product Owner 裁决：

- public/generated API、Blueprint 或 Design 语义需要变化；
- correctness 与 performance 无法同时成立；
- 需要新的 production dependency、第三 artifact、Loader、off-heap、mmap 或隐式 spill；
- 需要改变 null/order/duplicate/numeric/failure/resource visibility；
- reference 与 optimized/parallel 无法建立可信差分；
- 结果规模或内存峰值无法在 16 core/32 GiB envelope 内安全控制；
- 重复运行不再产生新证据，或差异小于噪声却持续消耗时间；
- profile 工具缺陷使热点结论无法归因，且没有独立替代证据。

## 10. Definition of Done

只有以下事项全部成立，本专题才可关闭：

1. `benchmarks/` 拥有可重复、非 production、按场景和 capability 组织的长期入口；
2. 三个 reference journey 在 frozen baseline 与最终版本上均有 correctness fingerprint；
3. 被优化路径具备 reference/optimized/sequential/parallel 的相称等价证据；
4. 每个保留的 production 优化都有 raw before/after、环境、收益、代价与回退依据；
5. 没有收益或增加坏复杂度的候选已回退；
6. scale、RSS、GC、managed memory、compression 与 parallel scaling 已形成有边界结论；
7. 正常使用路径未发现未披露的 correctness blocker；
8. 最佳实践清单可供未来用户文档投影，但不成为平行 Design；
9. regression ratchet、日常 smoke 与高成本 qualification 分层明确；
10. Conformance 更新、Temporary replacement closure、Markdown/path/diff/build/qualification 检查完成；
11. 未扩大 release、publication、一亿行或跨硬件 SLA claim。
