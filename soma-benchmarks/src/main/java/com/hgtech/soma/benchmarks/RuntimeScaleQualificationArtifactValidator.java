package com.hgtech.soma.benchmarks;

import java.io.File;
import java.util.List;
import java.util.Map;

/** Strict command-line validator for runtime-scale qualification JSONL. */
public final class RuntimeScaleQualificationArtifactValidator {
    private RuntimeScaleQualificationArtifactValidator() {
    }

    public static void main(String[] args) throws Exception {
        boolean complete = false;
        File artifact = null;
        for (String arg : args) {
            if ("--complete".equals(arg)) {
                complete = true;
            } else if (arg.startsWith("--")) {
                throw new IllegalArgumentException(
                        "unknown qualification validator option: " + arg);
            } else if (artifact == null) {
                artifact = new File(arg);
            } else {
                throw new IllegalArgumentException(
                        "one qualification artifact is required");
            }
        }
        if (artifact == null) {
            throw new IllegalArgumentException(
                    "qualification artifact path is required");
        }
        List<Map<String, Object>> records =
                RuntimeScaleQualificationModel.read(artifact);
        RuntimeScaleQualificationModel.validateArtifact(records, complete);
        System.out.println(
                "runtime-scale-qualification-artifact: ok records="
                        + records.size()
                        + " complete=" + complete);
    }
}
