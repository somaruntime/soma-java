package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;

/**
 * Hash sidecar over authoritative payload; hash collisions always compare
 * payload leaves. Non-unique posting links are canonical locator order:
 * append publishes the new tail locator, while update/remove rebuild scans
 * the bound directory from locator zero upward.
 */
final class IdentityHashIndex {

    private static final int SHARD_BITS = 6;
    private static final int SHARD_COUNT = 1 << SHARD_BITS;
    private static final int INITIAL_CAPACITY = 16;
    private static final long CONTAINER_BYTES = 64L + SHARD_COUNT * 8L;

    private final GeneratedTableLayout layout;
    private final int fieldIndex;
    private final boolean unique;
    private final int linkPageRows;
    private Shard[] shards;
    private PagedLongLinks links;
    private long managedBytes;

    private IdentityHashIndex(
            GeneratedTableLayout layout,
            int fieldIndex,
            boolean unique,
            int linkPageRows) {
        this.layout = layout;
        this.fieldIndex = fieldIndex;
        this.unique = unique;
        this.linkPageRows = linkPageRows;
        this.links = unique ? null : PagedLongLinks.empty(linkPageRows);
    }

    static IdentityHashIndex empty(
            GeneratedTableLayout layout,
            int fieldIndex,
            boolean unique,
            int linkPageRows) {
        return new IdentityHashIndex(layout, fieldIndex, unique, linkPageRows);
    }

    static IdentityHashIndex rebuild(
            GeneratedTableLayout layout,
            int fieldIndex,
            boolean unique,
            int linkPageRows,
            TableChunkDirectory directory,
            long size,
            SomaOperation operation,
            Object provenance) {
        IdentityHashIndex result = empty(layout, fieldIndex, unique, linkPageRows);
        for (long locator = 0L; locator < size; locator++) {
            result.addStored(directory, locator, operation, provenance);
        }
        return result;
    }

    long findUnique(TableChunkDirectory directory, TypedValues probe) {
        if (!unique) throw new AssertionError("non-unique Index used as Key");
        Bucket bucket = findBucket(directory, probe);
        return bucket == null ? -1L : bucket.head;
    }

    long count(TableChunkDirectory directory, TypedValues probe) {
        Bucket bucket = findBucket(directory, probe);
        return bucket == null ? 0L : bucket.count;
    }

    long first(TableChunkDirectory directory, TypedValues probe) {
        Bucket bucket = findBucket(directory, probe);
        return bucket == null ? -1L : bucket.head;
    }

    long firstJoin(
            TableChunkDirectory directory,
            GeneratedTableLayout probeLayout,
            TableChunkDirectory probeDirectory,
            long probeLocator,
            int probeFieldIndex) {
        if (shards == null) return -1L;
        long hash = probeLayout.hashField(
                probeDirectory, probeLocator, probeFieldIndex);
        Shard shard = shards[shardOrdinal(hash)];
        if (shard == null) return -1L;
        int slot = shard.findJoin(
                directory,
                hash,
                layout,
                fieldIndex,
                probeLayout,
                probeDirectory,
                probeLocator,
                probeFieldIndex);
        return slot < 0 ? -1L : shard.heads[slot];
    }

    long next(long locator) {
        if (unique) return -1L;
        return links.next(locator);
    }

    long managedBytes() {
        return managedBytes;
    }

    PreparedAdd prepareAdd(
            TableChunkDirectory directory,
            TypedValues probe,
            long locator,
            SomaOperation operation,
            Object provenance,
            String logicalName) {
        long hash = layout.hashField(probe, fieldIndex);
        int shardOrdinal = shardOrdinal(hash);
        Shard currentShard = shards == null ? null : shards[shardOrdinal];
        int existing = currentShard == null
                ? -1 : currentShard.findProbe(directory, probe, hash, layout, fieldIndex);
        if (existing >= 0 && unique) {
            throw SomaFailures.failure(
                    SomaFailureCode.DUPLICATE_KEY,
                    operation,
                    logicalName + " duplicate Key",
                    provenance);
        }

        PagedLongLinks linksAfter = unique ? null : links.ensureLocator(locator);
        long beforeShardBytes = currentShard == null ? 0L : currentShard.managedBytes;
        long afterShardBytes = beforeShardBytes;
        Shard replacement = null;
        Shard[] container = null;
        int slot;
        if (existing >= 0) {
            slot = existing;
        } else if (currentShard == null) {
            replacement = new Shard(INITIAL_CAPACITY, operation, provenance);
            slot = replacement.emptySlot(hash);
            replacement.installNew(slot, hash, locator);
            afterShardBytes = replacement.managedBytes;
            if (shards == null) {
                container = new Shard[SHARD_COUNT];
                container[shardOrdinal] = replacement;
            }
        } else if (currentShard.hasInsertCapacity()) {
            slot = currentShard.emptySlot(hash);
        } else {
            replacement = currentShard.rehash(
                    currentShard.expandedCapacity(operation, provenance),
                    operation,
                    provenance);
            slot = replacement.emptySlot(hash);
            replacement.installNew(slot, hash, locator);
            afterShardBytes = replacement.managedBytes;
        }

        long linksBytes = unique ? 0L : linksAfter.managedBytes(operation, provenance);
        long priorLinksBytes = unique ? 0L : links.managedBytes(operation, provenance);
        long shardBytes = CheckedLong.subtract(
                managedBytes, priorLinksBytes, operation, provenance);
        shardBytes = CheckedLong.subtract(
                shardBytes, beforeShardBytes, operation, provenance);
        shardBytes = CheckedLong.add(
                shardBytes, afterShardBytes, operation, provenance);
        if (shards == null) {
            shardBytes = CheckedLong.add(
                    shardBytes, CONTAINER_BYTES, operation, provenance);
        }
        long afterBytes = CheckedLong.add(
                shardBytes,
                linksBytes,
                operation,
                provenance);
        return new PreparedAdd(
                this,
                shardOrdinal,
                currentShard,
                replacement,
                container,
                slot,
                existing >= 0,
                hash,
                locator,
                linksAfter,
                afterBytes);
    }

    private Bucket findBucket(TableChunkDirectory directory, TypedValues probe) {
        if (shards == null) return null;
        long hash = layout.hashField(probe, fieldIndex);
        Shard shard = shards[shardOrdinal(hash)];
        if (shard == null) return null;
        int slot = shard.findProbe(directory, probe, hash, layout, fieldIndex);
        return slot < 0 ? null : new Bucket(
                shard.heads[slot], shard.tails[slot], shard.counts[slot]);
    }

    private void addStored(
            TableChunkDirectory directory,
            long locator,
            SomaOperation operation,
            Object provenance) {
        long hash = layout.hashField(directory, locator, fieldIndex);
        if (shards == null) {
            shards = new Shard[SHARD_COUNT];
            managedBytes = CONTAINER_BYTES;
        }
        int shardOrdinal = shardOrdinal(hash);
        Shard shard = shards[shardOrdinal];
        if (shard == null) {
            shard = new Shard(INITIAL_CAPACITY, operation, provenance);
            shards[shardOrdinal] = shard;
            managedBytes = CheckedLong.add(
                    managedBytes, shard.managedBytes, operation, provenance);
        }
        int slot = shard.findStored(directory, locator, hash, layout, fieldIndex);
        if (slot >= 0) {
            if (unique) throw new AssertionError("duplicate Key while rebuilding sidecar");
            links = links.ensureLocator(locator);
            links.link(shard.tails[slot], locator);
            shard.tails[slot] = locator;
            shard.counts[slot]++;
        } else {
            if (!shard.hasInsertCapacity()) {
                Shard replacement = shard.rehash(
                        shard.expandedCapacity(operation, provenance),
                        operation,
                        provenance);
                managedBytes = CheckedLong.subtract(
                        managedBytes, shard.managedBytes, operation, provenance);
                managedBytes = CheckedLong.add(
                        managedBytes, replacement.managedBytes, operation, provenance);
                shards[shardOrdinal] = replacement;
                shard = replacement;
            }
            shard.installNew(shard.emptySlot(hash), hash, locator);
            if (!unique) links = links.ensureLocator(locator);
        }
        if (!unique) {
            long shardOnly = managedBytesWithoutLinks(operation, provenance);
            managedBytes = CheckedLong.add(
                    shardOnly,
                    links.managedBytes(operation, provenance),
                    operation,
                    provenance);
        }
    }

    private long managedBytesWithoutLinks(
            SomaOperation operation,
            Object provenance) {
        if (unique || links == null) return managedBytes;
        long result = shards == null ? 0L : CONTAINER_BYTES;
        if (shards != null) {
            for (Shard shard : shards) {
                if (shard != null) {
                    result = CheckedLong.add(
                            result, shard.managedBytes, operation, provenance);
                }
            }
        }
        return result;
    }

    private static int shardOrdinal(long hash) {
        return (int) (hash >>> (Long.SIZE - SHARD_BITS));
    }

    static final class PreparedAdd {

        private final IdentityHashIndex owner;
        private final int shardOrdinal;
        private final Shard currentShard;
        private final Shard replacement;
        private final Shard[] container;
        private final int slot;
        private final boolean existing;
        private final long hash;
        private final long locator;
        private final PagedLongLinks linksAfter;
        private final long managedBytesAfter;

        private PreparedAdd(
                IdentityHashIndex owner,
                int shardOrdinal,
                Shard currentShard,
                Shard replacement,
                Shard[] container,
                int slot,
                boolean existing,
                long hash,
                long locator,
                PagedLongLinks linksAfter,
                long managedBytesAfter) {
            this.owner = owner;
            this.shardOrdinal = shardOrdinal;
            this.currentShard = currentShard;
            this.replacement = replacement;
            this.container = container;
            this.slot = slot;
            this.existing = existing;
            this.hash = hash;
            this.locator = locator;
            this.linksAfter = linksAfter;
            this.managedBytesAfter = managedBytesAfter;
        }

        long managedBytesAfter() {
            return managedBytesAfter;
        }

        void commit() {
            Shard target;
            if (container != null) {
                owner.shards = container;
                target = replacement;
            } else if (replacement != null) {
                if (owner.shards == null) throw new AssertionError("missing Index container");
                owner.shards[shardOrdinal] = replacement;
                target = replacement;
            } else {
                target = currentShard;
            }
            if (existing) {
                if (owner.unique) throw new AssertionError("duplicate Key prepared");
                owner.links = linksAfter;
                owner.links.link(target.tails[slot], locator);
                target.tails[slot] = locator;
                target.counts[slot]++;
            } else if (replacement == null) {
                target.installNew(slot, hash, locator);
                if (!owner.unique) owner.links = linksAfter;
            } else if (!owner.unique) {
                owner.links = linksAfter;
            }
            owner.managedBytes = managedBytesAfter;
        }
    }

    private static final class Bucket {
        private final long head;
        @SuppressWarnings("unused") private final long tail;
        private final long count;

        private Bucket(long head, long tail, long count) {
            this.head = head;
            this.tail = tail;
            this.count = count;
        }
    }

    private static final class Shard {

        private final long[] hashes;
        private final long[] representatives;
        private final long[] heads;
        private final long[] tails;
        private final long[] counts;
        private final byte[] states;
        private final int mask;
        private final long managedBytes;
        private int size;

        private Shard(int capacity, SomaOperation operation, Object provenance) {
            hashes = new long[capacity];
            representatives = new long[capacity];
            heads = new long[capacity];
            tails = new long[capacity];
            counts = new long[capacity];
            states = new byte[capacity];
            mask = capacity - 1;
            managedBytes = estimatedBytes(capacity, operation, provenance);
        }

        private Shard(
                long[] hashes,
                long[] representatives,
                long[] heads,
                long[] tails,
                long[] counts,
                byte[] states,
                int size,
                long managedBytes) {
            this.hashes = hashes;
            this.representatives = representatives;
            this.heads = heads;
            this.tails = tails;
            this.counts = counts;
            this.states = states;
            this.mask = hashes.length - 1;
            this.size = size;
            this.managedBytes = managedBytes;
        }

        int findProbe(
                TableChunkDirectory directory,
                TypedValues probe,
                long hash,
                GeneratedTableLayout layout,
                int fieldIndex) {
            int slot = ((int) hash) & mask;
            while (states[slot] != 0) {
                if (hashes[slot] == hash
                        && layout.fieldEquals(
                                directory, representatives[slot], probe, fieldIndex)) {
                    return slot;
                }
                slot = (slot + 1) & mask;
            }
            return -1;
        }

        int findStored(
                TableChunkDirectory directory,
                long locator,
                long hash,
                GeneratedTableLayout layout,
                int fieldIndex) {
            int slot = ((int) hash) & mask;
            while (states[slot] != 0) {
                if (hashes[slot] == hash
                        && layout.fieldEquals(
                                directory, representatives[slot], locator, fieldIndex)) {
                    return slot;
                }
                slot = (slot + 1) & mask;
            }
            return -1;
        }

        int findJoin(
                TableChunkDirectory directory,
                long hash,
                GeneratedTableLayout layout,
                int fieldIndex,
                GeneratedTableLayout probeLayout,
                TableChunkDirectory probeDirectory,
                long probeLocator,
                int probeFieldIndex) {
            int slot = ((int) hash) & mask;
            while (states[slot] != 0) {
                if (hashes[slot] == hash
                        && layout.joinFieldEquals(
                                directory,
                                representatives[slot],
                                fieldIndex,
                                probeLayout,
                                probeDirectory,
                                probeLocator,
                                probeFieldIndex)) return slot;
                slot = (slot + 1) & mask;
            }
            return -1;
        }

        int emptySlot(long hash) {
            int slot = ((int) hash) & mask;
            while (states[slot] != 0) slot = (slot + 1) & mask;
            return slot;
        }

        void installNew(int slot, long hash, long locator) {
            hashes[slot] = hash;
            representatives[slot] = locator;
            heads[slot] = locator;
            tails[slot] = locator;
            counts[slot] = 1L;
            states[slot] = 1;
            size++;
        }

        boolean hasInsertCapacity() {
            return (long) size + 1L <= ((long) hashes.length * 5L) / 8L;
        }

        int expandedCapacity(SomaOperation operation, Object provenance) {
            if (hashes.length > (1 << 29)) {
                throw SomaFailures.failure(
                        SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                        operation,
                        "Index shard reached Java array representation limit",
                        provenance);
            }
            return hashes.length << 1;
        }

        Shard rehash(int capacity, SomaOperation operation, Object provenance) {
            Shard result = new Shard(capacity, operation, provenance);
            for (int old = 0; old < hashes.length; old++) {
                if (states[old] != 0) {
                    int slot = result.emptySlot(hashes[old]);
                    result.hashes[slot] = hashes[old];
                    result.representatives[slot] = representatives[old];
                    result.heads[slot] = heads[old];
                    result.tails[slot] = tails[old];
                    result.counts[slot] = counts[old];
                    result.states[slot] = 1;
                    result.size++;
                }
            }
            return result;
        }

        static long estimatedBytes(
                int capacity,
                SomaOperation operation,
                Object provenance) {
            return CheckedLong.add(
                    160L,
                    CheckedLong.multiply(
                            capacity,
                            5L * Long.BYTES + 1L,
                            operation,
                            provenance),
                    operation,
                    provenance);
        }
    }
}
