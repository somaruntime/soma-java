# 已知差距与处置

类型：Conformance

状态：正式

Owner：SOMA Java 一致性审查

实现核对基线：2026-07-29 Corretto engineering candidate

事实范围：当前已确认的Blueprint/Design/Code/Evidence差距、关闭依据与Owner处置

非事实范围：自动授权release、扩大支持矩阵或重新定义Design

最后审查日期：2026-07-29

## 1. 未闭合差距

| ID | 分类 | 当前差距 | 影响与Owner处置 |
|---|---|---|---|
| `CF-005` | Evidence environment | 当前性能evidence只覆盖Amazon Corretto 8.502.07.1、macOS/aarch64、Apple M5 Pro与精确component/application profile；runtime-scale为旧Zulu历史evidence | 其他环境只能重新校准或得到`not-applicable`；不得外推支持矩阵、SLA或普遍性能优势 |
| `CF-006` | Release readiness | selected `private-github-source`的identity/SCM/support/security已成立，但JDK authority迁移后缺当前immutable commit的Corretto Linux CI、clean package/security provenance与matrix sign-off | G6保持`blocked`；在GitHub Actions完成Corretto Full与manual release qualification后，由G6 Owner更新Support Matrix和release report |
| `CF-016` | Runtime-scale evidence | Small/Medium、1M/10M、single/double100M、String、Expansion、Delivery、Soak只在旧Zulu authority下通过，尚未在Corretto 8重放 | G5保持`blocked`；按原十lane schema/预算/validator人工运行，不得缩小100M、双表100M或String目标，也不得改名旧artifact冒充当前结果 |

`CF-005`不能由private CI或Cloud smoke自动关闭；Linux build/contract support不等于
Linux性能baseline。`CF-016`是JDK evidence适用性差距，不是production实现回退。

## 2. 已关闭差距

| ID | 关闭结论 | Production replacement与evidence |
|---|---|---|
| `CF-009` | Metadata/runtime contract已闭合 | 完整Metadata hierarchy、schema-seeded Plan、Effective/Runtime Metadata、Table/Segment/access observation、Group member snapshot |
| `CF-010` | V1 type/String实现已闭合 | four-kind classifier、arbitrary object拒绝、concrete String protocol、mutation/epoch/clear/release与reference lifecycle |
| `CF-011` | storage/access实现已闭合 | flat/head-tail storage、flat-compact locator、closed Candidate shapes、point/exact/growth与双root表达能力 |
| `CF-012` | transformation/execution/resource已闭合 | specialized Group/Join/Window、Delta staging、unknown-bound fail-closed、一个bounded morsel scheduler、Invocation ledger与stats/explain |
| `CF-013` | Result Delivery实现已闭合 | Eager Detached默认；统一callback Definition→Template→Invocation；full/early/failure/cancel/deadline/non-escape contract；legacy borrow删除 |
| `CF-014` | product qualification实现与历史candidate已闭合 | strict schema/validator与十lane Zulu artifact证明当时candidate；当前JDK适用性由`CF-016`单独承担 |
| `CF-015` | migration/complexity/examples已闭合 | replacement closure、22-Table footprint Gate、三个Example显式Group与领域correctness |

`CF-006`曾在Zulu private-source candidate上关闭；Corretto成为唯一authority后，
原support/release evidence不再适用，因此当前重新打开。历史结论由Git和旧Report
保留，不在closed表中伪装成当前passed。

## 3. 受限目标，不是差距

- String V1只支持白名单immutable `String`引用；不保存任意Java object，不引入
  dictionary、arena或自动intern；
- 100M只支持qualification定义的窄schema与bounded workload；双100M指两张root
  同时驻留，不代表任意双wide Table或无界N:M relation；
- callback-scoped streaming是唯一Lazy Output试点；普通`Iterator<T>`、
  closeable pull cursor、Generator、Publisher、async push均不在本次surface；
- Group只拥有composition、resource和lifecycle，不提供跨Table transaction、
  snapshot isolation或合并root trust；
- runtime是JVM heap-resident library，不是database、ORM、workflow engine、
  distributed runtime或persistence layer；
- public GitHub、Maven Central和Codex Cloud均为`not-selected`，不是当前
  private-source缺口的替代名称。

这些边界来自正式Blueprint/Design，不能用“未来可能扩展”反向标成当前缺陷。

## 4. 防回归入口

- 当前判断：[当前一致性基线](current-conformance.md)；
- 当前状态：[Java V1 Goal execution status](../../reports/java-v1-goal-execution-status.md)；
- executable位置：[Implementation Map](../implementation-map/README.md)；
- Gate规则：[Validation Gate治理](../engineering/validation-gates.md)。
