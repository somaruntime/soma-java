package example.i1;

final class KeyEditorNegative {
    void mutate(EntityTable table) {
        table.update(1L, editor -> editor.id(2L));
    }
}
