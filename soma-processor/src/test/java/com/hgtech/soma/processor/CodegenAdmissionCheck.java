package com.hgtech.soma.processor;

import java.util.Arrays;

/** 对 generated source UTF-16 admission 的 exact boundary 做 package-local 验证。 */
public final class CodegenAdmissionCheck {
    private static final int LIMIT = 1_048_576;

    private CodegenAdmissionCheck() {
    }

    public static void main(String[] args) {
        String exact = repeated('x', LIMIT);
        SourceBuilder accepted = new SourceBuilder(exact);
        require(accepted.toString().length() == LIMIT,
                "exact generated source limit must be accepted");

        assertRejected(exact + "x", LIMIT + 1);
        try {
            accepted.append('x');
            throw new AssertionError("limit+1 append unexpectedly accepted");
        } catch (SourceLimitExceeded expected) {
            require(expected.proposed == LIMIT + 1,
                    "char append must report the exact proposed length");
        }

        SourceBuilder objectAppend = new SourceBuilder(repeated('y', LIMIT - 1));
        try {
            objectAppend.append("zz");
            throw new AssertionError("multi-character limit+1 append unexpectedly accepted");
        } catch (SourceLimitExceeded expected) {
            require(expected.proposed == LIMIT + 1,
                    "object append must report the exact proposed length");
        }
        System.out.println("codegen-source-admission: ok");
    }

    private static void assertRejected(String source, int proposed) {
        try {
            new SourceBuilder(source);
            throw new AssertionError("limit+1 source unexpectedly accepted");
        } catch (SourceLimitExceeded expected) {
            require(expected.proposed == proposed,
                    "constructor must report the exact proposed length");
        }
    }

    private static String repeated(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
