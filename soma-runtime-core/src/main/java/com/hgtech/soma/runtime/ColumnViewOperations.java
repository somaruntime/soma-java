package com.hgtech.soma.runtime;

/** Prebound diagnostic operation names for a generated column-view field. */
final class ColumnViewOperations {
    final String column;
    final String presence;
    final String value;

    private ColumnViewOperations(String field, String valueAction) {
        this.column = field + ".column";
        this.presence = column + ".isPresent";
        this.value = column + "." + valueAction;
    }

    /**
     * One cache is owned by each concrete view kind. Generated callers pass schema field
     * literals, so the retained entries are bounded by the loaded schema fields of that kind.
     */
    static final class Cache {
        private final String valueAction;
        private volatile Entry[] entries = new Entry[8];
        private int size;

        Cache(String valueAction) {
            if (valueAction == null) {
                throw new NullPointerException("valueAction");
            }
            this.valueAction = valueAction;
        }

        ColumnViewOperations forField(String field) {
            Entry[] snapshot = entries;
            ColumnViewOperations operations = find(snapshot, field);
            if (operations != null) {
                return operations;
            }
            return bind(field);
        }

        private synchronized ColumnViewOperations bind(String field) {
            Entry[] current = entries;
            ColumnViewOperations existing = find(current, field);
            if (existing != null) {
                return existing;
            }

            Entry[] updated;
            if (size >= current.length - (current.length >>> 2)) {
                if (current.length >= 1 << 29) {
                    throw new OutOfMemoryError("column view operation cache capacity");
                }
                updated = new Entry[current.length << 1];
                for (Entry entry : current) {
                    if (entry != null) {
                        insert(updated, entry);
                    }
                }
            } else {
                updated = new Entry[current.length];
                System.arraycopy(current, 0, updated, 0, current.length);
            }

            ColumnViewOperations operations = new ColumnViewOperations(field, valueAction);
            insert(updated, new Entry(field, operations));
            size++;
            entries = updated;
            return operations;
        }

        private static ColumnViewOperations find(Entry[] values, String field) {
            int mask = values.length - 1;
            int slot = spread(field.hashCode()) & mask;
            Entry entry;
            while ((entry = values[slot]) != null) {
                if (entry.field.equals(field)) {
                    return entry.operations;
                }
                slot = (slot + 1) & mask;
            }
            return null;
        }

        private static void insert(Entry[] values, Entry entry) {
            int mask = values.length - 1;
            int slot = spread(entry.field.hashCode()) & mask;
            while (values[slot] != null) {
                slot = (slot + 1) & mask;
            }
            values[slot] = entry;
        }

        private static int spread(int hash) {
            return hash ^ (hash >>> 16);
        }

        private static final class Entry {
            final String field;
            final ColumnViewOperations operations;

            Entry(String field, ColumnViewOperations operations) {
                this.field = field;
                this.operations = operations;
            }
        }
    }
}
