# SOMA V1 开发阶段采用准备与防退化治理

类型：Conformance / Governance Closure

状态：`PASS / D1-D4_COMPLETED / TEMPORARY_RETIRED`

日期：2026-08-13

Owner：开发阶段本地消费、轻量性能 ratchet、GitHub 开发入口及其证据边界

## 1. 结论

本专题在不改变 Blueprint、Design、public/generated API、production artifact、dependency、Java 8
基线或发布状态的前提下完成：

1. 当前两项 artifact、Maven dependency/processor path、generated source、standalone execution、linkage
   与 full regeneration 的既有下游消费闭环重放通过；
2. `benchmarks/` 获得 fixed M5 Pro、Java 8、10K/1M `core` 开发阶段 machine-readable baseline；
3. 既有比较器对相同 baseline 为 `PASS`，对人工制造的 98.2% ingest 回归稳定 `FAIL`；
4. Root README 增加 `develop` CI badge，GitHub 增加 Bug 与 Performance 两类 Issue Form；
5. GitHub Dependabot vulnerability alerts 已从 disabled 切换为 enabled，Dependency Graph SBOM API 可读；
6. 两项 workflow 继续只拥有 `contents: read`，外部 Actions 保持完整 commit SHA 固定。

该结论证明 SOMA 当前开发候选具有可重放的本地消费和防退化工程基础。它不证明真实业务项目试用、
生产可用、Java 多版本兼容、跨硬件性能、GitHub Release/Package、签名或正式发布成立。

## 2. 范围与排除

### 2.1 纳入

- 当前 Java 8 Maven 构建与本地 artifact consumption；
- 10K/1M 三项 reference workload 的 fixed-host relative ratchet；
- GitHub Issues 开发反馈入口、CI badge、Dependency Graph / Dependabot Alerts；
- Temporary 晋升、路由与关闭。

### 2.2 排除

- 真实项目/生产试用；
- Java 11/17/21 等多版本 consumer smoke；
- 新功能、API、执行路径、dependency、plugin 或第三 artifact；
- 10M、完整 performance frontier、Profile、allocation/memory attribution 的重新治理；
- hosted CI 的严格毫秒 Gate；
- Release、Package、Maven publication、版本、分支、签名和 release claim。

## 3. D1：独立本地 Consumer

### 3.1 当前 Owner 审核

专题没有新增平行 Consumer：

- Root README 已提供源码安装、runtime dependency 与 processor path；
- `scripts/qualify.sh` 先安装准确 runtime/processor SNAPSHOT，再以 Maven 坐标构建三个独立 Example
  reactor；随后只使用 `target/package` 中最终 runtime/processor JAR 重新编译并运行 Scheduling 与
  Simulation；
- `tests/build-spine` 是最小 generated application；
- `build-support/qualification/artifact-build.sh` 负责 linkage、same-version artifact mismatch、Maven
  downstream full-regeneration、Java 8 bytecode、classifier 与 reproducible artifact。

这些入口已经共同承担“开发阶段其他 Java 项目能否消费当前源码候选”的责任。新建模块、脚本或重复
fixture 只会增加维护成本，因此本专题采用验证和晋升，不增加机制。

### 3.2 定向证据

环境：Amazon Corretto `1.8.0_502-b07`，Maven 3.9.16，macOS arm64。

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home \
  build-support/qualification/artifact-build.sh
```

结果：`artifact-build: ok`。其中 runtime 91 tests、processor 34 tests 无 failure/error；最小 generated
consumer 输出 `soma-i0-consumer: ok`；两轮 Maven regeneration、same-version linkage rejection 与 artifact
reproducibility 均通过。

最终 `scripts/check.sh` 进一步重放当前 Maven Example consumers、packaged JAR consumers、SBOM/checksum/
source bundle 与全仓 correctness；结果见第 7 节。

## 4. D2：轻量性能防退化

### 4.1 Profile 裁决

为了兼顾覆盖与日常成本，本专题选择：

- 10K 与 1M 两个规模；
- `core` workload，`soma-auto`；
- Scheduling、Simulation、Real-time Dispatch；
- parallelism 8；
- 每组 3 个 fresh JVM，每个 operation 2 warmups / 5 samples；
- `-Xms2g -Xmx8g`、6 GiB SOMA memory budget；
- profiler 与 memory attribution 关闭。

这组 profile 覆盖 ingest、scan、parallel scan、Key、Index、Join、Top 与 GroupBy 正常路径。10M、完整
frontier、JFR 和内存归因保持人工专项入口，不复制到日常 ratchet。

### 4.2 Fixed-host identity

- Apple M5 Pro，18 cores，48 GB memory；
- macOS 26.6.1，arm64；
- Amazon Corretto `1.8.0_502-b07`；
- Maven 3.9.16；
- production source commit：`b854e577dd60ff3de36e190f35ce4835bf14ffe0`；
- benchmark 采集时 worktree 仅包含本专题 documentation/benchmark/GitHub surface，production source 未变。

### 4.3 Representative median

| Scenario | Rows | Ingest | Scan | Parallel scan | Key 10K | Index | Join | Top | GroupBy |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Real-time Dispatch | 10K | 20.373 ms | 0.262 ms | 0.112 ms | 1.016 ms | 0.029 ms | 0.623 ms | — | — |
| Scheduling | 10K | 15.491 ms | 0.473 ms | 0.277 ms | 0.763 ms | 0.022 ms | 0.507 ms | 0.047 ms | 0.343 ms |
| Simulation | 10K | 11.580 ms | 0.256 ms | 0.110 ms | 0.841 ms | 0.032 ms | — | 0.393 ms | — |
| Real-time Dispatch | 1M | 717.674 ms | 3.997 ms | 0.653 ms | 1.734 ms | 0.157 ms | 7.673 ms | — | — |
| Scheduling | 1M | 475.436 ms | 20.735 ms | 17.557 ms | 1.387 ms | 0.021 ms | 10.173 ms | 0.045 ms | 21.462 ms |
| Simulation | 1M | 371.072 ms | 3.689 ms | 0.648 ms | 1.378 ms | 0.030 ms | — | 9.556 ms | — |

全部 18 个 fresh JVM run 通过 scenario correctness、shared fingerprint 与 SOMA logical fingerprint。
Machine-readable baseline 与重放合同由
[`benchmarks/baselines/development-m5-pro-java8`](../../benchmarks/baselines/development-m5-pro-java8/README.md)
拥有。

### 4.4 Ratchet positive / negative

对 10K、1M baseline 各执行 self comparison，均输出 `benchmark-compare: PASS`。随后只在临时候选中把
10K Real-time Dispatch ingest median 从 20.373 ms 改为 40.373 ms，比较器输出：

```text
benchmark-compare: FAIL: ... ingestNanos +98.2%
```

现有默认阈值要求同时超过 15% 和 2 ms，且先验证 workload identity、group set 与 logical/shared
fingerprint。它保护同机相对回归，不是通用性能合同。

## 5. D3：GitHub 开发入口

### 5.1 Repository surface

- [Bug Report](../../.github/ISSUE_TEMPLATE/bug_report.yml)要求 revision、Java 8/Maven/OS、最小 schema/
  operation、expected/actual、structured failure 与安全 reproducer；
- [Performance Report](../../.github/ISSUE_TEMPLATE/performance_report.yml)要求硬件/runtime、数据形态、
  测量方法、correctness/fingerprint、profile 与复现；
- 两份 Issue Form 经 Ruby YAML parser 验证；
- Root README 的 badge 精确指向 `ci.yml` 的 `develop` 状态，不展示不存在的 Release 或 Maven artifact。

### 5.2 Repository settings snapshot

2026-08-13 使用 GitHub REST API 现场检查：

- vulnerability alerts 初始返回 `404 / Vulnerability alerts are disabled`；
- `PUT /repos/somaruntime/soma-java/vulnerability-alerts` 成功；
- 再次 `GET` 返回 `204 No Content`，证明 alerts enabled；
- Dependency Graph SBOM API 返回 `com.github.somaruntime/soma-java`。

这是可漂移的远端设置快照。没有启用 Dependabot version-update PR、Advanced Security、Release、Package
或其他付费/发布能力。

### 5.3 Actions 权限

`.github/workflows/ci.yml` 与 `release-qualification.yml` 均只有：

```yaml
permissions:
  contents: read
```

`actions/checkout` 与 `actions/setup-java` 使用完整 commit SHA。现状已经满足最小权限和供应链固定要求，
因此本专题没有制造无意义 workflow diff。

## 6. Quality claims

| Claim | Evidence | Result |
|---|---|---|
| Functional | Maven/generated/packaged consumer、18 fresh JVM correctness/fingerprint | PASS |
| Code | 未新增 production code、consumer、runner 或 parallel mechanism；baseline 复用既有 harness | PASS |
| Architecture | exactly two artifacts、Java 8、Design/API/Execution均未变化 | PASS |
| Engineering | machine-readable baseline、positive/negative compare、Issue Forms、GitHub setting readback | PASS |

## 7. 最终资格

最终候选运行：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home \
  ./scripts/check.sh
```

结果：`qualification: PASS`。

- runtime 91 tests、processor 34 tests，0 failure/error；
- compression/metadata cumulative consumer：`PASS`；
- Scheduling、Simulation、Real-time Dispatch：`PASS`；
- packaged Scheduling 与 Simulation consumer：`PASS`；
- local package、runtime/processor binary/sources/javadocs、checksum、SBOM、provenance 与 source bundle：
  `PASS`；
- source delivery 未包含 `benchmarks/project/tests/scripts/target/.git` 等内部开发证据；
- Issue Form YAML、baseline JSON/compare、Markdown target 与 `git diff --check`：`PASS`。

## 8. Replacement / Temporary closure

- 没有新增第二套 Consumer、benchmark runner、compare tool、CI workflow 或发布入口；
- baseline 长期 Owner 为 `benchmarks/`，GitHub feedback Owner 为 `.github/`，当前 claim Owner 为本记录；
- bounded Temporary 的目标、设计与证据已经完全晋升到上述 Owner；
- Temporary 删除后，`project/temp/` 只保留独立的 SOMA Engine queued intent；
- 当前仍无 active implementation、performance 或 bounded governance slice。

## 9. 最终声明边界

本专题可以声明：

> SOMA V1 当前 Java 8 开发候选具备可重放的本地 Maven/generated/packaged consumption，fixed-host
> 10K/1M reference-workload performance ratchet，以及低成本 GitHub 开发反馈与 dependency alert 入口。

不得声明：

- 已在真实项目或生产环境试用；
- 支持 Java 8 以外的 JVM；
- baseline 是跨机器 SLA；
- 已允许或完成 Release、Package、Maven publication、签名或正式发布。
