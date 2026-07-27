package com.hgtech.soma.runtime.metadata;

import com.hgtech.soma.runtime.SomaGroupMemberState;

/** Detached historical snapshot of one stable SomaGroup member slot。 */
public interface SomaGroupMemberMetadata {
    String memberId();
    String schemaHash();
    String rootTable();
    String runtimePlanHash();
    SomaGroupMemberState state();
    long aggregateInstanceId();
    long attachmentOrdinal();
}
