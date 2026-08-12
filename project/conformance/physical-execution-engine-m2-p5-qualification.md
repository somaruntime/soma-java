# Physical Execution Engine M2 P5 资格

类型：Conformance / Implementation Qualification

状态：`PASS / P5_COMPLETED / P6_ACTIVE`

日期：2026-08-12

Owner：bounded binary Relation topology、Relation downstream与Selection mutation handoff的P5 evidence

## 1. 实现结论

Relation的`LEFT_SCAN + RIGHT_SCAN/RIGHT_INDEX -> NESTED_CROSS/RIGHT_INDEX_LOOKUP/
RIGHT_HASH_BUILD_PROBE -> RELATION_PAIR/LEFT_LOCATOR`已经成为一个closed、data-only的bounded binary
Physical Pipeline。Planner一次固定source、kernel、output shape、residual/filter barrier、output upper bound与
resource peak；Frame在whole-operation admission后创建right hash、matched-right与Index cursor。executor只
消费Plan kernel，不再根据Table侧状态重新选择Index/Hash/Cross。

Relation直接Mapped/Primitive downstream也进入finite descriptor；mapped/primitive filter、map、hash distinct、
stable sort与slice由Plan记录，executor按descriptor执行。Semi/Anti relation-left handoff现在把Mapped、Primitive
或Group terminal requirement传入同一个Row Physical Plan，不再在binary边界退化为仅Row topology。

Selection Planner使用`MUTATION_HANDOFF` sink，并把frozen locator、write set/remove plan staging与query scratch
合并进一个whole-operation ResourceEstimate。admission成功后，Frame持有`CanonicalMutationHandoff`；Mutation
Owner继续独占validation、zero-match/zero-change、fault handling与atomic StateRoot publication。Point mutation、
cross-Table transaction和产品语义均未改变。

## 2. 边界

- 保持六种既有Join kind、null-never-match、duplicate Cartesian和canonical order；
- 没有新增Join、Batch、transaction、universal tuple/DAG、第二scheduler或dependency；
- Reference Relation仍使用独立nested-loop oracle，不读取Physical descriptor；
- Relation parallel请求保持既有contract，本slice没有虚构新的parallel Join algorithm；
- Selection publication不是普通kernel，Storage/Mutation Owner没有被Physical Pipeline取代。

## 3. Evidence

- runtime完整`GeneratedTableTest`：80 tests，0 failure/error；新增测试覆盖Index/Cross binary topology、
  direct Relation Mapped/Primitive stateful downstream、relation-left Mapped Breaker和Selection handoff；
- `group-relation.sh`：PASS；runtime 90 tests、processor 34 tests、Java 8 consumer/negative/generated surface均PASS；
- 既有Selection矩阵重放覆盖zero match/change、callback/resource/fault failure、dense remove、encoded fallback、
  sidecar与one-generation publication；
- 1M `frontier-relation`与`frontier-mutation`，3 fresh JVM runs，correctness/fingerprint PASS；
- 相邻提交`e4fa83a`/当前同机A/B：Join 163.150/170.727 ms（+4.6%）、filtered
  243.291/245.855 ms（+1.1%）、Semi 137.083/139.551 ms（+1.8%）、Anti
  152.544/154.309 ms（+1.2%）；Selection update 86.213/86.890 ms（+0.8%）、remove
  220.809/225.098 ms（+1.9%），均未触发`15% + 2 ms`守卫；
- `git diff --check`：PASS；public/generated surface无修改。

固定主机数字是qualification evidence，不是SLA。A/B前一次current Join 235.488 ms没有在紧邻重放中复现，
因此归类为host/JIT snapshot，不作为代码因果。

## 4. Replacement closure

`PhysicalRelationPlan`不再平行保存algorithm/access/build-side truth；唯一binary pipeline拥有这些决定。
`GeneratedRelation.rightLookup`只执行已选择的RIGHT_INDEX kernel。Relation Mapped/Primitive stage algorithm由
downstream descriptor拥有；Selection不再由`MutationOperation`在Physical Plan之外拼接第二份总scratch，
而是由Plan拥有共同峰值、Frame拥有冻结handoff、Mutation/Storage拥有提交。

## 5. Disposition

P5 exit全部满足，允许激活P6全局closure。Release/publication仍`NOT_AUTHORIZED`。
