# SOMA V1 大范围优化后全局完整性与回归审查

类型：Conformance / Post-implementation Global Review

状态：`PASS / FINDINGS_CLOSED / PUBLICATION_NOT_AUTHORIZED`

日期：2026-08-11

Owner：32位结构域与即时增量Index治理、Scheduling reference/performance治理之后的全仓Design↔code
一致性、正常路径正确性、资源/失败边界、产品隔离及回归证据

## 1. 审查结论

本次审查以`3049bd4..1cd0175`两轮大范围治理及其current checkout为主要变化域，并从正式
Blueprint、九个Design Owner、核心抽象/不变量、I0-I8与G1-G10向下复核production实现、generated
surface、tests、qualification、benchmark与Scheduling reference application。

结论为`PASS`：没有发现未关闭P0；两个影响正常operation合同的runtime P1已修复并建立回归证据；
正式Owner中的P1 current-state漂移已收口。审查没有增加application frontier、ordered Index、第二套Index
truth、第三artifact、dependency或公开API，也没有以异常使用穷举替代正常产品路径审查。

## 2. 审查边界与方法

本次优先验证以下高价值问题：

1. 32位结构域、64位累计域、packed remove与singleton-inline / ordered `int[]`是否仍只有一套truth；
2. point add/update/remove与Selection mutation是否保持one publication、failed-state和增量Index完整性；
3. PLAIN/encoded/overlay转换是否保持payload、Key/Index、order与reference clearing；
4. Group guard、callback provenance、temporary/retained admission与JVM Error边界是否一致；
5. generated/public surface与Java 8 topology是否漂移；
6. Scheduling的FJSP、FCFS/SPT与访问路径是否保持application-owned，没有泄漏进runtime；
7. 最近性能优化是否引入可见的正常路径复杂度或固定成本回归；
8. Blueprint↔Design↔Engineering↔Conformance是否仍有唯一current Owner。

不在本次范围内：新功能构思、用户文档、正式release/publication、一亿行承诺、跨硬件SLA、
application frontier或ordered access path设计。

## 3. Findings 与修正

### 3.1 P1：Group operation provenance跨operation复用

`GroupOperationGuard`原先复用同一`Lease`作为failure provenance。若application在前一次callback中
保存runtime产生的structured failure，并在同Group后续operation的callback中重放，identity比较会把
旧failure误判为当前operation的failure，外层错误保留旧code，而不是按合同转为`CALLBACK_FAILED`。

修正后Guard仍复用同一Lease，不为正常operation分配token；Lease维护checked `long generation`，
`SomaOperationException`在failure创建时只快照`owner + generation`。当前callback中的runtime failure
仍保留原code，跨operation replay或foreign failure不再冒充current runtime。generation耗尽时在
operation建立前fail closed，Guard不会被半占用。

回归证据同时覆盖：same-operation reentrancy保留`REENTRANT_GROUP_OPERATION`；跨operation replay
成为`CALLBACK_FAILED`且原failure仅作为cause；Table状态未发布变化。

### 3.2 P1：point add的Index replacement先分配、后资源准入

原`publishAdd`先调用`IdentityHashIndex.prepareAdd`，随后才取得temporary/retained admission；而
prepare可能分配Shard container、rehash replacement或expanded Bucket `int[]`。因此old/new sidecar
共存峰值未先进入SOMA managed-memory边界，tiny budget也可能在拒绝前发生engine-owned allocation。

修正后每个Key/Index先只读完成typed lookup并把Shard/Bucket计划写入复用的`PreparedAdd`，同时计算
本次精确新增allocation：new container、new/rehashed Shard base或replacement Bucket array；Table在
任何replacement allocation前一次取得temporary lease，再按同一计划prepare，不重复hash/equality。
point update在callback前按`2 × current sidecar + Chunk payload`
保守准入，覆盖未知staged Index value可能触发的rehash/Bucket replacement。正常point commit、
canonical order与唯一membership算法未改变。

`IdentityHashIndex`不再捕获`OutOfMemoryError`并伪装为`RESOURCE_LIMIT_EXCEEDED`：产品上限和预算
拒绝仍使用structured failure，真实JVM Error继续按Failure Design透传。

回归证据覆盖：prepare观察点已持有temporary lease；tiny-budget add在任何sidecar allocation前拒绝；
old root/sidecar/accounting保持零变化；成功路径lease释放且retained与Table managed bytes一致。

### 3.3 P1：正式Owner仍描述“尚无implementation”

Blueprint成功标准、Design实现程度、Conformance readiness与核心抽象成熟度仍保留实施前文字，和
I0-I8/G1-G10 current fact冲突。已完成以下唯一Owner修正：

- Blueprint只声明已存在qualified implementation，并继续排除release/SLA/一亿行外推；
- Design总览不再重复已经关闭的implementation gap；
- A15-A27在已有相称Conformance后从candidate/hypothesis晋升为`EVIDENCE_VALIDATED`；
- Implementation Plan的current I2 structural表述由`long reserve`校正为checked `int`；
- current Conformance将历史readiness标为已履行并由qualification承接，历史记录本身不重写。

另修正Architecture Design中的一处文本拼写。该项不改变产品或runtime语义。

## 4. 全局审查矩阵

| 方向 | 主要证据 | 结论 |
|---|---|---|
| Schema/generated surface | Java 8 reactor、generated consumer、source/ABI/golden、full regeneration | PASS |
| Structural domain | public/generated结构量`int`，累计/bytes/version `long`，checked failure | PASS |
| Payload/representation | PLAIN/encoded/overlay、跨Chunk packed remove、reference clearing | PASS |
| Key/Index | unique inline、ordered multi Bucket、randomized state model、validator | PASS |
| Mutation/publication | point局部commit、Selection candidate、fault injection、old-root invariant | PASS |
| Operation/failure | one-operation generation、callback replay、JVM Error passthrough | PASS |
| Resource | add prepare前lease、update conservative admission、retained/temporary归零 | PASS |
| Query/relation/parallel | reference differential、Index source、Join/Group、sequential/parallel Gates | PASS |
| Scheduling boundary | runtime/processor无FJSP/FCFS/SPT状态分支；四张Table只在example | PASS |
| Repository/governance | 两artifact、无legacy/release claim、Owner route与Temporary状态一致 | PASS |

## 5. 验证证据

- Java：Amazon Corretto `1.8.0_502`；Maven：`3.9.16`；
- targeted runtime：`GeneratedTableTest` 58项PASS；
- 新增正常合同证据：cross-operation provenance replay、add lease-before-prepare、tiny-budget
  no-sidecar-allocation；
- production source中没有把`OutOfMemoryError`转换成structured failure的Index路径；
- full `./scripts/check.sh`：PASS；覆盖reactor、I0-I8 qualification、generated consumer、三个
  reference application、benchmark compile、package/source consumer、SBOM/provenance与workflow pins；
- standard 100K FJSP warm harness：100,000 operations、makespan 50,281、correctness PASS，
  2 warmups / 5 samples的dispatch median为`492.220 ms`，未出现因资源修正造成的性能退化；
- `git diff --check`、Markdown link/current route、无committed build artifact：PASS；
- 一次bounded独立只读审查识别上述两个runtime P1，并在修正后复核闭合。

绝对性能只属于同机回归证据，不是跨硬件SLA。详细时间和最终提交/remote workflow结果由本次提交
及GitHub checks提供，不在Design中复制。

## 6. Exit 与 claim boundary

本记录允许声称：

> SOMA V1在两轮大范围结构/Index与Scheduling优化之后，正式Design、production实现、测试、
> qualification与reference application仍保持统一；本次发现的operation provenance和Index
> allocation admission缺口已修复，未发现新的产品边界或正常路径架构回归。

本记录不证明：GitHub Release/Package、签名、正式release、跨硬件SLA、一亿行性能、任意超大
Bucket最优性、application frontier或SOMA ordered access path。

当前没有active implementation slice或active bounded Temporary；frontend-neutral Logical IR仍只是
`QUEUED / NOT_ACTIVE / NOT_DESIGN`意图。
