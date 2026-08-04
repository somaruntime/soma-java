package io.github.somaruntime.soma.processor;

/** Exact Java 8 name transformations shared by validation and source rendering. */
final class GeneratedNames {

    private GeneratedNames() {
    }

    static String tableType(String schemaSimpleName) {
        return schemaSimpleName + "Table";
    }

    static String tableAccessor(String schemaSimpleName) {
        return transformFirst(schemaSimpleName, false) + "Table";
    }

    static String fieldEndpointType(String fieldName) {
        return transformFirst(fieldName, true) + "Field";
    }

    private static String transformFirst(String value, boolean upper) {
        int codePoint = value.codePointAt(0);
        int transformed = upper
                ? Character.toUpperCase(codePoint)
                : Character.toLowerCase(codePoint);
        int firstLength = Character.charCount(codePoint);
        return new StringBuilder(value.length())
                .appendCodePoint(transformed)
                .append(value.substring(firstLength))
                .toString();
    }
}
