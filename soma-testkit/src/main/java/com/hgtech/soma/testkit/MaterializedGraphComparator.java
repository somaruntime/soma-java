package com.hgtech.soma.testkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Explicit adapter-driven comparator；不会调用 table-row equals 充当 graph oracle。 */
public final class MaterializedGraphComparator {
    private MaterializedGraphComparator() {}

    public interface Adapter<T> {
        void compare(T expected, T actual, Context context);
    }

    public static <T> Result compareFirst(
            T expected, T actual, Adapter<T> adapter) {
        return compare(expected, actual, adapter, false);
    }

    public static <T> Result compareAll(T expected, T actual, Adapter<T> adapter) {
        return compare(expected, actual, adapter, true);
    }

    public static <T> Result compare(
            T expected, T actual, Adapter<T> adapter, boolean collectAll) {
        if (adapter == null) throw new NullPointerException("adapter");
        Context context = new Context("$", collectAll);
        adapter.compare(expected, actual, context);
        return new Result(context.mismatches);
    }

    public static final class Context {
        private final String path;
        private final boolean collectAll;
        private final List<Mismatch> mismatches;

        private Context(String path, boolean collectAll) {
            this(path, collectAll, new ArrayList<Mismatch>());
        }

        private Context(String path, boolean collectAll, List<Mismatch> mismatches) {
            this.path = path;
            this.collectAll = collectAll;
            this.mismatches = mismatches;
        }

        public String path() { return path; }
        public boolean hasMismatch() { return !mismatches.isEmpty(); }

        public Context field(String name) {
            if (name == null || name.isEmpty()) throw new IllegalArgumentException("field name");
            return new Context(path + "." + name, collectAll, mismatches);
        }

        public Context index(int index) {
            if (index < 0) throw new IllegalArgumentException("index");
            return new Context(path + "[" + index + "]", collectAll, mismatches);
        }

        public Context key(Object key) {
            return new Context(path + "{" + safeKey(key) + "}", collectAll, mismatches);
        }

        public void scalar(Object expected, Object actual) {
            if (expected == actual || (expected != null && expected.equals(actual))) return;
            mismatch(expected, actual);
        }

        public void floating(double expected, double actual) {
            if (Double.doubleToLongBits(expected) != Double.doubleToLongBits(actual)) {
                mismatch(Double.valueOf(expected), Double.valueOf(actual));
            }
        }

        public <T> void row(T expected, T actual, Adapter<T> adapter) {
            if (expected == null || actual == null) {
                if (expected != actual) mismatch(expected, actual);
                return;
            }
            adapter.compare(expected, actual, this);
        }

        public <T> void list(List<T> expected, List<T> actual, Adapter<T> adapter) {
            if (expected == null || actual == null) {
                if (expected != actual) mismatch(expected, actual);
                return;
            }
            if (expected.size() != actual.size()) {
                field("size").mismatch(Integer.valueOf(expected.size()), Integer.valueOf(actual.size()));
                if (!collectAll) return;
            }
            int count = Math.min(expected.size(), actual.size());
            for (int i = 0; i < count && (collectAll || !hasMismatch()); i++) {
                index(i).row(expected.get(i), actual.get(i), adapter);
            }
        }

        public <K, V> void map(
                Map<K, V> expected, Map<K, V> actual, Adapter<V> valueAdapter) {
            if (expected == null || actual == null) {
                if (expected != actual) mismatch(expected, actual);
                return;
            }
            if (expected.size() != actual.size()) {
                field("size").mismatch(Integer.valueOf(expected.size()), Integer.valueOf(actual.size()));
                if (!collectAll) return;
            }
            for (Map.Entry<K, V> entry : expected.entrySet()) {
                if (!actual.containsKey(entry.getKey())) {
                    key(entry.getKey()).mismatch("present", "missing");
                } else {
                    key(entry.getKey()).row(
                            entry.getValue(), actual.get(entry.getKey()), valueAdapter);
                }
                if (!collectAll && hasMismatch()) return;
            }
        }

        public void mismatch(Object expected, Object actual) {
            if (!collectAll && !mismatches.isEmpty()) return;
            mismatches.add(new Mismatch(path, render(expected), render(actual)));
        }

        private static String safeKey(Object key) {
            if (key == null) return "null";
            if (key instanceof Number || key instanceof Boolean || key instanceof Enum<?>) {
                return render(key);
            }
            return key.getClass().getName();
        }

        private static String render(Object value) {
            if (value == null) return "null";
            if (value instanceof Number || value instanceof Boolean
                    || value instanceof Character || value instanceof Enum<?>) {
                return String.valueOf(value);
            }
            if (value instanceof String) {
                String text = (String) value;
                return text.length() <= 64 ? text : text.substring(0, 64) + "...";
            }
            return value.getClass().getName();
        }
    }

    public static final class Result {
        private final List<Mismatch> mismatches;

        private Result(List<Mismatch> mismatches) {
            this.mismatches = Collections.unmodifiableList(
                    new ArrayList<Mismatch>(mismatches));
        }

        public boolean matches() { return mismatches.isEmpty(); }
        public List<Mismatch> mismatches() { return mismatches; }
        public Mismatch firstMismatch() {
            return mismatches.isEmpty() ? null : mismatches.get(0);
        }
    }

    public static final class Mismatch {
        private final String path;
        private final String expected;
        private final String actual;

        private Mismatch(String path, String expected, String actual) {
            this.path = path;
            this.expected = expected;
            this.actual = actual;
        }

        public String path() { return path; }
        public String expected() { return expected; }
        public String actual() { return actual; }
    }
}
