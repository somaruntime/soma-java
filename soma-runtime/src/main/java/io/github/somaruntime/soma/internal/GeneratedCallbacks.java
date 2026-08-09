package io.github.somaruntime.soma.internal;

/** Callback adapters emitted by the processor; application code never names these types. */
public final class GeneratedCallbacks {

    private GeneratedCallbacks() {
    }

    @FunctionalInterface
    public interface RowPredicate {
        boolean test();
    }

    @FunctionalInterface
    public interface RowComparator {
        int compare();
    }

    @FunctionalInterface
    public interface RowAction {
        void accept();
    }

    @FunctionalInterface
    public interface EditorAction {
        void accept();
    }

    @FunctionalInterface
    public interface RowMapper<R> {
        R apply();
    }

    @FunctionalInterface
    public interface RowToIntMapper {
        int applyAsInt();
    }

    @FunctionalInterface
    public interface RowToBooleanMapper {
        boolean applyAsBoolean();
    }

    @FunctionalInterface
    public interface RowToByteMapper {
        byte applyAsByte();
    }

    @FunctionalInterface
    public interface RowToShortMapper {
        short applyAsShort();
    }

    @FunctionalInterface
    public interface RowToCharMapper {
        char applyAsChar();
    }

    @FunctionalInterface
    public interface RowToLongMapper {
        long applyAsLong();
    }

    @FunctionalInterface
    public interface RowToDoubleMapper {
        double applyAsDouble();
    }

    @FunctionalInterface
    public interface RowToFloatMapper {
        float applyAsFloat();
    }
}
