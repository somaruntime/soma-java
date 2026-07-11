package com.example.internalnames;

import com.example.internalnames.generated.InternalNamesRowBatch;
import com.example.internalnames.generated.InternalNamesRowTable;

public final class InternalNamesConsumer {
    private InternalNamesConsumer() {
    }

    public static void main(String[] args) {
        InternalNamesRowTable table = InternalNamesRowTable.create();
        table.addBatch(new InternalNamesRowBatch().addValues(1, 2, 3, 4, 5));
        table.update(row -> {
            row.setCapacity(12);
            row.setScratchCapacity(13);
            row.setUpdateScratchCapacity(14);
            row.setCandidateScratch(15);
        });
        InternalNamesRow value = table.fetch(1);
        if (value.capacity != 12 || value.scratchCapacity != 13
                || value.updateScratchCapacity != 14 || value.candidateScratch != 15) {
            throw new AssertionError("internal scratch name isolation failed");
        }
        System.out.println("internal-names-phase6-consumer: ok");
    }
}
