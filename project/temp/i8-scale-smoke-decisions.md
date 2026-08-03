# I8 narrow-scale smoke decisions (temporary supplemental note)

本子切片只验证当前已实现的 Scalar Table path 在 1,000,000 行下的 correctness 与 telemetry；
可选 smoke 参数限定为 `[16, 5,000,000]`，以满足固定 16-way GroupBy 与 remove survivor probe。
不把 smoke 时间或 used-memory 直接升级为性能资格，不引入未经批准的 threshold、benchmark
baseline、Loader、Batch 或新的 production API。

如果 smoke 暴露 add/query/allocation/resource 的真实瓶颈，下一步先建立 profile evidence 和
唯一 Owner，再决定 internal 优化；不能在本子切片中静默改变 Blueprint/Design 语义。

本次 scale smoke 暴露了 long-key append 的线性查找坏味道。实现沿用已有 StateRoot/Group
guard 合同，增加 ScalarTableRuntime 的 long-key sidecar：在所有 checked preflight 与 index
capacity check 完成后，于 guard 内完成 bounded payload/index mutation，随后和 payload 一起发布
新的 StateRoot；失败路径清理 append slot 和 sidecar，非 long-key schema 保留原有回退路径。该
优化只改变内部执行效率，不改变用户语义。1M smoke 多次观察约 280–330ms load，但不把它升级为性能
资格或 G9 结论。

120 秒 cooperative deadline 与 150 秒进程 watchdog 是运行时 safeguards，不把未专门触发的超时
分支写成 qualification proof。
