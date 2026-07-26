package com.hgtech.soma.dataflow;

/** Opaque sequential consumer for one invocation-local group. */
public interface GroupConsumer {
    void accept(GroupCursor group);
}
