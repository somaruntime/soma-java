package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

import java.util.Arrays;
import java.util.List;

/**
 * Required, hashable composite key expression used by GroupBy and equi Join.
 *
 * <p>Components remain primitive/reference evaluators; no per-candidate tuple
 * object is created.</p>
 */
public final class KeyExpression<B extends DataFlowBinding> {
    private final SourceSlot<B> source;
    private final KeyComponent[] components;
    private final List<ParameterSlot<?>> parameters;
    private final boolean parallelSafe;
    private final String identity;

    private KeyExpression(
            SourceSlot<B> source,
            KeyComponent[] components,
            List<ParameterSlot<?>> parameters,
            boolean parallelSafe) {
        this.source = source;
        this.components = components;
        this.parameters = parameters;
        this.parallelSafe = parallelSafe;
        StringBuilder canonical = new StringBuilder("key-v1");
        for (KeyComponent component : components) {
            DataFlowSupport.appendCanonical(
                    canonical, "component", component.canonical());
        }
        identity = DataFlowSupport.identity(canonical.toString());
    }

    public static <B extends DataFlowBinding> KeyExpression<B> of(
            LongExpression<B> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        requireRequired(expression.required(), expression.path);
        return new KeyExpression<B>(
                expression.source,
                new KeyComponent[] {new LongKeyComponent(expression)},
                expression.parameters,
                expression.parallelSafe);
    }

    public static <B extends DataFlowBinding> KeyExpression<B> of(
            StringExpression<B> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        requireRequired(expression.required(), expression.path);
        return new KeyExpression<B>(
                expression.source,
                new KeyComponent[] {new StringKeyComponent(expression)},
                expression.parameters,
                expression.parallelSafe);
    }

    public static <B extends DataFlowBinding> KeyExpression<B> of(
            DoubleExpression<B> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        requireRequired(expression.required(), expression.path);
        return new KeyExpression<B>(
                expression.source,
                new KeyComponent[] {new DoubleKeyComponent(expression)},
                expression.parameters,
                expression.parallelSafe);
    }

    public static <B extends DataFlowBinding> KeyExpression<B> of(
            BooleanExpression<B> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        requireRequired(expression.required(), expression.path);
        return new KeyExpression<B>(
                expression.source,
                new KeyComponent[] {new BooleanKeyComponent(expression)},
                expression.parameters,
                expression.parallelSafe);
    }

    public KeyExpression<B> then(LongExpression<B> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        requireSource(expression.source);
        requireRequired(expression.required(), expression.path);
        return append(
                new LongKeyComponent(expression),
                expression.parameters,
                expression.parallelSafe);
    }

    public KeyExpression<B> then(StringExpression<B> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        requireSource(expression.source);
        requireRequired(expression.required(), expression.path);
        return append(
                new StringKeyComponent(expression),
                expression.parameters,
                expression.parallelSafe);
    }

    public KeyExpression<B> then(DoubleExpression<B> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        requireSource(expression.source);
        requireRequired(expression.required(), expression.path);
        return append(
                new DoubleKeyComponent(expression),
                expression.parameters,
                expression.parallelSafe);
    }

    public KeyExpression<B> then(BooleanExpression<B> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        requireSource(expression.source);
        requireRequired(expression.required(), expression.path);
        return append(
                new BooleanKeyComponent(expression),
                expression.parameters,
                expression.parallelSafe);
    }

    public String identity() {
        return identity;
    }

    SourceSlot<B> source() {
        return source;
    }

    List<ParameterSlot<?>> parameters() {
        return parameters;
    }

    boolean parallelSafe() {
        return parallelSafe;
    }

    long hash(
            ExecutionFrame frame, DataFlowBinding binding, int index) {
        long hash = 1469598103934665603L;
        for (KeyComponent component : components) {
            hash = (hash ^ component.hash(frame, binding, index))
                    * 1099511628211L;
        }
        return hash;
    }

    boolean equal(
            ExecutionFrame frame,
            DataFlowBinding leftBinding,
            int leftIndex,
            KeyExpression<?> right,
            DataFlowBinding rightBinding,
            int rightIndex) {
        for (int component = 0; component < components.length; component++) {
            if (!components[component].equal(
                    frame,
                    leftBinding,
                    leftIndex,
                    right.components[component],
                    rightBinding,
                    rightIndex)) {
                return false;
            }
        }
        return true;
    }

    void requireCompatible(KeyExpression<?> other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        if (components.length != other.components.length) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_key_shape_mismatch",
                    source.alias(),
                    "dataflow.key");
        }
        for (int index = 0; index < components.length; index++) {
            if (components[index].kind() != other.components[index].kind()) {
                throw DataFlowFailures.invalidInput(
                        "dataflow_key_carrier_mismatch",
                        source.alias(),
                        "dataflow.key");
            }
        }
    }

    private KeyExpression<B> append(
            KeyComponent component,
            List<ParameterSlot<?>> addedParameters,
            boolean addedParallelSafe) {
        KeyComponent[] next = Arrays.copyOf(
                components, components.length + 1);
        next[components.length] = component;
        return new KeyExpression<B>(
                source,
                next,
                DataFlowSupport.unionParameters(parameters, addedParameters),
                parallelSafe && addedParallelSafe);
    }

    private void requireSource(SourceSlot<?> candidate) {
        if (source != candidate) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_key_source_mismatch",
                    source.alias(),
                    "dataflow.key");
        }
    }

    private static void requireRequired(boolean required, String path) {
        if (!required) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_optional_key",
                    path,
                    "dataflow.key");
        }
    }
}

interface KeyComponent {
    int LONG = 1;
    int STRING = 2;
    int DOUBLE = 3;
    int BOOLEAN = 4;

    int kind();

    long hash(
            ExecutionFrame frame, DataFlowBinding binding, int index);

    boolean equal(
            ExecutionFrame frame,
            DataFlowBinding leftBinding,
            int leftIndex,
            KeyComponent right,
            DataFlowBinding rightBinding,
            int rightIndex);

    String canonical();
}

final class LongKeyComponent implements KeyComponent {
    private final LongExpression<?> expression;

    LongKeyComponent(LongExpression<?> expression) {
        this.expression = expression;
    }

    @Override
    public int kind() {
        return LONG;
    }

    @Override
    public long hash(
            ExecutionFrame frame, DataFlowBinding binding, int index) {
        long value = expression.evaluate(frame, binding, index);
        return value ^ (value >>> 32);
    }

    @Override
    public boolean equal(
            ExecutionFrame frame,
            DataFlowBinding leftBinding,
            int leftIndex,
            KeyComponent right,
            DataFlowBinding rightBinding,
            int rightIndex) {
        return expression.evaluate(frame, leftBinding, leftIndex)
                == ((LongKeyComponent) right).expression
                .evaluate(frame, rightBinding, rightIndex);
    }

    @Override
    public String canonical() {
        return "long(" + expression.identity() + ")";
    }
}

final class StringKeyComponent implements KeyComponent {
    private final StringExpression<?> expression;

    StringKeyComponent(StringExpression<?> expression) {
        this.expression = expression;
    }

    @Override
    public int kind() {
        return STRING;
    }

    @Override
    public long hash(
            ExecutionFrame frame, DataFlowBinding binding, int index) {
        String value = expression.evaluate(frame, binding, index);
        return value.hashCode();
    }

    @Override
    public boolean equal(
            ExecutionFrame frame,
            DataFlowBinding leftBinding,
            int leftIndex,
            KeyComponent right,
            DataFlowBinding rightBinding,
            int rightIndex) {
        return expression.evaluate(frame, leftBinding, leftIndex).equals(
                ((StringKeyComponent) right).expression
                        .evaluate(frame, rightBinding, rightIndex));
    }

    @Override
    public String canonical() {
        return "string(" + expression.identity() + ")";
    }
}

final class DoubleKeyComponent implements KeyComponent {
    private final DoubleExpression<?> expression;

    DoubleKeyComponent(DoubleExpression<?> expression) {
        this.expression = expression;
    }

    @Override
    public int kind() {
        return DOUBLE;
    }

    @Override
    public long hash(
            ExecutionFrame frame, DataFlowBinding binding, int index) {
        long bits = Double.doubleToLongBits(
                expression.evaluate(frame, binding, index));
        return bits ^ (bits >>> 32);
    }

    @Override
    public boolean equal(
            ExecutionFrame frame,
            DataFlowBinding leftBinding,
            int leftIndex,
            KeyComponent right,
            DataFlowBinding rightBinding,
            int rightIndex) {
        return Double.compare(
                expression.evaluate(frame, leftBinding, leftIndex),
                ((DoubleKeyComponent) right).expression.evaluate(
                        frame, rightBinding, rightIndex)) == 0;
    }

    @Override
    public String canonical() {
        return "double(" + expression.identity() + ")";
    }
}

final class BooleanKeyComponent implements KeyComponent {
    private final BooleanExpression<?> expression;

    BooleanKeyComponent(BooleanExpression<?> expression) {
        this.expression = expression;
    }

    @Override
    public int kind() {
        return BOOLEAN;
    }

    @Override
    public long hash(
            ExecutionFrame frame, DataFlowBinding binding, int index) {
        return expression.evaluate(frame, binding, index)
                ? 1231L : 1237L;
    }

    @Override
    public boolean equal(
            ExecutionFrame frame,
            DataFlowBinding leftBinding,
            int leftIndex,
            KeyComponent right,
            DataFlowBinding rightBinding,
            int rightIndex) {
        return expression.evaluate(frame, leftBinding, leftIndex)
                == ((BooleanKeyComponent) right).expression.evaluate(
                        frame, rightBinding, rightIndex);
    }

    @Override
    public String canonical() {
        return "boolean(" + expression.identity() + ")";
    }
}
