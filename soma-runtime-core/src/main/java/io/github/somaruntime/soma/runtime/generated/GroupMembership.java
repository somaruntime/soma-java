package io.github.somaruntime.soma.runtime.generated;

/**
 * Narrow parent SomaGroup callback held by one root ownership aggregate。
 *
 * <p>The callback never merges root trust state or turns Group into a DataFlow
 * guard. It only enforces member lifecycle and reports the first aggregate fault.</p>
 */
public interface GroupMembership {
    void bindAggregate(long aggregateInstanceId);
    void preflightAccess(long aggregateInstanceId, String operation);
    void aggregateFaulted(
            long aggregateInstanceId, String operation, String code);
    void releaseRoot(long aggregateInstanceId, String operation);
}
