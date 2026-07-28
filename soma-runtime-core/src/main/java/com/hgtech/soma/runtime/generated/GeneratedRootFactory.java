package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.TablePlan;
import com.hgtech.soma.runtime.metadata.SomaTableRuntimeMetadata;

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
