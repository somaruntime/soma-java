# Physical Execution Engine M2 P1 资格

类型：Conformance / Implementation Qualification

状态：`PASS / P1_COMPLETED / P2_ACTIVE`

日期：2026-08-12

Owner：最小Physical Pipeline骨架、finite Chunk kernel replacement与P1 evidence

## 1. 实现结论

`CanonicalRowPhysicalPlan`现在唯一拥有data-only `CanonicalPhysicalPipeline`，其最小纵向结构包含source、
一个stateless `CanonicalPhysicalSegment`、typed scalar或Chunk-specialized kernel、bounded
`CanonicalPhysicalMorsel`与精确terminal sink。原`vectorDecision`不再作为PhysicalPlan并行字段存在，finite
Chunk decision只由Segment持有。

Table count、integral Field sum与ordered `long[]`继续消费既有representation-native kernel；parallel
Chunk path现在把实际participant上限写入Morsel descriptor。只有真正的Row count会选择count kernel，关闭了
其他Row terminal偶然携带count decision的旧漂移。Explain增加segment/kernel/morsel/sink安全诊断。

## 2. 边界

- 未新增public/generated API、artifact、dependency或Canonical node；
- scalar family在P1只使用一个明确`TYPED_SCALAR`边界，未预建Breaker hierarchy；
- Reference Interpreter未读取PhysicalPlan；
- Point、Group、Relation与Selection行为未改变；
- scheduler仍为唯一`CanonicalParallelWorkScheduler`。

## 3. Evidence

- P1 vector targeted matrix：8 tests，0 failure/error；覆盖PLAIN/encoded/RLE/overlay、wide overflow、
  bounded pool、resource preflight、empty/all/no-match与P=1/2/4/16；
- runtime full suite：86 tests，0 failure/error；processor：34 tests，0 failure/error；
- `./scripts/check.sh`：PASS，包括Java 8 build、generated consumer、compression/metadata、三个reference
  application与local package；
- 1M `frontier-source`，3 fresh JVM runs，correctness/fingerprint PASS：Field sum 0.294 ms、parallel
  0.125 ms，materialize 0.326/0.152 ms，typed filter 3.393/0.445 ms，typed-filter materialize
  6.838/4.442 ms；与冻结Candidate同机baseline同量级，无触发性能守卫的稳定退化；
- `git diff --check`：PASS；public/generated surface无修改。

固定主机数字是qualification evidence，不是SLA。

## 4. Replacement closure

`rg`确认production不再读取`CanonicalRowPhysicalPlan.vectorDecision`；Chunk decision只能经
`physical.pipeline.segment.chunkKernel`访问。Eligibility、terminal scratch与parallel ownership仍在同一次
planner调用中形成，没有第二套physical plan或resource estimate。

## 5. Disposition

P1 exit全部满足，允许激活P2。Release/publication仍`NOT_AUTHORIZED`。
