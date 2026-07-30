# 已知差距与处置

类型：Conformance

状态：正式

Owner：SOMA Java 一致性审查

实现核对基线：当前`1.0.0` successor candidate

事实范围：当前open gap、受限目标与Owner处置

非事实范围：历史已关闭差距、自动授权release、扩大支持矩阵或重新定义Design

最后审查日期：2026-07-30

## 1. 未闭合差距

当前没有未裁决的Blueprint/Design/Code差距。冻结前审计发现的DataFlow integral
overflow policy已由Product Owner选择fail-closed checked arithmetic，并已固化到
Transformation Design、raw/closed expression、scalar/parallel reduction、
prefix、Group、Window、Expanded、public Javadoc、consumer Guide与canonical
contract；transformation/kernel protocol已分别升级为v5/v6。

上一精确candidate的同SHA Full、package/reproducibility、security/provenance与
support-matrix artifact仍可追溯，但不能外推到当前successor candidate。DataFlow
v5已在clean executable commit完成固定3-fork且未放宽threshold。三个reference
application的profiling/规模专题又关闭了generated scratch allocation和Industrial
重复refresh读取；九个profile已完成clean 3-fork Full，业务checksum保持。
Evidence closure不在source中预写passed：本报告所在最终clean immutable SHA只有在
Full、application与runtime-scale G5、independent consumer、package/security、
support matrix、remote qualification和下载bundle checksum全部通过后，条件式
G6 sign-off才生效；任一缺失或失败即blocked。

项目组织cutover当前还有一个非产品语义缺口：两份产品DOCX的OOXML正文仍引用
切换前路径。文档Gate已能fail closed地识别它；在完成最小路径替换、逐页渲染
复核、Temporary退役和最终同SHA验证前，candidate admission保持blocked。该缺口
不授权修改Blueprint、Design、public API或runtime实现。

已关闭差距由Git和当时Report保存，不在current Conformance维护历史清单。新的真实
差距必须重新登记，不能用placeholder、旧vendor、working-tree hash、smoke或
单一环境文本输出替代。

## 2. 受限目标，不是差距

- 性能与规模只适用于记录的Corretto/macOS/aarch64和精确workload；其他环境需要
  独立baseline/qualification，不能从Linux build/contract或单机结果外推；
- Industrial 1M fixed-machine diagnostic优化后仍显示frontier重算的非线性单位
  成本；这是reference application算法/profile边界，不构成SOMA V1任意调度模型
  的线性复杂度承诺；
- RTD的stable-order primitive sort是当前语义barrier；没有新profile证明前，
  不以额外索引、并发Table或不稳定顺序改写换取局部benchmark数字；
- Grassing用受限retained scratch换取显著更低的transient allocation；caller仍
  必须按实际population增长申报Plan，不能把当前baseline当作任意增长保证；
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
- [V1 release governance](../reports/java-v1-release-governance-report.md)；
- [Implementation Map](../implementation-map/README.md)；
- [Validation Gate治理](../process/validation-gates.md)。
