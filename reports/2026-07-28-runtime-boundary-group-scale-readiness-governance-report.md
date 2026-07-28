# SOMA Runtime Boundary、Group、Scale Readiness 与产品化综合治理报告

类型：Report / Governance / Qualification

状态：completed（G6仍blocked）

Owner：SOMA Java runtime boundary / scale governance

受众：SOMA maintainer、compiler/runtime/DataFlow开发者、reference application开发者

适用版本：`soma-java` `0.2.0-SNAPSHOT` runtime-scale production candidate

输入事实源：正式Blueprint/Design/Engineering、TV0–TV9裁决、production source、
generated/public golden、strict qualification artifact、三个Example与完整Gate

事实范围：本专题意图、设计裁决、production实现、规模/资源证据、Example审计、
代码规模、Conformance、scope non-regression与closeout

非事实范围：跨环境SLA、任意Schema/String规模承诺、public release授权或G6通过

最后审查日期：2026-07-28

## 1. Executive conclusion

本专题达成的不是“让一个100M benchmark跑完”，而是重新确认并实现SOMA应当如何
被理解、如何工作、每个核心抽象为何存在：

> SOMA Java是面向Java 8、Schema-Defined、Compiler-Specialized、
> JVM Heap-Resident、类型安全、资源可预测的runtime-state computing library。

生产candidate已经把runtime重构为`State/Owner + Capability + Plan/Lifecycle`：
Metadata是cold control plane，Table/Group是live state与ownership边界，closed
Capability在operation boundary选择受限physical plan，generated specialization
进入hot loop，Resource/Failure/Observation使执行可解释、可拒绝和可回收。

TV0–TV9的accepted方向已经进入正式Design和production；rejected方向没有以改名
方式回流；inconclusive阈值被保留为versioned formula/internal parameter或明确
claim boundary。`CF-009`–`CF-015`关闭，G0–G5在新target上重放通过。G6仍因真实
SCM/ownership/contact、signing/publishing、provenance与support matrix不足而
blocked。

## 2. 治理意图与产品目标

本轮同时服务四个层次：

1. **产品叙事**：用户先声明Schema与Metadata，freeze RuntimePlan/GroupPlan，
   创建bounded live state，执行typed capability，取得detached或callback-scoped
   result，观察资源/失败，并在显式owner边界clear/release；
2. **架构可演进**：Capability可以在内部局部替换，但不建立开放SPI/registry；
   接口和泛型停留在compile/operation boundary，逐行kernel保持specialized；
3. **规模可信度**：Small/Medium控制fixed tax，Large/100M控制retained、
   intermediate、memory bandwidth、GC与bounded parallelism；100M同时验证单表和
   两个100M root同时驻留；
4. **产品化完整性**：正式Design、generated/public API、实现、测试、Guide、
   benchmark、Example、Report和Conformance陈述同一个产品，不用局部测试掩盖
   ownership、failure、resource或release差距。

整体最优不等于增加最多接口。最终结果主动排除了arbitrary object、universal
segmentation、universal Candidate、hot Metadata interpreter、nested executor、
Iterator/pull/async lazy output和无界eager expansion。

## 3. Scope 与非目标

已治理范围包括Metadata、四类V1类型、String、SomaGroup/Table ownership、
storage/locator/Candidate/relation/Delta/Window、bounded scheduler、Invocation
resource、Result Delivery、observation/failure、production qualification、代码/
测试规模和三个Example。

没有扩展到Java 8以外、native/C ABI/Python、database/persistence、distributed
execution、transaction、workflow engine、开放plugin SPI或新第三方依赖。没有
push、PR、publishing、tag或public release。

## 4. TV0–TV9 最终裁决

| TV | 关注点 | accepted并进入Design/production | rejected或保持受限 |
|---|---|---|---|
| TV0 | Protocol/Metadata | bind→freeze→fail-closed；cold Metadata与hot data分离 | 不以Metadata interpreter进入hot path |
| TV1 | Storage/growth | Small/point flat；Large flat-head + fixed tail；segment-aware outer loop | simple per-row segmented及universal segmented否决；16K/32K胜负保持internal |
| TV2 | Locator/Unique/Exact | compact current-row locator、authoritative full equality、Owner分责 | full-key duplication、fixed 16-shard否决 |
| TV3 | Candidate/eager terminal | range/segment/exact/sparse closed shapes、specialized terminal、publish-after-complete | universal full indexes/Bitmap、per-row generic cursor否决 |
| TV4 | Group/Join | aggregate/member语义分责、N:1 fusion、bounded preaggregation | pair-first/member-first统一执行及unbounded N:M allocation否决 |
| TV5 | Delta/Window/fixed tax | changed-row staging、full incremental state、ordinal slots | full-copy small Delta、partial Window、hot Map/List interpreter否决 |
| TV6 | Kernel/parallel | 一个bounded adaptive scheduler、split/coalesce、worker-local merge | segment-only、nested executor、unconditional Small parallel否决 |
| TV7 | Composite scale | Small到single/double100M窄numeric composite可行；exact reserve与bounded final | wide/mutation-heavy/composite-Key 100M不外推；819.2GB pair output拒绝 |
| TV8 | String | String-onlyreference backend、authoritative value semantics、三层memory accounting、受限shared 100M | identity/hash/fingerprint equality否决；其他String profile需独立qualification |
| TV9 | Result Delivery | Eager默认；同步callback-scoped streaming作为read-only有限试点 | ordinary Iterator、pull cursor、Publisher、async/effect及partial detached否决 |

TV9原先对public signature、production terminal、真实Group/Join/GC成本的
inconclusive，已由generated typed visitor、production Table/operation、Delivery
lane和实际weak-reference GC关闭。其他硬件相关精确阈值继续由formula/qualification
拥有，而不是写成public semantics。

## 5. 最终核心设计

### 5.1 Metadata 与实际数据

完整体系包括`SomaMetadata`及Schema/Table/Column/Type/Key/Unique/Index/
Ownership Descriptor，create前可修改的schema-seeded Plan Builder，freeze后的
Effective Metadata，以及detached Group/Table/Segment/exact/Unique/Index Runtime
Metadata。Descriptor是Metadata子项。执行hot path只消费已解析的ordinal、primitive
和formula结果，不解释Metadata。

### 5.2 类型与ownership

V1只有四类field：

- primitive-backed scalar；
- reference-backed immutable scalar，白名单仅String；
- compiler-flattened `@SomaValue`；
- parent-owned `@SomaChild` structured state。

任意Java object、List、Map和object graph不能成为普通schema field。应用对象通过
stable ID与application sidecar/registry关联。

显式SomaGroup统一多个root的stable composition、structural ledger、metadata与
reverse release；implicit Group保持单Table journey一致。每个root仍有独立aggregate
trust/identity，Group不提供transaction、snapshot isolation或Join prerequisite。

### 5.3 Capability 与physical execution

V1 closed Capability Set覆盖Schema/Metadata、Storage/Layout、Access、Mutation、
Exact/Relation、Transformation、Execution、Result Delivery、Resource及
Observation/Failure。一个logical semantics可以绑定多个受限physical plan，但
不能出现universal后端或逐行generic dispatch。

Candidate、Group/Join/Window、Delta、parallel与delivery都有versioned formula/
protocol identity。Storage Segment、Execution Vector与Parallel Morsel分离；单个
Segment的Medium输入仍可按morsel并行，但crossover不允许无条件提交任务。

### 5.4 Result Delivery

Eager Detached保持完整、原子、易推理的默认。唯一惰性试点是同步
callback-scoped typed visitor：read-only、不可逃逸、支持early stop，并继续经过
cardinality/resource/lifecycle preflight。consumer exception、cancel、deadline与
cleanup使用同一Invocation ledger；外部callback side effect不由SOMA回滚。

## 6. Production implementation 与replacement closure

当前executable identity为：

- `soma-generated-runtime-v11` / `soma-runtime-java8-v11`；
- `soma-runtime-plan-v6`；
- `soma-transformation-v3` / `soma-kernel-v4` / planner v4；
- storage、primary locator、Candidate、relation、Delta、morsel scheduler和
  Invocation ledger均有versioned v1 identity。

Production新增或完成完整Runtime Metadata、flat/head-tail storage、
flat-compact locator、closed Candidate/relation paths、changed Delta、bounded
morsel scheduler、Invocation ledger、callback delivery与component stats。

被删除的旧Owner包括generic Object column/value family、consumer-in-Definition
borrow families、`CandidateBorrowAccess`、`ParallelExecution`/
`ParallelCandidateExecution`、`StorageBudget`及相关dead tests/protocol tokens。
每项删除都已有唯一production successor与public/generated/external evidence；
不存在temporary adapter、双轨API或未来consumer才能成立的slice。

## 7. Production-shape qualification

Qualification ID：
`runtime-scale-qualification-20260728-dfe8fa98b2a4`。

环境：Azul Zulu OpenJDK `1.8.0_492-b09`、Maven `3.9.16`、macOS `26.5.2` /
Darwin `25.5.0`、`aarch64`、Apple M5 Pro 18 processors、48GB物理内存、G1GC。

Identity/checksum：

- source list `sha256`：
  `dfe8fa98b2a411708359a378e05f22e2ad89a7b900c70d1f71e8dd1a6b7f8e69`；
- combined artifact `sha256`：
  `71f332598b897f9319afe08dbf12ba6292e44e52f64e7cf053b1c37e79d89e63`；
- strict schema `sha256`：
  `b124a985cc6581ba25e41dd3aabbbfbd906d76c955f2bf04770f3a50f534c8ea`。

| Lane | 结果 | 关键事实 |
|---|---|---|
| Small/Fast | passed，56.1ms | 0/1/16/256/1K/4K primitive+String，create/point/exact/scan/column/batch/mutate/Group/Join/callback fixed-tax matrix完整 |
| Medium | passed，108.2ms | 32K/64K/256K；1次sequential、2次parallel；1/2/8 storage segments与1/8/16 tasks证明Segment和morsel分离 |
| 1M | passed，639.9ms | Point/Exact/Scan/Column/Batch/Delta/Join/Group/Window及String selector/presence/no-op；实际weak reference cleared |
| 10M | passed，1.220s | segmented growth、fused aggregate、bounded relation、4,096-cardinality shared String与10M distinct String objects |
| 100M Single | passed，5.185s | 实际resident 100,000,000行；`long Key + int groupId + long metric`；structural high-water 5,493,306,072B；bounded scalar output |
| 100M Double | passed，10.352s | 两个root各100,000,000行同时resident；same-Group 65,536与cross-Group 4,096 bounded join；structural high-water 10,983,204,272B |
| 100M String | passed，2.422s | 两个100M root、18..34 UTF-16、cardinality 1,024、跨表100%共享对象、payload/group/join；structural 4,027,622,496B、reachable String model 90,112B；impossible profile分配前拒绝 |
| Expansion | passed，41.0ms | known over-budget、checked overflow、unknown-unprovable均在child enumeration/callback前拒绝；`childBindingCallsBeforeReject=0` |
| Delivery | passed，57.3ms | Eager及Candidate/Value/Group/Join/Window/String共7种；early stop、typed failure、cancel/deadline、non-escape、String GC、ledger归零 |
| Soak | passed，136.7ms | 100次create/load/mutate/Delta/parallel/callback fault/clear/release；100个String weak reference清除，Group/Invocation ledger归零，managed executor关闭 |

十条required lane均为`applicable=true`、`status=passed`、
`claimAllowed=false`。所有record经strict validator、complete-set validator、
shrunken-double100M、invalid claim与extra-field negative case验证；runner/validator
classfile major为52。

## 8. String 正式结论

SOMA V1可以正式支持reference-backed immutable String，但结论受profile约束：

- 存caller reference，不copy/intern/normalize；
- Key/Unique/Index/Group/Join使用authoritative value equality/hash/order；
- required null拒绝，optional absence与empty String区分；
- equal-value different-object更新为no-op，不替换reference或推进epoch；
- replace/failure/remove/clear/release后dead reference不再从SOMA结构可达；
- resource报告分别列SOMA-owned structural bytes、SOMA-retained reachable String
  model和JVM observed heap。

任何规模陈述必须同时说明UTF-16长度、value cardinality、distinct object
identity、共享率、presence、字段角色与同时live Table count。10M distinct-object
payload和双100M高共享payload证明两种不同mechanics，不能互相外推；高cardinality
String Key、多String列、长文本或mutation-heavy 100M仍需新的profile qualification。

## 9. 三个 Example 审计与治理

审计结论不是全面重写。三个应用的业务模型、算法叙事、分层、detached Result、
领域validator和独立consumer边界已经正确，予以保留。唯一共同真实偏差是相关root
Table共享lifecycle/resource owner，却由应用手工创建和逆序释放：

| 应用 | 治理结果 | 未改变事实 |
|---|---|---|
| industrial scheduler | 九槽`industrial-scheduler-solve`显式Group；Runtime唯一owner并公开detached Group Metadata | primitive frontier、完整调度约束、AssignmentSummarizer、Problem/Result |
| grassing simulation | 两槽`grassing-simulation-session`显式Group | six-system order、AoS oracle、grass/application scratch、Result |
| RTD | 两槽`real-time-dispatch-horizon`显式Group | reusable Definition/Template、Join/GroupBy、application commit、plain-Java reference |

Lifecycle tests验证active member/Table count、partial-create closure及release后资源
归零。Group/protocol改变RuntimePlan identity，因此九份application baseline按正式
5-fork公式版本化重校；config/input/result、Schema、领域validator及RTD
Definition/Template identity保持。Fast/Scale/Soak/Full 3-fork comparator均通过。

## 10. 代码与测试规模审查

审查按Design Intent、责任闭合、replacement closure和完整call chain判断，不按LOC、
单实现、单调用者或“暂未使用”机械删除。

当前generated footprint共有22张Table，每张恰有一个Scan和一个DataFlow companion：

| Surface | Tables | source bytes | source lines | class-family bytes | nested types |
|---|---:|---:|---:|---:|---:|
| neutral | 9 | 215,416 | 944 | 306,239 | 77 |
| industrial | 9 | 218,411 | 949 | 315,029 | 74 |
| grassing | 2 | 47,807 | 215 | 69,151 | 17 |
| RTD | 2 | 47,586 | 213 | 67,775 | 17 |
| neutral DataFlow | 9 | 101,772 | 1,064 | 349,331 | 92 |
| industrial DataFlow | 9 | 100,071 | 1,024 | 359,855 | 91 |
| grassing DataFlow | 2 | 22,266 | 235 | 77,965 | 20 |
| RTD DataFlow | 2 | 22,682 | 236 | 78,726 | 21 |

Qualification新增三张neutral schema Table，因此fixed candidate从6调整为9，并按
首个完整candidate设置15%ceiling；`check-scan-code-size.sh`通过。大型
`RuntimeScaleQualificationWorkloads`保留为一个cohesive evidence owner：它共享
同一strict schema、pre-registration、resource snapshot与lane lifecycle，当前没有
独立co-change或责任冲突证据支持仅为缩短文件而拆分。

## 11. Validation 与 Gate

本专题在Azul Zulu full JDK 8上覆盖：

- Maven reactor `verify`；
- public API、compiler/codegen、dense/keyed/access/child/breadth external
  consumers与Java major 52；
- DataFlow Slice A–F、48-trial reference differential、component benchmark；
- benchmark strict smoke、negative artifacts、baseline architecture与code size；
- 三个Example correctness/architecture及九个profile Full performance；
- 十lane runtime-scale qualification；
- `scripts/check.sh`与`git diff --check`。

DataFlow component baseline版本化为v2。归因证据显示全部execution checksum保持，
authoring checksum随v11/v3/v4 canonical identity预期变化；bounded morsel不再把
task数锁死为worker数，Invocation ledger又增加显式phase accounting，因此count和
large-sum parallel allocation三fork稳定为`3,252.125`与`3,706.875 B/op`。新
ceiling按原公式`ceil(max * 1.15 + 256)`为`3,996`与`4,519 B/op`；其他v1
allocation/timing/tail/GC envelope全部保留，没有用全面rebaseline掩盖回归。

最终Gate状态：

| Gate | 状态 | 说明 |
|---|---|---|
| G0 | passed | Java-only scope、正式Owner和claim boundary未改变 |
| G1 | passed | 四类type、String、Metadata/schema/compiler diagnostics |
| G2 | passed | deterministic codegen、generated Metadata/Group/delivery及golden |
| G3 | passed | storage/access/relation/scheduler/resource/delivery/failure |
| G4 | passed | ordinary external Maven Java 8 consumer journeys |
| G5 | passed | differential、component、runtime-scale及三个Example |
| G6 | blocked | 缺真实release事实；本地evidence不能替代 |

## 12. 可以确认、仍需验证、必须降低的声明

### 12.1 可以确认支持SOMA目标

- 当前产品叙事和closed Capability architecture可在Java 8 production code成立；
- 完整Metadata/Group/ownership/lifecycle、四类type与String baseline成立；
- Small/Medium不必依赖Segment数量决定并行，Large storage和bounded scheduler可
  同时成立；
- single/double100M窄numeric profile、双100M高共享String profile在记录环境可
  完成、结果正确、resource bounded；
- impossible expansion能在分配/枚举前稳定拒绝；
- Eager默认与callback-scoped delivery可以共享同一semantic/resource boundary；
- 三个真实Example可以按最佳实践消费新Group/Metadata而不牺牲领域设计。

### 12.2 需要更多技术验证

- 其他CPU/JDK build/OS、支持矩阵和跨环境性能；
- wide schema、composite Key、mutation-heavy 100M；
- 更长/多列/高cardinality Key或不同共享率的String 100M；
- 新relation multiplicity/skew、global materialization/sort/window profile；
- public performance claim、长期更高强度soak和真实production telemetry。

这些不是当前正式能力的隐藏缺口；只有当产品要扩大相应claim时才需要新的、预注册
且有退出条件的验证。

### 12.3 当前必须保持较低的目标/声明

- “100M”从任意Schema承诺降低为明确schema/workload/resource qualification
  profile；双100M必须是两张同时驻留root；
- String从“Java reference能装下任意object”降低为白名单immutable String value；
- Lazy Output从通用Iterator/Generator/Publisher降低为同步read-only callback
  capability；
- 高展开从“尽力执行”降低为可证明bound，否则分配前拒绝；
- G6保持blocked，不把本机技术成功表述为release/production readiness。

这些限制不是实现偷懒，而是当前计算机体系、JVM heap和可预测resource语义下更诚实、
更可产品化的目标形态。

## 13. Scope non-regression

- Java 8、Schema-Defined、Compiler-Specialized、heap-resident目标未缩水；
- annotation/schema/hash、Access、mutation、exact、child ownership、DataFlow
  logical semantics、failure atomicity与三个应用领域结果均保留；
- 一个logical semantics允许多个受限physical plan，但未新增第二套public语义；
- 没有generic Object、reflection interpreter、Collection/DTO live storage、
  temporary public API、test-only bypass或future canonical migration；
- 新增Metadata/Group/Result Delivery/qualification是additive completion；
  删除旧Object/borrow/parallel owners是clean replacement；
- Example只修复真实ownership偏差，没有为了展示新API而重写正确算法；
- G6和`CF-005`/`CF-006`未被本地evidence偷偷关闭。

## 14. Goal Traceability 与本地提交序列

G01–G17分别由正式Blueprint/Design、Implementation Map、Conformance、本报告及
对应production/evidence闭合。关键本地提交序列为：

1. `380722c`治理Goal契约；
2. `e90d2dd`Goal traceability；
3. `994e059`TV0–TV9；
4. `335a962`最终设计；
5. `f226d56` / `aea5cc0`独立设计与scope audit；
6. `d5f713d`正式Design promotion；
7. `d5bdc9c`旧Temporary退役；
8. `73d2a25`production disposition；
9. `7925a10`、`3c8d425`、`515bf91`、`6cde5d5`及对应trace，完成前四个production slices；
10. 最终candidate以本报告记录的production source sha256绑定后续
    Metadata/execution/delivery/qualification/Example/closeout事实。

production source sha256只绑定可执行产品与evidence surface，避免文档或commit
identity自指。最终治理变更在完整Gate后作为一个原子closeout边界固化；精确commit
identity由Git history拥有，不在报告正文内反向写入。

## 15. Temporary 与 Lab 退役

TV0–TV9的长期结论已经分别进入正式Design、Engineering、production、strict
qualification schema/runner、Conformance与本报告。一次性独立Lab和本专题
Temporary不再拥有任何唯一事实，已按授权删除，不归档。可重放入口保留在SOMA
repository；历史精确实现链由本地Git提交和本报告记录。

## 16. 最终产品判断

经过这次治理，SOMA不只是“能跑大数组”的库，而具有了清晰产品骨架：schema定义
语言、compiler specialization、完整Metadata/Plan control plane、可组合owner、
closed capabilities、受限physical strategies、可预测resource/failure、两种明确
Result Delivery和可重放qualification。

当前candidate足以支撑SOMA Java V1的正式技术目标以及记录profile的本机scale
readiness。下一阶段若要扩大到更多String/Schema/环境或public release，应新增
对应事实，而不是再次重写核心叙事。G6在这些外部事实齐备前继续blocked。
