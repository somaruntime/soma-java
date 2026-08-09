package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;

/** Internal exact-typed mutation access used by generated Editor wrappers. */
public interface GeneratedEditorAccess extends GeneratedRowAccess {

    void requireArgument(Object value, SomaOperation operation, String category);

    void editBoolean(int leaf, boolean value);
    void editByte(int leaf, byte value);
    void editShort(int leaf, short value);
    void editChar(int leaf, char value);
    void editInt(int leaf, int value);
    void editLong(int leaf, long value);
    void editFloat(int leaf, float value);
    void editDouble(int leaf, double value);
    void editReference(int leaf, Object value);
}
