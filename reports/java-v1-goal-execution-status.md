# SOMA Java V1 Goal execution status

类型：Report / Status

状态：G0–G6 passed for selected `private-github-source` profile

Owner：SOMA Java Goal execution

受众：SOMA maintainer、Gate owner、release owner

适用版本：`0.2.0-SNAPSHOT` 2026-07-28 private-source candidate

输入事实源：正式Blueprint/Design/Conformance/Engineering、executable source、
generated/public consumers、runtime-scale qualification、三个Example、private
SCM与GitHub CI/qualification

事实范围：当前G0–G6状态、最新candidate identity、selected profile与claim boundary

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
base commit: e68c4e42704bbf4c6b2fe1c28dc7dcd8e27dcc51
content-sha256: 509ea5aa50e50a97b1461900f0063adb50b781dba5012cd203be702e89d0b7c6
```

该identity绑定identity cutover后的完整可执行产品/evidence surface；后续
engineering setup与正式Report不会反向改变被验真的runtime-scale candidate。正式证据见
[综合治理报告](2026-07-28-runtime-boundary-group-scale-readiness-governance-report.md)。

2026-07-28完成的个人发布身份与私有仓库治理把copyright/SCM/package root迁移到
ArthurFeng、`somaruntime/soma-java`与`io.github.somaruntime.soma`，并以public/
generated contract、external consumer、三个Example、Linux CI、clean package/
security qualification重新取证；产品语义和既有重型qualification目标未改变。
Codex Cloud尝试未完成full check且因执行时间不可接受而停止，不属于本次ready
结论。

## 2. G0–G6

| Gate | 状态 | 当前直接证据 |
|---|---|---|
| G0 | passed | Java 8产品边界、单一Blueprint/Design Owner、Capability/claim boundary与scope checker |
| G1 | passed | four-kind schema、String、Metadata hierarchy、compiler diagnostics、schema/hash clean repeat |
| G2 | passed | Zulu full JDK 8 integration、generated Metadata/Group/callback、v11/v3/v4协议、golden和old-token absence |
| G3 | passed | storage/locator/Candidate/relation/Delta/Window、bounded scheduler、Invocation ledger、Result Delivery、failure/observation |
| G4 | passed | dense/keyed/access/child/breadth external Maven consumers、public API golden、Java major 52 |
| G5 | passed | reference differential、component/footprint、十lane runtime-scale qualification、三个Example audit/correctness及九profile Full Gate |
| G6 | passed for selected private-source | ArthurFeng/Apache-2.0、private SCM与完整历史、support/security/CODEOWNERS、clean package/security provenance、macOS/Linux Zulu 8 matrix和CI闭合；public/Maven未选择 |

G0–G6的`passed`只证明selected private-source profile与记录环境；不自动公开
repository、不发布artifact，也不构成production或跨环境性能claim。

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

Conformance只保留`CF-005`：环境/profile性能evidence有限。`CF-006`已对用户明确
选择的private-source profile关闭；public GitHub和Maven Central保持
`not-selected`，不是被删除的差距或已通过的profile。

若产品要扩大到其他环境、wide/composite/mutation-heavy 100M、其他String profile或
public performance claim，应建立新的预注册qualification。当前没有以future/MVP/
optional改名掩盖的production gap，也没有本专题内仍待实现的canonical migration。

## 7. Claim boundary

允许陈述：当前candidate在记录的Zulu 8 macOS/Linux matrix通过完整build/
contract；macOS profile通过既有application与受限scale qualification；
private GitHub source profile ready。

不允许陈述：Codex Cloud ready、任意Schema 100M、任意String 100M、Linux性能、
跨环境SLA、production-ready、public RC、Maven Central ready或已公开发布。
最终profile状态仍由[G6报告](java-v1-g6-release-readiness-report.md)拥有。
