# Access Model / Candidate Scan 产品化治理报告

类型：Report / Governance

状态：当前

Owner：SOMA Java Access Model / Candidate Scan 治理输出

受众：项目 Owner、SOMA 设计与实现维护者

适用版本：`0.2.0-SNAPSHOT`；implementation-affecting commit `fd82ebaa8dc4331b7cd9b3c62392d2472185f25f`

输入事实源：[产品蓝图](../docs/blueprints/soma-java-product-blueprint.md)、[Access Model 与 Candidate Scan Design](../docs/design/access-model-and-candidate-scan.md)、[Implementation Map](../docs/implementation-map/README.md)、commit-bound tests/benchmarks 与[性能报告](2026-07-23-access-model-candidate-scan-performance-report.md)

事实范围：本专题的意图、裁决、实现投影、scope non-regression、正式 Owner 切换和验证结论

非事实范围：重新定义正式 Design、跨机器性能声明、G6 或 public release readiness

治理日期：2026-07-23

最后审查日期：2026-07-23

## 1. 结论

本专题把 SOMA 从“所有访问都被 Row-shaped Pipeline 叙述”的接口模型，提升为完整 Access Model，并以 Candidate Scan 作为 CandidateAccess 的专用组合机制。产品目标、public/generated API、typed runtime execution、四场景、测试、benchmark 与正式文档已完成 clean cutover；没有保留 compatibility alias、第二套 executor 或待未来迁移的 canonical path。

可执行候选固定为 `fd82eba`。Schema semantics/hash、packed SoA、swap-remove、primary/exact incremental maintenance、child ownership、Index caller-responsibility与单 aggregate 原子性均未降低。Generated/runtime compatibility升级到v4，runtime plan protocol仍为v3；Maven development version升级为`0.2.0-SNAPSHOT`。G6仍因真实发布事实不足保持blocked。

## 2. 治理意图与目标

专题先建立 SOMA 本身的基本 Access Pattern，再由访问语义推导 API、Pipeline IR 和性能实现，而不是让当前数组或某个 FJSP 调用链反向定义产品。

目标包括：

- 建立 Point、Candidate、Column、Key、Bulk、Ownership 六类访问责任；
- 让每个重要 Pattern 拥有唯一、自然的 canonical API；
- 让 Unique 的0..1 cardinality、current Index、snapshot、borrow、materialization和mutation成本可见；
- 借鉴Java Stream的lazy stage、one-shot、short-circuit与terminal specialization，但不引入generic Stream/query runtime；
- 降低lazy plan、source、terminal和Traversal的临时对象成本；
- 清理Row-oriented public/generated命名，同时保留合法的物理row、count、materialized carrier和用户Schema名称；
- 保持四个真实Blueprint、correctness、compatibility、code size和end-to-end性能不回退。

本专题不增加range/order/join/top-k/reduction、第三方dependency、reflection、metadata interpreter、并发访问或跨Table transaction。

## 3. 核心产品裁决

```text
CandidateAccess := CandidateSource Stage* CandidateTerminal
PointAccess     := CurrentIndex | PrimaryKey | SecondaryUnique -> fixed terminals
ColumnAccess    := traversal | current-Index read | caller-managed snapshot gather
KeyAccess       := KeyTraversal -> borrow | materialize
BulkAccess      := Batch append/replace | clear | replaceChildren
```

- Table本身是Packed source，不再提供第二个等价根入口；
- `@SomaIndex` 使用 `scanByX`；`@SomaUnique`首先提供point family，并保留显式`scanByX`组合桥；
- Candidate family命名为`*Scan/*Cursor/*UpdateCursor`；key/column使用`*Traversal`；
- current Index统一使用`findIndex/requireIndex`，批量复制使用`indexSnapshot`；
- best-one不再要求`sorted(...).limit(1)`后创建snapshot；
- Candidate Scan lazy且one-shot，terminal-time绑定current source；Point、Column、Key、Bulk不强行进入同一planner。

这是首个公开发布前的clean breaking cutover，不生成旧名称alias。Consumer必须使用新artifact重新生成代码。

## 4. Runtime 与生命周期收口

Candidate Scan使用schema-specific typed source plan、small-inline stage storage和primitive/reference overflow arrays；terminal局部构造evaluation。Packed zero-stage terminal直接执行，exact source直接绑定maintained group，单Sort best-one采用stable arg-min；没有per-stage linked node、Stream、Iterator或per-candidate object。

P1 terminal lifecycle已统一：terminal接受执行后先消费handle；即使begin、mutation preflight或plan-default budget解析失败，也不能重试同一handle。显式null/非法参数仍在operation接受前拒绝，不误消费。Terminal完成或失败后清除callback、reference selector和Table strong reference。

P2 exact source-only `count()`在terminal-time读取current group cardinality，不遍历group Index；公开logical stats仍报告等价的scanned/matched。

Generated Scan handle只保存typed source-plan reference与generation；Table、source path和selector leaf由plan拥有，避免重复ownership和consumed handle retention。

## 5. 正式 Owner 原子切换

长期事实按以下唯一 Owner 固化：

| 事实 | 正式 Owner |
|---|---|
| 目标使用者心智模型与场景代码 | Product/FJSP/VRP/Simulation/Game Blueprint |
| Access family、组合代数、sequence、one-shot、terminal与成本边界 | Access Model 与 Candidate Scan Design |
| generated schema-specific投影与callback contract | Schema 与生成 API Design |
| packed/exact/swap-remove/IndexBuffer | Table、存储与访问 Design |
| handle/currentness/reentrancy/release | Ownership 与 lifecycle、Correctness 与 failure |
| compact plan、specialization与证据义务 | Performance Design、Engineering |
| 当前类、脚本、fixture与可执行surface | Implementation Map、代码与golden |
| 当前偏差判断 | Conformance |
| 数值和claim边界 | 本专题 Performance Report |

专题过程文档在正式事实、地图、Conformance、Engineering、Report与checker同步后删除，不归档，也不保留为平行事实源。

## 6. Compatibility 与迁移

| Surface | 迁移 |
|---|---|
| `*Rows/*Row/*MutableRow` | `*Scan/*Cursor/*UpdateCursor` |
| 显式Packed根别名 | 直接从Table调用terminal或首个stage |
| `findByX` group source | `scanByX` |
| `rowIndexes` | `indexSnapshot` |
| `findRowIndex/rowIndexOf` | `findIndex/requireIndex` |
| `*Keys` | `*KeyTraversal` |
| `*ColumnPipeline` | `*ColumnTraversal` |

四份canonical Schema hash与基线完全一致。Generated protocol从`soma-generated-runtime-v3`升级为v4，runtime compatibility从`soma-runtime-java8-v3`升级为v4；`soma-runtime-plan-v3`和plan输入集合不变，但compatibility字段值变化使plan hash确定性变化。Old generated/runtime pairing在create前fail closed。

## 7. Evidence 与目标非回退

`fd82eba`上的full `./scripts/check.sh`已得到`project-check: ok`，覆盖Maven reactor、public `javap`、compiler/codegen、dense/keyed/access/child/breadth external consumers、四场景、component allocation/memory、FJSP allocation/GC、Scan code size和diff检查。正式切换工作树在生产代码仍固定于该commit的前提下再次运行同一full Gate，也得到`project-check: ok`；因此正式Owner切换、checker更新和Temporary删除没有改变可执行候选结论。

专题性能结果包括16条allocation、24条memory、JFR attribution、33-table generated code size与5个独立JVM fork的FJSP A/B；详细方法和数值由[性能报告](2026-07-23-access-model-candidate-scan-performance-report.md)拥有。

Scope non-regression：

- annotation与Schema identity未改变；
- packed SoA、keyed/dense swap-remove、exact eager incremental maintenance和child ownership未改变；
- Index/IndexSnapshot继续采用caller-responsibility；
- 四场景仍保持application heap、业务策略和跨root commit责任；
- 未引入临时public API、parallel fact source、test-only bypass或未来rewrite；
- release/G6主体未触碰，也不声明public release readiness。

因此本轮不是目标缩水或局部补丁，而是对既定产品目标的完整投影与contract-preserving性能实现；后续工作只应是additive capability或internal refinement，除非新的正式Design专题重新裁决。

## 8. 当前边界

功能和性能证据足以支持个人项目继续试用与真实场景验证，但只限已测Java 8环境和workload。G6所需SCM/contact、namespace ownership、signing/publishing、clean public provenance与正式支持矩阵仍不足；本报告不改变该blocked状态。
