package io.github.somaruntime.soma.processor;

import javax.lang.model.element.Element;
import java.util.ArrayList;
import java.util.List;

import static io.github.somaruntime.soma.processor.DenseTableCodegenModel.TableSpec;

/** Deterministic artifact orchestrator for the V1 schema-specific columnar facade. */
final class DenseTableSourceGenerator {
    private final String generatedPackage;
    private final DenseAuxiliarySourceEmitter auxiliary;
    private final DenseTableSourceEmitter tableEmitter;
    private final DenseDataFlowSourceEmitter dataFlowEmitter;
    private final DenseDeltaSourceEmitter deltaEmitter;

    DenseTableSourceGenerator(
            String generatedPackage,
            String schemaHash,
            List<TableSpec> schemaTables) {
        this.generatedPackage = generatedPackage;
        this.auxiliary = new DenseAuxiliarySourceEmitter(generatedPackage, schemaTables);
        this.tableEmitter = new DenseTableSourceEmitter(
                generatedPackage, schemaHash, schemaTables);
        this.dataFlowEmitter = new DenseDataFlowSourceEmitter(
                generatedPackage, schemaHash);
        this.deltaEmitter = new DenseDeltaSourceEmitter(
                generatedPackage, schemaHash);
    }

    List<GeneratedSourceOutput> render(TableSpec table) {
        List<GeneratedSourceOutput> result = new ArrayList<GeneratedSourceOutput>();
        add(result, table.name("Cursor"), auxiliary.cursorSource(table), table.origin);
        add(result, table.name("UpdateCursor"), auxiliary.updateCursorSource(table), table.origin);
        add(result, table.name("Batch"), auxiliary.batchSource(table), table.origin);
        add(result, table.name("Mutator"), auxiliary.mutatorSource(table), table.origin);
        add(result, table.name("Scan"), auxiliary.scanSource(table), table.origin);
        add(result, table.name("DataFlow"), dataFlowEmitter.dataFlowSource(table), table.origin);
        if (table.keyed()) {
            add(result, table.name("Delta"), deltaEmitter.deltaSource(table), table.origin);
            add(result, table.name("KeyTraversal"), auxiliary.keyTraversalSource(table), table.origin);
        }
        add(result, table.name("Table"), tableEmitter.tableSource(table), table.origin);
        return result;
    }

    private void add(
            List<GeneratedSourceOutput> result,
            String simpleName,
            String source,
            Element origin) {
        result.add(new GeneratedSourceOutput(
                generatedPackage + "." + simpleName, source, origin));
    }
}
