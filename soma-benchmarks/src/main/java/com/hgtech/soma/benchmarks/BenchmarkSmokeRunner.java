package com.hgtech.soma.benchmarks;

import java.io.File;
import java.util.List;

/** Java 8 benchmark smoke CLI；只生成claimAllowed=false的结构化evidence。 */
public final class BenchmarkSmokeRunner {
    private BenchmarkSmokeRunner() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        BenchmarkConfig config = new BenchmarkConfig(options.commit, options.scale,
                options.rows, options.seed, options.warmup, options.forks,
                options.measurements);
        BenchmarkEnvironment environment = new BenchmarkEnvironment();
        List<BenchmarkRecord> records = SmokeLaneSuite.run(config, environment);
        BenchmarkModel.write(options.output, records);
        BenchmarkArtifactValidator.validate(options.output);
        System.out.println("benchmark-smoke-schema: " + BenchmarkModel.SCHEMA_VERSION);
        System.out.println("benchmark-smoke-records: " + records.size());
        System.out.println("benchmark-smoke-artifact: " + options.output.getAbsolutePath());
        System.out.println("benchmark-smoke-claim-allowed: false");
        System.out.println("benchmark-smoke-runner: ok");
    }

    private static final class Options {
        File output = new File("target/benchmark-smoke/benchmark-smoke.jsonl");
        String commit = "working-tree";
        String scale = "smoke";
        int rows = 128;
        long seed = 0x534f4d41L;
        int warmup = 1;
        int forks = 1;
        int measurements = 2;

        static Options parse(String[] args) {
            Options options = new Options();
            for (int index = 0; index < args.length; index++) {
                String option = args[index];
                if ("--output".equals(option)) options.output = new File(value(args, ++index, option));
                else if ("--commit".equals(option)) options.commit = value(args, ++index, option);
                else if ("--scale".equals(option)) options.scale = value(args, ++index, option);
                else if ("--rows".equals(option)) options.rows = integer(args, ++index, option);
                else if ("--seed".equals(option)) options.seed = longValue(args, ++index, option);
                else if ("--warmup".equals(option)) options.warmup = integer(args, ++index, option);
                else if ("--forks".equals(option)) options.forks = integer(args, ++index, option);
                else if ("--measurements".equals(option)) options.measurements = integer(args, ++index, option);
                else throw new IllegalArgumentException("unknown benchmark option: " + option);
            }
            return options;
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length || args[index].isEmpty()) {
                throw new IllegalArgumentException("missing value for " + option);
            }
            return args[index];
        }

        private static int integer(String[] args, int index, String option) {
            try { return Integer.parseInt(value(args, index, option)); }
            catch (NumberFormatException failure) {
                throw new IllegalArgumentException("invalid integer for " + option, failure);
            }
        }

        private static long longValue(String[] args, int index, String option) {
            try { return Long.parseLong(value(args, index, option)); }
            catch (NumberFormatException failure) {
                throw new IllegalArgumentException("invalid long for " + option, failure);
            }
        }
    }
}
