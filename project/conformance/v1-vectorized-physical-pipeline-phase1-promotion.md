# SOMA V1 Vectorized Physical Pipeline 第一阶段正式晋升

类型：Conformance / M1 Internal Mechanism Promotion / Qualification Record

状态：`PASS / FORMALLY_PROMOTED / BASELINE_INTEGRATED / RELEASE_NOT_AUTHORIZED`

Owner：finite primitive Chunk physical kernel、Chunk-morsel partial aggregate、Physical decision与
parallel lifecycle收口的最终候选、证据与claim boundary

最后更新：2026-08-12

## 1. 结论

第一阶段候选已经作为M1内部机制纳入SOMA V1 production baseline：

```text
Canonical / Bound semantics
    -> NormalizedOperation
        -> PhysicalPlan owns one finite-kernel decision + ResourceEstimate
            -> admitted ExecutionFrame consumes that decision
                -> representation-owned Chunk kernel
                    -> sequential scalar/materialization
                    -> or Chunk-morsel partial + deterministic merge
```

正式准入的能力范围只有：

- Table scan `count`，可融合simple pure typed predicate；
- schema-known integral Field `sum`，可融合simple pure typed predicate；
- ordered `long[]` materialization，包含eligible typed predicate；
- explicit parallel count/sum的O(Chunk count) partial与ordinal merge。

Unsupported shape继续选择同一PhysicalPlan Owner下既有optimized family；不转Reference、不新增
runtime unsupported failure。Public/generated API、Canonical semantics、两项production artifact、Java 8、
dependency、result/order/null/numeric/failure与resource visibility均未改变。

## 2. 为什么能够晋升

候选解决的是已经由profile与A/B证明的physical throughput问题，而不是引入新的用户能力：

- PLAIN primitive hot path由逐值visitor/stage dispatch转为once-per-Chunk typed array kernel；
- typed filter、Field projection和scalar/materialization sink在有限kernel内融合；
- parallel scalar aggregate不再先materialize O(rows) locator prefix，而是维护O(Chunk count) partial；
- exact integral partial通过signed-128 accumulator按Chunk ordinal合并；
- AUTO/encoded Chunk仍保持representation-aware fallback，未伪装成PLAIN。

它服务BP-8、BP-9、BP-10、BP-12和BP-15，长期事实分别由Planning、Execution、Architecture和Core
Design接管。

## 3. 最终结构收口

### 3.1 Physical decision单一Owner

第一阶段实验曾在terminal入口和kernel执行时重复判断eligibility、parallel ownership与temporary
scratch。最终候选已经关闭该坏味道：

- terminal-family planning refinement只在resource admission前运行一次；
- immutable PhysicalPlan保存selected kernel、compiled predicate/leaf binding、parallel ownership与
  additional temporary bytes；
- PhysicalPlan同时删除被specialization取代的O(rows) parallel prefix estimate；
- execution只消费selected decision，不能重新规划。

因此A17/A19拥有decision，A20只协调lifecycle，INV-12的Owner保持唯一。

### 3.2 Parallel lifecycle单一Owner

Row range与Chunk morsel保留不同work meaning，但共享唯一bounded ordinal-work lifecycle：

- configured/common ForkJoinPool availability；
- start gate与caller participation；
- rejection、canonical-ordinal failure selection与cancellation；
- interrupt restoration与同步quiescence；
- at most P-1 submitted drainers。

原先重复的morsel scheduler已经删除。Shared seam仅接收finite ordinal work，不是generic Executor
framework、public SPI或另一套semantic executor。

## 4. 证据

### 4.1 正确性、资源与失败

最终候选上的runtime qualification：

- `mvn -pl soma-runtime test`：78 tests，0 failure/error；
- targeted vector suite：4 tests，0 failure/error；
- PLAIN/AUTO、typed filter count、integral sum、ordered materialization与Reference exact differential通过；
- custom bounded ForkJoinPool实际提交worker，parallel partial temporary大于0且显著小于`rows * 4`；
- final ResourceEstimate证明旧O(rows) locator prefix被删除并加入O(Chunk count) partial；
- shutdown pool稳定`PARALLEL_EXECUTOR_UNAVAILABLE`；operation结束temporary accounting归零；
- existing numeric overflow、interrupt/rejection/quiescence、callback barrier与fallback breadth由runtime全量
  suite继续覆盖。

### 4.2 最终源码性能回归

固定主机：Amazon Corretto `1.8.0_502`、Apple M5 Pro、ParallelGC、1M rows、P=16、
`-Xms2g -Xmx8g`、SOMA budget 6 GiB、1 fresh JVM、3 warmup + 5 sample。该重放只验证结构收口没有
丢失第一阶段收益，不替代第一阶段更宽的10K/1M/10M资格。

| Operation | AUTO median | OFF median | 第一阶段代表值 | 判断 |
|---|---:|---:|---:|---|
| Field sum | 1.892 ms | 0.344 ms | 1.848 / 0.362 ms | stable |
| typed filter + projected sum | 7.662 ms | 2.728 ms | 7.721 / 2.702 ms | stable |
| parallel Field sum | 0.287 ms | 0.166 ms | 0.233 / 0.097 ms | direction preserved；sub-ms noise较大 |
| typed filter + materialize | 11.377 ms | 6.676 ms | 11.459 / 7.016 ms | stable |

两种implementation的correctness/fingerprint均为`PASS`。本表是同机回归证据，不是跨硬件SLA。

第一阶段更完整的10K/1M/10M、allocation和JFR provenance已经由本记录晋升前的bounded Temporary
产生；稳定结论已转入正式Design，本记录保留其资格摘要，Temporary不再保留为平行Owner。

## 5. Replacement closure

- Planning Design拥有finite kernel physical decision与一次resource projection；
- Execution Design拥有borrowed Frame lifecycle、Chunk partial与shared ordinal-work lifecycle；
- Architecture Design拥有PLAIN typed access、finite kernel family和scheduler mechanism；
- Core Design将其记录为A19/A20/A22/A26的M1成熟化，不新增A编号；
- production只保留一个Physical decision Owner和一个parallel lifecycle Owner；
- 第一阶段Temporary已退役；后续能力扩展在本记录关闭时转入独立queued Temporary，并于2026-08-12
  由Product Owner激活为[R2 Candidate Design治理](../temp/soma-vectorized-physical-pipeline-expansion-governance/README.md)；
  它不覆盖本baseline，也尚未获得production implementation授权；
- root/public surface未变化，无需更新Library user文档。

## 6. Claim boundary

本次`PASS`不证明：

- encoded-aware aggregate/predicate已经完成；
- 全部primitive/type/operator cross-product已经vectorized；
- GroupBy、Join、sort、distinct、top或mutation已经采用morsel/vector path；
- general Batch DAG、runtime codegen、SIMD Vector API或SOMA Engine已获准；
- 一亿行、跨硬件SLA、GitHub Release/Package、签名或正式发布已经成立。

后续能力只能在新Temporary完成Candidate Design、Baseline Freeze与单slice准入后实施。

## 7. 重放入口

```sh
mvn -pl soma-runtime test

SOMA_BENCHMARK_WORKLOAD=frontier \
SOMA_BENCHMARK_SCENARIOS=frontier-source \
SOMA_BENCHMARK_IMPLEMENTATIONS="soma-auto soma-off" \
SOMA_BENCHMARK_ROWS=1000000 \
SOMA_BENCHMARK_RUNS=1 \
SOMA_BENCHMARK_INNER_WARMUPS=3 \
SOMA_BENCHMARK_INNER_SAMPLES=5 \
SOMA_BENCHMARK_PARALLELISM=16 \
SOMA_BENCHMARK_PROFILER=none \
SOMA_BENCHMARK_MEMORY_ATTRIBUTION=1 \
SOMA_BENCHMARK_XMS=2g \
SOMA_BENCHMARK_XMX=8g \
SOMA_BENCHMARK_MEMORY_BUDGET_BYTES=6442450944 \
./scripts/benchmark.sh

./scripts/check.sh
```

最终checkout已经在正式Design、Conformance与Temporary replacement closure全部完成后重跑
`./scripts/check.sh`：runtime 78 tests、processor 34 tests、三个reference application、local package、
SBOM/provenance与最终`qualification: PASS`全部通过。该结果拥有本次晋升checkout的全仓资格状态。
