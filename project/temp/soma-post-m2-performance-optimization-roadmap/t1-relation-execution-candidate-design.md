# T1 Relation Physical Execution Candidate Design

类型：Temporary / Bounded Candidate Design / Active Slice

状态：`PASS / IMPLEMENTED / QUALIFIED / SLICE_CLOSED`

日期：2026-08-13

Owner：T1当前基线、热点归因、有限实现设计、资格与退出结论

## 1. 目标与边界

T1只优化现有Equality Relation的正常Index lookup路径，不改变Canonical IR、公开/generated API、
Join kind、encounter order、null/duplicate语义、Reference Interpreter、scheduler或资源Owner。

成功标准：

- 消除profile证明的重复typed equality与静态compatibility检查；
- current benchmark的Join count、filtered Join、Semi/Anti保持fingerprint正确并降低CPU/wall-clock；
- optimized路径与独立Reference Interpreter差分等价；
- 不增加per-row allocation、persistent state、第二执行路径或新抽象体系；
- 10K路径不出现有意义的固定成本退化，1M/10M正常路径不退化。

以下候选不进入T1：participant-local Relation state、并行morsel merge、新Join算法、Index存储变更、
compression策略、public hint或cost model扩张。只有本次有限去重仍不足且新profile证明收益大于复杂度时，
这些方向才可成为未来独立Candidate。

## 2. Current baseline与profile

固定事实：

- HEAD：`245f624`（production code与M2基线`fe402ef`相同）；
- JDK：Amazon Corretto 8；JVM：`-Xms2g -Xmx8g -XX:+UseParallelGC`；
- schema：1M `KernelRecord`，500K `RouteWeight`，`RouteWeight.route`为secondary Index；
- 分布：每个左侧route命中两个右侧locator；AUTO compression；parallelism 8；
- 测量：2次inner warmup、5次sample，async-profiler CPU；全部fingerprint PASS。

1M representative median：

| Operation | Baseline |
|---|---:|
| Equality Join count | 250.913 ms |
| Equality Join typed filter + primitive sum | 239.674 ms |
| explicit parallel filtered Join | 239.708 ms |
| Semi Join count | 134.187 ms |
| Anti Join count | 158.138 ms |

CPU profile的主要Relation leaf包括`RleColumn.raw`、`joinFieldEquals`、`joinCompatible`、
`TableChunkDirectory.get/digit`、`IdentityHashIndex.Shard.findJoin`和`conditionsEqual`。这说明当前
Index lookup命中后仍重复读取/decode同一个logical Field，而静态Field shape compatibility也进入了
per-candidate hot loop。

## 3. 信息流与重复工作的根因

当前单Field Index Join：

```text
GeneratedRelation.validateCondition
    -> 已证明left/right Field shape与equality合同兼容

每个left locator
    -> IdentityHashIndex.firstJoin
        -> hash probe
        -> findJoin
            -> joinFieldEquals(first bucket member, left row)
                -> 再次joinCompatible
                -> typed equality
        -> 返回一个已经证明与probe Field相等的Bucket
    -> Relation candidate loop
        -> conditionsEqual
            -> 对同一个单Field再次joinFieldEquals
                -> 再次joinCompatible
                -> 再次typed equality
```

Index Bucket的成员由Index维护合同保证拥有同一个完整logical Field值；`findJoin`已通过hash与typed
equality选中正确Bucket。因此，对该Bucket中的每个locator再次验证同一个单Field条件不增加正确性。
Field compatibility则是pipeline construction时已经验证且此后不可变化的schema事实。

## 4. Candidate Design

### 4.1 Prevalidated Join equality kernel

`GeneratedTableLayout`提供internal-only的prevalidated Join equality入口。该入口只执行null与leaf typed
equality，不在hot loop重复`joinCompatible`。它的唯一合法caller必须来自已经通过
`GeneratedRelation.validateCondition`的Canonical Relation，或者由Index自身持有的同一schema合同。

这不是新IR节点或public abstraction，只是把静态验证从per-row kernel移回既有pipeline construction
边界。方法命名必须显式包含`Prevalidated`，防止其他caller误用。

### 4.2 Index Bucket作为单Field membership proof

当Physical Relation Plan选择`RIGHT_INDEX_LOOKUP`时，现有Planner已经保证：

- Join condition只有一个logical Field；
- 右侧Key/Index就是该Join Field；
- `firstJoin`只有在完整typed equality成立时才绑定Bucket；
- Bucket内所有locator共享同一Index logical value。

因此candidate loop不再执行`conditionsEqual`；Hash build/probe和其他Relation kernel仍保留完整
conditions equality。typed side filter、callback barrier、outer/semi/anti行为均不改变。

### 4.3 明确不增加的机制

- 不缓存typed key对象，不改变Index Bucket表示；
- 不建立Join descriptor class、generic comparator hierarchy或第二Physical operator；
- 不改变AUTO compression，不为benchmark关闭RLE；
- 不新增parallel state；现有explicit parallel无收益是事实，但不是本slice要用复杂机制解决的问题；
- 不删除Reference Interpreter的重复检查；oracle继续按原始逻辑语义独立执行。

## 5. 不变量与失败边界

- `INV-9`：Reference Interpreter仍独立执行完整condition equality；
- `INV-12`：Index Bucket只提供已经由正式Index维护合同证明的membership；duplicate完整保留；
- `INV-13`：canonical left order与Bucket locator order不变；
- `INV-16`：无新增allocation或resource state，因此whole-operation admission不变；
- `INV-17`：不改变callback/failure包装与operation lifecycle；
- null永不Join匹配：`firstJoin`仍在选择Bucket前执行现有null/hash/equality合同。

## 6. 实施与资格

单一implementation delta：

1. 将现有`joinFieldEquals`改为明确的prevalidated internal kernel并删除静态重复验证；
2. `IdentityHashIndex.findJoin`使用该kernel；
3. Relation Index candidate跳过第二次condition equality；Hash路径保持原样；
4. 增加single-field Index Join与Reference differential，覆盖duplicate、null、typed side filter、
   Inner/Semi/Anti；既有Outer/Hash tests保持通过。

证据顺序：targeted unit/differential -> 1M相邻A/B -> 10K防固定成本退化 -> 10M capacity确认 ->
`./scripts/check.sh`。同一规模只在implementation变化后重测，不重复无新信息的profile。

退出规则：如果1M representative paths未获得稳定收益，撤销production delta并以`NO_CHANGE`关闭；
如果需要改变Index/Join语义、公开surface或正式Design，立即停止并请求Product Owner裁决。

## 7. 实施结论

实现严格保持了Candidate边界：

- `joinFieldEqualsPrevalidated`只删除construction阶段已经完成的shape/equality compatibility复验；
- `RIGHT_INDEX_LOOKUP`只删除Index Bucket已经证明的第二次single-Field equality；
- Hash/Cross/Reference路径、Canonical/Physical topology、resource estimate和public/generated API未变化；
- differential新增Inner/Semi/Anti，既有duplicate、null、typed side filter、Full与relation-derived
  pipeline证据继续通过。

相同固定主机上的结果：

| Scale / Operation | Before | After | Change |
|---|---:|---:|---:|
| 1M Equality Join count | 250.913 ms | 108.926 ms | -56.6% |
| 1M typed filtered Join | 239.674 ms | 183.374 ms | -23.5% |
| 1M Semi | 134.187 ms | 94.262 ms | -29.8% |
| 1M Anti | 158.138 ms | 83.505 ms | -47.2% |
| 10M Equality Join count | 1.628 s M2 snapshot | 0.954 s | -41.4% |
| 10M Semi | 1.328 s M2 snapshot | 0.891 s | -32.9% |

10M typed filtered Join为1.814 s，explicit parallel为1.785 s；这再次证明当前Relation lookup是
memory/decode-bound正常路径，不能仅为提高CPU占用而在T1增加participant-local并行机制。10K Join
count为0.786 ms，没有新增固定成本。

资格结果：runtime 90 tests `PASS`；full `./scripts/check.sh`与package qualification `PASS`；10K、
1M、10M benchmark fingerprint全部`PASS`。T1到此停止，不继续追逐剩余微热点。
