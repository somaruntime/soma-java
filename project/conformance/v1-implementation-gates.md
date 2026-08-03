# SOMA Java V1 Implementation Conformance Gates

类型：Conformance Gate Definition

状态：Active；I0 candidate 已出现，G1-G10 尚无正式 PASS

正式事实源：是（production evidence最低集合）

Owner：SOMA Java V1 implementation、qualification与release claim Gate

最后审查日期：2026-08-03

## 1. Gate原则

- Design定义系统应当是什么；Gate证明implementation实际符合；
- implementation surface 出现不自动产生 Gate PASS；I0 candidate 仍需绑定 commit、独立审查与
  Conformance evidence；
- feasibility fixture只降低selected design risk，不可替代production Gate；
- 每个Gate evidence必须绑定commit、JDK/OS/arch、command、input、result与artifact；
- deterministic evidence可重放；performance事实标明machine/JVM/heap/workload；
- failed/skipped Gate不能由README措辞变成通过；
- implementation readiness只检查这些Gate是否可执行、Owner是否完整，不执行未来Gate。

Gate按[核心抽象与叙事Design](../design/core-abstractions-and-narratives.md)的A0-A27、N1-N8与
INV-01..19路由证明责任；ID用于attention与coverage，不替代分责Design的精确合同。

## 2. G1 — Artifact、Build 与 Full Regeneration

必须证明：

- exactly `soma-runtime` + `soma-processor` production artifact；
- standard Maven clean reactor在qualified Java 8通过；
- independent consumer分离classpath/processorpath编译运行；
- composition full-source-set handshake、manifest、late round、deletion/rename stale cleanup；
- processor/runtime exact version match与mismatch negative；
- generated source不committed、不受input order影响；
- source/javadoc jar、LICENSE/NOTICE、manifest/provenance baseline；
- no legacy/predecessor/third production module。

## 3. G2 — Schema、Diagnostics 与 Generated Surface

必须证明：

- six annotation exact retention/target/signature；
- package composition、declaration modifier、Field role/type/Key/Index matrix；
- Enum/Object/array/parameterized signature type的generated-parent accessibility与stable diagnostic；
- Key/Index只允许direct Table Field，Value/nested role misuse compile-negative；
- empty Table/Value stable diagnostic与constructor non-collision；
- Value/Table object constructor/getter/setter/equals shape；
- source declaration encounter order对constructor/flatten/fetch/hash/diagnostic/source的一致投影；
- Table/object/facade/accessor与Index accessor首code-point命名、acronym/Unicode/`Table` suffix
  golden及collision negative；
- generated exact FQN与current source/dependency type占用的stable collision negative；
- all non-application-created carrier/configuration/exception/summary/Tuple explicit private
  constructor与no implicit public constructor；
- direct source、View/Editor/Stream/Selection/ReadStream/Field/Join/Group exact source；
- generated `Soma` class initialization不急切freeze/materialize default Group，configure-first与
  default-first consumer ordering；
- recursively keyable Field marker、typed Field projection/callback overload，以及float/double/
  non-keyable Value/ordinary Object capability exclusion；
- application-authored expression/Field/keyable/Order marker、foreign composition与replayed
  provenance negatives；不得泄漏`ClassCastException`或进入planner；
- reserved `io.github.somaruntime.soma`及其descendant shared/internal namespace，以及
  `java.*`/`javax.*`/`jdk.*`/`sun.*` generated namespace negatives；
- reserved collision与stable `[SOMA-xxxx]` diagnostic；
- visible Unicode/NFC positive及`$`/ignorable/FORMAT/control/bidi/non-NFC name injection negatives；
- nested support private constructor、top-level Group/Table private construction linkage、same-package
  fake/null/foreign capability-token negatives；
- 100+Table/Field/Index/Join surface compile time、class/method/constant-pool与IDE usability；
- all explicit absence compile-negative，包括`stream()`、Record、Batch、Right/non-equality Join、
  arbitrary ExecutorService、Outer typed select、Pair materialization与Join kind after intermediate。

## 4. G3 — Long-domain Storage、Key 与 Index

必须证明：

- paged Chunk directory、long size/capacity/locator/cardinality；
- tiny-Chunk cross-boundary、million real、near-int/long virtual arithmetic；
- all primitive/reference/Value flattening/null/equality/order；
- String content-vs-reference identity、Enum constant identity与ordinary referent identity；
- String PLAIN external body vs copied dictionary managed-byte accounting boundary；
- reserve/growth/add/remove compaction与GC reference clearing；
- default/explicit Group的same-type accessor sequential/concurrent identity、safe publication与无
  losing live Table；
- payload capacity monotonic、remove不隐式shrink；
- Key duplicate/zero/null/collision/immutable/update absence；
- non-unique Index null/repeated/collision/move/rebuild/canonical order；
- payload/Key/all Index/accounting same atomic logical generation；
- candidate-root swap与prevalidated bounded final-commit publication equivalence；
- explicit Group GC后Phantom/ReferenceQueue accounting release，无strong retention/double release；
- no single-array/int Table boundary、reflection或primitive boxing hot path；
- structural/retained byte accounting。

## 5. G4 — Direct Query、IR、Optimizer 与 Materialization

必须证明：

- Table/Index/Field direct source与one-shot pipeline lifecycle；
- intermediate atomic claim/no-branch、validation-before-claim与terminal post-validation permanent
  consumption；
- Stream/Selection/ReadStream capability narrowing；
- typed expression vs callback overload与owner/dependency negative；
- foreign/application-authored/replayed typed-node provenance在claim/guard前稳定失败；
- all intermediate/terminal operation-property matrix；
- adjacent filter normalization、barrier、leaf pruning、fusion；
- Key/Index substitution保持duplicate/order/null/failure；
- reference interpreter vs optimized sequential differential；
- checked integer与floating strictfp/NaN/Infinity/total-order canonical numeric；
- typed Table/Field/primitive array与mapped `toArray(Class)`；
- null/reference/container/array/resource boundary；
- between逆区间、empty/duplicate/null `in`、nullable order placement、lexicographic tie-break、empty
  match、mapped reference distinct/null与modifiable detached List；
- literal-only eager expression validation、`in` defensive snapshot与no config/guard/pipeline consume；
- expression/literal snapshot application-retained ownership、checked construction/OOME no-publication
  与terminal normalized/hash scratch managed admission；
- skip/limit/top zero/oversize/stable-sort equivalence与optimized top differential；
- element callback successful full-traversal once、short-circuit canonical prefix，以及Comparator/
  equals/hashCode可重复调用的canonical caller-thread barrier；
- `_explain()`不执行callback/data kernel。

## 6. G5 — Mutation、Resource 与 Structured Failure

必须证明：

- configuration class-load/metadata/configure/default-first ordering、cross-composition freeze race、
  repeated configure与stable effective policy；

- all direct/point/terminal same-Group fail-fast guard、pipeline-construction non-admission、
  reentrancy与metadata last-published exception；
- point reserve/add/update/remove与Selection update/remove；
- point update missing不执行callback且返回matched=changed=0；
- View/Editor callback scope/currentness；
- every recoverable validation/allocation/hash/codec/publish fault point；
- Update/Remove result invariants与no-op version；
- ordinary referent identity changed/no-op与referent-internal mutation boundary；
- zero partial Result/root/payload/Key/Index/accounting；
- failure code/operation/context mapping、phase precedence与sanitization；
- callback current-runtime provenance保留、application replay/foreign exception wrapping与JVM Error
  passthrough；
- arbitrary mapped distinct application equals/hashCode exception/reentrancy/provenance boundary；
- auto/explicit effective memory budget、retained/temporary peak accounting；
- opaque selection mutation按bound upper peak在任何callback前完成conservative admission；
- point update missing零admission/callback，命中后worst-case peak先于Editor callback；
- explicit Group PhantomReference/ReferenceQueue accounting release；
- all failure paths release lease/guard and quiesce workers。

## 7. G6 — GroupBy 与 Relation

必须证明：

- Group key type/null/equality/order与typed result；
- count/numeric/summary aggregate与checked resource；
- Join `on/and` inference、same composition compile、same Group runtime；
- float/double、non-keyable Value与ordinary Object GroupBy/Join exclusion，以及keyable marker；
- Inner/Left/Full/Semi/Anti/Cross semantics；
- null-never-match、outer MISSING truth、duplicate Cartesian、encounter order；
- Semi/Anti left existence、right-duplicate non-amplification与nullable-component behavior；
- Semi/Anti left ReadStream与right/mutation compile-negative；
- Pair scope、projection/materialization boundary；
- Inner/Cross typed select与Outer explicit missing mapping；
- typed predicate pushdown/residual/Index substitution per Join kind；
- lookup/hash/other admittedalgorithm vs reference differential；
- checked output cardinality/maxOutputRows/peak budget；
- no multi-way/Right/range/as-of/interval/non-equality surface。
- no relation alias/self-Join/self-Cross surface。

## 8. G7 — Bounded Parallel Execution

必须证明：

- sequential default、explicit `parallel()`；
- application-owned custom/common `ForkJoinPool`；
- P=1/2/4/16、small/large selection；
- caller + P-1 participant、bounded ranges/tasks/scratch；
- saturated same-pool progress/start gate/no partial callback；
- shutdown/rejection/nested/interrupt/cancellation/quiescence；
- no new/closed/alternate pool、no arbitrary ExecutorService；
- sequential/parallel result/order/numeric/mutation/non-resource failure exact equivalence；
- pool/resource/interrupt mode-specific failure稳定、quiescent、no alternate logical result；
- canonical failure frontier与forEachOrdered；
- parallel forEach partial external-effect boundary，以及forEachOrdered caller-thread ordered stop；
- opaque callback-bearing short-circuit/Comparator caller-thread barrier与typed-only speculation；
- different-Group application concurrency/global manager safety。

## 9. G8 — Compression、Metadata 与 Diagnostic Closure

必须证明：

- PLAIN + every admitted codec forced correctness；
- AUTO/OFF choice与benefit/peak/update-rate policy；
- sealed/affected-Chunk bounded work、no whole-Table/background rewrite；
- sparse overlay/rebuild/Index atomicity；
- compressed/uncompressed query/Group/Join/mutation equivalence；
- codec dispatch/no per-record virtual/boxing regression；
- final stable Soma/Group/Table/Field metadata carrier/API diff/independent consumer；
- nested logical Field `_metadata()`存在且不泄漏physical leaf/Column；
- Table/Field plain-equivalent/current-representation/savings summary与accounting同源，不泄漏codec/
  per-Chunk detail；
- metadata/explain information boundary与no physical mutable leak；
- effective budget/accounting/explain peak一致。

## 10. G9 — Reference Scenarios 与 Performance

Qualification必须在approved machine/JVM/heap记录：

- 调度、仿真、实时派工三个public API scenario correctness；
- Narrow、Medium、Reference-mixed million-row data；
- reserve + repeated add ingestion；
- Key/Index point/selection；
- scan/filter/map/numeric/materialization；
- Group/Join/sort/top；
- mutation、parallel saturation与compression；
- allocation/GC/managed peak/retained bytes；
- ArrayList+HashMap与合理manual column baseline；
- warm/cold、multiple JVM runs、variance与result fingerprint。

16 core/32 GB是qualification envelope；不外推为minimum/maximum。Exact thresholds在I8前由
profile proposal + Product Owner批准后写入同一Gate appendix；一亿行仍是future architecture
vision，不在未测量时拍硬数。

Loader trigger：如果repeated add是dominant bottleneck且无法通过internal优化达到approved
manual baseline threshold，G9阻断并建立Loader Temporary；不得引入 hidden Batch。

## 11. G10 — Security、Package 与 Release Qualification

必须证明：

- malformed/large schema、name/code injection、diagnostic/path sanitization；
- arithmetic/cardinality/allocation abuse与bounded failure；
- no schema/member/constructor reflection；mapped array reification只使用validated component type；
- callback/context/ordinary referent不泄密；
- dependency tree、license、SBOM、known vulnerability与processor trust boundary；
- documented same-JVM trust boundary：supported source construction/token negatives与runtime
  null/foreign validation成立，不虚假声明可隔离`setAccessible`/`Unsafe`/agent/modified bytecode；
- generated provenance、source/javadoc、LICENSE/NOTICE、manifest、checksum；
- packaged artifact independent consumer smoke；
- CI clean Java 8 matrix与release qualification workflow；
- version/Changelog/support matrix/claim review；
- no predecessor/temporary/internal file in delivery allowlist。

GitHub Release/Package、signing/publish仍需要独立Product Owner授权；G10 PASS不自动发布。

## 12. Gate status matrix

| Gate | Current | First owning slice |
|---|---|---|
| G1 Build/full regeneration | NOT_RUN | I0 |
| G2 Schema/generated surface | NOT_RUN | I0-I2 |
| G3 Storage/Key/Index | NOT_RUN | I1-I2 |
| G4 Query/IR/optimizer | NOT_RUN | I1-I3 |
| G5 Mutation/resource/failure | NOT_RUN | I1、I4 |
| G6 Group/Join | NOT_RUN | I5 |
| G7 Parallel | NOT_RUN | I6 |
| G8 Compression/metadata | NOT_RUN | I7 |
| G9 Scenarios/performance | NOT_RUN | I8 |
| G10 Security/package/release | NOT_RUN | I0、I8 |

## 13. Evidence record format

每次Gate更新至少记录：

```text
gate + scope
commit / dirty-state policy
JDK / OS / arch / CPU / memory / heap
command and configuration
input/workload fingerprint
result and artifact path
limitations/skips
claim boundary
reviewer/date
```

Generated artifact、benchmark output与temporary build result不进入source bundle；正式record只保存
必要摘要、fingerprint与可重放入口。
