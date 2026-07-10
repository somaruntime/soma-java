# 2026-07-10 Implementation-readiness 治理收尾报告

日期：2026-07-10
目标：从高质量 Java 开源项目角度闭合 SOMA Java 实施前的 compiler、contract、build、security、release 与文档治理缺口
范围：正式设计文档、Maven/module skeleton、Wrapper、CI/community skeleton 和 feasibility evidence；不实现 SOMA Java 功能代码
输入事实源：`docs/README.md` 与各 module `docs/README.md`

## 1. Outcome

本次治理完成了进入 Phase 0 implementation 所需的正式 owner contract 和可执行 build skeleton：

- 六模块边界保持不变，没有提前新增空模块；
- `@SomaValue` compiler feasibility blocker 已取得本地证据并形成正式 compiler contract；
- public compatibility、build/dependency、runtime plan、runtime error/diagnostics、security、version/release 均有唯一 Owner；
- Maven dependency graph、toolchain enforcement、Wrapper、CI、PR template 和 contribution workflow 已落地；
- public release 所需 license、namespace、SCM/contact 等未决组织/法律事实被明确设为 G6 blocker，没有使用 placeholder；
- 长期蓝图保持原位置和非事实源地位。

项目现在可以开始 [实现策略](../docs/implementation-strategy.md) 的 Phase 0，但仍没有 Java 功能实现，也没有任何 G1-G6 passed claim。

## 2. New formal owner contracts

Root：

- `docs/build-and-dependency-contract.md`；
- `docs/public-api-compatibility-contract.md`；
- `docs/security-model.md`；
- `docs/versioning-and-release-contract.md`。

Processor：

- `soma-processor/docs/compiler-integration-contract.md`。

Runtime core：

- `soma-runtime-core/docs/runtime-plan-contract.md`；
- `soma-runtime-core/docs/runtime-errors-and-diagnostics-contract.md`。

Runtime lifecycle 原先混合的 errors/stats 内容已迁入独立 Owner，lifecycle 文档只保留 ownership/state/mutation/concurrency/release。

## 3. Compiler feasibility evidence

[Compiler integration spike](2026-07-10-compiler-integration-spike-report.md) 验证：

- ordinary Filer replacement 产生 duplicate class；
- processor-round tree mutation 时机过晚；
- javac 8 parse-phase plugin 可以在 Enter 前提供 final class、public-final field 和 canonical constructor；
- same-unit user source 和 classfile 看到同一 effective type；
- final field mutation 被 compiler 拒绝；
- Maven Java 8 consumer 激活 plugin 后编译成功；
- 同一 javac 8 adapter 在 OpenJDK 25 下被 module boundary 拒绝。

因此 V1 compiler baseline 固化为 full JDK 8 javac plugin + JSR 269 processor。JDK 9+、ECJ 和 IDE code insight 只有新增 adapter/evidence 后才能声明支持。

## 4. Build skeleton changes

- root POM 集中 Java 8、encoding、reproducible timestamp、internal dependency versions 和 Maven plugin versions；
- Enforcer 要求 JDK `[1.8,1.9)` 与 Maven `[3.8.6,4)`；
- module POM 形成实际 dependency direction；
- testkit/examples/benchmarks 默认 `maven.deploy.skip=true`；
- Maven Wrapper `3.3.4` 固定 Maven `3.9.16`，采用 `only-script`；
- `.editorconfig` 固定文本基线；
- GitHub CI 使用 Java 8 执行 docs check 和 reactor verify；
- PR template 与 `CONTRIBUTING.md` 对齐 Owner、compatibility 和 evidence workflow。

Source/javadoc attach、publishing/signing、external consumer 和 benchmark runner 会在相应实现/gate 阶段落地；当前 POM 不伪造 release readiness。

## 5. Validation evidence

### Passed

```text
./scripts/check.sh
  -> doc-check: ok
  -> 7/7 reactor modules SUCCESS
  -> project-check: ok

./scripts/check-docs.sh
  -> doc-check: ok

git diff --check
  -> passed

xmllint --noout <root/module poms>
  -> passed

ruby YAML parse .github/workflows/ci.yml
sh -n scripts/check.sh scripts/check-docs.sh mvnw
  -> passed

JAVA_HOME=<Zulu JDK 8> ./mvnw -B -ntp verify
  -> 7/7 reactor modules SUCCESS
```

Maven verify 同时证明 Enforcer 的 Java/Maven rules 在 supported toolchain 下通过。由于模块尚无 Java source，这只证明 build skeleton 和 dependency graph，不证明 product behavior。

### Expected negative

```text
JAVA_HOME=<OpenJDK 25> ./mvnw -B -ntp validate
  -> failed at RequireJavaVersion
  -> detected 25.0.2 is outside [1.8,1.9)
```

该 failure 是期望的 fail-closed toolchain guard。

### Not run / not claimable

- GitHub-hosted CI 尚未在 remote repository 实际执行；
- real `soma-processor` external consumer 尚无 production artifact/source 可消费；
- source/javadoc/reproducibility checksum 未执行 release lane；
- G1-G6 product/release evidence 未开始；
- benchmark/performance claim 未执行。

## 6. Open-source readiness boundary

以下需要项目所有者做真实决策，当前不影响 Phase 0 implementation，但阻塞 public release/G6：

- OSI-compatible license；
- namespace `com.hgtech.soma` ownership；
- public SCM/issue URL；
- maintainer/governance/CODEOWNERS；
- private security contact、support channel 和 code-of-conduct enforcement contact；
- publishing/signing/provenance account。

在这些事实确定前，不创建假的 `LICENSE`、`SECURITY.md`、`SUPPORT.md` 或联系人。

## 7. Gate and claim boundary

允许声明：

- implementation-readiness design/build governance 已完成；
- javac 8 lowering mechanism 的最小 feasibility 已证明；
- repository skeleton 在本机 full JDK 8 + Maven Wrapper 下 verify 成功；
- unsupported JDK 被 POM fail closed。

不允许声明：

- G0 已正式 passed（尚无专用 `java-v1-g0-scope-freeze-report.md`）；
- compiler/processor feature complete；
- runtime/API/example 可运行；
- IDE/JDK 17+/ECJ supported；
- public open-source/Maven Central/release ready；
- 已具备性能优势。

## 8. Next implementation entry

下一步不是继续扩写总设计，而是进入 Phase 0 vertical slice：

1. 实现真实 javac 8 plugin skeleton；
2. 实现 `@SomaValue` 完整 lowering；
3. 实现 processor skeleton/effective-model validation；
4. 建立 external consumer 与 compile/classfile golden；
5. 完成 clean/incremental/unsupported compiler evidence；
6. 再进入 dense table Phase 1。

任何实施中暴露的新 contract gap 都必须回到唯一 Owner；不得为实现方便缩水现有 V1 gate。
