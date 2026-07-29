# 编译器与代码生成地图

类型：Implementation Map

状态：正式

Owner：SOMA compiler/codegen 实现导航

对应 Design：[Schema 与生成 API](../design/schema-and-generated-api.md)

事实范围：当前 javac integration、processor、normalization、hash、generation 与 fixture 入口

最近实现核对基线：包含本文件的 V1 `1.0.0` private-source sign-off commit；
精确commit由Git与同SHA qualification artifact记录

最后审查日期：2026-07-29

## 1. 主流程

| 阶段 | 当前入口 | 作用 |
|---|---|---|
| javac lowering | [`SomaJavacPlugin.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/javac8/SomaJavacPlugin.java) | `@SomaValue` compiler integration |
| compiler handshake | [`CompilerProtocol.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/internal/CompilerProtocol.java) | plugin/processor identity 协同 |
| JSR 269 | [`SomaProcessor.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/SomaProcessor.java) | discovery、validation、admission、artifact plan 与 output |
| normalized schema model | [`SomaSchemaModel.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/SomaSchemaModel.java)、[`SomaSchemaJson.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/SomaSchemaJson.java) | normalized schema/value/table model 与 canonical schema JSON |
| deterministic order | [`UnicodeCodePointOrder.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/UnicodeCodePointOrder.java) | schema/codegen stable ordering |
| dense codegen model | [`DenseTableCodegenModel.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/DenseTableCodegenModel.java) | Table/Field/Child/Selector emission model |
| selector codegen model | [`DenseSelectorCodegenModel.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/DenseSelectorCodegenModel.java) | selector 参数分组与 canonical public parameter type sequence |
| artifact orchestration | [`DenseTableSourceGenerator.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/DenseTableSourceGenerator.java) | deterministic generated artifact 清单与发布顺序 |
| Table emitter | [`DenseTableSourceEmitter.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/DenseTableSourceEmitter.java) | packed Table implementation |
| auxiliary emitter | [`DenseAuxiliarySourceEmitter.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/DenseAuxiliarySourceEmitter.java) | Cursor/UpdateCursor/Batch/Mutator/KeyTraversal/Scan facade |
| DataFlow emitter | [`DenseDataFlowSourceEmitter.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/DenseDataFlowSourceEmitter.java) | 每 Table 一个 typed Source/Binding/logical Expression companion；primitive indexed equality bridge |
| Metadata emitter | [`DenseMetadataSourceEmitter.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/DenseMetadataSourceEmitter.java) | 每 Schema 一个 immutable `SchemaMetadata` companion、完整 Descriptor projection 与 default Plan 入口 |
| exact-index emitter | [`DenseExactIndexSourceEmitter.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/DenseExactIndexSourceEmitter.java) | exact-index runtime source片段；保持byte-stable output |
| selector source support | [`DenseSelectorSourceSupport.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/DenseSelectorSourceSupport.java) | emitter 共享的 source arguments、comparison、change 与 unique support |
| Scan execution support | [`DenseScanExecutionSourceSupport.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/DenseScanExecutionSourceSupport.java) | 写入 Table artifact 的 Candidate Scan terminal executor source |
| admission | [`CodegenLimits.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/CodegenLimits.java) | 生成规模上限 |
| output boundary | [`GeneratedSourceOutput.java`](../../soma-processor/src/main/java/io/github/somaruntime/soma/processor/GeneratedSourceOutput.java) | generated source publish |

Processor admission 读取 selector codegen model，而不依赖 source emitter support；Table/Exact emitters 组合 selector model 与 source support，Table emitter 通过 Scan execution support 写入 terminal executor，不再借用 Auxiliary artifact emitter。`DenseTableSourceEmitter -> DenseExactIndexSourceEmitter` 仍是有意的 artifact-internal composition；exact-index emitter 不反向依赖 orchestrator。

Candidate Scan 继续生成 typed source plan、small-inline/overflow stage storage 与 terminal executor；public handle不暴露 runtime IR。DataFlow emitter 只投影 schema-specific capability，不复制 analyzer/kernel，也不按 operator 展开 artifact。当前依赖方向由 codegen admission source-shape check 约束，生成契约继续由 clean/repeat source、schema/hash、`javap` golden、external consumer 和 code-size Gate 约束。

`SomaSchemaModel.SchemaStorageKind` 现在显式封闭 primitive-backed、
reference-backed immutable String、compiler-flattened value 与 owned structured
state；任意应用对象在 compile boundary 被拒绝并指向 stable ID + sidecar。
String Key/Unique/Index 由 concrete `StringColumn` 和 typed String DataFlow
protocol投影，不再生成 generic Object value family。Descriptor 的 immutable
实现私有嵌入 schema-scoped companion，application 只能读取、不能自行构造
processor-owned descriptor。

DataFlow companion 对 enum、date、time、instant 分别投影
`EnumExpression`、`DateExpression`、`TimeExpression`、`InstantExpression`，
不再泄漏 raw `LongExpression` 算术；对应 Key/Join overload 保持 logical type。
Required primitive single-field Index 可生成 `CandidateLongEqualityAccess`
bridge，供 DataFlow 在公式许可时读取 maintained bitmap word。TIME 的 Batch、
replace、Mutator、Delta 和 flattened-value 写入统一调用 nano-of-day range
validation，不能从某条生成路径绕过。

`SchemaMetadata.newPlan()` 当前通过 generated-only `GeneratedRuntimePlan` bridge
播种 runtime plan v6。每个 Table 默认使用至少16的有效initial capacity、
non-binding planning rows、按schema structural bytes估算的保守hard maximum rows、
closed locator/exact-access identity，以及由generated structural row width、
application workload与planning rows解析的storage-layout formula和只产生
`NONE`/`FLAT_COMPACT`的primary-locator layout formula；含String
column的Table声明String capability并默认 `UNPROFILED`。Application只能通过metadata-scoped table editor
覆盖已开放的cold control-plane参数，不能构造raw descriptor或写入free-form
physical strategy。

Table artifact 现在同时生成 `attach(SomaGroup, memberName)` 与 implicit-Group
`create(plan)` 路径。前者经 `GeneratedRootFactory`/`GeneratedSomaGroup`完成
private construction、ledger bind和publish-once；后者复用同一协议建立单槽Group。
生成 public Table 不泄漏 `GroupLedger`、`TableLedger` 或 `GroupMembership`，
这些类型只属于generator binding protocol。

每个 generated Table 同时公开 detached `runtimeMetadata()`，把 Descriptor、
Effective Plan、当前 Table/Segment topology、primary locator、Unique/Index、
structural current/high-water、rows/epoch/lifecycle投影为完整 Metadata read model。
生成器在 binding 时冻结 owned-child maximum rows，Expand 的资源 admission 不在
执行期重入 public `runtimePlan()`。Metadata 只在operation boundary投影，逐行
access/scan/mutation不解释Metadata。

Table artifact 内部生成一组私有 failure-routing helper：structured `INTERNAL`
进入 aggregate fault，expected structured failure正常关闭 operation，raw
unexpected failure fail closed。Table、Auxiliary、Scan、Selector 和 Exact emitter
都投影到这一处规则；helper 只调用 current runtime v12 protocol，不进入 generated
public signature。

Selector-less Table 的私有 `ExactIndexStage` 显式声明无参构造器，避免JDK 8 javac
javac 8 在相邻 clean compile 间为私有内部类选择不同 synthetic access marker；
codegen admission 同时校验生成源码形状和完整编译。该形状不进入 public/generated
contract。

## 2. Schema 输入

Annotation 实现位于 [`soma-annotations/src/main/java/io/github/somaruntime/soma/annotation`](../../soma-annotations/src/main/java/io/github/somaruntime/soma/annotation)。Processor 从 package/schema 与 annotated value/table types 建立 normalized model，并输出 schema JSON/hash 与 Java source。

Generated source 在 consumer/module 的 `target/generated-sources/annotations` 中产生，不提交为手写事实源。

## 3. 验证入口

- codegen admission unit check：[`CodegenAdmissionCheck.java`](../../soma-processor/src/test/java/io/github/somaruntime/soma/processor/CodegenAdmissionCheck.java)；
- compiler/schema fixtures：[`tests/fixtures/compiler`](../../tests/fixtures/compiler)；
- public/generated golden、DataFlow companion/Delta surface 与 external Maven consumers：[`tests/fixtures`](../../tests/fixtures)；
- compiler Gate scripts：[`check-compiler-contracts.sh`](../../scripts/check-compiler-contracts.sh)、[`check-codegen-admission.sh`](../../scripts/check-codegen-admission.sh)、[`check-value-shape-contract.sh`](../../scripts/check-value-shape-contract.sh)、[`check-default-value-contract.sh`](../../scripts/check-default-value-contract.sh)。

修改 annotation、normalization/hash、generated public method 或 protocol binding 时，必须沿 source → generated artifact → external consumer 追踪，而不能只运行 processor 单元测试。
