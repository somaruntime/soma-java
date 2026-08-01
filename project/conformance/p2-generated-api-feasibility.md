# P2 Generated API Java 8 可行性证据

类型：Conformance Evidence Record

状态：Historical / Non-Replayable

正式事实源：是（仅拥有本次 evidence 结论与边界）

Owner：2026-08-01 P2 type-shape、runtime mechanism 与 build-boundary feasibility 结论

适用版本：SOMA Java clean-slate pre-implementation Design baseline

最后审查日期：2026-08-01

## 1. 记录目的

本文保存已完成 P2 Temporary validation spike 的正式结论和适用边界，使成立的事实能
被 Design 引用，同时让可整体删除的 fixture 退出 current project surface。

它不拥有产品语义，也不证明 production compiler、storage、Index、scheduler、
performance 或 release readiness。

## 2. Snapshot provenance

原 Temporary 路径：

```text
project/temp/soma-p2-generated-api-feasibility/
```

Snapshot：

| Fact | Value |
|---|---|
| Date | 2026-08-01 |
| Java/Javac | Amazon Corretto 1.8.0_502 |
| Maven | Apache Maven 3.9.16 |
| OS | macOS 26.6 aarch64 |
| Non-target files | 55 |
| Canonical file-hash-list SHA-256 | `d70f950a004832f2672d3e3fa83df6dc51b4536c3cd768e58502bed0222829c0` |
| Historical command | `./project/temp/soma-p2-generated-api-feasibility/verify.sh` |
| Historical result | `P2 generated type-shape/runtime/full-regeneration closeout verification passed` |

Tree digest 的计算输入为：按 path 排序的 55 个非-`target` file SHA-256 清单，再对清单
做 SHA-256。它用于识别本次 evidence snapshot，不把 fixture 变成永久测试 surface。
Historical command 在当前 checkout 中不再可执行，snapshot source 也不位于任何
normal product/build ref；digest 只能识别已经审查过的历史快照，不能从当前仓库重建它。
未来若需要重验，必须在 production topology 中建立新的 bounded/long-lived test，不能
把本文当作隐藏 executable test 或当前 readiness proof。

## 3. Surface admission boundary

Fixture 只准入：

- minimal annotation/processor source；
- generated Java 8 source 与 `javap` inspection；
- independent Maven consumer；
- compile-positive/negative/counterexample probes；
- bounded cursor/View/typed-array/owner runtime harness；
- primitive bytecode kernel；
- fresh-full/output-replacement/Maven/raw-javac/late-round build probes。

明确没有实现：

- production storage arrays/Index；
- Table admission/atomic publish；
- complete structured-failure runtime；
- production parallel scheduler；
- performance/reference scenario；
- production artifact/build/release workflow。

## 4. Findings

| ID | 正式结论 | Evidence boundary | 晋升 Owner |
|---|---|---|---|
| P2-R1 | `TYPE_SHAPE_FEASIBLE` | `.schema` declaration 可在父 package 生成同名 Value/Table object、`Soma/SomaGroup` 与 typed API；declaration 不泄漏 public signature | [Schema 与编译生成](../design/schema-and-generation.md) |
| P2-R2 | `RUNTIME_MECHANISM_FEASIBLE_WITH_EXPLICIT_PRECONDITION` | sequential O(1)/parallel O(P) reusable Record/Editor、scope token、Editor staging/fetch 与 failure zero publish mechanism | [逻辑 API](../design/logical-api.md)、[执行 Design](../design/execution-and-concurrency.md) |
| P2-R3 | `TYPE_AND_RUNTIME_MECHANISM_FEASIBLE` | complete flattened Value Field 使用 callback View W/detached V；无 fetch callback 零 materialization | [逻辑 API](../design/logical-api.md) |
| P2-R4 | `TYPE_AND_RUNTIME_MECHANISM_FEASIBLE` | mapped reference 只用 `toArray(Class<A>)`；exact/parent/Object/empty/null/incompatible/order probes | [逻辑 API](../design/logical-api.md) |
| P2-R5 | `SUPPORT_CONTRACT_FEASIBLE_AND_CLOSED` | full-source/output replacement/missing-handshake/late-round；Maven stale/raw partial blind spot；Gradle descriptor 仅 registration | [Schema 与编译生成](../design/schema-and-generation.md) |
| P2-R6 | `TYPE_AND_BYTECODE_FEASIBLE` | 八种 primitive Field/array/lambda；`xaload/xastore` 与无 wrapper class reference | [数据与存储](../design/data-model-and-storage.md)、[逻辑 API](../design/logical-api.md) |
| P2-R7 | `RUNTIME_GUARD_FEASIBLE` | cross-Group expression 可编译；generated endpoint 持有 owner，foreign selection 以 `INVALID_ARGUMENT` 失败 | [逻辑 API](../design/logical-api.md) |
| P2-R8 | `TYPE_SHAPE_FEASIBLE` | read-only Key Field、Field 双入口、IndexSelection 仅 execution-mode entry，negative capability 缺席 | [逻辑 API](../design/logical-api.md) |

## 5. Generated object/type-shape evidence

验证覆盖：

- package-private `.schema` declaration；
- generated immutable Value：private final Field、全参 constructor、accessor、
  structural equality、无默认 constructor；
- generated mutable detached Table object：private Field、无参/全参 constructor、
  同名 getter/`void` setter、无 public mutable Field；
- generated `Soma`/`SomaGroup`/typed Table/Field/nested Record/Editor/Stream；
- package-private、identity-token-gated Group/Table construction；
- default Group identity 与 explicit Group isolation；
- optional Key point API、Index selection、Field-first/Record-first projection；
- Key Field read-only 和 capability narrowing；
- Java 8 lambda inference 与 independent consumer。

## 6. Negative capability evidence

十五个 expected compile failures 同时检查预期 diagnostic：

- schema declaration 不可被 application 使用；
- Value 默认 constructor 缺席；
- detached Table object public mutable Field 缺席；
- direct Group/Table construction 缺席；
- keyless point API 缺席；
- Field remove、Mapped update、Record distinct 缺席；
- Key root/nested projection update 缺席；
- IndexSelection direct terminal 缺席；
- mapped noarg/array-factory `toArray` 缺席。

两个 Java type-system counterexample 预期编译成功：Record escape expression 和
cross-Group Field selection，证明这些合同不能只依赖 generic signature，必须有
runtime scope/owner guard。

## 7. Runtime mechanism evidence

Bounded harness 证明：

- one reusable Record/Editor/Value View per sequential participant；
- fixed P objects for P parallel participants；
- callback end、foreign thread/Table/execution/participant 与 direct mapper-result
  rejection；
- `Record.fetch()`/`Editor.fetch()` detached candidate；
- callback failure zero publication in harness；
- nested Value views direct flattened leaf access；
- exact/parent/Object typed mapped arrays；
- empty/all-null/null/primitive/incompatible component cases；
- sequential/parallel array runtime type/content/order equivalence；
- cross-Group owner guard。

同 execution、同 participant 中保存 reusable cursor 的旧 alias 无法被 Java 8/runtime
完全识别；该行为被正式 Design 归入 unsupported stateful/interfering callback，而非
虚构完全 borrow checking。

## 8. Primitive bytecode evidence

boolean/byte/short/char/int/long/float/double 均有 primitive Field/Stream/array signature。
缺少 JDK specialization 的类型使用 generated primitive functional interface。
`javap -c` 检查对应 primitive descriptor、`xaload/xastore`，并确认 kernel bytecode
不引用 wrapper class。

这只证明 fixture kernel，不可外推为尚不存在的 production hot path。

## 9. Build-boundary evidence

Evidence slices：

1. Fresh full javac 对 Table add/remove/change 生成 current composition；source input
   order 不影响 generated source；
2. 整体替换 generated/classes output 后 changed schema 无 removed `Beta` stale output；
3. Late processing round 新增 Table fail closed；
4. Processor jar 声明 Gradle `aggregating` descriptor，并满足 retention/Filer/static
   prerequisites；没有 actual Gradle runtime Gate；
5. Maven Compiler Plugin 3.11.0 non-clean 重新生成 current composition/class，但旧
   `Beta` generated Java source 仍残留；
6. Missing full-source handshake 的 raw partial javac 在生成前 fail closed；
7. Host 对 partial source 伪报 handshake 仍可生成 partial composition，证明
   processor 没有 portable source-set oracle。

因此 Product Owner 采用 composition-scoped full regeneration。Build integration
拥有 complete source set、schema change detection 与 stale cleanup；processor 验证
handshake 和 visible compilation facts。P2 没有固定 production carrier；此后正式
Design 已将它固定为 `-Asoma.fullSourceSet=true` 和 build-only composition manifest，
该新合同仍必须由 production Gate 重验。

相关 platform contract 参考：

- [Oracle JDK 8 Filer](https://docs.oracle.com/javase/8/docs/api/javax/annotation/processing/Filer.html)
- [Oracle JDK 8 Processor](https://docs.oracle.com/javase/8/docs/api/javax/annotation/processing/Processor.html)
- [Gradle incremental annotation processing](https://docs.gradle.org/current/userguide/java_plugin.html#sec:incremental_annotation_processing)
- [Maven Compiler Plugin useIncrementalCompilation](https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html#useIncrementalCompilation)

## 10. Result/failure carrier witness

P2 consumer fixed minimal Java 8 shape in `io.github.somaruntime.soma.api`：

- `UpdateResult(int matched, int changed)`；
- `RemoveResult(int removed)`；
- `SomaOperationException` with code/operation/context；
- fifteen `SomaFailureCode` enum values；
- 当时的 seven `SomaOperationKind` minimal witness（后续正式 Design 为 `reserve` 增加
  `RESERVE`，P2 不证明该新增 value）；
- immutable `SomaFailureContext(String table, String fieldPath)`。

这些 shapes 已晋升到[Result 与 Structured Failure Design](../design/results-and-failures.md)。
P2 没有实现完整 trigger/mapping/precedence runtime。

## 11. Evidence limitations

P2 不证明：

- complete annotation/type/collision diagnostic；
- real storage、Index、capacity、admission、atomic publish；
- full parallel scheduler/resource/failure semantics；
- production hot path allocation/boxing beyond selected harness/kernels；
- real Maven/IDE build integration or Gradle incremental；
- performance/scalability；
- package/release readiness。

因此所有 Design surface 在 [Conformance 总览](README.md)中仍为
`NOT_IMPLEMENTED`。P2 只降低 Java 8 可表达性和 selected mechanism 风险；由于 fixture
已退役，它不能单独让任何 implementation-readiness 或 production Gate PASS。

## 12. Temporary retirement

P2 成立的长期事实当时进入五份 semantic Design Owner；后续 exact Signature 与
Implementation Architecture 继续收口这些合同，但没有扩大 P2 evidence。证据范围、
环境、结果、限制和 snapshot fingerprint 由本文拥有。原 fixture 没有成为 production
module、永久 test suite 或第二套 API；所有入口迁移并验证后，其 Temporary source 和
ignored build output 已按项目文档框架整体删除。
