# SOMA Java V1 产品面收敛与仓库瘦身治理报告

类型：Report / Governance / Repository Surface

状态：completed；G6 仍 blocked

Owner：SOMA Java V1 产品面与仓库面治理

受众：SOMA maintainer、产品设计者、Gate owner 与 release owner

适用版本：`0.2.0-SNAPSHOT`

治理基线：`develop`
`d3f2e354fd553b3d2923cc2c145413930e06ba8c`

输入事实源：Blueprint、Design、Implementation Map、Conformance、production/public
source、tests/fixtures、benchmark、三个 reference application、Gate 与 release
evidence

事实范围：本轮产品面裁决、仓库 surface delta、replacement closure、验证结果与
scope non-regression

非事实范围：新的 public/schema/runtime 语义、跨环境性能承诺或 G6 release 授权

最后审查日期：2026-07-28

## 1. 结论

本轮治理已完成。SOMA 没有为了“瘦身”降低产品目标，也没有把 Design 仍需要的
能力、Java 8 specialization、String 路径、单表/双表 100M 目标、Metadata hierarchy、
Result Delivery 或三个 Example 删除。治理结果是：

- production 主源码保持 261 份、39,134 行，公开可执行分类保持 224 个
  `PUBLIC`、2 个 `INTERNAL`；没有 public/generated/schema/runtime 语义变化；
- `soma-testkit` 作为没有真实 consumer 的 artifact/module 退出，compiler 与
  external-consumer fixture 归入仓库级 `tests/fixtures`；
- tests、Gate 与 benchmark 编排由历史 phase/slice/cutover 名称收敛为 Capability、
  product journey、consumer 和 qualification；
- current 文档只保存当前事实，迁移 checkpoint、archive、thin tombstone 和空
  Temporary 占位退出 checkout；
- package 与 security evidence 补齐 `soma-dataflow`，发布形态与四个 production
  module 一致；
- 三个 reference application 继续作为相互独立的普通 Java 8 consumer，不制造
  第四个共享 example runtime/test artifact；
- 全部候选完成 `KEEP / REPLACE / DELETE` 裁决，`UNKNOWN=0`，不存在 predecessor、
  parallel fact owner 或 migration-only artifact 遗留。

本轮改善的是产品叙事、责任边界和维护面，不是把功能缺口改名为 optional，也不产生
新的 release readiness 或 public performance claim。

## 2. 裁决原则

所有 surface 均先追踪 Blueprint/Design intent、完整调用链、Capability owner、
consumer、lifecycle、failure domain 和 evidence claim，再判断保留、替换或删除。
以下信号不能单独成为删代码理由：

- 文件或类型较大；
- 只有一个实现、调用者或当前 application 引用；
- primitive/String specialization 看起来重复；
- generated bridge 具有 public JVM visibility；
- benchmark lane 数量较多。

新增 surface 必须拥有现有 canonical owner 无法承担的独立语义、证据、consumer 或
lifecycle；replacement 必须在同一变更中关闭 predecessor。该规则已固化到
`AGENTS.md`、Engineering 和 Implementation Map，不另建治理框架。

## 3. Surface delta

统计口径为治理基线的 tracked surface 与本轮完成后的逻辑 checkout surface；移动
按最终路径计数，`target/` 等 disposable evidence 不计入。

| Surface | 治理前 | 治理后 | 裁决说明 |
|---|---:|---:|---|
| 逻辑文件 | 956 | 867 | 删除历史、重复编排和无 owner artifact；保留全部必要 evidence |
| Java | 652 / 75,917 行 | 651 / 75,611 行 | production 与 Example 不变；test capability suite 去重 |
| Markdown 文件 | 142 | 79 | current 文档原子化；历史过程回归 Git |
| 四个 production module 主源码 | 261 / 39,134 行 | 261 / 39,134 行 | 不做机械删减或装饰性拆分 |
| public/internal executable golden | 224 / 2 | 224 / 2 | public/generated compatibility 不变 |
| 三个 Example 主源码 | 128 / 8,662 行 | 128 / 8,662 行 | 三个独立产品故事全部保留 |
| Java test source | 49 / 11,297 行 | 52 / 11,252 行 | 一个大 runtime suite 按能力拆开；净减 45 行 |
| compiler/external fixture | 224 artifact；175 Java / 6,464 行 | 219 artifact；172 Java / 6,380 行 | 删除 3 组无 current Gate 的 phase0 fixture，其余迁至统一 owner |
| `soma-benchmarks` 主源码 | 38 / 10,183 行 | 38 / 10,183 行 | lane/claim 不减；`PostCutover` 只做 current 命名替换 |
| Shell | 49 / 6,360 行 | 35 / 6,023 行 | phase/slice/delegate 收敛为长期 capability/profile 入口 |
| root current/checkpoint Report | 27 | 9 | 只保留当前综合、Gate、性能和 release 决策所需报告 |
| archive Report | 15 | 0 | 历史由 Git 保存，不在 checkout 维持第二叙事 |
| superseded tombstone | 26 / 702 行 | 0 | 无外部兼容承诺，不保留空路径事实源 |
| reactor module | 11 | 10 | 退役无真实 consumer 的 `soma-testkit` artifact |

Java test 文件数增加 3 不是 evidence 膨胀：原先聚合在
`RuntimeCorePhase1Check` 的不同 Capability 被拆为明确 contract suite，同时测试
总行数下降。Benchmark 文件数与 lane 数不变，因为逐项审计确认每一族都拥有不同
claim、oracle 或 failure domain。

## 4. 关键 replacement closure

| 原 surface | canonical successor / 最终裁决 | 闭合证据 |
|---|---|---|
| `soma-testkit` module、comparator、自测 | artifact 退役；fixture 由 `tests/fixtures` 拥有 | reactor、compiler、public API、external consumer 全部使用新路径 |
| DataFlow foundation 与 A–F slice 编排 | `check-dataflow-contracts.sh` 与七个 Capability contract | 全部原语义、reference differential、component performance 保留 |
| runtime/compiler 的 phase Gate | Runtime、Compiler、Codegen、Access、Ownership、Value 等能力入口 | golden、negative、generated/external consumer 全部重放 |
| Fast/Scale/Soak/Full 与三个转发脚本 | `check-reference-application-performance.sh <profile>` | 三应用 × 三 profile baseline identity 不变 |
| retired-name/report 枚举 checker | 通用、fail-closed 的文档和结构不变量 | 不再为每次迁移永久保存旧 token |
| `PostCutoverComponent*` | `AccessComponent*` 与 `check-access-performance.sh` | 五 fork、validator、negative comparator、baseline 通过 |
| 三模块 package/SBOM 形态 | annotations、processor、runtime-core、dataflow 四模块 exact set | 17 artifact 双构建一致；SBOM exact component set 通过 |
| checkpoint/archive/tombstone/空 temp | 正式 Design、Map、Conformance、Engineering、当前 Report 或 Git history | current 导航与 checker 不再依赖历史文件 |

少量 fixture 内部仍保留 `phase0` / `phase5` package、schema 或 golden identity。
它们是被测试的冻结输入/输出字节，不是 current Gate 名称、目录 taxonomy 或迁移
入口；为表面去词而改名只会无意义地改变 schema hash 与 compatibility evidence。

## 5. Production、Benchmark 与 Examples 裁决

### 5.1 Production

完整 Metadata hierarchy、primitive/String typed physical path、generated
cross-package protocol、Eager Detached、callback-scoped delivery，以及 runtime 与
DataFlow 各自拥有的 `StatsMode` 均保留。后两者同名但 owner、生命周期和诊断语义
不同，不合并。

`SomaProcessor`、`DenseTableSourceEmitter`、`DenseTableState` 和
`ChildOwnershipRegistry` 没有发现 predecessor/successor 双轨或可独立删除的成片
逻辑。本轮不以文件长度制造 micro-helper。以后只有稳定 capability boundary 与
co-change 分离成立时，才允许 internal refinement。

### 5.2 Benchmark

以下 evidence family 全部保留：20 条 smoke lane、Access component、DataFlow
component、10 条 runtime-scale qualification lane、三个应用的 9 个 profile、
baseline comparator/negative path 和 generated footprint。它们分别证明不同的
Access、Transformation、Small/Medium、单表/双表 100M、String、Expansion、
Delivery、Soak、领域 correctness 或 code-size claim，不能互相替代。

### 5.3 Examples

industrial scheduler、grassing simulation 与 real-time dispatch rule engine
分别证明动态调度、确定性仿真和多源规则 DataFlow。三者的少量 test-only
environment/options/metrics mechanics 保持本地拥有；抽取共享 artifact 会破坏
“三个独立普通 consumer”的产品证据，净维护收益为负。

## 6. 验证

本轮在 Azul Zulu OpenJDK `1.8.0_492-b09`、full `javac 1.8.0_492`、Maven
`3.9.16`、macOS `26.5.2` `aarch64` 上完成：

- `./scripts/check.sh`：通过；构建、224/2 public API、Compiler/Codegen、
  Runtime、DataFlow、generated/external consumer、三个独立 Example、code-size、
  benchmark smoke、Access 与 DataFlow component performance 全部闭合；
- `SOMA_PACKAGE_ALLOW_DIRTY=true ./scripts/package-smoke.sh`：通过；parent POM
  加四个 production module 共 17 个文件，两次 isolated repository clean build
  逐字节一致，manifest SHA-256 为
  `0961db9114214834d11178c6152a23303694ed14643e323a862723f11916a8d4`；
- 固定 OSV-Scanner 2.3.8 的 `./scripts/security-release-scan.sh`：通过；exact
  component set 为四个 SOMA module 加 JDK 8 `tools`，known published
  vulnerability 为 0、declared-license violation 为 0、production runtime
  third-party dependency 为 0；SBOM SHA-256 为
  `b7d959f44e45288993e28fbe9a07f7390083fb298aa6879b73c70c6ad1844a22`；
- 文档、迁移引用、可执行权限和 `git diff --check`：通过。

本轮没有修改 production source、runtime-scale workload 或 qualification
判定，因此没有把已经完成的 100M 全量资格验证再重复一遍。当前单表/双表 100M、
String、Small/Medium 和十条 qualification 结论继续由
`2026-07-28-runtime-boundary-group-scale-readiness-governance-report.md` 拥有；
本轮综合 Gate 已重放其 contract smoke 与所有受影响的 component/application
边界。

## 7. Scope non-regression 与剩余边界

- Java 8、Schema-Defined、Compiler-Specialized、JVM Heap-Resident 目标不变；
- `State / Owner + Capability + Plan / Lifecycle`、完整 Metadata、四类 field、
  parent-owned child、bounded execution 与两层自适应并行目标不变；
- String 仍为白名单 reference-backed immutable scalar，不引入 dictionary/arena，
  也不允许任意 Object graph；
- Eager Detached 仍是默认 Result Delivery；callback-scoped streaming 仍是受控
  试点，不扩展为 Iterator、pull cursor 或 async stream；
- Small/Medium、单表和双表 100M、String、bounded intermediate/result 与三个
  reference application 的产品目标和 claim boundary 均未降低；
- Conformance 仍只保留 `CF-005` 环境/profile evidence 有限与 `CF-006` G6
  release 事实不足；
- G6 仍因真实 SCM/contact、namespace ownership、签名/发布、clean provenance
  和正式 support matrix 阻塞。dirty、unsigned、本机证据不得外推为 public
  release readiness。

专题 closeout 时 `UNKNOWN=0`、parallel owner=0、migration-only artifact=0、
active Temporary=0。后续工作只允许围绕正式 Design 做 additive completion 或
internal refinement；任何新的长期设计变化必须建立新的独立专题，不能恢复本轮已
退出的历史叙事。
