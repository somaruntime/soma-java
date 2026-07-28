package io.github.somaruntime.soma.examples.grassing.support;

/**
 * 由稳定业务事实寻址的无状态随机源。
 *
 * <p>每次 draw 只依赖 seed、tick、stable identity、process 和 draw index，
 * 因而不会把 packed 物理顺序变成模型语义。</p>
 */
public final class DeterministicRandom {
  private DeterministicRandom() {
  }

  public static double unit(long seed, long tick, long identity,
                            int process, int draw) {
    long value = seed;
    value ^= mix(tick + 0x9e3779b97f4a7c15L);
    value ^= mix(identity + 0x632be59bd9b4e019L);
    value ^= mix((((long) process) << 32) ^ (draw & 0xffffffffL));
    return (mix(value) >>> 11) * 0x1.0p-53;
  }

  public static int bounded(long seed, long tick, long identity,
                            int process, int draw, int bound) {
    if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
    return (int) (unit(seed, tick, identity, process, draw) * bound);
  }

  private static long mix(long value) {
    value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
    value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
    return value ^ (value >>> 31);
  }
}
