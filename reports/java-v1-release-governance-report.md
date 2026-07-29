# SOMA Java V1 release governance report

类型：Report / Release Governance

状态：`1.0.0` clean-candidate qualification ready；G5/G6 blocked

Owner：SOMA Java V1 release governance

受众：SOMA maintainer、release owner、private repository consumer

适用版本：`1.0.0`

输入事实源：正式 Blueprint/Design/Engineering、当前 POM/source/generated
surface、Conformance、Gate scripts、private SCM/CI 与 release evidence

事实范围：当前候选身份、G0–G6、上轮尾项、AI consumer Skill、scope
non-regression 与 selected profile claim

非事实范围：tag/release授权、public GitHub、Maven Central、production SLA 或
未列环境支持

最后审查日期：2026-07-29

## 1. 治理目标与 release identity

本轮目标是把完整 SOMA Java 产品推进到 V1 `1.0.0` 的 selected
`private-github-source` release sign-off，不把 release 解释为 public repository、
Maven Central 或 production readiness，也不通过缩小 Blueprint/Design/Gate
关闭差距。

当前身份：

| 事实 | 当前值 |
|---|---|
| 产品 / Owner | SOMA / ArthurFeng |
| SCM | private `somaruntime/soma-java` |
| Maven / Java root | `io.github.somaruntime.soma` |
| release version | `1.0.0` |
| planned immutable tag | `v1.0.0`；尚未创建 |
| JDK authority | Amazon Corretto 8.502.07.1 full JDK 8 |
| selected profile | private GitHub source |
| not-selected | public GitHub、Maven/binary publishing |

Push、tag、GitHub Release、visibility 与 publishing 仍是外部授权边界。POM 中的
`v1.0.0` 是 release identity，不证明 tag 已存在。

## 2. 当前候选与 Gate

当前clean candidate已完成`1.0.0`坐标、release evidence workflow、
runtime-scale source closure、Skill与文档治理变更。DataFlow固定3-fork已在
clean commit `733db714…`通过；runtime-scale 8条required lane已在clean commit
`bd25e119…`及精确executable source tree `5669bf68ddf5…`通过。其后只更新正式
Report/Conformance，不改变production Java、public/generated surface或该
qualification source closure。

| Gate | 当前状态 | 关闭条件 |
|---|---|---|
| G0 | passed | Java-only scope、正式 Owner、claim boundary 与核心抽象叙事规则稳定 |
| G1–G4 | blocked | production Java surface 未改变，但仍需在最终 clean commit 的 canonical Full 重放 |
| G5 | blocked | clean-commit DataFlow 3-fork与8-lane required qualification已通过；等待最终candidate的canonical Full |
| G6 | blocked | 需要同一 final candidate 的 Full、package/reproducibility、security/provenance、support matrix 与 Owner sign-off |

旧 Corretto/macOS component/application/runtime-scale 和旧 Corretto/Linux Full
只能解释各自历史 candidate。它们可用于定位回归，不能自动关闭新的 `1.0.0`
candidate。

## 3. 上轮尾项闭环

| 尾项 | 当前处置 |
|---|---|
| canonical Full | 旧 HEAD `844d74d` 的 GitHub Full 已通过；最终候选仍只运行一次 canonical Full |
| exact qualification provenance | closed：`runtime-scale-qualification-bd25e1194931-5669bf68ddf5`绑定clean commit、唯一source manifest与8条required record |
| source identity 边界 | 由唯一 manifest 精确包含 production/build/runner closure，排除 tests、Examples、无关 benchmark/baseline，并纳入两个实际 shell library |
| DataFlow baseline provenance | closed：clean commit `733db714…`固定3-fork通过，workload/threshold不变；checker拒绝working-tree、dirty或relaxed calibration |
| 抽象叙事闭环 | Design Index 已制度化 Why/Owns/Not/Relationships/Lowering/Lifecycle/Resource/Failure/Evidence/Evolution |
| release evidence 留存 | package/security 接受独立 evidence root；manual workflow 校验同一 SHA/version、封存 checksums，并用 SHA-pinned upload action 保留 |

Runtime-scale required qualification仍只运行 Small、Medium、单1M、双1M、String、
Expansion、Delivery 与 Soak；10M/100M research 不进入本轮 release blocker，也不
因本轮治理被删除。

## 4. AI consumer Skill

唯一 canonical Skill 为
`.agents/skills/use-soma-java/`，只拥有 AI consumer workflow。Blueprint/Design
继续拥有长期语义，POM/generated source/class/golden/external consumer继续拥有
精确 surface。

当前已形成：

- 最小跨工具 frontmatter、四份按需 reference 和非语义 UI metadata；
- positive/negative trigger、禁止猜 API、禁止 object graph/stable Index/
  cross-table transaction/Iterator-lazy/Stream-reflection hot path 的边界；
- README 安全安装提示词，以及 Consumer Guide 的 project scope、固定来源、升级
  和卸载说明；
- instruction-only，无 `scripts/`、无 `allowed-tools`、无全局静默安装；
- `skill-creator` `quick_validate.py` 与 repository link/drift Gate 已通过。
- Codex在未显式点名Skill的positive consumer任务中完成发现与触发，生成并运行
  Java 8 keyed inventory consumer；父任务独立重验offline compile/run、runtime
  dependency tree和classfile major 52；
- 当前Codex沙箱阻止目标项目自动创建`.agents`目录，按安全提示人工复制后六个
  canonical文件blob hash一致；不把该限制伪装为自动安装通过。

Negative/anti-pattern behavior与第二独立宿主的真实发现/触发/行为验证仍未完成；
因此当前不声明multi-tool support，也不能关闭V1 Skill DoD。

## 5. Scope non-regression 与 surface delta

当前候选不修改 production Java、public/generated fixture 或 protocol identity。
变更类型为 release identity、consumer tooling、evidence boundary 和
contract-preserving governance refinement：

- production/public Java type delta：0；
- module delta：0；production artifact仍为四个；
- runtime dependency delta：0；
- tests/fixtures：只同步 SOMA consumer version；
- benchmark：不改 workload/threshold，只修 qualification source identity；
- scripts/workflow：版本 Owner、evidence retention 与 clean-candidate admission；
- docs/report：V1 identity、AI consumer入口、抽象叙事和当前 evidence 诚实性。

没有temporary public/generated API、parallel Design Owner、test-only bypass、
canonical hot-path migration或新的第三方production dependency。RC surface审计
确认production/public Java delta、module delta、runtime dependency delta、
migration artifact、tool-specific semantic copy与未裁决产品`UNKNOWN`均为零；
唯一active Temporary将在最终稳定事实固化后删除。

## 6. 下一证据

1. 完成AI Skill negative/anti-pattern与第二独立宿主行为验证；
2. 固化当前Report/Conformance后运行窄Gate与唯一canonical Full；
3. 在同一clean candidate运行private-source package/security provenance；
4. 获得Ubuntu private CI Full、support matrix与release Owner sign-off；
5. 原子校准最终Owner并删除Temporary。未经另行授权不push、不创建tag、
   GitHub Release或发布artifact。
