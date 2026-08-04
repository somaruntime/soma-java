package io.github.somaruntime.soma.processor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.lang.model.element.Element;

final class CompositionModel {

    private final String schemaPackage;
    private final String generatedPackage;
    private final String processorVersion;
    private final String contractVersion;
    private final String runtimeBuildIdentity;
    private final String fingerprint;
    private final List<String> schemaLines;
    private final List<TableModel> tables;
    private final List<Element> originatingElements;

    CompositionModel(
            String schemaPackage,
            String generatedPackage,
            String processorVersion,
            String contractVersion,
            String runtimeBuildIdentity,
            String fingerprint,
            List<String> schemaLines,
            List<TableModel> tables,
            List<? extends Element> originatingElements) {
        this.schemaPackage = schemaPackage;
        this.generatedPackage = generatedPackage;
        this.processorVersion = processorVersion;
        this.contractVersion = contractVersion;
        this.runtimeBuildIdentity = runtimeBuildIdentity;
        this.fingerprint = fingerprint;
        this.schemaLines = Collections.unmodifiableList(new ArrayList<String>(schemaLines));
        this.tables = Collections.unmodifiableList(new ArrayList<TableModel>(tables));
        this.originatingElements = Collections.unmodifiableList(
                new ArrayList<Element>(originatingElements));
    }

    CompositionModel(
            String schemaPackage,
            String generatedPackage,
            String processorVersion,
            String contractVersion,
            String runtimeBuildIdentity,
            String fingerprint,
            List<String> schemaLines,
            List<? extends Element> originatingElements) {
        this(
                schemaPackage,
                generatedPackage,
                processorVersion,
                contractVersion,
                runtimeBuildIdentity,
                fingerprint,
                schemaLines,
                Collections.<TableModel>emptyList(),
                originatingElements);
    }

    String schemaPackage() {
        return schemaPackage;
    }

    String generatedPackage() {
        return generatedPackage;
    }

    String processorVersion() {
        return processorVersion;
    }

    String contractVersion() {
        return contractVersion;
    }

    String runtimeBuildIdentity() {
        return runtimeBuildIdentity;
    }

    String fingerprint() {
        return fingerprint;
    }

    List<String> schemaLines() {
        return schemaLines;
    }

    List<TableModel> tables() {
        return tables;
    }

    boolean supportsI1Surface() {
        if (tables.isEmpty()) {
            return false;
        }
        for (TableModel table : tables) {
            if (!table.supportsI1Surface()) {
                return false;
            }
        }
        return true;
    }

    List<Element> originatingElements() {
        return originatingElements;
    }

    String generatedTypeName() {
        return generatedPackage + ".SomaCompositionLinkage";
    }

    String manifestPath() {
        return "META-INF/soma/" + schemaPackage + ".properties";
    }

    static final class TableModel {

        private final String simpleName;
        private final long defaultCapacity;
        private final List<FieldModel> fields;

        TableModel(String simpleName, long defaultCapacity, List<FieldModel> fields) {
            this.simpleName = simpleName;
            this.defaultCapacity = defaultCapacity;
            this.fields = Collections.unmodifiableList(new ArrayList<FieldModel>(fields));
        }

        String simpleName() {
            return simpleName;
        }

        long defaultCapacity() {
            return defaultCapacity;
        }

        List<FieldModel> fields() {
            return fields;
        }

        int keyFieldIndex() {
            for (int index = 0; index < fields.size(); index++) {
                if (fields.get(index).role() == FieldRole.KEY) {
                    return index;
                }
            }
            return -1;
        }

        boolean supportsI1Surface() {
            int keys = 0;
            int payloads = 0;
            for (FieldModel field : fields) {
                if (!"long".equals(field.typeName()) || field.role() == FieldRole.INDEX) {
                    return false;
                }
                if (field.role() == FieldRole.KEY) {
                    keys++;
                } else {
                    payloads++;
                }
            }
            return keys == 1 && payloads >= 1;
        }
    }

    static final class FieldModel {

        private final String name;
        private final String typeName;
        private final FieldRole role;

        FieldModel(String name, String typeName, FieldRole role) {
            this.name = name;
            this.typeName = typeName;
            this.role = role;
        }

        String name() {
            return name;
        }

        String typeName() {
            return typeName;
        }

        FieldRole role() {
            return role;
        }
    }

    enum FieldRole {
        KEY,
        INDEX,
        FIELD
    }
}
