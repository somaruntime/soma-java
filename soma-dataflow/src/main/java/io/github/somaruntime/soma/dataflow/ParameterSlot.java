package io.github.somaruntime.soma.dataflow;

/**
 * Typed invocation parameter identity.
 *
 * <p>A slot belongs to logical definition semantics. It never retains a bound
 * value; each one-shot invocation supplies that value explicitly.</p>
 */
public final class ParameterSlot<T> {
    private final int ordinal;
    private final String name;
    private final Class<T> type;

    private ParameterSlot(
            int ordinal, String name, Class<T> type, boolean internal) {
        if (ordinal < 0 && !internal) {
            throw new IllegalArgumentException("ordinal must be non-negative");
        }
        this.ordinal = ordinal;
        this.name = DataFlowSupport.required(name, "name");
        if (type == null) {
            throw new NullPointerException("type");
        }
        this.type = type;
    }

    public static <T> ParameterSlot<T> of(
            int ordinal, String name, Class<T> type) {
        return new ParameterSlot<T>(ordinal, name, type, false);
    }

    static <T> ParameterSlot<T> callback(Class<T> type) {
        return new ParameterSlot<T>(
                -1, "delivery.visitor", type, true);
    }

    public int ordinal() {
        return ordinal;
    }

    public String name() {
        return name;
    }

    public Class<T> type() {
        return type;
    }

    String canonical() {
        return ordinal + ":" + name.length() + ":" + name + ":"
                + type.getName();
    }
}
