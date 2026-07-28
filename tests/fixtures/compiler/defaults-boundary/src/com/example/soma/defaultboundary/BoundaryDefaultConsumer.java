package com.example.soma.defaultboundary;

import com.example.soma.defaultboundary.generated.BoundaryDefaultBatch;
import com.example.soma.defaultboundary.generated.BoundaryDefaultTable;

public final class BoundaryDefaultConsumer {
    private BoundaryDefaultConsumer() {
    }

    public static void main(String[] args) {
        BoundaryDefaultBatch batch = new BoundaryDefaultBatch().addValues(row -> { });
        BoundaryDefaultTable table = BoundaryDefaultTable.create();
        table.addBatch(batch);
        String value = table.fetchAt(0).value;
        if (value.length() != 4096 || !value.equals(DefaultLiterals.X4096)) {
            throw new AssertionError("4096 UTF-16-unit default was not preserved");
        }
        System.out.println("defaults-boundary-consumer: ok");
    }
}
