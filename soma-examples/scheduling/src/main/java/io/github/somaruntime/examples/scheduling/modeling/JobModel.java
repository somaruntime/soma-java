package io.github.somaruntime.examples.scheduling.modeling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable ordered operation chain for one FJSP job. */
public final class JobModel {
    private final long jobId;
    private final List<OperationModel> operations;

    public JobModel(long jobId, List<OperationModel> operations) {
        this.jobId = jobId;
        this.operations = Collections.unmodifiableList(
                new ArrayList<OperationModel>(
                        Objects.requireNonNull(operations, "operations")));
    }

    public long jobId() { return jobId; }
    public List<OperationModel> operations() { return operations; }
}
