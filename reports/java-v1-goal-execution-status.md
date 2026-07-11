# Java-only SOMA V1 Goal execution status

状态：active
更新日期：2026-07-11
唯一 Codex Goal：`完成完整 Java-only SOMA V1.0，并通过 G0–G6。`
Goal thread：`019f4bf2-6fb4-7d71-ad13-72e1abe9ba03`
当前 repository baseline：`3292e7a`
当前 checkpoint：Phase 4 parent-owned child 与 recursive materialization（连续实施中；阶段末集中验证/修复/报告/提交）

本文件是可恢复的执行状态与审计入口，不是设计事实源。产品语义仍只来自 `docs/README.md` 及各 module formal Owner；Capability/Gate 定义仍只来自 implementation strategy/validation gates。

## 1. Checkpoint status

| Checkpoint | 状态 | Exit/evidence |
|---|---|---|
| Phase 0 compiler/build foundation | completed | commits `2346252`、`c5fbfbf`；Phase 0 report |
| Phase 1A dense Row Pipeline / mutation terminal | completed | runtime protocol `13179d2`、depth budget `2b2b643`、processor/generated facade `c067dac`、dense read terminals `bcf0966`、dense remove `ca64774`；这是最终 V1 API 的已验证子集，不是版本或 capability closeout |
| Phase 1B Column Pipeline / ColumnView / borrow lifecycle | completed | commit `10a6107`；seven primitive typed Pipeline/View、optional presence、view pin/close/release/stale runtime state、public/generated/consumer evidence；这是最终 V1 API 的已验证子集，不是 capability closeout |
| Phase 1C dense shape/differential evidence | completed | commits `5ddaee3`、`e0d5b16`；randomized detached oracle、Column Pipeline allocation-scaling check、generated source/bytecode anti-pattern check；Phase 1 完成但完整 V1 Goal继续 active |
| P2-A int keyed identity vertical slice | completed | commit `c8480a6`；`@SomaKey int`、`HashIntKeySpace`、generated direct/Key Pipeline、typed key errors、external consumer 和 shape evidence；这是最终架构的有效子集，不是 Phase 2 或 V1 closeout |
| P2-B-S1 long keyed binding | completed | commit `d6517a6`；`HashLongKeySpace`、primitive `long` direct/Key Pipeline、collision/rehash/compaction evidence；是 P2-B additive completion，不关闭 Phase 2 |
| P2-B-S2 seven primitive keyed binding | completed | commit `da2db9e`；boolean/byte/short/float/double direct binding、strict floating canonicalization和external consumer evidence；是 P2-B additive completion，不关闭 Phase 2 |
| P2-B-S3 enum + semantic keyed binding | completed | commit `8d64122`；required enum static ordinal binding、enum Pipeline/View、DATE/TIME/DATE_TIME primitive semantic key external consumer；是 P2-B additive completion，不关闭 Phase 2 |
| P2-B-4a scalar value key | completed | commit `f013bc1`；single-leaf immutable `@SomaValue` 直接绑定 primitive column/KeySpace，Batch/import 无 value allocation；是 P2-B additive completion，不关闭 Phase 2 |
| P2-B keyed identity breadth / Key Pipeline | completed | commit `af8ba51`；flat/nested primitive、String、enum value leaves递归 flatten，generated full equality/hash、collision/compaction、staged atomic import、external consumer和完整 `check.sh` evidence；Capability仍待 Phase 5/G2/G3 closeout |
| Phase 3 access structures | completed | commit `3292e7a`；selector/index/unique/order、primitive sidecar、mutation/lifecycle/stats、external oracle与shape evidence；见 Phase 3 report，Capability仍保持 `in-progress` |
| Phase 4 child ownership | in-progress | child forest/cascade/replacement/recursive materialization；阶段内部连续实施，结束后集中 validation/closeout |
| Phase 5 full breadth + G1-G4 | pending | annotation/processor/codegen/runtime/incremental/compatibility closeout |
| Phase 6 examples/benchmark/release + G5-G6 | pending | formal scenarios、Access Pattern Cards、JSONL benchmark、package、License/SCM/contact/provenance/reproducibility/support matrix |
| Final total audit | pending | 23 Capability + G0-G6；仅此时 Goal completed |

Phase 0-6 不是版本、MVP 或独立 Goal。任何 checkpoint completed 都不改变唯一 Goal 的 active 状态，直到 G0-G6 全部通过。

### 1.1 优化后的持续交付路线

下列是当前 Goal 下的细粒度执行顺序和每项最终出口，不是新的产品版本或范围裁剪。每个切片只能 additive completion 或 contract-preserving internal refinement。

| 顺序 | 切片 | 最终出口 | 明确保留的后续 breadth |
|---|---|---|---|
| P2-B-4 | value/composite identity | completed：single/multi/nested primitive、String、enum value leaf flatten、generated static equality/hash、无 transient tuple lookup/compaction/Key Pipeline和staged atomic import | P3–P6 全部项 |
| P3 implementation | access structures完整实施 | completed：selector normalization、typed/grouped source、index/unique/order primitive sidecar、strict access、mutation/row-move/lifecycle/stats | P4–P6 全部项 |
| P3 closeout | 集中验证与修复 | completed：compile/diagnostic/schema/public/generated/runtime/external consumer/oracle/shape/check.sh evidence、V1 non-regression、commit `3292e7a` 与 Phase 3 report | P4–P6 全部项 |
| P4-A | child handle ownership | List/Map child creation、forest/reparent/cascade/replacement lifecycle | recursive detached projection |
| P4-B | recursive materialization | budget/path/all-or-nothing subtree projection | remaining field/default/processor breadth |
| P5-A | field/codegen breadth | string、value field、optional enum/value、defaults、direct/Writer final shape | incremental/diagnostic and compatibility closeout |
| P5-B | compiler/processor closeout | invalid matrix、incremental/schema hash/public-generated compatibility evidence | examples/benchmark/release evidence |
| P6-A | formal scenarios | Java 8 examples、Access Pattern Cards、external package use | benchmark/release evidence |
| P6-B | benchmark evidence | canonical hot lanes、runner、structured JSONL and allocation/bytecode evidence | package/release evidence |
| P6-C | G6 release evidence | package/license/SCM/contact/provenance/reproducibility/support matrix reports | final G0-G6 audit |

## 2. Capability status

当前 20 项 Capability 为 `in-progress`，没有任何一项因垂直切片而被错误标记 completed。Phase 0 已进入的八项保持 `in-progress`：

- `V1-ANNOTATION-SCHEMA`；
- `V1-COMPILER-LOWERING`；
- `V1-PROCESSING-MODEL`；
- `V1-SCHEMA-HASH`；
- `V1-PUBLIC-COMPATIBILITY`；
- `V1-SECURITY-INTEGRITY`；
- `V1-EVIDENCE-TOOLING`；
- `V1-CONSUMER-PACKAGE`。

Phase 1 与 P2-A 已把以下十一项从 `not-started` 推进为 `in-progress`：

- `V1-GENERATED-API`、`V1-DENSE-STORAGE`、`V1-ROW-PIPELINE`、`V1-MUTATION`；
- `V1-MATERIALIZATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-RUNTIME-PLAN`、`V1-PERFORMANCE-SHAPE`。
- `V1-COLUMN-ACCESS`、`V1-KEYED-IDENTITY`。

`V1-ACCESS-STRUCTURES` 已由 Phase 3 从 `not-started` 推进为 `in-progress`。其余三项仍为 `not-started`，并全部保留原完整出口：`V1-CHILD-OWNERSHIP`、`V1-SCENARIO-BENCHMARK`、`V1-RELEASE-EVIDENCE`。

## 3. Gate status

| Gate | 状态 |
|---|---|
| G0 | passed |
| G1 | not-started |
| G2 | not-started |
| G3 | not-started |
| G4 | not-started |
| G5 | not-started |
| G6 | not-started |

## 4. Current slice declaration

已完成 vertical slice：P1-S1 generated dense primitive/presence kernel（commits `13179d2`、`2b2b643`、`c067dac`）。它不是产品版本，也不代表 Phase 1 或任一 Capability 完整完成。

已完成 vertical slice：P1-S2 dense `remove` terminal。它以 reusable primitive row-index selection 和 reusable boolean mark scratch 选择 rows，稳定原地压缩全部 primitive/presence columns，成功后只在 row set改变时一次性提升 structural epoch；不留下 tombstone/hole，也不以 materialization/List/DTO 中转。它同时固化了最终 `RemoveResult` public shape、Rows/Table convenience API、external Maven consumer/API golden 和 callback failure no-partial-state evidence。

已完成 vertical slice：P1-S3 typed Column Pipeline / ColumnView。每个 supported primitive leaf 现在生成最终 `fieldValues()` / `fieldColumn()` facade；Pipeline 使用 primitive callback，optional 只遍历 present logical values；View 强持有 table、validated indexed read、explicit idempotent close，并通过 runtime active-view count 拒绝冲突 structural operation。final release 统一失效未关闭 view，non-structural mutator 在 active view 下仍可用。实现不引入 boxed/object traversal、Stream、snapshot/list view 或 temporary facade。

已完成 vertical slice：P1-S4 dense shape evidence。external consumer 以 fixed-seed randomized `ArrayList` detached oracle交叉验证 update/remove/packed stable survivor order/sorted result；JDK 8 per-thread allocation counter验证 warmup 后同 terminal 的 allocation 不随 32→512 rows线性增长；generated source/bytecode evidence拒绝 Stream、boxing factory、Iterator、object/boxed row-index array和 loop-local Cursor。以上均是本机结构证据，不构成性能优越或跨平台 claim。

Phase 3 access structures 已完成集中 validation/fix/独立复核并形成 commit `3292e7a` 与 `reports/java-v1-phase-3-access-structures-report.md`。`V1-ACCESS-STRUCTURES` 仍保持 `in-progress`，等待 Phase 5/G2/G3 总体验证，不把 checkpoint 冒充 Capability closeout。

当前 checkpoint：Phase 4 parent-owned child 与 recursive materialization 连续实施。按加速策略，child handle/registry、required/optional facade、cascade/replacement、recursive materializer和budget/path不拆成等待确认；实现主体连续完成后统一集中测试和修复。

涉及 Capability：`V1-ANNOTATION-SCHEMA`、`V1-PROCESSING-MODEL`、`V1-SCHEMA-HASH`、`V1-GENERATED-API`、`V1-CHILD-OWNERSHIP`、`V1-MATERIALIZATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-RUNTIME-PLAN`、`V1-PERFORMANCE-SHAPE`、`V1-EVIDENCE-TOOLING`、`V1-CONSUMER-PACKAGE`；其他 Capability 状态不回退。

唯一 Owner：annotation schema contract拥有 `@SomaChild` declaration；schema processing/code generation contract拥有child validation、normalization和static binding；Generated Table API/materialization contract拥有typed child facade与detached recursive shape；TableStore/runtime lifecycle/errors/plan/performance Owner分别拥有opaque handle material、forest/cascade/replacement、failure/stats、budget identity和allocation-bounded traversal；testkit只拥有comparator/evidence。

Phase exit：List/Map child handle与ownership registry形成forest；required/optional child区分unallocated-empty、absent、present-empty和present-data；ensure/unset/replace/delete/clear/release级联满足pinned-subtree preflight与all-or-nothing；recursive materialization按List/Map shape共享deterministic depth/table/row/leaf/allocation budget/path；无share/reparent/orphan/cycle。

仍保留的 V1 breadth：string/optional enum/value/default余量、processor/codegen full hardening、formal examples/Access Pattern Cards、benchmark JSONL、package、License/SCM/contact/provenance/reproducibility/support matrix，全部仍在原 Phase/Gate。

禁止捷径：Java Collection/public object作为live child storage、attach/reparent API、handle泄漏、optional sentinel猜测presence、partial subtree publication、失败后rollback式补救、materialization修改live state、lazy/live returned collection、无界递归、test-only ownership bypass、本机 smoke冒充Gate/RC。

计划 evidence：child declaration/invalid cycle compile matrix、normalized schema/hash和generated/public javap、registry/forest/cascade/replacement/pinned invariants、recursive materialization comparator与budget/path failure、external Maven consumer、source/allocation shape、阶段末`./scripts/check.sh`。

### 4.3 已完成 P2-A int keyed identity vertical slice

涉及 Capability：`V1-ANNOTATION-SCHEMA`、`V1-PROCESSING-MODEL`、`V1-SCHEMA-HASH`、`V1-GENERATED-API`、`V1-KEYED-IDENTITY`、`V1-DENSE-STORAGE`、`V1-ROW-PIPELINE`、`V1-MUTATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-PERFORMANCE-SHAPE`、`V1-EVIDENCE-TOOLING`、`V1-CONSUMER-PACKAGE`；均保持 `in-progress`。

唯一 Owner：annotation schema contract拥有 `@SomaKey`；schema processing/code generation contract拥有 normalized key role、schema hash 和 generated binding；Generated Table API contract拥有 direct/Key Pipeline signature；TableStore、errors和lifecycle Owner 分别拥有 KeySpace、failure和structural commit；testkit拥有 fixture/golden/evidence。没有联合 Owner。

实际交付：`@SomaKey` 保持 source-only public annotation；processor 将 table kind/field role归一化为 `keyed`/`key`，且对尚未支持的 key breadth fail-closed；generated keyed table以 `HashIntKeySpace` 绑定 packed primitive `int` key column，提供 `containsKey/find/fetch/mutate/delete/keys`。key identity 不进入 Mutator、MutableRow 或 Row Pipeline update setter；delete 与 Rows.remove 共用单次稳定 compaction，并在 commit 前删除 dead mapping、修复 survivor RowSlot。Key Pipeline 是稳定值 export boundary，primitive direct/row/column hot path仍无 boxing。

实际 evidence：5000-step deterministic primitive KeySpace oracle；external Maven keyed consumer覆盖 add/find/fetch/mutate、duplicate/missing、direct delete与Rows.remove后的slot repair；unsupported long/multiple key declarations稳定以 `SOMA-TABLE-008` fail closed；generated source/bytecode/public javap/schema-hash golden、Turkish locale/Pacific-Kiritimati repeatability、key mutation surface negative assertion和完整 `check.sh`。本机证明不外推 support matrix。

V1 scope non-regression：P2-A/P2-B-S1/S2 只添加最终 `@SomaKey`/keyed facade/seven primitive KeySpace binding；semantic/enum/value/composite、secondary access structures、child/materialization、scenario/benchmark/release breadth仍保留在原 P2-B 或后续工作包。无 temporary public/generated contract、temporary hot path、migration、rewrite或 `List<Row>`/DTO/metadata interpreter runtime path。

### 4.4 已完成 P2-B-S1 long keyed binding

涉及 Capability：`V1-KEYED-IDENTITY`、`V1-GENERATED-API`、`V1-DENSE-STORAGE`、`V1-MUTATION`、`V1-RUNTIME-ERRORS`、`V1-PERFORMANCE-SHAPE`、`V1-EVIDENCE-TOOLING` 和 `V1-CONSUMER-PACKAGE`；全部继续保持 `in-progress`。

实际交付：以与 `int` 相同的 final generated facade shape增加 `long` direct key parameter；`HashLongKeySpace` 保持 long full equality、primitive probe、tombstone-only remove和 row-slot update。generated key binding根据 normalized primitive key type静态选择 `HashIntKeySpace` 或 `HashLongKeySpace`，没有 generic `Object` key path或 key truncation。int/long `duplicateKey`、`missingKey` overload保持相同 stable code/category/context contract。

实际 evidence：5000-step deterministic long KeySpace oracle；external Maven consumer覆盖高位/negative `long` lookup、mutate、stable Key Pipeline export及delete compaction repair；schema/hash/public javap/source-shape/repeatability和完整 `check.sh` 通过。仍未实现的 enum/value/composite/semantic breadth仍属于 P2-B，未删除、未降级、未标记 optional。

### 4.5 已完成 P2-B-S2 seven primitive keyed binding

涉及 Capability：`V1-KEYED-IDENTITY`、`V1-GENERATED-API`、`V1-DENSE-STORAGE`、`V1-MUTATION`、`V1-RUNTIME-ERRORS`、`V1-PERFORMANCE-SHAPE`、`V1-EVIDENCE-TOOLING` 和 `V1-CONSUMER-PACKAGE`；全部继续保持 `in-progress`。

实际交付：seven primitive全部保留 primitive direct key parameter。boolean/byte/short/int/float静态绑定 `HashIntKeySpace`，long/double静态绑定 `HashLongKeySpace`；float/double在 Batch/import、lookup和compaction repair用 generated-runtime `KeyCanonicalization` 拒绝 NaN/infinity、canonicalize `-0.0`并使用 canonical bits。Key Pipeline 的 boxing仍只发生在显式 stable-value export boundary。

实际 evidence：external Maven consumer覆盖 boolean/byte/short direct lookup，float/double negative-zero canonicalization、duplicate和non-finite typed failure；generated signature/key-setter negative/source shape/schema hash/repeatability、runtime public manifest以及完整 `check.sh` 通过。enum/semantic/value/composite breadth仍属于 P2-B，未删除、未降级、未标记 optional。

### 4.6 已完成 P2-B-S3 enum + semantic keyed binding

涉及 Capability：`V1-ANNOTATION-SCHEMA`、`V1-PROCESSING-MODEL`、`V1-SCHEMA-HASH`、`V1-GENERATED-API`、`V1-KEYED-IDENTITY`、`V1-DENSE-STORAGE`、`V1-COLUMN-ACCESS`、`V1-MUTATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-PERFORMANCE-SHAPE`、`V1-EVIDENCE-TOOLING`、`V1-CONSUMER-PACKAGE`；全部继续保持 `in-progress`。

唯一 Owner：annotation schema contract 拥有 enum/semantic declaration；schema processing contract 拥有 enum member normalization、canonical JSON/hash；code-generation contract 拥有 generated static binding；Generated Table API contract 拥有 direct/key/column signatures；TableStore、runtime errors、lifecycle 和 performance Owner 分别拥有 ordinal storage、typed failure、borrow/structural state 和 hot-path shape；testkit 拥有 isolated consumer/evidence。没有联合 Owner。

实际交付：required enum table field/key 使用 exact enum public type、`IntColumn` ordinal packed storage、`HashIntKeySpace`、class-initialization-only cached enum member array、`EnumColumnPipeline<E>` 和 `EnumColumnView<E>`。lookup、Batch/import、compaction repair 和 key export 不创建 transient key wrapper/tuple，不在 row loop 调用 `Enum.values()`，也不使用 object key storage。null enum 在 visible mutation 前返回 `invalid_null_value`；duplicate/missing enum key 使用 bounded `Enum.name()` descriptor。DATE、TIME、DATE_TIME key 保持原有 `int` / `long` primitive direct binding，并有独立 schema normalization/consumer evidence。

实际 evidence：isolated external Maven enum/semantic consumer编译、运行并在默认与 Turkish locale/Pacific-Kiritimati timezone 下比较 generated source 和 schema/hash；验证 enum direct/key/Key Pipeline、column pipeline/view、null/duplicate、delete compaction repair，以及 DATE/TIME/DATE_TIME normalization/direct lookup。public runtime manifest固定 enum pipeline/view与typed error factory；`./scripts/check.sh` 全部通过。

V1 scope non-regression：该切片只把正式已经定义的 required enum 和三种 semantic scalar key接入最终 static primitive/ordinal architecture；value/composite key、optional enum/value、string/default、AccessStructures、child、materialization、examples、benchmark和release evidence仍保持原 Phase/Gate。未引入 temporary public/generated contract、temporary hot path、migration或rewrite。

### 4.7 已完成 P2-B-4a scalar immutable value key

涉及 Capability：`V1-ANNOTATION-SCHEMA`、`V1-COMPILER-LOWERING`、`V1-PROCESSING-MODEL`、`V1-SCHEMA-HASH`、`V1-GENERATED-API`、`V1-KEYED-IDENTITY`、`V1-DENSE-STORAGE`、`V1-MUTATION`、`V1-RUNTIME-ERRORS`、`V1-PERFORMANCE-SHAPE`、`V1-EVIDENCE-TOOLING`、`V1-CONSUMER-PACKAGE`；全部继续保持 `in-progress`。

实际交付：当前接受单个 primitive leaf 的 immutable `@SomaValue` 作为 `@SomaKey`。public/generated direct API 保持 value type；normalized table field 使用 `value:<FQN>`、完整 `key.leaf` path和primitive storage type。Batch、`copyBatch`、duplicate preflight、KeySpace install、remove 和 packed row move 直接传递 primitive storage accessor，不构造 value；只有 fetch/materialization、Row value getter和`Keys` explicit stable-value export构造 immutable value。null value key返回 typed `invalid_null_value`，value key conflict/missing 不泄露 payload。

实际 evidence：isolated external Maven consumer覆盖 javac lowering、value direct lookup、key export、duplicate/missing、delete compaction repair；默认与 Turkish locale/Pacific-Kiritimati timezone generated source/schema repeatability、generated source no-value-allocation import shape、public manifest和相关 keyed check均通过。

V1 scope non-regression：此切片不把单-leaf value冒充 composite completion。multi-leaf/nested value、string leaf、strict floating leaf、optional value及其 Column API/diagnostics仍在原 P2-B/P5；已固化 value public direct signature和primitive storage是后续 additive flatten的兼容基线，无 temporary contract/hot path/migration/rewrite。

### 4.8 已完成 Phase 2 keyed identity breadth

涉及 Capability：`V1-PROCESSING-MODEL`、`V1-SCHEMA-HASH`、`V1-GENERATED-API`、`V1-KEYED-IDENTITY`、`V1-DENSE-STORAGE`、`V1-MUTATION`、`V1-RUNTIME-ERRORS`、`V1-PERFORMANCE-SHAPE`、`V1-EVIDENCE-TOOLING`、`V1-CONSUMER-PACKAGE`；Capability继续保持 `in-progress`，等待Phase 5与G2/G3总体验证后关闭。

实际交付：processor递归展开flat/nested `@SomaValue` key的primitive、String和enum leaves，canonical table schema完整记录normalized leaf path/order/storage type。Generated Batch/Table使用独立primitive/`ObjectColumn<String>` leaf columns；strict float/double在写入和查询边界拒绝non-finite并canonicalize negative zero；enum只保存ordinal并使用class-init cached members。`HashCompositeKeySpace`只保存raw hash/probe state/RowSlot，generated source在同hash candidate上静态展开全部leaf equality，String hash collision继续probe，不把hash相等冒充identity相等。

Import原子性：primitive、enum、scalar value和composite value keyed table统一在触碰live columns前构造完整staged KeySpace；duplicate、hash、rehash和allocation failure都发生在publication前。copy/structural commit成功后只做不可失败的KeySpace引用发布，不留下partial key。delete/remove先定位full identity slot，再在stable packed compaction中修复survivor RowSlot；String dead reference由Batch clear和`ObjectColumn.clearRange`清除。

实际 evidence：独立 external Maven composite consumer覆盖direct contains/fetch、duplicate/missing、Key Pipeline重建、nested value、String、enum、strict float/double、`"Aa"`/`"BB"` String hash collision full equality、delete/compaction repair；默认与Turkish locale/Pacific/Kiritimati timezone generated source/schema/hash一致。source-shape拒绝value/tuple/object key live storage并固定staged-before-copy顺序；primitive/enum/scalar value历史consumer、KeySpace randomized oracle、public manifest、完整 `./scripts/check.sh`全部通过。独立shadow review发现并促成staged atomic import修复，复核后无correctness阻断。

V1 scope non-regression：Phase 2完成不关闭总Goal，也不把`V1-KEYED-IDENTITY`提前标记evidenced；P3 access structures、P4 child、P5 field/diagnostic/compatibility breadth、P6 scenario/benchmark/release evidence仍在原Phase/Gate。公开direct/Key Pipeline signature保持最终value type；无temporary tuple/API/storage、consumer migration或canonical hot-path rewrite。后续允许在不改变契约的前提下优化addBatch staged KeySpace内部算法；当前每次append构造combined staged KeySpace的O(existing+batch)成本必须进入P6 benchmark evidence，不能据此作性能优越claim。

### 4.2 P2-S1 primitive KeySpace foundation

涉及 Capability：`V1-KEYED-IDENTITY`、`V1-DENSE-STORAGE`、`V1-RUNTIME-ERRORS`、`V1-PERFORMANCE-SHAPE`；仍未把任何 Capability 标记 completed。

唯一 Owner：TableStore 契约拥有 key-to-RowSlot storage material；generated API/annotation/processor/runtime errors 各自保持其既有唯一职责。

实际交付：`SparseIntKeySpace` 使用 bounded non-negative sparse-set `key -> packed RowSlot` 映射，支持 stable compaction `removeAt`；`HashIntKeySpace` 使用 primitive open addressing、full int equality、tombstone/rehash、row-slot update。二者均不使用 `HashMap<Key,Integer>` 或 boxed runtime lookup path。

实际 evidence：5000 step fixed-seed `HashIntKeySpace` 对照 oracle、sparse stable compaction invariant、public manifest、JDK 8 `check-keyspace-phase2.sh`、docs/scope/diff checks。尚未实现 breadth：`@SomaKey` annotation/processor lowering、generated keyed facade/KeyPipeline、long/composite key binding、duplicate/missing generated errors、external keyed consumer；它们仍属于同一 Phase 2，不能因 foundation passing而被删除或延后出 V1。

### 4.1 已完成 P1-S1 记录

涉及 Capability：

- Phase 0 八项中的 annotation、processing、hash、public compatibility、security、evidence、consumer；compiler lowering保持既有 foundation；
- `V1-GENERATED-API`、`V1-DENSE-STORAGE`、`V1-ROW-PIPELINE`、`V1-MUTATION`、`V1-MATERIALIZATION`、`V1-RUNTIME-LIFECYCLE`、`V1-RUNTIME-ERRORS`、`V1-RUNTIME-PLAN`、`V1-PERFORMANCE-SHAPE`；
- `V1-COLUMN-ACCESS` 在当前 Phase 1后续 vertical slice补齐，不能由 fetch/materialization替代。

唯一 Owner 链：annotation schema、schema processing、code generation、generated API/materialization、public compatibility、TableStore/lifecycle/plan/errors/performance、security、testkit、build contract。每项行为仍由其中对应的一份唯一 Owner 拥有，不形成联合 Owner。

Slice exit：

- processor 真实生成最终命名的 dense Table/Batch/Rows/Row/MutableRow/Mutator；
- primitive/presence columnar Batch、packed `[0,size)` table、addBatch/replaceAll/clear/release；
- fetchAt/mutateAt、linear one-shot filter/skip/limit/count/forEach/update；
- whole-terminal primitive staging与callback failure atomicity；
- single-row/whole-dense detached materialization及default/explicit budget；
- create-time schema/generated/runtime/plan/estimator compatibility validation；
- structured errors/stats、generated/public/protocol manifests、external Maven consumer与shape evidence。

仍保留的 V1 breadth：value/string/enum、complete defaults、Column Pipeline/View、all terminals/remove、key/index/order、child ownership、recursive materialization、full diagnostics/incremental、examples/benchmark/package/release/support matrix。全部七种 V1 signed/floating primitive加boolean及其optional boxed presence已由本 slice覆盖。

禁止捷径：手写 facade冒充 generated、processor依赖 runtime-core、generic metadata/dtype interpreter、Object/DTO/List<Row> live storage、optional sentinel、Stream/boxing/per-row Cursor、callback直写 live columns、temporary API/protocol/storage、test-only compatibility bypass、本机 smoke冒充 Gate/RC/release。

实际 evidence：annotation/table valid-invalid compile、canonical JSON/hash golden、generated source跨locale/timezone repeatability、generated javap golden、runtime/protocol manifest、packed/presence randomized invariant、Batch/replace/clear/fetch/mutate/update/materialization/error/lifecycle、callback failure atomicity、stable primitive-index sorted、short-circuit any/none、find/required/fetchAll/rowIndexes、isolated external Maven consumer与artifact runtime graph。Dense differential、remove、Column path、cursor/bytecode/allocation shape继续由当前 slice生成。

## 5. Recovery protocol

会话或上下文切换后按以下顺序恢复：

1. 查询唯一 Codex Goal，必须仍为 active，除非 G0-G6 已全部通过；
2. 读取本报告、Phase/Gate reports、`git status` 与最近 commits；
3. 读取当前 checkpoint涉及的 formal Owners；
4. 恢复最多一个 `in-progress` step，不从 Phase 0 重做；
5. validation/commit/report后更新本报告并自动进入下一项。

如果 UI 计划再次消失，以本报告和 repository facts恢复；不得因此建立新的缩小 Goal、重复已完成工作或丢失未实现 V1 breadth。

## 6. V1 scope non-regression

- 唯一 Goal、23 项 Capability、G0-G6、RC完整性与release boundary未变化；
- Phase 0状态和证据未回退；
- Phase 1 新增了真实 annotations/runtime/processor/generated facade、primitive/presence storage、atomic update、materialization、public/schema golden和external consumer evidence；Capability只推进到 `in-progress`；
- P2-A/P2-B-S1/S2/S3 新增最终 seven primitive、semantic scalar和required enum `@SomaKey`、packed primitive/ordinal KeySpace binding、direct/Key Pipeline、no-key-setter contract、enum Column Pipeline/View和compaction repair；Capability只推进到 `in-progress`；
- P2-B-4完成flat/nested primitive、String、enum value key的recursive leaf binding、raw-hash/full-equality composite probe、staged atomic import、Key Pipeline与collision/compaction evidence；Capability仍只保持`in-progress`；
- 当前方案是最终 V1架构的有效子集，后续必须additive completion或contract-preserving internal refinement；P1-S2 固化 `RemoveResult` / remove，P1-S3 固化 typed Column Pipeline/View和borrow lifecycle，Phase 2固化primitive/enum/value/composite direct key API、floating canonicalization、ordinal/reference leaf binding与full equality，不引入未来迁移契约；
- 尚未引入temporary public/generated API、temporary hot path、migration或rewrite。

## 7. Latest validation record

- commit/artifact：`3292e7a`；reactor artifacts `soma-annotations`、`soma-runtime-core`、`soma-processor` `0.1.0-SNAPSHOT`；external artifact `external-maven-access-consumer-1.0.0-SNAPSHOT.jar`，以及 Phase 1/2 external artifacts；
- 完整命令：`./scripts/check-access-phase3.sh`、`./scripts/check-table-diagnostics-phase1.sh`、`./scripts/check-public-api.sh`、`./scripts/check-generated-keyed-phase2.sh`、`./scripts/check-runtime-core-phase1.sh`、`./scripts/check-docs.sh`、`git diff --check`、`./scripts/check.sh`；
- JDK：Azul Zulu OpenJDK `1.8.0_492-b09`，64-Bit Server VM build `25.492-b09`；
- Maven Wrapper：Apache Maven `3.9.16`；
- OS/architecture：macOS `26.5.2`、`aarch64`；
- 结果：Phase 0–2 evidence保持通过；Phase 3 selector/index/unique/order、primitive dirty/lazy sidecar、strict floating、mutation/operation atomicity、stats/lifecycle、external consumer、randomized oracle、allocation scaling、source/bytecode shape、compile/schema/hash/javap evidence和完整 `check.sh` 全部通过；
- 跳过：unsupported-javac negative lane（未设置 `SOMA_UNSUPPORTED_JAVAC`）；
- known limitation：该结果只表示上述本机环境通过，不能外推正式 support matrix；unsupported-javac negative lane未执行；G1-G6仍未关闭，Phase 4–6仍属于同一V1 Goal。
