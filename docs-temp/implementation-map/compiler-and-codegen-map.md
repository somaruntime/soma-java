# 编译器与代码生成地图

类型：Implementation Map

状态：候选

Owner：SOMA compiler/codegen 实现导航

对应 Design：[Schema 与生成 API](../design/schema-and-generated-api.md)

事实范围：当前 javac integration、processor、normalization、hash、generation 与 fixture 入口

最近核对基线：`4b6fa43`

最后审查日期：2026-07-19

## 1. 主流程

| 阶段 | 当前入口 | 作用 |
|---|---|---|
| javac lowering | [`SomaJavacPlugin.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/javac8/SomaJavacPlugin.java) | `@SomaValue` compiler integration |
| compiler handshake | [`CompilerProtocol.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/internal/CompilerProtocol.java) | plugin/processor identity 协同 |
| JSR 269 | [`SomaProcessor.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/SomaProcessor.java) | discovery、validation、normalization、schema artifact |
| deterministic order | [`UnicodeCodePointOrder.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/UnicodeCodePointOrder.java) | schema/codegen stable ordering |
| source generation | [`DenseTableSourceGenerator.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/DenseTableSourceGenerator.java) | dense/keyed facade 和 runtime binding 生成 |
| admission | [`CodegenLimits.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/CodegenLimits.java) | 生成规模上限 |
| output boundary | [`GeneratedSourceOutput.java`](../../soma-processor/src/main/java/com/hgtech/soma/processor/GeneratedSourceOutput.java) | generated source publish |

`DenseTableSourceGenerator` 当前同时覆盖大量 public surface 与 runtime mechanics，是 generator 的主要修改热点；其规模属于实现维护风险，不自动构成 Design 偏差。

## 2. Schema 输入

Annotation 实现位于 [`soma-annotations/src/main/java/com/hgtech/soma/annotation`](../../soma-annotations/src/main/java/com/hgtech/soma/annotation)。Processor 从 package/schema 与 annotated value/table types 建立 normalized model，并输出 schema JSON/hash 与 Java source。

Generated source 在 consumer/module 的 `target/generated-sources/annotations` 中产生，不提交为手写事实源。

## 3. 验证入口

- codegen admission unit check：[`CodegenAdmissionCheck.java`](../../soma-processor/src/test/java/com/hgtech/soma/processor/CodegenAdmissionCheck.java)；
- compiler/schema fixtures：[`soma-testkit/src/test/fixtures/compiler`](../../soma-testkit/src/test/fixtures/compiler)；
- public/generated golden 与 external Maven consumers：[`soma-testkit/src/test/fixtures`](../../soma-testkit/src/test/fixtures)；
- compiler Gate scripts：[`check-compiler-phase0.sh`](../../scripts/check-compiler-phase0.sh)、[`check-codegen-admission.sh`](../../scripts/check-codegen-admission.sh)、[`check-value-modifiers-phase5.sh`](../../scripts/check-value-modifiers-phase5.sh)、[`check-defaults-phase5.sh`](../../scripts/check-defaults-phase5.sh)。

修改 annotation、normalization/hash、generated public method 或 protocol binding 时，必须沿 source → generated artifact → external consumer 追踪，而不能只运行 processor 单元测试。
