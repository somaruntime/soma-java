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
publishing 是不同 profile。当前只启用前两项；private CI 通过不能被表达为
public 或 Maven release readiness。

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

在 Codex Cloud Environment 的 Setup script 中配置：

```text
./scripts/setup/setup-codex-cloud.sh
```

该脚本仅支持 Linux x64，并执行：

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

Setup 不读取或写入 repository secret，不执行 push，不修改产品源码。Agent
phase 开始后先运行：

```text
./scripts/check-toolchain.sh
git status --short
```

完整验收使用 `./scripts/check.sh`。同一 branch 同一时刻只保留一个 writer；
Cloud 与本机并行工作使用不同短期 branch/worktree，再通过正常 review 合并。

## 4. GitHub Actions

- `CI`：对 pull request 以及 `main`、`develop`、`release` push 执行 exact
  Zulu/Linux toolchain 和 `./scripts/check.sh`；
- `Release qualification`：仅手动触发，额外执行 clean package
  reproducibility 与 OSV/SBOM/license scan；
- action 使用 immutable commit SHA，runner image 与 JDK update 不使用
  `latest`/宽泛版本。

重型 runtime-scale qualification 不进入 hosted CI；它仍由具有足够物理内存的
显式、人工监管环境运行。
