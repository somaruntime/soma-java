# SOMA Java Engineering

类型：Engineering Entry

状态：Implementation Authorized / I0-I2 COMPLETED / NO ACTIVE SLICE / NEXT I3

正式事实源：是（实施计划与工程路由）

Owner：SOMA Java implementation sequence、work-unit与engineering evidence route

最后审查日期：2026-08-08

## 当前状态

新的大规模编译式Table引擎Blueprint/Design与核心抽象专题已经正式晋升，并通过
[实施前最终全局一致性审核](../conformance/v1-final-pre-implementation-global-consistency-review.md)。
Product Owner 已于 2026-08-03 授予完整 V1 implementation authorization。I0-I2已经完成，并分别通过
[I0资格](../conformance/i0-build-spine-qualification.md)、
[I1资格](../conformance/i1-primitive-keyed-table-qualification.md)、
[I2资格](../conformance/i2-schema-type-storage-breadth-qualification.md)：G1为`PASS`，G2-G3为I2范围
`PASS`，G4为I1/I2 direct-source范围`PASS`，G5为I1范围`PASS`，且这些Gate整体仍
`IN_PROGRESS`；G10为I0范围`PASS`且整体仍`IN_PROGRESS`。当前没有active slice，I3-I8为
`NOT_STARTED`，下一项只允许从I3开始。

`READY_FOR_IMPLEMENTATION`只拥有实施前准入事实；当前compiler/runtime/API能力以I0-I2资格记录
为边界，不能外推I3-I8、performance、package或release已经成立。

## 唯一计划

- [V1 Production Implementation Plan](v1-implementation-plan.md)

计划顺序：

```text
I0 build/full-regeneration (COMPLETED)
    -> I1 primitive Table vertical slice (COMPLETED)
        -> I2 schema/type/chunk/Key/Index breadth (COMPLETED)
            -> I3 direct query/IR/reference interpreter (NEXT)
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
- I0 qualification：[I0 Build Spine](../conformance/i0-build-spine-qualification.md)
- I1 qualification：[I1 Primitive Keyed Table](../conformance/i1-primitive-keyed-table-qualification.md)
- I2 qualification：[I2 Schema、Type 与 Storage Breadth](../conformance/i2-schema-type-storage-breadth-qualification.md)
- Current readiness verdict：[Final Global Consistency Review](../conformance/v1-final-pre-implementation-global-consistency-review.md)
- Architecture skeleton：[Core Abstractions and Narratives](../design/core-abstractions-and-narratives.md)

当前production qualification command为`./scripts/check.sh`。它回归I0-I1并证明I2
schema/type/storage/Key/Index scoped capability；不能替代I3-I8的optimizer、relation、parallel、
performance、package或release Gate。
