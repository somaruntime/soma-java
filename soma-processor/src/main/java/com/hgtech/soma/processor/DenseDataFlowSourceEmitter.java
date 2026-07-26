package com.hgtech.soma.processor;

import static com.hgtech.soma.processor.DenseSourceNames.q;
import static com.hgtech.soma.processor.DenseTableCodegenModel.TableSpec;

/** Emits one bounded schema-specific DataFlow companion per generated Table. */
final class DenseDataFlowSourceEmitter {
    private final String generatedPackage;
    private final String schemaHash;

    DenseDataFlowSourceEmitter(String generatedPackage, String schemaHash) {
        this.generatedPackage = generatedPackage;
        this.schemaHash = schemaHash;
    }

    String dataFlowSource(TableSpec table) {
        String type = table.name("DataFlow");
        String tableType = table.name("Table");
        SourceBuilder out = new SourceBuilder(
                "package " + generatedPackage + ";\n\n");
        out.append("import com.hgtech.soma.dataflow.CandidateFlow;\n")
                .append("import com.hgtech.soma.dataflow.GeneratedDataFlow;\n")
                .append("import com.hgtech.soma.dataflow.SourceSlot;\n")
                .append("import com.hgtech.soma.dataflow.generated.DataFlowBinding;\n")
                .append("import com.hgtech.soma.runtime.generated.RuntimeCompatibility;\n\n")
                .append("/** Schema-specific typed DataFlow source and binding companion. */\n")
                .append("public final class ").append(type).append(" {\n")
                .append("  public static final String SCHEMA_IDENTITY=")
                .append(q(schemaHash)).append(";\n")
                .append("  public static final String TABLE_IDENTITY=")
                .append(q(table.logicalName)).append(";\n")
                .append("  private ").append(type).append("(){}\n")
                .append("  public static Source source(String alias){return new Source(alias);}\n")
                .append("  public static Binding bind(").append(tableType)
                .append(" table){return new Binding(table);}\n\n")
                .append("  public static final class Source extends SourceSlot<Binding>{\n")
                .append("    private Source(String alias){super(0,alias,SCHEMA_IDENTITY,TABLE_IDENTITY);}\n")
                .append("    public CandidateFlow<Binding> candidates(){return GeneratedDataFlow.candidates(this);}\n")
                .append("  }\n\n")
                .append("  public static final class Binding implements DataFlowBinding{\n")
                .append("    private final ").append(tableType).append(" table;\n")
                .append("    private Binding(").append(tableType)
                .append(" table){if(table==null)throw new NullPointerException(\"table\");table.requireDataFlowRootSource();this.table=table;}\n")
                .append("    public long aggregateInstanceId(){return table.dataFlowAggregateInstanceId();}\n")
                .append("    public Object physicalIdentity(){return table.dataFlowPhysicalIdentity();}\n")
                .append("    public String schemaIdentity(){return SCHEMA_IDENTITY;}\n")
                .append("    public String tableIdentity(){return TABLE_IDENTITY;}\n")
                .append("    public String generatedProtocol(){return RuntimeCompatibility.GENERATED_PROTOCOL;}\n")
                .append("    public String transformationProtocol(){return GeneratedDataFlow.TRANSFORMATION_PROTOCOL;}\n")
                .append("    public String kernelProtocol(){return GeneratedDataFlow.KERNEL_PROTOCOL;}\n")
                .append("    public long structuralEpoch(){return table.dataFlowStructuralEpoch();}\n")
                .append("    public int packedSize(){return table.dataFlowPackedSize();}\n")
                .append("    public void acquire(String operation){table.acquireDataFlow(operation);}\n")
                .append("    public void release(String operation,boolean success,long scanned,long matched,String failureCode){table.releaseDataFlow(operation);}\n")
                .append("  }\n")
                .append("}\n");
        return out.toString();
    }
}
