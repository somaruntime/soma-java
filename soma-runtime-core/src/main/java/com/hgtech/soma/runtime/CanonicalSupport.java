package com.hgtech.soma.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class CanonicalSupport {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final char[] UPPER_HEX = "0123456789ABCDEF".toCharArray();

    private CanonicalSupport() {
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

    static String quote(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': result.append("\\\""); break;
                case '\\': result.append("\\\\"); break;
                case '\b': result.append("\\b"); break;
                case '\f': result.append("\\f"); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default:
                    if (Character.isSurrogate(c)) {
                        result.append("\\u")
                                .append(UPPER_HEX[(c >>> 12) & 0xf])
                                .append(UPPER_HEX[(c >>> 8) & 0xf])
                                .append(UPPER_HEX[(c >>> 4) & 0xf])
                                .append(UPPER_HEX[c & 0xf]);
                    } else if (c < 0x20) {
                        result.append("\\u")
                                .append(HEX[(c >>> 12) & 0xf])
                                .append(HEX[(c >>> 8) & 0xf])
                                .append(HEX[(c >>> 4) & 0xf])
                                .append(HEX[c & 0xf]);
                    } else {
                        result.append(c);
                    }
            }
        }
        return result.append('"').toString();
    }

    static String sha256(String prefix, String canonical) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    (prefix + canonical).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                int unsigned = value & 0xff;
                result.append(HEX[unsigned >>> 4]).append(HEX[unsigned & 0x0f]);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by Java 8", impossible);
        }
    }
}
