package io.github.somaruntime.examples.simulation.application;

import io.github.somaruntime.examples.simulation.configuration.SimulationConfig;
import io.github.somaruntime.examples.simulation.configuration.SimulationConfigLoader;
import io.github.somaruntime.examples.simulation.engine.api.SimulationRunResult;
import io.github.somaruntime.examples.simulation.presentation.console.ConsoleStatisticsReporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Runs the configurable UI or true headless Grassing reference application. */
public final class SimulationMain {
    private SimulationMain() {}

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        SimulationConfig config = SimulationConfigLoader.load(options.configPath);
        if (options.visualizationEnabled != null) {
            config = config.withVisualizationEnabled(options.visualizationEnabled);
        }
        if (options.ticks != null) {
            config = config.withMaxTicks(options.ticks);
        }
        SimulationRunResult result = new SimulationApplication().run(config);
        new ConsoleStatisticsReporter().report(result);
    }

    private static final class Options {
        Path configPath = defaultConfigPath();
        Boolean visualizationEnabled;
        Long ticks;

        static Options parse(String[] args) {
            Options options = new Options();
            for (int index = 0; index < args.length; index++) {
                String argument = args[index];
                if ("--headless".equals(argument)) {
                    options.visualizationEnabled = false;
                } else if ("--ui".equals(argument)) {
                    options.visualizationEnabled = true;
                } else if (argument.startsWith("--ticks=")) {
                    options.ticks = parseLong(argument, "--ticks=");
                } else if (argument.startsWith("--config=")) {
                    options.configPath = Paths.get(argument.substring("--config=".length()));
                } else {
                    throw new IllegalArgumentException("unsupported option: " + argument);
                }
            }
            return options;
        }

        private static long parseLong(String argument, String prefix) {
            try {
                return Long.parseLong(argument.substring(prefix.length()));
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("invalid option: " + argument, failure);
            }
        }

        private static Path defaultConfigPath() {
            Path repositoryPath = Paths.get(
                    "soma-examples", "simulation", "config", "grassing.properties");
            return Files.isRegularFile(repositoryPath)
                    ? repositoryPath
                    : Paths.get("config", "grassing.properties");
        }
    }
}
