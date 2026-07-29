package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

import java.util.Arrays;

/**
 * Closed primitive lowering for required long-column common chains.
 *
 * <p>This package-private object owns primitive evaluation and the packed outer
 * loop; it is not an application SPI.</p>
 */
final class ClosedLongExpression {
    static final byte ADD = 1;
    static final byte SUBTRACT = 2;
    static final byte MULTIPLY = 3;
    static final byte DIVIDE = 4;
    static final byte BITWISE_AND = 5;

    private final int column;
    private final byte[] operations;
    private final long[] operands;
    private final String[] failurePaths;
    private final String[] failureOperations;

    private ClosedLongExpression(
            int column,
            byte[] operations,
            long[] operands,
            String[] failurePaths,
            String[] failureOperations) {
        this.column = column;
        this.operations = operations;
        this.operands = operands;
        this.failurePaths = failurePaths;
        this.failureOperations = failureOperations;
    }

    static ClosedLongExpression column(int column) {
        return new ClosedLongExpression(
                column,
                new byte[0],
                new long[0],
                new String[0],
                new String[0]);
    }

    ClosedLongExpression append(
            byte operation,
            long operand,
            String failurePath,
            String failureOperation) {
        byte[] nextOperations = Arrays.copyOf(
                operations, operations.length + 1);
        long[] nextOperands = Arrays.copyOf(
                operands, operands.length + 1);
        String[] nextFailurePaths = Arrays.copyOf(
                failurePaths, failurePaths.length + 1);
        String[] nextFailureOperations = Arrays.copyOf(
                failureOperations, failureOperations.length + 1);
        nextOperations[operations.length] = operation;
        nextOperands[operands.length] = operand;
        nextFailurePaths[failurePaths.length] = failurePath;
        nextFailureOperations[failureOperations.length] = failureOperation;
        return new ClosedLongExpression(
                column,
                nextOperations,
                nextOperands,
                nextFailurePaths,
                nextFailureOperations);
    }

    long evaluate(DataFlowBinding binding, int index) {
        long value = binding.longValue(column, index);
        for (int operation = 0; operation < operations.length; operation++) {
            long operand = operands[operation];
            switch (operations[operation]) {
                case ADD:
                    value = IntegralArithmetic.add(
                            value,
                            operand,
                            failurePaths[operation],
                            failureOperations[operation]);
                    break;
                case SUBTRACT:
                    value = IntegralArithmetic.subtract(
                            value,
                            operand,
                            failurePaths[operation],
                            failureOperations[operation]);
                    break;
                case MULTIPLY:
                    value = IntegralArithmetic.multiply(
                            value,
                            operand,
                            failurePaths[operation],
                            failureOperations[operation]);
                    break;
                case DIVIDE:
                    value = IntegralArithmetic.divide(
                            value,
                            operand,
                            failurePaths[operation],
                            failureOperations[operation]);
                    break;
                case BITWISE_AND: value &= operand; break;
                default:
                    throw new IllegalStateException(
                            "unknown closed numeric operation");
            }
        }
        return value;
    }
}

final class ClosedNumericPredicate {
    private final ClosedLongExpression expression;
    private final long right;
    private final int comparison;

    ClosedNumericPredicate(
            ClosedLongExpression expression, long right, int comparison) {
        this.expression = expression;
        this.right = right;
        this.comparison = comparison;
    }

    boolean test(DataFlowBinding binding, int index) {
        long left = expression.evaluate(binding, index);
        switch (comparison) {
            case 0: return left == right;
            case 1: return left != right;
            case 2: return left < right;
            case 3: return left <= right;
            case 4: return left > right;
            case 5: return left >= right;
            default:
                throw new IllegalStateException(
                        "unknown closed numeric comparison");
        }
    }
}

final class ClosedBooleanKernel {
    private final ClosedNumericPredicate[] predicates;

    private ClosedBooleanKernel(ClosedNumericPredicate[] predicates) {
        this.predicates = predicates;
    }

    static ClosedBooleanKernel predicate(ClosedNumericPredicate predicate) {
        return new ClosedBooleanKernel(
                new ClosedNumericPredicate[] {predicate});
    }

    ClosedBooleanKernel and(ClosedBooleanKernel other) {
        ClosedNumericPredicate[] combined = Arrays.copyOf(
                predicates, predicates.length + other.predicates.length);
        System.arraycopy(
                other.predicates, 0, combined, predicates.length,
                other.predicates.length);
        return new ClosedBooleanKernel(combined);
    }

    boolean test(DataFlowBinding binding, int index) {
        for (ClosedNumericPredicate predicate : predicates) {
            if (!predicate.test(binding, index)) return false;
        }
        return true;
    }

    CandidateVisit visitPacked(
            ExecutionFrame frame,
            DataFlowBinding binding,
            int cardinality,
            CandidateVisitor visitor,
            String operation) {
        int scanned = 0;
        int matched = 0;
        for (int index = 0; index < cardinality; index++) {
            if ((index & 1023) == 0) frame.checkBoundary(operation);
            scanned++;
            if (!test(binding, index)) continue;
            int outputPosition = matched++;
            if (!visitor.accept(index, outputPosition)) break;
        }
        return new CandidateVisit(scanned, matched);
    }

    CandidateVisit countPacked(
            ExecutionFrame frame,
            DataFlowBinding binding,
            int cardinality,
            String operation) {
        int matched = 0;
        for (int index = 0; index < cardinality; index++) {
            if ((index & 1023) == 0) frame.checkBoundary(operation);
            if (test(binding, index)) matched++;
        }
        return new CandidateVisit(cardinality, matched);
    }

    CandidateSelection selectPacked(
            ExecutionFrame frame,
            DataFlowBinding binding,
            int cardinality,
            String operation) {
        int[] indexes = frame.newScratchIndexes(cardinality, operation);
        int matched = 0;
        for (int index = 0; index < cardinality; index++) {
            if ((index & 1023) == 0) frame.checkBoundary(operation);
            if (test(binding, index)) indexes[matched++] = index;
        }
        return new CandidateSelection(indexes, matched, cardinality);
    }
}
