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
- Field/Mapped distinct、reference-null map、`mapToInt/mapToLong/mapToDouble`；
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
- one-shot consumption 和 `STREAM_ALREADY_CONSUMED`；
- Record/Editor O(1)/O(P) reuse，Value View 无 per-record DTO；
- `Record.fetch()` detached、`Editor.fetch()` staged candidate、View fetch explicit
  materialization；
- callback-end/foreign Table/thread/execution/participant、direct mapper-result 的
  `CALLBACK_SCOPE_VIOLATION`；
- 同 participant old alias 明确为 unsupported stateful callback，不虚构完全检测；
- hot path 无 Field/constructor reflection、metadata interpretation、boxing collection；
- concurrent Read/Read、Read/Write、Write/Write admission，conflict fail-fast，无等待/
  retry/partial progress；
- source Table direct operation/terminal reentrancy failure；
- cross-Table sequential access 独立 admission、无 cross-Table atomicity；
- selection Update/Remove all-or-nothing；parallel mutation staging/validation/one
  publish；worker failure zero progress；
- point/selection no-match、logical no-op、real change/remove、stateVersion 规则；
- external callback side effect 不属于 rollback。

## 6. G5 Sequential 与 parallel execution

- `stream()` callback 只在 caller thread 顺序执行；
- Table/Index/Field `parallelStream()` 共享唯一 library-wide `ForkJoinPool`，不接受
  arbitrary Executor，不创建 per-Pipeline pool；
- custom/common-pool freeze、same-instance idempotence、different/null conflict、
  SOMA 不 shutdown application pool；
- read-only metadata observation 不返回 raw pool；
- P==1、小/大 selection，active callbacks 不超过 P，caller participation 计入 P；
- task fan-out 有界，terminal return/failure 前 all workers quiescent；
- callback thread-safety/non-interference contract；parallel `forEach` side-effect order
  unspecified，sequential保持 encounter order，`forEachOrdered` absent；
- deterministic callback 下 sequential/parallel Query result、order、reduction、
  short-circuit frontier、mutation state 和 non-resource failure equivalent；
- worker failure canonical position/work-unit arbitration；
- custom pool shutdown/rejection 无 common fallback、sequential downgrade 或 retry；
- callback nested parallel failure；cross-Table direct/sequential operation 保持独立；
- parallel Update/Remove failure zero publication 与 deterministic survivor order。

## 7. G6 Result 与 failure

- `void add`、`UpdateResult.matched()/changed()`、`RemoveResult.removed()` exact
  signatures；
- normal Optional/no-match/no-op 与 contract failure matrix；
- `SomaOperationException`、`SomaFailureCode`、operation/context/cause exact Java
  shape/package；
- 每个 stable code 的 positive/negative trigger；
- context immutable/sanitized，无 arbitrary Key/Object stringify 或 physical state；
- fixed phase precedence；parallel canonical arbitration；short-circuit decisive frontier；
- one primary failure、无 nondeterministic suppressed worker failures；
- structured failure 后 payload/Key/Index/size/capacity/version 可信且 workers quiescent；
- application `Math.addExact` -> `CALLBACK_FAILED` + cause；nested SOMA failure 不二次
  包装；JVM Error 不伪装；
- no AddResult、public currentness/unsupported code、string/message parsing、checked
  hierarchy 或 live Result handle。

## 8. G7 Performance 与 reference scenarios

- 对直接 detached allocation、application-owned carrier reuse、View/cursor allocation
  进行真实大规模 profile；
- primitive hot path、Value flattening、Index、materialization、parallel scheduler 的
  allocation/CPU/memory evidence；
- 在最多 16 cores、32 GB memory 的约束环境内，覆盖小/中/大规模与 saturation；
- 调度、仿真、实时派工三个 reference scenario 重新验证表达力、correctness 和性能；
- 记录 scenario input、规模、warmup、JVM/GC、hardware、commit、profile 和 baseline；
- 没有 production implementation 前，G7 为 `NOT_EVALUABLE`，不得复用 predecessor
  benchmark 或 P2 kernel 作产品性能 claim。

## 9. Gate status

| Gate | 当前状态 | Evidence |
|---|---|---|
| G1 | PARTIAL_FEASIBILITY_ONLY | P2 selected generated shapes/full-regeneration boundary |
| G2 | NOT_IMPLEMENTED | 无 production storage/Index |
| G3 | PARTIAL_FEASIBILITY_ONLY | P2 selected consumer/negative/type shape |
| G4 | PARTIAL_MECHANISM_ONLY | P2 bounded cursor/View/owner harness |
| G5 | NOT_IMPLEMENTED | 无 production scheduler/admission |
| G6 | PARTIAL_SHAPE_ONLY | P2 minimal carrier shape；无 runtime mapping |
| G7 | NOT_EVALUABLE | 无 production implementation |

任何上表状态变化都必须链接可重放 command/report 和适用 commit，不能只修改文字。
