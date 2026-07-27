package com.hgtech.soma.runtime.generated;

/**
 * Capability key for generated-only raw Plan construction。
 *
 * <p>普通 application 可以看到类型，但不能取得 token instance。</p>
 */
public final class GeneratedPlanToken {
    static final GeneratedPlanToken INSTANCE = new GeneratedPlanToken();

    private GeneratedPlanToken() {
    }

    public static void require(GeneratedPlanToken value) {
        if (value != INSTANCE) {
            throw new IllegalArgumentException(
                    "generated Plan construction token mismatch");
        }
    }
}
