# 2026-07-17 性能优化后 G6 本机诊断报告

状态：passed-local-diagnostic；G6 仍为 `blocked`
日期：2026-07-17
唯一 Owner：root（本报告只记录 evidence，不拥有 release 设计事实）
Capability：`V1-RELEASE-EVIDENCE` 保持 `blocked`；`V1-EVIDENCE-TOOLING` 保持 `evidenced`
Goal：`完成完整 Java-only SOMA V1.0，并通过 G0-G6。`（继续 active）

## 1. 目标与边界

本报告在第二轮 runtime allocation 优化后刷新仍可由本机执行的 G6 diagnostic：第二个完整 JDK 8 vendor、unsupported compiler fail-closed、binary/source/javadoc package shape、逐字节复现性、checksum、SBOM、known-vulnerability 与 declared-license scan。

本次不生成 SCM、contact、namespace ownership、法律授权、clean public history、signing/OIDC、publishing account 或正式支持矩阵的 placeholder；不 push、tag、publish，也不把 dirty/unsigned 本机结果升级为 public RC、G6 passed、release ready 或 production ready。

正式 release 边界仍以 [Versioning 与 release 契约](../docs/versioning-and-release-contract.md) 和 [V1 验证门禁](../docs/validation-gates.md) 为准。

## 2. 输入状态与工具

- repository commit：`1cf0515256f31ca64af9b94b37df5b95d006c1cb`；工作树 `dirty=true`；
- artifact version：`0.1.0-SNAPSHOT`；
- OS：macOS `26.5.2` / Darwin `25.5.0`，`arm64` / JVM `aarch64`；
- Maven Wrapper：Apache Maven `3.9.16`；
- Zulu：Azul Zulu `8.94.0.17` / OpenJDK `1.8.0_492-b09`；
- Corretto：Amazon Corretto `8.492.09.2` / OpenJDK `1.8.0_492-b09`；
- unsupported compiler negative lane：Homebrew `javac 25.0.2`；
- OSV-Scanner：`2.3.8`，commit `408fcd6f8707999a29e7ba45e15809764cf24f67`，binary SHA-256 `a8cd6507b06239f463a7642430cfd2d154882f150f6e30cdc0653e28dfc34216`。

Corretto archive 来自 AWS 官方 macOS aarch64 JDK 8 tarball，下载 SHA-256 与官方 endpoint 返回值均为 `4316bff4922d9799883e68f6b9d78a720663fd8f1664f74b005bb285eda0ca26`。OSV binary 与 SHA256SUMS 来自 Google OSV-Scanner `v2.3.8` release，执行前逐字节校验通过。下载物只位于 `/tmp`，未进入仓库、artifact、Schema 或支持声明。

## 3. Evidence-tooling fail-closed 修复

第一次 Corretto 重放使用只含 JDK/system path 的精简 `PATH`，`scripts/check-build-governance.sh` 因找不到 `rg` 输出 `rg: command not found`，但原 shell `if rg ...; then` 把 exit `127` 误当作“未发现违规”，随后错误输出 `build-governance-check: ok`。该次运行被立即判定为无效并中止，不进入本报告 positive evidence。

修复后脚本在执行 source-shape 检查前显式要求 `rg`：

```text
env JAVA_HOME=/tmp/corretto8-soma/Contents/Home \
  PATH=/tmp/corretto8-soma/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  ./scripts/check-build-governance.sh
```

负向结果：exit `1`，稳定输出 `build-governance-check: rg is required for source-shape validation`。随后完整工具路径下的 positive lane 通过。该变化只加固 Gate prerequisite，不改变 public/generated API、Schema、runtime semantics 或 artifact 内容。

## 4. Corretto 完整重放

命令：

```text
env JAVA_HOME=/tmp/corretto8-soma/Contents/Home \
  SOMA_UNSUPPORTED_JAVAC=/opt/homebrew/opt/openjdk/bin/javac \
  PATH=/tmp/corretto8-soma/Contents/Home/bin:/Applications/ChatGPT.app/Contents/Resources:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  ./scripts/check.sh
```

结果：exit `0`，结尾 `project-check: ok`。

- documentation、reactor、build governance、public API、processor/codegen、generated consumer、runtime、child/materialization、examples 与 benchmark Gate 全部通过；
- unsupported `javac 25.0.2` 被 `SOMA-COMP-002` fail closed；
- benchmark smoke 两次各 20 records，36 条 serialized negative artifact fail closed；
- FJSP 100k Gate：total allocated bytes `597,484,408`，`5,974.84408 B/op`，Young GC `18` 次 / `40 ms`，Full GC `0`，solve `8268.426209 ms`（仅诊断），`claimAllowed=false`。

该结果与 Zulu 第二轮性能 evidence 共同证明当前改动在同一 macOS/arm64 的两个 JDK 8 vendor 上没有观察到 correctness、API/Schema 或 allocation/GC Gate 回归；它仍不是跨 OS/architecture 的正式支持矩阵。

## 5. Package 与复现性 diagnostic

命令：

```text
SOMA_PACKAGE_ALLOW_DIRTY=true ./scripts/package-smoke.sh
```

结果：`package-smoke: ok`；最终 evidence path 为 `target/package-smoke.ZBNk3x`。

- 13 个 exact release-shape artifact：parent POM，以及三个 publishable module各自的 POM、binary、source、javadoc；
- 两个隔离 Maven repository 的 clean package 结果逐字节一致，`reproducibility.diff` 为 0 bytes；
- 全部 classfile major 为 52，binary/source archive 中 License/NOTICE 与 root exact content 一致；
- `release-checksums.sha256` SHA-256：`e617b3edd14861364e8b7a2b8c3273eaaef748708f3c6934c52b38f4909dcf81`；
- `provenance.properties` SHA-256：`d43de10a09d174c85a73b3d60911eba88b08a529ef39b1e0efb6884abb518f63`；
- provenance 明确记录 `dirty=true`、`signature=not-performed`，因此只能是 local diagnostic。

## 6. Security、SBOM 与 license diagnostic

命令：

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home \
  OSV_SCANNER=/tmp/osv-scanner-v2.3.8-darwin-arm64 \
  ./scripts/security-release-scan.sh
```

结果：`security-release-scan: ok`；执行时 evidence path 为 `target/security-release-scan.W6ppcO`。

- CycloneDX exact component set：三个 SOMA release module + JDK 8 `tools` compiler input；
- known published vulnerability：`0`；declared-license violation：`0`；production runtime third-party dependency：`0`；
- summary SHA-256：`9a6460cd4497d4646e3b2634a9d5e13c97272ffad94842f53835e78764d51661`；
- SBOM SHA-256：`cfb4f909ba6137071112fbace895c8c2ceba1e1b15ef22e4c1cd390cdc9e50f5`；
- vulnerability JSON SHA-256：`2efc232c5214d8bd153ae68b2c6005040c3eac4a46c50e17bada323dd92ca7c7`；
- license JSON SHA-256：`973926255aab122daa353cf17d97467cd430b40e0457591e9a18bc1e05614b56`；
- summary 明确记录 `dirty=true`，且 OSV 只覆盖已公开 advisories，不能证明不存在未知漏洞。

## 7. G6 状态裁决

| G6 evidence | 本次结果 | Gate 含义 |
|---|---|---|
| 当前代码双 JDK 8 vendor 本机验证 | Zulu、Corretto 均为 `project-check: ok` | observed evidence 增强；不是正式跨平台矩阵 |
| binary/source/javadoc/checksum | dirty diagnostic passed | `implemented-unverified`；仍需 clean immutable candidate |
| reproducibility | 两次 isolated-M2 byte-for-byte | `implemented-unverified`；缺 commit-bound clean provenance |
| dependency/vulnerability/license | dirty diagnostic passed | `implemented-unverified`；仍需 release sign-off 重放 |
| SCM/contact/namespace/legal authorization | 未获得真实输入 | `blocked` |
| clean public Git identity/provenance | 当前 dirty 且无 public SCM | `blocked` |
| signing/OIDC/publishing | 未提供 key/account/endpoint | `blocked` |
| approved OS/architecture/JDK support matrix | 只有同一 macOS arm64 的两个 vendor | `blocked` |

因此 G6 仍为 `blocked`，完整 V1 Goal 不能标记 complete。解除阻塞仍需项目所有者提供并批准真实 SCM/project/issue/publishing URL、namespace ownership、maintainer/support/private-security contact、CODEOWNERS/CoC enforcement、Apache-2.0 最终授权、clean public history、signing/OIDC、publishing account 和正式支持矩阵；之后在 clean immutable candidate 上重放 G0-G6。

## 8. V1 scope non-regression

- `V1-EVIDENCE-TOOLING` 增加 fail-closed prerequisite evidence，保持 `evidenced`；
- `V1-RELEASE-EVIDENCE` 的本机 mechanics evidence 被刷新，但因真实发布边界不足保持 `blocked`；
- 其余 22 项功能 Capability、G0-G5 passed 状态均未回退；
- public/generated API、Schema JSON/hash、runtime semantics、Owner contract、Capability Ledger、Gate 与 release claim均未改变；
- 未创建 placeholder、temporary public/generated API、temporary storage/hot path、test-only bypass 或 waiver；
- 后续仍是 additive release-boundary evidence completion；不需要 consumer/API/Schema/runtime migration 或 canonical hot-path rewrite。

## 9. 允许引用的结论

只允许引用：当前 dirty `0.1.0-SNAPSHOT` 工作树在 Zulu/Corretto JDK 8、macOS arm64 上通过完整验证，本地 package/reproducibility 与 security/SBOM/license diagnostics 通过，且 Gate 工具缺失现在 fail closed。

不得据此声明：G6 passed、public RC artifact、正式支持矩阵、Maven Central ready、release ready、production ready、已签名 provenance 或无未知漏洞。
