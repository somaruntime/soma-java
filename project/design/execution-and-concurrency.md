# SOMA Java V1 执行、并发与并行 Design

类型：Design

状态：Active V1 Baseline

正式事实源：是

Owner：Pipeline binding、Group operation guard、currentness、mutation publication、sequential/
parallel scheduling、configuration、resource admission、cancellation与quiescence

最后审查日期：2026-08-03

## 1. 设计目标

SOMA对用户表现为同步、顺序语义明确的抽象机。`parallel()`只改变本次terminal内部如何分片
执行，不改变logical result。Application明确知道同一Group外部operation严格串行；不同Group
可由application并发。

本文不拥有optimizer rewrite、storage layout或failure code表。

## 2. Pipeline lifecycle

- Table/Field/IndexSelection是reusable source；
- intermediate linked pipeline lazy、one-shot、不能branch；
- pipeline construction不持有StateRoot或Group guard；
- terminal-start统一validate/consume/admit/bind/plan/execute/publish/quiesce；
- linked pipeline对每次intermediate或terminal invocation先做argument/owner/state validation；
  validation失败不claim尚open receiver；成功后原子claim receiver；
- intermediate成功claim predecessor并只创建一个open child，因此同一receiver不能产生两个
  branch；terminal成功claim后，在reentrancy/Group admission前标记consumed，随后无论busy、
  resource、callback、execution或publish outcome都不能复用；
- 对已claimed/consumed linked pipeline再次调用intermediate或terminal均为
  `PIPELINE_ALREADY_CONSUMED`；Table/Field/IndexSelection reusable source不被claim，可重新构建。

Typed expression自身的literal-only validation/snapshot在expression construction完成，不进入
Group operation或pipeline claim；intermediate的negative limit/top等参数在claim receiver前验证。

## 3. Terminal-start binding

Terminal取得Group guard后，同时绑定本次operation参与的全部Table current StateRoot与
statistics。Pipeline创建后、terminal开始前发生的合法mutation对terminal可见；terminal不
持有历史root。

一次bound operation中的root直到quiescence稳定。Join按stable Table identity绑定same Group
多个Table；Group guard已消除同Group外部overlap，不需要跨Tablelock ordering或transaction。

## 4. Group operation guard

每个Group只有一个non-blocking operation guard：

- `size/capacity/find/get/reserve/add/update/remove`等direct/point operation，以及source terminal、
  Join、GroupBy与Selection mutation进入时non-blocking acquire；实现可以复用internal operation
  token，但不能弱化owner thread、operation kind、reentrancy或concurrent rejection；
- source/intermediate pipeline construction与generated accessor不读取bound Table state，因此不
  acquire；四级`_metadata()`是第5节唯一state-observation exception；
- guard已占用立即`CONCURRENT_GROUP_OPERATION`，不排队；
- callback内再次进入same Group为`REENTRANT_GROUP_OPERATION`；
- internal parallel worker共享caller取得的execution token，不各自acquire；
- different Group可以并发，但global memory manager与shared ForkJoinPool必须thread-safe。

这个合同意味着SOMA不负责application external concurrency consistency。Application若要并发
计算同一业务状态，必须使用不同Group/state copy或在SOMA外串行化。

## 5. Metadata exception

`Soma/Group/Table/Field._metadata()`读取最近atomic-published immutable snapshot，不取得Group
guard：

- 可以在operation期间看到last complete state；
- 不会看到partial mutation；
- multi-Table metadata不承诺cross-Table same instant；
- metadata不freeze configuration或创建default Group。

`_explain()`会bind/plan并取得guard，因为它依赖current roots与statistics。

## 6. Currentness 与 borrowed scope

View/Editor保存owner、execution token、participant/thread与callback epoch。Runtime在可检测时
拒绝：

- callback结束后访问；
- wrong Table/Group/terminal；
- foreign participant/thread；
- terminal结束后访问。

Failure为`CALLBACK_SCOPE_VIOLATION`。Java 8无法检测same participant后续callback复用对象时
保存的旧alias；该行为明确illegal/unsupported。Stable data只能`fetch()`。

## 7. Sequential execution

- 默认source/pipeline sequential；
- callback在calling thread执行；
- 不使用parallel pool、hidden worker或background task；
- terminal同步完成并在返回前释放temporary lease与Group guard；
- canonical encounter order由bound StateRoot/operation定义。

## 8. Parallel public contract

Parallel必须显式：

```java
table.parallel()...
field.parallel()...
table.join(other).on(...).parallel()...
```

- `parallel()`表示最多P个SOMA participants，不承诺加速或一定多线程；
- terminal仍同步；
- result/order/numeric/failure与sequential contract等价；
- no per-operation pool、no per-record task、no hidden parallel from sequential source；
- `forEach`side-effect order不保证，failure前可能已有其他range side effect；
- `forEachOrdered`的upstream可并行，但final action由calling thread按canonical order串行delivery，
  buffer先admit，首个action failure后停止后续delivery。

Predicate/mapper/match/Comparator遵守Logical Design的non-interfering behavioral callback合同。
含opaque callback的short-circuit segment在caller thread按canonical order运行；typed-only
short-circuit可以由worker内部speculate，但不执行application callback，frontier外internal
failure不成为operation failure。Editor mutation callback不short-circuit，对每个frozen selected
membership恰好一次。Opaque Comparator-based sort/top/min/max stage也使用canonical caller-thread
schedule；arbitrary mapped reference distinct的application `equals/hashCode`同样是caller-thread
barrier与callback/reentrancy/provenance boundary；Comparator/equals/hashCode可由canonical algorithm
多次调用，不承诺per-element调用次数；
`parallel()`不承诺每个stage都并行。

## 9. Executor configuration

V1只接受 application-owned `ForkJoinPool`：

```java
Soma.configure(
    SomaConfiguration.builder()
        .parallelExecutor(pool)
        .memoryBudgetBytes(bytes)
        .compression(SomaCompression.AUTO)
        .build());
```

- 未配置pool时使用`ForkJoinPool.commonPool()`；
- SOMA不创建、不关闭application pool；
- 不接受generic ExecutorService、fixed/cached pool adapter或per-stream Executor；
- 不提供独立setParallelism；effective P来自pool parallelism并受operation/chunk限制；
- shutdown/rejection为`PARALLEL_EXECUTOR_UNAVAILABLE`，不fallback sequential或其他pool。

## 10. One-time configuration

Configuration是same ClassLoader中runtime library-wide、one-time：

- 第一次successful `Soma.configure`立即freeze；
- 未显式configure时，第一个`createGroup/defaultGroup/Table shortcut`冻结default；
- class loading/linking、取得`Soma.class`或执行generated `Soma`的无状态static initialization本身
  不freeze，也不创建default Group；`Soma.configure(...)`必须能够成为该class的第一次真实调用；
- `_metadata()`只观察、不freeze；
- freeze后configure为`CONFIGURATION_FROZEN`；
- all composition代理到同一runtime configuration owner；
- builder-local invalid argument用ordinary `IllegalArgumentException`；进入Soma runtime后的
  contract failure使用structured exception。

Public option只有ForkJoinPool、optional positive memory budget、AUTO/OFF compression。

实现必须把configuration CAS与default Group/Table materialization分离（例如lazy holder或等价
机制），不能让eager static field initialization反转上述顺序；具体holder shape不是public合同。

Freeze前只有`Soma._metadata()`可被调用；它报告configuration为UNFROZEN，effective budget尚不存在，
且不为了填充数值而创建default Group或计算/冻结automatic policy。Explicit configure或first
runtime access完成freeze后，metadata才报告稳定effective budget。Exact optional carrier由I7准入。

## 11. Automatic memory budget

未显式配置时，freeze依据stable JVM heap boundary与versioned conservative policy得到
effective budget。Public contract：

- positive且不超过JVM可表达stable heap upper bound；
- freeze后不变；
- `_metadata()`显示effective value；
- retained与temporary reservation共用该budget；
- exact ratio/headroom/min/max是profile-driven internal policy；
- 不使用`Runtime.freeMemory()`作承诺；
- application需要确定边界时显式`memoryBudgetBytes(long)`。

Design不固定50%或其他比例。Budget不能消除真实JVM OOME，但任何SOMA publication仍遵守
zero-partial-state。

Automatic effective budget只依赖stable JVM/ClassLoader-wide facts与runtime policy version，不依赖
哪个composition先触发freeze，也不扫描schema。Schema/representation差异只进入后续per-operation
conservative byte estimate。

## 12. Global resource admission

Global manager 为所有 Groups 提供 atomic retained reservation 与 temporary lease。计入：

- Table Chunk/directory/Key/Index/compression/statistics；
- candidate root与mutation staging；
- sort/hash/group/join/materialization scratch；
- tasks/result construction peak；
- 返回前仍由SOMA持有的detached result。

不计ordinary referent、application长期持有detached result、已返回并由application持有的
expression/pipeline graph与literal snapshot、thread stack或callback allocation。
String PLAIN input object/content body同样不做deep accounting；codec实际复制成SOMA-owned
dictionary storage后才计入。Effective budget因此是engine-owned memory boundary，不是JVM总heap
上限；真实OOME边界仍受application/referent分配影响。

Known peak在不可逆work/callback前admit：old + candidate + scratch + result。Budget不足为
`RESOURCE_LIMIT_EXCEEDED`，无partial result/state。Representation estimate必须conservative并
由实测校准。

显式Group没有manual close。每个Group注册一个不反向引用Group/Table的accounting token与
`PhantomReference`；global manager同步drain `ReferenceQueue`后exactly-once释放该Group的
retained reservation。Drain发生在每次resource admission、configuration和global metadata
读取入口，不启动background cleaner。Default Group按ClassLoader lifetime保留。实现必须证明
phantom/token/global set不形成strong-retention cycle、并发drain不double release，且GC尚未投递
queue时仍保守计费。

## 13. Bounded participation

Parallel terminal把canonical input划分为fixed ordinal contiguous ranges：

- task/range count受P与Chunk数限制；
- caller是participant，最多提交`P-1`个drainer；
- worker从operation-local queue领取range；
- caller参与保证从saturated same ForkJoinPool调用时仍可前进；
- drainer在all submission成功前停在start gate；rejection不执行callback；
- active participants不超过effective P；
- result按range ordinal/canonical merge tree合并，不按completion race。

ForkJoin work stealing可用，但不能改变range ordinal、floating tree、order或failure arbitration。

## 14. Nested parallel 与 reentrancy

任何SOMA callback内启动新的parallel terminal为`NESTED_PARALLEL_OPERATION`。Same-Group nested
operation更早按reentrancy失败。Callback访问另一个Group只有application保证目标Group无overlap
且不形成nested parallel时才合法；SOMA不提供跨Groupdeadlock协调。

## 15. Mutation protocol

### 15.1 Point add

Validate carrier/Key/duplicate/resource/header -> stage payload/sidecars/compression -> complete all
throwing work -> final non-throwing commit/root publish。

### 15.2 Point update

Key lookup -> missing normal return或conservative single-record staging/candidate/sidecar/codec peak
admission -> callback-scoped Editor stage -> validate changed leaves -> build admitted Index/compression
candidate -> publish。Missing返回`matched=0, changed=0`、不admit、不调用callback且version不变；
logical no-op返回`matched=1, changed=0`且version不变并释放lease。不得先执行Editor callback再因
可预见的SOMA-owned update peak不足失败。

### 15.3 Point remove

Missing返回0；命中后freeze locator/compaction mapping -> stage payload/sidecars -> publish。

### 15.4 Selection update/remove

一次Group operation内all-or-nothing：

1. bind/plan并依据input cardinality upper bound预留selection、callback staging、candidate、scratch
   与result的conservative worst-case peak；
2. evaluate pipeline并freeze final locator selection；
3. run all mutation callbacks into already admitted staging；
4. validate schema/Index/compression/version并完成recoverable allocation/hash；
5. bounded non-callback commit或root swap；
6. publish Result、quiesce、release。

Sequential Selection Editor callback在calling thread按frozen selection order运行；显式parallel
时可由caller/workers处理不同range，不保证wall-clock callback/外部side-effect order。每个selected
membership最多一次，任一failure使Table zero publication，但已经发生的application side effect
不回滚；需要有序外部效果时不得用parallel mutation callback承载。

若opaque predicate使exact matched cardinality在callback前未知，admission使用bound input upper
bound；允许conservative rejection，不允许先运行有副作用callback再因可预见的SOMA staging/
candidate budget失败。Callback自己分配的application object不计入managed budget，仍由
application负责。

Large selection不能为减少scratch而partial batch publish；预算不足必须publish前失败。
Implementation可用small journal或large candidate root，但共享selection/order/failure/Result。

“small journal”不允许边写边验证。所有可能抛出的application code、allocation、hash/codec、
journal capacity和sidecar decision必须先完成；exclusive final commit只执行bounded、经证明
non-throwing的physical writes，然后一次发布新的header/statistics/accounting descriptor。
Structural/large mutation使用独立candidate root swap。Metadata在commit window仍只读取上一次
完整发布的header projection；普通same-Group operation被guard排除。两条路径具有同一zero-
publication observable contract。

## 16. Atomicity boundary

SOMA atomicity只覆盖一次Table-local mutation及其payload/Key/Index/compression/accounting。
它不覆盖：

- multiple sequential add；
- multiple Tables；
- application referent mutation；
- callback side effect；
- external I/O/transaction/compensation。

Join/Group都是query-only。Application必须在query terminal结束、guard释放后执行cross-Table
point mutation与补偿。

## 17. Failure arbitration、cancellation 与 interrupt

- parallel worker failure按operation phase、canonical element/work-unit ordinal仲裁，不取
  wall-clock first；
- short-circuit只承认canonical decisive frontier内failure；
- no nondeterministic suppressed failure API；
- submission rejection/shutdown/cancel为`PARALLEL_EXECUTOR_UNAVAILABLE`；
- caller interrupt触发best-effort cancel，等待quiescence，恢复interrupt flag后抛
  `OPERATION_CANCELLED`；
- no timeout/starvation detector；
- any structured failure返回前worker quiescent、temporary lease释放、Group guard释放。

## 18. Sequential/parallel equivalence

对same bound state与deterministic callback，必须相同：

- selected membership与encounter order；
- filter/map/aggregate/Group/Join result；
- integer overflow与floating bit contract；
- Update/Remove matched/changed/removed与published state；
- non-resource failure code/operation/context；
- no partial publication。

Resource availability、pool rejection与interrupt可以产生mode-specific resource failure，但不得
伪装成不同logical result。

## 19. Explicit absence

- blocking queue/lock acquisition；
- concurrent same-Group reads；
- snapshot retained across terminal；
- arbitrary ExecutorService、per-stream pool、runtime pool replacement；
- async/Future/timeout；
- hidden sequential fallback、spill、background compressor；
- cross-Table transaction或callback reentrancy；
- manual release/close。

## 20. Evidence Gate

Implementation必须覆盖：

- terminal-start binding与same-Group fail-fast admission；
- Soma/Group/Table/Field metadata lock-free last-published observation；
- View/Editor scope/currentness；
- point/selection mutation every recoverable failure point与zero publication；
- P=1/2/4/16、small/large selection、task/participant bound；
- custom/common/shutdown/rejection/saturated/nested/interrupt/quiescence；
- sequential/parallel result/order/numeric/mutation/non-resource failure equivalence，以及
  pool/resource/interrupt mode-specific failure的stable fail-closed、quiescence与no alternate result；
- retained/temporary accounting与peak admission；
- explicit Group PhantomReference/ReferenceQueue accounting release、无strong retention或double
  release；
- auto-budget policy evidence acrossqualified heap/JVM range；
- different-Group application concurrency与global manager safety。
