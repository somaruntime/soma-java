# SOMA V1 Dense Selection Remove Sidecar Projection 治理

类型：Conformance / Performance Slice Closure

状态：`PASS / T3_COMPLETED / OWNER_PROMOTED`

日期：2026-08-13

## 1. 结论

Post-M2 T3在不改变public/generated API、Table-local atomicity、zero-publication、packed order、
compression或failure合同的前提下，删除了Selection remove replacement Key/Index的逐row
hash/equality/growth重建。

当前唯一production机制是：

```text
frozen Selection
    -> dense move plan
        -> operation-scoped old-to-final locator projection
            -> replacement sidecar structural projection
                -> PLAIN bounded commit or encoded candidate root swap
                    -> one descriptor publication
```

Old sidecar继续拥有logical Bucket identity、hash和canonical membership；replacement只过滤removed
locator、改写packed survivor locator并排序multi Bucket。它不共享可变Shard/Bucket array，不建立reverse
Index、delta、journal或第二套membership truth。旧的payload-backed projected rebuild已经退出。

稳定职责已写回Data model and storage、Execution and concurrency与Core abstractions Design；精确数组、
Shard topology与排序算法仍是versioned internal mechanism。

## 2. Correctness、failure 与资源证据

- PLAIN deterministic dense compaction覆盖Key、empty/singleton/multi Index、tail-to-hole、reference clear、
  directory/Chunk identity与managed accounting；
- AUTO encoded fallback保持candidate root、canonical state、missing Key与Index count；
- 两条路径均在publication后调用`IdentityHashIndex.validateForTesting`，证明每个final locator恰好一次、
  Bucket locator严格递增、hash/equality与new payload一致；
- 既有`BEFORE_KEY_REBUILD`、`BEFORE_SIDECAR_ACCOUNTING`、`BEFORE_CANDIDATE_PUBLISH`、
  `BEFORE_FINAL_COMMIT` fault保持old root/Chunk/data/version；
- locator map和replacement sidecar只在whole-operation temporary lease之后创建；OOME仍透传；
- runtime targeted suite为`81 tests / 0 failure / 0 error`，全runtime为`91 tests / 0 failure / 0 error`。

## 3. Fixed-host performance

主机为Apple Silicon，JDK为Amazon Corretto 8。1M candidate为3个fresh JVM、每JVM 2 warmup + 5 samples；
baseline在同一T2 HEAD、相同数据与参数下于代码修改前采集。全部fingerprint `PASS`。

| 1M frontier mutation | Current baseline | Final median | Delta |
|---|---:|---:|---:|
| Selection remove AUTO | 214.444 ms | 100.202 ms | -53.3% |
| Selection remove OFF | 166.491 ms | 72.956 ms | -56.2% |

10M AUTO在`-Xms8g -Xmx24g`、24 GiB managed budget、1 warmup + 3 samples下为
`1,057.982 ms`；M2 P6资格快照为`2.679 s`，约下降`60.5%`。同run ingest为`5.199 s`、
Selection update为`486.027 ms`，mutation fingerprint与post-state均`PASS`。

CPU profile中Selection remove已经由`IdentityHashIndex.projectLocators`、candidate Chunk finish/encode
与payload copy构成；旧`rebuild -> addStored -> hashField/findStored/fieldEquals`stack退出。该结果证明收益
来自删除重复Index discovery，不是改变数据分布、业务结果或correctness guard。

这些数字是fixed-host relative evidence，不是跨硬件SLA或release claim。

## 4. Qualification

- `mvn -pl soma-runtime -Dtest=GeneratedTableTest test`：`PASS`；
- 1M AUTO/OFF 3-run frontier mutation：`PASS`；
- 10M AUTO async-profiler frontier mutation：`PASS`；
- `./scripts/check.sh`：`PASS`，包括完整runtime/processor suite、generated consumers、三个reference
  application、benchmark qualification、local package、SBOM与provenance；
- `git diff --check`与旧projected rebuild搜索：`PASS`；
- 无新增dependency、module、public surface或artifact。

## 5. Boundary

- replacement sidecar仍需线性访问全部Index membership并创建独立candidate，这是atomic publication的
  当前成本，不宣称Selection remove具有point remove复杂度；
- AUTO的剩余热点是affected Chunk candidate copy与finish/encode，不能在本切片中通过原地修改encoded
  state或弱化zero-publication消除；
- 单个极高cardinality Bucket仍需要排序projected locator；只有新profile证明其主导真实场景时才可
  单独治理；
- 本记录不授权GitHub push、Release、Package、签名或正式发布声明。
