package io.github.somaruntime.soma.internal;

interface MutationFaultInjector {

    MutationFaultInjector NONE = point -> false;

    boolean fail(MutationFaultPoint point);
}

enum MutationFaultPoint {
    BEFORE_CANDIDATE_PUBLISH,
    BEFORE_FINAL_COMMIT
}
