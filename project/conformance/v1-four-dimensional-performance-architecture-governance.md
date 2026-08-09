# SOMA V1 四维性能架构治理与优化

类型：Conformance / Performance Architecture Governance Record

状态：`PASS`

日期：2026-08-09

Owner：Design、Execution、Memory、CPU 四层性能因果、固定千万行 before/after、内部优化与剩余架构边界

## 1. 结论

本专题以三个正式 reference application、10,000,000 行 `composed` workload 和正常 API 组合为唯一主线，
在不改变 V1 Blueprint、Design semantic、public/generated API、dependency、production artifact、
logical result/order/failure、resource visibility 与 release boundary 的前提下完成四维治理：

- 建立 `Design -> Execution -> Memory -> CPU` 的性能因果模型，不再把低 CPU 使用率直接等同于
  “线程数不够”；
- GroupBy 根据已知 Key/Index cardinality 或保守小容量建立实际分组状态，不再一律按输入行数分配；
- primitive scalar GroupBy 直接读取 canonical primitive key，删除 generic hash/equality 的重复 payload
  读取和装箱路径；
- Relation 直接 `mapToInt/mapToLong(...).sum()` 融合为 canonical pair visit + 精确 signed-128
  accumulator，不再物化 O(Join output) 的 `long[]` 再二次扫描；
- benchmark harness 新增 process user/system CPU、平均占用 core、page fault 与 context switch 证据；
- Scheduling 的 core GroupBy 中位数从 869.772 ms 降至 561.768 ms，改善 35.4%；低基数 GroupBy
  从 231.287 ms 降至 85.959 ms，改善 62.8%；高基数 GroupBy 从 1698.356 ms 降至
  1069.804 ms，改善 37.0%；
- Scheduling fresh-JVM 总 CPU time 降低 12.6%，wall time 降低 14.2%，peak RSS 降低 5.9%；
- Simulation 与 Real-time Dispatch 的主要 terminal 保持在相对回归 ratchet 内；9 个最终 JVM 的
  correctness、shared/SOMA/composed fingerprint 全部稳定；
- 一个“单遍同时重建全部 Key/Index sidecar”的候选因真实 remove 路径退化而被完整撤销，没有为了
  代码外观保留负优化。

当前 G1-G10 继续为 `PASS`。本记录不构成跨硬件 SLA、一亿行资格、自动 parallel 承诺、GitHub
Release/Package、Maven publication、签名或正式 release 声明。

## 2. Evidence boundary

| 项目 | 值 |
|---|---|
| Baseline | `develop@1209dd067915e7fe1c02367938707874061504b6`；上一轮正式 10M qualification |
| Qualified tree | 本记录所在 commit；仅包含本记录描述的 runtime、test、benchmark 与治理投影 |
| Host | Apple M5 Pro，48 GiB physical memory |
| OS | macOS / Darwin arm64 |
| Java | Amazon Corretto `1.8.0_502` / `25.502-b07` |
| Maven | 3.9.16 |
| JVM | `-Xms8g -Xmx24g -XX:+UseParallelGC` |
| SOMA budget | 16 GiB |
| Workload | `composed`，10,000,000 rows，AUTO，P16 |
| Sampling | 每个场景 3 fresh JVM runs；2 warmups + 5 measured samples |
| Correctness | scenario assertion + shared/SOMA/composed fingerprint |

Baseline 与 candidate 使用同一机器、JDK/JVM、row count、workload、parallelism 和采样合同。Raw
JSONL、environment、GC/profile 与 rusage 文件只保存在本机临时目录；长期可重放入口仍是
[`scripts/benchmark.sh`](../../scripts/benchmark.sh)。本机数字只能用于相同环境的相对归因，不能提升为
兼容合同或公开 SLA。

## 3. 四维性能架构模型

```text
Design：减少必须发生的 logical work
    -> Execution：选择并组合 semantics-preserving physical work
        -> Memory：决定 touched / retained / temporary bytes 与 locality
            -> CPU：以 sequential 或 bounded parallel kernel 执行剩余 work
```

四层不是四套互相独立的优化清单。一个根因应当沿同一条因果链解释，例如：

```text
scalar sum 不需要完整 Join result
    -> relation visit 与 accumulator fusion
        -> 删除 O(output) temporary long[] 和第二次读
            -> 减少 allocation、GC、memory traffic 与 CPU work
```

本专题固定以下 evidence level：

- `MEASURED`：相同配置 fresh-JVM before/after；
- `PROFILED`：CPU/allocation stack 能指出 Owner；
- `PROVED`：可由 Key/Index/cardinality、IR barrier 或 source byte model 证明；
- `OBSERVED`：方向性 diagnostic，只能形成下一专题候选；
- `REJECTED`：实测无收益、退化或必须突破产品边界。

## 4. Operator-level 四维瓶颈地图

| 正常路径 | Design / Execution 结论 | Memory / CPU 结论 | Disposition |
|---|---|---|---|
| repeated atomic add | 当前 Design 要求逐次 atomic publication；上一专题已消除 directory accounting 全扫描 | 仍含 Index/compression/publication 与 GC 成本，但不是本轮新热点 | 保持；无 Loader/Batch 扩张 |
| typed Table scan | typed prefix 已能 bounded parallel；三个 fixture 的单行 kernel 较轻 | P16 调度收益依场景而异，不能仅凭 core 数自动开启 | 保持 sequential 默认 |
| GroupBy | 旧 physical state 把 input upper bound 当 actual group count；primitive key 仍走 generic equality | 低基数浪费 O(N) arrays，高基数存在重复 Field 读取/装箱 | `MEASURED`，已优化 |
| Relation primitive sum | scalar terminal 不需要 materialized relation values | 旧路径产生 O(output) `long[]` 并二次扫描 | `PROVED`，已融合 |
| broad Equality Join | logical/index plan 已成立，但 pair visit 与 callback terminal 仍主要顺序执行 | P16 与 sequential 接近，平均 CPU core 没有随 P16 扩张 | `MEASURED`，记录架构边界 |
| Selection remove | dense locator compaction 要求重建受影响 sidecar | memory write/locality 主导，简单合并循环反而退化 | 候选 `REJECTED` |
| AUTO compression | 不是本轮 Group/Relation profile 的主要 root cause | codec 修改会混淆四维归因和既有默认策略 | 保持正式 AUTO 合同 |

## 5. Retained optimization

### 5.1 GroupBy actual-cardinality state

旧实现按 `outputUpperBound` 一次性建立 representatives、hash links、counts、aggregate state 和可选
floating sequence。对于 10M 行、几十或几百个 group 的正常聚合，这会让输入规模而非实际结果规模
决定 temporary footprint。

新实现保持原有 conservative preflight，不放宽 resource admission；实际 allocation 使用：

1. Group Field 是 Table Key：结果基数最多且通常等于输入上界，直接使用上界；
2. Group Field 有 Index：从当前 immutable sidecar 读取精确 `distinctCount` 作为容量 seed；
3. 其他 Field：从有界小容量开始，并以 checked arithmetic 增长到输入上界；
4. floating sequence 只按实际消费元素增长；
5. 结果仍按首次 encounter representative materialize，canonical group order 不变。

这不是新的 Index 统计合同或 public hint；`distinctCount` 是同一 StateRoot 中现有 sidecar 的内部事实。
增长期间的峰值仍被原 conservative per-row lease 覆盖。

### 5.2 Primitive GroupBy kernel

boolean、byte、short、char、int、long scalar Group Field 现在直接从 bound directory 读取 primitive
canonical key，以 unboxed `long` 形式完成 hash/equality。String、Enum、ordinary Object、nested
`@SomaValue` 继续使用正式 logical equality 路径。

优化没有把 float/double、reference equality 或复合 Value 偷换为位级比较；reference interpreter
仍走独立的 canonical scan/equality algorithm。新增 differential 比较 primitive optimized/reference
group key、value 和 encounter order。

### 5.3 Relation scalar aggregate fusion

当 Relation primitive pipeline 满足以下全部条件时，`sum()`直接访问 canonical pair stream：

- root 是 direct `mapToInt` 或 `mapToLong`；
- mapper 后没有 primitive stage 或 stateful/order barrier；
- terminal 是整数 `sum()`。

执行仍使用相同 callback scope、canonical relation encounter order 与 `Signed128Accumulator`；只有最终
结果超出 signed long 时才产生原有 structured `ARITHMETIC_OVERFLOW`。不满足条件的 pipeline 继续走
原 materialization path。该 specialization 删除 temporary，不改变 public API 或 logical plan。

## 6. Before / after

以下均为 3 个 fresh JVM run 的中位数：

| Scenario / metric | Baseline | Final | Delta |
|---|---:|---:|---:|
| Scheduling core GroupBy | 869.772 ms | 561.768 ms | -35.4% |
| Scheduling low-cardinality GroupBy | 231.287 ms | 85.959 ms | -62.8% |
| Scheduling high-cardinality GroupBy | 1698.356 ms | 1069.804 ms | -37.0% |
| Scheduling broad Join | 1421.453 ms | 1386.142 ms | -2.5% |
| Scheduling broad Join P16 | 1420.889 ms | 1396.683 ms | -1.7% |
| Dispatch broad Join | 1029.020 ms | 1042.672 ms | +1.3% |
| Dispatch relation kinds | 4832.978 ms | 4882.685 ms | +1.0% |
| Dispatch remove | 3765.202 ms | 3590.671 ms | -4.6% |
| Simulation primitive pipeline | 374.911 ms | 387.311 ms | +3.3% |
| Simulation primitive pipeline P16 | 228.513 ms | 227.613 ms | -0.4% |
| Simulation remove | 2282.434 ms | 2209.200 ms | -3.2% |

非 GroupBy 路径的变化均未同时超过既有 15% / 2 ms 回归阈值；`compare.py` 为 `PASS`。没有把
单次更快或轻微抖动写成产品收益。

进程级证据：

| Scenario | CPU time baseline -> final | Wall baseline -> final | RSS baseline -> final | Final avg cores |
|---|---:|---:|---:|---:|
| Scheduling | 74.06 -> 64.75 s (-12.6%) | 64.82 -> 55.64 s (-14.2%) | 10,654 -> 10,029 MiB (-5.9%) | 1.162 |
| Simulation | 29.50 -> 29.20 s (-1.0%) | 21.09 -> 20.99 s (-0.5%) | 10,018 -> 9,944 MiB (-0.7%) | 1.390 |
| Dispatch | 97.70 -> 97.86 s (+0.2%) | 84.88 -> 84.77 s (-0.1%) | 12,552 -> 12,580 MiB (+0.2%) | 1.154 |

最终 9 个 JVM 均无 major page fault、OOME、OS kill、fingerprint drift 或 correctness failure。

## 7. CPU 使用率为何仍低

P16 表示一个 terminal 最多可使用 16 个参与者，不表示整个应用生命周期或每个 physical operator
都并行。当前事实是：

- repeated add、部分 GroupBy/Relation terminal、mutation、publication 与 application setup 仍为顺序工作；
- single-Table parallel path 主要并行 typed-filter/range work；opaque callback、稳定顺序和某些
  aggregate merge 受到 barrier 限制；
- broad Join 的 sequential/P16 中位数几乎相同，说明真正热点不在已并行的轻量前缀；
- 因此整个 fresh JVM 的平均 CPU 只约 1.15-1.39 cores，这不是 48 GiB 内存不足，也不是线程池反复
  创建，而是当前 physical coverage 与 workload 串行占比的结果。

直接让现有 mutable pair/view cursor 被多个 worker 共享会破坏 borrowed scope、callback exactly-once、
canonical order、first failure、resource lease 和 worker quiescence。真正的 parallel Relation/Group
需要 participant-local cursor/view、确定性 partition、ordered merge 和失败收敛协议；它属于下一轮
独立架构专题，不能在本轮用线程包装顺序 loop。

## 8. Rejected candidate

本轮实现并测量过“Selection remove 后单遍同时重建 Key 与全部 Index sidecar”的内部候选，目标是减少
多次 directory scan。Simulation 10M remove 从保留实现约 2.21 s 退化到 2.43 s；profile/行为说明
交错更新多个 hash sidecar 降低 locality，并没有减少主导 memory write。

该候选已完整撤销，production diff 不保留 helper、兼容分支或 dead abstraction。结论是：循环次数少
不等于 memory path 更快；未来 remove 优化必须从 locator stability、sidecar physical layout 或并行
rebuild 的完整架构重新证明。

## 9. Correctness 与验证

Retained optimization 保持：

- reference、optimized sequential、parallel 的 logical result/fingerprint 等价；
- Group 首次 encounter order、Join duplicate/missing/null 与 canonical pair order；
- signed-128 integer sum、floating canonical tree 与 structured failure；
- callback scope、one-shot lifecycle、resource preflight/lease、Group guard 与 detached result；
- Key/Index/compression 与 payload 同 StateRoot generation；
- Java 8、无新 production dependency、无第三 artifact。

最终 evidence：

- runtime targeted suite：57 tests，0 failure/error；
- generated I5 consumer 新增 direct Relation integer sum 正常路径；
- 10M AUTO：9 records / 3 groups，全部 scenario assertion 与三类 fingerprint 通过；
- 相同环境 before/after `compare.py`：`PASS`；
- full-regeneration 重建 runtime、processor、三个 reference application 和 benchmark consumer；
- `scripts/check.sh`、`scripts/qualify.sh`、`git diff --check` 与 Markdown route：`PASS`。

## 10. 当前最佳实践与后续边界

1. GroupBy 应优先使用 schema-typed Field；已有 Index 的 Field 能复用 exact distinct statistic，普通用户
   不需要手工提供 cardinality hint。
2. 只需要 scalar 时直接使用 `count/sum/min/max`，不要先 `toList/toArray`；Relation direct integer
   sum 已能避免完整中间数组。
3. `parallel()`继续显式使用；只在真实 pipeline 的 sequential/P16 对照证明收益时开启，不能依据 CPU
   core 数自动推断。
4. AUTO compression 保持默认；本轮热点不支持修改 codec 或默认 policy。
5. Selection remove 仍是 memory/sidecar rebuild 候选，但不以多 sidecar 交错写入作为解决方案。
6. 下一轮 CPU scaling 若启动，应以 Relation/Group participant-local execution architecture 为独立
   bounded topic，并首先保持 reference differential、order/failure/resource proof chain。
7. 一亿行仍是架构愿景；本记录只证明当前机器上的固定 10M 正常路径相对改进。

## 11. Replacement closure

| Temporary responsibility | Stable Owner | Disposition |
|---|---|---|
| 四维因果模型与 operator map | 本记录 | PROMOTED |
| process CPU/fault/context evidence | `benchmarks/tools/`、`scripts/benchmark.sh` | PROMOTED |
| GroupBy/Relation optimization | runtime code + runtime/generated consumer tests | PROMOTED |
| fixed 10M before/after 与当前边界 | 本记录 | PROMOTED |
| sidecar 单遍交错 rebuild | 不进入 production source | REJECTED BY EVIDENCE |
| parallel Relation/Group 架构候选 | 本记录第7/10节；未来独立 Temporary | DEFERRED WITH BOUNDARY |
| bounded Temporary | 删除 | RETIRED |

本记录关闭四维性能架构治理，不修改正式 Design，也不扩大 release/publication claim。
