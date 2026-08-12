# T2 GroupBy Physical Execution Candidate Design

类型：Temporary / Bounded Candidate Design / Active Slice

状态：`PASS / IMPLEMENTED / QUALIFIED / SLICE_CLOSED`

日期：2026-08-13

Owner：T2当前基线、热点归因、有限typed aggregate kernel设计、资格与退出结论

## 1. 目标与边界

T2优化现有typed GroupBy aggregate的正常production路径。它不改变Group key equality、首次出现
order、aggregate/numeric/overflow、result type、Canonical IR、public/generated API或Reference
Interpreter。

本slice不引入participant-local Group state、parallel merge、Index-assisted grouping、sort grouping、
新statistics或通用aggregate框架。原因是current profile已经暴露出一个更直接的Owner错误；必须先
删除已知的per-row generated callback成本，再判断复杂并行机制是否仍值得设计。

## 2. Current baseline与profile

固定环境沿用T1，current production delta只影响Relation。GroupBy 1M/10M结果：

| Operation | 1M | 10M |
|---|---:|---:|
| low-cardinality byte key + long sum | 17.858 ms | 170.152 ms |
| high-cardinality String key + long sum | 60.042 ms | 520.252 ms |
| explicit parallel high-cardinality | 49.217 ms | 499.015 ms |

profile把Group样本归因到reference equality/hash、Chunk directory、`GroupState.groupFor`，同时也出现
`CallbackExecutionScope`、ThreadLocal、`callbackMapLong`与GeneratedQueryCursor。这些callback成本
来自aggregate value读取，而不是application callback。

## 3. 根因

公开generated API只允许：

```java
table.groupBy(table.key).sum(table.numericField)
```

processor已验证Field identity，Canonical Group operation也拥有`valueFieldIndex`和numeric kind。
但当前processor额外生成`() -> view.numericField()`，production `GroupState.add`对每行调用：

```text
callbackMapLong/Double
    -> queryCursor.enter
    -> CallbackExecutionScope ThreadLocal enter
    -> generated lambda
    -> generated View accessor
    -> ThreadLocal exit
    -> queryCursor.leave
```

这把schema-known Field kernel错误地执行成了opaque callback。它增加CPU指令与状态切换，也模糊了
Physical Plan已经拥有的leaf demand。Reference Interpreter使用该generated mapper仍有独立oracle
价值；production optimized path不应继续使用它。

## 4. Candidate Design

### 4.1 Typed aggregate Field access

在operation binding后一次解析aggregate Field的leaf、slot和primitive kind。Group visitor把locator直接
交给typed aggregate kernel，kernel从bound `TableChunkDirectory`读取byte/short/char/int/long或
float/double，并执行现有exact accumulator。

该信息是现有Canonical operation与schema layout已经拥有的正式信息，不增加IR节点或public surface。
每行不再执行Field shape验证、cursor、ThreadLocal或generated callback。

### 4.2 Reference独立性

Reference Interpreter继续通过原generated mapper读取aggregate value。这样optimized direct Field read
与reference generated accessor是两条独立读取链，numeric result/failure差分可以发现leaf mapping错误。

### 4.3 不改变的合同

- integer继续使用signed 128-bit accumulator并只在结果发布时裁决long overflow；
- floating继续保存canonical sequence并采用现有1024-element block/pairwise sum；
- min/max/average/summary与NaN/Infinity合同不变；
- group state只在resource admission后创建，scratch估算保持保守；
- group首次出现order、null group与typed detached result不变；
- key materializer只在每个最终group执行一次，暂不改变。

## 5. 实施与资格

1. 在`group()`绑定一次numeric leaf descriptor；
2. 将`GroupState.add`分成optimized typed read与reference mapper read；
3. processor暂时保留generated mapper供Reference oracle使用，不改变ABI/source golden；
4. 增加integer、floating与overflow optimized/reference差分；
5. 运行runtime tests、1M相邻测量、10K防退化、10M确认与full check。

若正常GroupBy没有稳定收益则撤销；若剩余profile仍证明participant-local state有高价值，只把它记录为
未来Candidate，不在T2追加复杂度。

## 6. 实施结论

production GroupBy现在在binding后一次解析aggregate Field的leaf/slot/kind，per-row breaker直接读取
bound directory；Reference仍通过generated mapper/cursor读取。processor输出、public/generated ABI、
Canonical operation和Physical topology均未改变。

| Scale / Operation | Before | After | Change |
|---|---:|---:|---:|
| 1M low-cardinality long sum | 17.858 ms | 12.324 ms | -31.0% |
| 1M high-cardinality long sum | 60.042 ms | 44.886 ms | -25.2% |
| 10M low-cardinality long sum | 170.152 ms | 121.705 ms | -28.5% |
| 10M high-cardinality long sum | 520.252 ms | 440.572 ms | -15.3% |
| 10K low/high | 0.622 / 1.287 ms | 0.448 / 1.177 ms | 无固定成本退化 |

新增差分覆盖reference-key integer sum、floating sum、Infinity与integer overflow；runtime 91 tests、
10K/1M/10M fingerprint、full `./scripts/check.sh`与package qualification全部`PASS`。

explicit parallel high-cardinality在10M为437.510 ms，与sequential 440.572 ms等价。当前剩余成本主要是
reference-key hash/equality、Chunk access和key materialization；participant-local state会增加多份hash
state、merge、order恢复和resource peak，却没有当前收益证据。因此T2在typed kernel处停止，复杂并行
GroupBy不晋升。
