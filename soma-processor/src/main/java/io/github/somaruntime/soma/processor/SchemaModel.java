package io.github.somaruntime.soma.processor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

final class SchemaModel {
    enum TypeKind {
        TABLE,
        VALUE
    }

    enum FieldRole {
        FIELD,
        KEY,
        INDEX
    }

    static final class Field {
        final String name;
        final String type;
        final FieldRole role;
        final long sourcePosition;

        Field(String name, String type, FieldRole role, long sourcePosition) {
            this.name = name;
            this.type = type;
            this.role = role;
            this.sourcePosition = sourcePosition;
        }
    }

    static final class Type {
        final TypeElement element;
        final String qualifiedName;
        final String simpleName;
        final TypeKind kind;
        final long defaultCapacity;
        final List<Field> fields;

        Type(
                TypeElement element,
                TypeKind kind,
                long defaultCapacity,
                List<Field> fields) {
            this.element = element;
            this.qualifiedName = element.getQualifiedName().toString();
            this.simpleName = element.getSimpleName().toString();
            this.kind = kind;
            this.defaultCapacity = defaultCapacity;
            this.fields = Collections.unmodifiableList(new ArrayList<Field>(fields));
        }
    }

    static final class Composition {
        final PackageElement packageElement;
        final String schemaPackage;
        final String generatedNamespace;
        final List<Type> tables;
        final List<Type> values;
        final String fingerprint;
        final String generatedFqn;

        Composition(
                PackageElement packageElement,
                String schemaPackage,
                String generatedNamespace,
                List<Type> tables,
                List<Type> values,
                String fingerprint) {
            this.packageElement = packageElement;
            this.schemaPackage = schemaPackage;
            this.generatedNamespace = generatedNamespace;
            this.tables = Collections.unmodifiableList(new ArrayList<Type>(tables));
            this.values = Collections.unmodifiableList(new ArrayList<Type>(values));
            this.fingerprint = fingerprint;
            this.generatedFqn = generatedNamespace + ".internal."
                    + ProcessorContract.GENERATED_CARRIER_SIMPLE_NAME;
        }

        Element[] originatingElements() {
            List<Element> elements = new ArrayList<Element>();
            elements.add(packageElement);
            for (Type table : tables) {
                elements.add(table.element);
            }
            for (Type value : values) {
                elements.add(value.element);
            }
            return elements.toArray(new Element[elements.size()]);
        }
    }

    static final class GeneratedOutput {
        final Composition composition;
        final String source;
        final String sourceSha256;

        GeneratedOutput(Composition composition, String source, String sourceSha256) {
            this.composition = composition;
            this.source = source;
            this.sourceSha256 = sourceSha256;
        }
    }

    private SchemaModel() {
    }
}
