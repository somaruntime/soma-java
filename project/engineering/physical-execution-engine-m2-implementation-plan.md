# SOMA Physical Execution Engine M2 实施计划

类型：Engineering Plan

状态：`FROZEN_BASELINE / IMPLEMENTATION_AUTHORIZED / P1-P4_COMPLETED / P5_ACTIVE`

日期：2026-08-12

Owner：M2 P1-P6顺序、slice exit、性能守卫与停止规则

## 1. 授权与边界

Product Owner已审核冻结Candidate，并于2026-08-12授权Codex自主完成本专题。授权覆盖repository-local
Design晋升、production实现、test/benchmark/profile、Conformance与本地干净提交；不包含GitHub
Release/Package、Maven publication、签名或正式release声明。

实施不得改变public/generated API、Canonical语义、Reference合同、Java 8、two-artifact topology或已准入
dependency。任何此类变化、正确性与性能取舍、证明链无法闭合都必须停止并请求裁决。

## 2. 总则

- 一次只有一个active slice；
- 纵向替换planning、resource、frame、kernel与evidence，不铺空层级；
- Reference直接解释Bound语义，永不消费PhysicalPlan或成为production fallback；
- actual state只在whole-operation admission后创建；
- 复用唯一caller-participating scheduler；
- 保留primitive/locator/representation specialization，禁止universal boxed executor；
- 每个slice完成targeted evidence、replacement scan、Conformance与干净本地提交后再进入下一项；
- 没有新信息时不重复review、qualification或profile。

## 3. P1-P6

### P1 — 最小Physical Pipeline骨架

把Table count、integral Field sum与ordered `long[]`已有finite Chunk kernel纳入唯一Pipeline/Segment/
Kernel/Morsel plan；Frame按descriptor执行，删除重复eligibility/resource/parallel decision。验证representation、
order、overflow、sequential/parallel/Reference、resource/quiescence与1M防退化。

### P2 — Unary stateless Segments

覆盖Row、Field、Mapped与Primitive的typed filter/projection/map/count/sum/materialization，显式保留callback
barrier与element-shape transition。验证capability matrix、callback exactly-once/failure、required leaves、
10K fixed cost和1M allocation/profile。

### P3 — Unary stateful Breakers

把distinct、stable sort、top与materialization的algorithm/resource/state提升到finite Breaker descriptor与
Frame；保持typed buffers。验证first/stable/top等价、growth peak、Reference differential与1M stateful profile。

### P4 — GroupBy Breaker

把GroupBy key/value projection、hash aggregation、capacity与result sink纳入完整physical topology；实际
bucket/link/aggregate state只在Frame创建。先迁移sequential，只有证据成立才准入partial parallel。验证
primitive/reference/null/order/numeric/resource与low/high-cardinality profile。

### P5 — Relation与Selection handoff

把Relation build/probe/lookup/output和state纳入bounded binary Pipeline；Selection只交付frozen membership/
write set/remove plan，publication仍归Mutation/Storage。验证全部Join kind、pushdown/residual、order、resource、
atomic publication与1M relation/mutation profile。

### P6 — 全局closure

完成全family differential、Java 8/ABI/negative、resource/failure/quiescence、10K/1M/10M与三个reference
application；更新正式Owner和Gates，删除Temporary并证明无旧physical/resource/morsel truth。

## 4. 性能守卫

Changed hot path使用same host/JDK/JVM/rows/parallelism的fresh-JVM A/B。优先检查复杂度、allocation class、
temporary peak与profile归因；broad comparator继续使用`15% + 2 ms`，sub-ms路径出现稳定超过20%且绝对超过
0.05 ms的退化必须解释。新增O(N) copy、per-row allocation/boxing、double traversal或扩大串行区均阻止
slice关闭。

## 5. Stop rules

需要general DAG、universal Batch/Vector/Tuple、第二scheduler、runtime codegen、dynamic spill/lease、第三
artifact、新dependency、SOMA Engine workflow或产品语义变化时立即停止。若一个slice必须同时改两个以上
未迁移family才能运行，先缩小slice；若证据停止变化，停止重复执行并诊断。

## 6. Baseline provenance

冻结Candidate与完整设计过程保存在
[`project/temp/soma-physical-execution-engine-m2-governance/`](../temp/soma-physical-execution-engine-m2-governance/README.md)，
正式设计由Planning、Execution、Architecture与Core Owners拥有。Current状态与每个slice证据由
Conformance拥有；本计划冻结顺序，不作为完成状态的第二Owner。
