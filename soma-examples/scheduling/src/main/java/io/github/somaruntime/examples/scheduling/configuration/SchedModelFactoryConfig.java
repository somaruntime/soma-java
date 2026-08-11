package io.github.somaruntime.examples.scheduling.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/** Immutable input scale and distribution configuration for the model factory. */
public final class SchedModelFactoryConfig {
    private final int jobCount;
    private final int operationsPerJob;
    private final int machineCount;
    private final int candidatesPerOperation;
    private final long randomSeed;
    private final long minimumProcessingTime;
    private final long maximumProcessingTime;

    public SchedModelFactoryConfig(
            int jobCount,
            int operationsPerJob,
            int machineCount,
            int candidatesPerOperation,
            long randomSeed,
            long minimumProcessingTime,
            long maximumProcessingTime) {
        if (jobCount <= 0 || operationsPerJob <= 0 || machineCount <= 0) {
            throw new IllegalArgumentException("job, operation and machine counts must be positive");
        }
        if (candidatesPerOperation <= 0 || candidatesPerOperation > machineCount) {
            throw new IllegalArgumentException(
                    "candidatesPerOperation must be in [1, machineCount]");
        }
        if (minimumProcessingTime <= 0L
                || maximumProcessingTime < minimumProcessingTime) {
            throw new IllegalArgumentException("processing-time range is invalid");
        }
        this.jobCount = jobCount;
        this.operationsPerJob = operationsPerJob;
        this.machineCount = machineCount;
        this.candidatesPerOperation = candidatesPerOperation;
        this.randomSeed = randomSeed;
        this.minimumProcessingTime = minimumProcessingTime;
        this.maximumProcessingTime = maximumProcessingTime;
    }

    public static SchedModelFactoryConfig load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return new SchedModelFactoryConfig(
                positiveInt(properties, "jobCount"),
                positiveInt(properties, "operationsPerJob"),
                positiveInt(properties, "machineCount"),
                positiveInt(properties, "candidatesPerOperation"),
                requiredLong(properties, "randomSeed"),
                requiredLong(properties, "minimumProcessingTime"),
                requiredLong(properties, "maximumProcessingTime"));
    }

    private static int positiveInt(Properties properties, String name) {
        long value = requiredLong(properties, name);
        if (value <= 0L || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must be a positive int");
        }
        return (int) value;
    }

    private static long requiredLong(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("missing property: " + name);
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid long property: " + name, failure);
        }
    }

    public int jobCount() { return jobCount; }
    public int operationsPerJob() { return operationsPerJob; }
    public int machineCount() { return machineCount; }
    public int candidatesPerOperation() { return candidatesPerOperation; }
    public long randomSeed() { return randomSeed; }
    public long minimumProcessingTime() { return minimumProcessingTime; }
    public long maximumProcessingTime() { return maximumProcessingTime; }

    public long operationCount() {
        return Math.multiplyExact((long) jobCount, operationsPerJob);
    }

    public long processingOptionCount() {
        return Math.multiplyExact(operationCount(), candidatesPerOperation);
    }
}
