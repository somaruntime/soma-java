#!/usr/bin/env python3
"""Generate the fixed V1 grouped-result API grammar and its internal adapters."""

from __future__ import print_function

import argparse
import os
import sys


ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUBLIC = os.path.join(
    ROOT, "soma-runtime", "src", "main", "java", "io", "github",
    "somaruntime", "soma")
INTERNAL = os.path.join(PUBLIC, "internal")

KEYS = [
    ("Boolean", "boolean", "boolean[]", "boolean"),
    ("Byte", "byte", "byte[]", "byte"),
    ("Short", "short", "short[]", "short"),
    ("Char", "char", "char[]", "char"),
    ("Int", "int", "int[]", "int"),
    ("Long", "long", "long[]", "long"),
]
VALUES = [
    ("Int", "int", "int[]", "int", "java.util.function.ObjIntConsumer"),
    ("Long", "long", "long[]", "long", "java.util.function.ObjLongConsumer"),
    ("Double", "double", "double[]", "double", "java.util.function.ObjDoubleConsumer"),
    ("LongSummary", "SomaLongSummary", "SomaLongSummary[]", "SomaLongSummary",
     "SomaObjLongSummaryConsumer"),
    ("DoubleSummary", "SomaDoubleSummary", "SomaDoubleSummary[]", "SomaDoubleSummary",
     "SomaObjDoubleSummaryConsumer"),
]

HEADER = "package io.github.somaruntime.soma;\n\n"


def public_entry(value_token, value_type):
    name = "Grouped%sEntry" % value_token
    return name, HEADER + (
        "/** Detached grouped result entry. */\n"
        "public interface %s<K> {\n"
        "    K key();\n"
        "    %s value();\n"
        "}\n" % (name, value_type))


def public_result(value_token, consumer):
    name = "Grouped%sResult" % value_token
    entry = "Grouped%sEntry" % value_token
    if consumer.startswith("java.util.function"):
        consumer_type = "%s<? super K>" % consumer
    else:
        consumer_type = "%s<? super K>" % consumer
    return name, HEADER + (
        "import java.util.List;\n\n"
        "/** Detached reference-key grouped result. */\n"
        "public interface %s<K> {\n"
        "    long size();\n"
        "    void forEach(%s consumer);\n"
        "    List<%s<K>> toList();\n"
        "    %s<K>[] toArray();\n"
        "}\n" % (name, consumer_type, entry, entry))


def object_summary_consumer(token, value_type):
    name = "SomaObj%sConsumer" % token
    return name, HEADER + (
        "/** Unboxed object-key grouped summary consumer. */\n"
        "@FunctionalInterface\n"
        "public interface %s<K> {\n"
        "    void accept(K key, %s value);\n"
        "}\n" % (name, value_type))


def primitive_entry(key_token, key_type, value_token, value_type):
    name = "%sGrouped%sEntry" % (key_token, value_token)
    return name, HEADER + (
        "/** Detached primitive-key grouped result entry. */\n"
        "public interface %s {\n"
        "    %s key();\n"
        "    %s value();\n"
        "}\n" % (name, key_type, value_type))


def primitive_consumer(key_token, key_type, value_token, value_type):
    name = "Soma%s%sConsumer" % (key_token, value_token)
    return name, HEADER + (
        "/** Unboxed primitive-key grouped result consumer. */\n"
        "@FunctionalInterface\n"
        "public interface %s {\n"
        "    void accept(%s key, %s value);\n"
        "}\n" % (name, key_type, value_type))


def primitive_result(key_token, value_token):
    name = "%sGrouped%sResult" % (key_token, value_token)
    entry = "%sGrouped%sEntry" % (key_token, value_token)
    consumer = "Soma%s%sConsumer" % (key_token, value_token)
    return name, HEADER + (
        "import java.util.List;\n\n"
        "/** Detached columnar primitive-key grouped result. */\n"
        "public interface %s {\n"
        "    long size();\n"
        "    void forEach(%s consumer);\n"
        "    List<%s> toList();\n"
        "    %s[] toArray();\n"
        "}\n" % (name, consumer, entry, entry))


def internal_source():
    out = []
    out.append("package io.github.somaruntime.soma.internal;\n\n")
    out.append("import io.github.somaruntime.soma.*;\n")
    out.append("import java.util.ArrayList;\n")
    out.append("import java.util.List;\n")
    out.append("import java.util.function.*;\n\n")
    out.append("/** Internal adapters over detached typed grouped columns. */\n")
    out.append("final class GeneratedGroupedResults {\n\n")
    for index, (token, _, _, _) in enumerate(KEYS, 1):
        out.append("    static final int KEY_%s = %d;\n" % (token.upper(), index))
    out.append("    static final int KEY_REFERENCE = 7;\n")
    for index, (token, _, _, _, _) in enumerate(VALUES, 1):
        out.append("    static final int VALUE_%s = %d;\n" % (token.upper(), index))
    out.append("\n    private GeneratedGroupedResults() {}\n\n")
    out.append("    static Object create(int keyKind, int valueKind, Object keys, Object values, int size) {\n")
    out.append("        switch (keyKind) {\n")
    for key_token, _, _, key_suffix in KEYS:
        out.append("            case KEY_%s: return create%s(valueKind, (%s[]) keys, values, size);\n" % (
            key_token.upper(), key_token, key_suffix))
    out.append("            case KEY_REFERENCE: return createReference(valueKind, (Object[]) keys, values, size);\n")
    out.append("            default: throw new AssertionError(\"unknown grouped key kind\");\n")
    out.append("        }\n    }\n\n")

    for key_token, _, _, key_suffix in KEYS:
        out.append("    private static Object create%s(int valueKind, %s[] keys, Object values, int size) {\n" % (
            key_token, key_suffix))
        out.append("        switch (valueKind) {\n")
        for value_token, _, _, value_suffix, _ in VALUES:
            out.append("            case VALUE_%s: return new %s%s(keys, (%s[]) values, size);\n" % (
                value_token.upper(), key_token, value_token, value_suffix))
        out.append("            default: throw new AssertionError(\"unknown grouped value kind\");\n")
        out.append("        }\n    }\n\n")

    out.append("    private static Object createReference(int valueKind, Object[] keys, Object values, int size) {\n")
    out.append("        switch (valueKind) {\n")
    for value_token, _, _, value_suffix, _ in VALUES:
        out.append("            case VALUE_%s: return new Reference%s<Object>(keys, (%s[]) values, size);\n" % (
            value_token.upper(), value_token, value_suffix))
    out.append("            default: throw new AssertionError(\"unknown grouped value kind\");\n")
    out.append("        }\n    }\n\n")

    for value_token, value_type, _, _, consumer in VALUES:
        result = "Grouped%sResult" % value_token
        entry = "Grouped%sEntry" % value_token
        impl = "Reference%s" % value_token
        entry_impl = "Reference%sEntry" % value_token
        consumer_type = "%s<? super K>" % consumer
        out.append("    private static final class %s<K> implements %s<K> {\n" % (impl, result))
        out.append("        private final Object[] keys; private final %s[] values; private final int size;\n" % (
            "int" if value_type == "int" else "long" if value_type == "long" else "double" if value_type == "double" else value_type))
        out.append("        private %s(Object[] keys, %s[] values, int size) { this.keys = keys; this.values = values; this.size = size; }\n" % (
            impl, "int" if value_type == "int" else "long" if value_type == "long" else "double" if value_type == "double" else value_type))
        out.append("        @Override public long size() { return size; }\n")
        out.append("        @Override @SuppressWarnings(\"unchecked\") public void forEach(%s consumer) { require(consumer); for (int i=0;i<size;i++) consumer.accept((K) keys[i], values[i]); }\n" % consumer_type)
        out.append("        @Override public List<%s<K>> toList() { ArrayList<%s<K>> r=new ArrayList<%s<K>>(size); for(int i=0;i<size;i++) r.add(entry(i)); return r; }\n" % (entry, entry, entry))
        out.append("        @Override @SuppressWarnings(\"unchecked\") public %s<K>[] toArray() { %s<K>[] r=(%s<K>[]) new %s<?>[size]; for(int i=0;i<size;i++) r[i]=entry(i); return r; }\n" % (entry, entry, entry, entry))
        out.append("        @SuppressWarnings(\"unchecked\") private %s<K> entry(int i) { return new %s<K>((K) keys[i], values[i]); }\n" % (entry, entry_impl))
        out.append("    }\n")
        out.append("    private static final class %s<K> implements %s<K> { private final K key; private final %s value; private %s(K key, %s value){this.key=key;this.value=value;} public K key(){return key;} public %s value(){return value;} }\n\n" % (
            entry_impl, entry, value_type, entry_impl, value_type, value_type))

    for key_token, key_type, _, key_suffix in KEYS:
        for value_token, value_type, _, _, _ in VALUES:
            result = "%sGrouped%sResult" % (key_token, value_token)
            entry = "%sGrouped%sEntry" % (key_token, value_token)
            consumer = "Soma%s%sConsumer" % (key_token, value_token)
            impl = "%s%s" % (key_token, value_token)
            entry_impl = "%s%sEntry" % (key_token, value_token)
            out.append("    private static final class %s implements %s {\n" % (impl, result))
            out.append("        private final %s[] keys; private final %s[] values; private final int size;\n" % (key_suffix, value_type))
            out.append("        private %s(%s[] keys, %s[] values, int size){this.keys=keys;this.values=values;this.size=size;}\n" % (impl, key_suffix, value_type))
            out.append("        @Override public long size(){return size;}\n")
            out.append("        @Override public void forEach(%s consumer){require(consumer);for(int i=0;i<size;i++)consumer.accept(keys[i],values[i]);}\n" % consumer)
            out.append("        @Override public List<%s> toList(){ArrayList<%s> r=new ArrayList<%s>(size);for(int i=0;i<size;i++)r.add(entry(i));return r;}\n" % (entry, entry, entry))
            out.append("        @Override public %s[] toArray(){%s[] r=new %s[size];for(int i=0;i<size;i++)r[i]=entry(i);return r;}\n" % (entry, entry, entry))
            out.append("        private %s entry(int i){return new %s(keys[i],values[i]);}\n" % (entry, entry_impl))
            out.append("    }\n")
            out.append("    private static final class %s implements %s { private final %s key; private final %s value; private %s(%s key,%s value){this.key=key;this.value=value;} public %s key(){return key;} public %s value(){return value;} }\n\n" % (
                entry_impl, entry, key_type, value_type, entry_impl, key_type,
                value_type, key_type, value_type))

    out.append("    private static void require(Object value) { if (value == null) throw SomaFailures.invalid(io.github.somaruntime.soma.SomaOperation.QUERY, \"grouped consumer is null\"); }\n")
    out.append("}\n")
    return "".join(out)


def outputs():
    result = {}
    for value_token, value_type, _, _, consumer in VALUES:
        name, content = public_entry(value_token, value_type)
        result[os.path.join(PUBLIC, name + ".java")] = content
        name, content = public_result(value_token, consumer)
        result[os.path.join(PUBLIC, name + ".java")] = content
        if not consumer.startswith("java.util.function"):
            name, content = object_summary_consumer(value_token, value_type)
            result[os.path.join(PUBLIC, name + ".java")] = content
    for key_token, key_type, _, _ in KEYS:
        for value_token, value_type, _, _, _ in VALUES:
            for factory in (primitive_entry, primitive_consumer):
                name, content = factory(
                    key_token, key_type, value_token, value_type)
                result[os.path.join(PUBLIC, name + ".java")] = content
            name, content = primitive_result(key_token, value_token)
            result[os.path.join(PUBLIC, name + ".java")] = content
    result[os.path.join(INTERNAL, "GeneratedGroupedResults.java")] = internal_source()
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    stale = []
    for path, content in sorted(outputs().items()):
        if args.check:
            try:
                with open(path, "r") as stream:
                    actual = stream.read()
            except IOError:
                actual = None
            if actual != content:
                stale.append(os.path.relpath(path, ROOT))
        else:
            directory = os.path.dirname(path)
            if not os.path.isdir(directory):
                os.makedirs(directory)
            with open(path, "w") as stream:
                stream.write(content)
    if stale:
        print("grouped API is stale:", file=sys.stderr)
        for path in stale:
            print("  " + path, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
