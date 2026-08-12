# SOMA Java Engineering

类型：Engineering Entry

状态：I0–I8 `COMPLETED`；Canonical IR/Execution S1–S6 `COMPLETED`；
Physical Execution Engine M2 `IMPLEMENTATION_AUTHORIZED / P1-P4_COMPLETED / P5_ACTIVE`

Owner：SOMA Java implementation sequence、work unit 与 engineering evidence route

最后审查日期：2026-08-12

## 当前状态

Product Owner 已授权并完成当前 V1 的 I0–I8 implementation。每个 slice 的实现、exit、独立审查
与 Conformance 已闭合，G1–G10 为 `PASS`。这不授权 GitHub Release/Package、Maven publication、
签名或正式 release 声明。

I0-I8历史实施的唯一规范性计划为
[V1 Production Implementation Plan](v1-implementation-plan.md)。阶段状态：

```text
I0 build/full-regeneration
 -> I1 primitive Table
 -> I2 schema/type/storage breadth
 -> I3 query/IR/reference interpreter
 -> I4 mutation/resource/failure
 -> I5 Group/Join
 -> I6 bounded parallel
 -> I7 compression/metadata
 -> I8 scenarios/performance/security/package
COMPLETED
```

I0–I8 名称继续存在于计划和 Conformance 中，作为历史实施与证明链；当前 executable tests 和
qualification internals 已按长期 capability 组织，不再把实施 chronology 当作仓库主结构。
I2–I4 的 point-in-time consumer/golden snapshot 保存在 [`history/`](history/README.md)，不作为
current qualification 或 compatibility target。

Canonical IR与执行引擎M1责任替换由独立的
[S1-S6实施计划](canonical-ir-execution-engine-implementation-plan.md)拥有。该计划已经完成正式Design
晋升、Baseline Freeze与targeted readiness，Product Owner随后授予完整实施授权；S1-S6已经依次完成
exit evidence、Conformance与干净提交，[最终S6资格](../conformance/canonical-ir-execution-s6-final-qualification.md)
关闭production replacement gap。冻结计划继续作为实施顺序与exit的历史Owner，不复制current状态。

Physical Execution Engine M2由
[P1-P6正式计划](physical-execution-engine-m2-implementation-plan.md)拥有实施顺序，正式
[晋升与准入记录](../conformance/v1-physical-execution-engine-m2-promotion-readiness.md)拥有授权和current
状态。Product Owner已于2026-08-12授权完整治理；P1-P4已经`PASS`，当前只激活P5。

## 当前工程入口

- 日常 correctness 与 local delivery：`./scripts/check.sh`；
- 完整 non-publishing qualification：`./scripts/qualify.sh`；
- 独立 profile：`./scripts/benchmark.sh`；
- 本地 package：`./scripts/package-local.sh`；
- 能力级 fixtures：[tests](../../tests/README.md)；
- qualification/build internals：[build-support](../../build-support/README.md)。

## 规则

- 新 surface 必须满足根 `AGENTS.md` 的 surface admission；
- Blueprint/Design 语义变化先回到 Owner 或 bounded Temporary；
- code/build-support/tests 拥有 current executable fact，Conformance 记录证据与 claim boundary；
- reference interpreter 仍是 optimizer/parallel correctness oracle；
- 不恢复 predecessor 或增加 compatibility layer、第三 production artifact；
- benchmark、package 或 workflow PASS 不自动构成 release authorization。

## Evidence route

- [Design](../design/README.md)
- [Core abstractions](../design/core-abstractions-and-narratives.md)
- [Implementation Plan](v1-implementation-plan.md)
- [Canonical IR / Execution Engine Plan](canonical-ir-execution-engine-implementation-plan.md)
- [Physical Execution Engine M2 Plan](physical-execution-engine-m2-implementation-plan.md)
- [G1–G10](../conformance/v1-implementation-gates.md)
- [Current Conformance](../conformance/README.md)
- [I0–I8 qualification records](../conformance/README.md#4-active-records)
