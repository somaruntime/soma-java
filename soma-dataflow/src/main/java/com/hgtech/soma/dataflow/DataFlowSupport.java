package com.hgtech.soma.dataflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

final class DataFlowSupport {
    private DataFlowSupport() {
    }

    static String required(String value, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }

    static String identity(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : hash) {
                result.append(Character.forDigit((value >>> 4) & 0xf, 16));
                result.append(Character.forDigit(value & 0xf, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 is required by Java 8", impossible);
        }
    }

    static List<ParameterSlot<?>> unionParameters(
            List<ParameterSlot<?>> first,
            List<ParameterSlot<?>> second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        ArrayList<ParameterSlot<?>> result =
                new ArrayList<ParameterSlot<?>>(first.size() + second.size());
        IdentityHashMap<ParameterSlot<?>, Boolean> seen =
                new IdentityHashMap<ParameterSlot<?>, Boolean>();
        appendParameters(result, seen, first);
        appendParameters(result, seen, second);
        return Collections.unmodifiableList(result);
    }

    private static void appendParameters(
            List<ParameterSlot<?>> target,
            IdentityHashMap<ParameterSlot<?>, Boolean> seen,
            List<ParameterSlot<?>> source) {
        for (ParameterSlot<?> slot : source) {
            if (seen.put(slot, Boolean.TRUE) == null) {
                target.add(slot);
            }
        }
    }
}
