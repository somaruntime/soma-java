# SOMA V1 实施前最终全局一致性审核

类型：Conformance / Final Pre-implementation Review

状态：`PASS`

Design/Plan readiness：`READY_FOR_IMPLEMENTATION`

下一治理动作：`READY_TO_REQUEST_IMPLEMENTATION_AUTHORIZATION`

Implementation authorization：`NOT_GRANTED`

Production G1-G10：全部`NOT_RUN`

正式事实源：是（当前V1 baseline最终实施前审核、findings closure、promotion与claim boundary）

Owner：SOMA V1 全局产品/设计/工程/Conformance一致性与实施准入结论

审查日期：2026-08-03

## 1. 审核问题与结论

本审核回答：当前checkout中的全部项目内容是否统一、自洽、协调地服务SOMA产品目标；正式
Blueprint、九个Design Owner、I0-I8 Plan、G1-G10 Gate和repository boundary是否已经足以让
implementation按既定位置建立机制与证据，而不再临场发明普通用户可观察的语义。

结论为`PASS`：

1. 未发现未关闭P0产品目标/边界冲突；
2. 审核发现的P1精确合同空洞已经写回唯一Design Owner，并进入相应I/G evidence；
3. 核心抽象Temporary已正式晋升为第九个Design Owner，源候选完成replacement closure；
4. Blueprint→Design→A/N/INV→I0-I8→G1-G10双向可追溯；
5. 三个用户旅程可以只用正式public model自然闭环；
6. 当前只剩明确受控的implementation/profile evidence choice，不存在已知需要I0自行裁决的
   P0/P1语义；
7. repository仍是干净的pre-implementation product boundary，没有production source、build、
   artifact、workflow或release claim。

因此Design/Plan已经`READY_FOR_IMPLEMENTATION`，但本审核不等于Product Owner的实施授权。
下一步可以请求单独的implementation authorization；获得授权后只能从I0开始。

## 2. Frozen audit input

| Input | Frozen fact |
|---|---|
| Repository / branch | `somaruntime/soma-java` / `develop` |
| GitHub observation | private repository；default branch `develop`（2026-08-03 read-only query） |
| Audit-start HEAD | `d9944a1dc6b22cf7683d0039d63f75f87c34d582` |
| Predecessor archive | `archive/pre-product-reset-2026-07-31^{}` = `b69477432b44c4bc75c5f62fff741a729a33def2` |
| Core candidate source | `project/temp/soma-core-abstractions-narratives-and-invariants/README.md` |
| Candidate SHA-256 | `429067b512ad9cddc1d8b92607eb90298a226c8bbe43656ffc61e6cdd1360886` |
| Candidate disposition | promoted to `project/design/core-abstractions-and-narratives.md`; Temporary removed |
| Working-tree policy | 本次审核在既有未提交治理修改上继续；不commit、不push、不实现 |

Digest固定的是promotion前候选输入。正式文档已吸收本审核对state publication、GC accounting和
failure provenance的correction；候选原文不作为parallel Design归档。

## 3. 审核方向

本次采用九条相互独立但双向核对的审查线：

1. **产品目标与边界**：North Star、第一阶段目标、用户、非目标和claim是否一致；
2. **用户心智与叙事**：Soma/Group/Table/Field/Index、source→pipeline→terminal、relation和
   mutation是否形成一条可理解主线；
3. **抽象与Owner**：每个长期事实是否有唯一Owner，A0-A27是否有primary parent/lifecycle；
4. **精确Java 8 surface**：capability absence、overload inference、constructor/scope/type-state；
5. **状态与正确性**：currentness、order、null/missing、numeric、atomic publication、failure；
6. **执行与资源**：Group guard、parallel、memory accounting、GC lifecycle、quiescence；
7. **规划与性能可持续性**：typed IR、reference oracle、rewrite、Index/Join/compression seam；
8. **实施与证据闭环**：每项Blueprint承诺是否有slice/Gate，反向是否存在无目标surface；
9. **repository/documentation hygiene**：route、status、link、Temporary、historical claim、source/
   artifact absence与delivery boundary。

## 4. 产品叙事审核

### 4.1 当前产品叙事

SOMA是嵌入Java application、运行在单JVM进程内、面向schema-known mutable Tables的编译式
列式计算引擎。Application使用自然Java object、generated Table/Field/Index API与Stream-like
pipeline表达point、scan、aggregate、GroupBy和binary relation；processor建立类型安全专门化
surface；runtime把typed logical plan经语义等价优化后，在long-domain chunked state、Key/Index
与透明压缩表示上同步执行。

它的核心价值不是“另一套Collection API”，而是同时解决：

- application侧自然、typed、低心智负担；
- runtime侧紧凑、可扫描、可索引、可关系计算、资源受控；
- mutable authoritative state的Table-local atomic publication；
- optimizer/parallel/compression可以持续演进但不能改写语义。

### 4.2 与Java Stream的关系

| 维度 | Java Stream可借鉴 | SOMA必须不同 |
|---|---|---|
| 主线 | source→lazy intermediate→terminal；one-shot pipeline | source是Table/Field/Index/relation，不是通用Collection |
| 命名 | filter/map/sort/match/materialize；sequential default | direct source省略`stream()`；parallel必须显式 |
| 类型 | fluent pipeline和primitive specialization | processor按schema生成exact capability；illegal operation从type缺席 |
| 状态 | 通常不拥有source结构维护 | Table拥有add/point/Selection mutation与atomic publication |
| 访问 | element traversal | Key、Index、nested logical Field与binary relation |
| 执行 | 可并行、terminal触发 | typed IR、reference oracle、resource admission、Group guard |
| 结果/失败 | Java/source-specific | detached result、normal absence与stable structured failure分离 |

审核结论：当前设计借用了熟悉的执行主线，但没有把SOMA退化成`Stream<T>`包装器，也没有让
storage/planner机制泄漏到普通用户API。产品叙事清晰、差异有必要且可由三个场景验证。

## 5. P0/P1/P2/P3 findings closure

### 5.1 P0

未发现未关闭P0。North Star、million-row阶段目标、Java 8、single-process/on-heap first、binary
relation、AUTO compression、no spill/no ChildTable/no cross-Table transaction等边界一致。

### 5.2 P1 — 已关闭

| ID | Finding | Formal closure | Evidence route |
|---|---|---|---|
| F-01 | Generic `SomaField`会让ordinary Object误入GroupBy/Join | 增加keyable capability marker，在Java type层收窄GroupBy/Join | I2/I5；G2/G6 compile-negative |
| F-02 | Join builder允许kind在intermediate后改变，Outer projection会制造missing ambiguity | condition/matched/outer/left ReadStream type-state分离；typed select只在Inner/Cross | I5；G2/G6 |
| F-03 | typed Field projection与lambda callback lowering不明确 | Field non-functional overload进入typed IR；callback为opaque barrier；borrowed result escape失败 | I3；G2/G4 |
| F-04 | point update missing没有正式normal outcome | `matched=0, changed=0`，callback不执行，version不变 | I1/I4；G5 |
| F-05 | nullable Index null bucket与“null required argument”冲突 | 只有nullable String/Enum Index lookup允许null；Key/non-null Value仍invalid | I2；G3/G5 |
| F-06 | between/in/order/match/distinct边界可能由实现猜测 | 固定inclusive between、逆区间失败、empty/duplicate/null in、null placement、tie order、empty match与mapped distinct | I3；G4 |
| F-07 | materialized List ownership不明确 | 新建、application-owned、structurally modifiable；Table/Value detached，ordinary reference保持identity | I3；G4 |
| F-08 | callback内真正SOMA failure与application伪造/replay无法区分 | hidden current-operation provenance；trusted runtime failure保留code，replay/foreign包装`CALLBACK_FAILED` | I4；G5 |
| F-09 | “published root永远deep immutable”与journaled in-place commit矛盾 | 固定atomic logical generation；large candidate swap，small prevalidated non-throwing final commit | I1/I4；G3/G5 |
| F-10 | 无close Group与global retained accounting缺少GC释放机制 | Java 8 PhantomReference/ReferenceQueue accounting token，同步drain、无background/live registry | I4；G3/G5/G7 |
| F-11 | Table no-arg carrier/defaultCapacity首次分配语义不完整 | carrier可暂不完整，add预发布验证；initial capacity 0，reserve/add分别解释hint | I1/I2；G2/G3 |
| F-12 | Pair、summary、Group result与terminal return shape不够精确 | Signature Owner补齐Result family、mapped/primitive terminal、Join/Group type grammar与absence | I2-I5；G2/G4/G6 |
| F-13 | Selection mutation先运行callback再做resource admission会留下不可回滚副作用 | terminal按bound input upper bound先保守admit selection/staging/candidate peak，再运行任何callback | I4；G5 |
| F-14 | Parallel、short-circuit与opaque Comparator若不固定callback contract，调用/失败语义不可证明 | behavioral callback要求non-interfering；callback-bearing short-circuit与Comparator为canonical caller-thread barrier | I3/I6；G4/G7 |
| F-15 | 每Group每Table type单例且无alias时，self-Join左右identity不可无歧义表达 | 只为不同generated Table type生成Join/Cross overload；V1无self relation/alias | I5；G2/G6 |
| F-16 | “V1无trim/shrink”却允许remove释放尾Chunk会使capacity不可预测下降 | payload capacity为Group-lifetime high-water且单调不减；remove清引用但不隐式shrink | I2/I4；G3/G5 |
| F-17 | Architecture的“no runtime reflection”会误禁已裁决`toArray(Class)`数组reification | 只禁止schema/member/constructor reflection；validated `Array.newInstance`仅用于结果数组 | I3；G4/G10 |
| F-18 | Project entry仍声明项目组织框架`2.0.0-rc.1`，方法源已升至`rc.2` | current route更新为“面向项目生命周期、角色与场景”`2.0.0-rc.2` | documentation validation |
| F-19 | 已裁决的Soma/Group/Table/Field四级metadata在exact signature中遗漏Field层级 | 每个logical Field endpoint（含nested Field）提供`FieldMetadata _metadata()`；stable类别与physical-leak边界固定 | I7；G8 |
| F-20 | “behavioral callback每element至多一次”错误覆盖Comparator与distinct equality/hash调用 | 分离element callback与comparison/hash callback；后者可按canonical algorithm重复调用且不得承载副作用 | I3/I6；G4/G7 |
| F-21 | Blueprint Join mapper用普通`long + long`会让application arithmetic静默溢出 | 参考旅程改用`Math.addExact`并明确SOMA-owned与callback-owned arithmetic failure边界 | I3/I5；G4/G6 |
| F-22 | Selection来源清单遗漏`sorted/sortedBy`，与capability matrix的row-lineage合同不一致 | 明确filter/order/slice都保留Table row identity与mutation lineage；projection/relation/materialization才终止 | I3/I4；G2/G4/G5 |
| F-23 | Semi/Anti未定义right duplicate、null与left output cardinality | 固定existence semantics：每个left最多一次、保持left order、right duplicate不放大、null落入Anti | I5；G6 |
| F-24 | `SomaEqualityField`与float/double/Value Key矩阵矛盾，Value可包装浮点绕过identity限制 | 改为`SomaKeyableField`并递归闭合：浮点可比较/去重/排序，但不进入Key/Index/GroupBy/Join | I2/I5；G2/G3/G6 |
| F-25 | 零Field Table/Value会让no-arg与canonical constructor冲突并形成无意义special case | 编译期要求每个schema declaration至少一个direct SOMA Field，加入stable negative diagnostic | I0/I2；G2 |
| F-26 | Java annotation Target无法阻止Value内部误用`@SomaKey/@SomaIndex` | 固定Key/Index只属于direct Table Field；Value direct Field只允许`@SomaField` | I0/I2；G2 |
| F-27 | Schema Field modifier矩阵漏禁`final`，会先产生普通javac blank-final错误 | processor明确拒绝`static/final/transient/volatile`与initializer，保持稳定SOMA diagnostic | I0/I2；G2 |
| F-28 | `UpdateResult.changed`对无intrinsic equality的ordinary Object没有判定合同 | ordinary Object按referent identity `==`；其他类型沿用logical equality；referent内部变化不发布Table version | I2/I4；G3/G5 |
| F-29 | Point update叙事可能在Editor callback后才发现可预见资源不足 | missing先正常返回；命中后先准入single-record worst-case peak，再运行callback/build/publish | I4；G5 |
| F-30 | Group guard文字未明确`size/capacity/find/get`等direct read，可能形成隐式并发旁路 | 除pipeline构建/generated accessor/四级metadata外，所有direct/point/terminal state operation都CAS acquire | I1/I4；G3/G5/G7 |
| F-31 | One-shot pipeline在busy/resource/callback failure后的consumption时点未固定 | phase-1 validation失败不消费；成功后先atomic consume，随后任何outcome均永久consumed | I1/I3；G4/G5/G7 |
| F-32 | 只在terminal consume无法阻止同一linked pipeline生成两个intermediate branch | intermediate/terminal在validation后atomic claim receiver；只让新child open，source保持reusable | I1/I3；G2/G4 |
| F-33 | Generated package属于application，package-private constructor不能保证Group/Table/View不可伪造 | nested constructor必须private；Group/Table经private construction + unforgeable composition token，加入same-package forgery negatives | I0-I2；G2/G10 |
| F-34 | Public carrier片段若省略constructor会被Java生成implicit public no-arg constructor | 除detached Value/Table的正式public shape外，configuration/result/failure/summary/Tuple/metadata/support均显式private construction | I0-I7；G2/G8/G10 |
| F-35 | 非Java标准`top()`及skip/limit oversize边界未形成reference semantics | top固定为stable sortedBy+limit；zero/oversize与skip/limit min语义明确，optimized path须差分等价 | I3；G4 |
| F-36 | Floating aggregate overflow、NaN/Infinity与min/max comparator未固定 | 采用Java IEEE-754 + strictfp canonical tree；Infinity/NaN为normal，min/max按Float/Double.compare | I3/I6；G4/G7 |
| F-37 | Parallel side-effect terminal在callback failure后的执行线程与已发生效果边界不明确 | forEach允许已启动range效果且只quiesce；forEachOrdered由caller按order交付并在首个failure停止 | I3/I6；G4/G7 |
| F-38 | Arbitrary mapped distinct会执行application `equals/hashCode`，但未纳入callback failure/reentrancy boundary | 将其作为caller-thread callback boundary；普通异常包装、trusted provenance与same-Group reentry规则统一 | I3/I4；G4/G5 |
| F-39 | 既有“metadata可观察压缩结果”裁决退化成policy+总managed bytes，无法区分Index与payload收益 | 固定plain-equivalent/current payload/savings/encoded-presence类别；codec/Chunk细节仍只进unstable explain | I7；G8 |
| F-40 | String dictionary可canonicalize引用，但materialization只区分“ordinary reference identity”不够明确 | String只保证content、Enum保证constant、ordinary Object保证原referent identity | I2/I7；G3/G4/G8 |
| F-41 | Managed budget若不区分String input body与codec-owned copy，会被误解为JVM total heap guarantee | PLAIN String body按external referent；codec copy才计入，metadata只报告engine-owned representation | I2/I4/I7；G3/G5/G8/G9 |
| F-42 | Typed expression literal validation与`in`数组currentness未固定，可能受调用方后续mutation影响 | literal-only错误构造时失败；`in` defensive copy/normalize；不freeze/guard/consume pipeline | I2/I3；G2/G4/G5 |
| F-43 | Generated canonical constructor/flatten/member顺序没有唯一来源 | 固定Java source declaration order + depth-first Value flatten，并统一驱动constructor/materialization/hash/diagnostic | I0-I2；G1/G2/G3 |
| F-44 | “V1不包含Candidate”未限定predecessor public model，与Core的candidate maturity/mutation词汇形成表面冲突 | 排除项统一限定为predecessor public `DataFlow/Transformation/Candidate` model，不禁用正常设计生命周期与mutation candidate术语 | documentation terminology audit |
| F-45 | “unforgeable token”未写明same-JVM trust boundary，可能被误读为Java 8 sandbox承诺 | 封装保证限定于supported source/API与未篡改artifact；source construction/token negative及null/foreign validation必须成立，privileged reflection/Unsafe/agent/modified bytecode明确排除 | I0-I2；G2/G10 |
| F-46 | Plan/Gate摘要要求sequential/parallel全部failure完全相同，与Execution允许pool/resource/interrupt专属失败矛盾 | 等价合同收敛为result/order/numeric/mutation/non-resource failure；mode-specific resource/interrupt必须稳定fail closed、quiescent且不能产生alternate result | I6；G7 |
| F-47 | Java 8 public expression/Field/keyable/Order interface可被application手写，generated capability absence不能单独防止伪node进入runtime | shared marker明确为non-SPI；hidden composition/owner/node provenance在claim/config/guard前验证，foreign/replayed/app implementation稳定`INVALID_ARGUMENT`且不进入planner | I0-I5；G2/G4/G6/G10 |
| F-48 | Schema parent可选择SOMA shared/internal或platform-reserved namespace，可能污染library package并弱化same-package边界 | processor稳定拒绝`io.github.somaruntime.soma`及descendant与`java.*`/`javax.*`/`jdk.*`/`sun.*` generated namespace；不依赖碰巧发生的class collision | I0；G1/G2/G10 |
| F-49 | Enum/ordinary Object/array/parameterized Field type可能无法从generated parent package引用，导致普通javac access error或错误擦除 | processor在写source前验证最终signature type及递归type argument/bound的Java 8 accessibility；不可访问时稳定composition failure，不退化为`Object` | I0/I2；G2 |
| F-50 | Table/Index accessor只有示例而无exact case transformation，acronym/Unicode/既有`Table` suffix会由implementation猜测 | 固定locale-independent first-code-point lower/upper规则、机械`Table`/`by`拼接、no acronym/plural/suffix guessing，并在完整symbol table拒绝collision | I0/I2；G2 |
| F-51 | Generated exact FQN可能已被application source或dependency type占用，而“不扫描classpath”可能被误解为无需检查 | processor不扫描dependency发现schema，但在写source前对有限generated FQN集合做exact lookup并稳定拒绝占用，不能依赖FilerException/duplicate-class javac错误 | I0；G1/G2 |
| F-52 | Generated `Soma`若eager static-initialize default Group，会在调用`configure`方法体前提前freeze | class loading/linking/`Soma.class`与metadata不freeze；configuration CAS和lazy Group materialization分离，并在I0/G2/G5验证configure-first/default-first及cross-composition race | I0/I4；G2/G5 |
| F-53 | 未提交的audit deliverable被称为“versioned project surface”，会把local readiness误投影为Git/remote事实 | 结论限定为current local working tree；commit/push继续独立授权，origin/develop不因本审核自动获得新baseline | documentation/Git claim audit |
| F-54 | Generated accessor不取得Group guard，两个线程首次访问可能由naive lazy init发布两个同type Table instance | identity合同增加concurrent-safe accessor publication；eager或CAS lazy均可，但所有调用返回同一instance且无losing live Table | I2；G3 |
| F-55 | `in(...)` defensive copy发生在terminal admission前，却未说明literal snapshot是否计入global managed budget | expression/pipeline graph与成功返回的literal snapshot归application-retained heap；construction checked、OOME不发布Table，terminal normalized/hash scratch再受managed admission；大规模membership使用Table/Join | I3；G4/G5 |
| F-56 | Java-legal `$`、ignorable/FORMAT/bidi或非NFC schema name可污染binary name、generated source review与collision判断 | package/type/Field名称要求NFC可见Java identifier，拒绝`$`/control/FORMAT/bidi/ignorable且不静默rename；保留中文等可见Unicode支持 | I0/I2；G2/G10 |
| F-57 | Execution Owner的evidence清单仍残留“全部failure等价”旧摘要，弱化F-46已经固定的mode-specific resource/interrupt边界 | evidence文字同步收敛为result/order/numeric/mutation/non-resource failure等价；parallel专属资源/中断失败必须stable fail closed、quiescent且无alternate result | I6；G7 |

### 5.3 P2 — 受控、非I0 blocker

| Item | Why controlled | Earliest evidence/admission |
|---|---|---|
| Metadata exact nested carrier/getter topology | stable类别、快照性和安全边界已固定；exact Java shape需要真实consumer/API diff | I7 / G8 |
| Chunk rows、hash/load/shard、codec threshold、Join/group coefficient、task multiplier | internal mechanism，不能改变public semantics/resource visibility | owning slice profile + G3/G6-G9 |
| AUTO实际codec选择 | AUTO语义固定，但收益必须由forced-correctness与profile决定 | I7 / G8-G9 |
| Quantitative performance threshold | 没有production engine前拍数字会形成虚假Gate | I8 profile proposal + Product Owner approval / G9 |
| Loader重新准入 | 当前`reserve + repeated add`完整可用；只由million-row ingestion证据触发 | I4/G9 stop + new Temporary |

### 5.4 P3 — 已关闭

- 正式current文档不再使用public `Record`作为View名称；historical P2 record保留历史术语；
- 核心候选已经晋升，旧Temporary与空的大规模专题目录退役；
- entry route、Owner数量、readiness状态和active Temporary声明统一；
- 旧readiness标记为historical/superseded，最终结论由本文唯一拥有；
- Markdown/link/status/whitespace/repository inventory由最终validation重放。

## 6. Core abstraction promotion

本审核正式接纳[核心抽象、叙事体系与不变量证明链](../design/core-abstractions-and-narratives.md)
为第九个Design Owner。

### 6.1 独立责任

- A0-A27核心/显著机制抽象注册表与typed cross-relation；
- N1-N8 schema、state、query、mutation、relation、parallel、compression、failure主叙事；
- INV-01..19的Unique Owner→earliest defense→failed-state→evidence proof chain；
- I0-I8 fill map与M0/M1/M2 implementation-time change protocol；
- 三个canonical journey与God-object/parallel model/backend placeholder等bad-smell guard。

### 6.2 边界

它不拥有精确API、算法、failure matrix或production class hierarchy；这些仍由八个分责Owner
唯一拥有。A15-A27可以在evidence下按M1审慎修正，不能静默修改`SEMANTIC_BASELINE`。

### 6.3 Replacement closure

候选source fingerprint已记录；全部长期内容进入正式Owner；本审核的StateRoot、GC accounting与
failure provenance correction已合入正式文本；Temporary不再拥有active事实，也不建立archive
或第二份Design。

## 7. Java 8 bounded feasibility delta

本审核在Amazon Corretto 8 `1.8.0_502`、`javac -Xlint:all -source 8 -target 8`下重放一个非
production type-shape/construction fixture：

| Fixture | Result | What it supports |
|---|---|---|
| `ShapePositive.java` | compile PASS | `SomaKeyableField`、typed Field vs callback overload、float/non-keyable Value query capability、`SomaOrder.then`、GroupBy、Join stages、Inner select、Outer callback map、Semi/Anti narrowing |
| construction/runtime check | compile/run PASS | default Group/Table identity、explicit Group isolation、private construction linkage、null/foreign token rejection |
| `ForeignMarkerCounterexample.java` | compile PASS（预期反例） | Java 8 public marker/interface可由application实现；capability缺席只提供normal source narrowing，runtime hidden owner/provenance validation不可省略 |
| ordinary Object Join | expected compile FAIL | non-equality Field不能进入`on` |
| ordinary Object GroupBy | expected compile FAIL | non-equality Field不能进入`groupBy` |
| float/double Join + GroupBy | 2 × expected compile FAIL | floating Field有query equality但不实现keyable marker |
| non-keyable Value Join + GroupBy | 2 × expected compile FAIL | Value不能通过包裹floating leaf绕过recursive keyability |
| Outer typed select | expected compile FAIL | missing side不能隐式投影 |
| Join kind after filter | expected compile FAIL | relation kind在normal intermediate后不可改变 |
| same-package Group/Table/View construction + token access | 4 × expected compile FAIL | private constructor/token不因application package相同而成为source API |
| Java 8 synthetic-constructor source probe | expected compile FAIL | javac不把synthetic accessor constructor当作可调用source member；runtime scope/token仍是failed-state防线 |

Positive与runtime各1项通过；13/13 compile-negative按预期失败；`javap -p`确认Group/Table/View/
Editor正式constructor为private，并同时观察到Java 8 nested-class synthetic access mechanism。
Fixture位于`/private/tmp/soma-global-review-api-shape`，不进入repository或产品证据链。它只证明
selected Java 8 type shape可表达、开放marker反例真实存在且negative如预期，不证明production
processor/runtime/API、performance或G2/G6已经PASS。

## 8. Three journey review

| Journey | Public narrative closure | Critical boundary |
|---|---|---|
| Scheduling | schema→Group→Job/Machine/Option Tables→Key/Index→typed Join→map/sort/top detached decision→separate point mutations | cross-Table transaction/compensation属于application |
| Simulation | Event/State Tables→explicit lexicographic event order→detached next event→state update→separate event remove | Table没有业务顺序；remove missing是normal no-op |
| Real-time dispatch | pending Index→typed predicate/Join→optional parallel score→detached top decision→external effect→local Table updates | external side effect/referent不属于SOMA atomicity |

三个journey均不需要hidden API、ChildTable、public Column、Loader、cross-Table transaction、
non-equality/multi-way Join或per-stream Executor。真实自然性、吞吐、allocation和managed peak仍由
I8/G9 production scenario证明。

## 9. Traceability conclusion

BP-1..15均有至少一个唯一Design Owner、A/N/INV proof route、I0-I8 slice与G1-G10 Gate。
反向检查也没有发现只因“未来可能有用”而存在的module、public SPI、implementation slice或Gate。

关键主链：

```text
Blueprint BP-1..15
    -> nine Design Owners
        -> A0-A27 / N1-N8 / INV-01..19 routing
            -> I0-I8 production slices
                -> G1-G10 evidence
                    -> I8 product qualification
                        -> separate release authorization
```

Implementation不得把A/N/INV当成production class名，也不得用test代替Owner/encapsulation/
publication protocol。每个slice只在matching evidence进入Conformance后关闭。

## 10. Repository and documentation conclusion

当前local working-tree project surface（tracked baseline + 本次audit additions）只包含：品牌资产、
License/NOTICE/Security、entry README、九个
Design Owner、Engineering Plan、Conformance records和Git治理文件。Worktree中的`.idea/`是
`.gitignore`排除的本地IDE state，不属于repository、delivery或产品surface。

明确不存在：

- predecessor/legacy/compatibility source；
- production Maven reactor/module/generated source/test/benchmark/Example；
- CI/release/package workflow或artifact；
- active Temporary或parallel current Design；
- public release、million/100M performance、compatibility或package claim。

Selected delivery profile仍需在I0/I8建立allowlist；整个checkout不能默认成为library package。

### 10.1 Final validation replay

| Check | Current local evidence |
|---|---|
| Git identity/drift | branch `develop`；HEAD与`origin/develop`均为`d9944a1dc6b22cf7683d0039d63f75f87c34d582`，ahead/behind `0/0`；remote为`somaruntime/soma-java` |
| GitHub read-only observation | repository private；default branch `develop` |
| Formal registries | 9/9 Design Owner；BP-1..15、I0-I8、G1-G10、A0-A27、N1-N8、INV-01..19均完整 |
| Markdown structure | 26个Markdown；154个relative link、0 broken；code fence/final newline issue为0 |
| Java 8 bounded shape | Corretto 8 positive compile与runtime identity check通过；marker反例按预期可编译；13/13 negative按预期失败；private/synthetic surface已用`javap -p`观察 |
| Repository boundary | 无active Temporary、production/build/test/benchmark/Example/workflow/artifact、symlink或submodule；`.idea/`为ignored local state |
| Brand/legal assets | License/NOTICE/Security/CODEOWNERS route存在；3个PNG hash与2400×640、640×640、2048×2048 identity匹配`assets/README.md` |
| Text hygiene | tracked diff与4个untracked正式Markdown均无whitespace error；unresolved decision marker为0 |

这些是当前working tree的审核证据，不是production Gate，也不表示修改已commit或push。

## 11. Targeted readiness delta conclusion

与此前readiness相比，本次delta只加强、没有缩小或反转已批准产品目标：

- 新增正式Core Owner与A/N/INV proof routing；
- 收窄ordinary Object equality capability；
- 固定Join type-state、predicate/materialization edge semantics；
- 明确dual publication mechanism、GC accounting release与failure provenance；
- 区分predecessor public Candidate与内部candidate术语，并界定same-JVM source/API trust boundary；
- 补齐Java 8开放marker provenance、generated namespace/type accessibility/naming/FQN collision、
  configuration/accessor race与expression literal memory ownership；
- 将相应negative/runtime/fault/GC evidence加入I/G。

这些修正没有引入新artifact/module/dependency/public product family，也没有把internal mechanism
泄漏给普通用户。修正后的I0-I8顺序仍成立；G1-G10具有可执行Owner和输入。因此此前readiness
被本文完整替代，当前结论为`READY_FOR_IMPLEMENTATION`。

## 12. Authorization and claim boundary

本文不授权：

- 创建production source/module/API或开始I0；
- commit、push、PR或workflow变更；
- dependency/network/security exception；
- performance/support/compatibility claim；
- GitHub Release/Package/signing/publication。

Product Owner若同意进入实现，需要另行明确授权。授权后：

1. 只启动I0；
2. I0 exit和matching G1/G2/G10 evidence未成立前不进入I1；
3. 一次只推进一个active slice；
4. public semantics delta触发M2 stop，不在代码中打补丁；
5. implementation、commit/push与release/package继续各自独立授权。

## 13. Final verdict

```text
Global consistency review           PASS
Known open P0/P1                    NONE
Core abstraction promotion          PASS
Temporary replacement closure       PASS
Blueprint/Design/Plan/Gate closure  PASS
Design/Plan readiness               READY_FOR_IMPLEMENTATION
Implementation authorization        NOT_GRANTED
Production implementation           ABSENT
Production G1-G10                   NOT_RUN
Product/package/release readiness   NOT_QUALIFIED
```

SOMA当前基础足够稳固，可以结束设计扩张并请求进入实施；仍不得把“设计完整”误写为“产品已经
实现或可发布”。本结论只属于上述current local working tree；它不表示修改已commit或已出现在
`origin/develop`。
