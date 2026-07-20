# Contributing to soma_java

感谢关注 SOMA Java。项目目前处于 V1 implementation/release-readiness 阶段，尚未公开发布；设计事实已经形成正式 Owner 体系，发布主体为 HGTECH、产品品牌为 SOMA。仓库已加入标准 Apache License 2.0文本和一致POM metadata，但maintainer/contact、SCM、namespace ownership、签名/provenance与最终G6 evidence尚未全部完成，因此外部贡献在开始前仍应先与repository owner协调，不能假设提交即自动获得合并或发布许可。

## 1. 先读事实源

- 目标与正式设计从 [docs/README.md](docs/README.md) 进入；
- 文档分类和 Owner 遵守 [文档治理](docs/engineering/documentation-governance.md)；
- 修改前先读相关 Blueprint/Design，再通过 [Implementation Map](docs/implementation-map/README.md)和对应 `<module>/docs/README.md` 定位代码；
- README、AGENTS、Guide、Report、Implementation Map 和 superseded 历史文档都不重新定义 Design。

## 2. Build prerequisites

- full JDK 8；
- POSIX shell（Windows 使用 `mvnw.cmd`）；
- Git。

Maven 由 repository wrapper 固定，不要求预装相同 Maven 版本：

```text
./mvnw -B -ntp verify
```

V1 compiler integration 只把 full JDK 8 javac 当作 compiler authority；public RC/release 只声明正式 G6 matrix 中有证据的组合。使用新 JDK 的 `--release 8` 不等于 supported javac 8 build。

实施阶段先以当前开发机的完整 JDK 8 javac、Maven Wrapper 和当前 OS/architecture 为验证基线。每次 validation 必须记录实际 JDK vendor/version/build、OS、architecture 和执行命令；本机通过不能外推为正式跨平台支持，G6 support matrix 形成前对应状态保持 `not-started` 或 `blocked`。

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

所有变更至少运行：

```text
./scripts/check.sh
```

该入口依次执行文档治理、Maven reactor verify 和 `git diff --check`。

按变更 surface 追加：

- compiler/processor：compile fixture、golden、external consumer、unsupported compiler negative case；
- runtime：invariant、failure atomicity、error path、allocation/performance-shape evidence；
- public API：API/generated diff 和 compatibility case；
- benchmark：遵守 [Benchmark 治理](docs/engineering/benchmark-governance.md)，不把 smoke 写成性能 claim；
- release：G0-G6、reproducibility、checksum、migration/rollback 和 security/license evidence。

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

项目所有者已经确认 HGTECH / SOMA 身份基线并推荐 Apache License 2.0；标准`LICENSE`与POM metadata已经落地。仓库仍需在public RC/release sign-off前补齐真实SCM、maintainer、support、private security contact、`CODE_OF_CONDUCT.md`、`SECURITY.md`、`SUPPORT.md`、CODEOWNERS和公开issue policy，并完成License授权、签名/provenance和namespace ownership证据。缺失这些真实边界意味着项目尚未完成public-release readiness，不应使用placeholder绕过。
