# SOMA Java I8 Product Qualification

类型：Conformance / Implementation Slice Qualification

状态：`LOCAL_PASS / REMOTE_QUALIFICATION_PENDING`

Slice：`I8 COMPLETED LOCALLY / REMOTE G10 PENDING`

Gate disposition：`G1-G9 PASS`；
`G10 LOCAL_PASS / REMOTE_PENDING`

正式事实源：是（I8 implementation、G9与G10 local qualification current executable fact）

Owner：SOMA Java I8 implementation、performance、package 与 qualification evidence

资格日期：2026-08-09

## 1. 结论

I8 已实现并在干净的 Java 8 checkout 上完成一次完整本地资格。当前 V1 已具备：

- 调度、仿真、实时派工三个严肃的 downstream Example project；
- Medium、Narrow、Reference-mixed 三类百万行 profile；
- 可重放的 JFR、GC、RSS、阶段计时与 fingerprint 证据；
- source/javadoc JAR、source bundle、LICENSE/NOTICE、SPDX SBOM、checksum 与 provenance；
- packaged processor/runtime 的独立 consumer smoke；
- repository-local CI 与 non-publishing release qualification workflow。

本次资格没有发现需要修改 SOMA runtime、重新准入 Loader 或改变 Blueprint/Design 的正常路径
问题。Product Owner 已于2026-08-09批准G9 threshold并签署本地I8资格；当前只剩推送
`develop`后取得远端CI/release-qualification结果。

在远端结果完成前，G10与V1 implementation qualification不能宣告最终完成；本记录也不构成
GitHub Release、Package publication、签名或正式发布授权。

## 2. 实施边界

Implementation commit：`cd0d476ad4f3d1e3db978534c7942d49604876a9`

### 2.1 Example projects

`soma-examples` 是非 production-artifact 聚合工程，下含三个独立 application：

| Project | 正常用户旅程 |
|---|---|
| scheduling | Job/Machine/Option、Key/Index、Join、top、GroupBy、mutation |
| simulation | Event/State、顺序扫描、Key/Index、stable top、remove |
| real-time-dispatch | pending/eligible/machine、ordinary Object reference、Index、Join、remove |

三个工程通过正式 `soma-runtime` 与 annotation processor 构建 generated API；processor 仅位于
annotation processor path，不泄漏为 application runtime dependency。三个 Main 均产生确定性
结果并通过独立 Java 8 执行。

### 2.2 Qualification 与 delivery surface

- `scripts/benchmark-i8.sh`：三个场景、三次 fresh JVM、JFR/GC/RSS/计时/fingerprint；
- `scripts/package-i8.sh`：明确 source delivery allowlist、两项 production artifact、SBOM、checksum、provenance；
- `scripts/qualify-i8.sh`：I0-I7回归、Examples、profile、package、独立 packaged consumer 与安全边界；
- `.github/workflows/ci.yml`：clean Java 8 CI；
- `.github/workflows/release-qualification.yml`：`develop`上的 non-publishing qualification；
- `scripts/check.sh`：当前本地 canonical qualification 入口。

未增加第三项 production artifact、新 production dependency、remote publication 或正式 release
claim。

## 3. Canonical local qualification

正式入口：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home \
SOMA_I8_ROWS=1000000 \
SOMA_I8_RUNS=3 \
SOMA_I8_PARALLELISM=8 \
./scripts/qualify-i8.sh
```

最终结果：

```text
Java/Javac:                       Amazon Corretto 1.8.0_502, class major 52
Maven:                            3.9.16
soma-runtime tests:               55 run, 0 failures/errors/skips
soma-processor tests:             34 run, 0 failures/errors/skips
I2 112-Table scale fixture:       PASS
I7 regression/consumer:           PASS
Scheduling example:               PASS
Simulation example:               PASS
Real-time dispatch example:       PASS
Nine million-row JVM profiles:    PASS
Package/SBOM/checksum/provenance:  PASS
Packaged independent consumer:    PASS
Source delivery allowlist:        PASS
Local security/trust checks:       PASS
i8-qualification:                 PASS
```

资格从 implementation commit 的干净 tree 运行；package provenance 记录 `tree.state=clean`、
`publication=none`。Source bundle 不包含 `project/`、tests、scripts、profile、`target/`、`.git/`
或 predecessor material。

## 4. Performance evidence

### 4.1 Environment

| Dimension | Value |
|---|---|
| Machine | Apple M5 Pro，18 cores，48 GB physical memory |
| Qualification allocation | 8 parallel participants，8 GB JVM heap，6 GB SOMA budget |
| JVM | Amazon Corretto 8.502.07.1 |
| GC | Parallel GC |
| Dataset | 每个 profile 1,000,000 primary rows；3次 fresh JVM |

该运行低于16 core/32 GB qualification envelope；物理机器拥有48 GB内存不构成SOMA最低或最大
环境声明。

### 4.2 Three-run results

下表为 `min / median / max`，时间单位为毫秒：

| Scenario | Ingest | Sequential scan | Parallel scan | Key 10k | Index | Join | Stateful | Max RSS |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Scheduling / Medium | 788 / 800 / 837 | 34 / 37 / 41 | 36 / 39 / 42 | 6 / 6 / 6 | 6 / 6 / 7 | 23 / 23 / 24 | Group 53 / 53 / 54 | 0.96 GiB |
| Simulation / Narrow | 675 / 689 / 699 | 27 / 27 / 28 | 26 / 27 / 27 | 10 / 10 / 11 | 0 / 0 / 0 | n/a | Top 206 / 210 / 211 | 0.93 GiB |
| Dispatch / Reference-mixed | 1441 / 1469 / 1505 | 33 / 33 / 33 | 35 / 38 / 38 | 13 / 13 / 13 | 2 / 2 / 3 | 19 / 20 / 21 | n/a | 1.73 GiB |

三个场景的fingerprint在三次JVM运行中稳定，且sequential/parallel结果一致：

- Scheduling：`56702610`；
- Simulation：`23470633`；
- Real-time dispatch：`24374813604`。

压缩后的representation bytes在三类固定数据模型中均低于plain-equivalent bytes：

| Scenario | Representation bytes | Plain-equivalent bytes |
|---|---:|---:|
| Scheduling | 40,806,937 | 41,657,056 |
| Simulation | 40,783,804 | 44,704,480 |
| Dispatch | 40,930,960 | 48,767,712 |

### 4.3 Interpretation

- 百万行正常路径在全部三个场景中稳定完成，RSS最高约1.73 GiB；
- repeated add虽是最重阶段，但最复杂场景同时装载两张百万行Table仍在约1.5秒完成，没有出现
  饱和、失控分配或需要Loader的证据；
- 百万行轻量scan的parallel路径与sequential接近而非更快，因此最佳实践仍是默认sequential，
  只有实测获益的计算才显式`parallel()`；
- manual baseline只持有某个operation所需的最小数据，不承担完整schema、atomic state、secondary
  Index与resource guard，因此只作为观察参照，不是等价产品或硬倍数目标；
- 本次没有为了benchmark修改runtime合同或加入场景特化路径。

JFR、GC log与RSS证据均可读取。Corretto 8在本机M5环境中报告method sampler异常，因此CPU
method-sampling覆盖不完整；这不影响阶段计时、fingerprint、GC、allocation event、RSS和package
证据，但本报告不据此声明细粒度CPU热点已经穷尽。

## 5. Approved G9 threshold appendix

Product Owner于2026-08-09批准以下条件作为同一环境/同一fixture上的V1资格阈值；它们不是
公开性能承诺：

| Dimension | Approved threshold |
|---|---|
| Correctness | 三次fresh JVM fingerprint稳定；sequential/parallel结果相同 |
| Dataset | 三种固定模型各至少1,000,000 primary rows |
| Peak RSS | 每个场景不超过2.5 GiB |
| Ingest | Scheduling ≤1.25 s；Simulation ≤1.10 s；Dispatch ≤2.25 s |
| Scan | sequential与parallel均≤75 ms |
| Lookup/relation | 10k Key≤30 ms；Index≤25 ms；Join≤75 ms |
| Stateful | 当前Top/GroupBy阶段≤300 ms |
| Variance | 非零核心阶段三次运行 `max/min ≤1.30` |
| Representation | 当前三类compressible fixture均不大于plain-equivalent bytes |

这些阈值给当前结果保留约30%到100%以上的回归余量，能够发现明显退化，又不把单次机器噪声或
当前实现细节固化成对外兼容合同。一亿行仍是架构愿景，不属于本次V1硬Gate。

Loader trigger在本轮不成立：repeated add不是无法达到合理资格边界的dominant blocker，因此不建立
Loader Temporary。

## 6. G10 disposition

| Evidence | Local disposition |
|---|---|
| clean Java 8 build/full regeneration | PASS |
| malformed/large schema、name/code injection与diagnostic/path边界 | PASS（累计G1-G2证据） |
| checked arithmetic/resource/callback/trust boundary | PASS（累计G3-G8证据） |
| dependency/license/known-vulnerability boundary | PASS（I0依赖基线；I8无新增依赖） |
| source/javadoc/LICENSE/NOTICE/SBOM/checksum/provenance | PASS |
| packaged artifact independent consumer | PASS |
| source delivery allowlist/no predecessor/internal material | PASS |
| repository-local CI/release qualification definition | PASS |
| remote `develop` workflow execution | PENDING（尚未push） |
| GitHub Release/Package/signing/publication | NOT_AUTHORIZED / NOT_PERFORMED |

## 7. Owner sign-off

Product Owner于2026-08-09：

1. 批准第5节G9 qualification thresholds；
2. 审核并签署本报告，认可I8 normal-path evidence足以关闭本地implementation slice。

下一步提交并推送`develop`，以远端CI与non-publishing release qualification结果完成G10最终闭环。
