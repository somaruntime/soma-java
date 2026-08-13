# SOMA V1 工程系统一致性、可重复性与反馈效率治理

类型：Conformance Record

状态：`PASS / E0-E4_COMPLETED / TEMPORARY_RETIRED`

日期：2026-08-13

正式事实源：是

Owner：四个稳定工程入口、候选与构建会话绑定、两 artifact 构建合同、开发阶段 CI 拓扑及本专题证据边界

## 1. 结论

SOMA Java 的工程主线已从“多个能力分别可运行”收敛为一条候选身份明确、职责分层且可重复的路径：

```text
SourceCandidate
    -> BuildSession
        -> daily correctness check
        -> full non-publishing qualification
            -> performance evidence
            -> local delivery evidence
```

本专题没有改变 SOMA 产品语义、public/generated API、storage、IR、execution 或性能算法；没有增加
dependency、Maven plugin、production artifact、稳定命令或 publication surface。正式长期合同已经由
[Implementation Architecture](../design/implementation-architecture.md#31-engineering-command-and-candidate-contract)
接管，本文只拥有当前实现和证据结论。

## 2. 治理前事实

冻结基线为 `develop@6af2626`，环境为 Amazon Corretto `1.8.0_502-b07`、Maven `3.9.16`、
macOS `26.6.1` arm64 / Apple M5 Pro。

- `scripts/check.sh` 实际进入几乎完整的 qualification，只跳过 Benchmark；warm wall time 为
  `34.85 s`；
- 同一个 `develop` push 会自动触发 CI 与 full non-publishing release qualification；远端基线分别为
  `125 s` 与 `137 s`；
- Benchmark、package 和若干 capability helper 可以由裸 `*_REUSE_BUILD=1` 跳过构建，不能证明
  `target/` 属于当前候选；
- runtime/processor POM 为 artifact 自包含而重复 Java/plugin 配置，但缺少机械漂移防线；
- 长 qualification 缺少统一 phase 身份，失败定位主要依赖阅读混合日志。

这些事实证明需要治理反馈拓扑与复用合同，不证明应减少独立 consumer、full regeneration、package
或供应链证据。

## 3. E1：稳定入口与 BuildSession

仓库继续只提供四个稳定入口：

| Intent | Command | 当前职责 |
|---|---|---|
| 日常正确性 | `./scripts/check.sh` | production tests、cumulative consumer、Examples tests/smoke、light hygiene |
| 完整资格 | `./scripts/qualify.sh` | clean/full generation、全部 capability、Benchmark、package、packaged consumer、supply chain |
| 性能证据 | `./scripts/benchmark.sh` | standalone build，或验证同一 BuildSession 后复用 |
| 本地交付 | `./scripts/package-local.sh` | standalone build，或验证同一 BuildSession 后复用；永不发布 |

`build-support/qualification/build-session.py` 使用 repository source/config fingerprint、Git HEAD/status、
Java/Maven identity 和必需 artifact SHA-256 建立 ignored `target/` manifest。组合执行只有在全部身份仍
一致时才可复用；修改 source 或 artifact 后会 fail closed。所有裸 reuse flag 已退出。

Qualification 输出稳定的 `START/PASS/FAIL` phase context，同时保留原始命令输出和退出码。该机制不是
public manifest format、通用 task framework或持久 build cache。

E1 证据：

- BuildSession self-test 通过同候选正向、source mutation 与 artifact mutation拒绝；
- stale manifest 在 source 变化后稳定拒绝为 `source candidate is stale or foreign`；
- daily check 完整通过 runtime `91` tests、processor `34` tests、Examples `13` tests、cumulative
  generated consumer 与三个有界 application smoke；
- daily check warm wall time 从 `34.85 s` 降为 `22.97 s`，减少约 `34.1%`；该结果只描述本机同类
  warm feedback，不是跨机器 SLA。

## 4. E2：POM、artifact 与供应链边界

Root POM保持 non-published aggregator；runtime和processor POM继续自包含，不引入第三 parent/BOM/
build artifact。`artifact-build.sh` 对两项 POM 中必须相同的 encoding、output timestamp、Java
release/source/target、JUnit和既有 plugin version执行fail-closed一致性检查，同时保留processor的
`tools.jar`、forked javac和runtime dependency等合法差异。

E2 targeted qualification在Java 8下通过：runtime `91` tests、processor `34` tests、full
regeneration/stale cleanup、POM mismatch rejection、sources/javadocs classifiers、JUnit isolation和
artifact reproducibility。Root aggregator没有写入本地Maven仓库，production topology仍为恰好两个
artifact。

## 5. E3：CI 拓扑

开发阶段CI现为：

```text
pull_request or push(main/develop/release)
    -> ci.yml -> daily check

workflow_dispatch
    -> release-qualification.yml -> full non-publishing qualification
```

普通CI按workflow/ref取消被新候选取代的旧run；手工full qualification不自动取消。两条workflow继续
保持`contents: read`、dependency cache only、完整Action SHA pin和无deploy/sign/release/package写入。

本专题未获得push授权，因此没有把本地workflow变更外推为post-change远端运行证据。下一次显式push
应自动触发一条daily CI；full qualification需要手工触发。远端读回属于后续push动作的证据，而不是
本地E3实现的替代条件。

## 6. E4：最终资格

最终候选完成以下收口：

- Design长期合同、executable scripts/workflows、Conformance和项目入口一致；
- `BuildSession` self-test、shell/Python语法、Markdown links与`git diff --check`通过；
- `./scripts/qualify.sh`从当前候选完成full non-publishing qualification；
- fixed-host 10K/1M development ratchet使用当前BuildSession重放并通过既有baseline comparator；
- local binary/source/javadoc/checksum/SBOM/provenance/source bundle及独立packaged consumer通过；
- source delivery未泄漏`project/`、`tests/`、`benchmarks/`、`scripts/`或build-support内部机制；
- Temporary proposal/design的稳定事实均已分别晋升到Design和本文，原Temporary删除。

精确命令输出由本次本地运行日志与ignored `target/`产物拥有；本文记录可持续claim，不提交本机绝对
路径、Maven settings、完整日志或构建产物。

## 7. Before / after 与未外推边界

| Concern | Before | After |
|---|---|---|
| Daily check | 近似full qualify，`34.85 s` | 有界correctness lane，`22.97 s` |
| Build reuse | 裸环境变量与target存在性 | SourceCandidate + toolchain + artifact SHA绑定 |
| develop push | CI和full qualification自动双跑 | 只自动daily CI；full手工触发 |
| POM重复 | 必要重复但可能漂移 | 保持artifact自包含并机械检查一致值 |
| 失败定位 | 混合长日志 | 稳定phase START/PASS/FAIL + 原始错误 |

本记录不证明：

- GitHub Release/Package、Maven publication、签名或正式release readiness；
- Java 8以外的兼容性；
- hosted runner上的固定性能阈值；
- 尚未发生的post-change remote workflow运行；
- build-session manifest是外部兼容接口。

## 8. Replacement closure

原Temporary的职责已完成：

- 意图、范围和非目标由本文的治理结论与历史说明保留；
- 长期四入口、候选复用、POM和CI合同由Implementation Architecture拥有；
- 当前实现由scripts、build-support、POM和workflows拥有；
- 当前资格与claim boundary由本文拥有。

因此本专题状态为`PASS / E0-E4_COMPLETED / TEMPORARY_RETIRED`，当前没有active implementation
slice，也没有active bounded governance topic。SOMA Engine文档仍只是独立queued intent。
