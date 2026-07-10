# Versioning 与 release 契约

状态：正式设计文档
Owner：根项目协调层
事实范围：artifact version policy、release artifact、publishing prerequisites、open-source readiness、rollback/withdrawal 和 release evidence
非事实范围：public API 具体兼容语义、build graph、license 选择、漏洞联系人、release 执行结果和业务 roadmap
最后审查日期：2026-07-10

## 1. 目标

本文定义 SOMA Java 如何从 repository build 变成可引用、可撤回、可审计的 release。Compatibility surface 由 [Public API 与兼容性契约](public-api-compatibility-contract.md) 拥有，构建由 [Build 与依赖契约](build-and-dependency-contract.md) 拥有，最终 readiness 由 [V1 验证门禁](validation-gates.md) 判定。

## 2. Current release state

当前仓库是 pre-release Java-only prototype：

- version 为 `0.1.0-SNAPSHOT`；
- 没有 public release artifact；
- 没有配置 SCM remote/publishing endpoint；
- license、maintainer/contact、namespace ownership 尚未形成可发布证据；
- G1-G6 未通过。

因此当前内容可以支撑 implementation，但不能被描述为已开源发布、Maven Central ready 或 production ready。

## 3. Version policy

Artifact version 使用 SemVer shape：

```text
MAJOR.MINOR.PATCH[-QUALIFIER]
```

- `SNAPSHOT` 只用于未发布开发版本；
- released version immutable，禁止覆盖；
- `0.x` compatibility 规则由 public compatibility contract 定义；
- `1.0.0` 只有 G0-G6、public API freeze、migration/release policy 和公开支持边界全部满足后才能发布；
- artifact family 在同一 release 使用同一版本；
- schema version label、schema hash、runtime compatibility 和 runtime plan hash 不复用 artifact version。

## 4. Release artifact set

V1 release set：

- parent POM/build metadata；
- `soma-annotations` binary/source/javadoc；
- `soma-processor` binary/source/javadoc；
- `soma-runtime-core` binary/source/javadoc；
- checksums/signature/provenance as configured；
- release notes；
- compatibility matrix；
- install/consumer guide；
- gate reports and known limitations。

`soma-testkit`、`soma-examples` 和 `soma-benchmarks` 默认不发布到 dependency repository。Examples/benchmark evidence 保留在 source repository/report artifact。

## 5. Open-source publication prerequisites

任何 public repository announcement 或 public artifact release 前必须完成：

- 明确、经项目所有者确认的 OSI-compatible license，并加入 `LICENSE`；
- root POM 的 license metadata 与 `LICENSE` 一致；
- 可验证的 copyright/NOTICE policy；
- `CONTRIBUTING.md`；
- `CODE_OF_CONDUCT.md` 和真实 enforcement contact；
- `SECURITY.md` 和私密报告渠道；
- `SUPPORT.md`；
- maintainer/governance 说明；
- CODEOWNERS 或等价 review ownership；
- public SCM URL、issue/PR 模板；
- artifact namespace ownership证明；
- dependency/license/security scan；
- public API/compatibility/release guide。

这些文件是 community/operational policy，不是 SOMA semantic design owner。联系人、license 和组织身份未由用户/项目所有者决定时，禁止生成 placeholder 或替项目作法律承诺；G6 保持 blocked。

## 6. Namespace and metadata

当前 Maven coordinates 使用 `com.hgtech.soma`。Public publishing 前必须证明该 namespace 可由发布主体控制，并补齐：

- project URL；
- SCM connection/tag URL；
- license；
- developers/organization；
- issue management；
- description/name；
- source/javadoc artifacts；
- checksums/signature/provenance；
- Central-compatible POM metadata。

如果 namespace ownership 不能证明，必须在首个 public release 前更换 coordinates；发布后 coordinate 变化按 migration/breaking release 处理。

## 7. Release flow

```text
develop accepted state
  -> release candidate commit
  -> clean wrapper build
  -> G0-G5 reports
  -> reproducibility/checksum/API/compatibility review
  -> release notes + known limitations + install guide
  -> G6 sign-off
  -> immutable tag
  -> publish once
  -> post-publish external consumer verification
```

长期分支仍遵守 `main`、`develop`、`release`。Release branch/tag 操作只有在 release task 明确授权后执行；普通 implementation 不自动创建 release。

## 8. Release notes and changelog

首个 user-visible release 开始维护 changelog/release notes，至少包含：

- version/date/commit；
- added/changed/fixed/removed/security；
- breaking changes；
- required regeneration/migration；
- compiler/JDK/runtime support matrix；
- known limitations；
- artifact/checksum links；
- rollback/withdrawal guidance。

空 `CHANGELOG.md` 不构成 readiness，因此在首个 release candidate 前不创建占位文件。

## 9. Reproducibility and provenance

Release report 必须记录：

- commit/tag；
- JDK vendor/version；
- Maven Wrapper distribution/version；
- OS/architecture；
- full command；
- generated source identity；
- dependency graph；
- artifact filename/size/checksum；
- two-clean-build reproducibility result；
- signing/provenance mechanism；
- gate report paths。

“本机 package 成功”不能替代以上证据。

## 10. Rollback and withdrawal

已发布 artifact 不覆盖、不删除后重传同版本。

发现严重问题时：

1. 停止推荐受影响版本；
2. 发布 advisory/known limitation；
3. 在 repository/publishing service 能力范围内标记 deprecated/withdrawn；
4. 发布新 patch/minor/major version；
5. 提供 upgrade/downgrade 或 regeneration 步骤；
6. 更新 compatibility matrix 和 release evidence。

如果 compiler/runtime mismatch 可能产生错误结果，必须 fail closed；不能依赖文档提醒作为唯一防线。

## 11. Release support boundary

每个 release 明确：

- supported compiler/JDK vendor/minor；
- supported runtime JVM；
- Maven consumer path；
- processor/runtime pairing；
- known unsupported IDE/compiler/build tools；
- security-fix support window；
- 是否允许 snapshot/older generated source。

未进入 matrix 的组合是 unsupported/untested，不得表述为“理论上应该可以”。

## 12. G6 blocking checklist

以下任一缺失都阻塞 G6：

- G0-G5 report；
- license/namespace/SCM/contact；
- source/javadoc/checksum/provenance；
- external consumer；
- compatibility/API diff；
- release notes/install/limitations；
- reproducible build；
- rollback/withdrawal；
- security/dependency/license evidence；
- Java 8 compiler/runtime matrix。

Waiver 必须在 G6 report 中包含 owner、理由、影响、期限和移除条件；法律许可、artifact integrity 和 wrong-result risk 不允许用普通工程 waiver 跳过。

## 13. 非目标

本文不定义商业支持、SLA、长期支持周期、自动发布凭证、Maven Central account、license 具体选择或组织人员名单。
