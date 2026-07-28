# SOMA Java V1 Goal execution status

类型：Report / Status

状态：G0–G5 passed；G6 blocked

Owner：SOMA Java Goal execution

受众：SOMA maintainer、Gate owner、release owner

适用版本：`0.2.0-SNAPSHOT` 2026-07-28 runtime-scale production candidate

输入事实源：正式Blueprint/Design/Conformance/Engineering、executable source、
generated/public consumers、runtime-scale qualification、三个Example和Gate输出

事实范围：当前G0–G6状态、最新candidate identity、剩余blocker与claim boundary

非事实范围：public release授权、未执行环境的支持或任意workload性能承诺

最后审查日期：2026-07-28

## 1. 当前结论

Runtime Boundary、Group、Scale Readiness与产品化综合治理已完成其授权范围：
TV0–TV9、最终设计、正式Owner、production、generated/public API、测试、
benchmark、Guide、三个Example、代码规模、production qualification和closeout均
闭合。`CF-009`–`CF-015`已关闭。

随后完成的
[V1产品面收敛与仓库瘦身治理](2026-07-28-soma-v1-product-surface-simplification-governance-report.md)
没有改变上述production candidate：四个production module主源码、224/2
public/internal executable分类、三个Example、全部benchmark claim和规模/String
目标保持不变；无consumer的testkit artifact、历史编排与重复文档退出，package和
security evidence补齐dataflow。该治理以
`d3f2e354fd553b3d2923cc2c145413930e06ba8c`为仓库surface基线，不替换下列
production content identity。

当前production/evidence source identity为：

```text
base commit: 6cde5d508c9ce3a1d873d282b1d4808e0334a288
content-sha256: dfe8fa98b2a411708359a378e05f22e2ad89a7b900c70d1f71e8dd1a6b7f8e69
```

该identity明确表示含未提交后续slice的working-tree candidate，不冒充immutable
commit。正式证据见
[综合治理报告](2026-07-28-runtime-boundary-group-scale-readiness-governance-report.md)。

## 2. G0–G6

| Gate | 状态 | 当前直接证据 |
|---|---|---|
| G0 | passed | Java 8产品边界、单一Blueprint/Design Owner、Capability/claim boundary与scope checker |
| G1 | passed | four-kind schema、String、Metadata hierarchy、compiler diagnostics、schema/hash clean repeat |
| G2 | passed | Zulu full JDK 8 integration、generated Metadata/Group/callback、v11/v3/v4协议、golden和old-token absence |
| G3 | passed | storage/locator/Candidate/relation/Delta/Window、bounded scheduler、Invocation ledger、Result Delivery、failure/observation |
| G4 | passed | dense/keyed/access/child/breadth external Maven consumers、public API golden、Java major 52 |
| G5 | passed | reference differential、component/footprint、十lane runtime-scale qualification、三个Example audit/correctness及九profile Full Gate |
| G6 | blocked | SCM/ownership/contact、signing/publishing、clean release provenance与完整support matrix仍不足 |

G1–G5的`passed`只证明上述精确candidate与记录环境，不自动发布artifact，也不改变
G6。

## 3. 综合治理阶段

| 阶段 | 状态 | 结果 |
|---|---|---|
| P0 baseline/governance checkpoint | completed | live branch/dirty attribution与docs contract建立 |
| P1 traceability/TV9 preregistration | completed | G01–G17与TV9语义/预算/退出条件冻结 |
| P2 TV9 | completed | callback-scoped read-only pilot accepted；Eager保持默认 |
| P3 synthesis/Owner decisions | completed | TV0–TV9 accepted/rejected/inconclusive原子综合 |
| P4 final product/design | completed | 产品叙事、Capability、Metadata、type、ownership、physical、execution、delivery、resource/failure闭合 |
| P5 independent audit | completed | scope、design与production-shape独立审计通过 |
| P6 formal promotion | completed | Blueprint/Design/Conformance/Engineering原子固化 |
| P7 disposition | completed | exact retain/replace/delete/evidence矩阵建立 |
| P8 production | completed | v11/v3/v4、完整Metadata、closed physical paths、scheduler/ledger/delivery |
| P9 examples | completed | 三应用真实multi-root lifecycle偏差迁入explicit Group；其余最佳实践保留 |
| P10 qualification/Gates | completed | Small/Medium/1M/10M/single+double100M/String/Expansion/Delivery/Soak及完整Gate |
| P11 closeout | completed | formal Report/Map/Conformance、Lab与Temporary退役、scope non-regression |

## 4. Scale 与String边界

Qualification ID：
`runtime-scale-qualification-20260728-dfe8fa98b2a4`。
十条required applicable lane全部`passed`并保持`claimAllowed=false`。100M结论
只覆盖：

- 单root 100M窄numeric-Key Table；
- 两个100M窄numeric-Key root同时驻留并做bounded same/cross Group relation；
- 两个100M String payload root，UTF-16 18..34、cardinality 1,024、跨表100%
  object sharing、payload/group/join角色。

String仍为caller-reference backend，不引入dictionary/arena；10M high-cardinality
payload与1M actual GC另有独立lane。任意wide schema、high-cardinality String Key、
unbounded expansion或其他环境不得由row count外推。

## 5. Reference application 与baseline

三个独立Java 8 consumer各自拥有显式Group、detached Runtime Metadata、领域
correctness和三个profile：

- industrial scheduler：default v5、large v4、long-run v4；
- grassing simulation：default v3、large v2、long-run v2；
- RTD：default/large/long-run均v2。

九份baseline由同一5-fork治理公式重校，普通Full Gate按每profile 3 fork全部通过。
所有application artifact保持`claimAllowed=false`。

## 6. 当前仍开放

Conformance只保留：

- `CF-005`：环境/profile evidence有限；
- `CF-006`：G6 release事实不足。

若产品要扩大到其他环境、wide/composite/mutation-heavy 100M、其他String profile或
public performance claim，应建立新的预注册qualification。当前没有以future/MVP/
optional改名掩盖的production gap，也没有本专题内仍待实现的canonical migration。

## 7. Claim boundary

允许陈述：当前candidate在记录的Zulu 8/macOS/aarch64环境通过完整功能、
application与受限scale qualification。

不允许陈述：任意Schema 100M、任意String 100M、跨环境SLA、production-ready、
release-ready、public RC或已发布。最终release status仍由
[G6报告](java-v1-g6-release-readiness-report.md)拥有。
