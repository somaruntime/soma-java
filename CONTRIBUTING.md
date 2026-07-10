# Contributing to soma_java

感谢关注 SOMA Java。项目目前处于 pre-implementation、pre-public-release 阶段；设计事实已经形成正式 Owner 体系，但 public license、maintainer/contact 和外部贡献许可尚未最终确定。因此，外部贡献在开始前应先与 repository owner 协调，不能假设提交即自动获得合并或发布许可。

## 1. 先读事实源

- 根级正式设计从 [docs/README.md](docs/README.md) 进入；
- 文档分类和 Owner 遵守 [docs/documentation-governance.md](docs/documentation-governance.md)；
- 模块修改前阅读对应 `<module>/docs/README.md`；
- README、AGENTS、guides、reports 和 `docs/temp/` 不是设计事实源。

## 2. Build prerequisites

- full JDK 8；
- POSIX shell（Windows 使用 `mvnw.cmd`）；
- Git。

Maven 由 repository wrapper 固定，不要求预装相同 Maven 版本：

```text
./mvnw -B -ntp verify
```

V1 compiler integration 只把正式支持矩阵中的 javac 8 当作 authority。使用新 JDK 的 `--release 8` 不等于 supported javac 8 build。

## 3. Change workflow

设计或 public contract 变更：

1. 识别唯一 Owner；
2. 先修改 Owner contract；
3. 同步 glossary、module obligation、examples 和 gates；
4. 增加匹配的 compile/golden/invariant/consumer/benchmark evidence；
5. 更新 report，但不让 report 反向定义设计；
6. 完成整体一致性复核。

实现变更应沿 [实现策略](docs/implementation-strategy.md) 的 vertical slice 推进，不能为了局部速度把尚未实现的 V1 capability 改写成永久非目标。

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
- benchmark：使用正式 evidence contract，不把 smoke 写成性能 claim；
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

在项目所有者确认 license、SCM、maintainer、support 和 private security contact 后，仓库才会补齐 `LICENSE`、`CODE_OF_CONDUCT.md`、`SECURITY.md`、`SUPPORT.md`、CODEOWNERS 和公开 issue policy。缺失这些文件意味着项目尚未完成 public-release readiness，不应使用 placeholder 绕过。
