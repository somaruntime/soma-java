package io.github.somaruntime.soma.processor;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

/** Complete pre-render symbol validation for the currently admitted generated surface. */
final class GeneratedSymbolTable {

    static boolean validateAcrossCompositions(
            List<CompositionModel> models,
            Messager messager) {
        List<GlobalSymbol> symbols = new ArrayList<GlobalSymbol>();
        for (CompositionModel model : models) {
            Element origin = model.originatingElements().get(0);
            for (String name : topLevelNames(model)) {
                symbols.add(new GlobalSymbol(name, origin));
            }
        }
        boolean valid = true;
        for (int leftIndex = 0; leftIndex < symbols.size(); leftIndex++) {
            GlobalSymbol left = symbols.get(leftIndex);
            for (int rightIndex = leftIndex + 1;
                    rightIndex < symbols.size(); rightIndex++) {
                GlobalSymbol right = symbols.get(rightIndex);
                String leftPortable = portable(left.name);
                String rightPortable = portable(right.name);
                if (leftPortable.equals(rightPortable)
                        || leftPortable.startsWith(rightPortable + ".")
                        || rightPortable.startsWith(leftPortable + ".")) {
                    messager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "[SOMA-1015] Generated namespace collision: "
                                    + left.name + " vs " + right.name + ".",
                            right.origin);
                    valid = false;
                }
            }
        }
        return valid;
    }

    private final Elements elements;
    private final Messager messager;
    private final Element origin;
    private final Map<String, String> exact = new LinkedHashMap<String, String>();
    private final Map<String, String> portable = new LinkedHashMap<String, String>();
    private boolean valid = true;

    GeneratedSymbolTable(Elements elements, Messager messager, Element origin) {
        this.elements = elements;
        this.messager = messager;
        this.origin = origin;
    }

    boolean validate(CompositionModel model) {
        registerTopLevel(model.generatedTypeName(), "internal composition linkage");
        registerTopLevel(model.generatedPackage() + ".Soma", "composition facade");
        registerTopLevel(model.generatedPackage() + ".SomaGroup", "Group facade");

        for (CompositionModel.ValueModel value : model.values()) {
            registerTopLevel(value.publicTypeName(), "detached Value object");
            registerValueMembers(value);
        }

        String compositionScope = model.generatedPackage() + "#table-accessor";
        for (CompositionModel.TableModel table : model.tables()) {
            String objectType = model.generatedPackage() + "." + table.simpleName();
            String tableType = model.generatedPackage() + "."
                    + GeneratedNames.tableType(table.simpleName());
            registerTopLevel(objectType, "detached Table object");
            registerTopLevel(tableType, "Table facade");
            register(
                    compositionScope,
                    GeneratedNames.tableAccessor(table.simpleName()),
                    "Table accessor for " + table.simpleName());
            registerTableMembers(table);
        }
        return valid;
    }

    private void registerTableMembers(CompositionModel.TableModel table) {
        String tableScope = table.simpleName() + "Table#direct-member";
        String endpointScope = table.simpleName() + "Table#nested-type";
        String viewScope = table.simpleName() + "Table.View#method";

        for (String reserved : TABLE_MEMBERS) {
            register(tableScope, reserved, "generated Table member");
        }
        for (String reserved : new String[] {
                "getClass", "hashCode", "equals", "clone", "toString", "notify",
                "notifyAll", "wait", "finalize", "fetch"
        }) {
            register(viewScope, reserved, "Java/Object or generated View member");
        }
        register(endpointScope, "View", "generated nested type");
        register(endpointScope, "Editor", "generated nested type");
        register(endpointScope, "Stream", "generated nested type");
        register(endpointScope, "Selection", "generated nested type");
        register(endpointScope, "ReadStream", "generated nested type");
        register(endpointScope, "IndexSelection", "generated nested type");

        for (CompositionModel.FieldModel field : table.fields()) {
            register(tableScope, field.name(), "Field endpoint " + field.name());
            if (field.role() == CompositionModel.FieldRole.INDEX) {
                register(
                        tableScope,
                        GeneratedNames.indexAccessor(field.name()),
                        "Index accessor for " + field.name());
            }
            register(
                    endpointScope,
                    GeneratedNames.fieldEndpointType(field.name()),
                    "Field endpoint type for " + field.name());
            register(viewScope, field.name(), "Field accessor " + field.name());
            registerEndpointChildren(
                    table.simpleName() + "Table." + GeneratedNames.fieldEndpointType(field.name()),
                    field);
        }
    }

    private void registerValueMembers(CompositionModel.ValueModel value) {
        String objectScope = value.simpleName() + "#object-method";
        String viewScope = value.simpleName() + ".View#method";
        for (String reserved : new String[] {
                "getClass", "hashCode", "equals", "clone", "toString", "notify",
                "notifyAll", "wait", "finalize"
        }) {
            register(objectScope, reserved, "Java/Object member");
            register(viewScope, reserved, "Java/Object member");
        }
        register(viewScope, "fetch", "generated Value View member");
        for (CompositionModel.FieldModel field : value.fields()) {
            register(objectScope, field.name(), "Value accessor " + field.name());
            register(viewScope, field.name(), "Value View accessor " + field.name());
        }
    }

    private void registerEndpointChildren(
            String ownerScope,
            CompositionModel.FieldModel ownerField) {
        if (ownerField.type().kind() != CompositionModel.LogicalKind.VALUE) {
            return;
        }
        String memberScope = ownerScope + "#direct-member";
        String typeScope = ownerScope + "#nested-type";
        for (String reserved : ENDPOINT_MEMBERS) {
            register(memberScope, reserved, "generated Field endpoint member");
        }
        for (CompositionModel.FieldModel child : ownerField.type().value().fields()) {
            register(memberScope, child.name(), "nested Field endpoint " + child.name());
            register(
                    typeScope,
                    GeneratedNames.fieldEndpointType(child.name()),
                    "nested Field endpoint type for " + child.name());
            registerEndpointChildren(
                    ownerScope + "." + GeneratedNames.fieldEndpointType(child.name()),
                    child);
        }
    }

    private void registerTopLevel(String qualifiedName, String description) {
        register("top-level", qualifiedName, description);
        TypeElement occupied = elements.getTypeElement(qualifiedName);
        if (occupied != null) {
            String code = qualifiedName.endsWith(".SomaCompositionLinkage")
                    ? "[SOMA-1007]" : "[SOMA-1015]";
            messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    code + " Generated type already exists: " + qualifiedName + ".",
                    occupied);
            valid = false;
        }
    }

    private void register(String scope, String symbol, String description) {
        String exactKey = scope + '\u0000' + symbol;
        String existing = exact.put(exactKey, description);
        if (existing != null) {
            collision(scope, symbol, existing, description);
            return;
        }

        String portableSymbol = Normalizer.normalize(symbol, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT);
        String portableKey = scope + '\u0000' + portableSymbol;
        String portableExisting = portable.put(portableKey, description);
        if (portableExisting != null) {
            collision(scope, symbol, portableExisting, description);
        }
    }

    private static List<String> topLevelNames(CompositionModel model) {
        List<String> names = new ArrayList<String>();
        names.add(model.generatedTypeName());
        names.add(model.generatedPackage() + ".Soma");
        names.add(model.generatedPackage() + ".SomaGroup");
        for (CompositionModel.ValueModel value : model.values()) {
            names.add(value.publicTypeName());
        }
        for (CompositionModel.TableModel table : model.tables()) {
            names.add(model.generatedPackage() + "." + table.simpleName());
            names.add(model.generatedPackage() + "."
                    + GeneratedNames.tableType(table.simpleName()));
        }
        return names;
    }

    private static String portable(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT);
    }

    private void collision(
            String scope,
            String symbol,
            String existing,
            String incoming) {
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                "[SOMA-1015] Generated symbol collision in " + scope + ": "
                        + symbol + " (" + existing + " vs " + incoming + ").",
                origin);
        valid = false;
    }

    private static final String[] ENDPOINT_MEMBERS = {
        "runtime", "parallel", "filter", "map", "mapToInt", "mapToLong",
        "mapToDouble", "distinct", "sorted", "sortedBy", "skip", "limit",
        "top", "count", "anyMatch", "allMatch", "noneMatch", "findFirst",
        "min", "max", "sum", "average", "summaryStatistics", "forEach",
        "forEachOrdered", "toList", "toArray", "groupBy", "join", "crossJoin",
        "eq", "ne", "lt", "le", "gt", "ge", "between", "in", "isNull",
        "isNotNull", "and", "or", "not", "asc", "desc", "_metadata",
        "_explain"
    };

    private static final String[] TABLE_MEMBERS = {
        "LAYOUT", "runtime", "borrowedView", "borrowedEditor", "createLayout",
        "create", "size", "capacity", "reserve", "add", "find", "get", "update",
        "remove", "parallel", "count", "selectAll", "filter", "map", "mapToInt",
        "mapToLong", "mapToDouble", "sorted", "sortedBy", "skip", "limit", "top",
        "anyMatch", "allMatch", "noneMatch", "findFirst", "min", "max", "sum",
        "average", "summaryStatistics", "forEach", "forEachOrdered", "toList",
        "toArray", "groupBy", "join", "crossJoin", "materialize", "_metadata",
        "_explain"
    };

    private static final class GlobalSymbol {
        private final String name;
        private final Element origin;

        private GlobalSymbol(String name, Element origin) {
            this.name = name;
            this.origin = origin;
        }
    }
}
