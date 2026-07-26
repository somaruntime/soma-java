package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

import java.util.Comparator;
import java.util.Objects;

interface BooleanNode {
    boolean evaluate(
            ExecutionFrame frame, DataFlowBinding binding, int index);

    String canonical();
}

interface LongNode {
    long evaluate(
            ExecutionFrame frame, DataFlowBinding binding, int index);

    String canonical();
}

interface DoubleNode {
    double evaluate(
            ExecutionFrame frame, DataFlowBinding binding, int index);

    String canonical();
}

interface ObjectNode {
    Object evaluate(
            ExecutionFrame frame, DataFlowBinding binding, int index);

    String canonical();
}

interface OrderNode {
    int compare(
            ExecutionFrame frame,
            DataFlowBinding binding,
            int left,
            int right);

    String canonical();
}

final class ExpressionNodes {
    private static final BooleanNode PRESENT = new BooleanNode() {
        @Override
        public boolean evaluate(
                ExecutionFrame frame, DataFlowBinding binding, int index) {
            return true;
        }

        @Override
        public String canonical() {
            return "required";
        }
    };

    private ExpressionNodes() {
    }

    static BooleanNode alwaysPresent() {
        return PRESENT;
    }

    static boolean isAlwaysPresent(BooleanNode value) {
        return value == PRESENT;
    }

    static BooleanNode present(final int columnOrdinal, final String path) {
        return new BooleanNode() {
            @Override
            public boolean evaluate(
                    ExecutionFrame frame,
                    DataFlowBinding binding,
                    int index) {
                return binding.isPresent(columnOrdinal, index);
            }

            @Override
            public String canonical() {
                return "present(" + path + ")";
            }
        };
    }

    static LongNode longField(final int columnOrdinal, final String path) {
        return new LongNode() {
            @Override
            public long evaluate(
                    ExecutionFrame frame,
                    DataFlowBinding binding,
                    int index) {
                return binding.longValue(columnOrdinal, index);
            }

            @Override
            public String canonical() {
                return "long-column(" + path + ")";
            }
        };
    }

    static DoubleNode doubleField(final int columnOrdinal, final String path) {
        return new DoubleNode() {
            @Override
            public double evaluate(
                    ExecutionFrame frame,
                    DataFlowBinding binding,
                    int index) {
                return binding.doubleValue(columnOrdinal, index);
            }

            @Override
            public String canonical() {
                return "double-column(" + path + ")";
            }
        };
    }

    static BooleanNode booleanField(final int columnOrdinal, final String path) {
        return new BooleanNode() {
            @Override
            public boolean evaluate(
                    ExecutionFrame frame,
                    DataFlowBinding binding,
                    int index) {
                return binding.booleanValue(columnOrdinal, index);
            }

            @Override
            public String canonical() {
                return "boolean-column(" + path + ")";
            }
        };
    }

    static ObjectNode objectField(final int columnOrdinal, final String path) {
        return new ObjectNode() {
            @Override
            public Object evaluate(
                    ExecutionFrame frame,
                    DataFlowBinding binding,
                    int index) {
                return binding.objectValue(columnOrdinal, index);
            }

            @Override
            public String canonical() {
                return "object-column(" + path + ")";
            }
        };
    }

    static void requirePresent(
            ExecutionFrame frame,
            BooleanNode presence,
            DataFlowBinding binding,
            int index,
            String path) {
        if (!presence.evaluate(frame, binding, index)) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_absent_value", path, "dataflow.expression");
        }
    }

    static BooleanNode andPresence(
            final BooleanNode left, final BooleanNode right) {
        if (left == PRESENT) {
            return right;
        }
        if (right == PRESENT) {
            return left;
        }
        return new BooleanNode() {
            @Override
            public boolean evaluate(
                    ExecutionFrame frame,
                    DataFlowBinding binding,
                    int index) {
                return left.evaluate(frame, binding, index)
                        && right.evaluate(frame, binding, index);
            }

            @Override
            public String canonical() {
                return "and-presence(" + left.canonical() + ","
                        + right.canonical() + ")";
            }
        };
    }

    static BooleanNode negate(final BooleanNode value) {
        return new BooleanNode() {
            @Override
            public boolean evaluate(
                    ExecutionFrame frame,
                    DataFlowBinding binding,
                    int index) {
                return !value.evaluate(frame, binding, index);
            }

            @Override
            public String canonical() {
                return "not(" + value.canonical() + ")";
            }
        };
    }

    static BooleanNode and(final BooleanNode left, final BooleanNode right) {
        return new BooleanNode() {
            @Override
            public boolean evaluate(
                    ExecutionFrame frame,
                    DataFlowBinding binding,
                    int index) {
                return left.evaluate(frame, binding, index)
                        && right.evaluate(frame, binding, index);
            }

            @Override
            public String canonical() {
                return "and(" + left.canonical() + "," + right.canonical() + ")";
            }
        };
    }

    static BooleanNode or(final BooleanNode left, final BooleanNode right) {
        return new BooleanNode() {
            @Override
            public boolean evaluate(
                    ExecutionFrame frame,
                    DataFlowBinding binding,
                    int index) {
                return left.evaluate(frame, binding, index)
                        || right.evaluate(frame, binding, index);
            }

            @Override
            public String canonical() {
                return "or(" + left.canonical() + "," + right.canonical() + ")";
            }
        };
    }

    static int compareObjects(Object left, Object right, Comparator<Object> comparator) {
        if (comparator != null) {
            return comparator.compare(left, right);
        }
        @SuppressWarnings("unchecked")
        Comparable<Object> comparable = (Comparable<Object>) left;
        return comparable.compareTo(right);
    }

    static boolean equalObjects(Object left, Object right) {
        return Objects.equals(left, right);
    }
}
