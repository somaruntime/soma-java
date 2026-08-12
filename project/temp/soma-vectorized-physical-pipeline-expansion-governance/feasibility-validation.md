# R2.3 Candidate Design有限可行性与性能方向验证

类型：Bounded Temporary / Design Validation Evidence

状态：`PASS / FROZEN_INPUT / BOUNDED_DIRECTIONAL_EVIDENCE / NOT_QUALIFICATION`

基线：`e2ce237736cd6042fbce2d90c2522e1a280e7d7c`

日期：2026-08-12

Current route：本文件只为[冻结临时设计](design.md)和
[Baseline Freeze](baseline-freeze-and-readiness.md)提供方向证据；它不拥有current Design、Readiness或
Qualification状态。

## 1. 验证问题

R2.3只验证四件事：

1. 当前production shape能否承载“one final Physical decision + finite representation handler”；
2. 第一阶段kernel的正确性、bounded parallel与resource replacement是否仍可重放；
3. AUTO/encoded normal path是否仍存在足以支持S1优先级的性能差距；
4. 是否必须在设计阶段创建新fixture、dependency、module或production prototype。

它不进行全面优化、不修改production source、不刷新10K/1M/10M完整资格，也不建立跨硬件SLA。

比较基线由[第一阶段正式晋升](../../conformance/v1-vectorized-physical-pipeline-phase1-promotion.md)与
[全面性能前沿资格](../../conformance/v1-performance-frontier-qualification.md)拥有；本文件只记录当前
checkout的bounded refresh。

## 2. 固定环境

| Fact | Value |
|---|---|
| Commit | `e2ce237736cd6042fbce2d90c2522e1a280e7d7c` |
| Worktree at benchmark start | clean |
| JDK | Amazon Corretto `1.8.0_502` aarch64 |
| Maven | `3.9.16` |
| Host | Apple M5 Pro / macOS Darwin 25.6.0 |
| Rows | 1,000,000 |
| Heap | `-Xms2g -Xmx8g` |
| SOMA budget | 6 GiB |
| Parallelism | 16 |
| Sample | 1 fresh JVM per implementation；1 inner warmup + 3 samples |
| Implementations | `soma-auto`、`soma-off` |
| Profiler | none |

原始nonproduction输出位于本机`/private/tmp/soma-r2-vector-validation`，不进入repository，也不是长期Owner。

## 3. Source-shape验证

### 3.1 Single lifecycle可复用

代码审查确认：

- `CanonicalQueryOperation`已经拥有guard、binding、normalization、resource lease、Frame与cleanup；
- `CanonicalRowPhysicalPlan`已经拥有access、parallel/resource和可选finite decision；
- `CanonicalParallelWorkScheduler`已经同时服务row range和Chunk morsel；
- `TableChunk`/PLAIN/encoded/overlay已经拥有representation identity和operation-scoped访问；
- Reference interpreter与optimized executor保持独立。

因此Candidate不需要第二套IR、query lifecycle、scheduler或resource manager。

### 3.2 需要补齐的最小seam

现行base plan之后由terminal refinement附加vector decision；representation handler又在kernel执行时决定。
这证明当前对象图可以承载finite specialization，同时也证明继续扩展前需要把terminal和representation decision
收口到同一planner Owner。

### 3.3 Encoded-native机制可行

`EncodedChunk`已经保存bit boolean、plain integral与RLE等codec-owned数据。RLE拥有raw value与run end，
bit boolean拥有packed words。当前接口只是把它们逐row展开给visitor。因此run/bit级closed access是对既有
information的最小暴露，不要求改变storage truth、文件格式、public API或Java版本。

结论：source-shape feasibility `PASS`。

## 4. Targeted correctness与lifecycle重放

执行：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home \
mvn -pl soma-runtime \
  '-Dtest=GeneratedTableTest#vectorChunkKernelsMatchReferenceAcrossPlainEncodedAndTypedFilters+vectorMorselsUseBoundedPoolAndPreserveExactIntegralSum+vectorParallelPlanReplacesLinearLocatorScratchWithChunkPartials+vectorParallelFieldSumFailsClosedWhenPoolIsUnavailable' \
  test
```

结果：

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

覆盖：

- PLAIN/AUTO与Reference的count、integral sum、ordered `long[]` differential；
- bounded ForkJoinPool与exact integral sum；
- O(rows) parallel locator scratch被O(chunks) partial替换；
- unavailable pool structured failure。

它只证明现行第一阶段基线可重放；新cell仍需自己的positive/negative/resource/failure证据。

## 5. 1M定向性能重放

执行：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home \
SOMA_BENCHMARK_WORKLOAD=frontier \
SOMA_BENCHMARK_SCENARIOS=frontier-source \
SOMA_BENCHMARK_IMPLEMENTATIONS='soma-auto soma-off' \
SOMA_BENCHMARK_ROWS=1000000 \
SOMA_BENCHMARK_RUNS=1 \
SOMA_BENCHMARK_INNER_WARMUPS=1 \
SOMA_BENCHMARK_INNER_SAMPLES=3 \
SOMA_BENCHMARK_PARALLELISM=16 \
SOMA_BENCHMARK_PROFILER=none \
SOMA_BENCHMARK_MEMORY_ATTRIBUTION=1 \
SOMA_BENCHMARK_XMS=2g \
SOMA_BENCHMARK_XMX=8g \
SOMA_BENCHMARK_MEMORY_BUDGET_BYTES=6442450944 \
SOMA_BENCHMARK_OUTPUT_ROOT=/private/tmp/soma-r2-vector-validation \
./scripts/benchmark.sh
```

结果：`benchmark-summary: PASS (2 records, 2 groups)`；AUTO/OFF correctness与shared fingerprint一致。

| Operation | AUTO median | OFF median | Ratio AUTO/OFF | R2解释 |
|---|---:|---:|---:|---|
| Table count | 0.016 ms | 0.016 ms | 1.0x | fixed/metadata-like path，无治理价值 |
| typed filter + projected sum | 7.771 ms | 2.785 ms | 2.79x | encoded typed scan仍是S1高价值目标 |
| direct Field sum | 3.545 ms | 0.389 ms | 9.11x | encoded representation traversal是最清晰缺口 |
| parallel Field sum | 0.311 ms | 0.145 ms | 2.14x | O(chunks)结构正确；encoded handler仍影响吞吐 |
| direct Field materialize | 1.179 ms | 0.354 ms | 3.33x | representation-native ordered write有价值 |
| typed filter + materialize | 11.708 ms | 7.287 ms | 1.61x | two-pass/encoded handler值得后续S3验证 |
| callback Table filter | 19.472 ms | 21.819 ms | 0.89x | callback不是本finite pipeline目标 |
| callback primitive map | 20.887 ms | 19.900 ms | 1.05x | callback barrier裁决得到支持 |
| mapped reference | 16.033 ms | 16.042 ms | 1.00x | 不应扩张到reference/stateful universal pipeline |
| Index exact count | 0.294 ms | 0.334 ms | 0.88x | Index path优先级低 |

这些比例来自一次bounded fresh-JVM方向验证，不能当作稳定effect size；但多个typed source operation以同一方向
显示AUTO差距，而callback/reference/Index没有显示同类差距，足以确定设计优先级。

## 6. Allocation与resource观察

| Operation | AUTO allocated | OFF allocated | Temporary peak |
|---|---:|---:|---:|
| Field sum | 13,840 B | 13,968 B | 0 B |
| parallel Field sum | 17,728 B | 17,856 B | 0 B（observer粒度下） |
| Field materialize | 8,014,136 B | 8,014,264 B | detached result为主 |
| typed filter materialize | 12,015,248 B | 12,015,376 B | 16,000,032 B |
| mapped reference | 14,535,264 B | 14,535,392 B | 64,004,160 B |

结论：source aggregate当前已经低分配，S1的主要目标是CPU、dispatch与representation access，不是用新
buffer换吞吐。ordered result本身是必要内存；S3应消除额外membership/staging，而不能声称消除result成本。

## 7. 假设裁决

| Hypothesis | Result | 后续动作 |
|---|---|---|
| H1 encoded-native integral aggregate值得优先 | `SUPPORTED` | S1 first cell |
| H2 one final Physical decision可由现有lifecycle承载 | `SUPPORTED_BY_CODE_SHAPE` | S1结构收口 |
| H3 callback/mapped universal vectorization值得做 | `NOT_SUPPORTED` | 明确fallback/reject |
| H4 IndexSelection应优先specialize | `NOT_SUPPORTED` | defer |
| H5 parallel ordered materialization有清晰低内存方案 | `FEASIBLE_NOT_PROVEN` | S3 two-pass matched验证 |
| H6 floating aggregate现在可准入 | `NOT_PROVEN` | defer，先闭合numeric tree |
| H7 GroupBy或Join必须立即进入 | `NOT_PROVEN` | S1-S3后重新profile；当前选择none |
| H8 需要新module/dependency/fixture | `REJECTED` | 复用现有tests/benchmark |

## 8. 为什么没有创建额外prototype

当前production source已经同时提供：

- finite typed predicate compiler；
- PLAIN direct array kernel；
- encoded codec state；
- Chunk ordinal scheduler；
- Reference differential；
- 1M executable workload。

再写一个独立toy pipeline只能证明Java循环能够更快，不能证明与SOMA resource、order、numeric和failure合同
集成。因此R2.3选择直接审查真实seam并重放现有evidence，避免一次性prototype演化成第三implementation truth。

## 9. Claim boundary

本次`PASS`只证明Candidate方向有实施价值和现有架构承载能力。它不证明：

- Candidate已经Baseline Freeze或获implementation authorization；
- encoded-native handler、boolean kernel、integral matrix或parallel materialization已经实现；
- 当前一次1M数字是稳定基线或跨硬件SLA；
- GroupBy/Join/vector API/runtime codegen值得准入；
- 10M、一亿行或正式release性能已经更新。

## 10. R2.3结论

有限验证`PASS`。后续R2.4与最终Freeze审查已经完成；当前基线只准入VP1 encoded integral与VP2 ordered
`long[]`，其余能力不能从本方向证据自动推断。
