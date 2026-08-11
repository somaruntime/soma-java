package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;

/**
 * Hash sidecar over authoritative payload; hash collisions always compare
 * payload leaves. Non-unique posting links are canonical locator order:
 * append publishes the new tail locator. Point update/remove prepare bounded
 * bucket-local posting edits and commit them only after all recoverable work;
 * large Selection mutation may still rebuild a candidate sidecar.
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
    private PagedIntLinks links;
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
        this.links = unique ? null : PagedIntLinks.empty(linkPageRows);
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
            int size,
            SomaOperation operation,
            Object provenance) {
        IdentityHashIndex result = empty(layout, fieldIndex, unique, linkPageRows);
        for (int locator = 0; locator < size; locator++) {
            result.addStored(directory, locator, operation, provenance);
        }
        return result;
    }

    int findUnique(TableChunkDirectory directory, TypedValues probe) {
        if (!unique) throw new AssertionError("non-unique Index used as Key");
        Bucket bucket = findBucket(directory, probe);
        return bucket == null ? -1 : bucket.head;
    }

    long count(TableChunkDirectory directory, TypedValues probe) {
        Bucket bucket = findBucket(directory, probe);
        return bucket == null ? 0L : bucket.count;
    }

    int first(TableChunkDirectory directory, TypedValues probe) {
        Bucket bucket = findBucket(directory, probe);
        return bucket == null ? -1 : bucket.head;
    }

    int firstJoin(
            TableChunkDirectory directory,
            GeneratedTableLayout probeLayout,
            TableChunkDirectory probeDirectory,
            int probeLocator,
            int probeFieldIndex) {
        if (shards == null) return -1;
        long hash = probeLayout.hashField(
                probeDirectory, probeLocator, probeFieldIndex);
        Shard shard = shards[shardOrdinal(hash)];
        if (shard == null) return -1;
        int slot = shard.findJoin(
                directory,
                hash,
                layout,
                fieldIndex,
                probeLayout,
                probeDirectory,
                probeLocator,
                probeFieldIndex);
        return slot < 0 ? -1 : shard.heads[slot];
    }

    int next(int locator) {
        if (unique) return -1;
        return links.next(locator);
    }

    long managedBytes() {
        return managedBytes;
    }

    /** 当前 immutable sidecar 中 logical equality bucket 的精确数量。 */
    long distinctCount() {
        if (shards == null) return 0L;
        long result = 0L;
        for (Shard shard : shards) {
            if (shard != null) result += shard.size;
        }
        return result;
    }

    void prepareAdd(
            PreparedAdd target,
            TableChunkDirectory directory,
            TypedValues probe,
            int locator,
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

        PagedIntLinks linksAfter = unique ? null : links.ensureLocator(locator);
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
        } else if (currentShard.canInsertWithoutRehash()) {
            slot = currentShard.emptySlot(hash);
        } else {
            replacement = currentShard.rehash(
                    currentShard.capacityForInsert(
                            currentShard.size + 1, operation, provenance),
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
                shardBytes, linksBytes, operation, provenance);
        target.prepare(
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

    /** Prepares one indexed-value move without changing the published sidecar. */
    void prepareUpdate(
            PreparedUpdate target,
            TableChunkDirectory directory,
            TypedValues staged,
            int locator,
            SomaOperation operation,
            Object provenance) {
        if (unique) throw new AssertionError("Key cannot be updated");
        if (target.owner != null) throw new AssertionError("Index update scratch is busy");

        long oldHash = layout.hashField(directory, locator, fieldIndex);
        int sourceOrdinal = shardOrdinal(oldHash);
        Shard sourceShard = shards == null ? null : shards[sourceOrdinal];
        int sourceSlot = sourceShard == null
                ? -1 : sourceShard.findStored(
                        directory, locator, oldHash, layout, fieldIndex);
        if (sourceSlot < 0) throw new AssertionError("indexed source posting is missing");

        long newHash = layout.hashField(staged, fieldIndex);
        int destinationOrdinal = shardOrdinal(newHash);
        Shard destinationShard = shards[destinationOrdinal];
        int destinationSlot = destinationShard == null
                ? -1 : destinationShard.findProbe(
                        directory, staged, newHash, layout, fieldIndex);
        Shard destinationReplacement = null;
        boolean destinationExisting = destinationSlot >= 0;
        long managedBytesAfter = managedBytes;
        if (!destinationExisting) {
            if (destinationShard == null) {
                destinationReplacement = new Shard(
                        INITIAL_CAPACITY, operation, provenance);
                destinationShard = destinationReplacement;
                destinationSlot = destinationShard.emptySlot(newHash);
                managedBytesAfter = CheckedLong.add(
                        managedBytesAfter,
                        destinationShard.managedBytes,
                        operation,
                        provenance);
            } else {
                int liveAfter = destinationShard.size + 1;
                if (sourceShard == destinationShard
                        && sourceShard.counts[sourceSlot] == 1L) liveAfter--;
                if (!destinationShard.canInsertWithoutRehash()) {
                    Shard currentDestination = destinationShard;
                    destinationReplacement = currentDestination.rehash(
                            currentDestination.capacityForInsert(
                                    liveAfter, operation, provenance),
                            operation,
                            provenance);
                    destinationShard = destinationReplacement;
                    managedBytesAfter = CheckedLong.add(
                            CheckedLong.subtract(
                                    managedBytesAfter,
                                    currentDestination.managedBytes,
                                    operation,
                                    provenance),
                            destinationReplacement.managedBytes,
                            operation,
                            provenance);
                    if (sourceShard == currentDestination) {
                        sourceShard = destinationReplacement;
                        sourceSlot = sourceShard.findStored(
                                directory, locator, oldHash, layout, fieldIndex);
                        if (sourceSlot < 0) {
                            throw new AssertionError(
                                    "rehash lost indexed source posting");
                        }
                    }
                }
                destinationSlot = destinationShard.emptySlot(newHash);
            }
        }

        int sourcePrevious = predecessor(sourceShard, sourceSlot, locator);
        int sourceNext = links.next(locator);
        int destinationPrevious = -1;
        int destinationNext = -1;
        if (destinationExisting) {
            int cursor = destinationShard.heads[destinationSlot];
            while (cursor >= 0 && cursor < locator) {
                destinationPrevious = cursor;
                cursor = links.next(cursor);
            }
            destinationNext = cursor;
        }
        target.prepare(
                this,
                sourceShard,
                sourceSlot,
                sourcePrevious,
                sourceNext,
                destinationOrdinal,
                destinationShard,
                destinationReplacement,
                destinationSlot,
                destinationExisting,
                destinationPrevious,
                destinationNext,
                newHash,
                locator,
                managedBytesAfter);
    }

    /** Prepares packed point-remove sidecar edits without changing live state. */
    void prepareRemove(
            PreparedRemove target,
            TableChunkDirectory directory,
            int removedLocator,
            int tailLocator) {
        if (target.owner != null) throw new AssertionError("Index remove scratch is busy");
        long removedHash = layout.hashField(directory, removedLocator, fieldIndex);
        Shard removedShard = shards[shardOrdinal(removedHash)];
        int removedSlot = removedShard.findStored(
                directory, removedLocator, removedHash, layout, fieldIndex);
        if (removedSlot < 0) throw new AssertionError("removed Index posting is missing");

        if (unique) {
            if (tailLocator == removedLocator) {
                target.prepareUnique(
                        this, removedShard, removedSlot, null, -1,
                        removedLocator, tailLocator);
                return;
            }
            long tailHash = layout.hashField(directory, tailLocator, fieldIndex);
            Shard tailShard = shards[shardOrdinal(tailHash)];
            int tailSlot = tailShard.findStored(
                    directory, tailLocator, tailHash, layout, fieldIndex);
            if (tailSlot < 0 || tailShard.counts[tailSlot] != 1L) {
                throw new AssertionError("moved Key posting is invalid");
            }
            target.prepareUnique(
                    this, removedShard, removedSlot, tailShard, tailSlot,
                    removedLocator, tailLocator);
            return;
        }

        if (tailLocator == removedLocator
                || layout.fieldEquals(
                        directory,
                        removedLocator,
                        tailLocator,
                        fieldIndex)) {
            int posting = tailLocator;
            int slot = tailLocator == removedLocator
                    ? removedSlot
                    : removedShard.findStored(
                            directory, tailLocator, removedHash, layout, fieldIndex);
            if (slot < 0) throw new AssertionError("same-value tail posting is missing");
            target.preparePostingOnly(
                    this,
                    removedShard,
                    slot,
                    predecessor(removedShard, slot, posting),
                    links.next(posting),
                    posting,
                    removedLocator,
                    tailLocator);
            return;
        }

        int removedPrevious = predecessor(
                removedShard, removedSlot, removedLocator);
        int removedNext = links.next(removedLocator);
        long tailHash = layout.hashField(directory, tailLocator, fieldIndex);
        Shard tailShard = shards[shardOrdinal(tailHash)];
        int tailSlot = tailShard.findStored(
                directory, tailLocator, tailHash, layout, fieldIndex);
        if (tailSlot < 0) throw new AssertionError("moved Index posting is missing");
        int tailPrevious = predecessor(tailShard, tailSlot, tailLocator);
        if (links.next(tailLocator) >= 0) {
            throw new AssertionError("tail locator is not canonical posting tail");
        }
        int insertionPrevious = -1;
        int insertionNext = tailShard.heads[tailSlot];
        while (insertionNext >= 0
                && insertionNext != tailLocator
                && insertionNext < removedLocator) {
            insertionPrevious = insertionNext;
            insertionNext = links.next(insertionNext);
        }
        if (insertionNext == tailLocator) insertionNext = -1;
        target.prepareDifferentValues(
                this,
                removedShard,
                removedSlot,
                removedPrevious,
                removedNext,
                tailShard,
                tailSlot,
                tailPrevious,
                insertionPrevious,
                insertionNext,
                removedLocator,
                tailLocator);
    }

    private int predecessor(Shard shard, int slot, int locator) {
        int previous = -1;
        for (int cursor = shard.heads[slot]; cursor >= 0; cursor = links.next(cursor)) {
            if (cursor == locator) return previous;
            previous = cursor;
        }
        throw new AssertionError("Index posting locator is missing");
    }

    private void unlinkPosting(
            Shard shard,
            int slot,
            int locator,
            int previous,
            int next) {
        if (shard.counts[slot] == 1L) {
            shard.removeBucket(slot);
        } else {
            if (previous < 0) {
                shard.heads[slot] = next;
                shard.representatives[slot] = next;
            } else {
                links.link(previous, next);
            }
            if (shard.tails[slot] == locator) shard.tails[slot] = previous;
            shard.counts[slot]--;
        }
        links.link(locator, -1);
    }

    private void insertPosting(
            Shard shard,
            int slot,
            int locator,
            int previous,
            int next) {
        links.link(locator, next);
        if (previous < 0) {
            shard.heads[slot] = locator;
            shard.representatives[slot] = locator;
        } else {
            links.link(previous, locator);
        }
        if (next < 0) shard.tails[slot] = locator;
        shard.counts[slot]++;
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
            int locator,
            SomaOperation operation,
            Object provenance) {
        long hash = layout.hashField(directory, locator, fieldIndex);
        long priorLinksBytes = unique
                ? 0L : links.managedBytes(operation, provenance);
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
            if (!shard.canInsertWithoutRehash()) {
                Shard replacement = shard.rehash(
                        shard.capacityForInsert(
                                shard.size + 1, operation, provenance),
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
            long shardOnly = CheckedLong.subtract(
                    managedBytes, priorLinksBytes, operation, provenance);
            managedBytes = CheckedLong.add(
                    shardOnly,
                    links.managedBytes(operation, provenance),
                    operation,
                    provenance);
        }
    }

    private static int shardOrdinal(long hash) {
        return (int) (hash >>> (Long.SIZE - SHARD_BITS));
    }

    static final class PreparedAdd {

        private IdentityHashIndex owner;
        private int shardOrdinal;
        private Shard currentShard;
        private Shard replacement;
        private Shard[] container;
        private int slot;
        private boolean existing;
        private long hash;
        private int locator;
        private PagedIntLinks linksAfter;
        private long managedBytesAfter;

        PreparedAdd() {
        }

        private void prepare(
                IdentityHashIndex owner,
                int shardOrdinal,
                Shard currentShard,
                Shard replacement,
                Shard[] container,
                int slot,
                boolean existing,
                long hash,
                int locator,
                PagedIntLinks linksAfter,
                long managedBytesAfter) {
            if (this.owner != null) {
                throw new AssertionError("Index add scratch is already prepared");
            }
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
            if (owner == null) throw new AssertionError("Index add scratch is not prepared");
            return managedBytesAfter;
        }

        void commit() {
            if (owner == null) throw new AssertionError("Index add scratch is not prepared");
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

        void clear() {
            owner = null;
            currentShard = null;
            replacement = null;
            container = null;
            linksAfter = null;
        }
    }

    static final class PreparedUpdate {
        private IdentityHashIndex owner;
        private Shard sourceShard;
        private int sourceSlot;
        private int sourcePrevious;
        private int sourceNext;
        private int destinationOrdinal;
        private Shard destinationShard;
        private Shard destinationReplacement;
        private int destinationSlot;
        private boolean destinationExisting;
        private int destinationPrevious;
        private int destinationNext;
        private long newHash;
        private int locator;
        private long managedBytesAfter;

        void prepare(
                IdentityHashIndex owner,
                Shard sourceShard,
                int sourceSlot,
                int sourcePrevious,
                int sourceNext,
                int destinationOrdinal,
                Shard destinationShard,
                Shard destinationReplacement,
                int destinationSlot,
                boolean destinationExisting,
                int destinationPrevious,
                int destinationNext,
                long newHash,
                int locator,
                long managedBytesAfter) {
            this.owner = owner;
            this.sourceShard = sourceShard;
            this.sourceSlot = sourceSlot;
            this.sourcePrevious = sourcePrevious;
            this.sourceNext = sourceNext;
            this.destinationOrdinal = destinationOrdinal;
            this.destinationShard = destinationShard;
            this.destinationReplacement = destinationReplacement;
            this.destinationSlot = destinationSlot;
            this.destinationExisting = destinationExisting;
            this.destinationPrevious = destinationPrevious;
            this.destinationNext = destinationNext;
            this.newHash = newHash;
            this.locator = locator;
            this.managedBytesAfter = managedBytesAfter;
        }

        long managedBytesAfter() {
            if (owner == null) throw new AssertionError("Index update scratch is not prepared");
            return managedBytesAfter;
        }

        void commit() {
            if (owner == null) throw new AssertionError("Index update scratch is not prepared");
            owner.unlinkPosting(
                    sourceShard,
                    sourceSlot,
                    locator,
                    sourcePrevious,
                    sourceNext);
            Shard target = destinationShard;
            if (destinationReplacement != null) {
                owner.shards[destinationOrdinal] = destinationReplacement;
                target = destinationReplacement;
            }
            if (destinationExisting) {
                owner.insertPosting(
                        target,
                        destinationSlot,
                        locator,
                        destinationPrevious,
                        destinationNext);
            } else {
                target.installNew(destinationSlot, newHash, locator);
                owner.links.link(locator, -1);
            }
            owner.managedBytes = managedBytesAfter;
        }

        void clear() {
            owner = null;
            sourceShard = null;
            destinationShard = null;
            destinationReplacement = null;
        }
    }

    static final class PreparedRemove {
        private static final int UNIQUE = 1;
        private static final int POSTING_ONLY = 2;
        private static final int DIFFERENT_VALUES = 3;

        private IdentityHashIndex owner;
        private int kind;
        private Shard removedShard;
        private int removedSlot;
        private int removedPrevious;
        private int removedNext;
        private Shard tailShard;
        private int tailSlot;
        private int tailPrevious;
        private int insertionPrevious;
        private int insertionNext;
        private int posting;
        private int removedLocator;
        private int tailLocator;

        void prepareUnique(
                IdentityHashIndex owner,
                Shard removedShard,
                int removedSlot,
                Shard tailShard,
                int tailSlot,
                int removedLocator,
                int tailLocator) {
            this.owner = owner;
            this.kind = UNIQUE;
            this.removedShard = removedShard;
            this.removedSlot = removedSlot;
            this.tailShard = tailShard;
            this.tailSlot = tailSlot;
            this.removedLocator = removedLocator;
            this.tailLocator = tailLocator;
        }

        void preparePostingOnly(
                IdentityHashIndex owner,
                Shard shard,
                int slot,
                int previous,
                int next,
                int posting,
                int removedLocator,
                int tailLocator) {
            this.owner = owner;
            this.kind = POSTING_ONLY;
            this.removedShard = shard;
            this.removedSlot = slot;
            this.removedPrevious = previous;
            this.removedNext = next;
            this.posting = posting;
            this.removedLocator = removedLocator;
            this.tailLocator = tailLocator;
        }

        void prepareDifferentValues(
                IdentityHashIndex owner,
                Shard removedShard,
                int removedSlot,
                int removedPrevious,
                int removedNext,
                Shard tailShard,
                int tailSlot,
                int tailPrevious,
                int insertionPrevious,
                int insertionNext,
                int removedLocator,
                int tailLocator) {
            this.owner = owner;
            this.kind = DIFFERENT_VALUES;
            this.removedShard = removedShard;
            this.removedSlot = removedSlot;
            this.removedPrevious = removedPrevious;
            this.removedNext = removedNext;
            this.tailShard = tailShard;
            this.tailSlot = tailSlot;
            this.tailPrevious = tailPrevious;
            this.insertionPrevious = insertionPrevious;
            this.insertionNext = insertionNext;
            this.removedLocator = removedLocator;
            this.tailLocator = tailLocator;
        }

        void commit() {
            if (owner == null) throw new AssertionError("Index remove scratch is not prepared");
            if (kind == UNIQUE) {
                removedShard.removeBucket(removedSlot);
                if (tailShard != null) {
                    tailShard.representatives[tailSlot] = removedLocator;
                    tailShard.heads[tailSlot] = removedLocator;
                    tailShard.tails[tailSlot] = removedLocator;
                }
                return;
            }
            if (kind == POSTING_ONLY) {
                owner.unlinkPosting(
                        removedShard,
                        removedSlot,
                        posting,
                        removedPrevious,
                        removedNext);
                return;
            }

            owner.unlinkPosting(
                    removedShard,
                    removedSlot,
                    removedLocator,
                    removedPrevious,
                    removedNext);
            if (tailShard.counts[tailSlot] == 1L) {
                tailShard.representatives[tailSlot] = removedLocator;
                tailShard.heads[tailSlot] = removedLocator;
                tailShard.tails[tailSlot] = removedLocator;
                owner.links.link(tailLocator, -1);
                owner.links.link(removedLocator, -1);
            } else {
                owner.unlinkPosting(
                        tailShard,
                        tailSlot,
                        tailLocator,
                        tailPrevious,
                        -1);
                owner.insertPosting(
                        tailShard,
                        tailSlot,
                        removedLocator,
                        insertionPrevious,
                        insertionNext);
            }
        }

        void clear() {
            owner = null;
            removedShard = null;
            tailShard = null;
            kind = 0;
        }
    }

    private static final class Bucket {
        private final int head;
        @SuppressWarnings("unused") private final int tail;
        private final int count;

        private Bucket(int head, int tail, int count) {
            this.head = head;
            this.tail = tail;
            this.count = count;
        }
    }

    private static final class Shard {

        private final long[] hashes;
        private final int[] representatives;
        private final int[] heads;
        private final int[] tails;
        private final int[] counts;
        private final byte[] states;
        private final int mask;
        private final long managedBytes;
        private int size;
        private int tombstones;

        private Shard(int capacity, SomaOperation operation, Object provenance) {
            hashes = new long[capacity];
            representatives = new int[capacity];
            heads = new int[capacity];
            tails = new int[capacity];
            counts = new int[capacity];
            states = new byte[capacity];
            mask = capacity - 1;
            managedBytes = estimatedBytes(capacity, operation, provenance);
        }

        private Shard(
                long[] hashes,
                int[] representatives,
                int[] heads,
                int[] tails,
                int[] counts,
                byte[] states,
                int size,
                int tombstones,
                long managedBytes) {
            this.hashes = hashes;
            this.representatives = representatives;
            this.heads = heads;
            this.tails = tails;
            this.counts = counts;
            this.states = states;
            this.mask = hashes.length - 1;
            this.size = size;
            this.tombstones = tombstones;
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
                if (states[slot] == 1 && hashes[slot] == hash
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
                int locator,
                long hash,
                GeneratedTableLayout layout,
                int fieldIndex) {
            int slot = ((int) hash) & mask;
            while (states[slot] != 0) {
                if (states[slot] == 1 && hashes[slot] == hash
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
                int probeLocator,
                int probeFieldIndex) {
            int slot = ((int) hash) & mask;
            while (states[slot] != 0) {
                if (states[slot] == 1 && hashes[slot] == hash
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
            int tombstone = -1;
            while (states[slot] != 0) {
                if (states[slot] == 2 && tombstone < 0) tombstone = slot;
                slot = (slot + 1) & mask;
            }
            return tombstone >= 0 ? tombstone : slot;
        }

        void installNew(int slot, long hash, int locator) {
            if (states[slot] == 2) tombstones--;
            hashes[slot] = hash;
            representatives[slot] = locator;
            heads[slot] = locator;
            tails[slot] = locator;
            counts[slot] = 1;
            states[slot] = 1;
            size++;
        }

        void removeBucket(int slot) {
            if (states[slot] != 1 || counts[slot] != 1L) {
                throw new AssertionError("invalid Index bucket removal");
            }
            representatives[slot] = 0;
            heads[slot] = 0;
            tails[slot] = 0;
            counts[slot] = 0;
            states[slot] = 2;
            size--;
            tombstones++;
        }

        boolean canInsertWithoutRehash() {
            return (long) size + tombstones + 1L
                    <= ((long) hashes.length * 5L) / 8L;
        }

        int capacityForInsert(
                int liveAfter,
                SomaOperation operation,
                Object provenance) {
            if ((long) liveAfter <= ((long) hashes.length * 5L) / 8L) {
                return hashes.length;
            }
            return expandedCapacity(operation, provenance);
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
                if (states[old] == 1) {
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
                            Long.BYTES + 4L * Integer.BYTES + 1L,
                            operation,
                            provenance),
                    operation,
                    provenance);
        }
    }
}
