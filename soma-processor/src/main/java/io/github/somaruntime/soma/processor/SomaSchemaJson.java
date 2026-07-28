package io.github.somaruntime.soma.processor;

/** Canonical schema JSON constants and escaping support. */
final class SomaSchemaJson {
    static final char[] LOWER_HEX = "0123456789abcdef".toCharArray();
    static final char[] UPPER_HEX = "0123456789ABCDEF".toCharArray();
    static final String GENERATED_TARGET = "java8-columnar";

    private SomaSchemaJson() {
    }

    static String quote(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2);
        result.append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"':
                    result.append("\\\"");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                case '\b':
                    result.append("\\b");
                    break;
                case '\f':
                    result.append("\\f");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    if (Character.isSurrogate(character)) {
                        result.append("\\u");
                        result.append(UPPER_HEX[(character >>> 12) & 0x0f]);
                        result.append(UPPER_HEX[(character >>> 8) & 0x0f]);
                        result.append(UPPER_HEX[(character >>> 4) & 0x0f]);
                        result.append(UPPER_HEX[character & 0x0f]);
                    } else if (character < 0x20) {
                        result.append("\\u");
                        result.append(LOWER_HEX[(character >>> 12) & 0x0f]);
                        result.append(LOWER_HEX[(character >>> 8) & 0x0f]);
                        result.append(LOWER_HEX[(character >>> 4) & 0x0f]);
                        result.append(LOWER_HEX[character & 0x0f]);
                    } else {
                        result.append(character);
                    }
            }
        }
        return result.append('"').toString();
    }

}
