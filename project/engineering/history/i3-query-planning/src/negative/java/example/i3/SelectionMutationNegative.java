package example.i3;

final class SelectionMutationNegative {
    void reject() {
        EventTable table = Soma.eventTable();
        table.filter(table.id.eq(1L)).remove();
    }
}
