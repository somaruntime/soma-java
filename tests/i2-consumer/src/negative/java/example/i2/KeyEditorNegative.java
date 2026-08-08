package example.i2;

final class KeyEditorNegative {
    void use(AllTypesTable table, MachinePair key) {
        table.update(key, editor -> editor.key(key));
    }
}
