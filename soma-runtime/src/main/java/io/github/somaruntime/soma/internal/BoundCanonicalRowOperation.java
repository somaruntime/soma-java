package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;

/** Canonical Row semantics bound once to one immutable published generation. */
final class BoundCanonicalRowOperation {

    final CanonicalRowOperation canonical;
    final GeneratedTableLayout layout;
    final TableStateRoot root;
    final SomaOperation operation;
    final Object provenance;

    BoundCanonicalRowOperation(
            CanonicalRowOperation canonical,
            GeneratedTableLayout layout,
            TableStateRoot root,
            SomaOperation operation,
            Object provenance) {
        if (canonical == null || layout == null || root == null
                || operation == null || provenance == null
                || canonical.tableIdentity.descriptor() != layout) {
            throw new AssertionError("invalid bound Canonical Row operation");
        }
        this.canonical = canonical;
        this.layout = layout;
        this.root = root;
        this.operation = operation;
        this.provenance = provenance;
    }
}
