package io.github.somaruntime.soma.processor;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

/** Complete pre-render symbol validation for the currently admitted generated surface. */
final class GeneratedSymbolTable {

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
        if (!model.supportsI1Surface()) {
            return valid;
        }

        registerTopLevel(model.generatedPackage() + ".Soma", "composition facade");
        registerTopLevel(model.generatedPackage() + ".SomaGroup", "Group facade");

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

        for (String reserved : new String[] {
                "runtime", "create", "size", "capacity", "reserve", "add", "find", "get",
                "update", "count", "selectAll", "filter", "materialize"
        }) {
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
        register(endpointScope, "Selection", "generated nested type");

        for (CompositionModel.FieldModel field : table.fields()) {
            register(tableScope, field.name(), "Field endpoint " + field.name());
            register(
                    endpointScope,
                    GeneratedNames.fieldEndpointType(field.name()),
                    "Field endpoint type for " + field.name());
            register(viewScope, field.name(), "Field accessor " + field.name());
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
}
