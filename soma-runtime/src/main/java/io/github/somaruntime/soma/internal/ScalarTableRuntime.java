package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaOperationException;
import java.util.Objects;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/**
 * I2 scalar-column backend.  It deliberately keeps the physical representation typed while the
 * generated facade supplies the schema-specific names and Java signatures.
 */
public final class ScalarTableRuntime {
    private static final int DEFAULT_CHUNK_SIZE = 1024;
    private static final int PAGE_SIZE = 256;

    public enum FieldKind {
        BOOLEAN, BYTE, SHORT, CHAR, INT, LONG, FLOAT, DOUBLE, REFERENCE
    }

    /** Fixed-width two-limb signed accumulator used by the reference numeric path. */
    public static final class CheckedLongAccumulator {
        private long high;
        private long low;

        public void add(long value) {
            long previousLow = low;
            long nextLow = previousLow + value;
            long unsignedCarry = Long.compareUnsigned(nextLow, previousLow) < 0L ? 1L : 0L;
            long signExtension = value < 0L ? -1L : 0L;
            high = high + signExtension + unsignedCarry;
            low = nextLow;
        }

        public boolean fitsLong() {
            return (high == 0L && low >= 0L) || (high == -1L && low < 0L);
        }

        public long value() {
            if (!fitsLong()) {
                throw new ArithmeticException("128-bit accumulator does not fit long");
            }
            return low;
        }
    }

    public static final class FieldSpec {
        public final FieldKind kind;
        public final boolean key;
        public final boolean indexed;
        /** String fields use content equality; ordinary object references use identity. */
        public final boolean contentEquality;

        public FieldSpec(FieldKind kind, boolean key, boolean indexed) {
            this(kind, key, indexed, false);
        }

        public FieldSpec(FieldKind kind, boolean key, boolean indexed, boolean contentEquality) {
            if (kind == null) {
                throw new NullPointerException("kind");
            }
            this.kind = kind;
            this.key = key;
            this.indexed = indexed;
            this.contentEquality = contentEquality;
        }
    }

    private final PrimitiveLongTableRuntime.GroupRuntime group;
    private final long defaultCapacity;
    private final FieldSpec[] specs;
    private final int keyIndex;
    private final int chunkSize;
    private final AtomicReference<StateRoot> current;

    public ScalarTableRuntime(
            PrimitiveLongTableRuntime.GroupRuntime group,
            long defaultCapacity,
            FieldSpec[] specs,
            int keyIndex) {
        if (group == null || specs == null) {
            throw new NullPointerException("group/specs");
        }
        if (defaultCapacity < 0L) {
            throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.RESERVE,
                    "defaultCapacity must not be negative", null);
        }
        this.group = group;
        this.defaultCapacity = defaultCapacity;
        this.specs = specs.clone();
        this.keyIndex = keyIndex;
        if (keyIndex < -1 || keyIndex >= specs.length) {
            throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.CONFIGURE,
                    "key index is outside the schema", null);
        }
        int keys = 0;
        for (int i = 0; i < this.specs.length; i++) {
            if (this.specs[i] == null) {
                throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.CONFIGURE,
                        "null field specification", null);
            }
            if (this.specs[i].key) {
                keys++;
                if (i != keyIndex) {
                    throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.CONFIGURE,
                            "key specification does not match key index", null);
                }
            }
        }
        if (keys > 1 || (keys == 1) != (keyIndex >= 0)) {
            throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.CONFIGURE,
                    "a Table has zero or one key", null);
        }
        this.chunkSize = configuredChunkSize();
        this.current = new AtomicReference<StateRoot>(StateRoot.empty(chunkSize, specs));
    }

    public long size() {
        PrimitiveLongTableRuntime.GroupRuntime.Guard guard = group.enter(SomaOperation.QUERY);
        try {
            return current.get().size;
        } finally {
            guard.close();
        }
    }

    public long capacity() {
        PrimitiveLongTableRuntime.GroupRuntime.Guard guard = group.enter(SomaOperation.QUERY);
        try {
            return current.get().capacity;
        } finally {
            guard.close();
        }
    }

    public long stateVersion() {
        PrimitiveLongTableRuntime.GroupRuntime.Guard guard = group.enter(SomaOperation.QUERY);
        try {
            return current.get().version;
        } finally {
            guard.close();
        }
    }

    /** Bounded reference GroupBy path used by the I5 generated integer-key slice. */
    public io.github.somaruntime.soma.IntGroupedLongResult groupIntLong(
            int keyField, int valueField, boolean sum) {
        if (keyField < 0 || keyField >= specs.length
                || specs[keyField].kind != FieldKind.INT) {
            throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.QUERY,
                    "GroupBy key must be an int field", null);
        }
        if (sum && (valueField < 0 || valueField >= specs.length
                || specs[valueField].kind != FieldKind.INT)) {
            throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.QUERY,
                    "GroupBy sum field must be an int field", null);
        }
        PrimitiveLongTableRuntime.GroupRuntime.Guard guard = group.enter(SomaOperation.QUERY);
        try {
            StateRoot root = current.get();
            int[] keys = new int[16];
            CheckedLongAccumulator[] accumulators = new CheckedLongAccumulator[16];
            int groups = 0;
            for (long row = 0L; row < root.size; row++) {
                int key = root.directory.ints(keyField, row);
                int groupIndex = -1;
                for (int i = 0; i < groups; i++) {
                    if (keys[i] == key) {
                        groupIndex = i;
                        break;
                    }
                }
                if (groupIndex < 0) {
                    if (groups == keys.length) {
                        int nextLength;
                        try {
                            nextLength = Math.multiplyExact(keys.length, 2);
                        } catch (ArithmeticException overflow) {
                            throw failure(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED, SomaOperation.QUERY,
                                    "GroupBy cardinality exceeds Java array range", overflow);
                        }
                        keys = Arrays.copyOf(keys, nextLength);
                        accumulators = Arrays.copyOf(accumulators, nextLength);
                    }
                    groupIndex = groups++;
                    keys[groupIndex] = key;
                    accumulators[groupIndex] = new CheckedLongAccumulator();
                }
                long contribution = sum ? (long) root.directory.ints(valueField, row) : 1L;
                accumulators[groupIndex].add(contribution);
            }
            long[] values = new long[groups];
            for (int i = 0; i < groups; i++) {
                if (!accumulators[i].fitsLong()) {
                    throw failure(SomaFailureCode.ARITHMETIC_OVERFLOW, SomaOperation.QUERY,
                            "checked GroupBy aggregate overflow", null);
                }
                values[i] = accumulators[i].value();
            }
            return SomaRuntimeAccess.intGroupedLongResult(
                Arrays.copyOf(keys, groups), Arrays.copyOf(values, groups));
        } finally {
            guard.close();
        }
    }

    public void reserve(long expectedRows) {
        if (expectedRows < 0L) {
            throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.RESERVE,
                    "expectedRows must not be negative", null);
        }
        PrimitiveLongTableRuntime.GroupRuntime.Guard guard = group.enter(SomaOperation.RESERVE);
        try {
            StateRoot root = current.get();
            if (expectedRows <= root.capacity) {
                return;
            }
            Directory directory = root.directory.withCapacity(expectedRows, specs, chunkSize);
            publish(root, new StateRoot(root.size, directory.capacity, nextVersion(root.version,
                    SomaOperation.RESERVE), directory), SomaOperation.RESERVE);
        } finally {
            guard.close();
        }
    }

    public Append beginAppend() {
        return new Append(this, group.enter(SomaOperation.ADD), current.get());
    }

    public Query beginQuery(SomaOperation operation) {
        if (operation == null) {
            throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.QUERY,
                    "query operation must not be null", null);
        }
        return new Query(group.enter(operation), current.get(), specs, keyIndex);
    }

    public PointUpdate beginUpdate(Object key) {
        validateKeyArgument(key, SomaOperation.UPDATE);
        PrimitiveLongTableRuntime.GroupRuntime.Guard guard = group.enter(SomaOperation.UPDATE);
        StateRoot root = current.get();
        long locator = root.directory.findKey(key, specs, keyIndex, root.size);
        if (locator < 0L) {
            guard.close();
            return PointUpdate.missing();
        }
        return new PointUpdate(this, guard, root, locator, specs, keyIndex);
    }

    public io.github.somaruntime.soma.RemoveResult remove(Object key) {
        validateKeyArgument(key, SomaOperation.REMOVE);
        PrimitiveLongTableRuntime.GroupRuntime.Guard guard = group.enter(SomaOperation.REMOVE);
        try {
            StateRoot root = current.get();
            long locator = root.directory.findKey(key, specs, keyIndex, root.size);
            if (locator < 0L) {
                return SomaRuntimeAccess.removeResult(0L);
            }
            Directory next = root.directory.withoutRow(locator, root.size, specs, chunkSize);
            StateRoot candidate = new StateRoot(root.size - 1L, root.capacity,
                    nextVersion(root.version, SomaOperation.REMOVE), next);
            publish(root, candidate, SomaOperation.REMOVE);
            return SomaRuntimeAccess.removeResult(1L);
        } finally {
            guard.close();
        }
    }

    private void validateKeyArgument(Object key, SomaOperation operation) {
        if (keyIndex >= 0 && key == null) {
            throw failure(SomaFailureCode.INVALID_ARGUMENT, operation,
                    "key must not be null", null);
        }
    }

    private void append(Append append) {
        append.ensureOpen();
        StateRoot root = append.root;
        if (keyIndex >= 0) {
            Object key = append.valueObject(keyIndex);
            if (key == null && specs[keyIndex].kind == FieldKind.REFERENCE) {
                throw failure(SomaFailureCode.NULL_VALUE_UNSUPPORTED, SomaOperation.ADD,
                        "Key fields cannot be null", null);
            }
            if (root.directory.findKey(key, specs, keyIndex, root.size) >= 0L) {
                throw failure(SomaFailureCode.DUPLICATE_KEY, SomaOperation.ADD,
                        "key already exists", null);
            }
        }
        long target = root.size == root.capacity
                ? growthTarget(root.size, defaultCapacity)
                : root.capacity;
        Directory directory = root.directory;
        if (target > root.capacity) {
            directory = directory.withCapacity(target, specs, chunkSize);
        }
        Directory next = directory.withRow(root.size, specs, append);
        StateRoot candidate = new StateRoot(checkedAdd(root.size, 1L, SomaOperation.ADD),
                directory.capacity, nextVersion(root.version, SomaOperation.ADD), next);
        publish(root, candidate, SomaOperation.ADD);
        append.closed = true;
        append.guard.close();
    }

    private void prepare(PointUpdate update) {
        update.ensureOpen();
        update.changed = update.computeChanged();
        if (!update.changed) {
            update.prepared = update.root;
            return;
        }
        Directory next = update.root.directory.withUpdatedRow(
                update.locator, specs, update);
        update.prepared = new StateRoot(update.root.size, update.root.capacity,
                nextVersion(update.root.version, SomaOperation.UPDATE), next);
    }

    private void commit(PointUpdate update) {
        update.ensureOpen();
        if (update.prepared == null) {
            throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.UPDATE,
                    "point update was not prepared", null);
        }
        if (update.prepared != update.root) {
            publish(update.root, update.prepared, SomaOperation.UPDATE);
        }
        update.closed = true;
        update.guard.close();
    }

    private void abort(PointUpdate update) {
        update.closed = true;
        if (update.guard != null) {
            update.guard.close();
        }
    }

    private void publish(StateRoot expected, StateRoot candidate, SomaOperation operation) {
        if (!current.compareAndSet(expected, candidate)) {
            throw failure(SomaFailureCode.CONCURRENT_GROUP_OPERATION, operation,
                    "table StateRoot changed during guarded operation", null);
        }
    }

    private long growthTarget(long size, long hint) {
        long minimum = checkedAdd(size, 1L, SomaOperation.ADD);
        long initial = hint > 0L ? hint : 1L;
        if (initial >= minimum) {
            return initial;
        }
        long doubled = size <= Long.MAX_VALUE / 2L ? size * 2L : Long.MAX_VALUE;
        return doubled >= minimum ? doubled : minimum;
    }

    private static int configuredChunkSize() {
        String configured = System.getProperty("soma.test.chunkSize");
        if (configured != null) {
            try {
                int value = Integer.parseInt(configured);
                if (value > 0 && value <= 1 << 20) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Qualification-only malformed values use the safe default.
            }
        }
        return DEFAULT_CHUNK_SIZE;
    }

    private static long checkedAdd(long left, long right, SomaOperation operation) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw failure(SomaFailureCode.ARITHMETIC_OVERFLOW, operation,
                    "checked long arithmetic overflow", exception);
        }
    }

    private static long nextVersion(long version, SomaOperation operation) {
        return checkedAdd(version, 1L, operation);
    }

    private static SomaOperationException failure(
            SomaFailureCode code, SomaOperation operation, String context, Throwable cause) {
        return SomaRuntimeAccess.failure(code, operation, context, cause);
    }

    public static final class Append {
        private final ScalarTableRuntime runtime;
        private final PrimitiveLongTableRuntime.GroupRuntime.Guard guard;
        private final StateRoot root;
        private final Thread owner;
        private final boolean[] booleans;
        private final byte[] bytes;
        private final short[] shorts;
        private final char[] chars;
        private final int[] ints;
        private final long[] longs;
        private final float[] floats;
        private final double[] doubles;
        private final Object[] references;
        private boolean closed;

        private Append(ScalarTableRuntime runtime,
                PrimitiveLongTableRuntime.GroupRuntime.Guard guard, StateRoot root) {
            this.runtime = runtime;
            this.guard = guard;
            this.root = root;
            this.owner = Thread.currentThread();
            int length = runtime.specs.length;
            booleans = new boolean[length]; bytes = new byte[length]; shorts = new short[length];
            chars = new char[length]; ints = new int[length]; longs = new long[length];
            floats = new float[length]; doubles = new double[length]; references = new Object[length];
        }

        public Append setBoolean(int field, boolean value) { ensureOpen(); booleans[field] = value; return this; }
        public Append setByte(int field, byte value) { ensureOpen(); bytes[field] = value; return this; }
        public Append setShort(int field, short value) { ensureOpen(); shorts[field] = value; return this; }
        public Append setChar(int field, char value) { ensureOpen(); chars[field] = value; return this; }
        public Append setInt(int field, int value) { ensureOpen(); ints[field] = value; return this; }
        public Append setLong(int field, long value) { ensureOpen(); longs[field] = value; return this; }
        public Append setFloat(int field, float value) { ensureOpen(); floats[field] = value; return this; }
        public Append setDouble(int field, double value) { ensureOpen(); doubles[field] = value; return this; }
        public Append setReference(int field, Object value) { ensureOpen(); references[field] = value; return this; }

        private Object valueObject(int field) {
            switch (runtime.specs[field].kind) {
                case BOOLEAN: return Boolean.valueOf(booleans[field]);
                case BYTE: return Byte.valueOf(bytes[field]);
                case SHORT: return Short.valueOf(shorts[field]);
                case CHAR: return Character.valueOf(chars[field]);
                case INT: return Integer.valueOf(ints[field]);
                case LONG: return Long.valueOf(longs[field]);
                case FLOAT: return Float.valueOf(floats[field]);
                case DOUBLE: return Double.valueOf(doubles[field]);
                default: return references[field];
            }
        }

        public void commit() { ensureOpen(); runtime.append(this); }
        public void abort() { if (!closed) { closed = true; guard.close(); } }
        private void ensureOpen() {
            if (closed || Thread.currentThread() != owner) {
                throw failure(SomaFailureCode.CALLBACK_SCOPE_VIOLATION, SomaOperation.ADD,
                        "append state is outside its operation scope", null);
            }
        }
    }

    public static final class Query implements AutoCloseable {
        private final PrimitiveLongTableRuntime.GroupRuntime.Guard guard;
        private final StateRoot root;
        private final FieldSpec[] specs;
        private final int keyIndex;
        private final Thread owner;
        private boolean closed;

        private Query(PrimitiveLongTableRuntime.GroupRuntime.Guard guard, StateRoot root,
                FieldSpec[] specs, int keyIndex) {
            this.guard = guard; this.root = root; this.specs = specs; this.keyIndex = keyIndex;
            this.owner = Thread.currentThread();
        }
        public long size() { ensureOpen(); return root.size; }
        public Object keyAt(long locator) { ensureOpen(); return valueAt(keyIndex, locator); }
        public long findLocator(Object key) { ensureOpen(); return root.directory.findKey(key, specs, keyIndex, root.size); }
        public boolean matches(int field, long locator, Object value) { ensureOpen(); return ScalarTableRuntime.matches(valueAt(field, locator), value, specs[field].kind); }
        public boolean booleanAt(int field, long locator) { ensureOpen(); return root.directory.booleans(field, locator); }
        public byte byteAt(int field, long locator) { ensureOpen(); return root.directory.bytes(field, locator); }
        public short shortAt(int field, long locator) { ensureOpen(); return root.directory.shorts(field, locator); }
        public char charAt(int field, long locator) { ensureOpen(); return root.directory.chars(field, locator); }
        public int intAt(int field, long locator) { ensureOpen(); return root.directory.ints(field, locator); }
        public long longAt(int field, long locator) { ensureOpen(); return root.directory.longs(field, locator); }
        public float floatAt(int field, long locator) { ensureOpen(); return root.directory.floats(field, locator); }
        public double doubleAt(int field, long locator) { ensureOpen(); return root.directory.doubles(field, locator); }
        public Object referenceAt(int field, long locator) { ensureOpen(); return root.directory.references(field, locator); }
        private Object valueAt(int field, long locator) {
            if (field < 0) return null;
            switch (specs[field].kind) {
                case BOOLEAN: return Boolean.valueOf(booleanAt(field, locator));
                case BYTE: return Byte.valueOf(byteAt(field, locator));
                case SHORT: return Short.valueOf(shortAt(field, locator));
                case CHAR: return Character.valueOf(charAt(field, locator));
                case INT: return Integer.valueOf(intAt(field, locator));
                case LONG: return Long.valueOf(longAt(field, locator));
                case FLOAT: return Float.valueOf(floatAt(field, locator));
                case DOUBLE: return Double.valueOf(doubleAt(field, locator));
                default: return referenceAt(field, locator);
            }
        }
        @Override public void close() { if (!closed) { closed = true; guard.close(); } }
        private void ensureOpen() { if (closed || Thread.currentThread() != owner) throw failure(SomaFailureCode.CALLBACK_SCOPE_VIOLATION, SomaOperation.QUERY, "query is outside its operation scope", null); }
    }

    public static final class PointUpdate {
        private final ScalarTableRuntime runtime;
        private final PrimitiveLongTableRuntime.GroupRuntime.Guard guard;
        private final StateRoot root;
        private final long locator;
        private final FieldSpec[] specs;
        private final int keyIndex;
        private final Thread owner;
        private final boolean[] booleans; private final byte[] bytes; private final short[] shorts;
        private final char[] chars; private final int[] ints; private final long[] longs;
        private final float[] floats; private final double[] doubles; private final Object[] references;
        private final boolean[] originalBooleans; private final byte[] originalBytes; private final short[] originalShorts;
        private final char[] originalChars; private final int[] originalInts; private final long[] originalLongs;
        private final float[] originalFloats; private final double[] originalDoubles; private final Object[] originalReferences;
        private final boolean matched;
        private boolean closed;
        private boolean changed;
        private StateRoot prepared;

        private PointUpdate(ScalarTableRuntime runtime, PrimitiveLongTableRuntime.GroupRuntime.Guard guard,
                StateRoot root, long locator, FieldSpec[] specs, int keyIndex) {
            this.runtime = runtime; this.guard = guard; this.root = root; this.locator = locator;
            this.specs = specs; this.keyIndex = keyIndex; this.owner = Thread.currentThread(); this.matched = true;
            int length = specs.length;
            booleans = new boolean[length]; bytes = new byte[length]; shorts = new short[length]; chars = new char[length];
            ints = new int[length]; longs = new long[length]; floats = new float[length]; doubles = new double[length]; references = new Object[length];
            originalBooleans = new boolean[length]; originalBytes = new byte[length]; originalShorts = new short[length]; originalChars = new char[length];
            originalInts = new int[length]; originalLongs = new long[length]; originalFloats = new float[length]; originalDoubles = new double[length]; originalReferences = new Object[length];
            for (int i = 0; i < length; i++) copyFrom(i, locator, false);
        }

        private PointUpdate() { runtime = null; guard = null; root = null; locator = -1L; specs = null; keyIndex = -1; owner = null; matched = false; booleans = null; bytes = null; shorts = null; chars = null; ints = null; longs = null; floats = null; doubles = null; references = null; originalBooleans = null; originalBytes = null; originalShorts = null; originalChars = null; originalInts = null; originalLongs = null; originalFloats = null; originalDoubles = null; originalReferences = null; }
        private static PointUpdate missing() { return new PointUpdate(); }
        public boolean matched() { return matched; }
        public Object key() { ensureOpen(); return valueObject(keyIndex, false); }
        public boolean booleanAt(int field) { ensureOpen(); return booleans[field]; }
        public byte byteAt(int field) { ensureOpen(); return bytes[field]; }
        public short shortAt(int field) { ensureOpen(); return shorts[field]; }
        public char charAt(int field) { ensureOpen(); return chars[field]; }
        public int intAt(int field) { ensureOpen(); return ints[field]; }
        public long longAt(int field) { ensureOpen(); return longs[field]; }
        public float floatAt(int field) { ensureOpen(); return floats[field]; }
        public double doubleAt(int field) { ensureOpen(); return doubles[field]; }
        public Object referenceAt(int field) { ensureOpen(); return references[field]; }
        public PointUpdate setBoolean(int field, boolean value) { ensureOpen(); booleans[field] = value; return this; }
        public PointUpdate setByte(int field, byte value) { ensureOpen(); bytes[field] = value; return this; }
        public PointUpdate setShort(int field, short value) { ensureOpen(); shorts[field] = value; return this; }
        public PointUpdate setChar(int field, char value) { ensureOpen(); chars[field] = value; return this; }
        public PointUpdate setInt(int field, int value) { ensureOpen(); ints[field] = value; return this; }
        public PointUpdate setLong(int field, long value) { ensureOpen(); longs[field] = value; return this; }
        public PointUpdate setFloat(int field, float value) { ensureOpen(); floats[field] = value; return this; }
        public PointUpdate setDouble(int field, double value) { ensureOpen(); doubles[field] = value; return this; }
        public PointUpdate setReference(int field, Object value) { ensureOpen(); references[field] = value; return this; }
        public boolean changed() { if (!matched) return false; if (!closed) ensureOpen(); return changed; }
        public void prepare() { ensureOpen(); runtime.prepare(this); }
        public void commit() { ensureOpen(); runtime.commit(this); }
        public void abort() { if (!closed) runtime.abort(this); }
        private boolean computeChanged() { for (int i = 0; i < specs.length; i++) if (!same(i)) return true; return false; }
        private boolean same(int field) {
            switch (specs[field].kind) {
                case BOOLEAN: return booleans[field] == originalBooleans[field];
                case BYTE: return bytes[field] == originalBytes[field];
                case SHORT: return shorts[field] == originalShorts[field];
                case CHAR: return chars[field] == originalChars[field];
                case INT: return ints[field] == originalInts[field];
                case LONG: return longs[field] == originalLongs[field];
                case FLOAT: return Float.floatToIntBits(floats[field]) == Float.floatToIntBits(originalFloats[field]);
                case DOUBLE: return Double.doubleToLongBits(doubles[field]) == Double.doubleToLongBits(originalDoubles[field]);
                default:
                    return specs[field].contentEquality
                            ? Objects.equals(references[field], originalReferences[field])
                            : references[field] == originalReferences[field];
            }
        }
        private Object valueObject(int field, boolean original) { if (field < 0) return null; switch (specs[field].kind) { case BOOLEAN: return Boolean.valueOf(original ? originalBooleans[field] : booleans[field]); case BYTE: return Byte.valueOf(original ? originalBytes[field] : bytes[field]); case SHORT: return Short.valueOf(original ? originalShorts[field] : shorts[field]); case CHAR: return Character.valueOf(original ? originalChars[field] : chars[field]); case INT: return Integer.valueOf(original ? originalInts[field] : ints[field]); case LONG: return Long.valueOf(original ? originalLongs[field] : longs[field]); case FLOAT: return Float.valueOf(original ? originalFloats[field] : floats[field]); case DOUBLE: return Double.valueOf(original ? originalDoubles[field] : doubles[field]); default: return original ? originalReferences[field] : references[field]; } }
        private void copyFrom(int field, long row, boolean ignored) { switch (specs[field].kind) { case BOOLEAN: booleans[field] = originalBooleans[field] = root.directory.booleans(field, row); break; case BYTE: bytes[field] = originalBytes[field] = root.directory.bytes(field, row); break; case SHORT: shorts[field] = originalShorts[field] = root.directory.shorts(field, row); break; case CHAR: chars[field] = originalChars[field] = root.directory.chars(field, row); break; case INT: ints[field] = originalInts[field] = root.directory.ints(field, row); break; case LONG: longs[field] = originalLongs[field] = root.directory.longs(field, row); break; case FLOAT: floats[field] = originalFloats[field] = root.directory.floats(field, row); break; case DOUBLE: doubles[field] = originalDoubles[field] = root.directory.doubles(field, row); break; default: references[field] = originalReferences[field] = root.directory.references(field, row); break; } }
        private void ensureOpen() { if (!matched || closed || Thread.currentThread() != owner) throw failure(SomaFailureCode.CALLBACK_SCOPE_VIOLATION, SomaOperation.UPDATE, "Editor is outside its operation scope", null); }
    }

    private static boolean matches(Object left, Object right, FieldKind kind) {
        if (kind == FieldKind.FLOAT) return left != null && right != null && Float.compare(((Float) left).floatValue(), ((Float) right).floatValue()) == 0;
        if (kind == FieldKind.DOUBLE) return left != null && right != null && Double.compare(((Double) left).doubleValue(), ((Double) right).doubleValue()) == 0;
        return Objects.equals(left, right);
    }

    private static final class StateRoot {
        final long size; final long capacity; final long version; final Directory directory;
        StateRoot(long size, long capacity, long version, Directory directory) { this.size = size; this.capacity = capacity; this.version = version; this.directory = directory; }
        static StateRoot empty(int chunkSize, FieldSpec[] specs) { return new StateRoot(0L, 0L, 0L, Directory.empty(chunkSize, specs)); }
    }

    private static final class Directory {
        final DirectoryRoot[] roots; final int chunkSize; final long capacity; final FieldSpec[] specs;

        Directory(DirectoryRoot[] roots, int chunkSize, long capacity, FieldSpec[] specs) {
            this.roots = roots; this.chunkSize = chunkSize; this.capacity = capacity; this.specs = specs;
        }

        static Directory empty(int chunkSize, FieldSpec[] specs) {
            return new Directory(new DirectoryRoot[0], chunkSize, 0L, specs);
        }

        Directory withCapacity(long requested, FieldSpec[] specs, int chunkSize) {
            if (requested < 0L) {
                throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.RESERVE,
                        "requested capacity must not be negative", null);
            }
            long chunks = ceilDiv(requested, chunkSize, SomaOperation.RESERVE);
            long capacity = checkedMultiply(chunks, chunkSize, SomaOperation.RESERVE);
            long pagesNeeded = ceilDiv(chunks, PAGE_SIZE, SomaOperation.RESERVE);
            long rootsNeeded = ceilDiv(pagesNeeded, PAGE_SIZE, SomaOperation.RESERVE);
            if (rootsNeeded > Integer.MAX_VALUE) {
                throw failure(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED, SomaOperation.RESERVE,
                        "directory root exceeds Java array range", null);
            }
            DirectoryRoot[] next = new DirectoryRoot[(int) rootsNeeded];
            for (int rootIndex = 0; rootIndex < next.length; rootIndex++) {
                long firstPage = (long) rootIndex * PAGE_SIZE;
                int pageCount = (int) Math.min(PAGE_SIZE, pagesNeeded - firstPage);
                ChunkPage[] nextPages = new ChunkPage[pageCount];
                DirectoryRoot oldRoot = rootIndex < roots.length ? roots[rootIndex] : null;
                for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                    long pageNumber = firstPage + pageIndex;
                    long firstChunk = pageNumber * PAGE_SIZE;
                    int chunkCount = (int) Math.min(PAGE_SIZE, chunks - firstChunk);
                    ChunkPage oldPage = oldRoot != null && pageIndex < oldRoot.pages.length
                            ? oldRoot.pages[pageIndex] : null;
                    Chunk[] nextChunks = new Chunk[chunkCount];
                    for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                        nextChunks[chunkIndex] = oldPage != null && chunkIndex < oldPage.chunks.length
                                ? oldPage.chunks[chunkIndex] : new Chunk(chunkSize, specs);
                    }
                    nextPages[pageIndex] = new ChunkPage(nextChunks);
                }
                next[rootIndex] = new DirectoryRoot(nextPages);
            }
            return new Directory(next, chunkSize, capacity, specs);
        }

        Directory withRow(long row, FieldSpec[] specs, Append append) {
            return replace(row, specs, new RowSource() {
                public Object value(int field) { return append.valueObject(field); }
                public boolean bool(int f) { return append.booleans[f]; }
                public byte byt(int f) { return append.bytes[f]; }
                public short sht(int f) { return append.shorts[f]; }
                public char chr(int f) { return append.chars[f]; }
                public int integer(int f) { return append.ints[f]; }
                public long lng(int f) { return append.longs[f]; }
                public float flt(int f) { return append.floats[f]; }
                public double dbl(int f) { return append.doubles[f]; }
                public Object ref(int f) { return append.references[f]; }
            });
        }

        Directory withUpdatedRow(long row, FieldSpec[] specs, final PointUpdate update) {
            return replace(row, specs, new RowSource() {
                public Object value(int field) { return update.valueObject(field, false); }
                public boolean bool(int f) { return update.booleans[f]; }
                public byte byt(int f) { return update.bytes[f]; }
                public short sht(int f) { return update.shorts[f]; }
                public char chr(int f) { return update.chars[f]; }
                public int integer(int f) { return update.ints[f]; }
                public long lng(int f) { return update.longs[f]; }
                public float flt(int f) { return update.floats[f]; }
                public double dbl(int f) { return update.doubles[f]; }
                public Object ref(int f) { return update.references[f]; }
            });
        }

        Directory withoutRow(final long removedRow, long size, FieldSpec[] specs, int chunkSize) {
            Directory next = Directory.empty(chunkSize, specs).withCapacity(capacity, specs, chunkSize);
            long destination = 0L;
            for (long sourceRow = 0L; sourceRow < size; sourceRow++) {
                if (sourceRow == removedRow) continue;
                next.copyRowFrom(this, sourceRow, destination++, specs);
            }
            return next;
        }

        private void copyRowFrom(Directory source, long sourceRow, long destinationRow,
                FieldSpec[] specs) {
            Chunk sourceChunk = source.chunk(sourceRow);
            Chunk destinationChunk = chunk(destinationRow);
            int sourceOffset = (int) (sourceRow % chunkSize);
            int destinationOffset = (int) (destinationRow % chunkSize);
            for (int field = 0; field < specs.length; field++) {
                switch (specs[field].kind) {
                    case BOOLEAN: destinationChunk.booleans[field][destinationOffset] = sourceChunk.booleans[field][sourceOffset]; break;
                    case BYTE: destinationChunk.bytes[field][destinationOffset] = sourceChunk.bytes[field][sourceOffset]; break;
                    case SHORT: destinationChunk.shorts[field][destinationOffset] = sourceChunk.shorts[field][sourceOffset]; break;
                    case CHAR: destinationChunk.chars[field][destinationOffset] = sourceChunk.chars[field][sourceOffset]; break;
                    case INT: destinationChunk.ints[field][destinationOffset] = sourceChunk.ints[field][sourceOffset]; break;
                    case LONG: destinationChunk.longs[field][destinationOffset] = sourceChunk.longs[field][sourceOffset]; break;
                    case FLOAT: destinationChunk.floats[field][destinationOffset] = sourceChunk.floats[field][sourceOffset]; break;
                    case DOUBLE: destinationChunk.doubles[field][destinationOffset] = sourceChunk.doubles[field][sourceOffset]; break;
                    default: destinationChunk.references[field][destinationOffset] = sourceChunk.references[field][sourceOffset]; break;
                }
            }
        }

        private Directory replace(long row, FieldSpec[] specs, RowSource source) {
            long chunkNumber = row / chunkSize;
            int offset = (int) (row % chunkSize);
            long pageNumber = chunkNumber / PAGE_SIZE;
            int rootIndex = checkedArrayIndex(pageNumber / PAGE_SIZE, "directory root");
            int pageIndex = (int) (pageNumber % PAGE_SIZE);
            int chunkIndex = (int) (chunkNumber % PAGE_SIZE);
            DirectoryRoot oldRoot = rootAt(rootIndex);
            ChunkPage oldPage = oldRoot.pages[pageIndex];
            Chunk oldChunk = oldPage.chunks[chunkIndex];
            Chunk nextChunk = oldChunk.copy();
            for (int field = 0; field < specs.length; field++) {
                nextChunk.set(field, offset, specs[field].kind, source);
            }
            DirectoryRoot[] nextRoots = roots.clone();
            ChunkPage[] nextPages = oldRoot.pages.clone();
            Chunk[] pageChunks = oldPage.chunks.clone();
            pageChunks[chunkIndex] = nextChunk;
            nextPages[pageIndex] = new ChunkPage(pageChunks);
            nextRoots[rootIndex] = new DirectoryRoot(nextPages);
            return new Directory(nextRoots, chunkSize, capacity, specs);
        }

        long findKey(Object key, FieldSpec[] specs, int keyIndex, long size) {
            if (keyIndex < 0 || key == null) return -1L;
            FieldSpec spec = specs[keyIndex];
            switch (spec.kind) {
                case BOOLEAN:
                    if (!(key instanceof Boolean)) return -1L;
                    boolean booleanKey = ((Boolean) key).booleanValue();
                    for (long row = 0L; row < size; row++) if (booleans(keyIndex, row) == booleanKey) return row;
                    return -1L;
                case BYTE:
                    if (!(key instanceof Byte)) return -1L;
                    byte byteKey = ((Byte) key).byteValue();
                    for (long row = 0L; row < size; row++) if (bytes(keyIndex, row) == byteKey) return row;
                    return -1L;
                case SHORT:
                    if (!(key instanceof Short)) return -1L;
                    short shortKey = ((Short) key).shortValue();
                    for (long row = 0L; row < size; row++) if (shorts(keyIndex, row) == shortKey) return row;
                    return -1L;
                case CHAR:
                    if (!(key instanceof Character)) return -1L;
                    char charKey = ((Character) key).charValue();
                    for (long row = 0L; row < size; row++) if (chars(keyIndex, row) == charKey) return row;
                    return -1L;
                case INT:
                    if (!(key instanceof Integer)) return -1L;
                    int intKey = ((Integer) key).intValue();
                    for (long row = 0L; row < size; row++) if (ints(keyIndex, row) == intKey) return row;
                    return -1L;
                case LONG:
                    if (!(key instanceof Long)) return -1L;
                    long longKey = ((Long) key).longValue();
                    for (long row = 0L; row < size; row++) if (longs(keyIndex, row) == longKey) return row;
                    return -1L;
                case FLOAT:
                    if (!(key instanceof Float)) return -1L;
                    float floatKey = ((Float) key).floatValue();
                    for (long row = 0L; row < size; row++) if (Float.compare(floats(keyIndex, row), floatKey) == 0) return row;
                    return -1L;
                case DOUBLE:
                    if (!(key instanceof Double)) return -1L;
                    double doubleKey = ((Double) key).doubleValue();
                    for (long row = 0L; row < size; row++) if (Double.compare(doubles(keyIndex, row), doubleKey) == 0) return row;
                    return -1L;
                default:
                    for (long row = 0L; row < size; row++) {
                        Object stored = references(keyIndex, row);
                        if (spec.contentEquality ? Objects.equals(stored, key) : stored == key) return row;
                    }
                    return -1L;
            }
        }

        private Object valueAt(int field, long row) {
            if (field < 0) return null;
            switch (specs[field].kind) {
                case BOOLEAN: return Boolean.valueOf(booleans(field, row));
                case BYTE: return Byte.valueOf(bytes(field, row));
                case SHORT: return Short.valueOf(shorts(field, row));
                case CHAR: return Character.valueOf(chars(field, row));
                case INT: return Integer.valueOf(ints(field, row));
                case LONG: return Long.valueOf(longs(field, row));
                case FLOAT: return Float.valueOf(floats(field, row));
                case DOUBLE: return Double.valueOf(doubles(field, row));
                default: return references(field, row);
            }
        }

        private DirectoryRoot rootAt(int index) {
            if (index < 0 || index >= roots.length || roots[index] == null) {
                throw failure(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED, SomaOperation.ADD,
                        "directory root is not allocated", null);
            }
            return roots[index];
        }

        private Chunk chunk(long row) {
            if (row < 0L || row >= capacity) {
                throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.QUERY,
                        "row locator is outside directory capacity", null);
            }
            long chunkNumber = row / chunkSize;
            long pageNumber = chunkNumber / PAGE_SIZE;
            int rootIndex = checkedArrayIndex(pageNumber / PAGE_SIZE, "directory root");
            int pageIndex = (int) (pageNumber % PAGE_SIZE);
            int chunkIndex = (int) (chunkNumber % PAGE_SIZE);
            DirectoryRoot root = rootAt(rootIndex);
            if (pageIndex >= root.pages.length || root.pages[pageIndex] == null
                    || chunkIndex >= root.pages[pageIndex].chunks.length) {
                throw failure(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED, SomaOperation.QUERY,
                        "directory page is not allocated", null);
            }
            return root.pages[pageIndex].chunks[chunkIndex];
        }

        boolean booleans(int f, long r) { return chunk(r).booleans[f][(int) (r % chunkSize)]; }
        byte bytes(int f, long r) { return chunk(r).bytes[f][(int) (r % chunkSize)]; }
        short shorts(int f, long r) { return chunk(r).shorts[f][(int) (r % chunkSize)]; }
        char chars(int f, long r) { return chunk(r).chars[f][(int) (r % chunkSize)]; }
        int ints(int f, long r) { return chunk(r).ints[f][(int) (r % chunkSize)]; }
        long longs(int f, long r) { return chunk(r).longs[f][(int) (r % chunkSize)]; }
        float floats(int f, long r) { return chunk(r).floats[f][(int) (r % chunkSize)]; }
        double doubles(int f, long r) { return chunk(r).doubles[f][(int) (r % chunkSize)]; }
        Object references(int f, long r) { return chunk(r).references[f][(int) (r % chunkSize)]; }

        private static long ceilDiv(long value, long divisor, SomaOperation operation) {
            if (value < 0L || divisor <= 0L) {
                throw failure(SomaFailureCode.INVALID_ARGUMENT, operation,
                        "invalid checked division", null);
            }
            long quotient = value / divisor;
            return value % divisor == 0L ? quotient : checkedAdd(quotient, 1L, operation);
        }

        private static long checkedMultiply(long left, long right, SomaOperation operation) {
            try {
                return Math.multiplyExact(left, right);
            } catch (ArithmeticException exception) {
                throw failure(SomaFailureCode.ARITHMETIC_OVERFLOW, operation,
                        "checked long multiplication overflow", exception);
            }
        }

        private static int checkedArrayIndex(long value, String context) {
            if (value < 0L || value > Integer.MAX_VALUE) {
                throw failure(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED, SomaOperation.RESERVE,
                        context + " exceeds Java array range", null);
            }
            return (int) value;
        }
    }

    private interface RowSource { Object value(int f); boolean bool(int f); byte byt(int f); short sht(int f); char chr(int f); int integer(int f); long lng(int f); float flt(int f); double dbl(int f); Object ref(int f); }
    private static final class DirectoryRoot { final ChunkPage[] pages; DirectoryRoot(ChunkPage[] pages) { this.pages = pages; } }
    private static final class ChunkPage { final Chunk[] chunks; ChunkPage(Chunk[] chunks) { this.chunks = chunks; } }
    private static final class Chunk {
        final boolean[][] booleans; final byte[][] bytes; final short[][] shorts; final char[][] chars; final int[][] ints; final long[][] longs; final float[][] floats; final double[][] doubles; final Object[][] references;
        Chunk(int size, FieldSpec[] specs) { int n = specs.length; booleans = new boolean[n][]; bytes = new byte[n][]; shorts = new short[n][]; chars = new char[n][]; ints = new int[n][]; longs = new long[n][]; floats = new float[n][]; doubles = new double[n][]; references = new Object[n][]; for (int i=0;i<n;i++) { switch(specs[i].kind) { case BOOLEAN: booleans[i]=new boolean[size]; break; case BYTE: bytes[i]=new byte[size]; break; case SHORT: shorts[i]=new short[size]; break; case CHAR: chars[i]=new char[size]; break; case INT: ints[i]=new int[size]; break; case LONG: longs[i]=new long[size]; break; case FLOAT: floats[i]=new float[size]; break; case DOUBLE: doubles[i]=new double[size]; break; default: references[i]=new Object[size]; break; } } }
        private Chunk(Chunk other) { int n = other.booleans.length; booleans = new boolean[n][]; bytes = new byte[n][]; shorts = new short[n][]; chars = new char[n][]; ints = new int[n][]; longs = new long[n][]; floats = new float[n][]; doubles = new double[n][]; references = new Object[n][]; for (int i = 0; i < n; i++) { if (other.booleans[i] != null) booleans[i] = other.booleans[i].clone(); if (other.bytes[i] != null) bytes[i] = other.bytes[i].clone(); if (other.shorts[i] != null) shorts[i] = other.shorts[i].clone(); if (other.chars[i] != null) chars[i] = other.chars[i].clone(); if (other.ints[i] != null) ints[i] = other.ints[i].clone(); if (other.longs[i] != null) longs[i] = other.longs[i].clone(); if (other.floats[i] != null) floats[i] = other.floats[i].clone(); if (other.doubles[i] != null) doubles[i] = other.doubles[i].clone(); if (other.references[i] != null) references[i] = other.references[i].clone(); } }
        Chunk copy() { return new Chunk(this); }
        void set(int f,int o,FieldKind kind,RowSource s){switch(kind){case BOOLEAN:booleans[f][o]=s.bool(f);break;case BYTE:bytes[f][o]=s.byt(f);break;case SHORT:shorts[f][o]=s.sht(f);break;case CHAR:chars[f][o]=s.chr(f);break;case INT:ints[f][o]=s.integer(f);break;case LONG:longs[f][o]=s.lng(f);break;case FLOAT:floats[f][o]=s.flt(f);break;case DOUBLE:doubles[f][o]=s.dbl(f);break;default:references[f][o]=s.ref(f);break;}}
    }
}
