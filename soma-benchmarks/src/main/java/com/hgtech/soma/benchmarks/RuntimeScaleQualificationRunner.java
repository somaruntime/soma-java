package com.hgtech.soma.benchmarks;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * One-JVM-per-lane runtime-scale production qualification runner.
 *
 * <p>The shell owner selects heap/GC and operational timeout before launch.
 * This process runs exactly one preregistered lane so a large lane cannot hide
 * another lane's failure or contaminate its heap/GC observation.</p>
 */
public final class RuntimeScaleQualificationRunner {
    private RuntimeScaleQualificationRunner() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        QualificationConfig config = new QualificationConfig(
                options.qualificationId,
                options.commit,
                options.treeState,
                options.seed);
        QualificationObservation observation =
                RuntimeScaleQualificationWorkloads.run(
                        options.lane, config);
        Map<String, Object> record =
                RuntimeScaleQualificationModel.record(
                        config, observation);
        RuntimeScaleQualificationModel.write(
                options.output, Collections.singletonList(record));
        System.out.println(
                "runtime-scale-qualification-runner: ok lane="
                        + options.lane
                        + " output=" + options.output.getAbsolutePath());
    }

    private static final class Options {
        File output;
        String lane;
        String qualificationId;
        String commit;
        String treeState;
        long seed = 1397706049L;

        static Options parse(String[] args) {
            Options result = new Options();
            for (int index = 0; index < args.length; index++) {
                String option = args[index];
                if ("--output".equals(option)) {
                    result.output = new File(value(args, ++index, option));
                } else if ("--lane".equals(option)) {
                    result.lane = value(args, ++index, option);
                } else if ("--qualification-id".equals(option)) {
                    result.qualificationId =
                            value(args, ++index, option);
                } else if ("--commit".equals(option)) {
                    result.commit = value(args, ++index, option);
                } else if ("--tree-state".equals(option)) {
                    result.treeState = value(args, ++index, option);
                } else if ("--seed".equals(option)) {
                    result.seed = Long.parseLong(
                            value(args, ++index, option));
                } else {
                    throw new IllegalArgumentException(
                            "unknown qualification runner option: "
                                    + option);
                }
            }
            if (result.output == null
                    || result.lane == null
                    || result.qualificationId == null
                    || result.commit == null
                    || result.treeState == null) {
                throw new IllegalArgumentException(
                        "--output, --lane, --qualification-id, "
                                + "--commit and --tree-state are required");
            }
            if (!RuntimeScaleQualificationModel.REQUIRED_LANES
                    .contains(result.lane)) {
                throw new IllegalArgumentException(
                        "unknown qualification lane: " + result.lane);
            }
            return result;
        }

        private static String value(
                String[] args, int index, String option) {
            if (index >= args.length || args[index].startsWith("--")) {
                throw new IllegalArgumentException(
                        "missing value for " + option);
            }
            return args[index];
        }
    }
}
