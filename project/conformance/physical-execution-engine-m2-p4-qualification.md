# Physical Execution Engine M2 P4 资格

类型：Conformance / Implementation Qualification

状态：`PASS / P4_COMPLETED / P5_ACTIVE`

日期：2026-08-12

Owner：GroupBy HASH_AGGREGATE Breaker、typed Group state lifecycle与P4 evidence

## 1. 实现结论

GroupBy optimized normal path已经进入唯一`CanonicalPhysicalPipeline`。Physical Plan现在显式拥有
`HASH_AGGREGATE/GROUP_HASH` Breaker、`GROUPED_RESULT` shape与`GROUP_RESULT` sink；它一次固定
Group key/value projection之后的state边界和保守capacity，而不是让executor在执行中重新发现算法。

实际Group bucket/link/aggregate state只在whole-operation temporary admission成功后由
`CanonicalRowExecutionFrame`创建和持有。Key Field以source upper bound作为容量上界；有发布统计的Index
Field使用`min(source upper bound, distinctCount)`；其他Field采用保守但有限的内部上界。具体hash
coefficient、threshold与table implementation仍是private算法，不升级为长期Design合同。

Reference Interpreter继续直接解释Bound Canonical stages，不读取Physical Breaker或capacity hint。Relation-left
GroupBy在P5 binary handoff完成前保留既有保守capacity路径；它不是第二套Group算法，也不改变语义。

## 2. 边界

- 没有新增public/generated API、artifact、dependency或parallel aggregate capability；
- 没有引入generic tuple、universal aggregation container或runtime code generation；
- Plan只拥有algorithm/resource decision，Frame拥有operation-local state，executor只执行descriptor；
- numeric、null、first encounter order、resource/failure与stream one-shot合同保持不变；
- relation build/probe与Selection mutation handoff仍属于P5。

## 3. Evidence

- targeted topology、Reference differential与relation-left compatibility：3 tests，0 failure/error；
- primitive/reference/null/numeric/resource GroupBy矩阵：4 tests，0 failure/error；
- runtime完整`GeneratedTableTest`：79 tests，0 failure/error；
- `./scripts/check.sh`：PASS；processor 34 tests、三个reference application、package/SBOM/provenance均PASS；
- 1M `frontier-relation`，3 fresh JVM runs，correctness/fingerprint PASS：low-cardinality GroupBy
  17.778 ms、high-cardinality 50.195 ms、parallel low/high 17.422/49.326 ms；相对既有正式frontier
  23.513/63.842 ms未触发性能守卫；
- `git diff --check`：PASS；public/generated surface无修改。

固定主机数字是qualification evidence，不是SLA。

## 4. Replacement closure

optimized normal GroupBy不再由`CanonicalGroupingQueryOperation`独自拥有capacity与state creation decision。
`CanonicalPhysicalPipeline`是Group topology与expected state capacity的唯一production Owner；executor继续拥有
typed aggregation mechanics，但不能重选Breaker或在admission之前创建state。Reference保持独立。

## 5. Disposition

P4 exit全部满足，允许激活P5。Release/publication仍`NOT_AUTHORIZED`。
