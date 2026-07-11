package com.hgtech.soma.testkit;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MaterializedGraphComparatorCheck {
    private MaterializedGraphComparatorCheck() {}

    public static void main(String[] args) {
        Node expected = node(1, Arrays.asList(node(2, null)));
        Node actual = node(1, Arrays.asList(node(3, null)));
        MaterializedGraphComparator.Result result =
                MaterializedGraphComparator.compareAll(expected, actual, ADAPTER);
        require(!result.matches(), "mismatch expected");
        require("$.children[0].value".equals(result.firstMismatch().path()),
                "first path");

        Map<Integer, Node> left = new LinkedHashMap<Integer, Node>();
        Map<Integer, Node> right = new LinkedHashMap<Integer, Node>();
        left.put(Integer.valueOf(2), node(2, null));
        left.put(Integer.valueOf(1), node(1, null));
        right.put(Integer.valueOf(1), node(1, null));
        right.put(Integer.valueOf(2), node(2, null));
        MaterializedGraphComparator.Result mapResult = MaterializedGraphComparator.compareAll(
                left, right, new MaterializedGraphComparator.Adapter<Map<Integer, Node>>() {
                    @Override public void compare(
                            Map<Integer, Node> e,
                            Map<Integer, Node> a,
                            MaterializedGraphComparator.Context c) {
                        c.map(e, a, ADAPTER);
                    }
                });
        require(mapResult.matches(), "map iteration order must not matter");

        Node absent = node(1, null);
        Node presentEmpty = node(1, Arrays.<Node>asList());
        require(!MaterializedGraphComparator.compareAll(absent, presentEmpty, ADAPTER).matches(),
                "absent and present-empty differ");
        Node absentZero = node(1, null);
        Node presentZero = node(1, null);
        presentZero.optional = Integer.valueOf(0);
        require(!MaterializedGraphComparator.compareAll(
                absentZero, presentZero, ADAPTER).matches(),
                "absent and present zero differ");

        Node multiExpected = node(1, Arrays.asList(node(2, null)));
        Node multiActual = node(9, Arrays.asList(node(8, null)));
        require(MaterializedGraphComparator.compareFirst(
                multiExpected, multiActual, ADAPTER).mismatches().size() == 1,
                "compareFirst stops at first mismatch");
        require(MaterializedGraphComparator.compareAll(
                multiExpected, multiActual, ADAPTER).mismatches().size() == 2,
                "compareAll collects mismatches");

        MaterializedGraphComparator.Result floating =
                MaterializedGraphComparator.compareAll(
                        Double.valueOf(-0.0d), Double.valueOf(+0.0d),
                        new MaterializedGraphComparator.Adapter<Double>() {
                            @Override public void compare(
                                    Double e, Double a,
                                    MaterializedGraphComparator.Context c) {
                                c.floating(e.doubleValue(), a.doubleValue());
                            }
                        });
        require(!floating.matches(), "floating comparison keeps exact bits");
        System.out.println("materialized-graph-comparator-check: ok");
    }

    private static final MaterializedGraphComparator.Adapter<Node> ADAPTER =
            new MaterializedGraphComparator.Adapter<Node>() {
                @Override public void compare(
                        Node expected,
                        Node actual,
                        MaterializedGraphComparator.Context context) {
                    context.field("value").scalar(
                            Integer.valueOf(expected.value), Integer.valueOf(actual.value));
                    context.field("optional").scalar(expected.optional, actual.optional);
                    context.field("children").list(
                            expected.children, actual.children, this);
                }
            };

    private static Node node(int value, List<Node> children) {
        Node node = new Node();
        node.value = value;
        node.children = children;
        return node;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Node {
        int value;
        Integer optional;
        List<Node> children;
    }
}
