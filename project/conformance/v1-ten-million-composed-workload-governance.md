# SOMA V1 千万行组合负载性能与正确性治理

类型：Conformance / Performance and Correctness Governance Record

状态：`PASS`

日期：2026-08-09

Owner：固定千万行组合负载、正常路径正确性复核、profile 驱动优化、规模与资源证据边界

## 1. 结论

本专题在 Apple M5 Pro / 48 GiB 开发机上，以 10,000,000 行主 Table、三个正式 reference
application 和 C01-C12 组合操作完成治理：

- Scheduling、Simulation、Real-time Dispatch 的 typed filter、Index、projection、stateful
  operation、GroupBy、五种 Equality Join、parallel、mutation 与 AUTO/OFF 全部产生预期结果；
- 最终 9 个 fresh JVM record、3 个 summary group 全部通过，3 次 run 间 shared、SOMA logical 与
  composed fingerprint 均无漂移；
- 发现并修复一个正常 Join 被错误保守上界拒绝的资源准入问题；
- 修复 repeated atomic add 中每行重扫全部 Chunk accounting 的结构性热点，Scheduling 单次
  diagnostic ingest 从 53.08 s 降至 5.50 s，约改善 89.6%；
- 非 Index Field 的 Selection update 不再重建整张 Table 的 Index，Simulation 单次 diagnostic
  从 2.30 s 降至 12.9 ms；
- non-unique Index rebuild 改为增量 link accounting，移除每条 rebuild row 重扫 64 shards 的工作；
- 没有修改 Blueprint、Design、public/generated API、dependency、production artifact topology、
  failure/order/parallel 语义或 release authorization boundary。

当前 G1-G10 继续为 `PASS`。本记录不构成跨硬件 SLA、一亿行资格、production 并发承诺、GitHub
Release/Package、Maven publication、签名或正式 release 声明。

## 2. Evidence boundary

| 项目 | 值 |
|---|---|
| Source baseline | `develop@ba0087ddd1f8ddc428c339350d6deafa4f942a09`；正式结果由包含本记录的后续 Git commit 固定 |
| Host | Apple M5 Pro，48 GiB physical memory；高成本 JVM 前 `memory_pressure`约 90% free |
| OS | macOS / Darwin arm64 |
| Java | Amazon Corretto `1.8.0_502` / `25.502-b07` |
| Maven | 3.9.16 |
| JVM | `-Xms8g -Xmx24g -XX:+UseParallelGC` |
| SOMA budget | 16 GiB |
| Workload | `composed`，10,000,000 rows，P16，AUTO |
| Final sampling | 3 fresh JVM runs；每个 measurement 2 warmups + 5 measured samples |
| Representation check | 另有 3 个 10M OFF fresh JVM；与 AUTO 三类 fingerprint 精确一致 |

Raw JSONL、environment、GC log、JFR、collapsed stack 和 flame graph 是本机诊断证据，不提交为
distribution artifact。长期可重放入口是 [`scripts/benchmark.sh`](../../scripts/benchmark.sh)，
机器相关数字不能脱离上述环境解释。

## 3. 组合 workload 与正确性 spine

本专题没有建立 3M/5M/10M 规模曲线，而是把单一 10M 预算投入更高信息密度的正常操作组合：

| 场景 | 组合范围 | 独立正确性证据 |
|---|---|---|
| Scheduling C01-C04 | typed filters、Field aggregate、Index residual/top、低/高基数 GroupBy、broad Join sequential/P16 | closed-form sum/count、bounded ordered result、group fingerprint、Join sum |
| Simulation C05-C07 | Enum/primitive stateful pipeline、stable top materialization、Selection update/remove | detached top oracle、aggregate delta、size/Key/Index revalidation |
| Dispatch C08-C12 | String Index/top、typed/mapped reference、broad Join、Inner/Left/Full/Semi/Anti、missing/duplicate、mutation | ordered reference fingerprint、relation cardinality formula、post-mutation Join/Group/metadata |

每个 measurement 都创建新的 one-shot pipeline。大结果只在 materialization 本身是被测能力时创建；
其余使用 exact scalar、cardinality、bounded ordered result 或 streaming fingerprint，避免为了测试
正确性人为复制两份千万行结果。

最终 evidence：

- C01-C12 scenario assertion 全部通过；
- sequential/P16 的适用 scalar、ordered array 与 fingerprint 一致；
- AUTO/OFF 的 `sharedFingerprint`、`fingerprint`、`composedFingerprint` 在三个场景逐项一致；
- Selection mutation 后 size、Index、aggregate、Join、Group 与 metadata 自洽；
- `scripts/check.sh` 重放 runtime 57 tests、processor 34 tests、三个 reference application、
  generated consumer、package、SBOM/checksum/provenance，全部无 failure/error。

## 4. 正常路径发现与有界修复

### 4.1 Key-aware Join resource upper bound

首个 Scheduling 10M broad Equality Join 在执行任何 Join work 前以
`RESOURCE_LIMIT_EXCEEDED` fail closed。诊断 required managed bytes 约 3.93 TB；原因不是实际结果，
而是 resource upper bound 无条件采用 `leftRows * rightRows`。右侧 Join Field 实际是 immutable、
non-null Key，因此每个左值最多匹配一个右值，真实上界不超过左侧行数。

修复只收紧已证明安全的上界：

- Inner：两侧 Key 取 `min(L,R)`；右 Key 取 `L`；左 Key 取 `R`；
- Left：右 Key 取 `L`；仅左 Key 使用 `L+R`；
- Full：任一侧 Key 使用 `L+R`；
- Semi/Anti 仍为 `L`，Cross 与无唯一性证明的 relation 保持原保守公式。

新增测试覆盖两侧/单侧/无 Key 的 Inner、Left、Full、Semi 与 Cross。修复后相同 10M query 成功，
不以 reference fallback 或放宽 memory budget 绕过准入。

### 4.2 Directory-generation accounting cache

CPU profile 显示 Scheduling 53.08 s ingest 的主导栈是：

```text
GeneratedTable.publishAdd
    -> GeneratedTable.managedBytes
        -> TableChunkDirectory.chunkManagedBytes
            -> scan every Chunk
```

普通 add 只向已分配的 active PlainChunk 写值，不改变该 Chunk 的 managed size；表示只在 grow、
sealed Chunk、update/remove candidate 上变化。实现把 aggregate chunk bytes 缓存在 directory
generation 上，并在 append/replace 时失效。`volatile` cache 只缓存同一 generation 的确定值；
StateRoot、retained reservation、candidate publication 和 failure contract 未改变。

相同 CPU-profile diagnostic 的 Scheduling ingest 从 53.080 s 降至 5.498 s；最终三次资格中位数
为 5.555 s。该结果证明 repeated atomic add 当前已不再被全目录 accounting scan 主导，不触发
Loader/API 扩张 stop rule。

### 4.3 Selection update 的 sidecar dependency

Simulation 正常操作只更新 `delta`，Dispatch 只更新 `deadlineMinute`；两者都不是 Index Field，旧实现
仍无条件重建千万行 Index。Editor 现在把最终值与原值按 Index logical Field 比较；一次 Selection
中只要任一 Index Field 真正变化仍完整重建，否则复用上一代 immutable sidecar。

测试同时证明：

- 非 Index payload update 复用同一 Index generation，查询结果正确；
- Index Field update 建立新 sidecar，旧/新 lookup count 正确；
- no-op 不发布；Key 仍 immutable；atomic StateRoot 与 retained accounting 保持一致。

单次同配置 CPU diagnostic 中，Simulation update 从 2297.5 ms 降至 12.9 ms；最终三次资格中位数
为 18.5 ms。Dispatch 最终中位数为 4.5 ms。

### 4.4 Index rebuild accounting

Selection remove 必须处理 dense compaction 后发生变化的 Key/Index locator。Allocation/CPU profile
确认 rebuild 中每加入一条 non-unique posting 都重新扫描 64 shards 计算 `managedBytesWithoutLinks`。
实现改为从上一代 `managedBytes` 减去 prior link pages、加上 current link pages；shard/container 的
增减仍由原 rebuild 路径 checked accounting。

该修改删除 O(64×N) accounting 重复工作，不改变 hash、collision equality、posting order、locator
或 rebuild publication。Simulation 单次 remove 约从 2.74 s 降至 2.22 s，Dispatch 约从 4.35 s
降至 3.60 s；这些是 diagnostic 方向性结果，不是独立 SLA。最终三次资格中位数分别为 2.282 s
和 3.765 s。

## 5. 最终 10M 资格结果

以下均为 3 个 fresh JVM run 的中位数：

| Scenario | Ingest | Scan | P16 scan | Key 10k | Index | Core Join | Peak RSS |
|---|---:|---:|---:|---:|---:|---:|---:|
| Scheduling | 5.555 s | 405.4 ms | 405.2 ms | 1.416 ms | 0.013 ms | 121.8 ms | 10,654 MiB |
| Simulation | 4.610 s | 284.2 ms | 249.8 ms | 1.226 ms | 0.016 ms | - | 10,018 MiB |
| Real-time Dispatch | 9.575 s | 338.8 ms | 333.5 ms | 1.942 ms | 2.361 ms | 326.3 ms | 12,552 MiB |

代表性组合 terminal：

| Scenario / operation | Sequential | P16 |
|---|---:|---:|
| Scheduling typed filter -> projection -> sum | 438.3 ms | 336.1 ms |
| Scheduling broad Join -> filter -> primitive sum | 1421.5 ms | 1420.9 ms |
| Simulation stateful primitive pipeline | 374.9 ms | 228.5 ms |
| Dispatch broad Join -> filter -> primitive sum | 1029.0 ms | 1031.1 ms |
| Scheduling high-cardinality GroupBy sum | 1698.4 ms | - |
| Dispatch six relation cardinality terminals | 4833.0 ms | - |

`parallel()`在较重的单 Table pipeline 上有收益，但 broad Join 在当前 physical path 上没有加速。
这符合正式合同“最多 P 个参与者、不承诺每个 stage 并行或一定加速”；最佳实践仍是 sequential 默认，
只对真实 pipeline 实测后显式启用 parallel，不能依据 core 数自动开启。

## 6. Memory、compression 与 GC

| Scenario | SOMA retained | Representation | Plain equivalent | Peak RSS median |
|---|---:|---:|---:|---:|
| Scheduling | 1.715 GB | 402.5 MB | 411.2 MB | 11.17 GB |
| Simulation | 1.376 GB | 402.4 MB | 441.3 MB | 10.50 GB |
| Real-time Dispatch | 2.829 GB | 402.0 MB | 481.4 MB | 13.16 GB |

九个最终 JVM 合计：Scheduling 24 次 young GC、无 Full GC、pause 约 0.90 s；Simulation 15 次
young GC、无 Full GC、pause 约 0.77 s；Dispatch 27 次 young GC、3 次 ergonomic Full GC、pause
约 2.36 s。没有 OOME、OS kill、budget leak 或未解释的 memory spike。

单次 OFF 对照在本机部分路径更快，AUTO 也只获得依 schema 而异的 representation saving；这不改变
正式默认 AUTO。普通用户不需要选择 codec。只有真实项目证明 latency 比 memory 更重要时才显式 OFF，
并重新验证其 workload。

## 7. 当前最佳实践与剩余边界

1. 已知规模先 `reserve(expectedRows)`；10M repeated atomic add 已能在当前三个场景约 4.6-9.6 s
   完成，本专题没有证据支持为了 benchmark 新增 Loader/Batch API。
2. 只为稳定 access pattern 建 Key/Index。Index lookup 很快，但 mutation 后 sidecar 维护仍是明确成本。
3. 优先 typed expression 和 typed order，使 optimizer 可以进行 Index substitution、predicate analysis、
   top specialization 与 leaf pruning；callback 是 optimization barrier。
4. 非 Index payload Selection update 已避免无关 sidecar rebuild；Selection remove 因 dense locator
   compaction仍需重建受影响 Key/Index，是后续 profile 候选而非 correctness defect。
5. scalar terminal 不应先 materialize；本轮 allocation profile 未证明 relation primitive buffer 是
   主导热点，因此没有凭代码外观修改它。
6. sequential 默认；P16 对 single-Table heavy pipeline 有收益，但当前 broad Join 无收益。
7. 24 GiB heap 不等于可把 24 GiB 都交给 SOMA；16 GiB budget 为 JVM、application referent、GC 与
   result headroom 留出边界。SOMA managed bytes 与进程 RSS 必须分别观察。
8. 本轮没有一亿行证据。一亿行继续是架构愿景；下一次扩大规模应使用更大、隔离的服务器，并保持
   同一 harness、资源准入和 fingerprint 证明链。

## 8. 验证与 replacement closure

最终执行并通过：

- `scripts/check.sh`：runtime 57、processor 34、generated consumer、三个 reference application、
  local package、SBOM/checksum/provenance 全部 `PASS`；
- 10M AUTO final qualification：9 records / 3 groups，3 fresh JVM runs，2 warmups + 5 samples；
- 10M OFF equivalence：3 records / 3 groups，三类 fingerprint 与 AUTO 精确一致；
- Scheduling CPU、Simulation CPU、Dispatch CPU/allocation targeted profile；
- Key-aware Join bound、Selection Index dependency、remove accounting 与 existing failure/publication tests；
- `git diff --check`、Markdown route、无 committed build/profile artifact。

Replacement closure：

| Temporary responsibility | Stable Owner | Disposition |
|---|---|---|
| composed workload、scenario selector、machine-readable metric | `benchmarks/`、`scripts/benchmark.sh` | PROMOTED |
| normal-path correctness、10M performance/memory/profile conclusion | 本记录 | PROMOTED |
| runtime resource/accounting optimizations | code + runtime tests | PROMOTED |
| current best-practice candidates | 本记录第7节；future docs只做角色投影 | PROMOTED WITH BOUNDARY |
| Loader、incremental remove、automatic parallel、public SLA | 不进入本轮 code/Design | DEFERRED / REJECTED BY EVIDENCE |
| bounded Temporary | 删除 | RETIRED |

本记录关闭本次治理；长期 benchmark 仍可用于后续专门性能专题，但不能把本机数字转写为公开承诺。
