package com.example.soma.i1;

import io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime;

/** Internal evidence probe; not part of the generated application surface. */
public final class PublicationProbe {
    private PublicationProbe() {
    }

    public static void main(String[] arguments) {
        PrimitiveLongTableRuntime.GroupRuntime group =
                new PrimitiveLongTableRuntime.GroupRuntime();
        PrimitiveLongTableRuntime runtime =
                new PrimitiveLongTableRuntime(group, 2L);
        runtime.add(1L, 10L);
        if (runtime.stateVersion() != 1L) {
            throw new AssertionError("add did not publish generation one");
        }

        PrimitiveLongTableRuntime.PointUpdate candidate = runtime.beginUpdate(1L);
        candidate.payload(11L);
        candidate.commit();
        assertPayload(runtime, 11L);
        if (runtime.stateVersion() != 2L) {
            throw new AssertionError("candidate publication did not advance generation");
        }

        PrimitiveLongTableRuntime.PointUpdate prevalidated = runtime.beginUpdate(1L);
        prevalidated.payload(12L);
        prevalidated.prepareInPlace();
        prevalidated.commitPrevalidated();
        assertPayload(runtime, 12L);
        if (runtime.stateVersion() != 3L) {
            throw new AssertionError("prevalidated publication did not advance generation");
        }
        if (runtime.size() != 1L) {
            throw new AssertionError("publication changed row cardinality");
        }
    }

    private static void assertPayload(
            PrimitiveLongTableRuntime runtime,
            long expected) {
        PrimitiveLongTableRuntime.Query query = runtime.beginQuery();
        try {
            long locator = query.findLocator(1L);
            if (locator < 0L || query.payloadAt(locator) != expected) {
                throw new AssertionError("published payload mismatch");
            }
        } finally {
            query.close();
        }
    }
}
