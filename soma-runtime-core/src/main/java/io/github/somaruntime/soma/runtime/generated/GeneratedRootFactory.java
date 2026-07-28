package io.github.somaruntime.soma.runtime.generated;

import io.github.somaruntime.soma.runtime.RuntimePlan;
import io.github.somaruntime.soma.runtime.TablePlan;
import io.github.somaruntime.soma.runtime.metadata.SomaTableRuntimeMetadata;

/** Generated-only typed root construction/lifecycle binding for SomaGroup attach。 */
public interface GeneratedRootFactory<T> {
    T create(
            RuntimePlan runtimePlan,
            TablePlan tablePlan,
            ChildOwnershipRegistry ownership);
    void preflightRelease(T root);
    void release(T root);
    void preflightSafePoint(T root, String operation);
    SomaTableRuntimeMetadata runtimeMetadata(T root);
}
