# SOMA Java V1 Goal execution status

类型：Report / Status

状态：G0–G4 passed；G5、G6 blocked

Owner：SOMA Java Goal execution

受众：SOMA maintainer、Gate owner、release owner

适用版本：`0.2.0-SNAPSHOT` 2026-07-29 engineering candidate

输入事实源：正式Blueprint/Design/Conformance/Engineering、executable source、
generated/public consumers、component/application baseline、runtime-scale
qualification历史artifact、private SCM与CI配置

事实范围：当前G0–G6状态、JDK authority、有效evidence与claim boundary

非事实范围：public release授权、未执行环境的支持或任意workload性能承诺

最后审查日期：2026-07-29

## 1. 当前结论

SOMA Java V1的产品设计和production实现没有因本次工程治理缩水：
Metadata/Group、four-kind type、String reference backend、closed Capability、
single/double 100M目标、Result Delivery、两层自适应并行和三个reference
application仍是完整目标。

本次把唯一compiler/validation authority从Azul Zulu 8迁移到Amazon Corretto 8：

```text
Amazon Corretto 8.502.07.1
java 1.8.0_502-b07
javac 1.8.0_502
Maven 3.9.16
```

本机Corretto已通过精确toolchain约束、reactor、public/generated/external
consumer、runtime/DataFlow contract、两个component baseline及九个application
profile。九份application baseline使用同环境5 fork重新校准，并以普通3 fork
全部回放通过。旧baseline的Schema/RuntimePlan identity漂移在Zulu和Corretto上
生成结果一致，已经确认不是JDK不确定性。

2026-07-28十lane runtime-scale qualification和Linux package/security/CI只在旧
Zulu authority下形成。它们仍是相应历史candidate的有效证据，但不再证明当前
Corretto candidate。因此G5保持`blocked`直至Corretto重型qualification完成，
G6 selected `private-github-source`保持`blocked`直至当前immutable commit的
Corretto Linux CI与release qualification完成。

## 2. G0–G6

| Gate | 状态 | 当前直接证据与缺口 |
|---|---|---|
| G0 | passed | Java 8产品边界、单一Blueprint/Design Owner、Capability与claim boundary保持 |
| G1 | passed | four-kind schema、String、Metadata hierarchy、compiler diagnostics及schema/hash repeat在Corretto通过 |
| G2 | passed | Corretto full JDK 8 integration、generated Metadata/Group/callback、public/golden/negative contract通过 |
| G3 | passed | storage/access/relation/DataFlow/scheduler/ledger/delivery/failure/observation在Corretto contract与component evidence通过 |
| G4 | passed | dense/keyed/access/child/breadth external Maven consumers、public API与Java major 52通过 |
| G5 | blocked | component与九application profile通过；Small/Medium、1M/10M、single/double100M、String、Expansion、Delivery、Soak尚未在Corretto authority下重跑 |
| G6 | blocked for selected private-source | identity/SCM/support/security仍成立；Corretto Linux build/contract、clean package/security provenance与CI尚无当前commit evidence |

`blocked`是evidence适用性判断，不是产品目标删除、waive或实现回退。

## 3. 已完成的工程体系治理

- 日常反馈、完整工程验证和重型qualification分成Fast、Full、Qualification；
- `./scripts/check.sh fast|full`统一输出stage start/pass/fail/duration与总耗时；
- 默认安全并行上限为4，失败状态逐stage fail-closed，不吞并行子进程退出码；
- 普通开发使用Maven标准用户local repository与Resolver锁，不再为每项证据创建
  隔离仓库或直接复制repository布局；
- reactor/external artifacts/benchmark classes一次准备、多项consumer复用；
- code-size clean build在临时source copy中执行，不再删除主checkout的prepared
  output或orchestrator logs；
- public API `javap`和classfile major验证批量执行，消除逐class JVM启动；
- CI按变更选择docs-only或Full，使用Maven cache；同workflow/同SHA的重叠运行
  通过concurrency取消；
- 三个Example各自保持独立consumer和application-owned correctness/performance
  evidence；application Full继续是显式、低频入口；
- package reproducibility、security、runtime-scale和release仍保留独立、人工
  触发的隔离evidence，不为提速删除oracle。

## 4. Scale 与String边界

历史Qualification ID：
`runtime-scale-qualification-20260728-dfe8fa98b2a4`。其十条required lane在记录的
Zulu/macOS candidate上全部`passed`且`claimAllowed=false`，包括：

- 单root 100M窄numeric-Key Table；
- 两个100M窄numeric-Key root同时驻留并做bounded same/cross Group relation；
- 两个100M String payload root，UTF-16 18..34、cardinality 1,024、跨表100%
  object sharing、payload/group/join角色；
- Small/Medium、1M/10M、high-cardinality String、actual GC、Expansion、
  callback Delivery与Soak。

这些目标和historical artifact均保留，但必须在Corretto上重放后才能重新成为当前
G5 evidence。任意wide schema、high-cardinality String Key、unbounded expansion
或其他环境仍不得由row count外推。

## 5. Reference application 与baseline

三个独立Java 8 consumer各自拥有显式Group、detached Runtime Metadata、领域
correctness和三个profile。当前Corretto baseline为：

- industrial scheduler：default v6、large v5、long-run v5；
- grassing simulation：default v4、large v3、long-run v3；
- RTD：default/large/long-run均v3。

九份baseline使用同一5-fork治理公式校准，普通3-fork Full全部通过；所有artifact
保持`claimAllowed=false`。

## 6. 当前开放项

- `CF-005`：性能evidence只适用于精确环境/profile；
- `CF-006`：selected private-source G6在Corretto Linux/qualification重新取证前
  重新打开；
- `CF-016`：Corretto runtime-scale十lane重型qualification待执行。
- `CF-017`：Codex Cloud setup已收敛，但fresh-container setup/Fast/Full与
  clean-worktree有界验收待执行。

public GitHub与Maven Central保持`not-selected`；Codex Cloud为
`candidate / qualification-blocked`。它没有被改名为ready，也不阻塞本地继续
开发。

## 7. Claim boundary

允许陈述：当前Corretto macOS candidate通过G0–G4、component和九application
profile；SOMA V1设计与production能力目标没有回退。

不允许陈述：当前Corretto candidate已通过100M/双表100M/String 100M
qualification、Linux support、private-source G6、Codex Cloud ready、任意Schema
100M、跨环境SLA、production-ready、public RC或Maven Central ready。
