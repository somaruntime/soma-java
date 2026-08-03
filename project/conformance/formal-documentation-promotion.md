# SOMA Java 正式 Blueprint/Design 晋升审查

类型：Conformance Review

状态：`HISTORICAL_PASS`；已被 2026-08-03 V1 baseline supersede

正式事实源：是（仅拥有本次 replacement/conformance 结论）

Owner：产品基础 Temporary 到正式 Blueprint/Design/Conformance 的 replacement closure

审查日期：2026-08-01

后续状态：本记录只保存 2026-08-01 baseline 的 Temporary replacement provenance，不再
拥有 current readiness 或 Design。当前状态以
[大规模引擎正式晋升记录](large-scale-engine-formal-promotion.md)、
[实施前最终全局一致性审核](v1-final-pre-implementation-global-consistency-review.md)和
[Design 总览](../design/README.md)为准。

## 1. 审查目标

本次审查验证三类 Temporary 内容是否全部获得唯一正式去向，并防止把原文整体改名后
继续混合产品、技术、证据和用户投影：

```text
project/temp/soma-product-foundation/README.md
project/temp/soma-product-foundation/logical-api-draft.md
project/temp/soma-p2-generated-api-feasibility/
```

## 2. 正式目标结构

| Formal Owner | 责任 |
|---|---|
| [Blueprint](../blueprint/README.md) | 产品意图、用户模型、能力边界、质量属性与成功标准 |
| [Schema/Generation Design](../design/schema-and-generation.md) | schema composition、annotation、generated identity/object 与 full regeneration |
| [Data/Storage Design](../design/data-model-and-storage.md) | Table identity、storage、type、Key/Index、relation/order/lifecycle |
| [Logical API Design](../design/logical-api.md) | generated API、sources、capability、terminal、cursor/materialization/metadata |
| [Execution Design](../design/execution-and-concurrency.md) | currentness、admission、atomic publish、parallel/determinism/resource |
| [Result/Failure Design](../design/results-and-failures.md) | normal result、failure carrier/code/mapping/precedence/guarantee |
| [P2 Evidence Record](p2-generated-api-feasibility.md) | feasibility snapshot、findings、limitations 与 provenance |

## 3. 产品基础决策逐章晋升

| 原章节 | 正式去向 | 处置 |
|---|---|---|
| 1 文档角色 | 本审查和各文档 metadata | Temporary-only 说明不晋升为产品事实 |
| 2 产品意图 | Blueprint 2-3 | 晋升 |
| 3 四层职责 | Blueprint 4；Design index | Blueprint 拥有层级意图，各 Design 拥有精确合同 |
| 4 用户执行模型/parallel | Blueprint 5；Execution Design | 高层模型与执行合同分离 |
| 5 Composition/逻辑层级 | Schema Design；Logical API Design | 编译 identity 与用户 navigation 分责 |
| 6 Group/Table/关系/lifecycle | Data/Storage；Logical API | identity/data ownership 与 public accessor 分责 |
| 7 Schema object/storage/type | Schema；Data/Storage | declaration/generation 与 value/storage 分责 |
| 8 Source/Field/Column | Blueprint 5；Logical API；Data/Storage order | public projection 与 authoritative order 分责 |
| 9 Intermediate/capability | Logical API | 完整晋升 |
| 10 Terminal | Logical API；Execution；Result/Failure | capability、执行、observable outcome 分责 |
| 11 Direct operation | Logical API；Execution | API 与 atomic execution 分责 |
| 12 Currentness/concurrency/failure | Execution；Result/Failure | 完整晋升 |
| 13 Generated cursor/View | Logical API；Execution | shape/scope 与 runtime enforcement 分责 |
| 14 明确排除 | Blueprint；各 Design absence | 按事实 Owner 分散，不保留单一平行清单 |
| 15 P2 queue/裁决 | P2 Evidence Record；对应 Design | 产品 queue 已关闭；evidence 与合同分离 |
| 16 建立设计前 evidence | Conformance/各 Design admission Gates | 由正式 Gate 列表承接 |
| 17 Promotion/retirement | 本次治理闭环 | 原 Temporary 已删除 |

## 4. 逻辑 API 草稿逐章晋升

| 原章节 | 正式去向 | 处置 |
|---|---|---|
| 1 文档角色 | 本审查 | Temporary-only |
| 2 最短普通路径 | Blueprint 7；Logical API 2 | representative journey 保留，不创建未实现用户手册 |
| 3 Schema/关系/type/generated object | Schema；Data/Storage；Logical API | 按职责拆分 |
| 4 Generated hierarchy | Logical API 3 | 完整晋升 |
| 5 Group/default Group | Data/Storage；Logical API | identity 与 access 分责 |
| 6 Table direct operation | Logical API 5；Execution；Result/Failure | 完整晋升 |
| 7 Stream source | Logical API 6；Execution order/mode | 完整晋升 |
| 8 Intermediate/capability | Logical API 7-9 | 完整晋升 |
| 9 Query terminal | Logical API 10-11；Result/Failure | 完整晋升 |
| 10 Update terminal | Logical API 12；Execution mutation | 完整晋升 |
| 11 Remove terminal | Logical API 13；Execution mutation | 完整晋升 |
| 12 Currentness/concurrency/atomicity | Execution | 完整晋升 |
| 13 Metadata | Logical API 14 | semantic minimum 晋升，exact carrier 保留 implementation Gate |
| 14 Result/failure | Result/Failure | 完整晋升 |
| 15 Complete journey | Blueprint/API | representative/normative example 保留 |
| 16 刻意未定 API | Design index/Conformance | 产品语义均已关闭；技术 shape 作为 Gate |
| 17 API 成立条件 | Conformance/Design admission Gates | 去重并按 Owner 承接 |

## 5. P2 Temporary 晋升

| P2 content | 正式去向 |
|---|---|
| Product semantic findings | 对应 Design Owner |
| Generated signatures/capability absence | Logical API、Schema、Result/Failure Design |
| Cursor/View/array/primitive/owner mechanism | Logical API/Execution/Data Design + evidence record |
| Full-regeneration counterexamples | Schema Design + evidence record |
| Environment、command、result、limitations | P2 Evidence Record |
| Fixture source/build artifacts | 已按 Temporary lifecycle 删除，未成为 production/test surface |

## 6. Product Docs 处置

当前没有 production API、artifact 或 runnable Example，因此不创建 `docs/` 用户手册，
避免把尚未实现的规范写成“已经可用”的产品投影。最短 journey 和完整 reference journey
暂由 Blueprint/Logical API Design 作为规范性示例拥有；production implementation
通过 Conformance Gate 后，再按用户任务投影到 Getting Started、开发手册和 Examples。

## 7. 独立一致性检查项

- Blueprint requirement BP-1 至 BP-10 均有 Design Owner；
- composition/Group/Table/Field identity 没有第二套字符串或 owner-scoped model；
- ChildTable、ownership、cascade、join、Batch、Segment、public Column 未复活；
- Key/Index/null/type capability 在 Schema、Storage、API 三层一致；
- Key Field read-only、Field remove absent、Mapped mutation absent、Record distinct
  absent；
- sequential/parallel currentness、order、atomicity 与 failure arbitration 一致；
- `toArray(Class)`、parameterized Field、primitive no-boxing 边界一致；
- Result count、normal missing、failure code/precedence/state guarantee 一致；
- full-regeneration support 与 unsupported incremental claim 一致；
- P2 evidence 没有被外推为 production/runtime/performance/release capability。

## 8. Closure result

结果：`PASSED`

1. Blueprint BP-1 至 BP-10 已双向追踪到唯一 Design Owner；
2. 逐章 promotion matrix 与高风险合同搜索已覆盖三份 Temporary source；
3. `project/README.md`、root README 和 `AGENTS.md` 已改指正式 Owner；
4. Blueprint、Design、Conformance 已切换为 Active formal baseline；
5. 两份 product-foundation Temporary 与 P2 fixture/ignored build output 已删除；
6. P2 provenance、findings、limitations 和 snapshot digest 已进入正式 evidence record；
7. Product Docs 因无 production surface 明确延后，未用假 Quick Start 制造可用性声明；
8. 最终 repository/link/diff/no-stale-route Gate 的结果记录在本次治理 closeout 中。
