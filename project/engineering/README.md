# SOMA Java Engineering

类型：Engineering Entry

状态：I0–I8 `COMPLETED`；Canonical IR/Execution S1–S6 `FROZEN / READY / NOT_AUTHORIZED`；
当前无 active implementation slice

Owner：SOMA Java implementation sequence、work unit 与 engineering evidence route

最后审查日期：2026-08-11

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
晋升、Baseline Freeze与targeted readiness，但Product Owner尚未授予implementation authorization；
因此S1尚未active，不能把I0-I8历史授权推断为本计划授权。

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
- [G1–G10](../conformance/v1-implementation-gates.md)
- [Current Conformance](../conformance/README.md)
- [I0–I8 qualification records](../conformance/README.md#4-active-records)
