# 已知差距与处置

类型：Conformance

状态：正式

Owner：SOMA Java 一致性审查

实现核对基线：`1.0.0` release candidate preparation

事实范围：当前仍开放的Blueprint/Design/Code/Evidence差距与Owner处置

非事实范围：历史已关闭差距、自动授权release、扩大支持矩阵或重新定义Design

最后审查日期：2026-07-29

## 1. 未闭合差距

| ID | 分类 | 当前差距 | 影响与Owner处置 |
|---|---|---|---|
| `CF-006` | Release readiness | selected `private-github-source`尚未在同一`1.0.0` clean candidate形成Full、package/security provenance、最终matrix和sign-off | G6保持`blocked`；只在同一candidate形成完整evidence后更新Support Matrix和release report |
| `CF-019` | Candidate qualification | 旧runtime-scale artifact的executable content可信，但commit和source closure不满足最终candidate；DataFlow v4 calibration仍描述working-tree来源 | G1–G5不得自动继承；在精确clean commit重放DataFlow 3 fork、canonical Full与8-lane required qualification |
| `CF-020` | AI consumer evidence | canonical Skill、README/Guide与结构/drift Gate已形成，但blind behavior、anti-pattern和第二独立宿主验证未完成 | 不声明multi-tool support；完成真实consumer compile/run与两个宿主的发现/触发/行为验证 |

已关闭差距由Git和当时Report保存，不在current Conformance维护历史清单。上述三项
不得用placeholder、旧vendor、working-tree hash、smoke或单一宿主的文本输出替代。

## 2. 受限目标，不是差距

- 性能与规模只适用于记录的Corretto/macOS/aarch64和精确workload；其他环境需要
  独立baseline/qualification，不能从Linux build/contract或单机结果外推；
- String V1只支持白名单immutable `String`引用，不保存任意Java object，也不
  引入dictionary、arena或自动intern；
- V1 required scale只覆盖Small/Medium、单1M、双1M、String、Expansion、
  Delivery与Soak；10M/100M是非阻塞research/stress；
- callback-scoped delivery是唯一lazy形态；普通Iterator、pull cursor、
  Publisher与async result不在V1 surface；
- Group不提供跨Table transaction；runtime不是database、ORM、workflow engine、
  persistence或distributed runtime；
- public GitHub与Maven Central保持`not-selected`；Codex Cloud development不是
  当前release目标。

## 3. 防回归入口

- [当前一致性基线](current-conformance.md)；
- [V1 release governance](../../reports/java-v1-release-governance-report.md)；
- [Implementation Map](../implementation-map/README.md)；
- [Validation Gate治理](../engineering/validation-gates.md)。
