# SOMA Java V1 Production Implementation Plan

类型：Engineering Plan

状态：Ready / Not Started

正式事实源：是（实施顺序与完成定义）

Owner：SOMA Java V1 production implementation slices、依赖顺序与阶段性 Definition of Done

最后审查日期：2026-08-01

## 1. 目标与终点

目标是在不重新解释产品语义的前提下，把正式 Blueprint/Design 转化为可验证的 Java 8
compiler/runtime 产品。最终终点不是“代码写完”，而是 G1-G8 取得可重放 evidence、
三个 reference scenario 成立、package/release claim 与事实一致。

本计划不是继续写 Blueprint。Blueprint 与核心 Design 已经完成；后续只在实现证据发现
真实矛盾时才通过 Temporary + Product Owner 裁决修改它们。

## 2. 全局 Definition of Done

V1 production implementation 只有同时满足以下条件才完成：

1. 两个 production artifact 与 independent Java 8 consumer clean build/run；
2. exact generated/shared signature、positive/negative capability 与 full regeneration
   contract 通过；
3. storage/Key/Index/order/null/materialization invariants 通过；
4. point/selection mutation、admission、failure injection 与 zero publication 通过；
5. sequential/parallel callback、result、order、numeric、failure、quiescence 等价；
6. G7 三个 reference scenario 在 16 core/32 GB 上达到正式 qualification target；
7. package smoke、license/NOTICE/source/javadoc、SBOM/依赖与 release workflow 通过；
8. Conformance 无未披露 blocker，用户文档不含超出 evidence 的 claim。

## 3. Slice 状态

| Slice | 名称 | 状态 | 主要 Gate |
|---|---|---|---|
| I0 | Build spine 与 artifact boundary | NOT_STARTED | G1、G6、G8 |
| I1 | 最小 compiler-to-runtime 纵向闭环 | NOT_STARTED | G1-G4、G6 |
| I2 | Schema/type/storage/Key/Index breadth | NOT_STARTED | G1、G2、G4 |
| I3 | Query Stream capability breadth | NOT_STARTED | G3-G5 |
| I4 | Atomic mutation、admission 与 failure | NOT_STARTED | G4、G6 |
| I5 | Bounded parallel execution | NOT_STARTED | G5、G6 |
| I6 | Metadata、build host 与 exact surface closure | NOT_STARTED | G1、G3、G6、G8 |
| I7 | Reference scenarios、performance、package/release qualification | NOT_STARTED | G7、G8 + release |

## 4. I0 — Build spine 与 artifact boundary

### Capability

建立标准 Maven reactor、`soma-runtime`、`soma-processor` 和最小 independent consumer
fixture，使后续每一行 production code 都处于真实 Java 8 dependency/build boundary。

### 准入 surface

- root `pom.xml` 和两个 module；
- Java 8 compiler/toolchain policy；
- unit/consumer/compile-negative test taxonomy；
- exact version linkage 与 generated-contract handshake；
- license/NOTICE/source/javadoc 的最小 package policy。

### Exit evidence

- clean reactor 在真实 Java 8 编译；
- consumer 分离 classpath/processorpath；
- processor/runtime version mismatch negative；
- dependency tree 只有经 admission 的依赖，并建立 G8 processor/runtime security negatives；
- 不存在空 module、legacy source 或第三个 artifact。

## 5. I1 — 最小 compiler-to-runtime 纵向闭环

### Journey

一个 composition，包含一个 primitive Value、一个 keyed Table、一个 non-Key primitive
Field，贯通：

```text
schema -> processor -> generated Soma/Table/object
       -> add -> find/get -> stream/filter/count
       -> field update -> structured failure
```

### 目的

尽早同时验证 JSR 269、generated surface、runtime root、admission、cursor、failure 与
consumer usability。这里的“最小”只限定实施顺序，不缩减 V1 scope，也不允许把薄实现
固化成 universal boxed/reflection engine。

### Exit evidence

- generated source + `javap -v` golden；
- independent consumer positive/negative；
- no reflection/boxing hot path inspection；
- add/find/query/update success/failure state invariant；
- P2 结论在 production topology 中首次重验证。

## 6. I2 — Schema/type/storage/Key/Index breadth

覆盖全部 primitive、String、Enum、Value flattening、ordinary/parameterized Object、
keyless/keyed、多 Index、null/equality/collision、Group/default Group、capacity、关系 Table
journey。

Exit 必须包含：

- annotation/declaration/collision diagnostic catalog；
- all leaf generated/access/materialization matrix；
- Key/Index collision、duplicate、null、ordered subsequence；
- reserve/growth/remove compaction 与 GC reachability；
- 1:M/N:M 双向 Index consumer journey；
- storage structural bytes 和 allocation baseline。

## 7. I3 — Query Stream capability breadth

覆盖 Record/Field/Mapped/八种 primitive Stream 的完整 operation-property matrix：
filter/select/map/mapToXxx/distinct/sorted/skip/limit、match/find/forEach、min/max、
sum/average、toList/toArray。

Exit 必须包含：

- 每个应存在/缺席 signature 的 compile probe；
- stateful operation canonical order 与 stable tie；
- mapped `toArray(Class<A>)` 全部正反例；
- integer exact overflow、float/double canonical tree bit result；
- Record/Value View O(1) allocation 与 scope-negative；
- no unbudgeted internal task/scratch growth。

## 8. I4 — Atomic mutation、admission 与 failure

覆盖 point add/update/remove/reserve 与 selection update/remove，完成 fail-fast
Read/Write admission、reentrancy、phase precedence、fault injection、sanitization 和
zero-publication proof。

Exit 必须在 payload、Key、每个 Index、allocation、callback、merge、validation、publish
前的所有可恢复 failure point 注入失败，并证明 old root/stateVersion 完整。Repeated add
必须取得 G7 ingestion baseline；若触发 Batch stop rule，本 slice 停止等待产品裁决。

## 9. I5 — Bounded parallel execution

在 sequential correctness baseline 上加入 library-wide `ForkJoinPool`、fixed
configuration、bounded partition/task、worker token、deterministic merge/cancellation 和
quiescence。

Exit 必须覆盖 P=1/2/4/16、小/大 selection、custom/common/shutdown/rejection、nested
parallel、active callback <= P、task count bound，以及所有 deterministic operation 的
sequential/parallel result/order/numeric/failure/mutation 等价。

## 10. I6 — Metadata、build host 与 exact surface closure

完成 immutable metadata carrier、无副作用 observation、Maven clean-full
regeneration、IDE delegated build 说明、manifest/stale cleanup、完整 golden/javap 与 API
diff。重新运行独立 consumer，而不是使用 processor module 的同-reactor偶然可见性。

Exit 时 G1、G3、G6 除 production performance/release 依赖外必须全部 PASS。

## 11. I7 — Reference scenarios、performance 与发布资格

### Reference scenarios

1. 调度：Job/Operation/Machine/Eligibility relation、Key/双向 Index、筛选和状态更新；
2. 仿真：大规模事件/实体状态、sequential deterministic step 与 bounded parallel query；
3. 实时派工：高频 point lookup、Index candidate selection、低分配决策循环。

### 工作顺序

correctness journey -> deterministic workload -> profile -> identify owner -> narrow
optimization -> equivalence rerun -> scale/saturation -> package/release qualification。

不得为 benchmark 修改产品语义、降低校验或引入 example-only hidden API。若三个场景中
任何一个不能以公开 API 自然表达，先回到 Blueprint/Design 审查。

### Exit evidence

- G7 qualification report；
- 16 core/32 GB scale 与 saturation evidence；
- Examples 仅使用 public surface；
- package smoke、consumer from packaged artifact、source/javadoc/license/NOTICE；
- dependency/security/SBOM/checksum/provenance review；
- CI/release workflow 和版本/Changelog/claim review；
- Owner sign-off 后才允许 GitHub Release/Package。

## 12. Traceability matrix

| Blueprint | Primary Design | Implementation slices | Primary Gate |
|---|---|---|---|
| BP-1 | Schema、Logical、Signature | I0-I3、I6 | G1、G3 |
| BP-2 | Schema、Signature | I1-I3、I6 | G1、G3 |
| BP-3 | Storage、Schema | I1-I2 | G2 |
| BP-4 | Storage、Logical、Architecture | I1-I3 | G2、G3 |
| BP-5 | Logical、Execution、Architecture | I1、I3-I5 | G3、G4 |
| BP-6 | Execution、Architecture | I5 | G5 |
| BP-7 | Failure、Signature | I1、I4-I6 | G6 |
| BP-8 | Storage、Logical、Architecture | I2-I5、I7 | G3-G5、G7 |
| BP-9 | Blueprint、Storage、Execution | I2、I4、I7 | G2、G4、G7 |
| BP-10 | 全部 + Conformance | I0-I7 | G1-G8 |

## 13. Stop rules

任一条件出现时停止当前 slice，不继续堆代码：

- public/generated signature 需要偏离正式 Design；
- correctness 只能依靠 reflection/boxing/unbounded allocation 或 hidden blocking；
- sequential/parallel 无法产生同一 logical result/failure；
- mutation failure 不能证明 zero publication；
- full regeneration 不能在标准 Maven/IDE delegated path 重放；
- repeated add、memory peak 或核心 hot path未达 G7 stop threshold；
- 连续验证不产生新 evidence，只是在重复相同失败；
- 新 artifact/type/dependency/workflow 找不到独立 capability Owner。

停止后只建立一个 bounded Temporary：反例、影响的 Blueprint/Design、候选方案、所需
裁决与恢复条件。未经 Product Owner 裁决，不把 workaround 固化成产品事实。

## 14. Change protocol

- 技术实现细节在不改变合同且 evidence 更好时，可通过 Architecture Design 审查替换；
- public capability、用户心智模型、failure/numeric/order/lifecycle 变化必须回到 Product
  Owner；
- 计划中的状态变化必须链接 Conformance record 与适用 commit；
- release 后才启用 compatibility/deprecation policy；pre-release 实施不保留失败草案的
  compatibility alias。
