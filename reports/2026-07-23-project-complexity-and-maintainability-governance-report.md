# 项目复杂度与可维护性治理报告

类型：Report / Governance

状态：当前

Owner：SOMA Java 项目复杂度与可维护性治理输出

受众：项目 Owner、SOMA 设计与实现维护者

适用版本：`0.2.0-SNAPSHOT`；compiler/codegen implementation commit `6b6dc49`，evidence/checker candidate `9551039`

输入事实源：正式 Blueprint/Design、Implementation Map、代码、Git provenance、compiler/generated/runtime/scenario/benchmark Gate 与本专题多 fork artifact

事实范围：本专题的复杂度裁决、历史/current 文档治理、processor/codegen 内部重构、evidence 决策、性能非回归与最终 scope 审查

非事实范围：重新定义产品 Design、跨机器性能声明、public release readiness 或 G6 处置

治理日期：2026-07-23

审查环境：Azul Zulu OpenJDK `1.8.0_492-b09`，Maven Wrapper `3.9.16`，macOS `26.5.2`，aarch64

审查方法：Owner/引用/变化原因审计、逐切片 byte-stable generation、external consumer 与完整 Gate、5 个独立 JVM fork 对照、class manifest 比较

最后审查日期：2026-07-23

## 1. 结论

本专题已正式收口。治理没有把“瘦身”等同于减少 LOC，而是减少 historical active surface、消除 processor/codegen 的责任集中与反向依赖，并只增加能防止结构回退的最小 Gate。

正式 Design 在专题实施期间保持稳定。Public/generated API、annotation Schema、schema hash、Access Model、运行时语义、ownership、Index 生命周期、失败原子性、四场景和性能 Gate 均未缩水。没有引入第三方依赖，也没有处理 push、发布或 release readiness；G6 继续保持 blocked。

## 2. 基线与裁决

治理起点为 `2ff09b1`，Stage 0.1 immutable baseline 为 `eb1a520`。起点 tracked 口径为：

| 观察面 | 基线 |
|---|---:|
| 全部 tracked 文件 | 608 |
| Java | 362 份 / 33,613 行 |
| Markdown | 129 份 / 19,040 行 |
| Shell | 29 份 / 3,857 行 |
| superseded 文档 | 26 份 / 7,214 行 |
| annotations + processor + runtime 主源码 | 16,372 行 |

Stage 1 在 `4c853e2` 完成六项裁决：

- historical Design 保留稳定路径，但压缩为 thin tombstone；
- 已被后续结论取代的 root checkpoint Report 进入 archive；
- Blueprint 的完整 journey 与场景示例属于必要产品复杂度，不做篇幅驱动删减；
- processor/codegen 按 normalized model、codegen model、orchestrator、artifact emitter 分责；
- fixture、scenario、benchmark 和脚本保留独立证据域，不建立通用 runner；
- 复杂度使用软触发器，真实契约继续由既有硬 Gate 约束。

## 3. 文档历史负担与 current 导航

`bc5bdbb` 完成文档候选：

- 26 份 superseded Design 由 7,214 行压缩为 702 行；
- 每个 tombstone 保留原路径、current replacement、Owner、历史 commit `74f8f3c344eda6f5b8c3ddbd799f3460c1f786e1` 和可执行 `git show` provenance；
- checker 根据 metadata 发现并验证所有 tombstone，不再维护 26 项硬编码清单；
- 9 份 checkpoint Report 移至 `reports/archive/`，root Report 由 24 份降为 15 份；
- current README、模块入口和内部链接不再把历史正文作为事实 Owner。

没有删除仍有 provenance 价值的路径，也没有修改 Blueprint 或正式 Design 以迁就当前实现。

## 4. Processor/codegen 责任优化

Stage 3 由五个可独立保留的提交组成：

| Commit | 责任变化 |
|---|---|
| `a77b3e1` | dense emission model 离开 generator |
| `59480a7` | selector binding/comparison/change support 独立 |
| `c10ef87` | Cursor/UpdateCursor/Batch/Mutator/KeyTraversal/Scan emitter 独立 |
| `4859497` | generator 收敛为 deterministic artifact orchestrator |
| `6b6dc49` | normalized schema model 与 canonical schema JSON support 离开 processor 主类 |

结果：

- `SomaProcessor`：3,054 → 2,156 行；
- `DenseTableSourceGenerator`：4,432 → 47 行；
- Table、auxiliary facade、exact-index、selector support、dense codegen model 与 normalized schema model 分别拥有稳定责任；
- 核心三模块生产源码：16,372 → 16,524 行。

新增 152 行是显式责任边界的成本，不是失败。当前最大 emitter 仍保持单一的 packed Table 生成责任；没有为了行数继续拆出转发层、模板 DSL、AST、反射或 metadata interpreter。

最终 tracked source/doc/script 口径与起点对比：

| 观察面 | 起点 | 最终 | 变化 |
|---|---:|---:|---:|
| 全部 tracked 文件 | 608 | 617 | +9 |
| Java | 362 / 33,613 行 | 370 / 33,765 行 | +8 / +152 |
| Markdown | 129 / 19,040 行 | 130 / 12,721 行 | +1 / -6,319 |
| Shell | 29 / 3,857 行 | 29 / 3,898 行 | 0 / +41 |

文件数增加来自责任拆分，Markdown 大幅下降来自 historical body 退出 active checkout；两者都只是治理结果，不是未来配额。

## 5. 生成契约与可执行等价

Stage 1 在 clean Zulu JDK 8 generation 上建立 222-file per-source SHA-256 manifest。每个 Stage 3 切片均重新 clean generate；最终 manifest SHA-256 仍为：

`2ce2b6cd4e5da117b311a3c0719f5843140042fb6a0f8d1d269707c334de4897`

治理起点 `2ff09b1` 与最终实现候选还完成 class manifest 比较：

| 可执行范围 | 相同 class manifest SHA-256 |
|---|---|
| `soma-runtime-core` | `7c5c55f5cebe9a8a0790624448e949b29860ddab3ecad9c5232e900e0d17b808` |
| `soma-examples`（含 generated classes） | `8d9512d96dbfaaecd37b7ff51f4575089a7c77c245018c29a8276653aab4e187` |
| `soma-benchmarks` | `5442ebeb8c8efaa57137cb2dc1150bce1ef83fa38c25658188f9442ee6289810` |

因此本轮内部重构没有改变 runtime、四场景或 benchmark 的可执行字节。Processor 自身 class 变化只反映内部责任迁移；public API、schema/hash、golden 和 external consumer 由完整 Gate 单独验证。

## 6. Evidence 与 Gate 决策

`9551039` 在既有 codegen admission Gate 中增加最小责任检查：

- normalized schema model 与 dense codegen model 必须保持独立 Owner；
- generator 必须只编排 auxiliary 与 Table artifacts；
- SchemaModel/TableSpec 不得回流 processor/generator；
- exact-index emitter 不得反向依赖 orchestrator。

检查约束依赖方向，不约束 LOC。169 个 compiler fixture、四场景、benchmark lane 和 29 个脚本没有因外观相似被合并；它们仍证明不同 compiler、runtime、consumer 或 measurement failure domain。没有提交重复的 222-file 永久 manifest。

## 7. 多 fork 性能非回归

起点 `2ff09b1` 与最终候选均使用：

- 同一 Zulu JDK 8、机器、seed 与 100,000-operation FJSP workload；
- `-Xms512m -Xmx512m -Xmn96m -XX:+UseParallelGC`；
- 5 个独立 JVM，每 fork 2 次 warmup、3 次 measurement；
- 每个版本共 15 条有效记录。

| 指标 | 起点 | 最终候选 | 变化 |
|---|---:|---:|---:|
| solve median | 266.130 ms | 270.599 ms | +1.679% |
| solve range | 242.100–337.113 ms | 251.927–341.456 ms | — |
| allocation median | 8,492.397 B/op | 8,488.279 B/op | -0.048% |
| Young GC | 267 / 773 ms | 267 / 758 ms | count 相同 |
| Full GC | 5 / 262 ms | 5 / 269 ms | count 相同 |

15 条记录的 assignments、completed jobs、makespan、tardiness、checksum 和 runtime plan hash 各只有一个取值。起点与候选 raw JSONL checksum-manifest SHA-256 分别为 `c319e9f22c3cc1c92d5b64bb467b1e1790516688f692922e57462c8fc0e1d719`、`01713d975966d674f2a3abcee7603e7b34d2e94b83c451e14736e4edfa50d966`。

Solve 的 +1.679% 与 GC time 小幅反向波动不形成性能退化结论：两侧 runtime/examples/benchmark class bytes 完全相同，allocation 与 GC count 没有退化，measurement range 高度重叠。这是同一可执行字节的运行噪声，不被表述为收益，也不外推到其他机器、JDK 或 workload。

## 8. 正式固化与长期 Owner

- current compiler/codegen 投影由 [Compiler 与代码生成地图](../docs/implementation-map/compiler-and-codegen-map.md)拥有；
- 复杂度软触发器与 evidence 合并边界由[测试与 evidence](../docs/engineering/testing-and-evidence.md)拥有；
- historical/current、唯一 Owner 与 tombstone 规则继续由[文档治理](../docs/engineering/documentation-governance.md)拥有；
- 当前一致性判断由[Conformance](../docs/conformance/current-conformance.md)拥有；
- 本报告只拥有本次治理过程、测量和 scope non-regression 结论。

Temporary 已在上述长期事实固化、入口/checker 更新和最终验证后删除，不归档。

## 9. 最终验证与 scope non-regression

最终正式切换工作树在 Zulu JDK 8u492 上通过：

- `./scripts/check-docs.sh`；
- compiler phase-0、codegen admission、public API、schema/hash/golden 与 external Maven consumers；
- runtime invariant、dense/keyed/access/child/breadth；
- FJSP、VRP、Simulation、Game 四场景；
- component allocation/memory、Scan code-size、benchmark smoke 与 FJSP allocation/GC；
- `./scripts/check.sh` 和 `git diff --check`。

Scope non-regression 结论：

- Blueprint 与正式 Design 未被缩减或重定义；
- public/generated API、annotation Schema 与 schema hash 未变；
- packed SoA、exact access、swap-remove、ownership、Index/IndexSnapshot、失败原子性未变；
- 四场景的 application-owned heap/queue、事实源与提交边界未变；
- correctness、compatibility、allocation、code-size 和性能 Gate 未弱化；
- 无第三方依赖、临时 public API、平行模型或依赖未来重写才成立的切片；
- G6 和 release readiness 未触碰，仍为 blocked。

本专题没有遗留必须在未来完成才能成立的实现或文档尾项。后续复杂度变化按正式软触发器审查；只有新的证据或正式产品设计变化才需要开启新专题。
