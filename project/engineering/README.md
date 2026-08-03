# SOMA Java Engineering

类型：Engineering Entry

状态：Implementation Authorized / I0 COMPLETE / I1 COMPLETE (I1_SCOPE) / I2 COMPLETE (I2_SCOPE) / I3 COMPLETE (I3_SCOPE) / I4-I8 NOT_STARTED

正式事实源：是（实施计划与工程路由）

Owner：SOMA Java implementation sequence、work-unit与engineering evidence route

最后审查日期：2026-08-03

## 当前状态

新的大规模编译式Table引擎Blueprint/Design与核心抽象专题已经正式晋升，并通过
[实施前最终全局一致性审核](../conformance/v1-final-pre-implementation-global-consistency-review.md)。
Product Owner 已于 2026-08-03 授予完整 V1 implementation authorization；I0 已`COMPLETE`，
I1 已完成 bounded primitive keyed Table slice，I2 已完成 bounded scalar type/storage breadth slice，
I3 已完成 bounded direct query/reference slice；I4-I8 仍`NOT_STARTED`，当前没有 active implementation slice。

I0 已建立真实 build/runtime/processor/consumer boundary，I1 又完成了 bounded generated Table
runtime，并分别通过 [I0 Qualification](../conformance/i0-build-spine-qualification.md) 与
[I1 Qualification](../conformance/i1-primitive-keyed-table-qualification.md)。这仍不证明完整
type breadth、performance、package与release。

## 唯一计划

- [V1 Production Implementation Plan](v1-implementation-plan.md)

计划顺序：

```text
I0 build/full-regeneration
    -> I1 primitive Table vertical slice
    -> I2 scalar type/storage breadth (bounded)
            -> I3 direct query/IR/reference interpreter
                -> I4 Selection mutation/failure/resource
                    -> I5 Group/Join
                        -> I6 bounded parallel
                            -> I7 compression/diagnostic closure
                                -> I8 scenarios/performance/security/package/release
```

## Engineering rules

- 一次只推进一个active slice；
- 每个slice同时交付positive、negative、failed-state与Conformance evidence；
- 第一条production路径已经是chunked、long-domain、IR-driven；
- reference interpreter先成为correctness oracle，再增加optimization/parallel；
- 不恢复predecessor source、module、test、benchmark或compatibility layer；
- new surface必须通过AGENTS.md的surface admission；
- stop rule触发时回到Temporary/Owner，不在code中静默选择；
- 当前授权允许实现、验证、独立审查以及每个 slice 闭合后的 commit/`develop` push；
- Blueprint/Design语义变化、stop rule、权限扩张、新dependency、第三artifact、证明链无法闭合
  或性能与正确性取舍必须暂停等待Product Owner；
- GitHub Release、Package、签名和正式发布声明仍需独立授权。

## Evidence route

- Design contract：[Design](../design/README.md)
- Gate definition：[V1 Implementation Gates](../conformance/v1-implementation-gates.md)
- Current conformity：[Conformance](../conformance/README.md)
- Current readiness verdict：[Final Global Consistency Review](../conformance/v1-final-pre-implementation-global-consistency-review.md)
- Architecture skeleton：[Core Abstractions and Narratives](../design/core-abstractions-and-narratives.md)

I0 的正式可重放 qualification command 是`./scripts/check-i0.sh`；其结果已绑定 commit、独立
审查并进入 Conformance。输入未变化时复用该证据，不机械重复完整 qualification；后续 slice
仍需运行与自身变更 surface 相称的 Gate。
