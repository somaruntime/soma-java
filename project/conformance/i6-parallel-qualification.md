# I6 parallel qualification

状态：I6_SCOPE_PASS（bounded typed-count parallel slice）；不是完整 I6/G7。

覆盖：

- `SomaConfiguration` 冻结后的 application-owned `ForkJoinPool` 选择；
- generated `Selection.parallel().count()` 的 Java 8 surface；
- fixed contiguous range、shared pool、typed predicate result 与 sequential count 等价；
- caller participant + P-1 drainer、saturated same-pool progress 与 all-submission start gate；
- worker-local read token 拒绝 foreign-thread View 访问；
- opaque callback 保持 caller-thread barrier；
- shutdown pool 返回 `PARALLEL_EXECUTOR_UNAVAILABLE`，不静默 fallback；
- unsupported parallel terminal 在 claim 前返回 structured `INVALID_ARGUMENT`；
- I1-I5 regression 与 clean generated consumer。

未覆盖且不作声明：

- P=1/2/4/16 完整矩阵、interrupt/cancellation/quiescence；
- parallel materialization/mapped/short-circuit、GroupBy/Join、mutation、full numeric merge；
- 完整 G7、百万行并行性能或加速比。

正式 Blueprint/Design 未修改。
