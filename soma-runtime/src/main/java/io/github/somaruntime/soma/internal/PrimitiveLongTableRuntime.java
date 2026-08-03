package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaOperationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** First PLAIN primitive-keyed backend used by generated I1 facades. */
public final class PrimitiveLongTableRuntime {
    private static final int DEFAULT_CHUNK_SIZE = 1024;
    private static final int DIRECTORY_PAGE_SIZE = 256;
    private static final int DIRECTORY_ROOT_SIZE = 256;
    private static final int MAX_INDEX_LENGTH = 1 << 30;
    private static final int INDEX_SHARD_COUNT = 16;
    private static final int INITIAL_SHARD_LENGTH = 8;

    private final GroupRuntime group;
    private final long defaultCapacity;
    private final int chunkSize;
    private final AtomicReference<StateRoot> current;

    /** Creates an empty backend for one generated Group instance. */
    public PrimitiveLongTableRuntime(GroupRuntime group, long defaultCapacity) {
        if (group == null) {
            throw new NullPointerException("group");
        }
        if (defaultCapacity < 0L) {
            throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.RESERVE,
                    "defaultCapacity must not be negative", null);
        }
        this.group = group;
        this.defaultCapacity = defaultCapacity;
        this.chunkSize = configuredChunkSize();
        this.current = new AtomicReference<StateRoot>(StateRoot.empty(chunkSize));
    }

    public long size() {
        GroupRuntime.Guard guard = group.enter(SomaOperation.QUERY);
        try {
            return current.get().size;
        } finally {
            guard.close();
        }
    }

    public long capacity() {
        GroupRuntime.Guard guard = group.enter(SomaOperation.QUERY);
        try {
            return current.get().capacity;
        } finally {
            guard.close();
        }
    }

    /** Internal qualification observation of the published logical generation. */
    public long stateVersion() {
        GroupRuntime.Guard guard = group.enter(SomaOperation.QUERY);
        try {
            return current.get().version;
        } finally {
            guard.close();
        }
    }

    /** Reserves capacity through a candidate StateRoot publication. */
    public void reserve(long expectedRows) {
        if (expectedRows < 0L) {
            throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.RESERVE,
                    "expectedRows must not be negative", null);
        }
        GroupRuntime.Guard guard = group.enter(SomaOperation.RESERVE);
        try {
            StateRoot root = current.get();
            if (expectedRows <= root.capacity) {
                return;
            }
            StateRoot grown = grow(root, expectedRows, SomaOperation.RESERVE);
            StateRoot candidate = new StateRoot(
                    grown.size,
                    grown.capacity,
                    nextVersion(root.version, SomaOperation.RESERVE),
                    grown.directory,
                    grown.index);
            publish(root, candidate, SomaOperation.RESERVE);
        } finally {
            guard.close();
        }
    }

    /** Appends one detached primitive row. */
    public void add(long key, long payload) {
        GroupRuntime.Guard guard = group.enter(SomaOperation.ADD);
        try {
            StateRoot root = current.get();
            if (root.index.contains(key)) {
                throw failure(SomaFailureCode.DUPLICATE_KEY, SomaOperation.ADD,
                        "key already exists", null);
            }
            long target = root.size == root.capacity
                    ? growthTarget(root.size, defaultCapacity)
                    : root.capacity;
            StateRoot capacityRoot = target > root.capacity
                    ? grow(root, target, SomaOperation.ADD)
                    : root;
            long locator = capacityRoot.size;
            ChunkDirectory directory = capacityRoot.directory.withRow(locator, key, payload);
            LongKeyIndex index = capacityRoot.index.copyForPut(key, locator);
            StateRoot candidate = new StateRoot(
                    checkedAdd(capacityRoot.size, 1L, SomaOperation.ADD),
                    capacityRoot.capacity,
                    nextVersion(capacityRoot.version, SomaOperation.ADD),
                    directory,
                    index);
            publish(root, candidate, SomaOperation.ADD);
        } finally {
            guard.close();
        }
    }

    /** Returns an opaque locator, or -1 when the key is absent. */
    public long findLocator(long key) {
        GroupRuntime.Guard guard = group.enter(SomaOperation.FIND);
        try {
            return current.get().index.find(key);
        } finally {
            guard.close();
        }
    }

    public PointUpdate beginUpdate(long key) {
        GroupRuntime.Guard guard = group.enter(SomaOperation.UPDATE);
        StateRoot root = current.get();
        long locator = root.index.find(key);
        if (locator < 0L) {
            guard.close();
            return PointUpdate.missing();
        }
        return new PointUpdate(
                this, guard, root, locator, key, root.directory.payloadAt(locator));
    }

    public Query beginQuery() {
        return beginQuery(SomaOperation.QUERY);
    }

    /** Begins a guarded snapshot for a direct point/read operation. */
    public Query beginQuery(SomaOperation operation) {
        if (operation == null) {
            throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.QUERY,
                    "query operation must not be null", null);
        }
        GroupRuntime.Guard guard = group.enter(operation);
        return new Query(guard, current.get());
    }

    private void commit(PointUpdate update) {
        prepare(update);
        commitCandidate(update);
    }

    private void prepare(PointUpdate update) {
        if (update.proposedPayload == update.originalPayload) {
            update.prepared = update.root;
            return;
        }
        ChunkDirectory directory = update.root.directory.withPayload(
                update.locator, update.proposedPayload);
        StateRoot candidate = new StateRoot(
                update.root.size,
                update.root.capacity,
                nextVersion(update.root.version, SomaOperation.UPDATE),
                directory,
                update.root.index);
        update.prepared = candidate;
    }

    private void commitCandidate(PointUpdate update) {
        if (update.prepared == null) {
            throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.UPDATE,
                    "point update was not prepared", null);
        }
        if (update.prepared != update.root) {
            publish(update.root, update.prepared, SomaOperation.UPDATE);
        }
        update.guard.close();
    }

    private void prepareInPlace(PointUpdate update) {
        if (update.proposedPayload == update.originalPayload) {
            update.preparedInPlace = new PreparedInPlace(
                    update.root, null, -1, update.originalPayload);
            return;
        }
        int offset = checkedInt(update.locator % chunkSize);
        Chunk chunk = update.root.directory.chunk(update.locator);
        if (!chunk.occupied[offset]) {
            throw failure(SomaFailureCode.CALLBACK_SCOPE_VIOLATION, SomaOperation.UPDATE,
                    "point update locator is not occupied", null);
        }
        StateRoot descriptor = new StateRoot(
                update.root.size,
                update.root.capacity,
                nextVersion(update.root.version, SomaOperation.UPDATE),
                update.root.directory,
                update.root.index);
        update.preparedInPlace = new PreparedInPlace(
                descriptor, chunk, offset, update.proposedPayload);
    }

    private void commitPrevalidated(PointUpdate update) {
        PreparedInPlace prepared = update.preparedInPlace;
        if (prepared == null) {
            throw failure(SomaFailureCode.INVALID_ARGUMENT, SomaOperation.UPDATE,
                    "point update was not prepared for in-place publication", null);
        }
        if (prepared.chunk == null) {
            update.guard.close();
            return;
        }
        long before = prepared.chunk.payloads[prepared.offset];
        prepared.chunk.payloads[prepared.offset] = prepared.nextPayload;
        if (!current.compareAndSet(update.root, prepared.descriptor)) {
            prepared.chunk.payloads[prepared.offset] = before;
            throw failure(SomaFailureCode.CONCURRENT_GROUP_OPERATION, SomaOperation.UPDATE,
                    "table StateRoot changed during prevalidated publication", null);
        }
        update.guard.close();
    }

    private void abort(PointUpdate update) {
        update.guard.close();
    }

    private StateRoot grow(StateRoot root, long requested, SomaOperation operation) {
        long chunkCount = ceilDiv(requested, chunkSize);
        long actualCapacity;
        try {
            actualCapacity = Math.multiplyExact(chunkCount, (long) chunkSize);
        } catch (ArithmeticException exception) {
            throw failure(SomaFailureCode.ARITHMETIC_OVERFLOW, operation,
                    "chunk capacity arithmetic overflow", exception);
        }
        return new StateRoot(
                root.size,
                actualCapacity,
                root.version,
                root.directory.withCapacity(requested),
                root.index);
    }

    private void publish(StateRoot expected, StateRoot candidate, SomaOperation operation) {
        if (!current.compareAndSet(expected, candidate)) {
            throw failure(SomaFailureCode.CONCURRENT_GROUP_OPERATION, operation,
                    "table StateRoot changed during guarded operation", null);
        }
    }

    private long growthTarget(long size, long hint) {
        long minimum = checkedAdd(size, 1L, SomaOperation.ADD);
        long initial = hint > 0L ? hint : 1L;
        if (initial >= minimum) {
            return initial;
        }
        long doubled = size <= Long.MAX_VALUE / 2L ? size * 2L : Long.MAX_VALUE;
        return doubled >= minimum ? doubled : minimum;
    }

    private static int configuredChunkSize() {
        String configured = System.getProperty("soma.test.chunkSize");
        if (configured == null) {
            return DEFAULT_CHUNK_SIZE;
        }
        try {
            int value = Integer.parseInt(configured);
            if (value > 0 && value <= 1 << 20) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Test-only malformed input falls back to the production default.
        }
        return DEFAULT_CHUNK_SIZE;
    }

    private static long ceilDiv(long value, long divisor) {
        if (value == 0L) {
            return 0L;
        }
        long quotient = value / divisor;
        return value % divisor == 0L ? quotient : quotient + 1L;
    }

    private static long checkedAdd(long left, long right, SomaOperation operation) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw failure(SomaFailureCode.ARITHMETIC_OVERFLOW, operation,
                    "checked long arithmetic overflow", exception);
        }
    }

    private static long nextVersion(long version, SomaOperation operation) {
        return checkedAdd(version, 1L, operation);
    }

    private static SomaOperationException failure(
            SomaFailureCode code,
            SomaOperation operation,
            String context,
            Throwable cause) {
        return SomaRuntimeAccess.failure(code, operation, context, cause);
    }

    /** Shared operation admission for one generated Group. */
    public static final class GroupRuntime {
        private final AtomicBoolean active = new AtomicBoolean();
        private volatile Thread owner;

        public Guard enter(SomaOperation operation) {
            Thread currentThread = Thread.currentThread();
            if (owner == currentThread) {
                throw failure(SomaFailureCode.REENTRANT_GROUP_OPERATION, operation,
                        "nested operation on the same Group", null);
            }
            if (!active.compareAndSet(false, true)) {
                throw failure(SomaFailureCode.CONCURRENT_GROUP_OPERATION, operation,
                        "another operation is active on this Group", null);
            }
            owner = currentThread;
            return new Guard(this);
        }

        private void leave() {
            owner = null;
            active.set(false);
        }

        /** Lifetime token for one guarded operation. */
        public static final class Guard implements AutoCloseable {
            private GroupRuntime group;

            private Guard(GroupRuntime group) {
                this.group = group;
            }

            @Override
            public void close() {
                GroupRuntime value = group;
                if (value != null) {
                    group = null;
                    value.leave();
                }
            }
        }
    }

    /** Borrowed point-update state used by a generated Editor. */
    public static final class PointUpdate {
        private final PrimitiveLongTableRuntime runtime;
        private final GroupRuntime.Guard guard;
        private final StateRoot root;
        private final long locator;
        private final long key;
        private final long originalPayload;
        private final Thread owner;
        private long proposedPayload;
        private StateRoot prepared;
        private PreparedInPlace preparedInPlace;
        private boolean closed;
        private final boolean matched;

        private PointUpdate(
                PrimitiveLongTableRuntime runtime,
                GroupRuntime.Guard guard,
                StateRoot root,
                long locator,
                long key,
                long payload) {
            this.runtime = runtime;
            this.guard = guard;
            this.root = root;
            this.locator = locator;
            this.key = key;
            this.originalPayload = payload;
            this.owner = Thread.currentThread();
            this.proposedPayload = payload;
            this.matched = true;
        }

        private PointUpdate() {
            this.runtime = null;
            this.guard = null;
            this.root = null;
            this.locator = -1L;
            this.key = 0L;
            this.originalPayload = 0L;
            this.owner = null;
            this.proposedPayload = 0L;
            this.matched = false;
        }

        private static PointUpdate missing() {
            return new PointUpdate();
        }

        public boolean matched() {
            return matched;
        }

        public long key() {
            ensureOpen();
            return key;
        }

        public long payload() {
            ensureOpen();
            return proposedPayload;
        }

        public void payload(long value) {
            ensureOpen();
            proposedPayload = value;
        }

        public long originalPayload() {
            ensureOpen();
            return originalPayload;
        }

        public void commit() {
            ensureOpen();
            runtime.commit(this);
            closed = true;
        }

        /** Completes all throwing work before the bounded final publication. */
        public void prepare() {
            ensureOpen();
            runtime.prepare(this);
        }

        /** Prepares the small journaled in-place publication path. */
        public void prepareInPlace() {
            ensureOpen();
            runtime.prepareInPlace(this);
        }

        /** Publishes a previously prepared point update without allocation or callbacks. */
        public void commitPrevalidated() {
            ensureOpen();
            runtime.commitPrevalidated(this);
            closed = true;
        }

        public void abort() {
            if (!closed) {
                closed = true;
                if (guard != null) {
                    runtime.abort(this);
                }
            }
        }

        private void ensureOpen() {
            if (!matched || closed || Thread.currentThread() != owner) {
                throw failure(SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                        SomaOperation.UPDATE,
                        "point Editor is outside its callback scope", null);
            }
        }
    }

    private static final class PreparedInPlace {
        private final StateRoot descriptor;
        private final Chunk chunk;
        private final int offset;
        private final long nextPayload;

        private PreparedInPlace(
                StateRoot descriptor,
                Chunk chunk,
                int offset,
                long nextPayload) {
            this.descriptor = descriptor;
            this.chunk = chunk;
            this.offset = offset;
            this.nextPayload = nextPayload;
        }
    }

    /** Borrowed query cursor bound to a StateRoot snapshot. */
    public static final class Query implements AutoCloseable {
        private final GroupRuntime.Guard guard;
        private final StateRoot root;
        private final Thread owner;
        private boolean closed;

        private Query(GroupRuntime.Guard guard, StateRoot root) {
            this.guard = guard;
            this.root = root;
            this.owner = Thread.currentThread();
        }

        public long size() {
            ensureOpen();
            return root.size;
        }

        public long keyAt(long locator) {
            ensureOpen();
            return root.directory.keyAt(locator);
        }

        public long findLocator(long key) {
            ensureOpen();
            return root.index.find(key);
        }

        public long payloadAt(long locator) {
            ensureOpen();
            return root.directory.payloadAt(locator);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                guard.close();
            }
        }

        private void ensureOpen() {
            if (closed || Thread.currentThread() != owner) {
                throw failure(SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                        SomaOperation.QUERY,
                        "query View is outside its callback scope", null);
            }
        }
    }

    private static final class StateRoot {
        private final long size;
        private final long capacity;
        private final long version;
        private final ChunkDirectory directory;
        private final LongKeyIndex index;

        private StateRoot(
                long size,
                long capacity,
                long version,
                ChunkDirectory directory,
                LongKeyIndex index) {
            this.size = size;
            this.capacity = capacity;
            this.version = version;
            this.directory = directory;
            this.index = index;
        }

        private static StateRoot empty(int chunkSize) {
            return new StateRoot(0L, 0L, 0L,
                    ChunkDirectory.empty(chunkSize), LongKeyIndex.empty());
        }
    }

    private static final class ChunkDirectory {
        private final DirectoryPage[] roots;
        private final int chunkSize;

        private ChunkDirectory(DirectoryPage[] roots, int chunkSize) {
            this.roots = roots;
            this.chunkSize = chunkSize;
        }

        private static ChunkDirectory empty(int chunkSize) {
            return new ChunkDirectory(new DirectoryPage[0], chunkSize);
        }

        private ChunkDirectory withCapacity(long capacity) {
            long requiredChunks = ceilDiv(capacity, chunkSize);
            long requiredPages = ceilDiv(requiredChunks, DIRECTORY_PAGE_SIZE);
            long requiredRoots = ceilDiv(requiredPages, DIRECTORY_ROOT_SIZE);
            if (requiredRoots > Integer.MAX_VALUE) {
                throw failure(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                        SomaOperation.RESERVE,
                        "chunk directory root exceeds Java addressable page count", null);
            }
            DirectoryPage[] nextRoots = new DirectoryPage[(int) requiredRoots];
            for (int rootIndex = 0; rootIndex < nextRoots.length; rootIndex++) {
                DirectoryPage oldRoot = rootIndex < roots.length ? roots[rootIndex] : null;
                long remaining = requiredPages - ((long) rootIndex * DIRECTORY_ROOT_SIZE);
                int rootPageCount = (int) Math.min(DIRECTORY_ROOT_SIZE, remaining);
                ChunkPage[] nextPages = new ChunkPage[rootPageCount];
                if (oldRoot != null) {
                    System.arraycopy(oldRoot.pages, 0, nextPages, 0,
                            Math.min(oldRoot.pages.length, nextPages.length));
                }
                for (int pageIndex = 0; pageIndex < nextPages.length; pageIndex++) {
                    if (nextPages[pageIndex] == null) {
                        long pageNumber = ((long) rootIndex * DIRECTORY_ROOT_SIZE) + pageIndex;
                        long pageRemaining = requiredChunks - (pageNumber * DIRECTORY_PAGE_SIZE);
                        int pageChunkCount = (int) Math.min(DIRECTORY_PAGE_SIZE, pageRemaining);
                        Chunk[] nextChunks = new Chunk[pageChunkCount];
                        for (int chunkIndex = 0; chunkIndex < nextChunks.length; chunkIndex++) {
                            nextChunks[chunkIndex] = new Chunk(chunkSize);
                        }
                        nextPages[pageIndex] = new ChunkPage(nextChunks);
                    }
                }
                nextRoots[rootIndex] = new DirectoryPage(nextPages);
            }
            return new ChunkDirectory(nextRoots, chunkSize);
        }

        private ChunkDirectory withRow(long locator, long key, long payload) {
            long chunkIndex = locator / chunkSize;
            int offset = checkedInt(locator % chunkSize);
            ChunkPage page = page(chunkIndex);
            Chunk nextChunk = page.chunks[(int) (chunkIndex % DIRECTORY_PAGE_SIZE)].copy();
            nextChunk.keys[offset] = key;
            nextChunk.payloads[offset] = payload;
            nextChunk.occupied[offset] = true;
            return replaceChunk(chunkIndex, nextChunk);
        }

        private ChunkDirectory withPayload(long locator, long payload) {
            long chunkIndex = locator / chunkSize;
            int offset = checkedInt(locator % chunkSize);
            ChunkPage page = page(chunkIndex);
            Chunk nextChunk = page.chunks[(int) (chunkIndex % DIRECTORY_PAGE_SIZE)].copy();
            nextChunk.payloads[offset] = payload;
            return replaceChunk(chunkIndex, nextChunk);
        }

        private ChunkDirectory replaceChunk(long chunkIndex, Chunk replacement) {
            long pageNumber = chunkIndex / DIRECTORY_PAGE_SIZE;
            int rootIndex = checkedInt(pageNumber / DIRECTORY_ROOT_SIZE);
            int pageIndex = (int) (pageNumber % DIRECTORY_ROOT_SIZE);
            DirectoryPage[] nextRoots = roots.clone();
            ChunkPage[] nextPages = roots[rootIndex].pages.clone();
            Chunk[] nextChunks = nextPages[pageIndex].chunks.clone();
            nextChunks[(int) (chunkIndex % DIRECTORY_PAGE_SIZE)] = replacement;
            nextPages[pageIndex] = new ChunkPage(nextChunks);
            nextRoots[rootIndex] = new DirectoryPage(nextPages);
            return new ChunkDirectory(nextRoots, chunkSize);
        }

        private long keyAt(long locator) {
            return chunk(locator).keys[offset(locator)];
        }

        private long payloadAt(long locator) {
            return chunk(locator).payloads[offset(locator)];
        }

        private Chunk chunk(long locator) {
            long chunkIndex = locator / chunkSize;
            if (chunkIndex < 0L) {
                throw failure(SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                        SomaOperation.QUERY, "row locator is outside StateRoot", null);
            }
            return page(chunkIndex).chunks[(int) (chunkIndex % DIRECTORY_PAGE_SIZE)];
        }

        private ChunkPage page(long chunkIndex) {
            long pageNumber = chunkIndex / DIRECTORY_PAGE_SIZE;
            int rootIndex = checkedInt(pageNumber / DIRECTORY_ROOT_SIZE);
            int pageIndex = (int) (pageNumber % DIRECTORY_ROOT_SIZE);
            if (rootIndex < 0 || rootIndex >= roots.length || roots[rootIndex] == null
                    || pageIndex >= roots[rootIndex].pages.length
                    || roots[rootIndex].pages[pageIndex] == null) {
                throw failure(SomaFailureCode.CALLBACK_SCOPE_VIOLATION,
                        SomaOperation.QUERY, "row locator is outside StateRoot", null);
            }
            return roots[rootIndex].pages[pageIndex];
        }

        private int offset(long locator) {
            return checkedInt(locator % chunkSize);
        }
    }

    private static final class ChunkPage {
        private final Chunk[] chunks;

        private ChunkPage(Chunk[] chunks) {
            this.chunks = chunks;
        }
    }

    private static final class DirectoryPage {
        private final ChunkPage[] pages;

        private DirectoryPage(ChunkPage[] pages) {
            this.pages = pages;
        }
    }

    private static final class Chunk {
        private final long[] keys;
        private final long[] payloads;
        private final boolean[] occupied;

        private Chunk(int chunkSize) {
            keys = new long[chunkSize];
            payloads = new long[chunkSize];
            occupied = new boolean[chunkSize];
        }

        private Chunk copy() {
            Chunk next = new Chunk(keys.length);
            System.arraycopy(keys, 0, next.keys, 0, keys.length);
            System.arraycopy(payloads, 0, next.payloads, 0, payloads.length);
            System.arraycopy(occupied, 0, next.occupied, 0, occupied.length);
            return next;
        }
    }

    private static final class LongKeyIndex {
        private final Shard[] shards;
        private final long size;

        private LongKeyIndex(Shard[] shards, long size) {
            this.shards = shards;
            this.size = size;
        }

        private static LongKeyIndex empty() {
            Shard[] shards = new Shard[INDEX_SHARD_COUNT];
            for (int index = 0; index < shards.length; index++) {
                shards[index] = Shard.empty();
            }
            return new LongKeyIndex(shards, 0L);
        }

        private long find(long key) {
            return shards[shardFor(key)].find(key);
        }

        private boolean contains(long key) {
            return find(key) >= 0L;
        }

        /** Copy-on-write is limited to one hash shard, not the complete index. */
        private LongKeyIndex copyForPut(long key, long locator) {
            long nextSize;
            try {
                nextSize = Math.addExact(size, 1L);
            } catch (ArithmeticException exception) {
                throw failure(SomaFailureCode.ARITHMETIC_OVERFLOW,
                        SomaOperation.ADD, "key index size overflow", exception);
            }
            int shardIndex = shardFor(key);
            Shard[] nextShards = shards.clone();
            nextShards[shardIndex] = shards[shardIndex].copyForPut(key, locator);
            return new LongKeyIndex(nextShards, nextSize);
        }

        private static int shardFor(long key) {
            return mix(key) & (INDEX_SHARD_COUNT - 1);
        }

        private static final class Shard {
            private final long[] keys;
            private final long[] locators;
            private final boolean[] occupied;
            private int size;

            private Shard(long[] keys, long[] locators, boolean[] occupied, int size) {
                this.keys = keys;
                this.locators = locators;
                this.occupied = occupied;
                this.size = size;
            }

            private static Shard empty() {
                return new Shard(
                        new long[INITIAL_SHARD_LENGTH],
                        new long[INITIAL_SHARD_LENGTH],
                        new boolean[INITIAL_SHARD_LENGTH],
                        0);
            }

            private long find(long key) {
                int mask = keys.length - 1;
                int slot = (mix(key) >>> 4) & mask;
                for (int probes = 0; probes < keys.length; probes++) {
                    if (!occupied[slot]) {
                        return -1L;
                    }
                    if (keys[slot] == key) {
                        return locators[slot];
                    }
                    slot = (slot + 1) & mask;
                }
                return -1L;
            }

            private Shard copyForPut(long key, long locator) {
                int requiredSize = size + 1;
                int length = keys.length;
                if (requiredSize * 2 >= length) {
                    if (length >= MAX_INDEX_LENGTH) {
                        throw failure(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                                SomaOperation.ADD, "key index shard exceeds addressable capacity", null);
                    }
                    length <<= 1;
                }
                Shard next = new Shard(
                        new long[length], new long[length], new boolean[length], 0);
                for (int index = 0; index < keys.length; index++) {
                    if (occupied[index]) {
                        next.putDirect(keys[index], locators[index]);
                    }
                }
                next.putDirect(key, locator);
                return next;
            }

            private void putDirect(long key, long locator) {
                int mask = keys.length - 1;
                int slot = (mix(key) >>> 4) & mask;
                while (occupied[slot]) {
                    slot = (slot + 1) & mask;
                }
                keys[slot] = key;
                locators[slot] = locator;
                occupied[slot] = true;
                size++;
            }
        }

        private static int mix(long value) {
            long mixed = value ^ (value >>> 33);
            mixed *= 0xff51afd7ed558ccdl;
            mixed ^= mixed >>> 33;
            mixed *= 0xc4ceb9fe1a85ec53l;
            mixed ^= mixed >>> 33;
            return (int) mixed;
        }
    }

    private static int checkedInt(long value) {
        if (value < 0L || value > Integer.MAX_VALUE) {
            throw failure(SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                    SomaOperation.RESERVE, "internal chunk index exceeds int range", null);
        }
        return (int) value;
    }
}
