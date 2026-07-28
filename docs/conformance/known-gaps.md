# 已知差距与处置

类型：Conformance

状态：正式

Owner：SOMA Java 一致性审查

实现核对基线：2026-07-28 runtime-scale working-tree candidate（base
`6cde5d5`；production/evidence source
`content-sha256:dfe8fa98b2a411708359a378e05f22e2ad89a7b900c70d1f71e8dd1a6b7f8e69`）

事实范围：当前已确认的Blueprint/Design/Code/Evidence差距、关闭依据与Owner处置

非事实范围：自动授权release、扩大支持矩阵或重新定义Design

最后审查日期：2026-07-28

## 1. 未闭合差距

| ID | 分类 | 当前差距 | 影响与Owner处置 |
|---|---|---|---|
| `CF-005` | Evidence environment | performance与runtime-scale证据只覆盖记录的Azul Zulu 8、macOS/aarch64、Apple M5 Pro及精确workload/profile | 其他环境只能重新校准或得到`not-applicable`；不得外推支持矩阵、SLA或普遍性能优势 |
| `CF-006` | Release evidence | G6所需真实SCM/ownership/contact、signing/publishing、clean provenance和完整support matrix不足 | G6保持`blocked`；禁止public RC、release-ready、production-ready或publishing声明 |

这两个差距都不能由更多本机benchmark自动关闭，也不在本综合治理的push/release授权
内。

## 2. 本专题已关闭差距

| ID | 关闭结论 | Production replacement与evidence |
|---|---|---|
| `CF-009` | Metadata/runtime contract已闭合 | 完整Metadata hierarchy、schema-seeded Plan、Effective/Runtime Metadata、Table/Segment/access observation、Group member snapshot；external consumer与runtime diagnostics通过 |
| `CF-010` | V1 type/String已闭合 | four-kind classifier、arbitrary object拒绝、concrete String column/protocol、mutation/epoch/clear/release及actual weak-reference GC；1M/10M/100M profile明确长度/cardinality/共享率/角色/table count |
| `CF-011` | storage/access已闭合 | flat/head-tail storage、flat-compact locator、closed Candidate shapes、Small固定税、Medium crossover、point/exact/growth与single/double100M本机qualification |
| `CF-012` | transformation/execution/resource已闭合 | specialized Group/Join/Window、Delta staging、unknown-bound fail-closed、一个bounded morsel scheduler、Invocation phase ledger、component stats/explain及sequential differential |
| `CF-013` | Result Delivery已闭合 | Eager Detached默认；统一callback Definition→Template→Invocation；Candidate/Value/Group/Join/Window full/early/failure/cancel/deadline/non-escape/GC验证；legacy borrow删除 |
| `CF-014` | product qualification已闭合 | strict v1 schema/validator、Small/Medium、1M/10M、single/double100M、String100M、Expansion、Delivery、Soak十条required applicable lane全部passed，全部`claimAllowed=false` |
| `CF-015` | migration/complexity/examples已闭合 | exact disposition与replacement closure；删除generic Object、consumer-in-Definition、平行parallel owner和dead tests；22-Table footprint Gate；三个Example审计后仅把真实multi-root lifecycle偏差迁入explicit Group |

关闭表示正式Design所要求的当前V1 slice不再依赖未来public consumer、临时adapter、
test-only bypass或canonical hot-path migration。它不改变`CF-005`、`CF-006`。

## 3. 受限目标，不是差距

- String V1只支持白名单immutable `String`引用；不保存任意Java object，不引入
  dictionary、arena或自动intern；
- 100M只支持已qualification的窄schema与bounded workload；双100M指两张root同时
  驻留，不代表任意双wide Table或无界N:M relation；
- callback-scoped streaming是唯一Lazy Output试点；普通`Iterator<T>`、
  closeable pull cursor、Generator、Publisher、async push均不在本次surface；
- Group只拥有composition、resource和lifecycle，不提供跨Table transaction、
  snapshot isolation或合并root trust；
- runtime是JVM heap-resident library，不是database、ORM、workflow engine、
  distributed runtime或persistence layer。

这些边界来自正式Blueprint/Design，不能用“未来可能扩展”反向标成当前缺陷。

## 4. 防回归入口

- 当前判断：[当前一致性基线](current-conformance.md)；
- 完整闭合证据：[综合治理报告](../../reports/2026-07-28-runtime-boundary-group-scale-readiness-governance-report.md)；
- executable位置：[Implementation Map](../implementation-map/README.md)；
- Gate规则：[Validation Gate治理](../engineering/validation-gates.md)。
