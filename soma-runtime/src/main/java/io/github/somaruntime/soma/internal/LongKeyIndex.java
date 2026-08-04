package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;

/** Primitive sharded open-addressed Key sidecar; zero is an ordinary Key. */
final class LongKeyIndex {

    private static final int SHARD_BITS = 6;
    private static final int SHARD_COUNT = 1 << SHARD_BITS;
    private static final int INITIAL_CAPACITY = 16;
    private static final long CONTAINER_BYTES = 32L + SHARD_COUNT * 8L;

    private KeyShard[] shards;
    private long managedBytes;

    static LongKeyIndex empty() {
        return new LongKeyIndex();
    }

    long find(long key) {
        if (shards == null) {
            return -1L;
        }
        long hash = mix(key);
        KeyShard shard = shards[shardIndex(hash)];
        return shard == null ? -1L : shard.find(key, hash);
    }

    long managedBytes() {
        return managedBytes;
    }

    boolean canInsertInPlace(long key) {
        if (shards == null) {
            return true;
        }
        KeyShard shard = shards[shardIndex(mix(key))];
        return shard == null || shard.hasInsertCapacity();
    }

    long estimatedManagedBytesAfterAdd(
            long key,
            SomaOperation operation,
            Object provenance) {
        long hash = mix(key);
        if (shards == null) {
            return CheckedLong.add(
                    CONTAINER_BYTES,
                    KeyShard.estimatedBytes(INITIAL_CAPACITY, operation, provenance),
                    operation,
                    provenance);
        }
        KeyShard shard = shards[shardIndex(hash)];
        if (shard == null) {
            return CheckedLong.add(
                    managedBytes,
                    KeyShard.estimatedBytes(INITIAL_CAPACITY, operation, provenance),
                    operation,
                    provenance);
        }
        if (shard.hasInsertCapacity()) {
            return managedBytes;
        }
        int expanded = shard.expandedCapacity(operation, provenance);
        long withoutOld = managedBytes - shard.managedBytes;
        return CheckedLong.add(
                withoutOld,
                KeyShard.estimatedBytes(expanded, operation, provenance),
                operation,
                provenance);
    }

    PreparedInsert prepareInPlace(
            long key,
            long locator,
            SomaOperation operation,
            Object provenance) {
        long hash = mix(key);
        int shardIndex = shardIndex(hash);
        if (shards == null) {
            KeyShard[] candidateShards = new KeyShard[SHARD_COUNT];
            KeyShard candidate = new KeyShard(INITIAL_CAPACITY, operation, provenance);
            candidate.insert(key, locator, hash);
            candidateShards[shardIndex] = candidate;
            return PreparedInsert.installContainer(
                    this,
                    candidateShards,
                    CheckedLong.add(
                            CONTAINER_BYTES,
                            candidate.managedBytes,
                            operation,
                            provenance));
        }
        KeyShard shard = shards[shardIndex];
        if (shard == null) {
            KeyShard candidate = new KeyShard(INITIAL_CAPACITY, operation, provenance);
            candidate.insert(key, locator, hash);
            return PreparedInsert.installShard(
                    this,
                    shardIndex,
                    candidate,
                    CheckedLong.add(
                            managedBytes,
                            candidate.managedBytes,
                            operation,
                            provenance));
        }
        if (!shard.hasInsertCapacity()) {
            return null;
        }
        int slot = shard.emptySlot(hash);
        return PreparedInsert.existing(this, shard, slot, key, locator);
    }

    LongKeyIndex copyAndAdd(
            long key,
            long locator,
            SomaOperation operation,
            Object provenance) {
        LongKeyIndex result = new LongKeyIndex();
        result.shards = new KeyShard[SHARD_COUNT];
        result.managedBytes = CONTAINER_BYTES;
        if (shards != null) {
            for (int index = 0; index < SHARD_COUNT; index++) {
                KeyShard source = shards[index];
                if (source != null) {
                    KeyShard copied = source.copy();
                    result.shards[index] = copied;
                    result.managedBytes = CheckedLong.add(
                            result.managedBytes,
                            copied.managedBytes,
                            operation,
                            provenance);
                }
            }
        }

        long hash = mix(key);
        int index = shardIndex(hash);
        KeyShard target = result.shards[index];
        if (target == null) {
            target = new KeyShard(INITIAL_CAPACITY, operation, provenance);
            result.shards[index] = target;
            result.managedBytes = CheckedLong.add(
                    result.managedBytes,
                    target.managedBytes,
                    operation,
                    provenance);
        } else if (!target.hasInsertCapacity()) {
            int expanded = target.expandedCapacity(operation, provenance);
            KeyShard replacement = target.rehash(expanded, operation, provenance);
            result.managedBytes = result.managedBytes - target.managedBytes;
            result.managedBytes = CheckedLong.add(
                    result.managedBytes,
                    replacement.managedBytes,
                    operation,
                    provenance);
            result.shards[index] = replacement;
            target = replacement;
        }
        target.insert(key, locator, hash);
        return result;
    }

    private static int shardIndex(long hash) {
        return (int) (hash >>> (Long.SIZE - SHARD_BITS));
    }

    private static long mix(long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53l;
        mixed ^= mixed >>> 33;
        return mixed;
    }

    static final class PreparedInsert {

        private static final int EXISTING = 0;
        private static final int INSTALL_SHARD = 1;
        private static final int INSTALL_CONTAINER = 2;

        private final LongKeyIndex owner;
        private final int kind;
        private final KeyShard existing;
        private final int slot;
        private final long key;
        private final long locator;
        private final int shardIndex;
        private final KeyShard newShard;
        private final KeyShard[] newContainer;
        private final long managedBytesAfterCommit;

        private PreparedInsert(
                LongKeyIndex owner,
                int kind,
                KeyShard existing,
                int slot,
                long key,
                long locator,
                int shardIndex,
                KeyShard newShard,
                KeyShard[] newContainer,
                long managedBytesAfterCommit) {
            this.owner = owner;
            this.kind = kind;
            this.existing = existing;
            this.slot = slot;
            this.key = key;
            this.locator = locator;
            this.shardIndex = shardIndex;
            this.newShard = newShard;
            this.newContainer = newContainer;
            this.managedBytesAfterCommit = managedBytesAfterCommit;
        }

        static PreparedInsert existing(
                LongKeyIndex owner,
                KeyShard shard,
                int slot,
                long key,
                long locator) {
            return new PreparedInsert(
                    owner, EXISTING, shard, slot, key, locator,
                    -1, null, null, owner.managedBytes);
        }

        static PreparedInsert installShard(
                LongKeyIndex owner,
                int shardIndex,
                KeyShard shard,
                long bytes) {
            return new PreparedInsert(
                    owner, INSTALL_SHARD, null, -1, 0L, 0L,
                    shardIndex, shard, null, bytes);
        }

        static PreparedInsert installContainer(
                LongKeyIndex owner,
                KeyShard[] container,
                long bytes) {
            return new PreparedInsert(
                    owner, INSTALL_CONTAINER, null, -1, 0L, 0L,
                    -1, null, container, bytes);
        }

        long managedBytesAfterCommit() {
            return managedBytesAfterCommit;
        }

        void commit() {
            if (kind == EXISTING) {
                existing.insertAt(slot, key, locator);
            } else if (kind == INSTALL_SHARD) {
                owner.shards[shardIndex] = newShard;
            } else {
                owner.shards = newContainer;
            }
            owner.managedBytes = managedBytesAfterCommit;
        }
    }

    private static final class KeyShard {

        private final long[] keys;
        private final long[] locators;
        private final byte[] states;
        private final int mask;
        private final long managedBytes;
        private int size;

        private KeyShard(
                int capacity,
                SomaOperation operation,
                Object provenance) {
            this.keys = new long[capacity];
            this.locators = new long[capacity];
            this.states = new byte[capacity];
            this.mask = capacity - 1;
            this.managedBytes = estimatedBytes(capacity, operation, provenance);
        }

        private KeyShard(
                long[] keys,
                long[] locators,
                byte[] states,
                int size,
                long managedBytes) {
            this.keys = keys;
            this.locators = locators;
            this.states = states;
            this.mask = keys.length - 1;
            this.size = size;
            this.managedBytes = managedBytes;
        }

        long find(long key, long hash) {
            int slot = ((int) hash) & mask;
            while (states[slot] != 0) {
                if (keys[slot] == key) {
                    return locators[slot];
                }
                slot = (slot + 1) & mask;
            }
            return -1L;
        }

        boolean hasInsertCapacity() {
            return LongKeyIndex.hasInsertCapacity(size, keys.length);
        }

        int emptySlot(long hash) {
            int slot = ((int) hash) & mask;
            while (states[slot] != 0) {
                slot = (slot + 1) & mask;
            }
            return slot;
        }

        void insert(long key, long locator, long hash) {
            insertAt(emptySlot(hash), key, locator);
        }

        void insertAt(int slot, long key, long locator) {
            keys[slot] = key;
            locators[slot] = locator;
            states[slot] = 1;
            size++;
        }

        int expandedCapacity(SomaOperation operation, Object provenance) {
            if (keys.length > (1 << 29)) {
                throw SomaFailures.failure(
                        io.github.somaruntime.soma.SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                        operation,
                        "Key shard reached Java array representation limit",
                        provenance);
            }
            return keys.length << 1;
        }

        KeyShard copy() {
            return new KeyShard(
                    keys.clone(),
                    locators.clone(),
                    states.clone(),
                    size,
                    managedBytes);
        }

        KeyShard rehash(
                int newCapacity,
                SomaOperation operation,
                Object provenance) {
            KeyShard result = new KeyShard(newCapacity, operation, provenance);
            for (int slot = 0; slot < keys.length; slot++) {
                if (states[slot] != 0) {
                    result.insert(keys[slot], locators[slot], mix(keys[slot]));
                }
            }
            return result;
        }

        static long estimatedBytes(
                int capacity,
                SomaOperation operation,
                Object provenance) {
            long perEntry = Long.BYTES + Long.BYTES + 1L;
            return CheckedLong.add(
                    128L,
                    CheckedLong.multiply(capacity, perEntry, operation, provenance),
                    operation,
                    provenance);
        }
    }

    static boolean hasInsertCapacity(int size, int capacity) {
        return (long) size + 1L <= ((long) capacity * 5L) / 8L;
    }
}
