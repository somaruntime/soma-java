package com.hgtech.soma.runtime.generated;

/**
 * Generated Scan 共享的紧凑 stage storage 与 one-shot generation 状态。
 *
 * <p>该类型只保存 IR，不执行 schema callback，也不解释 metadata。typed source、Cursor
 * 和 executor 仍由 annotation processor 生成。</p>
 */
public class GeneratedScanPlan {
    private final String sourcePath;
    private Inline inline;
    private Overflow overflow;
    private int stageCount;
    private boolean active = true;

    public GeneratedScanPlan(String sourcePath) {
        if (sourcePath == null) {
            throw new NullPointerException("sourcePath");
        }
        this.sourcePath = sourcePath;
    }

    public final int stageCount() {
        return stageCount;
    }

    public final boolean isCurrent(int generation) {
        return active && stageCount == generation;
    }

    public final void consume() {
        active = false;
    }

    public final void append(
            byte kind, Object callback, long argument, int nextGeneration) {
        int count = stageCount;
        if (nextGeneration != count + 1) {
            throw new IllegalStateException("pipeline generation");
        }
        if (count < 3) {
            Inline next = inline;
            if (next == null) {
                next = new Inline();
            }
            if (count == 0) {
                next.kind0 = kind;
                next.callback0 = callback;
                next.argument0 = argument;
            } else if (count == 1) {
                next.kind1 = kind;
                next.callback1 = callback;
                next.argument1 = argument;
            } else {
                next.kind2 = kind;
                next.callback2 = callback;
                next.argument2 = argument;
            }
            inline = next;
            stageCount = count + 1;
            return;
        }

        Overflow next = overflow;
        if (count == 3) {
            next = new Overflow(8);
            next.kinds[0] = inline.kind0;
            next.kinds[1] = inline.kind1;
            next.kinds[2] = inline.kind2;
            next.callbacks[0] = inline.callback0;
            next.callbacks[1] = inline.callback1;
            next.callbacks[2] = inline.callback2;
            next.arguments[0] = inline.argument0;
            next.arguments[1] = inline.argument1;
            next.arguments[2] = inline.argument2;
        } else if (count == overflow.kinds.length) {
            int capacity = count <= Integer.MAX_VALUE / 2
                    ? count * 2 : Integer.MAX_VALUE;
            if (capacity <= count) {
                throw new OutOfMemoryError("pipeline stage capacity");
            }
            next = new Overflow(capacity);
            System.arraycopy(overflow.kinds, 0, next.kinds, 0, count);
            System.arraycopy(overflow.callbacks, 0, next.callbacks, 0, count);
            System.arraycopy(overflow.arguments, 0, next.arguments, 0, count);
        }
        next.kinds[count] = kind;
        next.callbacks[count] = callback;
        next.arguments[count] = argument;
        overflow = next;
        stageCount = count + 1;
        if (count == 3) {
            inline = null;
        }
    }

    public final byte kind(int index) {
        if (overflow != null) {
            return overflow.kinds[index];
        }
        return index == 0 ? inline.kind0 : index == 1 ? inline.kind1 : inline.kind2;
    }

    public final Object callback(int index) {
        if (overflow != null) {
            return overflow.callbacks[index];
        }
        return index == 0
                ? inline.callback0 : index == 1 ? inline.callback1 : inline.callback2;
    }

    public final long argument(int index) {
        if (overflow != null) {
            return overflow.arguments[index];
        }
        return index == 0
                ? inline.argument0 : index == 1 ? inline.argument1 : inline.argument2;
    }

    public final void argument(int index, long value) {
        if (overflow != null) {
            overflow.arguments[index] = value;
        } else if (index == 0) {
            inline.argument0 = value;
        } else if (index == 1) {
            inline.argument1 = value;
        } else {
            inline.argument2 = value;
        }
    }

    public final void clear() {
        if (inline != null) {
            inline.callback0 = null;
            inline.callback1 = null;
            inline.callback2 = null;
        }
        if (overflow != null) {
            for (int index = 0; index < stageCount; index++) {
                overflow.callbacks[index] = null;
            }
        }
        inline = null;
        overflow = null;
        clearSource();
    }

    public final String sourcePath() {
        return sourcePath;
    }

    protected void clearSource() {
        // Generated typed source plans clear their table and source-key references here.
    }

    private static final class Inline {
        private byte kind0;
        private byte kind1;
        private byte kind2;
        private Object callback0;
        private Object callback1;
        private Object callback2;
        private long argument0;
        private long argument1;
        private long argument2;
    }

    private static final class Overflow {
        private final byte[] kinds;
        private final Object[] callbacks;
        private final long[] arguments;

        private Overflow(int capacity) {
            kinds = new byte[capacity];
            callbacks = new Object[capacity];
            arguments = new long[capacity];
        }
    }
}
