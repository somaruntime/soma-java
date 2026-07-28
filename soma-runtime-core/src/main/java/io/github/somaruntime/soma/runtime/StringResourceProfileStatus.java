package io.github.somaruntime.soma.runtime;

/** String reachable-byte projection 的声明强度；两者都不是 JVM hard heap cap。 */
public enum StringResourceProfileStatus {
    UNPROFILED,
    PROFILED_UNVERIFIED
}
