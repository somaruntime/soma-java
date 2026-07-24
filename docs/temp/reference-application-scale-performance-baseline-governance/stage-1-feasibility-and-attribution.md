# Stage 1 Feasibility 与性能归因

类型：Temporary

状态：completed（Stage 1）

Owner：SOMA reference application scale performance baseline governance

正式事实源：否

事实范围：六个目标 workload 的单 fork 可行性、heap 裁决和 scheduler 归因

非事实范围：正式 threshold、多 fork baseline、public performance claim

环境：Zulu JDK `1.8.0_492-b09`、macOS `26.5.2`、aarch64、Apple M5 Pro

最后审查日期：2026-07-24

## 1. 六个 workload

以下数据均为 `warmup=0`、一个 measurement、独立 JVM 的诊断结果；只证明目标
workload 可执行，不形成 baseline。

| Application/profile | Heap | Hot operation | Allocation | Young/Full GC | 结果 |
|---|---:|---:|---:|---:|---|
| scheduler default | 256 MiB | 102.2 ms | 13.20 MB | 0 / 0 | 完成 |
| scheduler large | 1 GiB | 61.80 s | 21.61 GB | 77 / 0 | 完成但存在异常 allocation |
| scheduler long-run | 512 MiB | 563.4 ms | 290.55 MB | 2 / 0 | 完成 |
| simulation default | 256 MiB | 109.5 ms | 4.22 MB | 0 / 0 | 完成 |
| simulation large | 1 GiB | 5.98 s | 430.28 MB | 2 / 0 | 完成 |
| simulation long-run | 512 MiB | 3.64 s | 48.28 MB | 0 / 0 | 完成 |

六个 workload 均得到稳定的 input/result checksum、Schema hash 和 runtime-plan
hash；simulation large/long-run 保持非零 population growth，scheduler long-run
使用 400 个合法 machine-delay event。

## 2. Scheduler 归因

large workload 的约 3 亿次 candidate refresh 是当前全 frontier 动态重算算法的
预期成本，但 21.61 GB allocation 不是该算法必需成本。

63 秒 JFR 诊断得到：

- 5,309 个 execution sample 中，3,853 个位于 generated `LongColumn#get`，
  424 个位于 `HashLongKeySpace#locate`；
- allocation sample 主要是 refresh callback 每个 candidate 构造
  `MachineId`、`JobId`、`OperationId`，以及 application
  `Map<Long, ResourceCalendar>` lookup 的 `Long` boxing；
- `sorted(...).limit(1)` 已命中 generated stable arg-min；
- Full GC 为 0，GC pause 不是 61.8 秒的首要原因。

因此问题分类为：

```text
example SOMA access/API usage
  + application runtime acceleration structure
  != SOMA core defect
  != full-sort defect
  != GC-pause defect
```

## 3. 同语义修复

修复只使用已有 primitive key overload，并将 immutable machine maintenance
和 mutable resource lane calendar 表达为 application-owned、可重建的按 Index
投影：

- refresh callback 不再为 point lookup 重建 Value Object；
- resource calendar 不再做 boxed `Long` probe；
- machine calendar 不再为每个 candidate 重开 owned child ColumnView；
- SOMA Table 仍拥有 authoritative definition/state，投影只承担领域算法加速；
- comparator、constraint、system 顺序、failure boundary、Schema/API 均未改变。

large 单 fork同 workload A/B：

| 指标 | 修复前 | 修复后 | 变化 |
|---|---:|---:|---:|
| solve | 61.795 s | 40.129 s | -35.06% |
| allocation | 21.608 GB | 493.85 MB | -97.71% |
| result checksum | `ab0d9c...ad32a` | `ab0d9c...ad32a` | 相同 |
| Schema/runtime plan | 相同 | 相同 | 无变化 |
| Full GC | 0 | 0 | 无回归 |

该 A/B 仍是单 fork 诊断，正式结论必须等待 9-fork calibration。

## 4. Heap 裁决

修复后重新从 256 MiB 检查：

- scheduler large 在 256 MiB 出现 16 次 Full GC / 444 ms；512 MiB 为
  9 次 Young GC / 18 ms、Full GC 0，因此选择 512 MiB；
- scheduler default/long-run 在 256 MiB 无 Full GC；
- simulation 三个 profile 在 256 MiB 均无 Full GC，large 为 9 次 Young GC /
  9 ms，long-run 为 1 次 / 1 ms。

最终固定 heap：

| Application | default | large | long-run |
|---|---:|---:|---:|
| scheduler | 256 MiB | 512 MiB | 256 MiB |
| simulation | 256 MiB | 256 MiB | 256 MiB |

Heap 是 baseline exact identity。后续普通 Gate 不允许临时改大后继续比较。

## 5. Stage 1 结论

- 已确认全部目标规模可执行，不需要缩小 workload；
- warmup 1、large/long-run measurement 1 可行；
- scheduler 的显著临时对象问题已完成 application-owned 同语义修复；
- 没有证据支持修改 SOMA core、public/generated API 或 Schema；
- Stage 2 可以实现 v3 artifact、profile runner 和 Fast/Scale/Soak/Full Gate。
