# Java-only SOMA V1 benchmark smoke report

状态：passed（G5 benchmark contributor；不单独关闭 G5）
日期：2026-07-11
唯一 Owner：`soma-benchmarks`
Capability：`V1-SCENARIO-BENCHMARK`
Runner artifact：`soma-java-benchmark-runner-v2`
JSONL schema：`soma-benchmark-smoke-v2`
implementation commit：`2597c81895ea024867b2c8bb2eafab175e6cea5c`

本报告只证明 G5 §9 的 benchmark runner、20 条最小 integrated smoke workload、结构化 JSONL 与 strict validator 在记录环境通过。所有 record 固定 `level=smoke`、`claimAllowed=false`；不支持 throughput、latency、memory 或相对性能优势声明。

## 1. 实现与 evidence contract

- Required manifest 从 51 条带 generic kernel 改名的伪领域 lane 收口为 G5 §9 的 20 条最小 integrated workload；未执行的领域 diagnostic lane 不再输出 `passed` record。
- Generated workload 直接使用 `soma-examples` 生成的 table/batch/rows API；没有 `List<Row>`、DTO graph、reflection、metadata interpreter、Stream、boxing/per-row allocation hot path。
- JSONL v2 新增 lane-bound `workloadId` 与 exact `workloadEvidence`，并记录 lane-specific proof、真实 implementation 类型、正执行计数与 phase。
- Validator 重新 parse 落盘 artifact，校验 exact root field、nested required/type/range/const、manifest 唯一性、lane-specific evidence、non-empty maps、known limitations 与 `claimAllowed=false`。
- Negative artifacts 覆盖：claim=true、missing lane、unknown root field、wrong workload、empty workload evidence、zero measurement、wrong nested type、wrong lane proof，以及selector/keyspace/sidecar/materialization任意非空object绕过，共 12 类。

## 2. G5 §9 exact manifest

| Required shape | Evidence lane |
|---|---|
| optional all-present / all-absent / mixed bitmap | `kernel.optional_all_present`、`kernel.optional_all_absent`、`kernel.optional_mixed_chunks` |
| packed primitive vs handwritten primitive array | `kernel.packed_scan` |
| generated fused Row Pipeline / Cursor reuse / no per-row object | `generated.pipeline_fusion` |
| SparseInt / Hash load, collision, rehash, domain guard | `kernel.keyspace_domain_load_collision_rehash` |
| generated normal / constructed hash-collision full equality | `kernel.key_lookup_normal`、`kernel.key_lookup_collision` |
| batch reserve / insufficient-capacity growth | `kernel.batch_import.reserve`、`kernel.batch_import.growth` |
| generated maintained order dirty/lazy rebuild | `generated.ordered_access_lazy_rebuild` |
| generated keyed frontier add/update/dynamic first/remove | `generated.keyed_frontier` |
| generated dense replace + maintained/dynamic `findFirst` / `firstOrThrow` | `generated.dense_scratch_replace_order` |
| ColumnView acquire/read/release and typed lifecycle errors | `kernel.column_view` |
| parent-local child scan vs flat baseline | `child_locality.parent_scan_vs_flat` |
| generated recursive Map/schema-object/List materialization | `generated.materialization_recursive_success` |
| generated five-dimension budget + allocation admission + no partial + recovery | `materialization.budget_boundary` |
| single/batch compaction + capacity/scratch reuse | `kernel.compaction_capacity_reuse` |
| sidecar clean/dirty/rebuild storm | `kernel.sidecar_clean_dirty_rebuild_storm` |
| summary vs diagnostic integrated operation overhead | `kernel.stats_mode_overhead` |

关键 lane-specific facts：

- collision lane 构造两个不同 `LocationPairKey`，使 generated composite FNV hash 相同；artifact 记录 `fullEqualityDistinguished=true` 且 collision count 为正，两条 key 分别 fetch 到正确值。
- KeySpace lane实际执行SparseInt负key与maximum+1 lookup miss，并对负key `put`执行domain reject；evidence固定`2`个out-of-domain miss和`1`个guard reject。
- keyed frontier 使用 generated `MachineCandidateTable` 完成 128 条 add、64 条 grouped update、dynamic sorted `firstOrThrow` 和 grouped remove。
- dense workspace 使用 generated `InsertionCandidateRowTable.replaceAll`，maintained `byBestDelta().firstOrThrow` 与 dynamic `sorted().findFirst/firstOrThrow` 在同语义数据上结果一致。
- fusion 使用 generated `StateVectorRowTable` 的 filter + limit + update terminal，记录 reusable operation scratch 与 structural `perRowObjects=0`。
- recursive materialization 使用 generated `OperationDefinitionTable.materialize()`，实际产生 root `Map`、schema objects 与 parent-owned candidate `List`；budget lane先读取真实depth/table/row/leaf/bytes用量，再逐维执行exact limit成功和limit-1 typed failure，记录exception中的limit/current/proposed/path；随后执行受控allocation failure，验证无partial result、table rows不变并可恢复成功。
- stats lane在 SUMMARY 与 DIAGNOSTIC 下执行相同 begin/scan/end operation workload，不再只测 `statsSnapshot()`。

## 3. Validation record

执行命令：

```text
./mvnw -B -ntp -pl soma-benchmarks -am test-compile
./scripts/check-benchmark-smoke.sh
git diff --check -- soma-benchmarks scripts/check-benchmark-smoke.sh
```

最终结果：

- reactor `test-compile`：SUCCESS，benchmark 4 个 main source + 1 个 test source 使用 Java 8 target 编译；
- artifact negative paths：12/12 被拒绝；
- primary/repeat runner：各 20 条，required manifest 顺序一致；
- primary/repeat validator：各 20 条通过 exact/nested/lane-specific validation；
- runner/validator classfile major：52；checked-in schema 与 packaged resource 一致；
- `git diff --check`：通过；
- 最终失败 0、跳过 0、豁免 0。

本机 evidence：

- 最终Zulu全量目录：`target/benchmark-smoke.LY9fqL`；Corretto post-fix focused目录：`target/benchmark-smoke.YL4tuu`；
- 最终Zulu primary JSONL：20 行，SHA-256 `a4ddb55fb396a38353a1374e26a7b6f2af4a64301b96837904a4a64fe92b341e`；
- exact schema SHA-256：`37b234422b3307d3c184d2495443b9f8753d510e537f15b4f19713c4adf15a22`；
- implementation checksum manifest SHA-256：`71a91a8206197da1b1b71914275a7b00f46f65b76d0f30ef18a2130a34e958ad`。

验证环境：

- Azul Zulu OpenJDK `1.8.0_492-b09`，VM build `25.492-b09`；`javac 1.8.0_492`；
- Maven Wrapper / Apache Maven `3.9.16`，revision `2bdd9fddda4b155ebf8000e807eb73fd829a51d5`；
- macOS `26.5.2` / Darwin `25.5.0`，`arm64`（JVM/Maven `aarch64`）；
- record CPU identity `arm64`，memory 为当前 JVM max heap。

Amazon Corretto 8.492.09.2 / OpenJDK `1.8.0_492-b09`使用同一Maven/OS/architecture执行post-fix `./scripts/check-benchmark-smoke.sh`，20 primary + 20 repeat、12 negative均通过。

本机通过只证明上述环境；不能外推为 G6 support matrix。

## 4. V1 scope non-regression

| 审计项 | 前 | 后 |
|---|---|---|
| `V1-SCENARIO-BENCHMARK` | `implemented-unverified`，含伪 passed lane | `evidenced`，20 条 G5 minimum integrated workload |
| G5 benchmark contributor | 未可信 | passed |
| G5 overall | 未关闭 | 已由`reports/java-v1-g5-examples-benchmark-gate-report.md`联合examples与Access Pattern Cards关闭 |
| G6 / V1 Goal | active | blocked（G6外部发布事实不足） |

- Capability、正式 Owner、root Gate 与 release claim 未缩水；只是移除了没有真实 workload 的虚假 evidence claim。
- 未实现的领域 diagnostic lanes仍保留在 `runtime-state-benchmark-contract.md` 的可测问题目录，不被标为 optional/dropped，也不伪装成 smoke passed。
- 后续工作是 additive evidence 或 contract-preserving internal refinement；不要求迁移 public/generated API、核心事实、consumer 或 canonical hot path。
- 未引入 temporary contract、temporary hot path、future migration、rewrite 或第三方依赖。

## 5. Known limitations

- single-process、single-fork smoke 不是 JMH/claim-grade 方法；timing 受 JIT、GC 与机器噪声影响；
- 不采集 allocation profiler、physical memory、cache miss 或 branch counter；bytes 为 deterministic estimate；
- `perRowObjects=0` 是 generated code/cursor/scratch 结构证据，不是 JVM allocation profiler 结果；
- controlled allocation failure 是 deterministic admission fixture，不是 JVM OOME 注入；
- 未执行的领域 diagnostic lanes没有 artifact record，不能从本报告推导相应领域性能结论；
- 本报告不单独关闭 G5、RC、G6 或 release readiness；G5由root closeout report联合关闭，G6仍blocked。
