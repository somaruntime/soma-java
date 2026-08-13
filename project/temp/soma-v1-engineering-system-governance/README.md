# SOMA V1 工程系统一致性、可重复性与反馈效率治理提案

类型：Temporary / Governance Proposal

状态：`PROPOSAL_ACCEPTED / CANDIDATE_DESIGN_COMPLETE / IMPLEMENTATION_NOT_AUTHORIZED`

日期：2026-08-13

正式事实源：否

Owner：本次工程系统治理的意图、目标、范围、非目标、成功标准与生命周期

> 本文回答“为什么要进行本次治理、希望获得什么结果”。详细工程方案见
> [专题治理设计文档](design.md)。两份文档均为 bounded Temporary，不覆盖正式 Blueprint、
> Design、code/build 或 Conformance，也不授权实施、push、publication 或 release。

## 1. 提案意图

SOMA Java 已经完成 V1 production implementation、G1-G10 qualification、Canonical IR / Execution、
Physical Execution Engine M2、多个性能专题和三个 reference application。当前工程系统已经拥有：

- Java 8 与 Maven 3.9 构建基线；
- `soma-runtime`、`soma-processor` 两项 production artifact；
- processor full-regeneration 与 exact runtime/processor linkage；
- module tests、跨 artifact qualification fixtures 和三个 reference application；
- `check`、`qualify`、`benchmark`、`package-local` 四个稳定命令；
- local package、sources/javadocs、checksum、SBOM、provenance 与 source delivery；
- GitHub CI 与 non-publishing qualification workflow；
- fixed-host 10K/1M development performance ratchet。

这些能力分别成立，不等于它们已经形成成本最小、职责清楚、失败易诊断的一条工程主线。
当前更值得解决的问题是：

> 怎样让同一份干净源码以可理解、可重复、低浪费的方式完成构建、生成、验证、性能防退化、
> 本地交付与 CI 反馈，并确保每项证据绑定准确候选。

本专题不是“再增加一些工程工具”，而是治理现有工程系统的职责、拓扑、复用和反馈效率。

## 2. 为什么现在值得做

### 2.1 产品实现已经稳定

当前没有 active product implementation slice。工程系统治理不需要与 public API、storage、IR 或
execution engine 的大规模迁移并行，可以在稳定候选上识别真实重复和工程摩擦。

### 2.2 现有工程入口已经足够完整

项目不缺少构建、测试、Benchmark 或打包入口。继续添加新的顶层脚本、workflow 或工具，只会扩大
维护面。现在适合从“有没有”转向“职责是否唯一、组合是否高效”。

### 2.3 当前存在可验证的工程成本问题

现场盘点表明：

- `check.sh` 实际复用完整 `qualify.sh`，只跳过 Benchmark，名称给人的成本预期与真实工作不完全一致；
- `develop` push 会同时运行 CI 与 non-publishing qualification，两个 workflow 对构建、测试、
  Examples 与 package 存在显著重叠；
- qualification、Benchmark 与 package 已经有 build-reuse 入口，但复用是否绑定同一次候选和构建会话
  需要显式证明；
- 两个 production POM 重复维护部分插件与 Java 配置，其中既有 artifact 自包含所需的必要重复，也可能
  存在可以消除或机械校验的偶然重复；
- `SOMA_*` 环境变量、阶段输出和失败定位已经形成事实上的工程协议，但还没有经过完整合同审查。

这些问题可以通过运行拓扑、wall time、phase count、artifact identity 和远端 workflow 证据验证，
不是单纯的目录审美判断。

## 3. Target

把 SOMA 的开发工程主线治理为：

```text
clean source candidate
    -> explicit environment and candidate identity
        -> Maven build and full generation
            -> risk-matched correctness qualification
                -> optional performance ratchet
                    -> local delivery and independent consumer
                        -> low-duplication CI feedback
```

治理完成后，维护者和 Codex 应能够回答：

1. 日常修改最快应运行什么；
2. 完整 non-publishing qualification 应运行什么；
3. Benchmark 和 package 如何独立运行，又如何在完整资格中安全复用构建；
4. 每个失败属于环境、构建、生成、测试、场景、性能、打包还是交付边界；
5. 本地结果与 GitHub workflow 是否验证同一候选和同一合同；
6. 哪些配置重复是必要的，哪些只是工程债务。

## 4. 治理目标

### G1：稳定入口职责唯一

保留且只保留四个普通维护者稳定入口：

```text
scripts/check.sh
scripts/qualify.sh
scripts/benchmark.sh
scripts/package-local.sh
```

每个入口必须有明确 consumer、输入、包含/排除、输出、失败边界、产物和成本预期。能力级 helper
继续位于 `build-support/` 或 `tests/`，不冒充第五个用户入口。

### G2：候选与证据一致

构建复用只能发生在能够证明是同一 source candidate、同一构建会话和兼容环境时。不得用 stale
`target/`、旧 SNAPSHOT 或另一轮生成结果支持当前 qualification、Benchmark 或 package claim。

### G3：减少无价值重复

识别同一命令内、跨稳定入口和跨 GitHub workflow 的重复工作。只消除不增加独立证据的重复；为了
证明 clean build、独立 consumer、full regeneration、reproducibility 或 package boundary 而进行的重复
必须保留并说明理由。

### G4：提高反馈效率与可诊断性

日常路径优先快速发现高概率问题，完整资格覆盖低频高风险边界。脚本和 workflow 应输出清楚的阶段
身份，保留原始工具错误，并避免同一候选无意义地占用两套 hosted runner。

### G5：保持 artifact 与供应链边界

治理后仍然只有两个 production artifact；production dependency 默认零；JUnit 保持 test-only；
Maven plugin 和 GitHub Action 版本固定；local package、SBOM、checksum、provenance 与 source delivery
继续来自最终候选。

### G6：形成可持续而不过度的工程模型

不为了 DRY、统一或“最佳实践”引入第三 parent artifact、通用 task framework、容器、Gradle、Make、
新静态检查平台或额外 workflow。新机制必须解决现场证明的独立问题。

## 5. 范围

### 5.1 纳入

- root reactor、`soma-runtime`、`soma-processor`、Examples 与 Benchmarks Maven topology；
- `.mvn/`、POM plugin/dependency/version 与 Java/Maven enforcement；
- `scripts/` 四个稳定入口；
- `build-support/`、`tests/` 与 stable entry 的责任关系；
- code generation、clean build、linkage、independent consumer 与 stale-output boundary；
- qualification、Benchmark、local package 的组合与安全复用；
- `.github/workflows/ci.yml` 与 `release-qualification.yml` 的触发、权限、缓存和重复执行；
- build output、temporary directory、generated output、report 与 source-delivery hygiene；
- 工程 wall time、阶段计数、远端运行时间、失败定位和 before/after 证据；
- 最终 Temporary 晋升、Conformance 与入口关闭。

### 5.2 明确排除

- SOMA public/generated API 与产品能力；
- storage、IR、optimizer、execution、compression、parallel 或性能算法；
- Java 11/17/21 等兼容矩阵；
- GitHub Release、Package、Maven publication、签名、版本或 release branch；
- Docker、Gradle、Makefile、Maven Wrapper 或新的 task runner；
- 新 production/test dependency 或常态化 PMD、JaCoCo、Sonar、SpotBugs、Checkstyle；
- 用户 Manual、White Paper 与完整 `docs/`；
- hosted CI 的严格毫秒级性能 Gate；
- 没有职责变化支撑的目录重排；
- 为未来发布或多平台能力预建 placeholder。

发现排除项是完成目标的必要条件时，应停止并请求 Product Owner 裁决，不能静默扩张。

## 6. 治理对象与正式 Owner

| 事实 | 当前 Owner | 本专题可以做什么 |
|---|---|---|
| V1 产品与能力边界 | [Blueprint](../../blueprint/README.md) | 追溯，不修改 |
| artifact/build 长期合同 | [Implementation Architecture](../../design/implementation-architecture.md) | 发现差距；语义变化需另行晋升 |
| production build/config | POM、`.mvn/`、GitHub workflow、脚本 | 在后续授权内优化 executable fact |
| tests/qualification | module tests、`tests/`、`build-support/qualification/` | 保持证据职责，消除偶然重复 |
| performance evidence | `benchmarks/` | 保持 ratchet，避免冒充通用 SLA |
| 当前结论 | [Conformance](../../conformance/README.md) | 完成后晋升准确 claim |
| 本次候选 | 本 Temporary | 设计与实施结束后删除 |

本专题不创建新的长期工程 Design Owner。若实施证明现有 Implementation Architecture 无法拥有必要
合同，应先形成精确 Design delta，经审核后再实施。

## 7. 期望结果

### 7.1 对维护者

- 四个命令从名称即可推断用途和相对成本；
- 一份最小说明能够完成日常检查、完整资格、Benchmark 和本地交付；
- 失败能够定位到清楚阶段，而不是阅读整段混合日志；
- 独立运行和组合运行保持相同语义。

### 7.2 对 Codex/Agent

- 不需要遍历所有脚本才能选择正确验证；
- 可以从 changed surface 和 risk 选择最窄充分入口；
- 不重复运行输入未变、没有新增证据的昂贵步骤；
- 每项 claim 能绑定准确 commit/dirty candidate、JDK、Maven 和产物。

### 7.3 对 CI

- PR、`develop` push 和手工完整资格各自拥有清楚目的；
- 同一事件不无理由重复大面积相同工作；
- Action 继续最小权限、完整 SHA 固定、non-publishing；
- CI badge 继续反映 `develop` 的日常工程健康状态。

## 8. 成功标准

本专题只有在下列事实同时成立后才算完成：

1. current-state DAG、命令/环境变量/产物 inventory 与 before 成本已经形成；
2. 四个稳定入口合同明确且没有第五个平行入口；
3. build reuse 有同候选证明，或被取消而回到安全 clean build；
4. 必要重复和偶然重复已经分类，后者被删除或合并；
5. Maven 配置保持两 artifact 自包含，没有第三 parent artifact；
6. CI 触发拓扑与开发阶段风险匹配，远端真实运行通过；
7. clean build、full regeneration、module tests、cumulative consumer、Examples、Benchmark ratchet、
   package/SBOM/provenance/source bundle 均由最终候选通过；
8. 工程 wall time、重复阶段数和反馈路径有 before/after 对比；
9. 没有产品语义、production dependency、artifact 或 publication 扩张；
10. 稳定结论晋升到正式 Owner，Temporary 删除，仓库入口同步。

## 9. 风险与停止条件

出现以下任一情况必须停止当前实施路径：

- 为提速需要跳过独立 consumer、full regeneration、zero-stale、package 或供应链证明；
- build reuse 无法证明对应当前候选；
- POM 去重需要发布第三 parent artifact或破坏两 artifact 自包含；
- CI 提速以缩小当前 correctness/qualification claim 为代价；
- 需要新增 dependency、plugin、workflow、稳定命令或开发工具；
- 需要修改 Blueprint、public API、Java baseline 或 release boundary；
- before/after 输入不同，无法支持效率结论；
- 连续调整只改变脚本形态，没有减少成本、改善诊断或建立新证据。

Stop Rule 触发后，应保留当前事实和可选方案，等待 Product Owner 裁决，不用 fallback 掩盖问题。

## 10. 生命周期与权限

当前阶段为：

```text
PROPOSAL                         ACCEPTED
CANDIDATE DESIGN                 COMPLETE / REVIEW_REQUIRED
BASELINE FREEZE                  NOT_GRANTED
IMPLEMENTATION AUTHORIZATION     NOT_GRANTED
QUALIFICATION                    NOT_STARTED
PUBLICATION / RELEASE            NOT_AUTHORIZED
```

当前授权只覆盖本提案、[专题治理设计文档](design.md)及其文档级验证。它不授权修改脚本、POM、
workflow、tests、build-support 或 production code，也不授权 push 和任何发布行为。

## 11. 提案与设计的分责

本提案长期保存本专题的原始意图、目标、范围、非目标与成功标准。设计文档负责当前候选工程方案、
切片、合同、风险防线和证据。

实施开始前的正确顺序是：

```text
专题治理提案
    -> 候选设计
        -> Product Owner审核与Baseline Freeze
            -> Implementation Authorization
                -> 单一Active Slice实施与验证
                    -> Conformance晋升
                        -> Temporary replacement closure
```

允许同时保留多个处于`PROPOSED / QUEUED`状态的提案；同一项目实施时仍只激活一个工程专题或一个
Active Slice。提案数量不等于当前实现并发数。
