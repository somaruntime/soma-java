# Production Implementation Architecture Design

类型：Design

状态：Active Baseline

正式事实源：是

Owner：SOMA Java V1 production artifact/build topology、runtime component boundary、storage/Index baseline algorithm 与 publish mechanism

上游：[SOMA Java V1 产品蓝图](../blueprint/README.md)

最后审查日期：2026-08-01

## 1. 文档责任

本文关闭“产品合同已经确定，但实现者仍可能自行发明另一套架构”的空白。它唯一拥有：

- production artifact、module 与 dependency topology；
- Maven/IDE full-regeneration 的正式 carrier 和支持边界；
- generated facade、runtime kernel 与 authoritative state 的内部责任边界；
- V1 storage、Key/Index、admission、atomic publish 和 parallel scheduler 的基线算法；
- reduction、复杂度、内存峰值与实现替换准入条件。

产品语义仍由其他 Design Owner 拥有。本文中的 internal class name 是职责名，不是必须
逐字采用的 Java type；任何替换必须保持本文算法不变量并通过相同 Conformance Gate。

## 2. 最小 production topology

V1 只发布两个 production artifact：

| Artifact | Maven coordinate | 唯一责任 |
|---|---|---|
| Runtime | `io.github.somaruntime.soma:soma-runtime` | annotation、shared public API/function/metadata、runtime kernel 与 generated linkage |
| Processor | `io.github.somaruntime.soma:soma-processor` | JSR 269 schema validation、aggregation 与 generated source |

初始 Maven reactor 只包含这两个 module。Root `pom.xml` 只聚合和统一 Java 8、plugin、
dependency 与 verification 配置，不发布第三个产品 artifact。

```text
application source
    -> compile/runtime dependency: soma-runtime
    -> annotationProcessor path:   soma-processor
                                       -> exact-version soma-runtime
generated source
    -> compile/runtime linkage:     soma-runtime
```

不拆出 `soma-annotations`、`soma-api` 或 `soma-testkit`：这些 surface 没有独立
lifecycle/consumer/version，拆分只会增加组合和版本错配。V1 也不预建 Maven plugin、
Gradle plugin、benchmark、Examples 或 BOM artifact；它们在真实 capability 与 evidence
需要出现时分别准入。

`soma-runtime` 与 `soma-processor` 必须使用 exact same version。Processor 生成的
internal contract version 与 runtime 不匹配时必须在 generated class initialization
之前 fail closed。Generated source/class 是 consumer build output，不是发布 artifact。

Implementation baseline version 为 `1.0.0-SNAPSHOT`；只有 G1-G8、package/release
qualification 和 Owner sign-off 通过后才去掉 `-SNAPSHOT` 形成 `1.0.0`。本文不选择
Maven Central/GitHub Packages 等外部分发 channel，也不授权 publish。

## 3. Full-regeneration build contract

### 3.1 正式 carrier

Processor option 固定为：

```text
-Asoma.fullSourceSet=true
```

这个 option 只表示 build host 声明“本次 invocation 提交了 composition 的完整 schema
source set”；它不是 processor 推断完整性的 oracle。缺失、空值或非 `true` value 时，
processor 在创建任何 composition source 前以稳定 diagnostic 失败。

Processor 还为每个 composition 在 `CLASS_OUTPUT` 生成 UTF-8/LF、无 timestamp 的
build-only manifest：

```text
META-INF/soma/compositions/<generated-package>.properties
```

其中 `<generated-package>` 保留 dot-separated canonical package name。Key 固定为
`formatVersion`、`processorVersion`、`runtimeContractVersion`、`schemaPackage`、
`generatedPackage`、`schemaSha256`、`generatedTypes`；逐 key lexicographic order，
`generatedTypes` 同样排序。`schemaSha256` 的 input 是 UTF-8/LF canonical schema model：
Table/Value 按 canonical type name 排序，每个 type 内 Field 保持 source order，并包含
role、canonical declared type 与 annotation parameter；comment、whitespace、absolute path
不进入 hash。Manifest 用于 clean-full equivalence、stale-output 检查和诊断，不进入
application API 或 runtime metadata。

### 3.2 V1 支持矩阵

| 场景 | V1 状态 | 使用合同 |
|---|---|---|
| Maven schema add/change/delete/move | 支持 | `mvn clean verify`，完整 source set + clean generated/classes |
| Maven application-only change | 支持 | host 可增量编译；不得重新声明不完整 schema invocation 为 full |
| IDE schema change | 支持 | delegated Maven clean build；IDE native partial AP 不作保证 |
| raw partial `javac` | 不支持 | 不得设置 full-source handshake |
| Gradle incremental AP | 不支持 | 取得独立 add/change/delete/stale-cleanup Gate 后再准入 |

V1 不新增 Maven plugin 来掩盖标准 lifecycle。Consumer 文档必须把 schema change 的
clean build 作为显式合同。Processor 的 aggregating registration 不能替代 clean/full
source evidence。

## 4. Runtime component boundary

```text
generated typed facade
    -> generated access plan / specialized equality and hash
        -> runtime admission and execution kernel
            -> current StateRoot reference
                -> primitive/reference payload arrays
                -> Key/Index sidecars
                -> size/capacity/stateVersion
```

- generated facade 唯一了解 schema-specific Field path、primitive specialization、
  flatten/unflatten、Key/Index equality 和 detached object construction；
- runtime kernel 唯一拥有 admission、pipeline lifecycle、parallel scheduling、checked
  allocation、staging、publish 和 failure normalization；
- `StateRoot` 是一次 operation 绑定的 authoritative container；其中 payload array、Key/
  Index sidecar reference 和 header 只能在 Table exclusive admission 下按本文 journal/
  candidate protocol 修改；
- capacity 内 add、普通 update 与 point/small-selection remove 可以 journaled in-place，
  并以最终 immutable `PublishedHeader(size,stateVersion)` reference 更新作为成功
  publication；growth、reserve 与 large structural remove 使用 current-root replacement；
- metadata 只从 schema descriptor 和受 admission 保护的 StateRoot snapshot 构造；
- hot path 不使用 Field/constructor reflection、MethodHandle schema interpreter、
  boxed universal row、public planner 或 service-provider extension。

Generated code 只能依赖 `io.github.somaruntime.soma.internal.*` 的窄 linkage。Application
依赖 internal type 是 unsupported；internal surface 只能与 exact processor/runtime pair
一起演化。

## 5. Authoritative storage baseline

每个 Table instance 持有一个 current `StateRoot`。Root 内所有 column/sidecar length 与
capacity 一致，logical record domain 永远是 `[0, size)`。Root 通过一个 immutable
`PublishedHeader(size,stateVersion)` 拥有两个逻辑值；`[0,size)` payload 在 query/read
admission 期间固定，`[size,capacity)` 不是 logical state。StateRoot 不是对 application
暴露的 immutable snapshot：exclusive Write 可以按已预检的 journal commit 修改现有
payload/sidecar，并以最终 header-reference 写和 controller release 发布；shared Read
因此不可能观察中间状态。Growth、reserve 与需要 candidate root 的 large structural
remove 才使用 current-root replacement。普通 update 或 point/small-selection remove
不得仅为 atomicity 无条件复制整张 Table。
`stateVersion` 使用 non-negative long 和 checked increment；理论耗尽时 operation 在 staging
前以 `ARITHMETIC_OVERFLOW` 失败，不允许 wrap。

Group construction eagerly 建立 lightweight Table facade/Field endpoint，初始共享 empty
root，不分配 schema payload。首次 positive `reserve/add` 的 capacity candidate 额外取
`max(defaultCapacity, required)`；之后才使用 1.5x checked growth。Accessor、
`Soma.defaultGroup()` 和 schema metadata observation 因而不会触发 large storage allocation。

Generated `Soma` 使用 initialization-on-demand holder 创建 default Group；schema/
parallel metadata static initialization 与 holder 分离。`Soma._metadata()` 不触碰 holder，
因此不会创建 default Group/Table facade；`Soma.defaultGroup()` 或任一 default Table
shortcut 才首次创建它。

- primitive leaf 使用 exact primitive array；
- String 和 Enum 使用 schema-declared typed array；ordinary/parameterized reference leaf
  可以使用声明类型可 reify array 或 `Object[]`；generated typed access、store check 与
  public materialization 必须始终保持声明类型；
- flattened Value 的每个 leaf 独立成列，outer/nested null contract 由 generated plan
  验证；
- unused capacity 不属于 logical state，reference slot 必须在 remove/replacement root 中
  清空，避免 accidental retention。

V1 physical baseline 使用 dense slots。Add append 到 `size`；Remove 使用 deterministic
swap-compaction：从最小 selected slot 开始，用当前最后一个 live slot 填洞；若被移入的
Record 也在 frozen selection 中则继续删除，直到该位置为 survivor，再处理下一洞。
Point remove 是该算法的 single-slot specialization。它不承诺 insertion order，却让相同
起始 state/selection 的 direct、sequential 和 parallel publish 得到同一 canonical
survivor order。任何未来 free-list/Segment/其他 compaction 替代都必须先证明不改变
logical/Index order、memory bound 和 failure contract。

Canonical mapping 使用随 Record 一起移动的 frozen selected bitmap，等价伪代码为：

```text
live = size
searchFrom = 0
while (hole = firstSelectedSlot(searchFrom, live)) exists:
    last = live - 1
    if hole != last:
        move payload[last] and selected[last] to hole
    clear payload/selected at last
    live--
    searchFrom = (hole < live && selected[hole]) ? hole : hole + 1
```

被移入 hole 的 selected Record 会在下一轮继续删除。Mapping 先于 authoritative mutation
完整计算；Index/Key journal 与
candidate-root path 都必须重放这一相同 mapping，不能各自发明 compaction order。

Capacity growth 使用 checked policy：

```text
required = size + incoming
candidate = oldCapacity == 0
    ? max(defaultCapacity, required)
    : max(required, oldCapacity + oldCapacity / 2)
```

每一步使用 checked `int/long` arithmetic，并验证所有 array/byte estimate 可表示。超过
V1 `MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8`、Key/Index power-of-two/load-factor 或其他
已知 representation bound 时，在 allocation 前以 `RESOURCE_LIMIT_EXCEEDED` 失败；某个
Table 的实际 capacity ceiling 取所有 leaf/sidecar 中最严格者。SOMA 不根据瞬时 free heap
伪造可靠 memory budget；合法 allocation 仍可能抛出 `OutOfMemoryError`，不得伪装成
可恢复 structured failure。

## 6. Key 与 Index baseline

### 6.1 Equality 与 hashing

Processor 为每个合法 Key/Index 生成 specialized equality/hash：primitive 按正式 value
semantics，String/Enum 按正式 exact semantics，Value 按 leaf structural semantics。
Runtime 不 materialize Value，不调用 arbitrary Object `hashCode/equals`。

Hash input 固定使用 primitive value/bit、`String.hashCode()`、Enum ordinal、null constant
和 nested leaf composition；不使用 `Enum/Object` identity hash。Generated Value
`hashCode()` 使用同一 leaf hash grammar（float/double 用 canonical wrapper bits），保证
与 structural `equals` 一致。Bucket mixing 可以作为 internal exact-version algorithm
演化，因为 bucket order不进入 public semantics。

Hash mixing 和 table sizing 是 internal versioned algorithm，但在一个 StateRoot 内必须
一致。Bucket count 为 power of two，empty sentinel 为 `-1`，目标 load factor 不超过
`0.75`，所有 bucket/next/index array sizing 使用 checked arithmetic。

### 6.2 Key structure

Key 使用 generated open-addressed table，slot 保存 logical record position；duplicate
probe 在 mutation publish 前完成。Lookup expected `O(1)`、pathological collision
`O(N)`。Zero Key 是普通值，不能依赖 zero sentinel。

### 6.3 Non-unique Index structure

每个 Index 使用 bucket-head + `nextByRecord` chain：

```text
bucketHeads[bucket] -> record slot -> nextByRecord[slot] -> ... -> -1
```

Root build 按 record slot 从小到大插入 head，chain 因而是逆 canonical order。Index
selection execution 在 terminal binding 时收集命中 slots 并逆序，形成 canonical ordered
subsequence；绝不直接暴露 hash-chain order。Incremental add/remove/move 必须保持每条
chain 的 strictly descending slot invariant；swap-compaction 将 last slot 移入 hole 时，
受影响 Index node 按新 slot 重新插入正确位置，不能只改 slot number 破坏 order。

Add 可以增量维护 Key/Index；影响 indexed leaf 的 Update 重建对应 structure；Remove、
capacity/root replacement 重建全部 sidecar。首版优先证明 correctness 与 bounded memory，
不引入 incremental-delete tombstone、tree/range index 或 adaptive planner。

## 7. Admission mechanism

每张 Table 使用一个 non-blocking shared-read/exclusive-write controller：

- state 是一个原子整数；`>= 0` 表示 reader count，`-1` 表示 writer；
- Read 通过 CAS 增加读者，遇到 writer 立即失败；
- reader count 达到 representable maximum 时以 `RESOURCE_LIMIT_EXCEEDED` fail closed，
  不能 integer wrap；
- Write 只从 `0` CAS 到 `-1`，否则立即失败；
- terminal 的全部 worker 共享调用线程取得的一次 admission，不各自竞争；
- release 位于 `finally`，ThreadLocal callback/execution context 同样必须清除；
- owner identity、callback token 和 operation phase 检查先于用户 callback。

Controller acquire/release 与 current root/header publication 必须建立 Java Memory Model
happens-before；root/header reference 使用 final/volatile/atomic publication，不能依赖普通
data race。成功 Write release 后的 Read 必须观察完整 payload/Key/Index/header，concurrent
Read 只观察同一已发布 state。

调用者同线程 reentrancy 由 operation-scoped ThreadLocal context 检测；parallel worker
接收显式 immutable execution token。ThreadLocal 只能覆盖同步 terminal 生命周期，不得
持有 Group/Table/root 超过 invocation。

## 8. Atomic mutation protocols

### 8.1 Add

Add 先完成 input/schema/Key duplicate/resource preflight，并预构造 next
`PublishedHeader`。若 capacity 足够，可以写入
`[size]` 的尚未发布 payload slot，并记录 Key probe slot、各 Index bucket head/
`nextByRecord` 的恢复 journal；只有 payload、Key、Index 全部成功后才发布新
预构造的 `PublishedHeader(size + 1, stateVersion + 1)`。任意可恢复 failure 或 application
`RuntimeException` 必须恢复全部
sidecar journal 并清空 candidate reference slot。

发生 growth 时不得原地修改：分配 candidate root、复制、append、重建 sidecar，最后用
一次 current-root swap 发布。对未知 `Throwable` 的清理完成后原样传播；JVM `Error`
不转换为 SOMA failure。

### 8.2 Point/selection Update

执行顺序：freeze selection -> callback/Editor staging(old/new) -> validate -> 按 staged
overlay 预构建受影响 Index sidecar -> commit journal -> final header/version publish。Key
lineage没有 Editor setter；point replacement 的 Key 只用于定位 current Record，不进入
staged update；若该 Key 不存在则按正式 `MISSING_KEY` 失败。

Commit 前，next `PublishedHeader`、所有可恢复 allocation、callback、schema/equality/hash
和 Index build 都必须完成。Exclusive admission 下 commit 只执行已验证的 primitive/reference array store、
Index reference swap 和一次 `PublishedHeader` 更新；这些步骤不可被 concurrent reader
观察。若 commit 仍抛任意 `Throwable`，journal 用 staged old values/old sidecar references
恢复后再按 Failure Design 传播。全 selection no-op 不建立 Index candidate、不 publish。

因此不影响 Index 的 point/non-index selection update 为 `O(M * touchedLeaves)`，常见
point update 是 O(1)；只有 staged change 触及某个 Index Field 时才扫描 overlay 重建该
Index。V1 不为原子性无条件复制整列/整张 Table。

### 8.3 Point/selection Remove

执行顺序：freeze selection -> 建立 deterministic swap-compaction mapping -> 预构建
Key/Index mutation journal 或 survivor sidecar -> commit 或 candidate root -> final
header/root publish。Missing point
remove 与空 selection 返回 `removed == 0`，不分配、不发布、不改变 version。

Point/small selection 可以预计算所有 Key probe/Index chain mutation并 journal
target/last payload 后原地 swap；large selection 可以构造 same-capacity candidate root 与
sidecar。两条路径必须使用同一 mapping 并产生同一 survivor order。所有 hash/allocation
在 commit 前完成；reference slots `[newSize,capacity)` 清空。Threshold 是 internal
performance choice，不能改变结果/failure。

### 8.4 Reserve

`expectedRows <= capacity` 是 no-op。否则预检、分配/copy/rebuild candidate root 后一次
swap；不改变 size/order。V1 没有 shrink/trim protocol。

Query operation 在 shared admission 下捕获 current root 并只读使用到 terminal quiescent。
Mutation 以 root swap（structural）或最终 header publish（add/update）作为线性化点；
structured failure 后原 authoritative state 仍完整。

## 9. Cursor、View 与 callback scope mechanism

Sequential participant 为 current logical operand 复用 cursor/Value View。Parallel 每个
participant 拥有独立 cursor set；sort/comparator 等同时需要两个 operand 的 operation
可以使用常数个 cursor/View。因此 allocation bound 是 `O(1)` / `O(P)`，不是“永远一个
对象”。

每个 borrowed object 保存 owner identity、execution token、participant identity 和
scope epoch。每次 callback 入场更新 epoch，退场使其失效；所有 public getter/fetch/
Editor setter 验证 token。跨 callback/thread/Table/participant 或 terminal 后访问稳定
映射为 `CALLBACK_SCOPE_VIOLATION`。

Callback exit/participant completion 必须清除 borrowed cursor 中的 root/array/Table/
staging strong reference，只保留不反向引用 Group 的小型 invalid token。Application 即使
错误保存失效 View，也不能因此把整个 explicit Group 永久保留；下一次合法 callback
rebind 仅在当前 participant dynamic scope 内生效。

同一 participant 在一次 callback 内保存旧 alias，而 runtime 后续把同一 cursor 对象
推进到另一位置，是公开说明的 unsupported stateful callback pattern；V1 不为此分配
每条 Record 的对象来提供不可实现的别名稳定性。

## 10. Parallel scheduler baseline

Parallel terminal 由 caller 同步向 effective `ForkJoinPool` 提交一个 invocation task，
callback 只在 pool worker 上执行；普通 external caller 等待并负责
admission/publish/failure mapping，不额外执行 callback。若 caller 本身已经是该 pool
worker，ForkJoin execution 可以让它作为 participant，但它计入 `P`。Active SOMA
callback 始终不超过 pool parallelism `P`。

Selection 被规范化为最多 `4 * P` 个连续 canonical range，且不超过 record count。每个
participant 循环处理 range，而不是 per-record task。Range result/staging 为 worker-local，
merge 严格按 range canonical ordinal；completion race 不影响 result、order 或 failure。

- `P == 1` 或小 selection 可以使用单个 pool task；
- stateless stage 可 fuse；
- `sorted/distinct/skip/limit` 等 stateful stage 允许使用 deterministic sequential barrier；
- parallel 只承诺 bounded opt-in 与等价性，不承诺每个 pipeline 都加速；
- cancellation 只减少无必要工作，terminal 仍等待全部已提交 worker quiescent；
- primary failure 按正式 phase precedence、canonical position/work-unit 选择。

Common/custom pool shutdown、rejection或 required-task cancellation 不 fallback、不 inline sequential
执行、不重试。已接受的完整 work 正常完成后，随后 graceful shutdown 不反向撤销该
terminal；只有 required work 未被接受/完成才映射 unavailable。SOMA 不创建或关闭 pool。
Active-but-starved pool 没有 timeout detector，terminal 按 Execution Design 同步等待。

## 11. Numeric reduction baseline

为满足 sequential/parallel 逻辑结果一致和 integer overflow fail closed：

- byte/short/int sum 在 `long` accumulator 中精确累加，数学最终值超出目标 `int` range
  才失败；prefix 暂时超出但最终 cancellation 回到 range 内不失败；
- long sum 使用 generated/runtime signed 128-bit two-limb accumulator，数学最终值超出
  signed long range 才失败；Table 最多 `Integer.MAX_VALUE` 条，因此精确总和小于 96 bits；
- integer average 使用同一精确 accumulator 与 checked count直接转换成 double，不先把
  总和压回 `int/long` result range；
- float sum 使用 fixed 1024-element block/pairwise tree 的 float `+`；float average 把每个
  float 精确 widening 为 double 后使用 double tree；double sum/average 使用 double tree；
  sequential/parallel 都执行同一 canonical block/tree，不能按 worker completion order
  累加；
- min/max 按 canonical encounter order 处理 tie 和 signed-zero/NaN 的正式 Field
  semantics。

Empty `sum()` 返回对应 primitive zero；empty `average()` 返回 `OptionalDouble.empty()`。
Non-empty floating sequence 按 canonical order 切成连续至多 1024 elements 的 blocks；
每个 block 以首 element 为 seed 左折叠，其后按 block ordinal 对相邻 pair 分轮合并，奇数
尾 block 原样进入下一轮，直到只剩一个。该规则避免人为 `+0` 改变 single `-0.0`。
Float/double NaN、infinity 和 signed zero 按上述固定 IEEE-754 addition 自然传播；natural
min/max 使用 `Float.compare/Double.compare` total order。Production floating reduction
kernel 必须声明 `strictfp`，避免 Java 8 non-strict intermediate precision 使同一 tree 在
不同执行路径/硬件产生额外差异；application callback 自身仍由 application 保证
deterministic。

`1024` 是 V1 numeric-result contract 的一部分，不是任意调参。若未来更换 summation
algorithm，必须作为可见数值语义变化审查，并用版本/evidence 管理。

所有 buffer/count/index/byte estimate arithmetic 使用 checked calculation。Application
callback 内的 `Math.addExact` 仍是 `CALLBACK_FAILED`，不能和 SOMA reduction overflow
混为一谈。

## 12. Complexity 与 peak-memory contract

下表是 V1 algorithm baseline，不是延迟承诺：

| Operation | Expected time | Additional memory |
|---|---:|---:|
| whole/Field scan | `O(N)` | sequential `O(1)`；parallel `O(P + tasks)` |
| Key find/get | expected `O(1)`；worst `O(N)` | `O(1)` |
| Index select | expected `O(1 + M)`；worst `O(N)` | `O(M)` ordered slot selection |
| add without growth | expected `O(1)` | `O(index count)` journal |
| add with growth | `O(N)` | candidate root `O(capacity)` |
| non-Index update | `O(M * touched leaves)` | changed-slot old/new staging |
| Index-affecting update | `O(M + N per affected index)` | staging + candidate affected indexes |
| point remove | expected `O(leaves + Key/Index chain work)`；worst `O(N)` | bounded journal / candidate sidecars |
| selection remove | `O(N + M * leaves)` | selection bitmap/slots + journal or candidate root |
| sorted/distinct/materialize | `O(N log N)` / expected `O(N)` / `O(N)` | bounded by result/stage size |

Structural candidate-root publication（growth/remove/reserve）可以短时同时保留 old/new
root；普通 update 只保留 changed-slot staging 与 affected Index candidate。实现必须在
allocation 前估算已知 SOMA-owned bytes并拒绝 arithmetic/representation overflow；它
不能从 `freeMemory/maxMemory` 推断 JVM 尚有连续空间，也不能捕获所有 OOME。

Default Group 是 ClassLoader-lifetime singleton，capacity 是 high-water mark。需要周期性
整体替换、大对象回收或双缓存时，应使用 explicit Group 并丢弃旧 Group；V1 不用
`trimToSize/release` 给 singleton 增加第二套 lifecycle。

## 13. Repeated add 与 Batch 边界

V1 不引入 public Batch。Application 可以复用 detached carrier，但每次 `add` 仍是一个
独立 atomic operation。Production profile 必须把 million-scale ingestion 与等价
`ArrayList + HashMap` baseline 比较：若 admission、duplicate validation 或 repeated index
maintenance 使该路径不能达到 G7 门槛，实施必须停止并回到产品/Design review；不得在
内部偷偷把多次 add 变成具有不同 failure/visibility 语义的 hidden Batch。

## 14. Surface admission 与替换规则

下列任何新增都必须先更新 Design/Conformance：第三个 production artifact、public SPI、
new storage/index kind、plugin、background thread、cache、off-heap/Unsafe、serialization、
planner、async handle 或 runtime tuning knob。

内部优化只有同时满足以下条件才能替换 baseline：

1. capability、Owner、lifecycle 和 failure boundary 不变；
2. canonical order、sequential/parallel result、numeric semantics 和 structured failure
   不变；
3. old/new implementation 通过相同 golden、negative、fault-injection、concurrency 和
   performance matrix；
4. memory/complexity没有形成新的用户必须理解的 abstraction；
5. Conformance 记录适用 commit、environment 和可重放 evidence。

## 15. Implementation admission Gates

进入 production 实施后必须首先证明：

- exact two-artifact dependency graph、Java 8 clean reactor 与 independent consumer；
- schema clean-full Maven/IDE delegated build、delete/rename stale cleanup 与 manifest；
- primitive/reference/Value root invariant、Key/Index collision/null/order matrix；
- add/update/remove/reserve 每个 failure point 的 zero-publication fault injection；
- admission/reentrancy/callback token 的 leak、race 与 quiescence测试；
- fixed partition/tree 下 sequential/parallel result、failure 和 numeric bit equivalence；
- large mutation peak memory、repeated add stop rule 和 default/explicit Group GC profile；
- public/generated signature 与[精确 Signature Design](generated-api-signatures.md)完全一致。
