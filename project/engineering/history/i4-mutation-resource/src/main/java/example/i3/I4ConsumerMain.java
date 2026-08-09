package example.i3;

import io.github.somaruntime.soma.RemoveResult;
import io.github.somaruntime.soma.UpdateResult;
import example.i3.schema.Status;

/** Java 8 application consumer for Selection mutation. */
public final class I4ConsumerMain {
    private I4ConsumerMain() {}

    public static void main(String[] args) {
        EventTable table = Soma.createGroup().eventTable();
        table.add(event(1L, "selected", 10L));
        table.add(event(2L, "other", 20L));
        table.add(event(3L, "selected", 30L));
        table.add(event(4L, "remove", -1L));

        UpdateResult indexed = table.byKind("selected").update(editor ->
                editor.amount(editor.amount() + 5L));
        require(indexed.matched() == 2L && indexed.changed() == 2L,
                "Index selection update result");
        require(table.get(1L).amount() == 15L && table.get(3L).amount() == 35L,
                "Index selection update payload");

        UpdateResult noOp = table.selectAll().update(editor ->
                editor.amount(editor.amount()));
        require(noOp.matched() == 4L && noOp.changed() == 0L,
                "Selection no-op result");

        UpdateResult filtered = table
                .filter(table.kind.eq("other"))
                .update(editor -> editor.enabled(true));
        require(filtered.matched() == 1L && filtered.changed() == 1L
                        && table.get(2L).enabled(),
                "typed Selection update");

        RemoveResult removed = table
                .filter(table.amount.le(0L))
                .remove();
        require(removed.removed() == 1L && table.size() == 3L
                        && !table.find(4L).isPresent(),
                "Selection remove");
    }

    private static Event event(long id, String kind, long amount) {
        return new Event(
                id, kind, false, (byte) 1, (short) 1, 'a', 1, amount,
                1.0f, 1.0d, kind, new Object(),
                new RouteKey(id, id + 1L), Status.ACTIVE);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
