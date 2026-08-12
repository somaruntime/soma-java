# Physical Execution Engine M2 P6 最终资格

类型：Conformance / Final Implementation Qualification / Replacement Closure

状态：`PASS / P1-P6_COMPLETED / IMPLEMENTATION_FULFILLED / NO_ACTIVE_IMPLEMENTATION_SLICE`

日期：2026-08-12

Owner：M2全局执行拓扑、P1-P6最终证据、Temporary replacement closure与claim boundary

## 1. 最终结论

SOMA已将Row、Field、Mapped、Primitive、GroupBy、Relation与Selection query部分收敛到有限、
typed的Physical Pipeline与ExecutionFrame主线：

```text
Canonical / Bound / Normalized
    -> one operation-family Physical Pipeline
        -> finite streaming Segment(s)
        -> optional finite Breaker
        -> terminal sink or mutation handoff
        -> one whole-operation ResourceEstimate
            -> admission
                -> one ExecutionFrame
                    -> specialized typed kernels
                    -> existing caller-participating scheduler
```

P1–P6全部完成。Reference Interpreter仍直接解释Bound semantics，不读取Physical descriptor；
Storage仍是authoritative data Owner，Mutation仍是validation与atomic publication Owner。没有新增
public/generated API、artifact、dependency、scheduler、runtime codegen或SOMA Engine workflow。

## 2. P1–P5 纵向闭环

| Slice | 正式结果 | 资格 |
|---|---|---|
| P1 | Table count、integral Field sum、ordered `long[]` 进入唯一finite Pipeline/Kernel/Morsel decision | [PASS](physical-execution-engine-m2-p1-qualification.md) |
| P2 | Row/Field/Mapped/Primitive stateless typed Segment、callback barrier与shape transition收敛 | [PASS](physical-execution-engine-m2-p2-qualification.md) |
| P3 | distinct、stable reorder、bounded top等finite Breaker与admitted Frame state收敛 | [PASS](physical-execution-engine-m2-p3-qualification.md) |
| P4 | GroupBy key/value projection、HASH_AGGREGATE Breaker、capacity与result sink收敛 | [PASS](physical-execution-engine-m2-p4-qualification.md) |
| P5 | bounded binary Relation build/probe/lookup/downstream与Selection mutation handoff收敛 | [PASS](physical-execution-engine-m2-p5-qualification.md) |

## 3. P6 全量正确性与交付证据

Java 8完整`./scripts/qualify.sh`于当前checkout一次性通过：

- runtime：90 tests，0 failure/error；其中`GeneratedTableTest` 80 tests；
- processor：34 tests，0 failure/error；112 Tables / 448 Fields / 224 Indexes scale fixture通过；
- compression/metadata、full regeneration、Java 8 generated consumer、negative/public surface、linkage均通过；
- Scheduling 100K FJSP、Grassing Simulation headless与Real-time Dispatch三个reference application实际运行`PASS`；
- 1M默认benchmark：18 records / 6 groups，correctness与fingerprint全部`PASS`；
- local package、source bundle排除内部证据、checksums、SPDX 2.3 SBOM、provenance、source/javadoc、
  runtime dependency与forbidden reflection/legacy surface扫描全部`PASS`；
- `git diff --check`：`PASS`。

Qualification仍是local non-publishing evidence，不等价于GitHub Release、Maven publication、签名或正式
release声明。

## 4. 规模与复杂度资格

P1–P5已对每个changed family完成1M fresh-JVM differential与相邻A/B。P6不重复相同输入，
补齐两个规模边界：

- 10K：source、stateful、relation/group、mutation，3 fresh JVM runs，12 records，全部
  correctness/fingerprint `PASS`；
- 10M：同样四个family，4 records，全部correctness/fingerprint `PASS`；
- 10M代表median：Field sum 2.802 ms，typed Table filter 33.562 ms，Field top 92.390 ms，
  low/high GroupBy 165.174/492.188 ms，Join count 1.628 s，Semi 1.328 s，Selection update
  465.310 ms，remove 2.679 s；
- 全部容量路径无correctness/fingerprint failure。这些固定主机数字是qualification snapshot，不是
  SLA或一亿行承诺。

P5相邻A/B中Relation和Selection changed hot path均未触发`15% + 2 ms`守卫；P6没有发现
新的O(N) copy、per-row boxing/allocation、double traversal或扩大串行区。

## 5. Owner 与replacement closure

最终source盘点结论：

- Physical Plan是operation-family Pipeline、Segment、Breaker、Kernel、Morsel与whole-operation
  ResourceEstimate的唯一decision Owner；
- `PhysicalRelationPlan`不再平行保存algorithm/access/build-side truth，binary pipeline拥有这些决定；
- production Row/Mapped/Primitive执行器不再拥有family-local stateful algorithm selection；planner中的
  finite stage分段和Reference Interpreter中的`nextStateful`仍属于各自正当Owner，不是第二套
  production execution；
- Group/Relation actual hash、matched side、cursor、typed buffer只在whole-operation admission后由Frame
  创建；
- Selection只从Physical execution获得frozen membership/write-set/remove-plan handoff，Mutation/Storage继续
  独占validation与publication；
- parallel path只使用已有caller-participating scheduler与common/application executor合同，未增加第二
  pool或scheduler；
- Reference Interpreter不读取PhysicalPlan，也不是production fallback。

## 6. Temporary 与项目状态

Candidate fingerprint和freeze-time baseline已由
[晋升/准入记录](v1-physical-execution-engine-m2-promotion-readiness.md)保存；精确长期合同已由Planning、
Execution、Architecture和Core Design Owners拥有；实施顺序由
[冻结P1-P6计划](../engineering/physical-execution-engine-m2-implementation-plan.md)保存。因此Candidate Temporary已没有
独立Owner责任，于P6删除，不保留parallel Design/history navigation。

M2现在为`IMPLEMENTATION_FULFILLED / P1-P6_COMPLETED / NO_ACTIVE_IMPLEMENTATION_SLICE`。
G1–G10继续`PASS`；当前没有active bounded governance topic。SOMA Engine仍是独立queued product
concept，不是本次实施的隐性输入。

## 7. Claim boundary

本记录证明M2 internal responsibility revision的实施、正确性、资源、并行、性能与replacement
closure成立。它不声明所有operation已向量化，不准入general DAG、dynamic spill、runtime codegen、
ordered access、application frontier或SOMA Engine，也不授权发布。
