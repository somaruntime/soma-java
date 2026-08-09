package example.i3;

/** Emits the deterministic I3 logical-to-physical planning baseline. */
public final class I3PlanGoldenMain {
    private I3PlanGoldenMain() {}

    public static void main(String[] args) {
        EventTable table = Soma.eventTable();
        System.out.println("adjacent-filter-field="
                + table.filter(table.kind.eq("a"))
                        .filter(table.enabled.eq(true))
                        .mapToLong(table.amount)
                        ._explain());
        System.out.println("key-lookup="
                + table.filter(table.id.eq(1L))._explain());
        System.out.println("index-lookup="
                + table.filter(table.kind.eq("a"))._explain());
        System.out.println("callback-barrier="
                + table.filter(view -> true)
                        .filter(table.kind.eq("a"))
                        ._explain());
        System.out.println("field-source=" + table.amount._explain());
    }
}
