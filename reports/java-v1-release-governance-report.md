# SOMA Java V1 release governance report

类型：Report / Release Governance

状态：`1.0.0` G0–G5 passed；selected private-source G6 blocked

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
qualification source closure。随后clean commit `fa934c24996f37367843e2e2a1ac06cb97c7affd`
的唯一canonical Full通过；当前`ac433ff9bb57880e5e5964cb26b630ded39db800`
只修复其后发现的G6 NOTICE checksum drift，并由直接hash检查与完整security Gate
覆盖。

| Gate | 当前状态 | 关闭条件 |
|---|---|---|
| G0 | passed | Java-only scope、正式 Owner、claim boundary 与核心抽象叙事规则稳定 |
| G1–G4 | passed | clean-candidate canonical Full通过；后续G6 checker单行修复不改变其输入 |
| G5 | passed | differential、component、三个application、DataFlow 3-fork与8-lane required qualification通过 |
| G6 | blocked | macOS本地package/security通过；等待Ubuntu同SHA qualification、AI第二宿主、support matrix与Owner sign-off |

旧 Corretto/macOS component/application/runtime-scale 和旧 Corretto/Linux Full
只能解释各自历史 candidate。它们可用于定位回归，不能自动关闭新的 `1.0.0`
candidate。

## 3. 上轮尾项闭环

| 尾项 | 当前处置 |
|---|---|
| canonical Full | closed：clean commit `fa934c2…`唯一Full以exit 0完成，profile=`full`、jobs=4、duration=144s；后续仅G6 checker直接修复 |
| exact qualification provenance | closed：`runtime-scale-qualification-bd25e1194931-5669bf68ddf5`绑定clean commit、唯一source manifest与8条required record |
| source identity 边界 | 由唯一 manifest 精确包含 production/build/runner closure，排除 tests、Examples、无关 benchmark/baseline，并纳入两个实际 shell library |
| DataFlow baseline provenance | closed：clean commit `733db714…`固定3-fork通过，workload/threshold不变；checker拒绝working-tree、dirty或relaxed calibration |
| 抽象叙事闭环 | Design Index 已制度化 Why/Owns/Not/Relationships/Lowering/Lifecycle/Resource/Failure/Evidence/Evolution |
| release evidence 留存 | package/security 接受独立 evidence root；manual workflow 校验同一 SHA/version、封存 checksums，并用 SHA-pinned upload action 保留 |
| NOTICE drift | closed：追溯`6bd260c`的ArthurFeng品牌边界后同步security checksum；未修改NOTICE或降低fail-closed强度 |

本地G6 evidence已在clean commit `ac433ff9bb57880e5e5964cb26b630ded39db800`
形成：

- package：17件parent/module POM、binary/source/javadoc artifact，classfile major
  52，License/NOTICE精确，两个隔离build byte-for-byte一致；
- release checksums清单SHA-256：
  `a726b5059a3dc8fba0396fd67b3eff5f1c54db54975511a75dd1a1a780772d1d`；
- security：OSV-Scanner 2.3.8 binary
  `a8cd6507b06239f463a7642430cfd2d154882f150f6e30cdc0653e28dfc34216`，
  SBOM `d85be0fbddb5f9978dd50b2688b1e8b9694ee88db416bb04473a3f3166f3b8ca`；
- known vulnerability、declared-license violation与production runtime第三方依赖
  均为0；结果只代表执行时OSV.dev已知事实，不是public security certification。

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
2. 获得push/workflow授权，在最终candidate运行Ubuntu private-source
   qualification并保留90天sealed bundle；
3. 完成support matrix与release Owner sign-off；
4. 原子校准最终Owner并删除Temporary。未经另行授权不push、不创建tag、
   GitHub Release或发布artifact。
