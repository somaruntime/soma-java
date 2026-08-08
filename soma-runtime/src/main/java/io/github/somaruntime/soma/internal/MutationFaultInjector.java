package io.github.somaruntime.soma.internal;

interface MutationFaultInjector {

    MutationFaultInjector NONE = point -> false;

    boolean fail(MutationFaultPoint point);
}

enum MutationFaultPoint {
    BEFORE_KEY_REBUILD,
    BEFORE_INDEX_REBUILD,
    BEFORE_SIDECAR_ACCOUNTING,
    BEFORE_CANDIDATE_PUBLISH,
    BEFORE_FINAL_COMMIT
}
