package com.hgtech.soma.benchmarks;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Strict JSONL/schema/required-lane validator，可独立用于已有artifact。 */
public final class BenchmarkArtifactValidator {
    private static final String SCHEMA_RESOURCE =
            "/META-INF/soma/benchmark-smoke-schema-v4.json";

    private BenchmarkArtifactValidator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: BenchmarkArtifactValidator <artifact.jsonl>");
        }
        int records = validate(new File(args[0]));
        System.out.println("benchmark-artifact-schema: " + BenchmarkModel.SCHEMA_VERSION);
        System.out.println("benchmark-artifact-records: " + records);
        System.out.println("benchmark-artifact-validator: ok");
    }

    public static int validate(File artifact) throws IOException {
        validateSchemaResource();
        List<Map<String, Object>> records = BenchmarkModel.read(artifact);
        BenchmarkModel.validateArtifact(records);
        return records.size();
    }

    private static void validateSchemaResource() throws IOException {
        InputStream stream = BenchmarkArtifactValidator.class.getResourceAsStream(SCHEMA_RESOURCE);
        if (stream == null) throw new IOException("missing schema resource " + SCHEMA_RESOURCE);
        StringBuilder source = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                stream, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) source.append(line).append('\n');
        } finally {
            reader.close();
        }
        Object parsed = BenchmarkModel.Json.parse(source.toString());
        if (!(parsed instanceof Map)) throw new IllegalArgumentException("schema root must be object");
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) parsed;
        require(BenchmarkModel.SCHEMA_VERSION.equals(schema.get("$id")), "schema $id");
        require("object".equals(schema.get("type")), "schema root type");
        require(Boolean.FALSE.equals(schema.get("additionalProperties")),
                "schema additionalProperties");
        Object propertiesValue = schema.get("properties");
        Object requiredValue = schema.get("required");
        require(propertiesValue instanceof Map, "schema properties");
        require(requiredValue instanceof List, "schema required");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) propertiesValue;
        @SuppressWarnings("unchecked")
        List<Object> required = (List<Object>) requiredValue;
        require(new ArrayList<String>(properties.keySet()).equals(BenchmarkModel.FIELDS),
                "schema exact properties/order");
        require(required.equals(new ArrayList<Object>(BenchmarkModel.FIELDS)),
                "schema exact required/order");
        require("SmokeLaneSuite.validateLaneRecord".equals(schema.get("x-soma-laneBinding")),
                "schema lane binding");
        String[] nestedContracts = {"workloadEvidence", "scale", "phaseTimings",
                "throughput", "latency", "rowCounts", "candidateCounts",
                "operationCounts", "accessPatternCard", "observationKinds",
                "allocationPerOperation",
                "gcStats", "exactIndexStats", "keySpaceStats", "selectorStats",
                "mutationReadRatio", "materializationStats",
                "effectiveMaterializationBudget", "externalDtoStats",
                "columnViewStats", "allocationEstimate", "hardwareCounterStats"};
        for (String nested : nestedContracts) {
            @SuppressWarnings("unchecked")
            Map<String, Object> contract = (Map<String, Object>) properties.get(nested);
            requireExactNestedSchema(contract, "properties." + nested);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> claim = (Map<String, Object>) properties.get("claimAllowed");
        @SuppressWarnings("unchecked")
        Map<String, Object> level = (Map<String, Object>) properties.get("level");
        require(Boolean.FALSE.equals(claim.get("const")), "schema claimAllowed const");
        require("smoke".equals(level.get("const")), "schema level const");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message + " validation failed");
    }

    @SuppressWarnings("unchecked")
    private static void requireExactNestedSchema(Map<String, Object> schema, String path) {
        Object alternatives = schema.get("oneOf");
        if (alternatives != null) {
            require(alternatives instanceof List && !((List<?>) alternatives).isEmpty(),
                    path + " oneOf");
            for (Object alternative : (List<Object>) alternatives) {
                require(alternative instanceof Map, path + " alternative type");
                requireExactNestedSchema((Map<String, Object>) alternative, path + ".oneOf");
            }
            return;
        }
        require("object".equals(schema.get("type")), path + " object type");
        require(Boolean.FALSE.equals(schema.get("additionalProperties")),
                path + " additionalProperties");
        require(schema.get("properties") instanceof Map, path + " properties");
        require(schema.get("required") instanceof List, path + " required");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        List<Object> required = (List<Object>) schema.get("required");
        require(required.equals(new ArrayList<Object>(properties.keySet())),
                path + " exact required");
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!(entry.getValue() instanceof Map)) continue;
            Map<String, Object> child = (Map<String, Object>) entry.getValue();
            if ("object".equals(child.get("type")) || child.containsKey("oneOf")) {
                requireExactNestedSchema(child, path + "." + entry.getKey());
            } else if ("array".equals(child.get("type"))
                    && child.get("items") instanceof Map) {
                Map<String, Object> item = (Map<String, Object>) child.get("items");
                if ("object".equals(item.get("type")) || item.containsKey("oneOf")) {
                    requireExactNestedSchema(item, path + "." + entry.getKey() + "[]");
                }
            }
        }
    }
}
