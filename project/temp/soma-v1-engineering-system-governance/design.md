# SOMA V1 工程系统一致性、可重复性与反馈效率治理设计文档

类型：Temporary / Candidate Governance Design

状态：`BASELINE_FROZEN / E0-E1_COMPLETED / E2_ACTIVE / IMPLEMENTATION_AUTHORIZED`

日期：2026-08-13

正式事实源：否

Owner：本次治理的工程系统模型、稳定命令合同、候选身份、构建复用、CI拓扑、实施切片与证据设计

## 1. 文档责任

本文回答：怎样在不改变 SOMA 产品能力和两项 production artifact 的前提下，治理从源码到本地交付
与 CI 反馈的工程系统。

上游意图、目标和范围由[专题治理提案](README.md)拥有。正式长期事实仍由以下 Owner 拥有：

- [V1 Blueprint](../../blueprint/README.md)；
- [Design 总览](../../design/README.md)；
- [Production Implementation Architecture](../../design/implementation-architecture.md)；
- POM、`.mvn/`、scripts、build-support、tests 和 workflows 的 executable fact；
- [Conformance](../../conformance/README.md)的 current claim 与 evidence boundary。

本文不是第二份 Implementation Architecture，不授权 implementation、push、publication 或 release。
实施发现需要改变长期 artifact/build 合同时，必须形成精确 Design delta，经 Product Owner 审核后再继续。

## 2. Design Intent

工程系统的价值不是“拥有更多自动化”，而是让准确候选以最少的无价值重复获得与风险相称的证据。

主叙事是：

```text
identify exact source candidate and qualified environment
    -> construct one authoritative build generation
        -> run the cheapest sufficient correctness lane
            -> reuse only same-candidate outputs in wider lanes
                -> independently re-prove boundaries that cannot reuse reactor state
                    -> produce benchmark or local-delivery evidence when requested
                        -> map local contracts to one non-duplicated CI topology
                            -> publish no remote artifact
```

本设计必须同时防止：

1. **过少验证**：为了提速跳过 clean generation、independent consumer、package 或供应链边界；
2. **重复验证**：同一候选在没有新增 claim 的情况下重复构建、测试和打包；
3. **错误复用**：通过 stale `target/` 或旧 SNAPSHOT 获得表面 PASS；
4. **工程过度设计**：用 parent artifact、task framework、容器或更多 workflow 解决少量脚本重复。

## 3. Current-state baseline

### 3.1 Production topology

```text
root soma-reactor                 non-published Maven aggregator
    +-- soma-runtime              production artifact
    +-- soma-processor            production artifact; depends on runtime

soma-examples                    separate non-published downstream reactor
    +-- scheduling
    +-- simulation
    +-- real-time-dispatch

benchmarks                       separate non-published downstream consumer
```

正式合同仍是 exactly two production artifacts。Examples、Benchmarks、tests、build-support 和 generated
application source 都不是第三项 production artifact。

### 3.2 Stable command topology

```text
scripts/check.sh
    -> scripts/qualify.sh with benchmark skipped

scripts/qualify.sh
    -> codegen check
    -> Maven clean package + module tests
    -> cumulative qualification fixtures
    -> install exact SNAPSHOT pair
    -> build Examples and Benchmarks
    -> run reference applications
    -> optional benchmark
    -> local package
    -> packaged independent consumers
    -> source-delivery / dependency / forbidden-surface checks

scripts/benchmark.sh
    -> standalone build/install or caller-requested reuse
    -> fixed workload runners and summary

scripts/package-local.sh
    -> standalone build or caller-requested reuse
    -> binary/source/javadoc/checksum/SBOM/provenance/source bundle
```

### 3.3 CI topology

```text
ci.yml
    pull_request + push(main, develop, release)
    -> check.sh

release-qualification.yml
    workflow_dispatch + push(develop)
    -> qualify.sh with 1M / one fresh run
    -> confirm publication=none
```

因此每次 `develop` push 会同时触发两条大范围重叠的路径。2026-08-13 现场读取的远端最新候选
`b854e577...`上，两条 workflow 都通过；该结果证明当前路径可运行，不证明重复成本合理。

### 3.4 Toolchain and configuration

- Java baseline：Java 8；
- Maven baseline：`[3.9,4.0)`；
- `.mvn/maven.config`：`--no-transfer-progress`；
- `.mvn/jvm.config`：UTF-8 file encoding；
- Maven Wrapper：不存在，也不在本专题候选范围；
- production dependency：runtime 为零；processor 仅依赖 runtime；
- test framework：JUnit Jupiter 5.11.4 test-only；
- workflows：`contents: read`，checkout/setup-java 使用完整 commit SHA。

### 3.5 Current issues to prove, not assume

| Candidate issue | 当前证据 | E0需要回答 |
|---|---|---|
| `check`成本过宽 | 直接进入`qualify`，只跳过Benchmark | 哪些阶段是daily feedback必需，哪些属于完整资格 |
| develop双workflow重复 | 两条workflow同一push并发 | 重叠wall time、阶段和独立claim分别是什么 |
| build reuse可能失真 | 多个`SOMA_*_REUSE_BUILD`开关 | 是否只由同一parent invocation使用，怎样证明candidate identity |
| POM配置重复 | runtime/processor重复插件版本与Java配置 | 哪些为了artifact自包含必须重复，哪些可能漂移 |
| failure定位成本 | 长Shell orchestration依赖原始日志 | 当前失败能否快速映射到稳定phase |

表中内容是调查输入，不是已经成立的缺陷结论。

## 4. 工程系统概念模型

以下概念用于设计和脚本合同，不要求创建 production Java type 或通用 framework：

| Concept | 含义 | Owner / Lifecycle |
|---|---|---|
| `SourceCandidate` | commit、dirty diff、toolchain与输入配置共同确定的候选 | 一次工程运行 |
| `BuildSession` | 对一个SourceCandidate完成的一轮authoritative clean build/generation | 一次orchestration |
| `Phase` | 产生独立结果或失败归因的最小工程阶段 | stable command / internal helper |
| `PhaseResult` | phase身份、状态、原始退出码、关键产物与candidate关系 | 一次运行，不形成数据库 |
| `ReusableOutput` | 经当前BuildSession生成、允许被后续phase消费的产物 | session内有效 |
| `IndependentBoundary` | 必须脱离reactor偶然状态重新证明的consumer/package边界 | qualification |
| `EngineeringClaim` | 构建、生成、正确性、性能或交付的精确声明 | Conformance / report |

这些概念只帮助澄清职责。首轮不创建manifest service、pipeline engine、task API、全局运行数据库或
自定义配置语言。

## 5. 核心不变量

### ENG-01：Two-artifact invariant

工程优化后仍然只有 `soma-runtime` 和 `soma-processor` 两项 production artifact。Root reactor、
Examples、Benchmarks、tests 和 build-support 都保持 non-published。

### ENG-02：Qualified environment invariant

正式 build/qualification 使用 Java 8 与 Maven 3.9。入口必须在昂贵工作前拒绝不兼容环境，不下载
替代 JDK/Maven，也不创建仓库私有依赖缓存。

### ENG-03：Full-regeneration invariant

Processor composition 必须从完整 schema source set clean/full regeneration。Generated source 是
application build output，不提交为 production source，stale/partial generated state 不能被 runtime fallback
掩盖。

### ENG-04：Exact linkage invariant

Independent consumer 必须使用同一候选构建的 exact runtime/processor pair，并保持 classpath 与
processorpath 分离。不能因 Maven 本地仓库中存在同版本旧 SNAPSHOT 而得到假 PASS。

### ENG-05：Evidence/candidate invariant

所有 qualification、Benchmark 和 package claim 必须能回溯到 SourceCandidate、qualified environment 和
实际产物。修改候选后，旧结果不得支持新 claim。

### ENG-06：Safe reuse invariant

Standalone command 默认自给自足。跨phase复用只有在parent orchestration能够证明同一 BuildSession 时
有效；用户设置一个裸 `REUSE_BUILD=1` 不能成为跳过构建的充分依据。

### ENG-07：Risk-matched feedback invariant

日常 check、完整 qualification、Benchmark 和 package 是不同 claim。窄入口不能宣称宽资格；宽入口
也不应无理由重新执行已由同一候选、同一输入证明的阶段。

### ENG-08：Non-publishing invariant

所有本地和远端路径保持 `publication=none`。本专题不创建 deploy、Release、Package、signing、tag 或
正式release声明。

### ENG-09：Supply-chain invariant

Production dependency默认零；JUnit保持test-only；plugin/action版本固定；checksum、SBOM、provenance、
LICENSE/NOTICE与source bundle必须来自最终候选。

### ENG-10：One stable entry per intent

普通维护者只面对四个稳定入口。Internal helper可以按真实phase存在，但不能形成第五个平行入口或
要求用户理解qualification内部拓扑。

### ENG-11：Raw failure preservation

工程编排可以增加phase context，但不能吞掉 Maven、javac、test、application、Benchmark 或 package
的原始非零退出与诊断。

### ENG-12：Repository hygiene invariant

`target/`、generated output、temporary reports和local package不提交；source delivery按显式allowlist选择；
内部project/tests/benchmarks/scripts不因仓库存在而泄漏到用户source bundle。

## 6. Stable command contracts

### 6.1 `scripts/check.sh` — 日常正确性反馈

**Consumer**：本地维护者、Codex、PR与`develop`日常CI。

**Target contract**：用一次 authoritative build 提供当前生产模块、generated surface、长期 cumulative
fixture 和reference application smoke的最小充分正确性反馈。

**必须包含**：

- Java/Maven/environment preflight；
- codegen authoritative source consistency；
- production module compile/tests；
- 与当前Design直接相关的cumulative generated/consumer qualification；
- 三个reference application的有界正确性smoke；
- `git diff --check`及轻量forbidden-surface检查。

**默认不包含**：

- 10K/1M/10M Benchmark；
- sources/javadocs/checksum/SBOM/provenance/source delivery；
- packaged independent consumer；
- remote publication相关动作。

E0可以证明某一现有phase必须保留在daily check，但必须说明它防守的独立risk；不能因为“过去就在
qualify中”自动保留。

### 6.2 `scripts/qualify.sh` — 完整 non-publishing qualification

**Consumer**：里程碑候选、本地完整资格、手工 GitHub qualification。

**Target contract**：在一次可识别 BuildSession 内完成所有当前G1-G10所需的非发布工程证据。

它必须覆盖 `check` 的全部语义，并额外证明：

- clean/full build与regeneration；
- exact SNAPSHOT install与独立downstream Maven consumer；
- configured performance qualification；
- local package与packaged consumers；
- sources/javadocs/checksum/SBOM/provenance/source delivery；
- dependency/artifact leakage与publication=none。

`qualify`不要求把`check.sh`当作黑盒再次执行。它可以通过同一内部phase owner组合出check语义，前提是
两条入口的contract有可执行等价证据。

### 6.3 `scripts/benchmark.sh` — 性能与场景正确性证据

**Consumer**：性能专题、fixed-host ratchet、完整qualification。

**Target contract**：独立运行时完成准确build/install；由同一次`qualify`调用时只复用可证明属于当前
BuildSession的runtime/examples/benchmark classes。

Benchmark必须保留：workload identity、rows、runs、parallelism、implementation、JVM、host、fingerprint、
correctness和machine-readable summary。它不进入普通hosted CI的严格毫秒Gate。

### 6.4 `scripts/package-local.sh` — 本地交付候选

**Consumer**：本地交付资格与完整qualification。

**Target contract**：独立运行时构建当前候选；组合运行时复用同一BuildSession。输出只位于ignored
`target/package`，包含两项binary、sources、javadocs、checksum、SBOM、provenance和source bundle。

它不能deploy、push、tag、sign或创建GitHub Release/Package。

## 7. Candidate identity与安全复用设计

### 7.1 SourceCandidate identity

实现阶段应以最小机制绑定：

- Git HEAD；
- 是否dirty及受治理路径diff fingerprint；
- Java vendor/version与`JAVA_HOME`；
- Maven version；
- relevant command configuration；
- production/generated source fingerprint或等价的可靠候选标识。

Dirty candidate是合法的本地验证对象，但必须与clean commit区分。不能只记录`1.0.0-SNAPSHOT`。

### 7.2 BuildSession

安全复用需要满足：

```text
same SourceCandidate
AND same qualified toolchain
AND parent orchestration created required output
AND expected artifact identity/checksum exists
AND no relevant source/config mutation since build
```

精确实现可以是session marker、private token加target manifest或同进程显式artifact路径。E1应选择最小、
可审查且POSIX Shell可可靠实现的方案。

不接受：

- 仅检查`target/`存在；
- 仅比较artifact版本名；
- 由用户任意设置`REUSE_BUILD=1`；
- 依赖Maven local repository中“最新”同版本SNAPSHOT；
- 为此建立通用build cache service。

### 7.3 Independent boundaries不能复用

以下重复拥有独立证据，不能被普通reactor reuse删除：

- clean/full regeneration与stale cleanup；
- exact processor/runtime version mismatch rejection；
- downstream Maven consumer从坐标解析两项artifact；
- packaged JAR直接javac/processorpath consumer；
- reproducible artifact对照；
- source bundle内容allowlist与forbidden surface检查。

## 8. Phase与诊断合同

建议的稳定phase taxonomy：

```text
environment
codegen
production-build
module-tests
capability-qualification
examples
benchmark
local-package
packaged-consumer
supply-chain
repository-hygiene
```

实现只需为长路径输出简洁边界，例如：

```text
[soma] phase=production-build status=START
[soma] phase=production-build status=PASS durationMs=...
```

失败时必须保留原命令stderr/stdout和非零退出码。首轮不创建JSON日志协议、dashboard、历史数据库或
自定义异常类型。Phase duration只服务before/after和定位，不成为跨机器SLA。

Temporary目录使用`mktemp -d`和严格target pattern清理；signal/interrupt时也必须cleanup。不能把本机
绝对路径、credentials或Maven settings内容写入提交的证据。

## 9. Maven与依赖设计

### 9.1 保持production artifact自包含

不把root non-published reactor改成两项production artifact的外部parent。否则消费者获取runtime或
processor POM时可能额外依赖一个未发布parent，事实上形成第三项交付责任。

因此以下重复可以是**必要重复**：

- artifact identity与版本；
- Java 8 compiler contract；
- Enforcer要求；
- source/javadoc/jar delivery plugin；
- LICENSE/NOTICE资源。

### 9.2 只治理偶然漂移

E0/E2应比较runtime/processor的有效值，识别：

- 应相同但已经或可能漂移的plugin版本；
- processor因`tools.jar`、forked javac与runtime依赖产生的合法差异；
- root reactor、Examples、Benchmarks只对各自non-published consumer有效的配置。

首选顺序：

1. 删除真正无用途配置；
2. 在现有Owner中校正值；
3. 用现有qualification验证必要一致性；
4. 只有真实重复缺陷无法防守时，才提出新的build-support一致性检查。

不为少量XML重复引入parent、BOM、build module、模板生成器或POM预处理。

### 9.3 Dependency与plugin边界

- 不新增production/test dependency和Maven plugin；
- 不重新引入被质量方法实验拒绝的常态化工具；
- plugin version必须显式；
- runtime tree不得出现JUnit；
- processor/runtime packaged JAR不得包含JUnit或predecessor class；
- GitHub Dependabot Alerts继续作为远端漂移反馈，不自动授权version-update PR合并。

## 10. Target CI topology

### 10.1 推荐拓扑

```text
pull_request
    -> ci.yml / check

push(develop, main, release)
    -> ci.yml / check

workflow_dispatch
    -> release-qualification.yml / full non-publishing qualify
```

开发阶段不再让每个`develop` push自动并发运行完整qualification。理由是：

- CI已守护日常correctness；
-完整qualification成本更高，且含package/Benchmark/independent boundaries；
- 当前没有release candidate或publication授权；
- 手工workflow仍能对重要候选提供remote clean-host证据。

若E0证明某类`develop` push必须自动获得完整qualification，应基于独立risk保留，而不是因workflow名称
含“release”或历史触发器而保留。

### 10.2 CI concurrency

普通CI可以按workflow/ref取消被同一分支更新候选取代的旧run；已经开始承担不可替代qualification
或手工证据的run不默认取消。具体`concurrency`配置必须在E3用真实PR/push行为验证。

### 10.3 Cache与权限

- 继续使用`setup-java` Maven cache；
- cache只提高dependency解析，不作为build output truth；
- workflow权限保持`contents: read`；
- third-party action继续完整SHA固定；
- 不上传package artifact、不写Release、不授予token写权限；
- README CI badge继续指向`develop`的`ci.yml`。

## 11. Repository与产物生命周期

| Surface | 职责 | Lifecycle |
|---|---|---|
| `scripts/` | 四个稳定人/CI入口 | 长期 |
| `build-support/` | internal build、linkage、qualification、delivery机制 | 长期，有真实调用者 |
| `tests/` | 跨artifact与generated consumer证据 | 长期，按capability组织 |
| module `src/test` | 单元与不变量证据 | 长期 |
| `benchmarks/` | non-production性能与场景正确性证据 | 长期 |
| `target/` | build/report/package output | 单次候选，ignored |
| generated source | application build output | 单次build，禁止提交production副本 |
| `project/temp/`本专题 | proposal/design/实施推进 | 专题结束删除 |
| `project/conformance/` | 最终claim/evidence boundary | 长期正式记录 |

本专题默认不移动上述目录。只有发现文件的真实Owner与当前路径矛盾，且所有调用/allowlist/reference能够
在一个Slice内完成replacement closure时，才允许迁移。

## 12. Delivery Slice Map

实施获授权后，一次只推进一个Slice。

### E0 — Current-state DAG、成本与事实冻结

**Target**：建立可信before，区分必要重复、偶然重复和未知。

**动作**：

- 冻结commit/dirty candidate、Java、Maven、host和Maven-cache状态；
- 盘点POM、四个stable commands、internal helpers、`SOMA_*`变量、inputs、outputs和cleanup；
- 为`check`、`qualify`、`benchmark`、`package-local`建立phase DAG；
- 在相同候选下记录本地wall time与phase execution count；
- 读取最近远端CI/qualification耗时和触发重叠；
- 分类每个重复phase防守的claim。

**不修改**：POM、scripts、workflow和production code。

**Exit**：能够逐项回答“删掉它会失去什么证据”；形成冻结before与implementation delta。

### E1 — Stable command contract与候选复用

**Target**：让四个入口符合第6节合同，并关闭unsafe reuse与phase诊断问题。

**动作**：

- 调整check/qualify的phase组合；
- 选择并实现最小BuildSession proof；
- standalone command保持自给自足；
- 增加最小phase START/PASS context和可靠cleanup；
- 更新`scripts/README.md`与`build-support/README.md`；
- 删除被替代的flag、dead path或重复orchestration。

**Evidence**：正向同session reuse、stale/foreign candidate拒绝、失败退出码、cleanup、check/qualify
contract与现有correctness等价。

**Exit**：四入口职责唯一；没有第五个入口、fallback或裸reuse bypass。

### E2 — Maven、artifact与供应链一致性

**Target**：关闭POM/config漂移，同时保持artifact自包含。

**动作**：

- 按第9节分类配置；
- 删除无consumer配置，校正真实漂移；
- 只在有Evidence时增加最小一致性defense；
- 验证effective Java/plugin/dependency、LICENSE/NOTICE、manifest、sources/javadocs；
- 重新证明runtime/processor linkage与无第三artifact。

**Evidence**：clean build、dependency tree、artifact inventory、jar content、reproducibility、package smoke。

**Exit**：必要重复有理由，偶然重复已关闭；无parent/BOM/build module扩张。

### E3 — CI反馈拓扑

**Target**：使PR、push与手工qualification各承担唯一风险。

**动作**：

- 按E0证据调整workflow触发；
- 必要时为普通CI增加同ref cancellation；
- 保持cache、最小权限、SHA pin和publication=none；
- 推送经授权候选，观察真实remote check和手工qualification；
- 比较before/after runner数量、wall time与失败可诊断性。

**Evidence**：一个PR/push的准确触发矩阵、green CI、手工non-publishing qualification、权限与provenance
readback。

**Exit**：同一事件没有无价值双跑；完整资格仍可按需可靠重放。

### E4 — Final engineering qualification与Temporary closure

**Target**：证明最终工程系统完整、自洽、比before更高效，并关闭专题。

**动作**：

- 从最终候选运行风险相称的clean/full资格；
- 重放10K/1M fixed-host ratchet，确认工程优化没有改变运行时性能事实；
- 审查全部diff、稳定入口、internal helper、POM、workflow和delivery allowlist；
- 记录before/after和未证明边界；
- 将长期build合同delta晋升到Implementation Architecture（如有）；
- 将current evidence晋升到Conformance；
- 更新项目入口，删除本Temporary。

**Exit**：Definition of Done全部闭合，形成干净检查点；不外推release readiness。

## 13. Verification matrix

| Claim | Evidence |
|---|---|
| 四入口职责唯一 | command contract、call graph、README、无额外stable entry |
| Check保持正确性 | module tests、cumulative fixtures、generated consumer、三个Example smoke |
| Qualify覆盖完整 | check语义等价 + benchmark + package + independent consumer + supply chain |
| Reuse不读取stale candidate | same-session positive、source/config mutation negative、foreign/stale target negative |
| Full regeneration | clean schema generation、stale cleanup、manifest/fullSourceSet、regeneration fixture |
| Exact artifact linkage | same-version pair、mismatch rejection、Maven downstream与direct javac consumer |
| Maven配置一致 | effective Java/plugin/version/dependency对照，processor合法差异说明 |
| Two-artifact/no leakage | artifact inventory、dependency tree、JAR content、source bundle allowlist |
| Benchmark未退化 | fixed-host baseline comparator、correctness/fingerprint一致 |
| CI低重复 | event/trigger矩阵、remote run数量与wall time before/after |
| Non-publishing | workflow permission、provenance `publication=none`、无deploy/release surface |
| Temporary closure |正式Owner更新、链接检查、Temporary删除、clean diff/checkpoint |

## 14. 效率测量设计

Before/after必须使用相同候选或明确说明只变更工程系统，且保持：

- 同一Java/Maven/host；
- 相同Maven dependency cache状态（cold与warm不能混比）；
- 相同Benchmark rows/runs/parallelism；
- 相同workflow event与runner class；
- 相同functional/package claims。

最小指标：

| Metric | 目的 |
|---|---|
| stable command wall time | 维护者等待成本 |
| Maven build/install/package invocation count | 重复构建数量 |
| module test execution count | 同一候选重复测试数量 |
| Examples/Benchmark/package execution count | 宽路径重复程度 |
| GitHub runs per event | hosted runner重复 |
| remote total wall time | 反馈延迟，不等于billable精确成本 |
| first actionable failure phase | 可诊断性 |
| artifact/fingerprint equality | 证明优化未换候选或缩水claim |

不建立跨机器固定秒数Gate，也不以“Shell行数减少”作为治理成功指标。

## 15. Failure与恢复边界

- environment mismatch：任何构建前失败；
- production build/test失败：停止，不使用旧target继续下游；
- reuse proof失败：standalone入口重新构建，组合资格应失败而不是静默fallback到不明candidate；
- Benchmark correctness/fingerprint失败：不得产生性能PASS；
- package/SBOM/provenance失败：不得产生local delivery claim；
- workflow失败：保留远端run与准确phase，不用反复rerun代替修复；
- cleanup失败：仅删除经过严格pattern解析的本专题temporary target，不扩大删除范围；
- implementation需要Design、dependency、plugin、权限或release扩张：停止等待Product Owner。

如果一次调整没有减少DoD差距或产生新Evidence，应停止继续拆脚本/加检查，回读E0事实和当前diff。

## 16. 过度设计控制

本专题不创建：

- 第三production或parent artifact；
- 通用build pipeline/task graph framework；
- 新CLI、Makefile、Gradle、Docker或Maven Wrapper；
- 第五个stable command；
- JSON run manifest schema、历史数据库、dashboard或自定义日志库；
- 新静态分析/coverage/dependency工具；
- CI性能毫秒Gate或自动baseline更新；
- 为future release/multi-JDK预留的profile、module或workflow；
- 只为消除XML/Shell文本重复的generator/template层。

判断某项抽象是否值得引入，只问三个问题：

1. 它是否关闭一个已证明且会重复出现的独立工程风险；
2. 现有surface为什么不能承载；
3. 删除的复杂度和长期成本是否大于新增surface。

任一问题无法回答，就不新增。

## 17. Rejected alternatives

| 方案 | 本专题拒绝理由 |
|---|---|
| 把root reactor改成published parent | 使两artifact POM依赖第三交付责任，违反two-artifact边界 |
| 为四命令建立通用task framework | 规模不足以证明独立capability，增加调试层 |
| 每个develop push运行check+full qualify | 当前存在大面积重复，且开发阶段没有每提交release资格需求 |
| 完全删除clean/independent重复 | 会丢失stale generation、artifact解析和package consumer证据 |
| 引入Maven Wrapper/Docker固定环境 | 当前正式合同是Java 8+Maven 3.9范围，机器已有全局工具链 |
| 将Benchmark变成hosted CI严格Gate | runner噪声和硬件变化无法支持固定毫秒合同 |
| 顺便整理全部目录和README | 与反馈效率没有直接因果，扩大replacement风险 |
| 引入PMD/JaCoCo/Sonar | 先前方法实验未证明日常净收益，本专题不重开该裁决 |

## 18. Quality Claims

### 功能质量

工程调整不改变SOMA public behavior；最终check/qualify仍能发现真实功能失败，reference applications与
independent consumers保持PASS。

### 代码质量

脚本主流程能够按phase阅读；没有dead flag、重复orchestrator、隐藏fallback或过度抽象；原始tool错误保留。

### 架构质量

two-artifact、Java8/full-regeneration、classpath/processorpath、Owner与delivery边界符合正式Design；没有
第二套build truth。

### 工程质量

clean、standalone、composed、local和remote路径可重放；candidate identity、artifact、Benchmark与package
证据一致；反馈成本有before/after。

## 19. Candidate Design readiness

当前设计已经明确：

- [x] Target、Scope、Exclusions与Authority；
- [x] current production/build/CI拓扑；
- [x] 四个stable command的目标合同；
- [x] candidate identity与safe reuse不变量；
- [x] Maven必要重复与偶然重复的裁决原则；
- [x] 推荐CI触发拓扑；
- [x] E0-E4 Slice Map与单一Active Slice规则；
- [x] Evidence、before/after、failure和Stop Rule；
- [x] 过度设计与release boundary；
- [x] Product Owner审核本设计；
- [x] Baseline Freeze；
- [x] Implementation Authorization。

## 20. 当前结论

本Candidate Design已经足以指导E0-E4实施，不需要在实施前继续扩张工程抽象。当前仍需Product Owner
审核以下整体裁决：

1. `check`收敛为日常正确性路径，不再默认完成完整package/supply-chain资格；
2. `qualify`是唯一完整non-publishing工程资格入口；
3. `develop`自动运行CI check，完整qualification改为手工workflow；
4. build reuse必须绑定同一BuildSession，不接受裸`REUSE_BUILD=1`；
5. 保留两production POM必要重复，不引入第三parent artifact；
6. 本专题不扩张工具、依赖、Java版本、目录或release能力。

上述六项整体裁决已于2026-08-13获Product Owner批准。本文以`develop@6af2626`
为实施基线冻结，E0已完成，E1成为唯一active slice。授权不包含新依赖、新plugin、
第三production artifact、远端artifact发布、签名或正式release声明。

## 21. E0 Baseline Freeze

| 事实 | 冻结值 |
|---|---|
| Git baseline | `develop@6af2626ba9c81e1c21da4e1c4c2e70e031335d9b` |
| Java | Amazon Corretto `1.8.0_502-b07` / arm64 |
| Maven | Apache Maven `3.9.16` |
| Host | macOS `26.6.1` / Apple M5 Pro / arm64 |
| Local `check` before | warm dependency cache，`34.85 s` wall，`137.77 s` user，`9.78 s` sys |
| Remote push before | `b854e577` 上 CI `125 s`，full qualification `137 s`，同一`develop` push并发双跑 |
| Stable commands | `check`、`qualify`、`benchmark`、`package-local` |
| Production topology | exactly `soma-runtime` + `soma-processor` |

E0证明了三项需要实施的delta：

1. `check`当前仍运行benchmark build、local package、packaged consumer与supply-chain，
   超出日常正确性合同；
2. 组合路径使用多个可由调用者裸设置的`*_REUSE_BUILD=1`，不能证明与当前
   source candidate、工具链和artifact同一；
3. `develop` push对同一候选并发运行daily check与full qualification，守护范围大量重叠。

E0同时确认，clean/full regeneration、same-version mismatch、downstream Maven consumer、
packaged direct consumer、artifact reproducibility和source-delivery allowlist各自守护独立claim，
不能为了减少执行次数而删除。

## 22. E1 Qualification

E1已完成：

- `check.sh`现在只组合environment、codegen、production tests、cumulative generated
  consumer、Examples tests/smoke和repository hygiene；
- `qualify.sh`保持唯一完整non-publishing qualification语义；
- `benchmark.sh`和`package-local.sh`独立运行时自行构建，组合运行时只接受
  `target/`内通过当前source candidate、Java/Maven identity和artifact SHA-256验证的
  BuildSession manifest；
- 全部裸`*_REUSE_BUILD=1`路径已退出；
- 长路径输出`environment`到`repository-hygiene`的START/PASS/FAIL边界，保留原命令输出和
  exit code。

E1证据：

| Evidence | Result |
|---|---|
| BuildSession self-test | current candidate PASS；source mutation和artifact mutation均被拒绝 |
| Stale current-session negative | 修改受治理source后，旧manifest稳定拒绝 |
| Daily check after | `22.97 s` wall，`81.62 s` user，`6.18 s` sys |
| Daily check before | `34.85 s` wall，`137.77 s` user，`9.78 s` sys |
| Wall-time delta | `-34.1%` |
| Functional breadth | runtime 91 tests、processor 34 tests、Examples 13 tests、cumulative consumer与三个smoke PASS |

该改善没有删除full qualification的package、supply-chain、packaged consumer或Benchmark claim；
它们只从daily check迁回唯一`qualify`入口。E1因此关闭，E2成为唯一active slice。
