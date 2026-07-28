package io.github.somaruntime.soma.runtime.generated;

import io.github.somaruntime.soma.runtime.TablePlan;

/** Test-source protocol harness for atomic flat-head/fixed-tail publication。 */
public final class StorageTestProtocol {
    private StorageTestProtocol() {
    }

    public static void verifySegmentedPublication(TablePlan plan) {
        BooleanColumn booleans = new BooleanColumn();
        ByteColumn bytes = new ByteColumn();
        ShortColumn shorts = new ShortColumn();
        IntColumn ints = new IntColumn();
        LongColumn longs = new LongColumn();
        FloatColumn floats = new FloatColumn();
        DoubleColumn doubles = new DoubleColumn();
        StringColumn strings = new StringColumn();
        PresenceBitmap presence = new PresenceBitmap();
        FailingColumn failure = new FailingColumn();
        ColumnGroup group = new ColumnGroup(
                "Large",
                plan,
                new ChildOwnershipRegistry(),
                plan.initialCapacity(),
                booleans,
                bytes,
                shorts,
                ints,
                longs,
                floats,
                doubles,
                strings,
                presence,
                failure);
        ints.set(0, 17);
        strings.set(0, "head");
        presence.setPresent(0);

        failure.failNext = true;
        try {
            group.ensureCapacity(
                    plan.flatHeadRows() + 1,
                    plan.growthNumerator(),
                    plan.growthDenominator());
            throw new AssertionError(
                    "staged segment failure must reject publication");
        } catch (IllegalStateException expected) {
            if (!"intentional segment stage failure".equals(
                    expected.getMessage())) {
                throw expected;
            }
        }
        require(group.capacity() == plan.initialCapacity(),
                "failed stage preserves visible capacity");
        require(ints.get(0) == 17 && "head".equals(strings.get(0))
                        && presence.isPresent(0),
                "failed stage preserves payload and presence");

        group.ensureCapacity(
                plan.flatHeadRows() + 1,
                plan.growthNumerator(),
                plan.growthDenominator());
        require(group.capacity() == plan.flatHeadRows() + plan.segmentRows(),
                "tail growth publishes one complete segment");
        require(group.segmentCount() == 2,
                "head plus one tail topology");
        require(group.segmentEndExclusive(0, group.capacity())
                        == plan.flatHeadRows(),
                "head segment boundary");
        require(group.segmentEndExclusive(
                        plan.flatHeadRows(), group.capacity())
                        == group.capacity(),
                "tail segment boundary");

        int before = plan.flatHeadRows() - 1;
        int boundary = plan.flatHeadRows();
        int after = boundary + 1;
        booleans.set(boundary, true);
        bytes.set(boundary, (byte) 7);
        shorts.set(boundary, (short) 8);
        ints.set(before, 11);
        ints.set(boundary, 12);
        ints.set(after, 13);
        longs.set(boundary, 14L);
        floats.set(boundary, 15.0f);
        doubles.set(boundary, 16.0d);
        String equalButDistinct = new String("tail");
        strings.set(boundary, equalButDistinct);
        presence.setPresent(before);
        presence.setPresent(boundary);
        presence.setPresent(after);

        require(booleans.get(boundary)
                        && bytes.get(boundary) == (byte) 7
                        && shorts.get(boundary) == (short) 8
                        && longs.get(boundary) == 14L
                        && Float.floatToIntBits(floats.get(boundary))
                        == Float.floatToIntBits(15.0f)
                        && Double.doubleToLongBits(doubles.get(boundary))
                        == Double.doubleToLongBits(16.0d)
                        && strings.get(boundary) == equalButDistinct,
                "all concrete columns cross head-tail boundary");

        ints.copyFrom(ints, before, boundary, 3);
        require(ints.get(boundary) == 11
                        && ints.get(after) == 12
                        && ints.get(after + 1) == 13,
                "overlapping cross-segment copy is memmove-correct");
        presence.copyFrom(presence, before, boundary, 3);
        require(presence.isPresent(boundary)
                        && presence.isPresent(after)
                        && presence.isPresent(after + 1),
                "presence copy crosses segment boundary");

        booleans.clearRange(boundary, after + 1);
        bytes.clearRange(boundary, after + 1);
        shorts.clearRange(boundary, after + 1);
        ints.clearRange(boundary, after + 2);
        longs.clearRange(boundary, after + 1);
        floats.clearRange(boundary, after + 1);
        doubles.clearRange(boundary, after + 1);
        strings.clearRange(boundary, after + 1);
        presence.clearRange(boundary, after + 2);
        require(!booleans.get(boundary)
                        && bytes.get(boundary) == (byte) 0
                        && shorts.get(boundary) == (short) 0
                        && ints.get(boundary) == 0
                        && ints.get(after + 1) == 0
                        && longs.get(boundary) == 0L
                        && Float.floatToIntBits(floats.get(boundary)) == 0
                        && Double.doubleToLongBits(doubles.get(boundary)) == 0L
                        && strings.get(boundary) == null
                        && !presence.isPresent(boundary)
                        && !presence.isPresent(after)
                        && !presence.isPresent(after + 1),
                "segment-wise clear removes payload, references and presence");
        require(ints.get(0) == 17 && "head".equals(strings.get(0)),
                "tail mutation preserves head");

        group.releaseStorage();
        require(group.capacity() == 0,
                "release clears segmented storage");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FailingColumn extends GeneratedColumn {
        private int capacity;
        private boolean failNext;

        @Override public Object stageCapacity(int newCapacity) {
            requireGrowth(capacity, newCapacity);
            if (failNext) {
                failNext = false;
                throw new IllegalStateException(
                        "intentional segment stage failure");
            }
            return Integer.valueOf(newCapacity);
        }

        @Override public void commitCapacity(Object stagedCapacity) {
            if (!(stagedCapacity instanceof Integer)) {
                throw new IllegalStateException(
                        "staged capacity type mismatch");
            }
            int proposed = ((Integer) stagedCapacity).intValue();
            requireGrowth(capacity, proposed);
            capacity = proposed;
        }

        @Override public void clearRange(int from, int to) {
            requireRange(from, to, capacity);
        }

        @Override long estimatedBytes(int proposed) {
            requireCapacity(proposed);
            return 4L * (long) proposed;
        }

        @Override long stagingAllocationBytes(int proposed) {
            requireGrowth(capacity, proposed);
            return proposed == capacity ? 0L : 4L * (long) proposed;
        }

        @Override long replacementTransientBytes(int proposed) {
            requireGrowth(capacity, proposed);
            return proposed == capacity ? 0L : 4L * (long) capacity;
        }

        @Override long retainedBytes() {
            return 4L * (long) capacity;
        }

        @Override void releaseStorage() {
            capacity = 0;
        }
    }
}
