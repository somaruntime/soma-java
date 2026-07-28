# Contributing to soma_java

感谢关注 SOMA Java。项目目前处于 V1 private-source
implementation/release-readiness 阶段，尚未公开发布；设计事实已经形成正式
Owner 体系，copyright owner 与发布主体为 ArthurFeng，产品品牌为 SOMA，
GitHub Organization 为 `somaruntime`。当前只选择 private GitHub source
repository，不声明 public repository、Maven Central 或 production readiness。
当前G5等待Corretto runtime-scale重验，selected private-source G6等待Corretto
Linux和release qualification。
外部贡献在开始前仍应先与 repository owner 协调，不能假设提交即自动获得合并或
公开发布许可。

## 1. 先读事实源

- 目标与正式设计从 [docs/README.md](docs/README.md) 进入；
- 文档分类和 Owner 遵守 [文档治理](docs/engineering/documentation-governance.md)；
- 修改前先读相关 Blueprint/Design，再通过 [Implementation Map](docs/implementation-map/README.md)和对应 `<module>/docs/README.md` 定位代码；
- README、AGENTS、Guide、Report、Implementation Map 和 superseded 历史文档都不重新定义 Design。

## 2. Build prerequisites

- Amazon Corretto 8.502.07.1 full JDK 8（Java `1.8.0_502-b07`、
  `javac 1.8.0_502`）；macOS推荐`brew install --cask corretto@8`；
- POSIX shell（Windows 使用 `mvnw.cmd`）；
- Git。

Maven 由 repository wrapper 固定，不要求预装相同 Maven 版本：

```text
./mvnw -B -ntp verify
```

Codex Cloud 的可重复 setup、Linux profile 与 GitHub Actions 入口见
[GitHub 私有仓库与 Codex Cloud 开发](docs/engineering/github-and-cloud-development.md)。

V1 compiler integration 只把正式版本的 Corretto full JDK 8 javac 当作 compiler
authority；public RC/release 只声明正式 G6 matrix 中有证据的组合。使用新 JDK
的 `--release 8` 不等于 supported javac 8 build。

实施阶段先以当前开发机的完整 JDK 8 javac、Maven Wrapper 和当前
OS/architecture 为验证基线。每次 validation 必须记录实际 JDK
vendor/version/build、OS、architecture 和执行命令；本机通过不能外推为正式
跨平台支持，当前正式边界只采用G6 support matrix中列出的组合。

## 3. Change workflow

设计或 public contract 变更：

1. 从 Blueprint 确认目标并识别唯一 Design Owner；
2. 重大长期变化先在 Temporary 中形成候选并获得授权；
3. 实施代码和匹配的 compile/golden/invariant/consumer/benchmark evidence；
4. 更新 Implementation Map、Conformance 和必要 Report；
5. 原子固化正式事实并删除 Temporary；
6. 完成整体一致性复核。

实现必须服务 [Design](docs/design/README.md)，不能为了局部速度把未实现目标改写成 future、optional、MVP 或永久非目标。真实偏差进入 [Conformance](docs/conformance/README.md)，Conformance 不自动授权修改。

## 4. Dependency changes

`soma-annotations` 和 `soma-runtime-core` 的 production baseline 是 JDK-only。任何第三方 dependency 都需要先有正式设计决策，说明用途、owner、transitive/public exposure、license、安全、体积和替代方案。不要在普通实现 PR 中顺手添加 dependency repository。

## 5. Required checks

日常开发先运行快速反馈：

```text
./scripts/check.sh fast
```

它执行exact toolchain、文档/范围、baseline架构、Maven reactor verify和
`git diff --check`。Markdown-only可以只运行`./scripts/check-docs.sh`与
`git diff --check`。跨模块变更、PR最终候选和专题收口再运行一次默认的完整
`./scripts/check.sh`；release qualification仍使用独立手动workflow。

快速反馈之后按变更surface追加：

- compiler/processor：compile fixture、golden、external consumer、unsupported compiler negative case；
- runtime：invariant、failure atomicity、error path、allocation/performance-shape evidence；
- public API：API/generated diff 和 compatibility case；
- benchmark：遵守 [Benchmark 治理](docs/engineering/benchmark-governance.md)，不把 smoke 写成性能 claim；
- release：G0-G6、reproducibility、checksum、migration/rollback 和 security/license evidence。

Full Gate的独立功能检查默认最多4路并行，但performance、package、security和
qualification始终串行。相同失败只有在输入、假设或验证目标改变后才允许重试；
不得用固定间隔sleep或重复轮询代替诊断。

## 6. Pull request quality

PR 应当：

- 有单一目标和清晰非目标；
- 说明 owner/placement/dependency direction；
- 说明兼容性、错误、生命周期、性能和安全影响；
- 保留 skipped checks 与 residual risk；
- 不提交 `target/`、IDE workspace、local repository 或临时 spike artifact；
- 不混入无关格式化或大范围重写。

## 7. Branches

Repository 长期分支只使用 `main`、`develop`、`release`。常规设计和实现合入 `develop`；release 操作必须有明确 release task。Contributor 可以在自己的 fork/worktree 使用短期分支，但不能把它们提升为本仓库长期治理分支。

## 8. Community policy status

项目所有者 ArthurFeng 已确认 Apache License 2.0、`somaruntime/soma-java` 与
`io.github.somaruntime.soma` 身份基线。Private-source profile 使用真实 SCM、
maintainer、support、security、CODEOWNERS、CI 与 provenance；public RC/Maven
profile 仍需在未来单独补齐其 publishing/signing/community 义务。当前Corretto
Linux/qualification尚未形成，因此private-source G6保持blocked。任何profile都
不得用placeholder绕过真实边界。
