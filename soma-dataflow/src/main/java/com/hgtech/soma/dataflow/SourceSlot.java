package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

/** Typed logical source identity; it never retains a live Table. */
public class SourceSlot<B extends DataFlowBinding> {
    private final int ordinal;
    private final String alias;
    private final String schemaIdentity;
    private final String tableIdentity;

    protected SourceSlot(
            int ordinal, String alias, String schemaIdentity, String tableIdentity) {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must be non-negative");
        }
        this.ordinal = ordinal;
        this.alias = DataFlowSupport.required(alias, "alias");
        this.schemaIdentity = DataFlowSupport.required(
                schemaIdentity, "schemaIdentity");
        this.tableIdentity = DataFlowSupport.required(tableIdentity, "tableIdentity");
    }

    public int ordinal() { return ordinal; }
    public String alias() { return alias; }
    public String schemaIdentity() { return schemaIdentity; }
    public String tableIdentity() { return tableIdentity; }
}
