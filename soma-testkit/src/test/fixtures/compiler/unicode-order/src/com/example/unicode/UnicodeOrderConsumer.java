package com.example.unicode;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class UnicodeOrderConsumer {
    private UnicodeOrderConsumer() {
    }

    public static void main(String[] args) throws Exception {
        String resource = "/META-INF/soma/com.example.unicode.schema.json";
        InputStream input = UnicodeOrderConsumer.class.getResourceAsStream(resource);
        if (input == null) throw new AssertionError("missing canonical schema");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        for (int read; (read = input.read(buffer)) >= 0; ) bytes.write(buffer, 0, read);
        String schema = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        String bmp = "\"logicalName\":\"\uF900Value\"";
        String supplementary = "\"logicalName\":\"" + '\\' + "uD801" + '\\'
                + "uDC00Value\"";
        int bmpIndex = schema.indexOf(bmp);
        int supplementaryIndex = schema.indexOf(supplementary);
        require(bmpIndex >= 0 && supplementaryIndex > bmpIndex,
                "canonical declarations are not ordered by Unicode code point");
        require(schema.contains("\"literal\":\"" + '\\' + "uD800\""),
                "isolated high surrogate was not preserved");
        require(schema.contains("\"literal\":\"" + '\\' + "uD801\""),
                "distinct isolated surrogate was not preserved");
        System.out.println("unicode-order-consumer: ok");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
