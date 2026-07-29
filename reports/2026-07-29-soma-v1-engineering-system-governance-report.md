# SOMA Java V1 工程体系治理报告

类型：Report / Engineering Governance

状态：已完成

Owner：SOMA Java engineering system

受众：SOMA maintainer、开发者、Gate与release owner

适用版本：`0.2.0-SNAPSHOT`

输入事实源：当前仓库、脚本/CI diff、JDK/Maven实测、component/application
artifact、Conformance与Gate状态

事实范围：JDK、Maven lifecycle/local repository、脚本编排、Fast/Full/
Qualification、CI、测试/Benchmark/Example evidence、异常处置与scope
non-regression

非事实范围：重新定义public/schema/runtime语义、自动授权release或替代重型
qualification

最后审查日期：2026-07-29

## 1. 治理意图与最终目标

SOMA的工程体系不是“把所有脚本都跑一遍”，而是把产品目标持续投影为快速、
可信、可定位、可重放的证据：

```text
Blueprint / Design / Capability
        ↓
compiler / runtime / DataFlow / examples
        ↓
unit-contract / generated-public consumer / component / application
        ↓
Fast → Full → Qualification
        ↓
Conformance / Support Matrix / Release
```

最终目标是：

- Design和Capability决定实现与证据，当前代码或测试数量不反向降低目标；
- 日常开发能快速发现高概率回归，跨模块收口能完整验证，规模/release只在明确
  授权时执行；
- 同一source状态只构建一次，后续consumer共享Maven artifact或compiled classes；
- 并行只用于独立、CPU/内存允许的确定性Gate，失败必须fail-closed；
- 每个stage有`start/pass/fail/duration`，异常信号在首次出现时即可定位；
- CI、本地、package、security、benchmark和Example使用同一工程叙事，但各自保留
  必要隔离和evidence Owner；
- 工程治理不制造新module、production abstraction、parallel Owner、temporary
  public API或永久专题文档。

## 2. 触发问题与根因

治理前的`./scripts/check.sh`把日常反馈、完整contract、application和部分
qualification机械串联。本机warm run接近9分钟；已观察的旧CI run
`30369239506`约840.6秒。审查还发现49次Maven build调用和10次Maven version
启动；主要慢项包括逐class Public API `javap`约206秒、generated breadth约
127.7秒、codegen约80.3秒、keyed约70.6秒和reference applications约64.2秒。

根因不是“Java/Maven天然很慢”，而是：

- 多个check为相同reactor重复`clean/package/install/test-compile`；
- ordinary external consumer各自创建、预热或复制隔离local repository；
- `javap`按约450个class逐进程启动，classfile major也逐class启动JVM工具；
- application、benchmark和consumer没有显式“一次准备、多项消费”状态；
- 旧parallel wrapper丢失子进程失败码，prepare function在shell条件上下文中可能
  绕过`set -e`；
- 脚本没有stage耗时，长阶段和重复阶段不能在首次异常时被识别；
- 长任务执行曾用固定间隔轮询替代异常诊断，放大了上述工程问题。

## 3. Java与Maven权威决策

### 3.1 单一JDK authority

唯一compiler/validation authority迁移为Homebrew管理的Amazon Corretto 8：

```text
Corretto 8.502.07.1
java/runtime 1.8.0_502-b07
javac 1.8.0_502
```

Root Maven Enforcer验证Java 8与`Amazon.com Inc.` vendor；
`scripts/lib/supported-jdk.sh`验证java、javac、javap和精确build。CI使用
`actions/setup-java`的`corretto` distribution，Linux安装脚本固定官方版本与
SHA-256。Zulu positive path已退出；迁移时保留一次negative probe，证明旧vendor
会被toolchain和Maven双重拒绝。

### 3.2 Maven local repository

ordinary build、check和external consumer统一使用Maven标准用户local
repository。它是Maven Resolver管理的缓存和已安装artifact仓库，不由项目脚本
直接读取、复制或模拟其目录布局。

并发consumer使用Resolver named lock：

```text
-Daether.syncContext.named.factory=file-lock
-Daether.syncContext.named.nameMapper=file-gav
```

Root reactor只执行一次`install -DskipTests`，随后dense/keyed/access/child/
breadth、generic external consumer和三个Example通过正常Maven dependency
resolution消费相同artifact。

隔离repository只保留在其本身就是oracle的边界：

- `package-smoke.sh`：两个全新repository证明release-shaped package与
  reproducibility；
- `security-release-scan.sh`：隔离dependency/SBOM/security evidence。

这些隔离不进入Fast/Full日常路径。

## 4. 验证拓扑

| 层级 | 用途 | 当前入口 | 关键边界 |
|---|---|---|---|
| Fast | 日常反馈 | `./scripts/check.sh fast` | exact toolchain、docs、baseline architecture、reactor verify、diff |
| Full | 跨模块、PR、专题收口 | `./scripts/check.sh full` | Fast + public/compiler/generated/runtime/DataFlow/external consumer/Example correctness/component benchmark |
| Qualification | 规模、release、安全、公开claim准备 | 独立显式脚本/手工workflow | runtime-scale、application Full、package reproducibility、security、release |

`check.sh`默认`full`以保持旧入口的安全语义。默认并行度是4，实际使用
`min(SOMA_CHECK_JOBS, availableProcessors)`。只并行compile/scalar/generated/
integration等独立stage；Access/DataFlow performance保持串行，避免CPU、cache、
heap和GC互相污染。

每个并行batch等待所有已启动stage，逐项输出log；任一失败立即阻止下一batch。
Full内置negative orchestrator probe，专门证明失败子进程不会被报告为passed。
prepare stage显式返回真实Maven退出码。

## 5. 消除重复工作的实现

- reactor verify后设置`SOMA_REACTOR_PREPARED`，runtime/naming contract复用产物并
  检查required class/JAR存在；
- external install后设置`SOMA_EXTERNAL_ARTIFACTS_PREPARED`，全部consumer共享；
- reactor verify形成benchmark class后同时设置`SOMA_BENCHMARKS_PREPARED`，
  contract、reference、smoke与component performance复用；
- code-size在临时source copy中执行clean compile，Full以主checkout sentinel证明
  prepared classes、evidence和orchestrator log未被删除，旧的补偿性benchmark
  rebuild已经退出；
- Public API把全部type交给一次批量`javap`并重新绑定golden header；
- generated breadth同样批量`javap`，classfile major直接读取class header
  `0x0034`；
- 三个application performance入口一次准备external artifacts和benchmark
  comparator，Fast/Scale/Soak共享；
- Maven plugin使用完整坐标和root版本属性，不依赖易漂移prefix解析。

`check-build-governance.sh`防止ordinary check重新引入`maven.repo.local`、未固定
dependency plugin或缺失Resolver lock。

## 6. CI与异常协议

CI按实际diff选择：

- 纯Markdown变更：documentation check；
- 空diff、无法解析base、代码/配置/脚本变更：fail-closed到Full；
- Maven dependency cache由`setup-java`管理；
- 同workflow/同SHA的重叠run通过concurrency取消；
- final workspace必须`git diff --exit-code`。

本次把异常信号固化为执行纪律：

- stage显著慢于其职责、同一build重复出现、输出长期不变化、环境identity不一致、
  baseline identity先于performance comparison失败，都必须立即诊断；
- 同一失败只有在输入、假设、实现或证据目标已改变后定向重试一次；
- 连续两次没有新状态或证据就停止轮询并检查process/log/资源；
- 禁止固定间隔sleep轮询和用重复执行掩盖failure；
- performance与qualification发现异常样本时先形成methodology finding，不自动
  放宽阈值。

Corretto application校准首先暴露了旧baseline schema identity漂移。停止后用旧
Zulu对同一当前source做targeted build，结果与Corretto逐字节一致，证明这是旧
evidence漂移而非JDK不确定性；随后才建立新的5-fork baseline并做3-fork回放。

首次closeout Full还在47秒处暴露了嵌套shell函数污染外层`stage_name`、导致
telemetry名称为空。执行立即终止；`run_stage`改用独立变量后，orchestrator名称、
结果和耗时在重试中完整保留。该事件没有被“整体仍在运行”掩盖，也没有触发无输入
变化的重复执行。

## 7. 当前验证结果

- exact Corretto toolchain：passed；
- Zulu toolchain与Maven vendor negative probe：按预期failed；
- Corretto root reactor verify：passed；
- Access Corretto component baseline v1：passed，旧ceiling未放宽；
- DataFlow Corretto component baseline v3：passed，旧ceiling未放宽；
- 九个application profile：每profile 5-fork校准，普通3-fork Full全部passed；
- baseline architecture：2个component、9个application、0个public claim；
- shell syntax、workflow YAML和`git diff --check`：passed；
- `./scripts/check.sh fast`：passed，warm local 10秒（jobs=4）；
- 上一轮Corretto authority收口的`./scripts/check.sh full`：passed，warm local
  149秒（jobs=4）；
- 本次code-size/Cloud收尾的Full：运行147秒，在倒数第二个
  `dataflow-performance`阶段因单个fork的`tail.window`抖动停止；此前所有阶段
  passed，最后尚未进入的`diff`已单独passed；
- 同一HEAD、JDK和机器上，历史三fork `tail.window.p90`为394–404微秒；失败样本的
  前两fork为402/414微秒，第三fork为685微秒，超过674微秒ceiling约1.6%，但
  checksum、allocation、median、GC及其他全部规则passed；
- 改变证据目标后只做一次定向复验，三fork为401–416微秒并passed。没有重跑Full、
  放宽baseline或修改统计语义来掩盖异常。

本次Full中，Public API为1秒、codegen admission为50秒、generated keyed为30秒、
reference applications为25秒、隔离code-size clean oracle为10秒；所有stage均输出
非空名称、状态与duration。当前结果证明本次实现与功能证据闭合，但这次交互式本机
Full没有形成单次全绿的性能证据；该边界不外推为新的performance或release claim。

## 8. Gate与未完成evidence

JDK authority变化不会自动继承旧vendor的support/performance evidence：

- G0–G4在当前Corretto本机Full闭合；
- G5保持`blocked`：十lane runtime-scale尚未在Corretto重跑；
- G6 selected `private-github-source`保持`blocked`：当前commit尚无Corretto Linux
  Full、clean package/security与manual release qualification；
- public GitHub与Maven Central保持`not-selected`；
- Codex Cloud成为有界development candidate，但fresh-container setup/Fast/Full
  尚未重放，保持`qualification-blocked`。

旧Zulu runtime-scale和Linux release evidence仍是历史candidate事实，但不进入
当前passed声明。后续重验不得缩小Small/Medium、single/double100M、String、
Expansion、Delivery或Soak目标。

## 9. Surface delta与scope non-regression

本次没有改变annotation、generated public API、runtime/DataFlow public语义、
Schema、ownership、lifecycle、Result Delivery或第三方production dependency。
production-shape Java变化仅限qualification model的JDK identity和Benchmark/
Example test metrics文字。

新增长期surface只有：

- `scripts/lib/supported-jdk.sh`：单一JDK identity Owner；
- `scripts/lib/external-evidence.sh`：标准Maven external evidence Owner；
- `scripts/setup/install-corretto8-linux-x64.sh`：精确Linux toolchain bootstrap；
- 本报告。

Zulu installer与11份Zulu current baseline退出；Corretto 2份component和9份
application baseline成为唯一current Owner，旧事实由Git保存。无新module、无新
production type、无parallel Owner、无migration-only checker、无test-only
bypass、无未退役Temporary。

最终收尾没有新增脚本或长期Owner：`check-scan-code-size.sh`在既有Gate内取得隔离
build output，`check.sh`删除旧的`prepare-benchmarks`补偿路径；
`setup-codex-cloud.sh`删除第二Maven cache、逐fixture重复预取和release-only
OSV准备，收敛到exact toolchain、标准Maven cache、一个代表fixture与一个pinned
governance plugin。Cloud readiness仍由实际fresh-container evidence裁决。

## 10. 结论

本次治理把“9分钟的大脚本”改造成由产品Capability驱动、具备分层反馈、标准Maven
缓存、可控并行、明确耗时和异常退出条件的工程系统。性能提速没有删除证据，
package/security/runtime-scale仍保留其独立oracle。

本专题工程治理实现已经完成；本次Full唯一失败被定位为不可重复的单fork性能抖动，
定向复验和剩余diff阶段通过，原始失败仍如实保留。G5/G6的Corretto重型evidence
作为明确Conformance差距留给独立、人工监管任务；Cloud由`CF-017`承担一次有界
验收，不以本次治理名义暗中执行、反复轮询或降低目标。
