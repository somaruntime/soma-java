# Versioning 与 release 契约

类型：历史设计
状态：superseded
Owner：根项目协调层
当前取代者：[兼容性、安全与版本](design/compatibility-security-and-versioning.md)、[Release 治理](engineering/release-governance.md)、[Validation Gate 治理](engineering/validation-gates.md)
事实范围：artifact version policy、V1.0 RC 完成边界、发布身份、license 方向、release artifact、publishing prerequisites、open-source readiness、rollback/withdrawal 和 release evidence
非事实范围：public API 具体兼容语义、build graph、license 法律解释、漏洞联系人、release 执行结果和业务 roadmap
最后审查日期：2026-07-11

> 本文只保留切换前的历史设计上下文，不再拥有当前事实；当前 readiness 由正式 Report 陈述。

## 1. 目标

本文定义 SOMA Java 如何从 repository build 变成可引用、可撤回、可审计的 release。Compatibility surface 由 [Public API 与兼容性契约](public-api-compatibility-contract.md) 拥有，构建由 [Build 与依赖契约](build-and-dependency-contract.md) 拥有，最终 readiness 由 [V1 验证门禁](validation-gates.md) 判定。

## 2. Current release state

当前仓库是 pre-release Java-only prototype：

- version 为 `0.1.0-SNAPSHOT`；
- 没有 public release artifact；
- 没有配置 SCM remote/publishing endpoint；
- 项目所有者已确认 HGTECH / SOMA 发布身份基线，并推荐 Apache License 2.0；
- 标准 Apache License 2.0 `LICENSE`、`NOTICE`、POM license metadata、source/javadoc package mechanics 已落地；
- maintainer/contact、namespace ownership、SCM/publishing endpoint、signing/provenance 和 clean public history 尚未形成完整可发布证据；
- G0-G5 已通过，G6 保持 `blocked`。

因此当前仓库已达到完整 V1 功能与 G0-G5 evidence 的功能 RC 边界，但当前 `0.1.0-SNAPSHOT` 不是 public RC artifact，不能被描述为已开源发布、Maven Central ready、release ready 或 production ready。

## 3. V1.0 RC 与发布身份

### 3.1 V1.0 RC 完成边界

`V1.0 RC` 是完整功能候选，不是 `v0.x`、MVP、Lite、Basic 或缩小 capability 的预发布版本。RC development 完成至少要求：

- [实现策略 capability ledger](implementation-strategy.md#83-v1-capability-ledger) 中所有 V1 功能 capability 已按正式语义落地，没有被删除、降级或移入后续版本；
- G0-G5 required evidence 已通过，覆盖 correctness、compiler/processor、runtime、generated API、external consumer、formal examples 和 benchmark path；
- 当前实现是最终 V1 架构，不依赖 public/generated consumer migration、核心事实迁移或 canonical hot-path rewrite 才能达到正式 V1；
- G6-only license artifact、support matrix、SCM/contact、signing/provenance 等未完成项继续保持 `not-started` 或 `blocked`，仍属于同一个 V1 release 总目标。

V1.0 RC 以实现正确性为当前验收中心，不设置 throughput、latency、memory ratio 或相对 baseline 提升等硬性数值门槛。该决定不豁免 packed/primitive/fused/allocation-bounded 性能形态、benchmark smoke、结构化结果和禁止无证据性能声明等既有义务。RC 完成后可以启动独立性能治理专题，再基于可复现 evidence 决定是否增加数值目标。

功能 RC 完成不等于 public RC/release sign-off。G6 未通过时不得公开分发、发布 artifact、打不可变 release tag，或声明正式支持矩阵和 production readiness；也不得为了让 G6 通过而删除其 required evidence。

### 3.2 发布身份原则

V1 发布身份固定为：

| 维度 | 决定 |
|---|---|
| 组织与发布主体 | HGTECH |
| 产品品牌 | SOMA |
| Maven `groupId` / Java package root | `com.hgtech.soma` |
| 发布 artifact | `soma-annotations`、`soma-processor`、`soma-runtime-core`；其他 artifact 遵守本文 release set |
| License 方向 | 推荐 Apache License 2.0；正式 public RC/release 前必须由 `LICENSE`、POM metadata 和 G6 evidence 完整落地 |

HGTECH 只用于真实组织归属、repository ownership、POM organization/developer、SCM、publishing、copyright/NOTICE 和 provenance 等发布边界。SOMA 是产品品牌；annotation、generated API、runtime type、method、error code、schema concept 和用户文档中的产品公共概念不得使用组织名替代或前缀化 SOMA 语义。

此前由项目所有者明确排除的历史组织/品牌标识保持全仓库零出现：不得进入 repository content、Maven metadata、Java package/type、public/generated API、diagnostic、文档品牌、SCM、publishing 或 provenance；scope check 使用禁用 token 执行零命中验证。新增其他组织/品牌身份属于正式 release-identity change，必须由项目所有者批准并先修改本文。

### 3.3 Version policy

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

这些文件是 community/operational policy，不是 SOMA semantic design owner。标准 Apache License 2.0文本和一致POM metadata已经落地，但项目所有者的最终授权/copyright确认及G6审查仍须完成。联系人、SCM、security channel、签名和发布主体证明未确定时，禁止生成 placeholder 或替项目作法律承诺。

## 6. Namespace and metadata

当前 Maven coordinates 使用 `com.hgtech.soma`，发布主体为 HGTECH，产品品牌为 SOMA。Public publishing 前必须证明该 namespace 可由 HGTECH 控制，并补齐：

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

本文不定义商业支持、SLA、长期支持周期、自动发布凭证、Maven Central account、license 法律意见或组织人员名单。
