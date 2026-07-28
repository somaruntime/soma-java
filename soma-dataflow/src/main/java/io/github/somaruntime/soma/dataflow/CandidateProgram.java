package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;
import io.github.somaruntime.soma.dataflow.generated.CandidateIndexAccess;
import io.github.somaruntime.soma.dataflow.generated.PointIndexAccess;
import io.github.somaruntime.soma.dataflow.generated.SnapshotGatherAccess;
import io.github.somaruntime.soma.runtime.IndexSnapshot;

import java.util.Collections;
import java.util.List;

final class CandidatePlan<B extends DataFlowBinding> {
    private final CandidateInput<B> input;
    private final CandidateStage tail;
    private final int stageCount;

    CandidatePlan(SourceSlot<B> source) {
        this(new PackedCandidateInput<B>(source), null, 0);
    }

    CandidatePlan(CandidateInput<B> input) {
        this(input, null, 0);
    }

    private CandidatePlan(
            CandidateInput<B> input, CandidateStage tail, int stageCount) {
        this.input = input;
        this.tail = tail;
        this.stageCount = stageCount;
    }

    SourceSlot<B> source() {
        return input.source();
    }

    CandidatePlan<B> filter(BooleanExpression<B> expression) {
        return append(CandidateProgram.FILTER, expression, 0L);
    }

    CandidatePlan<B> skip(long count) {
        return append(CandidateProgram.SKIP, null, count);
    }

    CandidatePlan<B> limit(long count) {
        return append(CandidateProgram.LIMIT, null, count);
    }

    CandidatePlan<B> sort(CandidateOrder<B> order) {
        return append(CandidateProgram.SORT, order, 0L);
    }

    CandidateProgram<B> compileProgram() {
        byte[] kinds = new byte[stageCount];
        Object[] operands = new Object[stageCount];
        long[] arguments = new long[stageCount];
        CandidateStage stage = tail;
        for (int index = stageCount - 1; index >= 0; index--) {
            kinds[index] = stage.kind;
            operands[index] = stage.operand;
            arguments[index] = stage.argument;
            stage = stage.previous;
        }
        return new CandidateProgram<B>(
                input, kinds, operands, arguments);
    }

    private CandidatePlan<B> append(byte kind, Object operand, long argument) {
        if (stageCount == Integer.MAX_VALUE) {
            throw new IllegalStateException("candidate stage count overflow");
        }
        return new CandidatePlan<B>(
                input,
                new CandidateStage(tail, kind, operand, argument),
                stageCount + 1);
    }
}

final class CandidateStage {
    final CandidateStage previous;
    final byte kind;
    final Object operand;
    final long argument;

    CandidateStage(
            CandidateStage previous, byte kind, Object operand, long argument) {
        this.previous = previous;
        this.kind = kind;
        this.operand = operand;
        this.argument = argument;
    }
}

interface CandidateVisitor {
    boolean accept(int index, int outputPosition);
}

final class CandidateVisit {
    final long scanned;
    final int matched;

    CandidateVisit(long scanned, int matched) {
        this.scanned = scanned;
        this.matched = matched;
    }
}

enum CandidatePhysicalShape {
    CONTIGUOUS_RANGE,
    SEGMENT_RANGE,
    EXACT_SINGLE_PASS,
    POINT_SINGLE,
    SPARSE_INDEXES
}

final class CandidateSelection {
    final CandidatePhysicalShape shape;
    private final int start;
    private final int[] indexes;
    final int size;
    final long scanned;

    CandidateSelection(int[] indexes, int size, long scanned) {
        this(CandidatePhysicalShape.SPARSE_INDEXES, 0, indexes, size, scanned);
    }

    private CandidateSelection(
            CandidatePhysicalShape shape,
            int start,
            int[] indexes,
            int size,
            long scanned) {
        this.shape = shape;
        this.start = start;
        this.indexes = indexes;
        this.size = size;
        this.scanned = scanned;
    }

    static CandidateSelection range(
            DataFlowBinding binding, int start, int size, long scanned) {
        return new CandidateSelection(
                binding.segmentedStorage()
                        ? CandidatePhysicalShape.SEGMENT_RANGE
                        : CandidatePhysicalShape.CONTIGUOUS_RANGE,
                start,
                null,
                size,
                scanned);
    }

    static CandidateSelection point(int index, long scanned) {
        return index < 0
                ? new CandidateSelection(
                        CandidatePhysicalShape.POINT_SINGLE,
                        0,
                        null,
                        0,
                        scanned)
                : new CandidateSelection(
                        CandidatePhysicalShape.POINT_SINGLE,
                        index,
                        null,
                        1,
                        scanned);
    }

    int indexAt(int position) {
        if (position < 0 || position >= size) {
            throw new IndexOutOfBoundsException(
                    "candidate position out of range");
        }
        return shape == CandidatePhysicalShape.SPARSE_INDEXES
                ? indexes[position] : start + position;
    }

    int[] materializedIndexes(ExecutionFrame frame, String operation) {
        if (shape == CandidatePhysicalShape.SPARSE_INDEXES) {
            return indexes;
        }
        int[] result = frame.newScratchIndexes(size, operation);
        for (int position = 0; position < size; position++) {
            result[position] = start + position;
        }
        return result;
    }
}

interface CandidateInput<B extends DataFlowBinding> {
    SourceSlot<B> source();

    CandidateVisit visit(
            ExecutionFrame frame, CandidateVisitor visitor, String operation);

    CandidateSelection select(ExecutionFrame frame, String operation);

    int maximumCardinality(DataFlowBinding binding);

    default int selectionCardinality(ExecutionFrame frame) {
        return maximumCardinality(frame.binding(source()));
    }

    boolean supportsStreaming();

    String canonical();

    default String physicalForm() {
        return "sparse-indexes";
    }

    default List<ParameterSlot<?>> requiredParameters() {
        return Collections.emptyList();
    }
}

final class PackedCandidateInput<B extends DataFlowBinding>
        implements CandidateInput<B> {
    private final SourceSlot<B> source;

    PackedCandidateInput(SourceSlot<B> source) {
        this.source = source;
    }

    @Override
    public SourceSlot<B> source() {
        return source;
    }

    @Override
    public CandidateVisit visit(
            ExecutionFrame frame, CandidateVisitor visitor, String operation) {
        int cardinality = frame.binding(source).packedSize();
        int visited = 0;
        for (int index = 0; index < cardinality; index++) {
            if ((index & 1023) == 0) {
                frame.checkBoundary(operation);
            }
            visited++;
            if (!visitor.accept(index, index)) {
                break;
            }
        }
        return new CandidateVisit(visited, visited);
    }

    @Override
    public CandidateSelection select(ExecutionFrame frame, String operation) {
        DataFlowBinding binding = frame.binding(source);
        int cardinality = binding.packedSize();
        return CandidateSelection.range(
                binding, 0, cardinality, cardinality);
    }

    @Override
    public int maximumCardinality(DataFlowBinding binding) {
        return binding.packedSize();
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public String canonical() {
        return "packed";
    }

    @Override
    public String physicalForm() {
        return "range[storage-layout-aware]";
    }
}

final class ExactCandidateInput<B extends DataFlowBinding>
        implements CandidateInput<B> {
    private final SourceSlot<B> source;
    private final CandidateIndexAccess<B> access;

    ExactCandidateInput(
            SourceSlot<B> source, CandidateIndexAccess<B> access) {
        this.source = source;
        this.access = access;
    }

    @Override
    public SourceSlot<B> source() {
        return source;
    }

    @Override
    public CandidateVisit visit(
            ExecutionFrame frame, CandidateVisitor visitor, String operation) {
        B binding = binding(frame);
        int group = access.group(binding);
        if (group < 0) {
            return new CandidateVisit(0L, 0);
        }
        int expected = access.size(binding, group);
        int current = access.first(binding, group);
        int visited = 0;
        while (current >= 0) {
            if ((visited & 1023) == 0) {
                frame.checkBoundary(operation);
            }
            int index = current;
            current = access.next(binding, current);
            visited++;
            if (!visitor.accept(index, visited - 1)) {
                break;
            }
        }
        if (visited > expected || (current < 0 && visited != expected)) {
            throw DataFlowFailures.internal(
                    "dataflow_exact_source_sequence",
                    source.alias(),
                    operation,
                    access.identity());
        }
        return new CandidateVisit(visited, visited);
    }

    @Override
    public CandidateSelection select(
            ExecutionFrame frame, String operation) {
        B binding = binding(frame);
        int group = access.group(binding);
        if (group < 0) {
            return new CandidateSelection(
                    frame.newScratchIndexes(0, operation), 0, 0L);
        }
        int size = access.size(binding, group);
        int[] indexes = frame.newScratchIndexes(size, operation);
        int current = access.first(binding, group);
        int write = 0;
        while (current >= 0 && write < size) {
            if ((write & 1023) == 0) {
                frame.checkBoundary(operation);
            }
            indexes[write++] = current;
            current = access.next(binding, current);
        }
        if (write != size || current >= 0) {
            throw DataFlowFailures.internal(
                    "dataflow_exact_source_sequence",
                    source.alias(),
                    operation,
                    access.identity());
        }
        return new CandidateSelection(indexes, size, size);
    }

    @Override
    public int maximumCardinality(DataFlowBinding value) {
        @SuppressWarnings("unchecked")
        B binding = (B) value;
        int group = access.group(binding);
        return group < 0 ? 0 : access.size(binding, group);
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public String canonical() {
        return "exact(" + access.identity() + ")";
    }

    @Override
    public String physicalForm() {
        return "exact-single-pass";
    }

    @SuppressWarnings("unchecked")
    private B binding(ExecutionFrame frame) {
        return (B) frame.binding(source);
    }
}

final class PointCandidateInput<B extends DataFlowBinding>
        implements CandidateInput<B> {
    private final SourceSlot<B> source;
    private final PointIndexAccess<B> access;

    PointCandidateInput(SourceSlot<B> source, PointIndexAccess<B> access) {
        this.source = source;
        this.access = access;
    }

    @Override
    public SourceSlot<B> source() {
        return source;
    }

    @Override
    public CandidateVisit visit(
            ExecutionFrame frame, CandidateVisitor visitor, String operation) {
        int index = locate(frame);
        if (index < 0) {
            return new CandidateVisit(1L, 0);
        }
        visitor.accept(index, 0);
        return new CandidateVisit(1L, 1);
    }

    @Override
    public CandidateSelection select(ExecutionFrame frame, String operation) {
        int index = locate(frame);
        return CandidateSelection.point(index, 1L);
    }

    @Override
    public int maximumCardinality(DataFlowBinding binding) {
        return 1;
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public String canonical() {
        return "point(" + access.identity() + ")";
    }

    @Override
    public String physicalForm() {
        return "point-single";
    }

    @SuppressWarnings("unchecked")
    private int locate(ExecutionFrame frame) {
        return access.index((B) frame.binding(source));
    }
}

final class SnapshotCandidateInput<B extends DataFlowBinding>
        implements CandidateInput<B> {
    private final SourceSlot<B> source;
    private final ParameterSlot<IndexSnapshot> snapshot;
    private final SnapshotGatherAccess<B> access;
    private final List<ParameterSlot<?>> requiredParameters;

    SnapshotCandidateInput(
            SourceSlot<B> source,
            ParameterSlot<IndexSnapshot> snapshot,
            SnapshotGatherAccess<B> access) {
        this.source = source;
        this.snapshot = snapshot;
        this.access = access;
        requiredParameters = Collections.<ParameterSlot<?>>singletonList(snapshot);
    }

    @Override
    public SourceSlot<B> source() {
        return source;
    }

    @Override
    public CandidateVisit visit(
            ExecutionFrame frame, CandidateVisitor visitor, String operation) {
        IndexSnapshot value = checked(frame);
        int visited = 0;
        for (int position = 0; position < value.size(); position++) {
            if ((position & 1023) == 0) {
                frame.checkBoundary(operation);
            }
            visited++;
            if (!visitor.accept(value.indexAt(position), position)) {
                break;
            }
        }
        return new CandidateVisit(value.size(), visited);
    }

    @Override
    public CandidateSelection select(ExecutionFrame frame, String operation) {
        IndexSnapshot value = checked(frame);
        int[] indexes = frame.newScratchIndexes(value.size(), operation);
        for (int position = 0; position < value.size(); position++) {
            indexes[position] = value.indexAt(position);
        }
        return new CandidateSelection(indexes, value.size(), value.size());
    }

    @Override
    public int maximumCardinality(DataFlowBinding binding) {
        return Integer.MAX_VALUE;
    }

    @Override
    public int selectionCardinality(ExecutionFrame frame) {
        return checked(frame).size();
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public String canonical() {
        return "index-snapshot-gather(" + snapshot.canonical() + ","
                + access.identity() + ")";
    }

    @Override
    public List<ParameterSlot<?>> requiredParameters() {
        return requiredParameters;
    }

    @SuppressWarnings("unchecked")
    private IndexSnapshot checked(ExecutionFrame frame) {
        IndexSnapshot value = frame.parameter(snapshot);
        access.requireCurrent((B) frame.binding(source), value);
        return value;
    }
}

final class CombinedCandidateInput<B extends DataFlowBinding>
        implements CandidateInput<B> {
    private final CandidateProgram<B> first;
    private final CandidateProgram<B> second;

    CombinedCandidateInput(
            CandidateProgram<B> first, CandidateProgram<B> second) {
        if (first.source() != second.source()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_combine_lineage_mismatch",
                    first.source().alias(),
                    "dataflow.combine");
        }
        this.first = first;
        this.second = second;
    }

    @Override
    public SourceSlot<B> source() {
        return first.source();
    }

    @Override
    public CandidateVisit visit(
            ExecutionFrame frame, CandidateVisitor visitor, String operation) {
        CandidateSelection selected = select(frame, operation);
        int visited = 0;
        for (int position = 0; position < selected.size; position++) {
            if ((position & 1023) == 0) {
                frame.checkBoundary(operation);
            }
            visited++;
            if (!visitor.accept(selected.indexAt(position), position)) {
                break;
            }
        }
        return new CandidateVisit(selected.scanned, visited);
    }

    @Override
    public CandidateSelection select(ExecutionFrame frame, String operation) {
        CandidateSelection left = first.select(frame, operation);
        CandidateSelection right = second.select(frame, operation);
        long total = (long) left.size + (long) right.size;
        if (total > Integer.MAX_VALUE) {
            throw DataFlowFailures.resource(
                    "dataflow_cardinality_overflow",
                    source().alias(),
                    operation,
                    Long.toString(total));
        }
        int[] indexes = frame.newScratchIndexes((int) total, operation);
        for (int position = 0; position < left.size; position++) {
            indexes[position] = left.indexAt(position);
        }
        for (int position = 0; position < right.size; position++) {
            indexes[left.size + position] = right.indexAt(position);
        }
        return new CandidateSelection(
                indexes,
                (int) total,
                left.scanned + right.scanned);
    }

    @Override
    public int maximumCardinality(DataFlowBinding binding) {
        long total = (long) first.maximumCardinality(binding)
                + (long) second.maximumCardinality(binding);
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    @Override
    public boolean supportsStreaming() {
        return false;
    }

    @Override
    public String canonical() {
        return "combine(" + first.canonical() + "," + second.canonical() + ")";
    }

    @Override
    public List<ParameterSlot<?>> requiredParameters() {
        return DataFlowSupport.unionParameters(
                first.requiredParameters(), second.requiredParameters());
    }
}

final class ProgramCandidateInput<B extends DataFlowBinding>
        implements CandidateInput<B> {
    private final CandidateProgram<B> program;

    ProgramCandidateInput(CandidateProgram<B> program) {
        this.program = program;
    }

    @Override
    public SourceSlot<B> source() {
        return program.source();
    }

    @Override
    public CandidateVisit visit(
            ExecutionFrame frame, CandidateVisitor visitor, String operation) {
        return program.visit(frame, visitor, operation);
    }

    @Override
    public CandidateSelection select(ExecutionFrame frame, String operation) {
        return program.select(frame, operation);
    }

    @Override
    public int maximumCardinality(DataFlowBinding binding) {
        return program.maximumCardinality(binding);
    }

    @Override
    public boolean supportsStreaming() {
        return !program.requiresBarrier();
    }

    @Override
    public String canonical() {
        return program.canonical();
    }

    @Override
    public String physicalForm() {
        return program.physicalForm();
    }

    @Override
    public List<ParameterSlot<?>> requiredParameters() {
        return program.requiredParameters();
    }
}

final class CandidateProgram<B extends DataFlowBinding> {
    static final byte FILTER = 1;
    static final byte SKIP = 2;
    static final byte LIMIT = 3;
    static final byte SORT = 4;
    private static final long[] EMPTY_REMAINING = new long[0];

    private final CandidateInput<B> input;
    private final SourceSlot<B> source;
    private final byte[] kinds;
    private final Object[] operands;
    private final long[] arguments;
    private final boolean hasSort;
    private final boolean contiguousParallelSafe;
    private final boolean branchParallelSafe;
    private final List<ParameterSlot<?>> requiredParameters;
    private final String canonical;

    CandidateProgram(
            CandidateInput<B> input,
            byte[] kinds,
            Object[] operands,
            long[] arguments) {
        this.input = input;
        this.source = input.source();
        this.kinds = kinds;
        this.operands = operands;
        this.arguments = arguments;
        boolean sorting = false;
        boolean parallelSafe = input instanceof PackedCandidateInput<?>;
        boolean branchSafe = true;
        List<ParameterSlot<?>> parameters = input.requiredParameters();
        StringBuilder identity = new StringBuilder(input.canonical());
        for (int index = 0; index < kinds.length; index++) {
            byte kind = kinds[index];
            if (kind == FILTER) {
                BooleanExpression<?> expression =
                        (BooleanExpression<?>) operands[index];
                parallelSafe = parallelSafe
                        && expression.parallelSafe;
                branchSafe = branchSafe && expression.parallelSafe;
                parameters = DataFlowSupport.unionParameters(
                        parameters, expression.parameters);
                identity.append("->filter(")
                        .append(expression.identity())
                        .append(')');
            } else if (kind == SKIP) {
                parallelSafe = false;
                identity.append("->skip(").append(arguments[index]).append(')');
            } else if (kind == LIMIT) {
                parallelSafe = false;
                identity.append("->limit(").append(arguments[index]).append(')');
            } else if (kind == SORT) {
                CandidateOrder<?> order =
                        (CandidateOrder<?>) operands[index];
                sorting = true;
                parallelSafe = false;
                branchSafe = branchSafe && order.parallelSafe;
                parameters = DataFlowSupport.unionParameters(
                        parameters, order.parameters);
                identity.append("->sort(")
                        .append(order.identity())
                        .append(')');
            }
        }
        hasSort = sorting;
        contiguousParallelSafe = parallelSafe;
        branchParallelSafe = branchSafe;
        requiredParameters = parameters;
        canonical = identity.toString();
    }

    SourceSlot<B> source() {
        return source;
    }

    List<ParameterSlot<?>> requiredParameters() {
        return requiredParameters;
    }

    boolean hasSort() {
        return hasSort;
    }

    boolean requiresBarrier() {
        return hasSort || !input.supportsStreaming();
    }

    boolean supportsContiguousParallel() {
        return contiguousParallelSafe;
    }

    boolean parallelBranchSafe() {
        return branchParallelSafe;
    }

    int contiguousCardinality(DataFlowBinding binding) {
        if (!contiguousParallelSafe) {
            throw new IllegalStateException(
                    "candidate program is not contiguous-parallel safe");
        }
        return input.maximumCardinality(binding);
    }

    boolean parallelMatches(
            ExecutionFrame frame, DataFlowBinding binding, int index) {
        if (!contiguousParallelSafe) {
            throw new IllegalStateException(
                    "candidate program is not contiguous-parallel safe");
        }
        for (int stage = 0; stage < kinds.length; stage++) {
            @SuppressWarnings("unchecked")
            BooleanExpression<B> expression =
                    (BooleanExpression<B>) operands[stage];
            if (!expression.evaluate(frame, binding, index)) {
                return false;
            }
        }
        return true;
    }

    String canonical() {
        return canonical;
    }

    String physicalForm() {
        return requiresBarrier()
                ? "sparse-indexes" : input.physicalForm();
    }

    CandidateVisit visit(
            ExecutionFrame frame, CandidateVisitor visitor, String operation) {
        if (hasSort || !input.supportsStreaming()) {
            CandidateSelection selected = select(frame, operation);
            int visited = 0;
            for (int position = 0; position < selected.size; position++) {
                if ((position & 1023) == 0) {
                    frame.checkBoundary(operation);
                }
                visited++;
                if (!visitor.accept(selected.indexAt(position), position)) {
                    break;
                }
            }
            return new CandidateVisit(selected.scanned, visited);
        }
        final DataFlowBinding binding = frame.binding(source);
        final long[] remaining = initialRemaining();
        final int[] matched = new int[1];
        CandidateVisit sourceVisit = input.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(int index, int inputPosition) {
                        if (limitExhausted(remaining)) {
                            return false;
                        }
                        if (matches(frame, binding, index, remaining)) {
                            int outputPosition = matched[0]++;
                            if (!visitor.accept(index, outputPosition)) {
                                return false;
                            }
                        }
                        return !limitExhausted(remaining);
                    }
                },
                operation);
        return new CandidateVisit(sourceVisit.scanned, matched[0]);
    }

    CandidateSelection select(ExecutionFrame frame, String operation) {
        DataFlowBinding binding = frame.binding(source);
        int inputCardinality = input.selectionCardinality(frame);
        int boundedCardinality = upperBound(inputCardinality);
        if (!hasSort
                && input.supportsStreaming()
                && boundedCardinality < inputCardinality) {
            return selectBoundedStreaming(
                    frame, operation, boundedCardinality);
        }
        CandidateSelection base = input.select(frame, operation);
        int[] indexes = base.materializedIndexes(frame, operation);
        int size = base.size;
        int[] auxiliary = null;
        for (int stage = 0; stage < kinds.length; stage++) {
            frame.checkBoundary(operation);
            byte kind = kinds[stage];
            if (kind == FILTER) {
                @SuppressWarnings("unchecked")
                BooleanExpression<B> expression =
                        (BooleanExpression<B>) operands[stage];
                int write = 0;
                for (int index = 0; index < size; index++) {
                    int candidate = indexes[index];
                    if (expression.evaluate(frame, binding, candidate)) {
                        indexes[write++] = candidate;
                    }
                }
                size = write;
            } else if (kind == SKIP) {
                int remove = (int) Math.min((long) size, arguments[stage]);
                if (remove != 0) {
                    System.arraycopy(indexes, remove, indexes, 0, size - remove);
                    size -= remove;
                }
            } else if (kind == LIMIT) {
                size = (int) Math.min((long) size, arguments[stage]);
            } else {
                if (size > 1) {
                    if (auxiliary == null || auxiliary.length < size) {
                        auxiliary = frame.newScratchIndexes(size, operation);
                    }
                    @SuppressWarnings("unchecked")
                    CandidateOrder<B> order = (CandidateOrder<B>) operands[stage];
                    stableSort(
                            indexes, auxiliary, size, binding, order, frame, operation);
                }
            }
        }
        return new CandidateSelection(indexes, size, base.scanned);
    }

    private CandidateSelection selectBoundedStreaming(
            ExecutionFrame frame,
            String operation,
            final int capacity) {
        final int[] indexes =
                frame.newScratchIndexes(capacity, operation);
        CandidateVisit visit = visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(
                            int index, int outputPosition) {
                        if (outputPosition < 0
                                || outputPosition >= indexes.length) {
                            throw DataFlowFailures.internal(
                                    "dataflow_candidate_bound_violation",
                                    source.alias(),
                                    "dataflow.select",
                                    canonical);
                        }
                        indexes[outputPosition] = index;
                        return true;
                    }
                },
                operation);
        if (visit.matched > capacity) {
            throw DataFlowFailures.internal(
                    "dataflow_candidate_bound_violation",
                    source.alias(),
                    "dataflow.select",
                    canonical);
        }
        return new CandidateSelection(
                indexes, visit.matched, visit.scanned);
    }

    int upperBound(int cardinality) {
        long result = cardinality;
        for (int stage = 0; stage < kinds.length; stage++) {
            if (kinds[stage] == SKIP) {
                result = Math.max(0L, result - arguments[stage]);
            } else if (kinds[stage] == LIMIT) {
                result = Math.min(result, arguments[stage]);
            }
        }
        return (int) result;
    }

    int maximumCardinality(DataFlowBinding binding) {
        return upperBound(input.maximumCardinality(binding));
    }

    private long[] initialRemaining() {
        if (kinds.length == 0) {
            return EMPTY_REMAINING;
        }
        long[] remaining = new long[kinds.length];
        for (int stage = 0; stage < kinds.length; stage++) {
            remaining[stage] = arguments[stage];
        }
        return remaining;
    }

    private boolean matches(
            ExecutionFrame frame,
            DataFlowBinding binding,
            int index,
            long[] remaining) {
        for (int stage = 0; stage < kinds.length; stage++) {
            byte kind = kinds[stage];
            if (kind == FILTER) {
                @SuppressWarnings("unchecked")
                BooleanExpression<B> expression =
                        (BooleanExpression<B>) operands[stage];
                if (!expression.evaluate(frame, binding, index)) {
                    return false;
                }
            } else if (kind == SKIP) {
                if (remaining[stage] > 0L) {
                    remaining[stage]--;
                    return false;
                }
            } else if (kind == LIMIT) {
                if (remaining[stage] <= 0L) {
                    return false;
                }
                remaining[stage]--;
            } else {
                throw DataFlowFailures.internal(
                        "dataflow_streaming_sort",
                        source.alias(),
                        "dataflow.execute",
                        canonical);
            }
        }
        return true;
    }

    private boolean limitExhausted(long[] remaining) {
        for (int stage = 0; stage < kinds.length; stage++) {
            if (kinds[stage] == LIMIT && remaining[stage] <= 0L) {
                return true;
            }
        }
        return false;
    }

    private static <B extends DataFlowBinding> void stableSort(
            int[] indexes,
            int[] auxiliary,
            int length,
            DataFlowBinding binding,
            CandidateOrder<B> order,
            ExecutionFrame frame,
            String operation) {
        for (int width = 1; width < length;
             width = width > length / 2 ? length : width * 2) {
            frame.checkBoundary(operation);
            for (int start = 0; start < length; start += width * 2) {
                int middle = Math.min(start + width, length);
                int end = Math.min(start + width * 2, length);
                int left = start;
                int right = middle;
                int write = start;
                while (left < middle || right < end) {
                    if (right >= end
                            || (left < middle
                            && order.node.compare(
                            frame,
                            binding,
                            indexes[left],
                            indexes[right]) <= 0)) {
                        auxiliary[write++] = indexes[left++];
                    } else {
                        auxiliary[write++] = indexes[right++];
                    }
                }
                System.arraycopy(auxiliary, start, indexes, start, end - start);
            }
        }
    }
}
