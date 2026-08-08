package example.i2;

import io.github.somaruntime.soma.SomaConfiguration;

public final class I2ScaleMain {

    private static final long ROWS = 1_000_000L;
    private static final long BUCKETS = 1024L;

    private I2ScaleMain() {
    }

    public static void main(String[] arguments) {
        Soma.configure(SomaConfiguration.builder()
                .memoryBudgetBytes(1L << 30)
                .build());
        ScaleRecordTable table = Soma.scaleRecordTable();
        table.reserve(ROWS);

        long started = System.nanoTime();
        for (long row = 0L; row < ROWS; row++) {
            table.add(new ScaleRecord(
                    row, row & (BUCKETS - 1L), (int) (row & 255L), (int) row));
        }
        long elapsed = System.nanoTime() - started;

        require(table.size() == ROWS, "million-row size");
        require(table.capacity() >= ROWS, "million-row capacity");
        require(table.get(ROWS - 1L).value() == (int) (ROWS - 1L), "last Key lookup");
        long bucket = 17L;
        long expected = ((ROWS - 1L - bucket) / BUCKETS) + 1L;
        require(table.byBucket(bucket).count() == expected, "million-row Index count");
        int shard = 19;
        long expectedShard = ((ROWS - 1L - shard) / 256L) + 1L;
        require(table.byShard(shard).count() == expectedShard,
                "million-row second Index count");

        long fingerprint = table.size();
        fingerprint = 31L * fingerprint + table.capacity();
        fingerprint = 31L * fingerprint + table.get(ROWS - 1L).value();
        fingerprint = 31L * fingerprint + expected;
        fingerprint = 31L * fingerprint + expectedShard;

        double seconds = elapsed / 1_000_000_000.0d;
        long rowsPerSecond = elapsed == 0L
                ? Long.MAX_VALUE
                : (long) (ROWS * 1_000_000_000.0d / elapsed);
        System.out.println("i2-million-profile: rows=" + ROWS
                + " capacity=" + table.capacity()
                + " elapsedSeconds=" + seconds
                + " rowsPerSecond=" + rowsPerSecond
                + " resultFingerprint=" + Long.toHexString(fingerprint));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
