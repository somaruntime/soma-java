package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

import java.util.Arrays;

/**
 * Required, hashable composite key expression used by GroupBy and equi Join.
 *
 * <p>Components remain primitive/reference evaluators; no per-candidate tuple
 * object is created.</p>
 */
public final class KeyExpression<B extends DataFlowBinding> {
    private final SourceSlot<B> source;
    private final KeyComponent[] components;
    private final String identity;

    private KeyExpression(SourceSlot<B> source, KeyComponent[] components) {
        this.source = source;
        this.components = components;
        StringBuilder canonical = new StringBuilder("key-v1");
        for (KeyComponent component : components) {
            canonical.append('\n').append(component.canonical());
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
                new KeyComponent[] {new LongKeyComponent(expression)});
    }

    public static <B extends DataFlowBinding, T> KeyExpression<B> of(
            ObjectExpression<B, T> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        requireRequired(expression.required(), expression.path);
        return new KeyExpression<B>(
                expression.source,
                new KeyComponent[] {new ObjectKeyComponent(expression)});
    }

    public KeyExpression<B> then(LongExpression<B> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        requireSource(expression.source);
        requireRequired(expression.required(), expression.path);
        return append(new LongKeyComponent(expression));
    }

    public <T> KeyExpression<B> then(ObjectExpression<B, T> expression) {
        if (expression == null) {
            throw new NullPointerException("expression");
        }
        requireSource(expression.source);
        requireRequired(expression.required(), expression.path);
        return append(new ObjectKeyComponent(expression));
    }

    public String identity() {
        return identity;
    }

    SourceSlot<B> source() {
        return source;
    }

    long hash(DataFlowBinding binding, int index) {
        long hash = 1469598103934665603L;
        for (KeyComponent component : components) {
            hash = (hash ^ component.hash(binding, index))
                    * 1099511628211L;
        }
        return hash;
    }

    boolean equal(
            DataFlowBinding leftBinding,
            int leftIndex,
            KeyExpression<?> right,
            DataFlowBinding rightBinding,
            int rightIndex) {
        for (int component = 0; component < components.length; component++) {
            if (!components[component].equal(
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

    private KeyExpression<B> append(KeyComponent component) {
        KeyComponent[] next = Arrays.copyOf(
                components, components.length + 1);
        next[components.length] = component;
        return new KeyExpression<B>(source, next);
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
    int OBJECT = 2;

    int kind();

    long hash(DataFlowBinding binding, int index);

    boolean equal(
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
    public long hash(DataFlowBinding binding, int index) {
        long value = expression.evaluate(binding, index);
        return value ^ (value >>> 32);
    }

    @Override
    public boolean equal(
            DataFlowBinding leftBinding,
            int leftIndex,
            KeyComponent right,
            DataFlowBinding rightBinding,
            int rightIndex) {
        return expression.evaluate(leftBinding, leftIndex)
                == ((LongKeyComponent) right).expression
                .evaluate(rightBinding, rightIndex);
    }

    @Override
    public String canonical() {
        return "long(" + expression.identity() + ")";
    }
}

final class ObjectKeyComponent implements KeyComponent {
    private final ObjectExpression<?, ?> expression;

    ObjectKeyComponent(ObjectExpression<?, ?> expression) {
        this.expression = expression;
    }

    @Override
    public int kind() {
        return OBJECT;
    }

    @Override
    public long hash(DataFlowBinding binding, int index) {
        Object value = expression.evaluate(binding, index);
        return value.hashCode();
    }

    @Override
    public boolean equal(
            DataFlowBinding leftBinding,
            int leftIndex,
            KeyComponent right,
            DataFlowBinding rightBinding,
            int rightIndex) {
        return expression.evaluate(leftBinding, leftIndex).equals(
                ((ObjectKeyComponent) right).expression
                        .evaluate(rightBinding, rightIndex));
    }

    @Override
    public String canonical() {
        return "object(" + expression.identity() + ")";
    }
}
