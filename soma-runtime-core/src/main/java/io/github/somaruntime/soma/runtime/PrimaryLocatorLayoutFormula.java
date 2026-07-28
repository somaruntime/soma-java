package io.github.somaruntime.soma.runtime;

import io.github.somaruntime.soma.runtime.metadata.SomaPrimaryLocator;
import io.github.somaruntime.soma.runtime.metadata.SomaPrimaryLocatorLayout;

/** V1 主定位器物理布局的版本化选择公式。 */
final class PrimaryLocatorLayoutFormula {
    static final String IDENTITY = "soma-primary-locator-layout-v1";

    private PrimaryLocatorLayoutFormula() {
    }

    static SomaPrimaryLocatorLayout resolve(
            String identity, SomaPrimaryLocator locator) {
        String required = CanonicalSupport.required(
                identity, "primaryLocatorLayoutFormula");
        if (!IDENTITY.equals(required)) {
            throw new IllegalArgumentException(
                    "unsupported primary locator layout formula");
        }
        if (locator == null) throw new NullPointerException("primaryLocator");
        return locator == SomaPrimaryLocator.NONE
                ? SomaPrimaryLocatorLayout.NONE
                : SomaPrimaryLocatorLayout.FLAT_COMPACT;
    }
}
