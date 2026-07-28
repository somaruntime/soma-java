package io.github.somaruntime.soma.processor;

/**
 * V1 code generation 的确定性 admission 边界。
 *
 * <p>所有值由正式 code-generation contract 拥有；本类只集中承载 processor 与
 * bounded source writer 共同消费的内部常量。</p>
 */
final class CodegenLimits {
    static final int MAXIMUM_VALUE_DEPTH = 32;
    static final int MAXIMUM_TABLE_PHYSICAL_LEAVES = 256;
    static final int MAXIMUM_SCHEMA_TABLES = 256;
    static final int MAXIMUM_GENERATED_SOURCE_LENGTH = 1_048_576;
    static final int MAXIMUM_SCHEMA_GENERATED_SOURCE_LENGTH = 67_108_864;
    static final int MAXIMUM_JVM_PARAMETER_SLOTS = 255;

    private CodegenLimits() {
    }
}
