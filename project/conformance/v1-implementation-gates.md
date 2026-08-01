# SOMA Java V1 Implementation Conformance Gates

类型：Conformance Contract

状态：Active

正式事实源：是

Owner：从正式 Design 到 production implementation、性能与 release claim 的最低证据集合

最后审查日期：2026-08-01

## 1. 使用规则

本清单合并并去重了产品基础 Temporary 中的 Design/API 成立条件。它定义“必须证明
什么”，不预先选择 module、test framework、benchmark harness 或 CI workflow。

每项 Gate 在相应 production surface 真正准入后，必须补充：Owner、command、input、
environment、artifact/report path、pass/fail semantics 和适用 commit/version。P2
feasibility 只能标记明确覆盖的子项，不能整组判定通过。

## 2. G1 Schema 与 generated surface

- 最小真实 `.schema` declaration 生成父 package 同名 immutable Value、mutable
  detached Table object、`Soma/SomaGroup`、typed Table/Field/Stream，public signature
  不泄漏 declaration type；
- 无参数 `@SomaSchema`、exact-package collection、`.schema -> parent package` 映射，
  每个 direct top-level Table 都生成 Group/default Group accessor；
- declaration package-private、non-generic、无 application API leakage；
- annotation 使用 `CLASS` retention，runtime 不依赖 reflection；
- constructor/accessor、Record/Editor/View `fetch()`、typed Field/arrays 和 shared
  Result/failure carrier 由 generated source、`javap -v`/golden 固定；
- independent Java 8 Maven consumer clean compile/run 和普通 lambda inference；
- reserved name、generated type/member/path、Index accessor、composition mapping
  collision 的 compile-negative；
- nested `.schema.schema`、generated namespace 写回另一 schema namespace 的
  compile-negative；
- Table/Value add/remove/rename/move/change 通过 full regeneration 替换 composition
  output，与 clean full equivalent，无 stale source/class；
- missing handshake、late round、partial invocation fail closed；
- future incremental path 单独通过 add/change/delete/stale-cleanup/clean-full
  equivalence；
- final surface 不含 `SomaSemantic`、`@SomaOptional`、`@SomaDefault`、`@SomaIgnore`、
  `@SomaChild`、`@SomaUnique` compatibility alias，`@SomaTable` 不接受 `name`。

## 3. G2 Identity、storage、Key/Index 与关系

- default Group identity、explicit Group isolation、同 Group 每种 Table type 唯一；
- default Group 不通过 generic live registry 泄漏/保留 explicit Group；
- Group/Table/Field metadata observation 不改变 lifecycle 或 retention；
- primitive/reference leaf、Value flatten/unflatten 与 detached materialization；
- keyless Table 不生成 point API；keyed Table add/find/get/update/remove；
- missing、duplicate Key、zero Key、Key non-null/immutable/no rekey；
- duplicate Index selection、String/Enum null bucket、Value outer/Key-leaf null；
- ordinary Object/temporal/float/double Key/Index compile rejection；
- invalid Value leaf、reference slot semantics 与 ordinary referent mutation boundary；
- whole Table、Index ordered subsequence、Field projection 与 deterministic survivor
  order；
- 1:M/N:M normal relation Table 的双向 Index journey；无 referential integrity、
  cascade、owner-scoped Table 或 cross-Table atomicity；
- size/capacity/growth checked arithmetic、resource refusal 与 invariants；
- final runtime surface 无 Segment、public Column、row identity 或 reflection interpreter。

## 4. G3 Logical API capability

- default/explicit Group、Table/Field/metadata、point、Query、Update、Remove complete
  journey；
- Record-first 与 Field-first projection lowering 到同一 plan；cross-Group owner guard；
- Record/Field/Mapped Stream 的 compile-positive/negative capability matrix；
- Key root/nested Field read-only；non-Key Field lineage update；
- `IndexSelection` 只有 `stream()/parallelStream()`；
- Record `distinct`、Field `remove`、Mapped mutation、`getFirst`、direct `clear`、
  materialization budget overload、AddResult 的 absence；
- Field/Mapped distinct、reference-null map、primitive `map/mapToObj`、
  `mapToInt/mapToLong/mapToDouble`；
- boolean/byte/short/char/int/long/float/double generated specialization；
- primitive array/aggregate hot path no boxing，primitive `toList()` 只在 result boundary
  boxing；
- Record/runtime-reifiable Field noarg typed array；Mapped reference
  `toArray(Class<A>)`；parameterized Field `toList()` only；
- mapped noarg Object array、array-factory overload、`TypeToken` absence；
- parent/Object/empty/all-null/null/primitive/incompatible component cases；
- checked sizing、detached complete result、no partial materialization，`limit(n)` 表达
  application bound。

## 5. G4 Cursor、currentness 与 atomicity

- Pipeline terminal-start late binding，在 admission 期间观察固定 SOMA-owned state；
- linked-chain no-branch one-shot、argument-failure reuse 和
  `STREAM_ALREADY_CONSUMED`；
- Record/Editor O(1)/O(P) reuse，Value View 无 per-record DTO；
- `Record.fetch()` detached、`Editor.fetch()` staged candidate、View fetch explicit
  materialization；
- callback-end/foreign Table/thread/execution/participant、direct mapper-result 的
  `CALLBACK_SCOPE_VIOLATION`；
- callback/terminal exit 清除 cursor root/Table strong reference，失效 borrowed object 不
  accidental-retain explicit Group；
- 同 participant old alias 明确为 unsupported stateful callback，不虚构完全检测；
- hot path 无 Field/constructor reflection、metadata interpretation、boxing collection；
- concurrent Read/Read、Read/Write、Write/Write admission，conflict fail-fast，无等待/
  retry/partial progress；
- source Table direct operation/terminal reentrancy failure；
- cross-Table sequential access 独立 admission、无 cross-Table atomicity；
- selection Update/Remove all-or-nothing；parallel mutation staging/validation/one
  publish；worker failure zero progress；
- point/selection no-match、logical no-op、real change/remove、stateVersion 规则；
- max-stateVersion 下 add/reserve/effective mutation overflow 与 no-op success；
- external callback side effect 不属于 rollback。

## 6. G5 Sequential 与 parallel execution

- `stream()` callback 只在 caller thread 顺序执行；
- Table/Index/Field `parallelStream()` 共享唯一 library-wide `ForkJoinPool`，不接受
  arbitrary Executor，不创建 per-Pipeline pool；
- custom/common-pool freeze、same-instance idempotence、different-instance conflict、null
  invalid argument、
  SOMA 不 shutdown application pool；
- concurrent setter/first-terminal CAS linearization；argument/reentrancy/admission early
  failure 不提前固定 common pool；callback configuration 的 phase precedence；
- read-only metadata observation 不返回 raw pool；
- P==1、小/大 selection，active callbacks 不超过 P；parallel callback 只在 effective
  pool worker 上执行；普通 external caller 不执行，already-worker caller 可参与且计入 P；
- task fan-out 有界，terminal return/failure 前 all workers quiescent；
- callback thread-safety/non-interference contract；parallel `forEach` side-effect order
  unspecified，sequential保持 encounter order，`forEachOrdered` absent；
- deterministic callback 下 sequential/parallel Query result、order、reduction、
  short-circuit frontier、mutation state 和 non-resource failure equivalent；
- worker failure canonical position/work-unit arbitration；
- custom pool shutdown/rejection 无 common fallback、sequential downgrade 或 retry；
- all-required-work accepted/completed 与 concurrent graceful/forceful shutdown 的
  linearization；
- active-but-starved pool 不误报 unavailable、不 fallback/补偿/inline，且没有隐藏 timeout；
- callback nested parallel/configuration failure；cross-Table direct/sequential operation
  保持独立；
- parallel Update/Remove failure zero publication 与 deterministic survivor order。

## 7. G6 Result 与 failure

- `void add`、`UpdateResult.matched()/changed()`、`RemoveResult.removed()` exact
  signatures；
- normal Optional/no-match/no-op 与 contract failure matrix；
- `SomaOperationException`、`SomaFailureCode`、operation/context/cause exact Java
  shape/package；
- context Table facade FQCN、Table-relative Field path、empty/global representation 与
  multi-Field canonical selection；
- 每个 stable code 的 positive/negative trigger；
- context immutable/sanitized，无 arbitrary Key/Object stringify 或 physical state；
- fixed phase precedence；parallel canonical arbitration；short-circuit decisive frontier；
- same-phase argument/consumed-stream sub-order 与 effect-dependent version-overflow order；
- one primary failure、无 nondeterministic suppressed worker failures；
- structured failure 后 payload/Key/Index/size/capacity/version 可信且 workers quiescent；
- application `Math.addExact` -> `CALLBACK_FAILED` + cause；nested SOMA failure 不二次
  包装；JVM Error 不伪装；
- no AddResult、public currentness/unsupported code、string/message parsing、checked
  hierarchy 或 live Result handle。

## 8. G7 Performance 与 reference scenarios

### 8.1 Baseline 与 scale

每个 reference scenario 都使用相同 input/semantics 比较：

1. `HAND_TUNED`：直接 primitive/reference arrays + application-owned hash structure，作为
   机制上限/成本下限；
2. `IDIOMATIC_JAVA`：detached POJO、ArrayList/HashMap 和 JDK Stream/loop，作为用户现实
   替代方案；
3. `SOMA`：只使用 production public/generated API，不调用 internal benchmark hook。

“相同 semantics”包括 Key/Index equality、encounter order、duplicate/missing handling、
mutation publication 和 materialized result；不能让 baseline 少做 validation。Structural
bytes 用同一 retained-heap method 在 full GC 后测量，包含 Table/POJO/collection/array/
Key/Index infrastructure，排除三方共同引用的 input 与 ordinary referent payload 本体。

规模至少覆盖 `10k / 100k / 1M` live records；双缓存场景覆盖两个同时可达的 1M Group。
Saturation 在 32 GB machine 上逐级增大到 logical workload 完成、estimated live set 达
最大 heap 70%，或 10M records，以先到者为准。不得依赖 swap 形成“通过”。最多使用 16
cores；报告 effective pool parallelism。

### 8.2 V1 qualification thresholds

以下是 implementation go/no-go threshold，不是对所有业务 workload 的营销承诺：

- correctness、logical result/order/failure 和 sequential/parallel equivalence 必须 100%
  通过；任何差异直接失败；
- non-materializing primitive/flattened-Value Query 在 warm state 不得有随 record count
  线性增长的 allocation；Record/Value View 保持 O(1)/O(P)；
- 1M primitive-dominant Table 的 SOMA-owned steady structural bytes 不高于等价
  `IDIOMATIC_JAVA` live structural bytes 的 70%；ordinary referent payload 本体不计入双方；
- 主要 sequential scan throughput 不低于 `HAND_TUNED` 的 65%；三个 scenario 中至少两个
  primary hot path 达到 `IDIOMATIC_JAVA` 的 1.25x；未达到 1.25x 的路径不能同时比
  `IDIOMATIC_JAVA` 慢 20% 以上且占用更多 structural memory；
- Key lookup 与 non-unique Index selection throughput 分别不低于对应 HashMap/multimap
  baseline 的 70%，同时保持正式 order、null 和 atomic-maintenance semantics；
- 在 selection 足够大且 callback cost 合理的 eligible kernel 上，P>=4 的 parallel
  throughput 至少在两个 reference scenario 达到同一 SOMA sequential path 的 1.5x；
  cheap/stateful pipeline 可以不加速，但不能违反 task/allocation bound；
- repeated add ingestion throughput 不低于等价 `ArrayList + Key/Index map` baseline 的
  70%；否则触发 public Batch 边界复审，不得以隐藏 Batch 改语义；
- candidate-root mutation peak SOMA-owned bytes 不超过该 Table steady owned bytes 的
  2.5x，且 dual-1M reference scenario 在 32 GB 内保留至少 30% max-heap headroom；
- p95 latency、GC pause/allocation、task count 和 retained Group profile 不得出现随 N/P
  超出正式 complexity bound 的增长。

### 8.3 Evidence protocol

- 调度、仿真、实时派工三个 scenario 分别验证 expression、correctness、memory、CPU、
  allocation 和 wall-clock throughput；
- 记录 scenario input/digest、规模、warmup、fork/sample、JVM/GC、heap、hardware、OS、
  commit、artifact、profile 与 baseline source；
- 报告 median、p95 与方差；先运行 correctness，再运行 profile/benchmark；
- threshold miss 必须定位到 Design/implementation Owner；在无新 evidence 时停止重复跑；
- 没有 production implementation 前，G7 为 `NOT_EVALUABLE`，不得复用 predecessor
  benchmark 或 P2 kernel 作产品性能 claim。

## 9. G8 Security、packaging 与 provenance

- processor 只读取 javac model/options，写入本 compilation 的 Filer-owned output；不扫描
  arbitrary filesystem/classpath、不访问 network、不启动 process、不加载/执行
  application class initializer；
- adversarial/edge schema identifier、generic type、diagnostic payload、collision 和
  generated source escaping 不能形成 source injection、path traversal、host path/secret
  disclosure 或覆盖 existing source；
- runtime 不使用 `Unsafe`、native/off-heap、serialization gadget、setAccessible reflection、
  background service/thread 或未准入 SPI；
- production dependency allowlist、license compatibility、vulnerability review、SBOM 和
  runtime transitive dependency tree 有 evidence；默认目标为 runtime zero third-party
  dependency，偏离必须单独 surface admission；
- packaged runtime/processor/source/javadoc 中不含 schema fixture、benchmark input、
  Temporary、local path、credential、IDE/build output 或 predecessor material；
- independent consumer 从实际 packaged artifact 验证 checksum、coordinate、manifest、
  generated-contract/version mismatch、LICENSE/NOTICE；
- future CI/release workflow 使用 least privilege、pinned action/reference、protected secret、
  reproducible command 和 artifact checksum/provenance；
- [SECURITY.md](../../SECURITY.md) 与实际 supported/release scope 一致；公开发布前完成
  dependency/code/security review 与 Owner sign-off。

没有 production artifact/workflow 前，G8 为 `NOT_EVALUABLE`；本 Gate 不授权创建
workflow、publish 或宣称 supply-chain security 已成立。

## 10. Gate status

| Gate | 当前状态 | Evidence |
|---|---|---|
| G1 | PARTIAL_FEASIBILITY_ONLY | P2 selected generated shapes/full-regeneration boundary |
| G2 | NOT_IMPLEMENTED | 无 production storage/Index |
| G3 | PARTIAL_FEASIBILITY_ONLY | P2 selected consumer/negative/type shape |
| G4 | PARTIAL_MECHANISM_ONLY | P2 bounded cursor/View/owner harness |
| G5 | NOT_IMPLEMENTED | 无 production scheduler/admission |
| G6 | PARTIAL_SHAPE_ONLY | P2 minimal carrier shape；无 runtime mapping |
| G7 | NOT_EVALUABLE | 无 production implementation |
| G8 | NOT_EVALUABLE | 无 production artifact/workflow |

任何上表状态变化都必须链接可重放 command/report 和适用 commit，不能只修改文字。
