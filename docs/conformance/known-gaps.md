# 已知差距与处置

类型：Conformance

状态：正式

Owner：SOMA Java 一致性审查

实现核对基线：包含本文件的`1.0.0` private-source sign-off commit

事实范围：当前open-gap zero state、受限目标与Owner处置

非事实范围：历史已关闭差距、自动授权release、扩大支持矩阵或重新定义Design

最后审查日期：2026-07-29

## 1. 未闭合差距

当前没有已知且未裁决的 Blueprint/Design/Code/Evidence 差距。Release readiness
已经由同SHA Full、package/reproducibility、security/provenance、support matrix与
条件式Owner sign-off闭合；精确candidate和run由retained qualification artifact
记录。

已关闭差距由Git和当时Report保存，不在current Conformance维护历史清单。新的真实
差距必须重新登记，不能用placeholder、旧vendor、working-tree hash、smoke或
单一环境文本输出替代。

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
- AI Skill的negative/anti-pattern与第二独立宿主行为验证已由Product Owner对V1
  明确waive；这不构成当前release gap，但禁止声明multi-tool support，未来扩大
  support前必须重新获得真实宿主证据。

## 3. 防回归入口

- [当前一致性基线](current-conformance.md)；
- [V1 release governance](../../reports/java-v1-release-governance-report.md)；
- [Implementation Map](../implementation-map/README.md)；
- [Validation Gate治理](../engineering/validation-gates.md)。
