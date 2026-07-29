# SOMA V1 Release 治理交接

类型：Temporary

状态：active（等待下一轮 V1 release 治理统一收口）

Owner：SOMA Java 当前治理收口与 V1 release transition

事实范围：本 Codex 线程确认的治理意图、关键裁决、当前 production/evidence
candidate、计划对照、审计结论、未闭合事项和下一轮治理入口

非事实范围：重新定义正式 Blueprint/Design、授权 release、扩大支持矩阵、声明
public/Maven readiness，或替代 Conformance、Report 和可执行 Gate 的当前事实

最后审查日期：2026-07-29

## 1. 临时性质与使用方式

本文件是一次性治理交接，不是新的 Design、Conformance、Roadmap 或 release
事实源。它保留本 Codex 长线程中下一轮治理仍需使用、但尚未全部原子收口的上下文，
避免新线程只看到当前代码而丢失目标、裁决理由和审计发现。

下一轮 V1 release 治理必须：

1. 先以当前 HEAD、正式 Owner、代码、Gate 和 artifact 重新核实本文件中的
   drift-prone 状态；
2. 将仍然成立的稳定事实原子固化到唯一 Blueprint/Design/Engineering/
   Conformance/Report Owner；
3. 完成这里登记的当前治理尾项和 selected release profile 的 Definition of Done；
4. 删除整个 `docs/temp/soma-v1-release-transition-handoff/`，不得归档、改名为
   historical 或长期作为 current 入口。

## 2. 本线程的治理起点

本轮不是把 SOMA 重新定义为数据库，也不是补齐 SQL 功能。它以现代 SQL、
内存列式系统和编译式执行引擎为研究输入，重新审视 SOMA 的逻辑类型、约束、
Transformation、执行 lowering、物理结构、中间结果、并行与规模承诺，同时保持
SOMA 原有产品定义和核心抽象。

治理前确认的主要问题是：

- 原 100M 挑战不能只看单表，必须考虑两个同时驻留的 100M Table；同时不能让
  Small/Medium 为超大规模设计承担不可解释固定税；
- 前期 Research 对 Data Representation 的结论不能直接进入 production，还需结合
  SOMA Java 的中间结果、计算流程、JIT/cache/memory bandwidth/GC 与并行模型做
  独立技术验证；
- 实际 payload 与 Metadata 必须分离；完整 Metadata hierarchy 及
  mutable-before-freeze Plan 已成为此前治理的正式基础；
- String V1 继续保存 caller `String` reference，不引入 dictionary、character
  arena 或 intern backend；实际存储引用不等于支持任意 Java object；
- 普通 object graph、`List`、`Map` 和任意 DTO 不进入 live schema field；应用对象
  通过 SOMA 中的 stable ID 与 application sidecar/registry 关联；
- Eager Detached 保持完整、原子、易推理的默认 Result Delivery；最终只选择同步、
  read-only、callback-scoped delivery 作为惰性试点，不引入普通 `Iterator`、
  closeable cursor、Generator、Publisher 或 async push；
- Storage Segment 与 parallel work unit 必须分离；一个 Segment 也能按成本拆成
  多个 morsel，但不建立两套 executor/并行框架；
- runtime 可以理解为一组封闭 Capability，由接口、泛型和编译期/generated binding
  隔离实现，以便未来局部替换；V1 不开放 application strategy SPI；
- 代码、测试、benchmark、脚本和文档都应围绕核心抽象、Capability 与产品叙事，
  控制规模并退出治理批次、迁移入口和无 Owner 的噪声。

## 3. 关键认知模型

### 3.1 产品级关系

本线程确认的总体关系是：

```text
产品叙事
  -> 核心抽象：State / Owner + Capability + Plan / Lifecycle
  -> 每项 Capability 的三层展开：
       Logical semantics
         -> Java carrier + generated type-safe capability
         -> JVM / OS / CPU-aware physical strategy
  -> Evidence
  -> Conformance / Product Claim
```

四部分不处于同一抽象层：

- 产品叙事回答 SOMA 为什么存在、为谁解决什么问题；
- 核心抽象回答系统由什么长期概念构成、谁拥有事实与生命周期；
- 三层模型回答每项能力怎样从易用、类型安全的逻辑契约降低为 Java 8 和物理执行；
- Evidence 是横切验真层，不是运行时中的第四类业务抽象。

### 3.2 每个抽象自己的叙事

产品叙事不是唯一叙事。每个核心抽象都必须拥有自己的叙事和叙事展开，至少回答：

1. `Why`：为什么需要这个抽象，它解决什么系统矛盾；
2. `Owns`：它拥有哪些事实、能力和不变量；
3. `Not`：它明确不是什么，不能冒充哪些相邻抽象；
4. `Relationships`：它与哪些抽象组合，依赖方向是什么；
5. `Lifecycle`：怎样创建、freeze、bind、使用、失败和释放；
6. `Lowering`：其 logical semantics、Java carrier/generated capability 与
   physical strategy 分别是什么；
7. `Resource / Failure`：资源和失败后可信状态由谁闭合；
8. `Evidence`：哪些 contract、differential、consumer、benchmark 或 qualification
   才能证明它成立；
9. `Evolution`：未来怎样局部替换实现而不破坏语义与叙事。

当前正式文档已经建立抽象层次、唯一 Owner、事实/非事实范围、canonical terminology
和大量能力契约，但尚未把“每个抽象必须有完整叙事闭环”明确为共同治理规则。
下一轮应在现有设计宪法和 Design 导航中做窄修正，并复核既有 Owner 的开篇叙事；
不得为每个抽象另建一套平行文档。

## 4. 治理前 Research 与计划

治理前 Research 将方向分为三类。

### 4.1 直接吸收的设计原则

- logical type、Java carrier 与 physical representation 分层；
- 封闭 logical type catalog 和类型允许操作矩阵；
- required/optional/default、Key/Unique/Exact 与编译期合法性保持克制约束；
- logical/physical plan 不变量、pipeline/barrier、bounded intermediate；
- Explain 与物理选择可观察；
- Small/Medium、单表 1M、双表 1M + 1M 成为 V1 qualification；
- 10M/100M 降为 research/stress，不阻塞 V1。

### 4.2 明确不进入 V1

- SQL parser、DDL/DML/DQL 兼容层、事务、持久化和分布式执行；
- foreign key、table reference、cascade、trigger、跨表 CHECK；
- 任意 Java object/DTO/Collection graph；
- `SomaInt`、`SomaLong` 等 wrapper/type alias；
- 普通 Iterator/Stream、开放 runtime SPI、Java 16 Vector API；
- String dictionary/arena 和全套磁盘数据库索引。

### 4.3 必须先独立验证

- logical expression carrier 与 closed/fused loop；
- chunk/vector/selection representation；
- Segment min/max/zone map；
- 低基数 Bitmap；
- primitive Join min/max/Bloom dynamic filter；
- 中间结果与 barrier 前 materialization；
- 单 Segment 多 morsel、cache、worker-local state 和 merge；
- Decimal 仅在决定考虑纳入 V1 时触发验证。

执行流程按“Temporary 与独立 Lab → 高决策价值 TV → D1–D8 Design 裁决 →
production cutover → evidence/examples/Temporary 退出”推进。独立 Lab 只服务本专题，
治理完成后必须删除。

## 5. D1–D8 最终裁决与 production 结果

用户已明确授权 D1–D8 按推荐裁决更新正式 Design、Conformance、相关 Gate 并进入
production 实施。

| 决策 | 最终裁决与当前实现 |
|---|---|
| D1 logical type | enum/date/time/instant 使用 type-specific expression facade；内部共享 primitive carrier；不增加 wrapper/alias |
| D2 numeric kernel | 只对 required long column + constant arithmetic/comparison common chain 启用 closed whole-loop kernel；reference expression graph 保留 oracle/fallback |
| D3 Segment statistics | 拒绝 V1 mutable Segment min/max/zone-map Capability；没有 production type、配置或平行 Owner |
| D4 Bitmap | 只在公式许可的单字段 primitive exact equality intersection 中启用 maintained bitmap；exact hash/full equality 与 links 保持 authoritative/fallback |
| D5 Join filter | primitive 单分量 Join 可选 Invocation-local min/max 或 Bloom；最终 hash/full equality 不变；String 和 composite Key 禁用 |
| D6 String | 长度只属于 create 前可调 resource estimate 和 evidence profile，不是 Schema 约束或 mutation admission |
| D7 Scale | required envelope 为 Small、Medium、单 1M、双 1M、String、Expansion、Delivery、Soak；10M/100M 为 non-blocking research/stress |
| D8 产品边界 | 保持 SOMA 产品定义与核心抽象；不增加 SQL/DDL/DML/DQL、foreign key、reference 或通用数据库 API |

补充结果：

- TIME direct/Batch/replace/Mutator/Delta/flattened value 写入统一校验
  `[0, 86_400_000_000_000)`；
- Date/Instant arithmetic checked；Time arithmetic 为 24 小时 modular；
- protocol 切换到 generated/runtime v12、transformation v4、kernel v5、
  Candidate/relation formula v2，无旧 logical API 双轨；
- `CandidateLongEqualityAccess` 是 generated protocol，不进入 application data model；
- 三个 reference application 已按 canonical generated API、Metadata/Plan、Group、
  DataFlow 和 detached result 审计，不需要装饰性 production 改写；
- 测试与 benchmark 按 Capability、reference differential、external consumer、
  reference application 和 qualification 组织，没有保留以 TV 编号命名的 canonical
  taxonomy；
- 原逻辑/执行 Temporary 与独立验证 Lab 已删除。

## 6. 当前 Evidence

### 6.1 Runtime-scale qualification

当前本机 evidence 环境：

- Amazon Corretto `1.8.0_502-b07` full JDK 8；
- Maven Wrapper `3.9.16`；
- macOS `26.5.2` / Darwin `25.5.0`、`aarch64`、Apple M5 Pro、48 GiB；
- G1 GC；每条 lane 独立 JVM 和显式 heap/timeout。

当前 artifact：

- qualification ID：
  `runtime-scale-qualification-20260729-d90e8499d51f`；
- executable product/evidence content SHA-256：
  `d90e8499d51f7477db3959033895853e223bd692794e25eb8bdf234492e3c2ba`；
- combined artifact SHA-256：
  `4bdc5b51407aaec838af0a95de81249c717e8beab9fea78e1cbf4db8a4abbbef`；
- strict schema v2 SHA-256：
  `eeb8eb1e0f5beda9b3970746b796eb0c5e58a7b8ccd5a9f7cc21dbafc98b4ce2`。

八条 required lane 均为 applicable/passed：

- Small/Fast；
- Medium，覆盖 single Segment sequential 与 multi-morsel parallel；
- 实际驻留单表 1M；
- 两个同时驻留的 1M numeric roots；
- 两个同时驻留的 1M String 角色 Table；
- Expansion；
- Eager/callback Delivery；
- lifecycle/GC Soak。

String evidence 同时声明长度、value/object cardinality、sharing、field role、
live Table count、mutation/clear/release/GC，并区分 SOMA structural bytes、
SOMA-retained reachable String model 和 JVM observed heap。String 长度是 workload
profile，不是字段限制。

### 6.2 Component 与合同证据

- public/generated golden、negative compile、external Maven consumer；
- runtime/DataFlow Capability contract；
- sequential/reference differential；
- DataFlow component Corretto baseline v4；
- Access component baseline；
- 三个 reference application correctness 与环境限定 baseline；
- runtime-scale strict validator 的 false-claim、shrunken-1M、extra/missing/
  duplicate lane negative paths；
- Java 8 classfile major 52。

DataFlow v4 只更新 compiled authoring plan identity checksum
`1064084655879852845`；其余 execution checksum 以及 allocation、timing、tail 和
GC envelope 未放宽。

## 7. 相对治理前计划的审计

### 7.1 已达到

- 产品叙事未被数据库分类取代；
- `State / Owner + Capability + Plan / Lifecycle` 已进入正式 Design；
- logical → Java carrier/generated capability → physical strategy 已进入正式
  Design；
- logical type、closed kernel、Bitmap、Join filter、parallel、String 与 scale
  均有明确裁决和 production/evidence；
- 未通过或不值得进入 V1 的方向没有伪装成 roadmap；
- Small/Medium、单 1M、双 1M 和 String 已形成受环境/profile约束的证据；
- Example、测试、benchmark、文档与代码继续围绕同一 Capability 和产品边界；
- 没有 SQL/database 功能膨胀、普通 Iterator、任意 object storage、旧 protocol
  双轨、active 旧专题或独立 Lab 残留。

### 7.2 不是缩水的裁决

- 100M 从 V1 guarantee 调整为 research/stress 是用户明确批准的产品目标校准；
- Segment statistics 被 D3 明确否决；
- generic value constraint 未形成通用 V1 capability；当前只保留
  required/optional/default 和 TIME 等逻辑类型内在约束；
- Decimal 的验证前提没有触发；
- String、composite Key 不启用 runtime filter；
- lazy output 只保留 callback-scoped delivery。

这些结果应在最终治理报告中继续以 accepted / narrowed / rejected /
condition-not-triggered 区分，不能让未采用方向从叙事中无解释消失。

### 7.3 尚未达到严格收口

本文件创建前的审计结论是：技术治理主体已经完成，但正式 repository closeout
尚未完成，不能无保留声明达到卓越性要求。

提交本轮全部变更后，“巨大 dirty worktree、无提交承载”这一项应关闭；下一轮仍需
实时确认提交后 worktree 和 HEAD。其余尾项如下：

1. **Canonical Full**
   - 首次 Full 的前序阶段通过，DataFlow component 在旧 v3 authoring identity
     处按设计 fail closed；
   - v4 校准后受影响的 docs、baseline architecture、DataFlow component 和 diff
     已通过；
   - 第二次 Full 在 `prepare-external-artifacts` 因当前 Codex 沙箱不能写
     `~/.m2` 中止，不是 product failure；
   - 尚不存在一次针对最终候选整体 exit 0 的 canonical `./scripts/check.sh`。
2. **精确 commit provenance**
   - qualification 的 content SHA 与当时 executable worktree 精确一致；
   - artifact 的 `commit` 仍是治理前基线 `6bd260c`，而正式 Gate 要求在精确新
     immutable candidate 上重放；
   - 当前 Governance Report 的 `completed / G5 passed` 表述早于这一事实，下一轮
     必须按最终 evidence 原子校准 Report 与 Conformance。
3. **Qualification source identity 边界**
   - 当前 source list 过度包含整个模块 `src/**`、tests、Examples 和无关 component
     baseline，导致无关变化触发昂贵的八 lane qualification；
   - 同时没有包含实际 source 的 `scripts/lib/sha256.sh` 和
     `scripts/lib/supported-jdk.sh`；
   - 应在下一次重型 qualification 前做一次窄的 identity-boundary 修正。
4. **DataFlow baseline provenance**
   - v4 baseline 以旧 HEAD 加 `working-tree candidate` 说明校准来源；
   - 三 fork 结果可信，但还不是理想的 immutable calibration provenance。
5. **抽象叙事闭环**
   - 当前正式文档已拥有抽象层次、Owner、边界和机制；
   - 尚未明确制度化“每个核心抽象拥有 Why/Owns/Not/Relationships/Lowering/
     Lifecycle/Resource/Failure/Evidence/Evolution 的叙事闭环”；
   - 这是产品可理解性、未来局部替换和防止再次膨胀的治理要求，不是装饰性文案。

`CandidateProgram`、`GroupedExactIndex` 等核心实现体量上升属于观察信号，不是仅凭
LOC 拆分或删除的结论。下一轮只有在发现独立语义、Owner、生命周期或失败域时才
进行 capability-based refinement，不能为“看起来更小”制造更多类型。

## 8. 下一轮 V1 Release 治理的产品边界

下一轮目标是把 SOMA 推向 **V1 Release Candidate / selected private-source
release**，不是自动扩大发布渠道。

当前已经确认：

- copyright owner 与发布主体：ArthurFeng；
- 代码与文档许可证：Apache-2.0；
- SOMA 名称、Logo 和 Banner 品牌权利由 ArthurFeng 保留，仅允许为说明原始 SOMA
  项目而合理使用；
- GitHub Organization：`somaruntime`；
- repository：`somaruntime/soma-java`；
- Maven groupId / Java namespace：`io.github.somaruntime.soma`；
- 当前 selected release profile：private GitHub source repository；
- public repository 与 Maven Central：not-selected；
- 当前唯一 compiler/runtime validation authority：
  Amazon Corretto 8.502.07.1 full JDK 8；
- Zulu 和其他 JDK vendor 不属于当前支持范围；
- Git 历史可以原样保留；
- 三个 Example 是独立 Java 8 reference consumers；
- Codex Cloud development readiness 是独立 environment gap，不是 V1 release
  profile 的替代名称；用户已明确不以 Cloud 可用性作为当前必要目标。

V1 发布前还确认有一项产品化任务：在仓库中建立帮助 AI coding tools 正确使用
SOMA 的 skill，并在 README 提供让工具安装该 skill 的提示词。下一轮应先核实是否
已有对应 GitHub issue，再决定实施；不能让 skill 重新定义 SOMA Design。

## 9. 下一轮建议顺序

### R0：重新核实当前候选

- 读取正式入口、当前 HEAD、status、diff、Gate 与本文件；
- 确认本轮提交包含预期全部 surface，且没有新 drift；
- 不重跑已经通过且输入未变化的高成本动作。

### R1：关闭本轮治理尾项

- 精确修正 runtime-scale source identity；
- 建立 immutable executable candidate；
- 在能够写标准 `~/.m2` 的新 Codex 线程中运行一次 canonical Full；
- 只对精确候选运行一次 required qualification，不运行非阻塞 research；
- 校准 DataFlow baseline provenance；
- 原子更新 Governance Report、Conformance 与 performance summary；
- 在现有 Design Constitution/Design Index 中固化递归抽象叙事模型，不新增平行
  文档体系。

### R2：V1 RC 审计

- 以 Blueprint → Design → Code/Generated Surface → Evidence → Conformance
  做 scope non-regression；
- 复核 public/generated API、schema/compiler/runtime protocol、三个 Examples、
  package shape、license/NOTICE/brand、SCM 与 support contacts；
- 复核代码、测试、benchmark、脚本和文档 surface，删除只服务已完成迁移的噪声，
  但不以 LOC、单调用者或浅层 unused scan 删除抽象；
- 明确 V1 version、RC candidate identity、支持矩阵和 selected channel 的 claim。

### R3：G6 selected private-source

- 在同一最终 candidate 上完成 clean package/security provenance；
- 完成最终 support-matrix sign-off；
- 执行用户已确认的 manual heavy qualification；
- 只对 private GitHub source profile作出适用结论；
- 不创建 public release、Maven Central publish、tag 或 release，除非用户另行明确
  授权。

### R4：最终 Temporary 退出

- 稳定事实进入唯一正式 Owner；
- 所有 Report/Conformance claim 与最终 commit/artifact一致；
- worktree clean，parallel Owner、migration artifact、active旧专题、
  未裁决 `UNKNOWN` 为零；
- 删除本 Temporary topic；
- 提交并在用户明确要求时推送。

## 10. 下一轮 Definition of Done

只有同时满足以下条件，才能把 SOMA 推进到 V1 RC 或 selected release 的相应状态：

- 产品叙事、核心抽象、每个抽象自己的叙事和三层实现模型形成可追踪闭环；
- D1–D8 的 production、public/generated protocol、Evidence 与 Conformance 在精确
  immutable candidate 上一致；
- Small/Medium、单 1M、双 1M、String、Expansion、Delivery、Soak 保持 required
  qualification；10M/100M 不绑架 V1；
- canonical Full、适用 package/security/provenance 和 manual qualification 有
  可追踪 artifact；
- selected private-source profile 的 identity、license、brand、SCM、support、
  security 和 support matrix 闭合；
- 三个 Example 与 AI skill（若在 V1 前实施）符合最佳实践且不发明 core Design；
- 没有目标缩水、数据库功能膨胀、开放 SPI、任意 object storage、临时 API、
  parallel Owner、迁移双轨、未退役 Temporary 或虚假 readiness claim；
- 任何真实缺口进入 Conformance，不用 future/MVP/optional 改名消失；
- 完成后删除本文件。

## 11. 长任务异常控制

下一轮不得重复本线程早期工程治理中出现的低价值等待与重跑：

- 高成本命令前先回答：输入、假设或 evidence 目标发生了什么变化，本次将获得什么
  新证据；
- 相同输入已通过的证据直接复用；
- 同一失败只有在采取了具体修正后才能重试一次；
- 超出 Fast/Full/qualification 预算首先识别异常信号并诊断，不用固定间隔
  `sleep` 或盲目轮询；
- 一个问题连续两次没有新增状态或证据时停止并重新规划；
- 不在低决策价值的命名、格式、无界 benchmark 调参或装饰性重构上打转；
- 不因为时间、token、权限或环境困难降低产品目标；真正无法闭合的条件记录为
  blocker，并保留其 Owner。
