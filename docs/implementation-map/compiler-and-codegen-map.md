# 编译器与代码生成地图

类型：Implementation Map

状态：正式

Owner：SOMA compiler/codegen 实现导航

对应 Design：[Schema 与生成 API](../design/schema-and-generated-api.md)

事实范围：当前 javac integration、processor、normalization、hash、generation 与 fixture 入口

最近实现核对基线：`6b6dc49`

最后审查日期：2026-07-23

## 1. 主流程

| 阶段 | 当前入口 | 作用 |
|---|---|---|
| javac lowering | [`SomaJavacPlugin.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/javac8/SomaJavacPlugin.java) | `@SomaValue` compiler integration |
| compiler handshake | [`CompilerProtocol.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/internal/CompilerProtocol.java) | plugin/processor identity 协同 |
| JSR 269 | [`SomaProcessor.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/SomaProcessor.java) | discovery、validation、admission、artifact plan 与 output |
| normalized schema model | [`SomaSchemaModel.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/SomaSchemaModel.java)、[`SomaSchemaJson.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/SomaSchemaJson.java) | normalized schema/value/table model 与 canonical schema JSON |
| deterministic order | [`UnicodeCodePointOrder.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/UnicodeCodePointOrder.java) | schema/codegen stable ordering |
| dense codegen model | [`DenseTableCodegenModel.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/DenseTableCodegenModel.java) | Table/Field/Child/Selector emission model |
| artifact orchestration | [`DenseTableSourceGenerator.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/DenseTableSourceGenerator.java) | deterministic generated artifact 清单与发布顺序 |
| Table emitter | [`DenseTableSourceEmitter.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/DenseTableSourceEmitter.java) | packed Table implementation |
| auxiliary emitter | [`DenseAuxiliarySourceEmitter.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/DenseAuxiliarySourceEmitter.java) | Cursor/UpdateCursor/Batch/Mutator/KeyTraversal/Scan facade |
| exact-index emitter | [`DenseExactIndexSourceEmitter.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/DenseExactIndexSourceEmitter.java) | exact-index runtime source片段；保持byte-stable output |
| selector support | [`DenseSelectorSourceSupport.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/DenseSelectorSourceSupport.java) | emitter共享的selector binding、comparison、change与unique support |
| admission | [`CodegenLimits.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/CodegenLimits.java) | 生成规模上限 |
| output boundary | [`GeneratedSourceOutput.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/GeneratedSourceOutput.java) | generated source publish |

Processor、normalized model、codegen model、orchestrator 与 artifact emitter 之间只沿生成流程依赖；exact-index emitter 不反向依赖 orchestrator。Candidate Scan 生成 typed source plan、small-inline/overflow stage storage 与 terminal executor；public handle不暴露 runtime IR。当前责任关系由 codegen admission source-shape check 约束，生成契约继续由 clean/repeat source、schema/hash、`javap` golden、external consumer 和 code-size Gate 约束。

## 2. Schema 输入

Annotation 实现位于 [`soma-annotations/src/main/java/com/hgtech/soma/annotation`](../../soma-annotations/src/main/java/com/hgtech/soma/annotation)。Processor 从 package/schema 与 annotated value/table types 建立 normalized model，并输出 schema JSON/hash 与 Java source。

Generated source 在 consumer/module 的 `target/generated-sources/annotations` 中产生，不提交为手写事实源。

## 3. 验证入口

- codegen admission unit check：[`CodegenAdmissionCheck.java`](../../soma-processor/src/test/java/com/hgtech/soma/processor/CodegenAdmissionCheck.java)；
- compiler/schema fixtures：[`soma-testkit/src/test/fixtures/compiler`](../../soma-testkit/src/test/fixtures/compiler)；
- public/generated golden 与 external Maven consumers：[`soma-testkit/src/test/fixtures`](../../soma-testkit/src/test/fixtures)；
- compiler Gate scripts：[`check-compiler-phase0.sh`](../../scripts/check-compiler-phase0.sh)、[`check-codegen-admission.sh`](../../scripts/check-codegen-admission.sh)、[`check-value-modifiers-phase5.sh`](../../scripts/check-value-modifiers-phase5.sh)、[`check-defaults-phase5.sh`](../../scripts/check-defaults-phase5.sh)。

修改 annotation、normalization/hash、generated public method 或 protocol binding 时，必须沿 source → generated artifact → external consumer 追踪，而不能只运行 processor 单元测试。
