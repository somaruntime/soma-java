package com.hgtech.soma.runtime.metadata;

import com.hgtech.soma.runtime.SomaGroupState;

import java.util.List;

/** Detached historical snapshot of SomaGroup membership topology。 */
public interface SomaGroupMetadata {
    String logicalGroupId();
    String groupPlanHash();
    long groupInstanceId();
    boolean explicit();
    SomaGroupState state();
    long membershipEpoch();
    String dataVersion();
    long maximumStructuralBytes();
    long retainedStructuralBytes();
    long transientStructuralBytes();
    long currentStructuralBytes();
    long structuralHighWaterBytes();
    long maximumTableInstances();
    long currentTableInstances();
    long tableInstanceHighWater();
    List<SomaGroupMemberMetadata> members();
    SomaGroupMemberMetadata requireMember(String memberId);
}
