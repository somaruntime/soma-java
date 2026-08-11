package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;

/**
 * Typed Hash access path over authoritative payload. Every exact logical
 * Bucket owns one canonical ordered locator representation: singleton values
 * are inline and multi-value postings use one ascending primitive int array.
 */
final class IdentityHashIndex {

    private static final int SHARD_BITS = 6;
    private static final int SHARD_COUNT = 1 << SHARD_BITS;
    private static final int INITIAL_CAPACITY = 16;
    private static final int INITIAL_BUCKET_CAPACITY = 4;
    private static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;
    private static final long CONTAINER_BYTES = 64L + SHARD_COUNT * 8L;

    private final GeneratedTableLayout layout;
    private final int fieldIndex;
    private final boolean unique;
    private Shard[] shards;
    private long managedBytes;

    private IdentityHashIndex(
            GeneratedTableLayout layout,
            int fieldIndex,
            boolean unique) {
        this.layout = layout;
        this.fieldIndex = fieldIndex;
        this.unique = unique;
    }

    static IdentityHashIndex empty(
            GeneratedTableLayout layout,
            int fieldIndex,
            boolean unique) {
        return new IdentityHashIndex(layout, fieldIndex, unique);
    }

    static IdentityHashIndex rebuild(
            GeneratedTableLayout layout,
            int fieldIndex,
            boolean unique,
            TableChunkDirectory directory,
            int size,
            SomaOperation operation,
            Object provenance) {
        IdentityHashIndex result = new IdentityHashIndex(layout, fieldIndex, unique);
        for (int locator = 0; locator < size; locator++) {
            result.addStored(directory, locator, operation, provenance);
        }
        return result;
    }

    int findUnique(TableChunkDirectory directory, TypedValues probe) {
        if (!unique) throw new AssertionError("non-unique Index used as Key");
        if (shards == null) return -1;
        long hash = layout.hashField(probe, fieldIndex);
        Shard shard = shards[shardOrdinal(hash)];
        if (shard == null) return -1;
        int slot = shard.findProbe(directory, probe, hash, layout, fieldIndex);
        return slot < 0 ? -1 : shard.firstLocators[slot];
    }

    long count(TableChunkDirectory directory, TypedValues probe) {
        if (shards == null) return 0L;
        long hash = layout.hashField(probe, fieldIndex);
        Shard shard = shards[shardOrdinal(hash)];
        if (shard == null) return 0L;
        int slot = shard.findProbe(directory, probe, hash, layout, fieldIndex);
        return slot < 0 ? 0L : shard.counts[slot];
    }

    int first(TableChunkDirectory directory, TypedValues probe, Cursor cursor) {
        if (cursor == null) throw new AssertionError("Index Cursor is null");
        cursor.clear();
        if (shards == null) return -1;
        long hash = layout.hashField(probe, fieldIndex);
        Shard shard = shards[shardOrdinal(hash)];
        if (shard == null) return -1;
        int slot = shard.findProbe(directory, probe, hash, layout, fieldIndex);
        return slot < 0 ? -1 : cursor.bind(shard, slot);
    }

    int firstJoin(
            TableChunkDirectory directory,
            GeneratedTableLayout probeLayout,
            TableChunkDirectory probeDirectory,
            int probeLocator,
            int probeFieldIndex,
            Cursor cursor) {
        if (cursor == null) throw new AssertionError("Index Cursor is null");
        cursor.clear();
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
        return slot < 0 ? -1 : cursor.bind(shard, slot);
    }

    int next(Cursor cursor) {
        return cursor.next();
    }

    long managedBytes() {
        return managedBytes;
    }

    long preflightAdd(
            PreparedAdd target,
            TableChunkDirectory directory,
            TypedValues probe,
            SomaOperation operation,
            Object provenance,
            String logicalName) {
        if (target.owner != null) throw new AssertionError("Index add scratch is busy");
        long hash = layout.hashField(probe, fieldIndex);
        int ordinal = shardOrdinal(hash);
        Shard current = shards == null ? null : shards[ordinal];
        int existing = current == null
                ? -1 : current.findProbe(directory, probe, hash, layout, fieldIndex);
        if (existing >= 0 && unique) {
            throw SomaFailures.failure(
                    SomaFailureCode.DUPLICATE_KEY,
                    operation,
                    logicalName + " duplicate Key",
                    provenance);
        }
        long bytes = 0L;
        int replacementShardCapacity = 0;
        int replacementBucketCapacity = 0;
        if (existing < 0) {
            if (current == null) {
                replacementShardCapacity = INITIAL_CAPACITY;
                bytes = Shard.estimatedBaseBytes(
                        INITIAL_CAPACITY, operation, provenance);
                if (shards == null) {
                    bytes = CheckedLong.add(
                            bytes, CONTAINER_BYTES, operation, provenance);
                }
            } else if (!current.canInsertWithoutRehash()) {
                replacementShardCapacity = current.capacityForInsert(
                        current.size + 1, operation, provenance);
                bytes = Shard.estimatedBaseBytes(
                        replacementShardCapacity, operation, provenance);
            }
        } else if (!unique) {
            int count = current.counts[existing];
            if (count == 1) {
                replacementBucketCapacity = INITIAL_BUCKET_CAPACITY;
                bytes = arrayBytes(replacementBucketCapacity);
            } else {
                int[] members = current.members[existing];
                if (count == members.length) {
                    replacementBucketCapacity = expandedBucketCapacity(
                            members.length, count + 1, operation, provenance);
                    bytes = arrayBytes(replacementBucketCapacity);
                }
            }
        }
        target.preflight(
                this,
                ordinal,
                current,
                existing,
                hash,
                replacementShardCapacity,
                replacementBucketCapacity,
                shards == null);
        return bytes;
    }

    long distinctCount() {
        if (shards == null) return 0L;
        long result = 0L;
        for (Shard shard : shards) {
            if (shard != null) result += shard.size;
        }
        return result;
    }

    void validateForTesting(TableChunkDirectory directory, int tableSize) {
        if (shards == null) {
            if (tableSize != 0 || managedBytes != 0L) {
                throw new AssertionError("empty Index state is inconsistent");
            }
            return;
        }
        boolean[] seen = new boolean[tableSize];
        int memberships = 0;
        long calculatedBytes = CONTAINER_BYTES;
        for (Shard shard : shards) {
            if (shard == null) continue;
            int liveBuckets = 0;
            long shardBytes = Shard.estimatedBaseBytes(
                    shard.hashes.length, SomaOperation.QUERY, this);
            for (int slot = 0; slot < shard.hashes.length; slot++) {
                if (shard.states[slot] != 1) continue;
                liveBuckets++;
                int count = shard.counts[slot];
                if (count <= 0 || (unique && count != 1)) {
                    throw new AssertionError("invalid Index Bucket cardinality");
                }
                if ((count == 1) != (shard.members[slot] == null)) {
                    throw new AssertionError("invalid singleton/multi representation");
                }
                if (count > 1) shardBytes += arrayBytes(shard.members[slot].length);
                int previous = -1;
                for (int position = 0; position < count; position++) {
                    int locator = shard.locatorAt(slot, position);
                    if (locator < 0 || locator >= tableSize || locator <= previous) {
                        throw new AssertionError("Index Bucket is not canonical ordered");
                    }
                    if (seen[locator]) {
                        throw new AssertionError("locator appears in multiple Index Buckets");
                    }
                    seen[locator] = true;
                    memberships++;
                    if (layout.hashField(directory, locator, fieldIndex)
                            != shard.hashes[slot]
                            || !layout.fieldEquals(
                                    directory,
                                    shard.firstLocators[slot],
                                    locator,
                                    fieldIndex)) {
                        throw new AssertionError("Index Bucket identity is inconsistent");
                    }
                    previous = locator;
                }
            }
            if (liveBuckets != shard.size || shardBytes != shard.managedBytes) {
                throw new AssertionError("Index Shard accounting is inconsistent");
            }
            calculatedBytes += shardBytes;
        }
        if (memberships != tableSize || calculatedBytes != managedBytes) {
            throw new AssertionError("Index membership/accounting is inconsistent");
        }
    }

    int singletonBucketCountForTesting() {
        int result = 0;
        if (shards != null) {
            for (Shard shard : shards) {
                if (shard == null) continue;
                for (int slot = 0; slot < shard.counts.length; slot++) {
                    if (shard.states[slot] == 1 && shard.counts[slot] == 1) result++;
                }
            }
        }
        return result;
    }

    int multiBucketCountForTesting() {
        int result = 0;
        if (shards != null) {
            for (Shard shard : shards) {
                if (shard == null) continue;
                for (int slot = 0; slot < shard.counts.length; slot++) {
                    if (shard.states[slot] == 1 && shard.counts[slot] > 1) result++;
                }
            }
        }
        return result;
    }

    int maxBucketSizeForTesting() {
        int result = 0;
        if (shards != null) {
            for (Shard shard : shards) {
                if (shard == null) continue;
                for (int slot = 0; slot < shard.counts.length; slot++) {
                    if (shard.states[slot] == 1) result = Math.max(result, shard.counts[slot]);
                }
            }
        }
        return result;
    }

    void prepareAdd(
            PreparedAdd target,
            int locator,
            SomaOperation operation,
            Object provenance) {
        target.requirePreflight(this);
        long hash = target.hash;
        int ordinal = target.shardOrdinal;
        Shard current = target.currentShard;
        int existing = target.slot;

        Shard replacement = null;
        Shard[] container = null;
        Shard targetShard = current;
        int slot = existing;
        if (existing < 0) {
            if (target.replacementShardCapacity != 0) {
                replacement = current == null
                        ? new Shard(target.replacementShardCapacity, operation, provenance)
                        : current.rehash(
                                target.replacementShardCapacity, operation, provenance);
                targetShard = replacement;
                slot = replacement.emptySlot(hash);
                if (target.containerRequired) {
                    container = new Shard[SHARD_COUNT];
                    container[ordinal] = replacement;
                }
            } else {
                slot = current.emptySlot(hash);
            }
        }

        int[] replacementMembers = null;
        long bucketDelta = 0L;
        if (target.replacementBucketCapacity != 0) {
            int count = targetShard.counts[slot];
            replacementMembers = allocateBucket(
                    target.replacementBucketCapacity, operation, provenance);
            if (count == 1) {
                replacementMembers[0] = targetShard.firstLocators[slot];
                replacementMembers[1] = locator;
                bucketDelta = arrayBytes(target.replacementBucketCapacity);
            } else {
                int[] members = targetShard.members[slot];
                System.arraycopy(members, 0, replacementMembers, 0, count);
                replacementMembers[count] = locator;
                bucketDelta = arrayBytes(target.replacementBucketCapacity)
                        - arrayBytes(members.length);
            }
        }

        long after = managedBytes;
        if (shards == null) {
            after = CheckedLong.add(after, CONTAINER_BYTES, operation, provenance);
        }
        if (current == null) {
            after = CheckedLong.add(after, targetShard.managedBytes, operation, provenance);
        } else if (replacement != null) {
            after = CheckedLong.add(
                    CheckedLong.subtract(
                            after, current.managedBytes, operation, provenance),
                    replacement.managedBytes,
                    operation,
                    provenance);
        }
        after = CheckedLong.add(after, bucketDelta, operation, provenance);
        target.prepare(
                replacement,
                container,
                slot,
                existing >= 0,
                hash,
                locator,
                replacementMembers,
                after);
    }

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
        Shard sourceShard = shards[sourceOrdinal];
        int sourceSlot = sourceShard.findStored(
                directory, locator, oldHash, layout, fieldIndex);
        if (sourceSlot < 0) throw new AssertionError("indexed source Bucket is missing");

        long newHash = layout.hashField(staged, fieldIndex);
        int destinationOrdinal = shardOrdinal(newHash);
        Shard destinationOriginal = shards[destinationOrdinal];
        int destinationSlot = destinationOriginal == null
                ? -1 : destinationOriginal.findProbe(
                        directory, staged, newHash, layout, fieldIndex);
        boolean destinationExisting = destinationSlot >= 0;
        Shard destinationReplacement = null;
        Shard destinationShard = destinationOriginal;
        long after = managedBytes;

        if (!destinationExisting) {
            if (destinationOriginal == null) {
                destinationReplacement = new Shard(
                        INITIAL_CAPACITY, operation, provenance);
                destinationShard = destinationReplacement;
                destinationSlot = destinationShard.emptySlot(newHash);
                after = CheckedLong.add(
                        after, destinationShard.managedBytes, operation, provenance);
            } else if (!destinationOriginal.canInsertWithoutRehash()) {
                int liveAfter = destinationOriginal.size + 1;
                if (sourceShard == destinationOriginal
                        && sourceShard.counts[sourceSlot] == 1) {
                    liveAfter--;
                }
                destinationReplacement = destinationOriginal.rehash(
                        destinationOriginal.capacityForInsert(
                                liveAfter, operation, provenance),
                        operation,
                        provenance);
                destinationShard = destinationReplacement;
                after = CheckedLong.add(
                        CheckedLong.subtract(
                                after,
                                destinationOriginal.managedBytes,
                                operation,
                                provenance),
                        destinationReplacement.managedBytes,
                        operation,
                        provenance);
                if (sourceShard == destinationOriginal) {
                    sourceShard = destinationReplacement;
                    sourceSlot = sourceShard.findStored(
                            directory, locator, oldHash, layout, fieldIndex);
                    if (sourceSlot < 0) {
                        throw new AssertionError("rehash lost indexed source Bucket");
                    }
                }
                destinationSlot = destinationShard.emptySlot(newHash);
            } else {
                destinationSlot = destinationOriginal.emptySlot(newHash);
            }
        }

        int sourcePosition = sourceShard.positionOf(sourceSlot, locator);
        if (sourcePosition < 0) throw new AssertionError("indexed source locator is missing");
        long sourceDelta = sourceShard.counts[sourceSlot] == 2
                ? -arrayBytes(sourceShard.members[sourceSlot].length) : 0L;

        int destinationPosition = 0;
        int[] replacementMembers = null;
        long destinationDelta = 0L;
        if (destinationExisting) {
            destinationPosition = destinationShard.insertionPosition(destinationSlot, locator);
            if (destinationPosition < 0) {
                throw new AssertionError("destination Bucket already contains locator");
            }
            int destinationCount = destinationShard.counts[destinationSlot];
            if (destinationCount == 1) {
                replacementMembers = allocateBucket(
                        INITIAL_BUCKET_CAPACITY, operation, provenance);
                int existingLocator = destinationShard.firstLocators[destinationSlot];
                if (destinationPosition == 0) {
                    replacementMembers[0] = locator;
                    replacementMembers[1] = existingLocator;
                } else {
                    replacementMembers[0] = existingLocator;
                    replacementMembers[1] = locator;
                }
                destinationDelta = arrayBytes(replacementMembers.length);
            } else {
                int[] members = destinationShard.members[destinationSlot];
                if (destinationCount == members.length) {
                    int capacity = expandedBucketCapacity(
                            members.length,
                            destinationCount + 1,
                            operation,
                            provenance);
                    replacementMembers = allocateBucket(capacity, operation, provenance);
                    System.arraycopy(
                            members, 0, replacementMembers, 0, destinationPosition);
                    replacementMembers[destinationPosition] = locator;
                    System.arraycopy(
                            members,
                            destinationPosition,
                            replacementMembers,
                            destinationPosition + 1,
                            destinationCount - destinationPosition);
                    destinationDelta = arrayBytes(capacity) - arrayBytes(members.length);
                }
            }
        }
        after = CheckedLong.add(after, sourceDelta, operation, provenance);
        after = CheckedLong.add(after, destinationDelta, operation, provenance);

        target.prepare(
                this,
                sourceShard,
                sourceSlot,
                sourcePosition,
                destinationOrdinal,
                destinationShard,
                destinationReplacement,
                destinationSlot,
                destinationExisting,
                destinationPosition,
                replacementMembers,
                newHash,
                locator,
                after);
    }

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
        if (removedSlot < 0) throw new AssertionError("removed Index Bucket is missing");

        if (unique) {
            if (tailLocator == removedLocator) {
                target.prepareUnique(
                        this,
                        removedShard,
                        removedSlot,
                        null,
                        -1,
                        removedLocator,
                        tailLocator,
                        managedBytes);
                return;
            }
            long tailHash = layout.hashField(directory, tailLocator, fieldIndex);
            Shard tailShard = shards[shardOrdinal(tailHash)];
            int tailSlot = tailShard.findStored(
                    directory, tailLocator, tailHash, layout, fieldIndex);
            if (tailSlot < 0 || tailShard.counts[tailSlot] != 1) {
                throw new AssertionError("moved Key Bucket is invalid");
            }
            target.prepareUnique(
                    this,
                    removedShard,
                    removedSlot,
                    tailShard,
                    tailSlot,
                    removedLocator,
                    tailLocator,
                    managedBytes);
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
            if (slot < 0) throw new AssertionError("same-value tail Bucket is missing");
            int position = removedShard.positionOf(slot, posting);
            if (position < 0) throw new AssertionError("same-value tail locator is missing");
            long after = managedBytes;
            if (removedShard.counts[slot] == 2) {
                after -= arrayBytes(removedShard.members[slot].length);
            }
            target.preparePostingOnly(
                    this,
                    removedShard,
                    slot,
                    position,
                    removedLocator,
                    tailLocator,
                    after);
            return;
        }

        int removedPosition = removedShard.positionOf(removedSlot, removedLocator);
        if (removedPosition < 0) throw new AssertionError("removed Index locator is missing");
        long tailHash = layout.hashField(directory, tailLocator, fieldIndex);
        Shard tailShard = shards[shardOrdinal(tailHash)];
        int tailSlot = tailShard.findStored(
                directory, tailLocator, tailHash, layout, fieldIndex);
        if (tailSlot < 0) throw new AssertionError("moved Index Bucket is missing");
        int tailPosition = tailShard.positionOf(tailSlot, tailLocator);
        if (tailPosition != tailShard.counts[tailSlot] - 1) {
            throw new AssertionError("tail locator is not canonical Bucket tail");
        }
        int insertionPosition = tailShard.insertionPositionBeforeTail(
                tailSlot, removedLocator);
        long after = managedBytes;
        if (removedShard.counts[removedSlot] == 2) {
            after -= arrayBytes(removedShard.members[removedSlot].length);
        }
        target.prepareDifferentValues(
                this,
                removedShard,
                removedSlot,
                removedPosition,
                tailShard,
                tailSlot,
                insertionPosition,
                removedLocator,
                tailLocator,
                after);
    }

    private void addStored(
            TableChunkDirectory directory,
            int locator,
            SomaOperation operation,
            Object provenance) {
        long hash = layout.hashField(directory, locator, fieldIndex);
        if (shards == null) {
            shards = new Shard[SHARD_COUNT];
            managedBytes = CONTAINER_BYTES;
        }
        int ordinal = shardOrdinal(hash);
        Shard shard = shards[ordinal];
        if (shard == null) {
            shard = new Shard(INITIAL_CAPACITY, operation, provenance);
            shards[ordinal] = shard;
            managedBytes = CheckedLong.add(
                    managedBytes, shard.managedBytes, operation, provenance);
        }
        int slot = shard.findStored(directory, locator, hash, layout, fieldIndex);
        if (slot >= 0) {
            if (unique) throw new AssertionError("duplicate Key while rebuilding sidecar");
            long before = shard.managedBytes;
            shard.appendFresh(slot, locator, operation, provenance);
            managedBytes = CheckedLong.add(
                    CheckedLong.subtract(
                            managedBytes, before, operation, provenance),
                    shard.managedBytes,
                    operation,
                    provenance);
            return;
        }
        if (!shard.canInsertWithoutRehash()) {
            Shard replacement = shard.rehash(
                    shard.capacityForInsert(
                            shard.size + 1, operation, provenance),
                    operation,
                    provenance);
            managedBytes = CheckedLong.add(
                    CheckedLong.subtract(
                            managedBytes,
                            shard.managedBytes,
                            operation,
                            provenance),
                    replacement.managedBytes,
                    operation,
                    provenance);
            shards[ordinal] = replacement;
            shard = replacement;
        }
        shard.installNew(shard.emptySlot(hash), hash, locator);
    }

    private static int shardOrdinal(long hash) {
        return (int) (hash >>> (Long.SIZE - SHARD_BITS));
    }

    private static int[] allocateBucket(
            int capacity,
            SomaOperation operation,
            Object provenance) {
        if (capacity < 0 || capacity > MAX_ARRAY_LENGTH) {
            throw resourceLimit(operation, provenance, "Index Bucket exceeds Java array limit");
        }
        return new int[capacity];
    }

    private static int expandedBucketCapacity(
            int current,
            int required,
            SomaOperation operation,
            Object provenance) {
        if (required < 0 || required > MAX_ARRAY_LENGTH) {
            throw resourceLimit(operation, provenance, "Index Bucket exceeds Java array limit");
        }
        long preferred = Math.max((long) required, (long) current + (current >> 1) + 1L);
        return preferred > MAX_ARRAY_LENGTH ? required : (int) preferred;
    }

    private static long arrayBytes(int capacity) {
        long raw = 16L + (long) capacity * Integer.BYTES;
        return (raw + 7L) & ~7L;
    }

    private static RuntimeException resourceLimit(
            SomaOperation operation,
            Object provenance,
            String message) {
        return SomaFailures.failure(
                SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                operation,
                message,
                provenance);
    }

    static final class Cursor {
        private Shard shard;
        private int slot;
        private int position;

        int bind(Shard shard, int slot) {
            this.shard = shard;
            this.slot = slot;
            this.position = 0;
            return shard.locatorAt(slot, 0);
        }

        int next() {
            if (shard == null) return -1;
            int nextPosition = position + 1;
            if (nextPosition >= shard.counts[slot]) {
                clear();
                return -1;
            }
            position = nextPosition;
            return shard.locatorAt(slot, position);
        }

        void clear() {
            shard = null;
            slot = -1;
            position = -1;
        }
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
        private int[] replacementMembers;
        private long managedBytesAfter;
        private int replacementShardCapacity;
        private int replacementBucketCapacity;
        private boolean containerRequired;
        private boolean prepared;

        private void preflight(
                IdentityHashIndex owner,
                int shardOrdinal,
                Shard currentShard,
                int slot,
                long hash,
                int replacementShardCapacity,
                int replacementBucketCapacity,
                boolean containerRequired) {
            this.owner = owner;
            this.shardOrdinal = shardOrdinal;
            this.currentShard = currentShard;
            this.slot = slot;
            this.hash = hash;
            this.replacementShardCapacity = replacementShardCapacity;
            this.replacementBucketCapacity = replacementBucketCapacity;
            this.containerRequired = containerRequired;
            this.prepared = false;
        }

        private void requirePreflight(IdentityHashIndex candidate) {
            if (owner != candidate || prepared) {
                throw new AssertionError("Index add scratch is not preflighted");
            }
        }

        long managedBytesAfter() {
            if (!prepared) throw new AssertionError("Index add scratch is not prepared");
            return managedBytesAfter;
        }

        private void prepare(
                Shard replacement,
                Shard[] container,
                int slot,
                boolean existing,
                long hash,
                int locator,
                int[] replacementMembers,
                long managedBytesAfter) {
            this.replacement = replacement;
            this.container = container;
            this.slot = slot;
            this.existing = existing;
            this.hash = hash;
            this.locator = locator;
            this.replacementMembers = replacementMembers;
            this.managedBytesAfter = managedBytesAfter;
            this.prepared = true;
        }

        void commit() {
            if (!prepared) throw new AssertionError("Index add scratch is not prepared");
            Shard target;
            if (container != null) {
                owner.shards = container;
                target = replacement;
            } else if (replacement != null) {
                owner.shards[shardOrdinal] = replacement;
                target = replacement;
            } else {
                target = currentShard;
            }
            if (existing) {
                target.appendPrepared(slot, locator, replacementMembers);
            } else if (replacement == null) {
                target.installNew(slot, hash, locator);
            } else if (target.states[slot] == 0 || target.states[slot] == 2) {
                target.installNew(slot, hash, locator);
            }
            owner.managedBytes = managedBytesAfter;
        }

        void clear() {
            owner = null;
            currentShard = null;
            replacement = null;
            container = null;
            replacementMembers = null;
            replacementShardCapacity = 0;
            replacementBucketCapacity = 0;
            containerRequired = false;
            prepared = false;
        }
    }

    static final class PreparedUpdate {
        private IdentityHashIndex owner;
        private Shard sourceShard;
        private int sourceSlot;
        private int sourcePosition;
        private int destinationOrdinal;
        private Shard destinationShard;
        private Shard destinationReplacement;
        private int destinationSlot;
        private boolean destinationExisting;
        private int destinationPosition;
        private int[] replacementMembers;
        private long newHash;
        private int locator;
        private long managedBytesAfter;

        private void prepare(
                IdentityHashIndex owner,
                Shard sourceShard,
                int sourceSlot,
                int sourcePosition,
                int destinationOrdinal,
                Shard destinationShard,
                Shard destinationReplacement,
                int destinationSlot,
                boolean destinationExisting,
                int destinationPosition,
                int[] replacementMembers,
                long newHash,
                int locator,
                long managedBytesAfter) {
            this.owner = owner;
            this.sourceShard = sourceShard;
            this.sourceSlot = sourceSlot;
            this.sourcePosition = sourcePosition;
            this.destinationOrdinal = destinationOrdinal;
            this.destinationShard = destinationShard;
            this.destinationReplacement = destinationReplacement;
            this.destinationSlot = destinationSlot;
            this.destinationExisting = destinationExisting;
            this.destinationPosition = destinationPosition;
            this.replacementMembers = replacementMembers;
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
            sourceShard.removeAt(sourceSlot, sourcePosition);
            if (destinationExisting) {
                destinationShard.insertPrepared(
                        destinationSlot,
                        locator,
                        destinationPosition,
                        replacementMembers);
            } else {
                destinationShard.installNew(destinationSlot, newHash, locator);
            }
            if (destinationReplacement != null) {
                owner.shards[destinationOrdinal] = destinationReplacement;
            }
            owner.managedBytes = managedBytesAfter;
        }

        void clear() {
            owner = null;
            sourceShard = null;
            destinationShard = null;
            destinationReplacement = null;
            replacementMembers = null;
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
        private int removedPosition;
        private Shard tailShard;
        private int tailSlot;
        private int insertionPosition;
        private int removedLocator;
        private int tailLocator;
        private long managedBytesAfter;

        void prepareUnique(
                IdentityHashIndex owner,
                Shard removedShard,
                int removedSlot,
                Shard tailShard,
                int tailSlot,
                int removedLocator,
                int tailLocator,
                long managedBytesAfter) {
            this.owner = owner;
            this.kind = UNIQUE;
            this.removedShard = removedShard;
            this.removedSlot = removedSlot;
            this.tailShard = tailShard;
            this.tailSlot = tailSlot;
            this.removedLocator = removedLocator;
            this.tailLocator = tailLocator;
            this.managedBytesAfter = managedBytesAfter;
        }

        void preparePostingOnly(
                IdentityHashIndex owner,
                Shard shard,
                int slot,
                int position,
                int removedLocator,
                int tailLocator,
                long managedBytesAfter) {
            this.owner = owner;
            this.kind = POSTING_ONLY;
            this.removedShard = shard;
            this.removedSlot = slot;
            this.removedPosition = position;
            this.removedLocator = removedLocator;
            this.tailLocator = tailLocator;
            this.managedBytesAfter = managedBytesAfter;
        }

        void prepareDifferentValues(
                IdentityHashIndex owner,
                Shard removedShard,
                int removedSlot,
                int removedPosition,
                Shard tailShard,
                int tailSlot,
                int insertionPosition,
                int removedLocator,
                int tailLocator,
                long managedBytesAfter) {
            this.owner = owner;
            this.kind = DIFFERENT_VALUES;
            this.removedShard = removedShard;
            this.removedSlot = removedSlot;
            this.removedPosition = removedPosition;
            this.tailShard = tailShard;
            this.tailSlot = tailSlot;
            this.insertionPosition = insertionPosition;
            this.removedLocator = removedLocator;
            this.tailLocator = tailLocator;
            this.managedBytesAfter = managedBytesAfter;
        }

        long managedBytesAfter() {
            if (owner == null) throw new AssertionError("Index remove scratch is not prepared");
            return managedBytesAfter;
        }

        void commit() {
            if (owner == null) throw new AssertionError("Index remove scratch is not prepared");
            if (kind == UNIQUE) {
                removedShard.removeBucket(removedSlot);
                if (tailShard != null) tailShard.replaceSingleton(tailSlot, removedLocator);
            } else if (kind == POSTING_ONLY) {
                removedShard.removeAt(removedSlot, removedPosition);
            } else if (kind == DIFFERENT_VALUES) {
                removedShard.removeAt(removedSlot, removedPosition);
                tailShard.replaceTail(tailSlot, tailLocator, removedLocator, insertionPosition);
            } else {
                throw new AssertionError("unknown prepared Index remove kind");
            }
            owner.managedBytes = managedBytesAfter;
        }

        void clear() {
            owner = null;
            removedShard = null;
            tailShard = null;
            kind = 0;
        }
    }

    private static final class Shard {
        private final long[] hashes;
        private final int[] firstLocators;
        private final int[] counts;
        private final int[][] members;
        private final byte[] states;
        private final int mask;
        private long managedBytes;
        private int size;
        private int tombstones;

        private Shard(int capacity, SomaOperation operation, Object provenance) {
            hashes = new long[capacity];
            firstLocators = new int[capacity];
            counts = new int[capacity];
            members = new int[capacity][];
            states = new byte[capacity];
            mask = capacity - 1;
            managedBytes = estimatedBaseBytes(capacity, operation, provenance);
        }

        private Shard(
                long[] hashes,
                int[] firstLocators,
                int[] counts,
                int[][] members,
                byte[] states,
                int size,
                int tombstones,
                long managedBytes) {
            this.hashes = hashes;
            this.firstLocators = firstLocators;
            this.counts = counts;
            this.members = members;
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
                if (states[slot] == 1
                        && hashes[slot] == hash
                        && layout.fieldEquals(
                                directory,
                                firstLocators[slot],
                                probe,
                                fieldIndex)) {
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
                if (states[slot] == 1
                        && hashes[slot] == hash
                        && layout.fieldEquals(
                                directory,
                                firstLocators[slot],
                                locator,
                                fieldIndex)) {
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
                if (states[slot] == 1
                        && hashes[slot] == hash
                        && layout.joinFieldEquals(
                                directory,
                                firstLocators[slot],
                                fieldIndex,
                                probeLayout,
                                probeDirectory,
                                probeLocator,
                                probeFieldIndex)) {
                    return slot;
                }
                slot = (slot + 1) & mask;
            }
            return -1;
        }

        int locatorAt(int slot, int position) {
            return counts[slot] == 1 ? firstLocators[slot] : members[slot][position];
        }

        int positionOf(int slot, int locator) {
            int count = counts[slot];
            if (count == 1) return firstLocators[slot] == locator ? 0 : -1;
            int[] values = members[slot];
            for (int position = 0; position < count; position++) {
                int current = values[position];
                if (current == locator) return position;
                if (current > locator) return -1;
            }
            return -1;
        }

        int insertionPosition(int slot, int locator) {
            int count = counts[slot];
            if (count == 1) {
                int existing = firstLocators[slot];
                if (existing == locator) return -1;
                return locator < existing ? 0 : 1;
            }
            int[] values = members[slot];
            for (int position = 0; position < count; position++) {
                int current = values[position];
                if (current == locator) return -1;
                if (current > locator) return position;
            }
            return count;
        }

        int insertionPositionBeforeTail(int slot, int locator) {
            int count = counts[slot];
            if (count == 1) return 0;
            int[] values = members[slot];
            for (int position = 0; position < count - 1; position++) {
                if (values[position] >= locator) return position;
            }
            return count - 1;
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
            firstLocators[slot] = locator;
            counts[slot] = 1;
            members[slot] = null;
            states[slot] = 1;
            size++;
        }

        void appendFresh(
                int slot,
                int locator,
                SomaOperation operation,
                Object provenance) {
            int count = counts[slot];
            if (count == 1) {
                int[] replacement = allocateBucket(
                        INITIAL_BUCKET_CAPACITY, operation, provenance);
                replacement[0] = firstLocators[slot];
                replacement[1] = locator;
                members[slot] = replacement;
                counts[slot] = 2;
                managedBytes += arrayBytes(replacement.length);
                return;
            }
            int[] values = members[slot];
            if (count == values.length) {
                int capacity = expandedBucketCapacity(
                        values.length, count + 1, operation, provenance);
                int[] replacement = allocateBucket(capacity, operation, provenance);
                System.arraycopy(values, 0, replacement, 0, count);
                members[slot] = replacement;
                managedBytes += arrayBytes(capacity) - arrayBytes(values.length);
                values = replacement;
            }
            values[count] = locator;
            counts[slot] = count + 1;
        }

        void appendPrepared(int slot, int locator, int[] replacement) {
            int count = counts[slot];
            if (replacement != null) {
                int oldLength = count == 1 ? 0 : members[slot].length;
                members[slot] = replacement;
                managedBytes += arrayBytes(replacement.length)
                        - (oldLength == 0 ? 0L : arrayBytes(oldLength));
            } else {
                members[slot][count] = locator;
            }
            counts[slot] = count + 1;
            firstLocators[slot] = members[slot][0];
        }

        void insertPrepared(
                int slot,
                int locator,
                int position,
                int[] replacement) {
            int count = counts[slot];
            if (replacement != null) {
                int oldLength = count == 1 ? 0 : members[slot].length;
                members[slot] = replacement;
                managedBytes += arrayBytes(replacement.length)
                        - (oldLength == 0 ? 0L : arrayBytes(oldLength));
            } else {
                int[] values = members[slot];
                System.arraycopy(
                        values,
                        position,
                        values,
                        position + 1,
                        count - position);
                values[position] = locator;
            }
            counts[slot] = count + 1;
            firstLocators[slot] = members[slot][0];
        }

        void removeAt(int slot, int position) {
            int count = counts[slot];
            if (count == 1) {
                removeBucket(slot);
                return;
            }
            int[] values = members[slot];
            if (count == 2) {
                int survivor = values[position == 0 ? 1 : 0];
                members[slot] = null;
                firstLocators[slot] = survivor;
                counts[slot] = 1;
                managedBytes -= arrayBytes(values.length);
                return;
            }
            System.arraycopy(
                    values,
                    position + 1,
                    values,
                    position,
                    count - position - 1);
            values[count - 1] = 0;
            counts[slot] = count - 1;
            firstLocators[slot] = values[0];
        }

        void replaceSingleton(int slot, int locator) {
            if (counts[slot] != 1 || members[slot] != null) {
                throw new AssertionError("Key Bucket is not singleton");
            }
            firstLocators[slot] = locator;
        }

        void replaceTail(
                int slot,
                int expectedTail,
                int replacement,
                int insertionPosition) {
            int count = counts[slot];
            if (count == 1) {
                if (firstLocators[slot] != expectedTail) {
                    throw new AssertionError("Index tail locator changed before commit");
                }
                firstLocators[slot] = replacement;
                return;
            }
            int[] values = members[slot];
            if (values[count - 1] != expectedTail) {
                throw new AssertionError("Index tail locator changed before commit");
            }
            System.arraycopy(
                    values,
                    insertionPosition,
                    values,
                    insertionPosition + 1,
                    count - 1 - insertionPosition);
            values[insertionPosition] = replacement;
            firstLocators[slot] = values[0];
        }

        void removeBucket(int slot) {
            if (states[slot] != 1 || counts[slot] != 1 || members[slot] != null) {
                throw new AssertionError("invalid Index Bucket removal");
            }
            hashes[slot] = 0L;
            firstLocators[slot] = 0;
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
            if (hashes.length > (1 << 29)) {
                throw resourceLimit(
                        operation, provenance, "Index shard reached Java array limit");
            }
            return hashes.length << 1;
        }

        Shard rehash(int capacity, SomaOperation operation, Object provenance) {
            Shard result = new Shard(capacity, operation, provenance);
            for (int old = 0; old < hashes.length; old++) {
                if (states[old] != 1) continue;
                int slot = result.emptySlot(hashes[old]);
                result.hashes[slot] = hashes[old];
                result.firstLocators[slot] = firstLocators[old];
                result.counts[slot] = counts[old];
                result.members[slot] = members[old];
                result.states[slot] = 1;
                result.size++;
                if (members[old] != null) {
                    result.managedBytes += arrayBytes(members[old].length);
                }
            }
            return result;
        }

        static long estimatedBaseBytes(
                int capacity,
                SomaOperation operation,
                Object provenance) {
            return CheckedLong.add(
                    160L,
                    CheckedLong.multiply(
                            capacity,
                            Long.BYTES + 2L * Integer.BYTES + 8L + 1L,
                            operation,
                            provenance),
                    operation,
                    provenance);
        }
    }
}
