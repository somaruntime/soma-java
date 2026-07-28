package io.github.somaruntime.soma.dataflow;

/** Opaque sequential visitor for one invocation-local group。 */
public interface GroupVisitor {
    /** 返回 {@code false} 表示消费当前 group 后 early stop。 */
    boolean visit(GroupCursor group);
}
