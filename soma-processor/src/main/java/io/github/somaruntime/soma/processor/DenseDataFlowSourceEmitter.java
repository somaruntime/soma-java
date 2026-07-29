package io.github.somaruntime.soma.processor;

import static io.github.somaruntime.soma.processor.DenseSourceNames.q;
import static io.github.somaruntime.soma.processor.DenseSourceNames.cap;
import static io.github.somaruntime.soma.processor.DenseSelectorCodegenModel.selectorParameters;
import static io.github.somaruntime.soma.processor.DenseSelectorSourceSupport.appendSelectorParameters;
import static io.github.somaruntime.soma.processor.DenseSelectorSourceSupport.appendSourceLeafArguments;
import static io.github.somaruntime.soma.processor.DenseSelectorSourceSupport.bitmapEligible;
import static io.github.somaruntime.soma.processor.DenseSelectorSourceSupport.selectorHashBits;
import static io.github.somaruntime.soma.processor.DenseSelectorSourceSupport.selectorParameterArgument;
import static io.github.somaruntime.soma.processor.DenseSelectorSourceSupport.selectorSuffix;
import static io.github.somaruntime.soma.processor.DenseTableCodegenModel.FieldSpec;
import static io.github.somaruntime.soma.processor.DenseTableCodegenModel.ChildSpec;
import static io.github.somaruntime.soma.processor.DenseTableCodegenModel.SelectorLeafSpec;
import static io.github.somaruntime.soma.processor.DenseTableCodegenModel.SelectorSpec;
import static io.github.somaruntime.soma.processor.DenseTableCodegenModel.TableSpec;
import static io.github.somaruntime.soma.processor.DenseTableCodegenModel.ValueLeafSpec;

import io.github.somaruntime.soma.processor.DenseSelectorCodegenModel.SelectorParameter;

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
        out.append("import io.github.somaruntime.soma.dataflow.BooleanExpression;\n")
                .append("import io.github.somaruntime.soma.dataflow.CandidateFlow;\n")
                .append("import io.github.somaruntime.soma.dataflow.CallbackDeliveryDefinition;\n")
                .append("import io.github.somaruntime.soma.dataflow.DataFlowDefinition;\n")
                .append("import io.github.somaruntime.soma.dataflow.DateExpression;\n")
                .append("import io.github.somaruntime.soma.dataflow.DoubleExpression;\n")
                .append("import io.github.somaruntime.soma.dataflow.EnumExpression;\n")
                .append("import io.github.somaruntime.soma.dataflow.ExpandedFlow;\n")
                .append("import io.github.somaruntime.soma.dataflow.GeneratedDataFlow;\n")
                .append("import io.github.somaruntime.soma.dataflow.InstantExpression;\n")
                .append("import io.github.somaruntime.soma.dataflow.LongExpression;\n")
                .append("import io.github.somaruntime.soma.dataflow.StringExpression;\n")
                .append("import io.github.somaruntime.soma.dataflow.TimeExpression;\n")
                .append("import io.github.somaruntime.soma.dataflow.ParameterSlot;\n")
                .append("import io.github.somaruntime.soma.dataflow.PointFlow;\n")
                .append("import io.github.somaruntime.soma.dataflow.SourceSlot;\n")
                .append("import io.github.somaruntime.soma.dataflow.generated.CandidateDeliveryAccess;\n")
                .append("import io.github.somaruntime.soma.dataflow.generated.CandidateDeliverySession;\n")
                .append("import io.github.somaruntime.soma.dataflow.generated.CandidateIndexAccess;\n")
                .append("import io.github.somaruntime.soma.dataflow.generated.CandidateLongEqualityAccess;\n")
                .append("import io.github.somaruntime.soma.dataflow.generated.CandidateEffectAccess;\n")
                .append("import io.github.somaruntime.soma.dataflow.generated.CandidateMaterializationAccess;\n")
                .append("import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;\n")
                .append("import io.github.somaruntime.soma.dataflow.generated.OwnedChildAccess;\n")
                .append("import io.github.somaruntime.soma.dataflow.generated.PointIndexAccess;\n")
                .append("import io.github.somaruntime.soma.dataflow.generated.SnapshotGatherAccess;\n")
                .append("import io.github.somaruntime.soma.runtime.IndexSnapshot;\n")
                .append("import io.github.somaruntime.soma.runtime.MaterializationBudget;\n")
                .append("import io.github.somaruntime.soma.runtime.RemoveResult;\n")
                .append("import io.github.somaruntime.soma.runtime.UpdateResult;\n")
                .append("import io.github.somaruntime.soma.runtime.generated.KeyCanonicalization;\n")
                .append("import io.github.somaruntime.soma.runtime.generated.RuntimeFailures;\n")
                .append("import io.github.somaruntime.soma.runtime.generated.RuntimeCompatibility;\n\n")
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
        boolean hasBitmapResolver = false;
        for (SelectorSpec selector : table.selectors) {
            if (bitmapEligible(table, selector)) {
                hasBitmapResolver = true;
                break;
            }
        }
        if (hasBitmapResolver) {
            out.append("  private static final CandidateIndexAccess<Binding> MISSING_LONG_EXACT_ACCESS=new MissingLongExactAccess();\n")
                    .append("  private static final class MissingLongExactAccess implements CandidateIndexAccess<Binding>{public int group(Binding binding){return -1;}public int size(Binding binding,int group){return 0;}public int first(Binding binding,int group){return -1;}public int next(Binding binding,int currentIndex){return -1;}public String identity(){return \"missing-long-equality\";}}\n\n");
        }
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
                    .append("    public boolean bitmap(Binding binding,int group){return binding.table.selector")
                    .append(selectorIndex).append("SourceBitmap();}\n")
                    .append("    public int bitmapWordCount(Binding binding,int group){return binding.table.selector")
                    .append(selectorIndex).append("SourceBitmapWords();}\n")
                    .append("    public long bitmapWord(Binding binding,int group,int word){return binding.table.selector")
                    .append(selectorIndex).append("SourceBitmapWord(group,word);}\n")
                    .append("unique".equals(selector.kind)
                            ? "    public int index(Binding binding){int group=group(binding);return group<0?-1:first(binding,group);}\n"
                            : "")
                    .append("    public String identity(){return identity;}\n")
                    .append("  }\n\n");
            if (bitmapEligible(table, selector)) {
                SelectorLeafSpec leaf = selector.leaves.get(0);
                out.append("  private static final class LongEqualityAccess")
                        .append(selectorIndex)
                        .append(" implements CandidateLongEqualityAccess<Binding>{\n")
                        .append("    public CandidateIndexAccess<Binding> equalTo(long value){");
                if ("byte".equals(leaf.storageType)) {
                    out.append("if(value<Byte.MIN_VALUE||value>Byte.MAX_VALUE)return MISSING_LONG_EXACT_ACCESS;");
                } else if ("short".equals(leaf.storageType)) {
                    out.append("if(value<Short.MIN_VALUE||value>Short.MAX_VALUE)return MISSING_LONG_EXACT_ACCESS;");
                } else if ("int".equals(leaf.storageType)) {
                    out.append("if(value<Integer.MIN_VALUE||value>Integer.MAX_VALUE)return MISSING_LONG_EXACT_ACCESS;");
                }
                out.append(leaf.storageType).append(" sourceLeaf0=")
                        .append("long".equals(leaf.storageType) ? "value" : "(" + leaf.storageType + ")value")
                        .append(";long hash=1469598103934665603L;hash=(hash^")
                        .append(selectorHashBits(leaf.storageType, "sourceLeaf0"))
                        .append(")*1099511628211L;return new ExactAccess")
                        .append(selectorIndex)
                        .append("(hash,sourceLeaf0);}\n")
                        .append("    public String identity(){return ")
                        .append(q(selectorIdentity + ":long-equality"))
                        .append(";}\n  }\n\n");
            }
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
                            expressionKind(
                                    leaf.primitive,
                                    leaf.enumType,
                                    leaf.semantic,
                                    null),
                            leaf.enumType,
                            field.optional,
                            ordinal++,
                            bitmapSelectorIndex(
                                    table,
                                    field.logicalName + "."
                                            + leaf.logicalName));
                }
            } else {
                appendExpressionMethod(
                        out,
                        field.javaName,
                        field.logicalName,
                        expressionKind(
                                field.primitive,
                                field.enumType,
                                field.semantic,
                                null),
                        field.enumType,
                        field.optional,
                        ordinal++,
                        bitmapSelectorIndex(table, field.logicalName));
            }
        }
    }

    private static void appendExpressionMethod(
            SourceBuilder out,
            String method,
            String path,
            String expressionKind,
            String enumType,
            boolean optional,
            int ordinal,
            int bitmapSelector) {
        boolean indexed = !optional
                && bitmapSelector >= 0
                && ("Long".equals(expressionKind)
                || "Enum".equals(expressionKind)
                || "Date".equals(expressionKind)
                || "Time".equals(expressionKind)
                || "Instant".equals(expressionKind));
        out.append("    public ");
        out.append(expressionKind).append("Expression<Binding");
        if ("Enum".equals(expressionKind)) {
            out.append(',').append(enumType);
        }
        out.append('>');
        out.append(' ').append(method).append("(){return GeneratedDataFlow.")
                .append(optional
                        ? "optional"
                        : indexed ? "requiredIndexed" : "required")
                .append(expressionKind).append("(source,")
                .append(ordinal).append(',').append(q(path));
        if ("Enum".equals(expressionKind)) {
            out.append(',').append(enumType).append(".class");
        }
        if (indexed) {
            out.append(",new LongEqualityAccess")
                    .append(bitmapSelector).append("()");
        }
        out.append(");}\n");
    }

    private static int bitmapSelectorIndex(
            TableSpec table, String path) {
        for (int index = 0; index < table.selectors.size(); index++) {
            SelectorSpec selector = table.selectors.get(index);
            if (bitmapEligible(table, selector)
                    && selector.leaves.get(0).path.equals(path)) {
                return index;
            }
        }
        return -1;
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
                    String type = carrierType(
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
                String type = carrierType(
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

    private static String expressionKind(
            String primitive,
            String enumType,
            String semantic,
            String valueType) {
        if (valueType != null) {
            throw new IllegalArgumentException(
                    "compiler-flattened value has no object expression");
        }
        if (enumType != null) return "Enum";
        if ("DATE".equals(semantic)) return "Date";
        if ("TIME".equals(semantic)) return "Time";
        if ("DATE_TIME".equals(semantic)) return "Instant";
        return carrierType(primitive, enumType, valueType);
    }

    private static String carrierType(
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
