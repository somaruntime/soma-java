package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

import java.util.Collections;
import java.util.List;

/**
 * Immutable logical transformation definition.
 *
 * <p>A definition retains semantics and callbacks, never live Table state.</p>
 */
public final class DataFlowDefinition<R> {
    enum TerminalKind {
        PACKED_COUNT
    }

    private final List<SourceSlot<? extends DataFlowBinding>> requiredSources;
    private final SourceSlot<? extends DataFlowBinding> candidateSource;
    private final TerminalKind terminalKind;
    private final String identity;

    private DataFlowDefinition(
            SourceSlot<? extends DataFlowBinding> candidateSource,
            TerminalKind terminalKind) {
        this.candidateSource = candidateSource;
        this.requiredSources =
                Collections.<SourceSlot<? extends DataFlowBinding>>singletonList(
                        candidateSource);
        this.terminalKind = terminalKind;
        identity = DataFlowSupport.identity(
                "soma-definition-v1\n"
                        + candidateSource.ordinal() + "\n"
                        + candidateSource.alias() + "\n"
                        + candidateSource.schemaIdentity() + "\n"
                        + candidateSource.tableIdentity() + "\n"
                        + terminalKind.name() + "\n");
    }

    static DataFlowDefinition<LongScalarResult> packedCount(
            SourceSlot<? extends DataFlowBinding> source) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        return new DataFlowDefinition<LongScalarResult>(
                source, TerminalKind.PACKED_COUNT);
    }

    public String identity() {
        return identity;
    }

    public DataFlowTemplate<R> compile() {
        return new DataFlowTemplate<R>(this);
    }

    public DataFlowExplain explain() {
        return new DataFlowExplain(
                identity,
                "",
                "Candidate -> Scalar<long>",
                candidateSource.alias() + ":Packed -> Count",
                "unbound");
    }

    List<SourceSlot<? extends DataFlowBinding>> requiredSources() {
        return requiredSources;
    }

    SourceSlot<? extends DataFlowBinding> candidateSource() {
        return candidateSource;
    }

    TerminalKind terminalKind() {
        return terminalKind;
    }
}
