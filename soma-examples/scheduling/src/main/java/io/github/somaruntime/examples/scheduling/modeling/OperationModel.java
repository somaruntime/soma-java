package io.github.somaruntime.examples.scheduling.modeling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable FJSP operation definition. */
public final class OperationModel {
    private final long operationId;
    private final long jobId;
    private final long sequence;
    private final List<ProcessingOptionModel> processingOptions;

    public OperationModel(
            long operationId,
            long jobId,
            long sequence,
            List<ProcessingOptionModel> processingOptions) {
        this.operationId = operationId;
        this.jobId = jobId;
        this.sequence = sequence;
        this.processingOptions = Collections.unmodifiableList(
                new ArrayList<ProcessingOptionModel>(
                        Objects.requireNonNull(processingOptions, "processingOptions")));
    }

    public long operationId() { return operationId; }
    public long jobId() { return jobId; }
    public long sequence() { return sequence; }
    public List<ProcessingOptionModel> processingOptions() { return processingOptions; }
}
