# 个人发布身份、GitHub 私有发布与 Codex Cloud 就绪治理

类型：Temporary

状态：active

Owner：SOMA Java 个人发布与云端开发治理

事实范围：本专题已授权决策、当前外部事实、迁移范围、阶段退出条件、证据矩阵与退役条件

非事实范围：新增 SOMA 产品语义、降低 V1 能力目标、公开发布或 Maven Central readiness

最后审查日期：2026-07-28

## 1. 意图与授权

本专题把 SOMA Java 从旧组织发布身份原子迁移为 ArthurFeng 个人拥有、由 GitHub
Organization `somaruntime` 承载的私有源码产品，并形成可重复的 Linux CI 与
OpenAI Codex Cloud 开发环境。用户已经明确授权：

- copyright owner 与新提交 identity 为 `ArthurFeng`；
- License 继续使用 Apache-2.0；
- Organization 为 `somaruntime`，显示名称为 `SOMA`；
- 私有仓库为 `somaruntime/soma-java`；
- Maven `groupId` 与 Java package root 为 `io.github.somaruntime.soma`；
- artifactId 保持 `soma-*`；
- Git 历史原样保留，不重写旧提交；
- 当前只选择 `private-github-source` 发布 profile，不发布 public repository，
  不发布 Maven Central；
- Azul Zulu full JDK 8 继续是唯一 compiler/runtime authority，不借云端迁移扩大
  JDK vendor 支持范围；
- 可以在上述裁决范围内同步 Blueprint、Design、Engineering、Implementation Map、
  Conformance、G6 profile 与 Report。

任何新的 public/schema/runtime semantics、性能目标、第三方 production dependency
或跨语言边界变化不在授权范围内。

## 2. Live baseline

启动基线：

- branch：`develop`；
- HEAD：`07c6b1c1f40943e7eeddd36f415c7efccaa55b47`；
- worktree：clean；
- local remote：无；
- GitHub CLI：已登录个人账号 `283586450`；
- GitHub Organization：`somaruntime`，当前账号为 active admin；
- GitHub repository：`somaruntime/soma-java`，private、empty，目标 URL 为
  `https://github.com/somaruntime/soma-java`；
- Codex GitHub connector：个人账号与 `somaruntime` Organization 均已安装；
- GitHub user id/login：`105834932` / `283586450`。按 GitHub 对 2017-07-18
  之后账户的 ID-based noreply 规则，新提交邮箱候选为
  `105834932+283586450@users.noreply.github.com`；在首次 push 后必须验证 GitHub
  attribution，不能只凭格式完成身份 sign-off；
- 启动时的正式事实仍使用 HGTECH / `com.hgtech.soma`；当前正在原子迁移为
  ArthurFeng / `io.github.somaruntime.soma`，G6 public release readiness 保持
  blocked；
- 当前 G0–G5、Small/Medium、单表 100M、双 root 同时驻留 100M、String、
  Metadata、两层自适应并行、Result Delivery 与三个 reference application
  evidence 不因本专题失效或降级。

## 3. Release profile cutover

本专题必须区分三个 claim：

| Profile / claim | 本专题目标 | 边界 |
|---|---|---|
| `private-github-source` | 形成真实、可审计的通过结论 | private SCM、完整历史、CI、maintainer/support/security、clean provenance 与 selected support matrix 必须真实 |
| Codex Cloud development readiness | 形成真实全新容器验收 | 证明能够 checkout、安装 Zulu 8、构建、验证和继续开发，不等于 release |
| public GitHub / Maven Central | 保持未选择、未声明 | 不创建 public repository，不上传 Maven，不以 private profile 证据替代 signing/publishing/public community 义务 |

现有 G6 不得被机械标绿。正式 cutover 必须让 Gate 对“已选择发布 profile”做真实
判定，同时保留 public/Maven 未选择边界；历史 blocked 结论由 Git 保存。

## 4. Repository 与文档目标形态

保留现有 module taxonomy 和正式文档分类，不进行 `modules/`、`apps/` 等装饰性搬迁：

```text
production modules        soma-annotations / soma-processor /
                          soma-runtime-core / soma-dataflow
evidence consumers        soma-examples / soma-benchmarks / tests/fixtures
stable commands           scripts/check.sh / package-smoke.sh /
                          security-release-scan.sh
shared script internals   scripts/lib/
environment setup         scripts/setup/
formal documentation     docs/{blueprints,design,implementation-map,
                               conformance,engineering}
temporary decision        docs/temp/<active-topic> only
user/developer output     guides/
current evidence          reports/
GitHub product surface    .github/ + root community/support files
```

必须完成：

- 修复 current `AGENTS.md`、Map、Guide、Report 中的旧身份与已退役 module 事实；
- 为 `soma-dataflow` 补齐与其他 production module 对称的极简 README 与
  `docs/README.md`，只导航根级 Design/Map，不创建平行 Owner；
- README 面向 GitHub 首次访问者先解释产品、状态、最短消费/开发入口，再进入完整
  Design；CONTRIBUTING 面向 contributor；Engineering/Report 面向 maintainer；
- 只把被两个以上真实脚本复用的 OS/hash/download mechanics 放入 `scripts/lib/`；
- 环境安装进入 `scripts/setup/`，不把 Codex Cloud 事实写入 runtime；
- 不恢复 archive、tombstone、空 Temporary、`soma-testkit` 或新的 shared/common
  artifact。

## 5. Namespace 与兼容性迁移

namespace cutover 同时覆盖：

- root/module/fixture POM coordinates；
- production、test、benchmark、example 与 fixture Java package/path/import；
- javac plugin、processor constants、generated source、service descriptor；
- public/generated API classification、`javap` golden、schema JSON/hash golden；
- scripts 中 class name、source path、local repository coordinate 与 artifact 检查；
- docs、guides、reports、CI 与 contributor template。

`com.hgtech.soma` 到 `io.github.somaruntime.soma` 是当前 pre-1.0 私有发布前的
一次 intentional breaking identity cutover。generated/runtime protocol version
只有在字节/语义协议本身变化时才升级；package identity 变化由 public/generated
golden、schema repeat、external consumer 与 runtime binding 重新生成/重放证明，
不能为了“看起来重大”无依据递增协议。

旧 token absence 只作为本次 cutover completion invariant；不得在 closeout 后保留
一份随历史迁移不断膨胀的 blacklist。长期 checker 应验证当前发布 identity 的正向
不变量。

## 6. Linux、CI 与 Cloud 环境边界

- macOS 与 Linux 都必须使用 portable SHA-256 helper；
- Maven Wrapper distribution 必须固定 SHA-256；
- OSV-Scanner 固定版本并为实际支持的 macOS arm64、Linux x64 binary 分别固定
  upstream checksum；
- GitHub Actions 使用固定 Ubuntu image、pinned action SHA 与 exact Zulu 8
  version input，不使用 `ubuntu-latest` 或未记录的 JDK 漂移；
- Codex setup 必须幂等、非交互、无 secret，验证下载 checksum，并把
  `JAVA_HOME` / `PATH` 写入后续 agent phase 可见的 shell 环境；
- setup phase 预取 wrapper 与 Maven dependencies；agent phase 默认无网络；
- Linux CI/Codex Cloud 可以证明 selected Linux build/contract support，不得把
  macOS 绑定的性能 baseline 解释为 Linux 性能通过；不适用 comparator 必须明确
  `not-applicable`；
- 单表/双表 100M 等重型 qualification 不进入普通 hosted CI；本专题只重放被
  namespace/packaging 影响的 contract 与实际需要的资格证据。

## 7. 阶段与退出条件

### A. Baseline 与 Temporary

退出条件：外部 GitHub 事实、旧 identity surface、协议/golden surface、脚本平台
假设和正式 Owner 已登记；Temporary 通过 docs check。

### B. Identity、结构与 public/generated cutover

退出条件：tracked current surface 中不存在旧组织或旧 namespace；新 package path、
POM、生成物、golden、examples、benchmarks、scripts 与导航一致；窄 contract Gate
通过。

### C. Portable engineering 与 CI

退出条件：本地 macOS Zulu 8 完整检查通过；portable helpers、wrapper checksum、
Linux security tooling、GitHub Actions 与 Codex setup 具有可执行验证。

### D. GitHub private-source profile

退出条件：完整历史已推送；`develop` 为 default development branch，`main` 为
stable baseline；真实 CODEOWNERS/support/security/SCM 已配置；最终 commit 的
Actions 绿色；可用的 ruleset/branch protection 已配置并记录实际限制。

### E. Codex Cloud

退出条件：全新 cloud container checkout `develop`，实际验证 JDK/javac/Maven/Git、
dependency cache 与完整项目检查，且没有与本地 writer 并发修改同一 branch。

### F. Promotion 与 closeout

退出条件：正式 Owner、Conformance、selected support matrix、G6 与 Report 已按
最终 immutable commit 更新；Temporary 删除；local/remote/CI/cloud 全部干净并
完成 requirement-by-requirement audit。

## 8. Evidence matrix

| 要求 | 完成证据 |
|---|---|
| identity/namespace | tracked-current search、package path、POM effective model、public/generated golden |
| history/provenance | local/remote commit graph、clean commit、GitHub attribution |
| product contracts | `./scripts/check.sh` 与受影响 narrow Gate |
| package/reproducibility | clean `./scripts/package-smoke.sh`、exact artifact set 与 byte-for-byte diff |
| security/license | pinned OSV scan、SBOM exact set、LICENSE/NOTICE hash |
| examples | 三个 independent reference application Gate |
| Linux CI | final commit 对应 GitHub Actions run |
| Cloud | fresh environment setup 与 full-check run evidence |
| release claims | selected profile、support matrix、SCM/contact 与 G6 report |
| documentation | `check-docs.sh`、唯一 Owner、无 Temporary reference、无平行入口 |

## 9. Scope non-regression

本专题不删除、optional 化或移出 V1：

- Small/Medium、1M/10M、单表 100M、两个同时驻留的 100M root；
- reference-backed immutable String V1 及其 payload/access/relation/GC 边界；
- 完整 Metadata hierarchy 与 freeze 前可变、freeze 后 effective plan；
- Segment/vector/morsel 两层自适应并行；
- Eager Detached 默认与 callback-scoped Result Delivery 试点；
- Group/Table ownership、parent-owned child、bounded relation/intermediate；
- 三个独立 Java 8 reference application。

本专题预期只需要 identity cutover、cross-platform completion、release evidence
completion 和 contract-preserving internal refinement，不允许引入未来 consumer
migration 或 temporary hot path。

## 10. Blocker、rollback 与退役

- Organization handle、repository visibility、namespace、commit identity 或
  Cloud repository authorization与目标不一致时，停止对应 external mutation，
  不猜测替代 identity；
- 同一失败两次无新证据时改变诊断策略；三次仍无进展时登记 blocker 并请求用户；
- push 前 rollback 是删除新 remote 配置并保留本地 commit；push 后不重写历史，
  使用 additive fix commit；
- Temporary 不归档。稳定事实进入正式 Owner、最终 evidence 绑定 immutable commit、
  全部引用切换完成后删除本目录。
