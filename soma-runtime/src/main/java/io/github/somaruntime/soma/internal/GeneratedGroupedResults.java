package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.*;
import java.util.ArrayList;
import java.util.List;

/** Internal adapters over detached typed grouped columns. */
final class GeneratedGroupedResults {

    static final int KEY_BOOLEAN = 1;
    static final int KEY_BYTE = 2;
    static final int KEY_SHORT = 3;
    static final int KEY_CHAR = 4;
    static final int KEY_INT = 5;
    static final int KEY_LONG = 6;
    static final int KEY_REFERENCE = 7;
    static final int VALUE_INT = 1;
    static final int VALUE_LONG = 2;
    static final int VALUE_DOUBLE = 3;
    static final int VALUE_LONGSUMMARY = 4;
    static final int VALUE_DOUBLESUMMARY = 5;

    private GeneratedGroupedResults() {}

    static Object create(int keyKind, int valueKind, Object keys, Object values, int size) {
        switch (keyKind) {
            case KEY_BOOLEAN: return createBoolean(valueKind, (boolean[]) keys, values, size);
            case KEY_BYTE: return createByte(valueKind, (byte[]) keys, values, size);
            case KEY_SHORT: return createShort(valueKind, (short[]) keys, values, size);
            case KEY_CHAR: return createChar(valueKind, (char[]) keys, values, size);
            case KEY_INT: return createInt(valueKind, (int[]) keys, values, size);
            case KEY_LONG: return createLong(valueKind, (long[]) keys, values, size);
            case KEY_REFERENCE: return createReference(valueKind, (Object[]) keys, values, size);
            default: throw new AssertionError("unknown grouped key kind");
        }
    }

    private static Object createBoolean(int valueKind, boolean[] keys, Object values, int size) {
        switch (valueKind) {
            case VALUE_INT: return new BooleanInt(keys, (int[]) values, size);
            case VALUE_LONG: return new BooleanLong(keys, (long[]) values, size);
            case VALUE_DOUBLE: return new BooleanDouble(keys, (double[]) values, size);
            case VALUE_LONGSUMMARY: return new BooleanLongSummary(keys, (SomaLongSummary[]) values, size);
            case VALUE_DOUBLESUMMARY: return new BooleanDoubleSummary(keys, (SomaDoubleSummary[]) values, size);
            default: throw new AssertionError("unknown grouped value kind");
        }
    }

    private static Object createByte(int valueKind, byte[] keys, Object values, int size) {
        switch (valueKind) {
            case VALUE_INT: return new ByteInt(keys, (int[]) values, size);
            case VALUE_LONG: return new ByteLong(keys, (long[]) values, size);
            case VALUE_DOUBLE: return new ByteDouble(keys, (double[]) values, size);
            case VALUE_LONGSUMMARY: return new ByteLongSummary(keys, (SomaLongSummary[]) values, size);
            case VALUE_DOUBLESUMMARY: return new ByteDoubleSummary(keys, (SomaDoubleSummary[]) values, size);
            default: throw new AssertionError("unknown grouped value kind");
        }
    }

    private static Object createShort(int valueKind, short[] keys, Object values, int size) {
        switch (valueKind) {
            case VALUE_INT: return new ShortInt(keys, (int[]) values, size);
            case VALUE_LONG: return new ShortLong(keys, (long[]) values, size);
            case VALUE_DOUBLE: return new ShortDouble(keys, (double[]) values, size);
            case VALUE_LONGSUMMARY: return new ShortLongSummary(keys, (SomaLongSummary[]) values, size);
            case VALUE_DOUBLESUMMARY: return new ShortDoubleSummary(keys, (SomaDoubleSummary[]) values, size);
            default: throw new AssertionError("unknown grouped value kind");
        }
    }

    private static Object createChar(int valueKind, char[] keys, Object values, int size) {
        switch (valueKind) {
            case VALUE_INT: return new CharInt(keys, (int[]) values, size);
            case VALUE_LONG: return new CharLong(keys, (long[]) values, size);
            case VALUE_DOUBLE: return new CharDouble(keys, (double[]) values, size);
            case VALUE_LONGSUMMARY: return new CharLongSummary(keys, (SomaLongSummary[]) values, size);
            case VALUE_DOUBLESUMMARY: return new CharDoubleSummary(keys, (SomaDoubleSummary[]) values, size);
            default: throw new AssertionError("unknown grouped value kind");
        }
    }

    private static Object createInt(int valueKind, int[] keys, Object values, int size) {
        switch (valueKind) {
            case VALUE_INT: return new IntInt(keys, (int[]) values, size);
            case VALUE_LONG: return new IntLong(keys, (long[]) values, size);
            case VALUE_DOUBLE: return new IntDouble(keys, (double[]) values, size);
            case VALUE_LONGSUMMARY: return new IntLongSummary(keys, (SomaLongSummary[]) values, size);
            case VALUE_DOUBLESUMMARY: return new IntDoubleSummary(keys, (SomaDoubleSummary[]) values, size);
            default: throw new AssertionError("unknown grouped value kind");
        }
    }

    private static Object createLong(int valueKind, long[] keys, Object values, int size) {
        switch (valueKind) {
            case VALUE_INT: return new LongInt(keys, (int[]) values, size);
            case VALUE_LONG: return new LongLong(keys, (long[]) values, size);
            case VALUE_DOUBLE: return new LongDouble(keys, (double[]) values, size);
            case VALUE_LONGSUMMARY: return new LongLongSummary(keys, (SomaLongSummary[]) values, size);
            case VALUE_DOUBLESUMMARY: return new LongDoubleSummary(keys, (SomaDoubleSummary[]) values, size);
            default: throw new AssertionError("unknown grouped value kind");
        }
    }

    private static Object createReference(int valueKind, Object[] keys, Object values, int size) {
        switch (valueKind) {
            case VALUE_INT: return new ReferenceInt<Object>(keys, (int[]) values, size);
            case VALUE_LONG: return new ReferenceLong<Object>(keys, (long[]) values, size);
            case VALUE_DOUBLE: return new ReferenceDouble<Object>(keys, (double[]) values, size);
            case VALUE_LONGSUMMARY: return new ReferenceLongSummary<Object>(keys, (SomaLongSummary[]) values, size);
            case VALUE_DOUBLESUMMARY: return new ReferenceDoubleSummary<Object>(keys, (SomaDoubleSummary[]) values, size);
            default: throw new AssertionError("unknown grouped value kind");
        }
    }

    private static final class ReferenceInt<K> implements GroupedIntResult<K> {
        private final Object[] keys; private final int[] values; private final int size;
        private ReferenceInt(Object[] keys, int[] values, int size) { this.keys = keys; this.values = values; this.size = size; }
        @Override public long size() { return size; }
        @Override @SuppressWarnings("unchecked") public void forEach(java.util.function.ObjIntConsumer<? super K> consumer) { require(consumer); for (int i=0;i<size;i++) consumer.accept((K) keys[i], values[i]); }
        @Override public List<GroupedIntEntry<K>> toList() { ArrayList<GroupedIntEntry<K>> r=new ArrayList<GroupedIntEntry<K>>(size); for(int i=0;i<size;i++) r.add(entry(i)); return r; }
        @Override @SuppressWarnings("unchecked") public GroupedIntEntry<K>[] toArray() { GroupedIntEntry<K>[] r=(GroupedIntEntry<K>[]) new GroupedIntEntry<?>[size]; for(int i=0;i<size;i++) r[i]=entry(i); return r; }
        @SuppressWarnings("unchecked") private GroupedIntEntry<K> entry(int i) { return new ReferenceIntEntry<K>((K) keys[i], values[i]); }
    }
    private static final class ReferenceIntEntry<K> implements GroupedIntEntry<K> { private final K key; private final int value; private ReferenceIntEntry(K key, int value){this.key=key;this.value=value;} public K key(){return key;} public int value(){return value;} }

    private static final class ReferenceLong<K> implements GroupedLongResult<K> {
        private final Object[] keys; private final long[] values; private final int size;
        private ReferenceLong(Object[] keys, long[] values, int size) { this.keys = keys; this.values = values; this.size = size; }
        @Override public long size() { return size; }
        @Override @SuppressWarnings("unchecked") public void forEach(java.util.function.ObjLongConsumer<? super K> consumer) { require(consumer); for (int i=0;i<size;i++) consumer.accept((K) keys[i], values[i]); }
        @Override public List<GroupedLongEntry<K>> toList() { ArrayList<GroupedLongEntry<K>> r=new ArrayList<GroupedLongEntry<K>>(size); for(int i=0;i<size;i++) r.add(entry(i)); return r; }
        @Override @SuppressWarnings("unchecked") public GroupedLongEntry<K>[] toArray() { GroupedLongEntry<K>[] r=(GroupedLongEntry<K>[]) new GroupedLongEntry<?>[size]; for(int i=0;i<size;i++) r[i]=entry(i); return r; }
        @SuppressWarnings("unchecked") private GroupedLongEntry<K> entry(int i) { return new ReferenceLongEntry<K>((K) keys[i], values[i]); }
    }
    private static final class ReferenceLongEntry<K> implements GroupedLongEntry<K> { private final K key; private final long value; private ReferenceLongEntry(K key, long value){this.key=key;this.value=value;} public K key(){return key;} public long value(){return value;} }

    private static final class ReferenceDouble<K> implements GroupedDoubleResult<K> {
        private final Object[] keys; private final double[] values; private final int size;
        private ReferenceDouble(Object[] keys, double[] values, int size) { this.keys = keys; this.values = values; this.size = size; }
        @Override public long size() { return size; }
        @Override @SuppressWarnings("unchecked") public void forEach(java.util.function.ObjDoubleConsumer<? super K> consumer) { require(consumer); for (int i=0;i<size;i++) consumer.accept((K) keys[i], values[i]); }
        @Override public List<GroupedDoubleEntry<K>> toList() { ArrayList<GroupedDoubleEntry<K>> r=new ArrayList<GroupedDoubleEntry<K>>(size); for(int i=0;i<size;i++) r.add(entry(i)); return r; }
        @Override @SuppressWarnings("unchecked") public GroupedDoubleEntry<K>[] toArray() { GroupedDoubleEntry<K>[] r=(GroupedDoubleEntry<K>[]) new GroupedDoubleEntry<?>[size]; for(int i=0;i<size;i++) r[i]=entry(i); return r; }
        @SuppressWarnings("unchecked") private GroupedDoubleEntry<K> entry(int i) { return new ReferenceDoubleEntry<K>((K) keys[i], values[i]); }
    }
    private static final class ReferenceDoubleEntry<K> implements GroupedDoubleEntry<K> { private final K key; private final double value; private ReferenceDoubleEntry(K key, double value){this.key=key;this.value=value;} public K key(){return key;} public double value(){return value;} }

    private static final class ReferenceLongSummary<K> implements GroupedLongSummaryResult<K> {
        private final Object[] keys; private final SomaLongSummary[] values; private final int size;
        private ReferenceLongSummary(Object[] keys, SomaLongSummary[] values, int size) { this.keys = keys; this.values = values; this.size = size; }
        @Override public long size() { return size; }
        @Override @SuppressWarnings("unchecked") public void forEach(SomaObjLongSummaryConsumer<? super K> consumer) { require(consumer); for (int i=0;i<size;i++) consumer.accept((K) keys[i], values[i]); }
        @Override public List<GroupedLongSummaryEntry<K>> toList() { ArrayList<GroupedLongSummaryEntry<K>> r=new ArrayList<GroupedLongSummaryEntry<K>>(size); for(int i=0;i<size;i++) r.add(entry(i)); return r; }
        @Override @SuppressWarnings("unchecked") public GroupedLongSummaryEntry<K>[] toArray() { GroupedLongSummaryEntry<K>[] r=(GroupedLongSummaryEntry<K>[]) new GroupedLongSummaryEntry<?>[size]; for(int i=0;i<size;i++) r[i]=entry(i); return r; }
        @SuppressWarnings("unchecked") private GroupedLongSummaryEntry<K> entry(int i) { return new ReferenceLongSummaryEntry<K>((K) keys[i], values[i]); }
    }
    private static final class ReferenceLongSummaryEntry<K> implements GroupedLongSummaryEntry<K> { private final K key; private final SomaLongSummary value; private ReferenceLongSummaryEntry(K key, SomaLongSummary value){this.key=key;this.value=value;} public K key(){return key;} public SomaLongSummary value(){return value;} }

    private static final class ReferenceDoubleSummary<K> implements GroupedDoubleSummaryResult<K> {
        private final Object[] keys; private final SomaDoubleSummary[] values; private final int size;
        private ReferenceDoubleSummary(Object[] keys, SomaDoubleSummary[] values, int size) { this.keys = keys; this.values = values; this.size = size; }
        @Override public long size() { return size; }
        @Override @SuppressWarnings("unchecked") public void forEach(SomaObjDoubleSummaryConsumer<? super K> consumer) { require(consumer); for (int i=0;i<size;i++) consumer.accept((K) keys[i], values[i]); }
        @Override public List<GroupedDoubleSummaryEntry<K>> toList() { ArrayList<GroupedDoubleSummaryEntry<K>> r=new ArrayList<GroupedDoubleSummaryEntry<K>>(size); for(int i=0;i<size;i++) r.add(entry(i)); return r; }
        @Override @SuppressWarnings("unchecked") public GroupedDoubleSummaryEntry<K>[] toArray() { GroupedDoubleSummaryEntry<K>[] r=(GroupedDoubleSummaryEntry<K>[]) new GroupedDoubleSummaryEntry<?>[size]; for(int i=0;i<size;i++) r[i]=entry(i); return r; }
        @SuppressWarnings("unchecked") private GroupedDoubleSummaryEntry<K> entry(int i) { return new ReferenceDoubleSummaryEntry<K>((K) keys[i], values[i]); }
    }
    private static final class ReferenceDoubleSummaryEntry<K> implements GroupedDoubleSummaryEntry<K> { private final K key; private final SomaDoubleSummary value; private ReferenceDoubleSummaryEntry(K key, SomaDoubleSummary value){this.key=key;this.value=value;} public K key(){return key;} public SomaDoubleSummary value(){return value;} }

    private static final class BooleanInt implements BooleanGroupedIntResult {
        private final boolean[] keys; private final int[] values; private final int size;
        private BooleanInt(boolean[] keys, int[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaBooleanIntConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<BooleanGroupedIntEntry> toList(){ArrayList<BooleanGroupedIntEntry> r=new ArrayList<BooleanGroupedIntEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public BooleanGroupedIntEntry[] toArray(){BooleanGroupedIntEntry[] r=new BooleanGroupedIntEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private BooleanGroupedIntEntry entry(int i){return new BooleanIntEntry(keys[i],values[i]);}
    }
    private static final class BooleanIntEntry implements BooleanGroupedIntEntry { private final boolean key; private final int value; private BooleanIntEntry(boolean key,int value){this.key=key;this.value=value;} public boolean key(){return key;} public int value(){return value;} }

    private static final class BooleanLong implements BooleanGroupedLongResult {
        private final boolean[] keys; private final long[] values; private final int size;
        private BooleanLong(boolean[] keys, long[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaBooleanLongConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<BooleanGroupedLongEntry> toList(){ArrayList<BooleanGroupedLongEntry> r=new ArrayList<BooleanGroupedLongEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public BooleanGroupedLongEntry[] toArray(){BooleanGroupedLongEntry[] r=new BooleanGroupedLongEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private BooleanGroupedLongEntry entry(int i){return new BooleanLongEntry(keys[i],values[i]);}
    }
    private static final class BooleanLongEntry implements BooleanGroupedLongEntry { private final boolean key; private final long value; private BooleanLongEntry(boolean key,long value){this.key=key;this.value=value;} public boolean key(){return key;} public long value(){return value;} }

    private static final class BooleanDouble implements BooleanGroupedDoubleResult {
        private final boolean[] keys; private final double[] values; private final int size;
        private BooleanDouble(boolean[] keys, double[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaBooleanDoubleConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<BooleanGroupedDoubleEntry> toList(){ArrayList<BooleanGroupedDoubleEntry> r=new ArrayList<BooleanGroupedDoubleEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public BooleanGroupedDoubleEntry[] toArray(){BooleanGroupedDoubleEntry[] r=new BooleanGroupedDoubleEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private BooleanGroupedDoubleEntry entry(int i){return new BooleanDoubleEntry(keys[i],values[i]);}
    }
    private static final class BooleanDoubleEntry implements BooleanGroupedDoubleEntry { private final boolean key; private final double value; private BooleanDoubleEntry(boolean key,double value){this.key=key;this.value=value;} public boolean key(){return key;} public double value(){return value;} }

    private static final class BooleanLongSummary implements BooleanGroupedLongSummaryResult {
        private final boolean[] keys; private final SomaLongSummary[] values; private final int size;
        private BooleanLongSummary(boolean[] keys, SomaLongSummary[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaBooleanLongSummaryConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<BooleanGroupedLongSummaryEntry> toList(){ArrayList<BooleanGroupedLongSummaryEntry> r=new ArrayList<BooleanGroupedLongSummaryEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public BooleanGroupedLongSummaryEntry[] toArray(){BooleanGroupedLongSummaryEntry[] r=new BooleanGroupedLongSummaryEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private BooleanGroupedLongSummaryEntry entry(int i){return new BooleanLongSummaryEntry(keys[i],values[i]);}
    }
    private static final class BooleanLongSummaryEntry implements BooleanGroupedLongSummaryEntry { private final boolean key; private final SomaLongSummary value; private BooleanLongSummaryEntry(boolean key,SomaLongSummary value){this.key=key;this.value=value;} public boolean key(){return key;} public SomaLongSummary value(){return value;} }

    private static final class BooleanDoubleSummary implements BooleanGroupedDoubleSummaryResult {
        private final boolean[] keys; private final SomaDoubleSummary[] values; private final int size;
        private BooleanDoubleSummary(boolean[] keys, SomaDoubleSummary[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaBooleanDoubleSummaryConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<BooleanGroupedDoubleSummaryEntry> toList(){ArrayList<BooleanGroupedDoubleSummaryEntry> r=new ArrayList<BooleanGroupedDoubleSummaryEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public BooleanGroupedDoubleSummaryEntry[] toArray(){BooleanGroupedDoubleSummaryEntry[] r=new BooleanGroupedDoubleSummaryEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private BooleanGroupedDoubleSummaryEntry entry(int i){return new BooleanDoubleSummaryEntry(keys[i],values[i]);}
    }
    private static final class BooleanDoubleSummaryEntry implements BooleanGroupedDoubleSummaryEntry { private final boolean key; private final SomaDoubleSummary value; private BooleanDoubleSummaryEntry(boolean key,SomaDoubleSummary value){this.key=key;this.value=value;} public boolean key(){return key;} public SomaDoubleSummary value(){return value;} }

    private static final class ByteInt implements ByteGroupedIntResult {
        private final byte[] keys; private final int[] values; private final int size;
        private ByteInt(byte[] keys, int[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaByteIntConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<ByteGroupedIntEntry> toList(){ArrayList<ByteGroupedIntEntry> r=new ArrayList<ByteGroupedIntEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public ByteGroupedIntEntry[] toArray(){ByteGroupedIntEntry[] r=new ByteGroupedIntEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private ByteGroupedIntEntry entry(int i){return new ByteIntEntry(keys[i],values[i]);}
    }
    private static final class ByteIntEntry implements ByteGroupedIntEntry { private final byte key; private final int value; private ByteIntEntry(byte key,int value){this.key=key;this.value=value;} public byte key(){return key;} public int value(){return value;} }

    private static final class ByteLong implements ByteGroupedLongResult {
        private final byte[] keys; private final long[] values; private final int size;
        private ByteLong(byte[] keys, long[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaByteLongConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<ByteGroupedLongEntry> toList(){ArrayList<ByteGroupedLongEntry> r=new ArrayList<ByteGroupedLongEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public ByteGroupedLongEntry[] toArray(){ByteGroupedLongEntry[] r=new ByteGroupedLongEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private ByteGroupedLongEntry entry(int i){return new ByteLongEntry(keys[i],values[i]);}
    }
    private static final class ByteLongEntry implements ByteGroupedLongEntry { private final byte key; private final long value; private ByteLongEntry(byte key,long value){this.key=key;this.value=value;} public byte key(){return key;} public long value(){return value;} }

    private static final class ByteDouble implements ByteGroupedDoubleResult {
        private final byte[] keys; private final double[] values; private final int size;
        private ByteDouble(byte[] keys, double[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaByteDoubleConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<ByteGroupedDoubleEntry> toList(){ArrayList<ByteGroupedDoubleEntry> r=new ArrayList<ByteGroupedDoubleEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public ByteGroupedDoubleEntry[] toArray(){ByteGroupedDoubleEntry[] r=new ByteGroupedDoubleEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private ByteGroupedDoubleEntry entry(int i){return new ByteDoubleEntry(keys[i],values[i]);}
    }
    private static final class ByteDoubleEntry implements ByteGroupedDoubleEntry { private final byte key; private final double value; private ByteDoubleEntry(byte key,double value){this.key=key;this.value=value;} public byte key(){return key;} public double value(){return value;} }

    private static final class ByteLongSummary implements ByteGroupedLongSummaryResult {
        private final byte[] keys; private final SomaLongSummary[] values; private final int size;
        private ByteLongSummary(byte[] keys, SomaLongSummary[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaByteLongSummaryConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<ByteGroupedLongSummaryEntry> toList(){ArrayList<ByteGroupedLongSummaryEntry> r=new ArrayList<ByteGroupedLongSummaryEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public ByteGroupedLongSummaryEntry[] toArray(){ByteGroupedLongSummaryEntry[] r=new ByteGroupedLongSummaryEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private ByteGroupedLongSummaryEntry entry(int i){return new ByteLongSummaryEntry(keys[i],values[i]);}
    }
    private static final class ByteLongSummaryEntry implements ByteGroupedLongSummaryEntry { private final byte key; private final SomaLongSummary value; private ByteLongSummaryEntry(byte key,SomaLongSummary value){this.key=key;this.value=value;} public byte key(){return key;} public SomaLongSummary value(){return value;} }

    private static final class ByteDoubleSummary implements ByteGroupedDoubleSummaryResult {
        private final byte[] keys; private final SomaDoubleSummary[] values; private final int size;
        private ByteDoubleSummary(byte[] keys, SomaDoubleSummary[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaByteDoubleSummaryConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<ByteGroupedDoubleSummaryEntry> toList(){ArrayList<ByteGroupedDoubleSummaryEntry> r=new ArrayList<ByteGroupedDoubleSummaryEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public ByteGroupedDoubleSummaryEntry[] toArray(){ByteGroupedDoubleSummaryEntry[] r=new ByteGroupedDoubleSummaryEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private ByteGroupedDoubleSummaryEntry entry(int i){return new ByteDoubleSummaryEntry(keys[i],values[i]);}
    }
    private static final class ByteDoubleSummaryEntry implements ByteGroupedDoubleSummaryEntry { private final byte key; private final SomaDoubleSummary value; private ByteDoubleSummaryEntry(byte key,SomaDoubleSummary value){this.key=key;this.value=value;} public byte key(){return key;} public SomaDoubleSummary value(){return value;} }

    private static final class ShortInt implements ShortGroupedIntResult {
        private final short[] keys; private final int[] values; private final int size;
        private ShortInt(short[] keys, int[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaShortIntConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<ShortGroupedIntEntry> toList(){ArrayList<ShortGroupedIntEntry> r=new ArrayList<ShortGroupedIntEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public ShortGroupedIntEntry[] toArray(){ShortGroupedIntEntry[] r=new ShortGroupedIntEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private ShortGroupedIntEntry entry(int i){return new ShortIntEntry(keys[i],values[i]);}
    }
    private static final class ShortIntEntry implements ShortGroupedIntEntry { private final short key; private final int value; private ShortIntEntry(short key,int value){this.key=key;this.value=value;} public short key(){return key;} public int value(){return value;} }

    private static final class ShortLong implements ShortGroupedLongResult {
        private final short[] keys; private final long[] values; private final int size;
        private ShortLong(short[] keys, long[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaShortLongConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<ShortGroupedLongEntry> toList(){ArrayList<ShortGroupedLongEntry> r=new ArrayList<ShortGroupedLongEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public ShortGroupedLongEntry[] toArray(){ShortGroupedLongEntry[] r=new ShortGroupedLongEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private ShortGroupedLongEntry entry(int i){return new ShortLongEntry(keys[i],values[i]);}
    }
    private static final class ShortLongEntry implements ShortGroupedLongEntry { private final short key; private final long value; private ShortLongEntry(short key,long value){this.key=key;this.value=value;} public short key(){return key;} public long value(){return value;} }

    private static final class ShortDouble implements ShortGroupedDoubleResult {
        private final short[] keys; private final double[] values; private final int size;
        private ShortDouble(short[] keys, double[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaShortDoubleConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<ShortGroupedDoubleEntry> toList(){ArrayList<ShortGroupedDoubleEntry> r=new ArrayList<ShortGroupedDoubleEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public ShortGroupedDoubleEntry[] toArray(){ShortGroupedDoubleEntry[] r=new ShortGroupedDoubleEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private ShortGroupedDoubleEntry entry(int i){return new ShortDoubleEntry(keys[i],values[i]);}
    }
    private static final class ShortDoubleEntry implements ShortGroupedDoubleEntry { private final short key; private final double value; private ShortDoubleEntry(short key,double value){this.key=key;this.value=value;} public short key(){return key;} public double value(){return value;} }

    private static final class ShortLongSummary implements ShortGroupedLongSummaryResult {
        private final short[] keys; private final SomaLongSummary[] values; private final int size;
        private ShortLongSummary(short[] keys, SomaLongSummary[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaShortLongSummaryConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<ShortGroupedLongSummaryEntry> toList(){ArrayList<ShortGroupedLongSummaryEntry> r=new ArrayList<ShortGroupedLongSummaryEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public ShortGroupedLongSummaryEntry[] toArray(){ShortGroupedLongSummaryEntry[] r=new ShortGroupedLongSummaryEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private ShortGroupedLongSummaryEntry entry(int i){return new ShortLongSummaryEntry(keys[i],values[i]);}
    }
    private static final class ShortLongSummaryEntry implements ShortGroupedLongSummaryEntry { private final short key; private final SomaLongSummary value; private ShortLongSummaryEntry(short key,SomaLongSummary value){this.key=key;this.value=value;} public short key(){return key;} public SomaLongSummary value(){return value;} }

    private static final class ShortDoubleSummary implements ShortGroupedDoubleSummaryResult {
        private final short[] keys; private final SomaDoubleSummary[] values; private final int size;
        private ShortDoubleSummary(short[] keys, SomaDoubleSummary[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaShortDoubleSummaryConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<ShortGroupedDoubleSummaryEntry> toList(){ArrayList<ShortGroupedDoubleSummaryEntry> r=new ArrayList<ShortGroupedDoubleSummaryEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public ShortGroupedDoubleSummaryEntry[] toArray(){ShortGroupedDoubleSummaryEntry[] r=new ShortGroupedDoubleSummaryEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private ShortGroupedDoubleSummaryEntry entry(int i){return new ShortDoubleSummaryEntry(keys[i],values[i]);}
    }
    private static final class ShortDoubleSummaryEntry implements ShortGroupedDoubleSummaryEntry { private final short key; private final SomaDoubleSummary value; private ShortDoubleSummaryEntry(short key,SomaDoubleSummary value){this.key=key;this.value=value;} public short key(){return key;} public SomaDoubleSummary value(){return value;} }

    private static final class CharInt implements CharGroupedIntResult {
        private final char[] keys; private final int[] values; private final int size;
        private CharInt(char[] keys, int[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaCharIntConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<CharGroupedIntEntry> toList(){ArrayList<CharGroupedIntEntry> r=new ArrayList<CharGroupedIntEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public CharGroupedIntEntry[] toArray(){CharGroupedIntEntry[] r=new CharGroupedIntEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private CharGroupedIntEntry entry(int i){return new CharIntEntry(keys[i],values[i]);}
    }
    private static final class CharIntEntry implements CharGroupedIntEntry { private final char key; private final int value; private CharIntEntry(char key,int value){this.key=key;this.value=value;} public char key(){return key;} public int value(){return value;} }

    private static final class CharLong implements CharGroupedLongResult {
        private final char[] keys; private final long[] values; private final int size;
        private CharLong(char[] keys, long[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaCharLongConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<CharGroupedLongEntry> toList(){ArrayList<CharGroupedLongEntry> r=new ArrayList<CharGroupedLongEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public CharGroupedLongEntry[] toArray(){CharGroupedLongEntry[] r=new CharGroupedLongEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private CharGroupedLongEntry entry(int i){return new CharLongEntry(keys[i],values[i]);}
    }
    private static final class CharLongEntry implements CharGroupedLongEntry { private final char key; private final long value; private CharLongEntry(char key,long value){this.key=key;this.value=value;} public char key(){return key;} public long value(){return value;} }

    private static final class CharDouble implements CharGroupedDoubleResult {
        private final char[] keys; private final double[] values; private final int size;
        private CharDouble(char[] keys, double[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaCharDoubleConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<CharGroupedDoubleEntry> toList(){ArrayList<CharGroupedDoubleEntry> r=new ArrayList<CharGroupedDoubleEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public CharGroupedDoubleEntry[] toArray(){CharGroupedDoubleEntry[] r=new CharGroupedDoubleEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private CharGroupedDoubleEntry entry(int i){return new CharDoubleEntry(keys[i],values[i]);}
    }
    private static final class CharDoubleEntry implements CharGroupedDoubleEntry { private final char key; private final double value; private CharDoubleEntry(char key,double value){this.key=key;this.value=value;} public char key(){return key;} public double value(){return value;} }

    private static final class CharLongSummary implements CharGroupedLongSummaryResult {
        private final char[] keys; private final SomaLongSummary[] values; private final int size;
        private CharLongSummary(char[] keys, SomaLongSummary[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaCharLongSummaryConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<CharGroupedLongSummaryEntry> toList(){ArrayList<CharGroupedLongSummaryEntry> r=new ArrayList<CharGroupedLongSummaryEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public CharGroupedLongSummaryEntry[] toArray(){CharGroupedLongSummaryEntry[] r=new CharGroupedLongSummaryEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private CharGroupedLongSummaryEntry entry(int i){return new CharLongSummaryEntry(keys[i],values[i]);}
    }
    private static final class CharLongSummaryEntry implements CharGroupedLongSummaryEntry { private final char key; private final SomaLongSummary value; private CharLongSummaryEntry(char key,SomaLongSummary value){this.key=key;this.value=value;} public char key(){return key;} public SomaLongSummary value(){return value;} }

    private static final class CharDoubleSummary implements CharGroupedDoubleSummaryResult {
        private final char[] keys; private final SomaDoubleSummary[] values; private final int size;
        private CharDoubleSummary(char[] keys, SomaDoubleSummary[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaCharDoubleSummaryConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<CharGroupedDoubleSummaryEntry> toList(){ArrayList<CharGroupedDoubleSummaryEntry> r=new ArrayList<CharGroupedDoubleSummaryEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public CharGroupedDoubleSummaryEntry[] toArray(){CharGroupedDoubleSummaryEntry[] r=new CharGroupedDoubleSummaryEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private CharGroupedDoubleSummaryEntry entry(int i){return new CharDoubleSummaryEntry(keys[i],values[i]);}
    }
    private static final class CharDoubleSummaryEntry implements CharGroupedDoubleSummaryEntry { private final char key; private final SomaDoubleSummary value; private CharDoubleSummaryEntry(char key,SomaDoubleSummary value){this.key=key;this.value=value;} public char key(){return key;} public SomaDoubleSummary value(){return value;} }

    private static final class IntInt implements IntGroupedIntResult {
        private final int[] keys; private final int[] values; private final int size;
        private IntInt(int[] keys, int[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaIntIntConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<IntGroupedIntEntry> toList(){ArrayList<IntGroupedIntEntry> r=new ArrayList<IntGroupedIntEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public IntGroupedIntEntry[] toArray(){IntGroupedIntEntry[] r=new IntGroupedIntEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private IntGroupedIntEntry entry(int i){return new IntIntEntry(keys[i],values[i]);}
    }
    private static final class IntIntEntry implements IntGroupedIntEntry { private final int key; private final int value; private IntIntEntry(int key,int value){this.key=key;this.value=value;} public int key(){return key;} public int value(){return value;} }

    private static final class IntLong implements IntGroupedLongResult {
        private final int[] keys; private final long[] values; private final int size;
        private IntLong(int[] keys, long[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaIntLongConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<IntGroupedLongEntry> toList(){ArrayList<IntGroupedLongEntry> r=new ArrayList<IntGroupedLongEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public IntGroupedLongEntry[] toArray(){IntGroupedLongEntry[] r=new IntGroupedLongEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private IntGroupedLongEntry entry(int i){return new IntLongEntry(keys[i],values[i]);}
    }
    private static final class IntLongEntry implements IntGroupedLongEntry { private final int key; private final long value; private IntLongEntry(int key,long value){this.key=key;this.value=value;} public int key(){return key;} public long value(){return value;} }

    private static final class IntDouble implements IntGroupedDoubleResult {
        private final int[] keys; private final double[] values; private final int size;
        private IntDouble(int[] keys, double[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaIntDoubleConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<IntGroupedDoubleEntry> toList(){ArrayList<IntGroupedDoubleEntry> r=new ArrayList<IntGroupedDoubleEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public IntGroupedDoubleEntry[] toArray(){IntGroupedDoubleEntry[] r=new IntGroupedDoubleEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private IntGroupedDoubleEntry entry(int i){return new IntDoubleEntry(keys[i],values[i]);}
    }
    private static final class IntDoubleEntry implements IntGroupedDoubleEntry { private final int key; private final double value; private IntDoubleEntry(int key,double value){this.key=key;this.value=value;} public int key(){return key;} public double value(){return value;} }

    private static final class IntLongSummary implements IntGroupedLongSummaryResult {
        private final int[] keys; private final SomaLongSummary[] values; private final int size;
        private IntLongSummary(int[] keys, SomaLongSummary[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaIntLongSummaryConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<IntGroupedLongSummaryEntry> toList(){ArrayList<IntGroupedLongSummaryEntry> r=new ArrayList<IntGroupedLongSummaryEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public IntGroupedLongSummaryEntry[] toArray(){IntGroupedLongSummaryEntry[] r=new IntGroupedLongSummaryEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private IntGroupedLongSummaryEntry entry(int i){return new IntLongSummaryEntry(keys[i],values[i]);}
    }
    private static final class IntLongSummaryEntry implements IntGroupedLongSummaryEntry { private final int key; private final SomaLongSummary value; private IntLongSummaryEntry(int key,SomaLongSummary value){this.key=key;this.value=value;} public int key(){return key;} public SomaLongSummary value(){return value;} }

    private static final class IntDoubleSummary implements IntGroupedDoubleSummaryResult {
        private final int[] keys; private final SomaDoubleSummary[] values; private final int size;
        private IntDoubleSummary(int[] keys, SomaDoubleSummary[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaIntDoubleSummaryConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<IntGroupedDoubleSummaryEntry> toList(){ArrayList<IntGroupedDoubleSummaryEntry> r=new ArrayList<IntGroupedDoubleSummaryEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public IntGroupedDoubleSummaryEntry[] toArray(){IntGroupedDoubleSummaryEntry[] r=new IntGroupedDoubleSummaryEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private IntGroupedDoubleSummaryEntry entry(int i){return new IntDoubleSummaryEntry(keys[i],values[i]);}
    }
    private static final class IntDoubleSummaryEntry implements IntGroupedDoubleSummaryEntry { private final int key; private final SomaDoubleSummary value; private IntDoubleSummaryEntry(int key,SomaDoubleSummary value){this.key=key;this.value=value;} public int key(){return key;} public SomaDoubleSummary value(){return value;} }

    private static final class LongInt implements LongGroupedIntResult {
        private final long[] keys; private final int[] values; private final int size;
        private LongInt(long[] keys, int[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaLongIntConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<LongGroupedIntEntry> toList(){ArrayList<LongGroupedIntEntry> r=new ArrayList<LongGroupedIntEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public LongGroupedIntEntry[] toArray(){LongGroupedIntEntry[] r=new LongGroupedIntEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private LongGroupedIntEntry entry(int i){return new LongIntEntry(keys[i],values[i]);}
    }
    private static final class LongIntEntry implements LongGroupedIntEntry { private final long key; private final int value; private LongIntEntry(long key,int value){this.key=key;this.value=value;} public long key(){return key;} public int value(){return value;} }

    private static final class LongLong implements LongGroupedLongResult {
        private final long[] keys; private final long[] values; private final int size;
        private LongLong(long[] keys, long[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaLongLongConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<LongGroupedLongEntry> toList(){ArrayList<LongGroupedLongEntry> r=new ArrayList<LongGroupedLongEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public LongGroupedLongEntry[] toArray(){LongGroupedLongEntry[] r=new LongGroupedLongEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private LongGroupedLongEntry entry(int i){return new LongLongEntry(keys[i],values[i]);}
    }
    private static final class LongLongEntry implements LongGroupedLongEntry { private final long key; private final long value; private LongLongEntry(long key,long value){this.key=key;this.value=value;} public long key(){return key;} public long value(){return value;} }

    private static final class LongDouble implements LongGroupedDoubleResult {
        private final long[] keys; private final double[] values; private final int size;
        private LongDouble(long[] keys, double[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaLongDoubleConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<LongGroupedDoubleEntry> toList(){ArrayList<LongGroupedDoubleEntry> r=new ArrayList<LongGroupedDoubleEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public LongGroupedDoubleEntry[] toArray(){LongGroupedDoubleEntry[] r=new LongGroupedDoubleEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private LongGroupedDoubleEntry entry(int i){return new LongDoubleEntry(keys[i],values[i]);}
    }
    private static final class LongDoubleEntry implements LongGroupedDoubleEntry { private final long key; private final double value; private LongDoubleEntry(long key,double value){this.key=key;this.value=value;} public long key(){return key;} public double value(){return value;} }

    private static final class LongLongSummary implements LongGroupedLongSummaryResult {
        private final long[] keys; private final SomaLongSummary[] values; private final int size;
        private LongLongSummary(long[] keys, SomaLongSummary[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaLongLongSummaryConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<LongGroupedLongSummaryEntry> toList(){ArrayList<LongGroupedLongSummaryEntry> r=new ArrayList<LongGroupedLongSummaryEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public LongGroupedLongSummaryEntry[] toArray(){LongGroupedLongSummaryEntry[] r=new LongGroupedLongSummaryEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private LongGroupedLongSummaryEntry entry(int i){return new LongLongSummaryEntry(keys[i],values[i]);}
    }
    private static final class LongLongSummaryEntry implements LongGroupedLongSummaryEntry { private final long key; private final SomaLongSummary value; private LongLongSummaryEntry(long key,SomaLongSummary value){this.key=key;this.value=value;} public long key(){return key;} public SomaLongSummary value(){return value;} }

    private static final class LongDoubleSummary implements LongGroupedDoubleSummaryResult {
        private final long[] keys; private final SomaDoubleSummary[] values; private final int size;
        private LongDoubleSummary(long[] keys, SomaDoubleSummary[] values, int size){this.keys=keys;this.values=values;this.size=size;}
        @Override public long size(){return size;}
        @Override public void forEach(SomaLongDoubleSummaryConsumer consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}
        @Override public List<LongGroupedDoubleSummaryEntry> toList(){ArrayList<LongGroupedDoubleSummaryEntry> r=new ArrayList<LongGroupedDoubleSummaryEntry>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}
        @Override public LongGroupedDoubleSummaryEntry[] toArray(){LongGroupedDoubleSummaryEntry[] r=new LongGroupedDoubleSummaryEntry[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}
        private LongGroupedDoubleSummaryEntry entry(int i){return new LongDoubleSummaryEntry(keys[i],values[i]);}
    }
    private static final class LongDoubleSummaryEntry implements LongGroupedDoubleSummaryEntry { private final long key; private final SomaDoubleSummary value; private LongDoubleSummaryEntry(long key,SomaDoubleSummary value){this.key=key;this.value=value;} public long key(){return key;} public SomaDoubleSummary value(){return value;} }

    private static void require(Object value) { if (value == null) throw SomaFailures.invalid(io.github.somaruntime.soma.SomaOperation.QUERY, "grouped consumer is null"); }
}
