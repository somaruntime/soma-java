# SOMA Java Engineering

类型：Engineering Entry

状态：Implementation Authorized / I0 COMPLETED / NO ACTIVE SLICE / NEXT I1

正式事实源：是（实施计划与工程路由）

Owner：SOMA Java implementation sequence、work-unit与engineering evidence route

最后审查日期：2026-08-04

## 当前状态

新的大规模编译式Table引擎Blueprint/Design与核心抽象专题已经正式晋升，并通过
[实施前最终全局一致性审核](../conformance/v1-final-pre-implementation-global-consistency-review.md)。
Product Owner 已于 2026-08-03 授予完整 V1 implementation authorization。I0 build spine 已于
2026-08-04完成并通过[正式资格](../conformance/i0-build-spine-qualification.md)：G1为`PASS`，
G2/G10为I0范围`PASS`且整体仍`IN_PROGRESS`。当前没有active slice，I1-I8为`NOT_STARTED`，
下一项只允许从I1开始。

`READY_FOR_IMPLEMENTATION`只表示设计、计划与Gate足以接受单独实施授权，不表示compiler、
runtime、API、performance、package或release已经存在。

## 唯一计划

- [V1 Production Implementation Plan](v1-implementation-plan.md)

计划顺序：

```text
I0 build/full-regeneration
    -> I1 primitive Table vertical slice
        -> I2 schema/type/chunk/Key/Index breadth
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
- 当前授权允许实现、验证、独立审查、每个slice闭合后的commit/`develop` push，以及
  [Conformance authorization contract](../conformance/README.md#6-implementation-authorization-contract)
  限定的CI、internal benchmark/profile、local package qualification和JUnit Jupiter 5.x
  test-only stack；
- Blueprint/Design语义变化、stop rule、权限扩张、已准入test stack以外的新dependency、第三
  production artifact、证明链无法闭合或性能与正确性取舍必须暂停等待Product Owner；
- remote artifact publication、签名和正式发布声明仍需独立授权。

## Evidence route

- Design contract：[Design](../design/README.md)
- Gate definition：[V1 Implementation Gates](../conformance/v1-implementation-gates.md)
- Current conformity：[Conformance](../conformance/README.md)
- Current readiness verdict：[Final Global Consistency Review](../conformance/v1-final-pre-implementation-global-consistency-review.md)
- Architecture skeleton：[Core Abstractions and Narratives](../design/core-abstractions-and-narratives.md)

I0已经建立第一条production build/test command：`./scripts/check.sh`。它只证明I0 build、
generation与artifact边界；不能替代I1-I8的runtime、performance、package或release Gate。
