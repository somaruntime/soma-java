package com.example.soma.external;

import java.lang.reflect.Modifier;

public final class ExternalConsumer {
    private ExternalConsumer() {
    }

    public static void main(String[] args) throws Exception {
        ExternalId first = ExternalId.of(41L);
        ExternalId same = new ExternalId(41L);
        ExternalId other = new ExternalId(42L);

        require(first.equals(same), "canonical equality");
        require(first.hashCode() == same.hashCode(), "canonical hashCode");
        require(!first.equals(other), "field-sensitive equality");
        require("ExternalId{value=41}".equals(first.toString()), "canonical toString");
        require(Modifier.isFinal(ExternalId.class.getModifiers()), "effective final class");
        require(Modifier.isFinal(ExternalId.class.getField("value").getModifiers()),
                "effective final field");

        requireAbsent("io.github.somaruntime.soma.processor.SomaProcessor");
    }

    private static void requireAbsent(String typeName) {
        try {
            Class.forName(typeName);
            throw new AssertionError("build-only type leaked onto runtime classpath: " + typeName);
        } catch (ClassNotFoundException expected) {
            // Expected: runtime execution only receives consumer classes.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
