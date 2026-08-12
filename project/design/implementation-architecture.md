# SOMA Java V1 Production Implementation Architecture

类型：Design / Implementation Architecture

状态：Active V1 Baseline

正式事实源：是

Owner：Artifact/build topology、runtime component seams、storage/Index/compression/scheduler
baseline mechanism、complexity与replaceability boundary

最后审查日期：2026-08-12

本次冻结：Canonical Logical IR / Execution Engine M1 responsibility baseline；finite primitive
Chunk kernel、representation-native access与shared parallel lifecycle

## 1. 文档责任

本文规定怎样用production Java 8 compiler/runtime承载正式Design。它不重新定义public API、
logical rewrite或failure semantics。Internal mechanism可在evidence更好时替换，但不能改变
上游contract。

## 2. Production topology

V1只有两个published artifact：

```text
soma-runtime
    annotations
    public/shared API and functions
    generated linkage contracts
    planning/execution/storage runtime

soma-processor
    JSR 269 processor
    schema validation
    generated source
```

Generated source/class是application build output，不是第三个artifact。没有benchmark/example/
test-support/runtime-native/compatibility module。

Surface admission：

- runtime：application compile/runtime dependency；
- processor：application processorpath/build-time dependency；
- processor可依赖runtime annotation/shared signature，但runtime不依赖processor；
- no circular dependency、reflection scan或service-loadedexecution plugin；
- third-party dependency默认零；新增dependency需独立security/size/license/evidence admission。

## 3. Java/build baseline

- source/target/runtime方向Java 8；
- standard Maven reactor是production build owner；
- independent consumer分离classpath与processorpath；
- source/javadoc jar、NOTICE/LICENSE、manifest与provenance从I0开始建立；
- no generated source committed as production source；
- compiler/runtime exact version handshake；
- reproducible clean build是qualification前提。

## 4. Full-regeneration build mechanism

Build host必须：

1. discover完整schema source set；
2. set `-Asoma.fullSourceSet=true`；
3. clean/replacecomposition generated output；
4. invoke aggregating processor；
5. reject partial/stale manifest；
6. compile generated source against exact runtime version。

Processor输出composition manifest/provenance fingerprint，至少绑定schema identity、processor/
runtime contract version与generated file set。Stale/partial output不能由runtime fallback修复。

V1支持Maven clean/full compile与IDE delegated Maven build；raw partial javac/Gradle isolating
incremental不受支持。

## 5. Runtime components

```text
Generated facade/linkage
    -> Composition/Group/Table runtime roots
        -> Java lowering
            -> CanonicalOperation
                -> terminal-start BoundOperation
                    +-- Reference Interpreter
                    +-- Normalize / Physical planner
                            -> PhysicalPlan + ResourceEstimate
                                -> Managed-memory admission
                                    -> operation-local ExecutionFrame
                                        -> specialized sequential / parallel kernels
                                            -> Chunk StateRoot / Key / Index / Compression

Global runtime services
    Configuration freeze
    Managed-memory admission
    ForkJoin scheduler adapter
    Failure/context factory
```

Boundary requirements：

- generated code不持有physical arrays/hash/Chunk；
- Java lowering只验证frontend provenance并转换为compiled descriptor/ordinal identity、typed literal与
  host callback handle，不把generated facade instance升级为semantic identity Owner；
- hot kernel不做per-record reflection/metadata lookup；
- planner只能通过typed internal descriptors访问schema/storage capability；
- PhysicalPlan只拥有decision/estimate，不持有已分配的cursor、scratch、membership、worker或result
  staging；
- O(N) operation state只在resource lease成功后由ExecutionFrame拥有，并在terminal结束时释放；
- global services不建立live Group registry；
- internal package narrow/versioned，application依赖不兼容。

Generated construction linkage不把application-owned package误当作trust boundary：nested
View/Editor/Stream/Field constructor是private；top-level Group/Table private constructor由非公开
factory与per-composition identity token连接。Token只由generated Soma→Group链传递，null/foreign/
application-created Object必须在实例publication前拒绝；token不进入metadata/context/callback，
不使用constructor reflection或global live-instance registry。

该linkage是supported source/API integrity boundary，不是对同JVM privileged code的security
sandbox。Reflection access override、`Unsafe`、agent/instrumentation与篡改generated/runtime
bytecode属于application/JVM控制面；SOMA只证明正常source不能取得token、null/foreign token不
发布实例，以及正式artifact没有意外public/package-usable construction surface。

## 6. StateRoot baseline

每Table一个atomic current state descriptor：

```text
PublishedHeader(int size, int capacity, long stateVersion)
PagedChunkDirectory
Chunk representations
Key/Index sidecars
Immutable statistics
Managed-byte account
```

Implementation必须支持：

- paged directory与non-negative raw `int` locator；
- same-row-span leaves per Chunk；
- atomic descriptor/header publication；
- bound read state stable through terminal；
- Table structural domain在`Integer.MAX_VALUE`产品上限内fail closed，不因单个
  payload array更早失去capacity；
- internal tiny Chunk injection for boundary tests。

Initial plain Chunk target约2 MiB、rows `[4096,65536]` power-of-two，是profile mechanism。

Descriptor表达header、payload、sidecar、statistics与accounting的一个logical generation。Large/
structural mutation以candidate root swap发布；point/small mutation可以复用唯一拥有的physical
representation，但只能在exclusive final commit阶段执行预先journaled、bounded、non-throwing
writes，再atomic publish新的immutable descriptor/header projection。Metadata永远只读取last-
published projection。这里不把“所有physical array永久immutable”设为架构要求，但禁止任何
operation观察partial generation。

## 7. Generated leaf access

Processor为每个schema生成：

- typed flatten/unflatten/fetch/add copy；
- Key/Index equality/hash；
- View/Editor getter/setter linkage；
- primitive/reference kernel descriptors；
- direct Field/Value endpoint lowering。

Hot loops对Chunk representation做once-per-Chunk dispatch，再操作primitive/reference arrays；
不能逐 row 使用 virtual Field accessor、boxing 或 reflection。

PLAIN representation可以向同package内已经admitted的finite physical kernel提供once-per-Chunk typed
array borrow；borrow只能存活于一次ExecutionFrame，不能泄漏到generated/runtime public surface、
跨terminal缓存或成为第二份storage truth。Encoded/overlay保持representation-owned access；不得为了
复用PLAIN loop无预算地整体解压。Integral encoded representation可以通过closed、package-private、
immutable access投影plain integral buffer或`raw value + logical run end`；该access不接收Canonical/
terminal/planner对象，不跨Frame保存，也不把codec token或private layout升级为架构ABI。

## 8. Key/Index baseline

Key：typed sharded open-addressed hash，live slot直接内联唯一raw `int` locator；zero是
合法locator，empty/deleted state由slot metadata表达。

Index：typed sharded exact-value directory；singleton Bucket内联一个`int` locator，multi
Bucket拥有一个严格升序、无duplicate的`int[]`。不维护per-record next link、reverse
Index或第二套membership truth。

Mechanism必须：

- 结构域统一使用checked `int`，count/cardinality、memory bytes与stateVersion等
  累计域使用checked `long`；
- flatten-aware generated equality/hash，不materialize Value；
- collision/load/growth checked；
- nullable Index bucket与Join null-never-match分离；
- sidecar memory纳入budget；
- payload/Key/all Index/compression一次publish。

Point add/update/remove只增量维护受影响的Key slot与Index Bucket；packed remove在
同一commit中同步删除removed locator并把tail locator从`T`调整为`R`。Selection
mutation从最终candidate payload一次重建全部sidecar，不在每个命中行上重复维护。

Hash mixing/load factor/shard count可profile替换。

## 9. Mutation mechanism

### 9.1 Small vs large

Implementation可以：

- small point/update使用prevalidated journaled in-place final commit；
- large selection/remove/growth使用candidate Chunk/root；
- threshold由profile决定。

两者必须共享frozen selection/mapping、failure precedence、Result与zero publication。

### 9.2 Non-throwing publish

所有callback、validation、allocation、hash/codec build与journal capacity在publish前完成。
Final commit只包含经证明不会抛可恢复exception的bounded writes/atomic descriptor swap。Publish
后不运行application code。不能证明这一点的in-place mechanism必须退回candidate root。

### 9.3 Remove compaction

先冻结 `removed=R` 与 `tail=T`。若`R != T`，把tail payload搬至`R`，并在
同一commit中将Key映射和每个Index Bucket中的`T`替换为`R`；然后删除removed
membership、清理tail reference并发布new root。若old/new Index value相同，必须对同一
Bucket完成remove/add normalization，不得丢失或重复locator。Table不承诺stable insertion order。

## 10. Compression mechanism

Representation seam：

```text
PLAIN
BIT_PACKED / FRAME_OF_REFERENCE
DELTA / RLE / DICTIONARY
ENCODED + SPARSE_OVERLAY
```

Requirements：

- active tail 使用 PLAIN，sealed/affected Chunk 同步评估 AUTO；
- no background thread/whole-Table surprise；
- generated or specialized kernel可直接在encoded representation上执行eligible operation；
- overlay/rebuild与Index atomic；
- rebuild old+candidate+scratch preflight；
- codec choice不进入public identity；
- forced codec correctness与AUTO cost test分离。

First implementation可以先只支持PLAIN，但Chunk representation dispatch seam必须从首个slice
存在；在compression slice完成前Conformance明确标记AUTO capability未实现，不能宣称V1完成。

## 11. Canonical plan runtime

Public pipeline lowering到[Planning Design](planning-and-optimization.md)定义的IR。Production
至少包含：

- Java lowering/validator；
- immutable closed Canonical node/terminal family；
- terminal-start BoundOperation；
- deterministic NormalizedOperation；
- PhysicalPlan与checked conservative ResourceEstimate；
- sequential reference interpreter；
- optimized sequential executor；
- parallel adapters；
- operation-local ExecutionFrame；
- explain renderer；
- differential test harness（test-only，不是public mode）。

Reference interpreter不能成为unsupported fallback。Planner/physical operator internal class可以
替换；IR semantic properties与rewrite proof不可绕过。

Canonical identity复用compiled composition capability/descriptor与Table/Field/Index ordinals，不建立
runtime classpath registry或四层object graph。Schema-known literal以typed canonical leaves直接供bound
hash/equality/lookup消费；不得保留generated probe后再复制第二个probe。Java direct Field source统一
lower为`TableSource + FieldProject`，direct leaf kernel fusion只属于physical choice。

Opaque callback使用最小host-bound handle并保持barrier/scope/failure合同；arbitrary mapped reference是
host shape，不伪装成schema LogicalType。IR/internal class name和serialization不是public compatibility
surface；V1不建立public frontend/planner SPI、general DAG、prepared query或SOMA Engine placeholder。

## 12. Physical operator baseline

PhysicalPlan记录access path、specialized kernel、Join/Group algorithm、partition与deterministic merge；
ResourceEstimate记录执行峰值。Admission成功后，ExecutionFrame才分配/拥有cursor、membership、
sort/hash/materialization、task、result和mutation staging。Sequential与parallel可以有不同specialized
physical family，但必须细化同一PhysicalPlan并追溯同一Canonical semantic node。

已准入的finite primitive Chunk family只覆盖Table count、schema-known integral Field sum与ordered
`long[]` materialization及其zero/simple pure typed integral predicate。Planning接收closed terminal
requirement，一次编译leaf/predicate binding、representation handler、parallel ownership与complete
resource projection并放入最终PhysicalPlan；execution只消费decision。

PLAIN使用typed array；integral encoded-plain直接读取，single-distinct-leaf RLE按run读取/填充；需要
multi-leaf run zipper时使用encoded scalar，overlay使用current-value scalar。Scalar parallel aggregate以
existing Chunk为morsel，使用O(Chunk count) partial与exact ordinal merge。Ordered `long[]`无predicate
按Chunk prefix写固定range；typed predicate sequential使用single write + conditional compact，parallel
使用count/prefix/disjoint write。所有路径禁止先建立O(rows) locator buffer。具体predicate switch、array
loop、partial carrier、Chunk宽度和private carrier名称是可替换L4机制，不形成通用Batch DAG。

First complete engine需要：

- scan/Index lookup/Field projection；
- expression/callback filter；
- primitive/reference map；
- distinct/sort/top/skip/limit；
- scalar/match/materialize；
- selection update/remove；
- Group hash/sort path；
- Equality Join lookup/hash，其他 algorithm 只按 evidence additive admission；
- Cross Join budget preflight；
- explain-only planning。

不能先实现boxed universal `Object[]` operator后把specialization留为未来重写。

## 13. Scheduler baseline

Parallel adapter 使用 application-owned `ForkJoinPool` 与 operation-local ordinal work queue：

- caller + at most P-1 drainers；
- start gate防partial submission callback；
- bounded tasks/participants/scratch；
- caller participation确保progress；
- canonical range/merge tree；
- synchronous cancellation/quiescence；
- no per-record task、new pool或generic Executor adapter。

Row range与Chunk morsel共用同一submission/start/rejection/cancel/interrupt/quiescence lifecycle；不同
work family只负责“给定ordinal怎样执行”及其结果状态，不复制scheduler协议。该shared seam是
package-private最小机制，不是Executor SPI、通用task framework或第三artifact。

Numeric floating operator使用固定1024-element block + pairwise strictfp tree；parallelism不改变
tree。

## 14. Memory/accounting baseline

Global manager区分：

- retained reservation；
- temporary lease；
- operation estimated peak；
- returned detached handoff。

Every allocation owner明确，success/failure/quiescence后balance归零或转retained。Estimate使用
checked long与conservative array/object overhead；不得依赖`freeMemory()`。

每个显式Group使用`PhantomReference + ReferenceQueue + accounting token`与global retained
reservation关联。Token不能持有Group/Table/StateRoot；queue由admission/config/global metadata
入口同步drain并exactly-once release，不创建background cleaner或live Group registry。Default
Group按ClassLoader lifetime保留。GC投递前的reservation继续保守计费。

`_metadata()`从同一accounting source生成stable estimate；`_explain()`显示planned peak。

## 15. Complexity contract

Expected boundaries：

| Operation | Expected |
|---|---|
| Key point | O(1) average |
| Index selection | O(1 + matches) average + canonical normalization |
| scan/filter/map | O(N) |
| sort | O(N log N) unless proven optimized path |
| group | O(N) average hash / O(N log N) sort |
| equality join | O(L+R+output) expected hash/lookup；algorithm-dependent |
| cross join | O(L*R) and hard budget |
| selection mutation | O(scan + matched staging + affected sidecars) |

Pathological hash collision可退化O(N)，但不能破坏correctness/resource/failure。

Peak memory是first-class qualification dimension；wall time快但无界scratch的algorithm不可进入。

## 16. Loader boundary

V1先实现`reserve + repeated add`，不建立Loader/Batch。Profile必须记录million-row rows/s、
guard/publication/allocation、Key/Index与AUTO sealing cost。

只有repeated add成为主要瓶颈且internal优化无法关闭与合理manual column baseline差距时，才建立
新Temporary。Future Loader必须保留add value/schema/zero-publication语义，不能成为Batch
mutation或第二套schema。

## 17. Future backend seam

Off-heap/mmap通过Chunk representation与kernel dispatch扩展，不通过public backend SPI。
V1不创建interface/module/config placeholder。Future admission必须解决Java 8 lifecycle、cleaner、
serialization、reference hybrid、failure、安全与profile。

## 18. Security/build boundary

- processor处理untrusted schema必须bounded diagnostic，无path/stack泄漏；
- generated source只来自validated symbol，不拼接raw code；
- no runtime schema/classpath scan、reflective Field/constructor access、deserialization或plugin
  loading；`MappedStream.toArray(Class)`只允许以`Array.newInstance`使用validated reified component
  type分配结果数组，不构成schema reflection；
- arithmetic/allocation/cardinality checked；
- dependency/SBOM/license/provenance从I0建立；
- packaged processor/runtime version匹配；
- no predecessor class/resource/generated artifact。

## 19. Replaceability rules

可以凭evidence替换：Chunk size、hash mixing、Index structure、codec、planner coefficient、Join
algorithm、small/large threshold、task multiplier、scratch block（floating tree除外）。

不能作为internal替换：public API、32位结构域/64位累计域、result/order/null/missing、failure、callback、
zero publication、budget visibility、Group guard、future backend seam。

任何替换影响上游contract时停止implementation，建立Temporary并请求Product Owner裁决。

## 20. Architecture evidence Gate

Production必须证明：

- two-artifact clean Java 8 build与independent consumer；
- full regeneration/manifest/stale cleanup/version mismatch；
- primitive no-boxing/no-reflection hot path；
- cross-Chunk checked-int structural storage、64位累计与GC retention；
- Key/Index/compression/mutation fault injection；
- reference/optimized differential；
- bounded scheduler/quiescence/accounting；
- explicit Group GC accounting release、无strong-retention/double-release；
- million-row Narrow/Medium/Reference-mixed profile；
- security/dependency/SBOM/source/javadoc/package smoke；
- three public-API reference scenarios。

## 21. Physical Execution Engine M2 component boundary

M2在既有`soma-runtime`内部收敛职责，不新增artifact、dependency、public/generated API或internal plugin
SPI。允许的package-private组件边界是：

```text
Canonical/Bound/Normalized
    -> Physical Pipeline planner
        -> immutable Segment/Breaker/Kernel/Morsel descriptors
        -> one ResourceEstimate
            -> one admitted ExecutionFrame
                -> specialized family kernels
                    -> shared ordinal-work scheduler
```

Descriptor只能保存data-only decision与bound runtime handle，不得持有O(N) state、application callback
执行结果或resource lease。Frame拥有actual短生命周期state；typed kernel拥有loop/algorithm实现；scheduler
只拥有bounded work lifecycle。Family-specific kernel是合法specialization，不得为了统一删除primitive
array、locator或representation-native路径。

迁移采用纵向replacement：每个operation family进入新physical topology后，删除其旧eligibility、resource、
partition或state decision Owner。长期parallel hierarchy、bridge adapter、shadow PhysicalPlan与双重scratch
estimate均不允许保留。Point operations、Storage publication、Reference Interpreter和SOMA Engine边界保持
独立。
