package example.i1;

final class CallbackFilterNegative {
    void use(EntityTable table) {
        table.filter(view -> true);
    }
}
