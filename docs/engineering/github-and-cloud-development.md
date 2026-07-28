# GitHub 私有仓库与 Codex Cloud 开发

类型：Engineering

状态：正式

Owner：SOMA Java repository 与 cloud development 过程

事实范围：私有 GitHub 开发 profile、CI、Linux 工具链与 Codex Cloud setup

非事实范围：产品语义、public release、Maven Central publishing 或某次 Gate 结果

最后审查日期：2026-07-28

## 1. Repository profile

SOMA Java 的当前 SCM 是 private GitHub repository
`somaruntime/soma-java`。`develop` 是常规开发分支，`main` 是稳定基线，
`release` 只用于明确授权的 release 工作。完整 Git 历史保留；新提交使用
ArthurFeng 的 GitHub-attributed identity。

Private source、Codex Cloud development、public GitHub release 和 Maven
publishing 是不同 profile。当前只启用 private source；private CI 通过不能被
表达为 Cloud、public 或 Maven release readiness。

## 2. Exact Linux toolchain

受支持的 Linux CI/Cloud compiler baseline 是：

- Ubuntu 24.04 x64；
- Azul Zulu 8.94.0.17，Java `1.8.0_492-b09`，full `javac 1.8.0_492`；
- Maven Wrapper 3.9.16，distribution SHA-256 固定在 wrapper properties；
- ripgrep 15.2.0 Linux x64 static binary，使用发布 asset SHA-256 验证；
- OSV-Scanner 2.3.8，只在 release qualification 中使用经过平台 checksum
  验证的 binary。

[`scripts/check-toolchain.sh`](../../scripts/check-toolchain.sh) 对 JDK vendor、
runtime/build、javac 和 Maven 版本 fail-closed。macOS arm64 与 Linux x64
可以形成各自的 build/contract evidence；macOS 性能 baseline 在 Linux 上必须
报告 `not-applicable`，不能外推为 Linux 性能通过。

## 3. Codex Cloud setup

仓库保留了实验性的 Codex Cloud bootstrap：

```text
./scripts/setup/setup-codex-cloud.sh
```

它仅支持 Linux x64，并执行：

1. 从固定 HTTPS URL 下载 Azul Zulu full JDK 8、ripgrep 与 OSV-Scanner；
2. 验证发布方 SHA-256，拒绝已存在但不匹配的 bytes；
3. 若 Codex Cloud 暴露平台代理 CA，则把该 CA 合并到 setup 专用的 Zulu
   truststore 副本，使 Maven 保持完整 TLS 校验；不修改 vendor truststore，
   不使用 insecure SSL 参数；
4. 把 `JAVA_HOME`、`PATH`、`OSV_SCANNER` 写入后续 agent shell 可读取的
   environment file，并从 `.bashrc` / `.profile` 引用；
5. 验证 exact toolchain；
6. 通过 reactor `verify` 预热后续普通 Maven lifecycle 所需 dependencies；
7. 在 checkout 之外建立持久 Maven evidence repository，并通过独立 external
   consumer path 预取和验证隔离 Gate 所需 build/runtime dependencies 以及
   pinned governance plugin；后续 reactor `clean` 不会删除该缓存。

Setup 不读取或写入 repository secret，不执行 push，不修改产品源码。若未来重新
选择 Cloud profile，Agent phase 应先运行：

```text
./scripts/check-toolchain.sh
git status --short
```

完整验收仍需 `./scripts/check.sh`。在完成一次可接受时长的 fresh-container
验收前，不得声明 Cloud development ready。

## 4. GitHub Actions

- `CI`：对 pull request 以及 `main`、`develop`、`release` push 执行 exact
  Zulu/Linux toolchain 和 `./scripts/check.sh`；
- `Release qualification`：仅手动触发，额外执行 clean package
  reproducibility 与 OSV/SBOM/license scan；
- action 使用 immutable commit SHA，runner image 与 JDK update 不使用
  `latest`/宽泛版本。

重型 runtime-scale qualification 不进入 hosted CI；它仍由具有足够物理内存的
显式、人工监管环境运行。

## 5. 当前 GitHub 与 Cloud 控制面

当前实际配置：

- repository为private `somaruntime/soma-java`；
- `develop`是default development branch，`main`是stable baseline；
- Issues启用、Wiki关闭，Actions只允许repository workflow声明的只读权限；
- repository Actions policy要求immutable SHA pinning；
- `.github/CODEOWNERS`、`SECURITY.md`与`SUPPORT.md`使用真实owner和private
  repository入口；
- Organization当前free plan不支持private repository branch protection/
  ruleset；GitHub API返回upgrade限制，因此不伪造“已保护”。现阶段使用private
  visibility、单一owner、分支职责、CODEOWNERS、CI与人工review控制风险。

Codex Cloud environment以`develop`全新检出后，setup阶段曾实际验证：

- 平台CA进入Zulu私有truststore副本，Maven保持TLS校验；
- reactor与external Maven consumer预热成功；
- 直接`java`、`javac`与Maven解析到记录版本，Git可用；固定的
  `$RIPGREP`为15.2.0。Codex平台会把自己的`/opt/codex/codex-path/rg`
  shim置于`PATH`最前，因此普通`rg`可能显示平台版本；两者都必须可用，项目不把
  平台shim冒充仓库固定binary；
- `SOMA_MAVEN_EVIDENCE_REPOSITORY`位于checkout之外；
- agent阶段可以进入离线检查，但没有完成一次可接受时长的完整
  `./scripts/check.sh`与clean checkout验收。

第一次完整Cloud任务因Maven cache缺口失败；第二次任务在长时间重复setup/check、
没有形成最终结果时由maintainer取消。当前不再追求Cloud开发，因此Cloud
development状态为`not-selected / not-ready`，不进入支持矩阵，也不影响已选择的
private-source G6。

本次还暴露出一个工程效率问题：`check.sh`同时承担日常反馈和最终资格验收，约有
27个顶层阶段、72次Maven调用、18处隔离repository/cache准备，Cloud setup又会与
完整检查重复部分工作。本专题只记录该问题，不在closeout中继续重构；若未来重新
选择Cloud或优化开发反馈，应先独立设计fast feedback与full qualification分层，
以总耗时和重复Maven调用数作为验收指标。
