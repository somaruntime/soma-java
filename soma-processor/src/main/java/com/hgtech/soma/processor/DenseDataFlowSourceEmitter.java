package com.hgtech.soma.processor;

import static com.hgtech.soma.processor.DenseSourceNames.q;
import static com.hgtech.soma.processor.DenseSourceNames.cap;
import static com.hgtech.soma.processor.DenseSelectorCodegenModel.selectorParameters;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.appendSelectorParameters;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.appendSourceLeafArguments;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.selectorHashBits;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.selectorParameterArgument;
import static com.hgtech.soma.processor.DenseSelectorSourceSupport.selectorSuffix;
import static com.hgtech.soma.processor.DenseTableCodegenModel.FieldSpec;
import static com.hgtech.soma.processor.DenseTableCodegenModel.ChildSpec;
import static com.hgtech.soma.processor.DenseTableCodegenModel.SelectorLeafSpec;
import static com.hgtech.soma.processor.DenseTableCodegenModel.SelectorSpec;
import static com.hgtech.soma.processor.DenseTableCodegenModel.TableSpec;
import static com.hgtech.soma.processor.DenseTableCodegenModel.ValueLeafSpec;

import com.hgtech.soma.processor.DenseSelectorCodegenModel.SelectorParameter;

import java.util.List;

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
                "// SOMA-GENERATED: soma-processor-v1\npackage "
                        + generatedPackage + ";\n\n");
        out.append("import com.hgtech.soma.dataflow.BooleanExpression;\n")
                .append("import com.hgtech.soma.dataflow.CandidateFlow;\n")
                .append("import com.hgtech.soma.dataflow.CallbackDeliveryDefinition;\n")
                .append("import com.hgtech.soma.dataflow.DataFlowDefinition;\n")
                .append("import com.hgtech.soma.dataflow.DoubleExpression;\n")
                .append("import com.hgtech.soma.dataflow.ExpandedFlow;\n")
                .append("import com.hgtech.soma.dataflow.GeneratedDataFlow;\n")
                .append("import com.hgtech.soma.dataflow.LongExpression;\n")
                .append("import com.hgtech.soma.dataflow.StringExpression;\n")
                .append("import com.hgtech.soma.dataflow.ParameterSlot;\n")
                .append("import com.hgtech.soma.dataflow.PointFlow;\n")
                .append("import com.hgtech.soma.dataflow.SourceSlot;\n")
                .append("import com.hgtech.soma.dataflow.generated.CandidateDeliveryAccess;\n")
                .append("import com.hgtech.soma.dataflow.generated.CandidateDeliverySession;\n")
                .append("import com.hgtech.soma.dataflow.generated.CandidateIndexAccess;\n")
                .append("import com.hgtech.soma.dataflow.generated.CandidateEffectAccess;\n")
                .append("import com.hgtech.soma.dataflow.generated.CandidateMaterializationAccess;\n")
                .append("import com.hgtech.soma.dataflow.generated.DataFlowBinding;\n")
                .append("import com.hgtech.soma.dataflow.generated.OwnedChildAccess;\n")
                .append("import com.hgtech.soma.dataflow.generated.PointIndexAccess;\n")
                .append("import com.hgtech.soma.dataflow.generated.SnapshotGatherAccess;\n")
                .append("import com.hgtech.soma.runtime.IndexSnapshot;\n")
                .append("import com.hgtech.soma.runtime.MaterializationBudget;\n")
                .append("import com.hgtech.soma.runtime.RemoveResult;\n")
                .append("import com.hgtech.soma.runtime.UpdateResult;\n")
                .append("import com.hgtech.soma.runtime.generated.KeyCanonicalization;\n")
                .append("import com.hgtech.soma.runtime.generated.RuntimeFailures;\n")
                .append("import com.hgtech.soma.runtime.generated.RuntimeCompatibility;\n\n")
                .append("/** Schema-specific typed DataFlow source and binding companion. */\n")
                .append("public final class ").append(type).append(" {\n")
                .append("  public static final String SCHEMA_IDENTITY=")
                .append(q(schemaHash)).append(";\n")
                .append("  public static final String TABLE_IDENTITY=")
                .append(q(table.logicalName)).append(";\n")
                .append("  private static final String TABLE=TABLE_IDENTITY;\n")
                .append("  private ").append(type).append("(){}\n")
                .append("  public static Source source(String alias){return new Source(0,alias);}\n")
                .append("  public static Source source(int ordinal,String alias){return new Source(ordinal,alias);}\n")
                .append("  public static Binding bind(").append(tableType)
                .append(" table){return new Binding(table,true);}\n")
                .append("  static Binding bindOwned(").append(tableType)
                .append(" table){return new Binding(table,false);}\n\n")
                .append("  public static final class Source extends SourceSlot<Binding>{\n")
                .append("    private final Columns columns=new Columns(this);\n")
                .append("    private Source(int ordinal,String alias){super(ordinal,alias,SCHEMA_IDENTITY,TABLE_IDENTITY);}\n")
                .append("    public CandidateFlow<Binding> candidates(){return GeneratedDataFlow.candidates(this);}\n")
                .append("    public LongExpression<Binding> longParameter(ParameterSlot<Long> parameter){return GeneratedDataFlow.longParameter(this,parameter);}\n")
                .append("    public DoubleExpression<Binding> doubleParameter(ParameterSlot<Double> parameter){return GeneratedDataFlow.doubleParameter(this,parameter);}\n")
                .append("    public BooleanExpression<Binding> booleanParameter(ParameterSlot<Boolean> parameter){return GeneratedDataFlow.booleanParameter(this,parameter);}\n")
                .append("    public StringExpression<Binding> stringParameter(ParameterSlot<String> parameter){return GeneratedDataFlow.stringParameter(this,parameter);}\n");
        appendAccessSourceMethods(out, table);
        appendExactSourceMethods(out, table);
        appendOwnedChildSourceMethods(out, table);
        appendEffectSourceMethods(out, table);
        out.append("    public Columns columns(){return columns;}\n")
                .append("  }\n\n")
                .append("  public static final class Columns{\n")
                .append("    private final Source source;\n")
                .append("    private Columns(Source source){this.source=source;}\n");
        appendSourceExpressions(out, table);
        out.append("  }\n\n");
        appendExactAccessTypes(out, table);
        appendOwnedChildAccessTypes(out, table);
        appendEffectAccessType(out, table);
        appendAccessTypes(out, table);
        out.append("  private static String identityComponent(Object value){String text=String.valueOf(value);return text.length()+\":\"+text;}\n\n")
                .append("  public static final class Binding implements DataFlowBinding{\n")
                .append("    private final ").append(tableType).append(" table;\n");
        for (ChildSpec child : table.children) {
            out.append("    private final int ")
                    .append(child.javaName)
                    .append("MaximumRows;\n");
        }
        out
                .append("    private Binding(").append(tableType)
                .append(" table,boolean requireRoot){if(table==null)throw new NullPointerException(\"table\");if(requireRoot)table.requireDataFlowRootSource();this.table=table;");
        for (ChildSpec child : table.children) {
            out.append("this.")
                    .append(child.javaName)
                    .append("MaximumRows=table.dataFlow")
                    .append(cap(child.javaName))
                    .append("MaximumRows();");
        }
        out.append("}\n")
                .append("    public long aggregateInstanceId(){return table.dataFlowAggregateInstanceId();}\n")
                .append("    public Object physicalIdentity(){return table.dataFlowPhysicalIdentity();}\n")
                .append("    public String schemaIdentity(){return SCHEMA_IDENTITY;}\n")
                .append("    public String tableIdentity(){return TABLE_IDENTITY;}\n")
                .append("    public String generatedProtocol(){return RuntimeCompatibility.GENERATED_PROTOCOL;}\n")
                .append("    public String transformationProtocol(){return GeneratedDataFlow.TRANSFORMATION_PROTOCOL;}\n")
                .append("    public String kernelProtocol(){return GeneratedDataFlow.KERNEL_PROTOCOL;}\n")
                .append("    public long structuralEpoch(){return table.dataFlowStructuralEpoch();}\n")
                .append("    public int packedSize(){return table.dataFlowPackedSize();}\n");
        out.append("    public boolean segmentedStorage(){return table.dataFlowSegmentedStorage();}\n")
                .append("    public int flatHeadRows(){return table.dataFlowFlatHeadRows();}\n")
                .append("    public int segmentRows(){return table.dataFlowSegmentRows();}\n");
        appendBindingAccess(out, table);
        out.append("    public IndexSnapshot indexSnapshot(int[] indexes,int length){return table.dataFlowIndexSnapshot(indexes,length);}\n")
                .append("    public void acquire(String operation){table.acquireDataFlow(operation);}\n")
                .append("    public void release(String operation,boolean success,long scanned,long matched,String failureCode){table.releaseDataFlow(operation);}\n")
                .append("    private IllegalArgumentException unsupported(int column,String carrier){return new IllegalArgumentException(\"unsupported \"+carrier+\" dataflow column \"+column+\" for \"+TABLE_IDENTITY);}\n")
                .append("  }\n")
                .append("}\n");
        return out.toString();
    }

    private static void appendAccessSourceMethods(
            SourceBuilder out, TableSpec table) {
        String scan = table.name("Scan");
        String carrier = table.carrierType;
        out.append("    public PointFlow<Binding,").append(carrier)
                .append("> pointAt(int index){return GeneratedDataFlow.point(this,new CurrentIndexAccess(index),new MaterializationAccess());}\n")
                .append("    public CandidateFlow<Binding> gather(ParameterSlot<IndexSnapshot> snapshot){return GeneratedDataFlow.gather(this,snapshot,new GatherAccess());}\n")
                .append("    public CallbackDeliveryDefinition<").append(scan)
                .append(".Visitor> deliver(CandidateFlow<Binding> candidates){return GeneratedDataFlow.deliver(this,candidates,new DeliveryAccess(),")
                .append(scan).append(".Visitor.class);}\n")
                .append("    public DataFlowDefinition<java.util.List<")
                .append(carrier)
                .append(">> materialize(CandidateFlow<Binding> candidates,MaterializationBudget budget){return GeneratedDataFlow.materialize(this,candidates,new MaterializationAccess(),budget);}\n");
        if (table.keyed()) {
            FieldSpec key = table.keyField();
            out.append("    public PointFlow<Binding,").append(carrier)
                    .append("> pointBy").append(cap(key.javaName)).append('(')
                    .append(key.primitive).append(" key){return GeneratedDataFlow.point(this,new KeyAccess(key),new MaterializationAccess());}\n");
        }
    }

    private static void appendAccessTypes(
            SourceBuilder out, TableSpec table) {
        String scan = table.name("Scan");
        out.append("  private static final class CurrentIndexAccess implements PointIndexAccess<Binding>{private final int index;private CurrentIndexAccess(int index){this.index=index;}public int index(Binding binding){return binding.table.dataFlowCurrentIndex(index);}public String identity(){return TABLE_IDENTITY+\":current-index:\"+index;}}\n")
                .append("  private static final class GatherAccess implements SnapshotGatherAccess<Binding>{public void requireCurrent(Binding binding,IndexSnapshot snapshot){binding.table.dataFlowRequireCurrent(snapshot);}public String identity(){return TABLE_IDENTITY+\":index-snapshot-gather-v1\";}}\n")
                .append("  private static final class DeliveryAccess implements CandidateDeliveryAccess<Binding,")
                .append(scan)
                .append(".Visitor>{public CandidateDeliverySession open(Binding binding,")
                .append(scan)
                .append(".Visitor visitor){return binding.table.dataFlowDeliverySession(visitor);}public String identity(){return TABLE_IDENTITY+\":candidate-delivery-v1\";}}\n")
                .append("  private static final class MaterializationAccess implements CandidateMaterializationAccess<Binding,")
                .append(table.carrierType)
                .append(">{public java.util.List<").append(table.carrierType)
                .append("> materialize(Binding binding,int[] indexes,int count,MaterializationBudget budget){return binding.table.dataFlowMaterializeRows(indexes,count,budget);}public String identity(){return TABLE_IDENTITY+\":materialize-v1\";}}\n");
        if (table.keyed()) {
            FieldSpec key = table.keyField();
            out.append("  private static final class KeyAccess implements PointIndexAccess<Binding>{private final ")
                    .append(key.primitive)
                    .append(" key;private KeyAccess(").append(key.primitive)
                    .append(" key){this.key=key;}public int index(Binding binding){return binding.table.dataFlowKeyIndex(key);}public String identity(){return TABLE_IDENTITY+\":primary-key:\"+identityComponent(key);}}\n");
        }
        out.append('\n');
    }

    private static void appendEffectSourceMethods(
            SourceBuilder out, TableSpec table) {
        String scan = table.name("Scan");
        out.append("    public DataFlowDefinition<UpdateResult> update(CandidateFlow<Binding> candidates,")
                .append(scan)
                .append(".Updater updater){return GeneratedDataFlow.update(this,candidates,new EffectAccess(),updater);}\n")
                .append("    public DataFlowDefinition<RemoveResult> remove(CandidateFlow<Binding> candidates){return GeneratedDataFlow.remove(this,candidates,new EffectAccess());}\n");
    }

    private static void appendEffectAccessType(
            SourceBuilder out, TableSpec table) {
        String scan = table.name("Scan");
        out.append("  private static final class EffectAccess implements CandidateEffectAccess<Binding>{\n")
                .append("    public UpdateResult update(Binding binding,long expectedStructuralEpoch,int[] indexes,int count,Object updater){return binding.table.dataFlowUpdate(expectedStructuralEpoch,indexes,count,(")
                .append(scan).append(".Updater)updater);}\n")
                .append("    public RemoveResult remove(Binding binding,long expectedStructuralEpoch,int[] indexes,int count){return binding.table.dataFlowRemove(expectedStructuralEpoch,indexes,count);}\n")
                .append("    public String identity(){return TABLE_IDENTITY+\":candidate-effect-v1\";}\n")
                .append("  }\n\n");
    }

    private static void appendOwnedChildSourceMethods(
            SourceBuilder out, TableSpec table) {
        for (ChildSpec child : table.children) {
            String childDataFlow = child.rowSimpleName + "DataFlow";
            String method = "expand" + cap(child.javaName);
            out.append("    public ExpandedFlow<Binding,")
                    .append(childDataFlow).append(".Binding> ")
                    .append(method).append('(')
                    .append(childDataFlow).append(".Source childSource){return ")
                    .append(method)
                    .append("(candidates(),childSource);}\n")
                    .append("    public ExpandedFlow<Binding,")
                    .append(childDataFlow).append(".Binding> ")
                    .append(method)
                    .append("(CandidateFlow<Binding> parents,")
                    .append(childDataFlow)
                    .append(".Source childSource){return GeneratedDataFlow.ownedChildren(this,parents,childSource,new ")
                    .append(cap(child.javaName)).append("Access());}\n");
        }
    }

    private static void appendOwnedChildAccessTypes(
            SourceBuilder out, TableSpec table) {
        for (ChildSpec child : table.children) {
            String childDataFlow = child.rowSimpleName + "DataFlow";
            String access = cap(child.javaName) + "Access";
            out.append("  private static final class ").append(access)
                    .append(" implements OwnedChildAccess<Binding,")
                    .append(childDataFlow).append(".Binding>{\n")
                    .append("    public ").append(childDataFlow)
                    .append(".Binding childBinding(Binding parentBinding,int parentIndex){")
                    .append(child.tableType()).append(" child=parentBinding.table.dataFlow")
                    .append(cap(child.javaName))
                    .append("At(parentIndex);return child==null?null:")
                    .append(childDataFlow).append(".bindOwned(child);}\n")
                    .append("    public int maximumChildRows(Binding parentBinding){return parentBinding.")
                    .append(child.javaName)
                    .append("MaximumRows;}\n")
                    .append("    public String identity(){return ")
                    .append(q(table.logicalName + "." + child.logicalName))
                    .append(";}\n")
                    .append("  }\n\n");
        }
    }

    private static void appendExactSourceMethods(
            SourceBuilder out, TableSpec table) {
        for (int selectorIndex = 0;
             selectorIndex < table.selectors.size();
             selectorIndex++) {
            SelectorSpec selector = table.selectors.get(selectorIndex);
            List<SelectorParameter> parameters =
                    selectorParameters(table, selector);
            String suffix = selectorSuffix(selector);
            String method = "candidatesBy" + suffix;
            out.append("    public CandidateFlow<Binding> ")
                    .append(method).append('(');
            appendSelectorParameters(out, parameters);
            out.append("){");
            for (int leafIndex = 0;
                 leafIndex < selector.leaves.size();
                 leafIndex++) {
                SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
                out.append(leaf.storageType).append(" sourceLeaf")
                        .append(leafIndex).append('=')
                        .append(selectorParameterArgument(
                                parameters, leafIndex, leaf, method))
                        .append(';');
            }
            out.append("long hash=1469598103934665603L;");
            for (int leafIndex = 0;
                 leafIndex < selector.leaves.size();
                 leafIndex++) {
                SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
                out.append("hash=(hash^")
                        .append(selectorHashBits(
                                leaf.storageType,
                                "sourceLeaf" + leafIndex))
                        .append(")*1099511628211L;");
            }
            out.append("return GeneratedDataFlow.exactCandidates(this,new ExactAccess")
                    .append(selectorIndex).append("(hash");
            appendSourceLeafArguments(out, selector.leaves.size());
            out.append("));}\n");
            if ("unique".equals(selector.kind)) {
                out.append("    public PointFlow<Binding,")
                        .append(table.carrierType)
                        .append("> pointBy").append(suffix).append('(');
                appendSelectorParameters(out, parameters);
                out.append("){");
                for (int leafIndex = 0;
                     leafIndex < selector.leaves.size();
                     leafIndex++) {
                    SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
                    out.append(leaf.storageType).append(" sourceLeaf")
                            .append(leafIndex).append('=')
                            .append(selectorParameterArgument(
                                    parameters, leafIndex, leaf,
                                    "pointBy" + suffix))
                            .append(';');
                }
                out.append("long hash=1469598103934665603L;");
                for (int leafIndex = 0;
                     leafIndex < selector.leaves.size();
                     leafIndex++) {
                    SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
                    out.append("hash=(hash^")
                            .append(selectorHashBits(
                                    leaf.storageType,
                                    "sourceLeaf" + leafIndex))
                            .append(")*1099511628211L;");
                }
                out.append("return GeneratedDataFlow.point(this,new ExactAccess")
                        .append(selectorIndex).append("(hash");
                appendSourceLeafArguments(out, selector.leaves.size());
                out.append("),new MaterializationAccess());}\n");
            }
        }
    }

    private static void appendExactAccessTypes(
            SourceBuilder out, TableSpec table) {
        for (int selectorIndex = 0;
             selectorIndex < table.selectors.size();
             selectorIndex++) {
            SelectorSpec selector = table.selectors.get(selectorIndex);
            String selectorIdentity =
                    selector.kind + ":" + selector.name;
            out.append("  private static final class ExactAccess")
                    .append(selectorIndex)
                    .append(" implements CandidateIndexAccess<Binding>")
                    .append("unique".equals(selector.kind)
                            ? ",PointIndexAccess<Binding>" : "")
                    .append("{\n")
                    .append("    private final long hash;private final String identity;");
            for (int leafIndex = 0;
                 leafIndex < selector.leaves.size();
                 leafIndex++) {
                out.append("private final ")
                        .append(selector.leaves.get(leafIndex).storageType)
                        .append(" sourceLeaf").append(leafIndex).append(';');
            }
            out.append("\n    private ExactAccess")
                    .append(selectorIndex).append("(long hash");
            for (int leafIndex = 0;
                 leafIndex < selector.leaves.size();
                 leafIndex++) {
                out.append(',').append(
                        selector.leaves.get(leafIndex).storageType)
                        .append(" sourceLeaf").append(leafIndex);
            }
            out.append("){this.hash=hash;StringBuilder value=new StringBuilder(")
                    .append(q(selectorIdentity)).append(");");
            for (int leafIndex = 0;
                 leafIndex < selector.leaves.size();
                 leafIndex++) {
                SelectorLeafSpec leaf = selector.leaves.get(leafIndex);
                out.append("this.sourceLeaf").append(leafIndex)
                        .append("=sourceLeaf").append(leafIndex)
                        .append(";value.append('|').append(")
                        .append(q(leaf.storageType)).append(").append('|')")
                        .append(".append(identityComponent(sourceLeaf")
                        .append(leafIndex).append("));");
            }
            out.append("identity=value.toString();}\n")
                    .append("    public int group(Binding binding){return binding.table.selector")
                    .append(selectorIndex).append("SourceGroup(hash");
            appendSourceLeafArguments(out, selector.leaves.size());
            out.append(");}\n")
                    .append("    public int size(Binding binding,int group){return binding.table.selector")
                    .append(selectorIndex).append("SourceSize(group);}\n")
                    .append("    public int first(Binding binding,int group){return binding.table.selector")
                    .append(selectorIndex).append("SourceFirst(group);}\n")
                    .append("    public int next(Binding binding,int currentIndex){return binding.table.selector")
                    .append(selectorIndex).append("SourceNext(currentIndex);}\n")
                    .append("unique".equals(selector.kind)
                            ? "    public int index(Binding binding){int group=group(binding);return group<0?-1:first(binding,group);}\n"
                            : "")
                    .append("    public String identity(){return identity;}\n")
                    .append("  }\n\n");
        }
    }

    private static void appendSourceExpressions(
            SourceBuilder out, TableSpec table) {
        int ordinal = 0;
        for (FieldSpec field : table.fields) {
            if (field.valueBacked()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    appendExpressionMethod(
                            out,
                            leaf.stem(field),
                            field.logicalName + "." + leaf.logicalName,
                            expressionType(
                                    leaf.primitive, leaf.enumType, null),
                            field.optional,
                            ordinal++);
                }
            } else {
                appendExpressionMethod(
                        out,
                        field.javaName,
                        field.logicalName,
                        expressionType(
                                field.primitive, field.enumType, null),
                        field.optional,
                        ordinal++);
            }
        }
    }

    private static void appendExpressionMethod(
            SourceBuilder out,
            String method,
            String path,
            String expressionType,
            boolean optional,
            int ordinal) {
        out.append("    public ");
        out.append(expressionType).append("Expression<Binding>");
        out.append(' ').append(method).append("(){return GeneratedDataFlow.")
                .append(optional ? "optional" : "required")
                .append(expressionType).append("(source,")
                .append(ordinal).append(',').append(q(path)).append(");}\n");
    }

    private static void appendBindingAccess(
            SourceBuilder out, TableSpec table) {
        out.append("    public boolean isPresent(int column,int index){switch(column){");
        int ordinal = 0;
        for (FieldSpec field : table.fields) {
            if (field.valueBacked()) {
                for (ValueLeafSpec ignored : field.valueLeaves) {
                    out.append("case ").append(ordinal++).append(":return ")
                            .append(field.optional
                                    ? "table." + field.javaName
                                    + "Present(index)" : "true")
                            .append(';');
                }
            } else {
                out.append("case ").append(ordinal++).append(":return ")
                        .append(field.optional
                                ? "table." + field.javaName
                                + "Present(index)" : "true")
                        .append(';');
            }
        }
        out.append("default:throw unsupported(column,\"presence\");}}\n");
        appendCarrierAccess(out, table, "Boolean");
        appendCarrierAccess(out, table, "Long");
        appendCarrierAccess(out, table, "Double");
        appendCarrierAccess(out, table, "String");
    }

    private static void appendCarrierAccess(
            SourceBuilder out, TableSpec table, String carrier) {
        String returnType;
        if ("Boolean".equals(carrier)) {
            returnType = "boolean";
        } else if ("Long".equals(carrier)) {
            returnType = "long";
        } else if ("Double".equals(carrier)) {
            returnType = "double";
        } else {
            returnType = "String";
        }
        out.append("    public ").append(returnType).append(' ')
                .append(Character.toLowerCase(carrier.charAt(0)))
                .append(carrier.substring(1))
                .append("Value(int column,int index){switch(column){");
        int ordinal = 0;
        for (FieldSpec field : table.fields) {
            if (field.valueBacked()) {
                for (ValueLeafSpec leaf : field.valueLeaves) {
                    String type = expressionType(
                            leaf.primitive, leaf.enumType, null);
                    if (carrier.equals(type)) {
                        appendCarrierCase(
                                out,
                                carrier,
                                ordinal,
                                leaf.stem(field),
                                leaf.enumType != null);
                    }
                    ordinal++;
                }
            } else {
                String type = expressionType(
                        field.primitive, field.enumType, null);
                if (carrier.equals(type)) {
                    appendCarrierCase(
                            out,
                            carrier,
                            ordinal,
                            field.javaName,
                            field.enumType != null);
                }
                ordinal++;
            }
        }
        out.append("default:throw unsupported(column,")
                .append(q(carrier.toLowerCase(java.util.Locale.ROOT)))
                .append(");}}\n");
    }

    private static void appendCarrierCase(
            SourceBuilder out,
            String carrier,
            int ordinal,
            String method,
            boolean enumType) {
        out.append("case ").append(ordinal).append(":return ");
        if (enumType) {
            out.append("(long)table.").append(method)
                    .append("Value(index).ordinal();");
            return;
        }
        out.append(cast(carrier))
                .append("table.").append(method)
                .append("Value(index);");
    }

    private static String cast(String carrier) {
        return "Long".equals(carrier) ? "(long)" : "";
    }

    private static String expressionType(
            String primitive, String enumType, String valueType) {
        if (valueType != null) {
            throw new IllegalArgumentException(
                    "compiler-flattened value has no object expression");
        }
        if ("java.lang.String".equals(primitive)) {
            return "String";
        }
        if (enumType != null) {
            return "Long";
        }
        if ("boolean".equals(primitive)) {
            return "Boolean";
        }
        if ("float".equals(primitive) || "double".equals(primitive)) {
            return "Double";
        }
        return "Long";
    }

}
