# SOMA 三层性能基线治理报告

类型：Governance Report

状态：正式收口

Owner：SOMA three-layer performance baseline governance

实施基线：`05e7f93`

实现候选：`5be618a`

事实范围：三层性能 evidence 责任、artifact/comparator、三份 baseline、校准、
Gate、scope non-regression 与收口结论

非事实范围：跨环境 SLA、正式支持矩阵、public performance claim、G6 readiness

审查日期：2026-07-24

## 1. 结论

专题已经完整收口：

1. `soma-benchmarks` 拥有领域中性 Component Performance Baseline；
2. 两个 child application 各自拥有 Integrated Performance Baseline；
3. Public Performance Evidence / Claim 保持独立审批边界，当前数量为 `0`；
4. 三条 lane 共用 Java 8 strict baseline protocol/comparator，但不共享应用
   workload、阈值或 POM dependency；
5. measurement、baseline 和 comparator result 全部保持
   `claimAllowed=false`；
6. 普通 Gate 只读 baseline，环境不匹配为 `not-applicable`，invalid artifact
   仍 fail closed。

本轮是 additive evidence completion 和 contract-preserving refinement，不改变
SOMA 产品或两个应用的生产语义。

## 2. 实现与 Owner

| 责任 | 当前 Owner |
|---|---|
| baseline v1 parser/comparator/result | `PerformanceBaselineDefinition`、`PerformanceBaselineComparator` |
| component measurement/validation | `PostCutoverComponentBenchmark`、`PostCutoverComponentArtifactValidator` |
| component baseline | `soma-benchmarks/.../performance-baselines/` |
| scheduler baseline | scheduler `src/test/resources/benchmark/` |
| simulation baseline | simulation `src/test/resources/benchmark/` |
| 三层结构防回归 | `check-performance-baseline-architecture.sh` |
| 综合验证 | `scripts/check.sh` |

Comparator 先检查 exact record shape、claim、artifact version、fork、identity 和跨
fork 环境一致性，再判断环境适用性。准入状态为 `passed`、`failed` 和
`not-applicable`；后者不能掩盖坏 artifact，也不构成性能通过。

阶段提交：

| Commit | Slice |
|---|---|
| `29d3cf6` | Temporary 协议与 Stage 0 审计 |
| `2c6e26f` | 三层详细设计 |
| `66b7f6a` | neutral comparator 与环境 identity |
| `e26cf08` | component baseline 与 5-fork Gate |
| `a465929` | exact record-shape 和 numeric equality |
| `9da5405` / `ab28350` | 两个 application artifact v2 与 9-fork 能力 |
| `4b264fe` | 两个 application-owned baseline |
| `1b0d980` | 三层结构 Gate |
| `5be618a` | 旧 simulation 粗 allocation 双 Owner 退役 |

## 3. 校准环境与规则

环境为 Azul Zulu `1.8.0_492-b09`、OpenJDK 64-Bit Server VM
`25.492-b09`、macOS `26.5.2`、aarch64、Apple M5 Pro。Component heap 为
`-Xms256m -Xmx512m`；两个应用为 `-Xms256m -Xmx256m`。

- component 普通 Gate 5 fork，application 普通 Gate 3 fork；
- 校准为 9 个独立 JVM fork；
- application allocation limit = `ceil(max × 1.05)`；
- timing limit = `ceil(max(p50 × 1.50, p90 × 1.25))`；
- deterministic/high-water 全等；GC maximum 为 0；
- setup/preparation 报告但不进入 hot-operation timing Gate。

### 3.1 Reference applications

| Lane | allocation range / limit bytes | timing p50 / p90 / limit ns | deterministic evidence |
|---|---|---|---|
| industrial scheduler | `8,319,504..8,320,064` / `8,736,068` | `37,619,668` / `39,133,584` / `56,429,502` | result/schema/plan 全等；scratch `3,015/7,560/368`；GC 0 |
| grassing simulation | `7,439,296..7,440,784` / `7,812,824` | `66,086,417` / `76,318,167` / `99,129,626` | result/schema/plan 全等；scratch `64,333/27,336/12,776`；population `1,139`、growth `1`；GC 0 |

两份校准 artifact 均绑定 `ab28350`、default profile、warmup 1、
measurements 3 和各自 workload/checksum。普通 Gate 在当前精确环境再次
`passed`。

### 3.2 Component 复核

Checked-in component baseline 由 `66b7f6a` 的 9-fork calibration 产生。本次在
实现候选上另做 9-fork 只读复核：

| Lane | 复核 p50 / p90 ns/op | checked-in median limit |
|---|---:|---:|
| packed zero-stage count | `56.7082 / 77.4916` | `84` |
| exact zero-stage count | `93.075 / 130.3084` | `140` |
| exact one-filter count | `584.875 / 590.6582` | `867` |
| exact three-stage count | `736.5834 / 772.7668` | `1,105` |
| exact five-stage overflow | `946.0834 / 1,251.55` | `1,552` |
| exact filter-sort scalar Index | `747.2418 / 816.3418` | `1,112` |
| long ColumnTraversal | `1,076.8 / 1,157.9168` | `1,601` |

全部 median 规则通过；7 条 allocation envelope、3 类 GC count 和 24 条
exact-index retained-memory 全等规则也通过。首次复核曾出现一个约
`2.8 µs` 的 isolated timing outlier；按既定纪律未据此放宽 baseline，而是重跑
并取得上表稳定结果。普通 Gate 不会自动重写 baseline。

## 4. Validation

- `PerformanceBaselineComparatorCheck`：9 个 pass/fail/not-applicable/shape/
  claim/fork/identity/stability negative paths 通过；
- component artifact：每 fork 16 allocation + 24 memory records，validator 和
  5-fork comparator `passed`；
- scheduler/simulation：四 profile correctness/long-run、ordinary consumer、
  production JAR purity 和 3-fork comparator 均通过；
- 三层结构：component=1、reference-application=2、public-claim=0；
- generated footprint、public API、schema hash、codegen、runtime、external
  consumer 与完整 `./scripts/check.sh` 通过；
- `./scripts/check-docs.sh`、`git diff --check` 通过。

Zulu JDK 为 `1.8.0_492-b09`，Maven 为 `3.9.16`。本机结果不外推为其他 Zulu
update、OS、architecture 或 JDK distribution 的支持结论。

## 5. Scope non-regression

从治理起点到实现候选：

- `soma-annotations`、`soma-runtime-core`、`soma-processor`、两个应用
  `src/main`、应用 POM 和根 POM 均无改动；
- public/generated API、annotation Schema、Access Model、runtime/Index/
  ownership/failure 语义均未变化；
- component lane cardinality 和 Access Pattern 覆盖未缩小；
- 两应用的四 profile、领域 oracle/invariant/lifecycle、配置和 result identity
  未变化；
- baseline 位于 application test resources，不进入 production JAR；
- 旧 simulation 64 KiB/tick 粗阈值只在更严格的版本化 allocation baseline
  已生效并通过后删除，没有削弱 Gate；
- 未引入第三方依赖、temporary public API、parallel canonical path 或未来迁移。

剩余差距只有既有 `CF-005` 环境覆盖限制和 `CF-006` G6 外部事实，不是本专题尾项。
本专题没有遗留 active Temporary、双 Owner、隐式 public claim 或待迁移实现。
