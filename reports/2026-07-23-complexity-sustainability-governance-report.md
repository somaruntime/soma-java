# 复杂度可持续性后续治理报告

类型：Report / Governance

状态：当前

Owner：SOMA Java 复杂度可持续性治理输出

受众：项目 Owner、SOMA 设计与实现维护者

适用版本：`0.2.0-SNAPSHOT`；Stage 0 基线 `e69eec2`，实现候选 `8f685e2`

输入事实源：正式 Blueprint/Design、Implementation Map、Engineering、代码与 Git provenance、generated/schema/class manifest、scenario/benchmark/code-size artifact 和完整 Gate

事实范围：generated footprint、benchmark lane、processor emitter 与 runtime 状态机四项后续审计，三个实施 slice、验证、残余风险和 scope non-regression 结论

非事实范围：重新定义产品 Design、跨环境性能声明、public release readiness 或 G6 处置

治理日期：2026-07-23

审查环境：Azul Zulu OpenJDK `1.8.0_492-b09`，Maven Wrapper `3.9.16`，macOS `26.5.2`，aarch64

审查方法：正式 Owner/代码/Git history/Gate 审计、逐 slice 独立提交、clean generation 等价比较、artifact strict validation、完整项目 Gate 与 scope non-regression 审查

最后审查日期：2026-07-23

## 1. 结论

本专题作为[项目复杂度与可维护性治理](2026-07-23-project-complexity-and-maintainability-governance-report.md)的证据驱动后续审查，已正式收口。

治理修复了三个有证据的问题：

- benchmark smoke 的 manifest、workload、validation、evidence 与 aggregation 不再集中在一个 suite；
- processor admission 不再依赖 emitter support，Table artifact source helper 不再借道 Auxiliary emitter；
- generated Scan 在原 fixed-candidate Gate 之外获得 artifact/schema 两级归一化诊断。

Runtime 大状态机没有足够拆分证据，因此保持不动。治理没有把文件大小或 LOC 当成删除依据，也没有改变 Blueprint、Design、public/generated API、annotation Schema、Access Model、runtime 语义或性能 Gate。

## 2. 基线、设计与提交

Stage 0 在 `e69eec2` 固定四项只读审计，Stage 1 在 `6a9d8d0` 固定责任设计和非回归边界。三个实施 slice 均可独立保留：

| Commit | Slice | 结果 |
|---|---|---|
| `242d83f` | benchmark responsibility | suite 收敛为 orchestration/compatibility facade，contract、workload、evidence、aggregation 分责 |
| `79c0a89` | processor ownership | selector public shape 归 codegen model；Table Scan executor source 退出 Auxiliary emitter |
| `8f685e2` | generated footprint evidence | 原 Gate 不变，新增逐 artifact/schema 诊断与汇总一致性检查 |

`c572522` 固定实施结果、残余风险与项目 Owner 审查停止点；项目 Owner 接受后，本报告与正式 Owner 完成原子固化。

## 3. Benchmark 责任结果

当前依赖为：

```text
SmokeLaneSuite
  -> SmokeLaneContract
  -> SmokeLaneWorkloads
  -> SmokeLaneEvidence
  -> SmokeLaneAggregation

BenchmarkModel -> SmokeLaneContract
```

`SmokeLaneSuite` 从 2,369 行收敛为 46 行，只保留编排及窄 compatibility delegate。`SmokeLaneContract` 拥有 manifest、identity、metadata 与 validation；workload、observation evidence 和 repeated-measurement merge 各有单一 Owner。

JSON schema 的 `x-soma-laneBinding=SmokeLaneSuite.validateLaneRecord` 保持不变，兼容 delegate 不重新拥有契约。Source-shape checker阻止 suite 重新吸收 workload/validation/aggregation，并阻止 `BenchmarkModel` 再依赖 suite。

验证保持：

- schema `soma-benchmark-smoke-v4`；
- 20 条 lane record 和重复执行顺序；
- 36 条 negative artifact path；
- workload identity、measurement 与 aggregation invariant；
- `claimAllowed=false`。

本轮不删除或合并 lane，也不产生新的性能 claim。

## 4. Processor 所有权结果

Selector canonical public parameter types 与参数分组属于 codegen model，现由 `DenseSelectorCodegenModel` 拥有。`SomaProcessor` admission 读取该 model，不再依赖 `DenseSelectorSourceSupport`。

写入 Table artifact 的 Candidate Scan terminal executor source 由 `DenseScanExecutionSourceSupport` 拥有；`DenseTableSourceEmitter` 不再依赖 `DenseAuxiliarySourceEmitter`。Selector source arguments、comparison、change 与 unique support 继续由 `DenseSelectorSourceSupport` 拥有。

`DenseTableSourceEmitter -> DenseExactIndexSourceEmitter` 被保留为 artifact-internal composition；本专题没有证据支持进一步拆分。

Clean 前后：

- generated Java SHA-256 清单一致；
- schema artifact/hash 清单一致；
- generated class/public manifest 一致；
- external consumer 与 codegen admission 通过；
- 4 个 executable scenario、222 个 generated type、782 个 Java 8 major-52 class 保持。

因此 processor 变化是内部责任修正，不建立新的生成契约版本。

## 5. Generated footprint 结果

原 33-table fixed candidate、Stage 1 基线和 15% ceiling 均未修改。`check-scan-code-size.sh` 新增：

- `scan-artifact-footprint.tsv`：逐 Scan source、top-level class、nested class 与 class family；
- `schema-footprint.tsv`：逐 schema 的 table、field、physical leaf、selector 与生成源码规模；
- min/max/average、feature totals、准确的 `topLevelClassBytes` 和兼容 alias；
- TSV 汇总必须重建原 aggregate 的一致性检查。

当前样本为 4 个 schema、33 个 table、149 个 field、161 个 physical leaf 与 8 个 selector。Scan source 合计 785,446 bytes / 3,392 lines；单 Scan 为 22,057–28,284 bytes，平均 23,801 bytes；最大 class family 为 41,866 bytes。两次 clean 执行的 TSV 字节一致。

归一化数据只解释增长形状，不证明单 feature 因果斜率，不替代 compiler admission，也不构成 256-table/256-leaf 容量承诺。

## 6. Runtime 保留裁决

`DenseTableState` 与 `ChildOwnershipRegistry` 未修改。当前大小信号仍不足以证明存在两个独立变化原因；table mutation 与 ownership 原子协调继续由现有状态边界拥有。

只有未来至少两次独立 metrics/resource 变化持续绕开 mutation coordinator，才重新审查 satellite state。该事件触发条件是审查入口，不是预设拆分路线图。

## 7. 正式 Owner

- compiler/codegen 当前责任由[编译器与代码生成地图](../docs/implementation-map/compiler-and-codegen-map.md)投影；
- benchmark 当前责任由[场景与 benchmark 地图](../docs/implementation-map/scenario-and-benchmark-map.md)投影；
- footprint artifact 与 Gate 入口由[测试与 evidence 地图](../docs/implementation-map/test-and-evidence-map.md)投影；
- 复杂度与 evidence 分责规则由[测试与 evidence](../docs/engineering/testing-and-evidence.md)拥有；
- 本报告只拥有本次审计、实施证据、残余边界与 scope non-regression 结论。

没有新的产品规范性事实需要进入 Blueprint 或 Design，也没有新的 Conformance 偏差。

## 8. 残余边界

- `SmokeLaneWorkloads` 与 `SmokeLaneContract` 仍较大，但当前各自只有一个共同变化原因；LOC 本身不触发继续拆分。
- `BenchmarkModel` 仍承载通用 model/JSON/validation；只有出现独立 co-change 证据才重新治理。
- suite compatibility delegate 与旧 lane binding 有意保留；改变 artifact contract 需要独立授权。
- footprint 诊断尚未覆盖 compiler 最大 envelope，不得外推为产品容量承诺。
- runtime 状态机只按已记录的事件条件重新审查。

这些边界不是未完成实现，也不依赖未来重写才能使本轮成立。

## 9. 最终验证与 scope non-regression

正式切换候选在上述 Zulu JDK 8 环境通过：

- document scope/checker 与 `git diff --check`；
- Maven reactor、public API、compiler、codegen admission、schema/hash/golden；
- runtime、keyspace、generated keyed/dense、Access Model、child 与 external consumer；
- 四场景、benchmark smoke、post-cutover component、Scan code-size 与 FJSP allocation/GC；
- `./scripts/check.sh`，最终结果 `project-check: ok`。

Scope non-regression 结论：

- Blueprint 与 Design 未修改；
- public/generated API、annotation Schema、schema hash 与 Access Model 未变；
- ownership、Index 生命周期、runtime protocol 与失败原子性未变；
- 四场景、20 条 benchmark lane、correctness/compatibility/allocation/code-size Gate 未删减或放宽；
- 无第三方依赖、temporary public API、parallel fact source 或未来迁移依赖；
- 没有建立跨环境性能 claim；
- G6 与 release readiness 未触碰，继续保持 blocked。

Temporary 已在长期事实、报告、入口与 checker 同步并通过最终验证后删除，不归档。
