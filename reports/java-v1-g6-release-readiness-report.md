# Java-only SOMA V1 G6 release readiness report

类型：Report / Release Readiness

状态：blocked

Owner：SOMA Java G6 release readiness

受众：SOMA maintainer、release owner 与 public release reviewer

适用版本：`0.2.0-SNAPSHOT`

输入事实源：release governance、package/security evidence、support matrix 与真实 external facts

事实范围：当前 G6 blocker、允许声明和禁止声明

最后审查日期：2026-07-28

Gate：G6 release readiness gate
执行日期：2026-07-28
执行人：Codex
Capability：`V1-RELEASE-EVIDENCE` → `blocked`
Goal：`完成完整 Java-only SOMA V1.0，并通过 G0–G6。`（当前 blocked，不是 completed）

## 1. 结论

G6 未通过。完整 V1 功能与 G0–G5 已通过，本地 License/package/reproducibility/SBOM/security mechanics 也已实施并成功诊断；但真实 SCM/contact、namespace ownership、community/security contact、clean public Git provenance、最终 Apache-2.0授权、signing/publishing provenance 和正式支持矩阵仍缺失。

当前最强允许结论是“完整 V1 功能 RC 边界已满足，本地 release mechanics 通过诊断”。当前 `0.2.0-SNAPSHOT`、dirty/unsigned artifact 不是 public RC artifact，不得 push/tag/publish 或声明 release ready、Maven Central ready、production ready。

## 2. 已完成的本地 release evidence

### 2.1 License 与 artifact shape

- 标准 Apache License 2.0 `LICENSE` SHA-256：`cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`；
- `NOTICE` SHA-256：`30ee2fc6260f43d20ae9c8e062196dc9bddf9f92f565872de70256f61f1070bb`；
- POM license metadata、binary/source archive中的exact `META-INF/LICENSE`/`NOTICE`、source/javadoc profile均已落地；
- Apache-2.0仍需发布主体的最终授权/copyright确认，脚本和文本不能替代法律授权。

### 2.2 Package 与复现性 diagnostic

命令：

```text
SOMA_PACKAGE_ALLOW_DIRTY=true ./scripts/package-smoke.sh
```

结果：passed。诊断目录属于可清理的 `target/` output；当前报告保留结果摘要与
checksum，不把本机临时目录冒充长期 provenance。

- 两个完全独立 local Maven repository 分别执行 clean release-shape build；
- exact artifact set 共17项：parent POM，以及4个publishable module
  `soma-annotations`、`soma-processor`、`soma-runtime-core`、`soma-dataflow`
  各自POM、binary、source、javadoc；
- binary/source/javadoc shape、binary/source内License/NOTICE exact hash、全部classfile major 52通过；
- 两次所有artifact SHA-256逐字节一致，`reproducibility.diff`为空；
- release checksum manifest SHA-256：`0961db9114214834d11178c6152a23303694ed14643e323a862723f11916a8d4`；
- provenance明确记录`commit=d3f2e354...`、`dirty=true`、
  `signature=not-performed`，所以只属于local diagnostic，不能作为clean/signed
  G6 provenance。

### 2.3 Dependency、SBOM、known-vulnerability与declared-license diagnostic

命令：

```text
OSV_SCANNER=/tmp/osv-scanner-v2.3.8-darwin-arm64 ./scripts/security-release-scan.sh
```

结果：passed。诊断目录属于可清理的 `target/` output；当前报告保留 exact
component set、结果摘要与 checksum。

- 固定OSV-Scanner 2.3.8及其SHA-256；CycloneDX Maven plugin 2.9.1；
- exact SBOM component set：4个SOMA release module
  （annotations、processor、runtime-core、dataflow）+ JDK 8 `tools` compiler input；
- known published vulnerability：0；declared-license violation：0；production runtime第三方依赖：0；
- SBOM SHA-256：`b7d959f44e45288993e28fbe9a07f7390083fb298aa6879b73c70c6ad1844a22`；
- dependency tree与pinned effective build-plugin configuration已记录；OSV不能
  证明不存在未公开漏洞，build plugin也不属于SOMA runtime artifact；
- summary同样记录`dirty=true`，不构成public-release security sign-off。

## 3. G6 required evidence 状态

| Required evidence | 状态 | 当前事实/阻塞 |
|---|---|---|
| G0–G5、external consumer、API compatibility | evidenced | G0–G5 reports已通过；G2/G4拥有external consumer与public/generated API证据 |
| License/NOTICE/POM metadata | blocked | 标准文本、metadata和archive装载已验证；最终组织授权/copyright ownership仍需真实确认 |
| Binary/source/javadoc/checksum | implemented-unverified | dirty SNAPSHOT diagnostic通过；必须在clean immutable candidate上重放 |
| Reproducible build | implemented-unverified | 两次isolated-M2 byte-for-byte通过；commit-bound clean provenance未完成 |
| Dependency/vulnerability/license scan | implemented-unverified | 本机扫描通过；必须在clean candidate和sign-off时重放 |
| SCM/project/issue URL与namespace ownership | blocked | `git remote -v`为空；没有真实publishing endpoint/namespace proof，禁止placeholder |
| Maintainer/support/private security contact | blocked | 未提供真实联系人；`SECURITY.md`、`SUPPORT.md`、CoC enforcement和CODEOWNERS不能伪造 |
| Git identity/provenance | blocked | 现有历史含正式Owner明确禁止扩散的历史身份；公开前需用批准的真实身份重写或建立clean public history并重放commit-bound evidence |
| Signing/OIDC/publishing provenance | blocked | signing key、attestation/OIDC与publishing account/endpoint未提供 |
| Release notes/install/rollback | implemented-unverified | `CHANGELOG.md`、install guide和release contract已落地；仍需绑定最终version/date/commit/artifact URLs |
| Java 8/OS/architecture support matrix | blocked | JDK vendor边界已裁决为Azul Zulu only；当前v4只在记录版本的Zulu/macOS arm64验证，精确version/OS/architecture承诺尚未批准 |

## 4. 本机 observed validation matrix

正式矩阵报告见`reports/java-v1-support-matrix-report.md`。当前候选只记录Zulu observed evidence：

- Azul Zulu 8.94.0.17 / OpenJDK `1.8.0_492-b09`，full `javac 1.8.0_492`；
- Maven Wrapper / Apache Maven 3.9.16；macOS 26.5.2 / Darwin 25.5.0；arm64/aarch64；
- 当前v4候选的完整`./scripts/check.sh`为`project-check: ok`；component、code-size、benchmark与多fork证据均在同一Zulu环境形成。

早期Corretto执行结果只作为旧候选的历史evidence保留，不属于当前支持范围，也不要求再次重放。这不是跨平台支持承诺；正式G6 matrix保持blocked。

## 5. V1 scope non-regression

- Capability：前22项保持`evidenced`；`V1-RELEASE-EVIDENCE`从`not-started`进入实施后因真实发布边界不足保持`blocked`。
- 未完成项仍完整保留在原Phase 6/G6，没有被删除、optional化、waive或移入新版本。
- Owner、正式契约、Capability Ledger、Gate和release claim未改变。
- release脚本不改变public/generated API、runtime核心事实、consumer或canonical hot path。
- Zulu-only是明确的JDK vendor边界；support matrix仍需批准并验证具体Zulu version/build、OS和architecture，但不要求新增其他vendor。
- 后续只需additive release-boundary evidence completion与clean commit重放；不需要public contract migration、temporary contract、temporary hot path或rewrite。

## 6. 解除阻塞所需真实输入

G6只有在获得并验证以下真实事实后才能关闭：批准的SCM/project/issue/publishing URL；HGTECH对`com.hgtech.soma`的namespace ownership；真实maintainer、support和private-security contact；CoC/CODEOWNERS治理主体；Apache-2.0最终授权/copyright确认；不含禁止身份的clean public history；signing或OIDC provenance；发布账户；经批准并在目标环境执行的正式支持矩阵。随后必须在clean immutable candidate上重跑G0–G6、package、security与external consumer并完成sign-off。
