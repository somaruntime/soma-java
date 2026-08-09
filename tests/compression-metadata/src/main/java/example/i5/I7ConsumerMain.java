package example.i5;

import io.github.somaruntime.soma.FieldMetadata;
import io.github.somaruntime.soma.GroupMetadata;
import io.github.somaruntime.soma.SomaCompression;
import io.github.somaruntime.soma.SomaConfiguration;
import io.github.somaruntime.soma.SomaMetadata;
import io.github.somaruntime.soma.TableMetadata;

/** Java 8 consumer journey for I7 compression, metadata and explain. */
public final class I7ConsumerMain {
    private I7ConsumerMain() {}

    public static void main(String[] args) {
        SomaMetadata before = Soma._metadata();
        require(before.configurationState()
                        == SomaMetadata.ConfigurationState.UNFROZEN,
                "metadata must not freeze configuration");
        require(!before.effectiveMemoryBudgetBytes().isPresent(),
                "unfrozen metadata must not invent a budget");
        require(!before.compression().isPresent(),
                "unfrozen metadata must not invent compression policy");

        Soma.configure(SomaConfiguration.builder()
                .memoryBudgetBytes(256L << 20)
                .compression(SomaCompression.AUTO)
                .build());
        SomaMetadata configured = Soma._metadata();
        require(configured.configurationState()
                        == SomaMetadata.ConfigurationState.FROZEN,
                "configured metadata state");
        require(configured.effectiveMemoryBudgetBytes().getAsLong()
                        == (256L << 20),
                "effective budget");
        require(configured.compression().get() == SomaCompression.AUTO,
                "effective compression policy");

        SomaGroup defaultGroup = Soma.defaultGroup();
        SomaGroup secondary = Soma.createGroup();
        GroupMetadata defaultMetadata = defaultGroup._metadata();
        require(defaultMetadata.defaultGroup(), "default Group identity");
        require(!secondary._metadata().defaultGroup(), "explicit Group identity");

        MachineEventTable events = defaultGroup.machineEventTable();
        for (long eventId = 1L; eventId <= 32768L; eventId++) {
            events.add(new MachineEvent(
                    eventId,
                    eventId & 3L,
                    (eventId & 1L) == 0L ? "even" : "odd",
                    7L,
                    true));
        }

        TableMetadata table = events._metadata();
        require(table.size() == 32768L, "metadata size");
        require(table.encoded(), "AUTO should encode profitable complete Chunk");
        require(table.savingsBytes() > 0L, "encoded representation savings");
        require(events.byRoute("even").count() == 16384L,
                "Index over encoded representation");
        require(events.duration.sum() == 32768L * 7L,
                "Field query over encoded representation");

        FieldMetadata route = events.route._metadata();
        require("route".equals(route.logicalPath()) && route.indexed(),
                "Field metadata identity and Index role");
        require(events._explain().contains("compression=AUTO")
                        && events._explain().contains("encodedChunks=1"),
                "explain representation decision");

        events.update(10L, editor -> editor.duration(9L));
        require(events.get(10L).duration() == 9L,
                "sparse update over encoded Chunk");
        require(events.duration.sum() == 32768L * 7L + 2L,
                "Field query over encoded Chunk with overlay");
        events.remove(10L);
        require(events.size() == 32767L && !events.find(10L).isPresent(),
                "remove and tail materialization");
        require(events.duration.sum() == 32767L * 7L,
                "Field query after encoded tail materialization");
        require(defaultGroup._metadata().retainedBytes() > 0L,
                "Group retained accounting");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
