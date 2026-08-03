# SOMA Java Engineering

类型：Engineering Entry

状态：Implementation Planned / Final Global Review PASS / Awaiting Product Owner Authorization

正式事实源：是（实施计划与工程路由）

Owner：SOMA Java implementation sequence、work-unit与engineering evidence route

最后审查日期：2026-08-03

## 当前状态

新的大规模编译式Table引擎Blueprint/Design与核心抽象专题已经正式晋升，并通过
[实施前最终全局一致性审核](../conformance/v1-final-pre-implementation-global-consistency-review.md)。
Production implementation尚未授权、尚未开始；I0-I8全部`NOT_STARTED`。

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
- implementation、commit、push、release/package分别需要明确授权。

## Evidence route

- Design contract：[Design](../design/README.md)
- Gate definition：[V1 Implementation Gates](../conformance/v1-implementation-gates.md)
- Current conformity：[Conformance](../conformance/README.md)
- Current readiness verdict：[Final Global Consistency Review](../conformance/v1-final-pre-implementation-global-consistency-review.md)
- Architecture skeleton：[Core Abstractions and Narratives](../design/core-abstractions-and-narratives.md)

在I0出现前，没有production build/test command可运行。Documentation Gate不能替代未来Java 8
compile/runtime/performance/package Gate。
