# SOMA Java V1 release governance report

类型：Report / Release Governance

状态：`1.0.0` candidate preparation；G5/G6 blocked

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

当前 working tree 已完成 `1.0.0` 坐标、release evidence workflow、
runtime-scale source closure、Skill 与文档治理变更，并通过一次
`./scripts/check.sh fast`。该结果仍不是 clean immutable candidate evidence；
因此不得沿用旧 candidate 的 G5/G6 passed 表述。

| Gate | 当前状态 | 关闭条件 |
|---|---|---|
| G0 | passed | Java-only scope、正式 Owner、claim boundary 与核心抽象叙事规则稳定 |
| G1–G4 | blocked | production Java surface 未改变，但仍需在最终 clean commit 的 canonical Full 重放 |
| G5 | blocked | 需要 clean immutable commit 的 DataFlow 3-fork provenance 与 8-lane required qualification |
| G6 | blocked | 需要同一 final candidate 的 Full、package/reproducibility、security/provenance、support matrix 与 Owner sign-off |

旧 Corretto/macOS component/application/runtime-scale 和旧 Corretto/Linux Full
只能解释各自历史 candidate。它们可用于定位回归，不能自动关闭新的 `1.0.0`
candidate。

## 3. 上轮尾项闭环

| 尾项 | 当前处置 |
|---|---|
| canonical Full | 旧 HEAD `844d74d` 的 GitHub Full 已通过；最终候选仍只运行一次 canonical Full |
| exact qualification provenance | runner 现在要求 clean commit，ID 绑定 commit 与 executable content hash；最终 artifact 待重放 |
| source identity 边界 | 由唯一 manifest 精确包含 production/build/runner closure，排除 tests、Examples、无关 benchmark/baseline，并纳入两个实际 shell library |
| DataFlow baseline provenance | 禁止继续使用 `working-tree candidate`；在 clean commit 上重放固定 3 fork 后更新 calibration |
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

Blind behavior、anti-pattern 与至少两个独立宿主的真实发现/触发/行为验证仍未完成；
因此当前不声明 multi-tool support，也不能关闭 V1 Skill DoD。

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

没有 temporary public/generated API、parallel Design Owner、test-only bypass、
canonical hot-path migration 或新的第三方 production dependency。最终 closeout
仍需确认 migration artifact、active Temporary、tool-specific semantic copy 和未
裁决 `UNKNOWN` 为零。

## 6. 下一证据

1. 完成 RC surface 审计并提交 clean implementation/evidence candidate；
2. 在该 commit 上重放 DataFlow 3 fork 与 runtime-scale required qualification；
3. 固化 baseline/evidence/Conformance/Report，形成 final candidate；
4. 运行窄 Gate、一次 canonical Full 与 private-source package/security；
5. 更新 support matrix 与本报告后重验文档/clean workspace；
6. 删除 Temporary。未经另行授权不创建 tag、GitHub Release 或发布 artifact。
