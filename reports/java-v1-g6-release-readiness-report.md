# Java-only SOMA V1 G6 release readiness report

类型：Report / Release Readiness

状态：selected `private-github-source` profile blocked

Owner：SOMA Java G6 release readiness

受众：SOMA maintainer、private repository consumer与release profile reviewer

适用版本：`1.0.0`

输入事实源：release governance、private SCM、CI/workflow、package/security
scripts、current support matrix与AI consumer Skill

事实范围：selected private-source G6、已形成事实、缺失evidence与claim boundary

非事实范围：public GitHub、Maven Central、production SLA或公开性能声明

最后审查日期：2026-07-29

Gate：G6 selected release profile

## 1. 结论

`private-github-source`是当前唯一selected release profile，G6保持`blocked`。
`1.0.0`坐标与planned SCM tag已经进入POM，macOS clean-candidate DataFlow与
runtime-scale qualification已经通过；canonical Full、package/security、Ubuntu
同SHA evidence和最终sign-off尚未形成，不能把坐标或局部Gate误表述为release
passed。

旧HEAD `844d74d`的GitHub Actions run `30440373950`在Ubuntu 24.04 x64 exact
Corretto 8上完成canonical Full。该run证明当时source的Linux build/contract，
不自动证明新的`1.0.0` candidate，也没有保留package/security artifact。

当前仍需在同一最终candidate形成：

- canonical Full；
- release-shaped package、byte-for-byte reproducibility与checksums；
- SBOM、known-vulnerability、declared-license与security evidence；
- workflow留存的candidate/package/security/checksum bundle；
- macOS/Linux support matrix和release Owner sign-off；
- V1 `use-soma-java` Skill的negative/anti-pattern behavior与第二独立宿主验证。

## 2. Identity、SCM 与治理事实

| 关注点 | 当前状态 | 真实事实 |
|---|---|---|
| copyright / license | passed | copyright owner与`NOTICE`为ArthurFeng；Apache-2.0 |
| product / namespace | passed | SOMA；Organization `somaruntime`；Maven/Java root `io.github.somaruntime.soma`；artifact保持`soma-*` |
| SCM | passed | private `https://github.com/somaruntime/soma-java`；`develop`为默认开发分支，`main`为稳定基线 |
| maintainer / support | passed | ArthurFeng / GitHub `@283586450`；普通问题进入private repository Issues |
| security / ownership | passed | private Security Advisory优先；`.github/CODEOWNERS`为`* @283586450`；Actions最小权限与SHA pin |
| JDK authority | passed as policy | Amazon Corretto 8.502.07.1、`1.8.0_502-b07`、`javac 1.8.0_502` |
| macOS qualification | passed on clean executable candidate | DataFlow固定3-fork与runtime-scale 8条required lane通过；后者绑定`bd25e119…`/`5669bf68ddf5…` |
| final candidate Full | blocked | `844d74d` run `30440373950`是前序成功证据；`1.0.0` final candidate尚未运行 |
| package/security evidence | blocked | scripts/workflow已修正同SHA/version与evidence retention，尚未在final commit执行 |
| AI consumer Skill | blocked | canonical instruction-only Skill、结构Gate、Codex blind positive与真实consumer通过；negative/anti-pattern及第二宿主未完成 |

Organization plan不支持private repository branch protection/ruleset。当前控制为
private access、CODEOWNERS、长期分支约束、SHA-pinned workflow、clean candidate
admission和人工sign-off；Report保留未强制保护分支的残余风险，不伪造平台能力。

## 3. Profile状态

| Profile / claim | 状态 | 边界 |
|---|---|---|
| private GitHub source | blocked | 等待同一`1.0.0` final candidate的Full、package/security、matrix与sign-off |
| local macOS development | qualification partial passed | exact Corretto DataFlow/runtime-scale已通过；build/contract仍等待canonical Full |
| public GitHub source | not-selected | repository保持private |
| Maven Central / binary publishing | not-selected | 未配置signing/OIDC/publishing，不分发binary |
| Codex Cloud development | not release-scoped | 用户未把Cloud qualification设为本轮必要目标 |
| production readiness / SLA | not-claimed | 需要具体部署、workload与运营证据 |

Private-source profile仍要求package/security mechanics，因为它们验证source能生成
一致、可审计的release-shaped artifact与dependency/provenance边界；这不等于选择
Maven distribution，也不要求signing/publishing。

## 4. Evidence retention

Manual release workflow现在：

1. 固定exact Corretto和toolchain；
2. 写入candidate SHA/ref/version/profile；
3. 运行Full、两次隔离package build与security scan；
4. 校验package/security summary都是同一SHA、`dirty=false`和同一version；
5. 对evidence bundle生成SHA-256；
6. 使用SHA-pinned `actions/upload-artifact`保留90天。

最终长期审计记录仍应在获得外部release授权后绑定immutable tag/release；90天CI
artifact不是public distribution，也不能替代tag授权。

## 5. 关闭条件与授权边界

G6 Owner只在同一clean immutable commit上获得下列全部证据后标记passed：

1. Ubuntu x64 exact Corretto 8 canonical Full；
2. package/reproducibility、security、clean workspace与sealed checksums；
3. macOS exact candidate的G5 qualification和Support Matrix更新；
4. AI Skill V1 DoD；
5. Conformance、performance、support matrix与release governance原子校准；
6. release Owner sign-off。

Push、tag、GitHub Release、repository visibility、signing和publishing属于外部状态
变更，必须另行获得明确授权。G6技术通过本身不执行这些动作，也不声明public、
Maven或production readiness。

## 6. Scope non-regression

- Java 8、public/schema/runtime语义和production dependency没有因release治理改变；
- public/Maven保持`not-selected`，10M/100M保持non-blocking research；
- AI Skill不进入Maven JAR，也不成为Design或API Owner；
- 当前blocked来自exact candidate evidence，不是降低产品目标。
