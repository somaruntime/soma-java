# GitHub 私有仓库与 Codex Cloud 开发

类型：Engineering

状态：正式

Owner：SOMA Java repository 与 cloud development 过程

事实范围：私有 GitHub 开发 profile、CI、Linux 工具链与 Codex Cloud setup

非事实范围：产品语义、public release、Maven Central publishing 或某次 Gate 结果

最后审查日期：2026-07-29

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
- Amazon Corretto 8.502.07.1，Java `1.8.0_502-b07`，full
  `javac 1.8.0_502`；
- Maven Wrapper 3.9.16，distribution SHA-256 固定在 wrapper properties；
- ripgrep 15.2.0 Linux x64 static binary，使用发布 asset SHA-256 验证；
- OSV-Scanner 2.3.8，只在 release qualification 中使用经过平台 checksum
  验证的 binary。

[`scripts/check-toolchain.sh`](../../scripts/check-toolchain.sh) 对 JDK vendor、
runtime/build、javac 和 Maven 版本 fail-closed。macOS arm64 与 Linux x64
可以形成各自的 build/contract evidence；macOS 性能 baseline 在 Linux 上必须
报告 `not-applicable`，不能外推为 Linux 性能通过。

## 3. Codex Cloud setup

SOMA的Java 8/Maven Wrapper、无外部服务依赖、确定性生成和Fast/Full分层适合
Codex Cloud的隔离Linux环境。仓库因此把Cloud选为**有界开发验收候选**，但在
fresh-container evidence形成前仍为`not-ready`。

Codex Cloud官方执行模型会先checkout目标commit，再在有网络的setup阶段运行脚本；
setup中的普通`export`不会自动进入后续agent shell，container cache最长保留约
12小时，恢复cache时可以运行maintenance script。仓库使用以下bootstrap：

```text
./scripts/setup/setup-codex-cloud.sh
```

它仅支持Linux x64，并执行：

1. 从固定HTTPS URL下载Amazon Corretto full JDK 8与ripgrep；
2. 验证发布方 SHA-256，拒绝已存在但不匹配的 bytes；
3. 若Codex Cloud暴露平台代理CA，则把该CA合并到setup专用的Corretto
   truststore 副本，使 Maven 保持完整 TLS 校验；不修改 vendor truststore，
   不使用 insecure SSL 参数；
4. 把`JAVA_HOME`、`PATH`及必要的Maven trust options写入后续agent shell可读取的
   environment file，并从`.bashrc` / `.profile`引用；
5. 验证 exact toolchain；
6. 使用标准Maven local repository执行一次`install -DskipTests`，同时准备reactor
   artifact和普通lifecycle依赖；
7. 只对plugin surface最完整的独立external fixture执行一次`go-offline`，再显式
   解析build-governance使用的pinned help plugin。

Cloud setup不运行Fast/Full、不建立第二份Maven evidence repository，也不下载
只属于release qualification的OSV-Scanner。package reproducibility、security和
runtime-scale继续由其独立Gate拥有，不能混入日常Cloud环境准备。

Setup不读取或写入repository secret，不执行push，不修改产品源码。在Codex
environment settings中，initial setup与可选maintenance均可使用同一个幂等命令；
修改setup、环境变量或JDK/dependency authority后应让平台重建cache：

```text
./scripts/setup/setup-codex-cloud.sh
```

Agent phase首次进入checkout时只运行：

```text
./scripts/check-toolchain.sh
git status --short
./scripts/check.sh fast
```

普通任务按surface运行直接Gate；只有跨模块或专题收口才运行一次
`./scripts/check.sh`。Cloud agent internet保持默认关闭；若agent阶段发现缺失
dependency/plugin，应把它识别为setup cache gap，修正现有setup Owner后重建cache，
不得反复联网重试。

Fresh-container验收预算为setup不超过10分钟、Fast不超过60秒、Full不超过10分钟；
cached maintenance目标不超过2分钟。超出预算、同一Maven缺失重复出现或阶段无新增
输出都是异常信号，必须停止并诊断。在一次fresh-container完成setup、Fast、Full
与clean-worktree检查前，不得声明Cloud development ready。

## 4. GitHub Actions

- `CI`：Markdown-only diff只执行文档/范围/diff检查；其他pull request以及
  `main`、`develop`、`release` push先由仓库installer下载并校验exact Corretto
  archive，再通过`setup-java`的`jdkfile`路径设置JDK和标准Maven cache，最后执行
  完整`./scripts/check.sh`；并发key使用commit SHA，同一SHA的并发run只保留一个；
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

此前Zulu环境的Codex Cloud实验曾观察到：

- 平台CA进入vendor私有truststore副本，Maven保持TLS校验；
- reactor与external Maven consumer预热成功；
- 直接`java`、`javac`与Maven解析到记录版本，Git可用；固定的
  `$RIPGREP`为15.2.0。Codex平台会把自己的`/opt/codex/codex-path/rg`
  shim置于`PATH`最前，因此普通`rg`可能显示平台版本；两者都必须可用，项目不把
   平台shim冒充仓库固定binary；
- agent阶段可以进入离线检查，但没有完成一次可接受时长的完整
  `./scripts/check.sh`与clean checkout验收。

第一次完整Cloud任务因Maven cache缺口失败；第二次任务在长时间重复setup/check、
没有形成最终结果时由maintainer取消。Corretto installer与setup路径已经完成
静态迁移；旧的“独立evidence repository + 每fixture重复预取 + release tool”
setup已经退出。

2026-07-29工程体系治理已把普通开发验证改为Fast/Full分层，默认使用Maven标准
local repository、一次准备多次消费、最多4路安全并行及逐阶段耗时/fail-closed
输出；code-size clean build也已移入临时source copy，不再破坏checkout的prepared
output。上述形状使SOMA成为可实际验收的Cloud development candidate，但当前状态
仍是`candidate / qualification-blocked`，不进入支持矩阵，也不外推为release
readiness。

Codex Cloud环境行为以OpenAI官方
[Cloud environments](https://learn.chatgpt.com/docs/environments/cloud-environment.md)
为外部事实入口；本页只拥有SOMA仓库内setup、预算与验收边界。
