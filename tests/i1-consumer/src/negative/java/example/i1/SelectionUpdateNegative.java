package example.i1;

final class SelectionUpdateNegative {
    void use(EntityTable table) {
        table.selectAll().update(editor -> editor.value(1L));
    }
}
