package io.github.somaruntime.examples.simulation.configuration;

import io.github.somaruntime.soma.SomaCompression;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/** Strict loader for the documented grassing properties. */
public final class SimulationConfigLoader {
    private static final Set<String> ALLOWED = new HashSet<String>(Arrays.asList(
            "model.worldWidth", "model.worldHeight", "model.initialGrassers",
            "model.initialGrass", "model.initialEnergy", "model.satiationEnergy",
            "model.grassCapacity", "model.grassGrowthRate", "model.metabolism",
            "model.reproductionProbability", "model.grazingConsumption",
            "model.minimumGrassAfterGrazing", "model.resumeGrazingAt", "model.walkSpeed",
            "model.turnStdDevDegrees", "model.randomSeed", "run.maxTicks",
            "run.statisticsEveryTicks", "run.memoryBudgetBytes", "run.compression",
            "visualization.enabled", "visualization.renderEveryTicks",
            "visualization.cellSize", "visualization.frameDelayMillis"));

    private SimulationConfigLoader() {}

    public static SimulationConfig load(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        for (String name : properties.stringPropertyNames()) {
            if (!ALLOWED.contains(name)) {
                throw new IllegalArgumentException("unknown property: " + name);
            }
        }
        return new SimulationConfig(
                requiredInt(properties, "model.worldWidth"),
                requiredInt(properties, "model.worldHeight"),
                requiredInt(properties, "model.initialGrassers"),
                requiredFloat(properties, "model.initialGrass"),
                requiredFloat(properties, "model.initialEnergy"),
                requiredFloat(properties, "model.satiationEnergy"),
                requiredFloat(properties, "model.grassCapacity"),
                requiredFloat(properties, "model.grassGrowthRate"),
                requiredFloat(properties, "model.metabolism"),
                requiredFloat(properties, "model.reproductionProbability"),
                requiredFloat(properties, "model.grazingConsumption"),
                requiredFloat(properties, "model.minimumGrassAfterGrazing"),
                requiredFloat(properties, "model.resumeGrazingAt"),
                requiredFloat(properties, "model.walkSpeed"),
                (float) Math.toRadians(requiredFloat(properties, "model.turnStdDevDegrees")),
                requiredLong(properties, "model.randomSeed"),
                requiredLong(properties, "run.maxTicks"),
                requiredLong(properties, "run.statisticsEveryTicks"),
                requiredLong(properties, "run.memoryBudgetBytes"),
                parseCompression(required(properties, "run.compression")),
                requiredBoolean(properties, "visualization.enabled"),
                requiredLong(properties, "visualization.renderEveryTicks"),
                requiredInt(properties, "visualization.cellSize"),
                requiredLong(properties, "visualization.frameDelayMillis"));
    }

    private static String required(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("missing property: " + name);
        }
        return value.trim();
    }

    private static int requiredInt(Properties properties, String name) {
        try {
            return Integer.parseInt(required(properties, name));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid int property: " + name, failure);
        }
    }

    private static long requiredLong(Properties properties, String name) {
        try {
            return Long.parseLong(required(properties, name));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid long property: " + name, failure);
        }
    }

    private static float requiredFloat(Properties properties, String name) {
        try {
            return Float.parseFloat(required(properties, name));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid float property: " + name, failure);
        }
    }

    private static boolean requiredBoolean(Properties properties, String name) {
        String value = required(properties, name);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("invalid boolean property: " + name);
    }

    private static SomaCompression parseCompression(String value) {
        try {
            return SomaCompression.valueOf(value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid compression: " + value, failure);
        }
    }
}
