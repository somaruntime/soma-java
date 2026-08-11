package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import java.util.IdentityHashMap;
import java.util.List;

/** Operation-local hash membership prepared for typed IN predicates. */
final class PredicateMembership {

    private final GeneratedTableLayout layout;
    private final IdentityHashMap<PredicateIr, ProbeSet> sets;

    private PredicateMembership(
            GeneratedTableLayout layout,
            IdentityHashMap<PredicateIr, ProbeSet> sets) {
        this.layout = layout;
        this.sets = sets;
    }

    static PredicateMembership prepare(
            GeneratedTableLayout layout,
            List<LogicalRowPlan.Stage> stages,
            SomaOperation operation,
            Object provenance) {
        IdentityHashMap<PredicateIr, ProbeSet> sets =
                new IdentityHashMap<PredicateIr, ProbeSet>();
        for (LogicalRowPlan.Stage stage : stages) {
            if (stage.kind == LogicalRowPlan.StageKind.TYPED_FILTER) {
                collect(layout, stage.predicate, sets, operation, provenance);
            }
        }
        return new PredicateMembership(layout, sets);
    }

    static PredicateMembership preparePredicates(
            GeneratedTableLayout layout,
            List<PredicateIr> predicates,
            SomaOperation operation,
            Object provenance) {
        IdentityHashMap<PredicateIr, ProbeSet> sets =
                new IdentityHashMap<PredicateIr, ProbeSet>();
        for (PredicateIr predicate : predicates) {
            collect(layout, predicate, sets, operation, provenance);
        }
        return new PredicateMembership(layout, sets);
    }

    private static void collect(
            GeneratedTableLayout layout,
            PredicateIr predicate,
            IdentityHashMap<PredicateIr, ProbeSet> sets,
            SomaOperation operation,
            Object provenance) {
        switch (predicate.kind) {
            case IN:
                if (!sets.containsKey(predicate)) {
                    sets.put(predicate, new ProbeSet(
                            layout,
                            predicate.fieldIndex,
                            predicate.literals,
                            operation,
                            provenance));
                }
                return;
            case AND:
            case OR:
                collect(layout, predicate.left, sets, operation, provenance);
                collect(layout, predicate.right, sets, operation, provenance);
                return;
            case NOT:
                collect(layout, predicate.left, sets, operation, provenance);
                return;
            default:
                return;
        }
    }

    boolean contains(
            PredicateIr predicate,
            TableChunkDirectory directory,
            int locator) {
        ProbeSet set = sets.get(predicate);
        if (set == null) {
            throw new AssertionError("missing prepared IN membership");
        }
        return set.contains(layout, directory, locator);
    }

    private static final class ProbeSet {
        private final int fieldIndex;
        private final TypedLiteral[] probes;
        private final byte[] occupied;
        private final int mask;

        ProbeSet(
                GeneratedTableLayout layout,
                int fieldIndex,
                TypedLiteral[] literals,
                SomaOperation operation,
                Object provenance) {
            this.fieldIndex = fieldIndex;
            int capacity = capacity(literals.length, operation, provenance);
            this.probes = new TypedLiteral[capacity];
            this.occupied = new byte[capacity];
            this.mask = capacity - 1;
            for (TypedLiteral literal : literals) {
                add(layout, literal);
            }
        }

        private void add(
                GeneratedTableLayout layout,
                TypedLiteral literal) {
            long hash = layout.hashField(literal, fieldIndex);
            int slot = slot(hash, mask);
            while (occupied[slot] != 0) {
                if (layout.fieldEquals(
                        probes[slot], literal, fieldIndex)) return;
                slot = (slot + 1) & mask;
            }
            occupied[slot] = 1;
            probes[slot] = literal;
        }

        boolean contains(
                GeneratedTableLayout layout,
                TableChunkDirectory directory,
                int locator) {
            long hash = layout.hashField(directory, locator, fieldIndex);
            int slot = slot(hash, mask);
            while (occupied[slot] != 0) {
                if (layout.fieldEquals(
                        directory, locator, probes[slot], fieldIndex)) {
                    return true;
                }
                slot = (slot + 1) & mask;
            }
            return false;
        }

        private static int slot(long hash, int mask) {
            return ((int) (hash ^ (hash >>> 32))) & mask;
        }

        private static int capacity(
                int expected,
                SomaOperation operation,
                Object provenance) {
            if (expected <= 1) return 2;
            if (expected > (1 << 29)) {
                throw SomaFailures.failure(
                        SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                        operation,
                        "IN membership exceeds Java array boundary",
                        provenance);
            }
            int required = expected << 1;
            int capacity = 2;
            while (capacity < required) capacity <<= 1;
            return capacity;
        }
    }
}
