package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.*;
import java.util.LinkedHashSet;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

/** Unboxed primitive pipelines rooted in a binary relation. */
final class GeneratedRelationPrimitivePipeline {

    private enum Kind { INT, LONG, DOUBLE }
    private enum StageKind { FILTER, MAP, CONVERT, DISTINCT, SORTED, SKIP, LIMIT }

    private GeneratedRelationPrimitivePipeline() {}

    static SomaIntStream intStream(
            GeneratedRelation relation,
            GeneratedCallbacks.RowToIntMapper mapper) {
        return new IntPipeline(RelationPrimitivePipelineCapture.direct(
                relation, Kind.INT, mapper));
    }

    static SomaLongStream longStream(
            GeneratedRelation relation,
            GeneratedCallbacks.RowToLongMapper mapper) {
        return new LongPipeline(RelationPrimitivePipelineCapture.direct(
                relation, Kind.LONG, mapper));
    }

    static SomaDoubleStream doubleStream(
            GeneratedRelation relation,
            GeneratedCallbacks.RowToDoubleMapper mapper) {
        return new DoublePipeline(RelationPrimitivePipelineCapture.direct(
                relation, Kind.DOUBLE, mapper));
    }

    static SomaIntStream intStream(
            GeneratedRelationMappedPipeline.RelationMappedPipelineCapture mapped,
            SomaToIntFunction<Object> mapper) {
        return new IntPipeline(RelationPrimitivePipelineCapture.mapped(
                mapped, Kind.INT, mapper));
    }

    static SomaLongStream longStream(
            GeneratedRelationMappedPipeline.RelationMappedPipelineCapture mapped,
            SomaToLongFunction<Object> mapper) {
        return new LongPipeline(RelationPrimitivePipelineCapture.mapped(
                mapped, Kind.LONG, mapper));
    }

    static SomaDoubleStream doubleStream(
            GeneratedRelationMappedPipeline.RelationMappedPipelineCapture mapped,
            SomaToDoubleFunction<Object> mapper) {
        return new DoublePipeline(RelationPrimitivePipelineCapture.mapped(
                mapped, Kind.DOUBLE, mapper));
    }

    private abstract static class Pipeline {
        final RelationPrimitivePipelineCapture capture;
        private final AtomicBoolean consumed = new AtomicBoolean();
        Pipeline(RelationPrimitivePipelineCapture capture) {
            this.capture = capture;
        }
        void claim() {
            if (!consumed.compareAndSet(false, true)) throw SomaFailures.failure(
                    SomaFailureCode.PIPELINE_ALREADY_CONSUMED, SomaOperation.QUERY,
                    "linked primitive relation pipeline has already been consumed", new Object());
        }
        static void require(Object value, String category) {
            if (value == null) throw SomaFailures.invalid(
                    SomaOperation.QUERY, category + " is null");
        }
        static void requireCount(long value, String category) {
            if (value < 0L) throw SomaFailures.invalid(
                    SomaOperation.QUERY, category + " is negative");
        }
    }

    private static final class IntPipeline extends Pipeline implements SomaIntStream {
        IntPipeline(RelationPrimitivePipelineCapture capture) { super(capture); }
        @Override public SomaIntStream parallel(){claim();return new IntPipeline(capture.parallel());}
        @Override public SomaIntStream filter(SomaIntPredicate p) { require(p,"predicate");claim();return new IntPipeline(capture.add(Stage.of(StageKind.FILTER,Kind.INT,p,0))); }
        @Override public SomaIntStream map(SomaIntUnaryOperator m) { require(m,"mapper");claim();return new IntPipeline(capture.add(Stage.of(StageKind.MAP,Kind.INT,m,0))); }
        @Override public SomaLongStream mapToLong(SomaIntToLongFunction m) { require(m,"mapper");claim();return new LongPipeline(capture.add(Stage.of(StageKind.CONVERT,Kind.LONG,m,0))); }
        @Override public SomaDoubleStream mapToDouble(SomaIntToDoubleFunction m) { require(m,"mapper");claim();return new DoublePipeline(capture.add(Stage.of(StageKind.CONVERT,Kind.DOUBLE,m,0))); }
        @Override public SomaIntStream distinct(){claim();return new IntPipeline(capture.add(Stage.of(StageKind.DISTINCT,Kind.INT,null,0)));}
        @Override public SomaIntStream sorted(){claim();return new IntPipeline(capture.add(Stage.of(StageKind.SORTED,Kind.INT,null,0)));}
        @Override public SomaIntStream top(long n){requireCount(n,"top");claim();return new IntPipeline(capture.add(Stage.of(StageKind.SORTED,Kind.INT,null,0)).add(Stage.of(StageKind.LIMIT,Kind.INT,null,n)));}
        @Override public SomaIntStream skip(long n){requireCount(n,"skip");claim();return new IntPipeline(capture.add(Stage.of(StageKind.SKIP,Kind.INT,null,n)));}
        @Override public SomaIntStream limit(long n){requireCount(n,"limit");claim();return new IntPipeline(capture.add(Stage.of(StageKind.LIMIT,Kind.INT,null,n)));}
        @Override public long count(){claim();return values(capture).size;}
        @Override public boolean anyMatch(SomaIntPredicate p){require(p,"predicate");claim();Buffer b=values(capture);for(int i=0;i<b.size;i++)if(testInt(p,(int)b.values[i]))return true;return false;}
        @Override public boolean allMatch(SomaIntPredicate p){require(p,"predicate");claim();Buffer b=values(capture);for(int i=0;i<b.size;i++)if(!testInt(p,(int)b.values[i]))return false;return true;}
        @Override public boolean noneMatch(SomaIntPredicate p){require(p,"predicate");claim();Buffer b=values(capture);for(int i=0;i<b.size;i++)if(testInt(p,(int)b.values[i]))return false;return true;}
        @Override public OptionalInt findFirst(){claim();Buffer b=values(capture);return b.size==0?OptionalInt.empty():OptionalInt.of((int)b.values[0]);}
        @Override public OptionalInt min(){claim();Buffer b=values(capture);return b.size==0?OptionalInt.empty():OptionalInt.of((int)extremum(b,Kind.INT,false));}
        @Override public OptionalInt max(){claim();Buffer b=values(capture);return b.size==0?OptionalInt.empty():OptionalInt.of((int)extremum(b,Kind.INT,true));}
        @Override public long sum(){claim();return integralSum(capture).longValue(new Object());}
        @Override public OptionalDouble average(){claim();Buffer b=values(capture);if(b.size==0)return OptionalDouble.empty();return OptionalDouble.of(integral(b).doubleValue()/b.size);}
        @Override public SomaLongSummary summaryStatistics(){claim();return longSummary(values(capture),Kind.INT);}
        @Override public void forEach(SomaIntConsumer a){require(a,"action");claim();Buffer b=values(capture);for(int i=0;i<b.size;i++)acceptInt(a,(int)b.values[i]);}
        @Override public void forEachOrdered(SomaIntConsumer a){forEach(a);}
        @Override public int[] toArray(){claim();Buffer b=values(capture);int[] r=new int[b.size];for(int i=0;i<b.size;i++)r[i]=(int)b.values[i];return r;}
        @Override public String _explain(){claim();return explain(capture);}
    }

    private static final class LongPipeline extends Pipeline implements SomaLongStream {
        LongPipeline(RelationPrimitivePipelineCapture capture) { super(capture); }
        @Override public SomaLongStream parallel(){claim();return new LongPipeline(capture.parallel());}
        @Override public SomaLongStream filter(SomaLongPredicate p){require(p,"predicate");claim();return new LongPipeline(capture.add(Stage.of(StageKind.FILTER,Kind.LONG,p,0)));}
        @Override public SomaLongStream map(SomaLongUnaryOperator m){require(m,"mapper");claim();return new LongPipeline(capture.add(Stage.of(StageKind.MAP,Kind.LONG,m,0)));}
        @Override public SomaIntStream mapToInt(SomaLongToIntFunction m){require(m,"mapper");claim();return new IntPipeline(capture.add(Stage.of(StageKind.CONVERT,Kind.INT,m,0)));}
        @Override public SomaDoubleStream mapToDouble(SomaLongToDoubleFunction m){require(m,"mapper");claim();return new DoublePipeline(capture.add(Stage.of(StageKind.CONVERT,Kind.DOUBLE,m,0)));}
        @Override public SomaLongStream distinct(){claim();return new LongPipeline(capture.add(Stage.of(StageKind.DISTINCT,Kind.LONG,null,0)));}
        @Override public SomaLongStream sorted(){claim();return new LongPipeline(capture.add(Stage.of(StageKind.SORTED,Kind.LONG,null,0)));}
        @Override public SomaLongStream top(long n){requireCount(n,"top");claim();return new LongPipeline(capture.add(Stage.of(StageKind.SORTED,Kind.LONG,null,0)).add(Stage.of(StageKind.LIMIT,Kind.LONG,null,n)));}
        @Override public SomaLongStream skip(long n){requireCount(n,"skip");claim();return new LongPipeline(capture.add(Stage.of(StageKind.SKIP,Kind.LONG,null,n)));}
        @Override public SomaLongStream limit(long n){requireCount(n,"limit");claim();return new LongPipeline(capture.add(Stage.of(StageKind.LIMIT,Kind.LONG,null,n)));}
        @Override public long count(){claim();return values(capture).size;}
        @Override public boolean anyMatch(SomaLongPredicate p){require(p,"predicate");claim();Buffer b=values(capture);for(int i=0;i<b.size;i++)if(testLong(p,b.values[i]))return true;return false;}
        @Override public boolean allMatch(SomaLongPredicate p){require(p,"predicate");claim();Buffer b=values(capture);for(int i=0;i<b.size;i++)if(!testLong(p,b.values[i]))return false;return true;}
        @Override public boolean noneMatch(SomaLongPredicate p){require(p,"predicate");claim();Buffer b=values(capture);for(int i=0;i<b.size;i++)if(testLong(p,b.values[i]))return false;return true;}
        @Override public OptionalLong findFirst(){claim();Buffer b=values(capture);return b.size==0?OptionalLong.empty():OptionalLong.of(b.values[0]);}
        @Override public OptionalLong min(){claim();Buffer b=values(capture);return b.size==0?OptionalLong.empty():OptionalLong.of(extremum(b,Kind.LONG,false));}
        @Override public OptionalLong max(){claim();Buffer b=values(capture);return b.size==0?OptionalLong.empty():OptionalLong.of(extremum(b,Kind.LONG,true));}
        @Override public long sum(){claim();return integralSum(capture).longValue(new Object());}
        @Override public OptionalDouble average(){claim();Buffer b=values(capture);if(b.size==0)return OptionalDouble.empty();return OptionalDouble.of(integral(b).doubleValue()/b.size);}
        @Override public SomaLongSummary summaryStatistics(){claim();return longSummary(values(capture),Kind.LONG);}
        @Override public void forEach(SomaLongConsumer a){require(a,"action");claim();Buffer b=values(capture);for(int i=0;i<b.size;i++)acceptLong(a,b.values[i]);}
        @Override public void forEachOrdered(SomaLongConsumer a){forEach(a);}
        @Override public long[] toArray(){claim();Buffer b=values(capture);long[] r=new long[b.size];System.arraycopy(b.values,0,r,0,b.size);return r;}
        @Override public String _explain(){claim();return explain(capture);}
    }

    private static final class DoublePipeline extends Pipeline implements SomaDoubleStream {
        DoublePipeline(RelationPrimitivePipelineCapture capture){super(capture);}
        @Override public SomaDoubleStream parallel(){claim();return new DoublePipeline(capture.parallel());}
        @Override public SomaDoubleStream filter(SomaDoublePredicate p){require(p,"predicate");claim();return new DoublePipeline(capture.add(Stage.of(StageKind.FILTER,Kind.DOUBLE,p,0)));}
        @Override public SomaDoubleStream map(SomaDoubleUnaryOperator m){require(m,"mapper");claim();return new DoublePipeline(capture.add(Stage.of(StageKind.MAP,Kind.DOUBLE,m,0)));}
        @Override public SomaIntStream mapToInt(SomaDoubleToIntFunction m){require(m,"mapper");claim();return new IntPipeline(capture.add(Stage.of(StageKind.CONVERT,Kind.INT,m,0)));}
        @Override public SomaLongStream mapToLong(SomaDoubleToLongFunction m){require(m,"mapper");claim();return new LongPipeline(capture.add(Stage.of(StageKind.CONVERT,Kind.LONG,m,0)));}
        @Override public SomaDoubleStream distinct(){claim();return new DoublePipeline(capture.add(Stage.of(StageKind.DISTINCT,Kind.DOUBLE,null,0)));}
        @Override public SomaDoubleStream sorted(){claim();return new DoublePipeline(capture.add(Stage.of(StageKind.SORTED,Kind.DOUBLE,null,0)));}
        @Override public SomaDoubleStream top(long n){requireCount(n,"top");claim();return new DoublePipeline(capture.add(Stage.of(StageKind.SORTED,Kind.DOUBLE,null,0)).add(Stage.of(StageKind.LIMIT,Kind.DOUBLE,null,n)));}
        @Override public SomaDoubleStream skip(long n){requireCount(n,"skip");claim();return new DoublePipeline(capture.add(Stage.of(StageKind.SKIP,Kind.DOUBLE,null,n)));}
        @Override public SomaDoubleStream limit(long n){requireCount(n,"limit");claim();return new DoublePipeline(capture.add(Stage.of(StageKind.LIMIT,Kind.DOUBLE,null,n)));}
        @Override public long count(){claim();return values(capture).size;}
        @Override public boolean anyMatch(SomaDoublePredicate p){require(p,"predicate");claim();Buffer b=values(capture);for(int i=0;i<b.size;i++)if(testDouble(p,decode(b.values[i])))return true;return false;}
        @Override public boolean allMatch(SomaDoublePredicate p){require(p,"predicate");claim();Buffer b=values(capture);for(int i=0;i<b.size;i++)if(!testDouble(p,decode(b.values[i])))return false;return true;}
        @Override public boolean noneMatch(SomaDoublePredicate p){require(p,"predicate");claim();Buffer b=values(capture);for(int i=0;i<b.size;i++)if(testDouble(p,decode(b.values[i])))return false;return true;}
        @Override public OptionalDouble findFirst(){claim();Buffer b=values(capture);return b.size==0?OptionalDouble.empty():OptionalDouble.of(decode(b.values[0]));}
        @Override public OptionalDouble min(){claim();Buffer b=values(capture);return b.size==0?OptionalDouble.empty():OptionalDouble.of(decode(extremum(b,Kind.DOUBLE,false)));}
        @Override public OptionalDouble max(){claim();Buffer b=values(capture);return b.size==0?OptionalDouble.empty():OptionalDouble.of(decode(extremum(b,Kind.DOUBLE,true)));}
        @Override public double sum(){claim();Buffer b=values(capture);return floatingSum(b);}
        @Override public OptionalDouble average(){claim();Buffer b=values(capture);return b.size==0?OptionalDouble.empty():OptionalDouble.of(floatingSum(b)/b.size);}
        @Override public SomaDoubleSummary summaryStatistics(){claim();return doubleSummary(values(capture));}
        @Override public void forEach(SomaDoubleConsumer a){require(a,"action");claim();Buffer b=values(capture);for(int i=0;i<b.size;i++)acceptDouble(a,decode(b.values[i]));}
        @Override public void forEachOrdered(SomaDoubleConsumer a){forEach(a);}
        @Override public double[] toArray(){claim();Buffer b=values(capture);double[] r=new double[b.size];for(int i=0;i<b.size;i++)r[i]=decode(b.values[i]);return r;}
        @Override public String _explain(){claim();return explain(capture);}
    }

    private static Buffer values(final RelationPrimitivePipelineCapture capture) {
        final CanonicalRelationPhysicalDownstream downstream = physical(capture);
        Buffer root;
        if (capture.relation != null) {
            root = capture.relation.terminal(
                    CanonicalRelationOperation.TerminalKind.PRIMITIVE_SOURCE,
                    new GeneratedRelation.PairWork<Buffer>() {
                @Override public Buffer run(final GeneratedRelation.RelationBinding binding) {
                    int upper=RowExecutionSupport.arrayLength(capture.relation.outputUpperBound(binding),binding.provenance);
                    final Buffer out=new Buffer(upper);
                    capture.relation.visitBound(binding,true,new GeneratedRelation.PairVisitor(){
                        @Override public boolean visit(int l,int r){out.add(directValue(capture));return true;}
                    });
                    return out;
                    }
            },true,24L,downstream);
        } else {
            java.util.List<Object> mapped=GeneratedRelationMappedPipeline.materialize(capture.mapped);
            root=new Buffer(mapped.size());
            for(Object value:mapped)root.add(mappedValue(capture,value));
        }
        applyStages(root,capture,downstream);
        return root;
    }

    /**
     * 融合常见的 Relation 直接整数求和路径。直接 mapper 不包含 primitive stage，
     * 因此不存在有状态或顺序 barrier；同一个精确 128-bit accumulator 可以按
     * canonical relation encounter order 消费结果，无需先物化整个 Join 输出。
     */
    private static Signed128Accumulator integralSum(final RelationPrimitivePipelineCapture capture) {
        if (capture.relation == null || capture.stages.length != 0
                || (capture.rootKind != Kind.INT && capture.rootKind != Kind.LONG)) {
            return integral(values(capture));
        }
        return capture.relation.terminal(
                CanonicalRelationOperation.TerminalKind.PRIMITIVE_SOURCE,
                new GeneratedRelation.PairWork<Signed128Accumulator>() {
                    @Override
                    public Signed128Accumulator run(
                            GeneratedRelation.RelationBinding binding) {
                        final Signed128Accumulator accumulator =
                                new Signed128Accumulator();
                        capture.relation.visitBound(
                                binding,
                                true,
                                new GeneratedRelation.PairVisitor() {
                                    @Override
                                    public boolean visit(int left, int right) {
                                        accumulator.add(directValue(capture));
                                        return true;
                                    }
                                });
                        return accumulator;
                    }
                },
                true,
                0L,
                physical(capture));
    }

    private static long directValue(RelationPrimitivePipelineCapture capture) {
        CallbackExecutionScope.enter();
        try {
            if(capture.rootKind==Kind.INT)return ((GeneratedCallbacks.RowToIntMapper)capture.rootMapper).applyAsInt();
            if(capture.rootKind==Kind.LONG)return ((GeneratedCallbacks.RowToLongMapper)capture.rootMapper).applyAsLong();
            return encode(((GeneratedCallbacks.RowToDoubleMapper)capture.rootMapper).applyAsDouble());
        } catch(Exception failure){throw SomaFailures.callbackFailure(SomaOperation.QUERY,failure,new Object());}
        finally { CallbackExecutionScope.exit(); }
    }

    @SuppressWarnings("unchecked")
    private static long mappedValue(RelationPrimitivePipelineCapture capture,Object value){CallbackExecutionScope.enter();try{
        // RelationPrimitivePipelineCapture seals rootKind and rootMapper as one tagged value.
        if(capture.rootKind==Kind.INT)return ((SomaToIntFunction<Object>)capture.rootMapper).applyAsInt(value);
        if(capture.rootKind==Kind.LONG)return ((SomaToLongFunction<Object>)capture.rootMapper).applyAsLong(value);
        return encode(((SomaToDoubleFunction<Object>)capture.rootMapper).applyAsDouble(value));
    }catch(Exception failure){throw SomaFailures.callbackFailure(SomaOperation.QUERY,failure,new Object());}finally{CallbackExecutionScope.exit();}}

    private static void applyStages(
            Buffer b,
            RelationPrimitivePipelineCapture capture,
            CanonicalRelationPhysicalDownstream downstream) {
        if (downstream.kernels.length != capture.stages.length + 1
                || downstream.kernels[0]
                        != CanonicalRelationPhysicalDownstream.Kernel.PRIMITIVE_MAP) {
            throw new AssertionError("primitive Relation topology drift");
        }
        Kind current = capture.rootKind;
        for (int index = 0; index < capture.stages.length; index++) {
            Stage stage = capture.stages[index];
            switch (downstream.kernels[index + 1]) {
                case PRIMITIVE_FILTER: {
                    int output = 0;
                    for (int input = 0; input < b.size; input++) {
                        if (test(current, stage.callback, b.values[input])) {
                            b.values[output++] = b.values[input];
                        }
                    }
                    b.size = output;
                    break;
                }
                case PRIMITIVE_MAP:
                    for (int input = 0; input < b.size; input++) {
                        b.values[input] = map(
                                current, stage.output,
                                stage.callback, b.values[input]);
                    }
                    current = stage.output;
                    break;
                case PRIMITIVE_HASH: {
                    LinkedHashSet<Long> set = new LinkedHashSet<Long>();
                    for (int input = 0; input < b.size; input++) {
                        set.add(canonical(current, b.values[input]));
                    }
                    int output = 0;
                    for (Long value : set) b.values[output++] = value;
                    b.size = output;
                    break;
                }
                case PRIMITIVE_STABLE_SORT:
                    sort(b, current);
                    break;
                case PRIMITIVE_SLICE:
                    if (stage.kind == StageKind.SKIP) {
                        int count = (int) Math.min((long) b.size, stage.count);
                        System.arraycopy(
                                b.values, count, b.values, 0, b.size - count);
                        b.size -= count;
                    } else if (stage.count < b.size) {
                        b.size = (int) stage.count;
                    }
                    break;
                default:
                    throw new AssertionError("unknown primitive Relation kernel");
            }
        }
    }

    private static CanonicalRelationPhysicalDownstream physical(
            RelationPrimitivePipelineCapture capture) {
        CanonicalRelationPhysicalDownstream.Kernel[] kernels =
                new CanonicalRelationPhysicalDownstream.Kernel[
                        capture.stages.length + 1];
        kernels[0] = CanonicalRelationPhysicalDownstream.Kernel.PRIMITIVE_MAP;
        int breakers = 0;
        for (int index = 0; index < capture.stages.length; index++) {
            switch (capture.stages[index].kind) {
                case FILTER:
                    kernels[index + 1] = CanonicalRelationPhysicalDownstream.Kernel
                            .PRIMITIVE_FILTER;
                    break;
                case MAP:
                case CONVERT:
                    kernels[index + 1] = CanonicalRelationPhysicalDownstream.Kernel
                            .PRIMITIVE_MAP;
                    break;
                case DISTINCT:
                    kernels[index + 1] = CanonicalRelationPhysicalDownstream.Kernel
                            .PRIMITIVE_HASH;
                    breakers++;
                    break;
                case SORTED:
                    kernels[index + 1] = CanonicalRelationPhysicalDownstream.Kernel
                            .PRIMITIVE_STABLE_SORT;
                    breakers++;
                    break;
                case SKIP:
                case LIMIT:
                    kernels[index + 1] = CanonicalRelationPhysicalDownstream.Kernel
                            .PRIMITIVE_SLICE;
                    break;
                default:
                    throw new AssertionError("unknown primitive Relation stage");
            }
        }
        return CanonicalRelationPhysicalDownstream.primitive(kernels, breakers);
    }

    private static boolean test(Kind k,Object c,long raw){CallbackExecutionScope.enter();try{if(k==Kind.INT)return ((SomaIntPredicate)c).test((int)raw);if(k==Kind.LONG)return ((SomaLongPredicate)c).test(raw);return ((SomaDoublePredicate)c).test(decode(raw));}catch(Exception e){throw SomaFailures.callbackFailure(SomaOperation.QUERY,e,new Object());}finally{CallbackExecutionScope.exit();}}
    private static long map(Kind in,Kind out,Object c,long raw){CallbackExecutionScope.enter();try{
        if(in==Kind.INT){int v=(int)raw;if(out==Kind.INT)return ((SomaIntUnaryOperator)c).applyAsInt(v);if(out==Kind.LONG)return ((SomaIntToLongFunction)c).applyAsLong(v);return encode(((SomaIntToDoubleFunction)c).applyAsDouble(v));}
        if(in==Kind.LONG){if(out==Kind.INT)return ((SomaLongToIntFunction)c).applyAsInt(raw);if(out==Kind.LONG)return ((SomaLongUnaryOperator)c).applyAsLong(raw);return encode(((SomaLongToDoubleFunction)c).applyAsDouble(raw));}
        double v=decode(raw);if(out==Kind.INT)return ((SomaDoubleToIntFunction)c).applyAsInt(v);if(out==Kind.LONG)return ((SomaDoubleToLongFunction)c).applyAsLong(v);return encode(((SomaDoubleUnaryOperator)c).applyAsDouble(v));
    }catch(Exception e){throw SomaFailures.callbackFailure(SomaOperation.QUERY,e,new Object());}finally{CallbackExecutionScope.exit();}}

    private static long canonical(Kind k,long raw){return k==Kind.DOUBLE?Double.doubleToLongBits(decode(raw)):raw;}
    private static void sort(Buffer b,Kind k){long[] scratch=new long[b.size];merge(b.values,scratch,0,b.size,k);}
    private static void merge(long[] a,long[] s,int f,int t,Kind k){if(t-f<2)return;int m=(f+t)>>>1;merge(a,s,f,m,k);merge(a,s,m,t,k);int l=f,r=m,o=f;while(l<m&&r<t)s[o++]=compare(k,a[l],a[r])<=0?a[l++]:a[r++];while(l<m)s[o++]=a[l++];while(r<t)s[o++]=a[r++];System.arraycopy(s,f,a,f,t-f);}
    private static int compare(Kind k,long a,long b){if(k==Kind.INT)return Integer.compare((int)a,(int)b);if(k==Kind.LONG)return Long.compare(a,b);return Double.compare(decode(a),decode(b));}
    private static long extremum(Buffer b,Kind k,boolean max){long v=b.values[0];for(int i=1;i<b.size;i++){int c=compare(k,b.values[i],v);if(max?c>0:c<0)v=b.values[i];}return v;}
    private static Signed128Accumulator integral(Buffer b){Signed128Accumulator a=new Signed128Accumulator();for(int i=0;i<b.size;i++)a.add(b.values[i]);return a;}
    private static SomaLongSummary longSummary(Buffer b,Kind k){if(b.size==0)return SomaSharedSecrets.longSummaryAccess().create(0,0,0,0,0);long min=extremum(b,k,false),max=extremum(b,k,true);Signed128Accumulator a=integral(b);long sum=a.longValue(new Object());return SomaSharedSecrets.longSummaryAccess().create(b.size,min,max,sum,((double)sum)/b.size);}
    private static double floatingSum(Buffer b){double[] v=new double[b.size];for(int i=0;i<b.size;i++)v[i]=decode(b.values[i]);return pairwiseBlocks(v,b.size);}
    private static SomaDoubleSummary doubleSummary(Buffer b){if(b.size==0)return SomaSharedSecrets.doubleSummaryAccess().create(0,0,0,0,0);double min=decode(extremum(b,Kind.DOUBLE,false)),max=decode(extremum(b,Kind.DOUBLE,true)),sum=floatingSum(b);return SomaSharedSecrets.doubleSummaryAccess().create(b.size,min,max,sum,sum/b.size);}
    private static double pairwiseBlocks(double[] v,int n){int blocks=0;for(int start=0;start<n;start+=1024){int len=Math.min(1024,n-start);v[blocks++]=pairwise(v,start,len);}return pairwise(v,0,blocks);}
    private static double pairwise(double[] v,int start,int n){if(n==0)return 0;for(int w=1;w<n;w<<=1)for(int i=0;i+w<n;i+=w<<1)v[start+i]+=v[start+i+w];return v[start];}
    private static boolean testInt(SomaIntPredicate p,int v){CallbackExecutionScope.enter();try{return p.test(v);}catch(Exception e){throw SomaFailures.callbackFailure(SomaOperation.QUERY,e,new Object());}finally{CallbackExecutionScope.exit();}}
    private static boolean testLong(SomaLongPredicate p,long v){CallbackExecutionScope.enter();try{return p.test(v);}catch(Exception e){throw SomaFailures.callbackFailure(SomaOperation.QUERY,e,new Object());}finally{CallbackExecutionScope.exit();}}
    private static boolean testDouble(SomaDoublePredicate p,double v){CallbackExecutionScope.enter();try{return p.test(v);}catch(Exception e){throw SomaFailures.callbackFailure(SomaOperation.QUERY,e,new Object());}finally{CallbackExecutionScope.exit();}}
    private static void acceptInt(SomaIntConsumer a,int v){CallbackExecutionScope.enter();try{a.accept(v);}catch(Exception e){throw SomaFailures.callbackFailure(SomaOperation.QUERY,e,new Object());}finally{CallbackExecutionScope.exit();}}
    private static void acceptLong(SomaLongConsumer a,long v){CallbackExecutionScope.enter();try{a.accept(v);}catch(Exception e){throw SomaFailures.callbackFailure(SomaOperation.QUERY,e,new Object());}finally{CallbackExecutionScope.exit();}}
    private static void acceptDouble(SomaDoubleConsumer a,double v){CallbackExecutionScope.enter();try{a.accept(v);}catch(Exception e){throw SomaFailures.callbackFailure(SomaOperation.QUERY,e,new Object());}finally{CallbackExecutionScope.exit();}}
    private static long encode(double v){return Double.doubleToRawLongBits(v);}
    private static double decode(long v){return Double.longBitsToDouble(v);}
    private static String explain(RelationPrimitivePipelineCapture p){CanonicalRelationPhysicalDownstream d=physical(p);return "SOMA relation-primitive kind="+p.valueKind+" stages="+p.stages.length+" physicalBreakers="+d.breakerCount;}

    private static final class Buffer{final long[] values;int size;Buffer(int n){values=new long[n];}void add(long v){values[size++]=v;}}
    private static final class Stage{final StageKind kind;final Kind output;final Object callback;final long count;private Stage(StageKind k,Kind o,Object c,long n){kind=k;output=o;callback=c;count=n;}static Stage of(StageKind k,Kind o,Object c,long n){return new Stage(k,o,c,n);}}
    /** Java facade capture only; terminal lowering owns semantic meaning. */
    private static final class RelationPrimitivePipelineCapture{
        final GeneratedRelation relation;final GeneratedRelationMappedPipeline.RelationMappedPipelineCapture mapped;final Kind rootKind;final Object rootMapper;final Kind valueKind;final Stage[] stages;
        private RelationPrimitivePipelineCapture(GeneratedRelation r,GeneratedRelationMappedPipeline.RelationMappedPipelineCapture m,Kind root,Object mapper,Kind value,Stage[] s){relation=r;mapped=m;rootKind=root;rootMapper=mapper;valueKind=value;stages=s;}
        static RelationPrimitivePipelineCapture direct(GeneratedRelation r,Kind k,Object m){return new RelationPrimitivePipelineCapture(r,null,k,m,k,new Stage[0]);}
        static RelationPrimitivePipelineCapture mapped(GeneratedRelationMappedPipeline.RelationMappedPipelineCapture p,Kind k,Object m){return new RelationPrimitivePipelineCapture(null,p,k,m,k,new Stage[0]);}
        RelationPrimitivePipelineCapture add(Stage s){Stage[] n=new Stage[stages.length+1];System.arraycopy(stages,0,n,0,stages.length);n[stages.length]=s;return new RelationPrimitivePipelineCapture(relation,mapped,rootKind,rootMapper,s.output,n);}
        RelationPrimitivePipelineCapture parallel(){return new RelationPrimitivePipelineCapture(relation==null?null:relation.parallelCopy(),mapped==null?null:mapped.parallel(),rootKind,rootMapper,valueKind,stages);}
    }
}
