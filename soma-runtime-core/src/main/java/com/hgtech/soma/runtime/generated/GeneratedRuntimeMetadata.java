package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.TablePlan;
import com.hgtech.soma.runtime.TableStats;
import com.hgtech.soma.runtime.metadata.SomaExactAccessRuntimeMetadata;
import com.hgtech.soma.runtime.metadata.SomaIndexMetadata;
import com.hgtech.soma.runtime.metadata.SomaIndexRuntimeMetadata;
import com.hgtech.soma.runtime.metadata.SomaPrimaryLocator;
import com.hgtech.soma.runtime.metadata.SomaPrimaryLocatorLayout;
import com.hgtech.soma.runtime.metadata.SomaSegmentKind;
import com.hgtech.soma.runtime.metadata.SomaSegmentMetadata;
import com.hgtech.soma.runtime.metadata.SomaStorageLayout;
import com.hgtech.soma.runtime.metadata.SomaTableMetadata;
import com.hgtech.soma.runtime.metadata.SomaTableRuntimeMetadata;
import com.hgtech.soma.runtime.metadata.SomaUniqueMetadata;
import com.hgtech.soma.runtime.metadata.SomaUniqueRuntimeMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generated-code protocol for producing immutable Runtime Metadata snapshots。
 *
 * <p>The factory validates already-bound facts and never participates in a Table
 * hot path or RuntimePlan identity.</p>
 */
public final class GeneratedRuntimeMetadata {
    private static final String EXACT_INDEX_IMPLEMENTATION =
            "grouped-exact-hash-v1";

    private GeneratedRuntimeMetadata() {
    }

    public static SomaIndexRuntimeMetadata index(
            SomaIndexMetadata descriptor,
            long entries,
            long groups,
            long probes,
            long collisions,
            long rehashes,
            long retainedBytes,
            long highWaterBytes) {
        return new IndexSnapshot(
                descriptor,
                checkedAccess(
                        descriptor == null ? null : descriptor.name(),
                        entries, groups, probes, collisions, rehashes,
                        retainedBytes, highWaterBytes));
    }

    public static SomaUniqueRuntimeMetadata unique(
            SomaUniqueMetadata descriptor,
            long entries,
            long groups,
            long probes,
            long collisions,
            long rehashes,
            long retainedBytes,
            long highWaterBytes) {
        return new UniqueSnapshot(
                descriptor,
                checkedAccess(
                        descriptor == null ? null : descriptor.name(),
                        entries, groups, probes, collisions, rehashes,
                        retainedBytes, highWaterBytes));
    }

    public static SomaTableRuntimeMetadata table(
            SomaTableMetadata descriptor,
            TablePlan plan,
            TableStats observation,
            SomaIndexRuntimeMetadata[] indexes,
            SomaUniqueRuntimeMetadata[] uniques) {
        if (descriptor == null) throw new NullPointerException("descriptor");
        if (plan == null) throw new NullPointerException("plan");
        if (observation == null) throw new NullPointerException("observation");
        if (indexes == null) throw new NullPointerException("indexes");
        if (uniques == null) throw new NullPointerException("uniques");
        if (!descriptor.logicalName().equals(plan.tableLogicalName())) {
            throw new IllegalArgumentException(
                    "Runtime Metadata descriptor/plan Table mismatch");
        }
        if (observation.rows() < 0
                || observation.capacity() < observation.rows()) {
            throw new IllegalArgumentException(
                    "Runtime Metadata observation shape mismatch");
        }
        List<SomaIndexRuntimeMetadata> indexSnapshots =
                immutableIndexes(descriptor, indexes);
        List<SomaUniqueRuntimeMetadata> uniqueSnapshots =
                immutableUniques(descriptor, uniques);
        return new TableSnapshot(
                descriptor,
                plan,
                observation,
                segments(plan, observation.rows(), observation.capacity()),
                indexSnapshots,
                uniqueSnapshots);
    }

    private static AccessSnapshot checkedAccess(
            String name,
            long entries,
            long groups,
            long probes,
            long collisions,
            long rehashes,
            long retainedBytes,
            long highWaterBytes) {
        if (name == null) throw new NullPointerException("descriptor");
        if (entries < 0L || groups < 0L || groups > entries
                || probes < 0L || collisions < 0L || collisions > probes
                || rehashes < 0L || retainedBytes < 0L
                || highWaterBytes < retainedBytes) {
            throw new IllegalArgumentException(
                    "invalid exact-access Runtime Metadata");
        }
        return new AccessSnapshot(
                name, entries, groups, probes, collisions, rehashes,
                retainedBytes, highWaterBytes);
    }

    private static List<SomaIndexRuntimeMetadata> immutableIndexes(
            SomaTableMetadata descriptor,
            SomaIndexRuntimeMetadata[] values) {
        if (values.length != descriptor.indexes().size()) {
            throw new IllegalArgumentException(
                    "Runtime Metadata index shape mismatch");
        }
        ArrayList<SomaIndexRuntimeMetadata> result =
                new ArrayList<SomaIndexRuntimeMetadata>(values.length);
        for (int index = 0; index < values.length; index++) {
            SomaIndexRuntimeMetadata value = values[index];
            if (value == null
                    || value.descriptor()
                    != descriptor.indexes().get(index)) {
                throw new IllegalArgumentException(
                        "Runtime Metadata index descriptor mismatch");
            }
            result.add(value);
        }
        return Collections.unmodifiableList(result);
    }

    private static List<SomaUniqueRuntimeMetadata> immutableUniques(
            SomaTableMetadata descriptor,
            SomaUniqueRuntimeMetadata[] values) {
        if (values.length != descriptor.uniques().size()) {
            throw new IllegalArgumentException(
                    "Runtime Metadata unique shape mismatch");
        }
        ArrayList<SomaUniqueRuntimeMetadata> result =
                new ArrayList<SomaUniqueRuntimeMetadata>(values.length);
        for (int index = 0; index < values.length; index++) {
            SomaUniqueRuntimeMetadata value = values[index];
            if (value == null
                    || value.descriptor()
                    != descriptor.uniques().get(index)) {
                throw new IllegalArgumentException(
                        "Runtime Metadata unique descriptor mismatch");
            }
            result.add(value);
        }
        return Collections.unmodifiableList(result);
    }

    private static List<SomaSegmentMetadata> segments(
            TablePlan plan, int rows, int capacity) {
        if (capacity == 0) return Collections.emptyList();
        ArrayList<SomaSegmentMetadata> result =
                new ArrayList<SomaSegmentMetadata>();
        if (plan.storageLayout() == SomaStorageLayout.FLAT) {
            result.add(new SegmentSnapshot(
                    0, SomaSegmentKind.FLAT, 0, capacity, rows));
            return Collections.unmodifiableList(result);
        }
        int headEnd = Math.min(capacity, plan.flatHeadRows());
        result.add(new SegmentSnapshot(
                0, SomaSegmentKind.FLAT_HEAD, 0, headEnd, rows));
        int ordinal = 1;
        for (int start = headEnd; start < capacity; ordinal++) {
            long proposedEnd =
                    (long) start + (long) plan.segmentRows();
            int end = (int) Math.min((long) capacity, proposedEnd);
            result.add(new SegmentSnapshot(
                    ordinal, SomaSegmentKind.SEGMENTED_TAIL,
                    start, end, rows));
            start = end;
        }
        return Collections.unmodifiableList(result);
    }

    private static final class AccessSnapshot
            implements SomaExactAccessRuntimeMetadata {
        private final String name;
        private final long entries;
        private final long groups;
        private final long probes;
        private final long collisions;
        private final long rehashes;
        private final long retainedBytes;
        private final long highWaterBytes;

        private AccessSnapshot(
                String name,
                long entries,
                long groups,
                long probes,
                long collisions,
                long rehashes,
                long retainedBytes,
                long highWaterBytes) {
            this.name = name;
            this.entries = entries;
            this.groups = groups;
            this.probes = probes;
            this.collisions = collisions;
            this.rehashes = rehashes;
            this.retainedBytes = retainedBytes;
            this.highWaterBytes = highWaterBytes;
        }

        @Override public String name() { return name; }
        @Override public String implementationIdentity() {
            return EXACT_INDEX_IMPLEMENTATION;
        }
        @Override public long entryCount() { return entries; }
        @Override public long groupCount() { return groups; }
        @Override public long probeCount() { return probes; }
        @Override public long collisionCount() { return collisions; }
        @Override public long rehashCount() { return rehashes; }
        @Override public long retainedStructuralBytes() {
            return retainedBytes;
        }
        @Override public long structuralHighWaterBytes() {
            return highWaterBytes;
        }
    }

    private static final class IndexSnapshot
            implements SomaIndexRuntimeMetadata {
        private final SomaIndexMetadata descriptor;
        private final AccessSnapshot access;

        private IndexSnapshot(
                SomaIndexMetadata descriptor, AccessSnapshot access) {
            if (descriptor == null) throw new NullPointerException("descriptor");
            this.descriptor = descriptor;
            this.access = access;
        }

        @Override public SomaIndexMetadata descriptor() { return descriptor; }
        @Override public String name() { return access.name(); }
        @Override public String implementationIdentity() {
            return access.implementationIdentity();
        }
        @Override public long entryCount() { return access.entryCount(); }
        @Override public long groupCount() { return access.groupCount(); }
        @Override public long probeCount() { return access.probeCount(); }
        @Override public long collisionCount() {
            return access.collisionCount();
        }
        @Override public long rehashCount() { return access.rehashCount(); }
        @Override public long retainedStructuralBytes() {
            return access.retainedStructuralBytes();
        }
        @Override public long structuralHighWaterBytes() {
            return access.structuralHighWaterBytes();
        }
    }

    private static final class UniqueSnapshot
            implements SomaUniqueRuntimeMetadata {
        private final SomaUniqueMetadata descriptor;
        private final AccessSnapshot access;

        private UniqueSnapshot(
                SomaUniqueMetadata descriptor, AccessSnapshot access) {
            if (descriptor == null) throw new NullPointerException("descriptor");
            this.descriptor = descriptor;
            this.access = access;
        }

        @Override public SomaUniqueMetadata descriptor() {
            return descriptor;
        }
        @Override public String name() { return access.name(); }
        @Override public String implementationIdentity() {
            return access.implementationIdentity();
        }
        @Override public long entryCount() { return access.entryCount(); }
        @Override public long groupCount() { return access.groupCount(); }
        @Override public long probeCount() { return access.probeCount(); }
        @Override public long collisionCount() {
            return access.collisionCount();
        }
        @Override public long rehashCount() { return access.rehashCount(); }
        @Override public long retainedStructuralBytes() {
            return access.retainedStructuralBytes();
        }
        @Override public long structuralHighWaterBytes() {
            return access.structuralHighWaterBytes();
        }
    }

    private static final class SegmentSnapshot
            implements SomaSegmentMetadata {
        private final int ordinal;
        private final SomaSegmentKind kind;
        private final int start;
        private final int end;
        private final int liveRows;

        private SegmentSnapshot(
                int ordinal,
                SomaSegmentKind kind,
                int start,
                int end,
                int tableRows) {
            if (ordinal < 0 || kind == null || start < 0 || end <= start
                    || tableRows < 0) {
                throw new IllegalArgumentException(
                        "invalid Segment Runtime Metadata");
            }
            this.ordinal = ordinal;
            this.kind = kind;
            this.start = start;
            this.end = end;
            this.liveRows = Math.max(
                    0, Math.min(end, tableRows) - start);
        }

        @Override public int ordinal() { return ordinal; }
        @Override public SomaSegmentKind kind() { return kind; }
        @Override public int startRowInclusive() { return start; }
        @Override public int endRowExclusive() { return end; }
        @Override public int capacityRows() { return end - start; }
        @Override public int liveRows() { return liveRows; }
    }

    private static final class TableSnapshot
            implements SomaTableRuntimeMetadata {
        private final SomaTableMetadata descriptor;
        private final String runtimePlanHash;
        private final SomaStorageLayout storageLayout;
        private final SomaPrimaryLocator primaryLocator;
        private final SomaPrimaryLocatorLayout primaryLocatorLayout;
        private final TableStats observation;
        private final List<SomaSegmentMetadata> segments;
        private final List<SomaIndexRuntimeMetadata> indexes;
        private final List<SomaUniqueRuntimeMetadata> uniques;

        private TableSnapshot(
                SomaTableMetadata descriptor,
                TablePlan plan,
                TableStats observation,
                List<SomaSegmentMetadata> segments,
                List<SomaIndexRuntimeMetadata> indexes,
                List<SomaUniqueRuntimeMetadata> uniques) {
            this.descriptor = descriptor;
            runtimePlanHash = observation.runtimePlanHash();
            storageLayout = plan.storageLayout();
            primaryLocator = plan.primaryLocator();
            primaryLocatorLayout = plan.primaryLocatorLayout();
            this.observation = observation;
            this.segments = segments;
            this.indexes = indexes;
            this.uniques = uniques;
        }

        @Override public SomaTableMetadata descriptor() {
            return descriptor;
        }
        @Override public String logicalName() {
            return descriptor.logicalName();
        }
        @Override public String runtimePlanHash() {
            return runtimePlanHash;
        }
        @Override public SomaStorageLayout storageLayout() {
            return storageLayout;
        }
        @Override public SomaPrimaryLocator primaryLocator() {
            return primaryLocator;
        }
        @Override public SomaPrimaryLocatorLayout primaryLocatorLayout() {
            return primaryLocatorLayout;
        }
        @Override public String primaryLocatorImplementationIdentity() {
            return observation.keySpaceImplementation();
        }
        @Override public int rows() { return observation.rows(); }
        @Override public int capacity() { return observation.capacity(); }
        @Override public long structuralEpoch() {
            return observation.structuralEpoch();
        }
        @Override public boolean released() {
            return observation.released();
        }
        @Override public List<SomaSegmentMetadata> segments() {
            return segments;
        }
        @Override public List<SomaIndexRuntimeMetadata> indexes() {
            return indexes;
        }
        @Override public SomaIndexRuntimeMetadata requireIndex(String name) {
            if (name == null) throw new NullPointerException("name");
            for (SomaIndexRuntimeMetadata value : indexes) {
                if (value.name().equals(name)) return value;
            }
            throw new IllegalArgumentException(
                    "Unknown runtime index '" + name
                            + "' in SOMA table '" + logicalName() + "'");
        }
        @Override public List<SomaUniqueRuntimeMetadata> uniques() {
            return uniques;
        }
        @Override public SomaUniqueRuntimeMetadata requireUnique(
                String name) {
            if (name == null) throw new NullPointerException("name");
            for (SomaUniqueRuntimeMetadata value : uniques) {
                if (value.name().equals(name)) return value;
            }
            throw new IllegalArgumentException(
                    "Unknown runtime unique '" + name
                            + "' in SOMA table '" + logicalName() + "'");
        }
        @Override public TableStats observation() {
            return observation;
        }
    }
}
