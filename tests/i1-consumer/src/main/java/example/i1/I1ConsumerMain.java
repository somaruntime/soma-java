package example.i1;

import io.github.somaruntime.soma.SomaExpression;
import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.UpdateResult;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class I1ConsumerMain {

    private I1ConsumerMain() {
    }

    public static void main(String[] arguments) {
        EntityTable table = Soma.entityTable();
        require(table == Soma.entityTable(), "default Table identity");
        require(table == Soma.defaultGroup().entityTable(), "default Group routing");

        SomaGroup explicit = Soma.createGroup();
        EntityTable isolated = explicit.entityTable();
        require(isolated == explicit.entityTable(), "explicit Table identity");
        require(isolated != table, "Group isolation");

        require(table.size() == 0L, "initial size");
        require(table.capacity() == 0L, "initial capacity");
        table.reserve(3L);
        require(table.capacity() >= 3L, "reserve");

        Entity carrier = new Entity(0L, 10L);
        table.add(carrier);
        carrier.value(999L);
        require(table.get(0L).value() == 10L, "add copies carrier");
        table.add(new Entity(1L, 20L));

        Optional<Entity> found = table.find(1L);
        require(found.isPresent() && found.get().value() == 20L, "find hit");
        require(!table.find(99L).isPresent(), "find missing");
        expect(SomaFailureCode.MISSING_KEY, () -> table.get(99L));

        Entity detached = table.get(1L);
        detached.value(2000L);
        require(table.get(1L).value() == 20L, "get is detached");

        EntityTable.Selection terminalBound = table.filter(table.value.ge(20L));
        table.add(new Entity(2L, 30L));
        require(terminalBound.count() == 2L, "terminal-start binding");
        expect(SomaFailureCode.PIPELINE_ALREADY_CONSUMED, terminalBound::count);

        long[] literals = new long[] {10L, 30L, 30L};
        SomaExpression<EntityTable.View> literalSnapshot = table.value.in(literals);
        literals[0] = 999L;
        require(table.filter(literalSnapshot).count() == 2L, "in literal snapshot");
        require(table.filter(table.value.in()).count() == 0L, "empty in");
        require(table.filter(
                table.value.gt(10L).and(table.value.lt(30L))).count() == 1L,
                "and expression");
        require(table.filter(
                table.value.eq(10L).or(table.value.eq(30L))).count() == 2L,
                "or expression");
        require(table.filter(table.value.eq(20L).not()).count() == 2L,
                "not expression");

        AtomicBoolean missingCalled = new AtomicBoolean();
        UpdateResult missing = table.update(99L, editor -> missingCalled.set(true));
        require(missing.matched() == 0L && missing.changed() == 0L,
                "missing update result");
        require(!missingCalled.get(), "missing update callback");

        UpdateResult noChange = table.update(1L, editor -> editor.value(20L));
        require(noChange.matched() == 1L && noChange.changed() == 0L,
                "no-change update");

        final EntityTable.Editor[] escaped = new EntityTable.Editor[1];
        UpdateResult changed = table.update(1L, editor -> {
            editor.value(editor.value() + 5L);
            require(editor.fetch().value() == 25L, "Editor fetch");
            escaped[0] = editor;
        });
        require(changed.matched() == 1L && changed.changed() == 1L,
                "changed update");
        require(table.get(1L).value() == 25L, "update publication");
        SomaOperationException scope = expect(
                SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                () -> escaped[0].value());

        SomaOperationException replay = expect(
                SomaFailureCode.CALLBACK_FAILED,
                () -> table.update(1L, editor -> {
                    throw scope;
                }));
        require(replay.getCause() == scope, "foreign failure replay is callback failure");
        require(table.get(1L).value() == 25L, "callback failure publishes nothing");

        expect(SomaFailureCode.DUPLICATE_KEY,
                () -> table.add(new Entity(0L, 100L)));
        expect(SomaFailureCode.INVALID_ARGUMENT, () -> table.reserve(-1L));
        expect(SomaFailureCode.INVALID_ARGUMENT,
                () -> table.value.between(5L, 4L));
        expect(SomaFailureCode.INVALID_ARGUMENT,
                () -> isolated.filter(table.value.eq(10L)));
        require(isolated.size() == 0L, "explicit Group remains isolated");

        System.out.println("i1-consumer: ok");
    }

    private static SomaOperationException expect(
            SomaFailureCode code,
            ThrowingAction action) {
        try {
            action.run();
        } catch (SomaOperationException failure) {
            require(failure.code() == code,
                    "expected " + code + " but got " + failure.code());
            return failure;
        }
        throw new AssertionError("expected SOMA failure " + code);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private interface ThrowingAction {
        void run();
    }
}
