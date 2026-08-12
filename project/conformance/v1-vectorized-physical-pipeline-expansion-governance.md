# SOMA V1 Vectorized Physical Pipeline 扩展治理

类型：Conformance / M1 Internal Mechanism Promotion / Governance Closure

状态：`PASS / FORMALLY_PROMOTED / VP1-VP3_COMPLETED / TEMPORARY_RETIRED / RELEASE_NOT_AUTHORIZED`

Owner：single final Physical decision、encoded-native integral kernel、ordered `long[]`
representation-native/parallel materialization的实施、资格、正式晋升与claim boundary

日期：2026-08-12

## 1. 结论

本专题按冻结VP1→VP2→VP3计划完成实施和资格。SOMA在不改变Java frontend、Canonical/Reference
semantics、public/generated API、Java 8、two-artifact topology、dependency或release边界的前提下，正式
扩展了finite primitive physical family：

- terminal requirement与normalized semantics由唯一Planning Owner一次形成最终access、kernel、
  representation handler、parallel ownership与完整`ResourceEstimate`；
- Table count与schema-known integral Field sum可直接消费integral encoded-plain和single-distinct-leaf
  RLE；multi-leaf RLE与overlay保持明确scalar/current fallback；
- ordered `long[]`可以在PLAIN/encoded/mixed/overlay上按representation执行；无predicate按Chunk prefix
  写固定range，pure typed predicate sequential使用single write + conditional compact，parallel使用
  count/prefix/disjoint write；
- scalar aggregate与materialization复用唯一bounded caller-participating ordinal-work lifecycle，返回前
  quiescent；
- resource admission覆盖result、partial、count/offset和bounded task state，不建立O(rows) locator
  companion buffer。

Unsupported callback、mapped、stateful、Index residual、GroupBy、Join、mutation及未准入type/terminal继续
走existing optimized family，不转Reference，也不产生新的unsupported failure。

## 2. Replacement closure

VP1删除了base plan之后的terminal-family refinement Owner。Final Physical decision现在一次拥有：

```text
closed terminal requirement + normalized semantics
    -> access + finite kernel + predicate/leaf binding
        + representation handlers + parallel ownership
        + complete ResourceEstimate
            -> lease
                -> ExecutionFrame consumes without re-planning
```

Production没有保留第二planner、second executor、second scheduler、decode cache、feature flag、compatibility
adapter或dead experimental branch。Representation access是operation-scoped immutable borrow，不是第二
storage truth；private carrier、Chunk width、codec threshold和kernel loop仍是可替换L4机制。

稳定合同已分别晋升到：

- [Planning](../design/planning-and-optimization.md)：single final decision、finite capability与fallback；
- [Execution](../design/execution-and-concurrency.md)：complete admission、Frame与parallel quiescence；
- [Architecture](../design/implementation-architecture.md)：closed integral access与finite kernel seam；
- [Storage](../design/data-model-and-storage.md)：encoded-plain/RLE borrowed representation boundary；
- [Core](../design/core-abstractions-and-narratives.md)：A19/A22/A26与N6 proof routing。

冻结Temporary中的current-state、feasibility、Candidate Design和implementation plan已经完成使命并删除；
本记录是实施与资格的唯一长期Owner。

## 3. Correctness、resource 与 failure evidence

最终Java 8 breadth：

- `./scripts/check.sh`：`PASS`；runtime `86 tests`、processor `34 tests`，0 failure/error；
- PLAIN、encoded-plain、single-leaf RLE、multi-leaf RLE fallback、mixed root与overlay；
- empty、one/many Chunk、all/no/partial match、long boundary与signed-128 final overflow；
- Reference vs optimized sequential vs parallel exact result/order/failure；
- `long[]` P=1/2/4/16、detached result、unavailable pool与temporary归零；
- tiny-budget在pool submission、data pass和result write前拒绝；
- processor/full regeneration与public/generated surface没有变化。

完整non-publishing qualification同样`PASS`，覆盖三个reference application、18组core benchmark、local
package consumer、source bundle exclusion、runtime dependency tree、SBOM、checksum与provenance。该结果不
授权GitHub Release/Package、签名、Maven publication或正式release声明。

## 4. Fixed-host performance evidence

环境：Amazon Corretto `1.8.0_502`、Maven `3.9.16`、Apple M5 Pro、ParallelGC、P=16、
`-Xms2g -Xmx8g`、SOMA budget 6 GiB。全部run的correctness/fingerprint均为`PASS`。

### 4.1 VP1 matched 1M

相对冻结前同机baseline：AUTO Field sum `3.431 -> 0.290 ms`，typed projected sum
`7.445 -> 3.549 ms`，parallel sum `0.299 -> 0.156 ms`；OFF与unsupported fallback保持稳定。详细
provenance由[VP1资格](vectorized-pipeline-expansion-vp1-qualification.md)拥有。

### 4.2 VP2 matched 1M

相对VP1 baseline：AUTO sequential/parallel Field materialization分别`0.964 -> 0.366 ms`、
`5.515 -> 0.200 ms`；AUTO typed sequential/parallel materialization分别`11.140 -> 6.739 ms`、
`13.533 -> 4.450 ms`。OFF sequential无稳定退化，OFF parallel同样显著改善。详细fresh-JVM A/B由
[VP2资格](vectorized-pipeline-expansion-vp2-qualification.md)拥有。

### 4.3 VP3 scale boundary

最终checkout、1 warmup + 3 sample：

| Rows | Mode | Field sum | Field `long[]` | Parallel sum | Parallel `long[]` | Typed `long[]` seq / parallel |
|---:|---|---:|---:|---:|---:|---:|
| 10K | AUTO | 0.187 ms | 0.038 ms | 0.040 ms | 0.042 ms | 0.240 / 0.263 ms |
| 10K | OFF | 0.185 ms | 0.052 ms | 0.041 ms | 0.039 ms | 0.198 / 0.251 ms |
| 10M | AUTO | 2.725 ms | 2.089 ms | 0.528 ms | 1.133 ms | 67.981 / 41.983 ms |
| 10M | OFF | 3.522 ms | 1.956 ms | 0.475 ms | 3.634 ms | 64.617 / 41.087 ms |

10K证明固定成本没有因扩展异常放大；10M证明eligible路径保持capacity与顺序/并行结果，AUTO/OFF差异
符合representation分布和sub-ms/low-ms噪声边界。该表是固定主机资格，不是跨硬件SLA、一亿行承诺或
与其他产品的性能宣称。

## 5. Reference application 与 delivery evidence

最终qualification：

- Scheduling：100K operations，`scheduling-reference: PASS`；
- Grassing Simulation：headless deterministic fingerprint与validation `PASS`；
- Real-time Dispatch：`real-time-dispatch-reference: PASS`；
- 三类1M core workload、manual/SOMA AUTO共18个fresh process record：全部correctness/fingerprint
  `PASS`；
- package/source consumer、SBOM、checksum、provenance：`PASS`。

这些journey证明finite kernel没有破坏未特化的query、Join、Group、mutation或application流程；它们不
表示每个reference application都直接消费了本专题全部新增cell。

## 6. Claim boundary

本次没有准入：

- boolean、其他primitive array、floating、min/max/average/summary；
- callback、mapped、stateful、Index residual、GroupBy、Join或mutation specialization；
- sub-Chunk、general DAG、public/internal Batch/Vector SPI、runtime codegen、Java Vector API或plan cache；
- SOMA Engine frontend、第三artifact、新dependency或Java版本变化。

未来只有真实profile与独立bounded admission才能扩张上述矩阵；不得把本次finite family解释为通用
vector engine已经完成。

## 7. Final disposition

```text
VP1 single final decision / encoded integral     PASS / COMMITTED
VP2 ordered long[] / bounded parallel            PASS / COMMITTED
VP3 full qualification / formal promotion        PASS
Formal Design Owners                              UPDATED
Temporary replacement closure                     COMPLETE
Active implementation slice                       NONE
Release/publication                               NOT_AUTHORIZED
```
