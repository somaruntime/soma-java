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
| `CF-005` | Evidence environment | 当前性能evidence只覆盖Amazon Corretto 8.502.07.1、macOS/aarch64、Apple M5 Pro与精确component/application/runtime-scale profile | 其他环境只能重新校准或得到`not-applicable`；不得外推支持矩阵、SLA或普遍性能优势 |
| `CF-006` | Release readiness | selected `private-github-source`的identity/SCM/support/security与Corretto Linux Full已成立，但缺clean package/security provenance、最终matrix sign-off与manual release qualification | G6保持`blocked`；在同一最终candidate完成manual release qualification后，由G6 Owner更新Support Matrix和release report |
| `CF-017` | Cloud development evidence | Cloud setup已收敛为exact toolchain、标准Maven cache和最小dependency预取，但没有当前Corretto fresh-container的setup/Fast/Full/clean-worktree evidence | Cloud保持`candidate / qualification-blocked`；按10分钟setup、60秒Fast、10分钟Full预算做一次有界验收，异常时停止诊断，不反复setup/check |

`CF-005`不能由private CI或Cloud smoke自动关闭；当前Linux build/contract通过不
等于Linux性能baseline。

## 2. 已关闭差距

| ID | 关闭结论 | Production replacement与evidence |
|---|---|---|
| `CF-009` | Metadata/runtime contract已闭合 | 完整Metadata hierarchy、schema-seeded Plan、Effective/Runtime Metadata、Table/Segment/access observation、Group member snapshot |
| `CF-010` | V1 type/String实现已闭合 | four-kind classifier、arbitrary object拒绝、concrete String protocol、mutation/epoch/clear/release与reference lifecycle |
| `CF-011` | storage/access实现已闭合 | flat/head-tail storage、flat-compact locator、closed Candidate shapes、point/exact/growth与双root表达能力 |
| `CF-012` | transformation/execution/resource已闭合 | specialized Group/Join/Window、Delta staging、unknown-bound fail-closed、一个bounded morsel scheduler、Invocation ledger与stats/explain |
| `CF-013` | Result Delivery实现已闭合 | Eager Detached默认；统一callback Definition→Template→Invocation；full/early/failure/cancel/deadline/non-escape contract；legacy borrow删除 |
| `CF-014` | product qualification实现与历史candidate已闭合 | strict v1 schema/validator与十lane Zulu artifact证明当时candidate；当前JDK适用性已由`CF-016`的v2 artifact关闭 |
| `CF-015` | migration/complexity/examples已闭合 | replacement closure、22-Table footprint Gate、三个Example显式Group与领域correctness |
| `CF-016` | Corretto runtime-scale qualification已闭合 | v2 schema/runner/validator的Small、Medium、单1M、双1M、String、Expansion、Delivery、Soak共8条required lane在Corretto/macOS/aarch64全部passed；qualification ID `runtime-scale-qualification-20260729-d90e8499d51f`，artifact SHA-256 `4bdc5b51407aaec838af0a95de81249c717e8beab9fea78e1cbf4db8a4abbbef` |
| `CF-018` | Logical/physical production cutover已闭合 | enum/date/time/instant logical facade、TIME range、numeric closed kernel、formula-bound Bitmap、primitive min/max/Bloom/fallback Join已进入production；public/generated golden、negative compile、external consumer、runtime/DataFlow contract、reference differential与v12/v4/v5 identity一致，无旧logical API双轨 |

`CF-006`曾在Zulu private-source candidate上关闭；Corretto成为唯一authority后，
原support/release evidence不再适用，因此当前重新打开。历史结论由Git和旧Report
保留，不在closed表中伪装成当前passed。

## 3. 受限目标，不是差距

- String V1只支持白名单immutable `String`引用；不保存任意Java object，不引入
  dictionary、arena或自动intern；
- V1规模保证只覆盖qualification定义的Small/Medium、单1M与双1M窄schema和
  bounded workload；10M/100M是非阻塞research/stress，不代表任意wide Table、
  任意String profile或无界N:M relation；
- callback-scoped streaming是唯一Lazy Output试点；普通`Iterator<T>`、
  closeable pull cursor、Generator、Publisher、async push均不在本次surface；
- Group只拥有composition、resource和lifecycle，不提供跨Table transaction、
  snapshot isolation或合并root trust；
- runtime是JVM heap-resident library，不是database、ORM、workflow engine、
  distributed runtime或persistence layer；
- public GitHub和Maven Central仍为`not-selected`；Codex Cloud只是开发环境
  candidate，不是release profile，也不是当前private-source缺口的替代名称。

这些边界来自正式Blueprint/Design，不能用“未来可能扩展”反向标成当前缺陷。

## 4. 防回归入口

- 当前判断：[当前一致性基线](current-conformance.md)；
- 当前状态：[Java V1 Goal execution status](../../reports/java-v1-goal-execution-status.md)；
- executable位置：[Implementation Map](../implementation-map/README.md)；
- Gate规则：[Validation Gate治理](../engineering/validation-gates.md)。
