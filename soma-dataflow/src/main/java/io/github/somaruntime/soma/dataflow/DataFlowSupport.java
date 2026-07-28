package io.github.somaruntime.soma.dataflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

final class DataFlowSupport {
    private static final AtomicLong OPAQUE_ID = new AtomicLong(1L);

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

    static String registeredIdentity(
            String kind, String semanticId, String version) {
        String id = required(semanticId, "semanticId");
        String revision = required(version, "version");
        StringBuilder canonical = new StringBuilder(kind);
        appendCanonical(canonical, "semanticId", id);
        appendCanonical(canonical, "version", revision);
        return canonical.toString();
    }

    static void appendCanonical(
            StringBuilder target, String name, String value) {
        if (target == null) {
            throw new NullPointerException("target");
        }
        required(name, "name");
        if (value == null) {
            throw new NullPointerException("value");
        }
        target.append('|')
                .append(name.length()).append(':').append(name)
                .append('=')
                .append(value.length()).append(':').append(value);
    }

    static long nextOpaqueIdentity() {
        long value = OPAQUE_ID.getAndIncrement();
        if (value <= 0L) {
            throw new IllegalStateException(
                    "dataflow opaque definition identity exhausted");
        }
        return value;
    }

    static String constantIdentity(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Enum<?>) {
            Enum<?> enumeration = (Enum<?>) value;
            StringBuilder canonical = new StringBuilder("enum");
            appendCanonical(
                    canonical,
                    "type",
                    enumeration.getDeclaringClass().getName());
            appendCanonical(canonical, "name", enumeration.name());
            return canonical.toString();
        }
        if (value instanceof String
                || value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof Character) {
            StringBuilder canonical = new StringBuilder("scalar");
            appendCanonical(
                    canonical, "type", value.getClass().getName());
            appendCanonical(
                    canonical, "value", String.valueOf(value));
            return canonical.toString();
        }
        StringBuilder canonical =
                new StringBuilder("opaque-constant-instance");
        appendCanonical(
                canonical,
                "identity",
                Long.toString(nextOpaqueIdentity()));
        appendCanonical(
                canonical, "type", value.getClass().getName());
        return canonical.toString();
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
