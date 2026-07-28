package io.github.somaruntime.soma.processor;

import javax.lang.model.element.Element;

/** package-private codegen output model；不进入 processor 或 consumer API。 */
final class GeneratedSourceOutput {
    final String qualifiedName;
    final String source;
    final Element origin;

    GeneratedSourceOutput(String qualifiedName, String source, Element origin) {
        this.qualifiedName = qualifiedName;
        this.source = source;
        this.origin = origin;
    }
}

/** 单个 generated source 超出确定性 UTF-16 admission 时的内部控制流。 */
final class SourceLimitExceeded extends RuntimeException {
    final int proposed;

    SourceLimitExceeded(int proposed) {
        super("generated source exceeds deterministic UTF-16 admission");
        this.proposed = proposed;
    }
}

/**
 * 在 append 前执行 exact length admission 的 source writer。
 *
 * <p>它只负责有界字符输出，不解释 schema、annotation 或 runtime metadata。</p>
 */
final class SourceBuilder {
    private final StringBuilder delegate;

    SourceBuilder(String initial) {
        int limit = CodegenLimits.MAXIMUM_GENERATED_SOURCE_LENGTH;
        delegate = new StringBuilder(Math.min(limit, initial.length() + 4096));
        append(initial);
    }

    SourceBuilder append(char value) {
        int limit = CodegenLimits.MAXIMUM_GENERATED_SOURCE_LENGTH;
        if (delegate.length() == limit) {
            throw new SourceLimitExceeded(limit + 1);
        }
        delegate.append(value);
        return this;
    }

    SourceBuilder append(Object value) {
        String text = String.valueOf(value);
        long proposed = (long) delegate.length() + text.length();
        if (proposed > CodegenLimits.MAXIMUM_GENERATED_SOURCE_LENGTH) {
            throw new SourceLimitExceeded(
                    proposed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) proposed);
        }
        delegate.append(text);
        return this;
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
