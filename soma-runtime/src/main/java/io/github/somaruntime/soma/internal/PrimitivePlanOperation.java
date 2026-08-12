package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaDoublePredicate;
import io.github.somaruntime.soma.SomaDoubleSummary;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaIntPredicate;
import io.github.somaruntime.soma.SomaLongPredicate;
import io.github.somaruntime.soma.SomaLongSummary;
import io.github.somaruntime.soma.SomaOperation;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Unboxed execution of arbitrary primitive mapper chains. */
strictfp final class PrimitivePlanOperation {

    private PrimitivePlanOperation() {}

    static long count(final PrimitivePipelineCapture frontend) {
        final PrimitiveExecution execution = lower(
                frontend, CanonicalPrimitiveOperation.TerminalKind.COUNT, null);
        return terminal(execution, 0L, (bound, frame, plan) -> {
            final long[] count = new long[1];
            visitBound(frame, plan, value -> {
                count[0] = CheckedLong.increment(
                        count[0], bound.operation, bound.provenance);
                return true;
            });
            return count[0];
        });
    }

    static boolean match(
            final PrimitivePipelineCapture frontend,
            final Object predicate,
            final int mode) {
        final PrimitiveExecution execution = lower(
                frontend,
                CanonicalPrimitiveOperation.TerminalKind.MATCH,
                primitiveCallback(
                        frontend,
                        HostCallbackHandle.Kind.PRIMITIVE_PREDICATE,
                        predicate));
        return terminal(execution, 0L, (bound, frame, plan) -> {
            final boolean[] result = new boolean[] {mode != 0};
            visitBound(frame, plan, value -> {
                boolean matched = test(
                        bound,
                        plan.valueKind,
                        plan.terminalCallback,
                        value);
                if (mode == 0 && matched) { result[0] = true; return false; }
                if (mode == 1 && !matched) { result[0] = false; return false; }
                if (mode == 2 && matched) { result[0] = false; return false; }
                return true;
            });
            return result[0];
        });
    }

    static OptionalInt findInt(final PrimitivePipelineCapture frontend) {
        final long[] value = first(frontend);
        return value == null ? OptionalInt.empty() : OptionalInt.of((int) value[0]);
    }
    static Optional<Boolean> findBoolean(final PrimitivePipelineCapture frontend) {
        final long[] value = first(frontend);
        return value == null ? Optional.<Boolean>empty()
                : Optional.of(value[0] != 0L);
    }
    static OptionalLong findLong(final PrimitivePipelineCapture frontend) {
        final long[] value = first(frontend);
        return value == null ? OptionalLong.empty() : OptionalLong.of(value[0]);
    }
    static OptionalDouble findDouble(final PrimitivePipelineCapture frontend) {
        final long[] value = first(frontend);
        return value == null ? OptionalDouble.empty()
                : OptionalDouble.of(floatingValue(frontend.valueKind, value[0]));
    }

    private static long[] first(final PrimitivePipelineCapture frontend) {
        final PrimitiveExecution execution = lower(
                frontend,
                CanonicalPrimitiveOperation.TerminalKind.FIND_FIRST,
                null);
        return terminal(execution, 0L, (bound, frame, plan) -> {
            final long[] value = new long[1]; final boolean[] present = new boolean[1];
            visitBound(frame, plan, candidate -> { value[0] = candidate; present[0] = true; return false; });
            return present[0] ? value : null;
        });
    }

    static OptionalInt extremumInt(
            final PrimitivePipelineCapture frontend,
            final boolean maximum) {
        Long result = extremum(frontend, maximum);
        return result == null ? OptionalInt.empty() : OptionalInt.of(result.intValue());
    }
    static OptionalLong extremumLong(
            final PrimitivePipelineCapture frontend,
            final boolean maximum) {
        Long result = extremum(frontend, maximum);
        return result == null ? OptionalLong.empty() : OptionalLong.of(result.longValue());
    }
    static OptionalDouble extremumDouble(
            final PrimitivePipelineCapture frontend,
            final boolean maximum) {
        Long result = extremum(frontend, maximum);
        return result == null ? OptionalDouble.empty()
                : OptionalDouble.of(floatingValue(
                        frontend.valueKind, result.longValue()));
    }

    private static Long extremum(
            final PrimitivePipelineCapture frontend,
            final boolean maximum) {
        final PrimitiveExecution execution = lower(
                frontend,
                CanonicalPrimitiveOperation.TerminalKind.EXTREMUM,
                null);
        return terminal(execution, 0L, (bound, frame, plan) -> {
            final long[] result = new long[1]; final boolean[] present = new boolean[1];
            visitBound(frame, plan, value -> {
                if (!present[0] || (maximum
                        ? compare(plan.valueKind, value, result[0]) > 0
                        : compare(plan.valueKind, value, result[0]) < 0)) {
                    result[0] = value; present[0] = true;
                }
                return true;
            });
            return present[0] ? Long.valueOf(result[0]) : null;
        });
    }

    static long sumIntegral(final PrimitivePipelineCapture frontend) {
        final PrimitiveExecution execution = lower(
                frontend, CanonicalPrimitiveOperation.TerminalKind.SUM, null);
        return terminal(execution, 0L, (bound, frame, plan) -> {
            if (CanonicalPrimitiveVectorKernel.isIntegralSum(frame.plan)) {
                return CanonicalPrimitiveVectorKernel.sumIntegral(frame);
            }
            Signed128Accumulator result = new Signed128Accumulator();
            visitBound(frame, plan, value -> { result.add(integralValue(plan.valueKind, value)); return true; });
            return result.longValue(bound.provenance);
        });
    }

    static double sumDouble(final PrimitivePipelineCapture frontend) {
        return floatingValues(frontend, false).sum;
    }

    static OptionalDouble averageIntegral(final PrimitivePipelineCapture frontend) {
        final PrimitiveExecution execution = lower(
                frontend,
                CanonicalPrimitiveOperation.TerminalKind.AVERAGE,
                null);
        return terminal(execution, 0L, (bound, frame, plan) -> {
            Signed128Accumulator result = new Signed128Accumulator(); final long[] count = new long[1];
            visitBound(frame, plan, value -> { result.add(integralValue(plan.valueKind, value)); count[0]++; return true; });
            return count[0] == 0L ? OptionalDouble.empty()
                    : OptionalDouble.of(result.doubleValue() / (double) count[0]);
        });
    }

    static OptionalDouble averageDouble(final PrimitivePipelineCapture frontend) {
        FloatingResult result = floatingValues(frontend, false);
        return result.count == 0L ? OptionalDouble.empty() : OptionalDouble.of(result.sum / result.count);
    }

    static SomaLongSummary summaryIntegral(final PrimitivePipelineCapture frontend) {
        final PrimitiveExecution execution = lower(
                frontend,
                CanonicalPrimitiveOperation.TerminalKind.SUMMARY,
                null);
        return terminal(execution, 0L, (bound, frame, plan) -> {
            Signed128Accumulator sum = new Signed128Accumulator(); final long[] count = new long[1];
            final long[] min = new long[1]; final long[] max = new long[1];
            visitBound(frame, plan, raw -> {
                long value = integralValue(plan.valueKind, raw);
                if (count[0] == 0L) { min[0] = value; max[0] = value; }
                else { if (value < min[0]) min[0] = value; if (value > max[0]) max[0] = value; }
                sum.add(value); count[0]++; return true;
            });
            long exact = sum.longValue(bound.provenance);
            return SomaSharedSecrets.longSummaryAccess().create(count[0], count[0] == 0 ? 0 : min[0],
                    count[0] == 0 ? 0 : max[0], exact, count[0] == 0 ? 0.0d : exact / (double) count[0]);
        });
    }

    static SomaDoubleSummary summaryDouble(final PrimitivePipelineCapture frontend) {
        FloatingResult result = floatingValues(frontend, true);
        return SomaSharedSecrets.doubleSummaryAccess().create(result.count,
                result.count == 0 ? 0.0d : result.min, result.count == 0 ? 0.0d : result.max,
                result.sum, result.count == 0 ? 0.0d : result.sum / result.count);
    }

    static int[] toIntArray(final PrimitivePipelineCapture frontend) {
        final PrimitiveExecution execution = materialize(frontend);
        return terminal(execution, 8L, (bound, frame, plan) -> {
            int[] staging = new int[materializedLength(bound, plan)];
            final int[] size = new int[1]; visitBound(frame, plan, value -> { staging[size[0]++] = (int) value; return true; });
            int[] result = new int[size[0]]; System.arraycopy(staging, 0, result, 0, size[0]); return result;
        });
    }
    static boolean[] toBooleanArray(final PrimitivePipelineCapture frontend) {
        final PrimitiveExecution execution = materialize(frontend);
        return terminal(execution, 8L, (bound, frame, plan) -> {
            boolean[] staging = new boolean[materializedLength(bound, plan)];
            final int[] size = new int[1]; visitBound(frame, plan, value -> { staging[size[0]++] = value != 0L; return true; });
            boolean[] result = new boolean[size[0]]; System.arraycopy(staging, 0, result, 0, size[0]); return result;
        });
    }
    static byte[] toByteArray(final PrimitivePipelineCapture frontend) {
        final PrimitiveExecution execution = materialize(frontend);
        return terminal(execution, 8L, (bound, frame, plan) -> {
            byte[] staging = new byte[materializedLength(bound, plan)];
            final int[] size = new int[1]; visitBound(frame, plan, value -> { staging[size[0]++] = (byte) value; return true; });
            byte[] result = new byte[size[0]]; System.arraycopy(staging, 0, result, 0, size[0]); return result;
        });
    }
    static short[] toShortArray(final PrimitivePipelineCapture frontend) {
        final PrimitiveExecution execution = materialize(frontend);
        return terminal(execution, 8L, (bound, frame, plan) -> {
            short[] staging = new short[materializedLength(bound, plan)];
            final int[] size = new int[1]; visitBound(frame, plan, value -> { staging[size[0]++] = (short) value; return true; });
            short[] result = new short[size[0]]; System.arraycopy(staging, 0, result, 0, size[0]); return result;
        });
    }
    static char[] toCharArray(final PrimitivePipelineCapture frontend) {
        final PrimitiveExecution execution = materialize(frontend);
        return terminal(execution, 8L, (bound, frame, plan) -> {
            char[] staging = new char[materializedLength(bound, plan)];
            final int[] size = new int[1]; visitBound(frame, plan, value -> { staging[size[0]++] = (char) value; return true; });
            char[] result = new char[size[0]]; System.arraycopy(staging, 0, result, 0, size[0]); return result;
        });
    }
    static long[] toLongArray(final PrimitivePipelineCapture frontend) {
        final PrimitiveExecution execution = materialize(frontend);
        return terminal(execution, 16L, (bound, frame, plan) -> {
            if (CanonicalPrimitiveVectorKernel.isLongMaterialization(
                    frame.plan)) {
                return CanonicalPrimitiveVectorKernel.materializeLongs(frame);
            }
            long[] staging = new long[materializedLength(bound, plan)];
            final int[] scalarSize = new int[1];
            visitBound(frame, plan, value -> {
                staging[scalarSize[0]++] = value;
                return true;
            });
            int size = scalarSize[0];
            if (size == staging.length) return staging;
            long[] result = new long[size];
            System.arraycopy(staging, 0, result, 0, size);
            return result;
        });
    }
    static double[] toDoubleArray(final PrimitivePipelineCapture frontend) {
        final PrimitiveExecution execution = materialize(frontend);
        return terminal(execution, 16L, (bound, frame, plan) -> {
            double[] staging = new double[materializedLength(bound, plan)];
            final int[] size = new int[1]; visitBound(frame, plan, value -> { staging[size[0]++] = floatingValue(plan.valueKind, value); return true; });
            double[] result = new double[size[0]]; System.arraycopy(staging, 0, result, 0, size[0]); return result;
        });
    }
    static float[] toFloatArray(final PrimitivePipelineCapture frontend) {
        final PrimitiveExecution execution = materialize(frontend);
        return terminal(execution, 8L, (bound, frame, plan) -> {
            float[] staging = new float[materializedLength(bound, plan)];
            final int[] size = new int[1]; visitBound(frame, plan, value -> { staging[size[0]++] = Float.intBitsToFloat((int) value); return true; });
            float[] result = new float[size[0]]; System.arraycopy(staging, 0, result, 0, size[0]); return result;
        });
    }

    static void forEach(
            final PrimitivePipelineCapture frontend,
            final Object action) {
        final PrimitiveExecution execution = lower(
                frontend,
                CanonicalPrimitiveOperation.TerminalKind.FOR_EACH,
                primitiveCallback(
                        frontend,
                        HostCallbackHandle.Kind.PRIMITIVE_ACTION,
                        action));
        terminal(execution, 0L, (bound, frame, plan) -> {
            visitBound(frame, plan, value -> {
                accept(bound, plan.valueKind, plan.terminalCallback, value);
                return true;
            });
            return null;
        });
    }

    static List<Object> toBoxedList(final PrimitivePipelineCapture frontend) {
        final PrimitiveExecution execution = materialize(frontend);
        return terminal(execution, 48L, (bound, frame, plan) -> {
            ArrayList<Object> result = new ArrayList<Object>(
                    materializedLength(bound, plan));
            visitBound(frame, plan, value -> {
                result.add(box(plan.valueKind, value)); return true;
            });
            return result;
        });
    }

    static String explain(final PrimitivePipelineCapture frontend) {
        final PrimitiveExecution execution = lower(
                frontend,
                CanonicalPrimitiveOperation.TerminalKind.EXPLAIN,
                null);
        return CanonicalQueryOperation.inspectFamily(
                execution.table,
                execution.operation.source,
                new CanonicalQueryOperation.ReferenceWork<String>() {
        @Override public String run(BoundCanonicalRowOperation bound) {
            CanonicalPrimitiveOperation plan = execution.operation;
            long temporary = estimatedExecutionScratch(bound, plan, 0L);
            CanonicalRowPhysicalPlan physical =
                    CanonicalQueryOperation.planBound(
                            bound,
                            CanonicalRowPhysicalRequest.primitive(
                                    plan, temporary));
            StringBuilder result = new StringBuilder(
                    CanonicalQueryOperation.explainBound(
                            bound, physical));
            result.append(" primitiveRoot=").append(plan.rootKind).append(':').append(plan.rootValueKind).append(" stages=[");
            for (int i = 0; i < plan.stages.size(); i++) { if (i != 0) result.append(','); result.append(plan.stages.get(i).kind); }
            return result.append("] output=").append(plan.valueKind)
                    .append(" estimatedPrimitiveTemporaryPeakBytes=")
                    .append(physical.resources.temporaryBytes)
                    .toString();
        }});
    }

    static long[] valuesForTesting(final PrimitivePipelineCapture frontend) {
        final PrimitiveExecution execution = lower(
                frontend,
                CanonicalPrimitiveOperation.TerminalKind.TEST,
                null);
        return terminal(execution, 16L, (bound, frame, plan) -> {
            long[] staging = new long[materializedLength(bound, plan)];
            final int[] size = new int[1];
            visitBound(frame, plan, value -> {
                staging[size[0]++] = value; return true;
            });
            long[] result = new long[size[0]];
            System.arraycopy(staging, 0, result, 0, size[0]);
            return result;
        });
    }

    private static FloatingResult floatingValues(
            final PrimitivePipelineCapture frontend,
            final boolean extrema) {
        final PrimitiveExecution execution = lower(
                frontend,
                CanonicalPrimitiveOperation.TerminalKind.SUM,
                null);
        return terminal(execution, 8L, (bound, frame, plan) -> {
            double[] values = new double[materializedLength(bound, plan)];
            final int[] size = new int[1]; final double[] min = new double[1]; final double[] max = new double[1];
            visitBound(frame, plan, raw -> {
                double value = floatingValue(plan.valueKind, raw);
                if (size[0] == 0) { min[0] = value; max[0] = value; }
                else if (extrema) { if (Double.compare(value, min[0]) < 0) min[0] = value; if (Double.compare(value, max[0]) > 0) max[0] = value; }
                values[size[0]++] = value; return true;
            });
            return new FloatingResult(size[0], min[0], max[0], canonicalSum(values, size[0]));
        });
    }

    private static <T> T terminal(
            final PrimitiveExecution execution,
            final long extraPerElement,
            final Terminal<T> terminal) {
        return CanonicalQueryOperation.executePrimitiveFamily(
                execution.frontend,
                execution.operation.source,
                new CanonicalQueryOperation.ExtraScratch() {
            @Override public long bytes(BoundCanonicalRowOperation bound) {
                return estimatedExecutionScratch(
                        bound, execution.operation, extraPerElement);
            }
        }, new CanonicalQueryOperation.FrameWork<T>() {
            @Override public T run(CanonicalRowExecutionFrame frame) {
                return terminal.run(
                        frame.plan.normalized.bound,
                        frame,
                        execution.operation);
            }
        }, execution.operation);
    }

    private static PrimitiveExecution materialize(
            PrimitivePipelineCapture frontend) {
        return lower(
                frontend,
                CanonicalPrimitiveOperation.TerminalKind.MATERIALIZE,
                null);
    }

    private static PrimitiveExecution lower(
            PrimitivePipelineCapture frontend,
            CanonicalPrimitiveOperation.TerminalKind terminal,
            HostCallbackHandle terminalCallback) {
        GeneratedTable table = frontend.rows.owner();
        return new PrimitiveExecution(
                table,
                frontend.rows,
                CanonicalPrimitiveLowering.operation(
                        frontend, terminal, terminalCallback));
    }

    private static HostCallbackHandle primitiveCallback(
            PrimitivePipelineCapture frontend,
            HostCallbackHandle.Kind kind,
            Object callback) {
        return HostCallbackHandle.host(
                frontend.rows.owner().logicalIdentity(), kind, callback);
    }

    private static long estimatedExecutionScratch(
            BoundCanonicalRowOperation bound,
            CanonicalPrimitiveOperation plan,
            long extraPerElement) {
                long result = 0L;
                if (plan.mapped != null && plan.mapped.hasOwnStatefulStage()) {
                    result = CheckedLong.add(
                            result,
                            MappedQueryOperation.mappedStatefulScratch(bound),
                            bound.operation,
                            bound.provenance);
                }
                if (plan.hasOwnStatefulStage()) {
                    result = CheckedLong.add(
                            result,
                            RowExecutionSupport.arrayBytes(
                                    bound.root.size, 64L, bound.provenance),
                            bound.operation,
                            bound.provenance);
                }
                if (extraPerElement != 0L) {
                    result = CheckedLong.add(
                            result,
                            RowExecutionSupport.arrayBytes(
                                    plan.outputUpperBound(bound),
                                    extraPerElement,
                                    bound.provenance),
                            bound.operation,
                            bound.provenance);
                }
                return result;
    }

    private static int materializedLength(
            BoundCanonicalRowOperation bound,
            CanonicalPrimitiveOperation plan) {
        return RowExecutionSupport.arrayLength(
                plan.outputUpperBound(bound), bound.provenance);
    }

    static void visitBound(
            CanonicalRowExecutionFrame frame,
            CanonicalPrimitiveOperation plan,
            Visitor visitor) {
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        if (plan.hasOwnStatefulStage()) {
            LongValueBuffer values = values(frame, plan);
            for (int i = 0; i < values.size(); i++) if (!visitor.visit(values.get(i))) return;
            return;
        }
        final CanonicalPhysicalSegment segment =
                frame.plan.pipeline.terminalSegment();
        final int from = segment.shape == CanonicalPhysicalSegment.Shape.PRIMITIVE
                ? segment.fromStage : 0;
        final int to = segment.shape == CanonicalPhysicalSegment.Shape.PRIMITIVE
                ? segment.toStageExclusive : plan.stages.size();
        final long[] counters = new long[to - from];
        if (primitiveLimitReached(
                plan, counters, from, to)) {
            return;
        }
        visitRoot(frame, plan, raw -> {
            if (primitiveLimitReached(
                    plan, counters,
                    from, to)) return false;
            long value = raw;
            for (int i = from; i < to; i++) {
                CanonicalPrimitiveStage stage = plan.stages.get(i);
                int counter = i - from;
                switch (stage.kind) {
                    case FILTER: if (!test(bound, stage.input, stage.callback, value)) return !primitiveLimitReached(plan, counters, from, to); break;
                    case MAP: case CONVERT: value = apply(bound, stage, value); break;
                    case SKIP: if (counters[counter] < stage.count) { counters[counter]++; return !primitiveLimitReached(plan, counters, from, to); } break;
                    case LIMIT: if (counters[counter] >= stage.count) return false; counters[counter]++; break;
                    default: throw new AssertionError("stateful primitive stage in streaming path");
                }
            }
            return visitor.visit(value) && !primitiveLimitReached(
                    plan, counters,
                    from, to);
        });
    }

    private static boolean primitiveLimitReached(
            CanonicalPrimitiveOperation plan,
            long[] counters) {
        return primitiveLimitReached(plan, counters, 0, plan.stages.size());
    }

    private static boolean primitiveLimitReached(
            CanonicalPrimitiveOperation plan,
            long[] counters,
            int from,
            int to) {
        for (int index = from; index < to; index++) {
            CanonicalPrimitiveStage stage = plan.stages.get(index);
            if (stage.kind == CanonicalPrimitiveStage.Kind.LIMIT
                    && counters[index - from] >= stage.count) return true;
        }
        return false;
    }

    private static LongValueBuffer values(
            CanonicalRowExecutionFrame frame,
            CanonicalPrimitiveOperation plan) {
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        LongValueBuffer result = new LongValueBuffer(bound.root.size, bound.provenance);
        int firstStateful = primitiveNextStateful(plan.stages, 0);
        collectPrimitiveSegment(frame, plan, 0, firstStateful, result);
        int position = firstStateful;
        while (position < plan.stages.size()) {
            CanonicalPrimitiveStage stage = plan.stages.get(position);
            switch (stage.kind) {
                case DISTINCT: distinct(bound, result, stage.input); break;
                case SORTED: sort(result, stage.input); break;
                default: throw new AssertionError("expected stateful primitive stage");
            }
            int next = primitiveNextStateful(plan.stages, position + 1);
            compactPrimitiveSegment(
                    bound, result, plan.stages, position + 1, next);
            position = next;
        }
        return result;
    }

    private static void collectPrimitiveSegment(
            final CanonicalRowExecutionFrame frame,
            final CanonicalPrimitiveOperation plan,
            final int from,
            final int to,
            final LongValueBuffer output) {
        final BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        final long[] counters = new long[to - from];
        if (primitiveSegmentLimitReached(plan.stages, from, to, counters)) return;
        visitRoot(frame, plan, raw -> {
            long value = raw;
            for (int position = from; position < to; position++) {
                CanonicalPrimitiveStage stage = plan.stages.get(position);
                int counter = position - from;
                switch (stage.kind) {
                    case FILTER:
                        if (!test(bound, stage.input, stage.callback, value)) {
                            return !primitiveSegmentLimitReached(
                                    plan.stages, from, to, counters);
                        }
                        break;
                    case MAP:
                    case CONVERT:
                        value = apply(bound, stage, value);
                        break;
                    case SKIP:
                        if (counters[counter] < stage.count) {
                            counters[counter]++;
                            return !primitiveSegmentLimitReached(
                                    plan.stages, from, to, counters);
                        }
                        break;
                    case LIMIT:
                        if (counters[counter] >= stage.count) return false;
                        counters[counter]++;
                        break;
                    default:
                        throw new AssertionError("stateful primitive stage inside segment");
                }
            }
            output.add(value);
            return !primitiveSegmentLimitReached(
                    plan.stages, from, to, counters);
        });
    }

    private static void compactPrimitiveSegment(
            BoundCanonicalRowOperation bound,
            LongValueBuffer values,
            java.util.List<CanonicalPrimitiveStage> stages,
            int from,
            int to) {
        if (from == to) return;
        long[] counters = new long[to - from];
        int output = 0;
        inputLoop:
        for (int input = 0; input < values.size(); input++) {
            if (primitiveSegmentLimitReached(stages, from, to, counters)) break;
            long value = values.get(input);
            for (int position = from; position < to; position++) {
                CanonicalPrimitiveStage stage = stages.get(position);
                int counter = position - from;
                switch (stage.kind) {
                    case FILTER:
                        if (!test(bound, stage.input, stage.callback, value)) {
                            continue inputLoop;
                        }
                        break;
                    case MAP:
                    case CONVERT:
                        value = apply(bound, stage, value);
                        break;
                    case SKIP:
                        if (counters[counter] < stage.count) {
                            counters[counter]++;
                            continue inputLoop;
                        }
                        break;
                    case LIMIT:
                        if (counters[counter] >= stage.count) break inputLoop;
                        counters[counter]++;
                        break;
                    default:
                        throw new AssertionError("stateful primitive stage inside segment");
                }
            }
            values.set(output++, value);
        }
        values.size(output);
    }

    private static boolean primitiveSegmentLimitReached(
            java.util.List<CanonicalPrimitiveStage> stages,
            int from,
            int to,
            long[] counters) {
        for (int position = from; position < to; position++) {
            CanonicalPrimitiveStage stage = stages.get(position);
            if (stage.kind == CanonicalPrimitiveStage.Kind.LIMIT
                    && counters[position - from] >= stage.count) return true;
        }
        return false;
    }

    private static int primitiveNextStateful(
            java.util.List<CanonicalPrimitiveStage> stages,
            int from) {
        for (int position = from; position < stages.size(); position++) {
            CanonicalPrimitiveStage.Kind kind = stages.get(position).kind;
            if (kind == CanonicalPrimitiveStage.Kind.DISTINCT
                    || kind == CanonicalPrimitiveStage.Kind.SORTED) return position;
        }
        return stages.size();
    }

    private static void visitRoot(
            CanonicalRowExecutionFrame frame,
            CanonicalPrimitiveOperation plan,
            Visitor visitor) {
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        if (plan.rootKind == CanonicalPrimitiveOperation.RootKind.ROW) {
            if (plan.rootFieldIndex >= 0
                    && isDirectFieldProjection(
                            plan.source, plan.rootFieldIndex)) {
                GeneratedTableLayout layout = bound.layout;
                int leaf = layout.fieldStart(plan.rootFieldIndex);
                if (layout.fieldLeafCount(plan.rootFieldIndex) != 1) {
                    throw new AssertionError(
                            "primitive Field must have one physical leaf");
                }
                int slot = layout.leafSlot(leaf);
                byte kind = layout.leafKind(leaf);
                TableChunkDirectory directory = bound.root.directory;
                int chunkRows = directory.chunkRows();
                int remaining = bound.root.size;
                for (int ordinal = 0;
                        ordinal < directory.chunkCount() && remaining > 0;
                        ordinal++) {
                    TableChunk chunk = directory.chunk(ordinal);
                    int logicalRows = Math.min(chunkRows, remaining);
                    if (!chunk.visitPrimitive(
                            kind, slot, logicalRows, visitor)) return;
                    remaining -= logicalRows;
                }
            } else {
                CanonicalRowExecution.visit(
                        frame,
                        locator -> visitor.visit(rowRoot(bound, plan, locator)));
            }
        } else {
            MappedQueryOperation.visit(
                    frame,
                    plan.mapped,
                    value -> visitor.visit(mappedRoot(bound, plan, value)));
        }
    }

    private static long rowRoot(BoundCanonicalRowOperation bound, CanonicalPrimitiveOperation plan, int locator) {
        if (plan.rootFieldIndex >= 0) {
            return directFieldRoot(bound, plan, locator);
        }
        GeneratedQueryCursor cursor = bound.table.queryCursor();
        cursor.enter(locator);
        CallbackExecutionScope.enter();
        try {
            Object mapper = plan.rootMapper.callback;
            switch (plan.rootValueKind) {
                case BOOLEAN: return ((GeneratedCallbacks.RowToBooleanMapper) mapper).applyAsBoolean() ? 1L : 0L;
                case BYTE: return ((GeneratedCallbacks.RowToByteMapper) mapper).applyAsByte();
                case SHORT: return ((GeneratedCallbacks.RowToShortMapper) mapper).applyAsShort();
                case CHAR: return ((GeneratedCallbacks.RowToCharMapper) mapper).applyAsChar();
                case INT: return ((GeneratedCallbacks.RowToIntMapper) mapper).applyAsInt();
                case LONG: return ((GeneratedCallbacks.RowToLongMapper) mapper).applyAsLong();
                case FLOAT: return Float.floatToIntBits(((GeneratedCallbacks.RowToFloatMapper) mapper).applyAsFloat());
                case DOUBLE: return Double.doubleToLongBits(((GeneratedCallbacks.RowToDoubleMapper) mapper).applyAsDouble());
                default: throw new AssertionError();
            }
        } catch (Exception failure) {
            if (!plan.rootApplicationCallback && failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            if (!plan.rootApplicationCallback) {
                throw new AssertionError(
                        "generated primitive materializer threw checked failure",
                        failure);
            }
            throw SomaFailures.callbackFailure(
                    bound.operation, failure, bound.provenance);
        } finally {
            CallbackExecutionScope.exit();
            cursor.leave();
        }
    }

    private static long directFieldRoot(
            BoundCanonicalRowOperation bound,
            CanonicalPrimitiveOperation plan,
            int locator) {
        GeneratedTableLayout layout = bound.layout;
        int leaf = layout.fieldStart(plan.rootFieldIndex);
        if (layout.fieldLeafCount(plan.rootFieldIndex) != 1) {
            throw new AssertionError("primitive Field must have one physical leaf");
        }
        int slot = layout.leafSlot(leaf);
        switch (plan.rootValueKind) {
            case BOOLEAN:
                return bound.root.directory.booleanValue(locator, slot) ? 1L : 0L;
            case BYTE:
                return bound.root.directory.byteValue(locator, slot);
            case SHORT:
                return bound.root.directory.shortValue(locator, slot);
            case CHAR:
                return bound.root.directory.charValue(locator, slot);
            case INT:
                return bound.root.directory.intValue(locator, slot);
            case LONG:
                return bound.root.directory.longValue(locator, slot);
            case FLOAT:
                return Float.floatToIntBits(
                        bound.root.directory.floatValue(locator, slot));
            case DOUBLE:
                return Double.doubleToLongBits(
                        bound.root.directory.doubleValue(locator, slot));
            default:
                throw new AssertionError();
        }
    }

    @SuppressWarnings("unchecked") private static long mappedRoot(BoundCanonicalRowOperation bound, CanonicalPrimitiveOperation plan, Object value) {
        CallbackExecutionScope.enter();
        try {
            switch (plan.rootValueKind) {
                case INT: return ((io.github.somaruntime.soma.SomaToIntFunction<Object>) plan.rootMapper.callback).applyAsInt(value);
                case LONG: return ((io.github.somaruntime.soma.SomaToLongFunction<Object>) plan.rootMapper.callback).applyAsLong(value);
                case DOUBLE: return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaToDoubleFunction<Object>) plan.rootMapper.callback).applyAsDouble(value));
                default: throw new AssertionError();
            }
        } catch (Exception failure) { throw SomaFailures.callbackFailure(SomaOperation.QUERY, failure, bound.provenance); }
        finally { CallbackExecutionScope.exit(); }
    }

    private static boolean test(
            BoundCanonicalRowOperation bound,
            PrimitiveValueKind kind,
            HostCallbackHandle handle,
            long raw) {
        if (handle == null
                || handle.kind != HostCallbackHandle.Kind.PRIMITIVE_PREDICATE) {
            throw new AssertionError("primitive predicate callback kind drift");
        }
        Object callback = handle.callback;
        CallbackExecutionScope.enter();
        try {
            switch (kind) {
                case BOOLEAN: return ((io.github.somaruntime.soma.SomaBooleanPredicate) callback).test(raw != 0L);
                case BYTE: return ((io.github.somaruntime.soma.SomaBytePredicate) callback).test((byte) raw);
                case SHORT: return ((io.github.somaruntime.soma.SomaShortPredicate) callback).test((short) raw);
                case CHAR: return ((io.github.somaruntime.soma.SomaCharPredicate) callback).test((char) raw);
                case INT: return ((SomaIntPredicate) callback).test((int) raw);
                case LONG: return ((SomaLongPredicate) callback).test(raw);
                case FLOAT: return ((io.github.somaruntime.soma.SomaFloatPredicate) callback).test(Float.intBitsToFloat((int) raw));
                case DOUBLE: return ((SomaDoublePredicate) callback).test(Double.longBitsToDouble(raw));
                default: throw new AssertionError();
            }
        } catch (Exception failure) { throw SomaFailures.callbackFailure(SomaOperation.QUERY, failure, bound.provenance); }
        finally { CallbackExecutionScope.exit(); }
    }

    private static long apply(BoundCanonicalRowOperation bound, CanonicalPrimitiveStage stage, long raw) {
        if (stage.callback == null
                || stage.callback.kind != HostCallbackHandle.Kind.PRIMITIVE_MAPPER) {
            throw new AssertionError("primitive mapper callback kind drift");
        }
        Object callback = stage.callback.callback;
        CallbackExecutionScope.enter();
        try {
            if (stage.input == PrimitiveValueKind.BOOLEAN && stage.output == PrimitiveValueKind.BOOLEAN)
                return ((io.github.somaruntime.soma.SomaBooleanUnaryOperator) callback).applyAsBoolean(raw != 0L) ? 1L : 0L;
            if (stage.input == PrimitiveValueKind.BYTE && stage.output == PrimitiveValueKind.BYTE)
                return ((io.github.somaruntime.soma.SomaByteUnaryOperator) callback).applyAsByte((byte) raw);
            if (stage.input == PrimitiveValueKind.SHORT && stage.output == PrimitiveValueKind.SHORT)
                return ((io.github.somaruntime.soma.SomaShortUnaryOperator) callback).applyAsShort((short) raw);
            if (stage.input == PrimitiveValueKind.CHAR && stage.output == PrimitiveValueKind.CHAR)
                return ((io.github.somaruntime.soma.SomaCharUnaryOperator) callback).applyAsChar((char) raw);
            if (stage.input == PrimitiveValueKind.INT && stage.output == PrimitiveValueKind.INT)
                return ((io.github.somaruntime.soma.SomaIntUnaryOperator) callback).applyAsInt((int) raw);
            if (stage.input == PrimitiveValueKind.LONG && stage.output == PrimitiveValueKind.LONG)
                return ((io.github.somaruntime.soma.SomaLongUnaryOperator) callback).applyAsLong(raw);
            if (stage.input == PrimitiveValueKind.FLOAT && stage.output == PrimitiveValueKind.FLOAT)
                return Float.floatToIntBits(((io.github.somaruntime.soma.SomaFloatUnaryOperator) callback).applyAsFloat(Float.intBitsToFloat((int) raw)));
            if (stage.input == PrimitiveValueKind.DOUBLE && stage.output == PrimitiveValueKind.DOUBLE)
                return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaDoubleUnaryOperator) callback).applyAsDouble(Double.longBitsToDouble(raw)));
            if (stage.input == PrimitiveValueKind.BOOLEAN && stage.output == PrimitiveValueKind.INT)
                return ((io.github.somaruntime.soma.SomaBooleanToIntFunction) callback).applyAsInt(raw != 0L);
            if (stage.input == PrimitiveValueKind.BOOLEAN && stage.output == PrimitiveValueKind.LONG)
                return ((io.github.somaruntime.soma.SomaBooleanToLongFunction) callback).applyAsLong(raw != 0L);
            if (stage.input == PrimitiveValueKind.BOOLEAN && stage.output == PrimitiveValueKind.DOUBLE)
                return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaBooleanToDoubleFunction) callback).applyAsDouble(raw != 0L));
            if (stage.input == PrimitiveValueKind.BYTE && stage.output == PrimitiveValueKind.INT)
                return ((io.github.somaruntime.soma.SomaByteToIntFunction) callback).applyAsInt((byte) raw);
            if (stage.input == PrimitiveValueKind.BYTE && stage.output == PrimitiveValueKind.LONG)
                return ((io.github.somaruntime.soma.SomaByteToLongFunction) callback).applyAsLong((byte) raw);
            if (stage.input == PrimitiveValueKind.BYTE && stage.output == PrimitiveValueKind.DOUBLE)
                return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaByteToDoubleFunction) callback).applyAsDouble((byte) raw));
            if (stage.input == PrimitiveValueKind.SHORT && stage.output == PrimitiveValueKind.INT)
                return ((io.github.somaruntime.soma.SomaShortToIntFunction) callback).applyAsInt((short) raw);
            if (stage.input == PrimitiveValueKind.SHORT && stage.output == PrimitiveValueKind.LONG)
                return ((io.github.somaruntime.soma.SomaShortToLongFunction) callback).applyAsLong((short) raw);
            if (stage.input == PrimitiveValueKind.SHORT && stage.output == PrimitiveValueKind.DOUBLE)
                return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaShortToDoubleFunction) callback).applyAsDouble((short) raw));
            if (stage.input == PrimitiveValueKind.CHAR && stage.output == PrimitiveValueKind.INT)
                return ((io.github.somaruntime.soma.SomaCharToIntFunction) callback).applyAsInt((char) raw);
            if (stage.input == PrimitiveValueKind.CHAR && stage.output == PrimitiveValueKind.LONG)
                return ((io.github.somaruntime.soma.SomaCharToLongFunction) callback).applyAsLong((char) raw);
            if (stage.input == PrimitiveValueKind.CHAR && stage.output == PrimitiveValueKind.DOUBLE)
                return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaCharToDoubleFunction) callback).applyAsDouble((char) raw));
            if (stage.input == PrimitiveValueKind.INT && stage.output == PrimitiveValueKind.LONG)
                return ((io.github.somaruntime.soma.SomaIntToLongFunction) callback).applyAsLong((int) raw);
            if (stage.input == PrimitiveValueKind.INT && stage.output == PrimitiveValueKind.DOUBLE)
                return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaIntToDoubleFunction) callback).applyAsDouble((int) raw));
            if (stage.input == PrimitiveValueKind.LONG && stage.output == PrimitiveValueKind.INT)
                return ((io.github.somaruntime.soma.SomaLongToIntFunction) callback).applyAsInt(raw);
            if (stage.input == PrimitiveValueKind.LONG && stage.output == PrimitiveValueKind.DOUBLE)
                return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaLongToDoubleFunction) callback).applyAsDouble(raw));
            if (stage.input == PrimitiveValueKind.FLOAT && stage.output == PrimitiveValueKind.INT)
                return ((io.github.somaruntime.soma.SomaFloatToIntFunction) callback).applyAsInt(Float.intBitsToFloat((int) raw));
            if (stage.input == PrimitiveValueKind.FLOAT && stage.output == PrimitiveValueKind.LONG)
                return ((io.github.somaruntime.soma.SomaFloatToLongFunction) callback).applyAsLong(Float.intBitsToFloat((int) raw));
            if (stage.input == PrimitiveValueKind.FLOAT && stage.output == PrimitiveValueKind.DOUBLE)
                return Double.doubleToLongBits(((io.github.somaruntime.soma.SomaFloatToDoubleFunction) callback).applyAsDouble(Float.intBitsToFloat((int) raw)));
            if (stage.input == PrimitiveValueKind.DOUBLE && stage.output == PrimitiveValueKind.INT)
                return ((io.github.somaruntime.soma.SomaDoubleToIntFunction) callback).applyAsInt(Double.longBitsToDouble(raw));
            if (stage.input == PrimitiveValueKind.DOUBLE && stage.output == PrimitiveValueKind.LONG)
                return ((io.github.somaruntime.soma.SomaDoubleToLongFunction) callback).applyAsLong(Double.longBitsToDouble(raw));
            throw new AssertionError("unknown primitive conversion");
        } catch (Exception failure) { throw SomaFailures.callbackFailure(SomaOperation.QUERY, failure, bound.provenance); }
        finally { CallbackExecutionScope.exit(); }
    }

    private static void accept(
            BoundCanonicalRowOperation bound,
            PrimitiveValueKind kind,
            HostCallbackHandle handle,
            long raw) {
        if (handle == null
                || handle.kind != HostCallbackHandle.Kind.PRIMITIVE_ACTION) {
            throw new AssertionError("primitive action callback kind drift");
        }
        Object action = handle.callback;
        CallbackExecutionScope.enter();
        try {
            switch (kind) {
                case BOOLEAN: ((io.github.somaruntime.soma.SomaBooleanConsumer) action).accept(raw != 0L); break;
                case BYTE: ((io.github.somaruntime.soma.SomaByteConsumer) action).accept((byte) raw); break;
                case SHORT: ((io.github.somaruntime.soma.SomaShortConsumer) action).accept((short) raw); break;
                case CHAR: ((io.github.somaruntime.soma.SomaCharConsumer) action).accept((char) raw); break;
                case INT: ((io.github.somaruntime.soma.SomaIntConsumer) action).accept((int) raw); break;
                case LONG: ((io.github.somaruntime.soma.SomaLongConsumer) action).accept(raw); break;
                case FLOAT: ((io.github.somaruntime.soma.SomaFloatConsumer) action).accept(Float.intBitsToFloat((int) raw)); break;
                case DOUBLE: ((io.github.somaruntime.soma.SomaDoubleConsumer) action).accept(Double.longBitsToDouble(raw)); break;
                default: throw new AssertionError();
            }
        } catch (Exception failure) { throw SomaFailures.callbackFailure(SomaOperation.QUERY, failure, bound.provenance); }
        finally { CallbackExecutionScope.exit(); }
    }

    private static void compact(BoundCanonicalRowOperation bound, LongValueBuffer values, CanonicalPrimitiveStage stage) {
        int output = 0; for (int i = 0; i < values.size(); i++) { long value = values.get(i); if (test(bound, stage.input, stage.callback, value)) values.set(output++, value); } values.size(output);
    }
    private static void distinct(
            BoundCanonicalRowOperation bound,
            LongValueBuffer values,
            PrimitiveValueKind kind) {
        PrimitiveDistinctSet seen = new PrimitiveDistinctSet(
                values.size(), bound.provenance);
        int output = 0;
        for (int index = 0; index < values.size(); index++) {
            long value = values.get(index);
            if (seen.add(canonicalDistinctKey(kind, value))) {
                values.set(output++, value);
            }
        }
        values.size(output);
    }
    private static void sort(LongValueBuffer values, PrimitiveValueKind kind) {
        if (values.size() < 2) return;
        if (kind != PrimitiveValueKind.FLOAT
                && kind != PrimitiveValueKind.DOUBLE) {
            Arrays.sort(values.backing(), 0, values.size());
            return;
        }
        long[] scratch = new long[values.size()];
        mergeSort(values.backing(), scratch, 0, values.size(), kind);
    }
    private static void skip(LongValueBuffer values, long count) { if (count >= values.size()) { values.size(0); return; } int n = (int) count; int remaining = values.size() - n; System.arraycopy(values.backing(), n, values.backing(), 0, remaining); values.size(remaining); }
    private static boolean equal(PrimitiveValueKind kind, long a, long b) {
        if (kind == PrimitiveValueKind.FLOAT) {
            return Float.floatToIntBits(Float.intBitsToFloat((int) a))
                    == Float.floatToIntBits(Float.intBitsToFloat((int) b));
        }
        if (kind == PrimitiveValueKind.DOUBLE) {
            return Double.doubleToLongBits(Double.longBitsToDouble(a))
                    == Double.doubleToLongBits(Double.longBitsToDouble(b));
        }
        return a == b;
    }

    private static long canonicalDistinctKey(
            PrimitiveValueKind kind,
            long raw) {
        if (kind == PrimitiveValueKind.FLOAT) {
            return (long) Float.floatToIntBits(
                    Float.intBitsToFloat((int) raw));
        }
        if (kind == PrimitiveValueKind.DOUBLE) {
            return Double.doubleToLongBits(Double.longBitsToDouble(raw));
        }
        return raw;
    }

    private static void mergeSort(
            long[] values,
            long[] scratch,
            int from,
            int to,
            PrimitiveValueKind kind) {
        int length = to - from;
        if (length < 2) return;
        int middle = from + length / 2;
        mergeSort(values, scratch, from, middle, kind);
        mergeSort(values, scratch, middle, to, kind);
        int left = from;
        int right = middle;
        int output = from;
        while (left < middle && right < to) {
            if (compare(kind, values[left], values[right]) <= 0) {
                scratch[output++] = values[left++];
            } else {
                scratch[output++] = values[right++];
            }
        }
        while (left < middle) scratch[output++] = values[left++];
        while (right < to) scratch[output++] = values[right++];
        System.arraycopy(scratch, from, values, from, length);
    }

    /** Unboxed canonical-value set used by primitive distinct. */
    private static final class PrimitiveDistinctSet {
        private final long[] keys;
        private final byte[] occupied;
        private final int mask;

        PrimitiveDistinctSet(int expected, Object provenance) {
            if (expected > (1 << 29)) {
                throw SomaFailures.failure(
                        SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                        SomaOperation.QUERY,
                        "primitive distinct hash table exceeds Java array boundary",
                        provenance);
            }
            int capacity = 2;
            int required = Math.max(2, expected << 1);
            while (capacity < required) capacity <<= 1;
            keys = new long[capacity];
            occupied = new byte[capacity];
            mask = capacity - 1;
        }

        boolean add(long value) {
            long mixed = value;
            mixed ^= mixed >>> 33;
            mixed *= 0xff51afd7ed558ccdL;
            mixed ^= mixed >>> 33;
            mixed *= 0xc4ceb9fe1a85ec53L;
            mixed ^= mixed >>> 33;
            int slot = ((int) mixed) & mask;
            while (occupied[slot] != 0) {
                if (keys[slot] == value) return false;
                slot = (slot + 1) & mask;
            }
            occupied[slot] = 1;
            keys[slot] = value;
            return true;
        }
    }
    private static int compare(PrimitiveValueKind kind, long a, long b) {
        switch (kind) {
            case BOOLEAN: return Boolean.compare(a != 0L, b != 0L);
            case BYTE: return Byte.compare((byte) a, (byte) b);
            case SHORT: return Short.compare((short) a, (short) b);
            case CHAR: return Character.compare((char) a, (char) b);
            case INT: return Integer.compare((int) a, (int) b);
            case LONG: return Long.compare(a, b);
            case FLOAT: return Float.compare(Float.intBitsToFloat((int) a), Float.intBitsToFloat((int) b));
            case DOUBLE: return Double.compare(Double.longBitsToDouble(a), Double.longBitsToDouble(b));
            default: throw new AssertionError();
        }
    }

    private static long integralValue(PrimitiveValueKind kind, long raw) {
        switch (kind) {
            case BYTE: return (byte) raw;
            case SHORT: return (short) raw;
            case CHAR: return (char) raw;
            case INT: return (int) raw;
            case LONG: return raw;
            default: throw new AssertionError("not an integral primitive kind");
        }
    }

    private static double floatingValue(PrimitiveValueKind kind, long raw) {
        if (kind == PrimitiveValueKind.FLOAT) {
            return Float.intBitsToFloat((int) raw);
        }
        if (kind == PrimitiveValueKind.DOUBLE) {
            return Double.longBitsToDouble(raw);
        }
        throw new AssertionError("not a floating primitive kind");
    }

    private static Object box(PrimitiveValueKind kind, long raw) {
        switch (kind) {
            case BOOLEAN: return Boolean.valueOf(raw != 0L);
            case BYTE: return Byte.valueOf((byte) raw);
            case SHORT: return Short.valueOf((short) raw);
            case CHAR: return Character.valueOf((char) raw);
            case INT: return Integer.valueOf((int) raw);
            case LONG: return Long.valueOf(raw);
            case FLOAT: return Float.valueOf(Float.intBitsToFloat((int) raw));
            case DOUBLE: return Double.valueOf(Double.longBitsToDouble(raw));
            default: throw new AssertionError();
        }
    }

    private static double canonicalSum(double[] values, int size) {
        int blocks = 0;
        for (int start = 0; start < size;) {
            int length = Math.min(1024, size - start);
            values[blocks++] = pairwise(
                    values, start, length);
            start += length;
        }
        return pairwise(values, 0, blocks);
    }
    private static double pairwise(double[] values, int start, int length) {
        if (length == 0) return 0.0d;
        for (int width = 1; width < length;) {
            for (long offset = 0L;
                    offset + width < length;
                    offset += (long) width << 1) {
                values[start + (int) offset] +=
                        values[start + (int) offset + width];
            }
            if (width > Integer.MAX_VALUE / 2) break;
            width <<= 1;
        }
        return values[start];
    }

    private static boolean isDirectFieldProjection(
            CanonicalRowOperation source,
            int fieldIndex) {
        return source.sourceKind == CanonicalRowOperation.SourceKind.TABLE
                && source.stages.size() == 1
                && source.stages.get(0).kind
                        == CanonicalRowStage.Kind.FIELD_PROJECT
                && source.stages.get(0).fieldIndex == fieldIndex;
    }

    interface Visitor extends TableChunk.PrimitiveVisitor {}
    interface Terminal<T> {
        T run(
                BoundCanonicalRowOperation bound,
                CanonicalRowExecutionFrame frame,
                CanonicalPrimitiveOperation plan);
    }

    private static final class PrimitiveExecution {
        final GeneratedTable table;
        final LogicalRowPlan frontend;
        final CanonicalPrimitiveOperation operation;

        PrimitiveExecution(
                GeneratedTable table,
                LogicalRowPlan frontend,
                CanonicalPrimitiveOperation operation) {
            this.table = table;
            this.frontend = frontend;
            this.operation = operation;
        }
    }

    private static final class FloatingResult { final long count; final double min, max, sum; FloatingResult(long count, double min, double max, double sum) { this.count = count; this.min = min; this.max = max; this.sum = sum; } }
}
