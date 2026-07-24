# Reference Application 大规模性能基线治理

类型：Temporary

状态：active（Stage 1）

Owner：SOMA reference application scale performance baseline governance

正式事实源：否

实施授权：Stage 1–5 归因、保持语义的 example/core 优化、evidence、Gate 与固化

事实范围：专题意图、目标 workload、非回归约束、阶段、停止条件和验收协议

非事实范围：当前正式性能结论、已实现 workload、public claim、SOMA 产品语义

起始实现基线：`7252646`

最后审查日期：2026-07-24

## 1. 意图

当前两个 Reference Application Integrated Performance Baseline 只测较小的
`default` workload，适合快速回归，但不足以回答大工作集、持续 mutation、
容量增长、累计 allocation 和 GC 是否稳定。

本专题在既有三层性能责任模型内增强第二层：

```text
Component Performance Baseline
  -> Reference Application Integrated Performance Baseline
       -> default / large / long-run
  -> Public Performance Evidence / Claim（本专题不建立）
```

治理不以放大数字为目标，而要让 workload 分别回答快速回归、规模压力和持续运行
三个不同问题。正式三层模型、现有 baseline 和 Gate 在候选完成前保持 current。

## 2. 目标结果

两个应用分别拥有三个 application-integrated workload：

| Application | `default` | `large` | `long-run` |
|---|---|---|---|
| industrial scheduler | 10 jobs × 100 operations，10 machines，3 candidates/operation | 1,000 jobs × 100 operations，100 machines，3 candidates/operation | 100 jobs × 100 operations，100 machines，3 candidates/operation，并增强 machine delay 与持续 update/remove |
| grassing simulation | 1,000 individuals × 1,000 ticks，128 × 72 logical world | 100,000 individuals × 1,000 ticks，1280 × 720 logical world | 10,000 individuals × 10,000 ticks，400 × 225 logical world，并保持持续 birth/death churn |

`correctness` profile 不承担性能基线，继续作为小规模 oracle、失败路径和原子性
验证。显示分辨率不等同于 logical world；benchmark 只登记实际参与系统循环的
logical cell 数。

目标职责：

- `default`：快速 artifact、identity、correctness 和性能回归；
- `large`：大 live set、宽 candidate/population、capacity 和吞吐；
- `long-run`：累计 allocation、GC、mutation、growth 和长期稳定性；
- Full Performance Gate：组合六个 workload，作为性能专题的完整准入；
- 所有 measurement、baseline、result 和 Report 保持
  `claimAllowed=false`。

## 3. 不可缩水约束

1. 已确认的 operations、individuals、ticks、machines、candidate count 和 logical
   world 不得因首次运行缓慢、OOM 或 Gate 时间增加而静默缩小。
2. 单 fork 只允许 feasibility 和诊断，不能建立、更新或宣称正式 baseline。
3. 正式校准使用同一精确环境的 9 个独立 JVM fork；普通 comparator 至少使用
   3 fork。昂贵 workload 的每 fork measurement 数由 Stage 1 根据证据裁决，
   但不得退化为单 JVM wall-clock。
4. workload、result、schema、runtime plan、environment、fork 和 claim identity
   必须 fail closed；环境不匹配只能得到 `not-applicable`。
5. correctness guard 在任何性能数字之前成立，验证逻辑不得污染 hot-operation
   timing/allocation measurement。
6. heap 是 baseline identity。不得在 OOM 后临时扩大 heap 而继续沿用原 baseline。
7. 性能退化或不可行结果是诊断证据，不得通过自动放宽阈值或改小 workload
   消除。
8. 旧 baseline 在新候选完整通过并原子切换前继续拥有 current 回归责任。

## 4. 产品与应用非回归边界

本专题不得改变：

- SOMA public/generated API、annotation Schema 语义；
- Access Model、Candidate Scan、Index 生命周期、ownership、swap-remove、
  lifecycle 和失败原子性；
- 两个应用的领域算法、约束、系统顺序、deterministic random、tie-break 和结果
  语义；
- input generation 与 runtime state 的职责分离；
- production 对 test/evidence/benchmark 的隔离；
- Zulu JDK 8 唯一验真边界、G6 状态和 public/release claim；
- component baseline 的领域中性责任。

允许后续 Stage 在既有设计内修改 application config、内部生产实现、
application-owned design、benchmark/evidence、test resource baseline、脚本、
checker、Engineering、Implementation Map、Conformance 和 Report。允许修改
SOMA generated/runtime 内部性能实现，但必须保持 public/generated API、Schema、
Access Model 与全部 runtime 语义，并用 component reproduction 与同语义 A/B
证明归因和收益。

性能问题先按 [归因与修复协议](performance-attribution-and-remediation.md)甄别。
“Example 变慢”本身不能直接授权 core 修改；“某个 Table 字段多”也不能直接授权
拆表。

## 5. 必须停止并请求决定

出现以下情况时，不得用实现方便性替代 Owner 裁决：

- 需要不兼容修改 public/generated API 或 annotation Schema；
- 需要改变 Access Model、runtime/Index/ownership/lifecycle/failure 语义；
- 需要改变两个应用的领域算法或删除 correctness、large、long-run 能力；
- 需要引入第三方依赖；
- 目标 workload 在合理且固定的环境下仍不可执行，需要修改既定规模；
- 性能缺陷只能通过本专题之外的产品设计或 core runtime 改造解决；
- 需要形成 public performance claim、支持矩阵或 release readiness 结论。

停止时必须保留失败 artifact、环境、workload identity 和诊断，不得先修改目标。

## 6. 阶段

### Stage 0：协议与起点

- 建立本协议并登记 active Temporary；
- 审计当前 workload、runner、baseline、Gate 和成本模型；
- 保持生产代码、配置、baseline 和正式 Owner 不变；
- 运行文档与完整 Gate；
- 提交 immutable starting point。

详细证据见 [Stage 0 基线审计](stage-0-baseline-audit.md)。

### Stage 1：详细设计与 feasibility

- 裁决 profile-specific artifact、heap、warmup、measurement 和 Gate 拓扑；
- 定义归一化指标及其分母、测量窗口和 aggregation；
- 建立 performance attribution、component reproduction 和同语义优化规则；
- 完成六个 workload 的单 fork feasibility；
- 形成风险、预计 Gate 成本和实施 slices，不建立 baseline。

### Stage 2：workload 与 evidence 实施

- 更新版本化 config 和 generator identity；
- 扩展 runner/validator，保持 setup 与 hot operation 边界；
- 建立 Fast、Scale、Soak 和 Full Performance Gate；
- 保持每个 slice 独立正确、可验证、可保留。

### Stage 3：多 fork 校准与 baseline

- 在精确 Zulu JDK 8 环境运行 9-fork 校准；
- 验证 checksum、schema、runtime plan、allocation、GC、growth 和 high-water；
- 建立六份 application-owned、环境感知、只读 baseline；
- 用独立普通 Gate 复核，不自动重写 threshold。

### Stage 4：结果审查与非回归

- 复核目标规模、职责和指标没有缩水；
- 比较 default/large/long-run 的可解释增长；
- 运行六个专项 workload、架构 Gate、完整 `./scripts/check.sh` 和
  `git diff --check`；
- 若暴露 core/product 问题，停在审查处请求决定。

### Stage 5：原子固化与退役

- 将长期规则固化到唯一 Engineering Owner；
- 更新 Implementation Map、Conformance、应用 validation 和 current Report；
- 形成正式 Governance Report，记录旧 baseline provenance；
- 删除 superseded current baseline 和本 Temporary；
- 恢复 `docs/README.md` 的无 active topic 状态；
- 重跑完整 Gate、提交并完成 scope non-regression。

## 7. 候选 Gate 责任

Stage 1 必须详细裁决，当前先冻结责任而不冻结脚本形状：

| Gate | 必答问题 |
|---|---|
| Fast | 两个 `default` 的 contract、identity 与快速性能是否回归 |
| Scale | 两个 `large` 的 live set、capacity、throughput 与 high-water 是否回归 |
| Soak | 两个 `long-run` 的累计 allocation、GC、growth 与 mutation 是否稳定 |
| Full Performance | 六个 workload、三层 ownership 和全部 comparator 是否共同通过 |

普通开发 Gate 与专项性能 Gate 可以有不同运行频率，但本专题收口不得只运行 Fast
而省略 Scale/Soak。运行频率不能改变 baseline 的正式性、fork 要求或失败含义。

## 8. 预期指标

保留现有 timing、current-thread allocation、Young/Full GC、pause、
exact-index/update/operation scratch high-water、capacity growth 和 deterministic
identity。Stage 1 还应定义：

- scheduler：`nanos/operation`、`bytes/operation`、maximum frontier；
- simulation：`nanos/tick`、`bytes/tick`，以及语义明确的
  individual-update 归一化指标；
- setup/preparation、input generation 和 correctness validation 的报告边界；
- live heap 或 process memory 是否有低扰动、可验证的 measurement 方法。

归一化指标只帮助解释增长，不能取代总耗时、总 allocation、GC 和实际 workload
identity。

## 9. 完成条件

只有同时满足以下条件，专题才可退役：

- 六个目标 workload 与职责全部实现且未缩水；
- 每个 workload 都有 correctness-guarded、多 fork、环境感知 baseline；
- Fast、Scale、Soak、Full Performance 和三层 ownership Gate 全部通过；
- 旧 default baseline 完成 provenance 和唯一 Owner 切换；
- public API、Schema、Access Model、runtime 和应用领域语义无回归；
- 正式 Owner、Implementation Map、Conformance 和 Report 原子同步；
- Temporary 删除，正式文档不引用它；
- 完整验证通过且全部修改已提交。

阶段性 feasibility、单 fork 数字、部分 profile baseline 或一份报告都不能代替
最终收口。

## 10. 专题文档

- [Stage 0 基线审计](stage-0-baseline-audit.md)
- [性能归因与修复协议](performance-attribution-and-remediation.md)
- [Workload 与测量详细设计](workload-and-measurement-design.md)
- [Gate 与 Baseline 详细设计](gate-and-baseline-design.md)
