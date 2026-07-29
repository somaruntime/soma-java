package io.github.somaruntime.soma.dataflow;

/** Primitive Join invocation filter 的无假阴性与 fail-open 契约。 */
public final class JoinRuntimeFilterContractCheck {
    private JoinRuntimeFilterContractCheck() {
    }

    public static void main(String[] args) {
        require("minmax".equals(JoinRuntimeFilter.strategyFor(
                        1_000_000, 8_192, 0L, 8_191L)),
                "selective narrow build chooses minmax");
        require("bloom".equals(JoinRuntimeFilter.strategyFor(
                        1_000_000, 7_813, 0L, 999_936L)),
                "selective sparse build chooses bloom");
        require("none".equals(JoinRuntimeFilter.strategyFor(
                        65_536, 65_536, 0L, 65_535L)),
                "dense equal-size inputs fall back to hash equality");

        MinMaxJoinRuntimeFilter minmax =
                new MinMaxJoinRuntimeFilter(-7L, 11L);
        require(minmax.mightContain(-7L)
                        && minmax.mightContain(0L)
                        && minmax.mightContain(11L),
                "minmax accepts its complete range");
        require(!minmax.mightContain(-8L)
                        && !minmax.mightContain(12L),
                "minmax rejects values outside its range");

        BloomJoinRuntimeFilter bloom =
                new BloomJoinRuntimeFilter(new long[16], 1023);
        long[] members = new long[128];
        for (int index = 0; index < members.length; index++) {
            long value = (long) index * 1_000_003L - 31_337L;
            members[index] = value;
            bloom.add(value);
        }
        bloom.add(Long.MIN_VALUE);
        bloom.add(Long.MAX_VALUE);
        for (long member : members) {
            require(bloom.mightContain(member),
                    "bloom filter false negative");
        }
        require(bloom.mightContain(Long.MIN_VALUE)
                        && bloom.mightContain(Long.MAX_VALUE),
                "bloom long boundary membership");

        int rejected = 0;
        for (int index = 0; index < 1024; index++) {
            long value = (long) index * 7_000_001L + 97L;
            if (!bloom.mightContain(value)) {
                rejected++;
            }
        }
        require(rejected > 0, "bloom filter must reject some non-members");
        System.out.println("join-runtime-filter-contract: ok");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
