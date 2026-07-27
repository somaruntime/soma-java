package com.hgtech.soma.runtime.metadata;

/** Effective primary locator choice；不表达 secondary exact access。 */
public enum SomaPrimaryLocator {
    NONE,
    HASH_INT,
    HASH_LONG,
    HASH_COMPOSITE
}
