package io.github.somaruntime.soma.runtime;

/** Immutable effective runtime plan for one normalized child ownership edge。 */
public final class ChildPlan {
    private final String ownerTable;
    private final String childField;
    private final String childTable;
    private final int initialCapacity;

    private ChildPlan(
            String ownerTable,
            String childField,
            String childTable,
            int initialCapacity) {
        this.ownerTable = CanonicalSupport.required(ownerTable, "ownerTable");
        this.childField = CanonicalSupport.required(childField, "childField");
        this.childTable = CanonicalSupport.required(childTable, "childTable");
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("initialCapacity must be positive");
        }
        this.initialCapacity = initialCapacity;
    }

    public static ChildPlan create(
            String ownerTable,
            String childField,
            String childTable,
            int initialCapacity) {
        return new ChildPlan(ownerTable, childField, childTable, initialCapacity);
    }

    public String ownerTable() { return ownerTable; }
    public String childField() { return childField; }
    public String childTable() { return childTable; }
    public int initialCapacity() { return initialCapacity; }

    String identity() { return identity(ownerTable, childField); }

    static String identity(String ownerTable, String childField) {
        String owner = CanonicalSupport.required(ownerTable, "ownerTable");
        String field = CanonicalSupport.required(childField, "childField");
        return owner.length() + ":" + owner + field;
    }

    String toCanonicalJson() {
        return new StringBuilder()
                .append('{')
                .append("\"childField\":").append(CanonicalSupport.quote(childField)).append(',')
                .append("\"childTable\":").append(CanonicalSupport.quote(childTable)).append(',')
                .append("\"initialCapacity\":").append(initialCapacity).append(',')
                .append("\"ownerTable\":").append(CanonicalSupport.quote(ownerTable))
                .append('}').toString();
    }

}
