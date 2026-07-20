# Public API 与兼容性契约

类型：历史设计
状态：superseded
Owner：根项目协调层
当前取代者：[兼容性、安全与版本](design/compatibility-security-and-versioning.md)
事实范围：public/generated/internal surface 分类、兼容性维度、版本身份、deprecation 和 consumer migration
非事实范围：artifact 发布流程、具体 annotation/API 语义、schema hash 算法、runtime plan 参数和 release 结果
最后审查日期：2026-07-10

> 本文只保留切换前的历史设计上下文，不再拥有当前事实；精确当前 surface 从[可执行契约地图](implementation-map/executable-contract-map.md)进入。

## 1. 目标

本文定义 SOMA Java 中什么是 public contract、何种变化算兼容，以及 processor、generated source 和 runtime 如何证明彼此匹配。

具体 annotation、generated API、materialization 和 runtime behavior 仍由各自 owner 文档定义。本文只拥有跨模块兼容性边界。

## 2. Contract surface 分类

| Surface | 示例 | 兼容性地位 |
|---|---|---|
| handwritten public API | annotations、runtime public types、public error/stats/config types | artifact public contract |
| generated public API | generated Table/Batch/Rows/Mutator/ColumnView/materializer | processor-owned reproducible public contract |
| generated-runtime protocol | `com.hgtech.soma.runtime.generated` 中供 generated source 静态绑定的 runtime classes/methods | processor/runtime pairing contract；不是 handwritten application API 或 application SPI |
| schema declaration | annotation parameters、field roles、selectors、defaults、ownership | logical schema contract |
| materialized shape | schema row、List/Map child、optional/null shape | boundary data contract |
| compiler integration | plugin name、processor discovery、supported compiler identity | build-time consumer contract |
| runtime plan | capacity/algorithm/stats/default budget configuration | instance execution contract，不是 logical schema |
| internal implementation | packed Index、bitmap、bucket、group link、IndexBuffer、normalized-model class | 无 consumer compatibility 承诺 |
| evidence format | gate/benchmark structured artifact | evidence contract，不是 runtime API |

类型位于 artifact 中并不自动意味着 public。Public surface 必须由正式 owner contract 和发布时的 API manifest 同时确认；internal/package-private/test hook 不因可反射访问而获得兼容性承诺。

Generated-runtime protocol 必须跨任意 consumer generated package 可见，因此其 bytecode 可以是 `public`；但 application 不实现、不继承、不直接调用。Generated facade 的 public signature 不能泄漏该 protocol。Protocol 通过独立 manifest 与 runtime compatibility identity 管理，变化必须保持 processor/runtime pairing 或提升 compatibility identity，不能伪装成无承诺 internal refactor。

## 3. Version identities

SOMA 使用多个正交 identity，不能用一个版本号替代全部语义：

| Identity | 回答的问题 |
|---|---|
| artifact version | 发布的是哪组 annotations/processor/runtime artifact |
| schema version label | application 给 schema 的人类可读版本标签 |
| exact `schema_hash` | logical schema 是否逐项完全一致 |
| processor version | 哪个 processor 生成了 source/metadata |
| compiler integration identity | 哪个 lowering protocol/adapter/compiler family 生效 |
| runtime compatibility version | generated code 与 runtime protocol 是否兼容 |
| runtime plan hash | 某 table aggregate 的有效执行计划是否一致 |
| estimator version | materialization allocation estimate 使用哪套模型 |

Schema version label 不能替代 exact hash；artifact version 不能替代 runtime compatibility；runtime plan hash 不进入 logical schema hash。

当前packed exact-access runtime固化：generated protocol identity `soma-generated-runtime-v3`、runtime compatibility identity `soma-runtime-java8-v3`、runtime plan protocol `soma-runtime-plan-v3`、dense algorithm identity `dense-soa-v1`、materialization estimator `soma-materialization-estimator-v1`。这些 identity 必须进入 generated metadata、create mismatch fixture 和相应 manifest；字符串不包含组织名，也不改变 HGTECH/SOMA identity boundary。v1/v2只属于首个公开发布前的历史实现，不是current compatibility target。

## 4. Initialization compatibility check

Generated table 创建 runtime storage 前必须验证：

- generated target；
- exact schema hash；
- generated-code protocol；
- compiler/lowering compatibility identity；
- runtime compatibility version；
- runtime plan compatibility/identity；
- materialization budget protocol 与 estimator version。

不匹配必须在 create/initialization boundary fail fast，不能延迟到 hot loop。错误必须使用 [runtime error contract](../soma-runtime-core/docs/runtime-errors-and-diagnostics-contract.md) 定义的 structured code/context。

## 5. Compatibility dimensions

每项 public change 都要分别判断：

- source compatibility；
- binary compatibility；
- generated-source compatibility；
- processor/runtime protocol compatibility；
- logical schema compatibility；
- runtime behavior/default compatibility；
- materialized shape compatibility；
- evidence/operational compatibility。

“能重新编译”不等于 binary compatible；“schema hash 不变”不等于 runtime plan 相同；“方法签名没变”也不等于 default/error/order 语义没变。

## 6. Change classification

### 6.1 Breaking change

以下默认是 breaking：

- annotation parameter、field role、optional/default、ownership 或 selector semantics 改变；
- normalized schema/hash input 改变；
- generated public type/method/return shape 改变；
- `@SomaValue` lowering、constructor、equality/hash 改变；
- `@SomaTable` public carrier、public no-arg construction 或 public mutable schema-field requirement 改变；
- Materialized Object/List/Map shape 或 absent/present-empty 区分改变；
- key/index/unique equality、hash、source-sequence 或 floating canonicalization 改变；
- error code/category、lifecycle、failure atomicity 改变；
- concurrency/persistence boundary改变；
- supported compiler/build activation 被移除；
- runtime compatibility protocol 不再接受旧 generated code。

### 6.2 Behavior-preserving change

以下可以保持 public compatibility，但仍需证据：

- internal algorithm、column class、probe/growth/exact-index strategy 改变；
- performance 改善但 observable order/result/error 不变；
- diagnostics message prose 改写，stable code/context 不变；
- 新增 internal stats detail，默认成本和已有字段语义不变；
- additive implementation-only helper。

### 6.3 Additive public change

新增 public API 也必须审查 naming、overload ambiguity、generated collision、default 和 migration。V1 exact schema model 下，新增 schema field 即使 optional 也会改变 schema hash、layout、materialization 和 generated API，不能自动称为 backward compatible。

## 7. Pre-1.0 policy

`0.x` 表示 public contract 正在形成，不表示可以无记录地变化：

- patch release 必须保持 public source/behavior compatibility，只修复缺陷；
- minor release 可以包含明确记录的 breaking change；
- breaking minor 必须提供 release note、受影响 surface、regeneration/migration 步骤和 rollback；
- 同一 released version 的 artifact 不得被覆盖；
- internal implementation 可以快速演进，但不能静默改变 owner contract。

达到 `1.0.0` 后遵守 SemVer major/minor/patch，并执行正式 deprecation window。

## 8. Generated code policy

- generated source 不是手工维护入口；
- consumer 必须使用与 release compatibility matrix 匹配的 processor/runtime；
- processor upgrade 后需要重新生成并重新编译；
- generated source diff 是 review/evidence artifact，不能只靠 runtime smoke；
- generated code 不承诺跨任意 processor major/minor binary reuse；
- release matrix 必须列出允许的 processor/runtime pairing；
- checked-in generated fixtures 必须标记 generator identity，禁止手改后继续当作 golden。

## 9. Public/internal boundary

实现期必须建立 API manifest/check：

- 只列出正式 owner contract 授权的 public/protected surface；
- internal packages、test hooks 和 compiler internals 不进入 manifest；
- third-party type 不进入 public/generated signature；
- group link、live IndexBuffer、bitmap、bucket、allocator、compiler AST type 不得泄漏；
- reflection 可见性不构成 public contract；
- package split 前先证明独立版本/消费者边界。

具体 package/class 名由第一个 vertical slice 固化，不在没有实现时猜测。

首个 compiler/build vertical slice 固化的 manifest classification：

| Classification | Types/surface |
|---|---|
| handwritten public API | `com.hgtech.soma.annotation.SomaSchema`、`SomaValue`、`SomaField`、`SomaIgnore`、`SomaSemantic` |
| build-time provider contract | `com.hgtech.soma.processor.SomaProcessor`、`com.hgtech.soma.processor.javac8.SomaJavacPlugin`，以及正式 compiler contract 中的 plugin/processor identity；provider class 的 public 可见性服务 javac/ServiceLoader，不表示 application 应手工调用其 lifecycle method |
| internal implementation | `com.hgtech.soma.processor.internal.CompilerProtocol` / `CompilerProtocol.Session`、javac AST/lowering helper、processor normalized model；即使跨 package 技术约束要求某个 type/member 在 bytecode 中为 `public`，也不进入 consumer compatibility manifest |

当前 manifest/golden 位于 `soma-testkit/src/test/fixtures/public-api/phase1`，由 `scripts/check-public-api.sh` 从实际 JAR逐项重建并比较。新增或改变public/protected surface必须先更新唯一Owner，再显式审查manifest diff；不能由javap可见性自动升级为public contract。

首个 dense runtime slice 增加三份相互独立的 manifest：

- handwritten runtime API：`com.hgtech.soma.runtime` 中正式 plan/budget/error/stats/result types；
- generated-runtime protocol：`com.hgtech.soma.runtime.generated`，只允许 committed protocol list；
- schema-specific generated public API：由 fixture generated source/JAR 重建，锁定 Table/Batch/Rows/Row/MutableRow/Mutator 及 nested callback shape。

`com.hgtech.soma.runtime.internal`、test hook、schema-specific package-private storage binding均排除。Manifest check 必须同时证明 generated public signature 不引用 `.runtime.generated` 或 `.runtime.internal`。

Java 8跨package generated construction使用一个明确例外：handwritten public type `com.hgtech.soma.runtime.GeneratedColumnAccess` 是generated-only construction bridge。其public static factory签名只接受`Object`/String/enum member array并返回handwritten Column Pipeline/View；方法内部对`.runtime.generated` binding做exact type validation。所有具体Column Pipeline/View constructor改为package-private，application contract仍只允许经generated `fieldValues()/fieldColumn()`获取实例。该bridge进入handwritten manifest并标记`generated construction protocol`，不是application手工构造入口；schema-specific generated public signature仍不得引用`.runtime.generated`。

Packed exact-access cutover 删除 maintained order、SparseInt 与 dirty sidecar protocol，新增 epoch-bearing `IndexSnapshot`、incremental grouped exact index、swap-remove 和新的 RuntimePlan/Stats shape。Processor/runtime 必须成对升级并重新生成；identity 统一提升为 `soma-generated-runtime-v3`、`soma-runtime-java8-v3`、`soma-runtime-plan-v3`。v1/v2 generated code 与 v3 runtime 不能静默混用。该 breaking cutover 发生在首个公开发布前，直接删除旧 surface，不保留失效 annotation、deprecated 空壳 getter或 runtime fallback；完整迁移仍在同一 Java-only V1 内完成。

## 10. Deprecation and removal

`1.x+` public removal 至少经过：

1. owner contract 标记 deprecated；
2. Java `@Deprecated` 与 replacement/migration 文档；
3. 至少一个 minor release 的 coexistence，除非 security/correctness emergency；
4. major release removal；
5. release note 和 compatibility fixture 更新。

`0.x` 可以缩短窗口，但不能省略 release note 和 migration。Security emergency 可以直接移除危险能力，但必须记录影响、替代和回滚限制。

## 11. Compatibility evidence

至少包含：

- handwritten public API diff；
- generated API/source golden diff；
- schema hash golden；
- processor/runtime match and mismatch cases；
- old consumer source rebuild；
- previous released artifact compatibility fixture when applicable；
- error/default/source-sequence/materialization behavior cases；
- release matrix 与 migration note。

Passing unit tests 不能单独证明 compatibility。

## 12. 非目标

本文不承诺 automatic schema migration、wire compatibility、persistence format、cross-language ABI、所有 `0.x` minor 互换、跨任意 JDK compiler compatibility或 internal implementation stability。
