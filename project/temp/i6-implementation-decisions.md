# I6 implementation decisions (temporary)

状态：ACTIVE DURING I6；本文件只记录 bounded slice 的实施边界，不修改正式 Blueprint/Design。

## Scope

本 slice 闭合 shared application-owned `ForkJoinPool` 的显式 parallel query 基础：generated
`Selection.parallel().count()` 对 typed expression 使用固定 contiguous ordinal ranges；调用线程
参与同一个 operation-local range queue，最多提交 P-1 个 drainer，所有 submission 成功前由 start gate 阻止 callback；terminal
同步返回，结果与 sequential count 相同。没有为每次 operation 创建线程池；未配置时沿用已经冻结的
`ForkJoinPool.commonPool()`；shutdown/rejection fail closed。

含 opaque callback 的 count 按 Design 作为 caller-thread barrier 执行；当前 bounded slice 不为
parallel `findFirst`、materialization、mapped stream、GroupBy、Join、mutation或完整 cancellation/
interrupt/quiescence contract 提供 surface 资格。worker 使用 operation-local read token，普通
foreign-thread View 访问保持 `CALLBACK_SCOPE_VIOLATION`。为了避免静默顺序 fallback，parallel marker 下
这些未准入 terminal 会以 structured invalid-argument failure 拒绝（当前实现只准入 typed count）。

## Evidence

`./scripts/check-i6.sh` 覆盖 custom pool、typed parallel count、caller participation、saturated
same-pool progress、start gate、foreign-thread rejection、callback barrier、sequential equivalence、
executor shutdown failure、participant/range bound 的 bounded evidence。PASS 只代表
本 bounded parallel count slice，不代表完整 G7。
