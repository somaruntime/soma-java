# SOMA benchmarks

本目录是 SOMA 的长期、非 production 性能与场景正确性工程。它不是示例代码、第三项 production
artifact、公开性能 SLA 或 release claim。

```text
three reference applications
    -> deterministic benchmark consumers
        -> fresh-JVM runner
            -> machine-readable raw results
                -> correctness validation and comparative summary
```

当前 benchmark 使用三个正式 reference application：

- `scheduling`：Medium shape，覆盖 ingest、scan、Key/Index、Join、top、GroupBy 和 point update；
- `simulation`：Narrow shape，覆盖 ingest、scan、Key/Index、stable top 和 remove；
- `real-time-dispatch`：Reference-mixed shape，覆盖 ordinary Object、scan、Key/Index、Join 和 remove。

从 repository root 运行：

```sh
./scripts/benchmark.sh
```

常用诊断配置：

```sh
SOMA_BENCHMARK_ROWS=100000 \
SOMA_BENCHMARK_RUNS=1 \
SOMA_BENCHMARK_PARALLELISM=8 \
./scripts/benchmark.sh
```

默认使用可移植的 process wall-time 采集。需要在允许读取操作系统进程统计的本机采集 peak RSS
时，可显式设置 `SOMA_BENCHMARK_TIME_MODE=extended`；该模式不是 correctness 的前置条件。

正式比较必须使用相同 commit/source、JDK/JVM、workload、row count、parallelism 和 fresh-JVM
run count；Smoke 只证明测量链可运行，不能证明性能提升。完整证据合同与当前专题边界见
[`project/temp/soma-v1-performance-correctness-governance`](../project/temp/soma-v1-performance-correctness-governance/README.md)。
