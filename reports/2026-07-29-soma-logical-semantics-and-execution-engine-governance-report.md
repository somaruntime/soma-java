# SOMA 逻辑语义与执行引擎治理报告

类型：Report / Governance / Qualification

状态：completed；V1 G5 passed，G6 blocked

Owner：SOMA Java 产品、逻辑语义与执行引擎治理

受众：SOMA maintainer、Design/Capability/Gate与release owner

适用版本：`soma-java` `0.2.0-SNAPSHOT`

输入事实源：Blueprint、正式 Design、production source、public/generated golden、
external consumer、runtime/DataFlow contract、reference differential、三个 reference
application 与 runtime-scale qualification artifact

事实范围：D1–D8 裁决的 production cutover、当前 Corretto qualification、示例审计、
Conformance 与 V1 RC 关系

非事实范围：public/Maven 发布授权、跨环境性能、任意 Schema SLA 或 G6 sign-off

最后审查日期：2026-07-29

## 1. 治理结论

本专题没有把 SOMA 重新定义为数据库，也没有丢弃
`State / Owner + Capability + Plan / Lifecycle` 的核心抽象与产品叙事。新增的
三层视角用于解释同一能力如何实现：

```text
Logical semantics
    -> Java carrier + generated type-safe capability
    -> JVM / OS / CPU-aware physical execution
```

逻辑层继续服务易用性、类型安全和明确约束；Java carrier 与物理层服务 packed
storage、JIT、cache、memory bandwidth、bounded intermediate 和受控并行。SQL
系统只提供类型、约束、索引和执行 lowering 的设计启发；SOMA 仍以编译期 Schema
和 generated Java API 取代 SQL 与传统 row-object hot path。

本轮完成 D1–D8 的正式 Design、production、可执行契约和 evidence 闭环：

| 决策 | 最终结果 |
|---|---|
| D1 logical type | enum/date/time/instant 切换为 type-specific expression facade；内部共享 primitive carrier；不增加 `SomaInt`/`SomaLong` wrapper |
| D2 numeric kernel | 只为 required long column + constant arithmetic/comparison common chain启用 closed whole-loop kernel；reference graph保留oracle/fallback |
| D3 Segment statistics | 拒绝V1 mutable Segment min/max capability；没有建立production type、配置或平行Owner |
| D4 Bitmap | 只在公式许可的单字段primitive exact equality交集中启用maintained bitmap；exact hash/full equality与link path保持authoritative/fallback |
| D5 Join filter | primitive单分量Join可选Invocation-local min/max或Bloom；最终hash/full equality不变；String与复合Key禁用 |
| D6 String | 长度只属于mutable-before-freeze resource estimate与evidence profile，不是Schema约束或mutation admission |
| D7 Scale | V1 required envelope改为Small、Medium、单1M、双1M、String、Expansion、Delivery、Soak；10M/100M转为非阻塞research/stress |
| D8 产品边界 | 保持SOMA产品定义与核心抽象；不增加SQL/DDL/DML/DQL、foreign key、reference或通用数据库API |

## 2. Production cutover

### 2.1 Logical type 与编译期边界

Generated DataFlow companion 现在返回 `EnumExpression`、`DateExpression`、
`TimeExpression`、`InstantExpression`。Key/Join overload 接受相同 logical type；
enum arithmetic、date/raw-long 混合和 time/instant 比较在 external negative
consumer 中编译失败。Raw primitive 仍使用 numeric expression，不引入 row
wrapper 或 boxed hot storage。

Date 支持 checked plus/minus days；Time 支持 24 小时 modular plus/minus nanos；
Instant 支持 checked plus/minus millis。TIME 的 direct、Batch、replace、Mutator、
Delta 与 flattened-value 写入都在 publish 前校验
`[0, 86_400_000_000_000)`，越界产生 typed failure。

### 2.2 Closed physical capability

- `ClosedNumericKernel` 拥有 packed outer loop、boundary check和primitive
  evaluation；不适用形态回到reference expression graph；
- `GroupedExactIndex` 在相同authoritative group identity下维护link或bitmap
  membership，append/update/remove/relocation/clear/release与retained bytes闭合；
- `CandidateLongEqualityAccess`只把generated maintained equality words投影给
  DataFlow，不进入application data model；
- Join runtime filter由build cardinality、probe cardinality、range与cost公式选择；
  Bloom scratch属于单次Invocation，dense/high-hit直接fallback；
- protocol升级为generated/runtime v12、transformation v4、kernel v5，
  Candidate/relation formula升级为v2；没有保留旧logical API双轨。

## 3. Corretto runtime-scale qualification

执行环境：

- Amazon Corretto `1.8.0_502-b07` full JDK 8；
- Maven Wrapper `3.9.16`；
- macOS `26.5.2` / Darwin `25.5.0`、`aarch64`、Apple M5 Pro、48 GiB；
- G1 GC；每条lane独立JVM与显式heap/timeout。

Artifact identity：

- qualification ID：
  `runtime-scale-qualification-20260729-d90e8499d51f`；
- production/evidence source：
  `d90e8499d51f7477db3959033895853e223bd692794e25eb8bdf234492e3c2ba`；
- combined artifact：
  `4bdc5b51407aaec838af0a95de81249c717e8beab9fea78e1cbf4db8a4abbbef`；
- strict schema v2：
  `eeb8eb1e0f5beda9b3970746b796eb0c5e58a7b8ccd5a9f7cc21dbafc98b4ce2`。

八条 required lane 均为 `applicable=true`、`status=passed`、
`claimAllowed=false`：

| Lane | 诊断耗时 | Structural high-water | Reachable String model | JVM heap peak | 关键事实 |
|---|---:|---:|---:|---:|---|
| Small/Fast | 55.0 ms | 1,118,912 B | 1,540,096 B | 16,254,968 B | 0..4K primitive/String fixed-tax matrix |
| Medium | 111.5 ms | 27,568,936 B | 90,112 B | 75,497,840 B | 32K/64K/256K；single Segment sequential与multi-morsel parallel均触发 |
| 1M Single | 133.9 ms | 50,993,056 B | 0 | 124,519,032 B | actual resident 1M numeric root；Point到Window、kernel、Delta与release |
| 1M Double | 230.2 ms | 98,578,240 B | 0 | 238,783,192 B | 两个同时resident的1M roots；minmax、Bloom、dense fallback与cross-Group relation |
| String | 647.0 ms | 168,252,058 B | 192,753,664 B | 573,617,648 B | 两张1M角色Table；payload、Key/Unique/Index、Group/Join、mutation、clear/release/GC |
| Expansion | 37.0 ms | 0 | 0 | 7,867,400 B | over-budget、overflow、unknown bound均在child enumeration前拒绝 |
| Delivery | 51.8 ms | 600,992 B | 22,528 B | 12,060,664 B | 7种Eager/callback形态及early stop/failure/cancel/deadline/non-escape |
| Soak | 136.5 ms | 600,992 B | 11,264 B | 50,200,584 B | 100次lifecycle；100个String weak reference清除；ledger与executor归零 |

耗时只用于该机器上的诊断和回归，不是 latency SLA。Validator同时拒绝
`claimAllowed=true`、缩小后的伪1M记录、extra field、缺失或重复required lane；
runner/validator classfile均为Java 8 major 52。

### 3.1 String claim boundary

String lane 使用两个同时驻留的1M Table：

- payload profile：长度12..48、cardinality 4,096、高共享；
- access profile：Key/Unique为1M cardinality低共享，Index为高共享；
- 字段角色覆盖payload、Key、Unique、Index、Group、Join；
- mutation把真实qualification Table中的slot替换为不同长度caller reference；
- equal-value different-object mutation保留原reference且不改变epoch/access；
- clear/release后被追踪reference实际被GC回收。

Structural bytes、SOMA-retained reachable String model与JVM observed heap分开报告。
这些长度/cardinality/sharing只是本次workload profile，不是String字段约束。

## 4. 三个 reference application 审计

本轮重新核对三个production application journey：

- 工业调度继续以显式`SomaGroup`拥有九个相关root，使用direct/exact/Candidate和
  application-owned solver loop；
- 个体生态仿真继续以显式`SomaGroup`拥有state/trace，使用packed iteration、
  exact group、staged mutation与deterministic lifecycle；
- 实时派工继续通过generated enum exact source、primitive typed expression、
  reusable Definition/Template、bounded Join/Group和detached command工作。

三者都已使用canonical generated API、Metadata-scoped Plan、Group ownership与
detached result；没有旧`LongExpression` logical leakage、临时API、Iterator、
generic object storage或需要迁移的设计偏差。因此本轮不做装饰性Example改写；
本轮Full Gate的reference-applications阶段已把它们作为三个独立普通Java 8
consumer重新生成、编译和运行。

## 5. Scope non-regression 与 surface

本轮增加的production surface都有独立长期责任：

- 4个logical expression facade；
- 1个generated equality bridge；
- closed numeric lowering与Join runtime filter为package-private physical
  implementation，不形成application SPI；
- runtime-scale schema v1由v2原子替换，没有并存required协议。

Public executable classification由224增至229，增量正好是上述4个facade和1个
generated bridge；旧enum/date/time/instant raw-long generated signature退出。
Segment statistics没有落入production。测试与benchmark按Capability、reference
differential、external consumer和qualification组织，没有以TV编号形成canonical
taxonomy。

专题收口要求并验证：

- parallel Design Owner为零；
- migration adapter/旧protocol并存为零；
- active Temporary与独立Lab为零；
- 未裁决`UNKNOWN`为零；
- 三个Example无需额外production改写；
- Conformance `CF-016`、`CF-018`关闭，`CF-005`、`CF-006`、`CF-017`保持真实打开。

DataFlow component的authoring lane以compiled plan identity作为确定性checksum。
Transformation/kernel协议升级到v4/v5并加入closed numeric physical form后，旧
baseline v3的authoring identity按设计失效。三fork候选中新的checksum
`1064084655879852845`全等，其余14条lane的执行checksum以及全部allocation、
timing、tail和GC规则仍通过旧envelope；因此版本化替换为v4，只校准authoring
identity，不放宽任何性能上限。

## 6. V1 Release Candidate 关系

本专题完成后，SOMA已经具备进入V1 Release Candidate评估的产品与G5技术基础：
逻辑类型、核心抽象、执行引擎、Small/Medium/单1M/双1M/String、Result Delivery、
三个reference application与当前Corretto evidence形成闭环。

这不等于已经完成release：

- G6 selected `private-github-source`仍缺同一最终candidate的clean package/security
  provenance、support-matrix sign-off与manual release qualification；
- Linux现有build/contract evidence不能外推为Linux性能/规模baseline；
- Codex Cloud仍是单独的development-environment qualification gap；
- public repository与Maven Central仍为not-selected。

因此当前准确结论是：**G0–G5 passed，可进入V1 RC评估；G6 blocked，尚未获得
private-source release sign-off。**

## 7. 验证收口与重放入口

综合Full首次执行时，toolchain、docs、baseline architecture、reactor、public/
compiler/codegen、runtime/scalar、generated/integration consumer、code-size、
DataFlow contract/reference、benchmark smoke和Access component均通过；DataFlow
component按设计在旧v3 authoring identity处fail closed。完成上述v4版本化替换后，
只重新执行输入受影响的docs、baseline architecture、DataFlow component和diff，
均通过；Corretto required qualification也针对新source identity重新生成并8/8
passed。未变化且已通过的高成本阶段按Validation治理直接复用，不以重复Full掩盖
归因。

```sh
./scripts/check.sh
./scripts/check-runtime-scale-qualification.sh qualification
./scripts/check-runtime-scale-qualification.sh research
```

`research`只运行10M及三条100M stress lane，不参与G5通过率。重复qualification前
必须说明source、环境、假设或证据目标发生的变化；没有新证据目标时直接复用上述
artifact identity。
